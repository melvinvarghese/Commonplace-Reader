package im.commonplace.reader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Forest = Color(0xFF294C3B)
private val Paper = Color(0xFFF6F1E7)
private val Ink = Color(0xFF292D26)
private val Sage = Color(0xFFDDE5DA)
private val Brass = Color(0xFF716044)
private val Night = Color(0xFF17201B)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Sage,
    onPrimaryContainer = Color(0xFF183527),
    inversePrimary = Color(0xFFB4CEB9),
    secondary = Color(0xFF4D6252),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E9DE),
    onSecondaryContainer = Color(0xFF283C2D),
    tertiary = Brass,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEEE2CA),
    onTertiaryContainer = Color(0xFF463A24),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFCF5),
    onSurface = Ink,
    surfaceVariant = Color(0xFFECE9DE),
    onSurfaceVariant = Color(0xFF535B50),
    surfaceTint = Forest,
    surfaceDim = Color(0xFFDDD9CE),
    surfaceBright = Color(0xFFFFFCF5),
    surfaceContainerLowest = Color(0xFFFFFEF9),
    surfaceContainerLow = Color(0xFFF4EFE5),
    surfaceContainer = Color(0xFFEEEADF),
    surfaceContainerHigh = Color(0xFFE8E4D9),
    surfaceContainerHighest = Color(0xFFE2DED3),
    outline = Color(0xFF777D70),
    outlineVariant = Color(0xFFCCCFC1),
    inverseSurface = Color(0xFF2D362E),
    inverseOnSurface = Color(0xFFF3F0E6),
    error = Color(0xFF9C352F),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF651B17),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4CEB9),
    onPrimary = Color(0xFF173626),
    primaryContainer = Color(0xFF314D3B),
    onPrimaryContainer = Color(0xFFD8E9D7),
    inversePrimary = Forest,
    secondary = Color(0xFFB8C9B5),
    onSecondary = Color(0xFF253A2B),
    secondaryContainer = Color(0xFF344737),
    onSecondaryContainer = Color(0xFFDFE9D8),
    tertiary = Color(0xFFD7C49D),
    onTertiary = Color(0xFF3C301C),
    tertiaryContainer = Color(0xFF51442D),
    onTertiaryContainer = Color(0xFFF2E3C5),
    background = Night,
    onBackground = Color(0xFFEAE8DC),
    surface = Color(0xFF1D2720),
    onSurface = Color(0xFFEAE8DC),
    surfaceVariant = Color(0xFF354035),
    onSurfaceVariant = Color(0xFFC0C8B9),
    surfaceTint = Color(0xFFB4CEB9),
    surfaceDim = Color(0xFF141C17),
    surfaceBright = Color(0xFF3A443C),
    surfaceContainerLowest = Color(0xFF111914),
    surfaceContainerLow = Color(0xFF1B251E),
    surfaceContainer = Color(0xFF222D25),
    surfaceContainerHigh = Color(0xFF2C372E),
    surfaceContainerHighest = Color(0xFF374238),
    outline = Color(0xFF8D9888),
    outlineVariant = Color(0xFF455143),
    inverseSurface = Color(0xFFE1E5D8),
    inverseOnSurface = Color(0xFF293229),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF601B16),
    errorContainer = Color(0xFF7E2C25),
    onErrorContainer = Color(0xFFFFDAD4),
)

private val LibraryTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 25.sp, lineHeight = 33.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 19.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
)

@Composable
fun CommonplaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(
        handleColor = colors.primary,
        backgroundColor = colors.primary.copy(alpha = 0.25f),
    )) {
        MaterialTheme(
            colorScheme = colors,
            typography = LibraryTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(4.dp),
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
                extraLarge = RoundedCornerShape(20.dp),
            ),
            content = content,
        )
    }
}
