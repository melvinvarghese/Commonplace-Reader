package im.commonplace.reader.reader

/** An independently reported capability, scoped to the current document, device, and engine. */
sealed interface CapabilityStatus {
    data object Available : CapabilityStatus
    data class Unavailable(val reason: String) : CapabilityStatus
    data object Unknown : CapabilityStatus
}

/** Capability values deliberately distinguish an observed limitation from an untested feature. */
data class ReaderCapabilities(
    val render: CapabilityStatus,
    val search: CapabilityStatus,
    val select: CapabilityStatus,
    val highlightDisplay: CapabilityStatus,
    val navigation: CapabilityStatus,
)
