package com.androidreverse.ide.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidreverse.ide.core.ai.GeminiAppAssistant
import com.androidreverse.ide.core.ai.GeminiChatMessage
import com.androidreverse.ide.core.binary.AiReportExporter
import com.androidreverse.ide.core.binary.ApkWorkspaceEngine
import com.androidreverse.ide.core.binary.CallGraphData
import com.androidreverse.ide.core.binary.CfgGraph
import com.androidreverse.ide.core.binary.DexClassDetail
import com.androidreverse.ide.core.binary.DexClassSummary
import com.androidreverse.ide.core.binary.DexMethodInfo
import com.androidreverse.ide.core.binary.InspectedContent
import com.androidreverse.ide.core.binary.InstalledAppInfo
import com.androidreverse.ide.core.binary.InstalledAppsLoader
import com.androidreverse.ide.core.binary.LoadedWorkspace
import com.androidreverse.ide.core.binary.SearchHit
import java.io.File
import com.androidreverse.ide.data.AnalysisBookmark
import com.androidreverse.ide.data.AnalysisNote
import com.androidreverse.ide.data.EditorSettings
import com.androidreverse.ide.data.LocalStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val TAG = "MainViewModel"

data class MainUiState(
    val workspace: LoadedWorkspace? = null,
    val isLoadingWorkspace: Boolean = false,
    val isExporting: Boolean = false,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingInstalledApps: Boolean = false,
    val statusMessage: String? = null,
    val treeFilter: String = "",
    val expandedFolders: Set<String> = setOf("DEX_CLASSES"),
    val pendingEdits: Map<String, String> = emptyMap(),
    val searchQuery: String = "",
    val searchResults: List<SearchHit> = emptyList(),
    val isSearching: Boolean = false,
    val inspectedContent: InspectedContent? = null,
    val isInspecting: Boolean = false,
    val activeCfg: CfgGraph? = null,
    val activeCallGraph: CallGraphData? = null,
    val callGraphDepth: Int = 3,
    val geminiMessages: List<GeminiChatMessage> = emptyList(),
    val isGeminiStreaming: Boolean = false,
    val geminiError: String? = null,
    val notes: List<AnalysisNote> = emptyList(),
    val bookmarks: List<AnalysisBookmark> = emptyList(),
    val settings: EditorSettings = EditorSettings()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = ApkWorkspaceEngine(application)
    private val installedAppsLoader = InstalledAppsLoader(application)
    private val geminiAssistant = GeminiAppAssistant()
    private val store = LocalStore(application)

    private var geminiJob: Job? = null

    private val _uiState = MutableStateFlow(
        MainUiState(
            notes = store.loadNotes(),
            bookmarks = store.loadBookmarks(),
            settings = store.loadSettings()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun loadInstalledApps(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value.installedApps.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInstalledApps = true) }
            runCatching { installedAppsLoader.loadInstalledApps() }
                .onSuccess { apps ->
                    _uiState.update {
                        it.copy(
                            installedApps = apps,
                            isLoadingInstalledApps = false
                        )
                    }
                }
                .onFailure { err ->
                    Log.e(TAG, "Failed to load installed apps", err)
                    _uiState.update {
                        it.copy(
                            isLoadingInstalledApps = false,
                            statusMessage = err.message ?: "Unable to list installed apps"
                        )
                    }
                }
        }
    }

    fun openInstalledApp(app: InstalledAppInfo, onOpened: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWorkspace = true, statusMessage = null) }
            runCatching { engine.openInstalledApp(app) }
                .onSuccess { ws ->
                    geminiJob?.cancel()
                    _uiState.update {
                        it.copy(
                            workspace = ws,
                            isLoadingWorkspace = false,
                            pendingEdits = emptyMap(),
                            treeFilter = "",
                            expandedFolders = setOf("DEX_CLASSES"),
                            inspectedContent = null,
                            activeCfg = null,
                            activeCallGraph = null,
                            geminiMessages = emptyList(),
                            geminiError = null,
                            isGeminiStreaming = false
                        )
                    }
                    onOpened()
                }
                .onFailure { err ->
                    Log.e(TAG, "Failed to open installed app ${app.packageName}", err)
                    _uiState.update {
                        it.copy(
                            isLoadingWorkspace = false,
                            statusMessage = err.message ?: "Unable to read app APK"
                        )
                    }
                }
        }
    }

    fun openFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWorkspace = true, statusMessage = null) }
            runCatching { engine.openFromUri(uri) }
                .onSuccess { ws ->
                    geminiJob?.cancel()
                    _uiState.update {
                        it.copy(
                            workspace = ws,
                            isLoadingWorkspace = false,
                            pendingEdits = emptyMap(),
                            treeFilter = "",
                            expandedFolders = setOf("DEX_CLASSES"),
                            inspectedContent = null,
                            activeCfg = null,
                            activeCallGraph = null,
                            geminiMessages = emptyList(),
                            geminiError = null,
                            isGeminiStreaming = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingWorkspace = false,
                            statusMessage = err.message ?: "Unable to open archive"
                        )
                    }
                }
        }
    }

    fun closeWorkspace() {
        geminiJob?.cancel()
        _uiState.update {
            it.copy(
                workspace = null,
                pendingEdits = emptyMap(),
                treeFilter = "",
                searchQuery = "",
                searchResults = emptyList(),
                inspectedContent = null,
                activeCfg = null,
                activeCallGraph = null,
                geminiMessages = emptyList(),
                geminiError = null,
                isGeminiStreaming = false
            )
        }
    }

    fun askGemini(rawQuestion: String) {
        val question = rawQuestion.trim()
        val ws = _uiState.value.workspace
        if (question.isEmpty() || ws == null || _uiState.value.isGeminiStreaming) return

        val historySnapshot = _uiState.value.geminiMessages
        val userMsg = GeminiChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = question
        )
        val assistantId = UUID.randomUUID().toString()
        val placeholderMsg = GeminiChatMessage(
            id = assistantId,
            isUser = false,
            text = ""
        )

        _uiState.update {
            it.copy(
                geminiMessages = it.geminiMessages + userMsg + placeholderMsg,
                isGeminiStreaming = true,
                geminiError = null
            )
        }

        val currentInspected = _uiState.value.inspectedContent
        geminiJob?.cancel()
        geminiJob = viewModelScope.launch {
            try {
                val appContext = engine.buildAiContext(ws, currentInspected)
                val firstErr = runGeminiStream(
                    appContext = appContext,
                    history = historySnapshot,
                    question = question,
                    assistantId = assistantId,
                    useFallbackModel = false
                )

                // Automatically retry on gemini-3.5-flash-lite if primary model reports high demand / overload
                if (firstErr != null && isHighDemandError(firstErr)) {
                    Log.w(TAG, "Primary Gemini model busy, retrying with gemini-3.5-flash-lite", firstErr)
                    val fallbackErr = runGeminiStream(
                        appContext = appContext,
                        history = historySnapshot,
                        question = question,
                        assistantId = assistantId,
                        useFallbackModel = true
                    )
                    if (fallbackErr != null) {
                        handleGeminiError(assistantId, fallbackErr)
                    } else {
                        _uiState.update { it.copy(isGeminiStreaming = false) }
                    }
                } else if (firstErr != null) {
                    handleGeminiError(assistantId, firstErr)
                } else {
                    _uiState.update { it.copy(isGeminiStreaming = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleGeminiError(assistantId, e)
            }
        }
    }

    private suspend fun runGeminiStream(
        appContext: String,
        history: List<GeminiChatMessage>,
        question: String,
        assistantId: String,
        useFallbackModel: Boolean
    ): Exception? {
        val accumulated = StringBuilder()
        return try {
            withTimeout(60_000L) {
                geminiAssistant.streamAnswer(appContext, history, question, useFallbackModel)
                    .collect { chunk ->
                        if (chunk.isNotEmpty()) {
                            accumulated.append(chunk)
                            val currentText = accumulated.toString()
                            _uiState.update { state ->
                                state.copy(
                                    geminiMessages = state.geminiMessages.map { msg ->
                                        if (msg.id == assistantId) msg.copy(text = currentText) else msg
                                    }
                                )
                            }
                        }
                    }
            }
            if (accumulated.isBlank()) {
                IllegalStateException("Gemini returned an empty response.")
            } else {
                null
            }
        } catch (e: TimeoutCancellationException) {
            e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
    }

    private fun isHighDemandError(err: Exception): Boolean {
        val msg = err.message.orEmpty().lowercase()
        return msg.contains("high demand") ||
            msg.contains("overloaded") ||
            msg.contains("unavailable") ||
            msg.contains("503") ||
            msg.contains("429") ||
            msg.contains("quota") ||
            msg.contains("resource exhausted")
    }

    private fun handleGeminiError(assistantId: String, err: Exception) {
        Log.e(TAG, "Firebase AI generation failed", err)
        val friendly = if (err is TimeoutCancellationException) {
            "Request timed out after 60 seconds. Please try again."
        } else {
            err.localizedMessage ?: "Failed to reach Gemini."
        }
        _uiState.update { state ->
            state.copy(
                geminiMessages = state.geminiMessages.filterNot { it.id == assistantId && it.text.isBlank() },
                isGeminiStreaming = false,
                geminiError = friendly
            )
        }
    }

    fun clearGeminiChat() {
        geminiJob?.cancel()
        _uiState.update {
            it.copy(
                geminiMessages = emptyList(),
                geminiError = null,
                isGeminiStreaming = false
            )
        }
    }

    fun updateTreeFilter(filter: String) {
        _uiState.update { it.copy(treeFilter = filter) }
    }

    fun toggleFolder(folderKey: String) {
        _uiState.update { state ->
            val updated = state.expandedFolders.toMutableSet()
            if (!updated.add(folderKey)) updated.remove(folderKey)
            state.copy(expandedFolders = updated)
        }
    }

    fun runSearch(query: String) {
        val ws = _uiState.value.workspace ?: return
        _uiState.update { it.copy(searchQuery = query, isSearching = true) }
        viewModelScope.launch {
            val hits = engine.searchWorkspace(ws, query)
            _uiState.update { it.copy(searchResults = hits, isSearching = false) }
        }
    }

    fun inspectFile(path: String, onReady: () -> Unit = {}) {
        val ws = _uiState.value.workspace ?: return
        val edits = _uiState.value.pendingEdits
        viewModelScope.launch {
            _uiState.update { it.copy(isInspecting = true) }
            runCatching { engine.inspectArchiveFile(ws, path, edits) }
                .onSuccess { content ->
                    _uiState.update {
                        it.copy(
                            inspectedContent = content,
                            isInspecting = false,
                            activeCfg = null,
                            activeCallGraph = null
                        )
                    }
                    onReady()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isInspecting = false,
                            statusMessage = err.message ?: "Failed to read file"
                        )
                    }
                }
        }
    }

    fun inspectDexClass(summary: DexClassSummary, onReady: () -> Unit = {}) {
        val ws = _uiState.value.workspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isInspecting = true) }
            runCatching { engine.inspectDexClass(ws, summary) }
                .onSuccess { content ->
                    _uiState.update {
                        it.copy(
                            inspectedContent = content,
                            isInspecting = false,
                            activeCfg = null,
                            activeCallGraph = null
                        )
                    }
                    onReady()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isInspecting = false,
                            statusMessage = err.message ?: "Failed to parse class"
                        )
                    }
                }
        }
    }

    fun inspectDexByDescriptor(dexName: String, descriptor: String, onReady: () -> Unit = {}) {
        val ws = _uiState.value.workspace ?: return
        val summary = ws.dexClasses.firstOrNull {
            it.dexName == dexName && it.descriptor == descriptor
        } ?: return
        inspectDexClass(summary, onReady)
    }

    fun savePendingEdit(path: String, newContent: String) {
        val updatedEdits = _uiState.value.pendingEdits.toMutableMap().apply {
            put(path, newContent)
        }
        _uiState.update { it.copy(pendingEdits = updatedEdits, statusMessage = "Edit saved") }
        inspectFile(path)
    }

    fun exportWorkspace(uri: Uri) {
        val ws = _uiState.value.workspace ?: return
        val edits = _uiState.value.pendingEdits
        viewModelScope.launch {
            runCatching { engine.exportArchiveToUri(ws, edits, uri) }
                .onSuccess { count ->
                    _uiState.update { it.copy(statusMessage = "Exported $count files") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusMessage = err.message ?: "Export failed") }
                }
        }
    }

    fun exportAiReportToUri(uri: Uri) {
        val ws = _uiState.value.workspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, statusMessage = "Building full blueprint...") }
            runCatching { AiReportExporter.exportReportToUri(getApplication(), ws, uri) }
                .onSuccess { bytes ->
                    val sizeText = ApkWorkspaceEngine.formatSize(bytes.toLong())
                    _uiState.update { it.copy(isExporting = false, statusMessage = "Saved blueprint ($sizeText)") }
                }
                .onFailure { err ->
                    Log.e(TAG, "Failed to export AI report", err)
                    _uiState.update { it.copy(isExporting = false, statusMessage = err.message ?: "Export failed") }
                }
        }
    }

    fun prepareShareableReport(onReady: (File) -> Unit) {
        val ws = _uiState.value.workspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, statusMessage = "Building full blueprint...") }
            runCatching { AiReportExporter.createShareableReportFile(getApplication(), ws) }
                .onSuccess { file ->
                    _uiState.update { it.copy(isExporting = false, statusMessage = null) }
                    onReady(file)
                }
                .onFailure { err ->
                    Log.e(TAG, "Failed to prepare report for sharing", err)
                    _uiState.update { it.copy(isExporting = false, statusMessage = err.message ?: "Export failed") }
                }
        }
    }

    fun openMethodCfg(detail: DexClassDetail, method: DexMethodInfo, onReady: () -> Unit) {
        val parser = _uiState.value.workspace?.dexParsers?.get(detail.summary.dexName) ?: return
        val cfg = parser.buildMethodCfg(method)
        _uiState.update { it.copy(activeCfg = cfg) }
        onReady()
    }

    fun openClassCallGraph(detail: DexClassDetail, depth: Int = _uiState.value.callGraphDepth, onReady: () -> Unit = {}) {
        val parser = _uiState.value.workspace?.dexParsers?.get(detail.summary.dexName) ?: return
        val clamped = depth.coerceIn(1, 5)
        val graph = parser.buildClassCallGraph(detail, clamped)
        _uiState.update { it.copy(activeCallGraph = graph, callGraphDepth = clamped) }
        onReady()
    }

    fun updateCallGraphDepth(delta: Int) {
        val detail = (_uiState.value.inspectedContent as? InspectedContent.DexClass)?.detail ?: return
        val next = (_uiState.value.callGraphDepth + delta).coerceIn(1, 5)
        openClassCallGraph(detail, next)
    }

    fun addOrUpdateNote(id: String?, title: String, body: String) {
        val trimmedTitle = title.trim().ifEmpty { "Untitled" }
        val now = System.currentTimeMillis()
        val list = _uiState.value.notes.toMutableList()
        val existingIndex = if (id != null) list.indexOfFirst { it.id == id } else -1
        if (existingIndex >= 0) {
            list[existingIndex] = list[existingIndex].copy(title = trimmedTitle, body = body.trim(), updatedAt = now)
        } else {
            list.add(0, AnalysisNote(UUID.randomUUID().toString(), trimmedTitle, body.trim(), now))
        }
        store.saveNotes(list)
        _uiState.update { it.copy(notes = list) }
    }

    fun deleteNote(id: String) {
        val list = _uiState.value.notes.filterNot { it.id == id }
        store.saveNotes(list)
        _uiState.update { it.copy(notes = list) }
    }

    fun toggleBookmarkForCurrent() {
        val content = _uiState.value.inspectedContent ?: return
        val list = _uiState.value.bookmarks.toMutableList()
        val newBookmark = when (content) {
            is InspectedContent.DexClass -> AnalysisBookmark(
                id = "dex:${content.detail.summary.dexName}:${content.detail.summary.descriptor}",
                label = content.detail.summary.simpleName,
                targetPath = content.detail.summary.packageName,
                isDexClass = true,
                dexName = content.detail.summary.dexName,
                classDescriptor = content.detail.summary.descriptor
            )
            is InspectedContent.XmlFile -> AnalysisBookmark(
                id = "file:${content.path}",
                label = content.path.substringAfterLast('/'),
                targetPath = content.path,
                isDexClass = false
            )
            is InspectedContent.TextFile -> AnalysisBookmark(
                id = "file:${content.path}",
                label = content.path.substringAfterLast('/'),
                targetPath = content.path,
                isDexClass = false
            )
            is InspectedContent.ElfBinary -> AnalysisBookmark(
                id = "file:${content.path}",
                label = content.path.substringAfterLast('/'),
                targetPath = content.path,
                isDexClass = false
            )
            is InspectedContent.RawBinary -> AnalysisBookmark(
                id = "file:${content.path}",
                label = content.path.substringAfterLast('/'),
                targetPath = content.path,
                isDexClass = false
            )
        }
        val removed = list.removeAll { it.id == newBookmark.id }
        if (!removed) {
            list.add(0, newBookmark)
        }
        store.saveBookmarks(list)
        _uiState.update {
            it.copy(
                bookmarks = list,
                statusMessage = if (removed) "Bookmark removed" else "Bookmarked"
            )
        }
    }

    fun removeBookmark(id: String) {
        val list = _uiState.value.bookmarks.filterNot { it.id == id }
        store.saveBookmarks(list)
        _uiState.update { it.copy(bookmarks = list) }
    }

    fun updateSettings(transform: (EditorSettings) -> EditorSettings) {
        val updated = transform(_uiState.value.settings)
        store.saveSettings(updated)
        _uiState.update { it.copy(settings = updated) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
