package im.commonplace.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import im.commonplace.reader.data.*
import im.commonplace.reader.notebook.*

internal fun EntryKind.label() = when(this) {
    EntryKind.THOUGHT -> "Thought"
    EntryKind.HIGHLIGHT -> "Highlight"
    EntryKind.ANNOTATION -> "Annotation"
    EntryKind.SOURCE_NOTE -> "Source note"
}
internal fun EntryEntity.displayTitle() = title?.takeUnless { it.isBlank() } ?: kind.label()

@Composable
fun NotebookScreen(contentPadding: PaddingValues, onOpenEntryInReader: (String) -> Unit, model: NotebookViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle=LocalLifecycleOwner.current
    var showNew by rememberSaveable { mutableStateOf(false) }
    var pickerKind by rememberSaveable { mutableStateOf<EntryKind?>(null) }
    var changingSource by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreId=detailId
    val restoreFilter=savedFilter
    LaunchedEffect(Unit) {
        if(state.detail==null && restoreId!=null) model.openEntry(restoreId)
        model.setFilter(restoreFilter?.let { runCatching { EntryKind.valueOf(it) }.getOrNull() })
    }
    LaunchedEffect(state.detail?.id,state.filter) { detailId=state.detail?.id; savedFilter=state.filter?.name }
    DisposableEffect(lifecycle,model) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) model.flushDraft() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    BackHandler(state.editor!=null || state.detail!=null || state.resumeOffer!=null) {
        if(state.resumeOffer!=null) model.dismissResumeOffer() else model.requestLeave()
    }
    val editor=state.editor
    val detail=state.detail
    when {
        editor!=null -> NotebookEditorScreen(contentPadding,editor,
            state.entries.find { it.id==editor.draft.targetId } ?: detail,state.busy,
            model::updateFields,model::save,model::flushDraft,model::requestLeave,
            onDiscard={confirmDiscard=true},onReload=model::reloadSavedEntry,
            onChangeSource={ changingSource=true; pickerKind=editor.draft.kind })
        detail!=null -> NotebookDetailScreen(contentPadding,detail,state.sources.find { it.id==detail.sourceId },
            state.notice,state.busy,onBack=model::requestLeave,onEdit={model.editEntry(detail.id)},
            onOpen={onOpenEntryInReader(detail.id)})
        else -> LazyColumn(
            modifier=Modifier.fillMaxSize().padding(contentPadding).testTag("notebook-list"),
            contentPadding=PaddingValues(24.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp),
        ) {
            item { Text("Your commonplace",style=MaterialTheme.typography.headlineLarge) }
            item { Text("Passages you keep. Writing you make.",style=MaterialTheme.typography.bodyLarge) }
            item { Button(onClick={showNew=true},enabled=!state.busy && state.load==NotebookLoad.READY,
                modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("New entry") } }
            item {
                val filters=listOf(null to "All",EntryKind.THOUGHT to "Thoughts",EntryKind.HIGHLIGHT to "Highlights",
                    EntryKind.ANNOTATION to "Annotations",EntryKind.SOURCE_NOTE to "Source notes")
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    filters.forEach { (kind,label) -> FilterChip(selected=state.filter==kind,onClick={model.setFilter(kind)},
                        label={Text(label)},modifier=Modifier.heightIn(min=48.dp)) }
                }
            }
            state.notice?.let { item { Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
            when(state.load) {
                NotebookLoad.LOADING -> item { CircularProgressIndicator(Modifier.testTag("notebook-loading")) }
                NotebookLoad.ERROR -> item { Button(onClick=model::retryLoad,modifier=Modifier.heightIn(min=48.dp)) { Text("Retry notebook") } }
                NotebookLoad.READY -> {
                    if(state.drafts.isNotEmpty()) {
                        item { Text("Drafts",style=MaterialTheme.typography.titleLarge) }
                        items(state.drafts,key={"draft-${it.targetId}"}) { draft ->
                            Surface(tonalElevation=1.dp,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(draft.fields.title?.takeUnless { it.isBlank() } ?: draft.kind.label(),style=MaterialTheme.typography.titleMedium)
                                    draft.sourceTitleSnapshot?.let { Text(it) }
                                    Text(draft.fields.body?.take(160) ?: "Unfinished writing",maxLines=3)
                                    TextButton(onClick={model.editEntry(draft.targetId)},enabled=!state.busy,
                                        modifier=Modifier.heightIn(min=48.dp)) { Text("Review draft") }
                                }
                            }
                        }
                    }
                    item { Text("Entries",style=MaterialTheme.typography.titleLarge) }
                    if(state.entries.isEmpty()) item {
                        Text(if(state.filter==null) "No entries yet" else "No entries match this type",style=MaterialTheme.typography.headlineSmall)
                        Text("Write a thought, or keep a passage while reading.",modifier=Modifier.padding(top=8.dp))
                    }
                    items(state.entries,key={it.id}) { entry ->
                        Surface(onClick={model.openEntry(entry.id)},shape=MaterialTheme.shapes.large,tonalElevation=1.dp,
                            modifier=Modifier.fillMaxWidth().testTag("entry-${entry.id}")) {
                            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(entry.kind.label(),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                                Text(entry.displayTitle(),style=MaterialTheme.typography.titleLarge)
                                Text((entry.quotation ?: entry.body).orEmpty().take(240),maxLines=4)
                                entry.attributionTitle?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        }
    }
    if(showNew) AlertDialog(onDismissRequest={showNew=false},title={Text("New entry")},text={
        Column {
            listOf(EntryKind.THOUGHT,EntryKind.ANNOTATION,EntryKind.SOURCE_NOTE).forEach { kind ->
                TextButton(onClick={ showNew=false; if(kind==EntryKind.THOUGHT) model.newEntry(kind,null) else {changingSource=false;pickerKind=kind} },
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("new-${kind.name}")) { Text(kind.label()) }
            }
        }
    },confirmButton={TextButton(onClick={showNew=false}) {Text("Cancel")}})
    pickerKind?.let { kind -> AlertDialog(onDismissRequest={pickerKind=null},title={Text("Choose a book")},text={
        LazyColumn(Modifier.heightIn(max=400.dp)) {
            if(state.sources.isEmpty()) item { Text("Import a book in Library first.") }
            items(state.sources,key={it.id}) { source ->
                TextButton(onClick={pickerKind=null;if(changingSource) model.selectNewSource(source.id) else model.newEntry(kind,source.id)},
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("source-${source.id}")) {
                    Column { Text(source.title); if(source.availability==SourceAvailability.MISSING) Text("Document missing; you can still write") }
                }
            }
        }
    },confirmButton={TextButton(onClick={pickerKind=null}) {Text("Cancel")}}) }
    state.resumeOffer?.let { draft -> AlertDialog(onDismissRequest=model::dismissResumeOffer,title={Text("Recoverable draft")},
        text={Text("A separate draft is kept on this device. Resume it, or discard only the draft; saved writing is not deleted.")},
        confirmButton={TextButton(onClick={model.resumeDraft(draft.targetId)},enabled=!state.busy) {Text("Resume draft")}},
        dismissButton={Column {
            TextButton(onClick=model::discardDraft,enabled=!state.busy) {Text("Discard draft")}
            TextButton(onClick=model::dismissResumeOffer) {Text(if(draft.isNew) "Not now" else "View saved entry")}
        }}) }
    if(state.leavePrompt) AlertDialog(onDismissRequest=model::stayInEditor,title={Text("Draft not confirmed")},
        text={Text("Your latest input has not been confirmed on this device. Retry before leaving, stay here, or explicitly discard it.")},
        confirmButton={TextButton(onClick=model::requestLeave,enabled=!state.busy) {Text("Retry and leave")}},
        dismissButton={Column {
            TextButton(onClick=model::stayInEditor) {Text("Stay")}
            TextButton(onClick=model::discardDraft,enabled=!state.busy) {Text("Discard changes")}
        }})
    if(confirmDiscard) AlertDialog(onDismissRequest={confirmDiscard=false},title={Text("Discard this draft?")},
        text={Text("This removes only your unfinished draft, not a saved entry.")},
        confirmButton={TextButton(onClick={confirmDiscard=false;model.discardDraft()}) {Text("Discard draft")}},
        dismissButton={TextButton(onClick={confirmDiscard=false}) {Text("Keep writing")}})
}
