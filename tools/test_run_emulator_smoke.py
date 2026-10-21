import os
import hashlib
import importlib.util
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

TOOLS = Path(__file__).parent
from test_verify_android_test_results import PASSING


class LargeFixtureStagingTest(unittest.TestCase):
    def test_rejects_wrong_fixture_hash_before_adb(self):
        spec = importlib.util.spec_from_file_location('smoke_under_test', TOOLS / 'run_emulator_smoke.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = root / 'large.pdf'
            fixture.write_bytes(b'wrong bytes')
            with patch.object(module, 'REPORTS', root), patch.object(module, 'LARGE_FIXTURE', fixture), patch.object(Path, 'is_file', return_value=True), patch.object(module.subprocess, 'run') as run:
                self.assertNotEqual(module.stage_large_composepdf_fixture(), 0)
                run.assert_not_called()


class EmulatorSmokeTest(unittest.TestCase):
    def run_smoke(self, gradle_status=0, reports=True, adb_status=0, recovery_status=0, reader_recovery_status=0, pdf_recovery_status=0, fixture_readback=True):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'tools').mkdir()
            shutil.copy(TOOLS / 'verify_android_test_results.py', root / 'tools')
            (root / 'tools/run_import_recovery.py').write_text('import sys; sys.exit(' + str(recovery_status) + ')\n')
            (root / "tools/run_reader_recovery.py").write_text("import sys; sys.exit(" + str(reader_recovery_status) + ")\n")
            (root / 'tools/run_pdf_recovery.py').write_text('import sys; sys.exit(' + str(pdf_recovery_status) + ')\n')
            # Real subprocess boundaries; no emulator is available on this host.
            adb = root / 'adb'
            payload = '%PDF-smoke-test' if fixture_readback else 'partial'
            adb.write_text('#!/bin/sh\nprintf "adb %s\\n" "$*" >> build/reports/emulator/timeline.txt\ncase "$*" in "exec-out run-as im.commonplace.reader.composepdf cat files/proof-large-100mib.pdf") printf "%s" "' + payload + '"; exit 0 ;; esac\nprintf "adb %s\\n" "$*"\ncase "$*" in install*|exec-in*|"shell run-as"*) exit 0 ;; esac\nexit ' + str(adb_status) + '\n')
            adb.chmod(0o755)
            gradle = root / 'gradlew'
            gradle.write_text('''#!/bin/sh
mkdir -p build/reports/emulator
printf "%s\\n" "$*" >> build/reports/emulator/gradle-invocations.txt
printf "gradle %s\\n" "$*" >> build/reports/emulator/timeline.txt
case "$*" in
  *connectedAndroidxDebugAndroidTest*) flavor=androidx ;;
  *connectedComposepdfDebugAndroidTest*) flavor=composepdf ;;
esac
if [ -n "$flavor" ] && [ -n "$TEST_REPORT_FIXTURE" ]; then
  target="app/build/outputs/androidTest-results/connected/debug/flavors/$flavor"
  mkdir -p "$target"
  cp "$TEST_REPORT_FIXTURE" "$target/TEST-device.xml"
fi
exit ''' + str(gradle_status) + '\n')
            gradle.chmod(0o755)
            fixture = root / 'fixture.xml'
            if reports:
                for flavor in ('androidx', 'composepdf'):
                    folder = root / 'app/build/outputs/androidTest-results/connected/debug/flavors' / flavor
                    folder.mkdir(parents=True)
                    (folder / 'TEST-device.xml').write_text(PASSING)
                fixture.write_text(PASSING)
            transport_fixture = root / 'build/fixture-transport/large-100mib.local.pdf'
            transport_fixture.parent.mkdir(parents=True, exist_ok=True)
            transport_fixture.write_bytes(b'%PDF-smoke-test')
            # Only the isolated harness substitutes its tiny fixture's expected hash.
            runner = root / 'tools/run_emulator_smoke.py'
            runner.write_text((TOOLS / 'run_emulator_smoke.py').read_text().replace(
                '73c4e34e97311ce559c8755ee9c0e42f27f7fd6bc733db06a61a56fae0f69c6e',
                hashlib.sha256(b'%PDF-smoke-test').hexdigest()))
            for flavor in ('androidx', 'composepdf'):
                apk = root / f'app/build/outputs/apk/{flavor}/debug/app-{flavor}-debug.apk'
                apk.parent.mkdir(parents=True)
                apk.touch()
            result = subprocess.run([sys.executable, str(runner)],
                                    cwd=root, env={**os.environ, 'TEST_REPORT_FIXTURE': str(fixture) if reports else '',
                                                   'PATH': str(root) + ':' + os.environ['PATH']},
                                    capture_output=True, text=True, timeout=30)
            artifacts = {str(p.relative_to(root)): p.read_text(errors='replace')
                         for p in (root / 'build/reports/emulator').rglob('*') if p.is_file()}
            return result, artifacts

    def test_collects_live_evidence_and_preserves_gradle_failure(self):
        result, artifacts = self.run_smoke(gradle_status=7)
        self.assertEqual(result.returncode, 7, result.stderr)
        for phase in ('before', 'after-tests', 'after-probes'):
            self.assertIn(f'build/reports/emulator/{phase}/logcat.txt', artifacts)
            self.assertIn(f'build/reports/emulator/{phase}/page-size.txt', artifacts)
            self.assertIn(f'build/reports/emulator/{phase}/system-memory.txt', artifacts)
            self.assertIn('adb shell dumpsys mount',
                          artifacts[f'build/reports/emulator/{phase}/mount.txt'])
        for flavor in ('androidx', 'composepdf'):
            launch = artifacts[f'build/reports/emulator/probes/{flavor}-launch.txt']
            self.assertIn(f'am start -W -n im.commonplace.reader.{flavor}/im.commonplace.reader.MainActivity', launch)

    def test_successful_diagnostics_cannot_replace_missing_tests(self):
        result, _ = self.run_smoke(reports=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('no instrumentation XML reports', result.stderr)

    def test_failed_process_kill_recovery_cannot_pass_with_green_gradle_and_xml(self):
        result, _ = self.run_smoke(recovery_status=8)
        self.assertEqual(result.returncode, 8, result.stdout + result.stderr)

    def test_failed_reader_recovery_cannot_pass_with_green_gradle_and_xml(self):
        result, _ = self.run_smoke(reader_recovery_status=7)
        self.assertEqual(result.returncode, 7, result.stdout + result.stderr)

    def test_failed_pdf_annotation_recovery_is_an_independent_gate(self):
        result, _ = self.run_smoke(pdf_recovery_status=6)
        self.assertEqual(result.returncode, 6, result.stdout + result.stderr)

    def test_locally_signed_app_is_built_before_private_fixture_install(self):
        result, artifacts = self.run_smoke()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        timeline = artifacts['build/reports/emulator/timeline.txt'].splitlines()
        builds = [i for i, line in enumerate(timeline) if ':app:assembleComposepdfDebug' in line]
        installs = [i for i, line in enumerate(timeline) if line.startswith('adb install') and 'composepdf' in line]
        tests = [i for i, line in enumerate(timeline) if ':app:connectedComposepdfDebugAndroidTest' in line]
        self.assertTrue(builds, 'Downloaded APK signing key must not precede local Gradle signing')
        self.assertLess(builds[0], installs[0]); self.assertLess(installs[0], tests[0])

    def test_successful_send_with_truncated_device_bytes_is_a_gate_failure(self):
        result, artifacts = self.run_smoke(fixture_readback=False)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('staged fixture mismatch', artifacts['build/reports/emulator/fixture-staging.txt'])

    def test_diagnostic_failure_cannot_mask_successful_test_gate(self):
        result, artifacts = self.run_smoke(adb_status=9)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('exit=9', artifacts['build/reports/emulator/after-tests/logcat.txt'])

    def test_pdf_focus_runs_after_full_suite_and_keeps_separate_reports(self):
        result, artifacts = self.run_smoke(gradle_status=7)
        calls = artifacts['build/reports/emulator/gradle-invocations.txt'].splitlines()
        self.assertEqual(len(calls), 5, calls)
        self.assertIn(':app:connectedAndroidxDebugAndroidTest', calls[0])
        self.assertIn(':app:assembleComposepdfDebug', calls[1])
        self.assertIn(':app:connectedComposepdfDebugAndroidTest', calls[2])
        self.assertIn(':app:assembleComposepdfDebug', calls[3])
        self.assertIn(':app:connectedComposepdfDebugAndroidTest', calls[4])
        self.assertIn('-Pandroid.testInstrumentationRunnerArguments.class=im.commonplace.reader.reader.pdf.PdfProofTest', calls[4])
        self.assertEqual(result.returncode, 7, result.stdout + result.stderr)
        self.assertTrue(any(name.startswith('build/reports/emulator/test-results/full-suite/androidx/')
                            for name in artifacts))
        self.assertTrue(any(name.startswith('build/reports/emulator/test-results/full-suite/composepdf/')
                            for name in artifacts))
        self.assertTrue(any(name.startswith('build/reports/emulator/test-results/pdf-focused/composepdf/')
                            for name in artifacts))


if __name__ == '__main__':
    unittest.main()
