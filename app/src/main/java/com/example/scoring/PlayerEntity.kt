package com.example.scoring

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.util.UUID

@Keep
@Entity(tableName = "players")
class PlayerEntity() : java.io.Serializable {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var name: String = ""
    var jerseyNumber: String = "0" // 2-3 digits
    @JvmField
    var photoUri: String = "" // file path to saved photo, empty if none
    var createdAt: Long = System.currentTimeMillis() // System.currentTimeMillis()

    var gullyId: String = "local" // Tag for multi-gully support
    var cloudId: String? = null    // Gully-specific ID
    var globalId: String? = null   // UNIVERSAL ID (Across all gullies)
    var lastSyncedAt: Long = 0     // Timestamp for differential sync

    @androidx.room.Ignore
    var photoBase64: String? = null // Synced for cross-device photo support (Ignored by Room)

    var originGully: String? = null // PERSISTENT: Tracks where the player first played
    @androidx.room.Ignore
    var totalRuns: Long = 0
    @androidx.room.Ignore
    var totalWickets: Long = 0
    @androidx.room.Ignore
    var totalMatches: Long = 0

    init {} // REQUIRED FOR FIREBASE

    @Ignore
    constructor(name: String?, jerseyNumber: String?, photoUri: String?) : this() {
        this.name = name ?: ""
        this.jerseyNumber = jerseyNumber ?: "0"
        this.photoUri = photoUri ?: ""
    }
}
