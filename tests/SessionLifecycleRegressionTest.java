package org.biblememory.app;

import android.content.Context;
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
        for(Thread thread:Thread.getAllStackTraces().keySet())
            if(thread.getName().equals("BibleLoader"))thread.join(30000);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        store=(LocalStore)get("store");
    }
    @After public void close(){if(controller!=null)controller.pause().stop().destroy();}

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
        controller.pause().stop().destroy();
        controller=Robolectric.buildActivity(MainActivity.class).create().start().resume();
        activity=controller.get();
        for(Thread thread:Thread.getAllStackTraces().keySet())
            if(thread.getName().equals("BibleLoader"))thread.join(30000);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
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
