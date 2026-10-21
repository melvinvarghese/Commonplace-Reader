#!/usr/bin/env python3
"""Run guarded smoke tests and capture emulator diagnostics before shutdown.

Direct installs/launches are diagnostic only, never substitutes for executed tests.
Run only on the disposable, empty-shell CI emulator (logs include package state).
"""
import os
import hashlib
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

REPORTS = Path('build/reports/emulator')
RESULTS = Path('app/build/outputs/androidTest-results/connected/debug/flavors')
RETAINED_RESULTS = REPORTS / 'test-results'
LARGE_FIXTURE = Path('build/fixture-transport/large-100mib.local.pdf')
LARGE_FIXTURE_SHA256 = '73c4e34e97311ce559c8755ee9c0e42f27f7fd6bc733db06a61a56fae0f69c6e'
PDF_PROOF_CLASS = 'im.commonplace.reader.reader.pdf.PdfProofTest'
PDF_FAILURE_LABELS = ('rotated-cropped', 'large')


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
        'page-size': ['shell', 'getconf', 'PAGE_SIZE'],
        'system-memory': ['shell', 'cat', '/proc/meminfo'],
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


def stage_large_composepdf_fixture():
    package = 'im.commonplace.reader.composepdf'
    apk = Path('app/build/outputs/apk/composepdf/debug/app-composepdf-debug.apk')
    log = REPORTS / 'fixture-staging.txt'
    if not LARGE_FIXTURE.is_file():
        with log.open('a') as output:
            output.write(f'fixture unavailable: {LARGE_FIXTURE}\n')
        print(f'Compose PDF large fixture unavailable: {LARGE_FIXTURE}', flush=True)
        return 1
    with LARGE_FIXTURE.open('rb') as fixture:
        checksum = hashlib.file_digest(fixture, 'sha256').hexdigest()
    if checksum != LARGE_FIXTURE_SHA256:
        with log.open('a') as output:
            output.write(f'fixture SHA-256 mismatch: {checksum}\n')
        return 1
    if not apk.is_file():
        with log.open('a') as output:
            output.write(f'app APK unavailable: {apk}\n')
        return 1

    status = 0
    with log.open('a') as output:
        command = ['adb', 'install', '-r', str(apk)]
        output.write('$ ' + ' '.join(command) + '\n')
        try:
            installed = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT,
                                       timeout=90, check=False)
            output.write(f'exit={installed.returncode}\n')
            status = status or installed.returncode
        except (OSError, subprocess.TimeoutExpired) as error:
            output.write(f'error={error}\n')
            return 1
        command = ['adb', 'shell', 'run-as', package, 'mkdir', '-p', 'files']
        output.write('$ ' + ' '.join(command) + '\n')
        try:
            created = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT,
                                     timeout=20, check=False)
            if created.returncode != 0:
                return status or created.returncode
        except (OSError, subprocess.TimeoutExpired) as error:
            output.write(f'error={error}\n')
            return 1
        command = ['adb', 'exec-in', 'run-as', package, 'dd',
                   'of=files/proof-large-100mib.pdf']
        output.write('$ ' + ' '.join(command) + f' < {LARGE_FIXTURE}\n')
        try:
            with LARGE_FIXTURE.open('rb') as fixture:
                staged = subprocess.run(command, stdin=fixture, stdout=output,
                                        stderr=subprocess.STDOUT, timeout=180, check=False)
            output.write(f'exit={staged.returncode}\n')
            status = status or staged.returncode
        except (OSError, subprocess.TimeoutExpired) as error:
            output.write(f'error={error}\n')
            status = status or 1
        if status:
            return status
        # exec-in success does not acknowledge remote dd completion. Read back to a
        # bounded temporary disk file; never load the 100 MiB transport into RAM.
        for attempt in range(3):
            command = ['adb', 'exec-out', 'run-as', package, 'cat', 'files/proof-large-100mib.pdf']
            output.write('$ ' + ' '.join(command) + '\n')
            try:
                with tempfile.TemporaryFile() as receipt:
                    received = subprocess.run(command, stdout=receipt, stderr=output, timeout=180, check=False)
                    size = receipt.tell(); receipt.seek(0)
                    checksum = hashlib.file_digest(receipt, 'sha256').hexdigest()
                output.write(f'readback attempt={attempt}; exit={received.returncode}; bytes={size}; sha256={checksum}\n')
                if received.returncode == 0 and size == LARGE_FIXTURE.stat().st_size and checksum == LARGE_FIXTURE_SHA256:
                    return 0
            except (OSError, subprocess.TimeoutExpired) as error:
                output.write(f'readback error={error}\n')
            if attempt < 2:
                time.sleep(0.5)
        output.write('staged fixture mismatch: no verified complete device copy\n')
        status = 1
    return status


