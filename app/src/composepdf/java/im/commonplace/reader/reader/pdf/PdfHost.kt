package im.commonplace.reader.reader.pdf

import androidx.compose.runtime.*
import dev.nucleusframework.pdfium.PdfReaderState
import im.commonplace.reader.data.SourceEntity
import im.commonplace.reader.imports.DocumentValidation
import im.commonplace.reader.imports.UnsupportedDocument
import im.commonplace.reader.reader.*
import java.io.File
import kotlinx.coroutines.*

internal const val GEOMETRY_LIMIT = "Precise PDF capture, saved highlights and jump-back unavailable: original crop-box/rotation geometry is unverified."

/** Experimental adapter only. It never turns character indices into durable PDF quads. */
class PdfHost(
    private val filesDir: File,
    private val source: SourceEntity,
    val native: PdfReaderState,
    private val hasSavedLocations: Boolean,
    private val event: (ReaderEvent) -> Unit,
    private val notice: (String) -> Unit,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var closed = false
    private var searchJob: Job? = null
    var ready by mutableStateOf(false)
        private set
    var pageIndex by mutableIntStateOf(0)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set
    var searching by mutableStateOf(false)
        private set
    var searchResult by mutableStateOf<String?>(null)
        private set
    var matchingPages by mutableStateOf<List<Int>>(emptyList())
        private set

    fun open() {
        scope.launch {
            try {
                require(source.format == DocumentFormat.PDF)
                // Verification precedes validation, byte allocation and every native open.
                val file = verifySourceFile(filesDir, source)
                withContext(Dispatchers.IO) { DocumentValidation.validate(file) }
                // This released API requires a whole ByteArray; its JNI copies it again.
                // Keep the limitation visible in the proof rather than hiding the allocation.
                withContext(Dispatchers.IO) { native.open(file.readBytes()) }
                ensureActive()
                if (closed) return@launch
                if (native.error != null || native.pageCount <= 0) {
                    fail("This PDF could not be opened safely. Your writing is retained.")
                    return@launch
                }
                val text = withContext(Dispatchers.IO) { native.pageText(0) }
                ensureActive()
                if (closed) return@launch
                val reason = if (text.isBlank()) "No selectable text available" else GEOMETRY_LIMIT
                event(ReaderEvent.Opened(source.id, ReaderCapabilities(
                    CapabilityStatus.Available, CapabilityStatus.Available,
                    CapabilityStatus.Unavailable(reason), CapabilityStatus.Unavailable(GEOMETRY_LIMIT),
                    CapabilityStatus.Available,
                )))
                ready = true
                notice(if (hasSavedLocations) "Saved PDF locations are unresolved. $GEOMETRY_LIMIT" else reason)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: VerifiedSourceException) { fail(failure.userMessage) }
            catch (unsupported: UnsupportedDocument) {
                if (!closed) {
                    val no = CapabilityStatus.Unavailable(unsupported.message ?: "This PDF is unsupported.")
                    event(ReaderEvent.Opened(source.id, ReaderCapabilities(no, no, no, no, no)))
                    notice(unsupported.message ?: "This PDF is unsupported.")
                }
            }
            catch (_: Exception) { fail("This PDF could not be opened safely. Your writing is retained.") }
        }
    }
    fun navigate(delta: Int) {
        if (ready) pageIndex = (pageIndex + delta).coerceIn(0, native.pageCount - 1)
        // View position is intentionally ephemeral: no original-space locator is emitted.
    }
    fun zoomIn() { if (ready) zoom = if (zoom < 2f) 2f else 1f }
    fun search(query: String) {
        if (!ready || query.isBlank()) return
        searchJob?.cancel()
        searchJob = scope.launch {
            searching = true; searchResult = null; matchingPages = emptyList()
            try {
                val matches = withContext(Dispatchers.IO) {
                    verifySourceFile(filesDir, source)
                    buildList {
                        for (page in 0 until native.pageCount) {
                            ensureActive()
                            val text = native.pageText(page)
                            // Exact native Unicode; no normalization or fixture-derived substitute.
                            if (text.contains(query)) add(page to text)
                        }
                    }
                }
                ensureActive()
                if (closed) return@launch
                matchingPages = matches.map { it.first }
                searchResult = matches.joinToString("\n") { it.second }.ifEmpty { "No matches." }
                matches.firstOrNull()?.let { pageIndex = it.first }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: VerifiedSourceException) { fail(failure.userMessage) }
            catch (_: Exception) { notice("PDF search did not complete. Retry.") }
            finally { searching = false }
        }
    }
    fun go(location: SavedLocation) {
        scope.launch {
            try {
                verifySourceFile(filesDir, source)
                PdfLocations.decode(location)
                if (!closed) notice("This saved PDF location is unresolved. $GEOMETRY_LIMIT")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: VerifiedSourceException) { fail(failure.userMessage) }
            catch (_: IllegalArgumentException) { if (!closed) notice("This saved location belongs to an incompatible reader or version.") }
            catch (_: Exception) { fail("This saved PDF location could not be checked safely. Your writing is retained.") }
        }
    }
    private fun fail(message: String) {
        if (!closed) { ready = false; event(ReaderEvent.Failed(source.id, message)) }
    }
    override fun close() {
        if (closed) return
        closed = true; ready = false; scope.cancel()
        // rememberPdfReaderState owns upstream disposal. That API is asynchronous and
        // exposes no completion acknowledgment; do not claim native cleanup completion.
    }
}
