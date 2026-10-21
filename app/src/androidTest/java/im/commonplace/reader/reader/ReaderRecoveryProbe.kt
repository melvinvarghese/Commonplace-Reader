package im.commonplace.reader.reader

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import im.commonplace.reader.CommonplaceApplication
import im.commonplace.reader.ReaderActivity
import im.commonplace.reader.data.EntryKind
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import java.io.File
import java.io.FileOutputStream

/** Only executed explicitly by tools/run_reader_recovery.py, never counted as a skipped proof. */
@RunWith(AndroidJUnit4::class)
class ReaderRecoveryProbe {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val repository get() = (context as CommonplaceApplication).container.readerRepository
    private fun marker(name: String) = File(context.filesDir, name)
    private fun durableWrite(name: String, text: String) { FileOutputStream(marker(name)).use { it.write(text.toByteArray()); it.fd.sync() } }
    private fun modelState(scenario: ActivityScenario<ReaderActivity>): ReaderUiState {
        var state: ReaderUiState? = null
        scenario.onActivity { state = androidx.lifecycle.ViewModelProvider(it)[ReaderViewModel::class.java].uiState.value }
        return requireNotNull(state)
    }
    private fun fixtureDiagnostics(scenario: ActivityScenario<ReaderActivity>, sourceId: String): String {
        val state = modelState(scenario)
        val roomDraft = runBlocking { repository.getDraft(sourceId) }
        fun capture(value: im.commonplace.reader.reader.Capture?) = value?.let {
            "quotation=${JSONObject.quote(it.quotation)}, payload=${it.location.payloadJson}"
        } ?: "null"
        return "Fixture model(status=${state.captureStatus}, reflection=${state.reflection?.let { JSONObject.quote(it) }}, selection=${capture(state.selection)}, draftSaved=${state.draftSaved}); Room draft=${roomDraft?.let { "reflection=${it.reflection?.let { text -> JSONObject.quote(text) }}, selection=${capture(it.capture)}" } ?: "null"}"
    }

