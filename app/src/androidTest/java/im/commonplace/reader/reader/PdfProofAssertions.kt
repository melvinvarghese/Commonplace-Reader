package im.commonplace.reader.reader

import org.junit.Assert.*

/** Shared acceptance assertions; an unavailable capability is never a feature pass. */
object PdfProofAssertions {
    fun exactText(expected: String, actual: CharSequence) = assertEquals(expected, actual.toString())
    fun sameEngine(location: SavedLocation, engine: String) {
        location.validate()
        assertEquals(DocumentFormat.PDF, location.format)
        assertEquals(engine, location.engineId)
    }
    fun geometry(reference: FloatArray, actual: FloatArray) {
        assertEquals(8, actual.size)
        reference.indices.forEach { assertEquals("PDF point $it", reference[it], actual[it], 1f) }
    }
    fun overlay(expectedDp: FloatArray, actualDp: FloatArray) {
        assertEquals(expectedDp.size, actualDp.size)
        expectedDp.indices.forEach { assertEquals("overlay dp $it", expectedDp[it], actualDp[it], 2f) }
    }
    fun unavailable(status: CapabilityStatus, reason: String) {
        assertTrue(status is CapabilityStatus.Unavailable)
        assertEquals(reason, (status as CapabilityStatus.Unavailable).reason)
    }
}
