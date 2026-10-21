package im.commonplace.reader.reader.pdf

import im.commonplace.reader.reader.DocumentFormat
import im.commonplace.reader.reader.SavedLocation
import kotlinx.serialization.json.*

data class PdfLocationData(val pageIndex: Int, val pageLabel: String?, val pageBox: FloatArray, val rotationDegrees: Int, val quads: List<FloatArray>?, val nativePayloadJson: String?)

/** Storage codec only: accepting a payload does not prove a native coordinate mapping. */
object PdfLocations {
    fun encode(pageIndex: Int, pageLabel: String?, pageBox: FloatArray, rotationDegrees: Int, quads: List<FloatArray>?, nativePayloadJson: String?): SavedLocation {
        val payload = buildJsonObject {
            put("pageIndex", pageIndex)
            put("pageLabel", pageLabel?.let(::JsonPrimitive) ?: JsonNull)
            put("pageBox", JsonArray(pageBox.map(::JsonPrimitive)))
            put("rotationDegrees", rotationDegrees)
            put("coordinateSpace", "unrotated-pdf-points")
            put("quads", quads?.let { JsonArray(it.map { quad -> JsonArray(quad.map(::JsonPrimitive)) }) } ?: JsonNull)
            put("nativePayloadJson", nativePayloadJson?.let(::JsonPrimitive) ?: JsonNull)
        }
        return SavedLocation(1, DocumentFormat.PDF, "nucleus-composepdf", payload.toString()).also { it.validate() }
    }
    fun decode(location: SavedLocation): PdfLocationData {
        require(location.format == DocumentFormat.PDF && location.engineId == "nucleus-composepdf") { "Incompatible PDF engine" }
        location.validate()
        val payload = Json.parseToJsonElement(location.payloadJson).jsonObject
        fun floats(value: JsonElement) = value.jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
        fun optional(name: String) = payload[name]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
        return PdfLocationData(payload.getValue("pageIndex").jsonPrimitive.int, optional("pageLabel"), floats(payload.getValue("pageBox")),
            payload.getValue("rotationDegrees").jsonPrimitive.int,
            payload["quads"]?.takeUnless { it == JsonNull }?.jsonArray?.map(::floats), optional("nativePayloadJson"))
    }
}
