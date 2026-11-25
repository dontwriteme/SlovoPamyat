package org.biblememory.app;

import android.os.Bundle;
import java.util.Arrays;

/** Material and progress checkpoint for recreation. Navigation context is appended by the caller. */
final class LessonCheckpoint {
    private LessonCheckpoint(){}

    static Bundle capture(LocalStore store,LessonSessionState lesson,ReviewSession reviewSession){
        String stage=reviewSession.active()?reviewSession.current().item().stage:lesson.studyStage.name();
        int start=lesson.activeStep.start,end=lesson.activeStep.end;
        Bundle state=new Bundle();
        state.putLong("entry",lesson.selectedEntry);
        state.putLong("source_entry",lesson.lessonSourceEntry);
        state.putLong("source_group",lesson.lessonSourceGroup);
        state.putString("material",reviewSession.active()?material(lesson.activeEntry):lesson.lessonSessionMaterial);
        state.putString("stage",stage);
        state.putInt("begin",start);state.putInt("end",end);
        state.putInt("cursor",lesson.stepCursor);
        state.putBoolean("linked",lesson.groupMode);
        state.putBoolean("complete",lesson.checked&&lesson.passed);
        state.putBoolean("practice_again",lesson.lessonPracticeAgain);
        state.putBoolean("assisted",lesson.testAssisted);
        state.putString("recall",store.recallCheckpoint(lesson.selectedEntry,stage,start,end));
        state.putInt("exercise_done",lesson.studyStage==StudyPlanLogic.Stage.VERSE&&!reviewSession.active()&&!lesson.groupMode?
            store.exerciseDone(lesson.selectedEntry,start):-1);
        return state;
    }

    static boolean matchesMaterial(Bundle state,LocalStore.Entry entry,LocalStore.Entry source){
        if(entry==null||source==null||source.groupId!=state.getLong("source_group")||
            !material(entry).equals(state.getString("material")))return false;
        return source.id==entry.id||material(source).equals(state.getString("source_material"));
    }

    static boolean matchesRecall(LocalStore store,Bundle state,long id,String stage,int start,int end,
                                 boolean complete){
        String persisted=store.recallCheckpoint(id,stage,start,end);
        return persisted.equals(state.getString("recall"))&&(!complete||!persisted.isEmpty());
    }

    static String material(LocalStore.Entry entry){
        return SessionIntegrityLogic.fingerprint(Arrays.asList(entry.reference,entry.linesJson));
    }

}
