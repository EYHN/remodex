# Bouncy Castle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.remodex.android.**$$serializer { *; }
-keepclassmembers class com.remodex.android.** { *** Companion; }
-keepclasseswithmembers class com.remodex.android.** { kotlinx.serialization.KSerializer serializer(...); }
