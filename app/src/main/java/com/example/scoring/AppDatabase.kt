package com.example.scoring

import android.content.Context
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Database(
    entities = [PlayerEntity::class, MatchEntity::class, PlayerMatchStatEntity::class, DraftMatchEntity::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun matchDao(): MatchDao
    abstract fun statsDao(): StatsDao
    abstract fun draftDao(): DraftMatchDao

    companion object {
        private var instance: AppDatabase? = null
        
        @JvmField
        val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): AppDatabase {
            if (instance == null) {
                instance = databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cricket_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
            }
            return instance ?: throw IllegalStateException("Database not initialized")
        }
    }
}
