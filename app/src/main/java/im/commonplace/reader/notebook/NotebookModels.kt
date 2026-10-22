package im.commonplace.reader.notebook

import im.commonplace.reader.data.EntryEntity
import im.commonplace.reader.data.NotebookDraftEntity

data class NotebookFields(val title: String? = null, val body: String? = null, val colorArgb: Int? = null)

data class NotebookCommitRequest(val targetId: String, val sessionId: String, val draftRevision: Long, val commitToken: String)

sealed interface BeginEditResult {
    data class Started(val draft: NotebookDraftEntity) : BeginEditResult
    data class ResumeRequired(val draft: NotebookDraftEntity) : BeginEditResult
    data class Unavailable(val reason: String) : BeginEditResult
}
sealed interface DraftWriteResult {
    data class Written(val draft: NotebookDraftEntity) : DraftWriteResult
    data object Conflict : DraftWriteResult
    data object Missing : DraftWriteResult
}
sealed interface DiscardResult {
    data object Discarded : DiscardResult
    data object Conflict : DiscardResult
    data object Missing : DiscardResult
}
sealed interface NotebookCommitResult {
    data class Saved(val entry: EntryEntity) : NotebookCommitResult
    data class Conflict(val current: EntryEntity?) : NotebookCommitResult
    data class Invalid(val reason: String) : NotebookCommitResult
    data object MissingDraft : NotebookCommitResult
}
