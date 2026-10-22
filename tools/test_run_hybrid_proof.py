import io
import json
import os
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import run_hybrid_proof as runner


class HybridProofRunnerTest(unittest.TestCase):
    """Exercise adb orchestration only; these fixtures do not test PDF.js behavior."""

    run_id = "11111111-2222-4333-8444-555555555555"

    @staticmethod
    def tar_bytes():
        data = io.BytesIO()
        with tarfile.open(fileobj=data, mode="w") as archive:
            contents = b"{\"snapshot\":true}"
            info = tarfile.TarInfo("files/hybrid-proof/snapshot.json")
            info.size = len(contents)
            archive.addfile(info, io.BytesIO(contents))
        return data.getvalue()

    def exercise(self, *, focused="OK (4 tests)\n", stage="OK (1 test)\n",
                 verify="OK (1 test)\n", marker=None, pids=None, fresh="5678",
                 sdk="37\n", binary=None, env="1"):
        calls = []
        pid_responses = iter(pids or [
            subprocess.CompletedProcess([], 0, "1234\n", ""),
            subprocess.CompletedProcess([], 1, "", ""),
            subprocess.CompletedProcess([], 0, fresh + "\n", ""),
        ])
        marker_json = marker if marker is not None else json.dumps(
            {"runId": self.run_id, "sourceId": "fixture-source"})
        transcripts = {
            f"{runner.PROBE}": focused,
            f"{runner.RECOVERY_PROBE}#stageSelectedPassage": stage,
            f"{runner.RECOVERY_PROBE}#verifyRestoredPassage": verify,
        }
        binary = binary or {"tar": self.tar_bytes(), "screenshot": b"\x89PNG\r\n\x1a\nproof"}

        def adb(command, **kwargs):
            calls.append((command, kwargs))
            if "instrument" in command:
                test_class = command[command.index("-e") + 2]
                return subprocess.CompletedProcess(command, 0, transcripts[test_class], "")
            if command[1:3] == ["shell", "pidof"]:
                try:
                    return next(pid_responses)
                except StopIteration:
                    return subprocess.CompletedProcess(command, 1, "", "")
            if command[-1:] == ["ro.build.version.sdk"]:
                return subprocess.CompletedProcess(command, 0, sdk, "")
            if command[-1:] == ["getprop"]:
                return subprocess.CompletedProcess(command, 0, "[ro.build.version.sdk]: [37]\n", "")
            if "dumpsys" in command and "webviewupdate" in command:
                return subprocess.CompletedProcess(command, 0, "Current WebView package (name, version): (com.google.android.webview, 123.4)\n", "")
            if "/system/bin/cat" in command:
                return subprocess.CompletedProcess(command, 0, marker_json, "")
            if "exec-out" in command and "tar" in command:
                return subprocess.CompletedProcess(command, 0, binary.get("tar", b""), b"")
            if "exec-out" in command and "screencap" in command:
                return subprocess.CompletedProcess(command, 0, binary.get("screenshot", b""), b"")
            return subprocess.CompletedProcess(command, 0, "", "")

        with tempfile.TemporaryDirectory() as directory, \
             patch.object(runner, "REPORTS", Path(directory)), \
             patch.object(runner.subprocess, "run", side_effect=adb), \
             patch.object(runner.uuid, "uuid4", return_value=self.run_id), \
             patch.dict(os.environ, {"COMMONPLACE_DISPOSABLE_EMULATOR": env}):
            ok = runner.run_proof("api-37")
            report = Path(directory) / "api-37"
            log = (report / "hybrid-proof.txt").read_text()
            metadata = json.loads((report / "metadata.json").read_text())
            artifact_files = {p.relative_to(report).as_posix(): p.read_bytes()
                              for p in report.rglob("*") if p.is_file()}
        return ok, calls, log, metadata, artifact_files

    def test_invokes_focused_class_and_each_separate_recovery_method_once(self):
        ok, calls, log, metadata, artifacts = self.exercise()
        self.assertTrue(ok)
        instrument = [c for c, _ in calls if "instrument" in c and "am" in c]
        self.assertEqual(len(instrument), 3)
        focused = next(c for c in instrument if runner.PROBE in c and runner.RECOVERY_PROBE not in c)
        self.assertIn(runner.PROBE, focused)
        self.assertNotIn("#", focused[focused.index("-e") + 2])
        for method in ("stageSelectedPassage", "verifyRestoredPassage"):
            matching = [c for c in instrument if method in " ".join(c)]
            self.assertEqual(len(matching), 1)
            self.assertIn("-e", matching[0])
            self.assertIn("hybridRecovery", matching[0])
            self.assertIn("true", matching[0])
            self.assertIn("hybridRun", matching[0])
            self.assertIn(self.run_id, matching[0])
        self.assertIn("force-stop", " ".join(" ".join(c) for c, _ in calls))
        self.assertIn("monkey", " ".join(" ".join(c) for c, _ in calls))
        self.assertEqual(metadata["actualSdk"], "37")
        self.assertEqual(metadata["runId"], self.run_id)
        self.assertEqual(metadata["expectedSdk"], "37")
        self.assertEqual(metadata["webviewVersion"], "123.4")
        self.assertIn("run-as", log)
        self.assertIn("properties.txt", artifacts)
        self.assertIn("webview-version.txt", artifacts)
        tar_path = next(name for name in artifacts if name.endswith("app-files.tar"))
        with tarfile.open(fileobj=io.BytesIO(artifacts[tar_path])) as archive:
            self.assertIn("files/hybrid-proof/snapshot.json", archive.getnames())
        self.assertFalse(any(name.endswith("failure.png") for name in artifacts))

    def test_focused_count_failure_and_skips_are_rejected_without_retry(self):
        for transcript in ("OK (1 test)\n", "OK (4 tests)\n1 skipped\n", "FAILURES!!!\n"):
            with self.subTest(transcript=transcript):
                ok, calls, _, _, _ = self.exercise(focused=transcript)
                self.assertFalse(ok)
                self.assertEqual(sum("instrument" in c for c, _ in calls), 3)

    def test_stage_failure_still_collects_process_death_but_never_passes(self):
        ok, calls, _, metadata, _ = self.exercise(stage="FAILURES!!!\n")
        self.assertFalse(ok)
        commands = [c for c, _ in calls]
        self.assertTrue(any("force-stop" in c for c in commands))
        self.assertTrue(any("pidof" in c for c in commands))
        self.assertEqual(sum("stageSelectedPassage" in " ".join(c) for c in commands), 1)
        self.assertEqual(sum("verifyRestoredPassage" in " ".join(c) for c in commands), 1)
        self.assertFalse(metadata["markerValid"])

    def test_recovery_methods_require_exactly_one_case_and_reject_skips(self):
        for kwargs in ({"stage": "OK (2 tests)\n"},
                       {"stage": "OK (1 test)\n1 skipped\n"},
                       {"verify": "INSTRUMENTATION_FAILED: process crashed\n"}):
            with self.subTest(kwargs=kwargs):
                ok, calls, _, _, _ = self.exercise(**kwargs)
                self.assertFalse(ok)
                self.assertEqual(sum("instrument" in c for c, _ in calls), 3)

    def test_actual_sdk_must_match_matrix_api_label(self):
        ok, _, _, metadata, _ = self.exercise(sdk="28\n")
        self.assertFalse(ok)
        self.assertEqual(metadata["actualSdk"], "28")
        self.assertIn("API mismatch", " ".join(metadata["failures"]))

    def test_empty_actual_sdk_fails_closed(self):
        ok, _, _, metadata, _ = self.exercise(sdk="")
        self.assertFalse(ok)
        self.assertIn("empty actual SDK", " ".join(metadata["failures"]))

    def test_invalid_binary_diagnostics_are_reported_without_waiving_test_failure(self):
        ok, _, _, metadata, artifacts = self.exercise(
            focused="FAILURES!!!\n", binary={"tar": b"not a tar", "screenshot": b""})
        self.assertFalse(ok)
        self.assertTrue(any("tar" in failure.lower() for failure in metadata["diagnosticErrors"]))
        self.assertTrue(any("screenshot" in failure.lower() for failure in metadata["diagnosticErrors"]))
        self.assertNotIn("app-files.tar", artifacts)

    def test_failure_screenshot_uses_binary_stdout_and_is_saved(self):
        ok, _, _, _, artifacts = self.exercise(focused="FAILURES!!!\n")
        self.assertFalse(ok)
        self.assertEqual(artifacts["failure.png"], b"\x89PNG\r\n\x1a\nproof")

    def test_missing_or_wrong_run_marker_fails(self):
        for marker in ("", json.dumps({"runId": "other", "sourceId": "s"}),
                       json.dumps({"runId": self.run_id, "sourceId": ""})):
            with self.subTest(marker=marker):
                ok, _, _, _, _ = self.exercise(marker=marker)
                self.assertFalse(ok)

    def test_process_death_and_fresh_pid_are_required(self):
        dead_pids = [
            subprocess.CompletedProcess([], 0, "1234\n", ""),
            subprocess.CompletedProcess([], 0, "1234\n", ""),
            subprocess.CompletedProcess([], 0, "5678\n", ""),
        ]
        ok, _, _, _, _ = self.exercise(pids=dead_pids)
        self.assertFalse(ok)
        stale_pids = [
            subprocess.CompletedProcess([], 0, "1234\n", ""),
            subprocess.CompletedProcess([], 1, "", ""),
            subprocess.CompletedProcess([], 0, "1234\n", ""),
        ]
        ok, _, _, _, _ = self.exercise(pids=stale_pids)
        self.assertFalse(ok)

    def test_disposable_emulator_guard_is_mandatory(self):
        with patch.dict(os.environ, {"COMMONPLACE_DISPOSABLE_EMULATOR": "0"}):
            with self.assertRaises(RuntimeError):
                runner.run_proof("api-37")


if __name__ == "__main__":
    unittest.main()
