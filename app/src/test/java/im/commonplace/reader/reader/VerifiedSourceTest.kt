package im.commonplace.reader.reader

import im.commonplace.reader.data.SourceAvailability
import im.commonplace.reader.data.SourceEntity
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifiedSourceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun verifiesEpubAndPdfAssetsAtTheirCanonicalNames() = runBlocking {
        for (format in DocumentFormat.values()) {
            val bytes = "verified ${format.name}".toByteArray()
            val source = source(format, bytes)
            assertEquals(asset(source).canonicalFile, verifySourceFile(temporaryFolder.root, source))
        }
    }

    @Test fun replacementBytesFailWithTheSavedLocationWarning() = runBlocking {
        val source = source(DocumentFormat.EPUB, "original".toByteArray())
        asset(source).writeBytes("replacement".toByteArray())

        val failure = try {
            verifySourceFile(temporaryFolder.root, source)
            throw AssertionError("Expected changed document bytes to be rejected")
        } catch (expected: VerifiedSourceException) {
            expected
        }
        assertEquals("Document bytes changed. Saved locations are unresolved; your writing is retained.", failure.userMessage)
    }

    @Test fun rejectsTraversalAndMissingAssets() = runBlocking {
        val bytes = "document".toByteArray()
        val valid = source(DocumentFormat.PDF, bytes)
        val traversal = valid.copy(assetPath = "imports/assets/../outside/${valid.id}.pdf")
        try {
            verifySourceFile(temporaryFolder.root, traversal)
            throw AssertionError("Expected traversal path to be rejected")
        } catch (_: VerifiedSourceException) {
        }

        asset(valid).delete()
        try {
            verifySourceFile(temporaryFolder.root, valid)
            throw AssertionError("Expected missing asset to be rejected")
        } catch (_: VerifiedSourceException) {
        }
    }

    @Test fun cancellationDuringChecksumIsPropagated() = runBlocking {
        val bytes = ByteArray(64 * 1024) { 0x5a }
        val source = source(DocumentFormat.EPUB, bytes)
        val file = asset(source)
        file.outputStream().use { output -> repeat(1024) { output.write(bytes) } }
        val started = CountDownLatch(1)
        val result = CompletableDeferred<Boolean>()
        val job = launch(Dispatchers.IO) {
            started.countDown()
            try {
                verifySourceFile(temporaryFolder.root, source)
                result.complete(false)
            } catch (_: kotlinx.coroutines.CancellationException) {
                result.complete(true)
            } catch (failure: Throwable) {
                result.completeExceptionally(failure)
            }
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        delay(10)
        job.cancelAndJoin()
        assertTrue(result.await())
    }

    private fun source(format: DocumentFormat, bytes: ByteArray): SourceEntity {
        val id = UUID.randomUUID().toString()
        val extension = format.name.lowercase()
        val relativePath = "imports/assets/$id.$extension"
        asset(id, extension).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        return SourceEntity(
            id = id,
            title = "test",
            authors = null,
            format = format,
            originalUri = "content://test/document",
            checksum = sha256(bytes),
            assetPath = relativePath,
            availability = SourceAvailability.AVAILABLE,
            createdAt = 0,
            modifiedAt = 0,
        )
    }

    private fun asset(source: SourceEntity) = File(temporaryFolder.root, source.assetPath)

    private fun asset(id: String, extension: String) = File(temporaryFolder.root, "imports/assets/$id.$extension")

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
