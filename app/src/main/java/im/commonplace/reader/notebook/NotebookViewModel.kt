package im.commonplace.reader.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import im.commonplace.reader.data.EntryKind
import im.commonplace.reader.data.NotebookDraftEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotebookViewModel(private val store: NotebookStore) : ViewModel() {
    private val mutable=MutableStateFlow(NotebookUiState())
    val state: StateFlow<NotebookUiState> = mutable.asStateFlow()
    private val filter=MutableStateFlow<EntryKind?>(null)
    private val operations=Mutex()
    private var observations: Job?=null
    private var debounce: Job?=null
    private var generation=0L
    private var submitted: NotebookCommitRequest?=null

    init { retryLoad() }
    fun setFilter(kind: EntryKind?) { filter.value=kind; mutable.update { it.copy(filter=kind) } }
    fun retryLoad() {
        observations?.cancel()
        mutable.update { it.copy(load=NotebookLoad.LOADING) }
        observations=viewModelScope.launch {
            combine(filter.flatMapLatest { store.observeEntries(it) },store.observeDrafts(),store.observeSources()) { entries,drafts,sources ->
                Triple(entries,drafts,sources)
            }.catch { error ->
                if(error is CancellationException) throw error
                mutable.update { it.copy(load=NotebookLoad.ERROR,notice="Could not load the notebook. Retry to load your writing.") }
            }.collect { (entries,drafts,sources) ->
                mutable.update { it.copy(load=NotebookLoad.READY,entries=entries,drafts=drafts,sources=sources) }
            }
        }
    }

    fun openEntry(id: String) = action {
        if(state.value.editor!=null) return@action
        val entry=store.getEntry(id)
        val draft=if(entry!=null) store.getDraft(id) else null
        mutable.update { it.copy(detail=entry,resumeOffer=draft,
            notice=if(entry==null) "This entry is no longer available." else null) }
    }
    fun newEntry(kind: EntryKind, sourceId: String?) = action {
        if(state.value.editor==null) startEditor(store.beginNew(kind,sourceId))
    }
    fun editEntry(id: String) = action {
        if(state.value.editor!=null) return@action
        when(val result=store.beginEdit(id)) {
            is BeginEditResult.Started -> startEditor(result.draft)
            is BeginEditResult.ResumeRequired -> mutable.update { it.copy(resumeOffer=result.draft) }
            is BeginEditResult.Unavailable -> mutable.update { it.copy(notice=result.reason) }
        }
    }
    fun resumeDraft(targetId: String) = action {
        if(state.value.editor!=null) return@action
        val draft=store.getDraft(targetId)
        if(draft==null) {
            val saved=store.getEntry(targetId)
            mutable.update { it.copy(detail=saved,resumeOffer=null,notice=if(saved!=null) "This entry was already saved." else "This draft is no longer available.") }
            return@action
        }
        when(val result=store.resumeDraft(targetId,draft.sessionId,draft.draftRevision)) {
            is DraftWriteResult.Written -> startEditor(result.draft)
            else -> mutable.update { it.copy(notice="The draft changed. Reload it before editing.") }
        }
    }
    private suspend fun startEditor(draft: NotebookDraftEntity) {
        val saved=if(draft.isNew) null else store.getEntry(draft.targetId)
        generation++; submitted=null
        mutable.update { it.copy(editor=NotebookEditor(draft),detail=saved,resumeOffer=null,notice=null,leavePrompt=false) }
    }
    fun updateFields(fields: NotebookFields) {
        if(state.value.busy) return
        val editor=state.value.editor ?: return
        if(editor.fields==fields) return
        submitted=null
        mutable.update { it.copy(editor=editor.copy(fields=fields,dirty=fields!=editor.draft.fields || editor.selectedSourceId!=editor.draft.sourceId,
            status=NotebookSaveStatus.NOT_SAVED,error=null)) }
        scheduleDraft()
    }
    fun selectNewSource(sourceId: String) {
        if(state.value.busy) return
        val editor=state.value.editor ?: return
        if(!editor.draft.isNew || editor.draft.kind==EntryKind.THOUGHT || editor.selectedSourceId==sourceId) return
        submitted=null
        mutable.update { it.copy(editor=editor.copy(selectedSourceId=sourceId,dirty=true,status=NotebookSaveStatus.NOT_SAVED,error=null)) }
        scheduleDraft()
    }
    private fun scheduleDraft() {
        debounce?.cancel()
        debounce=viewModelScope.launch { delay(300); flushDraft() }
    }
    fun flushDraft(): Job {
        debounce?.cancel(); debounce=null
        return viewModelScope.launch { operations.withLock { persistCurrent() } }
    }
    private suspend fun persistCurrent(savingEntry: Boolean=false): Boolean {
        val editor=state.value.editor ?: return true
        if(!editor.dirty) return true
        val requestGeneration=generation
        mutable.update { it.copy(editor=it.editor?.copy(status=if(savingEntry) NotebookSaveStatus.SAVING_ENTRY else NotebookSaveStatus.SAVING_DRAFT)) }
        try {
            val result=store.writeDraft(editor.draft.targetId,editor.draft.sessionId,editor.draft.draftRevision,editor.fields,editor.selectedSourceId)
            if(requestGeneration!=generation) return false
            when(result) {
                is DraftWriteResult.Written -> mutable.update { ui ->
                    val active=ui.editor ?: return@update ui
                    val dirty=active.fields!=editor.fields || active.selectedSourceId!=editor.selectedSourceId
                    ui.copy(editor=active.copy(draft=result.draft,dirty=dirty,error=null,status=when {
                        savingEntry -> NotebookSaveStatus.SAVING_ENTRY
                        dirty -> NotebookSaveStatus.NOT_SAVED
                        else -> NotebookSaveStatus.DRAFT_SAVED
                    }))
                }
                else -> {
                    val current=store.getEntry(editor.draft.targetId)
                    mutable.update { it.copy(editor=it.editor?.copy(status=NotebookSaveStatus.SAVE_FAILED,
                        error="The draft changed or was removed. Your input is still here.",conflict=current)) }
                    return false
                }
            }
            return state.value.editor?.dirty==false
        } catch(error: Exception) {
            if(error is CancellationException) throw error
            if(requestGeneration==generation) mutable.update { it.copy(editor=it.editor?.copy(status=NotebookSaveStatus.SAVE_FAILED,
                error="Could not save this draft. Your input is still here; retry before leaving.")) }
            return false
        }
    }
    fun save() {
        val editor=state.value.editor ?: return
        if(state.value.busy) return
        debounce?.cancel(); debounce=null
        mutable.update { it.copy(busy=true,leavePrompt=false,editor=editor.copy(status=NotebookSaveStatus.SAVING_ENTRY,error=null)) }
        viewModelScope.launch {
            operations.withLock {
                try {
                    if(submitted==null) {
                        if(!persistCurrent(savingEntry=true)) return@withLock
                        val draft=state.value.editor?.draft ?: return@withLock
                        submitted=NotebookCommitRequest(draft.targetId,draft.sessionId,draft.draftRevision,draft.commitToken)
                    }
                    when(val result=store.commit(requireNotNull(submitted))) {
                        is NotebookCommitResult.Saved -> {
                            generation++; submitted=null
                            mutable.update { it.copy(editor=null,detail=result.entry,resumeOffer=null,notice="Saved",leavePrompt=false) }
                        }
                        is NotebookCommitResult.Conflict -> {
                            submitted=null
                            mutable.update { it.copy(editor=it.editor?.copy(status=NotebookSaveStatus.SAVE_FAILED,conflict=result.current,
                                error="The saved entry changed. Your draft is retained; review the saved version before reloading.")) }
                        }
                        is NotebookCommitResult.Invalid -> { submitted=null; failEditor(result.reason) }
                        NotebookCommitResult.MissingDraft -> { submitted=null; failEditor("This draft is no longer available. Your input is still here.") }
                    }
                } catch(error: Exception) {
                    if(error is CancellationException) throw error
                    failEditor("Could not confirm the save. Your input is still here. Retry Save to check the same submission.")
                } finally { mutable.update { it.copy(busy=false) } }
            }
        }
    }
    fun retrySave() = save()
    private fun failEditor(message: String) { mutable.update { it.copy(editor=it.editor?.copy(status=NotebookSaveStatus.SAVE_FAILED,error=message)) } }
    fun discardDraft() = discard(reload=false)
    fun reloadSavedEntry() = discard(reload=true)
    private fun discard(reload: Boolean) = action {
        debounce?.cancel(); debounce=null
        val draft=state.value.editor?.draft ?: state.value.resumeOffer ?: return@action
        when(store.discardDraft(draft.targetId,draft.sessionId,draft.draftRevision)) {
            DiscardResult.Discarded, DiscardResult.Missing -> {
                val detail=if(reload) store.getEntry(draft.targetId) else state.value.detail
                generation++; submitted=null
                mutable.update { it.copy(editor=null,resumeOffer=null,detail=detail,leavePrompt=false,notice="Draft discarded") }
            }
            DiscardResult.Conflict -> mutable.update { it.copy(notice="The draft changed. Reload it before discarding.") }
        }
    }
    fun requestLeave() = action {
        if(state.value.editor==null) { mutable.update { it.copy(detail=null,resumeOffer=null,notice=null) }; return@action }
        debounce?.cancel(); debounce=null
        if(persistCurrent()) {
            generation++; submitted=null
            mutable.update { it.copy(editor=null,resumeOffer=null,leavePrompt=false,notice=null) }
        } else mutable.update { it.copy(leavePrompt=true) }
    }
    fun stayInEditor() { mutable.update { it.copy(leavePrompt=false) } }
    fun dismissResumeOffer() = action {
        val offered=state.value.resumeOffer
        val saved=if(offered?.isNew==false) store.getEntry(offered.targetId) else null
        mutable.update { it.copy(resumeOffer=null,detail=saved ?: it.detail) }
    }
    private fun action(block: suspend () -> Unit) {
        if(state.value.busy) return
        mutable.update { it.copy(busy=true) }
        viewModelScope.launch {
            operations.withLock {
                try { block() }
                catch(error: Exception) {
                    if(error is CancellationException) throw error
                    mutable.update { it.copy(notice="Could not complete that action. Your writing is retained; retry.") }
                } finally { mutable.update { it.copy(busy=false) } }
            }
        }
    }
}
