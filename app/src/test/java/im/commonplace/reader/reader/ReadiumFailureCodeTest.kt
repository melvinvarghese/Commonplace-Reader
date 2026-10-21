package im.commonplace.reader.reader

import im.commonplace.reader.reader.epub.readiumFailureCode
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ReadiumFailureCodeTest {
    @Test fun diagnosticsRetainClassesAndCallSitesButNoMessagesOrFileNames() {
        val failure = IllegalStateException("PRIVATE quotation /private/book.epub", IOException("PRIVATE source URI"))
        failure.stackTrace = arrayOf(StackTraceElement("org.readium.Navigator", "attach", "PRIVATE-book.epub", 42))
        val code = readiumFailureCode(failure)
        assertFalse(code.contains("PRIVATE"))
        assertFalse(code.contains("/private"))
        assertTrue(code.contains("IllegalStateException"))
        assertTrue(code.contains("Navigator.attach:42"))
        assertTrue(code.contains("IOException"))
        val wrapped = org.readium.r2.shared.util.ErrorException(org.readium.r2.shared.util.DebugError("PRIVATE metadata", org.readium.r2.shared.util.ThrowableError(failure)))
        val wrappedCode = readiumFailureCode(wrapped)
        assertFalse(wrappedCode.contains("PRIVATE"))
        assertTrue(wrappedCode.contains("Navigator.attach:42"))
    }
}
