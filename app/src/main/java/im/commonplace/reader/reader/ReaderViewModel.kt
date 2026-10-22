package im.commonplace.reader.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import im.commonplace.reader.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Storage boundary for deterministic scheduling; Room remains the production store. */
interface ReaderStore {
    suspend fun getSource(sourceId: String): SourceEntity?
    suspend fun getProgress(sourceId: String): SavedLocation?
    suspend fun getDraft(sourceId: String): ReaderDraftEntity?
    fun observeEntries(sourceId: String): Flow<List<EntryEntity>>
    suspend fun getBookmarks(sourceId: String): List<BookmarkEntity>
    suspend fun saveDraft(draft: ReaderDraftEntity)
    suspend fun saveProgress(sourceId: String, location: SavedLocation)
    suspend fun saveCapture(sourceId: String, capture: Capture, reflection: String?, colorArgb: Int?): String
    suspend fun addBookmark(sourceId: String, location: SavedLocation): String
    suspend fun saveSourceAnnotation(sourceId: String, body: String): String
}
enum class ReaderPhase { Opening, Ready, Unsupported, Error }
enum class CaptureStatus { Unsaved, Pending, Failed, Saved }
data class ReaderUiState(
    val phase: ReaderPhase = ReaderPhase.Opening,
    val source: SourceEntity? = null,
    val capabilities: ReaderCapabilities = ReaderCapabilities(CapabilityStatus.Unknown, CapabilityStatus.Unknown, CapabilityStatus.Unknown, CapabilityStatus.Unknown, CapabilityStatus.Unknown),
    val initialLocation: SavedLocation? = null,
    val currentLocation: SavedLocation? = null,
    val selection: Capture? = null,
    val reflection: String? = null,
    val colorArgb: Int? = null,
    val captureStatus: CaptureStatus = CaptureStatus.Unsaved,
    val draftSaved: Boolean = false,
    val highlights: List<EntryEntity> = emptyList(),
    val entries: List<EntryEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val message: String? = null,
    val readerSession: Long = 0,
    val pendingEntryJump: EntryJump? = null,
)
class ReaderViewModel(private val repository: ReaderStore, private val pdfEngineId: String? = null) : ViewModel() {
    private val state = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = state.asStateFlow()
    private var activeId: String? = null
    private var generation = 0L
    private var jumpSequence = 0L
    private var rejectedJump = false
    val hasActiveRequest: Boolean get() = activeId!=null
    private var openJob: Job? = null
    private var entriesJob: Job? = null
    private var progressJob: Job? = null
    private var draftJob: Job? = null
    private val writes = Mutex()
    private var draftRevision = 0L
    private val draftRevisions = mutableMapOf<String, Long>()
    private val pendingFlushes = mutableMapOf<String, Job>()
    private val pendingCaptures = mutableMapOf<String, Job>()

