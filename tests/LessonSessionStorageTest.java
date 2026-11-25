package org.biblememory.app;

import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class LessonSessionStorageTest {
    private final SharedPreferences preferences=RuntimeEnvironment.getApplication()
        .getSharedPreferences("study_session",0);

    @Test public void legacyEntryCheckpointRestoresReadingAndAssemblyWithoutMarkingCompletion(){
        String key="entry_17_";
        preferences.edit().putString(key+"mode","HIDE").putString(key+"stage","VERSE")
            .putInt(key+"mask",75).putInt(key+"step",2)
            .putString(key+"draft","Начало ответа").putLong(key+"lesson_seed",41)
            .putInt(key+"lesson_chunk",1).putInt(key+"lesson_picked",1)
            .putString(key+"lesson_tiles","2").putString(key+"assembly_fingerprint","точный фрагмент").commit();
        LessonSessionState lesson=new LessonSessionState();
        lesson.checked=lesson.passed=true;
        assertTrue(LessonSessionPreferences.restore(preferences,key,false,lesson));
        assertEquals(LessonSessionState.TrainingMode.HIDE,lesson.trainingMode);
        assertEquals(75,lesson.mask);
        assertEquals(2,lesson.stepCursor);
        assertEquals("Начало ответа",lesson.lessonWriting.draft);
        assertEquals(41,lesson.lessonSeed);
        assertEquals(1,lesson.lessonChunk);
        assertEquals(1,lesson.lessonAssembly.picked());
        assertTrue(lesson.lessonAssembly.used(2));
        assertEquals("точный фрагмент",lesson.savedAssemblyFingerprint);
        assertFalse(lesson.checked);
        assertFalse(lesson.passed);
    }

    @Test public void groupCheckpointRetainsLegacyKeysAndLeavesOtherRoutesUntouched(){
        preferences.edit().putString("entry_17_draft","Отдельный черновик").commit();
        LessonSessionState saved=new LessonSessionState();
        saved.selectedEntry=17;
        saved.groupMode=true;
        saved.groupIntroDone=true;
        saved.groupBuildDone=true;
        saved.returnVerseCursor=3;
        saved.studyStage=StudyPlanLogic.Stage.CHAIN;
        saved.trainingMode=LessonSessionState.TrainingMode.TYPE;
        saved.activeStep=new StudyPlanLogic.Step(1,3,"Связка");
        saved.lessonWriting.draft="Черновик связки";
        saved.lessonSessionMaterial="Отпечаток";
        LessonSessionPreferences.save(preferences,saved,new ReviewSession());

        assertEquals("Отдельный черновик",preferences.getString("entry_17_draft",""));
        assertTrue(preferences.getBoolean("linked_17_pending",false));
        assertEquals(3,preferences.getInt("linked_17_return_cursor",-1));
        assertEquals(1,preferences.getInt("linked_17_begin",-1));
        assertEquals(3,preferences.getInt("linked_17_end",-1));
        assertEquals("Отпечаток",preferences.getString("linked_17_lesson_material",""));
        LessonSessionState restored=new LessonSessionState();
        assertTrue(LessonSessionPreferences.restore(preferences,"linked_17_",true,restored));
        assertEquals(StudyPlanLogic.Stage.CHAIN,restored.studyStage);
        assertTrue(restored.groupIntroDone);
        assertTrue(restored.groupBuildDone);
        assertEquals("Черновик связки",restored.lessonWriting.draft);
    }
}
