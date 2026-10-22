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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import im.commonplace.reader.CommonplaceApplication
import im.commonplace.reader.notebook.NotebookViewModel

private enum class Destination(val label: String) {
    LIBRARY("Library"),
    NOTEBOOK("Commonplace"),
    SETTINGS("Settings & data"),
}

@Composable
fun CommonplaceApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.LIBRARY) }
    var pending by rememberSaveable { mutableStateOf<Destination?>(null) }
    var promptShown by rememberSaveable { mutableStateOf(false) }
    val context=LocalContext.current
    val container=(context.applicationContext as CommonplaceApplication).container
    val notebook=viewModel { NotebookViewModel(container.notebookRepository) }
    val notebookState by notebook.state.collectAsStateWithLifecycle()
    val savedScreens=rememberSaveableStateHolder()
    LaunchedEffect(notebookState.editor,notebookState.busy,notebookState.leavePrompt,pending) {
        if(pending!=null && notebookState.leavePrompt) promptShown=true
        if(pending!=null && !notebookState.busy) {
            if(notebookState.editor==null) { destination=pending!!;pending=null;promptShown=false }
            else if(promptShown && !notebookState.leavePrompt) { pending=null;promptShown=false }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        enabled = !notebookState.busy,
                        onClick = {
                            if(item!=destination && destination==Destination.NOTEBOOK && notebookState.editor!=null) {
                                pending=item; notebook.requestLeave()
                            } else destination=item
                        },
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
        savedScreens.SaveableStateProvider(destination) {
            when (destination) {
                Destination.LIBRARY -> LibraryScreen(contentPadding)
                Destination.NOTEBOOK -> NotebookScreen(contentPadding,{ entryId ->
                    val entry=notebookState.detail?.takeIf { it.id==entryId } ?: notebookState.entries.find { it.id==entryId }
                    entry?.sourceId?.let { id -> context.startActivity(android.content.Intent(context,im.commonplace.reader.ReaderActivity::class.java).putExtra("sourceId",id)) }
                },notebook)
                Destination.SETTINGS -> SettingsScreen(contentPadding)
            }
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

            Destination.NOTEBOOK -> {
                drawRect(ink,topLeft=Offset(3f,2f),size=androidx.compose.ui.geometry.Size(size.width-6f,size.height-4f),style=Stroke(width=stroke))
                listOf(.3f,.5f,.7f).forEach { y -> drawLine(ink,Offset(7f,size.height*y),Offset(size.width-6f,size.height*y),stroke) }
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
