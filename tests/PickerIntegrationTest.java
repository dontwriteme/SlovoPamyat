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
    void tap(int v)throws Exception{call("selectPickerTextVerse",new Class<?>[]{int.class},v);}
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
        selection().active=true;tap(3);chapter(6);assertTrue(selection().active);chapter(7);tap(29);
        assertEquals(109,bible.passage(40,5,3,7,29).size());
        assertEquals("Добавить 109 стихов",((TextView)field("pickerSaveButton")).getText().toString());
        invoke("saveTextSelection");assertEquals("COLLECTION_DETAIL",field("screen").toString());
        assertEquals(1,store.entries(group).size());assertEquals(109,store.entries(group).get(0).lines().size());
        assertEquals(109,store.flashcards(store.unifiedForGroup(group).deckId).size());
        chapter(5);
        @SuppressWarnings("unchecked") List<View> buttons=(List<View>)field("pickerQuickButtons");
        TextView covered=(TextView)buttons.get(2);assertEquals("✓",covered.getText().toString());
        covered.performClick();assertEquals(1,store.entries(group).size());
    }
    @Test public void overlapDuplicateAndDeletedCollection()throws Exception {
        store.addPickedPassage(group,ref(5,3,5,3),bible.passage(40,5,3,5,3),bible);
        selection().active=true;tap(3);tap(12);invoke("saveTextSelection");
        assertEquals(2,store.entries(group).size());
        assertEquals(10,store.flashcards(store.unifiedForGroup(group).deckId).size());
        assertFalse((Boolean)call("savePickedPassageFor",new Class<?>[]{int.class,int.class,int.class,int.class,int.class},40,5,3,5,12));
        assertEquals(2,store.entries(group).size());
        store.deleteUnifiedCollection(store.unifiedForGroup(group).id);
        assertFalse((Boolean)call("savePickedPassageFor",new Class<?>[]{int.class,int.class,int.class,int.class,int.class},40,5,13,5,15));
        assertEquals(2,store.entries(group).size());
    }
    @Test public void reversedBoundsEditingAndCancel()throws Exception {
        selection().active=true;tap(12);tap(3);
        assertArrayEquals(new int[]{5,3,5,12},selection().bounds());
        ScrollView scroll=(ScrollView)field("currentScroller");tap(14);
        assertEquals(14,selection().endVerse);selection().changingStart=true;tap(4);
        assertEquals(4,selection().startVerse);assertSame(scroll,field("currentScroller"));
        selection().cancel();invoke("updatePickerSelection");
        assertFalse(selection().active);assertEquals(View.GONE,((View)field("pickerSelectionPanel")).getVisibility());
    }
    @Test public void recreateKeepsSelectionAndCollection()throws Exception {
        selection().active=true;tap(3);chapter(7);tap(29);
        controller.recreate();activity=controller.get();
        ((Bible)field("bible")).load(activity);invoke("render");
        assertEquals(group,field("pickerTargetId"));assertEquals("PICKER_TEXT",field("screen").toString());
        assertTrue(selection().active);assertArrayEquals(new int[]{5,3,7,29},selection().bounds());
    }
    @Test public void atomicRollbackWhenCardSyncFails()throws Exception {
        store.getWritableDatabase().execSQL("CREATE TRIGGER fail_picker_card BEFORE INSERT ON card_deck_members BEGIN SELECT RAISE(ABORT,'test failure'); END");
        try {store.addPickedPassage(group,ref(5,3,5,12),bible.passage(40,5,3,5,12),bible);fail("Expected rollback");}
        catch(Exception expected){}
        assertTrue(store.entries(group).isEmpty());
        assertTrue(store.flashcards(store.unifiedForGroup(group).deckId).isEmpty());
    }
}
