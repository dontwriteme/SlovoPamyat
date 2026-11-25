package org.biblememory.app;

import android.content.SharedPreferences;
import org.biblememory.app.LessonSessionState.TrainingMode;

final class LessonSessionPreferences {
    private LessonSessionPreferences(){}

    static void save(SharedPreferences preferences,LessonSessionState lesson,ReviewSession reviewSession) {
        if(lesson.selectedEntry<=0 || (reviewSession.active()&&lesson.checked&&lesson.passed))return;
        String key=reviewSession.active()?ReviewSessionPreferences.PREFIX:(lesson.groupMode?"linked_":"entry_")+lesson.selectedEntry+"_";
        SharedPreferences.Editor editor=preferences.edit()
            .putLong("entry",lesson.selectedEntry).putString(key+"stage",lesson.studyStage.name())
            .putInt(key+"step",lesson.stepCursor).putString(key+"mode",lesson.trainingMode.name())
            .putInt(key+"mask",lesson.mask);
        LessonExerciseState exercise=new LessonExerciseState(
            lesson.lessonWriting.resumableDraft(lesson.answerReview!=null),
            lesson.blurSeed,lesson.lessonVariant,lesson.lessonChunk,lesson.lessonAssembly.picked(),lesson.lessonSeed,lesson.savedAssemblyFingerprint,
            lesson.rotationExerciseKey,lesson.rotationExerciseSeed,lesson.rotationMask,lesson.lessonAssembly.usedTiles(),lesson.lessonWriting.variant,
            lesson.groupIntroDone,lesson.groupBuildDone,lesson.lessonWriting.practiceWeakFragment,lesson.lessonWriting.weakFragmentText);
        LessonExercisePreferences.write(editor,key,exercise);
        reviewSession.writeMetadata(editor,key,lesson.selectedEntry,lesson.groupMode)
            .putString(key+"lesson_material",reviewSession.active()?"":lesson.lessonSessionMaterial)
            .putInt(key+"begin",lesson.activeStep==null?0:lesson.activeStep.start)
            .putInt(key+"end",lesson.activeStep==null?0:lesson.activeStep.end)
            .putString(key+"group_stage",lesson.studyStage.name())
            .putInt(key+"return_cursor",lesson.returnVerseCursor).apply();
    }
    static boolean restore(SharedPreferences pref,String key,boolean linked,LessonSessionState lesson) {
        if(!pref.contains(key+"stage"))return false;
        try {
            lesson.studyStage=StudyPlanLogic.Stage.valueOf(pref.getString(key+"stage","VERSE"));
            lesson.trainingMode=TrainingMode.valueOf(pref.getString(key+"mode","READ"));
        }catch(IllegalArgumentException ex){lesson.studyStage=StudyPlanLogic.Stage.VERSE;lesson.trainingMode=TrainingMode.READ;}
        lesson.stepCursor=Math.max(0,pref.getInt(key+"step",0));
        lesson.mask=lesson.trainingMode==TrainingMode.HIDE?Math.max(0,Math.min(100,pref.getInt(key+"mask",0))):0;
        LessonExerciseState exercise=LessonExercisePreferences.read(pref,key,linked,lesson.groupIntroDone,lesson.groupBuildDone);
        lesson.blurSeed=exercise.blurSeed();lesson.lessonWriting.restore(exercise);
        lesson.lessonVariant=exercise.variant();lesson.lessonChunk=exercise.chunk();
        lesson.savedAssemblyFingerprint=exercise.assemblyFingerprint();lesson.lessonSeed=exercise.seed();
        lesson.rotationExerciseKey=exercise.rotationExerciseKey();lesson.rotationExerciseSeed=exercise.rotationExerciseSeed();
        lesson.rotationMask=exercise.rotationMask();
        lesson.lessonAssembly.restore(exercise.picked(),exercise.usedTiles());
        lesson.lessonPracticeAgain=false;
        lesson.groupIntroDone=exercise.groupIntroDone();lesson.groupBuildDone=exercise.groupBuildDone();
        lesson.checked=false;lesson.passed=false;lesson.testAssisted=false;lesson.lastAccuracy=-1;
        return true;
    }
}
