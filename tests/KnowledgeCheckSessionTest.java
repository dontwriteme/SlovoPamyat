package org.biblememory.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class KnowledgeCheckSessionTest {
    private final SharedPreferences preferences = RuntimeEnvironment.getApplication()
        .getSharedPreferences("knowledge-check-fixture", Context.MODE_PRIVATE);
    private final KnowledgeCheckSession.Question first = new KnowledgeCheckSession.Question(2,0,"Первый","Первый стих");
    private final KnowledgeCheckSession.Question second = new KnowledgeCheckSession.Question(3,0,"Второй","Второй стих");
    private final List<KnowledgeCheckSession.Question> questions = Arrays.asList(first,second);

    @Test public void restartAndRestoreKeepDraftAndCheckedResultUntilAdvance() {
        KnowledgeCheckSession initial = new KnowledgeCheckSession(preferences);
        assertEquals(KnowledgeCheckSession.Availability.NEW, initial.prepare(7, questions));
        initial.restart();
        assertFalse(initial.check());
        initial.edit("Первый");
        KnowledgeCheckSession resumed = new KnowledgeCheckSession(preferences);
        assertEquals(KnowledgeCheckSession.Availability.RESUME, resumed.prepare(7, questions));
        resumed.restore();
        assertEquals("Первый", resumed.task().draft());
        assertFalse(resumed.task().checked());
        resumed.edit("Первый стих");
        assertTrue(resumed.check());
        KnowledgeCheckSession afterCheck = new KnowledgeCheckSession(preferences);
        assertEquals(KnowledgeCheckSession.Availability.RESUME, afterCheck.prepare(7, questions));
        afterCheck.restore();
        assertTrue(afterCheck.task().checked()); assertTrue(afterCheck.task().passed());
        assertEquals(KnowledgeCheckSession.Advance.CONTINUE, afterCheck.advance(1));
        assertEquals(second, afterCheck.task().question());
        assertEquals("", afterCheck.task().draft());
        assertEquals(1, afterCheck.result().correct());
    }

    @Test public void completionRecordsWrongAndSkippedQuestionsAndClosesDraft() throws Exception {
        KnowledgeCheckSession run = new KnowledgeCheckSession(preferences);
        run.prepare(7, questions); run.restart();
        run.edit("Чужой"); assertTrue(run.check());
        assertFalse(run.task().passed());
        assertEquals("Первый стих", run.task().feedbackExpected());
        assertEquals(KnowledgeCheckSession.Advance.CONTINUE, run.advance(1));
        assertEquals(KnowledgeCheckSession.Advance.FINISHED, run.advance(2));
        assertNull(run.task());
        assertEquals(Arrays.asList("Первый","Второй"), run.result().mistakes());
        JSONArray history = new JSONArray(preferences.getString("run_history_7","[]"));
        assertEquals(1, history.length());
        assertEquals(0, history.getJSONObject(0).getInt("correct"));
        assertFalse(preferences.contains("writing_run_7_active"));
    }

    @Test public void differentMaterialRefusesOldDraftAndClearKeepsOtherCollections() {
        KnowledgeCheckSession run = new KnowledgeCheckSession(preferences);
        run.prepare(7, questions); run.restart(); run.edit("Незаконченный");
        assertEquals(KnowledgeCheckSession.Availability.CHANGED, run.prepare(7, Arrays.asList(second,first)));
        run.restart(); assertEquals("", run.task().draft());
        preferences.edit().putString("run_history_7","[]").putBoolean("writing_run_8_active",true).apply();
        run.clearCollection(7,false);
        assertTrue(preferences.contains("run_history_7"));
        assertFalse(preferences.contains("writing_run_7_active"));
        assertTrue(preferences.contains("writing_run_8_active"));
        run.clearCollection(7,true);
        assertFalse(preferences.contains("run_history_7"));
    }
}
