# Development workflow: measured result

Environment: existing JDK 17, Gradle 8.9, AGP 8.7.3, SDK 35; API 28 software AVD.
No production Java, resources, database schema, algorithms or tool versions changed.
Raw logs/JSON are local and excluded from Git. Values below are elapsed seconds,
not percentages or guarantees for future work. Trials are sequential within each
comparison; warm-up and other machine activity can affect single observations.

| Same scenario | Before | After |
| --- | --- | --- |
| Unchanged assembleDebug, three runs | no-daemon: 8.86 / 7.49 / 8.09 | daemon: 8.11 / 1.13 / 0.93 |
| clean + both APKs + unit task | no build cache: 31.01 | cache fill: 25.12; warm restore: 1.84 |
| Compile unit Java after a test-only comment edit | warm daemon, no cache: 1.09 | cache fill: 1.04; restore: 0.80 |
| Forced RepositoryIntegrationTest | no-daemon/no-cache: 12.75 | daemon/cache: 4.83 |
| Forced full unit suite | no-daemon/no-cache: 32.35 | daemon/cache: 23.79 |
| Repeat StorageSafetyCheck, including installation checks | old script always installs both APKs: 18.93 | verify installed bytes and skip unchanged APKs: 14.65 |

The clean-cache comparison restored task outputs (including the cached test
result); it is NOT a claim that tests executed in 1.84 seconds. Separate --rerun
Test-only runs actually executed the full 106-test suite with zero failures,
errors or skips. The micro-edit trial restored the baseline compilation between
arms; a prior trial without that reset was discarded. It is a comment edit, not
a prediction for complex code changes. FROM-CACHE appeared in actual Gradle logs.

Daemon remained responsive and IDLE after tests; observed Java RSS ranged from
roughly 0.54 GB early to 1.88 GB after larger builds. No stranded test worker was
observed. Long-duration leak/stress testing was not performed.

## Verification

- Python workflow tests: 4 passed (receipt invalidation, missing package/failure,
  forced installation, boot-ID changes, negative and positive native summaries).
- One APK runs StorageSafetyCheck (23), RepositorySafetyCheck (11),
  SessionStateSafetyCheck (8), ActivityRecreationCheck (3), without runner rebuilds.
- StorageSafetyCheck repeated with the same APK; no reinstall was required.
- Changed old/new application APKs installed successfully. Upgrade seed/verify
  passed with forced reinstalls and a fresh isolated UUID fixture.
- Real test-package removal was followed by installation and then a verified skip.
- The existing AVD was cold-restarted without wiping data; boot completed in
  95.931 seconds according to its log, and installation state was reverified.
  Deleting/recreating AVD userdata or cloning running-memory snapshots was NOT tested.
- Initial reboot overlapped an older device-command chain and failed; all later
  device operations use a process lock. Failed probes remain in local timing logs
  and are not reported as successful validations.
- Initial manifest merging replaced its first instrumentation entry. Explicitly
  placing the default runner first fixed this; all retained groups then ran.
- Configuration Cache experiments with strict problem handling passed APK assembly,
  forced index generation (31,169 verses / 33,055 terms), and a forced repository
  unit test using the workflow init script. It is NOT enabled persistently; broader
  compatibility and reuse remain a separate change.
- Lint still reports the prior styles.xml:5 NewApi (API 27 versus minSdk 26),
  1 error / 41 warnings. No suppressions were added.
- Bible grid checks and APK signature verification passed.

## Scope and use

See DEVELOPMENT_WORKFLOW.md and AGENTS.md. The shared manifest declares existing
runners instead of replacing their lifecycle with a dispatcher. Legacy init
scripts are preserved. Native install receipts require actual package/hash checks
and a matching boot identity, not just a local receipt. Upgrade never uses skip.
The prepared external START.md was left unchanged; project commands select daemon.
The Python transport/process lock currently targets Linux/POSIX.

Do not add these elapsed durations together to estimate whole-task calendar time:
some work overlapped, and nested journal records include child command durations.
