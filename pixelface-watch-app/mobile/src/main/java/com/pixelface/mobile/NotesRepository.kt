package com.pixelface.mobile

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Thread-safe JSON-based storage for voice notes.
 * Ported from note-taking-app.
 */
class NotesRepository(private val context: Context) {

  companion object {
    private const val TAG = "NotesRepository"
    private const val NOTES_FILE = "voice_notes.json"
    private val fileLock = Any()
  }

  data class VoiceNote(
    val id: String,
    val audioPath: String,
    val transcript: String?,
    val timestamp: Long,
    val durationMs: Long,
    val isTranscribing: Boolean = false,
    val processingTimeMs: Long = 0
  ) {
    val displayTime: String
      get() {
        val sdf = SimpleDateFormat("MMM dd, h:mm a", Locale.US)
        return sdf.format(Date(timestamp))
      }

    val durationDisplay: String
      get() {
        val s = (durationMs / 1000) % 60
        val m = (durationMs / 1000) / 60
        return String.format(Locale.US, "%d:%02d", m, s)
      }

    val processingTimeDisplay: String
      get() = when {
        processingTimeMs <= 0 -> ""
        processingTimeMs < 1000 -> "${processingTimeMs}ms"
        else -> String.format(Locale.US, "%.1fs", processingTimeMs / 1000.0)
      }

    val preview: String
      get() = when {
        isTranscribing -> "Transcribing…"
        transcript.isNullOrBlank() -> "No transcript"
        transcript.length > 120 -> transcript.take(120) + "…"
        else -> transcript
      }
  }

  private val notesFile: File get() = File(context.filesDir, NOTES_FILE)

  val recordingsDir: File
    get() {
      val dir = File(context.filesDir, "recordings")
      if (!dir.exists()) dir.mkdirs()
      return dir
    }

  fun loadNotes(): List<VoiceNote> = synchronized(fileLock) {
    return try {
      if (!notesFile.exists()) return emptyList()
      val text = notesFile.readText().trim()
      if (text.isEmpty()) return emptyList()
      val json = JSONArray(text)
      (0 until json.length()).map { i ->
        val obj = json.getJSONObject(i)
        VoiceNote(
          id = obj.getString("id"),
          audioPath = obj.getString("audioPath"),
          transcript = obj.optString("transcript", null),
          timestamp = obj.getLong("timestamp"),
          durationMs = obj.optLong("durationMs", 0),
          isTranscribing = obj.optBoolean("isTranscribing", false),
          processingTimeMs = obj.optLong("processingTimeMs", 0)
        )
      }.sortedByDescending { it.timestamp }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load notes", e)
      emptyList()
    }
  }

  fun hasNoteForFile(filename: String): Boolean = synchronized(fileLock) {
    val notes = loadNotes()
    return notes.any { File(it.audioPath).name == filename }
  }

  fun addNote(audioFile: File): VoiceNote = synchronized(fileLock) {
    val existing = loadNotes().find { File(it.audioPath).name == audioFile.name }
    if (existing != null) {
      Log.d(TAG, "Note already exists for ${audioFile.name}, skipping")
      return existing
    }

    val note = VoiceNote(
      id = UUID.randomUUID().toString(),
      audioPath = audioFile.absolutePath,
      transcript = null,
      timestamp = System.currentTimeMillis(),
      durationMs = 0,
      isTranscribing = true,
      processingTimeMs = 0
    )
    val notes = loadNotes().toMutableList()
    notes.add(0, note)
    saveNotes(notes)
    Log.d(TAG, "Added note: ${note.id} from ${audioFile.name}")
    return note
  }

  /** Add a transcript-only note (no audio file — from Google Speech Activity) */
  fun addTranscriptNote(filename: String, transcript: String): VoiceNote = synchronized(fileLock) {
    val existing = loadNotes().find { File(it.audioPath).name == filename }
    if (existing != null) {
      Log.d(TAG, "Transcript note already exists for $filename, skipping")
      return existing
    }

    val note = VoiceNote(
      id = UUID.randomUUID().toString(),
      audioPath = filename, // No actual file — just the identifier
      transcript = transcript,
      timestamp = System.currentTimeMillis(),
      durationMs = 0,
      isTranscribing = false,
      processingTimeMs = 0
    )
    val notes = loadNotes().toMutableList()
    notes.add(0, note)
    saveNotes(notes)
    Log.d(TAG, "Added transcript note: ${note.id}")
    return note
  }

  fun updateTranscript(noteId: String, transcript: String, processingTimeMs: Long = 0) = synchronized(fileLock) {
    val notes = loadNotes().toMutableList()
    val index = notes.indexOfFirst { it.id == noteId }
    if (index >= 0) {
      notes[index] = notes[index].copy(
        transcript = transcript,
        isTranscribing = false,
        processingTimeMs = processingTimeMs
      )
      saveNotes(notes)
      Log.d(TAG, "Updated transcript for $noteId (${processingTimeMs}ms)")
    }
  }

  fun deleteNote(noteId: String) = synchronized(fileLock) {
    val notes = loadNotes().toMutableList()
    val note = notes.find { it.id == noteId }
    if (note != null) {
      try { File(note.audioPath).delete() } catch (_: Exception) {}
      notes.removeAll { it.id == noteId }
      saveNotes(notes)
      Log.d(TAG, "Deleted note: $noteId")
    }
  }

  private fun saveNotes(notes: List<VoiceNote>) {
    val json = JSONArray()
    notes.forEach { note ->
      json.put(JSONObject().apply {
        put("id", note.id)
        put("audioPath", note.audioPath)
        put("transcript", note.transcript ?: JSONObject.NULL)
        put("timestamp", note.timestamp)
        put("durationMs", note.durationMs)
        put("isTranscribing", note.isTranscribing)
        put("processingTimeMs", note.processingTimeMs)
      })
    }
    notesFile.writeText(json.toString(2))
  }
}
