# --- GENERAL ANDROID & REFLECTION ATTRIBUTES ---
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses, SourceFile, LineNumberTable
-dontwarn android.hardware.biometrics.**

# --- APP CODE, MODELS, & INNER CLASSES ---
-keep class com.goldenduck.scoring.** { *; }
-keepclassmembers class com.goldenduck.scoring.** { *; }
-keepclassmembers class com.goldenduck.scoring.**$* { *; }
-keepclassmembers enum com.goldenduck.scoring.** { *; }

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
-keep class com.goldenduck.scoring.Converters { *; }
-keepclassmembers class com.goldenduck.scoring.Converters { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.RoomDatabase
-keep class com.goldenduck.scoring.AppDatabase

-keep @androidx.room.Entity class * { *; }
-keep class * implements androidx.room.RoomDatabase { *; }
-keep interface * extends androidx.room.Dao { *; }

# Keep Room Entities and DAOs
-keep class com.goldenduck.scoring.PlayerEntity { *; }
-keep class com.goldenduck.scoring.MatchEntity { *; }
-keep class com.goldenduck.scoring.PlayerMatchStatEntity { *; }
-keep class com.goldenduck.scoring.DraftMatchEntity { *; }
-keep interface com.goldenduck.scoring.*Dao { *; }

# --- DATA MODELS (Gson, Firestore, Room) ---
-keep class com.goldenduck.scoring.Ball { *; }
-keep class com.goldenduck.scoring.BallType { *; }
-keep class com.goldenduck.scoring.Match { *; }
-keep class com.goldenduck.scoring.Player { *; }
-keep class com.goldenduck.scoring.Innings { *; }
-keep class com.goldenduck.scoring.CommentaryEntry { *; }
-keep class com.goldenduck.scoring.FowEvent { *; }
-keep class com.goldenduck.scoring.PartnershipEvent { *; }
-keep class com.goldenduck.scoring.BackupManager$BackupBundle { *; }

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
-keep class com.goldenduck.scoring.ScoringViewModel { *; }
-keep class com.goldenduck.scoring.databinding.** { *; }

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

-keep class com.goldenduck.scoring.AppNotificationServiceExtension { *; }
-keepclassmembers class com.goldenduck.scoring.AppNotificationServiceExtension { *; }
-keep class * implements com.onesignal.notifications.INotificationServiceExtension { *; }
-keep class * implements com.onesignal.notifications.INotificationLifecycleListener { *; }
-keep class * implements com.onesignal.notifications.INotificationClickListener { *; }
-keep class com.goldenduck.scoring.NotificationReplyReceiver { *; }
-keepclassmembers class com.goldenduck.scoring.NotificationReplyReceiver { *; }
-keep class com.goldenduck.scoring.NotificationDismissReceiver { *; }
-keepclassmembers class com.goldenduck.scoring.NotificationDismissReceiver { *; }
-keep class com.goldenduck.scoring.ChatNotificationHelper { *; }
-keepclassmembers class com.goldenduck.scoring.ChatNotificationHelper { *; }
-keep class com.goldenduck.scoring.LeagueNotificationManager { *; }
-keepclassmembers class com.goldenduck.scoring.LeagueNotificationManager { *; }
