package com.androidreverse.ide.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidreverse.ide.R
import com.androidreverse.ide.core.binary.ApkWorkspaceEngine
import com.androidreverse.ide.core.binary.DexClassDetail
import com.androidreverse.ide.core.binary.DexMethodInfo
import com.androidreverse.ide.core.binary.InspectedContent
import com.androidreverse.ide.data.EditorSettings
import com.androidreverse.ide.ui.components.AppIcons
import com.androidreverse.ide.ui.components.CodeLanguage
import com.androidreverse.ide.ui.components.StatusIconMark
import com.androidreverse.ide.ui.components.SyntaxCodeViewer
import com.androidreverse.ide.ui.components.TintedIconBadge
import com.androidreverse.ide.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectorScreen(
    content: InspectedContent?,
    isLoading: Boolean,
    isBookmarked: Boolean,
    settings: EditorSettings,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onSaveEdit: (String, String) -> Unit,
    onOpenMethodCfg: (DexClassDetail, DexMethodInfo) -> Unit,
    onOpenCallGraph: (DexClassDetail) -> Unit,
    onAskGemini: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    var searchInFile by rememberSaveable { mutableStateOf("") }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var editBuffer by remember(content) {
        mutableStateOf(
            when (content) {
                is InspectedContent.XmlFile -> content.content
                is InspectedContent.TextFile -> content.content
                else -> ""
            }
        )
    }
    var showOutlineSheet by rememberSaveable { mutableStateOf(false) }

    val title = when (content) {
        is InspectedContent.DexClass -> content.detail.summary.simpleName
        is InspectedContent.XmlFile -> content.path.substringAfterLast('/')
        is InspectedContent.TextFile -> content.path.substringAfterLast('/')
        is InspectedContent.ElfBinary -> content.path.substringAfterLast('/')
        is InspectedContent.RawBinary -> content.path.substringAfterLast('/')
        null -> ""
    }

    val isEditable = content is InspectedContent.XmlFile || content is InspectedContent.TextFile

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("inspector_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    IconButton(
                        onClick = onAskGemini,
                        modifier = Modifier.testTag("inspector_ask_gemini_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = stringResource(R.string.action_ask_gemini),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (content is InspectedContent.DexClass) {
                        IconButton(
                            onClick = { onOpenCallGraph(content.detail) },
                            modifier = Modifier.testTag("open_call_graph_button")
                        ) {
                            Icon(
                                imageVector = AppIcons.CallGraph,
                                contentDescription = stringResource(R.string.tab_calls),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showOutlineSheet = true },
                            modifier = Modifier.testTag("open_outline_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.List,
                                contentDescription = stringResource(R.string.action_outline)
                            )
                        }
                    } else if (isEditable) {
                        if (isEditing) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val path = when (content) {
                                        is InspectedContent.XmlFile -> content.path
                                        is InspectedContent.TextFile -> content.path
                                        else -> return@IconButton
                                    }
                                    isEditing = false
                                    onSaveEdit(path, editBuffer)
                                },
                                modifier = Modifier.testTag("save_edit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = stringResource(R.string.action_save),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { isEditing = true },
                                modifier = Modifier.testTag("edit_file_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = stringResource(R.string.action_edit)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { showSearchBar = !showSearchBar },
                        modifier = Modifier.testTag("in_file_search_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.cd_search)
                        )
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleBookmark()
                        },
                        modifier = Modifier.testTag("toggle_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = stringResource(R.string.action_bookmark),
                            tint = if (isBookmarked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (showSearchBar) {
                OutlinedTextField(
                    value = searchInFile,
                    onValueChange = { searchInFile = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.action_search)) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (isLoading || content == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            when (content) {
                is InspectedContent.DexClass -> {
                    DexClassInspectorBody(
                        detail = content.detail,
                        settings = settings,
                        searchQuery = searchInFile,
                        onOpenMethodCfg = { method -> onOpenMethodCfg(content.detail, method) },
                        onCopyCode = { text -> clipboard.setText(AnnotatedString(text)) }
                    )
                }
                is InspectedContent.XmlFile -> {
                    EditableCodeBody(
                        path = content.path,
                        byteSize = content.byteSize,
                        sha256Short = content.checksums.sha256.take(12),
                        code = content.content,
                        language = CodeLanguage.XML,
                        isEditing = isEditing,
                        editBuffer = editBuffer,
                        onEditChange = { editBuffer = it },
                        settings = settings,
                        searchQuery = searchInFile,
                        onCopy = { clipboard.setText(AnnotatedString(content.content)) }
                    )
                }
                is InspectedContent.TextFile -> {
                    EditableCodeBody(
                        path = content.path,
                        byteSize = content.byteSize,
                        sha256Short = content.checksums.sha256.take(12),
                        code = content.content,
                        language = CodeLanguage.PLAIN,
                        isEditing = isEditing,
                        editBuffer = editBuffer,
                        onEditChange = { editBuffer = it },
                        settings = settings,
                        searchQuery = searchInFile,
                        onCopy = { clipboard.setText(AnnotatedString(content.content)) }
                    )
                }
                is InspectedContent.ElfBinary -> {
                    ElfBinaryInspectorBody(
                        elf = content,
                        settings = settings,
                        searchQuery = searchInFile
                    )
                }
                is InspectedContent.RawBinary -> {
                    Column(Modifier.fillMaxSize()) {
                        MetaStrip(
                            leftText = ApkWorkspaceEngine.formatSize(content.byteSize),
                            rightText = "CRC ${content.checksums.crc32}",
                            onCopy = { clipboard.setText(AnnotatedString(content.checksums.sha256)) }
                        )
                        SyntaxCodeViewer(
                            code = content.hexDump,
                            language = CodeLanguage.HEX,
                            fontSp = settings.codeFontSp,
                            showLineNumbers = false,
                            wrapLines = false,
                            highlightQuery = searchInFile,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showOutlineSheet && content is InspectedContent.DexClass) {
        ModalBottomSheet(
            onDismissRequest = { showOutlineSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ClassOutlineSheet(
                detail = content.detail,
                onSelectMethodForCfg = { method ->
                    showOutlineSheet = false
                    onOpenMethodCfg(content.detail, method)
                }
            )
        }
    }
}

@Composable
private fun DexClassInspectorBody(
    detail: DexClassDetail,
    settings: EditorSettings,
    searchQuery: String,
    onOpenMethodCfg: (DexMethodInfo) -> Unit,
    onCopyCode: (String) -> Unit
) {
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    val code = if (activeTab == 0) detail.smaliCode else detail.javaPseudoCode
    val lang = if (activeTab == 0) CodeLanguage.SMALI else CodeLanguage.JAVA

    Column(Modifier.fillMaxSize()) {
        // Tab strip + Copy
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                label = { Text(stringResource(R.string.tab_smali)) },
                modifier = Modifier.testTag("tab_smali")
            )
            FilterChip(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                label = { Text(stringResource(R.string.tab_java)) },
                modifier = Modifier.testTag("tab_java")
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onCopyCode(code) }) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.action_copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Quick CFG chips for methods that have bytecode
        val cfgMethods = remember(detail) {
            detail.methods.filter { it.instructions.isNotEmpty() }.take(12)
        }
        if (cfgMethods.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                items(cfgMethods, key = { it.signature }) { method ->
                    AssistChip(
                        onClick = { onOpenMethodCfg(method) },
                        leadingIcon = {
                            Icon(
                                imageVector = AppIcons.CfgGraph,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        label = { Text(method.name, maxLines = 1) }
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SyntaxCodeViewer(
            code = code,
            language = lang,
            fontSp = settings.codeFontSp,
            showLineNumbers = settings.showLineNumbers,
            wrapLines = settings.wrapLines,
            highlightQuery = searchQuery,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EditableCodeBody(
    path: String,
    byteSize: Long,
    sha256Short: String,
    code: String,
    language: CodeLanguage,
    isEditing: Boolean,
    editBuffer: String,
    onEditChange: (String) -> Unit,
    settings: EditorSettings,
    searchQuery: String,
    onCopy: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        MetaStrip(
            leftText = ApkWorkspaceEngine.formatSize(byteSize),
            rightText = "SHA $sha256Short",
            onCopy = onCopy
        )
        if (isEditing) {
            OutlinedTextField(
                value = editBuffer,
                onValueChange = onEditChange,
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = settings.codeFontSp.sp,
                    lineHeight = (settings.codeFontSp * 1.5f).sp
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .testTag("code_editor_textfield")
            )
        } else {
            SyntaxCodeViewer(
                code = code,
                language = language,
                fontSp = settings.codeFontSp,
                showLineNumbers = settings.showLineNumbers,
                wrapLines = settings.wrapLines,
                highlightQuery = searchQuery,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetaStrip(
    leftText: String,
    rightText: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = leftText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = rightText,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.action_copy),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ElfBinaryInspectorBody(
    elf: InspectedContent.ElfBinary,
    settings: EditorSettings,
    searchQuery: String
) {
    val analysis = elf.analysis
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TintedIconBadge(
                            icon = AppIcons.NativeElf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                        Column {
                            Text(
                                text = "${analysis.machine} · ${if (analysis.is64Bit) "64-bit" else "32-bit"}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${analysis.elfType} · ${analysis.entryPoint}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Security flags: shape + color icons (no status words)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SecurityFlagPill("PIE", analysis.isPie)
                        SecurityFlagPill("NX", analysis.hasNx)
                        SecurityFlagPill("RELRO", analysis.hasRelro)
                    }
                }
            }
        }

        if (analysis.sections.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.label_sections),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(analysis.sections.take(24)) { sec ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sec.name,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = sec.flags,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = ApkWorkspaceEngine.formatSize(sec.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.tab_hex),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                SyntaxCodeViewer(
                    code = analysis.hexPreview,
                    language = CodeLanguage.HEX,
                    fontSp = settings.codeFontSp,
                    showLineNumbers = false,
                    wrapLines = false,
                    highlightQuery = searchQuery
                )
            }
        }
    }
}

@Composable
private fun SecurityFlagPill(label: String, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusIconMark(isGood = enabled)
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ClassOutlineSheet(
    detail: DexClassDetail,
    onSelectMethodForCfg: (DexMethodInfo) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = detail.summary.simpleName,
            style = MaterialTheme.typography.titleLarge
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (detail.methods.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.label_methods),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(detail.methods, key = { "m:${it.signature}" }) { m ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMethodForCfg(m) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TintedIconBadge(
                                icon = AppIcons.CfgGraph,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${m.instructions.size} ops",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            if (detail.fields.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.label_fields),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                items(detail.fields, key = { "f:${it.name}:${it.type}" }) { f ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(f.name, fontFamily = JetBrainsMonoFamily, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            f.type,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
