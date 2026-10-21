#!/usr/bin/env python3
"""Security regression tests for the pinned PDFium artifact retriever."""

import hashlib
import io
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import verify_composepdf_native as verifier


class VerifiedArchiveTests(unittest.TestCase):
    def test_existing_parent_symlink_cannot_redirect_verified_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / 'verified.tgz'
            with tarfile.open(archive, mode='w:gz') as bundle:
                contents = {'VERSION': b'MAJOR=158\nMINOR=0\nBUILD=8111\nPATCH=0\n', 'lib/libpdfium.so': b'library'}
                contents.update({f'licenses/{name}': b'notice' for name in verifier.LICENSES})
                for name, body in contents.items():
                    member = tarfile.TarInfo(name)
                    member.size = len(body)
                    bundle.addfile(member, io.BytesIO(body))
            outside = root / 'outside'
            outside.mkdir()
            destination = root / 'output'
            destination.mkdir()
            (destination / 'lib').symlink_to(outside, target_is_directory=True)
            with self.assertRaisesRegex(verifier.VerificationError, 'symlink|escape'):
                verifier.extract_verified_archive(archive, destination, verifier.sha256_file(archive))
            self.assertFalse((outside / 'libpdfium.so').exists())

    def test_checksum_mismatch_is_rejected_before_opening_archive(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "payload.tgz"
            archive.write_bytes(b"not a tarball")
            destination = root / "output"

            with patch.object(verifier.tarfile, "open", side_effect=AssertionError("opened unverified archive")):
                with self.assertRaisesRegex(verifier.VerificationError, "SHA-256 mismatch"):
                    verifier.extract_verified_archive(archive, destination, "0" * 64)

            self.assertFalse(destination.exists())

    def test_path_traversal_member_is_rejected_without_writing_outside_destination(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "traversal.tgz"
            payload = io.BytesIO()
            with tarfile.open(fileobj=payload, mode="w:gz") as bundle:
                member = tarfile.TarInfo("../escaped.txt")
                body = b"must not escape"
                member.size = len(body)
                bundle.addfile(member, io.BytesIO(body))
            archive.write_bytes(payload.getvalue())
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            destination = root / "output"

            with self.assertRaisesRegex(verifier.VerificationError, "unsafe archive path"):
                verifier.extract_verified_archive(archive, destination, digest)

            self.assertFalse((root / "escaped.txt").exists())
            self.assertFalse(destination.exists())


if __name__ == "__main__":
    unittest.main()
