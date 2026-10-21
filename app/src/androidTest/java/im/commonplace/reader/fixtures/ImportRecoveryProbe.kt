package im.commonplace.reader.fixtures

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import im.commonplace.reader.CommonplaceApplication
import im.commonplace.reader.data.SourceAvailability
import im.commonplace.reader.data.SourceEntity
import im.commonplace.reader.imports.DocumentImporter
import im.commonplace.reader.reader.DocumentFormat
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportRecoveryProbe {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target: Context get() = instrumentation.targetContext
    private val app get() = target.applicationContext as CommonplaceApplication
    private val marker get() = File(target.filesDir, "import-recovery-ready")
    private val stateDir get() = File(target.filesDir, "import-recovery-probe")
    private val stateFile get() = File(stateDir, "state.properties")

    @Test fun stageSlowCopy() {
        if (InstrumentationRegistry.getArguments().getString("recoveryProbe") != "stage") return
        runBlocking {
            val repository = app.container.readerRepository
            val existing = repository.sources()
            assertSafeToReconcile(existing.map { it.assetPath }.toSet())

            check(!stateDir.exists()) { "A prior interrupted recovery fixture is still present." }
            val sourceId = UUID.randomUUID().toString()
            val annotation = "Import recovery fixture annotation $sourceId"
            val source = SourceEntity(
                id = sourceId,
                title = "Import recovery fixture $sourceId",
                authors = null,
                format = DocumentFormat.EPUB,
                originalUri = "content://import-recovery.invalid/$sourceId",
                checksum = MessageDigest.getInstance("SHA-256").digest(sourceId.toByteArray())
                    .joinToString("") { "%02x".format(it) },
                assetPath = "imports/assets/$sourceId.epub",
                availability = SourceAvailability.MISSING,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
            )
            app.container.database.readerDao().insertSource(source)
            repository.saveSourceAnnotation(sourceId, annotation)

            stateDir.mkdirs()
            Properties().apply {
                setProperty("sourceId", sourceId)
                setProperty("annotation", annotation)
                setProperty("baselineSourceIds", existing.joinToString(",") { it.id })
            }.let { properties ->
                FileOutputStream(stateFile).use { properties.store(it, "private process-kill probe state") }
            }

            val importer = DocumentImporter(
                target.contentResolver,
                target.filesDir,
                repository,
                openStream = {
                    object : InputStream() {
                        private var reads = 0
                        override fun read(): Int = 'A'.code
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            if (reads++ == 0) {
                                val count = minOf(length, 8192)
                                buffer.fill('A'.code.toByte(), offset, offset + count)
                                return count
                            }
                            val staging = File(target.filesDir, "imports/staging")
                            check(staging.listFiles().orEmpty().any { it.isFile && it.length() > 0L }) {
                                "The partial staging file must contain bytes before readiness is signaled."
                            }
                            FileOutputStream(marker).use { output ->
                                output.write("ready".toByteArray())
                                output.fd.sync()
                            }
                            CountDownLatch(1).await()
                            return -1
                        }
                    }
                },
            )
            val copy = async(Dispatchers.IO) {
                importer.importDocument(Uri.parse("content://import-recovery.invalid/$sourceId/document"))
            }
            withTimeout(60_000) {
                while (!marker.isFile || marker.length() == 0L) delay(50)
            }
            copy.await()
        }
    }

    @Test fun reopenAfterKill() {
        if (InstrumentationRegistry.getArguments().getString("recoveryProbe") != "verify") return
        runBlocking {
            assertTrue("The first process must have written its readiness marker.", marker.isFile)
            val properties = Properties().apply { stateFile.inputStream().use { load(it) } }
            val sourceId = properties.getProperty("sourceId")
            val annotation = properties.getProperty("annotation")
            val baselineIds = properties.getProperty("baselineSourceIds").split(',').filter { it.isNotEmpty() }.toSet()
            val repository = app.container.readerRepository
            val importer = DocumentImporter(target.contentResolver, target.filesDir, repository)
            try {
                importer.reconcile()
                val sources = repository.sources()
                assertEquals(baselineIds + sourceId, sources.map { it.id }.toSet())
                val fixtureSource = sources.single { it.id == sourceId }
                assertEquals(SourceAvailability.MISSING, fixtureSource.availability)
                assertFalse(sources.any { it.id !in baselineIds && it.availability == SourceAvailability.AVAILABLE })
                assertEquals(annotation, repository.observeEntries(sourceId).first().single().body)
                assertTrue(File(target.filesDir, "imports/staging").listFiles().orEmpty().isEmpty())
                val referenced = sources.map { it.assetPath }.toSet()
                assertTrue(File(target.filesDir, "imports/assets").listFiles().orEmpty()
                    .all { "imports/assets/${it.name}" in referenced })
            } finally {
                app.container.database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM entries WHERE sourceId = ?", arrayOf(sourceId),
                )
                app.container.database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM sources WHERE id = ?", arrayOf(sourceId),
                )
                marker.delete()
                stateDir.deleteRecursively()
            }
        }
    }

    private fun assertSafeToReconcile(referencedAssets: Set<String>) {
        val staging = File(target.filesDir, "imports/staging")
        check(staging.listFiles().orEmpty().isEmpty()) {
            "Refusing to run recovery probe with pre-existing staging files."
        }
        val assets = File(target.filesDir, "imports/assets")
        check(assets.listFiles().orEmpty().all { "imports/assets/${it.name}" in referencedAssets }) {
            "Refusing to run recovery probe with unrelated orphan assets."
        }
    }
}
