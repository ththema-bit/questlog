# kotlinx.serialization: Serializer der Spielstand-Klassen behalten (Backup und Speicherdatei).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.github.ththemabit.questlog.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.ththemabit.questlog.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.ththemabit.questlog.**$$serializer { *; }
