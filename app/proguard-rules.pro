# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable        # Keep file names and line numbers.
-keep public class * extends java.lang.Exception  # Optional: Keep custom exceptions.
-keep class com.noti.** implements java.io.Serializable { *; }
-keep class com.noti.** implements android.os.Parcelable { *; }

-keep public class com.google.firebase.** { *; }
-keep class com.google.auth.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.android.gms.internal.** { *; }
-keepclasseswithmembers class com.google.firebase.FirebaseException