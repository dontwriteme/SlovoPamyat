package org.biblememory.app;

import android.content.SharedPreferences;
import java.util.Random;

/** State of one lesson. The Activity still owns its route and database lifetime. */
final class LessonSessionState {
    enum TrainingMode {READ, HIDE, ORDER, TYPE}

    int mask=0,stepCursor=0;
    StudyPlanLogic.Stage studyStage=StudyPlanLogic.Stage.VERSE;
    long selectedEntry=0;
    boolean checked=false,passed=false;
    TrainingMode trainingMode=TrainingMode.READ;
    boolean testAssisted=false;
    final LessonWritingState lessonWriting=new LessonWritingState();
    int lastAccuracy=-1;
    LocalStore.Entry activeEntry;
    StudyPlanLogic.Step activeStep;
    boolean groupMode=false;
    int returnVerseCursor=0;
    StudyPlanLogic.Step forcedStep;
    int blurSeed=new Random().nextInt();
    int lessonVariant=0,lessonChunk=0;
    long lessonSeed=new Random().nextLong();
    final LessonAssemblyState lessonAssembly=new LessonAssemblyState();
    boolean lessonPracticeAgain=false;
    String savedAssemblyFingerprint="";
    int weakVerse=-1,weakTransitionFrom=-1;
    boolean groupBuildDone=false;
    boolean groupIntroDone=false;
    long lessonSourceEntry=-1,lessonSourceGroup=-1;
    String lessonSessionMaterial="";
    String lessonSourceMaterial="";
    String rotationExerciseKey="",rotationMask="";
    long rotationExerciseSeed=0L;
    AnswerReview answerReview;

    boolean hasActiveMaterial(){
        return activeEntry!=null&&activeEntry.id==selectedEntry&&activeStep!=null&&lessonSourceEntry>0;
    }

    void resetExercise(){
        savedAssemblyFingerprint="";
        lessonVariant=0;lessonChunk=0;lessonAssembly.reset();lessonWriting.variant=0;groupBuildDone=false;groupIntroDone=false;
        lessonWriting.practiceWeakFragment=false;lessonWriting.weakFragmentText="";
        lessonSeed=new Random().nextLong();lessonPracticeAgain=false;
        rotationExerciseKey="";rotationMask="";rotationExerciseSeed=0L;
        lessonWriting.hadFeedback=false;
        mask=0;checked=false;passed=false;lessonWriting.draft="";lastAccuracy=-1;
        blurSeed=new Random().nextInt();testAssisted=false;
    }

    /** Choose a balanced random mask once per exercise, never once per screen redraw. */
    int[] missingPositions(SharedPreferences prefs,String[] tokens,LessonCurriculumLogic.Variant kind,
                           Runnable save){
        if(kind!=LessonCurriculumLogic.Variant.GAPS)
            return LessonCurriculumLogic.missingPositions(tokens.length,kind,(int)lessonSeed);
        // Use the original fragment text as a stable identity. If that text changes,
        // its history changes too; do not combine positions from different verses.
        String historyKey=GapRotationLogic.identity(tokens);
        String exerciseKey=historyKey+":"+(groupMode?"group": "verse")+
            ":"+(trainingMode==TrainingMode.TYPE?"write":"build");
        if(exerciseKey.equals(rotationExerciseKey)&&rotationExerciseSeed==lessonSeed){
            int[] cached=GapRotationLogic.decodeMask(rotationMask,tokens.length);
            if(cached!=null)return cached;
        }
        int[] counts=GapRotationLogic.decodeCounts(prefs.getString(historyKey,""),tokens.length);
        int[] positions=GapRotationLogic.balancedWordPositions(tokens,counts,lessonSeed);
        prefs.edit().putString(historyKey,GapRotationLogic.encodeCounts(counts)).apply();
        rotationExerciseKey=exerciseKey;rotationExerciseSeed=lessonSeed;
        rotationMask=GapRotationLogic.encodeMask(positions);
        save.run();
        return positions;
    }
    static final class AnswerReview {
        final String expected,actual;
        final AnswerReviewLogic.Result result;
        AnswerReview(String expected,String actual,AnswerReviewLogic.Result result){
            this.expected=expected;
            this.actual=actual;
            this.result=result;
        }
    }

}
