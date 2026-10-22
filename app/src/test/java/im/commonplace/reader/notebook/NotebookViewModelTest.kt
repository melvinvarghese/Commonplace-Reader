package im.commonplace.reader.notebook

import androidx.lifecycle.ViewModelStore
import im.commonplace.reader.data.*
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotebookViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val owner=ViewModelStore()
    private lateinit var store: TestNotebookStore
    @Before fun setup() { Dispatchers.setMain(dispatcher); store=TestNotebookStore() }
    @After fun cleanup() { owner.clear(); Dispatchers.resetMain() }
    private fun model()=NotebookViewModel(store).also { owner.put("notebook",it) }
    private fun NotebookViewModel.body(text: String)=updateFields(NotebookFields(body=text))
    private suspend fun TestScope.new(model: NotebookViewModel) { model.newEntry(EntryKind.THOUGHT,null); runCurrent() }

    @Test fun draftDebounceAcknowledgesOnlyAt300ms()=runTest {
        val m=model(); new(m); m.body("latest")
        advanceTimeBy(299); runCurrent()
        assertEquals(NotebookSaveStatus.NOT_SAVED,m.state.value.editor!!.status)
        assertNull(store.drafts.value.single().fields.body)
        advanceTimeBy(1); runCurrent()
        assertEquals("latest",store.drafts.value.single().fields.body)
        assertEquals(NotebookSaveStatus.DRAFT_SAVED,m.state.value.editor!!.status)
        assertTrue(store.entries.value.isEmpty())
    }
    @Test fun savedLabelWaitsForCommit()=runTest {
        val m=model(); new(m); m.body("A"); store.commitGate=CompletableDeferred()
        m.save(); runCurrent()
        assertEquals(NotebookSaveStatus.SAVING_ENTRY,m.state.value.editor!!.status)
        assertTrue(store.entries.value.isEmpty()); assertNotEquals("Saved",m.state.value.notice)
        store.commitGate!!.complete(Unit); runCurrent()
        assertNull(m.state.value.editor); assertEquals("A",m.state.value.detail!!.body); assertEquals("Saved",m.state.value.notice)
    }
    @Test fun draftSavedIsNotEntrySaved()=runTest {
        val m=model(); new(m); m.body("A"); advanceTimeBy(300); runCurrent()
        assertEquals(NotebookSaveStatus.DRAFT_SAVED,m.state.value.editor!!.status)
        assertEquals("A",store.drafts.value.single().fields.body)
        assertNull(m.state.value.detail); assertTrue(store.entries.value.isEmpty())
    }
    @Test fun failedWriteKeepsInput()=runTest {
        val m=model(); new(m); store.failWrites=true; m.body("  ലോകം\n é  ")
        advanceTimeBy(300); runCurrent()
        assertEquals("  ലോകം\n é  ",m.state.value.editor!!.fields.body)
        assertEquals(NotebookSaveStatus.SAVE_FAILED,m.state.value.editor!!.status); assertTrue(m.state.value.editor!!.dirty)
    }
    @Test fun saveFreezesSubmittedFieldsAndRepeatedTapIsIgnored()=runTest {
        val m=model(); new(m); m.body("A"); store.commitGate=CompletableDeferred()
        m.save(); m.save(); m.body("B"); runCurrent()
        assertEquals("A",m.state.value.editor!!.fields.body)
        store.commitGate!!.complete(Unit); runCurrent()
        assertEquals(listOf("A"),store.entries.value.map { it.body }); assertEquals(1L,store.entries.value.single().revision)
    }
    @Test fun retryUsesSameCommitToken()=runTest {
        val m=model(); new(m); m.body("A"); store.failAfterCommit=true
        m.save(); runCurrent()
        val committed=store.entries.value.single()
        assertEquals(NotebookSaveStatus.SAVE_FAILED,m.state.value.editor!!.status)
        assertEquals("A",m.state.value.editor!!.fields.body)
        m.retrySave(); runCurrent()
        assertEquals(listOf(committed),store.entries.value); assertEquals(committed,m.state.value.detail)
    }
    @Test fun backgroundFlushUsesLatestSnapshot()=runTest {
        val m=model(); new(m); m.body("A"); advanceTimeBy(50); m.body("B")
        m.flushDraft(); runCurrent()
        assertEquals("B",store.drafts.value.single().fields.body)
        assertFalse(m.state.value.editor!!.dirty)
    }
    @Test fun staleCompletionCannotAcknowledgeNewerText()=runTest {
        val m=model(); new(m); store.writeGate=CompletableDeferred(); m.body("A")
        m.flushDraft(); runCurrent(); m.body("B")
        store.writeGate!!.complete(Unit); runCurrent()
        assertEquals("B",m.state.value.editor!!.fields.body); assertTrue(m.state.value.editor!!.dirty)
        m.flushDraft(); runCurrent(); assertEquals("B",store.drafts.value.single().fields.body)
        assertFalse(m.state.value.editor!!.dirty)
    }
    @Test fun conflictKeepsDraftAndShowsCurrentSavedVersion()=runTest {
        store.entries.value=listOf(store.entry("entry","old"))
        val m=model(); m.editEntry("entry"); runCurrent(); m.body("local")
        val newer=store.entry("entry","newer").copy(revision=9); store.entries.value=listOf(newer)
        m.save(); runCurrent()
        assertEquals(newer,m.state.value.editor!!.conflict)
        assertEquals("local",m.state.value.editor!!.fields.body)
        assertEquals("local",store.drafts.value.single().fields.body); assertEquals(newer,store.entries.value.single())
    }
    @Test fun leaveWhileUnacknowledgedOffersRetryStayOrDiscard()=runTest {
        val m=model(); new(m); m.body("A"); store.failWrites=true
        m.requestLeave(); runCurrent()
        assertTrue(m.state.value.leavePrompt); assertEquals("A",m.state.value.editor!!.fields.body)
        m.stayInEditor(); assertFalse(m.state.value.leavePrompt)
        store.failWrites=false; m.requestLeave(); runCurrent()
        assertNull(m.state.value.editor); assertEquals("A",store.drafts.value.single().fields.body)
    }
    @Test fun openingExistingDraftDoesNotOverwriteIt()=runTest {
        store.entries.value=listOf(store.entry("entry","saved"))
        val draft=store.beginEdit("entry") as BeginEditResult.Started
        store.writeDraft("entry",draft.draft.sessionId,0,NotebookFields(body="draft"),null)
        val m=model(); m.editEntry("entry"); runCurrent()
        assertNull(m.state.value.editor); assertEquals("draft",m.state.value.resumeOffer!!.fields.body)
        m.resumeDraft("entry"); runCurrent()
        assertEquals("draft",m.state.value.editor!!.fields.body)
    }
    @Test fun resumeLoadsImmutableSavedContextForEditor()=runTest {
        val saved=store.entry("entry","saved"); store.entries.value=listOf(saved)
        store.beginEdit("entry")
        val m=model(); m.resumeDraft("entry"); runCurrent()
        assertEquals(saved,m.state.value.detail)
    }
    @Test fun draftForAnotherEntryDoesNotCollide()=runTest {
        val m=model(); new(m); m.body("A"); m.requestLeave(); runCurrent()
        val a=store.drafts.value.single(); new(m); m.body("B"); m.save(); runCurrent()
        assertEquals(listOf(a),store.drafts.value); assertEquals(listOf("B"),store.entries.value.map { it.body })
    }
    @Test fun discardWaitsForGuardedDeleteBeforeLeaving()=runTest {
        val m=model(); new(m); m.body("A"); store.discardGate=CompletableDeferred()
        m.discardDraft(); runCurrent(); assertNotNull(m.state.value.editor)
        store.discardGate!!.complete(Unit); runCurrent(); assertNull(m.state.value.editor); assertTrue(store.drafts.value.isEmpty())
    }
    @Test fun failedDraftReadCannotStartBlankEditor()=runTest {
        val m=model(); store.failDraftReads=true; m.resumeDraft("unknown"); runCurrent()
        assertNull(m.state.value.editor); assertTrue(store.drafts.value.isEmpty()); assertNotNull(m.state.value.notice)
    }
}

