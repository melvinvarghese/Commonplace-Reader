package im.commonplace.reader.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import im.commonplace.reader.CommonplaceApplication
import im.commonplace.reader.imports.DocumentImporter
import im.commonplace.reader.imports.ImportResult
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.commonplace.reader.ReaderActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment

@RunWith(AndroidJUnit4::class)
class ReadiumProofTest {
    @Test fun readerOwnsOneRealNavigator() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/reflowable.epub").use { it.readBytes() }
        val repository = (context as CommonplaceApplication).container.readerRepository
        val imported = runBlocking { DocumentImporter(context.contentResolver, context.filesDir, repository, openStream = { ByteArrayInputStream(bytes) }).importDocument(Uri.parse("content://proof/legal-epub")) }
        val sourceId = when (imported) { is ImportResult.Imported -> imported.sourceId; is ImportResult.Duplicate -> imported.sourceId; else -> error("Legal EPUB import failed: $imported") }
        ActivityScenario.launch<ReaderActivity>(Intent(context, ReaderActivity::class.java).putExtra("sourceId", sourceId)).use { scenario ->
            var count = 0
            val deadline = System.nanoTime() + 15_000_000_000L
            while (count == 0 && System.nanoTime() < deadline) {
                scenario.onActivity { count = it.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().size }
                if (count == 0) Thread.sleep(50)
            }
            assertEquals("Reader must own one supported native EPUB navigator", 1, count)
        }
    }
}
