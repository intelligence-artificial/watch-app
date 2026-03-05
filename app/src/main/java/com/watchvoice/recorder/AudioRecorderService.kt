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

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTime: Long = 0L

    val isRecording: Boolean get() = recorder != null

    fun startRecording(): File? {
        if (isRecording) return null

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(outputDir, "voice_${timestamp}.m4a")
        currentFile = file

        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            startTime = System.currentTimeMillis()
            Log.d("AudioRecorder", "Recording started: ${file.name}")
            return file
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            recorder?.release()
            recorder = null
            currentFile = null
            return null
        }
    }

    fun stopRecording(): RecordingInfo? {
        if (!isRecording) return null

        val duration = System.currentTimeMillis() - startTime
        val file = currentFile

        try {
            recorder?.apply {
                stop()
                release()
            }
            Log.d("AudioRecorder", "Recording stopped: ${file?.name}, duration: ${duration}ms")
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop recording", e)
        } finally {
            recorder = null
        }

        return if (file != null && file.exists()) {
            RecordingInfo(
                file = file,
                timestamp = System.currentTimeMillis(),
                durationMs = duration
            )
        } else null
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
