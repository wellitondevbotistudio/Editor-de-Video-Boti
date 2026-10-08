package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Palette alinhada fielmente à referência visual premium do Boti Video Editor
val BackgroundDark = Color(0xFF09090D)
val BackgroundElevated = Color(0xFF111117)
val SurfaceDark = Color(0xFF161620)
val SurfaceElevated = Color(0xFF1E1E2C)
val SurfaceHigh = Color(0xFF28283A)
val SurfaceGlass = Color(0xCC1A1A26)

val PrimaryPurple = Color(0xFF7C3AED)
val PrimaryPurpleVariant = Color(0xFF8B5CF6)
val PrimaryPurpleLight = Color(0xFFA78BFA)
val PrimaryPurpleDark = Color(0xFF5B21B6)
val PrimaryPurpleSoft = Color(0x337C3AED)
val OnPrimary = Color(0xFFFFFFFF)

// Accent Colors for Pro Multi-Track Timeline & Highlights
val AccentTeal = Color(0xFF14B8A6)
val AccentCyan = Color(0xFF06B6D4)
val AccentPink = Color(0xFFEC4899)
val AccentOrange = Color(0xFFF97316)
val AccentYellow = Color(0xFFEAB308)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA1A1AA)
val TextTertiary = Color(0xFF71717A)

val BorderSubtle = Color(0xFF232332)
val BorderStrong = Color(0xFF35354A)
val BorderHighlight = Color(0x4D8B5CF6)

val SuccessGreen = Color(0xFF22C55E)
val DangerRed = Color(0xFFEF4444)
val WarningAmber = Color(0xFFF59E0B)
val InfoBlue = Color(0xFF3B82F6)

val TimelineTrackBg = Color(0xFF13131D)
val TimelineClipBg = Color(0xFF1F1F2F)
val TimelineClipSelectedBorder = Color(0xFF8B5CF6)
val TimelinePlayhead = Color(0xFFFFFFFF)
val WaveformColor = Color(0xFF8B5CF6)
val WaveformSecondary = Color(0xFF14B8A6)
val GoldPremium = Color(0xFFF59E0B)
val GoldPremiumLight = Color(0xFFFCD34D)

val PillInactiveBg = Color(0xFF1A1A24)

// Gradient Brushes
val GradientPrimary = Brush.horizontalGradient(
    colors = listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED), Color(0xFF6D28D9))
)

val GradientPrimaryVertical = Brush.verticalGradient(
    colors = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
)

val GradientHeroCard = Brush.linearGradient(
    colors = listOf(Color(0xFF7C3AED), Color(0xFF5B21B6), Color(0xFF2E1065))
)

val GradientGold = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFBBF24), Color(0xFFF59E0B), Color(0xFFD97706))
)

val GradientGlass = Brush.verticalGradient(
    colors = listOf(Color(0x33FFFFFF), Color(0x05FFFFFF))
)

val GradientDarkFade = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color(0xFF09090D))
)
