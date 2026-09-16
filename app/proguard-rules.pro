# Keep native JNI methods and DTOs for TagLib
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class now.link.mastigias.data.taglib.** { *; }

# Keep NativeTagBundle constructor & fields accessed from C++ JNI
-keepclassmembers class now.link.mastigias.data.taglib.NativeTagBundle {
    <init>(...);
    <fields>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
