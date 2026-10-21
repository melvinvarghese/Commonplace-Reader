package im.commonplace.reader.reader

import android.app.Application
import im.commonplace.reader.reader.epub.ClosingResourceContainer
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.data.asInputStream
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.resource.TransformingResource
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ClosingResourceContainerTest {
    private val href = requireNotNull(Url.fromDecodedPath("EPUB/chapter.xhtml"))
    private fun resource(read: suspend (LongRange?) -> Try<ByteArray, ReadError>) = object : Resource {
        override val sourceUrl = null
        override suspend fun properties() = Try.success(Resource.Properties(mapOf("marker" to "original")))
        override suspend fun length() = Try.success(6L)
        override suspend fun read(range: LongRange?) = read.invoke(range)
        override fun close() {}
    }
    private fun container(resource: Resource, close: () -> Unit = {}) = object : Container<Resource> {
        override val entries = setOf(href)
        override fun get(url: Url) = resource.takeIf { url == href }
        override fun close() = close.invoke()
    }
    @Test fun activeReadsPreserveRangeMetadataAndCloseTheContainerOnce() = runTest {
        var received: LongRange? = null
        var closes = 0
        val guarded = ClosingResourceContainer(container(resource { received = it; Try.success("bc".toByteArray()) }) { closes++ })
        val held = requireNotNull(guarded[href])
        assertArrayEquals("bc".toByteArray(), held.read(1L..2L).getOrNull())
        assertEquals(1L..2L, received)
        assertEquals(6L, held.length().getOrNull())
        assertEquals("original", held.properties().getOrNull()?.get("marker"))
        guarded.close(); guarded.close()
        assertEquals(1, closes)
    }
    @Test fun resourceRetainedBeforeCloseCannotReadTheClosedRealZipOrMetadata() = runTest {
        val path = Files.createTempFile("reader-close-regression", ".zip")
        try {
            ZipOutputStream(Files.newOutputStream(path)).use { it.putNextEntry(ZipEntry("chapter")); it.write("chapter".toByteArray()); it.closeEntry() }
            val zip = ZipFile(path.toFile())
            val entry = zip.getEntry("chapter")
            val guarded = ClosingResourceContainer(container(resource { Try.success(zip.getInputStream(entry).use { it.readBytes() }) }) { zip.close() })
            val held = requireNotNull(guarded[href])
            guarded.close()
            assertTrue(held.read().isFailure)
            assertThrows(java.io.IOException::class.java) { held.asInputStream().use { it.read() } }
            assertTrue(held.length().isFailure)
            assertTrue(held.properties().isFailure)
            assertNull(guarded[href])
        } finally { Files.deleteIfExists(path) }
    }
    @Test fun closeDuringSuspendedReadConvertsClosedZipStateToTypedFailure() = runTest {
        for (throwsAfterClose in listOf(false, true)) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var retired = false
            val guarded = ClosingResourceContainer(container(resource {
                entered.complete(Unit)
                release.await()
                if (throwsAfterClose) check(!retired) { "zip file closed" }
                Try.success("active".toByteArray())
            }) { retired = true })
            val held = requireNotNull(guarded[href])
            val read = async { held.read() }
            entered.await()
            guarded.close()
            release.complete(Unit)
            assertTrue(read.await().isFailure)
        }
    }
    @Test fun cancellationIsNotConvertedIntoAReadFailure() = runTest {
        lateinit var guarded: ClosingResourceContainer
        guarded = ClosingResourceContainer(container(resource { guarded.close(); throw CancellationException("cancelled") }))
        try { requireNotNull(guarded[href]).read(); fail("Cancellation must propagate") }
        catch (expected: CancellationException) { assertEquals("cancelled", expected.message) }
        guarded.close()
    }
    @Test fun cachedTransformedBytesAreNotServedAfterContainerClose() = runTest {
        val transformed = TransformingResource(resource { Try.success("cached".toByteArray()) }) { Try.success(it) }
        val guarded = ClosingResourceContainer(container(transformed))
        val held = requireNotNull(guarded[href])
        assertArrayEquals("cached".toByteArray(), held.read().getOrNull())
        guarded.close()
        assertTrue(held.read().isFailure)
    }
    @Test fun unrelatedActiveSessionInvariantFailuresAreNotHidden() = runTest {
        val guarded = ClosingResourceContainer(container(resource { error("active invariant") }))
        try { requireNotNull(guarded[href]).read(); fail("Unrelated invariants must propagate") }
        catch (expected: IllegalStateException) { assertEquals("active invariant", expected.message) }
        guarded.close()
    }
}
