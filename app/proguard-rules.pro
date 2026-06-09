# ============================================================
#  ProGuard / R8 rules for AliCards Admin
# ============================================================
#  These rules tell R8 what to keep when shrinking and
#  obfuscating the release build. Without them, Firebase
#  reflection and Glide annotation processing will silently
#  break at runtime.
# ============================================================


# ------------------------------------------------------------
#  General Android
# ------------------------------------------------------------

# Preserve line numbers in stack traces (helps debug crashes
# from real users without giving up obfuscation entirely)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotation info so Firebase + Glide reflection works
-keepattributes *Annotation*

# Keep generic signatures used by Gson / Firestore for parsing
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses


# ------------------------------------------------------------
#  Project model classes
#  Firestore uses reflection to map documents to POJOs.
#  These must keep their field names and no-arg constructors
#  so getString("title") etc. continues to work after R8.
# ------------------------------------------------------------

-keep class com.mowtiie.faithfully.data.Chapter { *; }
-keep class com.mowtiie.faithfully.data.Card { *; }
-keep class com.mowtiie.faithfully.data.Photo { *; }

# If you add more model classes that map to Firestore docs,
# either add them here individually or use a package wildcard:
# -keep class com.example.alicards.models.** { *; }


# ------------------------------------------------------------
#  Firebase
# ------------------------------------------------------------

# Firestore + Firebase Auth use reflection heavily
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firestore PropertyName annotation
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName *;
}

# Firestore needs no-arg constructors on model classes for deserialization
-keepclasseswithmembers class * {
    @com.google.firebase.firestore.DocumentId <fields>;
}


# ------------------------------------------------------------
#  Glide
#  Glide generates code via annotation processor + uses
#  reflection on @GlideModule classes
# ------------------------------------------------------------

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**


# ------------------------------------------------------------
#  Kotlin coroutines (pulled in transitively by Firebase)
# ------------------------------------------------------------

-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}


# ------------------------------------------------------------
#  Material Components / AndroidX
# ------------------------------------------------------------

# AndroidX libraries are generally safe with R8 default behaviour
-dontwarn androidx.**
-dontwarn com.google.android.material.**


# ------------------------------------------------------------
#  Keep all native methods (Android + JNI requirement)
# ------------------------------------------------------------

-keepclasseswithmembernames class * {
    native <methods>;
}


# ------------------------------------------------------------
#  Keep enums (Android uses reflection for valueOf / values)
# ------------------------------------------------------------

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# ------------------------------------------------------------
#  Keep Parcelable + Serializable contracts
# ------------------------------------------------------------

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}