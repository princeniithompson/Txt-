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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidreverse.ide.R
import com.androidreverse.ide.core.binary.ApkWorkspaceEngine
import com.androidreverse.ide.core.binary.ArchiveNodeKind
import com.androidreverse.ide.core.binary.DexClassSummary
import com.androidreverse.ide.core.binary.LoadedWorkspace
import com.androidreverse.ide.core.binary.SearchHit
import com.androidreverse.ide.ui.MainUiState
import com.androidreverse.ide.ui.components.AppIcons
import com.androidreverse.ide.ui.components.DestructiveConfirmDialog
import com.androidreverse.ide.ui.components.HeroStatItem
import com.androidreverse.ide.ui.components.TintedIconBadge
import com.androidreverse.ide.ui.components.iconForArchiveKind
import com.androidreverse.ide.ui.theme.StatusWarn

private sealed interface TreeRow {
    val key: String
    val depth: Int

    data class FolderRow(
        override val key: String,
        override val depth: Int,
        val title: String,
        val count: Int,
        val kind: ArchiveNodeKind,
        val isExpanded: Boolean
    ) : TreeRow

    data class FileRow(
        override val key: String,
        override val depth: Int,
        val title: String,
        val path: String,
        val sizeText: String,
        val kind: ArchiveNodeKind,
        val isModified: Boolean
    ) : TreeRow

    data class DexClassRow(
        override val key: String,
        override val depth: Int,
        val summary: DexClassSummary
    ) : TreeRow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    uiState: MainUiState,
    onOpenInstalledApps: () -> Unit,
    onPickApk: () -> Unit,
    onAskGemini: () -> Unit,
    onSaveReportTxt: () -> Unit,
    onShareReportTxt: () -> Unit,
    onExportApk: () -> Unit,
    onCloseWorkspace: () -> Unit,
    onFilterChange: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenDexClass: (DexClassSummary) -> Unit,
    onSearch: (String) -> Unit,
    onSearchHitClick: (SearchHit) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showCloseDialog by rememberSaveable { mutableStateOf(false) }
    var showSearchSheet by rememberSaveable { mutableStateOf(false) }
    var showExportSheet by rememberSaveable { mutableStateOf(false) }

    if (showCloseDialog) {
        DestructiveConfirmDialog(
            title = stringResource(R.string.dialog_close_title),
            message = stringResource(R.string.dialog_close_body),
            confirmLabel = stringResource(R.string.action_confirm_close),
            onCancel = { showCloseDialog = false },
            onConfirm = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showCloseDialog = false
                onCloseWorkspace()
            }
        )
    }

    if (showSearchSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSearchSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            WorkspaceSearchSheet(
                query = uiState.searchQuery,
                results = uiState.searchResults,
                isSearching = uiState.isSearching,
                onQueryChange = onSearch,
                onHitClick = { hit ->
                    showSearchSheet = false
                    onSearchHitClick(hit)
                }
            )
        }
    }

    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ExportOptionsSheet(
                onSaveTxt = {
                    showExportSheet = false
                    onSaveReportTxt()
                },
                onShareTxt = {
                    showExportSheet = false
                    onShareReportTxt()
                },
                onSaveApk = {
                    showExportSheet = false
                    onExportApk()
                }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        if (uiState.isLoadingWorkspace) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
        } else if (uiState.workspace == null) {
            EmptyWorkspaceLaunchpad(
                notesCount = uiState.notes.size,
                bookmarksCount = uiState.bookmarks.size,
                onOpenInstalledApps = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenInstalledApps()
                },
                onPickApk = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPickApk()
                }
            )
        } else {
            LoadedWorkspaceBody(
                workspace = uiState.workspace,
                isExporting = uiState.isExporting,
                treeFilter = uiState.treeFilter,
                expandedFolders = uiState.expandedFolders,
                pendingEdits = uiState.pendingEdits,
                onOpenInstalledApps = onOpenInstalledApps,
                onAskGemini = onAskGemini,
                onFilterChange = onFilterChange,
                onOpenSearch = { showSearchSheet = true },
                onExportApk = { showExportSheet = true },
                onRequestClose = { showCloseDialog = true },
                onToggleFolder = { key ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleFolder(key)
                },
                onOpenFile = onOpenFile,
                onOpenDexClass = onOpenDexClass
            )
        }
    }
}

