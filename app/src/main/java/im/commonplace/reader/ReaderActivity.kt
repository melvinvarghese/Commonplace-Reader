package im.commonplace.reader

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import im.commonplace.reader.data.EntryEntity
import im.commonplace.reader.reader.*
import im.commonplace.reader.ui.CommonplaceTheme
import im.commonplace.reader.ui.ReaderScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ReaderActivity : FragmentActivity() {
    private lateinit var readerModel: ReaderViewModel
    private var routeJob: Job?=null
    private var fromEntry by mutableStateOf(false)
    private var entryContext by mutableStateOf<EntryEntity?>(null)
    private var resolving by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        // The native Publication cannot be parceled; retain the model, rebuild one host.
        super.onCreate(null)
        val container=(application as CommonplaceApplication).container
        readerModel=ViewModelProvider(this,object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(ReaderViewModel::class.java))
                @Suppress("UNCHECKED_CAST") return ReaderViewModel(container.readerRepository,BuildConfig.PDF_ENGINE_ID.takeIf {it.isNotBlank()}) as T
            }
        })[ReaderViewModel::class.java]
        openIntent(intent,restoring=savedInstanceState!=null,restoreSource=savedInstanceState?.getString("consumedEntrySource"))
        val darkReading=container.readerPreferences.observeDarkReading()
        setContent {
            val dark by darkReading.collectAsStateWithLifecycle(initialValue=false)
            val state by readerModel.uiState.collectAsStateWithLifecycle()
            CommonplaceTheme(darkTheme=dark) {
                val entry=entryContext
                if(fromEntry && entry!=null && (state.phase==ReaderPhase.Error || state.phase==ReaderPhase.Unsupported)) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            TextButton(onClick={finish()},modifier=Modifier.heightIn(min=48.dp)) {Text("Back to entry")}
                            Text(state.message ?: "This saved location is unresolved. Your writing is retained.",style=MaterialTheme.typography.titleLarge)
                            entry.attributionTitle?.let {Text(it)}
                            entry.attributionAuthors?.let {Text(it)}
                            entry.quotation?.let {Text("Quotation");SelectionContainer {Text(it)}}
                            entry.body?.let {Text("Writing");SelectionContainer {Text(it)}}
                        }
                    }
                } else Box(Modifier.fillMaxSize()) {
                    ReaderScreen(this@ReaderActivity,readerModel,container.readerPreferences,dark,onLibrary={finish()},exitLabel=if(fromEntry) "Back to entry" else "Library")
                    if(resolving) Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            TextButton(onClick={finish()},modifier=Modifier.heightIn(min=48.dp)) {Text("Back to entry")}
                            CircularProgressIndicator();Text("Resolving entry")
                        }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);openIntent(intent)}
    private fun openIntent(intent: Intent,restoring: Boolean=false,restoreSource: String?=null) {
        routeJob?.cancel();fromEntry=intent.hasExtra("entryId");entryContext=null;resolving=false
        val container=(application as CommonplaceApplication).container
        if(fromEntry) {
            val id=intent.getStringExtra("entryId").orEmpty()
            resolving=true
            routeJob=lifecycleScope.launch {
                try {
                    entryContext=container.notebookRepository.getEntry(id)
                    if(restoring && readerModel.hasActiveRequest) return@launch
                    if(restoring && restoreSource!=null) readerModel.open(restoreSource)
                    else readerModel.openTarget(EntryReaderTargetResolver(container.notebookRepository,container.readerRepository) {verifySourceFile(filesDir,it)}.resolveEntryReaderTarget(id))
                } catch(cancelled: CancellationException) {throw cancelled}
                catch(_: Exception) {readerModel.openTarget(EntryReaderTarget.Unavailable("This entry could not be opened safely. Your writing is retained.",entryContext))}
                finally {resolving=false}
            }
        } else intent.getStringExtra("sourceId")?.let(readerModel::open) ?: finish()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        val state=readerModel.uiState.value
        if(fromEntry && state.pendingEntryJump==null && state.currentLocation!=null) state.source?.id?.let {outState.putString("consumedEntrySource",it)}
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {readerModel.flushProgress();super.onStop()}
}
