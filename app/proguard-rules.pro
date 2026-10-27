# Hilt, Room, WorkManager, Glance, ML Kit and OkHttp ship their own consumer
# rules; only project-specific keeps live here.

# Room entities are also @Serializable (backup): keep their names stable.
-keep @androidx.room.Entity class com.itermeum.app.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class kotlinx.serialization.json.** { *; }
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.itermeum.app.**$$serializer { *; }

# PdfBox-Android (D-40): JPEG 2000 and public-key encryption are optional and not shipped.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
