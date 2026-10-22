package im.commonplace.reader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommonplaceThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothThemesKeepSmallTextLegibleAndTextSurfacesAccessible() {
        val dark = mutableStateOf(false)
        compose.setContent {
            CommonplaceTheme(darkTheme = dark.value) {
                val colors = MaterialTheme.colorScheme
                val typography = MaterialTheme.typography
                SideEffect {
                    // Small attribution and local-storage notices must remain readable.
                    assertTrue("Small body copy must be at least 14sp", typography.bodySmall.fontSize.value >= 14f)
                    assertTrue("Small labels must be at least 12sp", typography.labelSmall.fontSize.value >= 12f)
                    listOf(
                        colors.onBackground to colors.background,
                        colors.onSurface to colors.surface,
                        colors.onSurfaceVariant to colors.surfaceVariant,
                        colors.onPrimary to colors.primary,
                        colors.onPrimaryContainer to colors.primaryContainer,
                        colors.onSecondaryContainer to colors.secondaryContainer,
                        colors.onErrorContainer to colors.errorContainer,
                    ).forEach { (ink, paper) ->
                        assertTrue("Text contrast must meet 4.5:1 in dark=${dark.value}", contrast(ink, paper) >= 4.5f)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { dark.value = true }
        compose.waitForIdle()
    }

    private fun contrast(a: Color, b: Color): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }
}
