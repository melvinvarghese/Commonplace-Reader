package im.commonplace.reader.imports

import android.content.ContentResolver
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.provider.OpenableColumns
import im.commonplace.reader.data.ReaderRepository
import im.commonplace.reader.data.SourceAvailability
import im.commonplace.reader.data.SourceEntity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DocumentImporter(
    private val resolver: ContentResolver,
    private val filesDir: File,
    private val repository: ReaderRepository,
    private val freeSpace: (File) -> Long = { it.usableSpace },
    private val openStream: (Uri) -> InputStream? = resolver::openInputStream,
) {
    /** One app process owns imports. Reconciliation cannot sweep an active import's files. */
    suspend fun importDocument(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        importLock.withLock {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return@withLock ImportResult.Unsupported("Choose a PDF or EPUB using the document picker.")
            var staged: File? = null
            var placed: File? = null
            var committed = false
            try {
                val stagingDir = File(filesDir, "imports/staging").apply { checkDirectory() }
                val assetsDir = File(filesDir, "imports/assets").apply { checkDirectory() }
                val id = UUID.randomUUID().toString()
                staged = File(stagingDir, "$id.part")
                val digest = MessageDigest.getInstance("SHA-256")
                val input = openStream(uri) ?: throw IOException("No stream")
                input.use {
                    FileOutputStream(staged).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            copied += count
                            if (copied > MAX_IMPORT_BYTES) throw UnsupportedDocument("Document exceeds the 256 MiB import limit.")
                            if (freeSpace(filesDir) < count + SPACE_RESERVE) throw IOException("Insufficient space")
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                currentCoroutineContext().ensureActive()
                val format = DocumentValidation.validate(staged)
                val checksum = digest.digest().joinToString("") { "%02x".format(it) }
                repository.sourceByChecksum(checksum)?.let { return@withLock ImportResult.Duplicate(it.id) }
                val relativePath = "imports/assets/$id.${format.name.lowercase()}"
                val title = displayTitle(uri)
                currentCoroutineContext().ensureActive()
                // No cancellable gap between placement and registration, or after durable registration
                // before recording ownership; cancellation must never remove a referenced asset.
                withContext(NonCancellable) {
                    placed = File(filesDir, relativePath)
                    if (!staged.renameTo(placed)) throw IOException("Unable to place document")
                    val now = System.currentTimeMillis()
                    try {
                        repository.registerImportedSource(SourceEntity(id, title, null, format, uri.toString(),
                            checksum, relativePath, SourceAvailability.AVAILABLE, now, now))
                        committed = true
                        ImportResult.Imported(id)
                    } catch (failure: SQLiteConstraintException) {
                        val existing = repository.sourceByChecksum(checksum) ?: throw failure
                        ImportResult.Duplicate(existing.id)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unsupported: UnsupportedDocument) {
                ImportResult.Unsupported(unsupported.message ?: "Unsupported document.")
            } catch (_: SecurityException) {
                ImportResult.Failed("Document access was revoked. Choose the file again.")
            } catch (_: Exception) {
                ImportResult.Failed("Import failed. Check document access and available storage, then retry.")
            } finally {
                staged?.delete()
                if (!committed) placed?.delete()
            }
        }
    }

    suspend fun reconcile(): Unit = withContext(Dispatchers.IO) {
        importLock.withLock {
            val sources = repository.sources() // If the database is unavailable, do not sweep anything.
            File(filesDir, "imports/staging").listFiles()?.forEach { it.deleteRecursively() }
            val references = sources.map { it.assetPath }.toSet()
            File(filesDir, "imports/assets").listFiles()?.forEach { file ->
                if ("imports/assets/${file.name}" !in references) file.deleteRecursively()
            }
            for (source in sources) {
                val asset = File(filesDir, source.assetPath)
                val contained = !File(source.assetPath).isAbsolute &&
                    asset.canonicalPath.startsWith(filesDir.canonicalPath + File.separator)
                if ((!contained || !asset.isFile) && source.availability != SourceAvailability.MISSING) {
                    repository.markMissing(source.id)
                }
            }
        }
    }

    private fun displayTitle(uri: Uri): String {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return name?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.filter { !it.isISOControl() }?.take(200)?.takeIf { it.isNotBlank() } ?: "Imported document"
    }

    private fun File.checkDirectory() { if (!isDirectory && !mkdirs()) throw IOException("Storage unavailable") }

    companion object {
        const val MAX_IMPORT_BYTES = 256L * 1024 * 1024
        private const val SPACE_RESERVE = 1024L * 1024
        private val importLock = Mutex()
    }
}

internal class UnsupportedDocument(message: String) : IOException(message)
