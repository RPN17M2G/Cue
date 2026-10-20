# Keep mp3agic and kotlinx.serialization model classes.
-keep class com.mpatric.mp3agic.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.rpn17m2g.cue.**$$serializer { *; }
-keepclassmembers class io.github.rpn17m2g.cue.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.rpn17m2g.cue.** {
    kotlinx.serialization.KSerializer serializer(...);
}
