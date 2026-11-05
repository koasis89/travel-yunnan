# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kr.koasis.travelyunnan.data.json.** { *** Companion; }
-keepclasseswithmembers class kr.koasis.travelyunnan.data.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class kr.koasis.travelyunnan.data.json.**$$serializer { *; }
