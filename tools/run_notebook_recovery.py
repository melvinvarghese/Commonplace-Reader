#!/usr/bin/env python3
"""Dedicated disposable-emulator notebook process-restart proof; never personal data."""
import json
import os
import re
import subprocess
import sys
import uuid
from pathlib import Path

try:
    from .run_reader_recovery import wait_for_fresh_pid
except ImportError:
    from run_reader_recovery import wait_for_fresh_pid

REPORTS=Path('build/reports/notebook-recovery')
CASES=('new-thought','edit-highlight','independent-drafts','committed-unicode')
PROBE='im.commonplace.reader.notebook.NotebookRecoveryProbe'
RUNNER='androidx.test.runner.AndroidJUnitRunner'

def run_flavor(flavor,source_sha=None):
    if os.environ.get('COMMONPLACE_DISPOSABLE_EMULATOR')!='1':
        print(f'{flavor}: notebook recovery refuses a non-disposable environment',file=sys.stderr,flush=True)
        return False
    folder=REPORTS/flavor;folder.mkdir(parents=True,exist_ok=True)
    run_id=uuid.uuid4().hex
    proof={'sourceSha':source_sha,'flavor':flavor,'runId':run_id,'api':None,'abi':None,'cases':{c:'NOT RUN' for c in CASES},'status':'FAIL'}
    def command(args,timeout=30,input=None):
        full=['adb',*args]
        try: result=subprocess.run(full,capture_output=True,text=True,timeout=timeout,check=False,input=input)
        except (OSError,subprocess.TimeoutExpired) as error: raise RuntimeError(f'ADB unavailable: {args[0]}') from error
        with (folder/'recovery.txt').open('a') as log: log.write('$ '+' '.join(full)+f'\nexit={result.returncode}\n{result.stdout or ""}\n{result.stderr or ""}\n')
        return result
    def require(result,message):
        if result.returncode!=0: raise RuntimeError(message)
        return (result.stdout or '').strip()
    def marker(name,field):
        text=require(command(['shell','run-as',package,'/system/bin/cat',f'files/{name}']),f'{name} missing')
        try: actual=json.loads(text)
        except ValueError as error: raise RuntimeError(f'{name} is not an acknowledgment') from error
        if any(actual.get(k)!=identity[k] for k in identity) or actual.get(field)!=list(CASES):
            raise RuntimeError(f'{name} identity or assertion list mismatch')
    def instrument(mode,method):
        result=command(['shell','am','instrument','-w','-e','notebookRecovery',mode,'-e','notebookRunId',run_id,
            '-e','notebookSourceSha',proof['sourceSha'],'-e','class',f'{PROBE}#{method}',f'{package}.test/{RUNNER}'],timeout=240)
        text=(result.stdout or '')+'\n'+(result.stderr or '')
        if result.returncode or re.search(r'(?m)^OK \(1 test\)\s*$',text) is None or any(x in text for x in ('FAILURES!!!','INSTRUMENTATION_FAILED','INSTRUMENTATION_ABORTED','Process crashed','INSTRUMENTATION_STATUS_CODE: -3','INSTRUMENTATION_STATUS_CODE: -4')):
            raise RuntimeError(f'{mode}: exactly one passing notebook assertion case required')
    package=f'im.commonplace.reader.{flavor}'
    try:
        if source_sha is None: source_sha=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
        if not re.fullmatch('[0-9a-f]{40}',source_sha): raise RuntimeError('invalid source identity')
        proof['sourceSha']=source_sha
        if require(command(['shell','getprop','ro.kernel.qemu']),'emulator check failed')!='1': raise RuntimeError('not a disposable emulator')
        proof['api']=require(command(['shell','getprop','ro.build.version.sdk']),'API check failed')
        proof['abi']=require(command(['shell','getprop','ro.product.cpu.abi']),'ABI check failed')
        if proof['api'] not in ('28','37') or not proof['abi']: raise RuntimeError('unexpected emulator identity')
        identity={k:proof[k] for k in ('runId','sourceSha','flavor')}
        print(f'{flavor}: notebook acknowledged drafts/save, real PID death and launcher recovery; source={source_sha}; API={proof["api"]}; ABI={proof["abi"]}',flush=True)
        for apk in (f'app/build/outputs/apk/{flavor}/debug/app-{flavor}-debug.apk',f'app/build/outputs/apk/androidTest/{flavor}/debug/app-{flavor}-debug-androidTest.apk'):
            require(command(['install','-r',apk],timeout=90),'APK install failed')
        require(command(['shell','run-as',package,'mkdir','-p','files']),'private files unavailable')
        require(command(['shell','run-as',package,'/system/bin/sh','-c',f"'printf %s {run_id} > files/notebook-disposable-install'"]),'could not establish disposable marker')
        if require(command(['shell','run-as',package,'/system/bin/cat','files/notebook-disposable-install']),'disposable marker unavailable')!=run_id: raise RuntimeError('disposable marker not acknowledged')
        require(command(['shell','run-as',package,'rm','-f','files/notebook-recovery-ready','files/notebook-recovery-verified']),'could not reset private notebook markers')
        instrument('stage','stageAcknowledgedNotebook')
        marker('notebook-recovery-ready','acknowledged')
        require(command(['shell','am','start','-W','-n',f'{package}/im.commonplace.reader.MainActivity']),'staged app launch failed')
        before=require(command(['shell','pidof',package]),'no confirmed live PID')
        if not before: raise RuntimeError('empty live PID')
        require(command(['shell','am','force-stop',package]),'force-stop failed')
        after=command(['shell','pidof',package])
        if after.returncode!=1 or (after.stdout or '').strip() or (after.stderr or '').strip(): raise RuntimeError('target process death not confirmed')
        require(command(['shell','monkey','-p',package,'1'],timeout=60),'launcher relaunch failed')
        fresh=wait_for_fresh_pid(command,package,before)
        instrument('verify','verifyRecoveredNotebook')
        marker('notebook-recovery-verified','verified')
        proof.update(status='PASS',oldPid=before,freshPid=fresh,cases={c:'PASS' for c in CASES})
        print(f'{flavor}: all notebook restart assertions PASS',flush=True)
        return True
    except Exception as error:
        proof['failure']=str(error)
        print(f'{flavor}: notebook recovery failed: {error}',file=sys.stderr,flush=True)
        return False
    finally:
        (folder/'proof.json').write_text(json.dumps(proof,indent=2)+'\n')

def main():
    results=[run_flavor(flavor) for flavor in ('androidx','composepdf')]
    return 0 if all(results) else 1

if __name__=='__main__': sys.exit(main())
