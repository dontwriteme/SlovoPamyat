package org.biblememory.app;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LessonWritingStateTest {
    @Test public void gapEditsKeepTheExistingSerializedDraftAndAnswer(){
        LessonWritingState state=new LessonWritingState();
        String[] tokens={"Слово","даёт","свет"};
        int[] missing={0,2};
        state.restoreGaps(tokens,missing);
        state.editGap(0,"Слово");
        state.editGap(1,"свет");
        assertEquals(InlineGapLogic.serialize(new String[]{"Слово","свет"}),state.draft);
        assertEquals("Слово свет",state.gapResponse());
        state.restoreGaps(tokens,missing);
        assertEquals("Слово",state.gap(0));
        assertEquals("свет",state.gap(1));
        assertEquals(Arrays.asList("Слово","свет"),state.gapAnswers());
    }

    @Test public void retryAndNextVariantClearAnswersAndFeedback(){
        LessonWritingState state=new LessonWritingState();
        state.variant=1;
        state.restoreGaps(new String[]{"слово"},new int[]{0});
        state.editGap(0,"ответ");
        state.hadFeedback=true;
        state.retry();
        assertEquals(1,state.variant);
        assertEquals("",state.draft);
        assertTrue(state.gapAnswers().isEmpty());
        assertFalse(state.hadFeedback);
        state.draft="новый ответ";
        state.hadFeedback=true;
        state.nextVariant();
        assertEquals(2,state.variant);
        assertEquals("",state.draft);
        assertFalse(state.hadFeedback);
    }

    @Test public void weakFragmentReturnsToFullAnswerWithoutCredit(){
        LessonWritingState state=new LessonWritingState();
        state.practiceWeakFragment=true;
        state.weakFragmentText="слабая фраза";
        state.draft="слабая фраза";
        state.hadFeedback=true;
        state.finishFragment(2);
        assertFalse(state.practiceWeakFragment);
        assertEquals("",state.weakFragmentText);
        assertEquals("",state.draft);
        assertFalse(state.hadFeedback);
        assertEquals(2,state.variant);
    }

    @Test public void checkpointRestoresDraftButNeverPersistedFeedback(){
        LessonWritingState state=new LessonWritingState();
        state.hadFeedback=true;
        LessonExerciseState checkpoint=new LessonExerciseState("черновик",0,0,0,0,1L,
            "","",0L,"",Collections.emptyList(),2,false,false,true,"фраза");
        state.restore(checkpoint);
        assertEquals("черновик",state.draft);
        assertEquals(2,state.variant);
        assertTrue(state.practiceWeakFragment);
        assertEquals("фраза",state.weakFragmentText);
        assertFalse(state.hadFeedback);
        assertEquals("черновик",state.resumableDraft(false));
        assertEquals("",state.resumableDraft(true));
        state.hadFeedback=true;
        assertEquals("",state.resumableDraft(false));
    }
}
