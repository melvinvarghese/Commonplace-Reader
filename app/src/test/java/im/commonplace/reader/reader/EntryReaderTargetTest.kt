package im.commonplace.reader.reader

import im.commonplace.reader.data.*
import im.commonplace.reader.notebook.TestNotebookStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class EntryReaderTargetTest {
    @get:Rule val temp=TemporaryFolder()
    private val notebook=TestNotebookStore()
    private val sourceId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val entryId="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    private val locator=SavedLocation(1,DocumentFormat.EPUB,"readium",""" {"href":"EPUB/one.xhtml","type":"application/xhtml+xml","text":{"highlight":"Exact café"},"unknown":"untouched"} """)
    private lateinit var source: SourceEntity
    private lateinit var asset: File
    private lateinit var reader: ReaderStore
    private lateinit var resolver: EntryReaderTargetResolver
    private fun entry()=EntryEntity(entryId,EntryKind.HIGHLIGHT,sourceId,locator,null,"My reflection","Exact café","Original snapshot","Author",42,1,1)
    @Before fun setup() {
        asset=File(temp.root,"imports/assets/$sourceId.epub");asset.parentFile.mkdirs();asset.writeText("original synthetic bytes")
        val digest=MessageDigest.getInstance("SHA-256").digest(asset.readBytes()).joinToString("") { "%02x".format(it) }
        source=SourceEntity(sourceId,"Book",null,DocumentFormat.EPUB,"content://synthetic",digest,"imports/assets/$sourceId.epub",SourceAvailability.AVAILABLE,1,1)
        notebook.entries.value=listOf(entry())
        reader=object : ReaderStore {
            override suspend fun getSource(sourceId: String)=source.takeIf { it.id==sourceId }
            override suspend fun getProgress(sourceId: String): SavedLocation?=null
            override suspend fun getDraft(sourceId: String): ReaderDraftEntity?=null
            override fun observeEntries(sourceId: String)=notebook.entries
            override suspend fun getBookmarks(sourceId: String)=emptyList<BookmarkEntity>()
            override suspend fun saveDraft(draft: ReaderDraftEntity)=error("not called")
            override suspend fun saveProgress(sourceId: String,location: SavedLocation)=error("not called")
            override suspend fun saveCapture(sourceId: String,capture: Capture,reflection: String?,colorArgb: Int?)=error("not called")
            override suspend fun addBookmark(sourceId: String,location: SavedLocation)=error("not called")
            override suspend fun saveSourceAnnotation(sourceId: String,body: String)=error("not called")
        }
        resolver=EntryReaderTargetResolver(notebook,reader) { verifySourceFile(temp.root,it) }
    }
    @Test fun sourceChecksumMismatchRetainsWritingAndRefusesTarget()=runTest {
        asset.appendText("changed")
        val result=resolver.resolveEntryReaderTarget(entryId) as EntryReaderTarget.Unavailable
        assertTrue(result.reason.contains("bytes changed"));assertEquals(entry(),result.entry)
        assertFalse(result.reason.contains(temp.root.path));assertEquals(listOf(entry()),notebook.entries.value)
    }
    @Test fun exactOpaqueLocatorIsReturnedWithoutReserialization()=runTest {
        val result=resolver.resolveEntryReaderTarget(entryId) as EntryReaderTarget.Passage
        assertEquals(EntryReaderTarget.Passage(entryId,sourceId,locator),result)
        assertEquals(locator.payloadJson,result.location.payloadJson)
    }
    @Test fun unknownEngineOrEnvelopeIsUnresolved()=runTest {
        for(location in listOf(locator.copy(engineId="unknown"),locator.copy(version=2),locator.copy(payloadJson="{}"))) {
            notebook.entries.value=listOf(entry().copy(location=location))
            val result=resolver.resolveEntryReaderTarget(entryId) as EntryReaderTarget.Unavailable
            assertTrue(result.reason.contains("unresolved"));assertEquals(location,result.entry!!.location)
        }
    }
    @Test fun missingSourceKeepsSnapshot()=runTest {
        notebook.entries.value=listOf(entry().copy(sourceId=null))
        val result=resolver.resolveEntryReaderTarget(entryId) as EntryReaderTarget.Unavailable
        assertTrue(result.reason.contains("missing"));assertEquals("Original snapshot",result.entry!!.attributionTitle)
        source=source.copy(availability=SourceAvailability.MISSING);notebook.entries.value=listOf(entry())
        assertTrue((resolver.resolveEntryReaderTarget(entryId) as EntryReaderTarget.Unavailable).reason.contains("missing"))
    }
    @Test fun pdfTargetNeverBecomesAcceptedPassage()=runTest {
        source=source.copy(format=DocumentFormat.PDF)
        val result=resolver.resolveEntryReaderTarget(entryId) as EntryReaderTarget.Unavailable
        assertTrue(result.reason.contains("PDF"));assertTrue(result.reason.contains("unresolved"))
    }
    @Test fun sourceNoteOffersBookNotInventedPassage()=runTest {
        for(kind in listOf(EntryKind.ANNOTATION,EntryKind.SOURCE_NOTE)) {
            notebook.entries.value=listOf(entry().copy(kind=kind,location=null,quotation=null))
            assertEquals(EntryReaderTarget.Book(entryId,sourceId),resolver.resolveEntryReaderTarget(entryId))
        }
    }
    @Test fun entryIdCannotSelectUnrelatedSource()=runTest {
        assertTrue(resolver.resolveEntryReaderTarget("not-a-uuid") is EntryReaderTarget.Unavailable)
        assertTrue(resolver.resolveEntryReaderTarget(sourceId) is EntryReaderTarget.Unavailable)
        notebook.entries.value=listOf(entry().copy(sourceId="cccccccc-cccc-4ccc-8ccc-cccccccccccc"))
        val result=resolver.resolveEntryReaderTarget(entryId) as EntryReaderTarget.Unavailable
        assertTrue(result.reason.contains("missing"));assertEquals(entryId,result.entry!!.id)
    }
    @Test fun noncanonicalAssetPathAndChangedFormatRefuseTarget()=runTest {
        source=source.copy(assetPath="../outside.epub")
        assertTrue((resolver.resolveEntryReaderTarget(entryId) as EntryReaderTarget.Unavailable).reason.contains("safely"))
        notebook.entries.value=listOf(entry().copy(location=locator.copy(format=DocumentFormat.PDF)))
        assertTrue(resolver.resolveEntryReaderTarget(entryId) is EntryReaderTarget.Unavailable)
    }
}
