package im.commonplace.reader.reader.pdf

import org.json.JSONObject

/** Correlates asynchronous WebView replies, including returns to identical page geometry. */
internal class HybridFrameHandshake {
    private var sequence = 0L
    private var receiver = false
    private var inFlight: Long? = null
    private var delivered: Long? = null
    var frame: JSONObject? = null
        private set

    fun receiverReady() { receiver = true }

    fun resetReceiver() {
        receiver = false
        invalidate()
    }

    fun invalidate() {
        frame = null
        inFlight = null
        delivered = null
    }

    fun observe(observed: JSONObject): Boolean {
        if (frame?.let { sameGeometry(it, observed) } == true) return false
        frame = JSONObject(observed.toString()).put("requestId", ++sequence)
        inFlight = null
        delivered = null
        return true
    }

    fun nextDispatch(): JSONObject? {
        val current = frame ?: return null
        val id = current.getLong("requestId")
        if (!receiver || inFlight == id || delivered == id) return null
        inFlight = id
        return JSONObject(current.toString())
    }

    fun acknowledge(requestId: Long, accepted: Boolean): Boolean {
        if (inFlight != requestId || frame?.optLong("requestId") != requestId) return false
        inFlight = null
        if (accepted) delivered = requestId
        return true
    }

    fun matches(message: JSONObject): Boolean {
        val reported = message.optJSONObject("frame") ?: return false
        val current = frame ?: return false
        val id = current.getLong("requestId")
        return receiver && (inFlight == id || delivered == id) &&
            reported.optLong("requestId", -1) == id && sameGeometry(reported, current)
    }

    private fun sameGeometry(left: JSONObject, right: JSONObject) =
        listOf("pageIndex", "widthPx", "heightPx", "leftPx", "topPx", "density", "zoom").all {
            left.optDouble(it, Double.NaN) == right.optDouble(it, Double.NaN)
        }
}
