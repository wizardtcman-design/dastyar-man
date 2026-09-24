-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlinx.**
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class com.dastyar.app.data.** { *; }
-keep class com.dastyar.app.ai.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature
