package im.commonplace.reader.reader

import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EntryNavigationTest {
    private val old=SavedLocation(1,DocumentFormat.EPUB,"readium","""{"href":"one.xhtml","locations":{"progression":0.1}}""")
    private val target=old.copy(payloadJson="""{"href":"one.xhtml","locations":{"progression":0.9}}""")
    @Test fun sameResourceOldPositionCannotConfirmInvisibleTarget()=runTest {
        var visible=false
        var observed=old
        val result=async { awaitEntryArrival({visible},{observed}) }
        runCurrent();assertFalse(result.isCompleted)
        advanceTimeBy(1000);runCurrent();assertFalse(result.isCompleted)
        observed=target;visible=true
        advanceTimeBy(50);runCurrent();assertEquals(target,result.await())
    }
    @Test fun absentExactRangeTimesOutWithoutInventingProgress()=runTest {
        val result=async { awaitEntryArrival({false},{old}) }
        runCurrent();advanceTimeBy(14_999);runCurrent();assertFalse(result.isCompleted)
        advanceTimeBy(1);runCurrent();assertNull(result.await())
    }
    @Test fun visibleRangeWithoutFreshNativeLocatorCannotConfirmArrival()=runTest {
        val result=async { awaitEntryArrival({true},{null}) }
        runCurrent();assertFalse(result.isCompleted)
        advanceTimeBy(15_000);runCurrent();assertNull(result.await())
    }
    @Test fun visibleTargetStillRejectsThePreJumpNativePosition()=runTest {
        var observed=old
        val result=async { awaitEntryArrival({true},{observed},old) }
        runCurrent();assertFalse(result.isCompleted)
        observed=target
        advanceTimeBy(50);runCurrent();assertEquals(target,result.await())
    }
}
