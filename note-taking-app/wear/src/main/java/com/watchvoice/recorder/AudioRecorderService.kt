package com.watchvoice.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorderService(private val context: Context, private val outputDir: File) {

    companion object {
        private const val TAG = "AudioRecorder"
    }

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTime: Long = 0L

    val isRecording: Boolean get() = recorder != null

    /** Returns current max amplitude (0–32767). Resets on each call. */
    fun getAmplitude(): Int = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }

    fun startRecording(): File? {
        if (isRecording) {
            Log.w(TAG, "startRecording called but already recording")
            return null
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(outputDir, "voice_${timestamp}.m4a")
        currentFile = file

        Log.d(TAG, "Attempting to start recording to: ${file.absolutePath}")
        Log.d(TAG, "Output dir exists: ${outputDir.exists()}, writable: ${outputDir.canWrite()}")

        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                Log.d(TAG, "Setting audio source: MIC")
                setAudioSource(MediaRecorder.AudioSource.MIC)

                Log.d(TAG, "Setting output format: MPEG_4")
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

                Log.d(TAG, "Setting audio encoder: AAC")
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                // Use Wear OS-friendly settings (lower than phone defaults)
                Log.d(TAG, "Setting encoding bitrate: 64000")
                setAudioEncodingBitRate(64000)

                Log.d(TAG, "Setting sampling rate: 16000")
                setAudioSamplingRate(16000)

                Log.d(TAG, "Setting output file: ${file.absolutePath}")
                setOutputFile(file.absolutePath)

                Log.d(TAG, "Calling prepare()...")
                prepare()
                Log.d(TAG, "prepare() succeeded")

                Log.d(TAG, "Calling start()...")
                start()
                Log.d(TAG, "start() succeeded")
            }
            startTime = System.currentTimeMillis()
            Log.d(TAG, "Recording started successfully: ${file.name}")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.javaClass.simpleName}: ${e.message}", e)
            try {
                recorder?.release()
            } catch (re: Exception) {
                Log.e(TAG, "Failed to release recorder after error: ${re.message}")
            }
            recorder = null
            currentFile = null

            // Attempt fallback with minimal settings
            return tryFallbackRecording(file)
        }
    }

    /**
     * Fallback recording attempt with minimal settings if the primary attempt fails.
     * Uses 8kHz / AMR_NB which is supported on virtually all Android hardware.
     */
    private fun tryFallbackRecording(file: File): File? {
        Log.w(TAG, "Attempting fallback recording with minimal settings...")
        // Use .3gp extension for AMR fallback
        val fallbackFile = File(outputDir, file.nameWithoutExtension + "_fallback.3gp")
        currentFile = fallbackFile

        return try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setAudioSamplingRate(8000)
                setOutputFile(fallbackFile.absolutePath)
                prepare()
                start()
            }
            startTime = System.currentTimeMillis()
            Log.d(TAG, "Fallback recording started: ${fallbackFile.name}")
            fallbackFile
        } catch (e: Exception) {
            Log.e(TAG, "Fallback recording also failed: ${e.message}", e)
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            currentFile = null
            null
        }
    }

    fun stopRecording(): RecordingInfo? {
        if (!isRecording) {
            Log.w(TAG, "stopRecording called but not recording")
            return null
        }

        val duration = System.currentTimeMillis() - startTime
        val file = currentFile

        try {
            recorder?.apply {
                Log.d(TAG, "Calling stop()...")
                stop()
                Log.d(TAG, "Calling release()...")
                release()
            }
            Log.d(TAG, "Recording stopped: ${file?.name}, duration: ${duration}ms, size: ${file?.length() ?: 0} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording: ${e.javaClass.simpleName}: ${e.message}", e)
        } finally {
            recorder = null
        }

        return if (file != null && file.exists() && file.length() > 0) {
            Log.d(TAG, "Recording file valid: ${file.name} (${file.length()} bytes)")
            RecordingInfo(
                file = file,
                timestamp = System.currentTimeMillis(),
                durationMs = duration
            )
        } else {
            Log.w(TAG, "Recording file invalid: exists=${file?.exists()}, size=${file?.length()}")
            null
        }
    }

    fun release() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        recorder = null
    }
}

data class RecordingInfo(
    val file: File,
    val timestamp: Long,
    val durationMs: Long
) {
    val displayName: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
            return sdf.format(Date(timestamp))
        }

    val durationDisplay: String
        get() {
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / 1000) / 60
            return String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
}
