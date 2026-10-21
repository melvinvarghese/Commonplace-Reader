import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools import run_pdf_recovery as runner


class PdfRecoveryRunnerTest(unittest.TestCase):
    def exercise(self, marker='ready', alive=True, dead=True, monkey=0, verify='OK (1 test)\n'):
        calls = []
        pids = iter([('1234', 0) if alive else ('', 1), ('', 1) if dead else ('1234', 0), ('5678', 0)])
        def run(command, **kwargs):
            calls.append(command)
            if '/system/bin/cat' in command:
                return subprocess.CompletedProcess(command, 0, marker, '')
            if command[1:3] == ['shell', 'pidof']:
                value, code = next(pids)
                return subprocess.CompletedProcess(command, code, value, '')
            if 'monkey' in command:
                return subprocess.CompletedProcess(command, monkey, 'Events injected: 1', '')
            if any('verifyRestoredPdfAnnotation' in v for v in command):
                return subprocess.CompletedProcess(command, 0, verify, '')
            return subprocess.CompletedProcess(command, 0, 'OK (1 test)\n', '')
        with tempfile.TemporaryDirectory() as directory, patch.object(runner, 'REPORTS', Path(directory)), patch.object(runner.subprocess, 'run', side_effect=run):
            result = runner.run_flavor('composepdf')
        return result, calls

    def test_confirmed_save_flush_kill_dead_pid_monkey_live_pid_then_exact_verification(self):
        result, calls = self.exercise()
        self.assertTrue(result)
        self.assertTrue(any('force-stop' in c for c in calls))
        self.assertTrue(any('monkey' in c for c in calls))
        stages = [i for i, c in enumerate(calls) if any('stageConfirmedPdfAnnotation' in v for v in c)]
        kills = [i for i, c in enumerate(calls) if 'force-stop' in c]
        launches = [i for i, c in enumerate(calls) if 'monkey' in c]
        verifies = [i for i, c in enumerate(calls) if any('verifyRestoredPdfAnnotation' in v for v in c)]
        self.assertLess(stages[0], kills[0]); self.assertLess(kills[0], launches[0]); self.assertLess(launches[0], verifies[0])

    def test_wrong_or_missing_durable_marker_cannot_pass(self):
        for marker in ['', 'not ready', 'ready but unconfirmed']:
            result, calls = self.exercise(marker=marker)
            self.assertFalse(result)
            self.assertFalse(any('force-stop' in c for c in calls))

    def test_missing_original_pid_prevents_success(self):
        self.assertFalse(self.exercise(alive=False)[0])

    def test_unconfirmed_death_prevents_relaunch_and_success(self):
        result, calls = self.exercise(dead=False)
        self.assertFalse(result)
        self.assertFalse(any('monkey' in c for c in calls))

    def test_failed_monkey_cannot_pass(self):
        self.assertFalse(self.exercise(monkey=1)[0])

    def test_missing_failed_or_misleading_verification_cannot_pass(self):
        for transcript in ['', 'OK (0 tests)', 'NOT OK (1 test)', 'OK (1 test)\nFAILURES!!!']:
            self.assertFalse(self.exercise(verify=transcript)[0])
