package com.androidreverse.ide.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.androidreverse.ide.R
import com.androidreverse.ide.core.binary.ArchiveNodeKind
import com.androidreverse.ide.ui.theme.CodeSyntaxPalette
import com.androidreverse.ide.ui.theme.JetBrainsMonoFamily
import com.androidreverse.ide.ui.theme.LocalCodePalette
import com.androidreverse.ide.ui.theme.StatusError
import com.androidreverse.ide.ui.theme.StatusGood
import com.androidreverse.ide.ui.theme.StatusWarn

/**
 * Single source of truth for app icons: one concept = one vector icon reused everywhere.
 */
object AppIcons {
    val Workspace: ImageVector = Icons.Rounded.FolderOpen
    val Tools: ImageVector = Icons.Rounded.Build
    val Notes: ImageVector = Icons.Rounded.EditNote
    val Settings: ImageVector = Icons.Rounded.Settings
    val FolderClosed: ImageVector = Icons.Rounded.Folder
    val FolderOpen: ImageVector = Icons.Rounded.FolderOpen
    val Dex: ImageVector = Icons.Rounded.Memory
    val Code: ImageVector = Icons.Rounded.Code
    val Xml: ImageVector = Icons.Rounded.Description
    val NativeElf: ImageVector = Icons.Rounded.Memory
    val Certificate: ImageVector = Icons.Rounded.VerifiedUser
    val ImageFile: ImageVector = Icons.Rounded.Image
    val GenericFile: ImageVector = Icons.Rounded.InsertDriveFile
    val AndroidApk: ImageVector = Icons.Rounded.Android
    val CfgGraph: ImageVector = Icons.Rounded.AccountTree
    val CallGraph: ImageVector = Icons.Rounded.Hub
    val Bookmark: ImageVector = Icons.Rounded.Bookmark
}

@Composable
fun TintedIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun iconForArchiveKind(kind: ArchiveNodeKind, isExpanded: Boolean = false): Triple<ImageVector, Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (kind) {
        ArchiveNodeKind.FOLDER -> Triple(
            if (isExpanded) AppIcons.FolderOpen else AppIcons.FolderClosed,
            cs.primary,
            cs.primaryContainer
        )
        ArchiveNodeKind.DEX_ROOT, ArchiveNodeKind.DEX_PACKAGE -> Triple(
            AppIcons.Dex,
            cs.secondary,
            cs.secondaryContainer
        )
        ArchiveNodeKind.DEX_CLASS -> Triple(
            AppIcons.Code,
            cs.primary,
            cs.primaryContainer
        )
        ArchiveNodeKind.XML -> Triple(
            AppIcons.Xml,
            cs.tertiary,
            cs.tertiaryContainer
        )
        ArchiveNodeKind.ELF -> Triple(
            AppIcons.NativeElf,
            cs.secondary,
            cs.secondaryContainer
        )
        ArchiveNodeKind.CERTIFICATE -> Triple(
            AppIcons.Certificate,
            StatusGood,
            cs.secondaryContainer
        )
        ArchiveNodeKind.IMAGE -> Triple(
            AppIcons.ImageFile,
            cs.tertiary,
            cs.tertiaryContainer
        )
        ArchiveNodeKind.TEXT -> Triple(
            AppIcons.Xml,
            cs.primary,
            cs.surfaceContainer
        )
        ArchiveNodeKind.BINARY -> Triple(
            AppIcons.GenericFile,
            cs.onSurfaceVariant,
            cs.surfaceContainer
        )
    }
}

/**
 * Shape + color status indicator (never status words).
 */
@Composable
fun StatusIconMark(
    isGood: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (isGood) StatusGood else StatusWarn
    val icon = if (isGood) Icons.Rounded.Check else Icons.Rounded.PriorityHigh
    val cd = stringResource(if (isGood) R.string.cd_status_good else R.string.cd_status_warn)
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = cd,
            tint = Color.White,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
fun HeroStatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * Non-dismissable destructive confirmation dialog matching the owner's visual spec.
 */
@Composable
fun DestructiveConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = stringResource(R.string.cd_warning),
                        tint = StatusError,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_cancel_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_confirm_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusError,
                            contentColor = Color.White
                        )
                    ) {
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}

enum class CodeLanguage {
    XML,
    SMALI,
    JAVA,
    HEX,
    PLAIN
}

