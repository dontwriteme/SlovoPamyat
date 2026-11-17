# Android development checks

Activate the existing environment toolchain first. The wrapper uses `gradle` on
PATH or the repository wrapper; override with SV_GRADLE if required. Ordinary
commands reuse daemon and the local build cache. Configuration Cache remains off.

Level A: `python3 tools/workflow.py compile`, then `unit --tests '<pattern>'` for
the affected repository/controller/learning contracts. Test task uses --rerun,
not global --rerun-tasks, so dependencies and the Bible index stay incremental.

Level B after implementation is finished: `unit`, `apks`, affected native suites,
`lint`, `python3 tests/check_bible_grids.py`, APK signature verification, Git checks.
Native SQLite tests remain mandatory for storage edits; JVM tests do not replace them.
Lint currently reports the existing styles.xml API 27/minSdk 26 defect: report it,
do not suppress it. Documentation-only edits do not require rebuilding Android.

## Device

Default transport is adb. For a prepared alternative client set SV_DEVICE_CLIENT
to its executable/arguments. It must support shell, push and pull (the existing
cloud Python client does). Use one transport operation at a time. `ready` waits
for Android boot and package-manager readiness; start the AVD with the environment's
existing launcher. Do not create another emulator if one is already running.

`apks` builds ONE test APK declaring all existing instrumentation runners. Existing
individual init scripts remain compatible, but switching suites needs no build:

```
python3 tools/workflow.py install app/build/outputs/apk/debug/app-debug.apk org.biblememory.app
python3 tools/workflow.py install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk org.biblememory.app.test
python3 tools/workflow.py native StorageSafetyCheck
python3 tools/workflow.py native RepositorySafetyCheck
python3 tools/workflow.py native SessionStateSafetyCheck
python3 tools/workflow.py native ActivityRecreationCheck
```

BibleIconCheck, HomeIconCheck and CollectionsIconCheck use the same APK. Multiple
manifest entries preserve the existing Instrumentation lifecycles, avoiding a
new dispatcher. Runner summaries, not shell status alone, determine success.

Installation checks local SHA-256, current device boot ID, actual pm path and
SHA-256 of installed bytes. Unknown receipts, changed boot, missing packages,
changed bytes or --force cause installation. Receipts alone never authorize skip.
A reboot conservatively invalidates a receipt even if the AVD was not recreated.
Split-package installations are considered unknown and are reinstalled.

For a genuine upgrade: `python3 tools/workflow.py upgrade <pre-extraction.apk>`.
This ALWAYS reinstalls the old and new application and the test APK, seeds a new
isolated UUID fixture through the old implementation, then verifies it through
the new implementation. It never clears the installed application's database.
The old APK must have the pre-repository LocalStore API; other upgrade baselines
need their own explicit fixture contract. Failed assertions are never cached.

## Measurements and caches

`.workflow/timings.jsonl` records labels, UTC start/end, elapsed monotonic duration
and exit status, without arguments, environment, captured output or user data.
Installation receipts and timings are ignored by Git. Raw Gradle output is sent
to the terminal; redirect it only to ignored local storage. Sum of nested/parallel
operations is NOT task calendar duration. Compare operation labels and wall intervals.
Preserve the environment's Gradle dependency caches and Robolectric Maven cache.
Build cache does not make every task cacheable; custom index generation still uses
its existing input/output checks. Do not use clean or --rerun-tasks routinely.

Configuration Cache compatibility is a separate experiment; do not enable it
persistently without addressing reported problems and rerunning checks.

Device commands hold a POSIX advisory lock for the complete operation, including
upgrade seed/install/verify, so two workflow invocations cannot replace each
other's transport. This script currently targets the prepared Linux environment.
`measure <non-sensitive-label> -- <command> <arguments>` records an existing
emulator launcher (or other operation) without recording its arguments. Pair
launcher timing with `ready` for preparation timing; don't confuse process launch
with completion of Android boot. A cold AVD restart changes boot ID; recreating
an AVD by restoring an entire running-memory snapshot was not tested.
