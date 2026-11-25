package com.androidreverse.ide.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidreverse.ide.R
import com.androidreverse.ide.core.ai.GeminiChatMessage
import com.androidreverse.ide.core.binary.InspectedContent
import com.androidreverse.ide.core.binary.LoadedWorkspace
import com.androidreverse.ide.ui.components.AppIcons
import com.androidreverse.ide.ui.components.TintedIconBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiAskScreen(
    workspace: LoadedWorkspace?,
    inspectedContent: InspectedContent?,
    messages: List<GeminiChatMessage>,
    isStreaming: Boolean,
    errorMessage: String?,
    onAsk: (String) -> Unit,
    onClearChat: () -> Unit,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val activeFileName = when (inspectedContent) {
        is InspectedContent.DexClass -> inspectedContent.detail.summary.simpleName
        is InspectedContent.XmlFile -> inspectedContent.path.substringAfterLast('/')
        is InspectedContent.TextFile -> inspectedContent.path.substringAfterLast('/')
        is InspectedContent.ElfBinary -> inspectedContent.path.substringAfterLast('/')
        is InspectedContent.RawBinary -> inspectedContent.path.substringAfterLast('/')
        null -> null
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("gemini_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.action_ask_gemini),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier.testTag("gemini_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.action_clear)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .imePadding()
            ) {
                // Active installed app context header
                if (workspace != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TintedIconBadge(
                                icon = AppIcons.AndroidApk,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = workspace.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = buildString {
                                    if (workspace.packageName.isNotBlank()) append(workspace.packageName)
                                    if (activeFileName != null) {
                                        if (isNotEmpty()) append(" · ")
                                        append(activeFileName)
                                    }
                                }
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick 1-tap question chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickPrompts = listOf(
                        R.string.prompt_permissions to "What permissions does this app request and which ones are sensitive?",
                        R.string.prompt_components to "List the main activities, services, and receivers in this app.",
                        R.string.prompt_security to "Check this app's manifest, native libraries, and certificates for security findings.",
                        R.string.prompt_explain to if (activeFileName != null) {
                            "Explain what $activeFileName does in plain words."
                        } else {
                            "Give a plain summary of what this app contains."
                        }
                    )
                    items(quickPrompts) { (labelRes, promptText) ->
                        AssistChip(
                            onClick = {
                                if (!isStreaming) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onAsk(promptText)
                                }
                            },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }

                // Error banner (transparent real error reporting)
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = stringResource(R.string.cd_warning),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Chat messages
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TintedIconBadge(
                                icon = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = stringResource(R.string.hint_ask_gemini),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val align = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
                            val bubbleColor = if (msg.isUser) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                            val textColor = if (msg.isUser) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = align
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = bubbleColor,
                                    border = if (msg.isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.widthIn(max = 520.dp)
                                ) {
                                    if (msg.text.isEmpty() && isStreaming) {
                                        Box(modifier = Modifier.padding(16.dp)) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.5.dp
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = textColor,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Composer bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            placeholder = { Text(stringResource(R.string.hint_ask_gemini)) },
                            maxLines = 4,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gemini_question_input")
                        )
                        FilledIconButton(
                            onClick = {
                                val text = draft.trim()
                                if (text.isNotEmpty() && !isStreaming) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    draft = ""
                                    onAsk(text)
                                }
                            },
                            enabled = draft.isNotBlank() && !isStreaming && workspace != null,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("gemini_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.action_send)
                            )
                        }
                    }
                }
            }
        }
    }
}
