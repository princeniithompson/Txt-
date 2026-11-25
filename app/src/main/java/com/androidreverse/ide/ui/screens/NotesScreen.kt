package com.androidreverse.ide.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.androidreverse.ide.data.AnalysisBookmark
import com.androidreverse.ide.data.AnalysisNote
import com.androidreverse.ide.ui.components.AppIcons
import com.androidreverse.ide.ui.components.DestructiveConfirmDialog
import com.androidreverse.ide.ui.components.TintedIconBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    notes: List<AnalysisNote>,
    bookmarks: List<AnalysisBookmark>,
    hasWorkspace: Boolean,
    onSaveNote: (id: String?, title: String, body: String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onOpenBookmark: (AnalysisBookmark) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    var editingNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var noteTitle by rememberSaveable { mutableStateOf("") }
    var noteBody by rememberSaveable { mutableStateOf("") }
    var showNoteEditor by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    if (pendingDeleteId != null) {
        DestructiveConfirmDialog(
            title = stringResource(R.string.dialog_delete_note_title),
            message = stringResource(R.string.dialog_delete_note_body),
            confirmLabel = stringResource(R.string.action_delete),
            onCancel = { pendingDeleteId = null },
            onConfirm = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                pendingDeleteId?.let(onDeleteNote)
                pendingDeleteId = null
            }
        )
    }

    if (showNoteEditor) {
        ModalBottomSheet(
            onDismissRequest = { showNoteEditor = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_new_note),
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.hint_note_title)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )
                OutlinedTextField(
                    value = noteBody,
                    onValueChange = { noteBody = it },
                    minLines = 4,
                    maxLines = 8,
                    placeholder = { Text(stringResource(R.string.hint_note_body)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_body_input")
                )
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSaveNote(editingNoteId, noteTitle, noteBody)
                        showNoteEditor = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_note_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.nav_notes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (activeTab == 0) {
                    Button(
                        onClick = {
                            editingNoteId = null
                            noteTitle = ""
                            noteBody = ""
                            showNoteEditor = true
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("new_note_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_new_note))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { Text("${stringResource(R.string.title_notes)} · ${notes.size}") },
                    modifier = Modifier.testTag("notes_tab")
                )
                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { Text("${stringResource(R.string.title_bookmarks)} · ${bookmarks.size}") },
                    modifier = Modifier.testTag("bookmarks_tab")
                )
            }

            Spacer(Modifier.height(14.dp))

            if (activeTab == 0) {
                if (notes.isEmpty()) {
                    EmptyNotebookCard(
                        icon = AppIcons.Notes,
                        actionLabel = stringResource(R.string.action_new_note),
                        onClick = {
                            editingNoteId = null
                            noteTitle = ""
                            noteBody = ""
                            showNoteEditor = true
                        }
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(notes, key = { it.id }) { note ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editingNoteId = note.id
                                        noteTitle = note.title
                                        noteBody = note.body
                                        showNoteEditor = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TintedIconBadge(
                                        icon = AppIcons.Notes,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (note.body.isNotBlank()) {
                                            Text(
                                                text = note.body,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { pendingDeleteId = note.id },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (bookmarks.isEmpty()) {
                    EmptyNotebookCard(
                        icon = AppIcons.Bookmark,
                        actionLabel = null,
                        onClick = null
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(bookmarks, key = { it.id }) { bm ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = hasWorkspace) { onOpenBookmark(bm) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TintedIconBadge(
                                        icon = if (bm.isDexClass) AppIcons.Code else AppIcons.Xml,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bm.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = bm.targetPath,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveBookmark(bm.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotebookCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String?,
    onClick: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TintedIconBadge(
                icon = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
            if (actionLabel != null && onClick != null) {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}
