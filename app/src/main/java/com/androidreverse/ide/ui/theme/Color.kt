package com.androidreverse.ide.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Bright, natural ocean-blue & teal palette — zero violet/purple anywhere.
val OceanPrimary = Color(0xFF0B6BCB)
val OceanOnPrimary = Color(0xFFFFFFFF)
val OceanPrimaryContainer = Color(0xFFD6EAFF)
val OceanOnPrimaryContainer = Color(0xFF002850)

val TealSecondary = Color(0xFF00796B)
val TealOnSecondary = Color(0xFFFFFFFF)
val TealSecondaryContainer = Color(0xFFC8F3EC)
val TealOnSecondaryContainer = Color(0xFF002B25)

val AmberTertiary = Color(0xFFC27200)
val AmberOnTertiary = Color(0xFFFFFFFF)
val AmberTertiaryContainer = Color(0xFFFFE5BE)
val AmberOnTertiaryContainer = Color(0xFF3A1E00)

val SkyCanvasLight = Color(0xFFF2F8FC)
val SurfaceWhite = Color(0xFFFFFFFF)
val SurfaceContainerLight = Color(0xFFE7F1F8)
val SurfaceContainerHighLight = Color(0xFFDBE8F3)
val OnSurfaceDarkInk = Color(0xFF0E1B2A)
val OnSurfaceMutedInk = Color(0xFF42566B)
val OutlineCrispLight = Color(0xFFB5C7D8)
val OutlineVariantLight = Color(0xFFD4E2EE)

// Vivid lifted dark theme — no murky gray, no violet.
val SkyPrimaryDark = Color(0xFF5EB4FF)
val SkyOnPrimaryDark = Color(0xFF002B52)
val SkyPrimaryContainerDark = Color(0xFF0A4785)
val SkyOnPrimaryContainerDark = Color(0xFFD9EEFF)

val MintSecondaryDark = Color(0xFF46D9C7)
val MintOnSecondaryDark = Color(0xFF00322C)
val MintSecondaryContainerDark = Color(0xFF005147)
val MintOnSecondaryContainerDark = Color(0xFFC2F7EF)

val WarmAmberDark = Color(0xFFFFBA52)
val WarmOnAmberDark = Color(0xFF3E2200)
val WarmAmberContainerDark = Color(0xFF663B00)
val WarmOnAmberContainerDark = Color(0xFFFFE3BA)

val DeepSlateCanvas = Color(0xFF0B141F)
val SlateSurfaceDark = Color(0xFF101C2B)
val SlateCardDark = Color(0xFF17273A)
val SlateCardHighDark = Color(0xFF20344C)
val OnSurfaceLightInk = Color(0xFFEBF3FA)
val OnSurfaceMutedLight = Color(0xFFA2B8CE)
val OutlineDark = Color(0xFF3C5570)
val OutlineVariantDark = Color(0xFF24384E)

// Shared semantic status colors (always paired with distinct vector icon shapes)
val StatusGood = Color(0xFF168A45)
val StatusGoodContainerLight = Color(0xFFD9F7E4)
val StatusGoodContainerDark = Color(0xFF0E3E22)

val StatusWarn = Color(0xFFD97706)
val StatusWarnContainerLight = Color(0xFFFFEDD5)
val StatusWarnContainerDark = Color(0xFF4A2B04)

val StatusError = Color(0xFFDC2626)
val StatusErrorContainerLight = Color(0xFFFEE2E2)
val StatusErrorContainerDark = Color(0xFF521212)

val StatusNeutral = Color(0xFF64748B)

// Syntax highlighting tokens (natural hues only — zero violet)
data class CodeSyntaxPalette(
    val keyword: Color,
    val type: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val tag: Color,
    val attr: Color,
    val register: Color,
    val lineNumber: Color,
    val editorBackground: Color,
    val editorGutter: Color
)

val LightCodePalette = CodeSyntaxPalette(
    keyword = Color(0xFF0B6BCB),
    type = Color(0xFF00796B),
    string = Color(0xFF157A3D),
    number = Color(0xFFC26A00),
    comment = Color(0xFF5C7287),
    tag = Color(0xFF0B6BCB),
    attr = Color(0xFF00796B),
    register = Color(0xFFB45309),
    lineNumber = Color(0xFF688096),
    editorBackground = Color(0xFFFFFFFF),
    editorGutter = Color(0xFFF0F6FB)
)

val DarkCodePalette = CodeSyntaxPalette(
    keyword = Color(0xFF6CC1FF),
    type = Color(0xFF4DE0CE),
    string = Color(0xFF7EE081),
    number = Color(0xFFFFC15E),
    comment = Color(0xFF7D97B0),
    tag = Color(0xFFFFB74D),
    attr = Color(0xFF6CC1FF),
    register = Color(0xFF4DE0CE),
    lineNumber = Color(0xFF6C86A0),
    editorBackground = Color(0xFF0F1B29),
    editorGutter = Color(0xFF152436)
)

val AppLightColorScheme = lightColorScheme(
    primary = OceanPrimary,
    onPrimary = OceanOnPrimary,
    primaryContainer = OceanPrimaryContainer,
    onPrimaryContainer = OceanOnPrimaryContainer,
    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = TealOnSecondaryContainer,
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberTertiaryContainer,
    onTertiaryContainer = AmberOnTertiaryContainer,
    background = SkyCanvasLight,
    onBackground = OnSurfaceDarkInk,
    surface = SurfaceWhite,
    onSurface = OnSurfaceDarkInk,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = OnSurfaceMutedInk,
    surfaceContainerLowest = SurfaceWhite,
    surfaceContainerLow = SkyCanvasLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    outline = OutlineCrispLight,
    outlineVariant = OutlineVariantLight,
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusErrorContainerLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

val AppDarkColorScheme = darkColorScheme(
    primary = SkyPrimaryDark,
    onPrimary = SkyOnPrimaryDark,
    primaryContainer = SkyPrimaryContainerDark,
    onPrimaryContainer = SkyOnPrimaryContainerDark,
    secondary = MintSecondaryDark,
    onSecondary = MintOnSecondaryDark,
    secondaryContainer = MintSecondaryContainerDark,
    onSecondaryContainer = MintOnSecondaryContainerDark,
    tertiary = WarmAmberDark,
    onTertiary = WarmOnAmberDark,
    tertiaryContainer = WarmAmberContainerDark,
    onTertiaryContainer = WarmOnAmberContainerDark,
    background = DeepSlateCanvas,
    onBackground = OnSurfaceLightInk,
    surface = SlateSurfaceDark,
    onSurface = OnSurfaceLightInk,
    surfaceVariant = SlateCardDark,
    onSurfaceVariant = OnSurfaceMutedLight,
    surfaceContainerLowest = DeepSlateCanvas,
    surfaceContainerLow = SlateSurfaceDark,
    surfaceContainer = SlateCardDark,
    surfaceContainerHigh = SlateCardHighDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF450A0A),
    errorContainer = StatusErrorContainerDark,
    onErrorContainer = Color(0xFFFEE2E2)
)
