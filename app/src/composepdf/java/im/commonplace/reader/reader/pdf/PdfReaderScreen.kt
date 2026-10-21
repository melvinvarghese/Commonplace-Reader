package im.commonplace.reader.reader.pdf

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.nucleusframework.pdfium.PdfPage
import dev.nucleusframework.pdfium.rememberPdfReaderState
import im.commonplace.reader.reader.*

@Composable
fun PdfReaderScreen(activity: FragmentActivity, model: ReaderViewModel, state: ReaderUiState, onLibrary: () -> Unit) {
    val source = state.source
    val native = rememberPdfReaderState()
    var notice by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var savedItems by remember { mutableStateOf(false) }
    var annotation by remember { mutableStateOf("") }
    val host = remember(source?.id, native) {
        source?.let {
            PdfHost(activity.filesDir, it, native, state.initialLocation != null || state.highlights.isNotEmpty(),
                event = { event -> model.onReaderEvent(it.id, event) }, notice = { message -> notice = message })
        }
    }
    LaunchedEffect(host) { host?.open() }
    DisposableEffect(host) { onDispose { host?.close() } }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLibrary, modifier = Modifier.heightIn(min = 48.dp)) { Text("Library") }
                Text(source?.title ?: "Opening PDF", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Text("Experimental comparison only. Precise capture, saved highlights, bookmarks and progress are not proven.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { host?.navigate(-1) }, enabled = host?.ready == true && host.pageIndex > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text("Previous page") }
                TextButton(onClick = { host?.navigate(1) }, enabled = host?.ready == true && host.pageIndex + 1 < native.pageCount, modifier = Modifier.heightIn(min = 48.dp)) { Text("Next page") }
                TextButton(onClick = { host?.zoomIn() }, enabled = host?.ready == true, modifier = Modifier.heightIn(min = 48.dp)) { Text("Zoom in") }
                Text(if (host?.zoom == 2f) "Zoom 2×" else "Zoom 1×")
            }
            if (host?.ready == true) Text("Page ${host.pageIndex + 1} of ${native.pageCount}", modifier = Modifier.padding(horizontal = 16.dp))
            (state.message ?: notice)?.let { message ->
                Text(message, modifier = Modifier.fillMaxWidth().heightIn(max = 96.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, label = { Text("Exact PDF text") }, singleLine = true,
                    enabled = host?.ready == true && !host.searching, modifier = Modifier.weight(1f).testTag("pdf-search-query"))
                TextButton(onClick = { host?.search(query) }, enabled = host?.ready == true && !host.searching && query.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text("Search PDF") }
            }
            if (host?.searching == true) Text("Searching…", modifier = Modifier.padding(horizontal = 16.dp))
            host?.searchResult?.let { result ->
                if (host.matchingPages.isNotEmpty()) Text("Matches on physical pages: ${host.matchingPages.joinToString(", ") { (it + 1).toString() }}", modifier = Modifier.padding(horizontal = 16.dp))
                Text(result, modifier = Modifier.fillMaxWidth().heightIn(max = 96.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("pdf-search-result"))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                TextButton(onClick = {}, enabled = false, modifier = Modifier.heightIn(min = 48.dp)) { Text("Keep selected passage") }
                TextButton(onClick = {}, enabled = false, modifier = Modifier.heightIn(min = 48.dp)) { Text("Bookmark") }
                TextButton(onClick = { savedItems = !savedItems }, enabled = source != null, modifier = Modifier.heightIn(min = 48.dp)) { Text("Saved items") }
            }
            if (savedItems) {
                Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saved items for this PDF", style = MaterialTheme.typography.titleMedium)
                    if (state.entries.isEmpty() && state.bookmarks.isEmpty()) Text("No saved items yet.")
                    state.entries.forEach { entry ->
                        entry.quotation?.let { Text(it) }
                        entry.body?.let { Text(it) }
                        entry.location?.let { location ->
                            TextButton(onClick = { host?.go(location) }, modifier = Modifier.heightIn(min = 48.dp).testTag("reader-open-capture")) { Text("Check saved passage") }
                        }
                    }
                    state.bookmarks.forEachIndexed { index, bookmark ->
                        TextButton(onClick = { host?.go(bookmark.location) }, modifier = Modifier.heightIn(min = 48.dp).testTag("reader-open-bookmark")) { Text("Check saved bookmark ${index + 1}") }
                    }
                    OutlinedTextField(annotation, { annotation = it }, label = { Text("Source annotation") }, modifier = Modifier.fillMaxWidth().testTag("reader-annotation"))
                    Button(onClick = { model.saveAnnotation(annotation) }, enabled = annotation.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save annotation") }
                    Text("Annotations save only after confirmation; unsaved annotation text is not autosaved.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                if (host?.ready == true) {
                    PdfPage(native, host.pageIndex,
                        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = host.zoom; scaleY = host.zoom; clip = true }
                            .testTag("nucleus-pdf-page").semantics { contentDescription = "PDF page ${host.pageIndex + 1} of ${native.pageCount}" },
                        selectableText = false, linksEnabled = false, onLinkClick = { true })
                } else if (state.phase == ReaderPhase.Opening) CircularProgressIndicator(Modifier.padding(24.dp))
            }
        }
    }
}
