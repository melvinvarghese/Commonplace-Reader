package im.commonplace.reader.reader.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.charset.StandardCharsets

/** Applied before Readium injects its own allowlisted navigator script. */
object EpubContentPolicy {
    fun allowsResource(mediaType: String?, href: String): Boolean {
        // This proof does not sanitize standalone SVG documents; never hand one to a WebView.
        val path = runCatching { java.net.URLDecoder.decode(href.substringBefore('?').substringBefore('#').replace("+", "%2B"), "UTF-8") }.getOrNull() ?: return false
        return mediaType?.substringBefore(';')?.trim()?.equals("image/svg+xml", ignoreCase = true) != true && !path.endsWith(".svg", ignoreCase = true)
    }
    fun sanitize(bytes: ByteArray): ByteArray {
        require(bytes.size <= 2 * 1024 * 1024) { "EPUB chapter exceeds the proof resource limit" }
        val html = bytes.toString(StandardCharsets.UTF_8)
        require(!html.contains("<!ENTITY", ignoreCase = true) && !Regex("<!DOCTYPE[^>]*\\[", RegexOption.IGNORE_CASE).containsMatchIn(html)) { "EPUB entity declarations are unsupported" }
        val document = Jsoup.parse(html)
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).prettyPrint(false).charset(StandardCharsets.UTF_8)
        document.select("script,iframe,frame,frameset,object,embed,applet,base,form,meta[http-equiv]").remove()
        for (element in document.allElements) {
            for (attribute in element.attributes().asList()) {
                val key = attribute.key.lowercase()
                val value = attribute.value.filterNot { it.isWhitespace() || it.code < 32 }.lowercase()
                if (key.startsWith("on") || key == "srcdoc" || (key in setOf("href", "xlink:href", "src", "action", "formaction") && (value.startsWith("javascript:") || value.startsWith("vbscript:") || (key.endsWith("href") && value.startsWith("data:"))))) {
                    element.removeAttr(attribute.key)
                }
            }
        }
        // CSP host-source syntax rejects SDK 3.4.0's underscore virtual hosts.
        // Permit only its exact trusted script path; author scripts/handlers were removed
        // above. Wildcard-host requests still fail closed in WebViewServer/container and
        // OfflineHttpClient, and the app has no INTERNET permission. No inline scripts,
        // unsafe-eval, application bridge or duplicate SDK script injection is enabled.
        document.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content",
            "default-src 'none'; script-src https://*/readium/scripts/readium-reflowable.js; style-src 'self' https://*/readium/ 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' https://*/readium/ data:; media-src 'self'; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'")
        return document.outerHtml().toByteArray(StandardCharsets.UTF_8)
    }
}
