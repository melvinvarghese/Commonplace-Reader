import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

from pypdf import PdfReader


ROOT = Path(__file__).resolve().parents[2]
GENERATOR = ROOT / "tools/fixtures/generate_fixtures.py"


class FixtureGeneratorTest(unittest.TestCase):
    def generate(self, target):
        return subprocess.run(
            [sys.executable, str(GENERATOR), "--output", str(target)],
            text=True,
            capture_output=True,
            check=True,
        )

    def test_small_corpus_is_reproducible_and_manifest_hashes_match(self):
        with tempfile.TemporaryDirectory() as tmp:
            first, second = Path(tmp) / "first", Path(tmp) / "second"
            self.generate(first)
            self.generate(second)
            m1 = json.loads((first / "manifest.json").read_text(encoding="utf-8"))
            m2 = json.loads((second / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(m1, m2)
            self.assertEqual(10, len(m1["fixtures"]))
            for fixture in m1["fixtures"]:
                p1, p2 = first / fixture["file"], second / fixture["file"]
                self.assertEqual(p1.read_bytes(), p2.read_bytes(), fixture["id"])
                self.assertEqual(fixture["sha256"], hashlib.sha256(p1.read_bytes()).hexdigest())
                self.assertGreater(fixture["bytes"], 0)

    def test_manifest_declares_exact_selections_and_geometry_without_fabricated_shape(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "fixtures"
            self.generate(target)
            manifest = json.loads((target / "manifest.json").read_text(encoding="utf-8"))
            by_id = {item["id"]: item for item in manifest["fixtures"]}
            self.assertIn("referenceSelections", by_id["pdf-repeated"]["expected"])
            selection = by_id["pdf-repeated"]["expected"]["referenceSelections"][0]
            self.assertEqual("Repeated passage: amber lantern.", selection["text"])
            self.assertEqual(0, selection["pageIndex"])
            self.assertEqual(1, len(selection["quads"]))
            self.assertEqual(8, len(selection["quads"][0]))
            self.assertEqual("unrotated-pdf-points", selection["coordinateSpace"])
            self.assertTrue(manifest["limitations"])

    def test_pdf_fixture_semantics_match_manifest_examples(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "fixtures"
            self.generate(target)
            repeated = PdfReader(str(target / "repeated-two-page.pdf"))
            self.assertEqual(2, len(repeated.pages))
            for page in repeated.pages:
                self.assertIn("Repeated passage: amber lantern.", page.extract_text())
            rotated = PdfReader(str(target / "rotated-cropped.pdf"))
            self.assertEqual(90, rotated.pages[0].rotation)
            self.assertEqual((36, 36, 576, 756), tuple(int(v) for v in rotated.pages[0].cropbox))
            multilingual = PdfReader(str(target / "multilingual.pdf"))
            self.assertIn("café", multilingual.pages[0].extract_text())
            encrypted = PdfReader(str(target / "encrypted.pdf"))
            self.assertTrue(encrypted.is_encrypted)
            self.assertEqual(1, encrypted.decrypt("fixture-password"))
            self.assertIn("Password fixture content.", encrypted.pages[0].extract_text())
            scan = PdfReader(str(target / "image-only-scan.pdf"))
            self.assertFalse((scan.pages[0].extract_text() or "").strip())
            self.assertTrue(scan.pages[0]["/Resources"]["/XObject"])

    def test_epubs_are_well_formed_minimal_packages_with_declared_layout(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "fixtures"
            self.generate(target)
            manifest = json.loads((target / "manifest.json").read_text(encoding="utf-8"))
            by_id = {item["id"]: item for item in manifest["fixtures"]}
            for fixture_id, layout in [("epub-reflowable", "reflowable"), ("epub-fixed", "fixed-layout"), ("epub-hostile-remote", "reflowable")]:
                fixture = by_id[fixture_id]
                with zipfile.ZipFile(target / fixture["file"]) as book:
                    self.assertEqual("application/epub+zip", book.read("mimetype").decode())
                    opf = book.read("EPUB/package.opf").decode("utf-8")
                    self.assertIn(layout, fixture["expected"]["layout"])
                    if fixture_id == "epub-hostile-remote":
                        self.assertIn("http://127.0.0.1:9/", opf)

    def test_large_pdf_is_exact_100_mib_and_manifest_has_hash_and_page_count(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "fixtures"
            subprocess.run([sys.executable, str(GENERATOR), "--output", str(target), "--large"], check=True, capture_output=True, text=True)
            manifest = json.loads((target / "manifest.json").read_text(encoding="utf-8"))
            large = next(item for item in manifest["fixtures"] if item["id"] == "pdf-large-100mib")
            path = target / large["file"]
            self.assertEqual(100 * 1024 * 1024, path.stat().st_size)
            self.assertEqual(large["bytes"], path.stat().st_size)
            self.assertEqual(1, large["expected"]["pages"])
            self.assertEqual(large["sha256"], hashlib.sha256(path.read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
