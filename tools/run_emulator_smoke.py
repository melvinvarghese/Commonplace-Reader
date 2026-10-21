#!/usr/bin/env python3
"""Run guarded smoke tests and capture emulator diagnostics before shutdown.

Direct installs/launches are diagnostic only, never substitutes for executed tests.
Run only on the disposable, empty-shell CI emulator (logs include package state).
"""
import os
import subprocess
import sys
from pathlib import Path

REPORTS = Path('build/reports/emulator')


def capture(folder, name, command, timeout=20):
    folder.mkdir(parents=True, exist_ok=True)
    with (folder / f'{name}.txt').open('w') as output:
        output.write('$ ' + ' '.join(command) + '\n')
        output.flush()
        try:
            result = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, timeout=timeout)
            output.write(f'\nexit={result.returncode}\n')
        except (OSError, subprocess.TimeoutExpired) as error:
            output.write(f'\ndiagnostic unavailable: {error}\n')


def snapshot(phase):
    folder = REPORTS / phase
    for name, command in {
        'devices': ['devices', '-l'],
        'properties': ['shell', 'getprop'],
        'system-server': ['shell', 'pidof', 'system_server'],
        'services': ['shell', 'service', 'list'],
        'mount': ['shell', 'dumpsys', 'mount'],
        'disk': ['shell', 'df', '-h', '/data'],
        'packages': ['shell', 'cmd', 'package', 'list', 'packages', '-f'],
        'logcat': ['logcat', '-b', 'all', '-d'],
    }.items():
        capture(folder, name, ['adb', *command])
    for flavor in ('androidx', 'composepdf'):
        capture(folder, f'{flavor}-package',
                ['adb', 'shell', 'dumpsys', 'package', f'im.commonplace.reader.{flavor}'])


def main():
    REPORTS.mkdir(parents=True, exist_ok=True)
    sdk = Path(os.environ.get('ANDROID_HOME', '/nonexistent'))
    with (REPORTS / 'host-sdk.txt').open('w') as output:
        for pattern in ('emulator/source.properties', 'platform-tools/source.properties',
                        'system-images/*/*/*/source.properties'):
            for source in sorted(sdk.glob(pattern)):
                output.write(f'\n{source}\n{source.read_text()}\n')
        for config in sorted((Path.home() / '.android/avd').glob('*/config.ini')):
            output.write(f'\n{config}\n{config.read_text()}\n')
    snapshot('before')
    print('Running both instrumentation variants (strict verification).', flush=True)
    result = subprocess.run([
        './gradlew', '--no-daemon', '--dependency-verification=strict',
        ':app:connectedAndroidxDebugAndroidTest', ':app:connectedComposepdfDebugAndroidTest',
    ])
    # Capture before the emulator runner terminates the guest, even if Gradle failed.
    snapshot('after-tests')
    print('Collecting diagnostic-only direct install/launch probes.', flush=True)
    for flavor in ('androidx', 'composepdf'):
        package = f'im.commonplace.reader.{flavor}'
        component = f'{package}/im.commonplace.reader.MainActivity'
        apk = f'app/build/outputs/apk/{flavor}/debug/app-{flavor}-debug.apk'
        capture(REPORTS / 'probes', f'{flavor}-install', ['adb', 'install', '-r', apk], timeout=90)
        capture(REPORTS / 'probes', f'{flavor}-resolve',
                ['adb', 'shell', 'cmd', 'package', 'resolve-activity', '--brief', '-n', component])
        capture(REPORTS / 'probes', f'{flavor}-launch',
                ['adb', 'shell', 'am', 'start', '-W', '-n', component], timeout=45)
    snapshot('after-probes')
    recovery_status = 0
    if result.returncode == 0:
        print('Verifying real process-kill import recovery for both variants.', flush=True)
        recovery_status = subprocess.run([sys.executable, 'tools/run_import_recovery.py']).returncode
    reader_recovery_status = 0
    if result.returncode == 0 and recovery_status == 0:
        print('Verifying acknowledged reader writes across real force-stop and monkey relaunch.', flush=True)
        reader_recovery_status = subprocess.run([sys.executable, 'tools/run_reader_recovery.py']).returncode
    guard = subprocess.run([sys.executable, 'tools/verify_android_test_results.py',
                            'app/build/outputs/androidTest-results/connected/debug/flavors'])
    print(f'Gate status: Gradle={result.returncode}, import-recovery={recovery_status}, reader-recovery={reader_recovery_status}, executed-test guard={guard.returncode}', flush=True)
    return result.returncode or recovery_status or reader_recovery_status or guard.returncode


if __name__ == '__main__':
    sys.exit(main())
