package org.biblememory.app;

import android.content.SharedPreferences;

final class ReviewSessionPreferences {
    static final String PREFIX = "review_";

    record Pending(LocalStore.DueItem item, String material) {}

    private ReviewSessionPreferences() {}

    static Pending read(SharedPreferences saved) {
        if (!saved.getBoolean("review_pending", false)) return null;
        LocalStore.DueItem item = new LocalStore.DueItem(saved.getLong("review_review_entry", -1),
            saved.getString("review_review_stage", ""), saved.getInt("review_begin", -1),
            saved.getInt("review_end", -1), 0);
        return new Pending(item, saved.getString("review_material", ""));
    }

    static String draft(SharedPreferences saved) {
        if (saved.getBoolean("review_practice_weak", false)) return "";
        return LessonExerciseState.resumableDraft(saved.getString("review_draft", ""),
            saved.getBoolean("review_lesson_had_feedback", false));
    }

    static SharedPreferences.Editor write(SharedPreferences.Editor editor, String prefix,
                                          long entryId, boolean linked, LocalStore.DueItem item, String material) {
        return editor.putBoolean(prefix + "pending", item != null || linked)
            .putLong(prefix + "review_entry", item == null ? 0 : entryId)
            .putString(prefix + "review_stage", item == null ? "" : item.stage)
            .putString(prefix + "material", item == null ? "" : material);
    }

    static void clearPending(SharedPreferences saved) {
        saved.edit().remove("review_pending").apply();
    }
}
