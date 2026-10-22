package im.commonplace.reader.reader.pdf

import im.commonplace.reader.reader.DocumentFormat
import im.commonplace.reader.reader.SavedLocation
import org.junit.Assert.*
import org.junit.Test

class HybridGeometryTest {
    @Test fun cropOriginAndRotationAreNotDiscarded() {
        val transform = doubleArrayOf(0.0, 2.0, 2.0, 0.0, -72.0, -72.0)
        val actual = HybridGeometry.map(doubleArrayOf(72.0, 698.0, 288.211, 698.0), transform)
        assertArrayEquals(doubleArrayOf(1324.0, 72.0, 1324.0, 504.422), actual, 0.00001)
    }
    @Test fun refusesNonfiniteOrSingularGeometry() {
        for (matrix in listOf(doubleArrayOf(0.0,0.0,0.0,0.0,0.0,0.0), doubleArrayOf(1.0,0.0,0.0,1.0,Double.NaN,0.0))) {
            assertThrows(IllegalArgumentException::class.java) { HybridGeometry.map(doubleArrayOf(1.0,2.0),matrix) }
        }
    }
    @Test fun rangeIdentityIsRequiredForExactReopening() {
        val incomplete = SavedLocation(1, DocumentFormat.PDF, HYBRID_ENGINE_ID,
            """{"pageIndex":1,"pageBox":[0,0,612,792],"rotationDegrees":0,"coordinateSpace":"unrotated-pdf-points","quads":[[72,698,280,698,280,712,72,712]]}""")
        assertThrows(IllegalArgumentException::class.java) { HybridGeometry.validateSelection(incomplete) }
    }
}
