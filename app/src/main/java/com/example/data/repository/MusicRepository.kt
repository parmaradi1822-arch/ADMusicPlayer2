package com.example.data.repository

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.example.data.audio.DynamicWavGenerator
import com.example.data.db.AppDatabase
import com.example.data.models.AppSettingEntity
import com.example.data.models.PlaylistEntity
import com.example.data.models.PlaylistSongCrossRef
import com.example.data.models.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File

class MusicRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val songDao = db.songDao()
    private val playlistDao = db.playlistDao()
    private val settingDao = db.settingDao()

    val allSongs: Flow<List<SongEntity>> = songDao.getAllSongs()
    val favorites: Flow<List<SongEntity>> = songDao.getFavorites()
    val recentlyPlayed: Flow<List<SongEntity>> = songDao.getRecentlyPlayed()
    val downloads: Flow<List<SongEntity>> = songDao.getDownloads()
    val playlists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    suspend fun getSongById(songId: String): SongEntity? {
        return songDao.getSongById(songId)
    }

    suspend fun toggleFavorite(songId: String, isFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            val song = songDao.getSongById(songId)
            if (song != null) {
                songDao.updateSong(song.copy(isFavorite = isFavorite))
            }
        }
    }

    suspend fun recordPlay(songId: String) {
        withContext(Dispatchers.IO) {
            val song = songDao.getSongById(songId)
            if (song != null) {
                songDao.updateSong(
                    song.copy(
                        playCount = song.playCount + 1,
                        lastPlayed = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun createPlaylist(name: String): Long {
        return withContext(Dispatchers.IO) {
            playlistDao.insertPlaylist(PlaylistEntity(name = name))
        }
    }

    suspend fun renamePlaylist(playlistId: Int, name: String) {
        withContext(Dispatchers.IO) {
            playlistDao.updatePlaylist(PlaylistEntity(id = playlistId, name = name))
        }
    }

    suspend fun deletePlaylist(playlistId: Int) {
        withContext(Dispatchers.IO) {
            playlistDao.deletePlaylistSongs(playlistId)
            playlistDao.deletePlaylist(playlistId)
        }
    }

    suspend fun addSongToPlaylist(playlistId: Int, songId: String) {
        withContext(Dispatchers.IO) {
            playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: Int, songId: String) {
        withContext(Dispatchers.IO) {
            playlistDao.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun getSongsInPlaylist(playlistId: Int): Flow<List<SongEntity>> {
        return playlistDao.getSongsInPlaylist(playlistId)
    }

    suspend fun saveSetting(key: String, value: String) {
        withContext(Dispatchers.IO) {
            settingDao.insertSetting(AppSettingEntity(key, value))
        }
    }

    suspend fun getSetting(key: String, defaultValue: String): String {
        return withContext(Dispatchers.IO) {
            settingDao.getSettingValue(key) ?: defaultValue
        }
    }

    /**
     * Scans device storage using MediaStore, and falls back to synthesized tracks
     * if no audio files are found (common on emulators and blank devices).
     */
    suspend fun scanDeviceStorage() {
        withContext(Dispatchers.IO) {
            val songsList = mutableListOf<SongEntity>()

            // 1. Try to scan Android MediaStore
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE
                )

                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    null
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idColumn)
                        val title = cursor.getString(titleColumn) ?: "Unknown Track"
                        val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                        val album = cursor.getString(albumColumn) ?: "Unknown Album"
                        val path = cursor.getString(dataColumn) ?: ""
                        val duration = cursor.getLong(durationColumn)

                        if (File(path).exists()) {
                            songsList.add(
                                SongEntity(
                                    id = id,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    path = path,
                                    duration = duration,
                                    folder = File(path).parentFile?.name ?: "Music"
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicRepository", "Error scanning MediaStore", e)
            }

            // 2. Fallback to Synthesis: Create 3 high-quality synthesized local songs if scan returns nothing
            if (songsList.isEmpty()) {
                val mediaDir = File(context.filesDir, "SyntheticMedia").apply { mkdirs() }

                val lofiFile = DynamicWavGenerator.generateTrack(mediaDir, "chilled_lofi.wav", "lofi")
                val cyberFile = DynamicWavGenerator.generateTrack(mediaDir, "cyberpunk_pulse.wav", "cyber")
                val ambientFile = DynamicWavGenerator.generateTrack(mediaDir, "space_ambient.wav", "ambient")

                songsList.add(
                    SongEntity(
                        id = "synthetic_lofi",
                        title = "Chilled Lofi Coffee",
                        artist = "Sunset Moods",
                        album = "Study Session Vol. 1",
                        path = lofiFile.absolutePath,
                        duration = 15000L, // 15 seconds
                        folder = "Lofi Lounge"
                    )
                )

                songsList.add(
                    SongEntity(
                        id = "synthetic_cyber",
                        title = "Cyberpunk Grid",
                        artist = "Mega Drive Core",
                        album = "Neon Dystopia",
                        path = cyberFile.absolutePath,
                        duration = 15000L,
                        folder = "Synthwave Vault"
                    )
                )

                songsList.add(
                    SongEntity(
                        id = "synthetic_ambient",
                        title = "Deep Space Ambient",
                        artist = "Ethereal Drone",
                        album = "Atmospheres",
                        path = ambientFile.absolutePath,
                        duration = 15000L,
                        folder = "Ambient Dreams"
                    )
                )
            }

            // Write scanning results to database
            if (songsList.isNotEmpty()) {
                // Ensure we don't wipe out user features like favorites, merge scan values
                for (scanned in songsList) {
                    val existing = songDao.getSongById(scanned.id)
                    if (existing == null) {
                        songDao.insertSong(scanned)
                    } else {
                        // Maintain favorites state
                        songDao.insertSong(scanned.copy(
                            isFavorite = existing.isFavorite,
                            playCount = existing.playCount,
                            lastPlayed = existing.lastPlayed,
                            isDownloaded = existing.isDownloaded
                        ))
                    }
                }
            }
        }
    }

    /**
     * Custom utility to download/add simulated song with specific download folder organizing
     */
    suspend fun addSimulatedDownload(title: String, artist: String, album: String, style: String, folderName: String) {
        withContext(Dispatchers.IO) {
            val downloadDir = File(context.filesDir, "Downloads/$folderName").apply { mkdirs() }
            val cleanName = title.lowercase().replace(" ", "_") + ".wav"
            val file = DynamicWavGenerator.generateTrack(downloadDir, cleanName, style)

            val newSong = SongEntity(
                id = "dl_${System.currentTimeMillis()}",
                title = title,
                artist = artist,
                album = album,
                path = file.absolutePath,
                duration = 15000L,
                isDownloaded = true,
                folder = folderName
            )
            songDao.insertSong(newSong)
        }
    }

    /**
     * Imports a direct file (e.g. from editor or trim) as a song
     */
    suspend fun importSong(file: File, title: String, artist: String, album: String, folder: String) {
        withContext(Dispatchers.IO) {
            val id = "imported_${System.currentTimeMillis()}"
            val newSong = SongEntity(
                id = id,
                title = title,
                artist = artist,
                album = album,
                path = file.absolutePath,
                duration = 15000L, // approx/simulated
                folder = folder
            )
            songDao.insertSong(newSong)
        }
    }

    suspend fun addPresetSettingsIfEmpty() {
        withContext(Dispatchers.IO) {
            if (settingDao.getSettingValue("theme_mode") == null) {
                settingDao.insertSetting(AppSettingEntity("theme_mode", "Dark"))
            }
            if (settingDao.getSettingValue("theme_color") == null) {
                settingDao.insertSetting(AppSettingEntity("theme_color", "Cosmic Blue"))
            }
            if (settingDao.getSettingValue("audio_quality") == null) {
                settingDao.insertSetting(AppSettingEntity("audio_quality", "Ultra (320kbps)"))
            }
        }
    }
}
