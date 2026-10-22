package im.commonplace.reader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM entries WHERE :kind IS NULL OR kind = :kind ORDER BY createdAt DESC, id ASC")
    fun observeEntries(kind: EntryKind?): Flow<List<EntryEntity>>
    @Query("SELECT * FROM notebook_drafts ORDER BY modifiedAt DESC, targetId ASC")
    fun observeDrafts(): Flow<List<NotebookDraftEntity>>
    @Query("SELECT * FROM entries WHERE id = :id") suspend fun entry(id: String): EntryEntity?
    @Query("SELECT * FROM notebook_drafts WHERE targetId = :targetId") suspend fun draft(targetId: String): NotebookDraftEntity?
    @Insert suspend fun insertDraft(draft: NotebookDraftEntity)
    @Update suspend fun updateDraft(draft: NotebookDraftEntity): Int
    @Insert suspend fun insertEntry(entry: EntryEntity)
    @Query("""UPDATE entries SET title=:title, body=:body, colorArgb=:color, modifiedAt=:modifiedAt,
        revision=revision+1, lastNotebookCommitId=:token WHERE id=:id AND revision=:expectedRevision""")
    suspend fun updateEntry(id: String, expectedRevision: Long, title: String?, body: String?, color: Int?, modifiedAt: Long, token: String): Int
    @Query("DELETE FROM notebook_drafts WHERE targetId=:targetId AND sessionId=:sessionId AND draftRevision=:revision")
    suspend fun deleteDraft(targetId: String, sessionId: String, revision: Long): Int
}
