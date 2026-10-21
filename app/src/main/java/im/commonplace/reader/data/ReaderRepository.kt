package im.commonplace.reader.data

import im.commonplace.reader.reader.Capture
import im.commonplace.reader.reader.SavedLocation
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class ReaderRepository(
    private val database: CommonplaceDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun observeSources() = database.readerDao().observeSources()
    fun observeEntries(sourceId: String) = database.readerDao().observeEntries(sourceId)
    suspend fun saveCapture(sourceId: String, capture: Capture, reflection: String?, colorArgb: Int?): String = ""
    suspend fun saveSourceAnnotation(sourceId: String, body: String): String = ""
    suspend fun addBookmark(sourceId: String, location: SavedLocation): String = ""
    suspend fun saveProgress(sourceId: String, location: SavedLocation) = Unit
    suspend fun getProgress(sourceId: String): SavedLocation? = null
    suspend fun saveDraft(draft: ReaderDraftEntity) = Unit
    suspend fun getDraft(sourceId: String): ReaderDraftEntity? = null
}
