package im.commonplace.reader.reader.pdf

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.util.FitPolicy
import im.commonplace.reader.data.SourceEntity
import im.commonplace.reader.imports.DocumentValidation
import im.commonplace.reader.reader.*
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/** Throwaway alignment probe. This flavor is not a selected or release PDF engine. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class HybridPdfView(
    context: Context,
    private val source: SourceEntity,
    private val event: (ReaderEvent) -> Unit,
    private val notice: (String) -> Unit,
) : FrameLayout(context), AutoCloseable {
    val native = PDFView(context, null)
    val web = WebView(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sourceFile: File? = null
    private var closed = false
    private var loaded = false
    private var sentFrame: String? = null
    private var pendingLocation: SavedLocation? = null
    private var drawingLocation: SavedLocation? = null
    private val paint = Paint().apply { color = 0x66578255; style = Paint.Style.FILL }
    var ready by mutableStateOf(false)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var pageInfo: JSONObject? = null
        private set
    var lastSelection: JSONObject? = null
        private set
    var lastRestored: JSONObject? = null
        private set
    var frame: JSONObject? = null
        private set
    var overlays: List<FloatArray> = emptyList()
        private set
    var pageIndex by mutableStateOf(0)
        private set
    var pageCount by mutableStateOf(0)
        private set
    var zoom by mutableStateOf(1f)
        private set
    var selecting by mutableStateOf(false)
        private set

    init {
        contentDescription = "Disposable hybrid PDF prototype"
        native.contentDescription = "Hybrid native PDF rendering"
        addView(native, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(web, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        web.setBackgroundColor(Color.TRANSPARENT)
        web.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            domStorageEnabled = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            textZoom = 100
        }
        web.addJavascriptInterface(Bridge(), "HybridBridge")
        android.util.Log.i("HybridProof", "WebView=${WebView.getCurrentWebViewPackage()?.versionName}; density=${resources.displayMetrics.density}")
        web.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.i("HybridProof", "WebView ${message.messageLevel()}: ${message.message()} @${message.lineNumber()}")
                return true
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun onPageFinished(view: WebView, url: String) {
                if (!closed) { sentFrame = null; native.invalidate() }
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val uri = request.url
                if (closed || uri.scheme != "https" || uri.host != "commonplace.invalid" || request.method != "GET" || uri.query != null) return denied()
                return try {
                    if (uri.path == "/document.pdf") {
                        val file = sourceFile ?: return denied()
                        // Both parsers see only the same checksum-verified app-owned source.
                        runBlocking(Dispatchers.IO) { verifySourceFile(context.filesDir, source) }
                        WebResourceResponse("application/pdf", null, file.inputStream())
                    } else {
                        val name = uri.path?.removePrefix("/hybrid/") ?: return denied()
                        if (uri.path != "/hybrid/$name" || name !in ASSETS) return denied()
                        val mime = when { name.endsWith(".mjs") -> "text/javascript"; name.endsWith(".css") -> "text/css"; else -> "text/html" }
                        WebResourceResponse(mime, "UTF-8", context.assets.open("hybrid/$name"))
                    }
                } catch (_: Exception) { denied() }
            }
        }
        web.setOnTouchListener { _, motion -> if (selecting) false else native.dispatchTouchEvent(motion) }
        post { open() }
    }

    private fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun open() {
        scope.launch {
            try {
                sourceFile = withContext(Dispatchers.IO) {
                    verifySourceFile(context.filesDir, source).also { DocumentValidation.validate(it) }
                }
                ensureActive()
                if (closed) return@launch
                web.loadUrl("https://commonplace.invalid/hybrid/index.html")
                native.fromFile(sourceFile!!).enableSwipe(true).enableDoubletap(true)
                    .enableAnnotationRendering(false).linkHandler { /* No document URL activation. */ }
                    .pageFitPolicy(FitPolicy.WIDTH).spacing(0).autoSpacing(false)
                    .onLoad { count -> loaded = true; pageCount = count }
                    .onError { fail("Hybrid native renderer could not open this PDF. Your writing is retained.") }
                    .onPageError { _, _ -> fail("Hybrid native page rendering failed. Your writing is retained.") }
                    .onPageChange { index, _ ->
                        pageIndex = index
                        sentFrame = null
                        ready = false
                        pageInfo = null
                        lastRestored = null
                        overlays = emptyList()
                    }
                    .onDrawAll { canvas, width, height, page -> if (!closed && page == native.currentPage) drawPage(canvas, width, height, page) }
                    .load()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (changed: VerifiedSourceException) { fail(changed.userMessage) }
            catch (_: Exception) { fail("This PDF could not be opened safely. Your writing is retained.") }
        }
    }

    @Suppress("DEPRECATION")
    private fun drawPage(canvas: Canvas, width: Float, height: Float, page: Int) {
        val origin = floatArrayOf(0f, 0f)
        canvas.matrix.mapPoints(origin)
        val observed = JSONObject().put("pageIndex", page).put("widthPx", width.toDouble()).put("heightPx", height.toDouble())
            .put("leftPx", origin[0].toDouble()).put("topPx", origin[1].toDouble())
            .put("density", resources.displayMetrics.density.toDouble()).put("zoom", native.zoom.toDouble())
        frame = observed
        val json = observed.toString()
        if (sentFrame != json) {
            sentFrame = json
            ready = false
            lastRestored = null
            pageInfo = null
            overlays = emptyList()
            web.post { if (!closed) web.evaluateJavascript("window.hybridFrame && window.hybridFrame($json)", null) }
        }
        val info = pageInfo ?: return
        if (info.optInt("pageIndex", -1) != page) return
        val location = drawingLocation ?: return
        val payload = JSONObject(location.payloadJson)
        if (payload.getInt("pageIndex") != page) return
        val transform = info.getJSONArray("viewportTransform").doubles()
        val density = resources.displayMetrics.density
        val drawn = mutableListOf<FloatArray>()
        val quads = payload.getJSONArray("quads")
        for (i in 0 until quads.length()) {
            val mapped = HybridGeometry.map(quads.getJSONArray(i).doubles(), transform)
            val local = FloatArray(mapped.size) { (mapped[it] * density).toFloat() }
            val path = Path().apply {
                moveTo(local[0], local[1]); for (j in 2 until local.size step 2) lineTo(local[j], local[j + 1]); close()
            }
            canvas.drawPath(path, paint)
            drawn.add(FloatArray(local.size) { local[it] + origin[it % 2] })
        }
        overlays = drawn
    }

    private inner class Bridge {
        @JavascriptInterface fun message(json: String) {
            if (json.length > 1_000_000) return
            post {
                if (closed || failure != null) return@post
                try {
                    val message = JSONObject(json)
                    when (message.getString("type")) {
                        "ready" -> {
                            if (!isCurrentFrame(message)) return@post
                            pageInfo = message
                            ready = loaded
                            if (ready) {
                                val unproven = CapabilityStatus.Unavailable("Disposable prototype; exactness gates are not accepted.")
                                event(ReaderEvent.Opened(source.id, ReaderCapabilities(CapabilityStatus.Available, unproven, unproven, unproven, CapabilityStatus.Available)))
                                (pendingLocation ?: drawingLocation)?.let { pendingLocation = it; applyRestore(it) }
                                native.invalidate()
                            }
                        }
                        "selection", "restored" -> verified {
                            if (!isCurrentFrame(message)) return@verified
                            val payload = message.getJSONObject("payload")
                            val location = SavedLocation(1, DocumentFormat.PDF, HYBRID_ENGINE_ID, payload.toString())
                            HybridGeometry.validateSelection(location)
                            require(payload.getInt("pageIndex") == native.currentPage)
                            require(message.getString("quotation").isNotBlank())
                            if (message.getString("type") == "selection") {
                                lastRestored = null
                                lastSelection = message
                                event(ReaderEvent.SelectionChanged(source.id, Capture(message.getString("quotation"), location)))
                            } else {
                                val expected = pendingLocation ?: return@verified
                                require(JSONObject(expected.payloadJson).getJSONObject("native").toString() == payload.getJSONObject("native").toString())
                                require(expected.payloadJson == location.payloadJson) { "Restored geometry is not identical to the saved selection" }
                                pendingLocation = null
                                lastRestored = message
                            }
                            drawingLocation = location
                            native.invalidate()
                        }
                        "captureError" -> notice("Select a passage in the text layer before keeping it.")
                        "error" -> fail("Hybrid selection layer: ${message.optString("message", "could not open PDF")}")
                    }
                } catch (_: Exception) { fail("Hybrid selection metadata is invalid. Your writing is retained.") }
            }
        }
    }

    private fun isCurrentFrame(message: JSONObject): Boolean {
        val reported = message.optJSONObject("frame") ?: return false
        val observed = frame ?: return false
        return listOf("pageIndex", "widthPx", "heightPx", "leftPx", "topPx", "density", "zoom").all {
            reported.optDouble(it, Double.NaN) == observed.getDouble(it)
        }
    }

    private fun verified(action: () -> Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { verifySourceFile(context.filesDir, source) }
                if (!closed && failure == null) action()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (changed: VerifiedSourceException) { fail(changed.userMessage) }
            catch (_: Exception) { fail("This saved PDF passage is unresolved. Your writing is retained.") }
        }
    }

    fun selectMode(enabled: Boolean) { selecting = enabled }
    fun capture() {
        lastSelection = null; lastRestored = null; overlays = emptyList()
        verified { web.evaluateJavascript("window.hybridCapture && window.hybridCapture()", null) }
    }
    fun navigate(delta: Int) {
        if (!loaded) return
        drawingLocation = null
        native.jumpTo((native.currentPage + delta).coerceIn(0, pageCount - 1))
    }
    fun zoomToggle() {
        if (!loaded) return
        lastRestored = null
        zoom = if (native.zoom < 1.5f) 2f else 1f
        native.zoomCenteredTo(zoom, android.graphics.PointF(width / 2f, height / 2f))
        native.loadPages()
        native.invalidate()
    }
    fun go(location: SavedLocation) {
        verified {
            HybridGeometry.validateSelection(location)
            pendingLocation = location
            lastRestored = null
            val page = JSONObject(location.payloadJson).getInt("pageIndex")
            require(page < pageCount)
            if (native.currentPage != page) native.jumpTo(page) else if (ready) applyRestore(location)
        }
    }
    private fun applyRestore(location: SavedLocation) {
        if (JSONObject(location.payloadJson).getInt("pageIndex") != native.currentPage) return
        web.evaluateJavascript("window.hybridRestore && window.hybridRestore(${location.payloadJson})", null)
    }
    private fun fail(message: String) {
        if (closed) return
        ready = false; failure = message; drawingLocation = null; overlays = emptyList()
        event(ReaderEvent.Failed(source.id, message)); notice(message)
    }
    override fun close() {
        if (closed) return
        closed = true; ready = false; scope.cancel()
        web.removeJavascriptInterface("HybridBridge"); web.stopLoading(); web.destroy()
        native.recycle()
    }
    companion object {
        private val ASSETS = setOf("index.html", "selection.mjs", "selection-geometry.mjs", "pdf.mjs", "pdf.worker.mjs", "text_layer.css")
    }
}

internal fun JSONArray.doubles() = DoubleArray(length()) { getDouble(it) }
