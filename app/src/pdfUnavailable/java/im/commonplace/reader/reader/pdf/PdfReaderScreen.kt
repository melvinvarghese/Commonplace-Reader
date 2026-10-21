package im.commonplace.reader.reader.pdf

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import im.commonplace.reader.reader.ReaderUiState
import im.commonplace.reader.reader.ReaderViewModel

/** Build-only fallback: no second-candidate code or dependency integration. */
@Composable
fun PdfReaderScreen(activity: FragmentActivity, model: ReaderViewModel, state: ReaderUiState, onLibrary: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        TextButton(onClick = onLibrary, modifier = Modifier.heightIn(min = 48.dp)) { Text("Library") }
        Text(state.message ?: "This comparison's PDF reader is not implemented yet. Your file and writing are retained.")
        state.entries.forEach { entry -> entry.quotation?.let { Text(it) }; entry.body?.let { Text(it) } }
    }
}
