# ProGuard rules for VoiceDrop
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keepattributes SerializedName
-keep class com.wangjianshuo.voicedrop.ModelKt { *; }
-keepclassmembers class com.wangjianshuo.voicedrop.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
