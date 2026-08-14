# --- GENERAL ANDROID ---
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-dontwarn android.hardware.biometrics.**

# --- ROOM DATABASE ---
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.RoomDatabase
-keep class com.example.scoring.AppDatabase

-keep @androidx.room.Entity class * { *; }
-keep class * implements androidx.room.RoomDatabase { *; }
-keep interface * extends androidx.room.Dao { *; }

# --- DATA MODELS (Gson, Firestore, Room) ---
# Keep all data classes and their members to prevent R8 from stripping/obfuscating fields used in reflection
-keep class com.example.scoring.Ball { *; }
-keep class com.example.scoring.BallType { *; }
-keep class com.example.scoring.Match { *; }
-keep class com.example.scoring.Player { *; }
-keep class com.example.scoring.Innings { *; }
-keep class com.example.scoring.CommentaryEntry { *; }
-keep class com.example.scoring.FowEvent { *; }
-keep class com.example.scoring.PartnershipEvent { *; }
-keep class com.example.scoring.BackupManager$BackupBundle { *; }

# Keep Room Entities and DAOs
-keep @androidx.room.Entity class * { *; }
-keep class com.example.scoring.PlayerEntity { *; }
-keep class com.example.scoring.MatchEntity { *; }
-keep class com.example.scoring.PlayerMatchStatEntity { *; }
-keep class com.example.scoring.DraftMatchEntity { *; }
-keep interface com.example.scoring.*Dao { *; }

# General rule for all scoring data classes to ensure zero-arg constructors and fields are preserved
-keepclassmembers class com.example.scoring.** {
    public <init>();
    <fields>;
}

# Keep all classes in the package that are likely models
-keepclassmembers class com.example.scoring.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- FIREBASE & GOOGLE ---
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepattributes SourceFile, LineNumberTable

# --- LOTTIE ---
-keep class com.airbnb.lottie.** { *; }

# --- GLIDE ---
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# --- KOTLIN COROUTINES ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# --- VIEWMODEL & VIEW BINDING ---
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class com.example.scoring.ScoringViewModel { *; }
-keep class com.example.scoring.databinding.** { *; }

# --- RECYCLERVIEW ---
-keep class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder { *; }
-keep class * extends androidx.recyclerview.widget.RecyclerView$Adapter { *; }

# --- MPANDROIDCHART ---
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**
