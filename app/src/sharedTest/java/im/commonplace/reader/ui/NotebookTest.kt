package im.commonplace.reader.ui

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.commonplace.reader.CommonplaceApplication
import im.commonplace.reader.MainActivity
import im.commonplace.reader.data.*
import im.commonplace.reader.notebook.*
import im.commonplace.reader.reader.*
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Shared UI assertions; host execution does not prove native reading or process death. */
@im.commonplace.reader.notebook.NotebookProof
@RunWith(AndroidJUnit4::class)
class NotebookTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var app: CommonplaceApplication
    private lateinit var repo: NotebookRepository
    private var scenario: ActivityScenario<MainActivity>?=null
    private val owner=ViewModelStore()
    private val source=SourceEntity(UUID(0,1).toString(),"Synthetic book","Éva 王",DocumentFormat.EPUB,"content://synthetic/book",
        "a".repeat(64),"imports/assets/${UUID(0,1)}.epub",SourceAvailability.MISSING,1,1)
    @Before fun setup() {
        app=ApplicationProvider.getApplicationContext()
        runBlocking(Dispatchers.IO) { app.container.database.clearAllTables(); app.container.database.readerDao().insertSource(source) }
        repo=app.container.notebookRepository
    }
    @After fun cleanup() { scenario?.close(); owner.clear(); runBlocking(Dispatchers.IO) { app.container.database.clearAllTables() } }
    private fun launch() {
        scenario=ActivityScenario.launch(Intent(app,MainActivity::class.java))
        compose.onNodeWithText("Commonplace").performClick()
        awaitText("New entry")
    }
    private fun awaitText(text: String) {
        try { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        catch(error: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Missing '$text'\n"+compose.onRoot().printToString(),error)
        }
    }
    private fun newThought(text: String) {
        compose.onNodeWithText("New entry").performClick(); compose.onNodeWithTag("new-THOUGHT").performClick()
        awaitText("Body"); compose.onNodeWithText("Body").performTextInput(text)
    }
    private fun save() { compose.onNodeWithText("Save entry").performClick(); awaitText("Saved") }
    private fun rows()=runBlocking { repo.observeEntries(null).first() }
    private fun highlight()=runBlocking { app.container.readerRepository.saveCapture(source.id,
        Capture("Quotation\n世界",SavedLocation(1,DocumentFormat.EPUB,"readium","""{"href":"chapter.xhtml"}""")),"Original reflection",-1) }

    @Test fun commonplaceTabHasHonestEmptyState() {
        launch(); compose.onNodeWithText("No entries yet").assertIsDisplayed()
        compose.onNodeWithText("New entry").assertIsDisplayed(); assertTrue(rows().isEmpty())
    }
    @Test fun readerCreatedHighlightAppearsWithSeparateReflection() {
        val id=highlight(); launch(); compose.onNodeWithTag("entry-$id").performScrollTo().performClick()
        awaitText("Edit"); compose.onNodeWithText("Quotation\n世界").assertIsDisplayed()
        compose.onNodeWithText("Original reflection").performScrollTo().assertIsDisplayed()
    }
    @Test fun createThoughtSaveThenEditPreservesIdentity() {
        launch(); newThought("  ചിന്ത — 世界\né\n  "); save()
        val first=rows().single(); assertEquals("  ചിന്ത — 世界\né\n  ",first.body); assertNull(first.attributionTitle)
        compose.onNodeWithText("Edit").performScrollTo().performClick(); awaitText("Body")
        compose.onNodeWithText("Body").performTextReplacement("Updated\nthought"); save()
        val second=rows().single(); assertEquals(first.id,second.id); assertEquals(first.createdAt,second.createdAt)
        assertEquals("Updated\nthought",second.body); assertEquals(2L,second.revision)
    }
    @Test fun createAnnotationAndTwoSourceNotesForSameBook() {
        launch()
        listOf("Annotation","Source note","Source note").forEachIndexed { i,kind ->
            compose.onNodeWithText("New entry").performClick(); compose.onNodeWithTag("new-"+if(kind=="Annotation") "ANNOTATION" else "SOURCE_NOTE").performClick()
            awaitText("Choose a book"); compose.onNodeWithTag("source-${source.id}").performClick()
            awaitText("Body"); compose.onNodeWithText("Body").performTextInput("Writing $i"); save()
            compose.onNodeWithText("Back to notebook").performClick(); awaitText("New entry")
        }
        assertEquals(1,rows().count { it.kind==EntryKind.ANNOTATION }); assertEquals(2,rows().count { it.kind==EntryKind.SOURCE_NOTE })
        assertTrue(rows().all { it.sourceId==source.id && it.location==null && it.quotation==null })
    }
    @Test fun typeFilterUsesOnlyFinishedEntries() {
        val id=highlight(); launch(); newThought("Unfinished")
        compose.onNodeWithText("Back").performClick(); awaitText("Drafts")
        compose.onNodeWithText("Thoughts").performScrollTo().performClick()
        compose.onNodeWithTag("notebook-list").performScrollToNode(hasText("No entries match this type")); awaitText("No entries match this type"); compose.onNodeWithTag("entry-$id").assertDoesNotExist()
        assertEquals(1,rows().size)
    }
    @Test fun draftsOfferResumeAndDiscard() {
        launch(); newThought("Recover this draft"); compose.onNodeWithText("Back").performClick(); awaitText("Drafts")
        val target=runBlocking { repo.observeDrafts().first().single().targetId }
        compose.onNodeWithTag("draft-$target").assertExists()
        compose.onNodeWithText("Review draft").performScrollTo().performClick(); awaitText("Recoverable draft")
        compose.onNodeWithText("Resume draft").performClick(); awaitText("Body")
        compose.onNodeWithText("Recover this draft").assertExists()
        compose.onNodeWithText("Back").performClick(); awaitText("Drafts")
        compose.onNodeWithText("Review draft").performScrollTo().performClick(); compose.onNodeWithText("Discard draft").performClick()
        compose.waitUntil(15_000) { runBlocking { repo.observeDrafts().first().isEmpty() } }
        assertTrue(rows().isEmpty())
    }
    @Test fun highlightQuotationAndAttributionAreNotEditable() {
        val id=highlight(); launch(); compose.onNodeWithTag("entry-$id").performScrollTo().performClick(); awaitText("Edit")
        compose.onNodeWithText("Edit").performScrollTo().performClick(); awaitText("Reflection (optional)")
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onNodeWithText("Reflection (optional)").performTextReplacement("New reflection"); save()
        val row=rows().single(); assertEquals("Quotation\n世界",row.quotation); assertEquals(source.title,row.attributionTitle)
        assertEquals("New reflection",row.body)
    }
    @Test fun missingBookWritingRemainsReadableAndEditable() {
        val id=highlight(); launch(); compose.onNodeWithTag("entry-$id").performScrollTo().performClick(); awaitText("Edit")
        compose.onNodeWithText("Document missing. Your writing is retained.").assertExists()
        compose.onNodeWithText("Edit").performScrollTo().performClick(); awaitText("Reflection (optional)")
        compose.onNodeWithText("Reflection (optional)").performTextReplacement("Still writing"); save()
        assertEquals("Still writing",rows().single().body)
    }
    @Test fun loadingFailureOffersRetry() {
        var first=true
        val failOnce=object : NotebookStore by repo {
            override fun observeEntries(kind: EntryKind?): Flow<List<EntryEntity>> = flow {
                if(first) { first=false; throw IOException("synthetic failure") }
                emitAll(repo.observeEntries(kind))
            }
        }
        scenario=ActivityScenario.launch(Intent(app,MainActivity::class.java))
        scenario!!.onActivity { activity ->
            val model=NotebookViewModel(failOnce); owner.put("failure",model)
            activity.setContent { CommonplaceTheme { NotebookScreen(PaddingValues(),{},model) } }
        }
        awaitText("Retry notebook"); compose.onNodeWithText("Retry notebook").performClick(); awaitText("No entries yet")
        compose.onNodeWithText("No entries yet").assertIsDisplayed()
    }
    @Test fun rotationRetainsAcknowledgedEditorDraft() {
        launch(); newThought("Rotation\nചിന്ത"); awaitText("Draft saved on this device")
        val before=runBlocking { repo.observeDrafts().first().single() }
        scenario!!.recreate(); awaitText("Body")
        compose.onNodeWithText("Rotation\nചിന്ത").assertExists()
        save(); assertEquals(before.targetId,rows().single().id); assertEquals("Rotation\nചിന്ത",rows().single().body)
    }
    @Test fun discardEditKeepsFinishedEntry() {
        val id=highlight(); val before=rows().single()
        launch(); compose.onNodeWithTag("entry-$id").performScrollTo().performClick(); awaitText("Edit")
        compose.onNodeWithText("Edit").performScrollTo().performClick(); awaitText("Reflection (optional)")
        compose.onNodeWithText("Reflection (optional)").performTextReplacement("Unfinished edit")
        compose.onNodeWithText("Discard draft").performScrollTo().performClick(); awaitText("Discard this draft?")
        compose.onNode(hasText("Discard draft") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(15_000) { runBlocking { repo.observeDrafts().first().isEmpty() } }
        assertEquals(listOf(before),rows())
    }
}
