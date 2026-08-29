import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// Release signing is driven entirely by environment variables so the keystore
// never lives in the repository. When they are absent (local dev, PR CI) the
// release build simply stays unsigned; the GitHub Release workflow provides
// them from repository secrets.
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

// versionCode must grow with every published build. The release workflow derives
// a monotonic value from the semantic version (major * 1000000 + minor * 1000 +
// patch), which keeps every release strictly above the last without depending on
// a CI run counter that resets when a workflow is renamed.
//
// A VERSION_CODE that is set but unreadable is a broken release pipeline, so it
// stops the build. Quietly falling back to 1 there would publish a release that
// sorts below every earlier one, and Android would then refuse the update on
// every watch that already has the app - with nothing in the logs to explain it.
val versionCodeBase =
    System.getenv("VERSION_CODE")?.let { raw ->
        raw.toIntOrNull() ?: error("VERSION_CODE is set to '$raw', which is not an integer")
    } ?: 1

android {
    namespace = "com.dchernykh.hex"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dchernykh.hex"
        // Wear OS 3 (API 30) is the oldest platform Google still supports; older
        // watches run the pre-3 RPC-based platform, which this app does not target.
        minSdk = 30
        targetSdk = 36

        versionCode = versionCodeBase
        versionName = System.getenv("VERSION_NAME") ?: rootProject.extra["releasedVersion"] as String

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // A debug build carries its own applicationId, so it installs beside
            // a release one instead of colliding with it. Without the suffix,
            // pushing a debug build to a watch that already has the published app
            // fails outright on the signature mismatch, and the only way through
            // is to uninstall the app - and with it the high scores that are the
            // most interesting thing to test against.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Shrink and obfuscate with R8, and strip unused resources. APK size
            // matters more on a watch than on a phone: watches have far less
            // storage, and the APK is pushed over Bluetooth when a paired phone
            // installs it.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // Builds res/xml/locales_config.xml from the values-* folders and points
        // the manifest at it, which is what puts the app in the watch's per-app
        // language list on Android 13 and later. Without it the eleven
        // translations can only ever be reached by changing the whole watch's
        // language, which nobody does to play a game in Czech.
        generateLocaleConfig = true
    }

    lint {
        // Fail the build on lint errors; warnings stay non-fatal for now and can
        // be promoted to errors once the codebase stabilises. Android Lint ships
        // the Wear OS checks (standalone flag, unsupported APIs, tile and
        // complication misuse), so this is the gate that catches watch-specific
        // manifest and API mistakes.
        abortOnError = true
        warningsAsErrors = false
        // lintDebug in CI covers analysis; skip the duplicate release lint pass.
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

ktlint {
    android.set(true)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

kover {
    reports {
        filters {
            excludes {
                // Generated code is not meaningful to cover.
                classes("*.BuildConfig", "*.R", "*.R$*", "*ComposableSingletons*")
                // The Compose screens and the DataStore-backed record store are
                // the two things a JVM test cannot reach: one needs a composition,
                // the other a Context. The instrumented test launches the app and
                // walks the screens to cover them instead.
                packages("com.dchernykh.hex.ui", "com.dchernykh.hex.store")
                classes("com.dchernykh.hex.MainActivity*")
            }
        }
        verify {
            rule {
                // What is left is the rules, the opponent, the board geometry,
                // the panning and the view model that drives them - all plain
                // Kotlin, and none of it with any excuse for being uncovered.
                minBound(80)
            }
        }
    }
}

// Pin transitive dependency versions for reproducible builds. Every classpath
// that either ships or gates a merge is locked; Android's internal configurations
// are intentionally left out. The instrumented one is in the list because the
// emulator run is a required check: an unannounced androidx.test bump that breaks
// it would otherwise fail a pull request that changed nothing.
// Regenerate wear/gradle.lockfile with the "Update lockfiles" workflow or
// `./gradlew :wear:dependencies --write-locks`.
listOf(
    "debugRuntimeClasspath",
    "releaseRuntimeClasspath",
    "debugUnitTestRuntimeClasspath",
    "releaseUnitTestRuntimeClasspath",
    "debugAndroidTestRuntimeClasspath",
).forEach { configurationName ->
    configurations.matching { it.name == configurationName }.configureEach {
        resolutionStrategy.activateDependencyLocking()
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // The tick loop and the record store are both suspending.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    // Box, fillMaxSize and background are imported by name from
    // androidx.compose.foundation, so it is declared rather than left to arrive
    // through Wear Compose - which is free to stop bringing it in any release.
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)
    // createAndroidComposeRule needs an activity it can host; this supplies the
    // empty one, in the debug manifest only so it never reaches a release APK.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)

    // The instrumented test writes `org.junit.Test` and `org.junit.Assert`, so
    // JUnit is declared here rather than left to arrive transitively through
    // androidx.test.ext:junit - a transitive that is free to drop it in any
    // release, taking the test source set with it.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
