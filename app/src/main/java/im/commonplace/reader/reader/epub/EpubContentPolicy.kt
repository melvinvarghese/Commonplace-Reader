package im.commonplace.reader.reader.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.charset.StandardCharsets

/** Applied before Readium injects its own allowlisted navigator script. */
object EpubContentPolicy {
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
        // No publication-origin scripts or inline script allowance. evaluateJavascript is
        // used internally by the supported navigator; no application bridge is installed.
        document.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content",
            "default-src 'none'; script-src https://readium_assets; style-src https://readium_assets https://readium_package 'unsafe-inline'; img-src https://readium_package data: blob:; font-src https://readium_assets https://readium_package data:; media-src https://readium_package; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'")
        return document.outerHtml().toByteArray(StandardCharsets.UTF_8)
    }
}
