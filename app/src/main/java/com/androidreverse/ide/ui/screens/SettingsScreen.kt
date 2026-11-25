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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WrapText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.androidreverse.ide.R
import com.androidreverse.ide.data.EditorSettings
import com.androidreverse.ide.ui.components.TintedIconBadge
import com.androidreverse.ide.ui.theme.DeepSlateCanvas
import com.androidreverse.ide.ui.theme.OceanPrimary
import com.androidreverse.ide.ui.theme.SkyCanvasLight
import com.androidreverse.ide.ui.theme.SkyPrimaryDark
import com.androidreverse.ide.ui.theme.SlateCardDark
import com.androidreverse.ide.ui.theme.SurfaceWhite

@Composable
fun SettingsScreen(
    settings: EditorSettings,
    onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.title_settings),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Visual mock-phone theme picker tiles (per owner preference: never a text toggle)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.section_theme),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ThemePhoneTile(
                            label = stringResource(R.string.theme_light),
                            selected = !settings.isDarkTheme,
                            previewBg = SkyCanvasLight,
                            previewCard = SurfaceWhite,
                            previewAccent = OceanPrimary,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateSettings { it.copy(isDarkTheme = false) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_tile_light")
                        )
                        ThemePhoneTile(
                            label = stringResource(R.string.theme_dark),
                            selected = settings.isDarkTheme,
                            previewBg = DeepSlateCanvas,
                            previewCard = SlateCardDark,
                            previewAccent = SkyPrimaryDark,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateSettings { it.copy(isDarkTheme = true) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_tile_dark")
                        )
                    }
                }
            }

            // Editor controls card
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.section_editor),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Font size stepper row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TintedIconBadge(
                                    icon = Icons.Rounded.FormatSize,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.label_font_size),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onUpdateSettings {
                                            it.copy(codeFontSp = (it.codeFontSp - 1).coerceAtLeast(12))
                                        }
                                    },
                                    modifier = Modifier.size(48.dp).testTag("font_size_minus")
                                ) {
                                    Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.cd_decrease))
                                }
                                Text(
                                    text = "${settings.codeFontSp}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onUpdateSettings {
                                            it.copy(codeFontSp = (it.codeFontSp + 1).coerceAtMost(20))
                                        }
                                    },
                                    modifier = Modifier.size(48.dp).testTag("font_size_plus")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_increase))
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            // Line numbers toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TintedIconBadge(
                                    icon = Icons.Rounded.FormatListNumbered,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.label_line_numbers),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = settings.showLineNumbers,
                                    onCheckedChange = { checked ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onUpdateSettings { it.copy(showLineNumbers = checked) }
                                    },
                                    modifier = Modifier.testTag("switch_line_numbers")
                                )
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            // Word wrap toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TintedIconBadge(
                                    icon = Icons.Rounded.WrapText,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.label_word_wrap),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = settings.wrapLines,
                                    onCheckedChange = { checked ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onUpdateSettings { it.copy(wrapLines = checked) }
                                    },
                                    modifier = Modifier.testTag("switch_word_wrap")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePhoneTile(
    label: String,
    selected: Boolean,
    previewBg: Color,
    previewCard: Color,
    previewAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (selected) 3.dp else 1.dp

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Static calm mini-phone preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(previewBg)
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(previewAccent)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(previewCard)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(previewCard)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(22.dp)
                    ) {}
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
