package im.commonplace.reader.ui

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.commonplace.reader.data.CommonplaceDatabase
import im.commonplace.reader.data.ReaderRepository
import im.commonplace.reader.imports.DocumentImporter
import im.commonplace.reader.imports.DocumentImporterTest
import im.commonplace.reader.imports.ImportResult
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class LibraryStateTest {
    private lateinit var context: Context
    private lateinit var db: CommonplaceDatabase
    private lateinit var root: File
    private lateinit var name: String
    private lateinit var model: LibraryViewModel
    private val store = ViewModelStore()
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        name = "library-${UUID.randomUUID()}.db"
        root = File(context.filesDir, name).apply { mkdirs() }
        db = Room.databaseBuilder(context, CommonplaceDatabase::class.java, name).build()
        val repository = ReaderRepository(db)
        val importer = DocumentImporter(context.contentResolver, root, repository, { Long.MAX_VALUE }) {
            ByteArrayInputStream(DocumentImporterTest.epub())
        }
        model = LibraryViewModel(repository, importer)
        store.put("library", model)
    }
    @After fun cleanup() { store.clear(); db.close(); context.deleteDatabase(name); root.deleteRecursively() }
    private fun awaitCondition(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("library did not reach expected state", predicate())
    }
    @Test fun initialStateHasNoFakeBooks() {
        assertFalse(model.uiState.value is LibraryUiState.Books)
    }
    @Test fun importedBookAppearsFromRepositoryAndDuplicateCanOpenExisting() {
        model.importDocument(Uri.parse("content://test/book"))
        awaitCondition { model.importResult.value is ImportResult.Imported }
        awaitCondition { model.uiState.value is LibraryUiState.Books }
        val source = (model.uiState.value as LibraryUiState.Books).sources.single()
        model.dismissResult()
        model.importDocument(Uri.parse("content://test/book"))
        awaitCondition { model.importResult.value is ImportResult.Duplicate }
        assertEquals(source.id, (model.importResult.value as ImportResult.Duplicate).sourceId)
        model.openExisting(source.id)
        awaitCondition { model.selectedSource.value?.id == source.id }
        model.closeSource()
        assertNull(model.selectedSource.value)
    }
}
