package com.androidreverse.ide.core.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class GeminiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String
)

/**
 * Gemini assistant powered by Firebase AI Logic with automatic fallback to gemini-3.5-flash-lite
 * when gemini-3.8-flash experiences transient high demand.
 */
class GeminiAppAssistant {

    private val systemPrompt = content {
        text(
            "You are an Android reverse engineering and security analysis assistant. " +
                "Answer questions clearly, directly, and concisely in plain language using " +
                "the provided APK metadata, decoded AndroidManifest.xml, DEX classes, " +
                "native libraries, certificates, and any open file snippet. " +
                "Never invent facts that are not supported by the provided app context."
        )
    }

    private val primaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = systemPrompt
        )
    }

    private val fallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            systemInstruction = systemPrompt
        )
    }

    fun streamAnswer(
        appContext: String,
        history: List<GeminiChatMessage>,
        question: String,
        useFallbackModel: Boolean = false
    ): Flow<String> {
        val recentHistory = history.takeLast(6)
        val prompt = buildString {
            append("LOADED ANDROID APP CONTEXT:\n")
            append(appContext)
            append("\n\n")
            if (recentHistory.isNotEmpty()) {
                append("PREVIOUS CONVERSATION:\n")
                for (msg in recentHistory) {
                    append(if (msg.isUser) "User: " else "Assistant: ")
                    append(msg.text.take(1000))
                    append('\n')
                }
                append('\n')
            }
            append("USER QUESTION:\n")
            append(question)
        }
        val activeModel = if (useFallbackModel) fallbackModel else primaryModel
        return activeModel.generateContentStream(prompt).map { chunk -> chunk.text.orEmpty() }
    }
}
