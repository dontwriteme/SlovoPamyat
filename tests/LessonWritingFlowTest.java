package org.biblememory.app;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LessonWritingFlowTest {
    private static final StudyPlanLogic.Stage VERSE=StudyPlanLogic.Stage.VERSE;
    private static final StudyPlanLogic.Stage CHAIN=StudyPlanLogic.Stage.CHAIN;

    private LessonWritingFlow.Context context(StudyPlanLogic.Stage stage,boolean review,
            boolean group,boolean run,boolean fragment,boolean feedback){
        return new LessonWritingFlow.Context(42L,stage,3,5,review,group,run,fragment,feedback);
    }

    private LessonWritingFlow.Transition decide(LessonWritingFlow.Context c,String actual,
            boolean full,boolean exact){
        WritingAssessment.Input input=new WritingAssessment.Input("Первый стих Второй стих",actual,
            false,full,c.practiceFragment(),Collections.emptyList(),
            Collections.emptyList(),Collections.emptyList());
        return LessonWritingFlow.decide(c,input,new WritingAssessment.Result(
            exact,exact?100:60,"Первый стих Второй стих",null,null));
    }

    private static final class Writer implements LessonWritingFlow.RecallWriter {
        final List<List<Object>> calls=new ArrayList<>();
        RuntimeException error;
        private void save(Object... args){
            if(error!=null)throw error;
            calls.add(Arrays.asList(args));
        }
        public void commitFailedRecall(long id,String stage,int start,int end,int accuracy,
                boolean schedule,WeakTransitionLogic.WeakSpot weak,long now){
            save("failed",id,stage,start,end,accuracy,schedule,weak,now);
        }
        public void recordRecall(long id,String stage,int start,int end,int accuracy,
                boolean assisted,long now){
            save("assisted",id,stage,start,end,accuracy,assisted,now);
        }
        public void commitSuccessfulRecall(long id,String stage,int start,int end,
                boolean verseRun,boolean finishLesson,long now){
            save("success",id,stage,start,end,verseRun,finishLesson,now);
        }
    }

    @Test public void decisionsMatchOriginalBranchOrder(){
        for(boolean review:new boolean[]{false,true})
        for(boolean group:new boolean[]{false,true})
        for(boolean fragment:new boolean[]{false,true})
        for(boolean feedback:new boolean[]{false,true})
        for(boolean full:new boolean[]{false,true})
        for(boolean exact:new boolean[]{false,true})
        for(String actual:new String[]{" ","Ответ"}){
            LessonWritingFlow.Context c=context(group?CHAIN:VERSE,review,group,false,fragment,feedback);
            LessonWritingFlow.Outcome expected;
            if(actual.trim().isEmpty())expected=LessonWritingFlow.Outcome.FEEDBACK;
            else if(fragment)expected=exact?LessonWritingFlow.Outcome.FRAGMENT_PRACTICED:LessonWritingFlow.Outcome.FEEDBACK;
            else if(exact&&!full)expected=LessonWritingFlow.Outcome.NEXT_VARIANT;
            else if(!exact&&!full)expected=LessonWritingFlow.Outcome.FEEDBACK;
            else if(!exact)expected=LessonWritingFlow.Outcome.FAILED_RECALL;
            else if(feedback)expected=LessonWritingFlow.Outcome.ASSISTED_CORRECTION;
            else expected=LessonWritingFlow.Outcome.SUCCESSFUL_RECALL;
            assertEquals(expected,decide(c,actual,full,exact).outcome());
        }
    }

    @Test public void practiceAndPartialAnswersDoNotWriteRecall(){
        Writer w=new Writer();
        for(boolean fragment:new boolean[]{false,true})
        for(boolean exact:new boolean[]{false,true}){
            decide(context(VERSE,false,false,false,fragment,false),"Ответ",false,exact).persist(w,123);
        }
        decide(context(VERSE,true,false,false,true,false),"Ответ",true,true).persist(w,123);
        decide(context(VERSE,false,false,false,false,false)," ",true,false).persist(w,123);
        assertTrue(w.calls.isEmpty());
    }

    @Test public void correctAnswersUseExistingAtomicWriteWithModeFlags(){
        Writer w=new Writer();
        decide(context(VERSE,false,false,false,false,false),"Ответ",true,true).persist(w,123);
        decide(context(VERSE,true,false,false,false,false),"Ответ",true,true).persist(w,124);
        decide(context(CHAIN,false,true,false,false,false),"Ответ",true,true).persist(w,125);
        decide(context(CHAIN,true,false,true,false,false),"Ответ",true,true).persist(w,126);
        assertEquals(Arrays.asList(
            Arrays.asList("success",42L,"VERSE",3,5,false,true,123L),
            Arrays.asList("success",42L,"VERSE",3,5,false,false,124L),
            Arrays.asList("success",42L,"CHAIN",3,5,false,false,125L),
            Arrays.asList("success",42L,"CHAIN",3,5,true,false,126L)),w.calls);
    }

    @Test public void failuresPreserveScheduleAndVerseRunSemantics(){
        Writer w=new Writer();
        decide(context(VERSE,false,false,false,false,false),"Ответ",true,false).persist(w,123);
        decide(context(VERSE,true,false,false,false,false),"Ответ",true,false).persist(w,124);
        decide(context(CHAIN,false,true,false,false,false),"Ответ",true,false).persist(w,125);
        decide(context(CHAIN,true,false,true,false,false),"Ответ",true,false).persist(w,126);
        assertEquals(Arrays.asList(
            Arrays.asList("failed",42L,"VERSE",3,5,60,false,null,123L),
            Arrays.asList("failed",42L,"VERSE",3,5,60,true,null,124L),
            Arrays.asList("failed",42L,"CHAIN",3,5,60,true,null,125L),
            Arrays.asList("failed",42L,"VERSE_RUN",3,5,60,true,null,126L)),w.calls);
    }

    @Test public void visibleCorrectionIsAssistedAndStorageErrorsPropagate(){
        Writer w=new Writer();
        decide(context(VERSE,false,false,false,false,true),"Ответ",true,true).persist(w,123);
        decide(context(CHAIN,true,false,true,false,true),"Ответ",true,true).persist(w,124);
        assertEquals(Arrays.asList(
            Arrays.asList("assisted",42L,"VERSE",3,5,100,true,123L),
            Arrays.asList("assisted",42L,"CHAIN",3,5,100,true,124L)),w.calls);
        w.error=new IllegalStateException("storage failed");
        try{
            decide(context(VERSE,false,false,false,false,false),"Ответ",true,true).persist(w,125);
            fail("Storage error was swallowed");
        }catch(IllegalStateException error){assertSame(w.error,error);}
        assertEquals(2,w.calls.size());
    }
}
