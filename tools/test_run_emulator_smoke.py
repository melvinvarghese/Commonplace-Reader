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
from tools.test_verify_notebook_test_results import NotebookResultsGuardTest


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
    def run_smoke(self, gradle_status=0, reports=True, adb_status=0, recovery_status=0, reader_recovery_status=0, pdf_recovery_status=0, fixture_readback=True, notebook_status=0, notebook_recovery_status=0, notebook_reports=True):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'tools').mkdir()
            shutil.copy(TOOLS / 'verify_android_test_results.py', root / 'tools')
            shutil.copy(TOOLS / 'verify_notebook_test_results.py', root / 'tools')
            (root / 'tools/run_notebook_recovery.py').write_text("from pathlib import Path\np=Path('build/reports/emulator/notebook-recovery-invoked');p.write_text('both flavors delegated')\nimport sys;sys.exit("+str(notebook_recovery_status)+")\n")
            (root / 'tools/run_import_recovery.py').write_text('import sys; sys.exit(' + str(recovery_status) + ')\n')
            (root / "tools/run_reader_recovery.py").write_text("import sys; sys.exit(" + str(reader_recovery_status) + ")\n")
            (root / 'tools/run_pdf_recovery.py').write_text('import sys; sys.exit(' + str(pdf_recovery_status) + ')\n')
            # Real subprocess boundaries; no emulator is available on this host.
            adb = root / 'adb'
            payload = '%PDF-smoke-test' if fixture_readback else 'partial'
            adb.write_text('#!/bin/sh\nprintf "adb %s\\n" "$*" >> build/reports/emulator/timeline.txt\ncase "$*" in "shell getprop ro.build.version.sdk") echo 28;exit 0 ;; "shell getprop ro.product.cpu.abi") echo x86_64;exit 0 ;; "exec-out run-as im.commonplace.reader.composepdf cat files/proof-large-100mib.pdf") printf "%s" "' + payload + '"; exit 0 ;; esac\nprintf "adb %s\\n" "$*"\ncase "$*" in install*|exec-in*|"shell run-as"*) exit 0 ;; esac\nexit ' + str(adb_status) + '\n')
            adb.chmod(0o755)
            git=root/'git';git.write_text('#!/bin/sh\nprintf "%s\\n" "'+'a'*40+'"\n');git.chmod(0o755)
            gradle = root / 'gradlew'
            gradle.write_text('''#!/bin/sh
mkdir -p build/reports/emulator
printf "%s\\n" "$*" >> build/reports/emulator/gradle-invocations.txt
printf "gradle %s\\n" "$*" >> build/reports/emulator/timeline.txt
case "$*" in
  *connectedAndroidxDebugAndroidTest*) flavor=androidx ;;
  *connectedComposepdfDebugAndroidTest*) flavor=composepdf ;;
esac
fixture="$TEST_REPORT_FIXTURE"
status="$TEST_GRADLE_STATUS"
case "$*" in *NotebookMigrationTest*) fixture="$NOTEBOOK_REPORT_FIXTURE";status="$NOTEBOOK_TEST_STATUS" ;; esac
if [ -n "$flavor" ] && [ -n "$fixture" ]; then
  target="app/build/outputs/androidTest-results/connected/debug/flavors/$flavor"
  mkdir -p "$target"
  cp "$fixture" "$target/TEST-device.xml"
fi
exit "$status"
''')
            gradle.chmod(0o755)
            fixture = root / 'fixture.xml'
            notebook_fixture=root/'notebook-fixture';notebook_fixture.mkdir()
            NotebookResultsGuardTest().fixture(notebook_fixture)
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
                                                   'NOTEBOOK_REPORT_FIXTURE':str(notebook_fixture/'androidx/TEST-device.xml') if notebook_reports else '',
                                                   'TEST_GRADLE_STATUS':str(gradle_status),'NOTEBOOK_TEST_STATUS':str(notebook_status),
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
        self.assertEqual(len(calls), 7, calls)
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

    def test_notebook_phases_and_recovery_run_even_after_pdf_failure(self):
        result,artifacts=self.run_smoke(gradle_status=7)
        calls=artifacts['build/reports/emulator/gradle-invocations.txt'].splitlines()
        notebook=[line for line in calls if 'NotebookMigrationTest' in line]
        self.assertEqual(2,len(notebook));self.assertIn('connectedAndroidx',notebook[0]);self.assertIn('connectedComposepdf',notebook[1])
        self.assertIn('build/reports/emulator/notebook-recovery-invoked',artifacts)
        for flavor in ('androidx','composepdf'):
            self.assertTrue(any(f'test-results/notebook-focused/{flavor}/TEST-' in path for path in artifacts))
        self.assertEqual(7,result.returncode)

    def test_failed_notebook_phase_does_not_hide_later_flavor_or_recovery(self):
        result,artifacts=self.run_smoke(notebook_status=8)
        self.assertEqual(8,result.returncode,result.stdout+result.stderr)
        self.assertEqual(2,artifacts['build/reports/emulator/gradle-invocations.txt'].count('NotebookMigrationTest'))
        self.assertIn('build/reports/emulator/notebook-recovery-invoked',artifacts)

    def test_notebook_recovery_and_absent_notebook_reports_are_independent_failures(self):
        result,_=self.run_smoke(notebook_recovery_status=9);self.assertEqual(9,result.returncode,result.stdout+result.stderr)
        result,artifacts=self.run_smoke(notebook_reports=False);self.assertNotEqual(0,result.returncode)
        self.assertIn('build/reports/emulator/notebook-recovery-invoked',artifacts)


if __name__ == '__main__':
    unittest.main()
