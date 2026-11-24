package org.biblememory.app;

import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.reflect.*;
import java.util.Collections;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class WritingWorkerTest {
    private static final String ANSWER="Воспроизведи текст по памяти ".repeat(55);
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private LocalStore store;
    private long entry;

    @Before public void prepare()throws Exception{
        controller=Robolectric.buildActivity(MainActivity.class).setup();
        activity=controller.get();ActivityTestWorkers.awaitStartup(activity);
        store=(LocalStore)field("store");
        entry=store.add(store.newGroup("Writing"),"Writing","Свой текст",
            Collections.singletonList(new Bible.Verse("1",ANSWER)));
        store.commitSuccessfulRecall(entry,"VERSE",0,1,false,true,1);
        Method open=MainActivity.class.getDeclaredMethod("openReview",LocalStore.DueItem.class);
        open.setAccessible(true);
        open.invoke(activity,new LocalStore.DueItem(entry,"VERSE",0,1,86_400_001L));
    }

    @After public void destroy()throws Exception{ActivityTestWorkers.destroy(controller);}

    private Object field(String name)throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);
        return field.get(activity);
    }

    private View find(View view,String label){
        if(label==null&&view instanceof EditText||
            label!=null&&view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View found=find(((ViewGroup)view).getChildAt(i),label);
            if(found!=null)return found;
        }
        return null;
    }

    private EditText input()throws Exception{return (EditText)find((View)field("root"),null);}
    private View check()throws Exception{return find((View)field("root"),"Проверить");}

    private int attempts(){
        try(Cursor cursor=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM review_attempts WHERE entry_id=?",
            new String[]{Long.toString(entry)})){
            assertTrue(cursor.moveToFirst());return cursor.getInt(0);
        }
    }

    private CountDownLatch holdWorker()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        ActivityTestWorkers.executor(activity,"writingExecutor").submit(()->{
            entered.countDown();
            if(!release.await(15,TimeUnit.SECONDS))throw new AssertionError("Worker not released");
            return null;
        });
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        return release;
    }

    @Test public void repeatedTapsQueueOneAttemptAndSaveOnlyAfterComputation()throws Exception{
        CountDownLatch release=holdWorker();
        try{
            input().setText(ANSWER);
            View button=check();
            button.performClick();
            Object submitted=field("pendingWritingCheck");
            assertNotNull(submitted);
            for(int i=0;i<5;i++)button.performClick();
            assertSame(submitted,field("pendingWritingCheck"));
            assertEquals(1,attempts());
        }finally{release.countDown();}
        ActivityTestWorkers.awaitWriting(activity);
        assertEquals(2,attempts());
        assertEquals(true,field("passed"));
    }

    @Test public void editedLargePasteRetiresThePendingResult()throws Exception{
        EditText input=input();
        input.setText("Вставка ".repeat(1800));
        check().performClick();
        assertNotNull(field("pendingWritingCheck"));
        // The result is already posted. Keep the main looper paused until after the edit.
        ((Future<?>)field("writingTask")).get(10,TimeUnit.SECONDS);
        input.setText(ANSWER);
        assertNull(field("pendingWritingCheck"));
        ActivityTestWorkers.awaitWriting(activity);
        assertEquals(1,attempts());
        assertEquals(ANSWER,field("draft"));
        assertNull(field("answerReview"));
    }

    @Test public void leavingExerciseKeepsTheDraftAndDropsTheResult()throws Exception{
        CountDownLatch release=holdWorker();
        try{
            input().setText(ANSWER);
            check().performClick();
            activity.onBackPressed();
            assertNull(field("pendingWritingCheck"));
        }finally{release.countDown();}
        ActivityTestWorkers.awaitWriting(activity);
        assertEquals(1,attempts());
        assertEquals(ANSWER,activity.getSharedPreferences("study_session",0).getString("review_draft",""));
    }

    @Test public void recreationDropsTheResultAndRestoresTheUnsubmittedDraft()throws Exception{
        CountDownLatch release=holdWorker();
        MainActivity old=activity;
        try{
            input().setText(ANSWER);
            check().performClick();
            controller.recreate();
        }finally{release.countDown();}
        ActivityTestWorkers.awaitDestroyed(old);
        activity=controller.get();ActivityTestWorkers.awaitStartup(activity);
        store=(LocalStore)field("store");
        Method start=MainActivity.class.getDeclaredMethod("startReviewSession");start.setAccessible(true);
        start.invoke(activity);
        assertEquals(1,attempts());
        assertEquals(ANSWER,field("draft"));
        assertNull(field("answerReview"));
    }

    @Test public void asyncFailurePreservesDraftAndAllowsRetry()throws Exception{
        store.getWritableDatabase().execSQL("CREATE TRIGGER refuse_attempt BEFORE INSERT ON review_attempts "+
            "BEGIN SELECT RAISE(ABORT,'injected'); END");
        input().setText("Другой ответ ".repeat(150));
        check().performClick();
        ActivityTestWorkers.awaitWriting(activity);
        assertEquals(1,attempts());assertNull(field("answerReview"));assertNotNull(check());
        assertEquals("Другой ответ ".repeat(150),input().getText().toString());
        store.getWritableDatabase().execSQL("DROP TRIGGER refuse_attempt");
        check().performClick();
        ActivityTestWorkers.awaitWriting(activity);
        assertEquals(2,attempts());assertNotNull(field("answerReview"));
    }
}
