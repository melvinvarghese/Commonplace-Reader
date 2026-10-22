package im.commonplace.reader.reader

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** A same-resource pagination event alone cannot confirm the requested quotation. */
internal suspend fun awaitEntryArrival(
    isTargetStartVisible: suspend () -> Boolean,
    freshVisibleLocation: suspend () -> SavedLocation?,
    excludedPreJumpLocation: SavedLocation?=null,
): SavedLocation? = withTimeoutOrNull(15_000) {
    var observed: SavedLocation?=null
    while(observed==null) {
        if(isTargetStartVisible()) observed=freshVisibleLocation()?.takeUnless { it==excludedPreJumpLocation }
        if(observed==null) delay(50)
    }
    observed
}
