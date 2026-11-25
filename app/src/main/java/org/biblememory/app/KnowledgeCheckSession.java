package org.biblememory.app;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A diagnostic run owns its session and history only. It never writes learning progress. */
final class KnowledgeCheckSession {
    record Question(long entryId, int position, String reference, String text) {}
    record Task(int position, int total, Question question, String draft, boolean checked, boolean passed,
                String feedbackExpected) {}
    record Result(int total, int correct, List<String> mistakes) {}
    enum Availability { EMPTY, NEW, RESUME, CHANGED }
    enum Advance { CONTINUE, FINISHED, SAVE_FAILED }

    private final SharedPreferences preferences;
    private List<Question> questions = Collections.emptyList();
    private long collectionId = -1;
    private String fingerprint = "";
    private int cursor;
    private int correct;
    private final List<String> mistakes = new ArrayList<>();
    private String draft = "";
    private boolean checked;
    private boolean passed;

    KnowledgeCheckSession(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    Availability prepare(long id, List<Question> material) {
        questions = Collections.unmodifiableList(new ArrayList<>(material));
        if (questions.isEmpty()) return Availability.EMPTY;
        collectionId = id;
        List<String> parts = new ArrayList<>();
        for (Question question : questions) {
            parts.add(Long.toString(question.entryId()));
            parts.add(Integer.toString(question.position()));
            parts.add(question.reference());
            parts.add(question.text());
        }
        fingerprint = SessionIntegrityLogic.fingerprint(parts);
        String key = key(id);
        if (!preferences.getBoolean(key+"active", false)) return Availability.NEW;
        return SessionIntegrityLogic.canResumeRun(preferences.getString(key+"fingerprint", ""),
            fingerprint, preferences.getInt(key+"cursor", -1), preferences.getInt(key+"correct", -1),
            questions.size()) ? Availability.RESUME : Availability.CHANGED;
    }

    void restart() {
        clearAnswer();
        cursor = 0;
        correct = 0;
        mistakes.clear();
        save();
    }

    void restore() {
        String key = key(collectionId);
        cursor = Math.min(Math.max(0, preferences.getInt(key+"cursor", 0)), questions.size()-1);
        correct = preferences.getInt(key+"correct", 0);
        draft = preferences.getString(key+"draft", "");
        checked = preferences.getBoolean(key+"checked", false);
        passed = preferences.getBoolean(key+"passed", false);
        mistakes.clear();
        try {
            JSONArray stored = new JSONArray(preferences.getString(key+"missed", "[]"));
            for (int i = 0; i < stored.length(); i++) mistakes.add(stored.getString(i));
        } catch (JSONException ignored) {
            // Malformed optional mistake history does not invalidate the exercise itself.
        }
    }

    Task task() {
        if (questions.isEmpty() || cursor >= questions.size()) return null;
        Question question = questions.get(cursor);
        String feedback = checked && !passed ? AcceptedTextLogic.bestExpected(question.text(), draft,
            AcceptedTextLogic.matthew513Verse(question.reference())) : "";
        return new Task(cursor, questions.size(), question, draft, checked, passed, feedback);
    }

    Result result() {
        return new Result(questions.size(), correct, Collections.unmodifiableList(new ArrayList<>(mistakes)));
    }

    void edit(String text) {
        draft = text;
        save();
    }

    boolean check() {
        if (draft.trim().isEmpty()) return false;
        Question question = questions.get(cursor);
        passed = AcceptedTextLogic.exact(question.text(), draft,
            AcceptedTextLogic.matthew513Verse(question.reference()));
        checked = true;
        save();
        return true;
    }

    Advance advance(long now) {
        Question question = questions.get(cursor);
        if (checked && passed) correct++;
        else mistakes.add(question.reference());
        cursor++;
        clearAnswer();
        if (cursor < questions.size()) {
            save();
            return Advance.CONTINUE;
        }
        try {
            String historyKey = historyKey(collectionId);
            JSONArray history = new JSONArray(preferences.getString(historyKey, "[]"));
            JSONObject result = new JSONObject();
            result.put("timestamp", now);
            result.put("total", questions.size());
            result.put("correct", correct);
            result.put("mistakes", new JSONArray(mistakes));
            JSONArray updated = new JSONArray();
            updated.put(result);
            for (int i = 0; i < Math.min(history.length(), 19); i++) updated.put(history.getJSONObject(i));
            preferences.edit().putString(historyKey, updated.toString())
                .remove(key(collectionId)+"active").apply();
            return Advance.FINISHED;
        } catch (JSONException error) {
            android.util.Log.e("MemoryRun", "Unable to save result", error);
            return Advance.SAVE_FAILED;
        }
    }

    void save() {
        if (collectionId < 0 || questions.isEmpty()) return;
        String key = key(collectionId);
        preferences.edit().putBoolean(key+"active", true).putString(key+"fingerprint", fingerprint)
            .putInt(key+"cursor", cursor).putInt(key+"correct", correct).putString(key+"draft", draft)
            .putBoolean(key+"checked", checked).putBoolean(key+"passed", passed)
            .putString(key+"missed", new JSONArray(mistakes).toString()).apply();
    }

    void clearCollection(long id, boolean clearHistory) {
        SharedPreferences.Editor edit = preferences.edit();
        String prefix = key(id);
        if (clearHistory) {
            edit.remove(historyKey(id));
            for (String suffix : new String[]{"active", "cursor", "correct", "draft", "checked", "passed", "missed", "fingerprint"})
                edit.remove(prefix + suffix);
        } else {
            for (String name : preferences.getAll().keySet())
                if (name.startsWith(prefix)) edit.remove(name);
        }
        edit.apply();
        if (collectionId == id) clear();
    }

    void clear() {
        collectionId = -1;
        fingerprint = "";
        questions = Collections.emptyList();
        cursor = 0;
        correct = 0;
        mistakes.clear();
        clearAnswer();
    }

    private void clearAnswer() {
        draft = "";
        checked = false;
        passed = false;
    }

    private static String key(long id) { return "writing_run_"+id+"_"; }
    private static String historyKey(long id) { return "run_history_"+id; }
}
