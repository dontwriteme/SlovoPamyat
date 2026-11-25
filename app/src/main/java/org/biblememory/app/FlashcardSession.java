package org.biblememory.app;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Progress through a deck of viewed cards. The lesson and its progress are separate. */
final class FlashcardSession {
    private final SharedPreferences preferences;
    private final Random random;
    private final List<Long> order = new ArrayList<>();
    private int cursor;
    private boolean referenceFirst = true;
    private boolean revealed;

    FlashcardSession(SharedPreferences preferences, Random random) {
        this.preferences = preferences;
        this.random = random;
    }

    boolean start(long deckId, List<Long> available) {
        if (available.isEmpty()) return false;
        order.clear();
        cursor = 0;
        String saved = preferences.getString(key(deckId), "");
        if (!saved.isEmpty()) {
            try {
                JSONObject json = new JSONObject(saved);
                JSONArray ids = json.getJSONArray("order");
                for (int index = 0; index < ids.length(); index++) order.add(ids.getLong(index));
                cursor = Math.max(0, Math.min(order.size() - 1, json.optInt("cursor", 0)));
                referenceFirst = json.optBoolean("reference_first", true);
            } catch (JSONException invalid) {
                order.clear();
            }
        }
        if (order.size() != available.size() || !new HashSet<>(order).equals(new HashSet<>(available))) {
            order.clear();
            order.addAll(FlashcardDeckLogic.shuffled(available, -1, random));
            cursor = 0;
            referenceFirst = random.nextBoolean();
        }
        revealed = false;
        save(deckId);
        return true;
    }

    boolean advance(long deckId, Supplier<List<Long>> available) {
        if (order.isEmpty()) return false;
        if (cursor + 1 < order.size()) cursor++;
        else {
            long previous = order.get(order.size() - 1);
            List<Long> nextOrder = FlashcardDeckLogic.shuffled(available.get(), previous, random);
            order.clear();
            order.addAll(nextOrder);
            cursor = 0;
        }
        revealed = false;
        referenceFirst = random.nextBoolean();
        save(deckId);
        return true;
    }

    long currentId() { return order.isEmpty() ? -1 : order.get(cursor); }
    boolean referenceFirst() { return referenceFirst; }
    boolean revealed() { return revealed; }

    boolean toggleAnswer() {
        revealed = !revealed;
        return revealed;
    }

    void save(long deckId) {
        if (deckId < 1 || order.isEmpty()) return;
        try {
            JSONObject json = new JSONObject();
            json.put("order", new JSONArray(order));
            json.put("cursor", cursor);
            json.put("reference_first", referenceFirst);
            preferences.edit().putString(key(deckId), json.toString()).apply();
        } catch (JSONException ignored) {
            // The JSON is built from a list of IDs, an integer and a boolean.
        }
    }

    void clearSaved(long deckId) {
        preferences.edit().remove(key(deckId)).apply();
    }

    private String key(long deckId) { return "deck_" + deckId; }
}
