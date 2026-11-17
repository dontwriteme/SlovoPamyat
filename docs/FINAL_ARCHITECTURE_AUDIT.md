# Stage 5: performance and stability audit

Base: 3ce4092 (stage 4), branch perf/final-stability-audit. Previous controller,
repository and atomic-answer changes are present. Baseline targeted contracts
compiled and passed. Production View resources, navigation, animations, Bible
assets, schema/version/file, scheduling intervals and acceptance rules are untouched.

## Confirmed problems and changes

- ProgressRepository.masteryCount repeatedly parsed its target and scanned the
  whole library for each unmastered verse. A 1000-line fixture made 4001 queries.
  masteryCount/firstUnmastered now use one operation-local snapshot (3 queries
  for canonical text, 2 for custom text). No persistent cache. Inherited mastery
  still uses exactly the existing ref+text matching across canonical entries;
  custom material remains independent, repeated positions retain their meaning.
- FlashcardRepository.cardDeck aggregated every deck to find one ID. Its query
  now filters the requested ID before grouping; empty/missing deck behavior remains.
- ReviewLogic.accuracy used word-distance DP across the entire answer even when
  almost all words matched. Identical prefix/suffix trimming and shorter DP rows
  compute the same Levenshtein distance and rounded score. A differential guard
  compares 1000 seeded examples with the previous implementation. Strict answer
  acceptance still requires exact matching; a rounded accuracy of 100 alone does
  not accept an imperfect answer.
- TypingDiffLogic merged fragments by repeatedly copying the entire accumulated
  String. A StringBuilder now builds each contiguous run. Alignment, error kinds,
  text and order remain unchanged; before/after long-output checksums match.
- CollectionController avoids querying for startup synchronization when already
  stopped. Failed collection synchronization is reported without saved text/SQL
  values; existing per-collection transactions still roll back and continue.

Audited write boundaries: collection sync/reorder/delete, card membership changes,
material revision/remapping, recall/mastery/schedule and atomic study submission.
The optimizations add no writes. Existing rollback and installed-upgrade guards
remain mandatory. No indexes or migrations were added.

## Reproducible measurements

Same host, JDK 17, isolated Robolectric SQLite fixture; one before/after observation
per size, not Android screen timings. Raw logs are ignored under .workflow.
PerformanceRegressionTest reproduces sizes and guards query count/output semantics.

| Operation | Before | After |
| --- | --- | --- |
| Mastery, 10 lines | 41 queries / 65.20 ms | 3 / 3.26 ms |
| Mastery, 100 lines | 401 / 307.02 ms | 3 / 2.59 ms |
| Mastery, 1000 lines | 4001 / 2845.30 ms | 3 / 11.15 ms |
| Score, 6000 matching words | 250.98 ms | 3.19 ms |
| Score, 6000 words + local edit | 205.03 ms | 4.43 ms |

Standalone TextBenchmark: 12000-word diff, same JDK/256 MB heap; trial 0 excluded,
median of trials 1–5. Matching text: 205.09 → 21.21 ms; edited text:
73.34 → 33.94 ms. JVM warmup/GC varied: these are observations, not a device-speed
promise. Piece counts/checksums identical (1/2019403520 and 24000/-54854086).
Run by compiling ReviewLogic.java, TypingDiffLogic.java and
`tests/benchmarks/TextBenchmark.java` into an ignored directory, then
`java -Xmx256m -cp <directory> TextBenchmark`. Compare the same fixture/revisions.

## Architecture and lifecycle

MainActivity retains Java View construction and some direct mutations of
controller-owned fields. BibleReaderController owns the existing single search
executor, Future cancellation, debouncing and generation checks; close cancels
work, removes Handler callbacks and rejects late results. Bible loading/search
index preparation/startup synchronization already run off the UI thread. Native
Activity recreation and search cancellation contracts cover these mechanisms.
No new background threads or View writes from workers were introduced.

LocalStore owns one schema/helper per application store; RepositoryScope shares
its connection/transaction boundary. StudySessionController owns current lesson
state, StudyAnswerEvaluation evaluates it and StudyProgressRecorder commits it.
Pure learning/queue/schedule/diff classes do not depend on Android Views.
RepositoryScope deliberately coordinates repositories with some cross-domain
references; these are known dependencies, not removed by a wholesale rewrite.
Icon animators settle/cancel on detach; Activity clears its warmup/home callbacks
and closes the reader and store. No static Activity/View reference was added.

## Remaining limits and next work

No claim of complete stability or absence of leaks: physical-device FPS, heap
retention profiling, low-memory/OOM kill, physical power loss, and every manual
exercise/tab flow were not tested. Emulator timings do not establish UI latency.
Some synchronous collection/status/history reads and UI construction remain;
profile individual screens before introducing lifecycle-bound async snapshots.
The operation-local mastery snapshot still reads library/progress rows once;
very large libraries may justify further bounded reads after device profiling.
Worst-case dissimilar word scoring is still quadratic in time (linear DP memory).
A pathological huge token can still make the existing character diff expensive;
changing its alignment/fallback requires a separate compatibility design.
The stage-4 receipt protects current submission replay, not arbitrary historical
snapshots after later attempts. A durable event ledger needs separate schema work.

Recommended next work: physical-device profiling of large library/status screens,
then one measured async loading change at a time; keep the current equivalence,
transaction, upgrade and lifecycle guards.

## Final checks actually performed

- 118 JVM tests, zero failures/errors/skipped; full suite actually executed (23 s).
  Includes scoring equivalence, long diff golden output, fresh reads after writes,
  shared/custom mastery, empty/missing decks and previous learning contracts.
- Debug application and shared test APK: BUILD SUCCESSFUL (1 s).
- Android 28 software emulator: repository guards 15/0 (including concurrent
  same-attempt submission and large snapshot), storage 23/0, sessions 8/0,
  Activity recreation 3/0; Bible icon/catalog/176-verse-grid/back/lifecycle PASS.
- Native mastery fixture: 1000 lines, 3 queries, 770.46 ms. No native pre-change
  timing was collected; do not compare this with the host/Robolectric table.
- Native EXPLAIN: verse_progress point lookup uses its existing covering index;
  filtered deck lookup searches the deck primary key and membership covering index.
- Forced installed upgrade from stage3-before.apk: PASS, same v8 file/schema/IDs,
  partial exercises, mastery, recall, attempt history, schedules, collections,
  cards and order. Tests use isolated fixtures, not bulk data in the user database.
- Existing model-only harness also passes; the new Robolectric performance class
  is excluded when pickerModelOnly is requested. No dependency/tool update.
- Bible numbering/ranges integrity and APK signature verification: PASS.
- Lint executed once: existing styles.xml:5 NewApi error (API 27 vs minSdk 26),
  41 warnings. Not suppressed and not reported as a passed check.
- Whitespace/staged diff reviewed; generated APKs, timing logs and caches excluded.

Large collection screen opening, a large flashcard-deck UI, huge attempt-history
screen latency, actual process-kill/low-memory behavior and heap retention were
not measured. The verified 1000-line snapshot still takes noticeable time on the
software emulator; asynchronous screen snapshots require separate stale-result
and lifecycle contracts before moving these dependent reads/writes off-thread.
