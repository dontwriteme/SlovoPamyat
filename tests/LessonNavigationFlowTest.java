package org.biblememory.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class LessonNavigationFlowTest {
    @Test public void allDestinationsPreserveOriginalPriority(){
        GroupLearningLogic.Candidate candidate=GroupLearningLogic.candidate("CHAIN",0,2,"fixture");
        for(boolean review:new boolean[]{false,true})
        for(boolean returnedFromGroup:new boolean[]{false,true})
        for(boolean pending:new boolean[]{false,true})
        for(boolean moreVerses:new boolean[]{false,true})
        for(boolean collection:new boolean[]{false,true})
        for(boolean card:new boolean[]{false,true}){
            int next=moreVerses?2:3;
            LessonNavigationFlow.Decision actual=LessonNavigationFlow.afterSuccess(review,
                returnedFromGroup,pending?candidate:null,next,3,collection,card);
            LessonNavigationFlow.Destination expected;
            if(review)expected=LessonNavigationFlow.Destination.REVIEW;
            else if(pending)expected=LessonNavigationFlow.Destination.GROUP;
            else if(moreVerses)expected=LessonNavigationFlow.Destination.VERSE;
            else if(collection)expected=LessonNavigationFlow.Destination.COLLECTION_AFTER_GROUPS;
            else if(card)expected=LessonNavigationFlow.Destination.CARD;
            else if(returnedFromGroup)expected=LessonNavigationFlow.Destination.GROUPS_OR_EXIT;
            else expected=LessonNavigationFlow.Destination.EXIT;
            assertEquals(expected,actual.destination());
            assertEquals(expected==LessonNavigationFlow.Destination.VERSE?next:-1,actual.verse());
            assertSame(expected==LessonNavigationFlow.Destination.GROUP?candidate:null,actual.group());
        }
    }

    @Test public void groupReturnUsesRestoredVerseRatherThanEndOfGroup(){
        LessonNavigationFlow.Decision next=LessonNavigationFlow.afterSuccess(false,true,null,2,5,true,false);
        assertEquals(LessonNavigationFlow.Destination.VERSE,next.destination());
        assertEquals(2,next.verse());
    }

    @Test public void lastStandaloneGroupStillChecksUnfinishedGroups(){
        assertEquals(LessonNavigationFlow.Destination.GROUPS_OR_EXIT,
            LessonNavigationFlow.afterSuccess(false,true,null,5,5,false,false).destination());
        assertEquals(LessonNavigationFlow.Destination.EXIT,
            LessonNavigationFlow.afterSuccess(false,false,null,5,5,false,false).destination());
    }
}
