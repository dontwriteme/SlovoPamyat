package org.biblememory.app;

import java.util.Collections;
import java.util.List;

/** Decisions for the active lesson, independent of views, storage and lifecycle. */
final class LessonStageFlow {
    private LessonStageFlow() {}

    record State(StudyPlanLogic.Stage stage,int completed,boolean review,boolean group,
                 boolean collectionQueue,boolean practiceAgain,
                 boolean groupIntroDone,boolean groupBuildDone) {
        boolean writingOnly(){
            boolean learnedBefore=collectionQueue&&stage==StudyPlanLogic.Stage.VERSE&&completed>=5;
            return !group&&(review||practiceAgain||stage!=StudyPlanLogic.Stage.VERSE||learnedBefore);
        }

        LessonCurriculumLogic.Phase phase(){
            if(group)return !groupIntroDone?LessonCurriculumLogic.Phase.INTRO:
                (groupBuildDone?LessonCurriculumLogic.Phase.WRITE:LessonCurriculumLogic.Phase.BUILD);
            return LessonCurriculumLogic.phase(completed,writingOnly());
        }

        int introDone(){
            return group?(groupIntroDone?1:0):(completed>=1?1:0);
        }
    }

    static List<LessonCurriculumLogic.Variant> writingVariants(String passage,
            boolean review,boolean practiceAgain,boolean practiceFragment){
        if(review||practiceAgain||practiceFragment)
            return Collections.singletonList(LessonCurriculumLogic.Variant.FULL);
        return LessonCurriculumLogic.variants(ReviewLogic.words(passage).size());
    }

    // A zero persistedCompletion means no SQLite write for this transition.
    record BuildTransition(int nextVariant,int nextChunk,int persistedCompletion,
                           boolean groupCompleted,boolean beginWriting) {}

    static BuildTransition advanceBuild(int variant,int chunk,int variantCount,
                                        int chunkCount,boolean group){
        if(chunk+1<chunkCount)
            return new BuildTransition(variant,chunk+1,0,false,false);
        if(variant+1<variantCount)
            return new BuildTransition(variant+1,0,group?0:2,false,false);
        return new BuildTransition(0,0,group?0:3,group,true);
    }
}
