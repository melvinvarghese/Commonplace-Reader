package im.commonplace.reader.notebook

import im.commonplace.reader.data.EntryEntity
import im.commonplace.reader.data.EntryKind
import im.commonplace.reader.data.NotebookDraftEntity
import im.commonplace.reader.data.SourceEntity

enum class NotebookLoad { LOADING, READY, ERROR }
enum class NotebookSaveStatus { NOT_SAVED, SAVING_DRAFT, DRAFT_SAVED, SAVING_ENTRY, SAVE_FAILED }
data class NotebookEditor(
    val draft: NotebookDraftEntity,
    val fields: NotebookFields = draft.fields,
    val selectedSourceId: String? = draft.sourceId,
    val status: NotebookSaveStatus = NotebookSaveStatus.DRAFT_SAVED,
    val dirty: Boolean = false,
    val error: String? = null,
    val conflict: EntryEntity? = null,
)
data class NotebookUiState(
    val load: NotebookLoad = NotebookLoad.LOADING,
    val entries: List<EntryEntity> = emptyList(),
    val drafts: List<NotebookDraftEntity> = emptyList(),
    val sources: List<SourceEntity> = emptyList(),
    val filter: EntryKind? = null,
    val detail: EntryEntity? = null,
    val editor: NotebookEditor? = null,
    val resumeOffer: NotebookDraftEntity? = null,
    val leavePrompt: Boolean = false,
    val busy: Boolean = false,
    val notice: String? = null,
)
