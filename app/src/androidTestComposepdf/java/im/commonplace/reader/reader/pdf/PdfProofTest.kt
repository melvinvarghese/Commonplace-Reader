package im.commonplace.reader.reader.pdf

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import im.commonplace.reader.*
import im.commonplace.reader.data.*
import im.commonplace.reader.reader.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Refusal assertions are safety evidence, never durable-highlight acceptance. */
@RunWith(AndroidJUnit4::class)
class PdfProofTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val container get() = (context as CommonplaceApplication).container
    private fun state(scenario: ActivityScenario<ReaderActivity>): ReaderUiState {
        var result: ReaderUiState? = null
        scenario.onActivity { result = ViewModelProvider(it)[ReaderViewModel::class.java].uiState.value }
        return requireNotNull(result)
    }
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + 20_000_000_000L
        while (System.nanoTime() < until) { compose.waitForIdle(); if (condition()) return; Thread.sleep(40) }
        assertTrue("Timed out waiting for ComposePdfReader", condition())
    }
    private fun source(name: String, large: Boolean = false, isolated: Boolean = false) = PdfProofFixture.source(name, large, isolated)
    private fun launch(source: SourceEntity) = ActivityScenario.launch<ReaderActivity>(Intent(context, ReaderActivity::class.java).putExtra("sourceId", source.id))
    private fun ready(scenario: ActivityScenario<ReaderActivity>, label: String? = null) {
        await { state(scenario).phase != ReaderPhase.Opening }
        assertEquals("Both matrix APIs must render; Unsupported is not a feature pass", ReaderPhase.Ready, state(scenario).phase)
        compose.onNodeWithTag("nucleus-pdf-page").assertExists()
        var lastFrame = "No frame sampled"
        try { await {
            val pixels = compose.onNodeWithTag("nucleus-pdf-page").captureToImage().toPixelMap()
            var ink = 0
            for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) {
                val pixel = pixels[x, y]
                if (pixel.alpha > 0.9f && pixel.red < 0.5f && pixel.green < 0.5f && pixel.blue < 0.5f) ink++
            }
            lastFrame = "width=${pixels.width}; height=${pixels.height}; sampledInk=$ink"
            ink > 20
        } } catch (failure: AssertionError) {
            Log.e("PdfProof", "Painted-page gate failed: $label; $lastFrame", failure)
            if (label in listOf("rotated-cropped", "large")) {
                try {
                    val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/composepdf-proof-$label.png")
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
                } catch (_: Exception) { Log.w("PdfProof", "Failure screenshot unavailable") }
            }
            throw failure
        }
    }
    private fun withPdf(name: String, block: (SourceEntity, ActivityScenario<ReaderActivity>) -> Unit) {
        val source = source(name)
        Log.i("PdfProof", "engine=nucleus-composepdf; artifact=158.0.8111.0; api=${Build.VERSION.SDK_INT}; fixture=$name; sha256=${source.checksum}")
        launch(source).use { scenario -> ready(scenario, name.substringBefore('.')); block(source, scenario) }
    }
    private fun search(query: String): String {
        compose.onNodeWithTag("pdf-search-query").performTextReplacement(query)
        compose.onNodeWithText("Search PDF", useUnmergedTree = true).performClick()
        await { compose.onAllNodesWithTag("pdf-search-result").fetchSemanticsNodes().isNotEmpty() }
        return compose.onNodeWithTag("pdf-search-result").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
    }
    @Test fun renderingNavigationZoomAndRecreationOrExplicitUnsupported() = withPdf("repeated-two-page.pdf") { _, scenario ->
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.INTERNET))
        compose.onNodeWithText("Page 1 of 2").assertExists()
        compose.onNodeWithText("Next page").performClick()
        compose.onNodeWithText("Page 2 of 2").assertExists()
        compose.onNodeWithText("Zoom in").performClick()
        compose.onNodeWithText("Zoom 2×").assertExists()
        scenario.recreate(); ready(scenario)
        // A fresh host is expected; durable progress is separately NOT PROVEN.
        compose.onNodeWithText("Page 1 of 2").assertExists()
    }
    @Test fun exactNativeMultilingualSearchAndSelectionOrExplicitUnsupported() = withPdf("multilingual.pdf") { _, scenario ->
        val expected = "English café — Ελληνικά — Русский текст."
        val actual = search(expected)
        assertTrue(actual.contains(expected))
        PdfProofAssertions.exactText(expected, actual.substring(actual.indexOf(expected), actual.indexOf(expected) + expected.length))
        assertTrue(state(scenario).capabilities.select is CapabilityStatus.Unavailable)
        assertTrue(state(scenario).capabilities.highlightDisplay is CapabilityStatus.Unavailable)
        assertNull(state(scenario).selection)
        Log.i("PdfProof", "Exact native extraction/search only; UI selection callback and durable highlight FAIL/unavailable")
    }
    @Test fun repeatedTextSearchUsesZeroBasedNativePagesOrExplicitUnsupported() = withPdf("repeated-two-page.pdf") { _, _ ->
        search("Repeated passage: amber lantern.")
        compose.onNodeWithText("Matches on physical pages: 1, 2").assertExists()
    }
    @Test fun croppedRotatedGeometryIsNotInventedOrExplicitUnsupported() = withPdf("rotated-cropped.pdf") { _, scenario ->
        assertTrue(state(scenario).capabilities.highlightDisplay is CapabilityStatus.Unavailable)
        compose.onNodeWithText("Zoom in").performClick()
        compose.onNodeWithText("Zoom 2×").assertExists()
        assertTrue(state(scenario).capabilities.select is CapabilityStatus.Unavailable)
        assertNull(state(scenario).selection)
        Log.i("PdfProof", "FAIL mandatory geometry: public layout has no original crop box/rotation; 1-point/2dp quads/overlays at 1x/2x NOT PROVEN")
    }
    @Test fun noTextIsExactAndNotDiagnosedAsScanOrExplicitUnsupported() = withPdf("image-only-scan.pdf") { _, scenario ->
        PdfProofAssertions.unavailable(state(scenario).capabilities.select, "No selectable text available")
        compose.onNodeWithText("No selectable text available").assertExists()
    }
    @Test fun replacedBytesKeepWritingButNeverApplySyntheticSavedCoordinates() {
        val source = source("ordinary-text.pdf", isolated = true)
        val file = File(context.filesDir, source.assetPath)
        val original = file.readBytes()
        val location = SavedLocation(1, DocumentFormat.PDF, "nucleus-composepdf", """{"pageIndex":0,"pageBox":[0,0,612,792],"rotationDegrees":0,"coordinateSpace":"unrotated-pdf-points","quads":[[72,698,352.295,698,352.295,712,72,712]]}""")
        runBlocking {
            container.readerRepository.saveCapture(source.id, Capture("A quiet synthetic page for local reader testing.", location), "Keep café reflection", null)
            container.readerRepository.saveSourceAnnotation(source.id, "Independent source annotation")
            container.readerRepository.addBookmark(source.id, location.copy(payloadJson = location.payloadJson.replace("\"pageIndex\":0", "\"pageIndex\":1")))
            container.readerRepository.saveProgress(source.id, location)
        }
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/repeated-two-page.pdf").use { input -> file.outputStream().use(input::copyTo) }
            launch(source).use { scenario ->
                await { state(scenario).phase == ReaderPhase.Error && state(scenario).entries.size >= 2 }
                val saved = state(scenario)
                assertEquals("Document bytes changed. Saved locations are unresolved; your writing is retained.", saved.message)
                assertTrue(saved.entries.any { it.quotation == "A quiet synthetic page for local reader testing." && it.body == "Keep café reflection" })
                assertTrue(saved.entries.any { it.body == "Independent source annotation" && it.location == null })
                assertEquals(1, org.json.JSONObject(saved.bookmarks.single().location.payloadJson).getInt("pageIndex"))
                assertEquals(location, runBlocking { container.readerRepository.getProgress(source.id) })
                compose.onAllNodesWithTag("nucleus-pdf-page").assertCountEquals(0)
            }
        } finally { file.writeBytes(original) }
    }
    @Test fun malformedAndEncryptedDoNotCrashOrExplicitUnsupported() {
        for (name in listOf("malformed.pdf", "encrypted.pdf")) launch(source(name)).use { scenario ->
            await { state(scenario).phase == ReaderPhase.Error || state(scenario).phase == ReaderPhase.Unsupported }
            assertNotNull(state(scenario).message)
            compose.onAllNodesWithTag("nucleus-pdf-page").assertCountEquals(0)
        }
    }
    private fun recordMemory(phase: String): Int {
        val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys -t 5 meminfo ${context.packageName}")
        val dump = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        File(context.filesDir, "pdf-performance.txt").appendText("\n$phase; api=${Build.VERSION.SDK_INT}; sampledPssKiB=${info.totalPss}\n$dump\n")
        Log.i("PdfProof", "$phase; totalPssKiB=${info.totalPss}")
        // UTP uninstalls the app after connected tests; preserve every raw line in
        // its retained per-test logcat instead of relying on a later run-as cat.
        dump.lineSequence().forEach { Log.i("PdfMemory", "$phase | $it") }
        return info.totalPss
    }
    @Test fun largeDocumentDoesNotDuplicateFullFileBuffers() {
        val source = source("ordinary-text.pdf", large = true)
        File(context.filesDir, "pdf-performance.txt").writeText("run=${UUID.randomUUID()}; shared large fixture=${source.checksum}\n")
        val runtime = Runtime.getRuntime()
        val latencies = mutableListOf<Long>()
        val peaks = mutableListOf<Long>()
        val duplicateSignals = mutableListOf<Long>()
        val closes = mutableListOf<Int>()
        repeat(3) { cycle ->
            System.gc(); Thread.sleep(500)
            recordMemory("large-cycle=$cycle before-open; warm process, not a cold-process claim")
            val javaBefore = runtime.totalMemory() - runtime.freeMemory()
            val nativeBefore = Debug.getNativeHeapAllocatedSize()
            val running = java.util.concurrent.atomic.AtomicBoolean(true)
            val peakDelta = java.util.concurrent.atomic.AtomicLong(0)
            val peakPss = java.util.concurrent.atomic.AtomicLong(0)
            val sampler = kotlin.concurrent.thread(name = "pdf-memory-sampler") {
                while (running.get()) {
                    val delta = runtime.totalMemory() - runtime.freeMemory() - javaBefore + Debug.getNativeHeapAllocatedSize() - nativeBefore
                    peakDelta.updateAndGet { maxOf(it, delta) }
                    val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
                    peakPss.updateAndGet { maxOf(it, memory.totalPss.toLong()) }
                    Thread.sleep(10)
                }
            }
            val started = System.nanoTime()
            try {
                launch(source).use { scenario ->
                    ready(scenario, "large")
                    val elapsedMs = (System.nanoTime() - started) / 1_000_000
                    latencies += elapsedMs
                    recordMemory("large-cycle=$cycle after-first-painted-page")
                    Log.i("PdfProof", "large=104857600; cycle=$cycle; openMs=$elapsedMs; sampledPeakJavaNativeDelta=${peakDelta.get()}; sampledPeakPssKiB=${peakPss.get()}; 20-page navigation NOT RUN: identical large fixture has one page")
                }
            } finally {
                running.set(false); sampler.join(5000)
                peaks += peakPss.get(); duplicateSignals += peakDelta.get()
                Log.i("PdfProof", "large-cycle=$cycle final; sampledPeakPssKiB=${peakPss.get()}; sampledPeakJavaNativeDelta=${peakDelta.get()}; firstPaintMs=${latencies.getOrNull(cycle) ?: "NOT_REACHED"}; elapsedUntilCloseMs=${(System.nanoTime() - started) / 1_000_000}")
                System.gc(); Thread.sleep(500)
                closes += recordMemory("large-cycle=$cycle after-close; upstream async disposal completion unobservable")
            }
        }
        assertTrue("First painted page must be within 10 seconds: $latencies", latencies.all { it <= 10_000 })
        assertTrue("Sampled PSS must stay below 512 MiB: $peaks", peaks.all { it < 512 * 1024 })
        assertTrue("Post-close PSS growth exceeds 32 MiB: $closes", closes.last() - closes.first() <= 32 * 1024)
        assertTrue("Sampled two-full-buffer signal; inspected JNI copies the whole ByteArray: $duplicateSignals", duplicateSignals.all { it < 150L * 1024 * 1024 })
        // Sampling cannot prove an absent transient; inspected JNI confirms a complete copy.
    }
    @Test fun repeatedOpenCloseAndCancellationReleaseResources() {
        val source = source("ordinary-text.pdf")
        val readings = mutableListOf<Int>()
        repeat(3) { cycle ->
            launch(source).use(::ready)
            System.gc(); Thread.sleep(500)
            val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            readings += memory.totalPss
            Log.i("PdfProof", "closeCycle=$cycle; totalPssKiB=${memory.totalPss}; async upstream disposal completion not observable")
        }
        val large = source("ordinary-text.pdf", large = true)
        launch(large).use { scenario ->
            assertEquals("Cancellation must start while Opening", ReaderPhase.Opening, state(scenario).phase)
        } // Activity destruction cancels the open job; upstream disposal completion remains unobservable.
        assertTrue("Post-close PSS growth over three cycles exceeds 32 MiB", readings.last() - readings.first() <= 32 * 1024)
    }
}
