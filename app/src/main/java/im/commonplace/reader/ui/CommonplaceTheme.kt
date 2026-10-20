package im.commonplace.reader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Forest = Color(0xFF315A49)
private val DeepForest = Color(0xFF1F3C32)
private val Paper = Color(0xFFF5F2E8)
private val Ink = Color(0xFF242B25)
private val MutedInk = Color(0xFF656D62)
private val PaleSage = Color(0xFFE4E9DE)
private val Terracotta = Color(0xFF9D5944)
private val Night = Color(0xFF151C18)
private val NightSurface = Color(0xFF202923)
private val NightInk = Color(0xFFE9ECE3)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    secondary = Terracotta,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFEFA),
    onSurface = Ink,
    surfaceVariant = PaleSage,
    onSurfaceVariant = MutedInk,
    secondaryContainer = PaleSage,
    onSecondaryContainer = DeepForest,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6D0B7),
    onPrimary = DeepForest,
    secondary = Color(0xFFE5A78E),
    onSecondary = Night,
    background = Night,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = Color(0xFF303B33),
    onSurfaceVariant = Color(0xFFBBC5B9),
    secondaryContainer = Color(0xFF334438),
    onSecondaryContainer = NightInk,
)

@Composable
fun CommonplaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography.copy(
            headlineLarge = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.4).sp,
            ),
            headlineSmall = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 25.sp,
                lineHeight = 31.sp,
            ),
        ),
        content = content,
    )
}
