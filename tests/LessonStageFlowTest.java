package org.biblememory.app;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LessonStageFlowTest {
    @Test public void phasesAndHotbarFlagsMatchPreviousRules(){
        for(StudyPlanLogic.Stage stage:StudyPlanLogic.Stage.values())
        for(int completed=0;completed<=5;completed++)
        for(boolean review:new boolean[]{false,true})
        for(boolean group:new boolean[]{false,true})
        for(boolean queue:new boolean[]{false,true})
        for(boolean practice:new boolean[]{false,true})
        for(boolean intro:new boolean[]{false,true})
        for(boolean built:new boolean[]{false,true}){
            LessonStageFlow.State state=new LessonStageFlow.State(stage,completed,
                review,group,queue,practice,intro,built);
            boolean previouslyLearned=queue&&stage==StudyPlanLogic.Stage.VERSE&&completed>=5;
            boolean independent=review||practice||stage!=StudyPlanLogic.Stage.VERSE||previouslyLearned;
            LessonCurriculumLogic.Phase expected=group?
                (!intro?LessonCurriculumLogic.Phase.INTRO:
                    (built?LessonCurriculumLogic.Phase.WRITE:LessonCurriculumLogic.Phase.BUILD)):
                LessonCurriculumLogic.phase(completed,independent);
            assertEquals(expected,state.phase());
            assertEquals(!group&&independent,state.writingOnly());
            assertEquals(group?(intro?1:0):(completed>=1?1:0),state.introDone());
        }
    }

    @Test public void partialAssemblyAdvancesOnlyChunk(){
        for(boolean group:new boolean[]{false,true}){
            LessonStageFlow.BuildTransition next=LessonStageFlow.advanceBuild(1,2,3,4,group);
            assertEquals(new LessonStageFlow.BuildTransition(1,3,0,false,false),next);
        }
    }

    @Test public void intermediateVariantPreservesExistingProgressRules(){
        assertEquals(new LessonStageFlow.BuildTransition(1,0,2,false,false),
            LessonStageFlow.advanceBuild(0,2,3,3,false));
        assertEquals(new LessonStageFlow.BuildTransition(1,0,0,false,false),
            LessonStageFlow.advanceBuild(0,2,3,3,true));
    }

    @Test public void finalAssemblyStartsWritingWithoutFalseRecallCredit(){
        assertEquals(new LessonStageFlow.BuildTransition(0,0,3,false,true),
            LessonStageFlow.advanceBuild(0,0,1,1,false));
        assertEquals(new LessonStageFlow.BuildTransition(0,0,3,false,true),
            LessonStageFlow.advanceBuild(2,3,3,4,false));
        assertEquals(new LessonStageFlow.BuildTransition(0,0,0,true,true),
            LessonStageFlow.advanceBuild(2,3,3,4,true));
    }

    @Test public void writingVariantRulesRetainLengthThresholdsAndPracticeModes(){
        for(int words:new int[]{0,1,8,9,20,21,100}){
            String passage=String.join(" ",Collections.nCopies(words,"слово"));
            for(boolean review:new boolean[]{false,true})
            for(boolean practice:new boolean[]{false,true})
            for(boolean fragment:new boolean[]{false,true}){
                List<LessonCurriculumLogic.Variant> expected=review||practice||fragment?
                    Collections.singletonList(LessonCurriculumLogic.Variant.FULL):
                    LessonCurriculumLogic.variants(words);
                assertEquals(expected,LessonStageFlow.writingVariants(passage,review,practice,fragment));
            }
        }
    }
}
