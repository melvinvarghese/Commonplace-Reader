package im.commonplace.reader.reader

import im.commonplace.reader.reader.epub.EpubContentPolicy
import org.junit.Assert.*
import org.junit.Test
import org.jsoup.Jsoup
import java.nio.charset.StandardCharsets

class EpubContentPolicyTest {
    @Test fun removesPublicationScriptsBridgesFramesAndUnsafeLinksButKeepsExactText() {
        val original = """<html xmlns="http://www.w3.org/1999/xhtml"><head><script src="https://evil.invalid/a.js"></script><meta http-equiv="refresh" content="0;url=https://evil.invalid"/><base href="https://evil.invalid"/></head><body onload="Android.secret()"><p>naïve café Καλημέρα</p><script>fetch('https://evil.invalid')</script><iframe src="https://evil.invalid"></iframe><object data="https://evil.invalid"></object><a href="java&#x73;cript:alert(1)" onclick="alert(1)">unsafe</a><a href="https://example.org">safe external link</a><img src="https://evil.invalid/probe.png"/><svg><script>bad()</script><a xlink:href="data:text/html,bad">bad link</a></svg></body></html>"""
        val safe = Jsoup.parse(EpubContentPolicy.sanitize(original.toByteArray()).toString(StandardCharsets.UTF_8))
        assertTrue(safe.select("script,iframe,object,base").isEmpty())
        assertTrue(safe.getElementsByAttribute("onload").isEmpty())
        assertTrue(safe.getElementsByAttribute("onclick").isEmpty())
        assertEquals("naïve café Καλημέρα", safe.selectFirst("p")?.text())
        assertEquals("https://example.org", safe.select("a").first { it.text() == "safe external link" }.attr("href"))
        assertEquals("", safe.select("a").first { it.text() == "unsafe" }.attr("href"))
        val csp = safe.select("meta[http-equiv=Content-Security-Policy]").single().attr("content")
        assertTrue(csp.contains("connect-src 'none'"))
        assertTrue(csp.contains("frame-src 'none'"))
        assertTrue(safe.select("meta[http-equiv=refresh]").isEmpty())
    }
    @Test fun rejectsOversizeAndEntityDeclarationsBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) { EpubContentPolicy.sanitize(ByteArray(2 * 1024 * 1024 + 1)) }
        assertThrows(IllegalArgumentException::class.java) { EpubContentPolicy.sanitize("<!DOCTYPE html [<!ENTITY x SYSTEM 'file:///private'>]><html>&x;</html>".toByteArray()) }
    }
}
