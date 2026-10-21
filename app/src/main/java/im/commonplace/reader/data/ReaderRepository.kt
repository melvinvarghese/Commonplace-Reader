package im.commonplace.reader.data

import androidx.room.withTransaction
import im.commonplace.reader.reader.ReaderStore
import im.commonplace.reader.reader.Capture
import im.commonplace.reader.reader.SavedLocation
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReaderRepository(
    private val database: CommonplaceDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ReaderStore {
    private val dao = database.readerDao()

    suspend fun registerImportedSource(source: SourceEntity): Unit = withContext(io) {
        require(UUID.fromString(source.id).toString() == source.id)
        require(source.title.isNotBlank())
        require(source.checksum.matches(Regex("[0-9a-f]{64}")))
        require(source.assetPath.matches(Regex("imports/assets/[0-9a-f-]{36}\\.(pdf|epub)")))
        require(source.availability == SourceAvailability.AVAILABLE)
        dao.insertSource(source)
    }

    suspend fun sourceByChecksum(checksum: String): SourceEntity? = withContext(io) { dao.sourceByChecksum(checksum) }
    suspend fun sources(): List<SourceEntity> = withContext(io) { dao.sources() }
    suspend fun markMissing(sourceId: String): Unit = withContext(io) {
        dao.setAvailability(sourceId, SourceAvailability.MISSING, clock())
    }

    override suspend fun getSource(sourceId: String): SourceEntity? = withContext(io) { dao.source(sourceId) }
    override suspend fun getBookmarks(sourceId: String): List<BookmarkEntity> = withContext(io) { dao.bookmarks(sourceId) }
    fun observeSources() = dao.observeSources()
    override fun observeEntries(sourceId: String) = dao.observeEntries(sourceId)

    override suspend fun saveCapture(
        sourceId: String,
        capture: Capture,
        reflection: String?,
        colorArgb: Int?,
    ): String = withContext(io) {
        database.withTransaction {
            require(capture.quotation.isNotBlank()) { "A capture needs a quotation" }
            val source = requireSource(sourceId)
            validateLocation(source, capture.location)
            val id = nextId()
            val now = clock()
            dao.insertEntry(EntryEntity(id, EntryKind.HIGHLIGHT, sourceId, capture.location,
                null, reflection, capture.quotation, source.title, source.authors, colorArgb, now, now))
            dao.deleteDraft(sourceId)
            id
        }
    }

    override suspend fun saveSourceAnnotation(sourceId: String, body: String): String = withContext(io) {
        database.withTransaction {
            require(body.isNotBlank()) { "An annotation needs a body" }
            val source = requireSource(sourceId)
            val id = nextId()
            val now = clock()
            dao.insertEntry(EntryEntity(id, EntryKind.ANNOTATION, sourceId, null,
                null, body, null, source.title, source.authors, null, now, now))
            id
        }
    }

    override suspend fun addBookmark(sourceId: String, location: SavedLocation): String = withContext(io) {
        database.withTransaction {
            validateLocation(requireSource(sourceId), location)
            val id = nextId()
            dao.insertBookmark(BookmarkEntity(id, sourceId, location, clock()))
            id
        }
    }

    override suspend fun saveProgress(sourceId: String, location: SavedLocation): Unit = withContext(io) {
        database.withTransaction {
            validateLocation(requireSource(sourceId), location)
            dao.upsertProgress(ReadingStateEntity(sourceId, location, clock()))
        }
    }

    override suspend fun getProgress(sourceId: String): SavedLocation? = withContext(io) {
        dao.progress(sourceId)?.location
    }

    override suspend fun saveDraft(draft: ReaderDraftEntity): Unit = withContext(io) {
        database.withTransaction {
            require(draft.capture.quotation.isNotBlank()) { "A capture draft needs a quotation" }
            validateLocation(requireSource(draft.sourceId), draft.capture.location)
            dao.upsertDraft(draft)
        }
    }

    override suspend fun getDraft(sourceId: String): ReaderDraftEntity? = withContext(io) {
        dao.draft(sourceId)
    }

    private suspend fun requireSource(sourceId: String): SourceEntity {
        val source = requireNotNull(dao.source(sourceId)) { "Source does not exist" }
        require(source.title.isNotBlank()) { "Source attribution is required" }
        return source
    }

    private fun validateLocation(source: SourceEntity, location: SavedLocation) {
        require(source.format == location.format) { "Location format does not match source" }
        location.validate()
    }

    private fun nextId(): String = newId().also { require(UUID.fromString(it).toString() == it) }
}
