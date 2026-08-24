package com.example.scoring

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migration definitions for Room database.
 * 
 * When you need to change the database schema:
 * 1. Increment the version number in @Database(version = X) in AppDatabase.kt
 * 2. Create a migration object for the version change below
 * 3. Add the migration to .addMigrations() in AppDatabase.kt
 * 4. Test the migration thoroughly before releasing
 */

// Migration from version 8 to 9 (placeholder for future schema changes)
// Uncomment and customize when you need to add this migration
/*
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Example: Add a new column to matches table
        // database.execSQL("ALTER TABLE matches ADD COLUMN newField TEXT DEFAULT ''")
        
        // Example: Add a new column to players table
        // database.execSQL("ALTER TABLE players ADD COLUMN newField INTEGER DEFAULT 0")
        
        // Example: Create a new table
        // database.execSQL("""
        //     CREATE TABLE IF NOT EXISTS new_table (
        //         id TEXT PRIMARY KEY NOT NULL,
        //         name TEXT NOT NULL
        //     )
        // """)
    }
}
*/

// Migration from version 9 to 10 (placeholder for future schema changes)
// Uncomment and customize when you need to add this migration
/*
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add your migration SQL here
    }
}
*/

// Add more migrations as needed for future schema changes
// val MIGRATION_10_11 = object : Migration(10, 11) { ... }
