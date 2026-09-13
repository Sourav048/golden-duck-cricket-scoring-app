# --- GENERAL ANDROID & REFLECTION ATTRIBUTES ---
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses, SourceFile, LineNumberTable
-dontwarn android.hardware.biometrics.**

# --- APP CODE, MODELS, & INNER CLASSES ---
-keep class com.example.scoring.** { *; }
-keepclassmembers class com.example.scoring.** { *; }
-keepclassmembers class com.example.scoring.**$* { *; }
-keepclassmembers enum com.example.scoring.** { *; }

# --- ENUM VALUES PRESERVATION ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- GSON & TYPETOKEN REFLECTION ---
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- ROOM DATABASE & CONVERTERS ---
-keep class com.example.scoring.Converters { *; }
-keepclassmembers class com.example.scoring.Converters { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.RoomDatabase
-keep class com.example.scoring.AppDatabase

-keep @androidx.room.Entity class * { *; }
-keep class * implements androidx.room.RoomDatabase { *; }
-keep interface * extends androidx.room.Dao { *; }

# Keep Room Entities and DAOs
-keep class com.example.scoring.PlayerEntity { *; }
-keep class com.example.scoring.MatchEntity { *; }
-keep class com.example.scoring.PlayerMatchStatEntity { *; }
-keep class com.example.scoring.DraftMatchEntity { *; }
-keep interface com.example.scoring.*Dao { *; }

# --- DATA MODELS (Gson, Firestore, Room) ---
-keep class com.example.scoring.Ball { *; }
-keep class com.example.scoring.BallType { *; }
-keep class com.example.scoring.Match { *; }
-keep class com.example.scoring.Player { *; }
-keep class com.example.scoring.Innings { *; }
-keep class com.example.scoring.CommentaryEntry { *; }
-keep class com.example.scoring.FowEvent { *; }
-keep class com.example.scoring.PartnershipEvent { *; }
-keep class com.example.scoring.BackupManager$BackupBundle { *; }

# --- FIREBASE & GOOGLE ---
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

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

# --- ONESIGNAL PUSH NOTIFICATIONS ---
-keep class com.onesignal.** { *; }
-keep interface com.onesignal.** { *; }
-dontwarn com.onesignal.**

-keep class com.example.scoring.AppNotificationServiceExtension { *; }
-keepclassmembers class com.example.scoring.AppNotificationServiceExtension { *; }
-keep class com.example.scoring.NotificationReplyReceiver { *; }
-keepclassmembers class com.example.scoring.NotificationReplyReceiver { *; }
-keep class com.example.scoring.NotificationDismissReceiver { *; }
-keepclassmembers class com.example.scoring.NotificationDismissReceiver { *; }
-keep class com.example.scoring.ChatNotificationHelper { *; }
-keepclassmembers class com.example.scoring.ChatNotificationHelper { *; }
-keep class com.example.scoring.LeagueNotificationManager { *; }
-keepclassmembers class com.example.scoring.LeagueNotificationManager { *; }
