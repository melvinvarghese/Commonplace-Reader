import hashlib
import json
import re
import tempfile
import unittest
import xml.etree.ElementTree as E
from pathlib import Path
from tools import verify_notebook_test_results as guard

SHA='a'*40

class NotebookResultsGuardTest(unittest.TestCase):
    def fixture(self, root):
        for flavor in ('androidx','composepdf'):
            folder=root/flavor;folder.mkdir()
            suite=E.Element('testsuite',tests=str(sum(map(len,guard.REQUIRED_CASES.values()))),failures='0',errors='0',skipped='0')
            for klass,cases in guard.REQUIRED_CASES.items():
                for name in sorted(cases): E.SubElement(suite,'testcase',classname=klass,name=name,time='0.1')
            E.ElementTree(suite).write(folder/'TEST-device.xml')
            self.manifest(folder,flavor)
    def manifest(self,folder,flavor):
        hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in folder.glob('TEST-*.xml')}
        (folder/'identity.json').write_text(json.dumps({'sourceSha':SHA,'runId':('b' if flavor=='androidx' else 'c')*32,'flavor':flavor,'api':'28','abi':'x86_64','exitCode':0,'xmlSha256':hashes}))
    def mutate_xml(self,folder,change):
        path=folder/'TEST-device.xml';tree=E.parse(path);change(tree.getroot());tree.write(path);self.manifest(folder,folder.name)
    def test_exact_complete_reports_pass(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.fixture(root);self.assertTrue(guard.verify(root,SHA,'28'))
    def test_missing_duplicate_and_skipped_cases_fail(self):
        changes=[lambda r:r.remove(r.find('testcase')),lambda r:r.append(E.fromstring(E.tostring(r.find('testcase')))),lambda r:E.SubElement(r.find('testcase'),'skipped'),lambda r:E.SubElement(r.find('testcase'),'failure'),lambda r:E.SubElement(r.find('testcase'),'error')]
        for change in changes:
            with self.subTest(change=change),tempfile.TemporaryDirectory() as d:
                root=Path(d);self.fixture(root);self.mutate_xml(root/'androidx',change)
                with self.assertRaises(ValueError):guard.verify(root,SHA,'28')
    def test_summary_failures_and_nonzero_invocation_fail(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.fixture(root);self.mutate_xml(root/'composepdf',lambda r:r.set('failures','1'))
            with self.assertRaises(ValueError):guard.verify(root,SHA,'28')
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.fixture(root);p=root/'androidx/identity.json';data=json.loads(p.read_text());data['exitCode']=1;p.write_text(json.dumps(data))
            with self.assertRaises(ValueError):guard.verify(root,SHA,'28')
    def test_mismatched_run_identities_and_sha_are_rejected(self):
        for field,value in [('sourceSha','d'*40),('flavor','composepdf'),('api','37'),('runId',''),('abi','')]:
            with self.subTest(field=field),tempfile.TemporaryDirectory() as d:
                root=Path(d);self.fixture(root);p=root/'androidx/identity.json';data=json.loads(p.read_text());data[field]=value;p.write_text(json.dumps(data))
                with self.assertRaises(ValueError):guard.verify(root,SHA,'28')
    def test_missing_xml_identity_or_changed_report_hash_fails(self):
        for filename in ['TEST-device.xml','identity.json']:
            with tempfile.TemporaryDirectory() as d:
                root=Path(d);self.fixture(root);(root/'androidx'/filename).unlink()
                with self.assertRaises((ValueError,OSError)):guard.verify(root,SHA,'28')
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.fixture(root);p=root/'androidx/TEST-device.xml';p.write_text(p.read_text()+'\n')
            with self.assertRaises(ValueError):guard.verify(root,SHA,'28')
    def test_inventory_covers_all_current_acceptance_tests(self):
        repo=Path(__file__).resolve().parents[1]
        for klass,names in guard.REQUIRED_CASES.items():
            filename=klass.split('.')[-1]+'.kt'
            paths=list((repo/'app/src').rglob(filename));self.assertEqual(1,len(paths),filename)
            found=set(re.findall(r'@Test\s+fun\s+(\w+)',paths[0].read_text()))
            self.assertEqual(found,names,klass)
