#!/usr/bin/env python3
"""Generate the original, offline Commonplace reader proof corpus."""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import shutil
import tempfile
import zipfile
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw
from pypdf import PdfReader, PdfWriter
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas

ROOT = Path(__file__).resolve().parents[2]
FONT_SOURCE = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
FONT_FILE = "DejaVuSans.ttf"
FONT_SHA256 = "ae7b7855e115a5966d8b1b3f80f254ccc117ec86f9965e202ee2940453837280"
FIXED_DATE = "D:20000101000000Z"
MIB = 1024 * 1024


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def draw_pdf(path: Path, pages: list[list[tuple[str, float, float]]], *, image=None) -> None:
    c = canvas.Canvas(str(path), pagesize=(612, 792), pageCompression=1, invariant=1)
    c.setTitle("Synthetic Commonplace fixture")
    c.setAuthor("Commonplace fixture generator")
    c.setCreator("tools/fixtures/generate_fixtures.py")
    c._doc.info.creationDate = FIXED_DATE
    c._doc.info.modDate = FIXED_DATE
    for page in pages:
        if image is not None:
            c.drawImage(ImageReader(BytesIO(image.read_bytes())), 72, 280, width=468, height=300, preserveAspectRatio=True)
        for text, x, y in page:
            c.setFont("FixtureSans", 12)
            c.drawString(x, y, text)
        c.showPage()
    c.save()


def rewrite_pdf(source: Path, target: Path, *, rotate=0, crop=None, encrypt=False) -> None:
    reader = PdfReader(str(source))
    writer = PdfWriter()
    for page in reader.pages:
        if crop:
            page.cropbox.lower_left = (crop[0], crop[1])
            page.cropbox.upper_right = (crop[2], crop[3])
        if rotate:
            page.rotate(rotate)
        writer.add_page(page)
    writer.add_metadata({
        "/Title": "Synthetic Commonplace fixture",
        "/Author": "Commonplace fixture generator",
        "/Creator": "tools/fixtures/generate_fixtures.py",
        "/CreationDate": FIXED_DATE,
        "/ModDate": FIXED_DATE,
    })
    if encrypt:
        writer.encrypt("fixture-password", "fixture-owner", use_128bit=False)
    with target.open("wb") as stream:
        writer.write(stream)


def selection(text: str, page_index: int, x: float, baseline: float, *, label=None, width=None) -> dict:
    width = width if width is not None else pdfmetrics.stringWidth(text, "FixtureSans", 12)
    # Reference quad is a declared font-box approximation, not engine-observed glyph geometry.
    return {
        "text": text,
        "pageIndex": page_index,
        "pageLabel": label,
        "pageBox": [0, 0, 612, 792],
        "rotationDegrees": 0,
        "coordinateSpace": "unrotated-pdf-points",
        "quads": [[round(x, 3), round(baseline - 2, 3), round(x + width, 3), round(baseline - 2, 3), round(x + width, 3), round(baseline + 12, 3), round(x, 3), round(baseline + 12, 3)]],
        "geometryNote": "Generator-authored conservative font-box estimate; not measured from a reader selection.",
    }


