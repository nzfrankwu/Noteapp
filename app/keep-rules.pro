# Noteapp's own shrinker (R8) rules for the release build.

# Keep class names and packages as they are (no renaming, no moving classes between packages).
# With renaming on, R8 moved one of Kotlin's internal string classes (kotlin.text.StringsKt__…)
# out of its package while its parent class is only visible inside that package, and the release
# app crashed at start with IllegalAccessError (found 18 Nov 2026). The speed-ups (inlining,
# removing unused code) stay; only the renaming (smaller APK, harder to read) is given up.
-dontobfuscate
