pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WearOSHex"

// One module: the watch app is the whole product. There is no phone companion to
// share a contract with, and the game logic is plain Kotlin that the module's own
// JVM unit tests reach directly, so a separate pure-Kotlin module would buy
// nothing but a second build script.
include(":wear")