def write_epub(path: Path, *, title: str, body: str, layout: str, hostile=False) -> None:
    fixed = layout == "fixed-layout"
    rendition = '<meta property="rendition:layout">pre-paginated</meta>' if fixed else '<meta property="rendition:layout">reflowable</meta>'
    viewport = '<meta name="viewport" content="width=1200,height=1600"/>' if fixed else ""
    remote = '<img alt="remote resource probe" src="http://127.0.0.1:9/probe.png"/>' if hostile else ""
    xhtml = f'''<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>{html.escape(title)}</title>{viewport}</head>
<body><h1>{html.escape(title)}</h1><p>{html.escape(body)}</p>{remote}</body></html>'''
    container = '''<?xml version="1.0" encoding="UTF-8"?>
<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>'''
    opf = f'''<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid" xml:lang="en">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:uuid:9e1efb1e-9b8d-47c2-9119-2cb7b83a4680</dc:identifier><dc:title>{html.escape(title)}</dc:title><dc:language>en</dc:language>{rendition}</metadata>
<manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"{(' properties="remote-resources"' if hostile else '')}/>{'<item id="probe" href="http://127.0.0.1:9/probe.png" media-type="image/png"/>' if hostile else ''}</manifest>
<spine><itemref idref="chapter"/></spine></package>'''
    with zipfile.ZipFile(path, "w") as zf:
        info = zipfile.ZipInfo("mimetype", (2000, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_STORED
        zf.writestr(info, "application/epub+zip")
        for name, text in [("META-INF/container.xml", container), ("EPUB/package.opf", opf), ("EPUB/chapter.xhtml", xhtml)]:
            info = zipfile.ZipInfo(name, (2000, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            zf.writestr(info, text.encode("utf-8"), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def generate(output: Path, include_large=False) -> None:
    output.mkdir(parents=True, exist_ok=True)
    font_path = ROOT / "tools/fixtures/assets" / FONT_FILE
    if not font_path.exists():
        if not FONT_SOURCE.exists():
            raise FileNotFoundError(f"Required local DejaVu Sans font not found: {FONT_SOURCE}")
        font_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(FONT_SOURCE, font_path)
    if FONT_SHA256 and sha256(font_path) != FONT_SHA256:
        raise RuntimeError("Pinned DejaVu font digest mismatch")
    pdfmetrics.registerFont(TTFont("FixtureSans", str(font_path)))

    created: list[dict] = []
    def register(fixture_id, filename, fmt, pages, expected, license_note="Original synthetic content; no external document/image assets."):
        p = output / filename
        created.append({"id": fixture_id, "file": filename, "format": fmt, "pages": pages, "bytes": p.stat().st_size,
                        "sha256": sha256(p), "expected": expected, "license": license_note})

    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        phrase = "A quiet synthetic page for local reader testing."
        p = work / "ordinary.pdf"
        draw_pdf(p, [[(phrase, 72, 700), ("Second line checks ordinary text flow.", 72, 676)]])
        shutil.copyfile(p, output / "ordinary-text.pdf")
        register("pdf-ordinary", "ordinary-text.pdf", "PDF", 1, {"pages": 1, "pageLabels": [None], "exactText": [phrase], "referenceSelections": [selection(phrase, 0, 72, 700)]})

        repeated = "Repeated passage: amber lantern."
        p = work / "repeated.pdf"
        draw_pdf(p, [[(repeated, 72, 700)], [(repeated, 72, 700)]])
        shutil.copyfile(p, output / "repeated-two-page.pdf")
        register("pdf-repeated", "repeated-two-page.pdf", "PDF", 2, {"pages": 2, "pageLabels": [None, None], "exactText": [repeated, repeated], "referenceSelections": [selection(repeated, 0, 72, 700), selection(repeated, 1, 72, 700)]})

        rotated_text = "Crop and rotation reference phrase."
        p = work / "rotated-source.pdf"
        draw_pdf(p, [[(rotated_text, 72, 700)]])
        rewrite_pdf(p, output / "rotated-cropped.pdf", rotate=90, crop=(36, 36, 576, 756))
        rot_sel = selection(rotated_text, 0, 72, 700)
        rot_sel.update({"pageBox": [36, 36, 576, 756], "rotationDegrees": 90, "geometryNote": "Source-space estimate remains unrotated; crop/rotation metadata is explicit. Not reader-observed."})
        register("pdf-rotated-cropped", "rotated-cropped.pdf", "PDF", 1, {"pages": 1, "pageLabels": [None], "pageBox": [36, 36, 576, 756], "rotationDegrees": 90, "exactText": [rotated_text], "referenceSelections": [rot_sel]})

        multilingual = "English café — Ελληνικά — Русский текст."
        p = work / "multilingual.pdf"
        draw_pdf(p, [[(multilingual, 72, 700)]])
        shutil.copyfile(p, output / "multilingual.pdf")
        register("pdf-multilingual", "multilingual.pdf", "PDF", 1, {"pages": 1, "pageLabels": [None], "exactText": [multilingual], "referenceSelections": [selection(multilingual, 0, 72, 700)]}, "Original generated text; embedded DejaVu Sans font, see tools/fixtures/assets/DejaVu-LICENSE.txt.")

        # An original procedural raster provides image pixels only; the PDF page has no text operators.
        scan = work / "scan.png"
        im = Image.new("RGB", (1200, 900), "#f7f1df")
        d = ImageDraw.Draw(im)
        d.rectangle((45, 45, 1155, 855), outline="#222222", width=5)
        d.text((110, 180), "IMAGE-ONLY SCAN SAMPLE", fill="#111111")
        d.text((110, 250), "This wording is pixels, not PDF text.", fill="#111111")
        im.save(scan, format="PNG", optimize=False)
        p = work / "scan.pdf"
        draw_pdf(p, [[]], image=scan)
        shutil.copyfile(p, output / "image-only-scan.pdf")
        register("pdf-image-only", "image-only-scan.pdf", "PDF", 1, {"pages": 1, "pageLabels": [None], "exactText": [], "referenceSelections": [], "content": "single raster image; intentionally no OCR/text layer"})

        p = work / "encrypted-source.pdf"
        draw_pdf(p, [[("Password fixture content.", 72, 700)]])
        rewrite_pdf(p, output / "encrypted.pdf", encrypt=True)
        register("pdf-encrypted", "encrypted.pdf", "PDF", 1, {"pages": 1, "password": "fixture-password", "pageLabels": [None], "exactText": ["Password fixture content."]})

        malformed = b"%PDF-1.4\n1 0 obj << /Type /Catalog /Pages 2 0 R >>\n2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >>\n3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\ntruncated-xref"
        (output / "malformed.pdf").write_bytes(malformed)
        register("pdf-malformed", "malformed.pdf", "PDF", None, {"pages": None, "expectedOutcome": "parser rejection or explicit malformed-document error", "reason": "intentionally truncated before a valid xref/trailer"})

        reflow_text = "A reflowable original chapter preserves naïve café and Greek: Καλημέρα."
        write_epub(output / "reflowable.epub", title="Synthetic Reflow", body=reflow_text, layout="reflowable")
        register("epub-reflowable", "reflowable.epub", "EPUB", None, {"layout": "reflowable", "exactText": [reflow_text], "referenceSelections": [{"text": reflow_text, "cfi": None, "note": "No reader-generated CFI; source text only."}]})
        fixed_text = "Fixed-page original layout sample."
        write_epub(output / "fixed-layout.epub", title="Synthetic Fixed Layout", body=fixed_text, layout="fixed-layout")
        register("epub-fixed", "fixed-layout.epub", "EPUB", None, {"layout": "fixed-layout", "exactText": [fixed_text], "referenceSelections": []})
        hostile_text = "Hostile remote-resource probe; the referenced URL is inert unless a reader fetches it."
        write_epub(output / "hostile-remote.epub", title="Synthetic Remote Probe", body=hostile_text, layout="reflowable", hostile=True)
        register("epub-hostile-remote", "hostile-remote.epub", "EPUB", None, {"layout": "reflowable", "exactText": [hostile_text], "remoteResource": "http://127.0.0.1:9/probe.png", "expectedOutcome": "reader must not fetch remote resource by default"})

        if include_large:
            large_temp = work / "large.pdf"
            draw_pdf(large_temp, [[("100 MiB local stress fixture; one page.", 72, 700)]])
            large_path = output / "large-100mib.local.pdf"
            target_size = 100 * MIB
            if large_temp.stat().st_size > target_size:
                raise RuntimeError("base PDF exceeds exact 100 MiB target")
            with large_temp.open("rb") as src, large_path.open("wb") as dst:
                shutil.copyfileobj(src, dst)
                remaining = target_size - large_temp.stat().st_size
                padding = b" " * (1024 * 1024)
                while remaining:
                    block = padding[:min(remaining, len(padding))]
                    dst.write(block)
                    remaining -= len(block)
            register("pdf-large-100mib", "large-100mib.local.pdf", "PDF", 1, {"pages": 1, "pageLabels": [None], "exactBytes": target_size, "padding": "ASCII whitespace after the PDF EOF marker; parser-defined validity only"})

    manifest = {
        "schemaVersion": 1,
        "generator": "tools/fixtures/generate_fixtures.py",
        "recipe": "Python 3.11+; reportlab==5.0.1; Pillow==12.3.0; pypdf==6.18.1; deterministic original content; ZIP timestamps fixed to 2000-01-01.",
        "generatorDependencies": [
            {"name": "reportlab", "version": "5.0.1", "license": "BSD License", "source": "https://pypi.org/project/reportlab/5.0.1/"},
            {"name": "Pillow", "version": "12.3.0", "license": "MIT-CMU", "source": "https://pypi.org/project/Pillow/12.3.0/"},
            {"name": "pypdf", "version": "6.18.1", "license": "BSD-3-Clause", "source": "https://pypi.org/project/pypdf/6.18.1/"}
        ],
        "font": {"file": "../../../../../tools/fixtures/assets/DejaVuSans.ttf", "sha256": sha256(font_path), "license": "Bitstream Vera / DejaVu; complete text in tools/fixtures/assets/DejaVu-LICENSE.txt", "source": "installed system DejaVu Sans copied locally; no web font asset downloaded"},
        "limitations": [
            "Expected PDF quads are manually authored conservative font-box approximations, not output from AndroidX or another reader; they are references for geometry plumbing only.",
            "Reference PDF strings are selected as whole single-line runs. Exact shaping, glyph-cluster boundaries, ligatures, bidi behavior, and platform text extraction are engine/device dependent and intentionally not asserted.",
            "Multilingual text covers Latin accents, Greek and Cyrillic only; this is not a language coverage benchmark.",
            "Page labels are null because no genuine printed labels are authored; pageIndex is zero-based physical order.",
            "The image-only fixture has no OCR text and cannot establish a reader's scan diagnosis; absence of text alone is not proof of scanning.",
            "EPUB reference CFI is null until a reader produces a native locator; text expectations are package contents, not proof of selection behavior.",
            "The hostile EPUB has a loopback URL at port 9; no server is started and this fixture itself does not test network policy.",
            "Encrypted fixture password is public test data: fixture-password. No password is confidential or persisted.",
            "Malformed fixture is intentionally truncated; parser behavior is implementation-specific.",
            "The large fixture is locally generated only when --large is passed and must not be committed. Whitespace padding after EOF preserves exact size but parser tolerance is not guaranteed."
        ],
        "fixtures": created,
    }
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "app/src/androidTest/assets/fixtures")
    parser.add_argument("--large", action="store_true", help="also write exact 100 MiB local-only PDF")
    args = parser.parse_args()
    generate(args.output.resolve(), args.large)


if __name__ == "__main__":
    main()
