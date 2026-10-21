package im.commonplace.reader.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
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
    fun validate() = Unit

    val physicalPageNumber: Int
        get() = pdfPayload().getValue("pageIndex").jsonPrimitive.content.toInt() + 1

    val pageLabel: String?
        get() = pdfPayload()["pageLabel"]?.let { if (it == JsonNull) null else it.jsonPrimitive.content }

    val quads
        get() = pdfPayload()["quads"]?.takeUnless { it == JsonNull }

    fun toJson(): String = buildJsonObject {
            put("version", version)
            put("format", format.name)
            put("engineId", engineId)
            put("payloadJson", payloadJson)
        }.toString()

    private fun pdfPayload() = Json.parseToJsonElement(payloadJson).jsonObject

    companion object {
        fun fromJson(json: String): SavedLocation {
            val wrapper = Json.parseToJsonElement(json).jsonObject
            return SavedLocation(
                version = wrapper.getValue("version").jsonPrimitive.int,
                format = DocumentFormat.valueOf(wrapper.getValue("format").jsonPrimitive.content),
                engineId = wrapper.getValue("engineId").jsonPrimitive.content,
                payloadJson = wrapper.getValue("payloadJson").jsonPrimitive.content,
            )
        }
    }
}
