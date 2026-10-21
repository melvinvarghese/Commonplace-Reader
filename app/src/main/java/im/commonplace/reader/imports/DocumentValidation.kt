package im.commonplace.reader.imports

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Xml
import im.commonplace.reader.reader.DocumentFormat
import java.io.File
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser

/** Import sanity checks only, not a full conformance validator or a reader capability promise. */
internal object DocumentValidation {
    private const val MAX_MEMBERS = 10_000
    private const val MAX_MEMBER = 64L * 1024 * 1024
    private const val MAX_EXPANSION = 512L * 1024 * 1024
    private const val MAX_XML = 2L * 1024 * 1024

    suspend fun validate(file: File): DocumentFormat {
        val prefix = file.inputStream().use { input -> ByteArray(5).also { input.read(it) } }
        return try {
            when {
                prefix.contentEquals("%PDF-".toByteArray()) -> {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { pdf ->
                            if (pdf.pageCount < 1) reject("PDF has no pages.")
                        }
                    }
                    DocumentFormat.PDF
                }
                prefix[0] == 0x50.toByte() && prefix[1] == 0x4b.toByte() -> {
                    validateEpub(file)
                    DocumentFormat.EPUB
                }
                else -> reject("This file is not a supported PDF or EPUB.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unsupported: UnsupportedDocument) {
            throw unsupported
        } catch (_: SecurityException) {
            reject("Password-protected or restricted documents are not supported by this import proof.")
        } catch (_: Exception) {
            reject("The document is malformed or cannot be safely validated.")
        }
    }

    private suspend fun validateEpub(file: File) {
        ZipFile(file).use { zip ->
            if (zip.size() > MAX_MEMBERS) reject("EPUB exceeds the 10,000-member limit.")
            val names = HashSet<String>()
            var declared = 0L
            var actual = 0L
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val entry = entries.nextElement()
                safePath(entry.name.trimEnd('/'))
                if (!names.add(entry.name)) reject("EPUB contains duplicate paths.")
                if (entry.size < 0 || entry.size > MAX_MEMBER) reject("EPUB member exceeds the 64 MiB limit.")
                declared += entry.size
                if (declared > MAX_EXPANSION) reject("EPUB exceeds the 512 MiB expansion limit.")
                var count = 0L
                val crc = CRC32()
                zip.getInputStream(entry).use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = stream.read(buffer)
                        if (read < 0) break
                        count += read; actual += read
                        if (count > MAX_MEMBER || actual > MAX_EXPANSION) reject("EPUB expanded data exceeds safe limits.")
                        crc.update(buffer, 0, read)
                    }
                }
                if (count != entry.size || crc.value != entry.crc) reject("EPUB member integrity check failed.")
            }
            val mimetype = zip.getEntry("mimetype") ?: reject("EPUB mimetype is missing.")
            if (mimetype.size != 20L || zip.getInputStream(mimetype).use { it.readBytes().decodeToString() } != "application/epub+zip") {
                reject("EPUB mimetype is invalid.")
            }
            fun xml(path: String): List<Element> {
                val entry = zip.getEntry(path) ?: reject("EPUB references a missing publication member.")
                if (entry.size > MAX_XML) reject("EPUB XML exceeds the 2 MiB structural parsing limit.")
                return zip.getInputStream(entry).use { parseXml(it) }
            }
            val container = xml("META-INF/container.xml")
            if (container.firstOrNull()?.name != "container" || container.first().namespace != "urn:oasis:names:tc:opendocument:xmlns:container") {
                reject("Invalid EPUB container.")
            }
            val packagePath = container.firstOrNull { it.name == "rootfile" && it.attributes["media-type"] == "application/oebps-package+xml" }
                ?.attributes?.get("full-path") ?: reject("EPUB package is missing.")
            safePath(packagePath)
            val opf = xml(packagePath)
            if (opf.firstOrNull()?.name != "package" || opf.first().namespace != "http://www.idpf.org/2007/opf") reject("Invalid EPUB package.")
            if ("META-INF/encryption.xml" in names || "META-INF/rights.xml" in names) reject("Encrypted EPUB resources are not supported by this import proof.")
            val items = opf.filter { it.name == "item" }.associateBy { it.attributes["id"] }
            val spine = opf.filter { it.name == "itemref" }
            if (spine.isEmpty()) reject("EPUB has no reading order.")
            val parent = packagePath.substringBeforeLast('/', "")
            for (reference in spine) {
                currentCoroutineContext().ensureActive()
                val item = items[reference.attributes["idref"]] ?: reject("EPUB reading order is invalid.")
                val href = item.attributes["href"] ?: reject("EPUB reading order has no resource.")
                safePath(href)
                val path = if (parent.isEmpty()) href else "$parent/$href"
                if (item.attributes["media-type"] != "application/xhtml+xml") reject("Only XHTML EPUB reading-order resources are supported by this import proof.")
                val chapter = xml(path)
                if (chapter.firstOrNull()?.name != "html" || chapter.first().namespace != "http://www.w3.org/1999/xhtml") reject("Invalid EPUB XHTML resource.")
            }
        }
    }

    private data class Element(val name: String, val namespace: String?, val attributes: Map<String, String>)

    private fun parseXml(stream: InputStream): List<Element> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(stream, null)
        val elements = mutableListOf<Element>()
        var tokens = 0
        while (true) {
            val token = parser.nextToken()
            if (++tokens > 100_000 || parser.depth > 128) reject("EPUB XML exceeds structural complexity limits.")
            if (token == XmlPullParser.DOCDECL || token == XmlPullParser.ENTITY_REF) reject("EPUB XML declarations/entities are not supported.")
            if (token == XmlPullParser.END_DOCUMENT) break
            if (token == XmlPullParser.START_TAG) {
                val attributes = (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
                elements.add(Element(parser.name, parser.namespace, attributes))
            }
        }
        return elements
    }

    private fun safePath(path: String) {
        if (path.isBlank() || path.length > 1024 || path.startsWith('/') || path.contains('\\') ||
            path.contains(':') || path.contains('%') || path.contains('\u0000') ||
            path.split('/').any { it.isEmpty() || it == ".." || it == "." }) reject("EPUB contains an unsafe resource path.")
    }
    private fun reject(reason: String): Nothing = throw UnsupportedDocument(reason)
}
