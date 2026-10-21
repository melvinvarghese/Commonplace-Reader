package im.commonplace.reader.reader.pdf

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ext.SdkExtensions
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.pdf.PdfDocument
import androidx.pdf.content.PdfPageTextContent
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfViewerFragment
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

@RunWith(AndroidJUnit4::class)
class PdfProofTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val container get() = (context as CommonplaceApplication).container
    private val supported get() = Build.VERSION.SDK_INT >= 31 && SdkExtensions.getExtensionVersion(31) >= 13
    private fun state(scenario: ActivityScenario<ReaderActivity>): ReaderUiState {
        var result: ReaderUiState? = null
        scenario.onActivity { result = ViewModelProvider(it)[ReaderViewModel::class.java].uiState.value }
        return requireNotNull(result)
    }
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + 20_000_000_000L
        while (System.nanoTime() < until) { compose.waitForIdle(); if (condition()) return; Thread.sleep(40) }
        assertTrue("Timed out waiting for native PDF result", condition())
    }
    private fun native(scenario: ActivityScenario<ReaderActivity>): PdfView {
        var found: PdfView? = null
        await {
            scenario.onActivity { activity ->
                fun find(view: View): PdfView? = if (view is PdfView) view else if (view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
                found = find(activity.window.decorView)?.takeIf { it.pdfDocument != null && it.width > 0 && it.arePagesFullyRendered() }
            }
            found != null
        }
        return requireNotNull(found)
    }
    private fun source(name: String): SourceEntity = runBlocking(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val file = File(context.filesDir, "imports/assets/$id.pdf").apply { parentFile!!.mkdirs() }
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/$name").use { input -> file.outputStream().use(input::copyTo) }
        val checksum = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val source = SourceEntity(id, "PDF proof", null, DocumentFormat.PDF, "content://synthetic/$name", checksum, "imports/assets/$id.pdf", SourceAvailability.AVAILABLE, 1, 1)
        // Exact duplicates must use the existing source, as the actual importer does.
        container.database.readerDao().sourceByChecksum(checksum) ?: source.also { container.database.readerDao().insertSource(it) }
    }
    private fun withPdf(name: String, block: (SourceEntity, ActivityScenario<ReaderActivity>) -> Unit) {
        val source = source(name)
        ActivityScenario.launch<ReaderActivity>(Intent(context, ReaderActivity::class.java).putExtra("sourceId", source.id)).use { scenario ->
            await { state(scenario).phase != ReaderPhase.Opening }
            Log.i("PdfProof", "api=${Build.VERSION.SDK_INT}; extensionS=${if (Build.VERSION.SDK_INT >= 30) SdkExtensions.getExtensionVersion(31) else 0}; fixture=$name; sha256=${source.checksum}")
            if (!supported) {
                assertEquals(ReaderPhase.Unsupported, state(scenario).phase)
                assertTrue(state(scenario).message.orEmpty().contains("API 31 and SDK extension 13"))
                scenario.onActivity { assertTrue(it.supportFragmentManager.fragments.none { f -> f is PdfViewerFragment }) }
                Log.i("PdfProof", "UNSUPPORTED: viewer requires API 31 and SDK extension 13; no native feature pass")
            } else block(source, scenario)
        }
    }
    @Test fun renderingNavigationZoomAndRecreationOrExplicitUnsupported() = withPdf("repeated-two-page.pdf") { _, scenario ->
        val view = native(scenario)
        assertEquals(2, view.pdfDocument!!.pageCount)
        scenario.onActivity { view.scrollToPage(1); view.zoom = 2f }
        await { view.firstVisiblePage == 1 }
        assertEquals(2f, view.zoom, 0.01f)
        scenario.recreate()
        assertNotSame(view, native(scenario))
        assertFalse(view.isAttachedToWindow)
    }
    @Test fun exactNativeMultilingualSearchAndSelectionOrExplicitUnsupported() = withPdf("multilingual.pdf") { _, scenario ->
        val view = native(scenario)
        val text = "English café — Ελληνικά — Русский текст."
        val document = requireNotNull(view.pdfDocument)
        val matches = runBlocking { document.searchDocument(text, 0..0) }
        assertEquals(1, matches[0].size)
        val bounds = matches[0].single().bounds
        val selection = runBlocking { document.getSelectionBounds(0, android.graphics.PointF(bounds.first().left, bounds.first().centerY()), android.graphics.PointF(bounds.last().right, bounds.last().centerY())) }
        PdfProofAssertions.exactText(text, requireNotNull(selection).selectedContents.filterIsInstance<PdfPageTextContent>().joinToString("") { it.text })
        // This is a native document selection result, not a fabricated UI callback.
        // Durable capture remains unavailable until original crop/rotation can be mapped.
        assertTrue(state(scenario).capabilities.highlightDisplay is CapabilityStatus.Unavailable)
    }
    @Test fun repeatedTextSearchUsesZeroBasedNativePagesOrExplicitUnsupported() = withPdf("repeated-two-page.pdf") { _, scenario ->
        val document = requireNotNull(native(scenario).pdfDocument)
        val matches = runBlocking { document.searchDocument("Repeated passage: amber lantern.", 0..1) }
        assertEquals(2, matches.size())
        assertEquals(1, matches[0].size); assertEquals(1, matches[1].size)
    }
    @Test fun croppedRotatedGeometryIsNotInventedOrExplicitUnsupported() = withPdf("rotated-cropped.pdf") { _, scenario ->
        val view = native(scenario)
        scenario.onActivity { view.zoom = 1f }
        assertTrue(state(scenario).capabilities.highlightDisplay is CapabilityStatus.Unavailable)
        scenario.onActivity { view.zoom = 2f }
        assertTrue(state(scenario).capabilities.select is CapabilityStatus.Unavailable)
        assertNull(state(scenario).selection)
        Log.i("PdfProof", "FAIL mandatory geometry: no public original page-box/rotation metadata; 1-point/2dp alignment and durable capture NOT PROVEN")
    }
    @Test fun noTextIsExactAndNotDiagnosedAsScanOrExplicitUnsupported() = withPdf("image-only-scan.pdf") { _, scenario ->
        native(scenario)
        await { state(scenario).capabilities.select == CapabilityStatus.Unavailable("No selectable text available") }
        PdfProofAssertions.unavailable(state(scenario).capabilities.select, "No selectable text available")
    }
    @Test fun malformedAndEncryptedDoNotCrashOrExplicitUnsupported() {
        for (name in listOf("malformed.pdf", "encrypted.pdf")) withPdf(name) { _, scenario ->
            await { state(scenario).phase == ReaderPhase.Error || state(scenario).phase == ReaderPhase.Unsupported }
            assertNotNull(state(scenario).message)
        }
    }
}
