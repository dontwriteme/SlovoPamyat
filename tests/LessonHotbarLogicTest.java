package org.biblememory.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class LessonHotbarLogicTest {
    @Test public void openedIntroIsOneCurrentExerciseWithZeroCompleted(){
        LessonHotbarLogic.Model m=LessonHotbarLogic.calculate(0,1,5,3,0,0,0,false);
        assertEquals(1,m.totals[0]);assertEquals(9,m.total);assertEquals(0,m.completedCount);
        assertEquals(1,m.currentNumber);assertEquals(LessonHotbarLogic.CURRENT,m.visual(0,0));
        assertEquals(LessonHotbarLogic.UPCOMING,m.visual(1,0));
    }
    @Test public void transitionAndRepeatedTransitionCountIntroOnce(){
        for(int attempt=0;attempt<3;attempt++){
            LessonHotbarLogic.Model m=LessonHotbarLogic.calculate(1,1,5,3,1,0,0,false);
            assertEquals(1,m.completedCount);assertEquals(2,m.currentNumber);
            assertEquals(LessonHotbarLogic.DONE,m.visual(0,0));assertEquals(LessonHotbarLogic.CURRENT,m.visual(1,0));
        }
    }
    @Test public void writingOnlyHasNoFictitiousIntroOrBuild(){
        LessonHotbarLogic.Model m=LessonHotbarLogic.calculate(2,1,20,3,1,20,1,true);
        assertArrayEquals(new int[]{0,0,3},m.totals);assertEquals(3,m.total);
        assertEquals(1,m.completedCount);assertEquals(2,m.currentNumber);assertEquals(LessonHotbarLogic.CURRENT,m.visual(2,1));
    }
    @Test public void totalsFollowTheActualCurriculum(){
        for(int build:new int[]{1,5,17,1000})for(int write:new int[]{1,2,3}){
            LessonHotbarLogic.Model m=LessonHotbarLogic.calculate(0,1,build,write,0,0,0,false);
            assertEquals(1+build+write,m.total);assertEquals(0,m.completedCount);
        }
    }
    @Test public void completedCountRemainsDistinctFromCurrentOrdinal(){
        LessonHotbarLogic.Model m=LessonHotbarLogic.calculate(1,1,5,3,1,2,0,false);
        assertEquals(3,m.completedCount);assertEquals(4,m.currentNumber);
    }
    @Test public void unavailableIntroAddsNoSegment(){
        LessonHotbarLogic.Model m=LessonHotbarLogic.calculate(1,0,4,1,0,0,0,false);
        assertEquals(0,m.totals[0]);assertEquals(0,m.completedCount);assertEquals(1,m.currentNumber);
    }
}
