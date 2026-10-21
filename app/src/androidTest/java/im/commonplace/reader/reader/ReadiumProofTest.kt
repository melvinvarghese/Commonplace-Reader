package im.commonplace.reader.reader

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import im.commonplace.reader.CommonplaceApplication
import im.commonplace.reader.ReaderActivity
import im.commonplace.reader.data.EntryKind
import im.commonplace.reader.data.SourceEntity
import im.commonplace.reader.imports.DocumentImporter
import im.commonplace.reader.imports.ImportResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReadiumProofTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val repository get() = (context as CommonplaceApplication).container.readerRepository

    // Thread.sleep alone freezes a test-clock-owned recomposer even while Android stays idle.
    private fun navigator(scenario: ActivityScenario<ReaderActivity>): EpubNavigatorFragment =
        Companion.navigator(scenario) { compose.waitForIdle() }
    @Test fun readerOwnsOneRealNavigator() = withBook { source, scenario ->
        val old = navigator(scenario)
        val retiredPublication = nativePublication(old)
        assertNotNull(retiredPublication.get(org.readium.r2.shared.util.Url("EPUB/one.xhtml")!!))
        scenario.recreate()
        val reopened = navigator(scenario)
        assertNotSame(old, reopened)
        await { retiredPublication.get(org.readium.r2.shared.util.Url("EPUB/one.xhtml")!!) == null }
        scenario.onActivity { assertEquals(1, it.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().size); assertFalse(old.isAdded); assertNull(old.view) }
        // A preference recomposition must keep the current native session, not reopen it.
        compose.onNodeWithText("Dark reading").performScrollTo().performClick()
        assertSame(reopened, navigator(scenario))
        compose.onNodeWithText("Dark reading").performScrollTo().performClick()
        assertNotNull(repositorySource(source.id))
    }

    @Test fun exactNativeSelectionCaptureBookmarkReopenDecorationAndSearch() = withBook { source, scenario ->
        val native = navigator(scenario)
        waitForWeb(scenario, "one.xhtml")
        assertTrue(org.json.JSONTokener(script(scenario, "one.xhtml", "document.body.textContent")).nextValue().toString().contains(ReaderProofFixture.MULTILINGUAL))
        compose.onNodeWithText("Search in book").performScrollTo().performClick()
        compose.onNodeWithTag("reader-search-query").performTextInput(ReaderProofFixture.MULTILINGUAL)
        compose.onNodeWithText("Find").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("reader-search-result").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onAllNodesWithTag("reader-search-result").onFirst().performClick()
        waitForWeb(scenario, "one.xhtml")
        val selection = selectSecond(scenario, native)
        compose.onNodeWithText("Keep selected passage").performScrollTo().performClick()
        compose.onNodeWithTag("reader-reflection").performScrollTo().performTextInput(ReaderProofFixture.REFLECTION)
        compose.onNodeWithTag("reader-save").performScrollTo().performClick()
        await { runBlocking { repository.observeEntries(source.id).first().any { it.kind == EntryKind.HIGHLIGHT && it.body == ReaderProofFixture.REFLECTION } } }
        val entry = runBlocking { repository.observeEntries(source.id).first().single { it.body == ReaderProofFixture.REFLECTION } }
        assertEquals(ReaderProofFixture.QUOTATION, entry.quotation)
        assertEquals(selection.toJSON().toString(), entry.location!!.payloadJson)
        assertTrue(selection.text.before.orEmpty().contains("Second context"))
        go(scenario, native, Locator.fromJSON(JSONObject("""{"href":"EPUB/two.xhtml","type":"application/xhtml+xml"}"""))!!)
        waitForWeb(scenario, "two.xhtml")
        compose.onNodeWithText("Bookmark").performScrollTo().performClick()
        await { runBlocking { repository.getBookmarks(source.id).isNotEmpty() } }
        scenario.moveToState(Lifecycle.State.CREATED)
        await { runBlocking { repository.getProgress(source.id)?.payloadJson?.contains("two.xhtml") == true } }
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.recreate()
        val restored = navigator(scenario)
        await { onMain { restored.currentLocator.value.href.toString().contains("two.xhtml") } }
        compose.onNodeWithText("Saved items").performScrollTo().performClick()
        compose.onAllNodesWithTag("reader-open-capture").onFirst().performScrollTo().performClick()
        waitForWeb(scenario, "one.xhtml")
        await { script(scenario, "one.xhtml", "Array.from(document.querySelectorAll('.commonplace-proof-highlight')).some(function(e){var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0 && r.height>0 && r.right>0 && r.bottom>0 && r.left<innerWidth && r.top<innerHeight && Number(s.opacity)>0 && s.backgroundColor!=='rgba(0, 0, 0, 0)';})") == "true" }
        assertEquals(selection.toJSON().toString(), runBlocking { repository.observeEntries(source.id).first().single { it.id == entry.id }.location!!.payloadJson })
        val savedBookmark = runBlocking { repository.getBookmarks(source.id).single() }
        compose.onNodeWithText("Saved items").performScrollTo().performClick()
        compose.onAllNodesWithTag("reader-open-bookmark").onFirst().performScrollTo().performClick()
        waitForWeb(scenario, "two.xhtml")
        assertEquals(savedBookmark.location.payloadJson, runBlocking { repository.getBookmarks(source.id).single().location.payloadJson })
        compose.onNodeWithText("Saved items").performScrollTo().performClick()
        compose.onNodeWithTag("reader-annotation").performScrollTo().performTextInput("Synthetic source annotation")
        compose.onNodeWithText("Save annotation").performScrollTo().performClick()
        await { runBlocking { repository.observeEntries(source.id).first().any { it.kind == EntryKind.ANNOTATION && it.body == "Synthetic source annotation" && it.location == null } } }
    }

    @Test fun sourceSwitchClosesPriorNavigatorAndIntegrityMismatchDoesNotApplyCoordinates() = withBook { first, scenario ->
        val old = navigator(scenario)
        val retiredPublication = nativePublication(old)
        val second = runBlocking { ReaderProofFixture.import(context, ReaderProofFixture.bytes("http://127.0.0.1:9")) }
        scenario.onActivity { it.startActivity(Intent(context, ReaderActivity::class.java).putExtra("sourceId", second.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        val switched = awaitNavigatorForSource(scenario, old)
        scenario.onActivity { assertFalse(old.isAdded); assertNull(old.view); assertEquals(1, it.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().size) }
        assertNotSame(old, switched)
        await { retiredPublication.get(org.readium.r2.shared.util.Url("EPUB/one.xhtml")!!) == null }
        val asset = File(context.filesDir, second.assetPath)
        val original = asset.readBytes()
        try {
            asset.appendText("changed bytes")
            scenario.onActivity { it.startActivity(Intent(context, ReaderActivity::class.java).putExtra("sourceId", first.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            navigator(scenario)
            scenario.onActivity { it.startActivity(Intent(context, ReaderActivity::class.java).putExtra("sourceId", second.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Document bytes changed. Saved locations are unresolved; your writing is retained.").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            scenario.onActivity { assertTrue(it.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().isEmpty()) }
        } finally { asset.writeBytes(original) }
    }

    @Test fun hostilePublicationMakesZeroRequestsToWorkingControlledEndpointWhileLocalReadingWorks() {
        val authority = Uri.parse("content://${InstrumentationRegistry.getInstrumentation().context.packageName}.network-probe")
        val started = context.contentResolver.call(authority, "start", null, null)!!
        assertTrue(started.getBoolean("controlPassed")); assertTrue(started.getBoolean("listening"))
        val endpoint = "http://127.0.0.1:${started.getInt("port")}"
        val source = runBlocking { ReaderProofFixture.import(context, ReaderProofFixture.bytes(endpoint)) }
        try {
            launch(source).use { scenario ->
                navigator(scenario); waitForWeb(scenario, "one.xhtml")
                assertTrue(org.json.JSONTokener(script(scenario, "one.xhtml", "document.body.textContent")).nextValue().toString().contains(ReaderProofFixture.MULTILINGUAL))
                assertEquals("0", script(scenario, "one.xhtml", "document.querySelectorAll('script:not([src^=\"https://readium_assets/\"]),iframe,object,[onmouseover]').length").trim('"'))
                assertEquals("null", script(scenario, "one.xhtml", "document.querySelector('#unsafe').getAttribute('href')"))
                val chapterPolicy = org.json.JSONTokener(script(scenario, "one.xhtml", "document.querySelector('meta[http-equiv=\"Content-Security-Policy\"]').content")).nextValue().toString()
                assertTrue(chapterPolicy.contains("script-src https://*/readium/scripts/readium-reflowable.js"))
                assertFalse(chapterPolicy.contains("https://readium_"))
                assertEquals("true", script(scenario, "one.xhtml", "typeof readium.getCurrentSelection === 'function'"))
                assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.INTERNET))
                // A real external link can request confirmation; it must not be followed automatically.
                script(scenario, "one.xhtml", "document.querySelector('#external').click(); true")
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Open external website?").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                compose.onNodeWithText("Cancel").performClick()
                Thread.sleep(500)
                assertEquals(0, context.contentResolver.call(authority, "count", null, null)!!.getInt("requests"))
                // Deliberately exercise the wildcard-host CSP path via privileged test code.
                // The publication cannot author this tag: its scripts were removed above.
                script(scenario, "one.xhtml", "(function(){var probe=document.createElement('script');probe.src='https://blocked.invalid/readium/scripts/readium-reflowable.js';document.head.appendChild(probe);return true;})()")
                compose.waitUntil(10_000) {
                    var denied = false
                    scenario.onActivity { denied = androidx.lifecycle.ViewModelProvider(it)[ReaderViewModel::class.java].uiState.value.phase == ReaderPhase.Error }
                    denied
                }
                assertEquals(0, context.contentResolver.call(authority, "count", null, null)!!.getInt("requests"))
            }
            val beforeSvg = runBlocking { repository.observeSources().first().size }
            val svgSpineBytes = ReaderProofFixture.bytes(endpoint, svgSpine = true)
            val rejectedSpine = runBlocking { DocumentImporter(context.contentResolver, context.filesDir, repository, openStream = { ByteArrayInputStream(svgSpineBytes) }).importDocument(Uri.parse("content://reader-proof/scripted-svg-spine")) }
            assertTrue("Non-XHTML spines must be rejected by the existing importer", rejectedSpine is ImportResult.Unsupported)
            assertEquals(beforeSvg, runBlocking { repository.observeSources().first().size })
            val svg = runBlocking { ReaderProofFixture.import(context, ReaderProofFixture.bytes(endpoint, svgResource = true)) }
            launch(svg).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodesWithText("SVG EPUB resources are not supported by this proof.").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                scenario.onActivity { assertTrue(it.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().isEmpty()) }
                assertEquals(0, context.contentResolver.call(authority, "count", null, null)!!.getInt("requests"))
            }
        } finally { context.contentResolver.call(authority, "stop", null, null) }
    }

    @Test fun controlsRemainReachableAt200PercentTextInDarkLandscape() = withBook { _, scenario ->
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = automation.executeShellCommand(command).use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().toString(Charsets.UTF_8) } }
        val originalScale = shell("settings get system font_scale").trim().takeIf { it.toFloatOrNull() != null } ?: "1.0"
        var originalOrientation = Configuration.ORIENTATION_UNDEFINED
        var originalRequest = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        scenario.onActivity { originalOrientation = it.resources.configuration.orientation; originalRequest = it.requestedOrientation }
        fun awaitConfiguration(scale: Float, orientation: Int) = await {
            var matches = false
            scenario.onActivity { matches = kotlin.math.abs(it.resources.configuration.fontScale - scale) < 0.01f && it.resources.configuration.orientation == orientation }
            matches
        }
        try {
            navigator(scenario)
            shell("settings put system font_scale 2.0")
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            // These real configuration changes already recreate the Activity.
            awaitConfiguration(2f, Configuration.ORIENTATION_LANDSCAPE)
            navigator(scenario); waitForWeb(scenario, "one.xhtml")
            scenario.onActivity { assertEquals(2f, it.resources.configuration.fontScale, 0.01f); assertEquals(Configuration.ORIENTATION_LANDSCAPE, it.resources.configuration.orientation) }
            for (label in listOf("Library", "Keep selected passage", "Bookmark", "Search in book", "Saved items", "Dark reading")) {
                val node = compose.onNodeWithText(label)
                if (label != "Library") node.performScrollTo()
                node.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            }
            compose.onNodeWithText("Dark reading").performScrollTo().performClick()
            val native = navigator(scenario)
            await { onMain { native.settings.value.theme == org.readium.r2.navigator.preferences.Theme.DARK } }
            val image = automation.takeScreenshot()
            assertNotNull(image)
            java.io.FileOutputStream(File(context.filesDir, "reader-proof-200-landscape.png")).use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
            compose.onNodeWithText("Dark reading").performScrollTo().performClick()
        } finally {
            shell("settings put system font_scale $originalScale")
            scenario.onActivity { it.requestedOrientation = originalRequest }
            awaitConfiguration(originalScale.toFloat(), originalOrientation)
        }
    }

    @Test fun malformedDrmAndFixedLayoutReportExplicitErrorOrUnsupported() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val drm = ReaderProofFixture.withEntry(ReaderProofFixture.bytes(), "META-INF/encryption.xml", """<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><EncryptedData xmlns="http://www.w3.org/2001/04/xmlenc#"><EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes256-cbc"/><CipherData><CipherReference URI="EPUB/one.xhtml"/></CipherData></EncryptedData></encryption>""")
        for ((name, bytes) in listOf("malformed.epub" to "not an EPUB".toByteArray(), "drm.epub" to drm)) {
            val result = runBlocking { DocumentImporter(context.contentResolver, context.filesDir, repository, openStream = { ByteArrayInputStream(bytes) }).importDocument(Uri.parse("content://reader-proof/$name")) }
            assertTrue("$name must not become a readable source", result is ImportResult.Unsupported || result is ImportResult.Failed)
        }
        val bytes = instrumentation.context.assets.open("fixtures/fixed-layout.epub").use { it.readBytes() }
        val fixed = runBlocking { ReaderProofFixture.import(context, bytes) }
        launch(fixed).use { scenario ->
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Fixed-layout EPUB is not supported by this proof.").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            scenario.onActivity { assertTrue(it.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().isEmpty()) }
        }
    }

    private fun withBook(block: (SourceEntity, ActivityScenario<ReaderActivity>) -> Unit) {
        val source = runBlocking {
            (context as CommonplaceApplication).container.readerPreferences.setDarkReading(false)
            ReaderProofFixture.import(context)
        }
        // Each case uses its own source bytes in a disposable test installation; clear only
        // this fixture's entries/progress through its own Room transaction, never user data.
        runBlocking(Dispatchers.IO) {
            val db = (context as CommonplaceApplication).container.database
            db.openHelper.writableDatabase.execSQL("DELETE FROM entries WHERE sourceId = ?", arrayOf(source.id))
            db.openHelper.writableDatabase.execSQL("DELETE FROM bookmarks WHERE sourceId = ?", arrayOf(source.id))
            db.openHelper.writableDatabase.execSQL("DELETE FROM reading_state WHERE sourceId = ?", arrayOf(source.id))
            db.openHelper.writableDatabase.execSQL("DELETE FROM reader_drafts WHERE sourceId = ?", arrayOf(source.id))
        }
        launch(source).use { block(source, it) }
    }
    private fun repositorySource(id: String) = runBlocking { repository.getSource(id) }
    private fun launch(source: SourceEntity): ActivityScenario<ReaderActivity> = ActivityScenario.launch(Intent(context, ReaderActivity::class.java).putExtra("sourceId", source.id))
    private fun awaitNavigatorForSource(scenario: ActivityScenario<ReaderActivity>, old: EpubNavigatorFragment): EpubNavigatorFragment {
        var found: EpubNavigatorFragment? = null
        await { compose.waitForIdle(); scenario.onActivity { found = it.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().singleOrNull()?.takeUnless { current -> current === old } }; found != null }
        return found!!
    }
    companion object {
        fun nativePublication(native: EpubNavigatorFragment): org.readium.r2.shared.publication.Publication {
            val field = org.readium.r2.navigator.NavigatorFragment::class.java.getDeclaredField("publication")
            field.isAccessible = true
            return field.get(native) as org.readium.r2.shared.publication.Publication
        }
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + 20_000_000_000L
            while (System.nanoTime() < deadline) { if (condition()) return; Thread.sleep(50) }
            assertTrue("Timed out waiting for actual reader state", condition())
        }
        fun <T> onMain(block: () -> T): T {
            var result: T? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
            @Suppress("UNCHECKED_CAST") return result as T
        }
        fun navigator(scenario: ActivityScenario<ReaderActivity>, advanceComposition: () -> Unit = {}): EpubNavigatorFragment {
            var result: EpubNavigatorFragment? = null
            await {
                advanceComposition()
                scenario.onActivity {
                    val state = androidx.lifecycle.ViewModelProvider(it)[ReaderViewModel::class.java].uiState.value
                    assertTrue("Reader failed before native attachment: ${state.message}", state.phase != ReaderPhase.Error && state.phase != ReaderPhase.Unsupported)
                    result = it.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().singleOrNull()?.takeIf { fragment -> fragment.view != null }
                }
                result != null
            }
            return result!!
        }
        fun waitForWeb(scenario: ActivityScenario<ReaderActivity>, suffix: String) {
            val marker = JSONObject.quote(if (suffix.endsWith("one.xhtml")) ReaderProofFixture.MULTILINGUAL else "A different place for a durable bookmark.")
            await {
                var locator: Locator? = null
                scenario.onActivity { locator = it.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().singleOrNull()?.currentLocator?.value }
                // DOM readiness precedes asynchronous SDK position/title enrichment.
                // Exact selection snapshots must sample the completed native locator.
                locator?.href?.toString()?.endsWith(suffix) == true && locator?.locations?.position != null &&
                    script(scenario, suffix, "document.readyState == 'complete' && !!document.body && typeof readium !== 'undefined' && typeof readium.getCurrentSelection === 'function' && document.body.textContent.indexOf($marker) >= 0").trim('"') == "true"
            }
        }
        fun script(scenario: ActivityScenario<ReaderActivity>, suffix: String, js: String): String {
            var result = "null"
            val latch = CountDownLatch(1)
            scenario.onActivity { activity ->
                fun views(view: View): List<WebView> = if (view is WebView) listOf(view) else if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
                val web = views(activity.window.decorView).firstOrNull { it.url?.contains(suffix) == true }
                if (web == null) latch.countDown() else web.evaluateJavascript(js) { result = it; latch.countDown() }
            }
            check(latch.await(10, TimeUnit.SECONDS)) { "WebView script callback timed out" }
            return result
        }
        fun selectSecond(scenario: ActivityScenario<ReaderActivity>, native: EpubNavigatorFragment): Locator {
            val text = JSONObject.quote(ReaderProofFixture.QUOTATION)
            val selected = script(scenario, "one.xhtml", """(function(){var n=document.getElementById('second').firstChild;var q=$text;var start=n.textContent.indexOf(q);var range=document.createRange();range.setStart(n,start);range.setEnd(n,start+q.length);var selection=window.getSelection();selection.removeAllRanges();selection.addRange(range);return selection.toString();})()""")
            assertEquals(ReaderProofFixture.QUOTATION, org.json.JSONTokener(selected).nextValue())
            val selection = runBlocking { withContext(Dispatchers.Main) { native.currentSelection() } }
            assertNotNull("Readium must report a real native selection", selection)
            assertEquals(ReaderProofFixture.QUOTATION, selection!!.locator.text.highlight)
            return selection.locator
        }
        fun go(scenario: ActivityScenario<ReaderActivity>, native: EpubNavigatorFragment, locator: Locator) { scenario.onActivity { assertTrue(native.go(locator)) } }
    }
}
