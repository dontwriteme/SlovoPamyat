package org.biblememory.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.SQLiteMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RecallAtomicityTest {
    private static final String VERSE="В начале сотворил Бог небо и землю";
    private static final String WRONG="Абсолютно другой ответ";
    private static final String SECOND="Земля была безвидна и пуста";
    private static final String WRONG_SECOND="Суша стала покрыта шумной водой";
    private static final String THIRD="Да будет свет и стал свет";
    private static final long FIRST_ATTEMPT=10_000L;

    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private LocalStore store;
    private long group;

    @Before public void setup() throws Exception {
        controller=Robolectric.buildActivity(MainActivity.class).setup();
        activity=controller.get();
        ActivityTestWorkers.awaitStartup(activity);
        store=(LocalStore)get("store");
        group=store.unifiedCollection(store.createUnifiedCollection("Recall fixture")).groupId;
    }

    @After public void close() throws Exception {
        if(controller!=null)ActivityTestWorkers.destroy(controller);
    }

    private Field field(String name) throws Exception {
        Field field=MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private Object get(String name) throws Exception {
        return field(name).get(activity);
    }

    private void set(String name,Object value) throws Exception {
        field(name).set(activity,value);
    }

    private void render() throws Exception {
        Method method=MainActivity.class.getDeclaredMethod("render");
        method.setAccessible(true);
        method.invoke(activity);
    }

    private void call(String name,Class<?> parameter,Object value) throws Exception {
        Method method=MainActivity.class.getDeclaredMethod(name,parameter);
        method.setAccessible(true);
        method.invoke(activity,value);
    }

    private long add(String... verses){
        List<Bible.Verse> lines=new ArrayList<>();
        for(int i=0;i<verses.length;i++)lines.add(new Bible.Verse("1:"+(i+1),verses[i]));
        return store.add(group,"Проверка","Бытие 1:1",lines);
    }

    private void openReview(long id,String stage,int end) throws Exception {
        call("openReview",LocalStore.DueItem.class,new LocalStore.DueItem(id,stage,0,end,0));
    }

    private <T extends View> T find(View root,Class<T> type,String label){
        if(type.isInstance(root)&&(label==null||label.contentEquals(((TextView)root).getText())))
            return type.cast(root);
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                T child=find(group.getChildAt(i),type,label);
                if(child!=null)return child;
            }
        }
        return null;
    }

    private TextView submit(String answer) throws Exception {
        View root=(View)get("root");
        EditText input=find(root,EditText.class,null);
        assertNotNull("Full writing field",input);
        input.setText(answer);
        TextView check=find(root,TextView.class,"Проверить");
        assertNotNull("Check button",check);
        return check;
    }

    private List<List<String>> rows(String table){
        List<List<String>> rows=new ArrayList<>();
        try(Cursor cursor=store.getReadableDatabase().rawQuery("SELECT * FROM "+table+" ORDER BY rowid",null)){
            while(cursor.moveToNext()){
                List<String> row=new ArrayList<>();
                for(int i=0;i<cursor.getColumnCount();i++)row.add(cursor.getString(i));
                rows.add(row);
            }
        }
        return rows;
    }

    private List<List<List<String>>> snapshot(){
        List<List<List<String>>> state=new ArrayList<>();
        for(String table:Arrays.asList("entries","recall_activity","review_attempts","review_schedule",
                "transition_issues","lesson_progress","verse_progress","stage_progress"))
            state.add(rows(table));
        return state;
    }

    private void refuse(String table,String action){
        refuse(table,action,"1");
    }

    private void refuse(String table,String action,String when){
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_attempt BEFORE "+action+" ON "+table+
            " WHEN "+when+" BEGIN SELECT RAISE(ABORT,'injected recall failure'); END");
    }

    private void assertUnchangedOnFailure(TextView check,String answer,List<List<List<String>>> before)throws Exception{
        check.performClick();
        ActivityTestWorkers.awaitWriting(activity);
        assertEquals("Storage failure must roll back every table",before,snapshot());
        assertEquals(answer,find(check.getRootView(),EditText.class,null).getText().toString());
    }

    private void allow(){
        store.getWritableDatabase().execSQL("DROP TRIGGER reject_attempt");
    }

    @Test public void failedReviewRollsBackAndTheSameButtonCanRetryExactlyOnce() throws Exception {
        long id=add(VERSE);
        store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,FIRST_ATTEMPT);
        openReview(id,"VERSE",1);
        TextView check=submit(WRONG);
        List<List<List<String>>> before=snapshot();
        SharedPreferences session=activity.getSharedPreferences("study_session",Context.MODE_PRIVATE);
        Map<String,?> draftSession=session.getAll();
        assertEquals(WRONG,session.getString("review_draft",""));
        refuse("review_attempts","INSERT");
        check.performClick();
        assertEquals(before,snapshot());
        assertEquals(draftSession,session.getAll());
        assertEquals(WRONG,get("draft"));
        assertNull(get("answerReview"));
        assertEquals(false,get("lessonWriteHadFeedback"));
        assertEquals(-1,get("lastAccuracy"));
        assertEquals(-1,get("weakVerse"));
        assertEquals(-1,get("weakTransitionFrom"));
        allow();

        check.performClick();
        List<List<List<String>>> committed=snapshot();
        assertNotEquals(before,committed);
        assertNotNull(get("answerReview"));
        assertEquals(true,get("lessonWriteHadFeedback"));
        assertEquals(1,store.recentFailures(id,"VERSE",0,1));
        assertEquals(2,rows("review_attempts").size());
        assertEquals("",session.getString("review_draft",""));
        check.performClick();
        assertEquals(committed,snapshot());
    }

    @Test public void failedStandaloneVerseDoesNotAdvanceTheLessonOrSchedule() throws Exception {
        long id=add(VERSE);
        store.finishExercise(id,0,4);
        call("openTraining",long.class,id);
        set("lessonWriteVariant",Integer.MAX_VALUE);
        render();
        TextView check=submit(WRONG);
        List<List<List<String>>> before=snapshot();
        refuse("recall_activity","INSERT");
        assertUnchangedOnFailure(check,WRONG,before);
        assertNull(get("answerReview"));
        assertEquals(-1,get("lastAccuracy"));
        allow();

        check.performClick();
        assertNotNull(get("answerReview"));
        assertEquals(1,store.recentFailures(id,"VERSE",0,1));
        assertEquals(4,store.exerciseDone(id,0));
        assertFalse(store.isStepMastered(id,"VERSE",0,1));
        assertTrue(rows("review_attempts").isEmpty());
        assertTrue(rows("review_schedule").isEmpty());
    }

    @Test public void groupedPassageRollsBackAtEveryWriteIncludingWeakVerseAndTransition() throws Exception {
        long id=add(VERSE,SECOND,THIRD);
        store.markMastered(id,1,2,100,"VERSE");
        call("openTraining",long.class,id);
        call("openGroup",GroupLearningLogic.Candidate.class,
            GroupLearningLogic.candidate("CHAIN",0,3,"Atomicity test"));
        // Open the full writing variant of a group whose introduction and assembly are complete.
        set("groupIntroDone",true);
        set("groupBuildDone",true);
        set("lessonWriteVariant",Integer.MAX_VALUE);
        render();
        String answer=VERSE+" "+WRONG_SECOND+" "+THIRD;
        TextView check=submit(answer);
        List<List<List<String>>> before=snapshot();
        String[][] failures={
            {"recall_activity","INSERT","NEW.stage='CHAIN'"},
            {"review_schedule","INSERT","NEW.stage='VERSE'"},
            {"transition_issues","INSERT","1"},
            {"transition_issues","UPDATE","1"},
            {"review_attempts","INSERT","1"},
            {"review_schedule","INSERT","NEW.stage='CHAIN'"}
        };
        for(String[] failure:failures){
            refuse(failure[0],failure[1],failure[2]);
            assertUnchangedOnFailure(check,answer,before);
            assertNull(get("answerReview"));
            assertEquals(false,get("lessonWriteHadFeedback"));
            allow();
        }
        check.performClick();
        assertNotNull(get("answerReview"));
        assertEquals(1,get("weakVerse"));
        assertEquals(0,get("weakTransitionFrom"));
        assertEquals(1,rows("transition_issues").size());
        assertEquals("1",rows("transition_issues").get(0).get(3));
        assertEquals(1,rows("review_attempts").size());
        List<String> attempt=rows("review_attempts").get(0);
        assertEquals("CHAIN",attempt.get(2));
        assertEquals("0",attempt.get(7));
        assertEquals(1,rows("recall_activity").size());
        assertEquals(2,rows("review_schedule").size());
        assertEquals(before.get(5),rows("lesson_progress"));
        assertEquals(before.get(6),rows("verse_progress"));
        assertEquals(before.get(7),rows("stage_progress"));
    }

    @Test public void longPassageWithNonzeroStartKeepsAbsoluteWeakPositions() throws Exception {
        String[] verses=new String[32];
        for(int i=0;i<verses.length;i++)verses[i]="Стих "+(i+1)+" повествует о начале творения мира";
        long id=add(verses);
        int start=2, weak=verses.length-1;
        store.markMastered(id,weak,weak+1,100,"VERSE");
        call("openReview",LocalStore.DueItem.class,
            new LocalStore.DueItem(id,"SECTION",start,verses.length,0));
        String answer=String.join(" ",Arrays.copyOfRange(verses,start,weak))+" "+WRONG_SECOND;
        TextView check=submit(answer);
        List<List<List<String>>> before=snapshot();
        refuse("review_schedule","INSERT","NEW.stage='SECTION'");
        assertUnchangedOnFailure(check,answer,before);
        allow();

        check.performClick();
        ActivityTestWorkers.awaitWriting(activity);
        assertNotNull(get("answerReview"));
        assertEquals(weak,get("weakVerse"));
        assertEquals(weak-1,get("weakTransitionFrom"));
        List<String> attempt=rows("review_attempts").get(0);
        assertEquals("SECTION",attempt.get(2));
        assertEquals(Integer.toString(start),attempt.get(3));
        assertEquals(Integer.toString(verses.length),attempt.get(4));
        long timestamp=Long.parseLong(attempt.get(5));
        assertEquals(2,rows("review_schedule").size());
        for(List<String> schedule:rows("review_schedule")){
            assertEquals("0",schedule.get(4));
            assertEquals(timestamp+86_400_000L,Long.parseLong(schedule.get(5)));
        }
    }

    @Test public void verseRunFailureOnlyUpdatesTheWeakVerseAndCanRetry() throws Exception {
        long id=add(VERSE,SECOND,THIRD);
        store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,FIRST_ATTEMPT);
        store.commitSuccessfulRecall(id,"VERSE",2,3,false,true,FIRST_ATTEMPT);
        store.markMastered(id,1,2,100,"VERSE");
        openReview(id,"VERSE_RUN",3);
        String answer=VERSE+" "+WRONG_SECOND+" "+THIRD;
        TextView check=submit(answer);
        List<List<List<String>>> before=snapshot();
        // The weak reschedule and transition run before recording the individual attempt.
        refuse("recall_activity","INSERT","NEW.first_line=1");
        assertUnchangedOnFailure(check,answer,before);
        allow();

        check.performClick();
        assertNotNull(get("answerReview"));
        assertEquals(1,get("weakVerse"));
        assertEquals(3,rows("review_attempts").size());
        List<String> attempt=rows("review_attempts").get(2);
        assertEquals("VERSE",attempt.get(2));
        assertEquals("1",attempt.get(3));
        assertEquals("2",attempt.get(4));
        assertEquals("0",attempt.get(6));
        assertEquals(before.get(2),rows("review_attempts").subList(0,2));
        assertEquals(before.get(1),rows("recall_activity").subList(0,2));
        assertEquals(before.get(3),rows("review_schedule").subList(0,2));
        assertEquals(1,store.recentFailures(id,"VERSE",1,2));
        assertEquals(1,rows("transition_issues").size());
        assertEquals(before.get(6),rows("verse_progress"));
    }

    @Test public void correctingWithFeedbackRemainsAssistedEvenAfterStorageFailure() throws Exception {
        long id=add(VERSE);
        store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,FIRST_ATTEMPT);
        openReview(id,"VERSE",1);
        // Model a correction while feedback is still attributed to the current answer.
        set("lessonWriteHadFeedback",true);
        TextView check=submit(VERSE);
        List<List<List<String>>> before=snapshot();
        refuse("recall_activity","INSERT");
        assertUnchangedOnFailure(check,VERSE,before);
        assertEquals(true,get("lessonWriteHadFeedback"));
        assertEquals(false,get("passed"));
        allow();

        check.performClick();
        assertEquals(false,get("passed"));
        assertEquals(false,get("lessonWriteHadFeedback"));
        assertEquals("",get("draft"));
        assertEquals(before.get(2),rows("review_attempts"));
        assertEquals(before.get(3),rows("review_schedule"));
        List<String> recall=rows("recall_activity").get(0);
        assertEquals(before.get(1).get(0).subList(5,9),recall.subList(5,9));
        assertNotEquals(before.get(1).get(0).get(4),recall.get(4));

        TextView independent=submit(VERSE);
        independent.performClick();
        assertEquals(true,get("passed"));
        assertEquals(2,rows("review_attempts").size());
        List<List<List<String>>> saved=snapshot();
        independent.performClick();
        assertEquals(saved,snapshot());
    }
}
