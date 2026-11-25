package com.androidreverse.ide.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AnalysisNote(
    val id: String,
    val title: String,
    val body: String,
    val updatedAt: Long
)

@Serializable
data class AnalysisBookmark(
    val id: String,
    val label: String,
    val targetPath: String,
    val isDexClass: Boolean,
    val dexName: String = "",
    val classDescriptor: String = ""
)

@Serializable
data class EditorSettings(
    val isDarkTheme: Boolean = false,
    val codeFontSp: Int = 14,
    val wrapLines: Boolean = false,
    val showLineNumbers: Boolean = true
)

class LocalStore(context: Context) {

    private val prefs = context.getSharedPreferences("reverse_ide_local_store", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadNotes(): List<AnalysisNote> {
        val raw = prefs.getString(KEY_NOTES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AnalysisNote>>(raw) }.getOrDefault(emptyList())
    }

    fun saveNotes(notes: List<AnalysisNote>) {
        prefs.edit().putString(KEY_NOTES, json.encodeToString(notes)).apply()
    }

    fun loadBookmarks(): List<AnalysisBookmark> {
        val raw = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AnalysisBookmark>>(raw) }.getOrDefault(emptyList())
    }

    fun saveBookmarks(bookmarks: List<AnalysisBookmark>) {
        prefs.edit().putString(KEY_BOOKMARKS, json.encodeToString(bookmarks)).apply()
    }

    fun loadSettings(): EditorSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return EditorSettings()
        return runCatching { json.decodeFromString<EditorSettings>(raw) }.getOrDefault(EditorSettings())
    }

    fun saveSettings(settings: EditorSettings) {
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(settings)).apply()
    }

    companion object {
        private const val KEY_NOTES = "notes_v1"
        private const val KEY_BOOKMARKS = "bookmarks_v1"
        private const val KEY_SETTINGS = "settings_v1"
    }
}
