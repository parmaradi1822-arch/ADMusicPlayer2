package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String, // Path or unique ID
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val duration: Long,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val isDownloaded: Boolean = false,
    val folder: String = "Internal",
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val dateCreated: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Int,
    val songId: String
)

@Entity(tableName = "settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
