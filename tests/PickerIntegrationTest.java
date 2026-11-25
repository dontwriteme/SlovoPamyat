package org.biblememory.app;

import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.ScrollView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class PickerIntegrationTest {
    ActivityController<MainActivity> controller;
    MainActivity activity;
    LocalStore store;
    Bible bible;
    long group;
    Object field(String name)throws Exception {Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    void set(String name,Object value)throws Exception {Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    Object call(String name,Class<?>[] types,Object... args)throws Exception {
        Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);
    }
    Object invoke(String name)throws Exception{return call(name,new Class<?>[0]);}
    @SuppressWarnings({"rawtypes","unchecked"}) Object option(String type,String value)throws Exception {
        return Enum.valueOf((Class)Class.forName("org.biblememory.app.MainActivity$"+type),value);
    }
    void chapter(int c)throws Exception {set("selectedChapter",c);set("screen",option("Screen","PICKER_TEXT"));invoke("render");}
    PassageSelection selection()throws Exception{return (PassageSelection)field("passageSelection");}
    TextView text(View view,String value){
        if(view instanceof TextView&&value.equals(((TextView)view).getText().toString()))return (TextView)view;
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){TextView found=text(group.getChildAt(i),value);if(found!=null)return found;}
        }
        return null;
    }
    TextView text(String value)throws Exception{return text((View)field("content"),value);}
    void choose(boolean end,int chapter,int verse)throws Exception {
        call("beginPickerBoundary",new Class<?>[]{boolean.class},end);
        assertEquals("PICKER_CHAPTERS",field("screen").toString());
        assertNotNull(text(String.valueOf(chapter)));text(String.valueOf(chapter)).performClick();
        assertEquals("PICKER_VERSES",field("screen").toString());
        assertNotNull(text(String.valueOf(verse)));text(String.valueOf(verse)).performClick();
        assertEquals("BIBLE_PICKER",field("screen").toString());
    }
    String ref(int a,int b,int c,int d){return Bible.reference(40,a,b,c,d);}
    @Before public void setup()throws Exception {
        controller=Robolectric.buildActivity(MainActivity.class).create().start().resume();activity=controller.get();
        ActivityTestWorkers.awaitStartup(activity);
        bible=(Bible)field("bible");
        store=(LocalStore)field("store");
        long id=store.createUnifiedCollection("Picker test");
        LocalStore.UnifiedCollection u=store.unifiedCollection(id);group=u.groupId;
        set("openUnifiedId",id);set("selectedGroup",group);
        Object target=option("BibleTarget","STUDY_GROUP"),back=option("Screen","COLLECTION_DETAIL");
        call("openBiblePicker",new Class<?>[]{target.getClass(),long.class,back.getClass()},target,group,back);
        set("selectedBook",40);chapter(5);
    }
    @After public void finish()throws Exception{if(controller!=null)ActivityTestWorkers.destroy(controller);}
    @Test public void quickTogglePreservesRowsAndHistory()throws Exception {
        ScrollView before=(ScrollView)field("currentScroller");
        @SuppressWarnings("unchecked") List<View> buttons=(List<View>)field("pickerQuickButtons");
        TextView verse=(TextView)buttons.get(2);assertEquals("+",verse.getText().toString());
        verse.performClick();assertEquals("−",verse.getText().toString());
        long entry=store.entries(group).get(0).id;
        verse.performClick();assertEquals("+",verse.getText().toString());
        assertTrue(store.entries(group).isEmpty());assertNotNull(store.entry(entry));
        assertSame(before,field("currentScroller"));
        verse.performClick();assertEquals(entry,store.entries(group).get(0).id);
    }
    @Test public void crossChapterRangeAndCoveredVerse()throws Exception {
        invoke("openPickerRange");choose(false,5,3);choose(true,7,29);
        assertEquals(109,bible.passage(40,5,3,7,29).size());
        assertEquals("Матфея 5:3–7:29 · 109 стихов",((TextView)field("pickerSelectionLabel")).getText().toString());
        invoke("savePickerRange");assertEquals("COLLECTION_DETAIL",field("screen").toString());
        assertEquals(1,store.entries(group).size());assertEquals(109,store.entries(group).get(0).lines().size());
        assertEquals(109,store.flashcards(store.unifiedForGroup(group).deckId).size());
        chapter(5);
        @SuppressWarnings("unchecked") List<View> buttons=(List<View>)field("pickerQuickButtons");
        TextView covered=(TextView)buttons.get(2);assertEquals("✓",covered.getText().toString());
        covered.performClick();assertEquals(1,store.entries(group).size());
    }
    @Test public void overlapDuplicateAndDeletedCollection()throws Exception {
        store.addPickedPassage(group,ref(5,3,5,3),bible.passage(40,5,3,5,3),bible);
        invoke("openPickerRange");choose(false,5,3);choose(true,5,12);invoke("savePickerRange");
        assertEquals(2,store.entries(group).size());
        assertEquals(10,store.flashcards(store.unifiedForGroup(group).deckId).size());
        assertFalse((Boolean)call("savePickedPassageFor",new Class<?>[]{int.class,int.class,int.class,int.class,int.class},40,5,3,5,12));
        assertEquals(2,store.entries(group).size());
        LocalStore.UnifiedCollection collection=store.unifiedForGroup(group);
        // Wait for the automatic snapshot first, then hold the same kind of worker
        // transaction while the UI requests deletion to make the overlap deterministic.
        ActivityTestWorkers.awaitCollectionWork(activity);
        LocalStore workerStore=(LocalStore)field("collectionStore");
        java.util.concurrent.CountDownLatch transactionOpen=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Future<?> reader=ActivityTestWorkers.executor(activity,"collectionExecutor").submit(()->{
            android.database.sqlite.SQLiteDatabase db=workerStore.getReadableDatabase();
            db.beginTransactionNonExclusive();
            try{
                transactionOpen.countDown();
                assertTrue(release.await(30,java.util.concurrent.TimeUnit.SECONDS));
            }finally{db.endTransaction();}
            return null;
        });
        try{
            assertTrue(transactionOpen.await(30,java.util.concurrent.TimeUnit.SECONDS));
            call("deleteUnified",new Class<?>[]{LocalStore.UnifiedCollection.class},collection);
            org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
                .getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            // AlertDialog posts its button callback to the main looper. Queue deletion
            // before releasing the worker, so the test actually exercises the overlap.
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }finally{release.countDown();}
        reader.get(30,java.util.concurrent.TimeUnit.SECONDS);
        ActivityTestWorkers.awaitCollectionWork(activity);
        assertNull(store.unifiedForGroup(group));
        assertFalse((Boolean)call("savePickedPassageFor",new Class<?>[]{int.class,int.class,int.class,int.class,int.class},40,5,13,5,15));
        assertEquals(2,store.entries(group).size());
    }
    @Test public void reversedBoundsEditingAndCancel()throws Exception {
        invoke("openPickerRange");
        assertEquals(View.GONE,((View)field("pickerSelectionLabel")).getVisibility());
        choose(false,5,12);
        assertEquals(View.GONE,((View)field("pickerSelectionLabel")).getVisibility());
        choose(true,5,3);
        TextView selected=(TextView)field("pickerSelectionLabel");
        assertEquals(View.VISIBLE,selected.getVisibility());
        assertTrue(selected.getText().toString().contains("Конец раньше начала"));
        assertArrayEquals(new int[]{5,12,5,3},selection().bounds());
        assertFalse(((View)field("pickerSaveButton")).isEnabled());
        invoke("savePickerRange");assertTrue(store.entries(group).isEmpty());
        choose(true,5,14);assertEquals(12,selection().startVerse);
        choose(false,5,4);assertEquals(14,selection().endVerse);
        activity.onBackPressed();assertFalse(selection().active);
        assertEquals("PICKER_TEXT",field("screen").toString());
        @SuppressWarnings("unchecked") List<View> buttons=(List<View>)field("pickerQuickButtons");
        assertFalse(buttons.isEmpty());assertEquals(View.VISIBLE,buttons.get(0).getVisibility());
    }
    @Test public void recreateKeepsSelectionAndCollection()throws Exception {
        invoke("openPickerRange");choose(false,5,3);choose(true,7,29);
        ActivityTestWorkers.recreate(controller);activity=controller.get();
        ((Bible)field("bible")).load(activity);invoke("render");
        assertEquals(group,field("pickerTargetId"));assertEquals("BIBLE_PICKER",field("screen").toString());
        assertTrue(selection().active);assertArrayEquals(new int[]{5,3,7,29},selection().bounds());
    }
    @Test public void atomicRollbackWhenCardSyncFails()throws Exception {
        store.getWritableDatabase().execSQL("CREATE TRIGGER fail_picker_card BEFORE INSERT ON card_deck_members BEGIN SELECT RAISE(ABORT,'test failure'); END");
        try {store.addPickedPassage(group,ref(5,3,5,12),bible.passage(40,5,3,5,12),bible);fail("Expected rollback");}
        catch(Exception expected){}
        assertTrue(store.entries(group).isEmpty());
        assertTrue(store.flashcards(store.unifiedForGroup(group).deckId).isEmpty());
    }
    @Test public void psalmsGridsExposeEveryNumberAndQuickTextDoesNotAdd()throws Exception {
        set("selectedBook",19);set("selectedChapter",118);set("screen",option("Screen","PICKER_CHAPTERS"));invoke("render");
        assertEquals(150,bible.chapterCount(19));assertNotNull(text("150"));assertNull(text("151"));
        text("118").performClick();assertEquals(176,bible.chapter(19,118).size());
        assertNotNull(text("1"));assertNotNull(text("176"));assertNull(text("177"));
        text("176").performClick();assertEquals("PICKER_TEXT",field("screen").toString());
        assertEquals(176,field("pickerVerseNumber"));assertTrue(store.entries(group).isEmpty());
        assertNotNull(text("Главы · 118"));assertNotNull(text("Стихи"));
        assertNull(text("‹ Глава"));assertNull(text("Глава ›"));
        invoke("openPickerRange");choose(false,118,1);choose(true,118,176);
        assertEquals("Псалтирь 118:1–176 · 176 стихов",((TextView)field("pickerSelectionLabel")).getText().toString());
        invoke("savePickerRange");assertEquals(1,store.entries(group).size());
        assertEquals(176,store.entries(group).get(0).lines().size());
    }
    @Test public void pickerKeepsFirstVerseAtTopAndRestoresPreviousGridOnBack()throws Exception {
        controller.visible();
        set("selectedBook",19);set("selectedChapter",118);
        set("screen",option("Screen","PICKER_CHAPTERS"));invoke("render");
        layoutReader();
        ScrollView chapterGrid=(ScrollView)field("currentScroller");
        assertEquals(0,chapterGrid.getScrollY());
        chapterGrid.scrollTo(0,400);
        int saved=chapterGrid.getScrollY();
        assertTrue(saved>0);
        for(int verse:new int[]{1,88,176}){
            text("118").performClick();layoutReader();
            assertEquals(0,((ScrollView)field("currentScroller")).getScrollY());
            text(Integer.toString(verse)).performClick();layoutReader();
            assertNotNull(text("Псалтирь 118"));
            int verseTextScroll=((ScrollView)field("currentScroller")).getScrollY();
            if(verse==1)assertEquals(0,verseTextScroll);
            else assertTrue(verseTextScroll>0);
            activity.onBackPressed();layoutReader();
            activity.onBackPressed();layoutReader();
            assertEquals(saved,((ScrollView)field("currentScroller")).getScrollY());
        }
    }
    @Test public void genesisVerse27OpensAtItsTextWithAddAction()throws Exception {
        controller.visible();
        set("screen",option("Screen","PICKER_BOOKS"));
        invoke("render");
        ((View)text("Бытие").getParent()).performClick();
        text("1").performClick();
        text("27").performClick();
        layoutReader();

        assertEquals("PICKER_TEXT",field("screen").toString());
        View marker=text("1:27");
        assertNotNull(marker);
        View verseCard=(View)marker.getParent().getParent();
        ScrollView scroll=(ScrollView)field("currentScroller");
        assertTrue(scroll.getScrollY()>0);
        assertTrue(verseCard.getTop()>=scroll.getScrollY());
        assertTrue(verseCard.getTop()<scroll.getScrollY()+scroll.getHeight());

        @SuppressWarnings("unchecked") List<View> buttons=(List<View>)field("pickerQuickButtons");
        buttons.get(26).performClick();
        assertEquals("Бытие 1:27",store.entries(group).get(0).reference);
    }
    @Test public void backCancelsOnlyPendingBoundaryAndKeepsQuickChapter()throws Exception {
        invoke("openPickerRange");choose(false,5,3);choose(true,7,29);
        call("beginPickerBoundary",new Class<?>[]{boolean.class},false);
        text("6").performClick();activity.onBackPressed();
        assertEquals("PICKER_CHAPTERS",field("screen").toString());
        activity.onBackPressed();assertEquals("BIBLE_PICKER",field("screen").toString());
        assertArrayEquals(new int[]{5,3,7,29},selection().bounds());
        activity.onBackPressed();assertEquals("PICKER_TEXT",field("screen").toString());
        assertEquals(5,field("selectedChapter"));
    }
    @Test public void identicalBoundaryAndLastVerseOfShortBook()throws Exception {
        set("selectedBook",31);chapter(1);assertEquals(1,bible.chapterCount(31));
        int last=bible.chapter(31,1).size();invoke("openPickerRange");choose(false,1,last);choose(true,1,last);
        assertTrue(((View)field("pickerSaveButton")).isEnabled());
        invoke("savePickerRange");assertEquals(1,store.entries(group).get(0).lines().size());
    }
    @Test public void collectionsAndReaderPreferencesStayIndependent()throws Exception {
        activity.getSharedPreferences("bible_last_chapter",0).edit().putInt("40",2).commit();
        @SuppressWarnings("unchecked") List<View> buttons=(List<View>)field("pickerQuickButtons");
        buttons.get(0).performClick();assertEquals(1,store.entries(group).size());
        long otherId=store.createUnifiedCollection("Other");long other=store.unifiedCollection(otherId).groupId;
        Object target=option("BibleTarget","STUDY_GROUP"),back=option("Screen","COLLECTION_DETAIL");
        call("openBiblePicker",new Class<?>[]{target.getClass(),long.class,back.getClass()},target,other,back);
        set("selectedBook",40);chapter(5);
        @SuppressWarnings("unchecked") List<View> fresh=(List<View>)field("pickerQuickButtons");
        assertEquals("+",((TextView)fresh.get(0)).getText().toString());fresh.get(1).performClick();
        assertEquals(1,store.entries(group).size());assertEquals(1,store.entries(other).size());
        invoke("openPickerRange");choose(false,6,1);choose(true,7,1);activity.onBackPressed();
        assertEquals(2,activity.getSharedPreferences("bible_last_chapter",0).getInt("40",-1));
    }
    @Test public void readerVersesHaveNoClickActions()throws Exception {
        set("screen",option("Screen","CHAPTER"));invoke("render");
        assertNull(text("Текст главы"));assertNotNull(text("Глава 5  ▾"));
        assertNull(text("Скрыть статусы"));assertNull(text("Показать статусы"));
        assertNull(text("Выбрать отрывок"));assertNull(text("Задать диапазон"));
        assertNull(text("Просмотреть отрывок"));assertNull(text("Добавить в карточки"));
        ScrollView scroller=(ScrollView)field("currentScroller");
        for(Bible.Verse verse:bible.chapter(40,5)){
            TextView body=text(verse.text);assertNotNull(body);
            View row=(View)body.getParent();
            assertFalse(body.hasOnClickListeners());assertFalse(row.hasOnClickListeners());
            assertFalse(body.performClick());assertFalse(row.performClick());
        }
        assertNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
        assertSame(scroller,field("currentScroller"));assertEquals("CHAPTER",field("screen").toString());
        assertTrue(store.entries(group).isEmpty());
    }
    @Test public void searchOnlyOpensReadingAndCannotChangeCollection()throws Exception {
        set("screen",option("Screen","BOOKS"));
        Bible.Verse verse=bible.chapter(40,5).get(2);
        set("bibleSearchHits",new ArrayList<>(Collections.singletonList(
            new BibleSearchLogic.Hit(40,5,3,ref(5,3,5,3),verse.text,1))));
        set("bibleSearchQuery","блаженны");
        android.widget.ListView list=(android.widget.ListView)invoke("createSearchResultsList");
        View result=list.getAdapter().getView(list.getHeaderViewsCount(),null,list);
        assertNull(text(result,"+"));assertNull(text(result,"−"));
        TextView open=text(result,"Показать в главе  ›");assertNotNull(open);open.performClick();
        assertEquals("CHAPTER",field("screen").toString());
        assertEquals(40,field("selectedBook"));assertEquals(5,field("selectedChapter"));
        assertTrue(store.entries(group).isEmpty());assertNull(text("Выбрать отрывок"));
        assertNull(text("✓ Выбран"));
        assertNotNull(text("● Не изучен   ·   Повторений: 0"));
        TextView focused=text("5:3");assertNotNull(focused);
        View card=(View)((View)focused.getParent()).getParent();
        android.graphics.drawable.GradientDrawable highlight=(android.graphics.drawable.GradientDrawable)card.getBackground();
        assertEquals(0xFFEDEBDD,highlight.getColor().getDefaultColor());
    }
    private static final class ManualSearchExecutor extends java.util.concurrent.AbstractExecutorService {
        final List<Runnable> queued=new ArrayList<>();boolean stopped;
        public void execute(Runnable task){queued.add(task);}
        public void shutdown(){stopped=true;}
        public List<Runnable> shutdownNow(){stopped=true;return queued;}
        public boolean isShutdown(){return stopped;}
        public boolean isTerminated(){return stopped;}
        public boolean awaitTermination(long timeout,java.util.concurrent.TimeUnit unit){return stopped;}
    }
    private ManualSearchExecutor manualSearch()throws Exception {
        ((java.util.concurrent.ExecutorService)field("bibleSearchExecutor")).shutdownNow();
        ManualSearchExecutor executor=new ManualSearchExecutor();set("bibleSearchExecutor",executor);
        set("screen",option("Screen","BOOKS"));invoke("render");controller.visible();return executor;
    }
    private android.widget.EditText searchInput()throws Exception {
        return (android.widget.EditText)call("findBibleSearchInput",new Class<?>[]{View.class},field("root"));
    }
    @Test public void blankSearchNeverQueuesWorkAndKeepsCatalog()throws Exception {
        ManualSearchExecutor executor=manualSearch();
        call("submitBibleSearch",new Class<?>[]{String.class},"   ");
        assertFalse((Boolean)field("bibleSearchActive"));assertTrue(executor.queued.isEmpty());
        assertNotNull(text("Бытие"));assertNotNull(text("Откровение"));assertNull(text("Ничего не найдено"));
        assertNull(field("bibleSearchTask"));
    }
    @Test public void typingDebouncesAndClearCancelsQueuedRequest()throws Exception {
        ManualSearchExecutor executor=manualSearch();android.widget.EditText input=searchInput();
        input.setText("бог");
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
        assertTrue(executor.queued.isEmpty());input.setText("любовь");
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(299));
        assertTrue(executor.queued.isEmpty());
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1));
        assertEquals(1,executor.queued.size());
        java.util.concurrent.Future<?> old=(java.util.concurrent.Future<?>)field("bibleSearchTask");
        input.setText("");assertTrue(old.isCancelled());
        for(Runnable task:executor.queued)task.run();
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse((Boolean)field("bibleSearchActive"));assertNotNull(text("Бытие"));
        assertTrue(((List<?>)field("bibleSearchHits")).isEmpty());
    }
    @Test public void nonemptyNoMatchKeepsQueryUntilClear()throws Exception {
        searchThroughInput(manualSearch(),"qzxvnotfound");
        assertEquals("qzxvnotfound",searchInput().getText().toString());
        assertNotNull(text((View)field("root"),"Ничего не найдено"));
        assertNull(text((View)field("root"),"Бытие"));
        searchInput().setText("");assertNotNull(text("Бытие"));assertNull(text("Ничего не найдено"));
    }
    @Test public void readerChapterGridHasNoCountSubtitle()throws Exception {
        set("screen",option("Screen","CHAPTERS"));set("selectedBook",19);invoke("render");
        assertNull(text("150 глав"));assertNotNull(text("150"));assertNotNull(text("Псалтирь"));
    }
    @Test public void leavingSearchCancelsAndReturnRestartsOnlyCurrentQuery()throws Exception {
        ManualSearchExecutor executor=manualSearch();searchInput().setText("бог");
        set("screen",option("Screen","CHAPTERS"));invoke("render");
        assertNull(field("bibleSearchDebounce"));assertTrue((Boolean)field("bibleSearchNeedsRefresh"));
        activity.onBackPressed();
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("BOOKS",field("screen").toString());assertEquals(1,executor.queued.size());
        assertEquals("бог",field("bibleSearchQuery"));
        searchInput().setText("");
        for(Runnable task:executor.queued)task.run();
        assertFalse((Boolean)field("bibleSearchActive"));assertNotNull(text("Бытие"));
    }
    @Test public void clearingVirtualResultsPreservesCatalogScroll()throws Exception {
        ManualSearchExecutor executor=manualSearch();
        layoutReader();
        ScrollView catalog=(ScrollView)field("currentScroller");
        catalog.scrollTo(0,123);
        assertEquals(123,catalog.getScrollY());

        searchThroughInput(executor,"бог");
        assertNull(field("currentScroller"));
        Object books=option("Screen","BOOKS");
        Map<?,?> tabs=(Map<?,?>)field("readyTabs");
        Object tab=tabs.get(books);
        Field scroller=tab.getClass().getDeclaredField("scroller");
        scroller.setAccessible(true);
        assertNull(scroller.get(tab));
        assertNotNull(text((View)field("root"),"Найдено стихов: "+((List<?>)field("bibleSearchHits")).size()));
        assertFalse(((List<?>)field("bibleSearchHits")).isEmpty());
        searchInput().setText("");
        layoutReader();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Integer.valueOf(123),((Map<?,?>)field("savedScrollY")).get(books));
        assertEquals(123,((ScrollView)field("currentScroller")).getScrollY());
        assertNotNull(text("Бытие"));
    }
    private void searchThroughInput(ManualSearchExecutor executor,String query)throws Exception{
        searchInput().setText(query);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
        assertEquals(1,executor.queued.size());
        executor.queued.remove(0).run();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layoutReader();
        assertNotNull("Search header must be attached after layout",searchInput());
    }
    private void reader(String destination,int book,int chapter)throws Exception {
        set("selectedBook",book);set("selectedChapter",chapter);
        set("screen",option("Screen",destination));invoke("render");
    }
    private void tapBibleTab()throws Exception {
        View root=(View)field("root");
        BookNavigationIcon icon=root.findViewWithTag(BookNavigationIcon.TAG);
        assertNotNull(icon);((View)icon.getParent()).performClick();
    }
    private void layoutReader()throws Exception {
        View page=(View)field("root");
        page.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY));
        page.layout(0,0,1080,1920);
        page.getViewTreeObserver().dispatchOnGlobalLayout();
    }
    @Test public void ordinaryReaderUsesAll176VerseNumbersAndBackStack()throws Exception {
        controller.visible();
        reader("HOME",19,1);
        tapBibleTab();assertEquals("BOOKS",field("screen").toString());
        for(String book:Bible.NAMES)assertNotNull(text(book));
        ((View)text("Псалтирь").getParent()).performClick();
        assertEquals("CHAPTERS",field("screen").toString());
        text("118").performClick();assertEquals("VERSES",field("screen").toString());
        for(int number=1;number<=176;number++)assertNotNull(text(String.valueOf(number)));
        assertNull(text("177"));
        Object pickerBounds=field("passageSelection");
        int pickerVerse=(Integer)field("pickerVerseNumber");
        text("176").performClick();assertEquals("CHAPTER",field("screen").toString());
        assertEquals(176,field("readerVerseNumber"));
        assertEquals(pickerVerse,field("pickerVerseNumber"));assertSame(pickerBounds,field("passageSelection"));
        assertTrue(store.entries(group).isEmpty());
        TextView last=text(bible.chapter(19,118).get(175).text);assertNotNull(last);
        View card=(View)last.getParent();Object highlighted=card.getBackground();
        layoutReader();
        ScrollView chapter=(ScrollView)field("currentScroller");
        assertTrue(chapter.getScrollY()>0);
        assertTrue(card.getTop()<chapter.getScrollY()+chapter.getHeight());
        assertTrue(card.getBottom()>chapter.getScrollY());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1200));
        assertNotSame(highlighted,card.getBackground());
        activity.onBackPressed();assertEquals("VERSES",field("screen").toString());
        assertNotNull(text("176"));activity.onBackPressed();
        assertEquals("CHAPTERS",field("screen").toString());
        activity.onBackPressed();assertEquals("BOOKS",field("screen").toString());
    }
    @Test public void bibleReselectReturnsFromEveryReaderLevelAndKeepsCatalogInstance()throws Exception {
        for(String destination:new String[]{"CHAPTERS","VERSES","CHAPTER"}) {
            reader(destination,19,118);tapBibleTab();
            assertEquals("BOOKS",field("screen").toString());
            for(String book:Bible.NAMES)assertNotNull(text(book));
            BookNavigationIcon icon=((View)field("root")).findViewWithTag(BookNavigationIcon.TAG);
            assertTrue(icon.isSelected());
            View catalog=(View)field("root");tapBibleTab();
            assertSame(catalog,field("root"));
        }
    }
    @Test public void readerVerseChoiceDoesNotEditActiveCollectionRange()throws Exception {
        invoke("openPickerRange");choose(false,5,3);choose(true,5,12);
        int[] bounds=selection().bounds();
        reader("CHAPTERS",19,118);text("118").performClick();text("176").performClick();
        assertEquals("CHAPTER",field("screen").toString());
        assertArrayEquals(bounds,selection().bounds());assertTrue(store.entries(group).isEmpty());
    }
    @Test public void readerFocusOverridesPreviouslySavedChapterScroll()throws Exception {
        controller.visible();
        reader("CHAPTER",40,5);
        @SuppressWarnings("unchecked") Map<Object,Integer> saved=(Map<Object,Integer>)field("savedScrollY");
        saved.put(option("Screen","CHAPTER"),0);
        reader("VERSES",40,5);text("40").performClick();layoutReader();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));
        assertTrue(((ScrollView)field("currentScroller")).getScrollY()>0);
    }

    @Test @Config(qualifiers="w1080dp-h1920dp")
    public void homeModeInfoButtonsOpenTheirOwnNonModalPopup()throws Exception {
        set("activeUnifiedId",store.unifiedForGroup(group).id);
        set("homeCollectionsExpanded",true);
        set("screen",option("Screen","HOME"));
        invoke("render");
        controller.visible();
        View decor=activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY));
        decor.layout(0,0,1080,1920);
        View root=(View)field("root");
        String[] names={"Изучение стихов","Карточки","Закрепление стихов","Проверка знаний","Общее повторение"};
        String[] headings={"Ознакомление","Как пользоваться","Что повторять","Что проверяется","Что повторять"};
        String[] descriptions={"постепенно убирая подсказки","Нажми на глаз","выбранной коллекции",
            "закреплённые отложенным повторением","из всех коллекций"};
        for(int i=0;i<names.length;i++){
            TextView label=text(root,names[i]);assertNotNull(label);
            android.view.ViewGroup row=(android.view.ViewGroup)((View)label.getParent()).getParent();
            View anchor=row.getChildAt(1);
            assertEquals("О режиме «"+names[i]+"»",anchor.getContentDescription());
            assertNotNull(anchor);
            assertTrue(anchor.getWidth()>=48*activity.getResources().getDisplayMetrics().density);
            assertNotSame(label.getParent(),anchor.getParent());
            ScrollView home=(ScrollView)field("currentScroller");
            android.graphics.Rect position=new android.graphics.Rect(0,0,anchor.getWidth(),anchor.getHeight());
            home.offsetDescendantRectToMyCoords(anchor,position);
            home.scrollTo(0,Math.max(0,position.top-home.getHeight()/2));
            anchor.performClick();
            android.widget.PopupWindow popup=(android.widget.PopupWindow)popoverField("modeInfoPopup");
            android.graphics.Rect frame=new android.graphics.Rect();
            anchor.getWindowVisibleDisplayFrame(frame);
            assertNotNull("Popover for "+names[i]+" in "+frame,popup);assertFalse(popup.isFocusable());
            assertNotNull(text(popup.getContentView(),names[i]));
            assertNotNull(text(popup.getContentView(),headings[i]));
            assertTrue(textContains(popup.getContentView(),descriptions[i]));
            assertEquals("HOME",field("screen").toString());
        }
        View anchor=(View)popoverField("modeInfoAnchor");anchor.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
        assertNull(popoverField("modeInfoPopup"));
        TextView summary=text(root,"0 стихов · 0 предварительно освоено");
        assertNotNull(summary);
        ((android.view.ViewGroup)summary.getParent()).getChildAt(1).performClick();
        android.widget.PopupWindow progress=(android.widget.PopupWindow)popoverField("modeInfoPopup");
        assertNotNull(progress);
        assertTrue(textContains(progress.getContentView(),"24 часов"));
        activity.onBackPressed();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
        assertNull(popoverField("modeInfoPopup"));
        assertEquals("HOME",field("screen").toString());
    }

    private Object popoverField(String name)throws Exception{
        Field member=ModeInfoPopover.class.getDeclaredField(name);
        member.setAccessible(true);
        return member.get(field("modeInfo"));
    }

    private boolean textContains(View view,String snippet){
        if(view instanceof TextView&&((TextView)view).getText().toString().contains(snippet))return true;
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)if(textContains(group.getChildAt(i),snippet))return true;
        }
        return false;
    }

}
