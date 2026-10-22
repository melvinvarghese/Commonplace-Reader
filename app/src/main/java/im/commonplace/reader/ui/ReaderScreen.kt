package im.commonplace.reader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import im.commonplace.reader.R
import im.commonplace.reader.data.ReaderPreferences
import im.commonplace.reader.reader.*
import im.commonplace.reader.reader.epub.ReadiumHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun ReaderScreen(activity: FragmentActivity, model: ReaderViewModel, preferences: ReaderPreferences, dark: Boolean, onLibrary: () -> Unit, exitLabel: String = "Library") {
    val state by model.uiState.collectAsStateWithLifecycle()
    key(state.source?.id,state.readerSession) {
        if (state.source?.format == DocumentFormat.PDF) im.commonplace.reader.reader.pdf.PdfReaderScreen(activity, model, state, onLibrary)
        else ReaderSourceScreen(activity, model, state, preferences, dark, onLibrary,exitLabel)
    }
}

@Composable
private fun ReaderSourceScreen(activity: FragmentActivity, model: ReaderViewModel, state: ReaderUiState, preferences: ReaderPreferences, dark: Boolean, onLibrary: () -> Unit, exitLabel: String) {
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    var externalUrl by remember { mutableStateOf<String?>(null) }
    var savedItems by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SavedLocation>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var annotation by remember { mutableStateOf("") }
    val source = state.source
    val host = remember(source?.id) {
        source?.takeIf { it.format == DocumentFormat.EPUB && it.availability == im.commonplace.reader.data.SourceAvailability.AVAILABLE }?.let {
            ReadiumHost(activity, it, state.currentLocation ?: state.initialLocation,
                event = { event -> model.onReaderEvent(it.id, event,state.readerSession) },
                externalLink = { url -> externalUrl = url }, notice = { message -> notice = message })
        }
    }
    DisposableEffect(host) { onDispose { searchJob?.cancel(); host?.close() } }
    SideEffect { host?.setDarkReading(dark) }
    LaunchedEffect(host, state.highlights) { host?.setHighlights(state.highlights) }
    val nativeReady = if (host != null) host.ready.collectAsStateWithLifecycle().value else false
    LaunchedEffect(host,nativeReady,state.pendingEntryJump?.requestId) {
        val jump=state.pendingEntryJump
        if(host!=null && source!=null && nativeReady && jump!=null && state.phase!=ReaderPhase.Error && state.phase!=ReaderPhase.Unsupported) {
            val arrived=host.goToEntry(jump.entryId,jump.location) {
                model.onEntryCommandAccepted(source.id,jump.requestId,state.readerSession)
            }
            model.onEntryNavigationResult(source.id,jump.requestId,state.readerSession,arrived!=null)
            if(arrived!=null) model.onReaderEvent(source.id,ReaderEvent.PositionChanged(source.id,arrived),state.readerSession)
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onLibrary, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text(exitLabel) }
                Text(source?.title ?: "Opening reader", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { host?.selectPassage() }, enabled = nativeReady, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Keep selected passage") }
                TextButton(onClick = model::addBookmark, enabled = nativeReady && state.currentLocation != null, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Bookmark") }
                TextButton(onClick = { searchVisible = true }, enabled = nativeReady && state.capabilities.search !is CapabilityStatus.Unavailable, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Search in book") }
                TextButton(onClick = { savedItems = !savedItems }, enabled = source != null, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Saved items") }
                TextButton(onClick = {
                    scope.launch {
                        try { preferences.setDarkReading(!dark) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { notice = "Reading preference not saved. Retry." }
                    }
                }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Dark reading") }
            }
            val message = notice ?: state.message
            if (message != null) Text(message, modifier = Modifier.fillMaxWidth().heightIn(max = 112.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val captureVisible = state.selection != null && state.captureStatus != CaptureStatus.Saved
                val panelVisible = savedItems || captureVisible
                val panelHeight = maxHeight * 0.48f
                val wide = maxWidth > maxHeight * 1.2f
                val panel: @Composable () -> Unit = {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (captureVisible) {
                            Text("Keep this passage", style = MaterialTheme.typography.titleLarge)
                            Text(state.selection!!.quotation, style = MaterialTheme.typography.bodyLarge)
                            OutlinedTextField(value = state.reflection.orEmpty(), onValueChange = { model.updateDraft(it, state.colorArgb) }, enabled = state.captureStatus != CaptureStatus.Pending, label = { Text("Reflection (optional)") }, modifier = Modifier.fillMaxWidth().testTag("reader-reflection"))
                            Text(when { state.captureStatus == CaptureStatus.Pending -> "Saving…"; state.captureStatus == CaptureStatus.Failed -> "Not saved. Retry below."; state.draftSaved -> "Draft saved on this device"; else -> "Draft not saved yet" }, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { model.saveSelection(state.reflection, state.colorArgb) }, enabled = state.captureStatus != CaptureStatus.Pending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reader-save")) { Text(if (state.captureStatus == CaptureStatus.Failed) "Retry save" else "Save in Commonplace") }
                        }
                        if (savedItems) {
                            Text("Saved items for this book", style = MaterialTheme.typography.titleLarge)
                            if (state.entries.isEmpty() && state.bookmarks.isEmpty()) Text("No saved items yet.")
                            state.entries.forEach { entry ->
                                if (entry.quotation != null) Text(entry.quotation, style = MaterialTheme.typography.bodyLarge)
                                entry.body?.let { Text(it) }
                                entry.location?.let { location ->
                                    TextButton(onClick = { host?.go(location); savedItems = false }, enabled = nativeReady, modifier = Modifier.heightIn(min = 48.dp).testTag("reader-open-capture")) { Text("Open passage") }
                                }
                            }
                            state.bookmarks.forEachIndexed { index, bookmark ->
                                TextButton(onClick = { host?.go(bookmark.location); savedItems = false }, enabled = nativeReady, modifier = Modifier.heightIn(min = 48.dp).testTag("reader-open-bookmark")) { Text("Open bookmark ${index + 1}") }
                            }
                            OutlinedTextField(value = annotation, onValueChange = { annotation = it }, label = { Text("Source annotation") }, modifier = Modifier.fillMaxWidth().testTag("reader-annotation"))
                            Button(onClick = { model.saveAnnotation(annotation) }, enabled = annotation.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save annotation") }
                            Text("An annotation is saved only after confirmation. This proof does not autosave annotation text.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                // Resize the viewport without relocating the native container's composition.
                Box(Modifier.fillMaxSize().padding(
                    end = if (wide && panelVisible) maxWidth / 2 else 0.dp,
                    bottom = if (!wide && panelVisible) panelHeight else 0.dp,
                )) {
                    if (host != null) AndroidView(
                        factory = { context -> FragmentContainerView(context).apply { id = R.id.readium_container; doOnAttach { host.attach(id) } } },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (state.phase == ReaderPhase.Opening && !nativeReady) CircularProgressIndicator(Modifier.padding(24.dp))
                }
                if (panelVisible) {
                    Box(Modifier.align(if (wide) Alignment.CenterEnd else Alignment.BottomCenter)
                        .width(if (wide) maxWidth / 2 else maxWidth)
                        .height(if (wide) maxHeight else panelHeight)) {
                        if (wide) VerticalDivider(Modifier.align(Alignment.CenterStart))
                        else HorizontalDivider(Modifier.align(Alignment.TopCenter))
                        Box(Modifier.fillMaxSize().padding(start = if (wide) 1.dp else 0.dp, top = if (wide) 0.dp else 1.dp)) { panel() }
                    }
                }
            }
        }
    }
    if (searchVisible) AlertDialog(
        onDismissRequest = { searchJob?.cancel(); searchVisible = false },
        title = { Text("Search in book") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(searchQuery, { searchQuery = it }, label = { Text("Exact words or phrase") }, modifier = Modifier.fillMaxWidth().testTag("reader-search-query"))
            if (searching) Text("Searching…") else if (searched && searchResults.isEmpty()) Text("No matches.")
            searchResults.forEach { location ->
                val label = runCatching { JSONObject(location.payloadJson).optJSONObject("text")?.optString("highlight") }.getOrNull().orEmpty().ifBlank { "Open search result" }
                TextButton(onClick = { host?.go(location); searchVisible = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reader-search-result")) { Text(label) }
            }
        } },
        confirmButton = { TextButton(onClick = {
            searchJob?.cancel(); searching = true; searched = true
            searchJob = scope.launch {
                try { searchResults = host?.search(searchQuery).orEmpty() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { notice = "Search did not complete. Retry." }
                finally { searching = false }
            }
        }, enabled = searchQuery.isNotBlank() && !searching, modifier = Modifier.heightIn(min = 48.dp)) { Text("Find") } },
        dismissButton = { TextButton(onClick = { searchJob?.cancel(); searchVisible = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") } },
    )
    externalUrl?.let { url ->
        AlertDialog(onDismissRequest = { externalUrl = null }, title = { Text("Open external website?") }, text = { Text("This leaves the offline reader and opens your browser.\n\n$url") },
            confirmButton = { TextButton(onClick = {
                externalUrl = null
                try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
                catch (_: android.content.ActivityNotFoundException) { notice = "No browser is available." }
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open browser") } },
            dismissButton = { TextButton(onClick = { externalUrl = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
        )
    }
}
