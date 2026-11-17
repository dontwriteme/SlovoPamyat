# Stage 4: answer submission boundaries

Base: f13c04e on refactor/localstore-repositories. Previous controllers,
repository extraction and v8 schema guards are present. Work is isolated on
refactor/study-attempts. Baseline learning/controller/repository tests passed.

## Audit and dependency map

- MainActivity renders Java Views and binds actions to StudySessionController.
  Lesson fields reside in the controller; the Activity references those fields.
  There is no second copy of lesson state introduced by this change.
- LessonCurriculumLogic chooses intro/build/write variants; LessonFlowLogic
  protects unlocked verses; StudyPlanLogic builds verse/chain/section/chapter ranges.
- AdaptiveLearningLogic selects recommendations from persisted mastery/due/errors;
  GroupLearningLogic selects linked segments; WeakTransitionLogic locates failures.
- ReviewLogic normalizes/scores text; AcceptedTextLogic preserves accepted wording;
  TypingDiffLogic supplies visible word/character errors.
- SpacedReviewLogic calculates intervals; ReviewQueueLogic plans non-overlapping
  due passages. ProgressRepository persists attempts/schedules/mastery using the
  single RepositoryScope/LocalStore connection.
- SequenceRecallController has its own preferences-backed sequence drill using
  SequenceRecallLogic. Its rules and indices are independent and unchanged.

Affected operations: checkAnswer, validateLessonWriting, training checkpoint
save/restore, pending review restore, and the progress writes they coordinate.
Risks audited: partial multi-table answer commits, combined-review partial commits,
repeat callbacks, process death between SQL and UI completion, assistance loss,
stale/deleted material, and accidental mastery of a synthetic VERSE_RUN.

## Changes

StudyAnswerEvaluation is an immutable, persistence-free adapter around existing
strict matching/scoring; neither normalization nor diff algorithms changed.
StudyProgressRecorder owns recall/correction/failure/success persistence decisions,
extracted in the same order from the controller. It has no Views or session flags.
ProgressRepository.commitStudyAttempt validates current material and encloses all
writes in the existing shared SQLite transaction. Rejected progress REPLACE writes
now throw instead of returning an unchecked failure. A combined review commits all
source verse results together; it never masters a synthetic chain.

Before SQL, the controller synchronously persists a hashed answer/exercise identity
and its timestamp in existing preferences. The existing recall_activity.last_attempt
serves as the committed receipt. Replaying the same current submission reuses it;
checked results also reject duplicate handlers. Each changed input/context receives
a new timestamp (monotonic by at least 1 ms when the wall clock does not advance).
Failure rolls back the receipt too, permitting retry. The controller logs SQLite
errors and keeps the answer available instead of displaying success.

Durable checkpoints now include inline answers, assistance, checked/passed result,
accuracy and expected feedback. Pending reviews restore their seed and due identity.
Old checkpoints use existing defaults. Schema version 8, filename, table layout,
Bible text, scheduling functions/intervals, lesson thresholds/order and UI resources
are unchanged. No new dependencies. No methods were deleted from public APIs.

## Limits

The receipt covers the current submission and normal lifecycle replay; it is not a
permanent globally unique event ledger. recall_activity retains the latest attempt
per segment. Arbitrarily replaying an old snapshot after later submissions is not
an exactly-once guarantee; that would require durable event IDs/schema design.
Preferences and SQLite cannot share one atomic commit: ordering the checkpoint
before SQL makes the tested interrupted submission replayable. Disk failure of
preferences aborts submission. No physical power-loss test was performed.
View binding still edits controller-owned fields in several exercise handlers;
extracting those actions can be a subsequent small stage. The separate sequence
drill was audited but not rewritten. No real-device performance claim is made.

## Validation

Results are recorded in the task report. Added guards cover strict text evaluation,
interrupted successful callbacks, pending review replay, rejected mastery rollback,
retry, stale material, durable inline/assistance/result state, and native SQLite
rollback of both an ordinary answer and the second verse of a combined review.

Final checks after implementation:

- Full JVM suite: 112 tests, 0 failures/errors/skipped; actually executed (24 s).
- Debug application and shared Android test APK: BUILD SUCCESSFUL (5 s).
- Android 28 emulator: RepositorySafetyCheck 13/0, StorageSafetyCheck 23/0,
  SessionStateSafetyCheck 8/0, ActivityRecreationCheck 3/0.
- Forced installed upgrade from stage3-before.apk: PASS; same file/schema/IDs,
  partial exercises, mastery, recall/history/schedules, collections/cards/order.
- Bible grid integrity (66 books, Psalm 118 with 176 verses) and APK signature: PASS.
- Lint executed once: existing styles.xml:5 NewApi error (API 27 vs minSdk 26),
  41 warnings; not suppressed. No new Lint errors.
- git diff --check and staged checks: PASS. No build outputs or device logs tracked.

The emulator tests exercise lifecycle and real SQLite; they do not replace a
manual run through every exercise on a physical device. Next small stage: move
remaining View action mutations into named controller methods with contract tests,
without changing exercise order or introducing another state model.
