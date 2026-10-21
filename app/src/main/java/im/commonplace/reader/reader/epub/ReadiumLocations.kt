package im.commonplace.reader.reader.epub

import im.commonplace.reader.reader.DocumentFormat
import im.commonplace.reader.reader.SavedLocation
import kotlinx.serialization.json.*

object ReadiumLocations {
    const val ENGINE_ID = "readium"
    fun encode(nativeLocatorJson: String): SavedLocation =
        SavedLocation(1, DocumentFormat.EPUB, ENGINE_ID, nativeLocatorJson).also { decode(it) }

    fun decode(location: SavedLocation): String {
        require(location.version == 1 && location.format == DocumentFormat.EPUB && location.engineId == ENGINE_ID) { "Incompatible EPUB location" }
        location.validate()
        val json = Json.parseToJsonElement(location.payloadJson).jsonObject
        for (field in listOf("href", "type")) {
            val value = json[field] as? JsonPrimitive
            require(value?.isString == true && value.content.isNotBlank()) { "Missing native locator $field" }
        }
        return location.payloadJson
    }
}
