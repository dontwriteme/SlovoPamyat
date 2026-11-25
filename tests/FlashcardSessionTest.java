package org.biblememory.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;
import java.util.Random;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class FlashcardSessionTest {
    private final SharedPreferences prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("card_sessions", Context.MODE_PRIVATE);
    private final FlashcardSession session = new FlashcardSession(prefs, new Random(17));

    @Test public void restoresLegacyOrderCursorAndDirectionButHidesAnswer() throws Exception {
        prefs.edit().putString("deck_7", "{\"order\":[3,2,1],\"cursor\":1,\"reference_first\":false}").apply();
        assertTrue(session.start(7, List.of(1L, 2L, 3L)));
        assertEquals(2, session.currentId());
        assertFalse(session.referenceFirst());
        assertTrue(session.toggleAnswer());
        assertTrue(session.advance(7, () -> { throw new AssertionError("Deck queried before wrap"); }));
        assertEquals(1, session.currentId());
        assertFalse(session.revealed());
        JSONObject saved = new JSONObject(prefs.getString("deck_7", ""));
        assertEquals(2, saved.getInt("cursor"));
        FlashcardSession resumed = new FlashcardSession(prefs, new Random(29));
        assertTrue(resumed.start(7, List.of(1L, 2L, 3L)));
        assertEquals(session.currentId(), resumed.currentId());
        assertEquals(session.referenceFirst(), resumed.referenceFirst());
        assertFalse(resumed.revealed());
    }

    @Test public void wrapsWithoutRepeatingPreviousAndReconcilesChangedDeck() {
        prefs.edit().putString("deck_7", "{\"order\":[1,2],\"cursor\":99}").apply();
        assertTrue(session.start(7, List.of(1L, 2L)));
        assertEquals(2, session.currentId());
        assertTrue(session.advance(7, () -> List.of(1L, 2L)));
        assertEquals(1, session.currentId());
        assertTrue(session.start(7, List.of(3L)));
        assertEquals(3, session.currentId());
        assertTrue(session.advance(7, () -> List.of(3L)));
        assertEquals(3, session.currentId());
    }

    @Test public void handlesCorruptStateEmptyDeckAndRemoval() {
        assertFalse(session.start(7, List.of()));
        assertFalse(session.advance(7, List::of));
        prefs.edit().putString("deck_7", "{\"order\":[1,\"bad\"]}").apply();
        assertTrue(session.start(7, List.of(5L)));
        assertTrue(session.advance(7, List::of));
        assertEquals(-1, session.currentId());
        session.clearSaved(7);
        assertFalse(prefs.contains("deck_7"));
    }
}
