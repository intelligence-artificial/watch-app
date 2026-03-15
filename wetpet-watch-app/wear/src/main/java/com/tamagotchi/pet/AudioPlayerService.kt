package com.tamagotchi.pet

import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * Plays back recorded audio files.
 * Ported from note-taking-app wear module.
 */
class AudioPlayerService {

  private var player: MediaPlayer? = null
  private var currentFile: File? = null

  val isPlaying: Boolean get() = player?.isPlaying == true
  val currentPlayingFile: File? get() = currentFile

  fun play(file: File, onCompletion: () -> Unit = {}) {
    stop()
    try {
      player = MediaPlayer().apply {
        setDataSource(file.absolutePath)
        prepare()
        setOnCompletionListener {
          currentFile = null
          onCompletion()
        }
        start()
      }
      currentFile = file
      Log.d("AudioPlayer", "Playing: ${file.name}")
    } catch (e: Exception) {
      Log.e("AudioPlayer", "Failed to play: ${file.name}", e)
      player?.release()
      player = null
      currentFile = null
    }
  }

  fun stop() {
    try {
      player?.apply {
        if (isPlaying) stop()
        release()
      }
    } catch (_: Exception) {}
    player = null
    currentFile = null
  }

  fun release() { stop() }
}