def run_instrumentation(flavor, *, focused=False):
    prepare_status = 0
    if flavor == 'composepdf':
        # Uploaded checks-job APKs use another ephemeral debug keystore. Produce the
        # local APK first, so staging and connected-test installs have one identity.
        prepare_status = subprocess.run(['./gradlew', '--no-daemon', '--dependency-verification=strict',
                                         ':app:assembleComposepdfDebug']).returncode
    if flavor == 'composepdf' and stage_large_composepdf_fixture() != 0:
        stage_status = 1
    else:
        stage_status = prepare_status
    run_label = 'pdf-focused' if focused else 'full-suite'
    if flavor == 'composepdf':
        capture(REPORTS / 'screenshots', f'{run_label}-cleanup',
                ['adb', 'shell', 'rm', '-f', *[f'/data/local/tmp/composepdf-proof-{label}.png' for label in PDF_FAILURE_LABELS]])
    source = RESULTS / flavor
    if source.exists():
        shutil.rmtree(source)
    command = ['./gradlew', '--no-daemon', '--dependency-verification=strict',
               f':app:connected{flavor.capitalize()}DebugAndroidTest']
    if focused:
        command.append(f'-Pandroid.testInstrumentationRunnerArguments.class={PDF_PROOF_CLASS}')
    print('Running ' + ('focused Compose PDF proof' if focused else f'{flavor} full instrumentation suite') + '.', flush=True)
    result = subprocess.run(command)
    retained = RETAINED_RESULTS / ('pdf-focused' if focused else 'full-suite') / flavor
    if retained.exists():
        shutil.rmtree(retained)
    if source.is_dir():
        retained.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(source, retained)
    if flavor == 'composepdf':
        for label in PDF_FAILURE_LABELS:
            capture(REPORTS / 'screenshots', f'{run_label}-{label}-pull',
                    ['adb', 'pull', f'/data/local/tmp/composepdf-proof-{label}.png',
                     str(REPORTS / 'screenshots' / f'{run_label}-{label}.png')])
    return result.returncode, stage_status


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
    androidx_status, _ = run_instrumentation('androidx')
    composepdf_status, fixture_status = run_instrumentation('composepdf')
    capture(REPORTS, 'pdf-memory-full-suite', ['adb', 'exec-out', 'run-as',
            'im.commonplace.reader.composepdf', 'cat', 'files/pdf-performance.txt'])
    focused_status, focused_fixture_status = run_instrumentation('composepdf', focused=True)
    capture(REPORTS, 'pdf-memory-focused', ['adb', 'exec-out', 'run-as',
            'im.commonplace.reader.composepdf', 'cat', 'files/pdf-performance.txt'])
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
    full_guard = subprocess.run([sys.executable, 'tools/verify_android_test_results.py',
                                  str(RETAINED_RESULTS / 'full-suite')])
    focused_guard = subprocess.run([sys.executable, 'tools/verify_android_test_results.py',
                                    '--pdf-focused', str(RETAINED_RESULTS / 'pdf-focused/composepdf')])
    print('Verifying PDF annotation-only force-stop/relaunch safety; native highlight recovery remains a separate unmet gate.', flush=True)
    pdf_recovery_status = subprocess.run([sys.executable, 'tools/run_pdf_recovery.py']).returncode
    if androidx_status == 0 and composepdf_status == 0 and full_guard.returncode == 0 and focused_status == 0 and focused_guard.returncode == 0:
        print('Verifying real process-kill import recovery for both variants.', flush=True)
        recovery_status = subprocess.run([sys.executable, 'tools/run_import_recovery.py']).returncode
    reader_recovery_status = 0
    if androidx_status == 0 and composepdf_status == 0 and focused_status == 0 and recovery_status == 0 and full_guard.returncode == 0 and focused_guard.returncode == 0:
        print('Verifying acknowledged reader writes across real force-stop and monkey relaunch.', flush=True)
        reader_recovery_status = subprocess.run([sys.executable, 'tools/run_reader_recovery.py']).returncode
    print(f'Gate status: AndroidX={androidx_status}, Composepdf={composepdf_status}, focused-PDF={focused_status}, fixture-staging={fixture_status or focused_fixture_status}, PDF-annotation-recovery={pdf_recovery_status}, import-recovery={recovery_status}, reader-recovery={reader_recovery_status}, full-suite guard={full_guard.returncode}, focused-PDF guard={focused_guard.returncode}', flush=True)
    return (androidx_status or composepdf_status or focused_status or fixture_status or focused_fixture_status
            or pdf_recovery_status or recovery_status or reader_recovery_status or full_guard.returncode or focused_guard.returncode)


if __name__ == '__main__':
    sys.exit(main())
