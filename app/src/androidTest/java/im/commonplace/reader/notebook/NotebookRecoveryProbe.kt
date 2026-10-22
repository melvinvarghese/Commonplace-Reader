package im.commonplace.reader.notebook

import android.content.Context
import android.content.Intent
import android.database.Cursor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import im.commonplace.reader.CommonplaceApplication
import im.commonplace.reader.MainActivity
import im.commonplace.reader.data.EntryKind
import im.commonplace.reader.reader.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Dedicated opt-in disposable fixture only. Ordinary-suite no-ops are NOT recovery passes. */
@RunWith(AndroidJUnit4::class)
class NotebookRecoveryProbe {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val container get()=(context as CommonplaceApplication).container
    private val repo get()=container.notebookRepository
    private val args get()=InstrumentationRegistry.getArguments()
    private val cases=listOf("new-thought","edit-highlight","independent-drafts","committed-unicode")
    private val thought="  Unfinished ചിന്ത\n世界 é  "
    private val independent="Independent source writing — Καλημέρα"
    private val edit="Unfinished saved highlight edit — മാറ്റം"
    private val unicode="  ചിന്ത — 世界\nnaïve café العربية\n  "
    private val quotation="Original quotation — 世界"
    private fun file(name: String)=File(context.filesDir,name)
    private fun durable(name: String,value: JSONObject) {FileOutputStream(file(name)).use {it.write(value.toString().toByteArray());it.fd.sync()}}
    private fun guard(): JSONObject {
        val runId=requireNotNull(args.getString("notebookRunId"));val sha=requireNotNull(args.getString("notebookSourceSha"))
        assertTrue(runId.matches(Regex("[0-9a-f]{32}")));assertTrue(sha.matches(Regex("[0-9a-f]{40}")))
        assertEquals(runId,file("notebook-disposable-install").readText())
        return JSONObject().put("runId",runId).put("sourceSha",sha).put("flavor",context.packageName.substringAfterLast('.'))
    }
    private fun awaitText(text: String) {compose.waitUntil(20_000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun awaitStore(predicate: suspend () -> Boolean) {compose.waitUntil(20_000) {runBlocking {predicate()}}}
    private fun snapshot(table: String,id: String): String=runBlocking(Dispatchers.IO) {
        require(table in listOf("entries","notebook_drafts"))
        val column=if(table=="entries") "id" else "targetId"
        container.database.openHelper.readableDatabase.query("SELECT * FROM $table WHERE $column = ?",arrayOf(id)).use {cursor ->
            check(cursor.moveToFirst());val row=JSONObject()
            for(i in 0 until cursor.columnCount) row.put(cursor.getColumnName(i),when(cursor.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                else -> cursor.getString(i)
            })
            row.toString()
        }
    }
    private fun enterNotebook() {compose.onNodeWithText("Commonplace").performClick();awaitText("New entry")}
    private fun new(kind: EntryKind,text: String,sourceId: String?=null): String {
        compose.onNodeWithText("New entry").performClick();compose.onNodeWithTag("new-${kind.name}").performClick()
        if(sourceId!=null) {awaitText("Choose a book");compose.onNodeWithTag("source-$sourceId").performScrollTo().performClick()}
        awaitText("Body");compose.onNodeWithText("Body").performTextInput(text)
        awaitStore {repo.observeDrafts().first().any {it.isNew && it.fields.body==text}}
        awaitText("Draft saved on this device")
        return runBlocking {repo.observeDrafts().first().single {it.isNew && it.fields.body==text}.targetId}
    }
    private fun back() {
        compose.onNodeWithText("Back").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithText("New entry").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Back to notebook").fetchSemanticsNodes().isNotEmpty()}
        if(compose.onAllNodesWithText("Back to notebook").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Back to notebook").performClick()
        awaitText("New entry")
    }
    private fun review(id: String) {
        compose.onNodeWithTag("notebook-list").performScrollToNode(hasTestTag("draft-$id"))
        compose.onNode(hasText("Review draft") and hasAnyAncestor(hasTestTag("draft-$id"))).performClick()
        awaitText("Recoverable draft");compose.onNodeWithText("Resume draft").performClick()
    }
    @Test fun stageAcknowledgedNotebook() {
        if(args.getString("notebookRecovery")!="stage") return
        val identity=guard()
        val source=runBlocking {ReaderProofFixture.import(context)}
        val highlight=runBlocking {container.readerRepository.saveCapture(source.id,Capture(quotation,SavedLocation(1,DocumentFormat.EPUB,"readium",""" {"href":"EPUB/one.xhtml","type":"application/xhtml+xml","opaque":"retain exact bytes"} """)),"Saved reflection before unfinished edit",42)}
        val original=snapshot("entries",highlight)
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use {
            enterNotebook()
            val thoughtId=new(EntryKind.THOUGHT,thought);back()
            val independentId=new(EntryKind.SOURCE_NOTE,independent,source.id);back()
            compose.onNodeWithTag("notebook-list").performScrollToNode(hasTestTag("entry-$highlight"))
            compose.onNodeWithTag("entry-$highlight").performClick();awaitText("Edit")
            compose.onNodeWithText("Edit").performScrollTo().performClick();awaitText("Reflection (optional)")
            compose.onNodeWithText("Reflection (optional)").performTextReplacement(edit)
            awaitStore {repo.getDraft(highlight)?.fields?.body==edit};awaitText("Draft saved on this device");back()
            val committed=new(EntryKind.THOUGHT,unicode)
            compose.onNodeWithText("Save entry").performClick();awaitText("Saved")
            awaitStore {repo.getEntry(committed)?.body==unicode && repo.getDraft(committed)==null}
            assertEquals(original,snapshot("entries",highlight))
            val state=JSONObject(identity.toString()).put("thoughtId",thoughtId).put("independentId",independentId).put("highlightId",highlight).put("committedId",committed)
                .put("thoughtSnapshot",snapshot("notebook_drafts",thoughtId)).put("independentSnapshot",snapshot("notebook_drafts",independentId))
                .put("editSnapshot",snapshot("notebook_drafts",highlight)).put("highlightSnapshot",original).put("committedSnapshot",snapshot("entries",committed))
            durable("notebook-recovery-state.json",state)
            durable("notebook-recovery-ready",JSONObject(identity.toString()).put("acknowledged",JSONArray(cases)))
        }
    }
    @Test fun verifyRecoveredNotebook() {
        if(args.getString("notebookRecovery")!="verify") return
        val identity=guard();val expected=JSONObject(file("notebook-recovery-state.json").readText())
        for(key in listOf("runId","sourceSha","flavor")) assertEquals(identity.getString(key),expected.getString(key))
        val thoughtId=expected.getString("thoughtId");val independentId=expected.getString("independentId")
        val highlight=expected.getString("highlightId");val committed=expected.getString("committedId")
        for((id,key) in listOf(thoughtId to "thoughtSnapshot",independentId to "independentSnapshot",highlight to "editSnapshot")) assertEquals(expected.getString(key),snapshot("notebook_drafts",id))
        assertEquals(expected.getString("highlightSnapshot"),snapshot("entries",highlight));assertEquals(expected.getString("committedSnapshot"),snapshot("entries",committed))
        runBlocking {
            assertNull(repo.getEntry(thoughtId));assertNull(repo.getEntry(independentId));assertNull(repo.getDraft(committed))
            assertEquals(unicode,repo.getEntry(committed)!!.body);assertEquals(quotation,repo.getEntry(highlight)!!.quotation)
            assertEquals(edit,repo.getDraft(highlight)!!.fields.body)
            assertNotEquals(thoughtId,independentId);assertNotEquals(highlight,thoughtId)
        }
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use {
            enterNotebook();review(thoughtId);awaitText("Body");compose.onNodeWithText("Body").assertTextContains(thought);back()
            review(independentId);awaitText("Body");compose.onNodeWithText("Body").assertTextContains(independent);back()
            review(highlight);awaitText("Reflection (optional)");compose.onNodeWithText("Reflection (optional)").assertTextContains(edit)
            compose.onNodeWithText(quotation).assertExists();back()
            compose.onNodeWithTag("notebook-list").performScrollToNode(hasTestTag("entry-$committed"))
            compose.onNodeWithTag("entry-$committed").performClick();awaitText("Edit");compose.onNodeWithText(unicode).performScrollTo().assertIsDisplayed()
            assertEquals(expected.getString("highlightSnapshot"),snapshot("entries",highlight))
            assertEquals(expected.getString("committedSnapshot"),snapshot("entries",committed))
            assertNull(runBlocking {repo.getDraft(committed)})
            durable("notebook-recovery-verified",JSONObject(identity.toString()).put("verified",JSONArray(cases)))
        }
    }
}
