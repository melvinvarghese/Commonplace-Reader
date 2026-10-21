package im.commonplace.reader.reader.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import im.commonplace.reader.CommonplaceApplication
import im.commonplace.reader.data.*
import im.commonplace.reader.reader.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals

internal object PdfProofFixture {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val container get() = (context as CommonplaceApplication).container
    fun source(name: String, large: Boolean = false, isolated: Boolean = false): SourceEntity = runBlocking(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val file = File(context.filesDir, "imports/assets/$id.pdf").apply { parentFile!!.mkdirs() }
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/$name").use { input -> file.outputStream().use(input::copyTo) }
        if (large) {
            val fixture = File(context.filesDir, "proof-large-100mib.pdf")
            check(fixture.isFile && fixture.length() == 100L * 1024 * 1024) { "Transport the shared generated 100 MiB corpus before instrumentation" }
            fixture.inputStream().use { input -> file.outputStream().use(input::copyTo) }
        }
        fun checksum(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val block = ByteArray(65536); while (true) { val count = input.read(block); if (count < 0) break; digest.update(block, 0, count) } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        val manifest = InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/manifest.json").bufferedReader().use { org.json.JSONObject(it.readText()) }
        val fixtures = manifest.getJSONArray("fixtures")
        val fixtureName = if (large) "large-100mib.local.pdf" else name
        val expected = (0 until fixtures.length()).map(fixtures::getJSONObject).single { it.getString("file") == fixtureName }
        assertEquals("Shared fixture identity changed", expected.getString("sha256"), checksum())
        if (isolated) {
            // Dedicated mutation-harness source; never corrupt the reusable exact fixture.
            file.appendText("\n% Task6 checksum mutation harness $id\n")
        }
        val checksum = checksum()
        val source = SourceEntity(id, "PDF proof", null, DocumentFormat.PDF, "content://synthetic/$name", checksum, "imports/assets/$id.pdf", SourceAvailability.AVAILABLE, 1, 1)
        container.database.readerDao().sourceByChecksum(checksum)?.also { file.delete() } ?: source.also { container.database.readerDao().insertSource(it) }
    }
}
