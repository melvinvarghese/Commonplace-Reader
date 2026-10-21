package im.commonplace.reader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.commonplace.reader.reader.Capture
import im.commonplace.reader.reader.DocumentFormat
import im.commonplace.reader.reader.SavedLocation
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The same file-backed SQLite contract runs on a device and on host Robolectric. */
@RunWith(AndroidJUnit4::class)
class ReaderRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: CommonplaceDatabase
    private lateinit var repository: ReaderRepository
    private lateinit var name: String
    private var sequence = 0L
    private val sourceId = "00000000-0000-0000-0000-000000000001"
    private val source get() = SourceEntity(sourceId, "പുസ്തകം — 本", "Éva 王", DocumentFormat.EPUB,
        "content://synthetic/book", "a".repeat(64), "documents/book.epub", SourceAvailability.AVAILABLE, 10, 10)

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        name = "repository-${UUID.randomUUID()}.db"
        open()
        database.readerDao().insertSource(source)
    }

    private fun open() {
        database = Room.databaseBuilder(context, CommonplaceDatabase::class.java, name).build()
        repository = ReaderRepository(database, Dispatchers.IO, { 1234L }, { UUID(0, ++sequence).toString() })
    }

    private fun reopen() { database.close(); open() }
    @After fun tearDown() { database.close(); context.deleteDatabase(name) }
    private fun location(position: Int = 1) = SavedLocation(1, DocumentFormat.EPUB, "readium-3.4.0",
        """{"href":"章.xhtml","locations":{"position":$position},"future":{"x":"é"}}""")
    private fun capture(text: String = "വാക്കുകൾ — 世界\nCafé") = Capture(text, location())
    private fun draft() = ReaderDraftEntity(sourceId, capture(), "ചിന്ത\n考え", 0xff00aa00.toInt(), 111)

    @Test fun unicodeCaptureAndAnnotationRecoverExactValuesAndSourceAttribution() = runBlocking {
        val id = repository.saveCapture(sourceId, capture(), "ഓർമ്മ\nréflexion", 0xffaabbcc.toInt())
        val annotationId = repository.saveSourceAnnotation(sourceId, "കുറിപ്പ്\n注釈")
        reopen()
        val entries = repository.observeEntries(sourceId).first()
        assertEquals(2, entries.size)
        val highlight = entries.single { it.id == id }
        assertEquals(EntryKind.HIGHLIGHT, highlight.kind)
        assertEquals("വാക്കുകൾ — 世界\nCafé", highlight.quotation)
        assertEquals("ഓർമ്മ\nréflexion", highlight.body)
        assertEquals(location(), highlight.location)
        assertEquals("പുസ്തകം — 本", highlight.attributionTitle)
        assertEquals("Éva 王", highlight.attributionAuthors)
        assertEquals(0xffaabbcc.toInt(), highlight.colorArgb)
        assertEquals(1234L, highlight.createdAt)
        assertEquals(1234L, highlight.modifiedAt)
        assertEquals("00000000-0000-0000-0000-000000000001", id)
        val annotation = entries.single { it.id == annotationId }
        assertEquals(EntryKind.ANNOTATION, annotation.kind)
        assertEquals("കുറിപ്പ്\n注釈", annotation.body)
        assertEquals("പുസ്തകം — 本", annotation.attributionTitle)
        assertNull(annotation.location)
        assertNull(annotation.quotation)
        assertEquals(source, repository.observeSources().first().single())
    }

    @Test fun blankCaptureIsRejectedWithoutAnEntry() = runBlocking {
        expectFailure<IllegalArgumentException> { repository.saveCapture(sourceId, capture(" \n\t"), null, null) }
        reopen()
        assertTrue(repository.observeEntries(sourceId).first().isEmpty())
    }

    @Test fun progressAndTwoBookmarksRemainIndependentAfterReopen() = runBlocking {
        repository.saveProgress(sourceId, location(1))
        val b = repository.addBookmark(sourceId, location(2))
        val c = repository.addBookmark(sourceId, location(3))
        reopen()
        assertEquals(location(1), repository.getProgress(sourceId))
        val bookmarks = database.readerDao().bookmarks(sourceId)
        assertEquals(setOf(b, c), bookmarks.map { it.id }.toSet())
        assertEquals(setOf(location(2), location(3)), bookmarks.map { it.location }.toSet())
        assertEquals(2, bookmarks.size)
        assertTrue(bookmarks.all { it.createdAt == 1234L })
        repository.saveProgress(sourceId, location(4))
        reopen()
        assertEquals(location(4), repository.getProgress(sourceId))
        assertEquals(2, database.readerDao().bookmarks(sourceId).size)
    }

    @Test fun draftSurvivesReopenAndNeverAppearsAsEntry() = runBlocking {
        repository.saveDraft(draft())
        reopen()
        assertEquals(draft(), repository.getDraft(sourceId))
        assertTrue(repository.observeEntries(sourceId).first().isEmpty())
    }

    @Test fun successfulCaptureClearsDraftInSameCommit() = runBlocking {
        repository.saveDraft(draft())
        val id = repository.saveCapture(sourceId, capture(), "kept", null)
        reopen()
        assertNull(repository.getDraft(sourceId))
        assertEquals(id, repository.observeEntries(sourceId).first().single().id)
    }

    @Test fun failedCaptureRetainsDraftAndLeavesNoEntry() = runBlocking {
        repository.saveDraft(draft())
        expectFailure<IllegalArgumentException> { repository.saveCapture(sourceId, capture(""), null, null) }
        reopen()
        assertEquals(draft(), repository.getDraft(sourceId))
        assertTrue(repository.observeEntries(sourceId).first().isEmpty())
    }

    @Test fun failureAfterInsertRollsBackEntryAndDraftDeletion() = runBlocking {
        repository.saveDraft(draft())
        // Fail the second statement, after the real entry INSERT, inside the transaction.
        database.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_draft_delete BEFORE DELETE ON reader_drafts
            BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END""")
        expectFailure<android.database.sqlite.SQLiteException> {
            repository.saveCapture(sourceId, capture(), null, null)
        }
        reopen()
        assertTrue(repository.observeEntries(sourceId).first().isEmpty())
        assertEquals(draft(), repository.getDraft(sourceId))
    }

    @Test fun missingSourceAndFormatMismatchCannotCreatePartialRecords() = runBlocking {
        expectFailure<IllegalArgumentException> { repository.saveSourceAnnotation("missing", "note") }
        val pdf = SavedLocation(1, DocumentFormat.PDF, "pdf-test", """{"pageIndex":0,"pageBox":[0,0,100,200],"rotationDegrees":0,"quads":null,"coordinateSpace":"unrotated-pdf-points"}""")
        expectFailure<IllegalArgumentException> { repository.saveCapture(sourceId, Capture("text", pdf), null, null) }
        assertTrue(repository.observeEntries(sourceId).first().isEmpty())
    }

    @Test fun unknownLocationVersionRemainsRecoverableButCannotOverwriteProgress() = runBlocking {
        repository.saveProgress(sourceId, location())
        val future = location().copy(version = 99)
        expectFailure<IllegalArgumentException> { repository.saveProgress(sourceId, future) }
        // Simulate a newer writer. Reading the raw version must not destroy or coerce it.
        database.openHelper.writableDatabase.execSQL("UPDATE reading_state SET location = ? WHERE sourceId = ?",
            arrayOf(future.toJson(), sourceId))
        reopen()
        assertEquals(future, repository.getProgress(sourceId))
        assertEquals(location().payloadJson, repository.getProgress(sourceId)!!.payloadJson)
    }

    @Test fun cancellationPropagatesWithoutAcknowledgingOrSaving() = runBlocking {
        repository = ReaderRepository(database, Dispatchers.IO, { throw CancellationException("cancel") })
        expectFailure<CancellationException> { repository.saveSourceAnnotation(sourceId, "note") }
        reopen()
        assertTrue(repository.observeEntries(sourceId).first().isEmpty())
    }

    @Test fun sourceDeletionRetainsWritingAndAttributionButRemovesDependentState() = runBlocking {
        val id = repository.saveCapture(sourceId, capture(), "reflection", null)
        repository.saveDraft(draft())
        repository.saveProgress(sourceId, location())
        repository.addBookmark(sourceId, location(2))
        database.openHelper.writableDatabase.execSQL("DELETE FROM sources WHERE id = ?", arrayOf(sourceId))
        reopen()
        val cursor = database.openHelper.readableDatabase.query("SELECT sourceId, quotation, attributionTitle FROM entries WHERE id = ?", arrayOf(id))
        cursor.use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
            assertEquals("വാക്കുകൾ — 世界\nCafé", it.getString(1))
            assertEquals("പുസ്തകം — 本", it.getString(2))
        }
        assertNull(repository.getProgress(sourceId))
        assertNull(repository.getDraft(sourceId))
        assertTrue(database.readerDao().bookmarks(sourceId).isEmpty())
    }

    @Test fun duplicateChecksumAndDanglingForeignKeysAreRejected() = runBlocking {
        expectFailure<android.database.sqlite.SQLiteException> {
            database.readerDao().insertSource(source.copy(id = UUID.randomUUID().toString()))
        }
        expectFailure<android.database.sqlite.SQLiteException> {
            database.readerDao().insertBookmark(BookmarkEntity(UUID.randomUUID().toString(), "missing", location(), 1))
        }
        reopen()
        assertEquals(1, repository.observeSources().first().size)
        assertTrue(database.readerDao().bookmarks("missing").isEmpty())
    }

    @Test fun invalidDraftOrBlankAnnotationDoesNotOverwriteExistingWriting() = runBlocking {
        repository.saveDraft(draft())
        expectFailure<IllegalArgumentException> { repository.saveSourceAnnotation(sourceId, " \n") }
        expectFailure<IllegalArgumentException> { repository.saveDraft(draft().copy(capture = capture(""))) }
        expectFailure<IllegalArgumentException> { repository.addBookmark(sourceId, location().copy(version = 2)) }
        reopen()
        assertEquals(draft(), repository.getDraft(sourceId))
        assertTrue(repository.observeEntries(sourceId).first().isEmpty())
        assertTrue(database.readerDao().bookmarks(sourceId).isEmpty())
    }

    private suspend inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
        try { block(); fail("Expected ${T::class.java.simpleName}") }
        catch (error: Throwable) { if (error !is T) throw error }
    }
}
