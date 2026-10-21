package im.commonplace.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SavedLocationTest {
    @Test
    fun epubEnvelopeRoundTripPreservesCompleteNativePayloadText() {
        val payload = """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"progression":0.42,"position":7},"text":{"after":"next","before":"prior","highlight":"quoted passage"},"futureField":{"opaque":[1,true,null]}}"""
        val original = SavedLocation(
            version = 1,
            format = DocumentFormat.EPUB,
            engineId = "readium",
            payloadJson = payload,
        )

        val restored = SavedLocation.fromJson(original.toJson())

        assertEquals(payload, restored.payloadJson)
        assertEquals(original, restored)
        restored.validate()
    }

    @Test
    fun pdfLocationUsesOneBasedDisplayAndPreservesUnicodeLabelAndNullQuads() {
        val location = SavedLocation(
            version = 1,
            format = DocumentFormat.PDF,
            engineId = "androidx-pdf",
            payloadJson = """{"pageIndex":0,"pageLabel":"পৃষ্ঠা Ω","pageBox":[0,0,612,792],"rotationDegrees":0,"coordinateSpace":"unrotated-pdf-points","quads":null}""",
        )

        location.validate()

        assertEquals(1, location.physicalPageNumber)
        assertEquals("পৃষ্ঠা Ω", location.pageLabel)
        assertNull(location.quads)
        assertEquals(location.payloadJson, SavedLocation.fromJson(location.toJson()).payloadJson)
    }

    @Test
    fun negativePdfPageIndexIsRejectedWithoutDiscardingStoredPayload() {
        val payload = """{"pageIndex":-1,"pageBox":[0,0,612,792],"rotationDegrees":0,"quads":null}"""
        val stored = SavedLocation.fromJson(
            SavedLocation(1, DocumentFormat.PDF, "androidx-pdf", payload).toJson(),
        )

        assertThrows(IllegalArgumentException::class.java) { stored.validate() }
        assertEquals(payload, stored.payloadJson)
        assertEquals(payload, SavedLocation.fromJson(stored.toJson()).payloadJson)
    }

    @Test
    fun nonFinitePdfCoordinatesAreRejectedWithoutDiscardingStoredPayload() {
        val payload = """{"pageIndex":0,"pageBox":[0,0,NaN,792],"rotationDegrees":0,"quads":null}"""
        val stored = SavedLocation.fromJson(
            SavedLocation(1, DocumentFormat.PDF, "androidx-pdf", payload).toJson(),
        )

        assertThrows(IllegalArgumentException::class.java) { stored.validate() }
        assertEquals(payload, stored.payloadJson)
        assertEquals(payload, SavedLocation.fromJson(stored.toJson()).payloadJson)
    }

    @Test
    fun unknownStoredVersionCanRoundTripButCannotBeUsed() {
        val payload = """{"pageIndex":0,"futureEngineData":"untouched"}"""
        val stored = SavedLocation.fromJson(
            SavedLocation(2, DocumentFormat.PDF, "future-pdf", payload).toJson(),
        )

        assertThrows(IllegalArgumentException::class.java) { stored.validate() }
        assertEquals(2, stored.version)
        assertEquals(payload, stored.payloadJson)
        assertEquals(stored, SavedLocation.fromJson(stored.toJson()))
    }
}
