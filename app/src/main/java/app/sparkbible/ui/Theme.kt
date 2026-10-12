package app.sparkbible.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.sparkbible.R
import app.sparkbible.data.Settings
import app.sparkbible.data.ThemePref

data class SparkPalette(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val sage: Color,
    val onSage: Color,
    val sageSoft: Color,
    val ember: Color,
    val emberSoft: Color,
    val sky: Color,
    val star: Color,
    val dark: Boolean,
)

val LocalPalette = staticCompositionLocalOf {
    SparkPalette(
        bg = Color(0xFFF3EEE4),
        surface = Color(0xFFFAF6EF),
        ink = Color(0xFF221C16),
        muted = Color(0xFF5E554B),
        line = Color(0xFFE2D6C6),
        sage = Color(0xFF2C6848),
        onSage = Color(0xFFF7F3EA),
        sageSoft = Color(0xFFE4F0E6),
        ember = Color(0xFF9A5B2A),
        emberSoft = Color(0xFFF8EAD7),
        sky = Color(0xFF1B2420),
        star = Color(0xFFF4EFE6),
        dark = false,
    )
}

val Literata = FontFamily(Font(R.font.literata))
val Fraunces = FontFamily(Font(R.font.fraunces))
val Lexend = FontFamily(Font(R.font.lexend))

fun paletteFor(pref: ThemePref): SparkPalette = when (pref) {
    ThemePref.LIGHT -> SparkPalette(
        bg = Color(0xFFF3EEE4),
        surface = Color(0xFFFAF6EF),
        ink = Color(0xFF221C16),
        muted = Color(0xFF5E554B),
        line = Color(0xFFE2D6C6),
        sage = Color(0xFF2C6848),
        onSage = Color(0xFFF7F3EA),
        sageSoft = Color(0xFFE4F0E6),
        ember = Color(0xFF9A5B2A),
        emberSoft = Color(0xFFF8EAD7),
        sky = Color(0xFF1B2420),
        star = Color(0xFFF4EFE6),
        dark = false,
    )
    ThemePref.SEPIA -> SparkPalette(
        bg = Color(0xFFF4E2C0),
        surface = Color(0xFFF8EDD4),
        ink = Color(0xFF3A2A16),
        muted = Color(0xFF7C6244),
        line = Color(0xFFE4CFAA),
        sage = Color(0xFF3F6B45),
        onSage = Color(0xFFF8EDD4),
        sageSoft = Color(0xFFEFE0C0),
        ember = Color(0xFF8D4E22),
        emberSoft = Color(0xFFF3DDB8),
        sky = Color(0xFF2A2118),
        star = Color(0xFFF8EDD4),
        dark = false,
    )
    ThemePref.DARK -> SparkPalette(
        bg = Color(0xFF141210),
        surface = Color(0xFF1C1916),
        ink = Color(0xFFF4EFE6),
        muted = Color(0xFFB3A89A),
        line = Color(0xFF2E2924),
        sage = Color(0xFF8FBF9F),
        onSage = Color(0xFF14241B),
        sageSoft = Color(0xFF1D2C24),
        ember = Color(0xFFE0B07A),
        emberSoft = Color(0xFF2C241C),
        sky = Color(0xFF101614),
        star = Color(0xFFF4EFE6),
        dark = true,
    )
}

@Composable
fun SparkTheme(settings: Settings, content: @Composable () -> Unit) {
    val palette = paletteFor(settings.theme)
    val font = if (settings.dyslexia) Lexend else Literata
    val scheme = if (palette.dark) {
        darkColorScheme(
            primary = palette.sage,
            onPrimary = palette.onSage,
            secondary = palette.ember,
            background = palette.bg,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.sageSoft,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
        )
    } else {
        lightColorScheme(
            primary = palette.sage,
            onPrimary = palette.onSage,
            secondary = palette.ember,
            background = palette.bg,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.sageSoft,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
        )
    }
    val type = Typography(
        displaySmall = TextStyle(fontFamily = if (settings.dyslexia) Lexend else Fraunces, fontSize = 34.sp, lineHeight = 40.sp, color = palette.ink),
        headlineSmall = TextStyle(fontFamily = if (settings.dyslexia) Lexend else Fraunces, fontSize = 24.sp, lineHeight = 30.sp, color = palette.ink),
        titleMedium = TextStyle(fontFamily = font, fontSize = 18.sp, lineHeight = 24.sp, color = palette.ink),
        bodyLarge = TextStyle(fontFamily = font, fontSize = 17.sp, lineHeight = 26.sp, color = palette.ink),
        bodyMedium = TextStyle(fontFamily = font, fontSize = 15.sp, lineHeight = 22.sp, color = palette.ink),
        labelLarge = TextStyle(fontFamily = font, fontSize = 15.sp, lineHeight = 20.sp, color = palette.ink),
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !palette.dark
            controller.isAppearanceLightNavigationBars = !palette.dark
        }
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}

@Composable
fun readingStyle(settings: Settings): TextStyle {
    val font = if (settings.dyslexia) Lexend else Literata
    return TextStyle(
        fontFamily = font,
        fontSize = settings.fontSize.sp,
        lineHeight = (settings.fontSize * settings.lineHeight).sp,
        color = LocalPalette.current.ink,
    )
}
