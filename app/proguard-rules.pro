-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlinx.**
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class com.dastyar.app.data.** { *; }
-keep class com.dastyar.app.ai.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature

# androidx.security-crypto is built on Google Tink. Tink is referenced
# reflectively and pulls in optional compile-only annotations that are not in
# the APK, so R8 must not fail on them and must keep Tink's own classes.
-dontwarn com.google.errorprone.annotations.**
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.api.** { *; }
