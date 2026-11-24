package org.biblememory.app;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LessonAssemblyStateTest {
    private final String[] tokens={"Слово,","даёт","свет.","слово"};
    private final int[] missing={0,2,3};

    @Test public void wrongTapDoesNotAdvanceAndCorrectTapClearsFeedback(){
        LessonAssemblyState state=new LessonAssemblyState();
        assertEquals(LessonAssemblyState.Tap.WRONG,state.tap(tokens,missing,1));
        assertEquals(0,state.picked());assertEquals(1,state.wrongTile());
        assertTrue(state.usedTiles().isEmpty());
        assertEquals(LessonAssemblyState.Tap.CORRECT,state.tap(tokens,missing,0));
        assertEquals(1,state.picked());assertEquals(-1,state.wrongTile());
        assertEquals(Collections.singletonList(0),state.usedTiles());
        assertEquals(LessonAssemblyState.Tap.IGNORED,state.tap(tokens,missing,0));
        assertEquals(1,state.picked());
    }

    @Test public void repeatedWordsCanBeSelectedInEitherEquivalentOrder(){
        LessonAssemblyState state=new LessonAssemblyState();
        assertEquals(LessonAssemblyState.Tap.CORRECT,state.tap(tokens,missing,2));
        assertEquals(LessonAssemblyState.Tap.CORRECT,state.tap(tokens,missing,1));
        assertEquals(LessonAssemblyState.Tap.CORRECT,state.tap(tokens,missing,0));
        assertEquals(3,state.picked());
        assertEquals(Arrays.asList(2,1,0),state.usedTiles());
        state.validatePrefix(tokens,missing);
        assertEquals(3,state.picked());
        assertEquals(LessonAssemblyState.Tap.IGNORED,state.tap(tokens,missing,1));
    }

    @Test public void restoreValidPrefixAndResetInvalidPrefix(){
        LessonAssemblyState state=new LessonAssemblyState();
        state.restore(2,Arrays.asList(2,1));
        state.validatePrefix(tokens,missing);
        assertEquals(2,state.picked());
        assertEquals(-1,state.wrongTile());
        state.restore(2,Arrays.asList(1,1));
        state.validatePrefix(tokens,missing);
        assertEquals(0,state.picked());
        assertTrue(state.usedTiles().isEmpty());
        assertEquals(-1,state.wrongTile());
    }

    @Test public void resetClearsSelectionAndIgnoresInvalidBankIndices(){
        LessonAssemblyState state=new LessonAssemblyState();
        state.tap(tokens,missing,0);state.tap(tokens,missing,2);
        state.reset();
        assertEquals(0,state.picked());assertEquals(-1,state.wrongTile());
        assertTrue(state.usedTiles().isEmpty());
        assertEquals(LessonAssemblyState.Tap.IGNORED,state.tap(tokens,missing,-1));
        assertEquals(LessonAssemblyState.Tap.IGNORED,state.tap(tokens,missing,3));
        try{
            state.usedTiles().add(0);
            fail("External selection mutation");
        }catch(UnsupportedOperationException expected){}
    }
}