internal class TestNotebookStore : NotebookStore {
    val entries=MutableStateFlow<List<EntryEntity>>(emptyList())
    val drafts=MutableStateFlow<List<NotebookDraftEntity>>(emptyList())
    val sources=MutableStateFlow<List<SourceEntity>>(emptyList())
    var writeGate: CompletableDeferred<Unit>?=null
    var commitGate: CompletableDeferred<Unit>?=null
    var discardGate: CompletableDeferred<Unit>?=null
    var failWrites=false
    var failAfterCommit=false
    var failDraftReads=false
    private var next=0
    fun entry(id: String, body: String)=EntryEntity(id,EntryKind.THOUGHT,null,null,null,body,null,null,null,null,1,1,0,null)
    override fun observeEntries(kind: EntryKind?)=entries.map { list -> list.filter { kind==null || it.kind==kind } }
    override fun observeDrafts(): Flow<List<NotebookDraftEntity>> = drafts
    override fun observeSources(): Flow<List<SourceEntity>> = sources
    override suspend fun getEntry(id: String)=entries.value.find { it.id==id }
    override suspend fun getDraft(targetId: String): NotebookDraftEntity? {
        if(failDraftReads) throw IOException("synthetic failure")
        return drafts.value.find { it.targetId==targetId }
    }
    override suspend fun beginNew(kind: EntryKind, sourceId: String?): NotebookDraftEntity {
        val d=NotebookDraftEntity("entry-${++next}",true,kind,sourceId,null,null,NotebookFields(),null,"session-${++next}",0,"token-${++next}",1)
        drafts.value += d; return d
    }
    override suspend fun beginEdit(entryId: String): BeginEditResult {
        getDraft(entryId)?.let { return BeginEditResult.ResumeRequired(it) }
        val e=getEntry(entryId) ?: return BeginEditResult.Unavailable("Missing")
        val d=NotebookDraftEntity(e.id,false,e.kind,e.sourceId,e.attributionTitle,e.attributionAuthors,NotebookFields(e.title,e.body,e.colorArgb),e.revision,"session-${++next}",0,"token-${++next}",1)
        drafts.value+=d; return BeginEditResult.Started(d)
    }
    private fun replace(d: NotebookDraftEntity) { drafts.value=drafts.value.map { if(it.targetId==d.targetId) d else it } }
    override suspend fun resumeDraft(targetId: String, expectedSessionId: String, expectedRevision: Long): DraftWriteResult {
        val d=getDraft(targetId) ?: return DraftWriteResult.Missing
        if(d.sessionId!=expectedSessionId || d.draftRevision!=expectedRevision) return DraftWriteResult.Conflict
        val next=d.copy(sessionId="session-${++next}",draftRevision=d.draftRevision+1,commitToken="token-${++next}")
        replace(next); return DraftWriteResult.Written(next)
    }
    override suspend fun writeDraft(targetId: String, sessionId: String, expectedRevision: Long, fields: NotebookFields, selectedSourceId: String?): DraftWriteResult {
        writeGate?.await(); if(failWrites) throw IOException("synthetic failure")
        val d=getDraft(targetId) ?: return DraftWriteResult.Missing
        if(d.sessionId!=sessionId || d.draftRevision!=expectedRevision) return DraftWriteResult.Conflict
        val updated=d.copy(fields=fields,sourceId=selectedSourceId,draftRevision=d.draftRevision+1,commitToken="token-${++next}")
        replace(updated); return DraftWriteResult.Written(updated)
    }
    override suspend fun discardDraft(targetId: String, sessionId: String, expectedRevision: Long): DiscardResult {
        discardGate?.await(); val d=getDraft(targetId) ?: return DiscardResult.Missing
        if(d.sessionId!=sessionId || d.draftRevision!=expectedRevision) return DiscardResult.Conflict
        drafts.value=drafts.value.filterNot { it.targetId==targetId }; return DiscardResult.Discarded
    }
    override suspend fun commit(request: NotebookCommitRequest): NotebookCommitResult {
        commitGate?.await()
        val current=getEntry(request.targetId)
        if(current?.lastNotebookCommitId==request.commitToken) return NotebookCommitResult.Saved(current)
        val d=getDraft(request.targetId) ?: return NotebookCommitResult.MissingDraft
        if(d.sessionId!=request.sessionId || d.draftRevision!=request.draftRevision || current?.revision!=d.baseEntryRevision)
            return NotebookCommitResult.Conflict(current)
        if(d.fields.body.isNullOrBlank()) return NotebookCommitResult.Invalid("Body required")
        val e=(current ?: entry(d.targetId,"unused")).copy(title=d.fields.title,body=d.fields.body,revision=(current?.revision ?: 0)+1,lastNotebookCommitId=request.commitToken)
        entries.value=entries.value.filterNot { it.id==e.id }+e; drafts.value=drafts.value.filterNot { it.targetId==e.id }
        if(failAfterCommit) { failAfterCommit=false; throw IOException("synthetic lost acknowledgment") }
        return NotebookCommitResult.Saved(e)
    }
}
