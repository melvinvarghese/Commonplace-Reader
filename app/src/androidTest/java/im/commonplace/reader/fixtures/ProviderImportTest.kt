package im.commonplace.reader.fixtures

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import im.commonplace.reader.data.CommonplaceDatabase
import im.commonplace.reader.data.ReaderRepository
import im.commonplace.reader.imports.DocumentImporter
import im.commonplace.reader.imports.ImportResult
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Device only: exercises real ContentResolver pipes and Android's PDF parser, not Robolectric. */
@RunWith(AndroidJUnit4::class)
class ProviderImportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val testContext = instrumentation.context
    private val name = "provider-${UUID.randomUUID()}.db"
    private val root = File(context.filesDir, name)
    private lateinit var db: CommonplaceDatabase
    private lateinit var repository: ReaderRepository
    private lateinit var importer: DocumentImporter
    private val authority get() = testContext.packageName + ".fixtures"
    private fun uri(path: String) = Uri.parse("content://$authority/$path")
    @Before fun setup() {
        root.mkdirs()
        db = Room.databaseBuilder(context, CommonplaceDatabase::class.java, name).build()
        repository = ReaderRepository(db)
        importer = DocumentImporter(context.contentResolver, root, repository)
        context.contentResolver.call(uri("revocable"), "reset", null, null)
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name); root.deleteRecursively() }
    @Test fun spoofedMimeUnknownLengthPipeImportsEpubAndSurvivesProviderRevocationOffline() = runBlocking {
        val original = context.contentResolver.openInputStream(uri("revocable"))!!.use { it.readBytes() }
        val result = importer.importDocument(uri("revocable"))
        assertTrue("provider result: $result", result is ImportResult.Imported)
        assertArrayEquals(original, context.contentResolver.openInputStream(uri("revocable"))!!.use { it.readBytes() })
        context.contentResolver.call(uri("revocable"), "revoke", "revocable", null)
        assertTrue(importer.importDocument(uri("revocable")) is ImportResult.Failed)
        db.close()
        db = Room.databaseBuilder(context, CommonplaceDatabase::class.java, name).build()
        repository = ReaderRepository(db)
        val source = repository.observeSources().first().single()
        val copied = File(root, source.assetPath)
        assertArrayEquals(testContext.assets.open("fixtures/reflowable.epub").use { it.readBytes() }, copied.readBytes())
        // Reimport the owned bytes, with no provider access at all: validates offline parse too.
        importer = DocumentImporter(context.contentResolver, root, repository, openStream = { copied.inputStream() })
        assertEquals(ImportResult.Duplicate(source.id), importer.importDocument(uri("revoked")))
    }
    @Test fun realPartialProviderPipeLeavesNoUsableSource() = runBlocking {
        val result = importer.importDocument(uri("failing"))
        assertTrue(result is ImportResult.Failed || result is ImportResult.Unsupported)
        assertTrue(repository.observeSources().first().isEmpty())
        assertEquals(0, root.walkTopDown().count { it.isFile })
    }
    @Test fun sharedPdfCorpusUsesRealPlatformParserAndRejectsEncryptedAndMalformed() = runBlocking {
        for (name in listOf("ordinary-text.pdf", "repeated-two-page.pdf", "rotated-cropped.pdf", "multilingual.pdf", "image-only-scan.pdf")) {
            val original = testContext.assets.open("fixtures/$name").use { it.readBytes() }
            val result = importer.importDocument(uri(name))
            assertTrue("$name: $result", result is ImportResult.Imported)
            val source = repository.sources().single { it.id == (result as ImportResult.Imported).sourceId }
            assertArrayEquals(original, File(root, source.assetPath).readBytes())
        }
        for (name in listOf("encrypted.pdf", "malformed.pdf")) {
            assertTrue(name, importer.importDocument(uri(name)) is ImportResult.Unsupported)
        }
        assertEquals(5, repository.sources().size)
    }
    @Test fun sharedEpubCorpusImportsWithoutRenderingOrFetchingRemoteResources() = runBlocking {
        for (name in listOf("reflowable.epub", "fixed-layout.epub", "hostile-remote.epub")) {
            assertTrue(name, importer.importDocument(uri(name)) is ImportResult.Imported)
        }
        assertEquals(3, repository.sources().size)
    }
}
