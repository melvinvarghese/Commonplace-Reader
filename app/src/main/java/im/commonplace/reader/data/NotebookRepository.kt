package im.commonplace.reader.data

import androidx.room.withTransaction
import im.commonplace.reader.notebook.*
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotebookRepository(
    private val database: CommonplaceDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : NotebookStore {
    private val dao = database.notebookDao()
    private val sources = database.readerDao()
    override fun observeEntries(kind: EntryKind?) = dao.observeEntries(kind)
    override fun observeDrafts() = dao.observeDrafts()
    override fun observeSources() = sources.observeSources()
    override suspend fun getEntry(id: String) = withContext(io) { dao.entry(id) }
    override suspend fun getDraft(targetId: String) = withContext(io) { dao.draft(targetId) }

    override suspend fun beginNew(kind: EntryKind, sourceId: String?): NotebookDraftEntity = withContext(io) {
        database.withTransaction {
            require(kind != EntryKind.HIGHLIGHT) { "Highlights require a reader selection." }
            val source = if (kind == EntryKind.THOUGHT) {
                require(sourceId == null) { "A thought has no source." }; null
            } else requireNotNull(sourceId?.let { sources.source(it) }) { "Choose an existing book." }
            val draft = NotebookDraftEntity(nextId(),true,kind,sourceId,source?.title,source?.authors,
                NotebookFields(),null,nextId(),0,nextId(),clock())
            check(dao.entry(draft.targetId) == null) { "Entry identity already exists." }
            dao.insertDraft(draft)
            draft
        }
    }

    override suspend fun beginEdit(entryId: String): BeginEditResult = withContext(io) {
        database.withTransaction {
            val oldDraft = dao.draft(entryId)
            if (oldDraft != null) return@withTransaction BeginEditResult.ResumeRequired(oldDraft)
            val entry = dao.entry(entryId)
                ?: return@withTransaction BeginEditResult.Unavailable("This entry is no longer available.")
            if (!safeShape(entry)) return@withTransaction BeginEditResult.Unavailable(
                "This legacy entry cannot be safely edited. Its writing is retained.")
            val draft = NotebookDraftEntity(entry.id,false,entry.kind,entry.sourceId,entry.attributionTitle,
                entry.attributionAuthors,NotebookFields(entry.title,entry.body,entry.colorArgb),entry.revision,
                nextId(),0,nextId(),clock())
            dao.insertDraft(draft)
            BeginEditResult.Started(draft)
        }
    }

    override suspend fun resumeDraft(targetId: String, expectedSessionId: String, expectedRevision: Long): DraftWriteResult = withContext(io) {
        database.withTransaction {
            val draft = dao.draft(targetId) ?: return@withTransaction DraftWriteResult.Missing
            if (!owns(draft,expectedSessionId,expectedRevision)) return@withTransaction DraftWriteResult.Conflict
            val next = draft.copy(sessionId=nextId(),draftRevision=draft.draftRevision+1,commitToken=nextId(),modifiedAt=clock())
            check(dao.updateDraft(next) == 1)
            DraftWriteResult.Written(next)
        }
    }

    override suspend fun writeDraft(targetId: String, sessionId: String, expectedRevision: Long,
        fields: NotebookFields, selectedSourceId: String?): DraftWriteResult = withContext(io) {
        database.withTransaction {
            val draft = dao.draft(targetId) ?: return@withTransaction DraftWriteResult.Missing
            if (!owns(draft,sessionId,expectedRevision)) return@withTransaction DraftWriteResult.Conflict
            require(draft.kind == EntryKind.HIGHLIGHT || fields.colorArgb == null) { "Only highlights have a color." }
            require(draft.isNew || selectedSourceId == draft.sourceId) { "An entry's source is immutable." }
            require(draft.kind != EntryKind.THOUGHT || selectedSourceId == null) { "A thought has no source." }
            var next = draft.copy(fields=fields,draftRevision=draft.draftRevision+1,commitToken=nextId(),modifiedAt=clock())
            if (draft.isNew && selectedSourceId != draft.sourceId) {
                val source = requireNotNull(selectedSourceId?.let { sources.source(it) }) { "Choose an existing book." }
                next = next.copy(sourceId=source.id,sourceTitleSnapshot=source.title,sourceAuthorsSnapshot=source.authors)
            }
            // The read/check/update share one SQLite transaction; late writers never insert rows.
            check(dao.updateDraft(next) == 1)
            DraftWriteResult.Written(next)
        }
    }

    override suspend fun discardDraft(targetId: String, sessionId: String, expectedRevision: Long): DiscardResult = withContext(io) {
        database.withTransaction {
            val draft = dao.draft(targetId) ?: return@withTransaction DiscardResult.Missing
            if (!owns(draft,sessionId,expectedRevision)) return@withTransaction DiscardResult.Conflict
            check(dao.deleteDraft(targetId,sessionId,expectedRevision) == 1)
            DiscardResult.Discarded
        }
    }

    override suspend fun commit(request: NotebookCommitRequest): NotebookCommitResult = withContext(io) {
        database.withTransaction {
            val current = dao.entry(request.targetId)
            if (current != null && current.lastNotebookCommitId == request.commitToken)
                return@withTransaction NotebookCommitResult.Saved(current)
            val draft = dao.draft(request.targetId) ?: return@withTransaction if (current != null)
                NotebookCommitResult.Conflict(current) else NotebookCommitResult.MissingDraft
            if (!owns(draft,request.sessionId,request.draftRevision) || draft.commitToken != request.commitToken)
                return@withTransaction NotebookCommitResult.Conflict(current)
            if ((draft.isNew && current != null) || (!draft.isNew && (current == null || current.revision != draft.baseEntryRevision)))
                return@withTransaction NotebookCommitResult.Conflict(current)
            if (draft.kind != EntryKind.HIGHLIGHT && draft.fields.body.isNullOrBlank())
                return@withTransaction NotebookCommitResult.Invalid("Write a body before saving this entry.")
            if (draft.kind != EntryKind.HIGHLIGHT && draft.fields.colorArgb != null)
                return@withTransaction NotebookCommitResult.Invalid("Only highlights have a color.")
            if (current != null && (!safeShape(current) || current.kind != draft.kind || current.revision == Long.MAX_VALUE))
                return@withTransaction NotebookCommitResult.Invalid("This entry cannot be safely edited. Its writing is retained.")
            val now = clock()
            val saved = if (draft.isNew) {
                if (draft.kind == EntryKind.HIGHLIGHT)
                    return@withTransaction NotebookCommitResult.Invalid("Highlights require a reader selection.")
                val source = if (draft.kind == EntryKind.THOUGHT) {
                    if (draft.sourceId != null) return@withTransaction NotebookCommitResult.Invalid("A thought has no source.")
                    null
                } else sources.source(draft.sourceId ?: "")
                    ?: return@withTransaction NotebookCommitResult.Invalid("The book record is missing. Your draft is retained.")
                EntryEntity(draft.targetId,draft.kind,source?.id,null,draft.fields.title,draft.fields.body,null,
                    source?.title,source?.authors,null,now,now,1,request.commitToken).also { dao.insertEntry(it) }
            } else {
                val old = requireNotNull(current)
                check(dao.updateEntry(old.id,old.revision,draft.fields.title,draft.fields.body,draft.fields.colorArgb,now,request.commitToken) == 1)
                old.copy(title=draft.fields.title,body=draft.fields.body,colorArgb=draft.fields.colorArgb,
                    modifiedAt=now,revision=old.revision+1,lastNotebookCommitId=request.commitToken)
            }
            check(dao.deleteDraft(draft.targetId,draft.sessionId,draft.draftRevision) == 1)
            NotebookCommitResult.Saved(saved)
        }
    }

    private fun owns(draft: NotebookDraftEntity, sessionId: String, revision: Long) =
        draft.sessionId == sessionId && draft.draftRevision == revision && revision in 0 until Long.MAX_VALUE
    private fun nextId(): String = newId().also { require(UUID.fromString(it).toString() == it) }
    private fun safeShape(entry: EntryEntity): Boolean = when (entry.kind) {
        EntryKind.HIGHLIGHT -> !entry.quotation.isNullOrBlank() && entry.location != null && !entry.attributionTitle.isNullOrBlank()
        EntryKind.THOUGHT -> entry.sourceId == null && entry.location == null && entry.quotation == null &&
            entry.attributionTitle == null && entry.attributionAuthors == null && entry.colorArgb == null && !entry.body.isNullOrBlank()
        EntryKind.ANNOTATION, EntryKind.SOURCE_NOTE -> entry.location == null && entry.quotation == null &&
            !entry.attributionTitle.isNullOrBlank() && entry.colorArgb == null && !entry.body.isNullOrBlank()
    }
}
