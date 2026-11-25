# Only the Material icons are trimmed down to the ones the app uses; everything else is kept as is
# (no renaming, no optimisation), so reflection in NewPipe, Ktor, serialization etc. keeps working.
-keep class !androidx.compose.material.icons.**,** { *; }
-keepattributes *
-dontobfuscate
-dontoptimize
-dontwarn **
-ignorewarnings