    fun open(sourceId: String) {
        if (activeId == sourceId && state.value.phase != ReaderPhase.Error) return
        openSource(sourceId,null)
    }
    private fun openSource(sourceId: String,jump: EntryJump?) {
        rejectedJump=false
        flushProgress()
        openJob?.cancel(); entriesJob?.cancel()
        activeId = sourceId
        val token = ++generation
        state.value = ReaderUiState(readerSession=token)
        openJob = viewModelScope.launch {
            try {
                pendingFlushes[sourceId]?.join()
                pendingCaptures[sourceId]?.join()
                val source = repository.getSource(sourceId) ?: error("missing source")
                val progress = repository.getProgress(sourceId)
                val draft = repository.getDraft(sourceId)
                val bookmarks = repository.getBookmarks(sourceId)
                if (token != generation) return@launch
                state.value = ReaderUiState(
                    source = source,
                    phase = when {
                        source.availability != SourceAvailability.AVAILABLE -> ReaderPhase.Error
                        source.format == DocumentFormat.PDF && pdfEngineId == null -> ReaderPhase.Unsupported
                        else -> ReaderPhase.Opening
                    },
                    initialLocation = jump?.location ?: progress, currentLocation = if(jump==null) progress else null,
                    readerSession = token, pendingEntryJump = jump,
                    selection = draft?.capture, reflection = draft?.reflection, colorArgb = draft?.colorArgb,
                    draftSaved = draft != null, bookmarks = bookmarks,
                    message = when {
                        source.availability != SourceAvailability.AVAILABLE -> "The document is missing. Your writing is retained."
                        source.format == DocumentFormat.PDF && pdfEngineId == null -> "PDF reading is not implemented yet. Your imported file is retained."
                        else -> null
                    },
                )
                entriesJob = viewModelScope.launch {
                    repository.observeEntries(sourceId).collect { rows ->
                        if (token == generation) state.update { it.copy(entries = rows, highlights = rows.filter { row ->
                            row.kind == EntryKind.HIGHLIGHT && row.location?.let(::compatible) == true
                        }) }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (token == generation) state.update { it.copy(phase = ReaderPhase.Error, message = "Unable to open this source. Your writing is retained.") } }
        }
    }
    fun openTarget(target: EntryReaderTarget) {
        rejectedJump=false
        if(target is EntryReaderTarget.Unavailable) {
            flushProgress();openJob?.cancel();entriesJob?.cancel();activeId=null
            state.value=ReaderUiState(phase=ReaderPhase.Error,message=target.reason,readerSession=++generation)
            return
        }
        val sourceId=when(target) { is EntryReaderTarget.Passage -> target.sourceId; is EntryReaderTarget.Book -> target.sourceId; else -> return }
        val jump=if(target is EntryReaderTarget.Passage) EntryJump(++jumpSequence,target.location,target.entryId) else null
        if(activeId==sourceId && state.value.source!=null && state.value.phase!=ReaderPhase.Error) {
            flushProgress()
            state.update { it.copy(pendingEntryJump=jump,currentLocation=if(jump==null) it.currentLocation else null,
                initialLocation=jump?.location ?: it.initialLocation,message=null) }
        } else openSource(sourceId,jump)
    }
    fun onEntryCommandAccepted(sourceId: String,requestId: Long,readerSession: Long) {
        if(sourceId!=activeId || readerSession!=generation || state.value.pendingEntryJump?.requestId!=requestId) return
        state.update { it.copy(pendingEntryJump=it.pendingEntryJump?.copy(commandAccepted=true)) }
    }
    fun onEntryNavigationResult(sourceId: String,requestId: Long,readerSession: Long,success: Boolean) {
        if(sourceId!=activeId || readerSession!=generation || state.value.pendingEntryJump?.requestId!=requestId) return
        rejectedJump=!success
        state.update { it.copy(pendingEntryJump=null,phase=if(success) it.phase else ReaderPhase.Error,
            message=if(success) null else "This saved location is unresolved. Your writing is retained.") }
    }
    fun onReaderEvent(sourceId: String, event: ReaderEvent, readerSession: Long? = null) {
        if (sourceId != activeId || event.sourceId != sourceId || rejectedJump || (readerSession!=null && readerSession!=generation)) return
        when (event) {
            is ReaderEvent.Opened -> state.update { it.copy(phase = if (event.capabilities.render is CapabilityStatus.Unavailable) ReaderPhase.Unsupported else ReaderPhase.Ready, capabilities = event.capabilities, message = (event.capabilities.render as? CapabilityStatus.Unavailable)?.reason ?: it.message) }
            is ReaderEvent.Failed -> state.update { it.copy(phase = ReaderPhase.Error, message = event.userMessage) }
            is ReaderEvent.PositionChanged -> {
                if(state.value.pendingEntryJump!=null || state.value.phase==ReaderPhase.Error) return
                if (!compatible(event.location)) { state.update { it.copy(message = "This saved location is unresolved.") }; return }
                state.update { it.copy(currentLocation = event.location) }
                progressJob?.cancel()
                val token = generation
                progressJob = viewModelScope.launch { delay(500); persistProgress(sourceId, event.location, token) }
            }
            is ReaderEvent.SelectionChanged -> {
                val capture = event.captureOrNull ?: return
                if (!compatible(capture.location) || capture.quotation.isBlank()) return
                val old = state.value
                if (old.selection != null && old.selection != capture && old.captureStatus != CaptureStatus.Saved) {
                    state.update { it.copy(message = "Keep the current passage before selecting another.") }; return
                }
                if (old.selection == capture) return
                state.update { it.copy(selection = capture, reflection = null, colorArgb = null, captureStatus = CaptureStatus.Unsaved, draftSaved = false, message = null) }
                scheduleDraft()
            }
        }
    }
    fun updateDraft(reflection: String?, colorArgb: Int?) {
        if (state.value.selection == null || state.value.captureStatus == CaptureStatus.Pending) return
        state.update { it.copy(reflection = reflection, colorArgb = colorArgb, captureStatus = CaptureStatus.Unsaved, draftSaved = false) }
        scheduleDraft()
    }
    private fun scheduleDraft() {
        draftJob?.cancel()
        val snapshot = state.value
        val id = snapshot.source?.id ?: return
        if (snapshot.selection == null) return
        val revision = ++draftRevision
        draftRevisions[id] = revision
        val token = generation
        draftJob = viewModelScope.launch { delay(300); persistDraft(id, snapshot, revision, token) }
    }
    fun saveSelection(reflection: String?, colorArgb: Int?) {
        val snapshot = state.value
        val id = snapshot.source?.id ?: return
        val capture = snapshot.selection ?: return
        if (snapshot.captureStatus == CaptureStatus.Pending || snapshot.captureStatus == CaptureStatus.Saved) return
        draftJob?.cancel()
        val revision = ++draftRevision
        draftRevisions[id] = revision
        val token = generation
        state.update { it.copy(reflection = reflection, colorArgb = colorArgb, captureStatus = CaptureStatus.Pending, message = null, draftSaved = false) }
        val job = viewModelScope.launch {
            try {
                writes.withLock {
                    repository.saveDraft(ReaderDraftEntity(id, capture, reflection, colorArgb, System.currentTimeMillis()))
                    repository.saveCapture(id, capture, reflection, colorArgb)
                }
                if (token == generation && revision == draftRevision) state.update { it.copy(captureStatus = CaptureStatus.Saved, draftSaved = false, message = "Saved in Commonplace") }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (token == generation) state.update { it.copy(captureStatus = CaptureStatus.Failed, message = "Not saved. Your passage and reflection are kept here; retry.") } }
        }
        track(pendingCaptures, id, job)
    }
    fun addBookmark() {
        val snapshot = state.value
        val id = snapshot.source?.id ?: return
        val location = snapshot.currentLocation ?: return
        val token = generation
        viewModelScope.launch {
            try {
                repository.addBookmark(id, location)
                val rows = repository.getBookmarks(id)
                if (token == generation) state.update { it.copy(bookmarks = rows, message = "Bookmark saved") }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (token == generation) state.update { it.copy(message = "Bookmark not saved. Retry.") } }
        }
    }
    fun saveAnnotation(body: String) {
        val id = state.value.source?.id ?: return
        if (body.isBlank()) return
        val token = generation
        viewModelScope.launch {
            try { repository.saveSourceAnnotation(id, body); if (token == generation) state.update { it.copy(message = "Annotation saved") } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (token == generation) state.update { it.copy(message = "Annotation not saved. Retry.") } }
        }
    }
    /** Called before navigation and ON_STOP; acknowledgments still require completed writes. */
    fun flushProgress() {
        progressJob?.cancel(); draftJob?.cancel()
        val snapshot = state.value
        val id = snapshot.source?.id ?: return
        val token = generation
        val revision = draftRevisions[id] ?: 0L
        val job = viewModelScope.launch {
            snapshot.currentLocation?.let { persistProgress(id, it, token) }
            if (snapshot.selection != null && snapshot.captureStatus != CaptureStatus.Saved && snapshot.captureStatus != CaptureStatus.Pending) persistDraft(id, snapshot, revision, token)
        }
        track(pendingFlushes, id, job)
    }
    private fun track(jobs: MutableMap<String, Job>, id: String, job: Job) {
        jobs[id] = job
        job.invokeOnCompletion { if (jobs[id] === job) jobs.remove(id) }
    }
    private suspend fun persistProgress(id: String, location: SavedLocation, token: Long) {
        try { repository.saveProgress(id, location) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (token == generation) state.update { it.copy(message = "Reading position not saved. Retry by leaving the reader again.") } }
    }
    private suspend fun persistDraft(id: String, snapshot: ReaderUiState, revision: Long, token: Long) {
        val capture = snapshot.selection ?: return
        try {
            writes.withLock {
                if ((draftRevisions[id] ?: 0L) != revision) return
                repository.saveDraft(ReaderDraftEntity(id, capture, snapshot.reflection, snapshot.colorArgb, System.currentTimeMillis()))
            }
            if (token == generation && revision == draftRevision) state.update { it.copy(draftSaved = true) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (token == generation) state.update { it.copy(draftSaved = false, message = "Draft not saved yet. Keep the reader open and retry.") } }
    }
    private fun compatible(location: SavedLocation): Boolean = try {
        location.validate()
        when (state.value.source?.format) {
            DocumentFormat.EPUB -> location.format == DocumentFormat.EPUB && location.engineId == "readium"
            DocumentFormat.PDF -> pdfEngineId != null && location.format == DocumentFormat.PDF && location.engineId == pdfEngineId
            null -> false
        }
    } catch (_: IllegalArgumentException) { false }
}
