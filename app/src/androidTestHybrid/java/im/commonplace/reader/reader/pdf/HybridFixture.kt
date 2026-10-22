package im.commonplace.reader.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.os.ParcelFileDescriptor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import im.commonplace.reader.CommonplaceApplication
import im.commonplace.reader.ReaderActivity
import im.commonplace.reader.data.SourceAvailability
import im.commonplace.reader.data.SourceEntity
import im.commonplace.reader.reader.DocumentFormat
import im.commonplace.reader.reader.PdfProofAssertions
import im.commonplace.reader.reader.SavedLocation
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/** Shared test-only helpers. Selection itself is always produced by injected UI touch events. */
internal object HybridFixture {
    val context: Context get() = ApplicationProvider.getApplicationContext()
    val container get() = (context as CommonplaceApplication).container
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val testContext get() = instrumentation.context
    private var latestDiagnostic: (() -> String)? = null

    fun manifestEntry(name: String): JSONObject {
        val manifest = testContext.assets.open("fixtures/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        val fixtures = manifest.getJSONArray("fixtures")
        return (0 until fixtures.length()).map(fixtures::getJSONObject).single { it.getString("file") == name }
    }

    fun source(name: String): SourceEntity {
        val expected = manifestEntry(name)
        val id = UUID.randomUUID().toString()
        val file = File(context.filesDir, "imports/assets/$id.pdf").apply { parentFile!!.mkdirs() }
        testContext.assets.open("fixtures/$name").use { input -> file.outputStream().use(input::copyTo) }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val block = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(block)
                if (count < 0) break
                digest.update(block, 0, count)
            }
        }
        val checksum = digest.digest().joinToString("") { "%02x".format(it) }
        assertEquals("Fixture bytes must match the checked-in manifest", expected.getString("sha256"), checksum)
        assertEquals("Fixture size must match the checked-in manifest", expected.getLong("bytes"), file.length())
        removePriorSyntheticFixture(checksum, "content://synthetic/$name")
        val source = SourceEntity(id, "Hybrid PDF proof", null, DocumentFormat.PDF, "content://synthetic/$name", checksum,
            "imports/assets/$id.pdf", SourceAvailability.AVAILABLE, 1, 1)
        runBlocking(Dispatchers.IO) { container.database.readerDao().insertSource(source) }
        return source
    }

    /** Remove only this exact test-owned fixture row; production checksum uniqueness stays intact. */
    private fun removePriorSyntheticFixture(checksum: String, originalUri: String) {
        val db = container.database.openHelper.writableDatabase
        val stalePaths = mutableListOf<String>()
        db.beginTransaction()
        try {
            db.query("SELECT id, assetPath FROM sources WHERE checksum = ? AND originalUri = ?",
                arrayOf(checksum, originalUri)).use { cursor ->
                while (cursor.moveToNext()) stalePaths += cursor.getString(1)
            }
            db.execSQL("DELETE FROM entries WHERE sourceId IN (SELECT id FROM sources WHERE checksum = ? AND originalUri = ?)",
                arrayOf(checksum, originalUri))
            db.execSQL("DELETE FROM bookmarks WHERE sourceId IN (SELECT id FROM sources WHERE checksum = ? AND originalUri = ?)",
                arrayOf(checksum, originalUri))
            db.execSQL("DELETE FROM reading_state WHERE sourceId IN (SELECT id FROM sources WHERE checksum = ? AND originalUri = ?)",
                arrayOf(checksum, originalUri))
            db.execSQL("DELETE FROM reader_drafts WHERE sourceId IN (SELECT id FROM sources WHERE checksum = ? AND originalUri = ?)",
                arrayOf(checksum, originalUri))
            db.execSQL("DELETE FROM sources WHERE checksum = ? AND originalUri = ?", arrayOf(checksum, originalUri))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        stalePaths.filter { it.startsWith("imports/assets/") }.forEach { File(context.filesDir, it).delete() }
    }

    fun sourceFile(source: SourceEntity) = File(context.filesDir, source.assetPath)

    fun state(scenario: ActivityScenario<ReaderActivity>): im.commonplace.reader.reader.ReaderUiState {
        var result: im.commonplace.reader.reader.ReaderUiState? = null
        scenario.onActivity { result = androidx.lifecycle.ViewModelProvider(it)[im.commonplace.reader.reader.ReaderViewModel::class.java].uiState.value }
        return requireNotNull(result)
    }

    fun findHost(root: View): HybridPdfView? = when {
        root is HybridPdfView -> root
        root is ViewGroup -> (0 until root.childCount).firstNotNullOfOrNull { findHost(root.getChildAt(it)) }
        else -> null
    }

    fun findHostIn(scenario: ActivityScenario<ReaderActivity>): HybridPdfView? {
        var found: HybridPdfView? = null
        scenario.onActivity { found = findHost(it.window.decorView) }
        return found
    }

    fun host(scenario: ActivityScenario<ReaderActivity>, timeoutMs: Long = 30_000): HybridPdfView {
        var found: HybridPdfView? = null
        await("HybridPdfView attachment and both-engine ready state", timeoutMs, diagnostic = {
            diagnosticFor(scenario)
        }) {
            scenario.onActivity { found = findHost(it.window.decorView)?.takeIf { view -> view.ready } }
            found != null
        }
        val readyHost = requireNotNull(found)
        latestDiagnostic = { diagnosticFor(scenario) }
        assertStartupAndWorker(scenario, readyHost)
        return readyHost
    }

    private fun diagnosticFor(scenario: ActivityScenario<ReaderActivity>): String {
        return try {
            val host = findHostIn(scenario) ?: return "HybridPdfView not attached"
            var result = ""
            scenario.onActivity { result = host.diagnosticSnapshot().toString() }
            result
        } catch (_: IllegalStateException) {
            "HybridPdfView diagnostics unavailable: activity scenario is no longer active"
        }
    }

    fun await(description: String, timeoutMs: Long = 20_000,
               diagnostic: () -> String = { latestDiagnostic?.invoke() ?: "no HybridPdfView diagnostics yet" },
               condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(40)
        }
        assertTrue("Timed out: $description; diagnosticSnapshot=${diagnostic()}", condition())
    }

    fun assertStartupAndWorker(scenario: ActivityScenario<ReaderActivity>, host: HybridPdfView) {
        var snapshot: JSONObject? = null
        scenario.onActivity { snapshot = host.diagnosticSnapshot() }
        val observed = requireNotNull(snapshot)
        assertTrue("Hybrid host ready evidence: $observed", observed.optBoolean("ready"))
        assertTrue("Native renderer must have completed its first open: $observed", observed.optBoolean("nativeLoaded"))
        assertTrue("A completed open must not retain a startup failure: $observed", observed.isNull("failure"))
        assertNotNull("The first PDF.js page must be parsed before startup passes: $observed", host.pageInfo)
        assertEquals("The first open must parse page zero before any navigation", 0, host.pageInfo!!.optInt("pageIndex", -1))
        val javascript = observed.optJSONObject("javascript")
        assertNotNull("PDF.js startup diagnostics must be present: $observed", javascript)
        assertNotNull("PDF.js document-loaded diagnostic is required: $observed", javascript!!.optJSONObject("document-loaded"))
        val worker = javascript.optJSONObject("worker-ready")
        assertNotNull("PDF.js worker-ready diagnostic is required: $observed", worker)
        assertEquals("Pinned PDF.js build is expected to use its explicit in-process worker handler",
            "in-process", worker!!.optString("workerMode"))
        Log.i("HybridProof", "PASS gate=first-open-parse-worker; workerMode=${worker.optString("workerMode")}; snapshot=$observed")
    }

    fun evaluate(web: WebView, javascript: String): JSONObject {
        val result = arrayOfNulls<String>(1)
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync { web.evaluateJavascript(javascript) { value -> result[0] = value; latch.countDown() } }
        assertTrue("WebView DOM geometry evaluation timed out", latch.await(5, TimeUnit.SECONDS))
        val encoded = requireNotNull(result[0])
        val decoded = JSONTokener(encoded).nextValue()
        return if (decoded is String) JSONObject(decoded) else decoded as JSONObject
    }

    /** Read a visible text-layer span's real DOM rectangle; this does not create or alter selection. */
    fun textRect(web: WebView, exactText: String): JSONObject {
        val js = """(function(){
          const wanted=${JSONObject.quote(exactText)};
          const spans=[...document.querySelectorAll('.textLayer span')].filter(s=>s.textContent.trim()===wanted);
          const s=spans.find(s=>{const r=s.getBoundingClientRect();return r.width>0&&r.height>0&&r.right>0&&r.left<innerWidth&&r.bottom>0&&r.top<innerHeight;});
          if(!s)return null; const r=s.getBoundingClientRect();
          return JSON.stringify({text:s.textContent.trim(),left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height,dpr:devicePixelRatio});
        })()"""
        val result = evaluate(web, js)
        assertEquals("A visible rendered PDF.js text span is required for a real touch", exactText, result.getString("text"))
        return result
    }

    /** Long-press and drag over the actual visible text layer with instrumentation pointer events. */
    fun selectByTouch(scenario: ActivityScenario<ReaderActivity>, host: HybridPdfView, exactText: String) {
        val rect = textRect(host.web, exactText)
        val webOrigin = IntArray(2)
        scenario.onActivity { host.web.getLocationOnScreen(webOrigin) }
        val density = rect.getDouble("dpr")
        val vertical = rect.getDouble("height") > rect.getDouble("width")
        val centerX = (rect.getDouble("left") + rect.getDouble("width") / 2.0) * density
        val centerY = (rect.getDouble("top") + rect.getDouble("height") / 2.0) * density
        val x0 = webOrigin[0] + (if (vertical) centerX else rect.getDouble("left") * density + 2).toInt()
        val x1 = webOrigin[0] + (if (vertical) centerX else rect.getDouble("right") * density - 2).toInt()
        val y0 = webOrigin[1] + (if (vertical) rect.getDouble("top") * density + 2 else centerY).toInt()
        val y1 = webOrigin[1] + (if (vertical) rect.getDouble("bottom") * density - 2 else centerY).toInt()
        val start = android.os.SystemClock.uptimeMillis()
        fun send(action: Int, x: Int, y: Int, time: Long) {
            instrumentation.sendPointerSync(MotionEvent.obtain(start, time, action, x.toFloat(), y.toFloat(), 0))
        }
        send(MotionEvent.ACTION_DOWN, x0, y0, start)
        Thread.sleep(700)
        repeat(12) { step ->
            val time = android.os.SystemClock.uptimeMillis()
            val x = x0 + ((x1 - x0) * (step + 1) / 12)
            val y = y0 + ((y1 - y0) * (step + 1) / 12)
            send(MotionEvent.ACTION_MOVE, x, y, time)
            Thread.sleep(35)
        }
        send(MotionEvent.ACTION_UP, x1, y1, android.os.SystemClock.uptimeMillis())
        Log.i("HybridProof", "realTouch text=${JSONObject.quote(exactText)}; cssRect=${rect}; density=$density; screenStart=($x0,$y0); screenEnd=($x1,$y1)")
    }

    fun screenshot(label: String) {
        val directory = File(context.filesDir, "hybrid-proof").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("A real UI screenshot is required as selection evidence", bitmap)
        FileOutputStream(File(directory, "$label.png")).use { requireNotNull(bitmap).compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    fun assertNativeInk(host: HybridPdfView, label: String) {
        val view = host.native
        assertTrue("Native PDFView has no rendered bounds", view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync { view.draw(Canvas(bitmap)) }
        var ink = 0
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
            val color = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(color) > 220 && android.graphics.Color.red(color) < 90 &&
                android.graphics.Color.green(color) < 90 && android.graphics.Color.blue(color) < 90) ink++
        }
        Log.i("HybridProof", "nativeBitmap label=$label; ${bitmap.width}x${bitmap.height}; sampledDarkInk=$ink")
        assertTrue("The AndroidPdfViewer surface must contain rendered PDF ink, not only a ready callback", ink > 10)
        val directory = File(context.filesDir, "hybrid-proof").apply { mkdirs() }
        FileOutputStream(File(directory, "$label-native.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    /** Compare text-layer-derived PDF points with independent PDFium point mapping and the drawn overlay. */
    fun assertIndependentGeometry(source: SourceEntity, host: HybridPdfView, label: String) {
        val selection = requireNotNull(host.lastRestored ?: host.lastSelection) { "No genuine selection or DOM-restoration callback was recorded" }
        val payload = selection.getJSONObject("payload")
        val pageIndex = payload.getInt("pageIndex")
        val quadsJson = payload.getJSONArray("quads")
        val clientRects = selection.getJSONArray("clientRects")
        assertTrue("Selection must carry actual selected DOM client rectangles", clientRects.length() > 0)
        assertEquals("Each real selection rect must map to a PDF quad", clientRects.length(), quadsJson.length())
        val frame = requireNotNull(host.frame) { "Native page frame was not observed" }
        assertEquals("The DOM selection and native raster must refer to the same physical page", pageIndex, frame.getInt("pageIndex"))
        val density = frame.optDouble("density", host.resources.displayMetrics.density.toDouble())
        val cssToDevice = selection.getDouble("devicePixelRatio")
        assertTrue("Observed WebView devicePixelRatio must be finite and positive", cssToDevice.isFinite() && cssToDevice > 0)
        val left = frame.getDouble("leftPx").toInt()
        val top = frame.getDouble("topPx").toInt()
        val width = frame.getDouble("widthPx").toInt()
        val height = frame.getDouble("heightPx").toInt()
        val nativeOrigin = IntArray(2)
        val hostOrigin = IntArray(2)
        val webOrigin = IntArray(2)
        instrumentation.runOnMainSync {
            host.native.getLocationOnScreen(nativeOrigin)
            host.getLocationOnScreen(hostOrigin)
            host.web.getLocationOnScreen(webOrigin)
        }
        val overlays = host.overlays
        assertEquals("Every selected DOM rect needs one actually drawn overlay quad", quadsJson.length(), overlays.size)

        val pfd = ParcelFileDescriptor.open(sourceFile(source), ParcelFileDescriptor.MODE_READ_ONLY)
        val core = PdfiumCore(context)
        var document: PdfDocument? = null
        try {
            val openedDocument = core.newDocument(pfd)
            document = openedDocument
            core.openPage(openedDocument, pageIndex)
            val box = payload.getJSONArray("pageBox")
            val x0 = box.getDouble(0); val y0 = box.getDouble(1)
            val x1 = box.getDouble(2); val y1 = box.getDouble(3)
            fun device(x: Double, y: Double) = core.mapPageCoordsToDevice(openedDocument, pageIndex, left, top, width, height, 0, x, y)
            val origin = device(x0, y0)
            val alongX = device(x1, y0)
            val alongY = device(x0, y1)
            val ux = (alongX.x - origin.x).toDouble(); val uy = (alongX.y - origin.y).toDouble()
            val vx = (alongY.x - origin.x).toDouble(); val vy = (alongY.y - origin.y).toDouble()
            val determinant = ux * vy - vx * uy
            assertTrue("PDFium CropBox forward mapping must define a nonsingular page transform", determinant != 0.0)
            for (rectIndex in 0 until quadsJson.length()) {
                val quad = quadsJson.getJSONArray(rectIndex).toFloatArray()
                val rect = clientRects.getJSONObject(rectIndex)
                val rectanglePoints = arrayOf(
                    doubleArrayOf(rect.getDouble("x"), rect.getDouble("y")),
                    doubleArrayOf(rect.getDouble("x") + rect.getDouble("width"), rect.getDouble("y")),
                    doubleArrayOf(rect.getDouble("x") + rect.getDouble("width"), rect.getDouble("y") + rect.getDouble("height")),
                    doubleArrayOf(rect.getDouble("x"), rect.getDouble("y") + rect.getDouble("height")),
                )
                val independentlyMapped = FloatArray(8)
                rectanglePoints.forEachIndexed { pointIndex, point ->
                    val screenX = webOrigin[0] + (point[0] * cssToDevice).toInt()
                    val screenY = webOrigin[1] + (point[1] * cssToDevice).toInt()
                    val deviceX = (screenX - nativeOrigin[0] - origin.x).toDouble()
                    val deviceY = (screenY - nativeOrigin[1] - origin.y).toDouble()
                    independentlyMapped[pointIndex * 2] = (x0 + (deviceX * vy - vx * deviceY) / determinant).toFloat()
                    independentlyMapped[pointIndex * 2 + 1] = (y0 + (ux * deviceY - deviceX * uy) / determinant).toFloat()
                }
                val canonicalPdf = canonicalQuad(independentlyMapped)
                PdfProofAssertions.geometry(canonicalPdf, quad)

                val independentlyDrawn = FloatArray(8)
                for (pointIndex in 0 until 4) {
                    val mapped: Point = core.mapPageCoordsToDevice(openedDocument, pageIndex, left, top, width, height, 0,
                        quad[pointIndex * 2].toDouble(), quad[pointIndex * 2 + 1].toDouble())
                    independentlyDrawn[pointIndex * 2] = (mapped.x + nativeOrigin[0] - hostOrigin[0]).toFloat()
                    independentlyDrawn[pointIndex * 2 + 1] = (mapped.y + nativeOrigin[1] - hostOrigin[1]).toFloat()
                }
                val drawnOverlay = overlays[rectIndex]
                assertEquals("Overlay must be a four-corner pixel quad", 8, drawnOverlay.size)
                PdfProofAssertions.overlay(FloatArray(8) { (independentlyDrawn[it] / density).toFloat() }, FloatArray(8) { (drawnOverlay[it] / density).toFloat() })
                val overlayCopy = drawnOverlay.copyOf()
                Log.i("HybridProof", "geometry label=$label; page=$pageIndex; frame=$frame; rect=$rect; pdfiumInverse=${canonicalPdf.contentToString()}; selected=${quad.contentToString()}; pdfiumDevice=${independentlyDrawn.contentToString()}; overlay=${overlayCopy.contentToString()}; tolerance=1pt/2dp")
            }
        } finally {
            document?.let(core::closeDocument)
            pfd.close()
        }
    }

    private fun JSONArray.toFloatArray() = FloatArray(length()) { getDouble(it).toFloat() }

    private fun canonicalQuad(points: FloatArray): FloatArray {
        val corners = (0 until 4).map { points[it * 2] to points[it * 2 + 1] }
        val ordered = corners.sortedWith(compareBy<Pair<Float, Float>> { it.second }.thenBy { it.first })
        return floatArrayOf(ordered[0].first, ordered[0].second, ordered[1].first, ordered[1].second,
            ordered[3].first, ordered[3].second, ordered[2].first, ordered[2].second)
    }
}
