package im.commonplace.reader.reader

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
import im.commonplace.reader.MainActivity
import im.commonplace.reader.ReaderActivity
import im.commonplace.reader.data.SourceEntity
import im.commonplace.reader.reader.ReadiumProofTest.Companion.await
import im.commonplace.reader.reader.ReadiumProofTest.Companion.navigator
import im.commonplace.reader.reader.ReadiumProofTest.Companion.waitForWeb
import im.commonplace.reader.reader.ReadiumProofTest.Companion.script
import im.commonplace.reader.reader.ReadiumProofTest.Companion.selectSecond
import im.commonplace.reader.reader.ReadiumProofTest.Companion.go
import im.commonplace.reader.reader.epub.ReadiumLocations
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import java.io.File

/** Only original disposable fixture bytes; no personal library data. */
@im.commonplace.reader.notebook.NotebookProof
@RunWith(AndroidJUnit4::class)
class NotebookPassageTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val container get()=(context as CommonplaceApplication).container
    private val repository get()=container.readerRepository
    private lateinit var source: SourceEntity
    private lateinit var firstId: String
    private lateinit var secondId: String
    private lateinit var secondExact: String
    @Before fun prepareNativeTargetsAndThirdProgressPosition() {
        val chapter="""<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Two distant occurrences</title></head><body><h1>Two occurrences</h1><p>${ReaderProofFixture.MULTILINGUAL}</p><p id="first">First context. ${ReaderProofFixture.QUOTATION} First ending.</p><p>${"Original synthetic distance between passages. ".repeat(1200)}</p><p id="second">Second context. ${ReaderProofFixture.QUOTATION} Second ending.</p></body></html>"""
        source=runBlocking {
            container.readerPreferences.setDarkReading(false)
            ReaderProofFixture.import(context,ReaderProofFixture.withEntry(ReaderProofFixture.bytes(),"EPUB/one.xhtml",chapter))
        }
        runBlocking(Dispatchers.IO) {
            val db=container.database.openHelper.writableDatabase
            db.execSQL("DELETE FROM notebook_drafts WHERE sourceId = ?",arrayOf(source.id))
            for(table in listOf("entries","bookmarks","reading_state","reader_drafts")) db.execSQL("DELETE FROM $table WHERE sourceId = ?",arrayOf(source.id))
        }
        ActivityScenario.launch<ReaderActivity>(Intent(context,ReaderActivity::class.java).putExtra("sourceId",source.id)).use { scenario ->
            val native=navigator(scenario,compose);waitForWeb(scenario,"one.xhtml")
            script(scenario,"one.xhtml","""(function(){var n=document.getElementById('first').firstChild;var start=n.textContent.indexOf(${JSONObject.quote(ReaderProofFixture.QUOTATION)});var r=document.createRange();r.setStart(n,start);r.setEnd(n,start+${ReaderProofFixture.QUOTATION.length});var s=window.getSelection();s.removeAllRanges();s.addRange(r);return s.toString();})()""")
            val first=runBlocking { withContext(Dispatchers.Main) { native.currentSelection() } }!!.locator
            assertEquals(ReaderProofFixture.QUOTATION,first.text.highlight);assertTrue(first.text.before.orEmpty().contains("First context"))
            firstId=runBlocking { repository.saveCapture(source.id,Capture(ReaderProofFixture.QUOTATION,ReadiumLocations.encode(first.toJSON().toString())),"Notebook first reflection",null) }
            val second=selectSecond(scenario,native);secondExact=second.toJSON().toString()
            secondId=runBlocking { repository.saveCapture(source.id,Capture(ReaderProofFixture.QUOTATION,ReadiumLocations.encode(secondExact)),"Notebook second reflection",null) }
            assertNotEquals(first.toJSON().toString(),secondExact)
            go(scenario,native,Locator.fromJSON(JSONObject("""{"href":"EPUB/two.xhtml","type":"application/xhtml+xml"}"""))!!)
            waitForWeb(scenario,"two.xhtml")
            await { compose.waitForIdle();runBlocking { repository.getProgress(source.id)?.payloadJson?.contains("two.xhtml")==true } }
        }
    }
    private fun launchEntry(id: String)=ActivityScenario.launch<ReaderActivity>(Intent(context,ReaderActivity::class.java).putExtra("entryId",id))
    private fun assertAt(scenario: ActivityScenario<ReaderActivity>,paragraph: String) {
        navigator(scenario,compose);waitForWeb(scenario,"one.xhtml")
        await {
            compose.waitForIdle()
            var consumed=false
            scenario.onActivity { consumed=ViewModelProvider(it)[ReaderViewModel::class.java].uiState.value.pendingEntryJump==null }
            consumed && script(scenario,"one.xhtml","""(function(){var r=document.getElementById('$paragraph').getBoundingClientRect();return r.right>0 && r.left<innerWidth && r.bottom>0 && r.top<innerHeight;})()""")=="true"
        }
    }
    @Test fun entryLocationBeatsLastProgress() {
        launchEntry(secondId).use { scenario ->
            assertAt(scenario,"second")
            val selection=selectSecond(scenario,navigator(scenario,compose))
            assertEquals(ReaderProofFixture.QUOTATION,selection.text.highlight)
            assertTrue(selection.text.before.orEmpty().contains("Second context"))
            assertEquals(secondExact,runBlocking { container.notebookRepository.getEntry(secondId) }!!.location!!.payloadJson)
            scenario.recreate();assertAt(scenario,"second")
        }
    }
    @Test fun sameSourceNewEntryRequestIsNotIgnored() {
        launchEntry(firstId).use { scenario ->
            assertAt(scenario,"first");val native=navigator(scenario,compose)
            scenario.onActivity { it.startActivity(Intent(context,ReaderActivity::class.java).putExtra("entryId",secondId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            assertAt(scenario,"second");assertSame(native,navigator(scenario,compose))
            assertEquals(secondExact,runBlocking { container.notebookRepository.getEntry(secondId) }!!.location!!.payloadJson)
            scenario.recreate();assertAt(scenario,"second")
        }
    }
    @Test fun immediateRecreationAfterAcceptanceRetainsPendingPassage() {
        launchEntry(secondId).use { scenario ->
            assertAt(scenario,"second")
            val first=runBlocking { container.notebookRepository.getEntry(firstId) }!!.location!!
            go(scenario,navigator(scenario,compose),Locator.fromJSON(JSONObject(first.payloadJson))!!)
            assertAt(scenario,"first")
            val native=navigator(scenario,compose)
            scenario.onActivity {
                val model=ViewModelProvider(it)[ReaderViewModel::class.java]
                val target=ReadiumLocations.encode(secondExact)
                model.openTarget(EntryReaderTarget.Passage(secondId,source.id,target))
                val state=model.uiState.value;val jump=requireNotNull(state.pendingEntryJump)
                assertTrue(native.go(Locator.fromJSON(JSONObject(secondExact))!!))
                model.onEntryCommandAccepted(source.id,jump.requestId,state.readerSession)
                assertNotNull(model.uiState.value.pendingEntryJump)
            }
            scenario.recreate();assertAt(scenario,"second")
        }
    }
    @Test fun recreationDoesNotReplayConsumedJump() {
        launchEntry(secondId).use { scenario ->
            assertAt(scenario,"second")
            go(scenario,navigator(scenario,compose),Locator.fromJSON(JSONObject("""{"href":"EPUB/two.xhtml","type":"application/xhtml+xml"}"""))!!)
            waitForWeb(scenario,"two.xhtml")
            await { var found=false;scenario.onActivity { found=ViewModelProvider(it)[ReaderViewModel::class.java].uiState.value.currentLocation?.payloadJson?.contains("two.xhtml")==true };found }
            scenario.recreate();navigator(scenario,compose);waitForWeb(scenario,"two.xhtml")
            scenario.onActivity { assertNull(ViewModelProvider(it)[ReaderViewModel::class.java].uiState.value.pendingEntryJump) }
        }
    }
    @Test fun nativePositionAfterJumpUpdatesNormalProgress() {
        launchEntry(firstId).use { scenario ->
            assertAt(scenario,"first")
            await { compose.waitForIdle();runBlocking { repository.getProgress(source.id)?.payloadJson?.contains("one.xhtml")==true } }
            go(scenario,navigator(scenario,compose),Locator.fromJSON(JSONObject("""{"href":"EPUB/two.xhtml","type":"application/xhtml+xml"}"""))!!)
            waitForWeb(scenario,"two.xhtml")
            await { compose.waitForIdle();runBlocking { repository.getProgress(source.id)?.payloadJson?.contains("two.xhtml")==true } }
        }
    }
    @Test fun returnRestoresEntryDetail() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val monitor=instrumentation.addMonitor(ReaderActivity::class.java.name,null,false)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use {
                compose.onNodeWithText("Commonplace").performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("entry-$secondId").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("entry-$secondId").performScrollTo().performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Open passage").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Open passage").performScrollTo().performClick()
                val reader=instrumentation.waitForMonitorWithTimeout(monitor,20_000)
                assertTrue(reader is ReaderActivity)
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Back to entry").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Back to entry").performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Notebook second reflection").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Notebook second reflection").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Edit").performScrollTo().assertIsDisplayed()
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun changedBytesKeepWritingVisibleWithoutNativeFallback() {
        val asset=File(context.filesDir,source.assetPath);val original=asset.readBytes()
        try {
            asset.appendText("Changed bytes")
            launchEntry(secondId).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Document bytes changed. Saved locations are unresolved; your writing is retained.").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Notebook second reflection").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(ReaderProofFixture.QUOTATION).performScrollTo().assertIsDisplayed()
                scenario.onActivity { assertTrue(it.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().isEmpty()) }
                compose.onNodeWithText("Back to entry").assertExists()
            }
        } finally { asset.writeBytes(original) }
    }
}
