package org.biblememory.app;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class SessionLifecycleRegressionTest {
    private MainActivity activity;
    private ActivityController<MainActivity> controller;
    private LocalStore store;
    private Object get(String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);
    }
    private void set(String name,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);
    }
    private Object call(String name,Class<?>[] types,Object... args)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);
    }
    private void invoke(String name)throws Exception{call(name,new Class<?>[0]);}

    @Before public void setup()throws Exception{
        Context context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("slovo_pamyat.db");
        for(String name:new String[]{"study_session","collection_study_queue","unified_library_ui"})
            context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit();
        controller=Robolectric.buildActivity(MainActivity.class).create().start().resume();
        activity=controller.get();
        ActivityTestWorkers.awaitStartup(activity);
        store=(LocalStore)get("store");
    }
    @After public void close()throws Exception{if(controller!=null)ActivityTestWorkers.destroy(controller);}

    private LocalStore.UnifiedCollection collection(String title){
        return store.unifiedCollection(store.createUnifiedCollection(title));
    }
    private long add(LocalStore.UnifiedCollection u,String ref,String text){
        return store.add(u.groupId,ref,ref,Collections.singletonList(new Bible.Verse("1:1",text)));
    }
    private void consolidate(long id){
        store.markMastered(id,0,1,100,"VERSE");
        store.recordRecall(id,"VERSE",0,1,100,false,1000L);
        store.recordRecall(id,"VERSE",0,1,100,false,86402000L);
    }

    @Test public void staleMemoryDraftIsDiscardedWhenCollectionIsReplaced()throws Exception{
        LocalStore.UnifiedCollection u=collection("Проверка");
        long old=add(u,"Бытие 1:1","Первый стих");consolidate(old);
        call("activateUnified",new Class<?>[]{long.class},u.id);
        invoke("startActiveRun");
        assertEquals("RUN",get("screen").toString());
        String oldFingerprint=(String)get("runMaterialFingerprint");
        set("runDraft","Первый стих");invoke("saveRunSession");
        activity.onBackPressed();
        store.clearUnifiedCollection(u.id);
        long next=add(u,"Матфея 1:1","Другой стих");consolidate(next);
        invoke("startActiveRun");
        assertEquals("RUN",get("screen").toString());
        assertNotEquals(oldFingerprint,get("runMaterialFingerprint"));
        assertEquals("",get("runDraft"));assertEquals(0,get("runCursor"));
        assertEquals(0,get("runCorrect"));
        assertTrue(store.isStepMastered(old,"VERSE",0,1));
    }

    @Test public void deletingCollectionInvalidatesQueueButKeepsArchive()throws Exception{
        LocalStore.UnifiedCollection u=collection("Старая коллекция");
        long id=add(u,"Бытие 1:1","Текст для обучения");
        call("startStudyCollection",new Class<?>[]{long.class},u.groupId);
        assertEquals(u.groupId,get("collectionQueueGroup"));
        activity.onBackPressed();
        store.deleteUnifiedCollection(u.id);
        invoke("restoreCollectionQueue");
        assertEquals(-1L,get("collectionQueueGroup"));
        assertEquals(-1,get("collectionQueueCursor"));
        assertNotNull(store.entry(id));
    }

    @Test public void openingReviewCannotInheritWeakFragmentFromPreviousLesson()throws Exception{
        LocalStore.UnifiedCollection u=collection("Повторение");
        long id=add(u,"Бытие 1:1","Текст целого стиха");
        set("practiceWeakFragment",true);
        set("weakFragmentText","Посторонний текст");
        set("lessonWriteVariant",2);
        set("draft","Старый черновик");
        call("openReview",new Class<?>[]{LocalStore.DueItem.class},
            new LocalStore.DueItem(id,"VERSE",0,1,0));
        assertEquals("TRAIN",get("screen").toString());
        assertEquals(false,get("practiceWeakFragment"));
        assertEquals("",get("weakFragmentText"));assertEquals("",get("draft"));
        assertEquals(0,get("lessonWriteVariant"));
    }

    private void reopenActivity()throws Exception{
        ActivityTestWorkers.destroy(controller);
        controller=Robolectric.buildActivity(MainActivity.class).create().start().resume();
        activity=controller.get();
        ActivityTestWorkers.awaitStartup(activity);
        store=(LocalStore)get("store");
    }

    @Test public void unfinishedWritingDraftSurvivesActivityDestruction()throws Exception{
        LocalStore.UnifiedCollection u=collection("Черновик");
        long id=add(u,"Бытие 1:1","В начале сотворил Бог небо и землю");
        store.finishExercise(id,0,3);
        call("openTraining",new Class<?>[]{long.class},id);
        set("draft","В начале сотворил");
        reopenActivity();
        call("openTraining",new Class<?>[]{long.class},id);
        assertEquals("TRAIN",get("screen").toString());
        assertEquals("В начале сотворил",get("draft"));
        assertEquals(3,store.exerciseDone(id,0));
    }

    @Test public void recreationReturnsDirectlyToActiveLesson()throws Exception{
        LocalStore.UnifiedCollection u=collection("Активный урок");
        long id=add(u,"Бытие 1:1","В начале сотворил Бог небо и землю");
        store.finishExercise(id,0,3);
        call("openTraining",new Class<?>[]{long.class},id);
        set("draft","В начале сотворил");
        recreate();
        assertEquals("TRAIN",get("screen").toString());
        assertEquals(id,get("selectedEntry"));
        assertEquals("В начале сотворил",get("draft"));
        assertEquals(3,store.exerciseDone(id,0));
    }

    private void recreate()throws Exception{
        ActivityTestWorkers.recreate(controller);
        activity=controller.get();
        store=(LocalStore)get("store");
    }

    private void createFrom(Bundle state)throws Exception{
        ActivityTestWorkers.destroy(controller);
        controller=Robolectric.buildActivity(MainActivity.class).create(state).start().restoreInstanceState(state).resume();
        activity=controller.get();
        ActivityTestWorkers.awaitStartup(activity);
        store=(LocalStore)get("store");
    }

    @Test public void activeGroupKeepsItsRangeAndReturnPosition()throws Exception{
        LocalStore.UnifiedCollection u=collection("Связка");
        long id=store.add(u.groupId,"Свой текст","Связка",Arrays.asList(
            new Bible.Verse("1:1","Первый стих"),new Bible.Verse("1:2","Второй стих"),
            new Bible.Verse("1:3","Третий стих")));
        store.markMastered(id,0,1,100,"VERSE");
        store.markMastered(id,1,2,100,"VERSE");
        call("openTraining",new Class<?>[]{long.class},id);
        set("stepCursor",1);
        call("openGroup",new Class<?>[]{GroupLearningLogic.Candidate.class},
            GroupLearningLogic.candidate("CHAIN",0,2,"fixture"));
        set("groupIntroDone",true);
        set("groupBuildDone",true);
        set("draft","Первый стих");
        recreate();
        StudyPlanLogic.Step step=(StudyPlanLogic.Step)get("activeStep");
        assertEquals(0,step.start);assertEquals(2,step.end);
        assertEquals(1,get("returnVerseCursor"));
        assertEquals(true,get("groupMode"));
        assertEquals(true,get("groupIntroDone"));
        assertEquals(true,get("groupBuildDone"));
        assertEquals("Первый стих",get("draft"));
        assertFalse(store.isStepMastered(id,"CHAIN",0,2));
    }

    @Test public void activeRunReviewRestoresDraftAndRetainsFollowingDueItem()throws Exception{
        LocalStore.UnifiedCollection u=collection("Повторения");
        long id=store.add(u.groupId,"Свой текст","Первый отрывок",Arrays.asList(
            new Bible.Verse("1:1","Первый стих"),new Bible.Verse("1:2","Второй стих")));
        long following=add(u,"Отдельный","Третий стих");
        for(int pos=0;pos<2;pos++){
            store.markMastered(id,pos,pos+1,100,"VERSE");
            store.scheduleReview(id,"VERSE",pos,pos+1,100,false,1000L);
        }
        store.markMastered(following,0,1,100,"VERSE");
        store.scheduleReview(following,"VERSE",0,1,100,false,1000L);
        set("reviewCollectionGroup",u.groupId);
        invoke("startReviewSession");
        set("draft","Первый стих");
        recreate();
        assertEquals("TRAIN",get("screen").toString());
        assertEquals(true,get("reviewMode"));
        assertEquals(u.groupId,get("reviewCollectionGroup"));
        assertEquals("VERSE_RUN",((LocalStore.DueItem)get("currentReview")).stage);
        assertEquals("Первый стих",get("draft"));
        invoke("advanceReviewSession");
        assertEquals(following,get("selectedEntry"));
    }

    @Test public void completedWritingReopensCompletionWithoutRepeatingAttempt()throws Exception{
        LocalStore.UnifiedCollection u=collection("Завершение");
        long id=add(u,"Свой текст","Текст для обучения");
        store.finishExercise(id,0,3);
        call("openTraining",new Class<?>[]{long.class},id);
        set("draft","Текст для обучения");
        call("validateLessonWriting",new Class<?>[]{LocalStore.Entry.class},store.entry(id));
        ActivityTestWorkers.awaitWriting(activity);
        assertEquals(true,get("passed"));
        String completed=store.recallCheckpoint(id,"VERSE",0,1);
        assertFalse(completed.isEmpty());
        recreate();
        assertEquals("TRAIN",get("screen").toString());
        assertEquals(true,get("passed"));
        assertEquals("",get("draft"));
        assertEquals(completed,store.recallCheckpoint(id,"VERSE",0,1));
        assertEquals(5,store.exerciseDone(id,0));
    }

    @Test public void oldTaskSnapshotCannotReplayAttemptCommittedAfterSave()throws Exception{
        LocalStore.UnifiedCollection u=collection("Снимок");
        long id=add(u,"Свой текст","Текст для обучения");
        store.finishExercise(id,0,3);
        call("openTraining",new Class<?>[]{long.class},id);
        set("draft","Текст для обучения");
        Bundle state=new Bundle();
        controller.saveInstanceState(state);
        store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,1000L);
        createFrom(state);
        assertEquals("HOME",get("screen").toString());
        assertEquals(5,store.exerciseDone(id,0));
    }

    @Test public void archivedSourceCannotRestoreEvenThoughItsEntrySurvives()throws Exception{
        LocalStore.UnifiedCollection u=collection("Удаляемая коллекция");
        long id=add(u,"Свой текст","Текст для обучения");
        call("openTraining",new Class<?>[]{long.class},id);
        store.deleteUnifiedCollection(u.id);
        recreate();
        assertEquals("HOME",get("screen").toString());
        assertNotNull(store.entry(id));
    }

    @Test public void changedMaterialCannotInheritTheOldTaskDraft()throws Exception{
        LocalStore.UnifiedCollection u=collection("Изменяемый текст");
        long id=add(u,"Свой текст","Старый текст стиха");
        store.finishExercise(id,0,3);
        call("openTraining",new Class<?>[]{long.class},id);
        set("draft","Старый текст");
        store.getWritableDatabase().execSQL("UPDATE entries SET lines=? WHERE id=?",
            new Object[]{"[{\"ref\":\"1:1\",\"text\":\"Другой текст стиха\"}]",id});
        recreate();
        assertEquals("HOME",get("screen").toString());
        assertEquals("",get("draft"));
        call("openTraining",new Class<?>[]{long.class},id);
        assertEquals("",get("draft"));
    }

    @Test public void importedCopyRejectsOldTaskEvenWhenEntryAndDraftStillMatch()throws Exception{
        LocalStore.UnifiedCollection u=collection("Копия");
        long id=add(u,"Свой текст","Текст для обучения");
        store.finishExercise(id,0,3);
        call("openTraining",new Class<?>[]{long.class},id);
        set("draft","Черновик");
        Bundle state=new Bundle();
        controller.saveInstanceState(state);
        assertNotNull(state.getBundle("active_lesson"));
        Context context=RuntimeEnvironment.getApplication();
        android.net.Uri uri=android.net.Uri.fromFile(
            new java.io.File(context.getCacheDir(),"r51-same-material.svbackup"));
        SvetochBackup.exportTo(context,store,uri);
        String previous=SvetochBackup.generation(context);
        SvetochBackup.restoreFrom(context,store,uri);
        assertNotEquals(previous,SvetochBackup.generation(context));
        createFrom(state);
        assertEquals("HOME",get("screen").toString());
        assertNotNull(store.entry(id));
        assertEquals(3,store.exerciseDone(id,0));
    }

    @Test public void linkedLessonIsSavedBeforeAnyInteraction()throws Exception{
        LocalStore.UnifiedCollection u=collection("Связка");
        long id=store.add(u.groupId,"Два стиха","Свой текст",Arrays.asList(
            new Bible.Verse("1:1","Первый стих"),new Bible.Verse("1:2","Второй стих")));
        store.markMastered(id,0,1,100,"VERSE");
        store.markMastered(id,1,2,100,"VERSE");
        call("openTraining",new Class<?>[]{long.class},id);
        call("openGroup",new Class<?>[]{GroupLearningLogic.Candidate.class},
            GroupLearningLogic.candidate("CHAIN",0,2,"fixture"));
        android.content.SharedPreferences p=activity.getSharedPreferences("study_session",Context.MODE_PRIVATE);
        String key="linked_"+id+"_";
        assertTrue(p.getBoolean(key+"pending",false));
        assertEquals(0,p.getInt(key+"begin",-1));
        assertEquals(2,p.getInt(key+"end",-1));
        reopenActivity();
        call("openTraining",new Class<?>[]{long.class},id);
        assertEquals(true,get("groupMode"));
        StudyPlanLogic.Step step=(StudyPlanLogic.Step)get("forcedStep");
        assertEquals(0,step.start);assertEquals(2,step.end);
    }

    @Test public void emptyCollectionModesOpenTheSameCollectionForAddingMaterial()throws Exception{
        LocalStore.UnifiedCollection u=collection("Пустая");
        call("activateUnified",new Class<?>[]{long.class},u.id);
        for(String mode:Arrays.asList("startActiveStudy","startActiveCards","startActiveRun")){
            invoke(mode);
            assertEquals(mode,"COLLECTION_DETAIL",get("screen").toString());
            assertEquals(u.id,get("openUnifiedId"));
            assertEquals(u.groupId,get("selectedGroup"));
            activity.onBackPressed();
            activity.onBackPressed();
        }
    }

    private android.widget.EditText firstEdit(android.view.View view){
        if(view instanceof android.widget.EditText)return (android.widget.EditText)view;
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                android.widget.EditText edit=firstEdit(group.getChildAt(i));
                if(edit!=null)return edit;
            }
        }
        return null;
    }

    @Test public void invalidCollectionNameKeepsDialogOpenForCorrection()throws Exception{
        call("newUnifiedCollection",new Class<?>[]{Class.forName("org.biblememory.app.MainActivity$UnifiedCreated")},(Object)null);
        android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        android.widget.EditText name=firstEdit(dialog.getWindow().getDecorView());
        assertNotNull(name);
        name.setText("   ");
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing());assertNotNull(name.getError());
        assertTrue(store.unifiedCollections().isEmpty());
        name.setText("Новая коллекция");
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(dialog.isShowing());
        assertEquals(1,store.unifiedCollections().size());
        assertEquals("Новая коллекция",store.unifiedCollections().get(0).title);
        assertEquals("COLLECTION_DETAIL",get("screen").toString());
    }

    @Test public void successfulVerseContinuesToNextVerseWithoutCreatingRecall()throws Exception{
        LocalStore.UnifiedCollection u=collection("Переход");
        long id=store.add(u.groupId,"Два стиха","Свой текст",Arrays.asList(
            new Bible.Verse("1","Первый стих"),new Bible.Verse("2","Второй стих")));
        call("openTraining",new Class<?>[]{long.class},id);
        store.finishExercise(id,0,5);
        store.markMastered(id,0,1,100,"VERSE");
        set("checked",true);set("passed",true);
        call("advanceAfterSuccess",new Class<?>[]{LocalStore.Entry.class},store.entry(id));
        assertEquals("TRAIN",get("screen").toString());
        assertEquals(1,get("stepCursor"));
        assertEquals(StudyPlanLogic.Stage.VERSE,get("studyStage"));
        assertEquals(false,get("checked"));
        assertFalse(store.isStepMastered(id,"VERSE",1,2));
        assertEquals(0,store.exerciseDone(id,1));
        assertEquals("",store.recallCheckpoint(id,"VERSE",1,2));
    }

    @Test public void finalStandaloneBridgeReturnsHomeAfterAllGroupsAreMastered()throws Exception{
        LocalStore.UnifiedCollection u=collection("Завершение");
        long id=store.add(u.groupId,"Два стиха","Свой текст",Arrays.asList(
            new Bible.Verse("1:1","Первый стих"),new Bible.Verse("1:2","Второй стих")));
        List<String> refs=Arrays.asList("1:1","1:2");
        for(int i=0;i<2;i++)store.markMastered(id,i,i+1,100,"VERSE");
        for(StudyPlanLogic.Stage stage:new StudyPlanLogic.Stage[]{
                StudyPlanLogic.Stage.CHAIN,StudyPlanLogic.Stage.SECTION,StudyPlanLogic.Stage.CHAPTER})
            for(StudyPlanLogic.Step step:StudyPlanLogic.build(refs,stage,false))
                store.markMastered(id,step.start,step.end,100,stage.name());
        store.markMastered(id,0,2,100,"CHAIN");
        call("openTraining",new Class<?>[]{long.class},id);
        call("openGroup",new Class<?>[]{GroupLearningLogic.Candidate.class},
            GroupLearningLogic.candidate("CHAIN",0,2,"fixture"));
        set("returnVerseCursor",1);set("checked",true);set("passed",true);
        call("advanceAfterSuccess",new Class<?>[]{LocalStore.Entry.class},store.entry(id));
        assertEquals("HOME",get("screen").toString());
        assertTrue(store.isStepMastered(id,"CHAIN",0,2));
    }

    private android.view.View findCustomTextView(android.view.View view,String text){
        if(view instanceof android.widget.TextView&&
            text.contentEquals(((android.widget.TextView)view).getText()))return view;
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                android.view.View found=findCustomTextView(group.getChildAt(i),text);
                if(found!=null)return found;
            }
        }
        return null;
    }

    @Test public void legacyUnlearnableLineCanBeCorrectedWithoutLosingOtherProgress()throws Exception{
        LocalStore.UnifiedCollection u=collection("Свой текст");
        long id=store.add(u.groupId,"Текст","Свой текст",Arrays.asList(
            new Bible.Verse("1","Начало"),new Bible.Verse("2","…"),new Bible.Verse("3","Конец")));
        store.markMastered(id,0,1,100,"VERSE");
        store.markMastered(id,2,3,100,"VERSE");
        call("openTraining",new Class<?>[]{long.class},id);
        set("studyStage",StudyPlanLogic.Stage.VERSE);
        set("groupMode",false);set("reviewMode",false);set("forcedStep",null);
        set("stepCursor",1);
        call("resetTrainingStep",new Class<?>[]{LocalStore.Entry.class},store.entry(id));
        invoke("render");
        assertEquals("TRAIN",get("screen").toString());
        android.view.View repair=findCustomTextView((android.view.View)get("root"),"Изменить текст");
        assertNotNull(repair);
        repair.performClick();
        android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        android.widget.EditText words=(android.widget.EditText)findCustomTextView(
            dialog.getWindow().getDecorView(),"Начало\n…\nКонец");
        assertNotNull(words);
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing());assertNotNull(words.getError());
        assertEquals("…",store.entry(id).lines().get(1).text);
        words.setText("Начало\nСередина\nКонец");
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(dialog.isShowing());
        assertEquals("Середина",store.entry(id).lines().get(1).text);
        assertTrue(store.isStepMastered(id,"VERSE",0,1));
        assertFalse(store.isStepMastered(id,"VERSE",1,2));
        assertTrue(store.isStepMastered(id,"VERSE",2,3));
    }

    @Test public void movingUnchangedLineInSameLengthEditPreservesItsHistory()throws Exception{
        LocalStore.UnifiedCollection u=collection("Перемещение");
        long id=store.add(u.groupId,"Текст","Свой текст",Arrays.asList(
            new Bible.Verse("1","Начало"),new Bible.Verse("2","Середина"),new Bible.Verse("3","Конец")));
        store.finishExercise(id,1,5);
        store.markMastered(id,1,2,100,"VERSE");
        store.recordRecall(id,"VERSE",1,2,100,false,1000L);
        store.recordRecall(id,"VERSE",1,2,100,false,86402000L);
        store.scheduleReview(id,"VERSE",1,2,100,false,86402000L);
        assertTrue(store.isStepMastered(id,"VERSE",1,2));
        assertEquals(5,store.exerciseDone(id,1));
        long oldDue;
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery(
            "SELECT due FROM review_schedule WHERE entry_id=? AND stage='VERSE' AND first_line=1 AND last_line=2",
            new String[]{String.valueOf(id)})){
            assertTrue(c.moveToFirst());oldDue=c.getLong(0);
        }
        call("editCustomStudyEntry",new Class<?>[]{LocalStore.Entry.class},store.entry(id));
        android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        android.widget.EditText words=(android.widget.EditText)findCustomTextView(
            dialog.getWindow().getDecorView(),"Начало\nСередина\nКонец");
        assertNotNull(words);
        words.setText("Середина\nКонец\nДобавление");
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(dialog.isShowing());
        assertEquals("2",store.entry(id).lines().get(0).ref);
        assertTrue(store.isStepMastered(id,"VERSE",0,1));
        assertEquals(5,store.exerciseDone(id,0));
        assertFalse(store.isStepMastered(id,"VERSE",2,3));
        assertEquals(0,store.exerciseDone(id,2));
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery(
            "SELECT last_success,success_count FROM recall_activity WHERE entry_id=? AND stage='VERSE' AND first_line=0 AND last_line=1",
            new String[]{String.valueOf(id)})){
            assertTrue(c.moveToFirst());assertEquals(86402000L,c.getLong(0));assertEquals(2,c.getInt(1));
        }
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery(
            "SELECT due FROM review_schedule WHERE entry_id=? AND stage='VERSE' AND first_line=0 AND last_line=1",
            new String[]{String.valueOf(id)})){
            assertTrue(c.moveToFirst());assertEquals(oldDue,c.getLong(0));
        }
    }

    @Test public void wordlessFlashcardStaysAvailableAsCardButCannotCreateLesson()throws Exception{
        LocalStore.UnifiedCollection u=collection("Карточки");
        store.addCustomFlashcard(u.deckId,"Знак","…");
        set("selectedDeck",u.deckId);
        int before=store.allEntries().size();
        invoke("studyCardDeck");
        assertEquals(before,store.allEntries().size());
        assertEquals(1,store.flashcards(u.deckId).size());
        assertEquals("…",store.flashcards(u.deckId).get(0).content);
        assertTrue(((List<?>)get("cardStudyQueue")).isEmpty());
    }

    @Test public void warmMemoryRunBuildDoesNotQueryEachVerse()throws Exception{
        LocalStore.UnifiedCollection u=collection("Большая коллекция");
        for(int i=0;i<80;i++)consolidate(add(u,"Свой текст "+i,"Строка "+i));
        store.collectionProgress();
        long before=store.readQueryCount();
        call("buildRunLines",new Class<?>[]{LocalStore.UnifiedCollection.class},u);
        long queries=store.readQueryCount()-before;
        assertEquals(80,((List<?>)get("runLines")).size());
        assertTrue("Unexpected per-verse SQL queries: "+queries,queries<=3);
    }
}
