# Flutter
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# flutter_local_notifications
-keep class com.dexterous.** { *; }

# Kakao SDK
-keep class com.kakao.sdk.** { *; }

# Naver Login SDK (com.navercorp.nid:oauth)
-keep class com.navercorp.** { *; }
-keep interface com.navercorp.** { *; }
-keep class com.nhn.android.naverlogin.** { *; }
-keep class com.example.flutter_naver_login.** { *; }

# AndroidX Browser (CustomTabs - used by Naver Login)
-keep class androidx.browser.** { *; }

# Google Sign-In
-keep class com.google.android.gms.auth.** { *; }

# OkHttp & Okio (used by Naver SDK)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin coroutines & metadata (used by Naver SDK)
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepattributes Signature,InnerClasses,EnclosingMethod

# Google Play Core (deferred components)
-dontwarn com.google.android.play.core.splitcompat.SplitCompatApplication
-dontwarn com.google.android.play.core.splitinstall.**
-dontwarn com.google.android.play.core.tasks.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
