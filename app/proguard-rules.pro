# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.RoomDatabase

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# kotlinx.serialization — reflection on Companion + $$serializer is mandatory
# for @Serializable types to round-trip through R8/ProGuard.
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
-keep,includedescriptorclasses class com.avangard.app.**$$serializer { *; }
-keepclassmembers class com.avangard.app.core.domain.model.BackupBundle {
    *** Companion;
}
-keepclassmembers class com.avangard.app.core.domain.model.BackupDailySession {
    *** Companion;
}
-keepclassmembers class com.avangard.app.core.domain.model.BackupFocusSession {
    *** Companion;
}
-keepclassmembers class com.avangard.app.core.domain.model.BackupHabitLog {
    *** Companion;
}

# Sentry — preserve runtime classes and metadata so symbolicated stacks
# still resolve after R8.
-keep class io.sentry.** { *; }
-keepattributes LineNumberTable, SourceFile
-dontwarn io.sentry.**
