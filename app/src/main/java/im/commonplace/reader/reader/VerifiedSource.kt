package im.commonplace.reader.reader

import im.commonplace.reader.data.SourceEntity
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal class VerifiedSourceException(val userMessage: String, cause: Throwable? = null) : IOException(userMessage, cause)

internal suspend fun verifySourceFile(filesDir: File, source: SourceEntity): File = withContext(Dispatchers.IO) {
    coroutineContext.ensureActive()
    val extension = when (source.format) {
        DocumentFormat.EPUB -> "epub"
        DocumentFormat.PDF -> "pdf"
    }
    val canonicalId = runCatching { UUID.fromString(source.id).toString() }.getOrNull()
    val expectedPath = canonicalId?.let { "imports/assets/$it.$extension" }
    if (canonicalId != source.id || source.assetPath != expectedPath) {
        throw VerifiedSourceException("This document could not be opened safely. Your writing is retained.")
    }

    val root = filesDir.canonicalFile
    val imports = File(root, "imports").canonicalFile
    val assets = File(imports, "assets").canonicalFile
    val asset = File(root, requireNotNull(expectedPath)).canonicalFile
    if (imports.parentFile != root || assets.parentFile != imports || asset.parentFile != assets) {
        throw VerifiedSourceException("This document could not be opened safely. Your writing is retained.")
    }
    if (!asset.isFile) throw VerifiedSourceException("The document is missing. Your writing is retained.")

    val digest = MessageDigest.getInstance("SHA-256")
    asset.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    coroutineContext.ensureActive()
    val checksum = digest.digest().joinToString("") { "%02x".format(it) }
    if (checksum != source.checksum) {
        throw VerifiedSourceException("Document bytes changed. Saved locations are unresolved; your writing is retained.")
    }
    asset
}
