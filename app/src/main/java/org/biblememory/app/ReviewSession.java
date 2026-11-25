package org.biblememory.app;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Queue and material identity for a review. Lesson checks and their transactions are separate. */
final class ReviewSession {
    private static final String RUN = "VERSE_RUN";

    record Exercise(LocalStore.DueItem item, StudyPlanLogic.Stage studyStage,
                    StudyPlanLogic.Step step, String material) {}
    record Start(LocalStore.DueItem first, String draft) {}

    private final List<LocalStore.DueItem> queue = new ArrayList<>();
    private int cursor;
    private long group = -1;
    private Exercise current;

    long group() { return group; }
    Exercise current() { return current; }
    boolean active() { return current != null; }

    Start start(LocalStore store, SharedPreferences saved, long scope, long now) {
        clear();
        group = scope;
        ReviewSessionPreferences.Pending savedReview = ReviewSessionPreferences.read(saved);
        ReviewSchedule schedule = savedReview == null ? store.reviewSchedule(now) : store.reviewSchedule();
        LocalStore.DueItem pending = null;
        if (savedReview != null) {
            LocalStore.DueItem previous = savedReview.item();
            boolean sameMaterial = savedReview.material().isEmpty()
                || savedReview.material().equals(material(schedule.verses(previous.entryId), previous));
            if (sameMaterial) pending = schedule.resolve(previous, group, now, true);
            if (pending == null && (!sameMaterial || schedule.resolve(previous, -1, now, true) == null))
                ReviewSessionPreferences.clearPending(saved);
        }
        populate(schedule, now, pending);
        return new Start(queue.isEmpty() ? null : queue.get(0),
            pending == null ? "" : ReviewSessionPreferences.draft(saved));
    }

    LocalStore.DueItem restore(LocalStore store, SharedPreferences saved, long scope, long now,
                               LocalStore.Entry entry, LocalStore.DueItem item, boolean complete) {
        if (scope > 0 && store.unifiedForGroup(scope) == null) return null;
        ReviewSchedule schedule = complete ? store.reviewSchedule(now) : store.reviewSchedule();
        if (!complete) {
            ReviewSessionPreferences.Pending savedReview = ReviewSessionPreferences.read(saved);
            if (savedReview == null || item.entryId != savedReview.item().entryId
                || !item.stage.equals(savedReview.item().stage)
                || item.start != savedReview.item().start || item.end != savedReview.item().end
                || !material(entry.lines(), item).equals(savedReview.material())) return null;
            LocalStore.DueItem resolved = schedule.resolve(item, scope, now, true);
            if (resolved == null || resolved.entryId != item.entryId
                || resolved.start != item.start || resolved.end != item.end) return null;
            item = resolved;
        }
        clear();
        group = scope;
        populate(schedule, now, item);
        return item;
    }

    boolean open(LocalStore.Entry entry, LocalStore.DueItem item) {
        if (entry == null || entry.id != item.entryId) return false;
        List<Bible.Verse> lines = entry.lines();
        String identity = material(lines, item);
        if (identity.isEmpty()) return false;
        StudyPlanLogic.Stage stage;
        try {
            stage = RUN.equals(item.stage) ? StudyPlanLogic.Stage.CHAIN : StudyPlanLogic.Stage.valueOf(item.stage);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        StudyPlanLogic.Step step = new StudyPlanLogic.Step(item.start, item.end,
            "Повторение · " + lines.get(item.start).ref);
        current = new Exercise(item, stage, step, identity);
        return true;
    }

    boolean matchesMaterial(LocalStore.Entry latest) {
        return current != null && latest != null && latest.id == current.item().entryId
            && current.material().equals(material(latest.lines(), current.item()));
    }

    SharedPreferences.Editor writeMetadata(SharedPreferences.Editor editor, String prefix,
                                           long entryId, boolean linked) {
        return ReviewSessionPreferences.write(editor, prefix, entryId, linked,
            current == null ? null : current.item(), current == null ? "" : current.material());
    }

    LocalStore.DueItem advance(LocalStore store, long now) {
        if (cursor + 1 < queue.size()) {
            ReviewSchedule schedule = store.reviewSchedule(now);
            while (++cursor < queue.size()) {
                LocalStore.DueItem queued = queue.get(cursor);
                LocalStore.DueItem next = schedule.resolve(queued, group, now, false);
                if (next != null) return next;
                if (RUN.equals(queued.stage)) {
                    // An invalidated run may still contain individual verses that are due.
                    int insertAt = cursor + 1;
                    for (int pos = queued.start; pos < queued.end; pos++) {
                        LocalStore.DueItem verse = new LocalStore.DueItem(queued.entryId, "VERSE", pos, pos + 1, 0);
                        LocalStore.DueItem remaining = schedule.resolve(verse, group, now, false);
                        if (remaining != null) queue.add(insertAt++, remaining);
                    }
                }
            }
        }
        clear();
        return null;
    }

    void clear() {
        current = null;
        queue.clear();
        cursor = 0;
    }

    private void populate(ReviewSchedule schedule, long now, LocalStore.DueItem pending) {
        List<LocalStore.DueItem> due = schedule.due(now, group);
        if (pending != null) due.removeIf(item -> schedule.overlaps(pending, item));
        List<ReviewQueueLogic.Item> raw = new ArrayList<>();
        for (LocalStore.DueItem item : due)
            raw.add(new ReviewQueueLogic.Item(item.entryId, item.stage, item.start, item.end, item.due));
        List<ReviewQueueLogic.Item> items = ReviewQueueLogic.plan(raw, new ReviewQueueLogic.Source() {
            @Override public List<String> references(long id) {
                List<String> refs = new ArrayList<>();
                for (Bible.Verse verse : schedule.verses(id)) refs.add(verse.ref);
                return refs;
            }
            @Override public List<String> texts(long id) {
                List<String> texts = new ArrayList<>();
                for (Bible.Verse verse : schedule.verses(id)) texts.add(verse.text);
                return texts;
            }
        });
        if (pending != null) queue.add(pending);
        for (ReviewQueueLogic.Item item : items)
            queue.add(new LocalStore.DueItem(item.entryId, item.stage, item.start, item.end, item.due));
    }

    private static String material(List<Bible.Verse> lines, LocalStore.DueItem item) {
        if (item.start < 0 || item.end > lines.size() || item.start >= item.end) return "";
        List<String> material = new ArrayList<>();
        for (Bible.Verse verse : lines.subList(item.start, item.end)) {
            material.add(verse.ref);
            material.add(verse.text);
        }
        return SessionIntegrityLogic.fingerprint(material);
    }
}
