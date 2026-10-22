package im.commonplace.reader.reader.pdf

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.commonplace.reader.ReaderActivity
import im.commonplace.reader.reader.PdfProofAssertions
import im.commonplace.reader.reader.SavedLocation
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Two one-case invocations; the runner, not this class, proves the actual process death and new PID. */
@RunWith(AndroidJUnit4::class)
class HybridRecoveryProbe {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = HybridFixture.context
    private val repository get() = HybridFixture.container.readerRepository
    private val reflection = "Hybrid restart reflection."
    private val quotation = "Repeated passage: amber lantern."
    private val marker get() = File(context.filesDir, "hybrid-recovery-ready.json")

    private fun recoveryRunId(): String? {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        if (args.getString("hybridRecovery") != "true") return null
        val value = args.getString("hybridRun") ?: throw AssertionError("hybridRun UUID is required for recovery probes")
        try { UUID.fromString(value) } catch (_: IllegalArgumentException) { throw AssertionError("hybridRun must be a UUID") }
        return value
    }

    private fun launch(sourceId: String) = ActivityScenario.launch<ReaderActivity>(
        Intent(context, ReaderActivity::class.java).putExtra("sourceId", sourceId))

    private fun waitFor(condition: () -> Boolean) = HybridFixture.await("hybrid recovery state", timeoutMs = 30_000, condition = condition)

    @Test fun stageSelectedPassage() {
        val runId = recoveryRunId() ?: return
        val source = HybridFixture.source("repeated-two-page.pdf")
        val reflectionText = reflection
        launch(source.id).use { scenario ->
            val host = HybridFixture.host(scenario)
            compose.onNodeWithText("Next page").performClick()
            waitFor { host.pageInfo?.optInt("pageIndex", -1) == 1 }
            compose.onNodeWithText("Select text").performClick()
            HybridFixture.selectByTouch(scenario, host, quotation)
            compose.onNodeWithText("Keep selected passage").performClick()
            waitFor { host.lastSelection?.optString("quotation") == quotation && HybridFixture.state(scenario).selection?.quotation == quotation }
            val capture = requireNotNull(HybridFixture.state(scenario).selection)
            assertEquals(1, JSONObject(capture.location.payloadJson).getInt("pageIndex"))
            PdfProofAssertions.sameEngine(capture.location, im.commonplace.reader.BuildConfig.PDF_ENGINE_ID)
            compose.onNodeWithTag("reader-reflection").performTextReplacement(reflectionText)
            compose.onNodeWithTag("reader-save").performClick()
            waitFor { HybridFixture.state(scenario).entries.any { it.quotation == quotation && it.body == reflectionText } }
            val entry = runBlocking { repository.observeEntries(source.id).first().single { it.quotation == quotation && it.body == reflectionText } }
            assertEquals(capture.location, entry.location)
            val savedLocation = requireNotNull(entry.location)
            val record = JSONObject().put("runId", runId).put("sourceId", source.id).put("entryId", entry.id)
                .put("quotation", quotation).put("locationJson", savedLocation.toJson())
            marker.parentFile?.mkdirs()
            FileOutputStream(marker).use { stream -> stream.write((record.toString() + "\n").toByteArray(Charsets.UTF_8)); stream.fd.sync() }
            assertTrue("The recovery marker must be durable before the process is stopped", marker.isFile && marker.length() > 0)
            HybridFixture.screenshot("recovery-staged-page-two")
        }
    }

    @Test fun verifyRestoredPassage() {
        val runId = recoveryRunId() ?: return
        assertTrue("The runner must preserve the durable staging marker across the confirmed force-stop", marker.isFile)
        val record = JSONObject(marker.readText())
        assertEquals(runId, record.getString("runId"))
        val sourceId = record.getString("sourceId")
        val entryId = record.getString("entryId")
        val exactQuotation = record.getString("quotation")
        assertEquals(quotation, exactQuotation)
        val storedLocation = SavedLocation.fromJson(record.getString("locationJson"))
        val entry = runBlocking { repository.observeEntries(sourceId).first().single { it.id == entryId } }
        assertEquals(exactQuotation, entry.quotation)
        assertEquals(reflection, entry.body)
        assertEquals(storedLocation, entry.location)
        assertEquals("Exact stored location JSON must survive process death", storedLocation.toJson(),
            record.getString("locationJson"))
        PdfProofAssertions.sameEngine(storedLocation, im.commonplace.reader.BuildConfig.PDF_ENGINE_ID)
        assertEquals(1, storedLocation.physicalPageNumber - 1)
        val source = requireNotNull(runBlocking { repository.getSource(sourceId) })
        val sourceManifest = HybridFixture.manifestEntry("repeated-two-page.pdf")
        assertEquals(sourceManifest.getString("sha256"), source.checksum)

        launch(sourceId).use { scenario ->
            val host = HybridFixture.host(scenario)
            compose.onNodeWithText("Saved items").performClick()
            compose.onNodeWithTag("reader-open-capture").performClick()
            waitFor { host.lastRestored?.optString("quotation") == exactQuotation }
            val restored = requireNotNull(host.lastRestored)
            assertTrue("The reopened selection payload must equal the Room payload",
                kotlinx.serialization.json.Json.parseToJsonElement(storedLocation.payloadJson) == kotlinx.serialization.json.Json.parseToJsonElement(restored.getJSONObject("payload").toString()))
            assertEquals(1, restored.getJSONObject("payload").getInt("pageIndex"))
            assertEquals(exactQuotation, restored.getString("quotation"))
            HybridFixture.assertIndependentGeometry(source, host, "recovery-restored-1x")
            HybridFixture.screenshot("recovery-restored-1x")

            compose.onNodeWithText("Zoom 1×").performClick()
            waitFor { host.frame?.optInt("zoom", 0) == 2 && host.overlays.isNotEmpty() && host.lastRestored?.optString("quotation") == exactQuotation }
            HybridFixture.assertIndependentGeometry(source, host, "recovery-restored-2x")
            HybridFixture.screenshot("recovery-restored-2x")
        }
    }
}
