package im.commonplace.reader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private enum class Destination(val label: String) {
    LIBRARY("Library"),
    SETTINGS("Settings & data"),
}

@Composable
fun CommonplaceApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.LIBRARY) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = {
                            NavigationGlyph(
                                modifier = Modifier.size(22.dp),
                                destination = item,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { contentPadding ->
        when (destination) {
            Destination.LIBRARY -> LibraryScreen(contentPadding)
            Destination.SETTINGS -> SettingsScreen(contentPadding)
        }
    }
}

@Composable
private fun NavigationGlyph(
    modifier: Modifier = Modifier,
    destination: Destination,
) {
    val ink = if (destination == Destination.LIBRARY) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()

        when (destination) {
            Destination.LIBRARY -> {
                val fold = size.width / 2f
                val book = Path().apply {
                    moveTo(2f, 3f)
                    quadraticTo(fold, 1f, fold, 5f)
                    quadraticTo(fold, 1f, size.width - 2f, 3f)
                    lineTo(size.width - 2f, size.height - 3f)
                    quadraticTo(fold, size.height - 5f, fold, size.height - 1f)
                    quadraticTo(fold, size.height - 5f, 2f, size.height - 3f)
                    close()
                }
                drawPath(book, ink, style = Stroke(width = stroke))
                drawLine(ink, Offset(fold, 4f), Offset(fold, size.height - 2f), stroke)
            }

            Destination.SETTINGS -> {
                val rows = listOf(size.height * .25f, size.height * .5f, size.height * .75f)
                rows.forEachIndexed { index, y ->
                    drawLine(ink, Offset(3f, y), Offset(size.width - 3f, y), stroke)
                    val knobX = size.width * if (index == 1) .38f else .68f
                    drawCircle(ink, radius = stroke * 1.7f, center = Offset(knobX, y))
                }
            }
        }
    }
}
