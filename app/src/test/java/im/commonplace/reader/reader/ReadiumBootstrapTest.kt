package im.commonplace.reader.reader

import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import im.commonplace.reader.R
import im.commonplace.reader.data.SourceAvailability
import im.commonplace.reader.data.SourceEntity
import im.commonplace.reader.reader.epub.ReadiumHost
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Host initialization only. Shadow WebView does not prove native rendering or selection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = im.commonplace.reader.CommonplaceApplication::class)
class ReadiumBootstrapTest {
    @Test fun composeReaderAttachesSupportedNavigator() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<im.commonplace.reader.CommonplaceApplication>()
        val id = UUID.randomUUID().toString()
        val bytes = ReaderProofFixture.bytes()
        val relative = "imports/assets/$id.epub"
        val asset = File(context.filesDir, relative).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        val source = SourceEntity(id, "Compose bootstrap fixture", null, DocumentFormat.EPUB, "content://bootstrap/fixture", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, relative, SourceAvailability.AVAILABLE, 0, 0)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { context.container.database.readerDao().insertSource(source) }
        val intent = android.content.Intent(context, im.commonplace.reader.ReaderActivity::class.java).putExtra("sourceId", id)
        val controller = Robolectric.buildActivity(im.commonplace.reader.ReaderActivity::class.java, intent).setup().visible()
        val activity = controller.get()
        try {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (activity.supportFragmentManager.fragments.isEmpty() && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
                Thread.sleep(20)
            }
            assertEquals("Compose bridge must attach the supported native fragment", 1, activity.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().size)
        } finally {
            controller.pause().stop().destroy()
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { context.container.database.openHelper.writableDatabase.execSQL("DELETE FROM sources WHERE id = ?", arrayOf(id)) }
            asset.delete()
        }
    }

    @Test fun supportedNavigatorAttachesToRealFragmentContainer() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        val id = UUID.randomUUID().toString()
        val bytes = ReaderProofFixture.bytes()
        val relative = "imports/assets/$id.epub"
        val asset = File(activity.filesDir, relative).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        val source = SourceEntity(id, "Bootstrap fixture", null, DocumentFormat.EPUB, "content://bootstrap/fixture", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, relative, SourceAvailability.AVAILABLE, 0, 0)
        activity.setContentView(FragmentContainerView(activity).apply { this.id = R.id.readium_container })
        val events = mutableListOf<ReaderEvent>()
        val host = ReadiumHost(activity, source, null, events::add, {}, {})
        try {
            host.attach(R.id.readium_container)
            val deadline = System.nanoTime() + 10_000_000_000L
            while (activity.supportFragmentManager.fragments.isEmpty() && events.none { it is ReaderEvent.Failed } && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20)
            }
            assertTrue("Host failed before navigator attachment: $events", events.none { it is ReaderEvent.Failed })
            assertEquals(1, activity.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().size)
            assertNotNull(activity.supportFragmentManager.fragments.single().view)
        } finally { host.close(); controller.pause().stop().destroy(); asset.delete() }
    }
}
