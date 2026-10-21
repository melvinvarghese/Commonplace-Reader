package im.commonplace.reader.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Persisted source format. */
enum class DocumentFormat {
    EPUB,
    PDF,
}

/** A selected quotation paired with the reader-native location that produced it. */
data class Capture(val quotation: String, val location: SavedLocation)

/** Versioned wrapper around an opaque engine-native JSON payload. */
data class SavedLocation(
    val version: Int,
    val format: DocumentFormat,
    val engineId: String,
    val payloadJson: String,
) {
    fun validate() {
        require(version == CURRENT_VERSION) { "Unsupported saved location version: $version" }
        require(engineId.isNotBlank()) { "Reader engine identity is required" }
        val payload = Json.parseToJsonElement(payloadJson).jsonObject
        if (format == DocumentFormat.PDF) validatePdfPayload(payload)
    }

    val physicalPageNumber: Int
        get() {
            validate()
            require(format == DocumentFormat.PDF) { "Physical page number is only available for PDFs" }
            return pdfPayload().getValue("pageIndex").intValue() + 1
        }

    val pageLabel: String?
        get() {
            validate()
            require(format == DocumentFormat.PDF) { "Page label is only available for PDFs" }
            return pdfPayload()["pageLabel"]?.let { if (it == JsonNull) null else it.jsonPrimitive.content }
        }

    val quads: JsonElement?
        get() {
            validate()
            require(format == DocumentFormat.PDF) { "Selection geometry is only available for PDFs" }
            return pdfPayload()["quads"]?.takeUnless { it == JsonNull }
        }

    /** Serialization preserves the original payload text, including unknown engine fields. */
    fun toJson(): String = buildJsonObject {
        put("version", version)
        put("format", format.name)
        put("engineId", engineId)
        put("payloadJson", payloadJson)
    }.toString()

    private fun pdfPayload() = Json.parseToJsonElement(payloadJson).jsonObject

    private fun validatePdfPayload(payload: JsonObject) {
        val pageIndex = payload.required("pageIndex").intValue()
        require(pageIndex >= 0 && pageIndex < Int.MAX_VALUE) { "PDF page index is outside the displayable range" }

        val pageBox = payload.required("pageBox") as? JsonArray
            ?: throw IllegalArgumentException("PDF pageBox must contain four bounds")
        require(pageBox.size == 4) { "PDF pageBox must contain four bounds" }
        val bounds = pageBox.map(JsonElement::finiteNumber)
        require(bounds[0] < bounds[2] && bounds[1] < bounds[3]) { "PDF pageBox bounds must be ordered" }

        val rotation = payload.required("rotationDegrees").intValue()
        require(rotation in setOf(0, 90, 180, 270)) { "Unsupported PDF page rotation: $rotation" }
        require(payload.required("coordinateSpace").stringValue() == PDF_COORDINATE_SPACE) {
            "PDF coordinates must use unrotated PDF points"
        }
        payload["pageLabel"]?.let { if (it != JsonNull) it.stringValue() }
        payload["quads"]?.let { quads ->
            if (quads != JsonNull) {
                val groups = quads as? JsonArray
                    ?: throw IllegalArgumentException("PDF quads must be null or arrays of eight coordinates")
                groups.forEach { group ->
                    val coordinates = group as? JsonArray
                        ?: throw IllegalArgumentException("Each PDF quad must contain eight coordinates")
                    require(coordinates.size == 8) { "Each PDF quad must contain eight coordinates" }
                    coordinates.forEach(JsonElement::finiteNumber)
                }
            }
        }
    }

    companion object {
        private const val CURRENT_VERSION = 1
        private const val PDF_COORDINATE_SPACE = "unrotated-pdf-points"

        fun fromJson(json: String): SavedLocation {
            val wrapper = Json.parseToJsonElement(json).jsonObject
            return SavedLocation(
                version = wrapper.getValue("version").jsonPrimitive.content.toInt(),
                format = DocumentFormat.valueOf(wrapper.getValue("format").jsonPrimitive.content),
                engineId = wrapper.getValue("engineId").jsonPrimitive.content,
                payloadJson = wrapper.getValue("payloadJson").jsonPrimitive.content,
            )
        }
    }
}

private fun JsonObject.required(name: String): JsonElement =
    get(name) ?: throw IllegalArgumentException("Missing required location field: $name")

private fun JsonElement.intValue(): Int {
    val primitive = this as? JsonPrimitive
        ?: throw IllegalArgumentException("Expected an integer")
    require(!primitive.isString) { "Expected an integer" }
    return primitive.content.toIntOrNull()
        ?: throw IllegalArgumentException("Expected an integer")
}

private fun JsonElement.stringValue(): String {
    val primitive = this as? JsonPrimitive
        ?: throw IllegalArgumentException("Expected a string")
    require(primitive.isString) { "Expected a string" }
    return primitive.content
}

private fun JsonElement.finiteNumber(): Double {
    val primitive = this as? JsonPrimitive
        ?: throw IllegalArgumentException("Expected a finite number")
    require(!primitive.isString) { "Expected a finite number" }
    val value = primitive.content.toDoubleOrNull()
        ?: throw IllegalArgumentException("Expected a finite number")
    require(value.isFinite()) { "Expected a finite number" }
    return value
}
