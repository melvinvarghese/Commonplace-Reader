package im.commonplace.reader.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import im.commonplace.reader.data.ReaderRepository
import im.commonplace.reader.data.SourceEntity
import im.commonplace.reader.imports.DocumentImporter
import im.commonplace.reader.imports.ImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LibraryUiState {
    data object Empty : LibraryUiState
    data object Loading : LibraryUiState
    data class Books(val sources: List<SourceEntity>) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}
class LibraryViewModel(private val repository: ReaderRepository, private val importer: DocumentImporter) : ViewModel() {
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Empty)
    val uiState = _uiState.asStateFlow()
    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult = _importResult.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()
    private val _selectedSource = MutableStateFlow<SourceEntity?>(null)
    val selectedSource = _selectedSource.asStateFlow()
    private val reconciliation = viewModelScope.launch {
        _uiState.value = LibraryUiState.Loading
        try {
            importer.reconcile()
            viewModelScope.launch {
                repository.observeSources().catch {
                    _uiState.value = LibraryUiState.Error("Your library could not be loaded. Restart to retry; your writing has not been deleted.")
                }.collect { sources ->
                    _uiState.value = if (sources.isEmpty()) LibraryUiState.Empty else LibraryUiState.Books(sources)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _uiState.value = LibraryUiState.Error("Storage could not be checked. Restart to retry; your writing has not been deleted.")
        }
    }
    fun importDocument(uri: Uri) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            try {
                // Wait for the startup storage check, not for the long-lived source collector.
                reconciliation.join()
                if (_uiState.value !is LibraryUiState.Error) _importResult.value = importer.importDocument(uri)
            } finally { _importing.value = false }
        }
    }
    fun openExisting(sourceId: String) {
        _selectedSource.value = (_uiState.value as? LibraryUiState.Books)?.sources?.firstOrNull { it.id == sourceId }
        dismissResult()
    }
    fun dismissResult() { _importResult.value = null }
    fun closeSource() { _selectedSource.value = null }
}
