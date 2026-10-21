package im.commonplace.reader.reader.pdf

import im.commonplace.reader.reader.*
import org.junit.Assert.*
import org.junit.Test

class PdfLocationsTest {
    private val box = floatArrayOf(36f, 36f, 576f, 756f)
    private val quad = floatArrayOf(72f, 698f, 288.211f, 698f, 288.211f, 712f, 72f, 712f)
    @Test fun completeNativePayloadAndGeometryRoundTrip() {
        val location = PdfLocations.encode(0, "前言", box, 90, listOf(quad), """{"opaque":"café","zoom":2}""")
        val data = PdfLocations.decode(SavedLocation.fromJson(location.toJson()))
        assertEquals("nucleus-composepdf", location.engineId)
        assertEquals(1, location.physicalPageNumber)
        assertEquals(0, data.pageIndex)
        assertEquals("前言", data.pageLabel)
        assertArrayEquals(box, data.pageBox, 0f)
        assertEquals(90, data.rotationDegrees)
        assertArrayEquals(quad, data.quads!!.single(), 0f)
        assertEquals("""{"opaque":"café","zoom":2}""", data.nativePayloadJson)
    }
    @Test fun missingSelectionGeometryStaysMissing() {
        val data = PdfLocations.decode(PdfLocations.encode(1, null, box, 90, null, null))
        assertNull(data.quads); assertNull(data.pageLabel); assertNull(data.nativePayloadJson)
    }
    @Test fun otherEngineEpubAndVersionAreRejected() {
        val own = PdfLocations.encode(0, null, box, 90, null, null)
        listOf(own.copy(engineId = "androidx-pdf"), own.copy(engineId = "readium", format = DocumentFormat.EPUB), own.copy(version = 2)).forEach {
            assertThrows(IllegalArgumentException::class.java) { PdfLocations.decode(it) }
        }
    }
    @Test fun malformedGeometryCannotReachViewer() {
        assertThrows(IllegalArgumentException::class.java) { PdfLocations.encode(-1, null, box, 90, null, null) }
        assertThrows(IllegalArgumentException::class.java) { PdfLocations.encode(0, null, box, 45, null, null) }
        assertThrows(IllegalArgumentException::class.java) { PdfLocations.encode(0, null, box, 90, listOf(floatArrayOf(Float.NaN)), null) }
    }
}
