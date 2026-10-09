package com.raman.emergencyrelay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/*
 * ============================================================
 * EMERGENCY RELAY — FIXED THEME
 * ============================================================
 *
 * The app uses ONE colour scheme for everyone. It deliberately
 * ignores:
 *   - the device light/dark setting, and
 *   - Material You dynamic colour (Android 12+),
 * so the emergency interface looks identical and predictable on
 * every phone. This matters for a safety-critical app: users
 * must recognise the same SOS button and the same status colours
 * regardless of their personal theme.
 *
 * Material roles are mapped to the fixed palette in Color.kt.
 * Built on darkColorScheme because the base surface is a deep
 * teal-black (calm, low-glare, readable in low light).
 */

private val EmergencyColorScheme = darkColorScheme(
    primary = Teal,
    onPrimary = InkOnTeal,
    primaryContainer = TealWash,
    onPrimaryContainer = TealWashInk,

    secondary = Purple,
    onSecondary = InkOnColor,
    secondaryContainer = PurpleWash,
    onSecondaryContainer = PurpleWashInk,

    tertiary = Mint,
    onTertiary = InkOnColor,
    tertiaryContainer = MintWash,
    onTertiaryContainer = MintWashInk,

    error = Coral,
    onError = InkOnColor,
    errorContainer = CoralWash,
    onErrorContainer = CoralWashInk,

    background = BgBase,
    onBackground = Ink,

    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = Muted,

    outline = Slate,
    outlineVariant = Line
)

@Composable
fun EmergencyRelayTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EmergencyColorScheme,
        typography = Typography,
        content = content
    )
}
