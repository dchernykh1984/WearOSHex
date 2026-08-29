# R8 rules for the release build.
#
# The app has no reflection, no serialization and no JNI, so the defaults in
# proguard-android-optimize.txt cover it. Compose and AndroidX ship their own
# consumer rules, which are applied automatically.
#
# Keep the line numbers in stack traces readable: without this the mapping file
# cannot map a crash back to a source line, and the mapping published with each
# release becomes far less useful.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
