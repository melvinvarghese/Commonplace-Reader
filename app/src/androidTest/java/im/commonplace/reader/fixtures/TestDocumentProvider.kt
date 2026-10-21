package im.commonplace.reader.fixtures

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** Test-only provider: unknown length, unseekable pipe, revoked grant and partial writer. */
class TestDocumentProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/pdf" // Deliberate MIME spoof for EPUB bytes.
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf("../../pretend.pdf", null))
        }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri.lastPathSegment?.let { it in revoked || it == "revoked" } == true) throw SecurityException("Synthetic grant revoked")
        val key = uri.lastPathSegment ?: throw FileNotFoundException()
        val fixture = if (key == "failing" || key == "revocable") "reflowable.epub" else key
        if (fixture !in setOf("reflowable.epub", "fixed-layout.epub", "hostile-remote.epub", "ordinary-text.pdf", "repeated-two-page.pdf", "rotated-cropped.pdf", "multilingual.pdf", "image-only-scan.pdf", "encrypted.pdf", "malformed.pdf")) throw FileNotFoundException()
        val bytes = requireNotNull(context).assets.open("fixtures/$fixture").use { it.readBytes() }
        val pipe = ParcelFileDescriptor.createReliablePipe()
        thread(name = "synthetic-document-writer") {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                    if (uri.lastPathSegment == "failing") {
                        output.write(bytes, 0, minOf(100, bytes.size))
                        pipe[1].closeWithError("Synthetic provider failure")
                    } else output.write(bytes)
                }
            } catch (_: java.io.IOException) { /* Import cancellation closes the read end. */ }
        }
        return pipe[0]
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Read only")
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method == "revoke" && arg != null) revoked.add(arg)
        if (method == "reset") revoked.clear()
        return Bundle.EMPTY
    }
    private val revoked = ConcurrentHashMap.newKeySet<String>()
}
