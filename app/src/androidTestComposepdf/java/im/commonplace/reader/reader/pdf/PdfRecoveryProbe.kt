package im.commonplace.reader.reader.pdf

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import im.commonplace.reader.CommonplaceApplication
import im.commonplace.reader.ReaderActivity
import im.commonplace.reader.reader.*
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Annotation-only safety proof; no fabricated native PDF capture, bookmark or overlay. */
@RunWith(AndroidJUnit4::class)
class PdfRecoveryProbe {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val repository get() = (context as CommonplaceApplication).container.readerRepository
    private val annotation = "PDF annotation survives actual process death — café."
    private fun marker(name: String) = File(context.filesDir, "pdf-recovery-$name")
    private fun durableWrite(name: String, value: String) { FileOutputStream(marker(name)).use { it.write(value.toByteArray()); it.fd.sync() } }
    private fun state(scenario: ActivityScenario<ReaderActivity>): ReaderUiState {
        var result: ReaderUiState? = null
        scenario.onActivity { result = ViewModelProvider(it)[ReaderViewModel::class.java].uiState.value }
        return requireNotNull(result)
    }
    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + 20_000_000_000L
        while (System.nanoTime() < end) { compose.waitForIdle(); if (condition()) return; Thread.sleep(40) }
        assertTrue("PDF annotation recovery timed out", condition())
    }
    private fun ready(scenario: ActivityScenario<ReaderActivity>) {
        await { state(scenario).phase != ReaderPhase.Opening }
        assertEquals(ReaderPhase.Ready, state(scenario).phase)
        compose.onNodeWithTag("nucleus-pdf-page").assertExists()
    }
    private fun launch(id: String) = ActivityScenario.launch<ReaderActivity>(Intent(context, ReaderActivity::class.java).putExtra("sourceId", id))
    @Test fun stageConfirmedPdfAnnotation() {
        if (InstrumentationRegistry.getArguments().getString("pdfRecovery") != "stage") return
        val source = PdfProofFixture.source("ordinary-text.pdf")
        launch(source.id).use { scenario ->
            ready(scenario)
            compose.onNodeWithText("Saved items").performClick()
            compose.onNodeWithTag("reader-annotation").performScrollTo().performTextReplacement(annotation)
            compose.onNodeWithText("Save annotation").performScrollTo().performClick()
            await { state(scenario).message == "Annotation saved" && state(scenario).entries.any { it.body == annotation && it.location == null } }
            val entry = runBlocking { repository.observeEntries(source.id).first().last { it.body == annotation } }
            durableWrite("state.json", JSONObject().put("sourceId", source.id).put("entryId", entry.id).toString())
            durableWrite("ready", "ready")
        }
    }
    @Test fun verifyRestoredPdfAnnotation() {
        if (InstrumentationRegistry.getArguments().getString("pdfRecovery") != "verify") return
        val expected = JSONObject(marker("state.json").readText())
        val id = expected.getString("sourceId")
        val entry = runBlocking { repository.observeEntries(id).first().single { it.id == expected.getString("entryId") } }
        assertEquals(annotation, entry.body)
        assertNull(entry.quotation); assertNull(entry.location)
        launch(id).use { scenario ->
            ready(scenario)
            assertTrue(state(scenario).capabilities.select is CapabilityStatus.Unavailable)
            assertTrue(state(scenario).capabilities.highlightDisplay is CapabilityStatus.Unavailable)
            compose.onNodeWithText("Saved items").performClick()
            compose.onAllNodesWithText(annotation).onLast().performScrollTo().assertExists()
        }
    }
}
