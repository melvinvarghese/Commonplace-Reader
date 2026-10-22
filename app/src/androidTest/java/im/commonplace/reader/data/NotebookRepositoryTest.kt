package im.commonplace.reader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.commonplace.reader.notebook.*
import im.commonplace.reader.reader.Capture
import im.commonplace.reader.reader.DocumentFormat
import im.commonplace.reader.reader.SavedLocation
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@im.commonplace.reader.notebook.NotebookProof
@RunWith(AndroidJUnit4::class)
class NotebookRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: CommonplaceDatabase
    private lateinit var repo: NotebookRepository
    private lateinit var reader: ReaderRepository
    private val name = "notebook-${UUID.randomUUID()}.db"
    private val source = SourceEntity(UUID(0, 1).toString(), "പുസ്തകം — 本", "Éva 王", DocumentFormat.EPUB,
        "content://synthetic/book", "a".repeat(64), "imports/assets/${UUID(0,1)}.epub", SourceAvailability.MISSING, 10, 11)
    private val body = "  ചിന്ത — 世界\né\n  "
    private var seq = 100L
    private var now = 20L
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        open(); db.readerDao().insertSource(source)
    }
    private fun open() {
        db = Room.databaseBuilder(context, CommonplaceDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        repo = NotebookRepository(db, Dispatchers.IO, { now }, { UUID(0, ++seq).toString() })
        reader = ReaderRepository(db)
    }
    private fun reopen() { db.close(); open() }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    private suspend fun write(d: NotebookDraftEntity, text: String? = body, title: String? = null, color: Int? = null): NotebookDraftEntity {
        val result = repo.writeDraft(d.targetId,d.sessionId,d.draftRevision,NotebookFields(title,text,color),d.sourceId)
        assertTrue("The guarded draft update must be durable", result is DraftWriteResult.Written)
        return (result as DraftWriteResult.Written).draft
    }
    private fun request(d: NotebookDraftEntity) = NotebookCommitRequest(d.targetId,d.sessionId,d.draftRevision,d.commitToken)
    private suspend fun save(d: NotebookDraftEntity): EntryEntity {
        val result = repo.commit(request(d))
        assertTrue("Valid draft must commit", result is NotebookCommitResult.Saved)
        return (result as NotebookCommitResult.Saved).entry
    }
    private suspend fun edit(id: String): NotebookDraftEntity {
        val result = repo.beginEdit(id)
        assertTrue(result is BeginEditResult.Started)
        return (result as BeginEditResult.Started).draft
    }
    private suspend fun highlight(): String = reader.saveCapture(source.id,Capture("Quotation\n世界",
        SavedLocation(1,DocumentFormat.EPUB,"readium","""{ "href":"chapter.xhtml", "future":"é" }""")),"old",-1)

    @Test fun newThoughtPreservesExactBodyAndHasNoAttribution() = runBlocking {
        val d=write(repo.beginNew(EntryKind.THOUGHT,null)); val entry=save(d)
        reopen()
        assertEquals(body,repo.getEntry(entry.id)!!.body); assertEquals(d.targetId,entry.id)
        assertNull(entry.sourceId); assertNull(entry.attributionTitle); assertNull(entry.location); assertNull(entry.quotation)
        assertEquals(1L,entry.revision); assertEquals(20L,entry.createdAt)
    }
    @Test fun sourceNoteAndAnnotationKeepDistinctKinds() = runBlocking {
        for (kind in listOf(EntryKind.SOURCE_NOTE,EntryKind.ANNOTATION,EntryKind.SOURCE_NOTE)) {
            val e=save(write(repo.beginNew(kind,source.id)))
            assertEquals(kind,e.kind); assertEquals(source.title,e.attributionTitle)
            assertNull(e.location); assertNull(e.quotation)
        }
        assertEquals(3,repo.observeEntries(null).first().size)
        assertEquals(2,repo.observeEntries(EntryKind.SOURCE_NOTE).first().size)
    }
    @Test fun highlightEditChangesOnlyTitleReflectionColor() = runBlocking {
        val id=highlight(); val old=repo.getEntry(id)!!; now=50
        val changed=save(write(edit(id),body,"title",42))
        assertEquals(old.copy(title="title",body=body,colorArgb=42,modifiedAt=50,revision=1,lastNotebookCommitId=changed.lastNotebookCommitId),changed)
        reopen(); assertEquals(changed,repo.getEntry(id))
    }
    @Test fun orphanedEntryRemainsEditable() = runBlocking {
        val id=highlight(); db.openHelper.writableDatabase.execSQL("DELETE FROM sources")
        val changed=save(write(edit(id)))
        assertNull(changed.sourceId); assertEquals(source.title,changed.attributionTitle); assertEquals(body,changed.body)
    }
    @Test fun newSourceNoteRetainsDraftIfSourceRowDisappears() = runBlocking {
        val d=write(repo.beginNew(EntryKind.SOURCE_NOTE,source.id))
        db.openHelper.writableDatabase.execSQL("DELETE FROM sources")
        assertTrue(repo.commit(request(d)) is NotebookCommitResult.Invalid)
        reopen(); val restored=repo.getDraft(d.targetId)!!
        assertEquals(body,restored.fields.body); assertEquals(source.title,restored.sourceTitleSnapshot)
        assertTrue(repo.observeEntries(null).first().isEmpty())
    }
    @Test fun commitClearsOnlyItsDraftAtomically() = runBlocking {
        val a=write(repo.beginNew(EntryKind.THOUGHT,null)); val b=write(repo.beginNew(EntryKind.THOUGHT,null))
        save(a); reopen(); assertNull(repo.getDraft(a.targetId)); assertNotNull(repo.getDraft(b.targetId))
    }
    @Test fun failedDraftDeleteRollsBackEntryUpdate() = runBlocking {
        val id=highlight(); val old=repo.getEntry(id)!!; val d=write(edit(id))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_notebook_delete BEFORE DELETE ON notebook_drafts BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        var failed=false
        try { repo.commit(request(d)) } catch (_: android.database.sqlite.SQLiteException) { failed=true }
        assertTrue(failed); reopen(); assertEquals(old,repo.getEntry(id)); assertEquals(d,repo.getDraft(id))
    }
    @Test fun retryAfterCommittedAcknowledgmentLossDoesNotDuplicate() = runBlocking {
        val d=write(repo.beginNew(EntryKind.THOUGHT,null)); val first=save(d)
        reopen(); val retry=repo.commit(request(d))
        assertEquals(NotebookCommitResult.Saved(first),retry)
        assertEquals(1,repo.observeEntries(null).first().size); assertEquals(1L,repo.getEntry(first.id)!!.revision)
    }
    @Test fun retryAfterNewerCommitReturnsConflict() = runBlocking {
        val d=write(repo.beginNew(EntryKind.THOUGHT,null)); val first=save(d)
        val updated=save(write(edit(first.id),"newer"))
        assertTrue(repo.commit(request(d)) is NotebookCommitResult.Conflict)
        assertEquals(updated,repo.getEntry(first.id))
    }
    @Test fun staleSessionCannotOverwriteOrResurrectDraft() = runBlocking {
        val d=write(repo.beginNew(EntryKind.THOUGHT,null))
        val claim=repo.resumeDraft(d.targetId,d.sessionId,d.draftRevision) as DraftWriteResult.Written
        assertEquals(DraftWriteResult.Conflict,repo.writeDraft(d.targetId,d.sessionId,d.draftRevision,NotebookFields(body="stale"),null))
        val fresh=claim.draft
        assertEquals(DiscardResult.Discarded,repo.discardDraft(fresh.targetId,fresh.sessionId,fresh.draftRevision))
        assertEquals(DraftWriteResult.Missing,repo.writeDraft(fresh.targetId,fresh.sessionId,fresh.draftRevision,NotebookFields(body="late"),null))
        assertNull(repo.getDraft(d.targetId))
    }
    @Test fun twoTargetsKeepIndependentDrafts() = runBlocking {
        val a=write(repo.beginNew(EntryKind.THOUGHT,null),"A"); val b=write(repo.beginNew(EntryKind.THOUGHT,null),"B")
        reopen(); assertEquals(setOf(a,b),repo.observeDrafts().first().toSet()); assertTrue(repo.observeEntries(null).first().isEmpty())
    }
    @Test fun blankRequiredBodyStaysDraftOnly() = runBlocking {
        val d=write(repo.beginNew(EntryKind.THOUGHT,null)," \n\t")
        assertTrue(repo.commit(request(d)) is NotebookCommitResult.Invalid)
        assertEquals(d,repo.getDraft(d.targetId)); assertTrue(repo.observeEntries(null).first().isEmpty())
    }
    @Test fun sourceSnapshotsRefreshOnlyOnExplicitNewSourceChange() = runBlocking {
        val d=repo.beginNew(EntryKind.SOURCE_NOTE,source.id)
        db.openHelper.writableDatabase.execSQL("UPDATE sources SET title='Changed' WHERE id=?",arrayOf(source.id))
        val same=write(d)
        assertEquals(source.title,same.sourceTitleSnapshot)
        val other=source.copy(id=UUID(0,2).toString(),title="Other",checksum="b".repeat(64))
        db.readerDao().insertSource(other)
        val moved=repo.writeDraft(same.targetId,same.sessionId,same.draftRevision,same.fields,other.id) as DraftWriteResult.Written
        assertEquals("Other",moved.draft.sourceTitleSnapshot)
        assertEquals("Other",save(moved.draft).attributionTitle)
    }
    @Test fun staleEntryRevisionKeepsBothVersionsAndDraft() = runBlocking {
        val id=highlight(); val d=write(edit(id))
        db.openHelper.writableDatabase.execSQL("UPDATE entries SET body='newer', revision=revision+1 WHERE id=?",arrayOf(id))
        assertTrue(repo.commit(request(d)) is NotebookCommitResult.Conflict)
        assertEquals("newer",repo.getEntry(id)!!.body); assertEquals(d,repo.getDraft(id))
    }
    @Test fun existingDraftIsOfferedInsteadOfOverwritten() = runBlocking {
        val id=highlight(); val d=write(edit(id))
        assertEquals(BeginEditResult.ResumeRequired(d),repo.beginEdit(id)); assertEquals(d,repo.getDraft(id))
    }
    @Test fun inconsistentLegacyEntryCannotBeSilentlyRewritten() = runBlocking {
        val id=highlight(); db.openHelper.writableDatabase.execSQL("UPDATE entries SET quotation='' WHERE id=?",arrayOf(id))
        assertTrue(repo.beginEdit(id) is BeginEditResult.Unavailable)
        assertEquals("",repo.getEntry(id)!!.quotation); assertNull(repo.getDraft(id))
    }
    @Test fun lateDraftWriteAfterCommitNeverResurrects() = runBlocking {
        val d=write(repo.beginNew(EntryKind.THOUGHT,null))
        val barrier=kotlinx.coroutines.CompletableDeferred<Unit>()
        val late=async(Dispatchers.IO) {
            barrier.await(); repo.writeDraft(d.targetId,d.sessionId,d.draftRevision,NotebookFields(body="late"),null)
        }
        save(d); barrier.complete(Unit)
        assertEquals(DraftWriteResult.Missing,late.await()); assertNull(repo.getDraft(d.targetId))
        assertEquals(body,repo.getEntry(d.targetId)!!.body)
    }
    @Test fun unsupportedOpaqueLocatorBytesAreNotReencodedByEditing() = runBlocking {
        val id=highlight()
        val opaque="  { \"version\":99, \"format\":\"EPUB\", \"engineId\":\"future\", \"payloadJson\":\"{  }\", \"future\":true }  "
        db.openHelper.writableDatabase.execSQL("UPDATE entries SET location=? WHERE id=?",arrayOf(opaque,id))
        save(write(edit(id)))
        db.openHelper.readableDatabase.query("SELECT location FROM entries WHERE id=?",arrayOf(id)).use {
            assertTrue(it.moveToFirst()); assertEquals(opaque,it.getString(0))
        }
    }
}
