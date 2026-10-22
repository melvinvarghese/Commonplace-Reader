package im.commonplace.reader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import im.commonplace.reader.data.*
import im.commonplace.reader.reader.DocumentFormat
import java.text.DateFormat
import java.util.Date

@Composable
internal fun NotebookDetailScreen(padding: PaddingValues, entry: EntryEntity, source: SourceEntity?, notice: String?, busy: Boolean,
    onBack: () -> Unit, onEdit: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        TextButton(onClick=onBack,enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) { Text("Back to notebook") }
        Text(entry.kind.label(),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        SelectionContainer { Text(entry.displayTitle(),style=MaterialTheme.typography.headlineLarge) }
        if(notice!=null) Text(notice,color=MaterialTheme.colorScheme.primary)
        entry.quotation?.let { quotation ->
            Text("Quotation",style=MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(quotation,style=MaterialTheme.typography.bodyLarge) }
            HorizontalDivider()
        }
        Text(if(entry.kind==EntryKind.HIGHLIGHT) "Reflection" else "Writing",style=MaterialTheme.typography.titleMedium)
        SelectionContainer { Text(entry.body ?: "No reflection yet.",style=MaterialTheme.typography.bodyLarge) }
        entry.attributionTitle?.let { Text(it,style=MaterialTheme.typography.titleMedium) }
        entry.attributionAuthors?.let { Text(it) }
        val dates=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT)
        Text("Created ${dates.format(Date(entry.createdAt))}\nModified ${dates.format(Date(entry.modifiedAt))}",style=MaterialTheme.typography.bodySmall)
        when {
            source?.format==DocumentFormat.PDF || entry.location?.format==DocumentFormat.PDF -> Text("PDF passages are experimental and unresolved. Your writing is retained.")
            source?.availability==SourceAvailability.MISSING -> Text("Document missing. Your writing is retained.")
            source==null && entry.kind!=EntryKind.THOUGHT -> Text("Book record missing. Your writing is retained.")
            source?.format==DocumentFormat.EPUB -> OutlinedButton(onClick=onOpen,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Open book") }
        }
        Button(onClick=onEdit,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("Edit") }
    }
}
