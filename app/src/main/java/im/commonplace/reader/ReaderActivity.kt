package im.commonplace.reader

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import im.commonplace.reader.reader.ReaderViewModel
import im.commonplace.reader.ui.CommonplaceTheme
import im.commonplace.reader.ui.ReaderScreen

class ReaderActivity : FragmentActivity() {
    private lateinit var readerModel: ReaderViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        // Readium's Publication factory cannot be parceled. Restore exactly one fresh
        // navigator from the retained model / Room rather than an orphaned fragment.
        super.onCreate(null)
        val container = (application as CommonplaceApplication).container
        readerModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(ReaderViewModel::class.java))
                @Suppress("UNCHECKED_CAST") return ReaderViewModel(container.readerRepository) as T
            }
        })[ReaderViewModel::class.java]
        openIntent(intent)
        val darkReading = container.readerPreferences.observeDarkReading()
        setContent {
            val dark by darkReading.collectAsStateWithLifecycle(initialValue = false)
            CommonplaceTheme(darkTheme = dark) {
                ReaderScreen(this, readerModel, container.readerPreferences, dark, onLibrary = { finish() })
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); openIntent(intent) }
    private fun openIntent(intent: Intent) { intent.getStringExtra("sourceId")?.let(readerModel::open) ?: finish() }
    override fun onStop() { readerModel.flushProgress(); super.onStop() }
}
