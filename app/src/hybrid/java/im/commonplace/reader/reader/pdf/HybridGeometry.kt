package im.commonplace.reader.reader.pdf

import im.commonplace.reader.reader.DocumentFormat
import im.commonplace.reader.reader.SavedLocation
import kotlinx.serialization.json.*
import kotlin.math.abs

const val HYBRID_ENGINE_ID = "disposable-pdfjs-androidpdfviewer"

/** Coordinate plumbing only; independent native measurements are still required for acceptance. */
internal object HybridGeometry {
    fun map(points: DoubleArray, matrix: DoubleArray): DoubleArray {
        require(points.isNotEmpty() && points.size % 2 == 0 && points.all(Double::isFinite))
        require(matrix.size == 6 && matrix.all(Double::isFinite))
        require(abs(matrix[0] * matrix[3] - matrix[1] * matrix[2]) > 1e-12)
        return DoubleArray(points.size) { i ->
            val x = points[i - i % 2]; val y = points[i - i % 2 + 1]
            if (i % 2 == 0) matrix[0] * x + matrix[2] * y + matrix[4]
            else matrix[1] * x + matrix[3] * y + matrix[5]
        }.also { require(it.all(Double::isFinite)) }
    }
    fun validateSelection(location: SavedLocation) {
        location.validate()
        require(location.format == DocumentFormat.PDF && location.engineId == HYBRID_ENGINE_ID)
        val payload = Json.parseToJsonElement(location.payloadJson).jsonObject
        val quads = payload["quads"] as? JsonArray
        require(!quads.isNullOrEmpty()) { "A real selection must supply geometry" }
        val native = payload["native"] as? JsonObject ?: throw IllegalArgumentException("Missing exact DOM range")
        for (edge in listOf("start", "end")) {
            val point = native[edge] as? JsonObject ?: throw IllegalArgumentException("Missing exact DOM endpoint")
            for (field in listOf("spanIndex", "offset")) {
                val number = point[field] as? JsonPrimitive
                require(number != null && !number.isString && number.intOrNull?.let { it >= 0 } == true)
            }
        }
        require(native["start"] != native["end"]) { "Empty DOM range" }
    }
}
