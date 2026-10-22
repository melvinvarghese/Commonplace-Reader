package im.commonplace.reader.reader.pdf

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.commonplace.reader.ReaderActivity
import im.commonplace.reader.reader.Capture
import im.commonplace.reader.reader.DocumentFormat
import im.commonplace.reader.reader.PdfProofAssertions
import im.commonplace.reader.reader.ReaderPhase
import im.commonplace.reader.reader.ReaderViewModel
import im.commonplace.reader.reader.SavedLocation
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Four focused acceptance gates for the disposable hybrid spike. */
@RunWith(AndroidJUnit4::class)
class HybridProofTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = HybridFixture.context
    private val container get() = HybridFixture.container

    private fun launch(sourceId: String) = ActivityScenario.launch<ReaderActivity>(
        Intent(context, ReaderActivity::class.java).putExtra("sourceId", sourceId))

    private fun waitFor(condition: () -> Boolean) = HybridFixture.await("hybrid reader state", condition = condition)

    @Test fun offlineNativeRenderingAndRealTextGesture() {
        Log.i("HybridProof", "START API ${Build.VERSION.SDK_INT}; disposable hybrid PDF.js 4.10.38 + AndroidPdfViewer 3.1.0-beta.1")
        assertEquals("PDF.js bytes are served locally; network permission is not part of this proof",
            android.content.pm.PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(android.Manifest.permission.INTERNET))
        realGestureAndNativeRender()
    }

    @Test fun rotatedCroppedMetadataAndGeometryAtOneAndTwoTimes() {
        croppedGeometryAtBothZoomLevels()
    }

    @Test fun duplicateSecondPageSelectionRoomSaveAndReopen() {
        repeatedSecondPageRoomSaveAndReopen()
    }

    @Test fun changedBytesRejectLocationAndRetainWriting() {
        changedBytesRetainWriting()
    }

    private fun realGestureAndNativeRender() {
        val firstCopy = HybridFixture.source("ordinary-text.pdf")
        val source = HybridFixture.source("ordinary-text.pdf")
        assertNotEquals("Test fixture rows must be isolated before reusing a unique production checksum", firstCopy.id, source.id)
        assertEquals("Fixture isolation must retain the actual content checksum", firstCopy.checksum, source.checksum)
        val quotation = "A quiet synthetic page for local reader testing."
        launch(source.id).use { scenario ->
            waitFor { HybridFixture.state(scenario).phase != ReaderPhase.Opening }
            val host = HybridFixture.host(scenario)
            HybridFixture.assertStartupAndWorker(scenario, host)
            assertTrue(host.ready)
            assertEquals(1, host.pageCount)
            assertNull("A completed host must not hide its render/JS failure", host.failure)
            HybridFixture.assertNativeInk(host, "ordinary-native-render")
            HybridFixture.screenshot("ordinary-before-gesture")
            compose.onNodeWithText("Select text").performClick()
            HybridFixture.selectByTouch(scenario, host, quotation)
            compose.onNodeWithText("Keep selected passage").performClick()
            waitFor { host.lastSelection?.optString("quotation") == quotation && HybridFixture.state(scenario).selection?.quotation == quotation }
            PdfProofAssertions.exactText(quotation, host.lastSelection!!.getString("quotation"))
            assertEquals("A genuine page-1 selection must carry a zero-based location", 0,
                host.lastSelection!!.getJSONObject("payload").getInt("pageIndex"))
            HybridFixture.screenshot("ordinary-after-real-touch")
            Log.i("HybridProof", "PASS gate=offline-render-and-real-native-touch; source=${source.checksum}; selection=${host.lastSelection}")
        }
    }

    private fun croppedGeometryAtBothZoomLevels() {
        val source = HybridFixture.source("rotated-cropped.pdf")
        val quotation = "Crop and rotation reference phrase."
        launch(source.id).use { scenario ->
            val host = HybridFixture.host(scenario)
            val expected = HybridFixture.manifestEntry("rotated-cropped.pdf").getJSONObject("expected")
            for (zoom in listOf(1, 2)) {
                waitFor { host.ready && host.frame?.optInt("zoom", 0) == zoom }
                val pageInfo = requireNotNull(host.pageInfo) { "PDF.js page metadata must be available" }
                assertEquals(4, pageInfo.getJSONArray("pageBox").length())
                for (bound in 0 until 4) assertEquals(expected.getJSONArray("pageBox").getDouble(bound),
                    pageInfo.getJSONArray("pageBox").getDouble(bound), 0.0)
                assertEquals(expected.getInt("rotationDegrees"), pageInfo.getInt("rotationDegrees"))
                assertEquals("CropBox metadata must be real PDF.js page metadata", 36.0,
                    pageInfo.getJSONArray("pageBox").getDouble(0), 0.0)
                compose.onNodeWithText("Select text").performClick()
                HybridFixture.selectByTouch(scenario, host, quotation)
                compose.onNodeWithText("Keep selected passage").performClick()
                waitFor { host.lastSelection?.optString("quotation") == quotation && host.lastSelection?.getJSONObject("frame")?.optInt("zoom", 0) == zoom && host.overlays.isNotEmpty() }
                assertEquals(zoom, host.frame!!.getInt("zoom"))
                HybridFixture.assertIndependentGeometry(source, host, "rotated-cropped-${zoom}x")
                HybridFixture.screenshot("rotated-cropped-${zoom}x")
                if (zoom == 1) compose.onNodeWithText("Zoom 1×").performClick()
                else compose.onNodeWithText("Zoom 2×").performClick()
                waitFor { host.frame?.optInt("zoom", 0) == if (zoom == 1) 2 else 1 }
                Log.i("HybridProof", "PASS gate=cropped-native-pdfium-and-dom-geometry; zoom=${zoom}x; pageInfo=$pageInfo")
            }
        }
    }

    private fun repeatedSecondPageRoomSaveAndReopen() {
        val source = HybridFixture.source("repeated-two-page.pdf")
        val quotation = "Repeated passage: amber lantern."
        val reflection = "Hybrid page-two reflection."
        launch(source.id).use { scenario ->
            val host = HybridFixture.host(scenario)
            assertEquals(2, host.pageCount)
            compose.onNodeWithText("Next page").performClick()
            waitFor { host.pageInfo?.optInt("pageIndex", -1) == 1 }
            compose.onNodeWithText("Select text").performClick()
            HybridFixture.selectByTouch(scenario, host, quotation)
            compose.onNodeWithText("Keep selected passage").performClick()
            waitFor { host.lastSelection?.optString("quotation") == quotation && HybridFixture.state(scenario).selection?.quotation == quotation }
            val selected = requireNotNull(HybridFixture.state(scenario).selection)
            assertEquals("The exact duplicate must come from physical page two", 1,
                selected.location.payloadJson.let { JSONObject(it).getInt("pageIndex") })
            PdfProofAssertions.sameEngine(selected.location, im.commonplace.reader.BuildConfig.PDF_ENGINE_ID)
            compose.onNodeWithTag("reader-reflection").performTextReplacement(reflection)
            compose.onNodeWithTag("reader-save").performClick()
            waitFor { HybridFixture.state(scenario).entries.any { it.quotation == quotation && it.body == reflection } }
            val persisted = runBlocking { container.readerRepository.observeEntries(source.id).first().single { it.quotation == quotation && it.body == reflection } }
            assertEquals(selected.location, persisted.location)
            assertEquals(2, persisted.location!!.physicalPageNumber)
            HybridFixture.assertIndependentGeometry(source, host, "repeated-page-two-saved")
            compose.onNodeWithText("Saved items").performClick()
            compose.onNodeWithTag("reader-open-capture").performClick()
            waitFor { host.lastRestored?.optString("quotation") == quotation }
            val restored = requireNotNull(host.lastRestored)
            assertTrue("Reopening must reproduce the exact persisted engine payload",
                kotlinx.serialization.json.Json.parseToJsonElement(persisted.location!!.payloadJson) == kotlinx.serialization.json.Json.parseToJsonElement(restored.getJSONObject("payload").toString()))
            assertEquals(1, restored.getJSONObject("payload").getInt("pageIndex"))
            assertEquals(quotation, restored.getString("quotation"))
            HybridFixture.assertIndependentGeometry(source, host, "repeated-page-two-reopened")
            compose.onNodeWithText("Zoom 1×").performClick()
            waitFor { host.frame?.optInt("zoom", 0) == 2 && host.overlays.isNotEmpty() && host.lastRestored?.optString("quotation") == quotation }
            HybridFixture.assertIndependentGeometry(source, host, "repeated-page-two-reopened-2x")
            compose.onNodeWithText("Zoom 2×").performClick()
            Log.i("HybridProof", "PASS gate=exact-page-two-selection-room-save-reopen; entry=${persisted.id}; location=${persisted.location!!.payloadJson}")
        }
    }

    private fun changedBytesRetainWriting() {
        val source = HybridFixture.source("ordinary-text.pdf")
        val file = HybridFixture.sourceFile(source)
        val original = file.readBytes()
        val quotation = "A quiet synthetic page for local reader testing."
        val synthetic = SavedLocation(1, DocumentFormat.PDF, im.commonplace.reader.BuildConfig.PDF_ENGINE_ID,
            """{"pageIndex":0,"pageBox":[0,0,612,792],"rotationDegrees":0,"coordinateSpace":"unrotated-pdf-points","quads":[[72,698,352.295,698,352.295,712,72,712]],"native":{"start":{"spanIndex":0,"offset":0},"end":{"spanIndex":0,"offset":${quotation.length}}}}""")
        val reflection = "Writing survives changed bytes."
        try {
            // This is intentionally storage-safety setup, not evidence of real selection or capture.
            runBlocking {
                container.readerRepository.saveCapture(source.id, Capture(quotation, synthetic), reflection, null)
                container.readerRepository.saveSourceAnnotation(source.id, "Independent retained source annotation")
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
                .open("fixtures/repeated-two-page.pdf").use { input -> file.outputStream().use(input::copyTo) }
            launch(source.id).use { scenario ->
                waitFor { HybridFixture.state(scenario).phase == ReaderPhase.Error && HybridFixture.state(scenario).entries.size >= 2 }
                val state = HybridFixture.state(scenario)
                assertEquals("Document bytes changed. Saved locations are unresolved; your writing is retained.", state.message)
                assertTrue(state.entries.any { it.quotation == quotation && it.body == reflection })
                assertTrue(state.entries.any { it.body == "Independent retained source annotation" && it.location == null })
                assertTrue("Unverified saved coordinates must not reopen an engine", state.phase == ReaderPhase.Error)
                val host = HybridFixture.findHostIn(scenario)
                assertNotNull("Changed bytes must be rejected by the host before saved locations are replayed", host)
                assertFalse("A source with changed bytes must never become ready", requireNotNull(host).ready)
                assertNotNull("The verified-source rejection should be visible", host.failure)
                Log.i("HybridProof", "PASS gate=changed-bytes-retain-writing; synthetic seed explicitly NOT real selection; source=${source.id}")
            }
        } finally {
            file.writeBytes(original)
        }
    }
}
