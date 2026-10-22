package im.commonplace.reader.reader.pdf

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import im.commonplace.reader.reader.*

@Composable
fun PdfReaderScreen(activity: FragmentActivity, model: ReaderViewModel, state: ReaderUiState, onLibrary: () -> Unit) {
    val source = state.source
    var notice by remember { mutableStateOf<String?>(null) }
    var savedItems by remember { mutableStateOf(false) }
    val host = remember(source?.id, state.readerSession) {
        source?.let {
            HybridPdfView(activity, it, { event -> model.onReaderEvent(it.id, event, state.readerSession) }, { notice = it })
        }
    }
    DisposableEffect(host) { onDispose { host?.close() } }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(onClick = onLibrary, modifier = Modifier.heightIn(min = 48.dp)) { Text("Library") }
                Text(source?.title ?: "Opening PDF", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(12.dp))
            }
            Text("Disposable PDF.js + AndroidPdfViewer prototype. PDF: NO SELECTION.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { host?.navigate(-1) }, enabled = host?.ready == true && host.pageIndex > 0,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Previous page") }
                TextButton(onClick = { host?.navigate(1) }, enabled = host?.ready == true && host.pageIndex + 1 < host.pageCount,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Next page") }
                TextButton(onClick = { host?.zoomToggle() }, enabled = host?.ready == true,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(if (host?.zoom == 2f) "Zoom 2×" else "Zoom 1×") }
                TextButton(onClick = { host?.selectMode(true) }, enabled = host?.ready == true,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Select text") }
                TextButton(onClick = { host?.selectMode(false) }, enabled = host?.ready == true,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Pan PDF") }
                TextButton(onClick = { host?.capture() }, enabled = host?.ready == true,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Keep selected passage") }
                TextButton(onClick = { savedItems = !savedItems }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Saved items") }
            }
            if (host?.ready == true) Text("Page ${host.pageIndex + 1} of ${host.pageCount}",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            (notice ?: state.message)?.let { Text(it, modifier = Modifier.fillMaxWidth().heightIn(max = 80.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall) }
            if (state.selection != null && state.captureStatus != CaptureStatus.Saved) {
                Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Text(state.selection.quotation)
                    OutlinedTextField(state.reflection.orEmpty(), { model.updateDraft(it, state.colorArgb) },
                        label = { Text("Reflection (optional)") }, modifier = Modifier.fillMaxWidth().testTag("reader-reflection"))
                    Button(onClick = { model.saveSelection(state.reflection, state.colorArgb) },
                        enabled = state.captureStatus != CaptureStatus.Pending,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("reader-save")) { Text("Save in Commonplace") }
                }
            }
            if (savedItems) {
                Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    if (state.entries.isEmpty()) Text("No saved items yet.")
                    state.entries.forEach { entry ->
                        entry.quotation?.let { Text(it) }
                        entry.body?.let { Text(it) }
                        entry.location?.let { location ->
                            TextButton(onClick = { host?.go(location); savedItems = false },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("reader-open-capture")) { Text("Open saved passage") }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (host != null) AndroidView(factory = { host }, modifier = Modifier.fillMaxSize().testTag("hybrid-pdf-view"))
            }
        }
    }
}
