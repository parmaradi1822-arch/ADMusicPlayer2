package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.AudioPlayerManager
import com.example.data.models.PlaylistEntity
import com.example.data.models.SongEntity
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application)

    // Exposed Flows from DB
    val allSongs: StateFlow<List<SongEntity>> = repository.allSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<SongEntity>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<SongEntity>> = repository.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<SongEntity>> = repository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search query
    val searchQuery = MutableStateFlow("")

    val filteredSongs: StateFlow<List<SongEntity>> = combine(allSongs, searchQuery) { songs, query ->
        if (query.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Playlist Details State
    private val _selectedPlaylistId = MutableStateFlow<Int?>(null)
    val selectedPlaylistId: StateFlow<Int?> = _selectedPlaylistId.asStateFlow()

    val songsInSelectedPlaylist: StateFlow<List<SongEntity>> = _selectedPlaylistId
        .flatMapLatest { playlistId ->
            if (playlistId != null) {
                repository.getSongsInPlaylist(playlistId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Player States mirrored from AudioPlayerManager
    val currentSong = AudioPlayerManager.currentSong
    val isPlaying = AudioPlayerManager.isPlaying
    val currentPosition = AudioPlayerManager.currentPosition
    val duration = AudioPlayerManager.duration
    val playlistQueue = AudioPlayerManager.playlistQueue
    val isShuffle = AudioPlayerManager.isShuffle
    val repeatMode = AudioPlayerManager.repeatMode

    // Equalizer & FX state mirrors
    val bassBoost = AudioPlayerManager.bassBoostLevel
    val trebleBoost = AudioPlayerManager.trebleBoostLevel
    val currentPreset = AudioPlayerManager.currentPreset
    val eqBands = AudioPlayerManager.eqBands

    // Slow and Lofi values
    val slowFactor = AudioPlayerManager.slowFactor
    val pitchFactor = AudioPlayerManager.pitchFactor

    // Sleep Timer countdown
    val sleepTimerRemainingSeconds = AudioPlayerManager.sleepTimerRemainingSeconds

    // App Preferences
    val themeMode = MutableStateFlow("Dark") // Dark, Light
    val themeColor = MutableStateFlow("Cosmic Blue") // Cosmic Blue, Deep Emerald, Cyberpunk Purple, Neon Red
    val audioQuality = MutableStateFlow("Ultra (320kbps)")
    val isAppLockEnabled = MutableStateFlow(false)
    val appLockPin = MutableStateFlow("")
    val isFingerprintEnabled = MutableStateFlow(false)

    // Security Gate State
    val isAppLocked = MutableStateFlow(false)

    // Studio Process/Operation States
    val studioExportSuccessMessage = MutableStateFlow<String?>(null)
    val studioIsLoading = MutableStateFlow(false)

    init {
        // Init player singletons
        AudioPlayerManager.initPlayer(application)

        // Load preferences
        viewModelScope.launch {
            repository.addPresetSettingsIfEmpty()
            themeMode.value = repository.getSetting("theme_mode", "Dark")
            themeColor.value = repository.getSetting("theme_color", "Cosmic Blue")
            audioQuality.value = repository.getSetting("audio_quality", "Ultra (320kbps)")
            
            val pin = repository.getSetting("app_lock_pin", "")
            appLockPin.value = pin
            isAppLockEnabled.value = pin.isNotEmpty()
            isAppLocked.value = pin.isNotEmpty() // Gate screen active on start if PIN set

            isFingerprintEnabled.value = repository.getSetting("fingerprint_enabled", "false").toBoolean()

            // Auto-Scan Storage / synthesis fallback
            repository.scanDeviceStorage()
        }
    }

    // --- Media Core Trigerring ---

    fun playAll(songs: List<SongEntity>, startIndex: Int = 0) {
        AudioPlayerManager.playQueue(getApplication(), songs, startIndex)
    }

    fun playSingle(song: SongEntity) {
        AudioPlayerManager.playSingle(getApplication(), song)
    }

    fun togglePlayPause() {
        AudioPlayerManager.togglePlayPause(getApplication())
    }

    fun next() {
        AudioPlayerManager.next(getApplication())
    }

    fun previous() {
        AudioPlayerManager.previous(getApplication())
    }

    fun seekTo(position: Int) {
        AudioPlayerManager.seekTo(position)
    }

    fun toggleShuffle() {
        AudioPlayerManager.toggleShuffle()
    }

    fun advanceRepeatMode() {
        AudioPlayerManager.advanceRepeatMode()
    }

    // --- Database updates ---

    fun toggleFavorite(songId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(songId, isFavorite)
        }
    }

    fun scanLibrary() {
        viewModelScope.launch {
            repository.scanDeviceStorage()
        }
    }

    fun selectPlaylist(playlistId: Int?) {
        _selectedPlaylistId.value = playlistId
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.createPlaylist(name)
            }
        }
    }

    fun renamePlaylist(playlistId: Int, newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                repository.renamePlaylist(playlistId, newName)
            }
        }
    }

    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
        }
    }

    fun addSongToPlaylist(playlistId: Int, songId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Int, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    // --- Audio FX actions ---

    fun setBassBoost(level: Float) {
        AudioPlayerManager.setBassBoost(level)
    }

    fun setTrebleBoost(level: Float) {
        AudioPlayerManager.setTrebleBoost(level)
    }

    fun setEQBand(index: Int, level: Float) {
        AudioPlayerManager.setEQBand(index, level)
    }

    fun applyPreset(presetName: String) {
        AudioPlayerManager.applyPreset(presetName)
    }

    // --- Slow / Lofi speeds ---

    fun setTempoFactor(factor: Float) {
        AudioPlayerManager.updateSlowFactor(factor)
    }

    fun setPitchFactor(factor: Float) {
        AudioPlayerManager.updatePitchFactor(factor)
    }

    // --- Sleep Timer ---

    fun startSleepTimer(minutes: Int) {
        AudioPlayerManager.startSleepTimer(minutes)
    }

    fun stopSleepTimer() {
        AudioPlayerManager.stopSleepTimer()
    }

    // --- App Locks ---

    fun updatePin(pin: String) {
        viewModelScope.launch {
            repository.saveSetting("app_lock_pin", pin)
            appLockPin.value = pin
            isAppLockEnabled.value = pin.isNotEmpty()
        }
    }

    fun toggleFingerprint(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("fingerprint_enabled", enabled.toString())
            isFingerprintEnabled.value = enabled
        }
    }

    fun tryUnlock(pin: String): Boolean {
        if (pin == appLockPin.value || (pin == "1234" && appLockPin.value.isEmpty())) {
            isAppLocked.value = false
            return true
        }
        return false
    }

    fun forceLock() {
        if (isAppLockEnabled.value) {
            isAppLocked.value = true
        }
    }

    // --- Settings Changes ---

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            repository.saveSetting("theme_mode", mode)
            themeMode.value = mode
        }
    }

    fun updateThemeColor(colorName: String) {
        viewModelScope.launch {
            repository.saveSetting("theme_color", colorName)
            themeColor.value = colorName
        }
    }

    fun updateAudioQuality(qualitySpec: String) {
        viewModelScope.launch {
            repository.saveSetting("audio_quality", qualitySpec)
            audioQuality.value = qualitySpec
        }
    }

    // --- Simulated Downloads Section ---

    fun downloadSong(title: String, artist: String, album: String, style: String, folderName: String) {
        viewModelScope.launch {
            repository.addSimulatedDownload(title, artist, album, style, folderName)
        }
    }

    // --- Studio Operations (Tricks, merges, and slow edits) ---

    fun processTrim(sourceSong: SongEntity, startMs: Long, endMs: Long, fadeIn: Boolean, fadeOut: Boolean, title: String) {
        viewModelScope.launch {
            studioIsLoading.value = true
            val file = AudioPlayerManager.trimAudio(getApplication(), sourceSong, startMs, endMs, fadeIn, fadeOut, title)
            studioIsLoading.value = false
            if (file != null) {
                studioExportSuccessMessage.value = "Successfully trimmed: ${file.name} exported safely!"
            } else {
                studioExportSuccessMessage.value = "Failed to trim audio."
            }
        }
    }

    fun processMerge(songList: List<SongEntity>, title: String) {
        viewModelScope.launch {
            studioIsLoading.value = true
            val file = AudioPlayerManager.mergeAudio(getApplication(), songList, title)
            studioIsLoading.value = false
            if (file != null) {
                studioExportSuccessMessage.value = "Merged ${songList.size} files into ${file.name}!"
            } else {
                studioExportSuccessMessage.value = "Failed to merge audio files."
            }
        }
    }

    fun processLofiCreator(sourceSong: SongEntity, speed: Float, pitch: Float, bass: Float, noise: Float, lpf: Float, title: String) {
        viewModelScope.launch {
            studioIsLoading.value = true
            val file = AudioPlayerManager.createLofiMix(getApplication(), sourceSong, speed, pitch, bass, noise, lpf, title)
            studioIsLoading.value = false
            if (file != null) {
                studioExportSuccessMessage.value = "Slow+Reverb lofi mix exported: ${file.name} (WAV)!"
            } else {
                studioExportSuccessMessage.value = "Failed to export lofi mix."
            }
        }
    }

    fun clearStudioMessage() {
        studioExportSuccessMessage.value = null
    }
}
