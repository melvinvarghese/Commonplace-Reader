package im.commonplace.reader.reader

import android.content.Context
import android.net.Uri
import im.commonplace.reader.CommonplaceApplication
import im.commonplace.reader.data.SourceEntity
import im.commonplace.reader.imports.DocumentImporter
import im.commonplace.reader.imports.ImportResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Original synthetic fixture prose; no third-party publication or private writing. */
object ReaderProofFixture {
    const val QUOTATION = "The patient reader returns."
    const val MULTILINGUAL = "naïve café Καλημέρα"
    const val REFLECTION = "Synthetic reflection for restart proof."
    const val BOOK_TITLE = "Original reader proof"
    suspend fun import(context: Context, bytes: ByteArray = bytes()): SourceEntity {
        val repository = (context.applicationContext as CommonplaceApplication).container.readerRepository
        val result = DocumentImporter(context.contentResolver, context.filesDir, repository, openStream = { ByteArrayInputStream(bytes) }).importDocument(Uri.parse("content://reader-proof/legal-epub"))
        val id = when (result) { is ImportResult.Imported -> result.sourceId; is ImportResult.Duplicate -> result.sourceId; else -> error("Synthetic legal EPUB import failed: $result") }
        return requireNotNull(repository.getSource(id))
    }
    fun withEntry(bytes: ByteArray, entry: String, content: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
                while (true) {
                    val old = input.nextEntry ?: break
                    if (old.name != entry) { zip.putNextEntry(ZipEntry(old.name)); input.copyTo(zip); zip.closeEntry() }
                }
            }
            zip.putNextEntry(ZipEntry(entry)); zip.write(content.toByteArray()); zip.closeEntry()
        }
        return output.toByteArray()
    }
    fun bytes(endpoint: String? = null, svgSpine: Boolean = false): ByteArray {
        val firstHref = if (svgSpine) "scripted.svg" else "one.xhtml"
        val firstType = if (svgSpine) "image/svg+xml" else "application/xhtml+xml"
        val remoteManifest = endpoint?.let { "<item id=\"probe\" href=\"$it/probe.png\" media-type=\"image/png\"/>" } ?: ""
        val hostile = endpoint?.let { """<img alt="remote image probe" src="$it/image.png"/><script src="$it/authored.js"></script><script>fetch('$it/fetch');window.location='$it/script-nav';</script><iframe src="$it/frame"></iframe><p onmouseover="fetch('$it/handler')">Local hostile text</p><a id="unsafe" href="javascript:fetch('$it/unsafe')">unsafe link</a><a id="external" href="$it/explicit">external link</a><style>@import url('$it/style.css');body{background-image:url('$it/background.png')}</style>""" } ?: ""
        val files = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "EPUB/package.opf" to """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid" xml:lang="en"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:uuid:1ec06ebc-bcff-43bd-bb35-c66206d31e64</dc:identifier><dc:title>$BOOK_TITLE</dc:title><dc:language>en</dc:language><meta property="rendition:layout">reflowable</meta><meta property="dcterms:modified">2000-01-01T00:00:00Z</meta></metadata><manifest><item id="one" href="$firstHref" media-type="$firstType"/><item id="two" href="two.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>$remoteManifest</manifest><spine><itemref idref="one"/><itemref idref="two"/></spine></package>""",
            "EPUB/$firstHref" to if (svgSpine) """<svg xmlns="http://www.w3.org/2000/svg" width="600" height="800"><script>document.documentElement.setAttribute("data-authored-script","executed");fetch("$endpoint/svg-script");</script><text x="20" y="40">Original scripted SVG security fixture</text></svg>""" else """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Two occurrences</title></head><body><h1>Two occurrences</h1><p>$MULTILINGUAL</p><p id="first">First context. $QUOTATION First ending.</p><p id="second">Second context. $QUOTATION Second ending.</p>$hostile</body></html>""",
            "EPUB/two.xhtml" to """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Bookmark chapter</title></head><body><h1>Bookmark chapter</h1><p>A different place for a durable bookmark.</p></body></html>""",
            "EPUB/nav.xhtml" to """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol><li><a href="$firstHref">Two occurrences</a></li><li><a href="two.xhtml">Bookmark chapter</a></li></ol></nav></body></html>""",
        )
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, content) -> zip.putNextEntry(ZipEntry(name).apply { time = 0L }); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
        }
        return output.toByteArray()
    }
}
