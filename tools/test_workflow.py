import importlib.util, pathlib, tempfile, unittest
from unittest.mock import patch
spec = importlib.util.spec_from_file_location('workflow', pathlib.Path(__file__).with_name('workflow.py'))
w = importlib.util.module_from_spec(spec); spec.loader.exec_module(w)

class InstallationTests(unittest.TestCase):
    def test_verified_state_invalidations_and_forced_upgrade(self):
        with tempfile.TemporaryDirectory() as d:
            apk=pathlib.Path(d)/'test.apk'; apk.write_bytes(b'first')
            state={'boot':'one','hash':None,'installs':0}
            def device(op,*args,**kwargs):
                command=args[0]
                if op=='push': return ''
                if command.startswith('cat '): return state['boot']
                if command.startswith('pm path'):
                    if not state['hash']: raise RuntimeError('Package not installed')
                    return 'package:/data/app/base.apk'
                if command.startswith('sha256sum'): return state['hash']+'  /data/app/base.apk'
                if command.startswith('pm install'):
                    state['hash']=w.hashlib.sha256(apk.read_bytes()).hexdigest();state['installs']+=1;return 'Success'
                raise AssertionError(command)
            with patch.object(w,'LOG',pathlib.Path(d)/'logs'),patch.object(w,'device',device):
                w.install(apk,'org.example.test'); w.install(apk,'org.example.test');self.assertEqual(1,state['installs'])
                apk.write_bytes(b'changed');w.install(apk,'org.example.test');self.assertEqual(2,state['installs'])
                state['boot']='new';w.install(apk,'org.example.test');self.assertEqual(3,state['installs'])
                state['hash']=None;w.install(apk,'org.example.test');self.assertEqual(4,state['installs'])
                w.install(apk,'org.example.test',True);self.assertEqual(5,state['installs'])
    def test_unknown_boot_identity_never_skips_install(self):
        with tempfile.TemporaryDirectory() as d:
            apk=pathlib.Path(d)/'x.apk';apk.write_bytes(b'x');digest=w.hashlib.sha256(b'x').hexdigest();calls=[]
            def device(op,*args,**kwargs):
                if op=='push':return ''
                if args[0].startswith('cat '):raise RuntimeError('Identity unavailable')
                if args[0].startswith('pm path'):return 'package:/data/app/base.apk'
                if args[0].startswith('sha256sum'):return digest
                if args[0].startswith('pm install'):calls.append(1);return 'Success'
                raise AssertionError(args)
            with patch.object(w,'LOG',pathlib.Path(d)/'logs'),patch.object(w,'device',device):
                w.install(apk,'org.example.test');w.install(apk,'org.example.test');self.assertEqual(2,len(calls))
    def test_failed_package_manager_does_not_create_receipt(self):
        with tempfile.TemporaryDirectory() as d:
            apk=pathlib.Path(d)/'x.apk';apk.write_bytes(b'x')
            def device(op,*args,**kwargs):
                if args[0].startswith('cat '):return 'boot'
                if args[0].startswith('pm install'):return 'Failure'
                return ''
            with patch.object(w,'LOG',pathlib.Path(d)/'logs'),patch.object(w,'device',device):
                with self.assertRaises(RuntimeError):w.install(apk,'org.example.test')
                self.assertFalse(w.LOG.exists())
class SummaryTests(unittest.TestCase):
    def test_success_and_failure_are_not_confused(self):
        self.assertTrue(w.native_success('GUARDS_PASSED=23 EXPECTED_FAILURES=0 UNEXPECTED_FAILURES=0'))
        self.assertFalse(w.native_success('GUARDS_PASSED=0 UNEXPECTED_FAILURES=0'))
        self.assertFalse(w.native_success('GUARD_FAIL x\nGUARDS_PASSED=23 UNEXPECTED_FAILURES=1'))
        self.assertFalse(w.native_success('INSTRUMENTATION_FAILED'))
if __name__=='__main__':unittest.main()
