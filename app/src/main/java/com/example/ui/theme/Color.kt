package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Technical Precision Bento Palette
val CanvasBg = Color(0xFF09090B)
val SurfaceCard = Color(0xFF0C0C0C)
val SurfaceRaised = Color(0xFF141416)
val SurfaceContainerLow = Color(0xFF1C1B1D)
val SurfaceContainerHigh = Color(0xFF2A2A2C)

val SurfaceBorder = Color(0xFF222222)
val BorderSubtle = Color(0x14FFFFFF)
val BorderHighlight = Color(0x2EFFFFFF)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8B8B8B)
val TextMuted = Color(0xFF52525B)

val AccentCyan = Color(0xFF38BDF8)
val AccentCyanContainer = Color(0xFF004965)
val AccentIce = Color(0xFF60A5FA)
val AccentGlow = Color(0x2638BDF8)

val StatusVerified = Color(0xFF10B981)
val StatusVerifiedBg = Color(0x1A10B981)
val StatusWarning = Color(0xFFF59E0B)
val StatusWarningBg = Color(0x1AF59E0B)
val StatusError = Color(0xFFFFB4AB)
val StatusErrorContainer = Color(0xFF93000A)
val StatusErrorBg = Color(0x1AFFB4AB)

val OrganizerDarkColorScheme = darkColorScheme(
    primary = TextPrimary,
    onPrimary = Color(0xFF000000),
    primaryContainer = AccentCyan,
    onPrimaryContainer = Color(0xFF002636),
    secondary = AccentCyan,
    onSecondary = Color(0xFF00354A),
    secondaryContainer = SurfaceRaised,
    onSecondaryContainer = AccentIce,
    tertiary = AccentIce,
    onTertiary = Color(0xFF003355),
    background = CanvasBg,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = CanvasBg,
    surfaceContainerLow = SurfaceCard,
    surfaceContainer = SurfaceRaised,
    surfaceContainerHigh = SurfaceContainerLow,
    surfaceContainerHighest = SurfaceContainerHigh,
    outline = SurfaceBorder,
    outlineVariant = BorderSubtle,
    error = StatusError,
    onError = Color(0xFF690005),
    errorContainer = StatusErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6)
)
