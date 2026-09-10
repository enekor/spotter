# Mantener clases serializables de kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.n3k0chan.spotter.**$$serializer { *; }
-keepclassmembers class com.n3k0chan.spotter.** {
    *** Companion;
}
-keepclasseswithmembers class com.n3k0chan.spotter.** {
    kotlinx.serialization.KSerializer serializer(...);
}
