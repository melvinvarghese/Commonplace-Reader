#!/usr/bin/env python3
"""Prove a real process kill during import leaves no recoverable partial state."""
import subprocess
import re
import sys
import time
from pathlib import Path

REPORTS = Path("build/reports/import-recovery")
PROBE = "im.commonplace.reader.fixtures.ImportRecoveryProbe"
RUNNER = "androidx.test.runner.AndroidJUnitRunner"
FLAVORS = ("androidx", "composepdf")
READY_TIMEOUT = 90
KILL_TIMEOUT = 30


def record(path, command, result=None, error=None):
    with path.open("a") as output:
        output.write("$ " + " ".join(command) + "\n")
        if result is not None:
            output.write(f"exit={result.returncode}\nstdout:\n{result.stdout or ''}\nstderr:\n{result.stderr or ''}\n")
        if error is not None:
            output.write(f"error={error}\n")


def adb(command, log, timeout=15):
    try:
        result = subprocess.run(["adb", *command], capture_output=True, text=True,
                                timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired) as error:
        record(log, ["adb", *command], error=error)
        return None
    record(log, ["adb", *command], result)
    return result


def instrument_command(package, mode, method):
    selector = f"{PROBE}#{method}"
    component = f"{package}.test/{RUNNER}"
    return ["adb", "shell", "am", "instrument", "-w", "-e", "recoveryProbe", mode,
            "-e", "class", selector, component]


def run_flavor(flavor):
    print(f"{flavor}: staging import for confirmed process-kill probe", flush=True)
    package = f"im.commonplace.reader.{flavor}"
    folder = REPORTS / flavor
    folder.mkdir(parents=True, exist_ok=True)
    stage_log = folder / "stage.txt"
    verify_log = folder / "verify.txt"
    marker = ["shell", "run-as", package, "test", "-f", "files/import-recovery-ready"]
    pid_command = ["shell", "pidof", package]
    stage = None
    try:
        # Connected-test runners may uninstall APKs after their suite. Reinstall both explicitly;
        # -r retains the disposable emulator's preexisting app records for the recovery assertion.
        for apk in (f"app/build/outputs/apk/{flavor}/debug/app-{flavor}-debug.apk",
                    f"app/build/outputs/apk/androidTest/{flavor}/debug/app-{flavor}-debug-androidTest.apk"):
            installed = adb(["install", "-r", apk], stage_log, timeout=90)
            if installed is None or installed.returncode != 0:
                raise RuntimeError("required recovery-probe APK could not be installed")
        marker_remove = adb(["shell", "run-as", package, "rm", "-f",
                             "files/import-recovery-ready"], stage_log)
        if marker_remove is None or marker_remove.returncode != 0:
            raise RuntimeError("could not clear the probe's previous readiness marker")
        stage_command = instrument_command(package, "stage", "stageSlowCopy")
        with stage_log.open("a") as output:
            output.write("$ " + " ".join(stage_command) + " (background)\n")
            output.flush()
            try:
                stage = subprocess.Popen(stage_command, stdout=output,
                                         stderr=subprocess.STDOUT, text=True)
            except OSError as error:
                output.write(f"launch error={error}\n")
                raise RuntimeError("could not launch the staging instrumentation") from error
            deadline = time.monotonic() + READY_TIMEOUT
            ready = False
            while time.monotonic() < deadline:
                marker_result = adb(marker, stage_log, timeout=5)
                if marker_result is not None and marker_result.returncode == 0:
                    ready = True
                    output.write("confirmed readiness marker\n")
                    break
                if stage.poll() is not None:
                    output.write(f"staging instrumentation exited early: {stage.returncode}\n")
                    break
                time.sleep(0.5)
            if not ready:
                raise RuntimeError("staging did not confirm non-empty partial file before timeout")

            before = adb(pid_command, stage_log)
            if before is None or before.returncode != 0 or not before.stdout.strip():
                raise RuntimeError("ready marker was present, but target app process was not confirmed live")
            output.write(f"target PID before force-stop: {before.stdout.strip()}\n")
            killed = adb(["shell", "am", "force-stop", package], stage_log)
            if killed is None or killed.returncode != 0:
                raise RuntimeError("adb force-stop failed")
            try:
                stage_status = stage.wait(timeout=KILL_TIMEOUT)
                output.write(f"instrumentation client exit after force-stop={stage_status}\n")
            except subprocess.TimeoutExpired as error:
                output.write(f"instrumentation client did not exit after force-stop: {error}\n")
                stage.kill()
                stage.wait(timeout=KILL_TIMEOUT)
                raise RuntimeError("staging instrumentation client remained alive after target kill")

            deadline = time.monotonic() + KILL_TIMEOUT
            stopped = False
            while time.monotonic() < deadline:
                after = adb(pid_command, stage_log)
                if (after is not None and after.returncode == 1 and not after.stdout.strip()
                        and not (after.stderr or "").strip()):
                    stopped = True
                    output.write(f"confirmed target process stopped (pidof exit={after.returncode})\n")
                    break
                time.sleep(0.5)
            if not stopped:
                raise RuntimeError("target package process remained alive after force-stop")

        verified = adb(instrument_command(package, "verify", "reopenAfterKill")[1:],
                       verify_log, timeout=120)
        if verified is None or verified.returncode != 0:
            raise RuntimeError("reopen-after-kill instrumentation command failed")
        transcript = (verified.stdout or "") + "\n" + (verified.stderr or "")
        if re.search(r"(?m)^OK \(1 test\)\s*$", transcript) is None or any(token in transcript for token in
                ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Tests run: 1, Failures: 1")):
            raise RuntimeError("verification must report exactly one passing test and no failures")
        print(f"{flavor}: confirmed process kill and successful recovery", flush=True)
        return True
    except Exception as error:
        with stage_log.open("a") as output:
            output.write(f"RECOVERY FAILURE: {error}\n")
        if stage is not None and stage.poll() is None:
            # Cleanup only; readiness and process death have not been proven, so this can never pass.
            adb(["shell", "am", "force-stop", package], stage_log)
            try:
                stage.wait(timeout=KILL_TIMEOUT)
            except subprocess.TimeoutExpired:
                stage.kill()
                stage.wait(timeout=KILL_TIMEOUT)
        print(f"{flavor}: import recovery failed: {error}", file=sys.stderr, flush=True)
        return False


def main():
    REPORTS.mkdir(parents=True, exist_ok=True)
    results = {flavor: run_flavor(flavor) for flavor in FLAVORS}
    return 0 if all(results.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
