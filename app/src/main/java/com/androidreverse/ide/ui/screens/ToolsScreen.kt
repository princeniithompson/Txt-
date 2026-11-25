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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidreverse.ide.R
import com.androidreverse.ide.core.binary.CertificateInfo
import com.androidreverse.ide.core.tools.ConverterMode
import com.androidreverse.ide.core.tools.DataConverter
import com.androidreverse.ide.ui.components.AppIcons
import com.androidreverse.ide.ui.components.TintedIconBadge
import com.androidreverse.ide.ui.theme.JetBrainsMonoFamily
import com.androidreverse.ide.ui.theme.StatusGood

@Composable
fun ToolsScreen(
    certificates: List<CertificateInfo>,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current

    var activeSection by rememberSaveable { mutableIntStateOf(0) }
    var selectedModeIndex by rememberSaveable { mutableIntStateOf(0) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var xorKey by rememberSaveable { mutableStateOf("0x5A") }

    val mode = ConverterMode.entries[selectedModeIndex]
    val outputText = remember(inputText, mode, xorKey) {
        DataConverter.convert(inputText, mode, xorKey)
    }
    val checksums = remember(inputText) {
        if (inputText.isEmpty()) null else DataConverter.checksums(inputText.toByteArray())
    }
    val colorPreview = remember(inputText) {
        DataConverter.parseColorHexOrNull(inputText)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.nav_tools),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = activeSection == 0,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeSection = 0
                        },
                        label = { Text(stringResource(R.string.title_converter)) },
                        modifier = Modifier.testTag("tools_converter_tab")
                    )
                    FilterChip(
                        selected = activeSection == 1,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeSection = 1
                        },
                        label = { Text(stringResource(R.string.title_certificate)) },
                        modifier = Modifier.testTag("tools_certs_tab")
                    )
                }
            }

            if (activeSection == 0) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ConverterMode.entries.size) { index ->
                            val itemMode = ConverterMode.entries[index]
                            FilterChip(
                                selected = selectedModeIndex == index,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedModeIndex = index
                                },
                                label = { Text(itemMode.label) }
                            )
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text(stringResource(R.string.hint_input_text)) },
                                minLines = 3,
                                maxLines = 6,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("converter_input")
                            )

                            if (mode == ConverterMode.XOR_HEX) {
                                OutlinedTextField(
                                    value = xorKey,
                                    onValueChange = { xorKey = it },
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.hint_xor_key)) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (colorPreview != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(colorPreview))
                                    )
                                    Text(
                                        text = "#${colorPreview.toString(16).uppercase().padStart(8, '0')}",
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.label_result),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (outputText.isNotEmpty()) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            clipboard.setText(AnnotatedString(outputText))
                                        }
                                    },
                                    modifier = Modifier.testTag("converter_copy_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = stringResource(R.string.action_copy)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = outputText.ifEmpty { "—" },
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                }

                if (checksums != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.label_hashes),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                HashRow("SHA-256", checksums.sha256) {
                                    clipboard.setText(AnnotatedString(checksums.sha256))
                                }
                                HashRow("MD5", checksums.md5) {
                                    clipboard.setText(AnnotatedString(checksums.md5))
                                }
                                HashRow("CRC32", checksums.crc32) {
                                    clipboard.setText(AnnotatedString(checksums.crc32))
                                }
                            }
                        }
                    }
                }
            } else {
                if (certificates.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TintedIconBadge(
                                    icon = AppIcons.Certificate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.action_open_apk),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(certificates, key = { "${it.source}:${it.sha256}" }) { cert ->
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TintedIconBadge(
                                        icon = AppIcons.Certificate,
                                        contentDescription = null,
                                        tint = StatusGood,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cert.algorithm,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${cert.validFrom} → ${cert.validUntil}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                CertLine("Subject", cert.subject)
                                CertLine("Issuer", cert.issuer)
                                HashRow("SHA-256", cert.sha256) {
                                    clipboard.setText(AnnotatedString(cert.sha256))
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
private fun HashRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onCopy, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.action_copy),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CertLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
