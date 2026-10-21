#!/usr/bin/env python3
"""Disposable-emulator reader recovery: acknowledged save/flush, real kill, launcher reopen."""
import re
import subprocess
import sys
import time
from pathlib import Path

REPORTS = Path('build/reports/reader-recovery')
PROBE = 'im.commonplace.reader.reader.ReaderRecoveryProbe'
RUNNER = 'androidx.test.runner.AndroidJUnitRunner'


def wait_for_fresh_pid(command, package, previous, timeout=10):
    # monkey returns after submitting the launch, not after ActivityThread starts.
    deadline = time.monotonic() + timeout
    while True:
        result = command(['shell', 'pidof', package], timeout=2)
        pid = (result.stdout or '').strip()
        stderr = (result.stderr or '').strip()
        if result.returncode == 0 and not stderr:
            if not pid or pid == previous:
                raise RuntimeError('fresh launcher process not confirmed')
            return pid
        if result.returncode != 1 or pid or stderr:
            raise RuntimeError('PID query failed; process startup not confirmed')
        if time.monotonic() >= deadline:
            raise RuntimeError('timed out waiting for a fresh launcher process')
        time.sleep(0.25)


def run_flavor(flavor):
    folder = REPORTS / flavor
    folder.mkdir(parents=True, exist_ok=True)
    package = f'im.commonplace.reader.{flavor}'
    log = folder / 'recovery.txt'

    def command(args, timeout=30):
        full = ['adb', *args]
        try:
            result = subprocess.run(full, capture_output=True, text=True, timeout=timeout, check=False)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise RuntimeError(f'ADB command unavailable: {args[0]}') from error
        with log.open('a') as output:
            output.write('$ ' + ' '.join(full) + f'\nexit={result.returncode}\n{result.stdout or ""}\n{result.stderr or ""}\n')
        return result

    def require_success(result, message):
        if result.returncode != 0:
            raise RuntimeError(message)
        return result

    def instrument(mode, method):
        result = require_success(command(['shell', 'am', 'instrument', '-w', '-e', 'readerRecovery', mode,
            '-e', 'class', f'{PROBE}#{method}', f'{package}.test/{RUNNER}'], timeout=180), f'{mode} instrumentation failed')
        transcript = (result.stdout or '') + '\n' + (result.stderr or '')
        if re.search(r'(?m)^OK \(1 test\)\s*$', transcript) is None or any(token in transcript for token in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'INSTRUMENTATION_ABORTED')):
            raise RuntimeError(f'{mode} must execute exactly one passing assertion case')

    try:
        print(f'{flavor}: reader save/flush and real process-death proof', flush=True)
        for apk in (f'app/build/outputs/apk/{flavor}/debug/app-{flavor}-debug.apk',
                    f'app/build/outputs/apk/androidTest/{flavor}/debug/app-{flavor}-debug-androidTest.apk'):
            require_success(command(['install', '-r', apk], timeout=90), 'APK install failed')
        require_success(command(['shell', 'run-as', package, 'rm', '-f', 'files/reader-recovery-ready']), 'could not reset private marker')
        instrument('stage', 'stageConfirmedReader')
        marker = require_success(command(['shell', 'run-as', package, '/system/bin/cat', 'files/reader-recovery-ready']), 'durable marker missing')
        if (marker.stdout or '').strip() != 'ready':
            raise RuntimeError('save/background-flush acknowledgment not confirmed')
        # Android may tear down the instrumentation process after stage completes. Launch
        # the real app, then confirm/kill that live process; never call recreation death.
        require_success(command(['shell', 'am', 'start', '-W', '-n', f'{package}/im.commonplace.reader.MainActivity']), 'staged app launch failed')
        before = require_success(command(['shell', 'pidof', package]), 'no confirmed live app process')
        if not (before.stdout or '').strip():
            raise RuntimeError('empty live PID')
        require_success(command(['shell', 'am', 'force-stop', package]), 'force-stop failed')
        after = command(['shell', 'pidof', package])
        if after.returncode != 1 or (after.stdout or '').strip() or (after.stderr or '').strip():
            raise RuntimeError('target process death not confirmed')
        require_success(command(['shell', 'monkey', '-p', package, '1'], timeout=60), 'launcher relaunch failed')
        wait_for_fresh_pid(command, package, before.stdout.strip())
        instrument('verify', 'verifyRestoredReader')
        print(f'{flavor}: confirmed save/flush, force-stop, PID disappearance, monkey/fresh PID and native restoration assertions', flush=True)
        return True
    except Exception as error:
        with log.open('a') as output:
            output.write(f'READER RECOVERY FAILURE: {error}\n')
        print(f'{flavor}: reader recovery failed: {error}', file=sys.stderr, flush=True)
        return False


def main():
    results = [run_flavor(flavor) for flavor in ('androidx', 'composepdf')]
    return 0 if all(results) else 1


if __name__ == '__main__':
    sys.exit(main())