@Composable
fun SyntaxCodeViewer(
    code: String,
    language: CodeLanguage,
    fontSp: Int,
    showLineNumbers: Boolean,
    wrapLines: Boolean,
    highlightQuery: String = "",
    modifier: Modifier = Modifier
) {
    val palette = LocalCodePalette.current
    val lines = remember(code) { code.lines() }
    val hScroll = rememberScrollState()
    val highlightBg = MaterialTheme.colorScheme.tertiaryContainer

    val contentModifier = if (wrapLines) {
        modifier
            .fillMaxWidth()
            .background(palette.editorBackground)
    } else {
        modifier
            .fillMaxWidth()
            .background(palette.editorBackground)
            .horizontalScroll(hScroll)
    }

    LazyColumn(
        modifier = contentModifier
    ) {
        itemsIndexed(lines, key = { idx, _ -> idx }) { idx, line ->
            val annotated = remember(line, language, palette, highlightQuery) {
                highlightLine(line, language, palette, highlightQuery, highlightBg)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 1.5.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (showLineNumbers) {
                    Text(
                        text = (idx + 1).toString().padStart(4, ' '),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = fontSp.sp,
                        lineHeight = (fontSp * 1.5f).sp,
                        color = palette.lineNumber,
                        modifier = Modifier
                            .background(palette.editorGutter, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = annotated,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = fontSp.sp,
                    lineHeight = (fontSp * 1.5f).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = wrapLines
                )
            }
        }
    }
}

private val SMALI_KEYWORDS = setOf(
    ".class", ".super", ".field", ".method", ".end", ".registers", ".locals",
    "public", "private", "protected", "static", "final", "abstract", "constructor",
    "invoke-virtual", "invoke-direct", "invoke-static", "invoke-interface", "invoke-super",
    "return", "return-void", "return-object", "return-wide",
    "if-eq", "if-ne", "if-lt", "if-ge", "if-gt", "if-le",
    "if-eqz", "if-nez", "if-ltz", "if-gez", "if-gtz", "if-lez",
    "goto", "goto/16", "goto/32", "const/4", "const/16", "const", "const-string",
    "iget", "iget-object", "iget-boolean", "iput", "iput-object", "sget", "sget-object",
    "new-instance", "check-cast", "move-result", "move-result-object", "throw"
)

private val JAVA_KEYWORDS = setOf(
    "package", "import", "public", "private", "protected", "static", "final",
    "abstract", "class", "extends", "implements", "void", "boolean", "int",
    "long", "float", "double", "byte", "char", "short", "if", "else", "return", "goto", "new"
)

private fun highlightLine(
    line: String,
    language: CodeLanguage,
    palette: CodeSyntaxPalette,
    query: String,
    highlightBg: Color
): AnnotatedString = buildAnnotatedString {
    append(line)

    when (language) {
        CodeLanguage.XML -> {
            if (line.trimStart().startsWith("<!--")) {
                addStyle(SpanStyle(color = palette.comment), 0, line.length)
            } else {
                Regex("</?[\\w:.-]+|/?>").findAll(line).forEach { m ->
                    addStyle(SpanStyle(color = palette.tag, fontWeight = FontWeight.Medium), m.range.first, m.range.last + 1)
                }
                Regex("\\b[\\w:.-]+(?==)").findAll(line).forEach { m ->
                    addStyle(SpanStyle(color = palette.attr), m.range.first, m.range.last + 1)
                }
                Regex("\"[^\"]*\"").findAll(line).forEach { m ->
                    addStyle(SpanStyle(color = palette.string), m.range.first, m.range.last + 1)
                }
            }
        }
        CodeLanguage.SMALI -> {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("#")) {
                addStyle(SpanStyle(color = palette.comment), 0, line.length)
            } else {
                Regex("\"[^\"]*\"").findAll(line).forEach { m ->
                    addStyle(SpanStyle(color = palette.string), m.range.first, m.range.last + 1)
                }
                Regex("\\b[vp]\\d+\\b").findAll(line).forEach { m ->
                    addStyle(SpanStyle(color = palette.register, fontWeight = FontWeight.Medium), m.range.first, m.range.last + 1)
                }
                Regex("L[\\w/$-]+;").findAll(line).forEach { m ->
                    addStyle(SpanStyle(color = palette.type), m.range.first, m.range.last + 1)
                }
                Regex("(?::cond_|\\:goto_|0x)[0-9a-fA-F]+").findAll(line).forEach { m ->
                    addStyle(SpanStyle(color = palette.number), m.range.first, m.range.last + 1)
                }
                Regex("[\\w./-]+").findAll(line).forEach { m ->
                    if (m.value in SMALI_KEYWORDS) {
                        addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.Medium), m.range.first, m.range.last + 1)
                    }
                }
            }
        }
        CodeLanguage.JAVA -> {
            val commentIdx = line.indexOf("//")
            if (commentIdx >= 0) {
                addStyle(SpanStyle(color = palette.comment), commentIdx, line.length)
            }
            val codeEnd = if (commentIdx >= 0) commentIdx else line.length
            Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b").findAll(line.substring(0, codeEnd)).forEach { m ->
                if (m.value in JAVA_KEYWORDS) {
                    addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
                }
            }
        }
        CodeLanguage.HEX -> {
            if (line.length >= 6) {
                addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.Medium), 0, 6)
            }
            val pipeIdx = line.indexOf('|')
            if (pipeIdx > 0) {
                addStyle(SpanStyle(color = palette.string), pipeIdx, line.length)
            }
        }
        CodeLanguage.PLAIN -> Unit
    }

    if (query.isNotBlank()) {
        var idx = line.indexOf(query, ignoreCase = true)
        while (idx >= 0) {
            addStyle(SpanStyle(background = highlightBg, fontWeight = FontWeight.Bold), idx, idx + query.length)
            idx = line.indexOf(query, startIndex = idx + query.length, ignoreCase = true)
        }
    }
}
