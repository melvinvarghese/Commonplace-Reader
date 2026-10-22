package im.commonplace.reader.reader.pdf

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HybridFrameHandshakeTest {
    private fun frame(page: Int = 0, zoom: Int = 1) = JSONObject(
        """{"pageIndex":$page,"widthPx":612,"heightPx":792,"leftPx":0,"topPx":0,"density":1,"zoom":$zoom}"""
    )
    private fun message(frame: JSONObject) = JSONObject().put("frame", frame)

    @Test fun doesNotSendUntilTheModuleInstallsItsReceiver() {
        val handshake = HybridFrameHandshake()
        handshake.observe(frame())
        assertNull(handshake.nextDispatch())
        handshake.receiverReady()
        assertNotNull(handshake.nextDispatch())
    }

    @Test fun failedDeliveryCanBeRetriedButSuccessfulDeliveryIsNotRepeated() {
        val handshake = HybridFrameHandshake()
        handshake.receiverReady()
        handshake.observe(frame())
        val first = handshake.nextDispatch()!!
        assertNull(handshake.nextDispatch())
        handshake.acknowledge(first.getLong("requestId"), false)
        val retry = handshake.nextDispatch()!!
        assertEquals(first.getLong("requestId"), retry.getLong("requestId"))
        handshake.acknowledge(retry.getLong("requestId"), true)
        assertNull(handshake.nextDispatch())
    }

    @Test fun anOldDeliveryCallbackCannotConsumeTheNewFrame() {
        val handshake = HybridFrameHandshake()
        handshake.receiverReady()
        handshake.observe(frame())
        val old = handshake.nextDispatch()!!
        handshake.observe(frame(page = 1))
        val current = handshake.nextDispatch()!!
        assertFalse(handshake.acknowledge(old.getLong("requestId"), true))
        assertTrue(handshake.matches(message(current)))
        handshake.acknowledge(current.getLong("requestId"), false)
        assertNotNull(handshake.nextDispatch())
    }

    @Test fun invalidationImmediatelyRejectsOldPageAndZoomReadiness() {
        for (next in listOf(frame(page = 1), frame(zoom = 2))) {
            val handshake = HybridFrameHandshake()
            handshake.receiverReady()
            handshake.observe(frame())
            val old = handshake.nextDispatch()!!
            handshake.invalidate()
            assertFalse(handshake.matches(message(old)))
            handshake.observe(next)
            val current = handshake.nextDispatch()!!
            assertFalse(handshake.matches(message(old)))
            assertTrue(handshake.matches(message(current)))
        }
    }

    @Test fun returningToIdenticalGeometryStillRejectsAnOldReadinessMessage() {
        val handshake = HybridFrameHandshake()
        handshake.receiverReady()
        handshake.observe(frame())
        val old = handshake.nextDispatch()!!
        handshake.invalidate()
        handshake.observe(frame())
        val current = handshake.nextDispatch()!!
        assertNotEquals(old.getLong("requestId"), current.getLong("requestId"))
        assertFalse(handshake.matches(message(old)))
        assertTrue(handshake.matches(message(current)))
    }

    @Test fun requiresTheExactDispatchedFrameNotJustItsRequestId() {
        val handshake = HybridFrameHandshake()
        handshake.receiverReady()
        handshake.observe(frame())
        assertFalse(handshake.matches(message(handshake.frame!!)))
        val current = handshake.nextDispatch()!!
        assertTrue(handshake.matches(message(current)))
        for (field in listOf("pageIndex", "widthPx", "heightPx", "leftPx", "topPx", "density", "zoom")) {
            val changed = JSONObject(current.toString()).put(field, 99)
            assertFalse(field, handshake.matches(message(changed)))
        }
        assertFalse(handshake.matches(JSONObject()))
        assertFalse(handshake.matches(message(JSONObject(current.toString()).put("requestId", -1))))
    }

    @Test fun pageReloadRequiresANewReceiverAndNeverReusesThePreviousRequest() {
        val handshake = HybridFrameHandshake()
        handshake.receiverReady()
        handshake.observe(frame())
        val old = handshake.nextDispatch()!!
        handshake.resetReceiver()
        handshake.observe(frame())
        assertNull(handshake.nextDispatch())
        assertFalse(handshake.matches(message(old)))
        handshake.receiverReady()
        assertNotEquals(old.getLong("requestId"), handshake.nextDispatch()!!.getLong("requestId"))
    }

    @Test fun unchangedDrawAndDuplicateReceiverSignalDoNotCreateAnotherRequest() {
        val handshake = HybridFrameHandshake()
        handshake.receiverReady()
        assertTrue(handshake.observe(frame()))
        val current = handshake.nextDispatch()!!
        handshake.acknowledge(current.getLong("requestId"), true)
        assertFalse(handshake.observe(frame()))
        handshake.receiverReady()
        assertNull(handshake.nextDispatch())
        assertTrue(handshake.matches(message(current)))
    }
}
