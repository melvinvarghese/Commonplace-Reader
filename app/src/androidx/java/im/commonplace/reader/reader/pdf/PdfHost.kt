@file:OptIn(androidx.pdf.ExperimentalPdfApi::class)

package im.commonplace.reader.reader.pdf

import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.fragment.app.FragmentActivity
import androidx.pdf.PdfDocument
import androidx.pdf.content.ExternalLink
import androidx.pdf.selection.Selection
import androidx.pdf.selection.model.TextSelection
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfViewerFragment
import im.commonplace.reader.data.EntryEntity
import im.commonplace.reader.data.SourceEntity
import im.commonplace.reader.imports.DocumentValidation
import im.commonplace.reader.imports.UnsupportedDocument
import im.commonplace.reader.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal const val PLATFORM_LIMIT = "AndroidX PDF comparison requires API 31 and SDK extension 13 for the full viewer."
internal const val GEOMETRY_LIMIT = "Precise capture and saved highlights unavailable: original PDF crop-box and rotation mapping is unverified."

/** One file/fragment session. The fragment owns its document and bound service. */
class PdfHost(
    private val activity: FragmentActivity,
    private val source: SourceEntity,
    private val initialLocation: SavedLocation?,
    private val highlights: List<EntryEntity>,
    private val event: (ReaderEvent) -> Unit,
    private val notice: (String) -> Unit,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var fragment: CandidatePdfFragment? = null
    private var view: PdfView? = null
    private var closed = false
    private var attached = false
    private val loadedState = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = loadedState.asStateFlow()

    fun attach(containerId: Int) {
        check(!attached); attached = true
        scope.launch {
            try {
                require(source.format == DocumentFormat.PDF)
                // Integrity precedes even the unsupported-platform result, and all native use.
                val file = verifySourceFile(activity.filesDir, source)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) < 13) {
                    unavailable(PLATFORM_LIMIT); return@launch
                }
                withContext(Dispatchers.IO) { DocumentValidation.validate(file) }
                ensureActive()
                if (closed) return@launch
                val native = CandidatePdfFragment().also { candidate ->
                    candidate.owner = this@PdfHost
                }
                fragment = native
                activity.supportFragmentManager.beginTransaction().replace(containerId, native, "androidx-pdf:${source.id}").commitNow()
                // beta01's setter accesses a fragment-scoped ViewModel, so attachment
                // must precede loading; do not access it on a detached fragment.
                native.documentUri = Uri.fromFile(file)
                if (initialLocation != null || highlights.isNotEmpty()) notice("Saved PDF locations are unresolved. $GEOMETRY_LIMIT")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: VerifiedSourceException) { fail(failure.userMessage) }
            catch (unsupported: UnsupportedDocument) { unavailable(unsupported.message ?: "This PDF is unsupported.") }
            catch (_: Exception) { fail("This PDF could not be opened safely. Your writing is retained.") }
        }
    }
    internal fun viewCreated(native: PdfView) {
        if (closed) return
        view = native
        native.isImageSelectionEnabled = false
        native.isFormFillingEnabled = false
        native.addOnSelectionChangedListener(object : PdfView.OnSelectionChangedListener {
            override fun onSelectionChanged(newSelection: Selection?) {
                if (!closed && newSelection is TextSelection) notice(GEOMETRY_LIMIT)
                // Never manufacture SelectionChanged/Capture from viewport rectangles.
            }
        })
    }
    internal fun loaded(document: PdfDocument) {
        scope.launch {
            try {
                val hasText = requireNotNull(document.getPageContent(0)).textContents.any { it.text.isNotBlank() }
                if (closed) return@launch
                val selection = CapabilityStatus.Unavailable(if (hasText) GEOMETRY_LIMIT else "No selectable text available")
                event(ReaderEvent.Opened(source.id, ReaderCapabilities(CapabilityStatus.Available,
                    CapabilityStatus.Available, selection, CapabilityStatus.Unavailable(GEOMETRY_LIMIT), CapabilityStatus.Available)))
                notice(if (hasText) GEOMETRY_LIMIT else "No selectable text available")
                loadedState.value = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail("PDF text capabilities could not be determined. Your writing is retained.") }
        }
    }
    fun toggleSearch() { fragment?.let { it.isTextSearchActive = !it.isTextSearchActive } }
    fun navigate(delta: Int) { view?.let { native -> native.pdfDocument?.let { document -> native.scrollToPage((native.firstVisiblePage + delta).coerceIn(0, document.pageCount - 1)) } } }
    fun zoom(factor: Float) { view?.let { it.zoom = (it.zoom * factor).coerceIn(it.minZoom, it.maxZoom) } }
    fun go(location: SavedLocation) {
        scope.launch {
            try {
                verifySourceFile(activity.filesDir, source)
                PdfLocations.decode(location)
                // There is no proven inverse original-space mapping in this candidate.
                notice("This saved PDF location is unresolved. $GEOMETRY_LIMIT")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: VerifiedSourceException) { fail(failure.userMessage) }
            catch (_: IllegalArgumentException) { notice("This saved location belongs to an incompatible reader or version.") }
            catch (_: Exception) { fail("This saved PDF location could not be checked safely. Your writing is retained.") }
        }
    }
    internal fun fail(message: String) { if (!closed) { loadedState.value = false; event(ReaderEvent.Failed(source.id, message)) } }
    private fun unavailable(message: String) {
        if (closed) return
        val no = CapabilityStatus.Unavailable(message)
        event(ReaderEvent.Opened(source.id, ReaderCapabilities(no, no, no, no, no)))
        notice(message)
    }
    override fun close() {
        if (closed) return
        closed = true; loadedState.value = false; scope.cancel()
        val old = fragment
        old?.owner = null
        if (old != null && !activity.supportFragmentManager.isDestroyed && old.isAdded) {
            activity.supportFragmentManager.beginTransaction().remove(old).commitNowAllowingStateLoss()
        }
        view = null; fragment = null
    }
}

/** Public no-arg constructor is required by Android's fragment recreation machinery. */
class CandidatePdfFragment : PdfViewerFragment() {
    internal var owner: PdfHost? = null
    override fun onPdfViewCreated(pdfView: PdfView) { isToolboxVisible = false; owner?.viewCreated(pdfView) }
    override fun onLoadDocumentSuccess(document: PdfDocument) { owner?.loaded(document) }
    override fun onLoadDocumentError(error: Throwable) { owner?.fail("This PDF could not be opened safely. Your writing is retained.") }
    override fun onLinkClicked(externalLink: ExternalLink): Boolean = true
    override fun onRequestImmersiveMode(enterImmersive: Boolean) { isToolboxVisible = false }
    // Default onInitOcrProvider() returns null; no OCR is installed or inferred.
}
