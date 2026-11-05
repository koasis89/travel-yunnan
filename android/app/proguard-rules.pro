# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kr.koasis.travelyunnan.data.json.** { *** Companion; }
-keepclasseswithmembers class kr.koasis.travelyunnan.data.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class kr.koasis.travelyunnan.data.json.**$$serializer { *; }

# Room 엔티티·DAO (KSP 생성 코드가 참조)
-keep class kr.koasis.travelyunnan.data.db.** { *; }
# 위젯·리시버
-keep class kr.koasis.travelyunnan.widget.** { *; }
-keep class kr.koasis.travelyunnan.notify.** { *; }
# 디버깅용 스택트레이스
-keepattributes SourceFile,LineNumberTable
