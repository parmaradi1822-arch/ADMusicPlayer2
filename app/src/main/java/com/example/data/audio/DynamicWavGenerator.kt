package com.example.data.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object DynamicWavGenerator {

    /**
     * Generates a synthesized WAV track and returns the File object.
     */
    fun generateTrack(parentDir: File, filename: String, style: String): File {
        val file = File(parentDir, filename)
        if (file.exists() && file.length() > 0) {
            return file // Already generated
        }

        val sampleRate = 22050
        val durationSeconds = 15 // 15 seconds is short enough to generate fast and long enough to demo
        val numSamples = sampleRate * durationSeconds
        val dataSize = numSamples * 2 // 16-bit mono = 2 bytes per sample
        val totalFileSize = 44 + dataSize

        FileOutputStream(file).use { out ->
            // Write WAV Header
            out.write("RIFF".toByteArray())
            out.write(intToByteArray(totalFileSize - 8))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intToByteArray(16)) // Subchunk1Size
            out.write(shortToByteArray(1)) // AudioFormat: 1 = PCM
            out.write(shortToByteArray(1)) // NumChannels: 1 = Mono
            out.write(intToByteArray(sampleRate))
            out.write(intToByteArray(sampleRate * 2)) // ByteRate
            out.write(shortToByteArray(2)) // BlockAlign: Mono 16-bit = 2 bytes
            out.write(shortToByteArray(16)) // BitsPerSample
            out.write("data".toByteArray())
            out.write(intToByteArray(dataSize))

            // Write PCM audio data depending on style
            val buffer = ByteArray(4096)
            var bufferIndex = 0

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val sampleValue = when (style) {
                    "lofi" -> generateLofiSample(t)
                    "cyber" -> generateCyberSample(t)
                    else -> generateAmbientSample(t)
                }

                // Clip and scale to 16-bit signed integer
                val shortVal = (sampleValue * 32767).toInt().coerceIn(-32768, 32767).toShort()

                buffer[bufferIndex++] = (shortVal.toInt() and 0x00FF).toByte()
                buffer[bufferIndex++] = ((shortVal.toInt() and 0xFF00) shr 8).toByte()

                if (bufferIndex >= buffer.size) {
                    out.write(buffer, 0, bufferIndex)
                    bufferIndex = 0
                }
            }

            if (bufferIndex > 0) {
                out.write(buffer, 0, bufferIndex)
            }
        }

        return file
    }

    private fun generateLofiSample(t: Double): Double {
        // Soft slow arpeggio + mechanical click (viny/noise) + soft bass
        val tempo = 75.0 // BPM
        val beatDuration = 60.0 / tempo
        val currentBeat = (t / beatDuration).toInt()
        val beatProgress = (t % beatDuration) / beatDuration

        // Notes in cord Am, F, C, G
        val chords = listOf(
            listOf(220.0, 261.63, 329.63), // Am
            listOf(174.61, 220.0, 261.63), // F
            listOf(261.63, 329.63, 392.00), // C
            listOf(196.00, 246.94, 293.66)  // G
        )
        val chordIndex = (currentBeat / 4) % chords.size
        val chord = chords[chordIndex]

        // Elegant melody note (8th notes pattern)
        val subBeat = (beatProgress * 8).toInt()
        val noteFreq = when (subBeat) {
            0 -> chord[0]
            2 -> chord[1]
            4 -> chord[2]
            6 -> chord[1] * 1.5 // fifth higher/shifter
            else -> 0.0
        }

        var audio = 0.0
        // Arpeggiated melody
        if (noteFreq > 0.0) {
            val decay = (1.0 - (beatProgress * 8 % 1.0)).coerceIn(0.0, 1.0)
            audio += sin(2.0 * Math.PI * noteFreq * t) * 0.3 * decay
        }

        // Bass root notes
        val bassFreq = chord[0] / 2.0
        audio += sin(2.0 * Math.PI * bassFreq * t) * 0.4

        // Add lofi rain/crackle noise
        val noise = (Math.random() * 2.0 - 1.0) * 0.06
        // Soften and mix
        return (audio * 0.7 + noise) * 0.6
    }

    private fun generateCyberSample(t: Double): Double {
        // Fast electronic tempo 120BPM, saw wave bass and pulse
        val tempo = 120.0
        val beatDuration = 60.0 / tempo
        val currentBeat = (t / beatDuration).toInt()
        val beatProgress = (t % beatDuration) / beatDuration

        // Bassline Notes
        val bassNotes = listOf(110.0, 110.0, 130.81, 146.83, 98.0, 98.0, 110.0, 123.47)
        val bassFreq = bassNotes[(currentBeat) % bassNotes.size]

        // Synthesize triangular/saw wave for aggressive futuristic theme
        val bassWave = 4.0 * Math.abs((t * bassFreq) % 1.0 - 0.5) - 1.0

        // High frequency synth blip
        val leadNotes = listOf(220.0, 330.0, 440.0, 550.0)
        val leadFreq = leadNotes[(currentBeat * 4 + (beatProgress * 4).toInt()) % leadNotes.size]
        val leadWave = sin(2.0 * Math.PI * leadFreq * t)

        val leadDecay = 1.0 - (beatProgress * 4 % 1.0)
        return (bassWave * 0.4 + leadWave * 0.25 * leadDecay) * 0.7
    }

    private fun generateAmbientSample(t: Double): Double {
        // Swelling space drones, multiple overlapping sine waves (frequencies changing slowly)
        val swellOne = sin(2.0 * Math.PI * (164.81 + 1.5 * sin(2.0 * Math.PI * 0.05 * t)) * t) // E3 modulated
        val swellTwo = sin(2.0 * Math.PI * (220.0 + 0.8 * sin(2.0 * Math.PI * 0.08 * t)) * t) // A3 modulated
        val swellThree = sin(2.0 * Math.PI * 329.63 * t) // E4

        // Low cosmic hum
        val hum = sin(2.0 * Math.PI * 55.0 * t)

        val masterVolume = (0.6 + 0.4 * sin(2.0 * Math.PI * 0.1 * t)) // slow amplitude sweep
        return (swellOne * 0.25 + swellTwo * 0.2 + swellThree * 0.15 + hum * 0.4) * masterVolume * 0.6
    }

    private fun intToByteArray(value: Int): ByteArray {
        val buffer = ByteBuffer.allocate(4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(value)
        return buffer.array()
    }

    private fun shortToByteArray(value: Short): ByteArray {
        val buffer = ByteBuffer.allocate(2)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(value)
        return buffer.array()
    }
}
