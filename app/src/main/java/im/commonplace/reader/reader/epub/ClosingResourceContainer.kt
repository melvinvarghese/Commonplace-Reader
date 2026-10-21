package im.commonplace.reader.reader.epub

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.resource.Resource

/** WebView may retain lazy response streams after the publication is closed. */
internal class ClosingResourceContainer(private val delegate: Container<Resource>) : Container<Resource> by delegate {
    private val closed = AtomicBoolean(false)

    override fun get(url: Url): Resource? {
        if (closed.get()) return null
        val resource = try { delegate[url] }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: IllegalStateException) {
            if (!closed.get()) throw failure
            null
        } ?: return null
        if (closed.get()) return null
        return object : Resource by resource {
            override suspend fun properties(): Try<Resource.Properties, ReadError> = guarded { resource.properties() }
            override suspend fun length(): Try<Long, ReadError> = guarded { resource.length() }
            override suspend fun read(range: LongRange?): Try<ByteArray, ReadError> = guarded { resource.read(range) }
        }
    }

    private suspend fun <T> guarded(read: suspend () -> Try<T, ReadError>): Try<T, ReadError> {
        currentCoroutineContext().ensureActive()
        if (closed.get()) return closedFailure()
        return try {
            val result = read()
            currentCoroutineContext().ensureActive()
            if (closed.get()) closedFailure() else result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: IllegalStateException) {
            // SDK 3.4.0 ZIP reads throw this rather than a typed ReadError after close.
            if (!closed.get()) throw failure
            closedFailure()
        }
    }

    private fun <T> closedFailure(): Try<T, ReadError> =
        Try.failure(ReadError.Decoding(IOException("Publication session is closed")))

    override fun close() {
        if (closed.compareAndSet(false, true)) delegate.close()
    }
}