@Composable
private fun EmptyWorkspaceLaunchpad(
    notesCount: Int,
    bookmarksCount: Int,
    onOpenInstalledApps: () -> Unit,
    onPickApk: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 580.dp)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.AndroidApk,
                        contentDescription = stringResource(R.string.app_name),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onOpenInstalledApps,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("installed_apps_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.action_installed_apps),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    FilledTonalButton(
                        onClick = onPickApk,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("open_apk_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.action_open_apk),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // Visual capability icons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CapabilityTile(
                icon = AppIcons.Xml,
                label = "AXML",
                tint = MaterialTheme.colorScheme.tertiary,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            CapabilityTile(
                icon = AppIcons.Dex,
                label = "DEX",
                tint = MaterialTheme.colorScheme.primary,
                container = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.weight(1f)
            )
            CapabilityTile(
                icon = AppIcons.CfgGraph,
                label = "CFG",
                tint = MaterialTheme.colorScheme.secondary,
                container = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.weight(1f)
            )
            CapabilityTile(
                icon = AppIcons.Certificate,
                label = "Certs",
                tint = MaterialTheme.colorScheme.primary,
                container = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.weight(1f)
            )
        }

        // Saved local stats card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroStatItem(
                    value = notesCount.toString(),
                    label = stringResource(R.string.title_notes),
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    modifier = Modifier.height(36.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                HeroStatItem(
                    value = bookmarksCount.toString(),
                    label = stringResource(R.string.title_bookmarks),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CapabilityTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TintedIconBadge(
                icon = icon,
                contentDescription = label,
                tint = tint,
                containerColor = container
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LoadedWorkspaceBody(
    workspace: LoadedWorkspace,
    isExporting: Boolean,
    treeFilter: String,
    expandedFolders: Set<String>,
    pendingEdits: Map<String, String>,
    onOpenInstalledApps: () -> Unit,
    onAskGemini: () -> Unit,
    onFilterChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onExportApk: () -> Unit,
    onRequestClose: () -> Unit,
    onToggleFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenDexClass: (DexClassSummary) -> Unit
) {
    val rows = remember(workspace, treeFilter, expandedFolders, pendingEdits) {
        buildTreeRows(workspace, treeFilter, expandedFolders, pendingEdits)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // Header bar with package title, switch app, export, and close actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (workspace.packageName.isNotBlank()) {
                        "${workspace.packageName} · ${ApkWorkspaceEngine.formatSize(workspace.packageSizeBytes)}"
                    } else {
                        ApkWorkspaceEngine.formatSize(workspace.packageSizeBytes)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onOpenInstalledApps,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("switch_installed_apps_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = stringResource(R.string.action_installed_apps),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(
                onClick = onExportApk,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("export_apk_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.action_export_apk),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onRequestClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("close_workspace_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close_workspace),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (isExporting) {
            LinearProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(10.dp))
        }

        // Prominent Ask Gemini bar for the loaded app
        Button(
            onClick = onAskGemini,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("workspace_ask_gemini_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.action_ask_gemini),
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(Modifier.height(10.dp))

        // Compact hero metrics strip
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroStatItem(workspace.entries.size.toString(), stringResource(R.string.stat_files), Modifier.weight(1f))
                VerticalDivider(Modifier.height(30.dp), color = MaterialTheme.colorScheme.outlineVariant)
                HeroStatItem(workspace.dexClasses.size.toString(), stringResource(R.string.stat_classes), Modifier.weight(1f))
                VerticalDivider(Modifier.height(30.dp), color = MaterialTheme.colorScheme.outlineVariant)
                HeroStatItem(workspace.nativeLibCount.toString(), stringResource(R.string.stat_native), Modifier.weight(1f))
                VerticalDivider(Modifier.height(30.dp), color = MaterialTheme.colorScheme.outlineVariant)
                HeroStatItem(pendingEdits.size.toString(), stringResource(R.string.stat_edits), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))

        // Filter & Search bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = treeFilter,
                onValueChange = onFilterChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.hint_filter_tree)) },
                trailingIcon = {
                    if (treeFilter.isNotEmpty()) {
                        IconButton(onClick = { onFilterChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear))
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("tree_filter_input")
            )
            FilledTonalButton(
                onClick = onOpenSearch,
                modifier = Modifier
                    .height(54.dp)
                    .testTag("workspace_search_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.action_search),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Archive & DEX Tree
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is TreeRow.FolderRow -> {
                            val (icon, tint, container) = iconForArchiveKind(row.kind, row.isExpanded)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleFolder(row.key) }
                                    .padding(
                                        start = (12 + row.depth * 18).dp,
                                        end = 14.dp,
                                        top = 10.dp,
                                        bottom = 10.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (row.isExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                TintedIconBadge(
                                    icon = icon,
                                    contentDescription = stringResource(R.string.cd_folder),
                                    tint = tint,
                                    containerColor = container
                                )
                                Text(
                                    text = row.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = row.count.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is TreeRow.FileRow -> {
                            val (icon, tint, container) = iconForArchiveKind(row.kind)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFile(row.path) }
                                    .padding(
                                        start = (22 + row.depth * 18).dp,
                                        end = 14.dp,
                                        top = 10.dp,
                                        bottom = 10.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TintedIconBadge(
                                    icon = icon,
                                    contentDescription = stringResource(R.string.cd_file),
                                    tint = tint,
                                    containerColor = container
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = row.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (row.isModified) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(StatusWarn)
                                    )
                                }
                                Text(
                                    text = row.sizeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is TreeRow.DexClassRow -> {
                            val (icon, tint, container) = iconForArchiveKind(ArchiveNodeKind.DEX_CLASS)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenDexClass(row.summary) }
                                    .padding(
                                        start = (22 + row.depth * 18).dp,
                                        end = 14.dp,
                                        top = 10.dp,
                                        bottom = 10.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TintedIconBadge(
                                    icon = icon,
                                    contentDescription = null,
                                    tint = tint,
                                    containerColor = container
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = row.summary.simpleName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (row.depth == 0) {
                                        Text(
                                            text = row.summary.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

private fun buildTreeRows(
    workspace: LoadedWorkspace,
    filter: String,
    expanded: Set<String>,
    pendingEdits: Map<String, String>
): List<TreeRow> {
    val q = filter.trim()
    val rows = mutableListOf<TreeRow>()

    // Flat filtered list when typing a filter
    if (q.isNotEmpty()) {
        workspace.dexClasses
            .filter { it.simpleName.contains(q, true) || it.packageName.contains(q, true) }
            .take(80)
            .forEach { cls ->
                rows.add(TreeRow.DexClassRow("flt:cls:${cls.dexName}:${cls.descriptor}", 0, cls))
            }
        workspace.entries
            .filter { it.path.contains(q, true) }
            .take(80)
            .forEach { entry ->
                rows.add(
                    TreeRow.FileRow(
                        key = "flt:file:${entry.path}",
                        depth = 0,
                        title = entry.path,
                        path = entry.path,
                        sizeText = ApkWorkspaceEngine.formatSize(entry.size),
                        kind = entry.kind,
                        isModified = entry.path in pendingEdits
                    )
                )
            }
        return rows
    }

    // 1. DEX Classes grouped by package
    if (workspace.dexClasses.isNotEmpty()) {
        val dexKey = "DEX_CLASSES"
        val dexOpen = dexKey in expanded
        rows.add(
            TreeRow.FolderRow(
                key = dexKey,
                depth = 0,
                title = "DEX Classes",
                count = workspace.dexClasses.size,
                kind = ArchiveNodeKind.DEX_ROOT,
                isExpanded = dexOpen
            )
        )
        if (dexOpen) {
            val byPackage = workspace.dexClasses.groupBy { it.packageName }
            for ((pkg, classes) in byPackage) {
                val pkgKey = "PKG:$pkg"
                val pkgOpen = pkgKey in expanded
                rows.add(
                    TreeRow.FolderRow(
                        key = pkgKey,
                        depth = 1,
                        title = pkg,
                        count = classes.size,
                        kind = ArchiveNodeKind.DEX_PACKAGE,
                        isExpanded = pkgOpen
                    )
                )
                if (pkgOpen) {
                    for (cls in classes) {
                        rows.add(TreeRow.DexClassRow("CLS:${cls.dexName}:${cls.descriptor}", 2, cls))
                    }
                }
            }
        }
    }

    // 2. Root files (AndroidManifest.xml, resources.arsc, classes.dex, etc.)
    val rootFiles = workspace.entries.filter { !it.path.contains('/') }
    for (entry in rootFiles) {
        rows.add(
            TreeRow.FileRow(
                key = "FILE:${entry.path}",
                depth = 0,
                title = entry.path,
                path = entry.path,
                sizeText = ApkWorkspaceEngine.formatSize(entry.size),
                kind = entry.kind,
                isModified = entry.path in pendingEdits
            )
        )
    }

    // 3. Top-level archive directories (META-INF, res, lib, assets, etc.)
    val folders = workspace.entries
        .filter { it.path.contains('/') }
        .groupBy { it.path.substringBefore('/') }
        .toSortedMap()

    for ((folderName, folderEntries) in folders) {
        val fKey = "DIR:$folderName"
        val fOpen = fKey in expanded
        rows.add(
            TreeRow.FolderRow(
                key = fKey,
                depth = 0,
                title = folderName,
                count = folderEntries.size,
                kind = ArchiveNodeKind.FOLDER,
                isExpanded = fOpen
            )
        )
        if (fOpen) {
            for (entry in folderEntries.take(200)) {
                rows.add(
                    TreeRow.FileRow(
                        key = "FILE:${entry.path}",
                        depth = 1,
                        title = entry.path.removePrefix("$folderName/"),
                        path = entry.path,
                        sizeText = ApkWorkspaceEngine.formatSize(entry.size),
                        kind = entry.kind,
                        isModified = entry.path in pendingEdits
                    )
                )
            }
        }
    }

    return rows
}

@Composable
private fun WorkspaceSearchSheet(
    query: String,
    results: List<SearchHit>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onHitClick: (SearchHit) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.action_search),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.hint_search_query)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_sheet_input")
        )
        if (isSearching) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.id }) { hit ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onHitClick(hit) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TintedIconBadge(
                                icon = if (hit.isDexClass) AppIcons.Code else AppIcons.Xml,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = hit.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = hit.location,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportOptionsSheet(
    onSaveTxt: () -> Unit,
    onShareTxt: () -> Unit,
    onSaveApk: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.action_export_apk),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        ExportOptionCard(
            icon = Icons.Rounded.Description,
            title = stringResource(R.string.export_save_txt),
            tint = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.primaryContainer,
            highlighted = true,
            onClick = onSaveTxt,
            tag = "export_save_txt_option"
        )

        ExportOptionCard(
            icon = Icons.Rounded.Share,
            title = stringResource(R.string.export_share_txt),
            tint = MaterialTheme.colorScheme.secondary,
            container = MaterialTheme.colorScheme.secondaryContainer,
            highlighted = false,
            onClick = onShareTxt,
            tag = "export_share_txt_option"
        )

        ExportOptionCard(
            icon = AppIcons.AndroidApk,
            title = stringResource(R.string.export_save_apk),
            tint = MaterialTheme.colorScheme.tertiary,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            highlighted = false,
            onClick = onSaveApk,
            tag = "export_save_apk_option"
        )
    }
}

@Composable
private fun ExportOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color,
    highlighted: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (highlighted) 2.dp else 1.dp,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TintedIconBadge(
                icon = icon,
                contentDescription = title,
                tint = tint,
                containerColor = container
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
