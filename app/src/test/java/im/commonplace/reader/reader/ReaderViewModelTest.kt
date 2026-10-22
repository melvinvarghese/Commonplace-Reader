package im.commonplace.reader.reader

import androidx.lifecycle.ViewModelStore
import im.commonplace.reader.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val store = ViewModelStore()
    private val persistence = MemoryReaderStore()
    private lateinit var model: ReaderViewModel
    private val a = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val b = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    private val location = SavedLocation(1, DocumentFormat.EPUB, "readium", """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"progression":0.4},"text":{"highlight":"same phrase"},"unknown":"réflexion"}""")
    private val capture get() = Capture("same phrase", location)
    private val available = ReaderCapabilities(CapabilityStatus.Available, CapabilityStatus.Available, CapabilityStatus.Available, CapabilityStatus.Available, CapabilityStatus.Available)
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        persistence.sources[a] = source(a)
        persistence.sources[b] = source(b)
        model = ReaderViewModel(persistence)
        store.put("reader", model)
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun source(id: String) = SourceEntity(id, "Original book", null, DocumentFormat.EPUB, "content://fixture", "a".repeat(64), "imports/assets/$id.epub", SourceAvailability.AVAILABLE, 1, 1)
    private fun open(id: String = a) { model.open(id); scheduler.runCurrent(); model.onReaderEvent(id, ReaderEvent.Opened(id, available)) }
    private fun select() { model.onReaderEvent(a, ReaderEvent.SelectionChanged(a, capture)) }

    @Test fun configuredPdfCandidateOpensAndNeverPromotesForeignCoordinates() {
        persistence.sources[a] = source(a).copy(format = DocumentFormat.PDF)
        val engine = im.commonplace.reader.BuildConfig.PDF_ENGINE_ID
        model = ReaderViewModel(persistence, engine.takeIf { it.isNotBlank() })
        store.put("configured-pdf", model)
        model.open(a); scheduler.runCurrent()
        assertEquals(ReaderPhase.Opening, model.uiState.value.phase)
        val own = SavedLocation(1, DocumentFormat.PDF, engine, """{"pageIndex":0,"pageBox":[0,0,612,792],"rotationDegrees":0,"coordinateSpace":"unrotated-pdf-points"}""")
        model.onReaderEvent(a, ReaderEvent.PositionChanged(a, own))
        val other = if (engine == "nucleus-composepdf") "androidx-pdf" else "nucleus-composepdf"
        model.onReaderEvent(a, ReaderEvent.PositionChanged(a, own.copy(engineId = other)))
        model.onReaderEvent(a, ReaderEvent.SelectionChanged(a, Capture("foreign", own.copy(engineId = other))))
        assertEquals(own, model.uiState.value.currentLocation)
        assertNull(model.uiState.value.selection)
    }

    @Test fun pdfCandidateOpensWithoutPromotingAnotherEnginesLocations() {
        persistence.sources[a] = source(a).copy(format = DocumentFormat.PDF)
        model = ReaderViewModel(persistence, pdfEngineId = "androidx-pdf")
        store.put("pdf", model)
        model.open(a); scheduler.runCurrent()
        assertEquals(ReaderPhase.Opening, model.uiState.value.phase)
        val pdf = SavedLocation(1, DocumentFormat.PDF, "androidx-pdf", """{"pageIndex":0,"pageBox":[0,0,612,792],"rotationDegrees":0,"coordinateSpace":"unrotated-pdf-points"}""")
        model.onReaderEvent(a, ReaderEvent.PositionChanged(a, pdf))
        assertEquals(pdf, model.uiState.value.currentLocation)
        model.onReaderEvent(a, ReaderEvent.PositionChanged(a, pdf.copy(engineId = "nucleus-composepdf")))
        assertEquals(pdf, model.uiState.value.currentLocation)
        model.onReaderEvent(a, ReaderEvent.PositionChanged(a, location))
        assertEquals(pdf, model.uiState.value.currentLocation)
        model.onReaderEvent(a, ReaderEvent.SelectionChanged(a, Capture("wrong engine", pdf.copy(engineId = "nucleus-composepdf"))))
        assertNull(model.uiState.value.selection)
    }
    @Test fun savedAcknowledgementWaitsForCommitAndClearsDraftOnlyOnSuccess() {
        open(); select(); model.updateDraft("réflexion", 42)
        scheduler.advanceTimeBy(300); scheduler.runCurrent()
        assertNotNull(persistence.drafts[a])
        persistence.commit = CompletableDeferred()
        model.saveSelection("réflexion", 42); scheduler.runCurrent()
        assertEquals(CaptureStatus.Pending, model.uiState.value.captureStatus)
        assertNotNull(persistence.drafts[a]); assertTrue(persistence.entries.value.isEmpty())
        persistence.commit.complete(Unit); scheduler.runCurrent()
        assertEquals(CaptureStatus.Saved, model.uiState.value.captureStatus)
        assertEquals("réflexion", persistence.entries.value.single().body)
        assertNull(persistence.drafts[a])
    }
    @Test fun failedCaptureKeepsQuotationAndReflectionForRetry() {
        open(); select(); persistence.fail = true
        model.saveSelection("keep this reflection", 42); scheduler.runCurrent()
        assertEquals(CaptureStatus.Failed, model.uiState.value.captureStatus)
        assertEquals(capture, model.uiState.value.selection)
        assertEquals("keep this reflection", model.uiState.value.reflection)
        persistence.fail = false
        model.saveSelection(model.uiState.value.reflection, 42); scheduler.runCurrent()
        assertEquals(CaptureStatus.Saved, model.uiState.value.captureStatus)
        assertEquals("keep this reflection", persistence.entries.value.single().body)
    }
    @Test fun cancellationIsNotSavedOrReportedAsOrdinaryFailure() {
        open(); select(); persistence.commit = CompletableDeferred()
        model.saveSelection("unfinished", null); scheduler.runCurrent()
        store.clear(); scheduler.runCurrent()
        assertTrue(persistence.cancelled)
        assertTrue(persistence.entries.value.isEmpty())
        assertNotEquals(CaptureStatus.Saved, model.uiState.value.captureStatus)
        assertNotEquals(CaptureStatus.Failed, model.uiState.value.captureStatus)
    }
    @Test fun staleEventsAndCompletionsCannotCrossSourceSwitch() {
        open(); select(); persistence.commit = CompletableDeferred()
        model.saveSelection("old reflection", null); scheduler.runCurrent()
        open(b)
        model.onReaderEvent(a, ReaderEvent.SelectionChanged(a, capture))
        model.onReaderEvent(b, ReaderEvent.Failed(a, "wrong event identity"))
        persistence.commit.complete(Unit); scheduler.runCurrent()
        assertEquals(b, model.uiState.value.source?.id)
        assertNull(model.uiState.value.selection)
        assertEquals(ReaderPhase.Ready, model.uiState.value.phase)
        assertNotEquals(CaptureStatus.Saved, model.uiState.value.captureStatus)
    }
    @Test fun progressCoalescesAt500msAndBoundaryFlushIsImmediate() {
        open()
        model.onReaderEvent(a, ReaderEvent.PositionChanged(a, location))
        scheduler.advanceTimeBy(499); scheduler.runCurrent(); assertNull(persistence.progress[a])
        val next = location.copy(payloadJson = """{"href":"next.xhtml","type":"application/xhtml+xml"}""")
        model.onReaderEvent(a, ReaderEvent.PositionChanged(a, next))
        scheduler.advanceTimeBy(499); scheduler.runCurrent(); assertNull(persistence.progress[a])
        scheduler.advanceTimeBy(1); scheduler.runCurrent(); assertEquals(next, persistence.progress[a])
        model.onReaderEvent(a, ReaderEvent.PositionChanged(a, location)); model.flushProgress(); scheduler.runCurrent()
        assertEquals(location, persistence.progress[a])
        model.onReaderEvent(a, ReaderEvent.PositionChanged(a, next)); open(b)
        assertEquals(next, persistence.progress[a])
    }
    @Test fun acknowledgedDraftRecoversWith300msDebounceAndBackgroundFlush() {
        open(); select(); model.updateDraft("first", 17)
        scheduler.advanceTimeBy(299); scheduler.runCurrent(); assertNull(persistence.drafts[a])
        assertFalse(model.uiState.value.draftSaved)
        scheduler.advanceTimeBy(1); scheduler.runCurrent()
        assertTrue(model.uiState.value.draftSaved)
        model.updateDraft("latest", 18); model.flushProgress(); scheduler.runCurrent()
        assertEquals("latest", persistence.drafts[a]?.reflection)
        val restored = ReaderViewModel(persistence); store.put("restored", restored)
        restored.open(a); scheduler.runCurrent()
        assertEquals(capture, restored.uiState.value.selection)
        assertEquals("latest", restored.uiState.value.reflection)
        assertTrue(restored.uiState.value.draftSaved)
    }
    @Test fun reopenKeepsExactLocatorAndOnlyMatchingEngineHighlights() {
        persistence.progress[a] = location
        persistence.entries.value = listOf(entry("correct", location), entry("other", location.copy(engineId = "another-engine")))
        open()
        assertEquals(location, model.uiState.value.initialLocation)
        assertEquals(listOf("correct"), model.uiState.value.highlights.map { it.id })
        assertEquals(location.payloadJson, model.uiState.value.highlights.single().location?.payloadJson)
    }
    @Test fun bookmarkAndAnnotationDoNotOverwriteProgressOrQuotation() {
        open(); model.onReaderEvent(a, ReaderEvent.PositionChanged(a, location)); select()
        model.addBookmark(); model.saveAnnotation("source annotation"); scheduler.runCurrent()
        assertEquals(location, persistence.bookmarks.single().location)
        assertEquals("source annotation", persistence.entries.value.single().body)
        assertEquals(capture, model.uiState.value.selection)
        assertNull(persistence.entries.value.single().location)
    }
    @Test fun lateBackgroundFlushCannotResurrectDraftAfterCaptureCommitsAndSourceSwitches() {
        open(); select(); model.updateDraft("save me", null)
        model.onReaderEvent(a, ReaderEvent.PositionChanged(a, location))
        persistence.progressCommit = CompletableDeferred()
        model.flushProgress(); scheduler.runCurrent()
        model.saveSelection("save me", null); scheduler.runCurrent()
        assertEquals(CaptureStatus.Saved, model.uiState.value.captureStatus)
        open(b)
        persistence.progressCommit.complete(Unit); scheduler.runCurrent()
        assertNull(persistence.drafts[a]); assertEquals(1, persistence.entries.value.size)
    }
    @Test fun fastSourceRoundTripWaitsForAcknowledgedBoundaryDraftInsteadOfLosingIt() {
        open(); select(); model.updateDraft("in-flight draft", null)
        persistence.draftCommit = CompletableDeferred()
        open(b); open(a)
        persistence.draftCommit.complete(Unit); scheduler.runCurrent()
        assertEquals(a, model.uiState.value.source?.id)
        assertEquals("in-flight draft", model.uiState.value.reflection)
        assertEquals(capture, model.uiState.value.selection)
    }
    private fun entry(id: String, loc: SavedLocation?) = EntryEntity(id, if (loc == null) EntryKind.ANNOTATION else EntryKind.HIGHLIGHT, a, loc, null, null, if (loc == null) null else "same phrase", "Original book", null, 42, 1, 1)
    @Test fun sameSourceNewEntryRequestIsNotIgnored() {
        open();model.openTarget(EntryReaderTarget.Passage("first",a,location));scheduler.runCurrent()
        val first=requireNotNull(model.uiState.value.pendingEntryJump)
        val next=location.copy(payloadJson="""{"href":"next.xhtml","type":"application/xhtml+xml"}""")
        model.openTarget(EntryReaderTarget.Passage("second",a,next));scheduler.runCurrent()
        val second=requireNotNull(model.uiState.value.pendingEntryJump)
        assertNotEquals(first.requestId,second.requestId);assertEquals(next,second.location)
        model.onEntryNavigationResult(a,first.requestId,model.uiState.value.readerSession,true)
        assertEquals(second,model.uiState.value.pendingEntryJump)
    }
    @Test fun entryLocationBeatsLastProgressWithoutSynthesizingProgress() {
        val last=location.copy(payloadJson="""{"href":"last.xhtml","type":"application/xhtml+xml"}""")
        persistence.progress[a]=last
        model.openTarget(EntryReaderTarget.Passage("entry",a,location));scheduler.runCurrent()
        assertEquals(location,model.uiState.value.initialLocation);assertNull(model.uiState.value.currentLocation)
        model.flushProgress();scheduler.advanceTimeBy(1000);scheduler.runCurrent()
        assertEquals(last,persistence.progress[a]);assertNotNull(model.uiState.value.pendingEntryJump)
    }
    @Test fun oldSourceEventCannotConsumeNewJump() {
        open();val oldSession=model.uiState.value.readerSession;open(b)
        model.openTarget(EntryReaderTarget.Passage("entry",a,location));scheduler.runCurrent()
        val current=model.uiState.value;val jump=requireNotNull(current.pendingEntryJump)
        assertNotEquals(oldSession,current.readerSession)
        model.onReaderEvent(a,ReaderEvent.PositionChanged(a,location),oldSession)
        model.onEntryNavigationResult(a,jump.requestId,oldSession,true)
        assertNull(model.uiState.value.currentLocation);assertEquals(jump,model.uiState.value.pendingEntryJump)
    }
    @Test fun consumedRequestDoesNotReappearDuringNormalProgress() {
        model.openTarget(EntryReaderTarget.Passage("entry",a,location));scheduler.runCurrent()
        val ui=model.uiState.value;val jump=requireNotNull(ui.pendingEntryJump)
        model.onEntryNavigationResult(a,jump.requestId,ui.readerSession,true)
        val next=location.copy(payloadJson="""{"href":"next.xhtml","type":"application/xhtml+xml"}""")
        model.onReaderEvent(a,ReaderEvent.PositionChanged(a,next),ui.readerSession)
        model.open(a);scheduler.runCurrent()
        assertNull(model.uiState.value.pendingEntryJump);assertEquals(next,model.uiState.value.currentLocation)
    }
    @Test fun nativePositionAfterJumpUpdatesNormalProgress() {
        model.openTarget(EntryReaderTarget.Passage("entry",a,location));scheduler.runCurrent()
        val ui=model.uiState.value;val jump=requireNotNull(ui.pendingEntryJump)
        model.onReaderEvent(a,ReaderEvent.PositionChanged(a,location),ui.readerSession)
        scheduler.advanceTimeBy(500);scheduler.runCurrent();assertNull(persistence.progress[a])
        model.onEntryNavigationResult(a,jump.requestId,ui.readerSession,true)
        model.onReaderEvent(a,ReaderEvent.PositionChanged(a,location),ui.readerSession)
        scheduler.advanceTimeBy(500);scheduler.runCurrent();assertEquals(location,persistence.progress[a])
    }
    @Test fun rejectedNativeJumpDoesNotClaimSuccessOrWriteProgress() {
        model.openTarget(EntryReaderTarget.Passage("entry",a,location));scheduler.runCurrent()
        val ui=model.uiState.value;val jump=requireNotNull(ui.pendingEntryJump)
        model.onEntryNavigationResult(a,jump.requestId,ui.readerSession,false)
        assertEquals(ReaderPhase.Error,model.uiState.value.phase);assertTrue(model.uiState.value.message!!.contains("unresolved"))
        model.onReaderEvent(a,ReaderEvent.Opened(a,available),ui.readerSession)
        assertEquals(ReaderPhase.Error,model.uiState.value.phase)
        model.flushProgress();scheduler.runCurrent();assertNull(persistence.progress[a])
    }
    private inner class MemoryReaderStore : ReaderStore {
        val sources = mutableMapOf<String, SourceEntity>()
        val progress = mutableMapOf<String, SavedLocation>()
        val drafts = mutableMapOf<String, ReaderDraftEntity>()
        val entries = MutableStateFlow(emptyList<EntryEntity>())
        val bookmarks = mutableListOf<BookmarkEntity>()
        var commit = CompletableDeferred(Unit)
        var draftCommit = CompletableDeferred(Unit)
        var progressCommit = CompletableDeferred(Unit)
        var fail = false
        var cancelled = false
        override suspend fun getSource(sourceId: String) = sources[sourceId]
        override suspend fun getProgress(sourceId: String) = progress[sourceId]
        override suspend fun getDraft(sourceId: String) = drafts[sourceId]
        override fun observeEntries(sourceId: String) = entries.map { rows -> rows.filter { it.sourceId == sourceId } }
        override suspend fun getBookmarks(sourceId: String) = bookmarks.filter { it.sourceId == sourceId }
        override suspend fun saveDraft(draft: ReaderDraftEntity) { draftCommit.await(); drafts[draft.sourceId] = draft }
        override suspend fun saveProgress(sourceId: String, location: SavedLocation) { progressCommit.await(); progress[sourceId] = location }
        override suspend fun saveCapture(sourceId: String, capture: Capture, reflection: String?, colorArgb: Int?): String {
            try { commit.await() } catch (e: CancellationException) { cancelled = true; throw e }
            if (fail) error("disk full")
            entries.value += entry("saved-${entries.value.size}", capture.location).copy(sourceId = sourceId, body = reflection, colorArgb = colorArgb)
            drafts.remove(sourceId)
            return entries.value.last().id
        }
        override suspend fun addBookmark(sourceId: String, location: SavedLocation): String {
            bookmarks += BookmarkEntity("bookmark", sourceId, location, 1); return "bookmark"
        }
        override suspend fun saveSourceAnnotation(sourceId: String, body: String): String {
            entries.value += entry("annotation", null).copy(sourceId = sourceId, body = body); return "annotation"
        }
    }
}
