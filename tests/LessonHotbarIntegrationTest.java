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
    Object get(String name)throws Exception{
        if(name.startsWith("lessonBuild")){
            Object assembly=get("assemblyView");
            Field field=assembly.getClass().getDeclaredField(name);field.setAccessible(true);
            return field.get(assembly);
        }
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    Object call(String name,Class<?>[] types,Object... args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
    void invoke(String name)throws Exception{call(name,new Class<?>[0]);}
    LessonHotbarLogic.Model model(LessonCurriculumLogic.Phase phase)throws Exception{return (LessonHotbarLogic.Model)call("lessonHotbarModel",new Class<?>[]{LessonCurriculumLogic.Phase.class,LocalStore.Entry.class},phase,store.entry(id));}
    TextView text(View root,String value){
        if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return (TextView)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView found=text(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}
        return null;
    }
    void waitLoader()throws Exception{ActivityTestWorkers.awaitStartup(activity);}
    @Before public void setup()throws Exception{
        lifecycle=Robolectric.buildActivity(MainActivity.class).create().start().resume();activity=lifecycle.get();
        ((Bible)get("bible")).load(activity);waitLoader();Shadows.shadowOf(Looper.getMainLooper()).idle();
        store=(LocalStore)get("store");long collection=store.createUnifiedCollection("Hotbar fixture");
        id=store.add(store.unifiedCollection(collection).groupId,"Saved","Свой текст",Arrays.asList(new Bible.Verse("1:1","Слово истины живо"),new Bible.Verse("1:2","Слово дает свет")));
        call("openTraining",new Class<?>[]{long.class},id);
    }
    @After public void close()throws Exception{waitLoader();if(lifecycle!=null)ActivityTestWorkers.destroy(lifecycle);}
    @Test public void progressStripRemainsWithoutAnyVisibleCaptions()throws Exception{
        assertOnlyStrip(LessonCurriculumLogic.Phase.INTRO);
        store.finishExercise(id,0,1);invoke("render");
        assertOnlyStrip(LessonCurriculumLogic.Phase.BUILD);
        assertNull(text((View)get("root"),LearningHelp.BUILD_PROMPT));
        assertFalse(java.util.regex.Pattern.compile("\\d+\\s*/\\s*\\d+\\s+слов")
            .matcher(visibleText((View)get("root"))).find());
        store.finishExercise(id,0,3);invoke("render");
        assertOnlyStrip(LessonCurriculumLogic.Phase.WRITE);
    }
    private void assertOnlyStrip(LessonCurriculumLogic.Phase phase)throws Exception{
        View root=(View)get("root");
        ViewGroup bar=root.findViewWithTag("lesson-progress");
        View strip=root.findViewWithTag("lesson-progress-strip");
        assertNotNull(bar);assertNotNull(strip);
        assertEquals(1,bar.getChildCount());
        assertSame(strip,bar.getChildAt(0));
        float density=activity.getResources().getDisplayMetrics().density;
        assertEquals(Math.round(190*density),bar.getLayoutParams().width);
        assertEquals(Math.round(9*density),strip.getLayoutParams().height);
        assertNotNull(bar.getBackground());
        assertEquals(Math.round(4*density),bar.getPaddingLeft());
        assertEquals(Math.round(3*density),bar.getPaddingTop());
        Field fillField=strip.getClass().getDeclaredField("fill");
        fillField.setAccessible(true);
        float[] fill=(float[])fillField.get(strip);
        LessonHotbarLogic.Model m=model(phase);
        for(int i=0;i<3;i++){
            float expected=m.totals[i]==0?0f:m.finished[i]/(float)m.totals[i];
            assertEquals("Phase "+phase+", segment "+i,expected,fill[i],0.001f);
        }
        assertNull(root.findViewWithTag("lesson-progress-count"));
        assertNull(root.findViewWithTag("lesson-progress-current-phase"));
        for(int i=0;i<3;i++)assertNull(root.findViewWithTag("lesson-progress-label-"+i));
        assertTrue(strip.getContentDescription().toString().contains(
            new String[]{"Читай","Собери","Напиши"}[phase.ordinal()]));
    }
    @SuppressWarnings("unchecked")
    private List<TextView> assemblyTiles()throws Exception{
        return (List<TextView>)get("lessonBuildTiles");
    }

    @Test public void assemblyShowsWrongTileThenFillsProgressWithoutCommittingEarly()throws Exception{
        text((View)get("root"),"Продолжить").performClick();
        LessonHotbarLogic.Model before=model(LessonCurriculumLogic.Phase.BUILD);
        TextView action=(TextView)get("lessonBuildAction");
        assertFalse(action.isEnabled());
        assertEquals("Продолжить",action.getText().toString());
        View root=(View)get("root");
        TextView wrong=assemblyTiles().stream().filter(tile->"живо".contentEquals(tile.getText())).findFirst().get();
        wrong.performClick();
        assertSame(root,get("root"));
        assertEquals(0,((LessonAssemblyState)get("lessonAssembly")).picked());
        assertEquals(0xFF923C37,wrong.getCurrentTextColor());
        assertNull(text(root,"Это слово сейчас не подходит. Попробуй выбрать другое."));
        assertEquals(before.completedCount,model(LessonCurriculumLogic.Phase.BUILD).completedCount);
        for(String word:new String[]{"Слово","истины","живо"}){
            TextView tile=assemblyTiles().stream().filter(t->word.contentEquals(t.getText())).findFirst().get();
            tile.performClick();
            assertEquals(View.INVISIBLE,tile.getVisibility());
        }
        assertTrue(action.isEnabled());
        assertEquals(0xFFFFFFFF,action.getCurrentTextColor());
        assertArrayEquals(new int[]{0xFF7D9C49,0xFF3E692D},
            ((android.graphics.drawable.GradientDrawable)action.getBackground()).getColors());
        LessonHotbarLogic.Model filled=model(LessonCurriculumLogic.Phase.BUILD);
        assertEquals(before.completedCount+1,filled.completedCount);
        assertEquals(LessonHotbarLogic.DONE,filled.visual(1,0));
        assertEquals(1,store.exerciseDone(id,0));
        activity.onBackPressed();
        call("openTraining",new Class<?>[]{long.class},id);
        assertTrue(((TextView)get("lessonBuildAction")).isEnabled());
        assertEquals(filled.completedCount,model(LessonCurriculumLogic.Phase.BUILD).completedCount);
        ((TextView)get("lessonBuildAction")).performClick();
        assertEquals(3,store.exerciseDone(id,0));
    }

    @Test @Config(qualifiers="w320dp-h560dp")
    public void assemblyKeepsPunctuationInAnswerAndWrapsLongLinkedText()throws Exception{
        long group=store.entry(id).groupId;
        id=store.add(group,"Текст","Свой текст",Arrays.asList(
            new Bible.Verse("1:1","«Слово», — живо."),
            new Bible.Verse("1:2","Необыкновенная последовательность демонстрирует многосложные "+
                "словосочетания богословского повествования сопровождающие размышления путешественников. "+
                "Продолжительное повествование дополнительно иллюстрирует последовательное запоминание "+
                "необыкновенных многосложных формулировок.")));
        call("openTraining",new Class<?>[]{long.class},id);
        text((View)get("root"),"Продолжить").performClick();
        ViewGroup answer=((View)get("root")).findViewWithTag("assembly-answer");
        for(TextView tile:assemblyTiles())assertTrue(tile.getText().toString().matches("[\\p{L}\\p{M}\\p{N} ]+"));
        for(String word:new String[]{"Слово","живо"})
            assemblyTiles().stream().filter(t->word.contentEquals(t.getText())).findFirst().get().performClick();
        assertEquals("«Слово»,",((TextView)answer.getChildAt(0)).getText().toString());
        assertEquals("— живо.",((TextView)answer.getChildAt(1)).getText().toString());

        call("openGroup",new Class<?>[]{GroupLearningLogic.Candidate.class},
            GroupLearningLogic.candidate("CHAIN",0,2,"fixture"));
        text((View)get("root"),"Продолжить").performClick();
        assertNull(text((View)get("root"),"Восстанови пропущенные слова в правильном порядке."));
        // Advance through the first short verse to the longer second fragment of the same group.
        Field picked=LessonAssemblyState.class.getDeclaredField("picked");picked.setAccessible(true);
        picked.setInt(get("lessonAssembly"),(Integer)get("lessonBuildRequired"));refreshAssembly();
        ((TextView)get("lessonBuildAction")).performClick();
        ViewGroup root=(ViewGroup)get("root");
        ViewGroup bank=root.findViewWithTag("assembly-word-bank");
        for(int i=0;i<bank.getChildCount();i++){
            TextView tile=(TextView)bank.getChildAt(i);
            tile.setTextSize(27);
            tile.setMinWidth(150); // Model wider glyph measurements on a narrow screen.
        }
        root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(560,View.MeasureSpec.EXACTLY));
        root.layout(0,0,320,560);
        assertTrue(bank.getChildCount()>=2);
        ViewGroup wrappedAnswer=root.findViewWithTag("assembly-answer");
        assertTrue(wrappedAnswer.getChildAt(wrappedAnswer.getChildCount()-1).getTop()>
            wrappedAnswer.getChildAt(0).getTop());
        assertTrue(bank.getChildAt(bank.getChildCount()-1).getTop()>bank.getChildAt(0).getTop());
        for(int i=0;i<bank.getChildCount();i++)assertTrue(bank.getChildAt(i).getRight()<=bank.getWidth());
        assertEquals(560,root.getChildAt(root.getChildCount()-1).getBottom());
        assertFalse(((TextView)get("lessonBuildAction")).isEnabled());
    }

    @Test public void everyHideLevelLeavesIntroCurrentAndPreservesPassageView()throws Exception{
        View root=(View)get("root"),passage=root.findViewWithTag("lesson-reading-passage"),strip=root.findViewWithTag("lesson-progress-strip");
        TextView hide=text(root,"Скрыть 25% слов");assertNotNull(hide);
        for(int expected:new int[]{0,25,50,75,100,0}){
            if(expected!=0||(int)get("mask")==100)hide.performClick();
            assertEquals(expected,get("mask"));LessonHotbarLogic.Model m=model(LessonCurriculumLogic.Phase.INTRO);
            assertEquals(1,m.totals[0]);assertEquals(0,m.completedCount);assertEquals(1,m.currentNumber);
            assertEquals(LessonHotbarLogic.CURRENT,m.visual(0,0));assertEquals(0,store.exerciseDone(id,0));
            assertSame(passage,((View)get("root")).findViewWithTag("lesson-reading-passage"));assertSame(strip,((View)get("root")).findViewWithTag("lesson-progress-strip"));
        }
    }
    @Test @Config(qualifiers="w320dp-h560dp")
    public void introHintKeepsHideButtonStationaryAtEveryMask()throws Exception{
        View root=(View)get("root");
        ViewGroup hint=root.findViewWithTag("lesson-intro-hint");
        TextView hide=text(root,"Скрыть 25% слов");
        assertNotNull(hint);assertNotNull(hide);
        TextView read=text(hint,LearningHelp.INTRO_READ);
        TextView hold=text(hint,LearningHelp.INTRO_HOLD);
        assertNotNull(read);assertNotNull(hold);
        for(int width:new int[]{320,480}){
            layoutIntro(root,width);
            int height=hint.getHeight(),top=hide.getTop();
            assertTrue(height>0);
            assertEquals(Math.max(read.getMeasuredHeight(),hold.getMeasuredHeight()),height);
            for(int expected:new int[]{25,50,75,100,0}){
                hide.performClick();
                layoutIntro(root,width);
                assertEquals(expected,get("mask"));
                assertEquals("Hint height changed at mask "+expected,height,hint.getHeight());
                assertEquals("Hide button moved at mask "+expected,top,hide.getTop());
                assertEquals(expected==0?View.VISIBLE:View.INVISIBLE,read.getVisibility());
                assertEquals(expected==0?View.INVISIBLE:View.VISIBLE,hold.getVisibility());
            }
        }
    }
    private void layoutIntro(View root,int width){
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(560,View.MeasureSpec.EXACTLY));
        root.layout(0,0,width,560);
    }
    @Test public void lessonFootersUseThePageBackgroundAcrossPhases()throws Exception{
        assertSeamlessFooter();
        store.finishExercise(id,0,1);invoke("render");
        assertSeamlessFooter();
        openWritingFixture();
        assertSeamlessFooter();
        submit("а");
        assertSeamlessFooter();
        call("renderCompletion",new Class<?>[]{LocalStore.Entry.class},store.entry(id));
        assertSeamlessFooter();
        activity.onBackPressed();
        assertEquals(0xFFFFFAF5,activity.getWindow().getNavigationBarColor());
    }
    @Test public void lessonButtonTouchKeepsClickAndCancelBehavior()throws Exception{
        View root=(View)get("root");
        root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(560,View.MeasureSpec.EXACTLY));
        root.layout(0,0,320,560);
        TextView hide=text(root,"Скрыть 25% слов");assertNotNull(hide);
        assertEquals("Скрыть 25% слов",hide.getText().toString());
        touch(hide,MotionEvent.ACTION_DOWN);
        touch(hide,MotionEvent.ACTION_CANCEL);
        assertEquals(0,get("mask"));
        touch(hide,MotionEvent.ACTION_DOWN);
        touch(hide,MotionEvent.ACTION_UP);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0,get("mask"));
        hide.performClick();
        assertEquals(25,get("mask"));
        assertEquals("Скрыть 50% слов",hide.getText().toString());
        hide.setEnabled(false);
        touch(hide,MotionEvent.ACTION_DOWN);
        touch(hide,MotionEvent.ACTION_UP);
        assertEquals(25,get("mask"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
        assertEquals(1f,hide.getScaleX(),0.001f);
        assertEquals(1f,hide.getScaleY(),0.001f);
    }

    private long touchDownTime;
    private void touch(View view,int action){
        long now=android.os.SystemClock.uptimeMillis();
        if(action==MotionEvent.ACTION_DOWN)touchDownTime=now;
        MotionEvent event=MotionEvent.obtain(touchDownTime,now,action,
            view.getWidth()/2f,view.getHeight()/2f,0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    @Test public void memoryCheckFooterUsesTheSamePageBackground()throws Exception{
        KnowledgeCheckSession run=(KnowledgeCheckSession)get("knowledgeCheck");
        run.prepare(42,List.of(new KnowledgeCheckSession.Question(id,0,"Бытие 1:1","Слово истины живо")));
        run.restart();
        set("screen",Enum.valueOf((Class)Class.forName("org.biblememory.app.MainActivity$Screen"),"RUN"));
        invoke("render");
        assertSeamlessFooter();
        run.edit("Ошибка");
        assertTrue(run.check());assertFalse(run.task().passed());invoke("render");
        assertSeamlessFooter();
        run.restart();
        run.edit("Слово истины живо");
        assertTrue(run.check());assertTrue(run.task().passed());invoke("render");
        assertSeamlessFooter();
    }

    private void refreshAssembly()throws Exception{
        Object assembly=get("assemblyView");
        Method update=assembly.getClass().getDeclaredMethod("updateLessonBuildView");
        update.setAccessible(true);update.invoke(assembly);
    }

    private void assertSeamlessFooter()throws Exception{
        ViewGroup root=(ViewGroup)get("root");
        View footer=root.getChildAt(root.getChildCount()-1);
        assertNull(footer.getBackground());
        assertNotNull(((ViewGroup)footer).getChildAt(0).getBackground());
        assertEquals(0xFFF8E5D9,activity.getWindow().getNavigationBarColor());
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
        call("openReview",new Class<?>[]{LocalStore.DueItem.class},
            new LocalStore.DueItem(id,"VERSE",0,1,0));
        LessonHotbarLogic.Model review=model(LessonCurriculumLogic.Phase.WRITE);
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
        android.text.style.ReplacementSpan[] errors=styled.getSpans(0,styled.length(),android.text.style.ReplacementSpan.class);
        assertEquals(1,errors.length);
        assertEquals("е",styled.subSequence(styled.getSpanStart(errors[0]),styled.getSpanEnd(errors[0])).toString());
        assertEquals("ReviewErrorSpan",errors[0].getClass().getSimpleName());
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
        lifecycle.visible();
        StringBuilder passage=new StringBuilder();
        for(int i=0;i<120;i++)passage.append("Слово истины живо. ");
        String expected=passage.toString().trim();
        String actual=expected.replaceFirst("истины","истены");
        long longEntry=store.add(store.entry(id).groupId,"Long","Свой текст",
            java.util.Collections.singletonList(new Bible.Verse("1",expected)));
        call("openReview",new Class<?>[]{LocalStore.DueItem.class},
            new LocalStore.DueItem(longEntry,"VERSE",0,1,0));
        submit(actual);
        ActivityTestWorkers.awaitWriting(activity);
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

    @Test public void reenteringWritingPreservesOnlyUncheckedDraft()throws Exception{
        openWritingFixture();
        edit((View)get("root")).setText("Непроверенный черновик");
        activity.onBackPressed();
        call("openTraining",new Class<?>[]{long.class},id);
        assertEquals("Непроверенный черновик",edit((View)get("root")).getText().toString());
        submit("Ошибочный ответ");
        assertNotNull(text((View)get("root"),"Твой ответ"));
        assertEquals("Ошибочный ответ",((LessonWritingState)get("lessonWriting")).draft);
        activity.onBackPressed();
        call("openTraining",new Class<?>[]{long.class},id);
        assertEquals("",edit((View)get("root")).getText().toString());
        assertNull(text((View)get("root"),"Правильный ответ"));
        assertEquals(3,store.exerciseDone(id,0));
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
