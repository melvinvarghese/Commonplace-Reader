package im.commonplace.reader.reader.pdf

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import im.commonplace.reader.R
import im.commonplace.reader.data.SourceAvailability
import im.commonplace.reader.reader.*

@Composable
fun PdfReaderScreen(activity: FragmentActivity, model: ReaderViewModel, state: ReaderUiState, onLibrary: () -> Unit) {
    val source = state.source ?: return
    var notice by remember(source.id) { mutableStateOf<String?>(null) }
    var showSaved by remember { mutableStateOf(false) }
    var annotation by remember { mutableStateOf("") }
    val host = remember(source.id) {
        source.takeIf { it.availability == SourceAvailability.AVAILABLE }?.let {
            PdfHost(activity, it, state.initialLocation, state.highlights,
                event = { event -> model.onReaderEvent(it.id, event) }, notice = { message -> notice = message })
        }
    }
    DisposableEffect(host) { onDispose { host?.close() } }
    val ready = host?.ready?.collectAsStateWithLifecycle()?.value ?: false
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TextButton(onClick = onLibrary, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Library") }
            TextButton(onClick = { host?.toggleSearch() }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp)) { Text("Search PDF") }
            TextButton(onClick = { host?.navigate(-1) }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp)) { Text("Previous page") }
            TextButton(onClick = { host?.navigate(1) }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp)) { Text("Next page") }
            TextButton(onClick = { host?.zoom(1.25f) }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp)) { Text("Zoom in") }
            TextButton(onClick = { host?.zoom(0.8f) }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp)) { Text("Zoom out") }
            TextButton(onClick = { showSaved = !showSaved }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Saved items") }
        }
        Text("Experimental AndroidX PDF comparison — ${source.title}", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
        Text(state.message ?: notice ?: "Opening local PDF…", Modifier.fillMaxWidth().heightIn(max = 100.dp).verticalScroll(rememberScrollState()).padding(16.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (host != null) AndroidView(factory = { context -> FragmentContainerView(context).apply {
                id = R.id.pdf_container; doOnAttach { host.attach(id) }
            } }, modifier = Modifier.fillMaxSize())
            if (state.phase == ReaderPhase.Opening) CircularProgressIndicator(Modifier.padding(24.dp))
        }
        if (showSaved) Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Saved writing remains readable even when its location is unresolved.")
            state.entries.forEach { entry ->
                entry.quotation?.let { Text(it) }; entry.body?.let { Text(it) }
                entry.location?.let { location -> TextButton(onClick = { host?.go(location) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Check saved passage") } }
            }
            Text("Precise highlights, durable PDF jump-back, bookmarks and progress are not proven in this candidate.")
            OutlinedTextField(annotation, { annotation = it }, label = { Text("Source annotation") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { model.saveAnnotation(annotation) }, enabled = annotation.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text("Save annotation") }
        }
    }
}
