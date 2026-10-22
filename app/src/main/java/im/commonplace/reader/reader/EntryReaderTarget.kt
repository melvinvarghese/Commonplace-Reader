package im.commonplace.reader.reader

import im.commonplace.reader.data.EntryEntity
import im.commonplace.reader.data.EntryKind
import im.commonplace.reader.data.SourceAvailability
import im.commonplace.reader.data.SourceEntity
import im.commonplace.reader.notebook.NotebookStore
import im.commonplace.reader.reader.epub.ReadiumLocations
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.UUID

sealed interface EntryReaderTarget {
    data class Passage(val entryId: String,val sourceId: String,val location: SavedLocation): EntryReaderTarget
    data class Book(val entryId: String,val sourceId: String): EntryReaderTarget
    data class Unavailable(val reason: String,val entry: EntryEntity?=null): EntryReaderTarget
}
data class EntryJump(val requestId: Long,val location: SavedLocation)
class EntryReaderTargetResolver(private val notebook: NotebookStore,private val reader: ReaderStore,private val verifyFile: suspend (SourceEntity) -> File) {
    suspend fun resolveEntryReaderTarget(entryId: String): EntryReaderTarget {
        var entry: EntryEntity?=null
        try {
            if(runCatching { UUID.fromString(entryId).toString() }.getOrNull()!=entryId)
                return EntryReaderTarget.Unavailable("This entry request is invalid. Your writing is retained.")
            entry=notebook.getEntry(entryId) ?: return EntryReaderTarget.Unavailable("This entry is no longer available.")
            val sourceId=entry.sourceId ?: return EntryReaderTarget.Unavailable("The book record is missing. Your writing is retained.",entry)
            val source=reader.getSource(sourceId)?.takeIf { it.id==sourceId }
                ?: return EntryReaderTarget.Unavailable("The book record is missing. Your writing is retained.",entry)
            if(source.availability!=SourceAvailability.AVAILABLE)
                return EntryReaderTarget.Unavailable("The document is missing. Your writing is retained.",entry)
            if(source.format==DocumentFormat.PDF || entry.location?.format==DocumentFormat.PDF)
                return EntryReaderTarget.Unavailable("PDF passages remain experimental and unresolved. Your writing is retained.",entry)
            verifyFile(source)
            return when(entry.kind) {
                EntryKind.HIGHLIGHT -> {
                    val location=entry.location ?: return EntryReaderTarget.Unavailable("This saved location is unresolved. Your writing is retained.",entry)
                    ReadiumLocations.decode(location)
                    EntryReaderTarget.Passage(entry.id,source.id,location)
                }
                EntryKind.ANNOTATION,EntryKind.SOURCE_NOTE ->
                    if(entry.location==null && entry.quotation==null) EntryReaderTarget.Book(entry.id,source.id)
                    else EntryReaderTarget.Unavailable("This saved location is unresolved. Your writing is retained.",entry)
                EntryKind.THOUGHT -> EntryReaderTarget.Unavailable("Independent thoughts have no book passage.",entry)
            }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(failure: VerifiedSourceException) { return EntryReaderTarget.Unavailable(failure.userMessage,entry) }
        catch(_: IllegalArgumentException) { return EntryReaderTarget.Unavailable("This saved location is unresolved. Your writing is retained.",entry) }
        catch(_: Exception) { return EntryReaderTarget.Unavailable("This entry could not be opened safely. Your writing is retained.",entry) }
    }
}
