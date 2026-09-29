package com.ember.companion.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val EmberDark = darkColorScheme(
    primary = Color(0xFFE5583E),
    onPrimary = Color(0xFF1E0A06),
    primaryContainer = Color(0xFF5E2015),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFFFB380),
    onSecondary = Color(0xFF2C1508),
    secondaryContainer = Color(0xFF522A14),
    onSecondaryContainer = Color(0xFFFFDCC7),
    background = Color(0xFF0F0E13),
    onBackground = Color(0xFFEFE8E4),
    surface = Color(0xFF16141D),
    onSurface = Color(0xFFEFE8E4),
    surfaceVariant = Color(0xFF231E29),
    onSurfaceVariant = Color(0xFFC3B9B4),
    outline = Color(0xFF42332F),
    outlineVariant = Color(0xFF2A201D),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0A05),
)

private val EmberLight = lightColorScheme(
    primary = Color(0xFFB03A22),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD1),
    onPrimaryContainer = Color(0xFF3E0A02),
    secondary = Color(0xFF8A4B22),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC4),
    onSecondaryContainer = Color(0xFF2B1508),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF221A18),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF221A18),
    surfaceVariant = Color(0xFFF4DED8),
    onSurfaceVariant = Color(0xFF53433F),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD7C2BD),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val EmberType = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
)

@Composable
fun EmberTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) EmberDark else EmberLight
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = scheme, typography = EmberType, content = content)
}
