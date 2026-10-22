package im.commonplace.reader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import im.commonplace.reader.data.EntryEntity
import im.commonplace.reader.data.EntryKind
import im.commonplace.reader.notebook.*
import kotlinx.coroutines.Job

@Composable
internal fun NotebookEditorScreen(padding: PaddingValues, editor: NotebookEditor, saved: EntryEntity?, busy: Boolean,
    onFields: (NotebookFields) -> Unit, onSave: () -> Unit, onFlush: () -> Job, onBack: () -> Unit,
    onDiscard: () -> Unit, onReload: () -> Unit, onChangeSource: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal=24.dp)) {
        TextButton(onClick=onBack,enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) { Text("Back") }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(editor.draft.kind.label(),style=MaterialTheme.typography.headlineSmall)
            Text(when(editor.status) {
                NotebookSaveStatus.NOT_SAVED -> "Not saved yet"
                NotebookSaveStatus.SAVING_DRAFT -> "Saving draft"
                NotebookSaveStatus.DRAFT_SAVED -> "Draft saved on this device"
                NotebookSaveStatus.SAVING_ENTRY -> "Saving entry"
                NotebookSaveStatus.SAVE_FAILED -> "Save failed"
            },color=MaterialTheme.colorScheme.primary)
            editor.draft.sourceTitleSnapshot?.let { Text(it,style=MaterialTheme.typography.titleMedium) }
            editor.draft.sourceAuthorsSnapshot?.let { Text(it) }
            if(editor.draft.isNew && editor.draft.kind!=EntryKind.THOUGHT)
                TextButton(onClick=onChangeSource,enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) {Text("Change book")}
            if(editor.draft.kind==EntryKind.HIGHLIGHT) saved?.quotation?.let { quotation ->
                Text("Original quotation",style=MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(quotation,style=MaterialTheme.typography.bodyLarge) }
            }
            OutlinedTextField(value=editor.fields.title.orEmpty(),onValueChange={onFields(editor.fields.copy(title=it))},
                label={Text("Title (optional)")},enabled=!busy,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=editor.fields.body.orEmpty(),onValueChange={onFields(editor.fields.copy(body=it))},
                label={Text(if(editor.draft.kind==EntryKind.HIGHLIGHT) "Reflection (optional)" else "Body")},
                enabled=!busy,minLines=6,modifier=Modifier.fillMaxWidth())
            if(editor.draft.kind==EntryKind.HIGHLIGHT) {
                Text("Highlight color",style=MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("Default" to null,"Yellow" to 0xFFFFFF00.toInt(),"Green" to 0xFF90EE90.toInt(),"Blue" to 0xFFADD8E6.toInt()).forEach { (name,color) ->
                        FilterChip(selected=editor.fields.colorArgb==color,onClick={onFields(editor.fields.copy(colorArgb=color))},
                            label={Text(name)},enabled=!busy,modifier=Modifier.heightIn(min=48.dp))
                    }
                }
            }
            editor.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            editor.conflict?.let { current ->
                HorizontalDivider()
                Text("Current saved version",style=MaterialTheme.typography.titleLarge)
                SelectionContainer { Text(current.body.orEmpty()) }
                OutlinedButton(onClick=onReload,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Discard draft and reload saved entry")}
            }
            if(editor.status==NotebookSaveStatus.SAVE_FAILED) TextButton(onClick={onFlush()},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) { Text("Retry draft") }
            TextButton(onClick=onDiscard,enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) {Text("Discard draft")}
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick=onSave,enabled=!busy,modifier=Modifier.fillMaxWidth().padding(vertical=8.dp).heightIn(min=56.dp)) {
            Text(if(editor.status==NotebookSaveStatus.SAVE_FAILED) "Retry Save" else "Save entry")
        }
    }
}
