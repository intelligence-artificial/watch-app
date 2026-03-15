package com.wetpet.mobile

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Transcribes audio files locally using Vosk offline speech recognition.
 * Decodes .m4a to 16-bit PCM at 16kHz mono, then feeds to Vosk.
 * Ported from note-taking-app.
 */
class TranscriptionService(private val context: Context) {

  companion object {
    private const val TAG = "TranscriptionService"
    private const val SAMPLE_RATE = 16000f
    private const val MODEL_DIR = "vosk-model"
    private const val MODEL_VERSION = "en-us-0.22-lgraph"
    private var model: Model? = null
    private val modelLock = Any()
  }

  suspend fun ensureModel(): Boolean = withContext(Dispatchers.IO) {
    synchronized(modelLock) {
      if (model != null) return@withContext true

      try {
        val modelDir = File(context.filesDir, MODEL_DIR)
        val versionFile = File(modelDir, ".model_version")

        val needsExtract = !modelDir.exists() ||
          !versionFile.exists() ||
          versionFile.readText().trim() != MODEL_VERSION

        if (needsExtract) {
          Log.d(TAG, "Extracting Vosk model ($MODEL_VERSION) from assets...")
          if (modelDir.exists()) modelDir.deleteRecursively()
          extractModelFromAssets(modelDir)
          versionFile.writeText(MODEL_VERSION)
        }

        Log.d(TAG, "Loading Vosk model ($MODEL_VERSION)...")
        model = Model(modelDir.absolutePath)
        Log.d(TAG, "Vosk model loaded")
        true
      } catch (e: Exception) {
        Log.e(TAG, "Failed to load Vosk model: ${e.message}", e)
        false
      }
    }
  }

  suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
    if (!ensureModel()) {
      return@withContext "[Vosk model not available]"
    }

    try {
      Log.d(TAG, "Decoding ${audioFile.name}...")
      val pcmData = decodeAudioToPcm(audioFile)
      if (pcmData == null || pcmData.isEmpty()) {
        return@withContext "[Could not decode audio]"
      }

      Log.d(TAG, "Decoded ${pcmData.size} bytes, transcribing...")

      val recognizer = Recognizer(model, SAMPLE_RATE)
      var offset = 0
      val chunkSize = 4096
      while (offset < pcmData.size) {
        val end = minOf(offset + chunkSize, pcmData.size)
        val chunk = pcmData.copyOfRange(offset, end)
        recognizer.acceptWaveForm(chunk, chunk.size)
        offset = end
      }

      val resultJson = recognizer.finalResult
      recognizer.close()

      val text = JSONObject(resultJson).optString("text", "").trim()
      if (text.isEmpty()) {
        Log.d(TAG, "No speech detected")
        return@withContext "[No speech detected]"
      }

      Log.d(TAG, "Transcript: ${text.take(80)}...")
      text
    } catch (e: Exception) {
      Log.e(TAG, "Transcription failed: ${e.message}", e)
      "[Transcription error: ${e.message}]"
    }
  }

  private fun decodeAudioToPcm(audioFile: File): ByteArray? {
    val extractor = MediaExtractor()
    try {
      extractor.setDataSource(audioFile.absolutePath)

      var audioTrackIndex = -1
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
          audioTrackIndex = i
          break
        }
      }
      if (audioTrackIndex < 0) return null

      extractor.selectTrack(audioTrackIndex)
      val format = extractor.getTrackFormat(audioTrackIndex)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
      val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      val srcCh = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

      val codec = MediaCodec.createDecoderByType(mime)
      codec.configure(format, null, null, 0)
      codec.start()

      val pcmShorts = mutableListOf<Short>()
      val bufInfo = MediaCodec.BufferInfo()
      var done = false

      while (!done) {
        val inputIndex = codec.dequeueInputBuffer(10000)
        if (inputIndex >= 0) {
          val buf = codec.getInputBuffer(inputIndex)!!
          val sampleSize = extractor.readSampleData(buf, 0)
          if (sampleSize < 0) {
            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            done = true
          } else {
            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
            extractor.advance()
          }
        }

        var outputIndex = codec.dequeueOutputBuffer(bufInfo, 10000)
        while (outputIndex >= 0) {
          if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
          val outBuf = codec.getOutputBuffer(outputIndex)!!
          val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
          while (shortBuf.hasRemaining()) pcmShorts.add(shortBuf.get())
          codec.releaseOutputBuffer(outputIndex, false)
          outputIndex = codec.dequeueOutputBuffer(bufInfo, 0)
        }
      }

      codec.stop()
      codec.release()
      extractor.release()

      val totalSrcSamples = pcmShorts.size / srcCh
      val ratio = srcRate.toDouble() / 16000
      val targetSamples = (totalSrcSamples / ratio).toInt()

      val output = ByteBuffer.allocate(targetSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
      for (i in 0 until targetSamples) {
        val srcPos = (i * ratio).toInt().coerceIn(0, totalSrcSamples - 1)
        if (srcCh == 1) {
          output.putShort(pcmShorts[srcPos])
        } else {
          var sum = 0L
          for (ch in 0 until srcCh) sum += pcmShorts[srcPos * srcCh + ch]
          output.putShort((sum / srcCh).toShort())
        }
      }
      return output.array()
    } catch (e: Exception) {
      Log.e(TAG, "Decode error: ${e.message}", e)
      extractor.release()
      return null
    }
  }

  private fun extractModelFromAssets(targetDir: File) {
    targetDir.mkdirs()
    val assetManager = context.assets
    fun extractDir(assetPath: String, targetPath: File) {
      val files = assetManager.list(assetPath) ?: return
      if (files.isEmpty()) {
        assetManager.open(assetPath).use { input ->
          FileOutputStream(targetPath).use { output -> input.copyTo(output) }
        }
      } else {
        targetPath.mkdirs()
        for (file in files) {
          val childAsset = "$assetPath/$file"
          val childTarget = File(targetPath, file)
          val childFiles = assetManager.list(childAsset)
          if (childFiles != null && childFiles.isNotEmpty()) {
            extractDir(childAsset, childTarget)
          } else {
            assetManager.open(childAsset).use { input ->
              FileOutputStream(childTarget).use { output -> input.copyTo(output) }
            }
          }
        }
      }
    }
    extractDir("model", targetDir)
    Log.d(TAG, "Model extracted to ${targetDir.absolutePath}")
  }

  fun getAudioDuration(file: File): Long {
    return try {
      val extractor = MediaExtractor()
      extractor.setDataSource(file.absolutePath)
      val d = extractor.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION) / 1000
      extractor.release()
      d
    } catch (_: Exception) { 0L }
  }
}
