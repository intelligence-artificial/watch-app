package com.tamagotchi.pet

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records voice notes as WAV files using AudioRecord (low-level API).
 * Uses AudioSource.VOICE_COMMUNICATION so SpeechRecognizer (VOICE_RECOGNITION)
 * can run concurrently on the same mic.
 *
 * WAV format: 16kHz mono 16-bit PCM
 */
class AudioRecorderService(private val context: Context, private val outputDir: File) {

  companion object {
    private const val TAG = "AudioRecorder"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
  }

  private var audioRecord: AudioRecord? = null
  private var recordingThread: Thread? = null
  private var currentFile: File? = null
  private var startTime: Long = 0L
  @Volatile private var isCurrentlyRecording = false
  @Volatile private var lastAmplitude = 0

  val isRecording: Boolean get() = isCurrentlyRecording

  /** Returns current max amplitude (0–32767). */
  fun getAmplitude(): Int = lastAmplitude

  @SuppressLint("MissingPermission")
  fun startRecording(): File? {
    if (isCurrentlyRecording) {
      Log.w(TAG, "startRecording called but already recording")
      return null
    }

    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    // Keep .m4a extension for compatibility with phone app
    val file = File(outputDir, "voice_${timestamp}.m4a")
    currentFile = file

    Log.d(TAG, "Starting AudioRecord to: ${file.absolutePath}")

    val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
      Log.e(TAG, "Invalid buffer size: $bufferSize")
      return null
    }

    try {
      // Use VOICE_COMMUNICATION source — allows SpeechRecognizer (VOICE_RECOGNITION)
      // to run concurrently on the same mic
      audioRecord = AudioRecord(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        bufferSize * 2
      )

      if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
        Log.e(TAG, "AudioRecord failed to initialize")
        audioRecord?.release()
        audioRecord = null
        return null
      }

      audioRecord?.startRecording()
      isCurrentlyRecording = true
      startTime = System.currentTimeMillis()

      // Write in background thread
      recordingThread = Thread {
        writeWavFile(file, bufferSize)
      }.apply {
        name = "AudioRecordThread"
        start()
      }

      Log.d(TAG, "Recording started (AudioRecord/VOICE_COMMUNICATION)")
      return file
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start recording: ${e.message}", e)
      audioRecord?.release()
      audioRecord = null
      isCurrentlyRecording = false
      return null
    }
  }

  private fun writeWavFile(file: File, bufferSize: Int) {
    try {
      FileOutputStream(file).use { fos ->
        // Write placeholder WAV header (will be updated when recording stops)
        val header = ByteArray(44)
        fos.write(header)

        val buffer = ShortArray(bufferSize / 2)
        var totalDataBytes = 0

        while (isCurrentlyRecording) {
          val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
          if (read > 0) {
            // Track amplitude for UI visualization
            var max = 0
            for (i in 0 until read) {
              val abs = Math.abs(buffer[i].toInt())
              if (abs > max) max = abs
            }
            lastAmplitude = max

            // Convert shorts to bytes (little-endian)
            val byteBuffer = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until read) byteBuffer.putShort(buffer[i])
            val bytes = byteBuffer.array()
            fos.write(bytes, 0, read * 2)
            totalDataBytes += read * 2
          }
        }

        fos.flush()

        // Update WAV header with correct sizes
        updateWavHeader(file, totalDataBytes)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Write error: ${e.message}", e)
    }
  }

  private fun updateWavHeader(file: File, dataSize: Int) {
    try {
      RandomAccessFile(file, "rw").use { raf ->
        val totalSize = 36 + dataSize
        val byteRate = SAMPLE_RATE * 1 * 16 / 8
        val blockAlign = 1 * 16 / 8

        raf.seek(0)
        raf.write("RIFF".toByteArray())
        raf.write(intToLeBytes(totalSize))
        raf.write("WAVE".toByteArray())
        raf.write("fmt ".toByteArray())
        raf.write(intToLeBytes(16))        // subchunk size
        raf.write(shortToLeBytes(1))       // PCM format
        raf.write(shortToLeBytes(1))       // mono
        raf.write(intToLeBytes(SAMPLE_RATE))
        raf.write(intToLeBytes(byteRate))
        raf.write(shortToLeBytes(blockAlign))
        raf.write(shortToLeBytes(16))      // bits per sample
        raf.write("data".toByteArray())
        raf.write(intToLeBytes(dataSize))
      }
      Log.d(TAG, "WAV header written: ${file.length()} bytes")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to update WAV header: ${e.message}", e)
    }
  }

  private fun intToLeBytes(value: Int): ByteArray =
    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

  private fun shortToLeBytes(value: Int): ByteArray =
    ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()

  fun stopRecording(): RecordingInfo? {
    if (!isCurrentlyRecording) {
      Log.w(TAG, "stopRecording called but not recording")
      return null
    }

    val duration = System.currentTimeMillis() - startTime
    val file = currentFile

    isCurrentlyRecording = false

    try {
      recordingThread?.join(3000)
      audioRecord?.stop()
      audioRecord?.release()
      Log.d(TAG, "Recording stopped: ${file?.name}, duration: ${duration}ms")
    } catch (e: Exception) {
      Log.e(TAG, "Stop error: ${e.message}", e)
    } finally {
      audioRecord = null
      recordingThread = null
      lastAmplitude = 0
    }

    return if (file != null && file.exists() && file.length() > 44) {
      Log.d(TAG, "Recording valid: ${file.name} (${file.length()} bytes)")
      RecordingInfo(file = file, timestamp = System.currentTimeMillis(), durationMs = duration)
    } else {
      Log.w(TAG, "Recording invalid: exists=${file?.exists()}, size=${file?.length()}")
      null
    }
  }

  fun release() {
    isCurrentlyRecording = false
    try {
      recordingThread?.join(1000)
      audioRecord?.stop()
      audioRecord?.release()
    } catch (_: Exception) {}
    audioRecord = null
    recordingThread = null
  }
}

data class RecordingInfo(
  val file: File,
  val timestamp: Long,
  val durationMs: Long
) {
  val displayName: String
    get() = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(timestamp))

  val durationDisplay: String
    get() {
      val seconds = (durationMs / 1000) % 60
      val minutes = (durationMs / 1000) / 60
      return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
