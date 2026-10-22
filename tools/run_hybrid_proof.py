#!/usr/bin/env python3
"""One-shot disposable-emulator proof for hybrid PDF.js passage recovery."""
import argparse
import io
import json
import os
import re
import subprocess
import sys
import tarfile
import time
import uuid
from pathlib import Path

REPORTS = Path("build/reports/hybrid-proof")
PACKAGE = "im.commonplace.reader.hybrid"
PROBE = "im.commonplace.reader.reader.pdf.HybridProofTest"
RECOVERY_PROBE = "im.commonplace.reader.reader.pdf.HybridRecoveryProbe"
RUNNER = "androidx.test.runner.AndroidJUnitRunner"
MARKER = "files/hybrid-recovery-ready.json"


def run_proof(api_label="api-unknown"):
    if os.environ.get("COMMONPLACE_DISPOSABLE_EMULATOR") != "1":
        raise RuntimeError("COMMONPLACE_DISPOSABLE_EMULATOR=1 is required")

    folder = REPORTS / api_label
    folder.mkdir(parents=True, exist_ok=True)
    log = folder / "hybrid-proof.txt"
    run_id = str(uuid.uuid4())
    failures = []
    diagnostic_errors = []

    def record_failure(message):
        failures.append(message)
        print(f"hybrid proof: {message}", file=sys.stderr, flush=True)

    def command(args, timeout=30, *, binary=False):
        full = ["adb", *args]
        try:
            result = subprocess.run(full, capture_output=True, text=not binary,
                                    timeout=timeout, check=False)
        except (OSError, subprocess.TimeoutExpired) as error:
            empty = b"" if binary else ""
            result = subprocess.CompletedProcess(full, 127, empty, str(error))
        with log.open("a") as output:
            stdout = (f"<binary {len(result.stdout or b'')} bytes>" if binary
                      else result.stdout or "")
            stderr = result.stderr.decode(errors="replace") if isinstance(result.stderr, bytes) else result.stderr
            output.write("$ " + " ".join(full) + f"\nexit={result.returncode}\n"
                         f"{stdout}\n{stderr or ''}\n")
        return result

    def successful(result, description):
        if result.returncode != 0:
            record_failure(description)
            return False
        return True

    def instrument(test_class, expected_count, *, recovery=False):
        args = ["shell", "am", "instrument", "-w", "-e", "class", test_class]
        if recovery:
            args += ["-e", "hybridRecovery", "true", "-e", "hybridRun", run_id]
        result = command(args + [f"{PACKAGE}.test/{RUNNER}"], timeout=180)
        transcript = (result.stdout or "") + "\n" + (result.stderr or "")
        summaries = re.findall(r"(?m)^OK \((\d+) tests?\)\s*$", transcript)
        failed = any(token in transcript for token in
                     ("FAILURES!!!", "INSTRUMENTATION_FAILED", "INSTRUMENTATION_ABORTED"))
        failed |= re.search(r"INSTRUMENTATION_STATUS_CODE: -(?:2|3|4)\b", transcript) is not None
        skipped = re.search(r"\b(?:skip|skipped)\b", transcript, re.IGNORECASE) is not None
        if (result.returncode != 0 or summaries != [str(expected_count)] or failed or skipped):
            record_failure(f"{test_class} did not execute exactly {expected_count} passing, unskipped case(s)")
            return False
        return True

    def diagnostic_error(message):
        diagnostic_errors.append(message)
        print(f"hybrid proof diagnostic: {message}", file=sys.stderr, flush=True)

    def capture_text(args, name):
        result = command(args, timeout=30)
        text = result.stdout or ""
        if result.returncode != 0 or not text.strip():
            diagnostic_error(f"could not capture nonempty {name}")
            return ""
        (folder / name).write_text(text)
        return text

    def capture_archive():
        result = command(["exec-out", "run-as", PACKAGE, "tar", "-cf", "-", "files"],
                         timeout=60, binary=True)
        payload = result.stdout or b""
        try:
            if result.returncode != 0 or not payload:
                raise tarfile.TarError("capture is empty or adb failed")
            with tarfile.open(fileobj=io.BytesIO(payload), mode="r:*") as archive:
                if not archive.getmembers():
                    raise tarfile.TarError("archive contains no app files")
            (folder / "app-files.tar").write_bytes(payload)
        except (OSError, tarfile.TarError) as error:
            diagnostic_error(f"app files tar capture is invalid: {error}")

    def capture_failure_screenshot():
        result = command(["exec-out", "screencap", "-p"], timeout=30, binary=True)
        payload = result.stdout or b""
        if result.returncode != 0 or not payload.startswith(b"\x89PNG\r\n\x1a\n"):
            diagnostic_error("failure screenshot capture is empty or not a PNG")
            return
        (folder / "failure.png").write_bytes(payload)

    print(f"{api_label}: one-shot hybrid PDF.js passage recovery proof (run {run_id})", flush=True)
    for apk in (
            "app/build/outputs/apk/hybrid/debug/app-hybrid-debug.apk",
            "app/build/outputs/apk/androidTest/hybrid/debug/app-hybrid-debug-androidTest.apk"):
        successful(command(["install", "-r", apk], timeout=90), f"could not install {apk}")

    successful(command(["shell", "run-as", PACKAGE, "rm", "-f", MARKER]),
               "could not clear the previous recovery marker")
    focused_ok = instrument(PROBE, 4)
    stage_ok = instrument(f"{RECOVERY_PROBE}#stageSelectedPassage", 1, recovery=True)

    marker_result = command(["shell", "run-as", PACKAGE, "/system/bin/cat", MARKER])
    marker_ok = False
    if successful(marker_result, "hybrid recovery marker is missing"):
        try:
            marker = json.loads(marker_result.stdout or "")
            marker_ok = (stage_ok and marker.get("runId") == run_id and
                         isinstance(marker.get("sourceId"), str) and bool(marker["sourceId"].strip()))
        except (json.JSONDecodeError, AttributeError, KeyError):
            marker_ok = False
        if not marker_ok:
            record_failure("hybrid recovery marker does not identify this run and a source")

    launched = successful(command(["shell", "am", "start", "-W", "-n",
                                   f"{PACKAGE}/im.commonplace.reader.MainActivity"], timeout=60),
                          "staged hybrid app launch failed")
    live = command(["shell", "pidof", PACKAGE])
    previous_pid = (live.stdout or "").strip()
    if not successful(live, "no live hybrid app process was confirmed") or not previous_pid:
        record_failure("empty live hybrid app PID")
        previous_pid = ""

    successful(command(["shell", "am", "force-stop", PACKAGE]), "hybrid app force-stop failed")
    dead = command(["shell", "pidof", PACKAGE])
    if dead.returncode != 1 or (dead.stdout or "").strip() or (dead.stderr or "").strip():
        record_failure("hybrid app process disappearance was not confirmed")

    successful(command(["shell", "monkey", "-p", PACKAGE, "1"], timeout=60),
               "hybrid launcher relaunch failed")
    fresh_pid = ""
    deadline = time.monotonic() + 10
    while True:
        fresh = command(["shell", "pidof", PACKAGE], timeout=2)
        candidate = (fresh.stdout or "").strip()
        if fresh.returncode == 0 and not (fresh.stderr or "").strip():
            fresh_pid = candidate
            break
        if fresh.returncode != 1 or candidate or (fresh.stderr or "").strip() or time.monotonic() >= deadline:
            break
        time.sleep(0.25)
    if not fresh_pid or not previous_pid or fresh_pid == previous_pid:
        record_failure("fresh launcher process was not confirmed after process death")

    verify_ok = instrument(f"{RECOVERY_PROBE}#verifyRestoredPassage", 1, recovery=True)
    sdk = command(["shell", "getprop", "ro.build.version.sdk"])
    actual_sdk = (sdk.stdout or "").strip() if successful(sdk, "could not read actual emulator SDK") else ""
    expected = re.search(r"\d+", api_label)
    expected_sdk = expected.group(0) if expected else ""
    if not expected_sdk:
        record_failure(f"api-label {api_label!r} does not identify an Android API level")
    elif not actual_sdk:
        record_failure("emulator reported an empty actual SDK")
    elif actual_sdk != expected_sdk:
        record_failure(f"API mismatch: label {api_label} expects SDK {expected_sdk}, emulator reports {actual_sdk}")

    capture_text(["shell", "getprop"], "properties.txt")
    webview = capture_text(["shell", "dumpsys", "webviewupdate"], "webview-version.txt")
    version_match = re.search(r"Current WebView package \(name, version\):\s*\([^,]+,\s*([^)]+)\)",
                              webview, re.IGNORECASE)
    webview_version = version_match.group(1).strip() if version_match else ""
    if not webview_version:
        diagnostic_error("could not identify the installed WebView version")
    capture_archive()
    if failures:
        capture_failure_screenshot()
    metadata = {
        "apiLabel": api_label,
        "actualSdk": actual_sdk,
        "expectedSdk": expected_sdk,
        "webviewVersion": webview_version,
        "runId": run_id,
        "package": PACKAGE,
        "focusedPassed": focused_ok,
        "stagePassed": stage_ok,
        "verifyPassed": verify_ok,
        "markerValid": marker_ok,
        "launched": launched,
        "preStopPid": previous_pid,
        "freshPid": fresh_pid,
        "failures": failures,
        "diagnosticErrors": diagnostic_errors,
    }
    (folder / "metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
    if failures:
        return False
    print(f"{api_label}: run-bound marker, force-stop, PID disappearance, fresh launcher PID and exact one-case assertions confirmed", flush=True)
    return True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-label", default=os.environ.get("ANDROID_API", "api-unknown"))
    args = parser.parse_args()
    try:
        return 0 if run_proof(args.api_label) else 1
    except Exception as error:
        print(f"hybrid proof could not run: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
