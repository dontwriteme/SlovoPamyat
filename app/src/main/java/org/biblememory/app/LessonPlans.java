package org.biblememory.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.biblememory.app.LessonSessionState.TrainingMode;

final class LessonPlans {
    private LessonPlans(){}
    static AdaptiveLearningLogic.Recommendation recommend(LocalStore store,LessonSessionState lesson,LocalStore.Entry e,long now) {
        List<String> refs=new ArrayList<>();
        for(Bible.Verse v:e.lines())refs.add(v.ref);
        AdaptiveLearningLogic.Recommendation advice=AdaptiveLearningLogic.recommend(
            refs,isSermon(e),store.collectionProgress().recommendationProgress(e.id),now);
        lesson.studyStage=advice.stage;lesson.stepCursor=advice.index;
        return advice;
    }
    static boolean isSermon(LocalStore.Entry e) {
        return e.reference!=null && (e.reference.startsWith("Матфея 5:") ||
          e.reference.startsWith("Матфея 6:") || e.reference.startsWith("Матфея 7:"));
    }
    static List<StudyPlanLogic.Step> steps(LocalStore.Entry e,LessonSessionState lesson,ReviewSession reviewSession) {
        if(reviewSession.active())return Collections.singletonList(reviewSession.current().step());
        if(lesson.groupMode&&lesson.forcedStep!=null)return Collections.singletonList(lesson.forcedStep);
        return steps(e,lesson.studyStage);
    }
    static List<StudyPlanLogic.Step> steps(LocalStore.Entry e,StudyPlanLogic.Stage stage) {
        List<String> refs=new ArrayList<>();
        for(Bible.Verse v:e.lines())refs.add(v.ref);
        return StudyPlanLogic.build(refs,stage,isSermon(e));
    }
    static void reset(LocalStore store,LessonSessionState lesson,ReviewSession reviewSession,LocalStore.Entry e) {
        lesson.resetExercise();
        boolean mastered=false;
        if(e!=null) {
            List<StudyPlanLogic.Step> steps=steps(e,lesson,reviewSession);
            if(!steps.isEmpty()) {
                lesson.stepCursor=Math.max(0,Math.min(lesson.stepCursor,steps.size()-1));
                StudyPlanLogic.Step s=steps.get(lesson.stepCursor);
                mastered=store.isStepMastered(e.id,lesson.studyStage.name(),s.start,s.end);
            }
        }
        int done=e!=null && lesson.studyStage==StudyPlanLogic.Stage.VERSE?
            store.exerciseDone(e.id,lesson.stepCursor):0;
        lesson.trainingMode=done>=4?TrainingMode.TYPE:
            done==3?TrainingMode.TYPE:done==2?TrainingMode.ORDER:
            done==1?TrainingMode.HIDE:TrainingMode.READ;
        if(done==1)lesson.mask=25;
        // A final examination becomes assisted only if a hint is viewed DURING that exam.
        if(reviewSession.active() || (lesson.studyStage==StudyPlanLogic.Stage.VERSE&&done>=4))lesson.testAssisted=false;
    }
    static void prepareGroup(LocalStore store,LessonSessionState lesson,ReviewSession reviewSession,GroupLearningLogic.Candidate group){
        if(!lesson.groupMode) lesson.returnVerseCursor=lesson.studyStage==StudyPlanLogic.Stage.VERSE?lesson.stepCursor:
            store.firstUnmastered(lesson.selectedEntry,store.entry(lesson.selectedEntry).lines().size());
        reviewSession.clear();lesson.groupMode=true;
        lesson.studyStage=StudyPlanLogic.Stage.valueOf(group.stage);
        lesson.forcedStep=new StudyPlanLogic.Step(group.start,group.end,"Связка "+(group.start+1)+"–"+group.end);
        lesson.stepCursor=0;lesson.mask=0;lesson.lessonWriting.draft="";lesson.checked=false;lesson.passed=false;
        lesson.lessonVariant=0;lesson.lessonChunk=0;lesson.savedAssemblyFingerprint="";
        lesson.testAssisted=false;lesson.lessonWriting.hadFeedback=false;lesson.lessonPracticeAgain=false;
        lesson.lessonWriting.variant=0;lesson.groupBuildDone=false;lesson.groupIntroDone=false;lesson.lessonWriting.practiceWeakFragment=false;lesson.lessonWriting.weakFragmentText="";lesson.lessonAssembly.reset();lesson.lessonSeed=new Random().nextLong();
        lesson.activeStep=lesson.forcedStep;
        lesson.answerReview=null;
        lesson.trainingMode=TrainingMode.ORDER;
    }
}
