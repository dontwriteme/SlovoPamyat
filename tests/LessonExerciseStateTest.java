package org.biblememory.app;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LessonExerciseStateTest {
    @Test public void checkpointDoesNotShareMutableTileSelection(){
        List<Integer> selected=new ArrayList<>(Arrays.asList(2,0));
        LessonExerciseState state=new LessonExerciseState("ответ",10,1,2,2,19L,
            "geometry","rotation",19L,"0,2",selected,1,true,false,false,"");
        selected.clear();
        assertEquals(Arrays.asList(2,0),state.usedTiles());
        assertEquals("[2, 0]",state.encodedTiles());
        try{
            state.usedTiles().add(9);
            fail("Mutable checkpoint");
        }catch(UnsupportedOperationException expected){}
    }

    @Test public void legacyTileEncodingRetainsOrderAndIgnoresMalformedNumbers(){
        assertEquals(Arrays.asList(4,2,4,-1),
            LessonExerciseState.decodeTiles("[4, invalid, 2, 4, -1, ]"));
        assertTrue(LessonExerciseState.decodeTiles("[]").isEmpty());
        assertTrue(LessonExerciseState.decodeTiles("").isEmpty());
    }

    @Test public void checkedDraftCannotBecomeAnUnaidedAnswer(){
        assertEquals("",LessonExerciseState.resumableDraft("видимый ответ",true));
        assertEquals("",LessonExerciseState.resumableDraft(null,false));
        assertEquals("черновик",LessonExerciseState.resumableDraft("черновик",false));
    }
}
