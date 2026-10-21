import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from prepare_emulator_runner import prepare


class PrepareEmulatorRunnerTest(unittest.TestCase):
    def run_runner(self, target):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tools = root / 'sdk' / 'bin'
            tools.mkdir(parents=True)
            (tools / 'avdmanager').write_text('#!/bin/sh\nexit 0\n')
            (tools / 'avdmanager').chmod(0o755)
            (tools.parent / 'source.properties').write_text('Pkg.Revision=23.0.0\n')
            avd = root / 'avd'
            avd.mkdir()
            (avd / 'test.ini').write_text(f'target={target}\n')
            (root / 'tools').mkdir()
            (root / 'tools' / 'assert_avd_target.sh').symlink_to(Path(__file__).with_name('assert_avd_target.sh').resolve())
            path = root / 'main.js'
            path.write_text('''const core = {addPath: p => {process.env.PATH = p + ':' + process.env.PATH;}};
const exec = {exec: async (cmd, args) => require('child_process').execFileSync(cmd, args, {stdio: 'inherit'})};
(async () => {
        // create AVD
console.log('CREATE_TOOL=' + require('child_process').execFileSync('which', ['avdmanager']).toString().trim());
// The upstream hook has completed, including its swallowed failures.
        // launch an emulator
console.log('BOOT_REACHED');
})().catch(error => {console.error(error.message); process.exitCode = 1;});
''')
            prepare(path)
            result = subprocess.run(['node', str(path)], cwd=root, env={**os.environ, 'COMMONPLACE_CMDLINE_TOOLS_BIN': str(tools), 'ANDROID_AVD_HOME': str(avd)}, capture_output=True, text=True)
            self.assertIn(f'CREATE_TOOL={tools}/avdmanager', result.stdout)
            self.assertEqual((root / 'build/reports/avd-target/test.ini').read_text(), f'target={target}\n')
            return result

    def test_correct_target_uses_selected_tools_and_allows_boot(self):
        result = self.run_runner('android-37.0')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('BOOT_REACHED', result.stdout)

    def test_wrong_target_stops_before_boot(self):
        result = self.run_runner('android-0')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('BOOT_REACHED', result.stdout)

    def test_changed_boundary_fails_without_modifying_action(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'main.js'
            original = 'unknown action layout'
            path.write_text(original)
            with self.assertRaises(ValueError):
                prepare(path)
            self.assertEqual(original, path.read_text())


if __name__ == '__main__':
    unittest.main()
