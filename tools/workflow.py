#!/usr/bin/env python3
"""Measured Android checks. No command arguments or device output enter the journal."""
import sys
import fcntl
import argparse, datetime, hashlib, json, os, pathlib, re, shlex, subprocess, time, shutil, uuid
ROOT = pathlib.Path(__file__).resolve().parents[1]
LOG = ROOT / '.workflow'

def run(name, args, capture=False, quiet=False):
    LOG.mkdir(exist_ok=True)
    start = datetime.datetime.now(datetime.timezone.utc).isoformat()
    tick = time.monotonic()
    result = subprocess.run(args, cwd=ROOT, text=True, stdout=subprocess.PIPE if capture else None,
                            stderr=subprocess.PIPE if capture else None)
    with (LOG / 'timings.jsonl').open('a') as stream:
        stream.write(json.dumps(dict(check=name, start=start, end=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                                    seconds=time.monotonic()-tick, exit_code=result.returncode))+'\n')
    if result.returncode:
        if capture and not quiet:
            print(result.stdout or '', file=sys.stderr); print(result.stderr or '', file=sys.stderr)
        raise RuntimeError(f'{name} failed ({result.returncode}); inspect the command output')
    return result.stdout if capture else ''

def device(operation, *args, label=None, quiet=False):
    client = shlex.split(os.environ.get('SV_DEVICE_CLIENT', 'adb'))
    return run(label or 'device.'+operation, client+[operation, *args], True, quiet)

def install(apk, package, force=False):
    if not re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+', package): raise ValueError('Invalid package name')
    apk = pathlib.Path(apk).resolve()
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    try: identity = device('shell', 'cat /proc/sys/kernel/random/boot_id', quiet=True).splitlines()[0].strip()
    except (RuntimeError, OSError, IndexError): identity = None
    receipt_path = LOG / (package+'.json')
    try: receipt = json.loads(receipt_path.read_text())
    except (OSError, ValueError): receipt = {}
    # Verify actual installed bytes on this device, not just the local receipt.
    try: path = device('shell', 'pm path '+package, quiet=True)
    except RuntimeError: path = ''
    matches = re.findall(r'^package:(\S+)', path, re.M)
    actual = ''
    if len(matches) == 1:
        try: output = device('shell', 'sha256sum '+shlex.quote(matches[0]), quiet=True)
        except RuntimeError: output = ''
        match = re.search(r'\b[0-9a-f]{64}\b', output)
        if match: actual = match[0]
    if not force and identity and actual == digest and receipt.get('boot_id') == identity and receipt.get('sha256') == digest:
        print('Already installed and verified:', package)
        return
    remote = '/data/local/tmp/svetoch-'+digest[:16]+'.apk'
    device('push', str(apk), remote)
    output = device('shell', 'pm install -r '+shlex.quote(remote), label='apk.install')
    if not re.search(r'^Success\s*$', output, re.M):
        raise RuntimeError('Package manager did not confirm installation')
    # Confirm presence and exact bytes after success as well.
    paths = re.findall(r'^package:(\S+)', device('shell', 'pm path '+package), re.M)
    if len(paths) != 1 or digest not in device('shell', 'sha256sum '+shlex.quote(paths[0])):
        raise RuntimeError('Installed APK verification failed')
    LOG.mkdir(exist_ok=True)
    (LOG / (package+'.json')).write_text(json.dumps(dict(sha256=digest, boot_id=identity, verified=True)))
    print('Installed and verified:', package)

def gradle(tasks):
    executable = os.environ.get('SV_GRADLE') or shutil.which('gradle') or str(ROOT/'gradlew')
    run('gradle.'+tasks[0], [executable, '--daemon', '--max-workers=4', '-I', 'tests/workflow.init.gradle', *tasks])

def native_success(output):
    return not re.search(r'GUARD_FAIL|^FAIL:|INSTRUMENTATION_FAILED|UNEXPECTED_FAILURES=[1-9]', output, re.M) and bool(re.search(r'PASS:|GUARDS_PASSED=[1-9]\d* (?:EXPECTED_FAILURES=0 )?UNEXPECTED_FAILURES=0', output))

