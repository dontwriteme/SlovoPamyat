package org.biblememory.app;

import android.view.*;
import android.widget.*;
import android.os.Looper;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class LessonHotbarIntegrationTest {
    ActivityController<MainActivity> lifecycle;MainActivity activity;LocalStore store;long id;
    Object get(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    Object call(String name,Class<?>[] types,Object... args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
    void invoke(String name)throws Exception{call(name,new Class<?>[0]);}
    LessonHotbarLogic.Model model(LessonCurriculumLogic.Phase phase)throws Exception{return (LessonHotbarLogic.Model)call("lessonHotbarModel",new Class<?>[]{LessonCurriculumLogic.Phase.class,LocalStore.Entry.class},phase,store.entry(id));}
    TextView text(View root,String value){
        if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return (TextView)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView found=text(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}
        return null;
    }
    void waitLoader()throws Exception{for(Thread t:Thread.getAllStackTraces().keySet())if(t.getName().equals("BibleLoader"))t.join(10000);}
    @Before public void setup()throws Exception{
        lifecycle=Robolectric.buildActivity(MainActivity.class).create().start().resume();activity=lifecycle.get();
        ((Bible)get("bible")).load(activity);waitLoader();Shadows.shadowOf(Looper.getMainLooper()).idle();
        store=(LocalStore)get("store");long collection=store.createUnifiedCollection("Hotbar fixture");
        id=store.add(store.unifiedCollection(collection).groupId,"Saved","Свой текст",Arrays.asList(new Bible.Verse("1:1","Слово истины живо"),new Bible.Verse("1:2","Слово дает свет")));
        call("openTraining",new Class<?>[]{long.class},id);
    }
    @After public void close()throws Exception{waitLoader();if(lifecycle!=null)lifecycle.pause().stop().destroy();}
    @Test public void everyHideLevelLeavesIntroCurrentAndPreservesPassageView()throws Exception{
        View root=(View)get("root"),passage=(View)get("activePassage"),strip=root.findViewWithTag("lesson-progress-strip");
        TextView hide=text(root,"Скрыть 25% слов");assertNotNull(hide);
        for(int expected:new int[]{0,25,50,75,100,0}){
            if(expected!=0||(int)get("mask")==100)hide.performClick();
            assertEquals(expected,get("mask"));LessonHotbarLogic.Model m=model(LessonCurriculumLogic.Phase.INTRO);
            assertEquals(1,m.totals[0]);assertEquals(0,m.completedCount);assertEquals(1,m.currentNumber);
            assertEquals(LessonHotbarLogic.CURRENT,m.visual(0,0));assertEquals(0,store.exerciseDone(id,0));
            assertSame(passage,get("activePassage"));assertSame(strip,((View)get("root")).findViewWithTag("lesson-progress-strip"));
        }
    }
    @Test public void continueAndRestoredCheckpointCompleteIntroExactlyOnce()throws Exception{
        TextView next=text((View)get("root"),"Продолжить");assertNotNull(next);next.performClick();next.performClick();
        assertEquals(1,store.exerciseDone(id,0));assertEquals(1,model(LessonCurriculumLogic.Phase.BUILD).completedCount);
        invoke("saveTrainingSession");assertTrue((Boolean)call("restoreTrainingSession",new Class<?>[]{long.class},id));
        assertEquals(1,model(LessonCurriculumLogic.Phase.BUILD).completedCount);assertEquals(2,model(LessonCurriculumLogic.Phase.BUILD).currentNumber);
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM lesson_progress WHERE entry_id=? AND position=0",new String[]{String.valueOf(id)})){c.moveToFirst();assertEquals(1,c.getInt(0));}
    }
    @Test public void groupUsesItsExistingIntroFlagAndReviewHasOnlyWriting()throws Exception{
        set("groupMode",true);set("groupIntroDone",false);assertEquals(0,model(LessonCurriculumLogic.Phase.INTRO).completedCount);
        set("groupIntroDone",true);assertEquals(1,model(LessonCurriculumLogic.Phase.BUILD).completedCount);
        set("groupMode",false);set("reviewMode",true);LessonHotbarLogic.Model review=model(LessonCurriculumLogic.Phase.WRITE);
        assertEquals(0,review.totals[0]);assertEquals(0,review.totals[1]);assertEquals(review.totals[2],review.total);
    }
    @Test public void stripFitsNarrowScreenAndRetainsTalkBackCountsForManyTasks()throws Exception{
        Class<?> type=Class.forName("org.biblememory.app.MainActivity$LessonSegmentedBar");
        Constructor<?> constructor=type.getDeclaredConstructor(MainActivity.class,LessonHotbarLogic.Model.class);constructor.setAccessible(true);
        View strip=(View)constructor.newInstance(activity,LessonHotbarLogic.calculate(0,1,1000,3,0,0,0,false));
        strip.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(7,View.MeasureSpec.EXACTLY));strip.layout(0,0,240,7);
        assertEquals(240,strip.getWidth());assertEquals(7,strip.getHeight());assertTrue(strip.getContentDescription().toString().contains("1004"));
        ViewGroup progress=((View)get("root")).findViewWithTag("lesson-progress");
        for(int i=0;i<progress.getChildCount();i++)assertFalse(progress.getChildAt(i) instanceof HorizontalScrollView);
    }
    @Test @Config(qualifiers="w320dp-h560dp")
    public void shortAnswerReviewRetriesSameExerciseAndOnlyCorrectAnswerCompletes()throws Exception{
        openWritingFixture();
        for(int attempt=0;attempt<2;attempt++){
            submit("а");
            View root=(View)get("root");
            assertNotNull(root.findViewWithTag("lesson-progress-strip"));
            assertNotNull(text(root,"Твой ответ"));
            assertNotNull(text(root,"а"));
            assertNotNull(text(root,"Слово истины живо"));
            assertNull(text(root,"Не хватает большей части стиха"));
            assertEquals(2,((ViewGroup)get("content")).getChildCount());
            assertFalse(visibleText(root).contains("⟨пропуск:"));
            assertNull(edit(root));
            assertEquals(3,store.exerciseDone(id,0));
            TextView retry=text(root,"Написать ещё раз");
            root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(560,View.MeasureSpec.EXACTLY));
            root.layout(0,0,320,560);
            assertTrue(retry.isShown());
            assertNull(text(root,"Слово истины живо").getEllipsize());
            retry.performClick();
            assertEquals("",edit((View)get("root")).getText().toString());
            assertNull(text((View)get("root"),"Правильный ответ"));
            assertEquals(0,get("stepCursor"));
        }
        submit("Слово истины живо");
        assertEquals(5,store.exerciseDone(id,0));
        assertTrue((Boolean)get("passed"));
    }

    @Test public void emptyReviewDoesNotAdvanceAndCloseStillExitsTheLesson()throws Exception{
        openWritingFixture();
        submit("");
        assertNotNull(text((View)get("root"),"Ответ не введён"));
        assertEquals(3,store.exerciseDone(id,0));
        text((View)get("root"),"Написать ещё раз").performClick();
        submit("Мир");
        activity.onBackPressed();
        assertEquals("HOME",get("screen").toString());
        assertNull(get("answerReview"));
        assertEquals(3,store.exerciseDone(id,0));
    }

    @Test public void markedReviewUsesOnlyTwoCardsAndRetryClearsItsDraft()throws Exception{
        openWritingFixture();
        submit("Слово истены");
        View root=(View)get("root");
        assertEquals(2,((ViewGroup)get("content")).getChildCount());
        assertNotNull(root.findViewWithTag("lesson-progress-strip"));
        TextView displayed=text(root,"Слово истены живо");
        assertNotNull(displayed);
        assertTrue(displayed.getText() instanceof android.text.Spanned);
        android.text.Spanned styled=(android.text.Spanned)displayed.getText();
        assertEquals(1,styled.getSpans(0,styled.length(),android.text.style.ForegroundColorSpan.class).length);
        assertEquals(1,styled.getSpans(0,styled.length(),android.text.style.ReplacementSpan.class).length);
        TextView canonical=text(root,"Слово истины живо");
        assertNotNull(canonical);
        assertEquals(0,((android.text.Spanned)canonical.getText()).getSpans(0,canonical.getText().length(),
            android.text.style.CharacterStyle.class).length);
        assertNull(text(root,"Подробнее"));
        assertNull(text(root,"Потренировать ошибочный фрагмент"));
        assertEquals(3,store.exerciseDone(id,0));
        text(root,"Написать ещё раз").performClick();
        assertEquals("",edit((View)get("root")).getText().toString());
        assertNull(text((View)get("root"),"Правильный ответ"));
        assertEquals(3,store.exerciseDone(id,0));
    }

    @Test @Config(qualifiers="w320dp-h560dp")
    public void longAnswerCardsScrollAboveTheFixedRetryButton()throws Exception{
        openWritingFixture();
        StringBuilder passage=new StringBuilder();
        for(int i=0;i<120;i++)passage.append("Слово истины живо. ");
        String expected=passage.toString();
        String actual=expected.replaceFirst("истины","истены");
        call("openAnswerReview",new Class<?>[]{String.class,String.class,boolean.class},expected,actual,false);
        View root=(View)get("root");
        root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(560,View.MeasureSpec.EXACTLY));
        root.layout(0,0,320,560);
        ScrollView scroll=(ScrollView)get("currentScroller");
        assertSame(get("content"),scroll.getChildAt(0));
        assertEquals(actual,text(root,actual).getText().toString());
        assertEquals(expected,text(root,expected).getText().toString());
        assertNull(text(root,expected).getEllipsize());
        assertTrue(text(root,expected).getWidth()<=root.getWidth());
        assertTrue(text(root,"Написать ещё раз").isShown());
        assertEquals(2,((ViewGroup)get("content")).getChildCount());
    }

    private void openWritingFixture()throws Exception{
        lifecycle.visible();
        store.finishExercise(id,0,3);
        invoke("render");
    }

    private void submit(String value)throws Exception{
        View root=(View)get("root");
        EditText answer=edit(root);
        assertNotNull(answer);
        answer.setText(value);
        text(root,"Проверить").performClick();
    }

    private String visibleText(View root){
        if(root.getVisibility()!=View.VISIBLE)return "";
        if(root instanceof TextView)return ((TextView)root).getText().toString();
        StringBuilder result=new StringBuilder();
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++)result.append(visibleText(group.getChildAt(i)));
        }
        return result.toString();
    }

    private EditText edit(View root){
        if(root instanceof EditText)return (EditText)root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                EditText found=edit(group.getChildAt(i));
                if(found!=null)return found;
            }
        }
        return null;
    }

}
