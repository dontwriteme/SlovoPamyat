# Safe collection removal and retained learning history

## Audit and boundaries

Base: 2be8f75, branch perf/final-stability-audit. Learning history is attached to
entries.id plus verse positions/ranges, not directly to collection IDs. Entries
have one group_id; unified_collections connects the visible group and card deck.
Progress tables reference entries with ON DELETE CASCADE. Deleting entries/groups
would therefore destroy history. The former collection delete removed only the
unified row, leaving orphan groups/decks active. Clear already archived entries,
but queues/checkpoints relied on row existence and could resume removed material.
Selection also automatically fell back to another collection.

Affected operations were audited before implementation: delete/clear/detach,
shared generated cards, startup sync, passage restore, progress/schedule writes,
collection queue restore/advance, review planning, writing-run checkpoints,
Activity restoration, home actions and backup import/export.

## Storage and deletion

No SQLite table definitions, version (8), filename or indexes changed. No migration,
reset, destructive cleanup or bulk rewrite of existing history is performed.

- Removing an entry moves the SAME row to the existing private study library.
  Only its collection maps/order/card memberships are detached. Generated cards
  supplied by a surviving overlapping entry keep their membership and receive a
  surviving source map. Both legacy range cards and generated verse cards are
  handled, preventing background synchronization from recreating removed entries.
- Clear retains the visible collection and active selection, removes its links
  and deck memberships, and archives its entries in one transaction.
- Delete performs clear, then removes the unified row, group and deck in the same
  transaction. Other decks' shared cards/memberships are retained. Repeating a
  completed removal is harmless.
- The private library has no unified membership. Its lookup excludes visible
  collections, including a user collection with the same reserved title.
- Existing orphan groups left by the old delete remain stored but are excluded
  from visible groups and active queues. Reattaching matching canonical material
  recovers its original entry ID/history. The legacy newGroup API and backup
  import now create the same visible group/deck association as the current UI.

Rows changed by membership removal: entries.group_id, unified_collections, groups_,
card_decks, card_deck_members, unified_entry_cards, unified_card_entries,
study_order, card_order. Existing lesson_progress, verse_progress, stage_progress,
recall_activity, review_attempts, review_schedule and transition_issues rows survive.
Completed writing-run diagnostics in preferences are retained; only temporary
run checkpoints are invalidated.

## Identity, queues and sessions

Canonical verse identity includes BOOK, verse ref and exact saved text. Previously
inheritance omitted the book. Single-verse progress writes now resolve an existing
history owner (preferring an existing schedule, then other progress, then the oldest
matching location). Both entry ID and position are remapped together. Overlapping
passages use the same owner for common verses; changed larger ranges do not inherit
mastery of a different chain. Existing duplicate history rows are retained, while
one authoritative schedule enters the active queue. Review interval/scoring
functions and exercise order are unchanged.

Archived history is eligible only when its exact segment still belongs to a visible
collection. Custom text does not inherit by equal contents. Newly created custom
material carries an optional user_material marker in existing verse JSON, and
custom card lessons carry study_card_id as an explicit source binding. Existing
readers ignore these extra fields; normal ref/text fields and SQLite schema remain.
Ambiguous unbound old custom card rows are not guessed from their contents; their
history remains stored. No parallel progress store is introduced.

Each lesson retains its origin group (or legacy card-deck context). Opening,
restoring, rendering and advancing a lesson/queue reject invalid membership.
Writing and milestone commits validate membership inside the shared SQLite
transaction, including when canonical history resides in another archived entry.
A lesson from deleted A cannot save merely because the verse still exists in B.
Card-deck lessons validate the source deck independently. Restoring an old Activity
snapshot cannot restore a deleted collection queue. Repeated final writing-run
callbacks are guarded against duplicate completion/out-of-range indexing.

Clearing/deleting invalidates affected queue/run checkpoints. Deleting the selected
collection persists -1, with no automatic fallback. Empty home states offer the
existing Bible/custom-text actions or collection selection/creation. Confirmation
dialogs state that learning history is retained. Home/tab contents refresh after
successful membership changes.

## Backup compatibility

Exports add an optional group archived flag; import keeps archived history private
and creates visible associations only for live groups. Old formats remain accepted.
Card source IDs are installation-local and are stripped on import; user-material
identity markers remain. SQL history is imported through the existing mechanism.
There is no schema migration or automatic merge/delete of historical attempts.

## Verification and limits

Added CollectionRemovalTest covers unstudied/partial/mastered removal, one/last
membership, clear/selected/nonselected/last deletion, restart, repeat add, stale
sessions/milestones/snapshots, shared schedules, larger/overlapping passages,
custom identities, prior-version orphans, synchronization resurrection, backup
archive state, imported card IDs, reserved-name isolation and SQL fault rollback.
Activity tests cover the real dialogs, empty home, selection after recreation and
a stale visible exercise button. Native SQLite guards cover history/reattachment,
rollback and the overlapping review owner. Existing storage/lifecycle guards remain.

Actual final results are appended below. Tests use isolated fixtures. No low-memory
kill or physical power-loss test is claimed. The current-attempt receipt retains
the stage-4 limit: it is not a permanent event ledger for arbitrary historic snapshot
replay. Old backups without enough identity/type metadata cannot prove that two
custom texts are the same material; history is preserved rather than guessed.

### Results actually obtained

- Full JVM suite: 149 tests, 0 failures/errors/skipped; actually executed.
- Application and shared instrumentation APK assembled successfully; APK signature
  and the 66-book/chapter/verse integrity check passed.
- Android 28 emulator: RepositorySafetyCheck 19/19, StorageSafetyCheck 23/23,
  SessionStateSafetyCheck 8/8, ActivityRecreationCheck 3/3. Bible icon/catalog,
  176-verse grid, rapid exit/repeat and lifecycle checks passed.
- Existing-installation upgrade fixture passed: same file/schema/IDs, partial
  exercises, mastery, recall, attempts, schedules, collections, cards and order;
  read-only repository calls did not mutate the fixture. The version-27 debug
  baseline was installed with an explicit test-only downgrade before the forced
  old/new scenario because the current application is version 28. No uninstall or
  application-data clearing was used. The final APK was installed with -r.
- Lint executed: existing styles.xml:5 NewApi error (API 27 vs minSdk 26), 59
  warnings including launcher-resource warnings. No suppression or claim of Lint
  success. The final source-binding guard uses the same existing Android APIs.
- Git whitespace/staged checks passed. APKs, fixtures, device captures and logs
  remain outside the commit.

Not performed: low-memory/process-kill stress, physical power loss or a manual run
on a real phone. Long/overlapping fixtures, Activity recreation, transaction
failures and existing-installation compatibility were exercised automatically.
