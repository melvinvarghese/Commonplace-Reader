package im.commonplace.reader.reader

/** Values emitted by a reader integration; source identity prevents stale events crossing a source switch. */
sealed interface ReaderEvent {
    val sourceId: String

    data class Opened(
        override val sourceId: String,
        val capabilities: ReaderCapabilities,
    ) : ReaderEvent

    data class PositionChanged(
        override val sourceId: String,
        val location: SavedLocation,
    ) : ReaderEvent

    data class SelectionChanged(
        override val sourceId: String,
        val captureOrNull: Capture?,
    ) : ReaderEvent

    data class Failed(
        override val sourceId: String,
        val userMessage: String,
    ) : ReaderEvent
}
