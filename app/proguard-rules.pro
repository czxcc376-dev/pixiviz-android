# Keep pixiv REST models (kotlinx.serialization) — they are reflectively referenced by name.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class pixiviz.mobile.** {
    *** Companion;
}
-keepclasseswithmembers class pixiviz.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
