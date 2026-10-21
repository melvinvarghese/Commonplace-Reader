package im.commonplace.reader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderDao {
    @Insert suspend fun insertSource(source: SourceEntity)
    @Query("SELECT * FROM sources ORDER BY createdAt DESC, id") fun observeSources(): Flow<List<SourceEntity>>
    @Query("SELECT * FROM sources WHERE id = :id") suspend fun source(id: String): SourceEntity?
    @Query("SELECT * FROM entries WHERE sourceId = :sourceId ORDER BY createdAt, id") fun observeEntries(sourceId: String): Flow<List<EntryEntity>>
    @Insert suspend fun insertEntry(entry: EntryEntity)
    @Insert suspend fun insertBookmark(bookmark: BookmarkEntity)
    @Query("SELECT * FROM bookmarks WHERE sourceId = :sourceId ORDER BY createdAt, id") suspend fun bookmarks(sourceId: String): List<BookmarkEntity>
    @Upsert suspend fun upsertProgress(state: ReadingStateEntity)
    @Query("SELECT * FROM reading_state WHERE sourceId = :sourceId") suspend fun progress(sourceId: String): ReadingStateEntity?
    @Upsert suspend fun upsertDraft(draft: ReaderDraftEntity)
    @Query("SELECT * FROM reader_drafts WHERE sourceId = :sourceId") suspend fun draft(sourceId: String): ReaderDraftEntity?
    @Query("DELETE FROM reader_drafts WHERE sourceId = :sourceId") suspend fun deleteDraft(sourceId: String)
}