def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest='action', required=True)
    sub.add_parser('compile')
    t = sub.add_parser('unit'); t.add_argument('--tests', action='append', default=[])
    sub.add_parser('apks')
    sub.add_parser('lint')
    i = sub.add_parser('install'); i.add_argument('apk'); i.add_argument('package'); i.add_argument('--force', action='store_true')
    n = sub.add_parser('native'); n.add_argument('suite', choices=['StorageSafetyCheck','RepositorySafetyCheck','SessionStateSafetyCheck','ActivityRecreationCheck','BibleIconCheck','CollectionsIconCheck','HomeIconCheck'])
    u = sub.add_parser('upgrade'); u.add_argument('old_apk')
    m = sub.add_parser('measure'); m.add_argument('name'); m.add_argument('command', nargs=argparse.REMAINDER)
    sub.add_parser('ready')
    a = p.parse_args()
    if a.action == 'measure':
        if not re.fullmatch(r'[a-zA-Z0-9_.-]+',a.name): raise ValueError('Use a non-sensitive operation label')
        command=a.command[1:] if a.command[:1]==['--'] else a.command
        if not command: raise ValueError('Missing command')
        run(a.name,command)
    elif a.action == 'compile': gradle([':app:compileDebugJavaWithJavac'])
    elif a.action == 'unit':
        tasks = [':app:testDebugUnitTest', '--rerun']
        for pattern in a.tests: tasks += ['--tests', pattern]
        gradle(tasks)
    elif a.action == 'apks': gradle([':app:assembleDebug', ':app:assembleDebugAndroidTest'])
    elif a.action == 'lint': gradle([':app:lintDebug'])
    elif a.action == 'install': install(a.apk, a.package, a.force)
    elif a.action == 'upgrade':
        fixture = uuid.uuid4().hex
        install(a.old_apk, 'org.biblememory.app', True)
        install(ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk', 'org.biblememory.app.test', True)
        seed = device('shell', 'am instrument -w -e phase seed -e fixture '+fixture+' org.biblememory.app.test/org.biblememory.app.InstalledUpgradeCheck', label='upgrade.seed')
        if 'UPGRADE_FIXTURE_SEEDED' not in seed: raise RuntimeError('Upgrade seed failed')
        install(ROOT/'app/build/outputs/apk/debug/app-debug.apk', 'org.biblememory.app', True)
        output = device('shell', 'am instrument -w -e phase verify -e fixture '+fixture+' org.biblememory.app.test/org.biblememory.app.InstalledUpgradeCheck', label='upgrade.verify')
        if 'INSTALLED_UPGRADE_PASS:' not in output: raise RuntimeError('Upgrade verification failed')
        print(output)
    elif a.action == 'ready':
        start = time.monotonic()
        while time.monotonic()-start < 300:
            try:
                boot = device('shell', 'getprop sys.boot_completed', quiet=True).splitlines()[0:1]
            except (RuntimeError, OSError):
                time.sleep(2); continue
            if boot == ['1']:
                try:
                    if 'package:' in device('shell', 'pm path android', quiet=True): return
                except (RuntimeError, OSError): pass
            time.sleep(2)
        raise RuntimeError('Emulator readiness timeout')
    elif a.action == 'native':
        output = device('shell', 'am instrument -w org.biblememory.app.test/org.biblememory.app.'+a.suite, label='instrumentation.'+a.suite)
        print(output)
        if not native_success(output):
            raise RuntimeError('Instrumentation summary did not confirm success')

if __name__ == '__main__':
    tick=time.monotonic(); start=datetime.datetime.now(datetime.timezone.utc).isoformat(); status=0
    try:
        if len(sys.argv)>1 and sys.argv[1] in ['install','native','ready','upgrade','measure']:
            LOG.mkdir(exist_ok=True)
            device_lock=(LOG/'device.lock').open('a')
            fcntl.flock(device_lock,fcntl.LOCK_EX)
        main()
    except SystemExit as error:
        status=error.code or 0; raise
    except BaseException:
        status=1; raise
    finally:
        LOG.mkdir(exist_ok=True)
        with (LOG/'timings.jsonl').open('a') as stream:
            stream.write(json.dumps(dict(check='workflow.'+(sys.argv[1] if len(sys.argv)>1 and sys.argv[1] in ['compile','unit','apks','lint','ready','native','install','upgrade'] else 'unknown'),start=start,end=datetime.datetime.now(datetime.timezone.utc).isoformat(),seconds=time.monotonic()-tick,exit_code=status))+'\n')
