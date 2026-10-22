package im.commonplace.reader.notebook

import im.commonplace.reader.data.EntryEntity
import im.commonplace.reader.data.EntryKind
import im.commonplace.reader.data.NotebookDraftEntity
import im.commonplace.reader.data.SourceEntity
import kotlinx.coroutines.flow.Flow

interface NotebookStore {
    fun observeEntries(kind: EntryKind?): Flow<List<EntryEntity>>
    fun observeDrafts(): Flow<List<NotebookDraftEntity>>
    fun observeSources(): Flow<List<SourceEntity>>
    suspend fun getEntry(id: String): EntryEntity?
    suspend fun getDraft(targetId: String): NotebookDraftEntity?
    suspend fun beginNew(kind: EntryKind, sourceId: String?): NotebookDraftEntity
    suspend fun beginEdit(entryId: String): BeginEditResult
    suspend fun resumeDraft(targetId: String, expectedSessionId: String, expectedRevision: Long): DraftWriteResult
    suspend fun writeDraft(targetId: String, sessionId: String, expectedRevision: Long, fields: NotebookFields, selectedSourceId: String?): DraftWriteResult
    suspend fun discardDraft(targetId: String, sessionId: String, expectedRevision: Long): DiscardResult
    suspend fun commit(request: NotebookCommitRequest): NotebookCommitResult
}
