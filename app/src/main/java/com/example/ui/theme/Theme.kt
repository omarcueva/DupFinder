package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class DupSweepSemanticColors(
  val originalAccent: Color,
  val originalContainer: Color,
  val duplicateAccent: Color,
  val duplicateContainer: Color,
  val warningAccent: Color,
  val warningContainer: Color,
)

val LocalDupSweepColors = staticCompositionLocalOf {
  DupSweepSemanticColors(
    originalAccent = OriginalEmeraldLight,
    originalContainer = OriginalContainerLight,
    duplicateAccent = DuplicateCoralLight,
    duplicateContainer = DuplicateContainerLight,
    warningAccent = WarningAmberLight,
    warningContainer = WarningContainerLight,
  )
}

private val DarkColorScheme = darkColorScheme(
  primary = DarkTealPrimary,
  onPrimary = DarkTealOnPrimary,
  primaryContainer = DarkTealPrimaryContainer,
  onPrimaryContainer = DarkTealOnPrimaryContainer,
  secondary = DarkSlateSecondary,
  onSecondary = DarkSlateOnSecondary,
  secondaryContainer = DarkSlateSecondaryContainer,
  onSecondaryContainer = DarkSlateOnSecondaryContainer,
  tertiary = DarkCoralTertiary,
  onTertiary = DarkCoralOnTertiary,
  tertiaryContainer = DarkCoralTertiaryContainer,
  onTertiaryContainer = DarkCoralOnTertiaryContainer,
  background = DarkBackground,
  onBackground = DarkOnBackground,
  surface = DarkSurface,
  onSurface = DarkOnSurface,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = DarkOnSurfaceVariant,
  outline = DarkOutline,
  outlineVariant = DarkOutlineVariant,
  surfaceContainerLow = DarkSurfaceContainerLow,
  surfaceContainer = DarkSurfaceContainer,
  surfaceContainerHigh = DarkSurfaceContainerHigh,
)

private val LightColorScheme = lightColorScheme(
  primary = TealPrimary,
  onPrimary = TealOnPrimary,
  primaryContainer = TealPrimaryContainer,
  onPrimaryContainer = TealOnPrimaryContainer,
  secondary = SlateSecondary,
  onSecondary = SlateOnSecondary,
  secondaryContainer = SlateSecondaryContainer,
  onSecondaryContainer = SlateOnSecondaryContainer,
  tertiary = CoralTertiary,
  onTertiary = CoralOnTertiary,
  tertiaryContainer = CoralTertiaryContainer,
  onTertiaryContainer = CoralOnTertiaryContainer,
  background = LightBackground,
  onBackground = LightOnBackground,
  surface = LightSurface,
  onSurface = LightOnSurface,
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = LightOnSurfaceVariant,
  outline = LightOutline,
  outlineVariant = LightOutlineVariant,
  surfaceContainerLow = LightSurfaceContainerLow,
  surfaceContainer = LightSurfaceContainer,
  surfaceContainerHigh = LightSurfaceContainerHigh,
)

@Composable
fun DupSweepTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val semanticColors = if (darkTheme) {
    DupSweepSemanticColors(
      originalAccent = OriginalEmeraldDark,
      originalContainer = OriginalContainerDark,
      duplicateAccent = DuplicateCoralDark,
      duplicateContainer = DuplicateContainerDark,
      warningAccent = WarningAmberDark,
      warningContainer = WarningContainerDark,
    )
  } else {
    DupSweepSemanticColors(
      originalAccent = OriginalEmeraldLight,
      originalContainer = OriginalContainerLight,
      duplicateAccent = DuplicateCoralLight,
      duplicateContainer = DuplicateContainerLight,
      warningAccent = WarningAmberLight,
      warningContainer = WarningContainerLight,
    )
  }

  CompositionLocalProvider(LocalDupSweepColors provides semanticColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      content = content,
    )
  }
}