    @Test fun stageConfirmedReader() {
        if (InstrumentationRegistry.getArguments().getString("readerRecovery") != "stage") return
        val source = runBlocking { ReaderProofFixture.import(context) }
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            val db = (context as CommonplaceApplication).container.database.openHelper.writableDatabase
            for (table in listOf("entries", "bookmarks", "reading_state", "reader_drafts")) db.execSQL("DELETE FROM $table WHERE sourceId = ?", arrayOf(source.id))
        }
        ActivityScenario.launch<ReaderActivity>(Intent(context, ReaderActivity::class.java).putExtra("sourceId", source.id)).use { scenario ->
            val native = ReadiumProofTest.navigator(scenario, compose)
            ReadiumProofTest.waitForWeb(scenario, "one.xhtml")
            val selected = ReadiumProofTest.selectSecond(scenario, native)
            compose.onNodeWithText("Keep selected passage").performScrollTo().performClick()
            compose.onNodeWithTag("reader-reflection").performScrollTo().performTextInput(ReaderProofFixture.REFLECTION)
            compose.onNodeWithTag("reader-save").performScrollTo().performClick()
            ReadiumProofTest.await(message = { fixtureDiagnostics(scenario, source.id) }) {
                compose.waitForIdle()
                modelState(scenario).captureStatus == CaptureStatus.Saved && runBlocking {
                    repository.observeEntries(source.id).first().any { it.body == ReaderProofFixture.REFLECTION } && repository.getDraft(source.id) == null
                }
            }
            val entry = runBlocking { repository.observeEntries(source.id).first().single { it.kind == EntryKind.HIGHLIGHT } }
            assertEquals(selected.toJSON().toString(), entry.location!!.payloadJson)
            ReadiumProofTest.go(scenario, native, Locator.fromJSON(JSONObject("""{"href":"EPUB/two.xhtml","type":"application/xhtml+xml"}"""))!!)
            ReadiumProofTest.waitForWeb(scenario, "two.xhtml")
            compose.onNodeWithText("Bookmark").performScrollTo().performClick()
            ReadiumProofTest.await { runBlocking { repository.getBookmarks(source.id).size == 1 } }
            val expectedBookmark = runBlocking { repository.getBookmarks(source.id).single().location.payloadJson }
            ReadiumProofTest.script(scenario, "two.xhtml", """(function(){var node=document.querySelector('p').firstChild;var range=document.createRange();range.selectNodeContents(node);var selection=window.getSelection();selection.removeAllRanges();selection.addRange(range);return selection.toString();})()""")
            val draftSelection = runBlocking { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { native.currentSelection() } }!!.locator
            assertEquals("A different place for a durable bookmark.", draftSelection.text.highlight)
            val expectedDraft = "Acknowledged draft survives actual process death."
            val expectedDraftQuotation = draftSelection.text.highlight
            val expectedDraftSelection = draftSelection.toJSON().toString()
            fun hasDraftSelection(state: ReaderUiState) = state.selection?.let {
                it.quotation == expectedDraftQuotation && it.location.payloadJson == expectedDraftSelection
            } == true
            compose.onNodeWithText("Keep selected passage").performScrollTo().performClick()
            ReadiumProofTest.await(message = { fixtureDiagnostics(scenario, source.id) }) {
                compose.waitForIdle()
                val state = modelState(scenario)
                state.captureStatus == CaptureStatus.Unsaved && hasDraftSelection(state)
            }
            val beforeTyping = modelState(scenario)
            assertTrue("Reflection must be empty before typing. ${fixtureDiagnostics(scenario, source.id)}", beforeTyping.reflection.isNullOrEmpty())
            compose.onNodeWithTag("reader-reflection").performScrollTo().performTextInput(expectedDraft)
            ReadiumProofTest.await(message = { fixtureDiagnostics(scenario, source.id) }) {
                compose.waitForIdle()
                val state = modelState(scenario)
                state.reflection == expectedDraft && hasDraftSelection(state)
            }
            val afterTyping = modelState(scenario)
            assertEquals("Model reflection after typing. ${fixtureDiagnostics(scenario, source.id)}", expectedDraft, afterTyping.reflection)
            assertTrue("Model capture after typing. ${fixtureDiagnostics(scenario, source.id)}", hasDraftSelection(afterTyping))
            ReadiumProofTest.await(message = { fixtureDiagnostics(scenario, source.id) }) {
                compose.waitForIdle()
                val state = modelState(scenario)
                val draft = runBlocking { repository.getDraft(source.id) }
                state.draftSaved && draft?.let {
                    it.reflection == expectedDraft && it.capture.quotation == expectedDraftQuotation &&
                        it.capture.location.payloadJson == expectedDraftSelection
                } == true
            }
            val expectedProgress = ReadiumProofTest.onMain { native.currentLocator.value.toJSON().toString() }
            scenario.moveToState(Lifecycle.State.CREATED)
            ReadiumProofTest.await { runBlocking { repository.getProgress(source.id)?.payloadJson == expectedProgress } }
            durableWrite("reader-recovery-state.json", JSONObject().put("sourceId", source.id).put("entryId", entry.id).put("selection", selected.toJSON().toString()).put("bookmark", expectedBookmark).put("progress", expectedProgress).put("draftSelection", draftSelection.toJSON().toString()).toString())
            durableWrite("reader-recovery-ready", "ready")
        }
    }

    @Test fun verifyRestoredReader() {
        if (InstrumentationRegistry.getArguments().getString("readerRecovery") != "verify") return
        val expected = JSONObject(marker("reader-recovery-state.json").readText())
        val sourceId = expected.getString("sourceId")
        val entry = runBlocking { repository.observeEntries(sourceId).first().single { it.id == expected.getString("entryId") } }
        assertEquals(ReaderProofFixture.QUOTATION, entry.quotation)
        assertEquals(ReaderProofFixture.REFLECTION, entry.body)
        assertEquals(expected.getString("selection"), entry.location!!.payloadJson)
        val restoredDraft = runBlocking { repository.getDraft(sourceId) }!!
        assertEquals("Acknowledged draft survives actual process death.", restoredDraft.reflection)
        assertEquals(expected.getString("draftSelection"), restoredDraft.capture.location.payloadJson)
        assertEquals(expected.getString("bookmark"), runBlocking { repository.getBookmarks(sourceId).single().location.payloadJson })
        assertEquals(expected.getString("progress"), runBlocking { repository.getProgress(sourceId)!!.payloadJson })
        ActivityScenario.launch<ReaderActivity>(Intent(context, ReaderActivity::class.java).putExtra("sourceId", sourceId)).use { scenario ->
            val native = ReadiumProofTest.navigator(scenario, compose)
            ReadiumProofTest.waitForWeb(scenario, "two.xhtml")
            compose.onNodeWithTag("reader-reflection").performScrollTo().assertTextContains("Acknowledged draft survives actual process death.")
            assertEquals("EPUB/two.xhtml", ReadiumProofTest.onMain { native.currentLocator.value.href.toString() })
            compose.onNodeWithText("Saved items").performScrollTo().performClick()
            compose.onAllNodesWithTag("reader-open-capture").onFirst().performScrollTo().performClick()
            ReadiumProofTest.waitForWeb(scenario, "one.xhtml")
            ReadiumProofTest.await { ReadiumProofTest.script(scenario, "one.xhtml", "document.querySelectorAll('.commonplace-proof-highlight').length").trim('"') != "0" }
            assertTrue(ReadiumProofTest.script(scenario, "one.xhtml", "document.body.textContent").contains(ReaderProofFixture.MULTILINGUAL))
            assertEquals(expected.getString("selection"), runBlocking { repository.observeEntries(sourceId).first().single { it.id == entry.id }.location!!.payloadJson })
        }
    }
}
