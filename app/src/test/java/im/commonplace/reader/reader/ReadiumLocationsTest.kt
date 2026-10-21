package im.commonplace.reader.reader

import im.commonplace.reader.reader.epub.ReadiumLocations
import org.junit.Assert.*
import org.junit.Test

class ReadiumLocationsTest {
    private val json = """ {"href":"EPUB/chapter.xhtml", "type":"application/xhtml+xml","locations":{"fragments":["epubcfi(/6/2!/4/2:8)"],"progression":0.375},"text":{"before":"first ","highlight":"naïve Καλημέρα","after":" second"},"future":{"unknown":true}} """
    @Test fun preservesCompleteNativeJsonIncludingUnknownFieldsAndWhitespace() {
        val saved = ReadiumLocations.encode(json)
        assertEquals(DocumentFormat.EPUB, saved.format)
        assertEquals("readium", saved.engineId)
        assertEquals(json, ReadiumLocations.decode(SavedLocation.fromJson(saved.toJson())))
    }
    @Test fun rejectsOtherEngineVersionFormatAndMalformedNativeLocator() {
        val good = SavedLocation(1, DocumentFormat.EPUB, "readium", json)
        for (bad in listOf(good.copy(version = 2), good.copy(engineId = "pdfium"), good.copy(format = DocumentFormat.PDF), good.copy(payloadJson = "[]"), good.copy(payloadJson = "{}"))) {
            assertThrows(IllegalArgumentException::class.java) { ReadiumLocations.decode(bad) }
        }
    }
}
