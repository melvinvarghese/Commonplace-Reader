import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import run_import_recovery


class FakeProcess:
    def __init__(self, output):
        self.output = output
        self.returncode = None

    def poll(self):
        return self.returncode

    def wait(self, timeout=None):
        self.returncode = -9
        return self.returncode


class ImportRecoveryTest(unittest.TestCase):
    def exercise(self, *, ready=True, running=True, install_status=0, legacy_no_test=False, marker_content="ready\n", verify_output="OK (1 test)\n"):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        reports = Path(temp.name) / "build/reports/import-recovery"
        calls = []
        stage = FakeProcess(None)
        pids = iter([("1234\n", 0), ("", 1), ("5678\n", 0), ("", 1)])
        clock = [0]

        def monotonic():
            clock[0] += 1
            return clock[0]

        def run(command, **kwargs):
            calls.append((command, kwargs))
            if command[1] == "install":
                return subprocess.CompletedProcess(command, install_status, "", "")
            if "test" in command and any("import-recovery-ready" in arg for arg in command):
                if legacy_no_test:
                    return subprocess.CompletedProcess(command, 1, "", "run-as: exec failed for test: Permission denied\n")
                return subprocess.CompletedProcess(command, 0 if ready else 1, "", "")
            if "/system/bin/cat" in command and any("import-recovery-ready" in arg for arg in command):
                return subprocess.CompletedProcess(command, 0 if ready else 1, marker_content if ready else "", "")
            if command[1:3] == ["shell", "pidof"]:
                stdout, status = next(pids)
                if not running and status == 0:
                    stdout, status = "", 1
                return subprocess.CompletedProcess(command, status, stdout, "")
            if command[1:4] == ["shell", "am", "force-stop"]:
                return subprocess.CompletedProcess(command, 0, "", "")
            if any("reopenAfterKill" in arg for arg in command):
                return subprocess.CompletedProcess(command, 0, verify_output, "")
            return subprocess.CompletedProcess(command, 0, "", "")

        with patch.object(run_import_recovery, "REPORTS", reports), \
             patch.object(run_import_recovery, "READY_TIMEOUT", 2), \
             patch.object(run_import_recovery.subprocess, "run", side_effect=run), \
             patch.object(run_import_recovery.subprocess, "Popen", return_value=stage) as popen, \
             patch.object(run_import_recovery.time, "sleep"), \
             patch.object(run_import_recovery.time, "monotonic", side_effect=monotonic):
            status = run_import_recovery.main()
        return status, calls, popen.call_args_list, reports

    def test_kills_confirmed_running_process_only_after_readiness_then_reopens(self):
        status, calls, launched, reports = self.exercise()
        self.assertEqual(status, 0, calls)
        self.assertEqual(len(launched), 2)
        commands = [entry[0] for entry in calls]
        self.assertEqual(sum(command[1:3] == ["shell", "am"] and "force-stop" in command
                             for command in commands), 2)
        background_commands = [call.args[0] for call in launched]
        for flavor in ("androidx", "composepdf"):
            self.assertTrue(any(f"im.commonplace.reader.{flavor}.test" in " ".join(command)
                                and "stageSlowCopy" in " ".join(command)
                                for command in background_commands))
            self.assertTrue(any(any("reopenAfterKill" in arg for arg in command)
                                for command in commands))
            self.assertTrue((reports / flavor / "stage.txt").exists())
            self.assertTrue((reports / flavor / "verify.txt").exists())

    def test_api28_without_external_test_command_uses_exact_private_marker(self):
        status, calls, _, _ = self.exercise(legacy_no_test=True)
        self.assertEqual(status, 0)
        self.assertTrue(any("/system/bin/cat" in command for command, _ in calls))
        self.assertFalse(any("test" in command for command, _ in calls))

    def test_empty_or_wrong_marker_contents_never_count_as_readiness(self):
        for contents in ("", "not ready", "ready but corrupted"):
            with self.subTest(contents=contents):
                status, calls, _, _ = self.exercise(marker_content=contents)
                self.assertNotEqual(status, 0)
                self.assertFalse(any(any("reopenAfterKill" in arg for arg in command)
                                     for command, _ in calls))

    def test_missing_ready_marker_never_counts_as_recovery(self):
        status, calls, _, reports = self.exercise(ready=False)
        self.assertNotEqual(status, 0)
        self.assertFalse(any(any("reopenAfterKill" in arg for arg in command)
                             for command, _ in calls))
        self.assertIn("RECOVERY FAILURE", (reports / "androidx" / "stage.txt").read_text())

    def test_missing_live_pid_prevents_unverified_force_stop_pass(self):
        status, calls, _, _ = self.exercise(running=False)
        self.assertNotEqual(status, 0)
        self.assertFalse(any(any("reopenAfterKill" in arg for arg in command)
                             for command, _ in calls))

    def test_failed_apk_install_cannot_pass_or_launch_stage(self):
        status, _, launched, _ = self.exercise(install_status=1)
        self.assertNotEqual(status, 0)
        self.assertFalse(launched)

    def test_misleading_success_substring_cannot_pass(self):
        status, _, _, _ = self.exercise(verify_output="NOT OK (1 test) trailing junk\n")
        self.assertNotEqual(status, 0)

    def test_verify_requires_exactly_one_successful_test(self):
        status, _, _, _ = self.exercise(verify_output="FAILURES!!!\nTests run: 1, Failures: 1\n")
        self.assertNotEqual(status, 0)


if __name__ == "__main__":
    unittest.main()
