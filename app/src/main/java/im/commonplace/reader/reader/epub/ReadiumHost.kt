@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package im.commonplace.reader.reader.epub

import android.graphics.Color
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.FragmentActivity
import im.commonplace.reader.data.EntryEntity
import im.commonplace.reader.data.SourceEntity
import im.commonplace.reader.reader.*
import java.io.IOException
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.html.HtmlDecorationTemplate
import org.readium.r2.navigator.html.HtmlDecorationTemplates
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isProtected
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.publication.services.search.isSearchable
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.*
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.http.*
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.resource.TransformingResource
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser

/** One source, one supported navigator. Created once per Compose source key, never per frame. */
class ReadiumHost(
    private val activity: FragmentActivity,
    private val source: SourceEntity,
    private val initialLocation: SavedLocation?,
    private val event: (ReaderEvent) -> Unit,
    private val externalLink: (String) -> Unit,
    private val notice: (String) -> Unit,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var publication: Publication? = null
    private var navigator: EpubNavigatorFragment? = null
    private var opening: Job? = null
    private var decorationsJob: Job? = null
    private var closed = false
    private var darkReading = false
    private var highlights = emptyList<EntryEntity>()
    private var rendered = false
    private var selected = false
    private val readyState = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = readyState.asStateFlow()

    fun attach(containerId: Int) {
        check(opening == null) { "Reader host already attached" }
        opening = scope.launch {
            var parsed: Publication? = null
            var transferred = false
            var stage = "verify-asset"
            try {
                withContext(Dispatchers.IO) {
                    require(source.format == DocumentFormat.EPUB)
                    val assetFile = try { verifySourceFile(activity.filesDir, source) }
                    catch (failure: VerifiedSourceException) { throw ReaderOpenFailure(failure.userMessage, failure) }
                    ZipFile(assetFile).use { zip ->
                        // Import already rejects encryption. Recheck at the reader boundary too.
                        if (zip.getEntry("META-INF/encryption.xml") != null) throw UnsupportedEpub("Encrypted EPUB is not supported by this proof.")
                    }
                    stage = "retrieve-asset"
                    val retriever = AssetRetriever(activity.contentResolver, OfflineHttpClient)
                    val asset = retriever.retrieve(assetFile.toUrl(isDirectory = false), MediaType.EPUB).getOrElse { throw ReaderOpenFailure("This EPUB could not be opened safely. Your writing is retained.", ErrorException(it)) }
                    var handedToPublication = false
                    try {
                        stage = "parse-publication"
                        parsed = PublicationOpener(EpubParser(OfflineHttpClient)).open(asset, allowUserInteraction = false, onCreatePublication = {
                            val original = container
                            val contentManifest = manifest
                            container = ClosingResourceContainer(object : Container<Resource> by original {
                                override fun get(url: Url): Resource? {
                                    // No arbitrary remote/file resources are delegated to the parser.
                                    if (url is AbsoluteUrl) return null
                                    val resource = original[url] ?: return null
                                    val type = contentManifest.linkWithHref(url)?.mediaType
                                    if (!EpubContentPolicy.allowsResource(type?.toString(), url.toString())) return null
                                    if (type?.isHtml != true) return resource
                                    return TransformingResource(resource) { bytes ->
                                        try { Try.success(EpubContentPolicy.sanitize(bytes)) }
                                        catch (_: IllegalArgumentException) { Try.failure(ReadError.Decoding(IOException("Unsupported EPUB chapter content"))) }
                                    }
                                }
                            })
                        }).getOrElse { throw ReaderOpenFailure("This EPUB could not be opened safely. Your writing is retained.", ErrorException(it)) }
                        handedToPublication = true
                    } finally { if (!handedToPublication) asset.close() }
                }
                currentCoroutineContext().ensureActive()
                if (closed) return@launch
                val book = requireNotNull(parsed)
                if (book.isRestricted || book.isProtected) throw UnsupportedEpub("Encrypted EPUB is not supported by this proof.")
                if ((book.readingOrder + book.resources).any { !EpubContentPolicy.allowsResource(it.mediaType?.toString(), it.href.toString()) }) throw UnsupportedEpub("SVG EPUB resources are not supported by this proof.")
                if (book.metadata.layout == Layout.FIXED) throw UnsupportedEpub("Fixed-layout EPUB is not supported by this proof.")
                stage = "configure-navigator"
                val initial = initialLocation?.let { decode(it) }
                val templates = HtmlDecorationTemplates.defaultTemplates().apply {
                    this[Decoration.Style.Highlight::class] = HtmlDecorationTemplate(
                        layout = HtmlDecorationTemplate.Layout.BOXES,
                        element = { decoration ->
                            val tint = (decoration.style as Decoration.Style.Highlight).tint
                            val rgb = "#%06x".format(tint and 0xFFFFFF)
                            "<div class=\"commonplace-proof-highlight\" style=\"background:$rgb;opacity:0.35;border-radius:2px;\"></div>"
                        },
                    )
                }
                val factory = EpubNavigatorFactory(book).createFragmentFactory(
                    initialLocator = initial,
                    initialPreferences = preferences(),
                    listener = object : EpubNavigatorFragment.Listener {
                        override fun onExternalLinkActivated(url: AbsoluteUrl) {
                            if (closed) return
                            if (url.scheme.value.lowercase() in setOf("http", "https")) externalLink(url.toString())
                            else notice("This link type is blocked.")
                        }
                        override fun onResourceLoadFailed(href: Url, error: ReadError) { if (!closed) event(ReaderEvent.Failed(source.id, "This EPUB resource could not be rendered safely. Your writing is retained.")) }
                    },
                    paginationListener = object : EpubNavigatorFragment.PaginationListener {
                        override fun onPageLoaded() {
                            if (closed) return
                            rendered = true
                            emitCapabilities()
                            scope.launch { applyHighlights() }
                        }
                    },
                    configuration = EpubNavigatorFragment.Configuration(
                        decorationTemplates = templates,
                        selectionActionModeCallback = object : ActionMode.Callback {
                            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { menu.clear(); menu.add("Keep passage"); return true }
                            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean { selectPassage(); return true }
                            override fun onDestroyActionMode(mode: ActionMode) {}
                        },
                    ),
                )
                stage = "instantiate-navigator"
                val fragment = factory.instantiate(activity.classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
                publication = book
                navigator = fragment
                stage = "attach-navigator"
                activity.supportFragmentManager.beginTransaction().replace(containerId, fragment, "readium:${source.id}").commitNow()
                transferred = true
                readyState.value = true
                scope.launch { fragment.currentLocator.collect { if (!closed) event(ReaderEvent.PositionChanged(source.id, ReadiumLocations.encode(it.toJSON().toString()))) } }
                stage = "apply-decorations"
                applyHighlights()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (unsupported: UnsupportedEpub) {
                if (!closed) {
                    val no = CapabilityStatus.Unavailable(unsupported.message.orEmpty())
                    event(ReaderEvent.Opened(source.id, ReaderCapabilities(no, no, no, no, no)))
                    notice(unsupported.message.orEmpty())
                }
            } catch (failure: Exception) {
                android.util.Log.e("ReadiumHost", "Startup failed at $stage: ${readiumFailureCode(failure)}")
                if (!closed) event(ReaderEvent.Failed(source.id, (failure as? ReaderOpenFailure)?.message ?: "This EPUB could not be opened safely. Your writing is retained."))
            } finally {
                if (!transferred) { parsed?.close(); publication = null; navigator = null }
            }
        }
    }
    fun setDarkReading(dark: Boolean) { if (darkReading == dark) return; darkReading = dark; navigator?.submitPreferences(preferences()) }
    private fun preferences() = EpubPreferences(theme = if (darkReading) Theme.DARK else Theme.LIGHT, fontSize = activity.resources.configuration.fontScale.toDouble())
    fun setHighlights(entries: List<EntryEntity>) {
        if (entries == highlights) return
        highlights = entries
        decorationsJob?.cancel()
        decorationsJob = scope.launch { applyHighlights() }
    }
    private suspend fun applyHighlights() {
        val native = navigator ?: return
        if (!native.supportsDecorationStyle(Decoration.Style.Highlight::class)) return
        val decorations = highlights.mapNotNull { entry ->
            if (entry.sourceId != source.id) return@mapNotNull null
            val locator = entry.location?.let { runCatching { decode(it) }.getOrNull() } ?: return@mapNotNull null
            Decoration(entry.id, locator, Decoration.Style.Highlight(entry.colorArgb ?: Color.YELLOW))
        }
        native.applyDecorations(decorations, "commonplace-highlights")
    }
    fun selectPassage() {
        scope.launch {
            try {
                val selection = navigator?.currentSelection()
                if (closed) return@launch
                val text = selection?.locator?.text?.highlight
                if (selection == null || text.isNullOrBlank()) { notice("Select text in the book first. No selectable text available at this position."); return@launch }
                selected = true
                emitCapabilities()
                event(ReaderEvent.SelectionChanged(source.id, Capture(text, ReadiumLocations.encode(selection.locator.toJSON().toString()))))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (!closed) notice("This selection could not be read. Your writing is retained.") }
        }
    }
    fun go(location: SavedLocation) {
        try {
            if (navigator?.go(decode(location)) != true) notice("This saved location is unresolved.")
        } catch (_: IllegalArgumentException) { notice("This saved location is unresolved.") }
    }
    suspend fun search(query: String): List<SavedLocation> {
        val book = publication ?: return emptyList()
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val iterator = book.search(query) ?: return@withContext emptyList()
            try {
                val results = mutableListOf<SavedLocation>()
                while (results.size < 100) {
                    ensureActive()
                    val page = iterator.next().getOrElse { throw IOException("EPUB search failed") } ?: break
                    results += page.locators.map { ReadiumLocations.encode(it.toJSON().toString()) }
                }
                results.take(100)
            } finally { iterator.close() }
        }
    }
    private fun decode(location: SavedLocation): Locator = Locator.fromJSON(JSONObject(ReadiumLocations.decode(location))) ?: throw IllegalArgumentException("Invalid native locator")
    private fun emitCapabilities() {
        val native = navigator ?: return
        val yes = CapabilityStatus.Available
        val unknown = CapabilityStatus.Unknown
        event(ReaderEvent.Opened(source.id, ReaderCapabilities(
            render = if (rendered) yes else unknown,
            search = if (publication?.isSearchable == true) yes else CapabilityStatus.Unavailable("Search is unavailable for this EPUB."),
            select = if (selected) yes else unknown,
            highlightDisplay = if (native.supportsDecorationStyle(Decoration.Style.Highlight::class)) yes else CapabilityStatus.Unavailable("Highlight display is unavailable."),
            navigation = yes,
        )))
    }
    override fun close() {
        if (closed) return
        closed = true
        readyState.value = false
        scope.cancel()
        navigator?.let { fragment ->
            if (fragment.isAdded) activity.supportFragmentManager.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
        }
        navigator = null
        publication?.close()
        publication = null
    }
    private class UnsupportedEpub(message: String) : IOException(message)
    private class ReaderOpenFailure(message: String, cause: Throwable? = null) : IOException(message, cause)
}

/** Both asset retrieval and parser external-container access share this fail-closed client. */
internal object OfflineHttpClient : HttpClient {
    override suspend fun stream(request: HttpRequest): HttpTry<HttpStreamResponse> = Try.failure(HttpError.IO(IOException("Publication network access is disabled")))
}

/** Code symbols only: never log exception messages, resource URLs, paths or writing. */
internal fun readiumFailureCode(failure: Throwable): String =
    generateSequence(failure) { it.cause }.take(4).joinToString(" <- ") { cause ->
        val error = (cause as? ErrorException)?.error
        val actual = (error as? ThrowableError<*>)?.throwable ?: cause
        val name = if (error != null && error !is ThrowableError<*>) error.javaClass.simpleName else actual.javaClass.simpleName
        name + "@" + actual.stackTrace.take(3).joinToString(",") { "${it.className}.${it.methodName}:${it.lineNumber}" }
    }
