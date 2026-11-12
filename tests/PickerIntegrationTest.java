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
        bible=(Bible)field("bible");bible.load(activity);
        store=(LocalStore)field("store");
        long id=store.createUnifiedCollection("Picker test");
        LocalStore.UnifiedCollection u=store.unifiedCollection(id);group=u.groupId;
        set("openUnifiedId",id);set("selectedGroup",group);
        Object target=option("BibleTarget","STUDY_GROUP"),back=option("Screen","COLLECTION_DETAIL");
        call("openBiblePicker",new Class<?>[]{target.getClass(),long.class,back.getClass()},target,group,back);
        set("selectedBook",40);chapter(5);
    }
    @After public void finish(){if(controller!=null)controller.pause().stop().destroy();}
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
        store.deleteUnifiedCollection(store.unifiedForGroup(group).id);
        assertFalse((Boolean)call("savePickedPassageFor",new Class<?>[]{int.class,int.class,int.class,int.class,int.class},40,5,13,5,15));
        assertEquals(2,store.entries(group).size());
    }
    @Test public void reversedBoundsEditingAndCancel()throws Exception {
        invoke("openPickerRange");choose(false,5,12);choose(true,5,3);
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
        controller.recreate();activity=controller.get();
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
}
