# ProGuard & R8 Configuration for Boti Video Editor (Production Release)

# 1. Models & Entities
-keep class com.example.model.** { *; }
-keep class com.example.data.db.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.migration.Migration { *; }
-dontwarn androidx.room.**

# 2. Media3 / ExoPlayer
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.common.** { *; }
-dontwarn androidx.media3.**

# 3. MediaCodec & Codecs
-keepclassmembers class * {
    native <methods>;
}

# 4. Coil Image Loading
-keep class coil.** { *; }
-dontwarn coil.**

# 5. Jetpack Compose
-keep class androidx.compose.** { *; }

# 6. Coroutines
-dontwarn kotlinx.coroutines.**
