package org.biblememory.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class ReviewIdentityTest {
    private static final long DAY=86_400_000L;
    private final Bible bible=new Bible();
    private Context context;
    private LocalStore store;
    private ActivityController<MainActivity> controller;

    @Before public void prepare()throws Exception{
        context=RuntimeEnvironment.getApplication();
        bible.load(context);
        store=new LocalStore(context,bible);
    }

    @After public void close()throws Exception{
        if(controller!=null)ActivityTestWorkers.destroy(controller);
        store.close();
    }

    private LocalStore.UnifiedCollection collection(String title){
        return store.unifiedCollection(store.createUnifiedCollection(title));
    }
    private long add(long group,int book,int chapter,int first,int last){
        String ref=Bible.reference(book,chapter,first,chapter,last);
        return store.add(group,ref,ref,bible.passage(book,chapter,first,chapter,last));
    }
    private void successful(long id,int position,long now){
        store.commitSuccessfulRecall(id,"VERSE",position,position+1,false,true,now);
    }
    private void legacyTerm(long id,int position,int level,long due){
        store.getWritableDatabase().execSQL("INSERT OR REPLACE INTO review_schedule "+
            "(entry_id,stage,first_line,last_line,level,due,last_score) VALUES(?,'VERSE',?,?,?,?,100)",
            new Object[]{id,position,position+1,level,due});
        store.markMastered(id,position,position+1,100,"VERSE");
    }
    private long number(String table,String field,String condition){
        try(Cursor cursor=store.getReadableDatabase().rawQuery(
                "SELECT "+field+" FROM "+table+" WHERE "+condition,null)){
            assertTrue(cursor.moveToFirst());return cursor.getLong(0);
        }
    }
    private void verifyTerm(long entry,int pos,int level,long due){
        String key="entry_id="+entry+" AND stage='VERSE' AND first_line="+pos;
        assertEquals(level,number("review_schedule","level",key));
        assertEquals(due,number("review_schedule","due",key));
    }

    @Test public void conflictedCopiesUseEarliestTermAndRecordOnlyTheAnsweredAttempt(){
        LocalStore.UnifiedCollection first=collection("First"),second=collection("Second");
        long a=add(first.groupId,1,1,2,2),b=add(second.groupId,1,1,1,3);
        legacyTerm(a,0,4,5*DAY);
        legacyTerm(b,1,0,5*DAY);
        assertEquals(0,store.dueCount(5*DAY-1));
        assertEquals(1,store.dueCount(5*DAY));
        assertEquals(a,store.dueItems(5*DAY,first.groupId).get(0).entryId);
        LocalStore.DueItem inSecond=store.dueItems(5*DAY,second.groupId).get(0);
        assertEquals(b,inSecond.entryId);assertEquals(1,inSecond.start);
        assertTrue(store.belongsToGroupMaterial(first.groupId,inSecond));
        successful(a,0,5*DAY);
        verifyTerm(a,0,1,8*DAY);verifyTerm(b,1,1,8*DAY);
        assertEquals(0,store.dueCount(5*DAY));
        assertEquals(1,number("review_attempts","count(*)","entry_id="+a));
        assertEquals(0,number("review_attempts","count(*)","entry_id="+b));
        store.commitFailedRecall(b,"VERSE",1,2,35,true,null,8*DAY);
        verifyTerm(a,0,0,9*DAY);verifyTerm(b,1,0,9*DAY);
        assertEquals(0,store.dueCount(8*DAY));
        assertEquals(1,store.dueCount(9*DAY));
        assertEquals(1,number("review_attempts","count(*)","entry_id="+b));
        assertFalse(store.isStepMastered(b,"VERSE",0,1));
    }

    @Test public void existingEarlierDueWinsEvenWithLowerLevelOnAnotherCopy(){
        long a=add(collection("Earliest").groupId,1,1,1,1);
        long b=add(collection("Lower").groupId,1,1,1,1);
        legacyTerm(a,0,2,7*DAY);legacyTerm(b,0,0,9*DAY);
        assertEquals(7*DAY,store.dueItems(7*DAY).get(0).due);
        successful(b,0,7*DAY);
        verifyTerm(a,0,3,21*DAY);verifyTerm(b,0,3,21*DAY);
    }

    @Test public void deletingCollectionsKeepsEvidenceAndReattachesWithThePreviousTerm(){
        LocalStore.UnifiedCollection first=collection("First"),second=collection("Second");
        long a=add(first.groupId,1,1,2,2);
        long b=add(second.groupId,1,1,1,3);
        successful(a,0,DAY);
        store.deleteUnifiedCollection(first.id);
        assertEquals(b,store.dueItems(2*DAY).get(0).entryId);
        assertEquals(1,store.dueItems(2*DAY,second.groupId).get(0).start);
        assertTrue(store.dueItems(2*DAY,first.groupId).isEmpty());
        store.deleteUnifiedCollection(second.id);
        assertEquals(0,store.dueCount(2*DAY));
        assertNotNull(store.entry(a));assertNotNull(store.entry(b));
        assertEquals(1,number("review_attempts","count(*)","entry_id="+a));
        verifyTerm(a,0,0,2*DAY);
        long group=collection("Restored elsewhere").groupId;
        assertEquals(a,store.restoreOrAddStudyPassage(group,"Бытие 1:2",bible.passage(1,1,2,1,2)));
        assertEquals(a,store.dueItems(2*DAY,group).get(0).entryId);
        assertEquals(2*DAY,store.dueItems(2*DAY).get(0).due);
    }

    @Test public void distinctBooksAndCustomMaterialsKeepIndependentSchedules(){
        long group=store.newGroup("Independent study");
        long exodus=add(group,2,14,1,1),leviticus=add(group,3,14,1,1);
        String text=store.entry(exodus).lines().get(0).text;
        assertEquals(text,store.entry(leviticus).lines().get(0).text);
        long custom=store.add(group,"User","Исход 14:1",Collections.singletonList(new Bible.Verse("1",text)));
        long anotherCustom=store.add(group,"Another user text","Исход 14:1",
            Collections.singletonList(new Bible.Verse("1",text)));
        long edited=store.add(group,"Variant","Исход 14:1",Collections.singletonList(new Bible.Verse("14:1",text+" Изменено")));
        for(long id:new long[]{exodus,leviticus,custom,edited})successful(id,0,DAY);
        assertEquals(4,store.dueCount(2*DAY));
        assertFalse(store.isStepMastered(anotherCustom,"VERSE",0,1));
        successful(anotherCustom,0,DAY);
        assertEquals(5,store.dueCount(2*DAY));
        long cardDeck=collection("Card deck").deckId;
        store.addCustomFlashcard(cardDeck,"Исход 14:1",text);
        store.removeFlashcard(cardDeck,store.flashcards(cardDeck).get(0).id);
        assertEquals(5,store.dueCount(2*DAY));
        successful(exodus,0,2*DAY);
        assertEquals(4,store.dueCount(2*DAY));
        store.deleteGroup(group);
        assertEquals(0,store.dueCount(2*DAY));
        assertNotNull(store.entry(exodus));
    }

    @Test public void overlappingPassagesKeepGroupExercisesAndVerseRunsSeparate(){
        long shortEntry=add(collection("Single").groupId,1,1,2,2);
        long passage=add(collection("Passage").groupId,1,1,1,3);
        successful(shortEntry,0,DAY);
        successful(passage,0,DAY);
        successful(passage,2,DAY);
        store.commitSuccessfulRecall(passage,"CHAIN",0,2,false,false,DAY);
        assertEquals(4,store.dueCount(2*DAY));
        store.commitFailedRecall(passage,"VERSE_RUN",0,3,35,false,
            new WeakTransitionLogic.WeakSpot(1,0,2),2*DAY);
        verifyTerm(shortEntry,0,0,3*DAY);verifyTerm(passage,1,0,3*DAY);
        assertEquals(3,store.dueCount(2*DAY));
        assertEquals(1,number("review_attempts","count(*)","entry_id="+shortEntry));
        assertEquals(0,number("review_attempts","count(*)","stage='VERSE_RUN'"));
        assertEquals(2*DAY,number("review_schedule","due","stage='CHAIN' AND entry_id="+passage));
        store.commitSuccessfulRecall(passage,"CHAIN",0,3,true,false,3*DAY);
        verifyTerm(shortEntry,0,1,6*DAY);verifyTerm(passage,1,1,6*DAY);
        assertEquals(0,number("review_schedule","count(*)","stage='VERSE_RUN'"));
        assertEquals(2*DAY,number("review_schedule","due","stage='CHAIN' AND entry_id="+passage));
    }

    @Test public void sqliteFailureOnAnotherAliasRollsBackTheWholeAttempt(){
        long a=add(collection("A").groupId,1,1,1,1),b=add(collection("B").groupId,1,1,1,1);
        legacyTerm(a,0,0,DAY);legacyTerm(b,0,0,DAY);
        store.getWritableDatabase().execSQL("CREATE TRIGGER refuse_alias BEFORE INSERT ON review_schedule "+
            "WHEN NEW.entry_id="+a+" BEGIN SELECT RAISE(ABORT,'test failure'); END");
        assertThrows(SQLiteException.class,()->successful(b,0,DAY));
        assertThrows(SQLiteException.class,()->store.commitFailedRecall(b,"VERSE",0,1,35,true,null,DAY));
        verifyTerm(a,0,0,DAY);verifyTerm(b,0,0,DAY);
        assertEquals(0,number("review_attempts","count(*)","1"));
        assertEquals(0,number("recall_activity","count(*)","1"));
    }

    @Test public void backupRoundTripPreservesArchivedTermsAndVisibility()throws Exception{
        LocalStore.UnifiedCollection first=collection("Archive"),second=collection("Live");
        long a=add(first.groupId,1,1,2,2),b=add(second.groupId,1,1,1,3);
        successful(a,0,DAY);
        store.deleteUnifiedCollection(first.id);
        File archive=File.createTempFile("review-identity",".svbackup",context.getCacheDir());
        Uri uri=Uri.fromFile(archive);
        SvetochBackup.exportTo(context,store,uri);
        store.deleteUnifiedCollection(second.id);
        assertEquals(0,store.dueCount(2*DAY));
        SvetochBackup.restoreFrom(context,store,uri);
        assertEquals(9,store.getReadableDatabase().getVersion());
        assertEquals(b,store.dueItems(2*DAY).get(0).entryId);
        assertEquals(1,number("review_attempts","count(*)","entry_id="+a));
        assertTrue(store.dueItems(2*DAY,first.groupId).isEmpty());
    }

    private void start(MainActivity activity,long group)throws Exception{
        Method start=MainActivity.class.getDeclaredMethod("startReviewSession",long.class);
        start.setAccessible(true);
        start.invoke(activity,group);
    }
    private ReviewSession review(MainActivity activity)throws Exception{
        return (ReviewSession)field(activity,"reviewSession");
    }
    private List<?> queue(MainActivity activity)throws Exception{
        Field queue=ReviewSession.class.getDeclaredField("queue");
        queue.setAccessible(true);
        return (List<?>)queue.get(review(activity));
    }
    private void invoke(MainActivity activity,String name)throws Exception{
        Method method=MainActivity.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(activity);
    }
    private Object field(MainActivity activity,String name)throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);
    }

    @Test public void pendingDraftMovesToActiveAliasButSuccessfulReviewDiscardsStalePending()throws Exception{
        LocalStore.UnifiedCollection first=collection("First"),second=collection("Second");
        long a=add(first.groupId,1,1,2,2),b=add(second.groupId,1,1,1,3);
        legacyTerm(a,0,0,DAY);
        controller=Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity=controller.get();ActivityTestWorkers.awaitStartup(activity);
        start(activity,first.groupId);
        ((LessonWritingState)field(activity,"lessonWriting")).draft="Начатый ответ";
        invoke(activity,"saveTrainingSession");
        store.deleteUnifiedCollection(first.id);
        ActivityTestWorkers.recreate(controller);
        MainActivity recreated=controller.get();
        start(recreated,second.groupId);
        LocalStore.DueItem current=review(recreated).current().item();
        assertEquals(b,current.entryId);assertEquals(1,current.start);
        assertEquals("Начатый ответ",((LessonWritingState)field(recreated,"lessonWriting")).draft);
        assertEquals(1,queue(recreated).size());
        successful(b,1,System.currentTimeMillis());
        start(recreated,-1);
        assertEquals("DUE",field(recreated,"screen").toString());
        SharedPreferences prefs=context.getSharedPreferences("study_session",0);
        assertFalse(prefs.getBoolean("review_pending",false));
    }

    @Test public void partiallyAnsweredQueuedRunKeepsItsOtherDueVerses()throws Exception{
        LocalStore.UnifiedCollection first=collection("Run"),second=collection("Alias");
        long introduction=add(first.groupId,2,14,1,1);
        long passage=add(first.groupId,1,1,2,3),alias=add(second.groupId,1,1,2,2);
        legacyTerm(introduction,0,0,DAY);
        legacyTerm(passage,0,0,2*DAY);legacyTerm(passage,1,0,2*DAY);
        controller=Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity=controller.get();ActivityTestWorkers.awaitStartup(activity);
        start(activity,-1);
        List<?> planned=queue(activity);
        assertEquals(2,planned.size());
        assertEquals("VERSE_RUN",((LocalStore.DueItem)planned.get(1)).stage);
        successful(alias,0,System.currentTimeMillis());
        invoke(activity,"advanceReviewSession");
        LocalStore.DueItem current=review(activity).current().item();
        assertEquals("VERSE",current.stage);assertEquals(1,current.start);assertEquals(passage,current.entryId);
    }

    @Test public void advancingSkipsAQueuedVerseAlreadyAnsweredThroughAnotherCopy()throws Exception{
        LocalStore.UnifiedCollection a=collection("A"),b=collection("B");
        long first=add(a.groupId,1,1,1,1);
        long second=add(a.groupId,1,1,3,3);
        long alias=add(b.groupId,1,1,3,3);
        legacyTerm(first,0,0,DAY);legacyTerm(second,0,0,2*DAY);
        controller=Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity=controller.get();ActivityTestWorkers.awaitStartup(activity);
        start(activity,-1);
        assertEquals(2,queue(activity).size());
        assertEquals(first,review(activity).current().item().entryId);
        successful(alias,0,System.currentTimeMillis());
        invoke(activity,"advanceReviewSession");
        assertEquals("HOME",field(activity,"screen").toString());
    }
}
