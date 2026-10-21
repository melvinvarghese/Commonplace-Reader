package im.commonplace.reader.imports

sealed interface ImportResult {
    data class Imported(val sourceId: String) : ImportResult
    data class Duplicate(val sourceId: String) : ImportResult
    data class Unsupported(val reason: String) : ImportResult
    data class Failed(val reason: String) : ImportResult
}
