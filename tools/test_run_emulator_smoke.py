import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).parent
PASSING = ('<testsuite tests="1" failures="0" errors="0" skipped="0">'
           '<testcase name="launchAndNavigate" '
           'classname="im.commonplace.reader.ui.ShellTest"/></testsuite>')


class EmulatorSmokeTest(unittest.TestCase):
    def run_smoke(self, gradle_status=0, reports=True, adb_status=0):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'tools').mkdir()
            shutil.copy(TOOLS / 'verify_android_test_results.py', root / 'tools')
            # Real subprocess boundaries; no emulator is available on this host.
            adb = root / 'adb'
            adb.write_text('#!/bin/sh\nprintf "adb %s\\n" "$*"\nexit ' + str(adb_status) + '\n')
            adb.chmod(0o755)
            gradle = root / 'gradlew'
            gradle.write_text('#!/bin/sh\nexit ' + str(gradle_status) + '\n')
            gradle.chmod(0o755)
            if reports:
                for flavor in ('androidx', 'composepdf'):
                    folder = root / 'app/build/outputs/androidTest-results/connected/debug/flavors' / flavor
                    folder.mkdir(parents=True)
                    (folder / 'TEST-device.xml').write_text(PASSING)
            for flavor in ('androidx', 'composepdf'):
                apk = root / f'app/build/outputs/apk/{flavor}/debug/app-{flavor}-debug.apk'
                apk.parent.mkdir(parents=True)
                apk.touch()
            result = subprocess.run([sys.executable, str(TOOLS / 'run_emulator_smoke.py')],
                                    cwd=root, env={**os.environ, 'PATH': str(root) + ':' + os.environ['PATH']},
                                    capture_output=True, text=True, timeout=30)
            artifacts = {str(p.relative_to(root)): p.read_text()
                         for p in (root / 'build/reports/emulator').rglob('*.txt')}
            return result, artifacts

    def test_collects_live_evidence_and_preserves_gradle_failure(self):
        result, artifacts = self.run_smoke(gradle_status=7)
        self.assertEqual(result.returncode, 7, result.stderr)
        for phase in ('before', 'after-tests', 'after-probes'):
            self.assertIn(f'build/reports/emulator/{phase}/logcat.txt', artifacts)
            self.assertIn('adb shell dumpsys mount',
                          artifacts[f'build/reports/emulator/{phase}/mount.txt'])
        for flavor in ('androidx', 'composepdf'):
            launch = artifacts[f'build/reports/emulator/probes/{flavor}-launch.txt']
            self.assertIn(f'am start -W -n im.commonplace.reader.{flavor}/im.commonplace.reader.MainActivity', launch)

    def test_successful_diagnostics_cannot_replace_missing_tests(self):
        result, _ = self.run_smoke(reports=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('no instrumentation XML reports', result.stderr)

    def test_diagnostic_failure_cannot_mask_successful_test_gate(self):
        result, artifacts = self.run_smoke(adb_status=9)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('exit=9', artifacts['build/reports/emulator/after-tests/logcat.txt'])


if __name__ == '__main__':
    unittest.main()
