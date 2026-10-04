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
# reflectively and pulls in optional compile-only classes that are not in the
# APK (errorprone annotations, the Google API HTTP client used only by its
# remote-keys downloader, and joda-time). R8 must not fail on those, and Tink's
# own classes must be kept so encryption keeps working after minification.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.api.** { *; }
