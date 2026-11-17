# Svetoch development

Preserve Java Views, navigation, animation, learning rules and existing SQLite data.
Change only components required by the task. Do not upgrade tools incidentally.
Use `rg` and bounded source ranges; reuse the established dependency map.
For data/learning work, list affected methods, multi-table writes, rollback/retry,
state restoration and compatibility risks before editing.

Workflow: audit → implement → focused checks → final regression → commit → push.
Finish planned implementation before calling a run final. After a fix, repeat
its affected checks. Do not run every suite after every small edit.

Use the prepared JDK/SDK and persistent GRADLE_USER_HOME. Do not change HOME.
`python3 tools/workflow.py compile` compiles production Java.
`python3 tools/workflow.py unit --tests '*RepositoryIntegrationTest'` is an example
focused check; select tests relevant to the actual change.

Final: `python3 tools/workflow.py unit` (full, actually executed), then
`python3 tools/workflow.py apks`, affected `native <Runner>` suites, and `lint`.
For storage changes retain StorageSafetyCheck, RepositorySafetyCheck and the
installed-upgrade scenario; for session/UI changes include lifecycle/navigation.
Do not suppress the known styles.xml API 26/27 Lint defect or treat it as passed.
See docs/DEVELOPMENT_WORKFLOW.md for device setup, measurements and upgrade.

Before commit: `git diff --check`; stage concrete intended paths;
`git diff --cached --check`; `git diff --cached --stat`; review staged contents.
Never stage APKs, caches, local logs, secrets or unrelated user changes.
Commit logical completed work and push the current feature branch when authorized;
never force-push or merge main without instructions.
