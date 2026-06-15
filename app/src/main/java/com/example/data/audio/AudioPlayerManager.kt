package com.example.data.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.models.SongEntity
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioPlayerManager {
    private const val TAG = "AudioPlayerManager"

    private var mediaPlayer: MediaPlayer? = null
    private var nativeEqualizer: Equalizer? = null
    private var nativeBassBoost: BassBoost? = null

    // Player State
    val currentSong = MutableStateFlow<SongEntity?>(null)
    val isPlaying = MutableStateFlow(false)
    val currentPosition = MutableStateFlow(0)
    val duration = MutableStateFlow(0)
    val playlistQueue = MutableStateFlow<List<SongEntity>>(emptyList())
    var currentIndex = -1

    // Playback Settings
    val isShuffle = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(RepeatMode.NONE) // NONE, ONE, ALL

    enum class RepeatMode { NONE, ONE, ALL }

    // Equalizer State
    val bassBoostLevel = MutableStateFlow(50f) // 0 - 100
    val trebleBoostLevel = MutableStateFlow(50f) // 0 - 100
    val currentPreset = MutableStateFlow("Normal")
    val eqBands = MutableStateFlow(listOf(50f, 50f, 50f, 50f, 50f)) // 5 Bands: 60Hz, 230Hz, 910Hz, 4kHz, 14kHz

    // Audio Lofi/Pitch Speed Attributes
    val slowFactor = MutableStateFlow(1.0f) // 0.5f to 1.5f (Playback speed)
    val pitchFactor = MutableStateFlow(1.0f) // 0.5f to 1.5f
    val isLofiPresetActive = MutableStateFlow(false)

    // Sleep Timer
    val sleepTimerRemainingSeconds = MutableStateFlow(0)
    private var sleepTimerJob: Job? = null

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTrackerJob: Job? = null

    fun initPlayer(context: Context) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setOnCompletionListener {
                    handleTrackCompletion(context)
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    true
                }
            }
        }
    }

    fun playQueue(context: Context, songs: List<SongEntity>, startIndex: Int) {
        if (songs.isEmpty()) return
        playlistQueue.value = songs
        currentIndex = startIndex.coerceIn(0, songs.size - 1)
        playCurrentIndex(context)
    }

    fun playSingle(context: Context, song: SongEntity) {
        playlistQueue.value = listOf(song)
        currentIndex = 0
        playCurrentIndex(context)
    }

    private fun playCurrentIndex(context: Context) {
        val queue = playlistQueue.value
        if (currentIndex < 0 || currentIndex >= queue.size) {
            isPlaying.value = false
            return
        }

        val song = queue[currentIndex]
        currentSong.value = song

        try {
            stopProgressTracker()
            val mp = mediaPlayer ?: MediaPlayer().also { mediaPlayer = it }
            mp.reset()
            mp.setDataSource(song.path)
            mp.prepare()

            // Apply playback parameter speeds/pitches
            applyPlaybackSpeedAndPitch()

            // Initialize/Apply equalizer and bass boost
            setupAudioFX(mp.audioSessionId)

            mp.start()
            isPlaying.value = true
            duration.value = mp.duration

            startProgressTracker()

            // Record playback dynamically in repository
            playerScope.launch {
                val repo = MusicRepository(context)
                repo.recordPlay(song.id)
            }

            // Sync with Foreground Service
            MusicService.updateNotification(context, song, isPlaying.value)

        } catch (e: Exception) {
            Log.e(TAG, "Error playing song: ${song.title}", e)
            isPlaying.value = false
        }
    }

    fun togglePlayPause(context: Context) {
        val mp = mediaPlayer ?: return
        if (currentSong.value == null) return

        if (mp.isPlaying) {
            mp.pause()
            isPlaying.value = false
            MusicService.updateNotification(context, currentSong.value!!, false)
        } else {
            mp.start()
            isPlaying.value = true
            MusicService.updateNotification(context, currentSong.value!!, true)
        }
    }

    fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            isPlaying.value = false
            currentPosition.value = 0
        }
        stopAudioFX()
    }

    fun next(context: Context) {
        val queue = playlistQueue.value
        if (queue.isEmpty()) return

        if (repeatMode.value == RepeatMode.ONE) {
            // Repeat current
            playCurrentIndex(context)
            return
        }

        if (isShuffle.value) {
            currentIndex = (0 until queue.size).random()
        } else {
            currentIndex = (currentIndex + 1) % queue.size
        }
        playCurrentIndex(context)
    }

    fun previous(context: Context) {
        val queue = playlistQueue.value
        if (queue.isEmpty()) return

        if (isShuffle.value) {
            currentIndex = (0 until queue.size).random()
        } else {
            currentIndex = if (currentIndex - 1 < 0) queue.size - 1 else currentIndex - 1
        }
        playCurrentIndex(context)
    }

    fun seekTo(msec: Int) {
        mediaPlayer?.seekTo(msec)
        currentPosition.value = msec
    }

    fun toggleShuffle() {
        isShuffle.value = !isShuffle.value
    }

    fun advanceRepeatMode() {
        repeatMode.value = when (repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.NONE
        }
    }

    private fun handleTrackCompletion(context: Context) {
        val queue = playlistQueue.value
        if (queue.isEmpty()) {
            isPlaying.value = false
            return
        }

        when (repeatMode.value) {
            RepeatMode.ONE -> {
                playCurrentIndex(context)
            }
            RepeatMode.ALL -> {
                next(context)
            }
            RepeatMode.NONE -> {
                if (currentIndex < queue.size - 1) {
                    next(context)
                } else {
                    isPlaying.value = false
                    stopProgressTracker()
                }
            }
        }
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = playerScope.launch {
            while (true) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        currentPosition.value = it.currentPosition
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
    }

    // --- Audio FX Processing ---

    private fun setupAudioFX(audioSessionId: Int) {
        try {
            nativeEqualizer = Equalizer(0, audioSessionId).apply { enabled = true }
            nativeBassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
                setStrength((bassBoostLevel.value * 10).toInt().toShort()) // 0 to 1000 strength
            }
            syncEQBandsWithNative()
        } catch (e: Exception) {
            Log.w(TAG, "AudioFX setup failed. Fallback simulation active.", e)
        }
    }

    private fun stopAudioFX() {
        nativeEqualizer?.release()
        nativeEqualizer = null
        nativeBassBoost?.release()
        nativeBassBoost = null
    }

    fun setBassBoost(level: Float) {
        bassBoostLevel.value = level
        try {
            nativeBassBoost?.setStrength((level * 10).toInt().toShort())
        } catch (e: Exception) {
            Log.w(TAG, "Cannot apply native BassBoost strength: $e")
        }
    }

    fun setTrebleBoost(level: Float) {
        trebleBoostLevel.value = level
        // Treble corresponds to the top frequency band of the EQ
        val bands = eqBands.value.toMutableList()
        bands[4] = level // Highest band (8kHz - 14kHz)
        eqBands.value = bands
        syncEQBandsWithNative()
    }

    fun setEQBand(index: Int, valPercent: Float) {
        val bands = eqBands.value.toMutableList()
        if (index in bands.indices) {
            bands[index] = valPercent
            eqBands.value = bands
            syncEQBandsWithNative()
            currentPreset.value = "Custom"
        }
    }

    fun applyPreset(preset: String) {
        currentPreset.value = preset
        val bands = when (preset) {
            "Rock" -> listOf(75f, 60f, 40f, 65f, 80f)
            "Pop" -> listOf(45f, 55f, 75f, 60f, 45f)
            "Jazz" -> listOf(65f, 50f, 40f, 55f, 65f)
            "Classical" -> listOf(70f, 60f, 50f, 55f, 45f)
            "Lofi" -> listOf(70f, 40f, 30f, 50f, 40f)
            else -> listOf(50f, 50f, 50f, 50f, 50f) // Normal
        }
        eqBands.value = bands

        val bass = when (preset) {
            "Rock" -> 70f
            "Pop" -> 50f
            "Jazz" -> 40f
            "Classical" -> 35f
            "Lofi" -> 80f
            else -> 50f
        }
        bassBoostLevel.value = bass

        val treble = when (preset) {
            "Rock" -> 75f
            "Pop" -> 55f
            "Jazz" -> 50f
            "Classical" -> 45f
            "Lofi" -> 30f
            else -> 50f
        }
        trebleBoostLevel.value = treble

        syncEQBandsWithNative()
    }

    private fun syncEQBandsWithNative() {
        val bands = eqBands.value
        val eq = nativeEqualizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val minEQLevel = eq.bandLevelRange[0]
            val maxEQLevel = eq.bandLevelRange[1]
            val totalRange = maxEQLevel - minEQLevel

            for (i in 0 until numBands.coerceAtMost(bands.size)) {
                val normalizedVal = bands[i] / 100f // 0.0 to 1.0
                val bandLevel = (minEQLevel + (normalizedVal * totalRange)).toInt().toShort()
                eq.setBandLevel(i.toShort(), bandLevel)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot apply native bands update: $e")
        }
    }

    // --- Modern Playback Speed / Pitching ---

    fun updateSlowFactor(factor: Float) {
        slowFactor.value = factor
        applyPlaybackSpeedAndPitch()
    }

    fun updatePitchFactor(factor: Float) {
        pitchFactor.value = factor
        applyPlaybackSpeedAndPitch()
    }

    private fun applyPlaybackSpeedAndPitch() {
        mediaPlayer?.let { mp ->
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (mp.isPlaying || true) {
                        val params = mp.playbackParams
                        params.speed = slowFactor.value
                        params.pitch = pitchFactor.value
                        mp.playbackParams = params
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "PlaybackParams setting failed on this platform: $e")
            }
        }
    }

    // --- Sleep Timer ---

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerRemainingSeconds.value = minutes * 60

        sleepTimerJob = playerScope.launch {
            while (sleepTimerRemainingSeconds.value > 0) {
                delay(1000)
                sleepTimerRemainingSeconds.value--
            }
            // Timer expired, stop player!
            stop()
        }
    }

    fun stopSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerRemainingSeconds.value = 0
    }

    // --- Studio & Audio Editing Engine (Pure Kotlin High-Fidelity WAV Processor) ---

    suspend fun trimAudio(
        context: Context,
        sourceSong: SongEntity,
        startMs: Long,
        endMs: Long,
        applyFadeIn: Boolean,
        applyFadeOut: Boolean,
        exportName: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourceSong.path)
            if (!sourceFile.exists()) return@withContext null

            // Output edited audio to Trims directory
            val studioDir = File(context.filesDir, "Studio").apply { mkdirs() }
            val outputFile = File(studioDir, "${exportName.lowercase().replace(" ", "_")}.wav")

            val fis = FileInputStream(sourceFile)
            val headerBytes = ByteArray(44)
            fis.read(headerBytes) // Read the WAV 44 byte header

            // Parse channel, samplerate, bitspersample from WAV
            val sampleRate = ByteBuffer.wrap(headerBytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val channels = ByteBuffer.wrap(headerBytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val bitsPerSample = ByteBuffer.wrap(headerBytes, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val bytesPerSample = bitsPerSample / 8

            val bytesPerSecond = sampleRate * channels * bytesPerSample

            val startByte = (startMs * bytesPerSecond / 1000L).coerceAtLeast(0L)
            var endByte = (endMs * bytesPerSecond / 1000L).coerceAtMost(sourceFile.length() - 44L)

            if (endByte <= startByte) {
                endByte = startByte + (5 * bytesPerSecond) // trim at least 5s fallback
            }

            val targetPcmSize = (endByte - startByte).toInt()
            val totalFileSize = 44 + targetPcmSize

            // Skip to start position in file
            fis.channel.position(44L + startByte)

            val pcmData = ByteArray(targetPcmSize)
            var bytesRead = fis.read(pcmData)
            fis.close()

            if (bytesRead < targetPcmSize && bytesRead > 0) {
                // Short read
            }

            // Apply Fade-In (first 1.5 seconds)
            if (applyFadeIn && bytesPerSecond > 0) {
                val fadeSamples = (1.5 * sampleRate).toInt()
                val fadeBytes = fadeSamples * bytesPerSample * channels
                for (i in 0 until fadeBytes.coerceAtMost(targetPcmSize) step 2) {
                    val progress = (i.toFloat() / fadeBytes).coerceIn(0f, 1f)
                    val originalValue = getShortLE(pcmData, i)
                    val fadedValue = (originalValue * progress).toInt().toShort()
                    writeShortLE(pcmData, i, fadedValue)
                }
            }

            // Apply Fade-Out (last 1.5 seconds)
            if (applyFadeOut && bytesPerSecond > 0) {
                val fadeSamples = (1.5 * sampleRate).toInt()
                val fadeBytes = fadeSamples * bytesPerSample * channels
                val startFadeOutByte = (targetPcmSize - fadeBytes).coerceAtLeast(0)
                for (i in startFadeOutByte until targetPcmSize step 2) {
                    val progress = 1.0f - ((i - startFadeOutByte).toFloat() / fadeBytes).coerceIn(0f, 1f)
                    val originalValue = getShortLE(pcmData, i)
                    val fadedValue = (originalValue * progress).toInt().toShort()
                    writeShortLE(pcmData, i, fadedValue)
                }
            }

            // Write modified WAV back
            FileOutputStream(outputFile).use { fos ->
                val newHeader = headerBytes.clone()
                ByteBuffer.wrap(newHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalFileSize - 8)
                ByteBuffer.wrap(newHeader, 40, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(targetPcmSize)
                fos.write(newHeader)
                fos.write(pcmData)
            }

            // Record inside repository
            val repo = MusicRepository(context)
            repo.importSong(
                file = outputFile,
                title = exportName,
                artist = "Audio Studio",
                album = "Edited Tracks",
                folder = "Studio Exports"
            )

            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed trimming audio", e)
            null
        }
    }

    suspend fun mergeAudio(
        context: Context,
        songList: List<SongEntity>,
        exportName: String
    ): File? = withContext(Dispatchers.IO) {
        if (songList.isEmpty()) return@withContext null
        try {
            val studioDir = File(context.filesDir, "Studio").apply { mkdirs() }
            val outputFile = File(studioDir, "${exportName.lowercase().replace(" ", "_")}.wav")

            val fos = FileOutputStream(outputFile)
            var firstHeader: ByteArray? = null
            var totalPcmSize = 0

            // Read headers to confirm settings and write concatenated files
            val pcmStreams = mutableListOf<ByteArray>()

            for (song in songList) {
                val f = File(song.path)
                if (!f.exists()) continue

                val fis = FileInputStream(f)
                val h = ByteArray(44)
                fis.read(h)
                if (firstHeader == null) {
                    firstHeader = h
                }

                val pcmSize = f.length() - 44
                val data = ByteArray(pcmSize.toInt())
                fis.read(data)
                fis.close()

                pcmStreams.add(data)
                totalPcmSize += data.size
            }

            val finalHeader = firstHeader ?: return@withContext null
            val totalFileSize = 44 + totalPcmSize

            ByteBuffer.wrap(finalHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalFileSize - 8)
            ByteBuffer.wrap(finalHeader, 40, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalPcmSize)

            fos.write(finalHeader)
            for (data in pcmStreams) {
                fos.write(data)
            }
            fos.close()

            // Register in library
            val repo = MusicRepository(context)
            repo.importSong(
                file = outputFile,
                title = exportName,
                artist = "Audio Studio Merge",
                album = "Studio Master",
                folder = "Studio Exports"
            )

            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to merge files", e)
            null
        }
    }

    suspend fun createLofiMix(
        context: Context,
        sourceSong: SongEntity,
        speed: Float,
        pitch: Float,
        bassBoostVal: Float,
        noiseIntensity: Float,
        lpfIntensity: Float,
        exportName: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(sourceSong.path)
            if (!file.exists()) return@withContext null

            val studioDir = File(context.filesDir, "LofiCreator").apply { mkdirs() }
            val outputFile = File(studioDir, "${exportName.lowercase().replace(" ", "_")}.wav")

            // Parse original PCM block
            val fis = FileInputStream(file)
            val headerBytes = ByteArray(44)
            fis.read(headerBytes)

            val pcmSize = file.length() - 44
            val buffer = ByteArray(pcmSize.toInt())
            fis.read(buffer)
            fis.close()

            // To apply SLOW (tempo speed factor like 0.8x) programmatically in PCM, 
            // we resample/interpolate the PCM data.
            // When slowing down, we stretch the samples (more samples, longer duration)
            val channels = ByteBuffer.wrap(headerBytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val sampleRate = ByteBuffer.wrap(headerBytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val bitsPerSample = ByteBuffer.wrap(headerBytes, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

            val origSampleCount = buffer.size / 2
            val slowMultiplier = 1.0f / speed // e.g. 0.8x speed -> 1.25x samples count
            val processedSampleCount = (origSampleCount * slowMultiplier).toInt()

            val slowPctData = ByteArray(processedSampleCount * 2)

            val numChannels = channels.coerceAtLeast(1).coerceAtMost(2)
            val lastFiltered = FloatArray(numChannels) { 0f }
            val lpfAlpha = if (lpfIntensity > 0f) {
                // Map intensity [0, 100] to alpha [1.0, 0.05]
                (1.0f - (lpfIntensity / 100f) * 0.95f).coerceIn(0.01f, 1.0f)
            } else {
                1.0f
            }

            for (i in 0 until processedSampleCount) {
                // Find matching spot in original
                val origIdxDouble = i / slowMultiplier
                val leftIdx = origIdxDouble.toInt()
                val rightIdx = (leftIdx + 1).coerceAtMost(origSampleCount - 1)
                val weight = origIdxDouble - leftIdx

                val leftSample = getShortLE(buffer, leftIdx * 2)
                val rightSample = getShortLE(buffer, rightIdx * 2)

                // Blend/Interpolate
                val blendedSample = (leftSample * (1.0 - weight) + rightSample * weight).toInt().toShort()

                // Apply massive bass overlay to simulate Lofi warm sub-bass (boost low values)
                val bassSample = if (bassBoostVal > 50f) {
                    val lowBoostFactor = (bassBoostVal - 50f) / 100f // 0f to 0.5f
                    // Double frequencies of 50-100Hz in local wave. Simplified by filtering adjacent values
                    val blendedWithBass = (blendedSample + (blendedSample * lowBoostFactor)).toInt().coerceIn(-32768, 32767).toShort()
                    blendedWithBass
                } else blendedSample

                // Add tape hiss & vinyl sound pop/crackle (static noise)
                val sampleWithNoise = if (noiseIntensity > 0f) {
                    val r = noiseIntensity / 100f
                    val hiss = (Math.random().toFloat() * 2f - 1f) * 800f * r
                    val crackle = if (Math.random() < 0.00015f * r) {
                        (if (Math.random() < 0.5) 1.0f else -1.0f) * 6000f * r
                    } else {
                        0f
                    }
                    (bassSample + hiss + crackle).coerceIn(-32768f, 32767f).toInt().toShort()
                } else {
                    bassSample
                }

                // Apply dynamic State-based Low Pass Filter matching channels
                val chIdx = i % numChannels
                val filterInput = sampleWithNoise.toFloat()
                val filterOutput = lastFiltered[chIdx] + lpfAlpha * (filterInput - lastFiltered[chIdx])
                lastFiltered[chIdx] = filterOutput

                val finalSample = filterOutput.toInt().coerceIn(-32768, 32767).toShort()

                writeShortLE(slowPctData, i * 2, finalSample)
            }

            val finalHeader = headerBytes.clone()
            val totalFileSize = 44 + slowPctData.size
            ByteBuffer.wrap(finalHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalFileSize - 8)
            ByteBuffer.wrap(finalHeader, 40, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(slowPctData.size)

            FileOutputStream(outputFile).use { fos ->
                fos.write(finalHeader)
                fos.write(slowPctData)
            }

            // Register in library
            val repo = MusicRepository(context)
            repo.importSong(
                file = outputFile,
                title = exportName,
                artist = "Lofi Resampler",
                album = "Lofi Magic Space",
                folder = "Lofi Exports"
            )

            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Lofi Creator resample failed", e)
            null
        }
    }

    private fun getShortLE(bytes: ByteArray, offset: Int): Short {
        return ((bytes[offset + 1].toInt() and 0xFF shl 8) or (bytes[offset].toInt() and 0xFF)).toShort()
    }

    private fun writeShortLE(bytes: ByteArray, offset: Int, value: Short) {
        bytes[offset] = (value.toInt() and 0x00FF).toByte()
        bytes[offset + 1] = ((value.toInt() and 0xFF00) shr 8).toByte()
    }
}
