package org.biblememory.app;

import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Recreate real Activities; verify both visible answers/navigation and unchanged persisted results. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class ActivityRecreationIntegrationTest {
    private ActivityController<MainActivity> lifecycle;
    private MainActivity activity;
    private LocalStore store;
    private long collection,group,entry;
    private Object get(String key)throws Exception{return ActivityTestAccess.get(activity,key);}
    private void set(String key,Object value)throws Exception{ActivityTestAccess.set(activity,key,value);}
    private Object call(String key,Class<?>[] types,Object... args)throws Exception{return ActivityTestAccess.call(activity,key,types,args);}
    private StudySessionController study()throws Exception{return (StudySessionController)get("studySession");}
    private FlashcardSessionController cards()throws Exception{return (FlashcardSessionController)get("flashcardSession");}
    private CollectionController collections()throws Exception{return (CollectionController)get("collectionActions");}
    private BibleReaderController reader()throws Exception{return (BibleReaderController)get("bibleReader");}
    private View root()throws Exception{return (View)get("root");}
    private void render(Screen destination)throws Exception{set("screen",destination);call("render",new Class<?>[0]);layout();}
    private void layout()throws Exception{
        View root=root();root.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1800,View.MeasureSpec.EXACTLY));
        root.layout(0,0,1080,1800);root.getViewTreeObserver().dispatchOnGlobalLayout();
    }
    private void finishLoading()throws Exception{
        reader().bible.load(activity);
        for(Thread worker:Thread.getAllStackTraces().keySet())if("BibleLoader".equals(worker.getName())){
            worker.join(10000);assertFalse("BibleLoader startup timeout",worker.isAlive());
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();layout();
    }
    private void recreate()throws Exception{
        lifecycle.recreate();activity=lifecycle.get();store=(LocalStore)get("store");finishLoading();
    }
    private EditText input(View view){
        if(view instanceof EditText)return (EditText)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){EditText found=input(group.getChildAt(i));if(found!=null)return found;}}
        return null;
    }
    @Before public void setup()throws Exception{
        lifecycle=Robolectric.buildActivity(MainActivity.class).create().start().resume();activity=lifecycle.get();store=(LocalStore)get("store");finishLoading();
        collection=store.createUnifiedCollection("Recreation");group=store.unifiedCollection(collection).groupId;
        entry=store.add(group,"Saved","Пользовательский текст",Arrays.asList(new Bible.Verse("1:1","Слово истины"),new Bible.Verse("1:2","Слово живо")));
    }
    @After public void teardown()throws Exception{if(lifecycle!=null){finishLoading();lifecycle.pause().stop().destroy();}}
    @Test public void unfinishedTypedAnswerSurvivesAndDoesNotAdvanceProgress()throws Exception{
        store.finishExercise(entry,0,3);call("openTraining",new Class<?>[]{long.class},entry);layout();
        EditText answer=input(root());assertNotNull(answer);answer.setText("Незавершённый ответ");
        long seed=study().lessonSeed;recreate();assertEquals(Screen.TRAIN,get("screen"));
        assertEquals("Незавершённый ответ",study().draft);assertEquals("Незавершённый ответ",input(root()).getText().toString());
        assertEquals(seed,study().lessonSeed);assertEquals(3,store.exerciseDone(entry,0));assertFalse(study().checked);
        activity.onBackPressed();assertEquals(Screen.HOME,get("screen"));
    }
    @Test public void assemblyKeepsSeedAndSelectedTilesWithoutCompletingAnExercise()throws Exception{
        store.finishExercise(entry,0,1);call("openTraining",new Class<?>[]{long.class},entry);
        study().lessonSeed=1234;study().lessonUsedTiles.add(0);study().lessonPicked=1;render(Screen.TRAIN);
        recreate();assertEquals(Screen.TRAIN,get("screen"));assertEquals(1234,study().lessonSeed);
        assertEquals(Collections.singletonList(0),study().lessonUsedTiles);assertEquals(1,study().lessonPicked);assertEquals(1,store.exerciseDone(entry,0));
    }
    @Test public void revealedFlashcardKeepsExactOrderDirectionAndCursor()throws Exception{
        long deck=store.unifiedCollection(collection).deckId;
        long first=store.addCustomFlashcard(deck,"First","Первый"),second=store.addCustomFlashcard(deck,"Second","Второй");
        store.syncUnifiedCollection(collection,reader().bible);
        cards().selectedDeck=deck;cards().cardFromUnified=true;assertTrue(cards().startCardSession());
        List<Long> order=new ArrayList<>(cards().cardSequence);int materialCount=store.flashcards(deck).size();cards().cardIndex=1;
        cards().cardShowReference=false;cards().cardRevealed=true;cards().cardFromUnified=true;render(Screen.CARD_SESSION);
        recreate();assertEquals(Screen.CARD_SESSION,get("screen"));assertEquals(order,cards().cardSequence);
        assertEquals(1,cards().cardIndex);assertFalse(cards().cardShowReference);assertTrue(cards().cardRevealed);
        assertEquals(materialCount,store.flashcards(deck).size());activity.onBackPressed();assertEquals(Screen.HOME,get("screen"));
    }
    @Test public void selectedReaderVerseAndScrollReturnAfterContentLoads()throws Exception{
        reader().selectedBook=19;reader().selectedChapter=118;reader().readerVerseNumber=120;reader().chapterFromVerseGrid=true;
        reader().focusVerse=-1;render(Screen.CHAPTER);ScrollView scroll=(ScrollView)get("currentScroller");scroll.scrollTo(0,2300);int y=scroll.getScrollY();assertTrue(y>0);
        recreate();assertEquals(Screen.CHAPTER,get("screen"));assertEquals(19,reader().selectedBook);assertEquals(118,reader().selectedChapter);
        assertEquals(120,reader().readerVerseNumber);assertEquals(y,((ScrollView)get("currentScroller")).getScrollY());
        activity.onBackPressed();assertEquals(Screen.VERSES,get("screen"));assertEquals(176,reader().bible.chapter(19,118).size());
    }
    @Test public void rangePickerKeepsCrossChapterBoundsAndInsertionTrail()throws Exception{
        collections().openUnifiedId=collection;collections().selectedGroup=group;collections().insertAfterEntryId=entry;collections().studyInsertionTrail.add(entry);
        reader().openBiblePicker(BibleReaderController.BibleTarget.STUDY_GROUP,group,Screen.COLLECTION_DETAIL);
        reader().selectedBook=40;reader().selectedChapter=5;reader().pickerQuickBook=40;reader().pickerQuickChapter=5;
        reader().passageSelection.openBook(40);reader().passageSelection.active=true;
        reader().passageSelection.setBoundary(false,5,3);reader().passageSelection.setBoundary(true,7,29);render(Screen.BIBLE_PICKER);
        recreate();assertEquals(Screen.BIBLE_PICKER,get("screen"));assertEquals(group,reader().pickerTargetId);
        assertArrayEquals(new int[]{5,3,7,29},reader().passageSelection.bounds());assertTrue(reader().passageSelection.active);
        assertEquals(entry,collections().insertAfterEntryId);assertEquals(Collections.singletonList(entry),collections().studyInsertionTrail);
        assertEquals(1,store.entries(group).size());activity.onBackPressed();assertEquals(Screen.PICKER_TEXT,get("screen"));
    }
    @Test public void collectionQueueContinuesAfterRecreationWithoutJoiningPassages()throws Exception{
        long second=store.add(group,"Second","Пользовательский текст",Collections.singletonList(new Bible.Verse("1:1","Второй текст")));
        store.reorderStudy(group,Arrays.asList(entry,second));collections().startStudyCollection(group);layout();
        recreate();assertEquals(Screen.TRAIN,get("screen"));assertTrue(collections().executingCollectionQueue);
        assertEquals(Arrays.asList(entry,second),collections().collectionQueue);assertEquals(0,collections().collectionQueueCursor);
        collections().advanceStudyCollection();assertEquals(second,study().selectedEntry);assertEquals(1,collections().collectionQueueCursor);
        assertEquals(2,store.entries(group).size());assertEquals(0,store.exerciseDone(entry,0));
    }
    @Test public void reviewContextAndFeedbackArePreservedWithoutSchedulingAgain()throws Exception{
        StudySessionController session=study();session.selectedEntry=entry;session.reviewMode=true;
        session.studyStage=StudyPlanLogic.Stage.VERSE;session.forcedStep=new StudyPlanLogic.Step(0,1,"1:1");
        session.currentReview=new LocalStore.DueItem(entry,"VERSE",0,1,12345);session.reviewQueue.add(session.currentReview);
        session.draft="Неполный ответ";session.checked=false;render(Screen.TRAIN);recreate();
        assertTrue(study().reviewMode);assertEquals(1,study().reviewQueue.size());assertEquals(12345,study().currentReview.due);
        assertEquals(0,study().forcedStep.start);assertEquals(1,study().forcedStep.end);assertEquals("Неполный ответ",study().draft);
        assertFalse(store.isStepMastered(entry,"VERSE",0,1));assertEquals(0,store.entry(entry).repetitions);
    }
    @Test public void writingRunRebuildsOnlyConfirmedMaterialAndKeepsAnswer()throws Exception{
        // Fixed historical confirmation fixture; production scheduling is untouched.
        for(int i=0;i<2;i++)store.getWritableDatabase().execSQL("INSERT INTO recall_activity(entry_id,stage,first_line,last_line,first_success,last_success,success_count,fail_streak) VALUES(?, 'VERSE',?,?,1,86400001,2,0)",new Object[]{entry,i,i+1});
        study().runCollectionId=collection;study().buildRunLines(store.unifiedCollection(collection));assertEquals(2,study().runLines.size());
        study().runCursor=1;study().runCorrect=1;study().runDraft="Продолжаю писать";render(Screen.RUN);
        recreate();assertEquals(Screen.RUN,get("screen"));assertEquals(2,study().runLines.size());assertEquals(1,study().runCursor);
        assertEquals("Продолжаю писать",study().runDraft);assertEquals("Продолжаю писать",input(root()).getText().toString());
        assertEquals(1,study().runCorrect);assertEquals(0,store.entry(entry).repetitions);
    }
    @Test public void parcelledSavedStateRestoresInANewActivityInstance()throws Exception{
        store.finishExercise(entry,0,3);call("openTraining",new Class<?>[]{long.class},entry);input(root()).setText("Ответ перед выгрузкой");
        Bundle saved=new Bundle();lifecycle.saveInstanceState(saved);Parcel parcel=Parcel.obtain();Bundle restored;
        try{parcel.writeBundle(saved);parcel.setDataPosition(0);restored=parcel.readBundle(MainActivity.class.getClassLoader());}finally{parcel.recycle();}
        lifecycle.pause().stop().destroy();lifecycle=Robolectric.buildActivity(MainActivity.class).create(restored).start().resume();activity=lifecycle.get();store=(LocalStore)get("store");finishLoading();
        assertEquals(Screen.TRAIN,get("screen"));assertEquals("Ответ перед выгрузкой",input(root()).getText().toString());assertEquals(3,store.exerciseDone(entry,0));
    }
}
