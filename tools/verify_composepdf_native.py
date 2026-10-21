#!/usr/bin/env python3
"""Retrieve checksum-pinned PDFium Android release assets without upstream build hooks."""

import argparse
import hashlib
import os
import re
import tarfile
import tempfile
import urllib.request
from pathlib import Path, PurePosixPath


VERSION = "158.0.8111.0"
RELEASE_URL = "https://github.com/bblanchon/pdfium-binaries/releases/download/chromium%2F8111"
ASSETS = {
    "pdfium-android-arm64.tgz": ("arm64-v8a", "727cb2645ece75d28d0b72f2ec05b82858ca3bfb6712cd333ffc248807016e2c"),
    "pdfium-android-arm.tgz": ("armeabi-v7a", "fc6a54e286a46a52e45d5bd52803f59f3b86dfcff73ba8f458939872f5466de3"),
    "pdfium-android-x64.tgz": ("x86_64", "e2c31cffe42ee3a34e2b9d6c6c254ddf5da1541ce2d82216171e9354c86f0a7c"),
    "pdfium-android-x86.tgz": ("x86", "7bb3c95e17450a077658a8128ba074dfdc040249d6f33c5d065d2146b3a82621"),
}
LICENSES = {
    "abseil.txt", "agg23.txt", "catapult.txt", "cpu_features.txt",
    "dragonbox-Apache2-LLVM.txt", "dragonbox-Boost.txt", "fast_float.txt",
    "freetype.txt", "harfbuzz.txt", "highway.txt", "icu.txt", "lcms.txt",
    "libjpeg_turbo.ijg", "libjpeg_turbo.md", "libopenjpeg.txt", "libpng.txt",
    "libunwind.txt", "llvm-libc.txt", "pdfium.txt", "simdutf.txt", "zlib.txt",
}


class VerificationError(Exception):
    """An artifact did not match the pinned identity or safe archive layout."""


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _safe_member_name(name):
    if "\\" in name or name.startswith("/"):
        raise VerificationError(f"unsafe archive path: {name!r}")
    parts = name.rstrip("/").split("/")
    if not name.rstrip("/") or any(part in ("", ".", "..") for part in parts):
        raise VerificationError(f"unsafe archive path: {name!r}")
    return PurePosixPath(*parts).as_posix()


def extract_verified_archive(archive_path, destination, expected_sha256):
    """Verify a pinned TGZ, then extract only VERSION, 21 licenses and libpdfium.so."""
    archive_path = Path(archive_path)
    actual = sha256_file(archive_path)
    if actual != expected_sha256:
        raise VerificationError(f"SHA-256 mismatch for {archive_path.name}: expected {expected_sha256}, got {actual}")

    files = {}
    seen = set()
    with tarfile.open(archive_path, mode="r:gz") as bundle:
        for member in bundle.getmembers():
            name = _safe_member_name(member.name)
            if name in seen:
                raise VerificationError(f"duplicate archive path: {name}")
            seen.add(name)
            if not (member.isfile() or member.isdir()):
                raise VerificationError(f"unsupported archive entry type: {name}")

            if member.isdir():
                continue
            if name.startswith("licenses/") and name.removeprefix("licenses/") not in LICENSES:
                raise VerificationError(f"unexpected license path: {name}")
            if name.startswith("lib/") and name != "lib/libpdfium.so":
                raise VerificationError(f"unexpected library path: {name}")

            if name == "VERSION" or name == "lib/libpdfium.so" or name.startswith("licenses/"):
                source = bundle.extractfile(member)
                if source is None:
                    raise VerificationError(f"unreadable archive entry: {name}")
                files[name] = source.read()

    required = {"VERSION", "lib/libpdfium.so"} | {f"licenses/{name}" for name in LICENSES}
    missing = sorted(required - files.keys())
    if missing:
        raise VerificationError(f"archive missing required entries: {', '.join(missing)}")
    version = dict(re.findall(rb"^(MAJOR|MINOR|BUILD|PATCH)=(\d+)$", files["VERSION"].replace(b"\r", b""), re.M))
    if version != {b"MAJOR": b"158", b"MINOR": b"0", b"BUILD": b"8111", b"PATCH": b"0"}:
        raise VerificationError(f"unexpected PDFium archive version: {files['VERSION']!r}")

    destination = Path(destination)
    for name, content in files.items():
        relative = Path(name)
        target = destination / relative
        if not target.resolve().is_relative_to(destination.resolve()):
            raise VerificationError(f"destination symlink escape: {target}")
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.is_symlink():
            raise VerificationError(f"refusing to overwrite symlink: {target}")
        target.write_bytes(content)


def download_asset(filename, expected_sha256, archive_dir):
    archive_dir.mkdir(parents=True, exist_ok=True)
    target = archive_dir / filename
    if target.exists() and sha256_file(target) == expected_sha256:
        return target

    fd, temporary_name = tempfile.mkstemp(prefix=f".{filename}.", suffix=".part", dir=archive_dir)
    temporary_path = Path(temporary_name)
    digest = hashlib.sha256()
    try:
        with os.fdopen(fd, "wb") as output, urllib.request.urlopen(f"{RELEASE_URL}/{filename}", timeout=60) as response:
            while chunk := response.read(1024 * 1024):
                digest.update(chunk)
                output.write(chunk)
        actual = digest.hexdigest()
        if actual != expected_sha256:
            raise VerificationError(f"SHA-256 mismatch for {filename}: expected {expected_sha256}, got {actual}")
        os.replace(temporary_path, target)
    except Exception:
        temporary_path.unlink(missing_ok=True)
        raise
    return target


def retrieve(retrieve_dir):
    retrieve_dir = Path(retrieve_dir)
    archives = retrieve_dir / "archives"
    extracted = retrieve_dir / "extracted"
    verified = []
    for filename, (_, digest) in ASSETS.items():
        verified.append((filename, download_asset(filename, digest, archives), digest))
    for filename, archive, digest in verified:
        abi, _ = ASSETS[filename]
        extract_verified_archive(archive, extracted / abi, digest)
        print(f"verified {filename} ({abi}): sha256={digest}")
    print(f"Verified artifacts and extracted libraries/notices: {extracted}")


def main():
    parser = argparse.ArgumentParser(description=f"Retrieve verified PDFium {VERSION} Android release assets")
    parser.add_argument("--retrieve-dir", required=True, type=Path, help="directory for verified archives and selected extracted files")
    args = parser.parse_args()
    try:
        retrieve(args.retrieve_dir)
    except (OSError, tarfile.TarError, VerificationError, urllib.error.URLError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
