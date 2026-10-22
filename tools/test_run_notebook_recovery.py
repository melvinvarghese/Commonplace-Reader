import json
import os
import subprocess
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import patch
from tools import run_notebook_recovery as runner

SHA='a'*40
RUN='b'*32

class NotebookRecoveryRunnerTest(unittest.TestCase):
    def exercise(self, marker=True, alive='1234', dead=True, after='5678', stage='OK (1 test)\n', verify='OK (1 test)\n', verified=True, consent=True):
        calls=[]
        pids=iter([(alive,0 if alive else 1),('',1) if dead else ('1234',0),(after,0)])
        identity={'runId':RUN,'sourceSha':SHA,'flavor':'androidx'}
        def run(command,**kwargs):
            calls.append(command)
            text='OK (1 test)\n';code=0
            if 'getprop' in command:
                text={'ro.kernel.qemu':'1','ro.build.version.sdk':'28','ro.product.cpu.abi':'x86_64'}[command[-1]]
            elif '/system/bin/cat' in command:
                filename=command[-1]
                if filename.endswith('notebook-disposable-install'): text=RUN if consent else ''
                elif filename.endswith('notebook-recovery-ready'): text=json.dumps({**identity,'acknowledged':list(runner.CASES)}) if marker else ''
                elif filename.endswith('notebook-recovery-verified'): text=json.dumps({**identity,'verified':list(runner.CASES)}) if verified else '{}'
                else: raise AssertionError('Unexpected marker '+filename)
            elif command[1:3]==['shell','pidof']: text,code=next(pids)
            elif any('stageAcknowledgedNotebook' in value for value in command): text=stage
            elif any('verifyRecoveredNotebook' in value for value in command): text=verify
            return subprocess.CompletedProcess(command,code,text,'')
        with tempfile.TemporaryDirectory() as directory,patch.object(runner,'REPORTS',Path(directory)),patch.object(runner.subprocess,'run',side_effect=run),patch.object(runner.uuid,'uuid4',return_value=types.SimpleNamespace(hex=RUN)),patch.dict(os.environ,{'COMMONPLACE_DISPOSABLE_EMULATOR':'1'}):
            result=runner.run_flavor('androidx',SHA)
            report=Path(directory,'androidx/proof.json')
            proof=json.loads(report.read_text()) if report.exists() else {}
        return result,calls,proof
    def test_acknowledged_writing_real_death_fresh_launcher_and_exact_verification(self):
        result,calls,proof=self.exercise();self.assertTrue(result)
        self.assertEqual(SHA,proof['sourceSha']);self.assertEqual('28',proof['api'])
        self.assertEqual({case:'PASS' for case in runner.CASES},proof['cases'])
        self.assertTrue(any('force-stop' in command for command in calls));self.assertTrue(any('monkey' in command for command in calls))
    def test_missingAcknowledgmentMarkerFails(self):
        result,calls,_=self.exercise(marker=False);self.assertFalse(result);self.assertFalse(any('force-stop' in c for c in calls))
    def test_emptyOrUnchangedPidFails(self):
        self.assertFalse(self.exercise(alive='')[0]);self.assertFalse(self.exercise(after='1234')[0]);self.assertFalse(self.exercise(after='')[0])
    def test_killWithoutPidDisappearanceFails(self):
        result,calls,_=self.exercise(dead=False);self.assertFalse(result);self.assertFalse(any('monkey' in c for c in calls))
    def test_missingOrSkippedVerificationCaseFails(self):
        for text in ['', 'OK (0 tests)', 'OK (1 test)\nINSTRUMENTATION_ABORTED', 'OK (1 test)\nFAILURES!!!', 'OK (1 test)\nINSTRUMENTATION_STATUS_CODE: -3']:
            self.assertFalse(self.exercise(verify=text)[0])
        self.assertFalse(self.exercise(verified=False)[0])
        self.assertFalse(self.exercise(stage='OK (0 tests)')[0])
    def test_bothFlavorsRunAfterFirstFailure(self):
        with patch.object(runner,'run_flavor',side_effect=[False,True]) as run:
            self.assertEqual(1,runner.main());self.assertEqual(['androidx','composepdf'],[call.args[0] for call in run.call_args_list])
    def test_recoveryUsesDedicatedDisposableMarker(self):
        result,calls,_=self.exercise(consent=False);self.assertFalse(result)
        self.assertFalse(any('instrument' in c for c in calls))
        self.assertTrue(any('files/notebook-disposable-install' in c for c in calls))
        self.assertFalse(any('files/reader-recovery-ready' in c for c in calls))
    def test_no_disposable_environment_refuses_before_install(self):
        with patch.dict(os.environ,{'COMMONPLACE_DISPOSABLE_EMULATOR':'0'}),patch.object(runner.subprocess,'run') as run:
            self.assertFalse(runner.run_flavor('androidx',SHA));run.assert_not_called()
