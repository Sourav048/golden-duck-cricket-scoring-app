package com.example.scoring

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.util.UUID

@Keep
@Entity(tableName = "players")
class PlayerEntity() {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var name: String = ""
    var jerseyNumber: String = "0" // 2-3 digits
    @JvmField
    var photoUri: String = "" // file path to saved photo, empty if none
    var createdAt: Long = System.currentTimeMillis() // System.currentTimeMillis()

    @Ignore
    var photoBase64: String? = null // Only used for Export/Import

    init {} // REQUIRED FOR FIREBASE

    @Ignore
    constructor(name: String?, jerseyNumber: String?, photoUri: String?) : this() {
        this.name = name ?: ""
        this.jerseyNumber = jerseyNumber ?: "0"
        this.photoUri = photoUri ?: ""
    }
}
