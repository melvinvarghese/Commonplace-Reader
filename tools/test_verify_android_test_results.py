import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("verify_android_test_results.py")
PASSING = ('<testsuites tests="1" failures="0" errors="0" skipped="0">'
           '<testsuite><testcase name="launchAndNavigate" '
           'classname="im.commonplace.reader.ui.ShellTest"/></testsuite></testsuites>')


class InstrumentationResultGuardTest(unittest.TestCase):
    def run_guard(self, reports):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for flavor, content in reports.items():
                folder = root / flavor
                folder.mkdir()
                (folder / "TEST-emulator.xml").write_text(content)
            return subprocess.run([sys.executable, str(SCRIPT), directory],
                                  capture_output=True, text=True)

    def test_accepts_executed_shell_test_in_both_flavors(self):
        self.assertEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), PASSING)).returncode, 0)

    def test_rejects_missing_reports_even_if_gradle_succeeded(self):
        self.assertNotEqual(self.run_guard({}).returncode, 0)

    def test_rejects_missing_second_flavor(self):
        self.assertNotEqual(self.run_guard({"androidx": PASSING}).returncode, 0)

    def test_rejects_zero_tests(self):
        empty = '<testsuites tests="0" failures="0" errors="0" skipped="0"/>'
        self.assertNotEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), empty)).returncode, 0)

    def test_rejects_failed_or_skipped_shell_test(self):
        for child in ("failure", "error", "skipped"):
            with self.subTest(child=child):
                report = PASSING.replace('/></testsuite>', f'><{child}/></testcase></testsuite>')
                self.assertNotEqual(self.run_guard({"androidx": report, "composepdf": PASSING}).returncode, 0)

    def test_rejects_other_test_in_place_of_shell_test(self):
        other = PASSING.replace("launchAndNavigate", "someOtherTest")
        self.assertNotEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), other)).returncode, 0)

    def test_rejects_malformed_xml(self):
        self.assertNotEqual(self.run_guard({"androidx": "broken", "composepdf": PASSING}).returncode, 0)


if __name__ == "__main__":
    unittest.main()
