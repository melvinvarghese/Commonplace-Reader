package im.commonplace.reader.imports

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.commonplace.reader.data.*
import im.commonplace.reader.reader.DocumentFormat
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real file-backed Room + app-owned files; provider streams are injected at the I/O boundary. */
@RunWith(AndroidJUnit4::class)
class DocumentImporterTest {
    private lateinit var context: Context
    private lateinit var db: CommonplaceDatabase
    private lateinit var repo: ReaderRepository
    private lateinit var root: File
    private lateinit var name: String
    private val uri = Uri.parse("content://untrusted/../../private/pretend.pdf")
    private val existingId = "00000000-0000-0000-0000-000000000001"
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        name = "import-${UUID.randomUUID()}.db"
        root = File(context.filesDir, "import-test-${UUID.randomUUID()}").apply { mkdirs() }
        db = Room.databaseBuilder(context, CommonplaceDatabase::class.java, name).build()
        repo = ReaderRepository(db)
        db.readerDao().insertSource(SourceEntity(existingId, "Keep attribution", null, DocumentFormat.EPUB,
            "content://existing", "a".repeat(64), "documents/existing.epub", SourceAvailability.MISSING, 1, 1))
        repo.saveSourceAnnotation(existingId, "Keep this unrelated writing")
        Unit
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name); root.deleteRecursively() }
    private fun importer(bytes: ByteArray = epub(), space: (File) -> Long = { Long.MAX_VALUE },
                         stream: () -> InputStream = { ByteArrayInputStream(bytes) }) =
        DocumentImporter(context.contentResolver, root, repo, space) { stream() }
    private suspend fun imported() = repo.observeSources().first().filter { it.id != existingId }
    private suspend fun assertNoPartial() {
        assertTrue(imported().isEmpty())
        assertTrue(root.walkTopDown().filter { it.isFile }.toList().isEmpty())
        assertEquals("Keep this unrelated writing", repo.observeEntries(existingId).first().single().body)
    }
    @Test fun unknownLengthUnseekableStreamCopiesExactBytesIntoRandomRelativeAsset() = runBlocking {
        val original = epub()
        val result = importer(original, stream = { object : FilterInputStream(ByteArrayInputStream(original)) {
            override fun available() = 0
            override fun markSupported() = false
        } }).importDocument(uri)
        assertTrue(result is ImportResult.Imported)
        val row = imported().single()
        assertEquals((result as ImportResult.Imported).sourceId, row.id)
        assertEquals(DocumentFormat.EPUB, row.format)
        assertEquals(SourceAvailability.AVAILABLE, row.availability)
        assertFalse(File(row.assetPath).isAbsolute)
        assertFalse(row.assetPath.contains(".."))
        assertFalse(row.assetPath.contains("pretend"))
        assertArrayEquals(original, File(root, row.assetPath).readBytes())
        assertArrayEquals(epub(), original)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it) }, row.checksum)
    }
    @Test fun extensionAndMimeAreNotAcceptedAsEvidenceOfPdfContent() = runBlocking {
        assertTrue(importer("not a pdf".toByteArray()).importDocument(uri) is ImportResult.Unsupported)
        assertNoPartial()
    }
    @Test fun pdfHeaderWithoutParseableDocumentIsUnsupported() = runBlocking {
        assertTrue(importer("%PDF-1.7\ninvalid\n%%EOF".toByteArray()).importDocument(uri) is ImportResult.Unsupported)
        assertNoPartial()
    }
    @Test fun invalidEpubContainerIsUnsupported() = runBlocking {
        assertTrue(importer(epub(container = "<container/>" )).importDocument(uri) is ImportResult.Unsupported)
        assertNoPartial()
    }
    @Test fun missingSpineMemberIsUnsupported() = runBlocking {
        assertTrue(importer(epub(chapter = false)).importDocument(uri) is ImportResult.Unsupported)
        assertNoPartial()
    }
    @Test fun revokedPermissionReturnsSafeFailureWithoutProviderPath() = runBlocking {
        val result = importer(stream = { throw SecurityException("secret /provider/path") }).importDocument(uri)
        assertTrue(result is ImportResult.Failed)
        assertFalse((result as ImportResult.Failed).reason.contains("provider/path"))
        assertNoPartial()
    }
    @Test fun providerFailureHalfwayDoesNotRegisterOrLeaveAFile() = runBlocking {
        val result = importer(stream = { object : InputStream() {
            var count = 0
            override fun read(): Int { if (++count > 100) throw IOException("provider died"); return 65 }
        } }).importDocument(uri)
        assertTrue(result is ImportResult.Failed)
        assertNoPartial()
    }
    @Test fun remainingSpaceIsCheckedDuringCopyNotJustAtStart() = runBlocking {
        var checks = 0
        val result = importer(epub(extra = ByteArray(200_000).also { java.util.Random(0).nextBytes(it) }),
            space = { if (++checks < 3) Long.MAX_VALUE else 0 }).importDocument(uri)
        assertTrue(result is ImportResult.Failed)
        assertTrue("must recheck remaining space", checks >= 3)
        assertNoPartial()
    }
    @Test fun enospcFromStreamBoundaryLeavesNoPartial() = runBlocking {
        val result = importer(stream = { throw IOException("ENOSPC") }).importDocument(uri)
        assertTrue(result is ImportResult.Failed)
        assertNoPartial()
    }
    @Test fun cancellationPropagatesAndRemovesStaging() = runBlocking {
        var cancelled = false
        try { importer(stream = { throw CancellationException("cancel") }).importDocument(uri) }
        catch (_: CancellationException) { cancelled = true }
        assertTrue("Cancellation must not become Failed", cancelled)
        assertNoPartial()
    }
    @Test fun cancelledCoroutineMidCopyLeavesNoSource() = runBlocking {
        val reading = CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1)
        val job = launch(Dispatchers.IO) {
            importer(stream = { object : ByteArrayInputStream(epub()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    reading.complete(Unit); release.await(); return super.read(b, off, len)
                }
            } }).importDocument(uri)
        }
        withTimeout(10000) { reading.await() }
        job.cancel(); release.countDown(); job.join()
        assertNoPartial()
    }
    @Test fun databaseFailureAfterPlacementRemovesOnlyNewAssetAndKeepsWriting() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_import BEFORE INSERT ON sources BEGIN SELECT RAISE(ABORT, 'injected'); END")
        assertTrue(importer().importDocument(uri) is ImportResult.Failed)
        assertNoPartial()
    }
    @Test fun concurrentDuplicatesHaveOneCompleteAssetAndOneSource() = runBlocking {
        val operation = importer()
        val results = coroutineScope { (1..4).map { async(Dispatchers.IO) { operation.importDocument(uri) } }.awaitAll() }
        assertEquals(1, results.count { it is ImportResult.Imported })
        assertEquals(3, results.count { it is ImportResult.Duplicate })
        val source = imported().single()
        assertTrue(results.all { when(it) {
            is ImportResult.Imported -> it.sourceId == source.id
            is ImportResult.Duplicate -> it.sourceId == source.id
            else -> false
        } })
        assertArrayEquals(epub(), File(root, source.assetPath).readBytes())
        assertEquals(1, root.walkTopDown().count { it.isFile })
    }
    @Test fun startupRemovesAbandonedStagingAndOrphanButRetainsNotesAndUnownedFiles() = runBlocking {
        File(root, "imports/staging").mkdirs(); File(root, "imports/assets").mkdirs()
        File(root, "imports/staging/dead.part").writeText("partial")
        File(root, "imports/assets/orphan.epub").writeText("orphan")
        File(root, "unrelated.txt").writeText("preserve")
        importer().reconcile()
        assertFalse(File(root, "imports/staging/dead.part").exists())
        assertFalse(File(root, "imports/assets/orphan.epub").exists())
        assertEquals("preserve", File(root, "unrelated.txt").readText())
        assertEquals(1, repo.observeEntries(existingId).first().size)
    }
    @Test fun missingAssetBecomesUnavailableWithoutLosingSourceOrWriting() = runBlocking {
        db.openHelper.writableDatabase.execSQL("UPDATE sources SET availability='AVAILABLE' WHERE id='$existingId'")
        importer().reconcile()
        assertEquals(SourceAvailability.MISSING, db.readerDao().source(existingId)!!.availability)
        assertEquals(1, repo.observeEntries(existingId).first().size)
    }
    @Test fun unsafeZipPathsAndDuplicateMembersAreRejected() = runBlocking {
        for (path in listOf("../escape", "/absolute", "C:/windows", "OEBPS/../../escape", "evil\\name")) {
            assertTrue("unsafe path", importer(epub(extraName = path, extra = byteArrayOf(1))).importDocument(uri) is ImportResult.Unsupported)
        }
        assertNoPartial()
    }
    @Test fun oversizedZipMemberIsRejectedBeforeParsing() = runBlocking {
        assertTrue(importer(epub(extra = ByteArray(64 * 1024 * 1024 + 1))).importDocument(uri) is ImportResult.Unsupported)
        assertNoPartial()
    }
    @Test fun xmlDoctypeAndEntityExpansionAreRejectedWithoutFetching() = runBlocking {
        assertTrue(importer(epub(container = """<!DOCTYPE container [<!ENTITY x SYSTEM "file:///private">]><container>&x;</container>""")).importDocument(uri) is ImportResult.Unsupported)
        assertNoPartial()
    }
    @Test fun successfulCopyCanBeReopenedAfterOriginalAccessIsRevoked() = runBlocking {
        var revoked = false
        val bytes = epub()
        val operation = importer(stream = { if (revoked) throw SecurityException() else ByteArrayInputStream(bytes) })
        assertTrue(operation.importDocument(uri) is ImportResult.Imported)
        revoked = true
        val source = imported().single()
        assertArrayEquals(bytes, File(root, source.assetPath).readBytes())
        assertTrue(operation.importDocument(uri) is ImportResult.Failed)
        assertEquals(1, imported().size)
    }
    @Test fun inputBeyond256MiBStopsWithoutRegisteringOrBufferingWholeFile() = runBlocking {
        var consumed = 0L
        val result = importer(stream = { object : InputStream() {
            override fun read() = error("bulk read required")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                consumed += length
                java.util.Arrays.fill(buffer, offset, offset + length, 0.toByte())
                return length
            }
        } }).importDocument(uri)
        assertTrue(result is ImportResult.Unsupported)
        assertTrue(consumed > 256L * 1024 * 1024)
        assertTrue(consumed <= 256L * 1024 * 1024 + 65536)
        assertNoPartial()
    }
    @Test fun tooManyZipMembersAreRejected() = runBlocking {
        assertTrue(importer(epub(extraCount = 10_001)).importDocument(uri) is ImportResult.Unsupported)
        assertNoPartial()
    }
    @Test fun cumulativeZipExpansionAbove512MiBIsRejected() = runBlocking {
        assertTrue(importer(epub(extra = ByteArray(64 * 1024 * 1024), extraCount = 9)).importDocument(uri) is ImportResult.Unsupported)
        assertNoPartial()
    }
    @Test fun duplicateZipPathsAndForgedCrcAreRejected() = runBlocking {
        // ZIP writer forbids duplicate names: patch equal-length names in both headers instead.
        val duplicate = epub(extraName = "duplicat", extra = byteArrayOf(1)).toString(Charsets.ISO_8859_1)
            .replace("duplicat", "mimetype").toByteArray(Charsets.ISO_8859_1)
        assertTrue(importer(duplicate).importDocument(uri) is ImportResult.Unsupported)
        val invalidCrc = epub().copyOf()
        val central = invalidCrc.indices.first { i -> i + 4 < invalidCrc.size && invalidCrc[i] == 0x50.toByte() && invalidCrc[i+1] == 0x4b.toByte() && invalidCrc[i+2] == 1.toByte() && invalidCrc[i+3] == 2.toByte() }
        invalidCrc[central + 16] = (invalidCrc[central + 16].toInt() xor 1).toByte()
        assertTrue(importer(invalidCrc).importDocument(uri) is ImportResult.Unsupported)
        assertNoPartial()
    }
    @Test fun cancellationAtRegistrationNeverDeletesDurablyReferencedAsset() = runBlocking {
        val parent = Job()
        val cancelledAfterInsert = java.util.concurrent.atomic.AtomicBoolean(false)
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                Dispatchers.IO.dispatch(context, Runnable {
                    val inserted = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sources").use {
                        it.moveToFirst(); it.getInt(0) > 1
                    }
                    if (inserted) { cancelledAfterInsert.set(true); parent.cancel() }
                    block.run()
                })
            }
        }
        val operation = DocumentImporter(context.contentResolver, root, ReaderRepository(db, dispatcher),
            { Long.MAX_VALUE }) { ByteArrayInputStream(epub()) }
        try {
            val task = async(Dispatchers.IO + parent) { operation.importDocument(uri) }
            try { task.await() } catch (_: CancellationException) { }
            assertTrue("cancel must happen after durable INSERT, before its dispatched continuation", cancelledAfterInsert.get())
            val source = imported().single()
            assertArrayEquals(epub(), File(root, source.assetPath).readBytes())
            assertEquals(1, root.walkTopDown().count { it.isFile })
        } finally { parent.cancel() }
    }
    @Test fun duplicateOfMissingSourceDoesNotPretendToRepairOrDeleteWriting() = runBlocking {
        val operation = importer()
        val result = operation.importDocument(uri) as ImportResult.Imported
        val source = imported().single()
        repo.saveSourceAnnotation(source.id, "retain missing-source note")
        File(root, source.assetPath).delete()
        operation.reconcile()
        assertEquals(ImportResult.Duplicate(result.sourceId), operation.importDocument(uri))
        assertEquals(SourceAvailability.MISSING, imported().single().availability)
        assertFalse(File(root, source.assetPath).exists())
        assertEquals("retain missing-source note", repo.observeEntries(source.id).first().single().body)
    }
    companion object {
        fun epub(container: String = """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
                 chapter: Boolean = true, extraName: String = "padding.bin", extra: ByteArray? = null, extraCount: Int = 1): ByteArray {
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                fun add(name: String, bytes: ByteArray) {
                    val entry = ZipEntry(name).apply { time = 0 }
                    zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
                }
                add("mimetype", "application/epub+zip".toByteArray())
                add("META-INF/container.xml", container.toByteArray())
                add("OEBPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">test</dc:identifier><dc:title>Original test</dc:title><dc:language>en</dc:language></metadata><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>""".toByteArray())
                if (chapter) add("OEBPS/chapter.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Original</title></head><body><p>Keep this original phrase.</p></body></html>""".toByteArray())
                if (extra != null || extraCount != 1) repeat(extraCount) { index ->
                    add(if (extraCount == 1) extraName else "$extraName-$index", extra ?: byteArrayOf())
                }
            }
            return output.toByteArray()
        }
    }
}
