package org.biblememory.app;

import android.content.SharedPreferences;
import android.database.*;
import android.database.sqlite.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class CollectionRemovalTest {
    LocalStore store;long opened;static final long NOW=1700000000000L;
    @Before public void open(){store=new LocalStore(RuntimeEnvironment.getApplication());}
    @After public void close(){store.close();}
    LocalStore.UnifiedCollection collection(String title){return store.unifiedCollection(store.createUnifiedCollection(title));}
    List<Bible.Verse> lines(int begin,int end){List<Bible.Verse> out=new ArrayList<>();for(int i=begin;i<=end;i++)out.add(new Bible.Verse("5:"+i,"Слово "+i));return out;}
    long passage(LocalStore.UnifiedCollection c,int begin,int end){return store.restoreOrAddStudyPassage(c.groupId,"Матфея 5:"+begin+(end==begin?"":"–"+end),lines(begin,end));}
    String tableRows(Cursor c){
        org.json.JSONArray rows=new org.json.JSONArray();
        while(c.moveToNext()){
            org.json.JSONArray row=new org.json.JSONArray();
            for(int i=0;i<c.getColumnCount();i++)row.put(c.isNull(i)?org.json.JSONObject.NULL:c.getString(i));
            rows.put(row);
        }return rows.toString();
    }
    Map<String,String> history(){Map<String,String> rows=new TreeMap<>();for(String table:new String[]{"lesson_progress","verse_progress","stage_progress","recall_activity","review_attempts","review_schedule","transition_issues"})
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT * FROM "+table+" ORDER BY rowid",null)){rows.put(table,tableRows(c));}return rows;}
    Map<String,String> allRows(){Map<String,String> rows=history();for(String table:new String[]{"entries","groups_","unified_collections","card_decks","card_deck_members","unified_entry_cards","unified_card_entries","study_order","card_order"})
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT * FROM "+table+" ORDER BY rowid",null)){rows.put(table,tableRows(c));}return rows;}
    long count(String table){try(Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getLong(0);}}
    void seed(long id,int completed){if(completed>0)store.finishExercise(id,0,completed);if(completed==5){store.markMastered(id,0,1,100,"VERSE");store.recordRecall(id,"VERSE",0,1,100,false,NOW);store.scheduleReview(id,"VERSE",0,1,100,false,NOW);}}
    SharedPreferences prefs(String name){return RuntimeEnvironment.getApplication().getSharedPreferences(name,0);}
    CollectionController actions(){return new CollectionController(new CollectionController.Ports(){
        public StudyRepository study(){return store.study();}public ProgressRepository progress(){return store.progress();}
        public FlashcardRepository flashcards(){return store.flashcards();}public CollectionRepository collections(){return store.collections();}
        public SharedPreferences activePreferences(){return prefs("active");}public SharedPreferences queuePreferences(){return prefs("queue");}
        public Bible bible(){return new Bible();}public boolean studyPicker(){return false;}public long pickerTargetId(){return -1;}
        public int readerBook(){return 40;}public void stopCardStudy(){}public boolean hasPendingGroup(LocalStore.Entry e){return false;}
        public void openTraining(long id){opened=id;}public void navigate(Screen screen){}public void toast(String message){}
        public void clearRunHistory(long id){}public boolean stopped(){return false;}
    });}
    @Test public void removalKeepsUnstudiedPartialAndMasteredHistory(){
        for(int completed:new int[]{0,3,5}){
            LocalStore.UnifiedCollection c=collection("Level "+completed);long id=passage(c,completed+3,completed+3);seed(id,completed);
            store.syncUnifiedCollection(c.id,null);Map<String,String> before=history();
            assertTrue(store.collections().removeStudyEntry(id));assertEquals(before,history());
            assertTrue(store.entries(c.groupId).isEmpty());assertTrue(store.flashcards(c.deckId).isEmpty());
            assertEquals(completed,store.exerciseDone(id,0));assertFalse(store.collections().activeMaterial(new LocalStore.DueItem(id,"VERSE",0,1,0)));
            assertFalse(store.collections().removeStudyEntry(id));assertEquals(before,history());
        }
        assertTrue(store.dueItems(Long.MAX_VALUE).isEmpty());
    }
    @Test public void deletionOfLastMembershipPreservesHistoryAcrossRestartAndReadd(){
        LocalStore.UnifiedCollection c=collection("Saved");long id=passage(c,3,3);seed(id,5);Map<String,String> before=history();
        store.deleteUnifiedCollection(c.id);assertEquals(before,history());assertNull(store.unifiedCollection(c.id));assertNull(store.group(c.groupId));assertNull(store.cardDeck(c.deckId));
        assertEquals(0,count("unified_entry_cards"));assertTrue(store.dueItems(Long.MAX_VALUE).isEmpty());
        store.close();store=new LocalStore(RuntimeEnvironment.getApplication());assertEquals(before,history());
        LocalStore.UnifiedCollection next=collection("Again");assertEquals(id,passage(next,3,3));
        assertEquals(before,history());assertEquals(1,store.dueItems(Long.MAX_VALUE).size());
    }
    @Test public void survivingCollectionKeepsDueHistoryWithoutDuplicateQueueEntries(){
        LocalStore.UnifiedCollection a=collection("A"),b=collection("B");long first=passage(a,3,3),second=passage(b,3,3);seed(first,5);
        assertEquals(first,store.canonicalStudyEntryId(second));store.scheduleReview(second,"VERSE",0,1,100,false,NOW);
        assertEquals(1,store.dueItems(Long.MAX_VALUE).size());Map<String,String> before=history();
        store.deleteUnifiedCollection(a.id);assertEquals(before,history());assertEquals(1,store.dueItems(Long.MAX_VALUE).size());
        assertEquals(5,store.exerciseDone(second,0));assertEquals(1,store.entries(b.groupId).size());
    }
    @Test public void clearStaysSelectedEmptyAndInvalidatesQueue(){
        CollectionController actions=actions();LocalStore.UnifiedCollection a=actions.createUnifiedCollection("A"),b=actions.createUnifiedCollection("B");
        long id=passage(a,3,3);seed(id,3);actions.startStudyCollection(a.groupId);assertTrue(actions.queueValid());
        Map<String,String> before=history();actions.clearUnifiedCollection(a.id);assertEquals(before,history());
        assertEquals(a.id,actions.activeUnified().id);assertTrue(store.entries(a.groupId).isEmpty());assertFalse(actions.resumeCollectionTraining());
        assertEquals(2,store.unifiedCollections().size());assertNotNull(store.unifiedCollection(b.id));
    }
    @Test public void deleteSelectedDoesNotChooseAnotherAndRestartKeepsExplicitNoSelection(){
        CollectionController actions=actions();LocalStore.UnifiedCollection a=actions.createUnifiedCollection("A"),b=actions.createUnifiedCollection("B");
        actions.activateUnified(a.id);actions.deleteUnifiedCollection(a.id);assertNull(actions.activeUnified());assertEquals(-1,prefs("active").getLong("active",0));
        CollectionController restored=actions();restored.activeUnifiedId=prefs("active").getLong("active",-1);assertNull(restored.activeUnified());
        restored.activateUnified(b.id);restored.deleteUnifiedCollection(b.id);assertNull(restored.activeUnified());assertTrue(store.unifiedCollections().isEmpty());
    }
    @Test public void deleteNonSelectedPreservesSelectionAndOtherContents(){
        CollectionController actions=actions();LocalStore.UnifiedCollection a=actions.createUnifiedCollection("A"),b=actions.createUnifiedCollection("B");long id=passage(a,3,3);seed(id,3);
        actions.deleteUnifiedCollection(b.id);assertEquals(a.id,actions.activeUnified().id);assertEquals(id,store.entries(a.groupId).get(0).id);assertEquals(3,store.exerciseDone(id,0));
    }
    @Test public void staleLessonCannotCommitAfterDeletionEvenWhenMaterialSurvivesElsewhere(){
        LocalStore.UnifiedCollection a=collection("A"),b=collection("B");long id=passage(a,3,3);passage(b,3,3);LocalStore.Entry entry=store.entry(id);
        store.deleteUnifiedCollection(a.id);Map<String,String> before=history();
        try{new StudyProgressRecorder(store.progress(),a.groupId).success(entry,"VERSE",new StudyPlanLogic.Step(0,1,"5:3"),false,false,false,NOW);fail();}catch(IllegalStateException expected){}
        assertEquals(before,history());assertTrue(store.collections().activeMaterial(new LocalStore.DueItem(id,"VERSE",0,1,0)));
    }
    @Test public void savedQueueCannotResumeArchivedRows(){
        CollectionController actions=actions();LocalStore.UnifiedCollection a=actions.createUnifiedCollection("A");long id=passage(a,3,3);seed(id,3);
        actions.startStudyCollection(a.groupId);store.clearUnifiedCollection(a.id);
        CollectionController restored=actions();restored.restoreCollectionQueue();assertFalse(restored.resumeCollectionTraining());assertTrue(restored.collectionQueue.isEmpty());
    }
    @Test public void longerReaddedPassageKeepsCommonVerseProgressButNotNewGroupMastery(){
        LocalStore.UnifiedCollection a=collection("A");long id=passage(a,3,27);store.finishExercise(id,0,3);store.markMastered(id,1,2,100,"VERSE");store.markMastered(id,0,25,100,"CHAIN");
        store.deleteUnifiedCollection(a.id);Map<String,String> before=history();LocalStore.UnifiedCollection b=collection("B");long expanded=passage(b,3,32);
        assertEquals(before,history());assertEquals(3,store.exerciseDone(expanded,0));assertEquals(5,store.exerciseDone(expanded,1));assertEquals(0,store.exerciseDone(expanded,25));
        assertFalse(store.isStepMastered(expanded,"CHAIN",0,30));
    }
    @Test public void partialOverlapOnlyKeepsEligibleSourceVersesInReviews(){
        LocalStore.UnifiedCollection a=collection("A"),b=collection("B");long id=passage(a,3,12);passage(b,8,16);
        for(int i=0;i<10;i++){store.markMastered(id,i,i+1,100,"VERSE");store.scheduleReview(id,"VERSE",i,i+1,100,false,NOW);}
        Map<String,String> before=history();store.deleteUnifiedCollection(a.id);assertEquals(before,history());
        List<LocalStore.DueItem> due=store.dueItems(Long.MAX_VALUE);assertEquals(5,due.size());for(LocalStore.DueItem item:due)assertTrue(item.start>=5);
    }
    @Test public void userTextsWithEqualContentNeverInheritArchivedIdentity(){
        LocalStore.UnifiedCollection a=collection("A");List<Bible.Verse> text=Collections.singletonList(new Bible.Verse("1:1","Одинаковый текст"));
        long id=store.add(a.groupId,"Свое","Свое",text);seed(id,5);store.deleteUnifiedCollection(a.id);LocalStore.UnifiedCollection b=collection("B");
        long fresh=store.restoreOrAddStudyPassage(b.groupId,"Свое",text);assertNotEquals(id,fresh);assertEquals(0,store.exerciseDone(fresh,0));assertTrue(store.dueItems(Long.MAX_VALUE).isEmpty());
    }
    @Test public void anotherBookWithSameVerseRefAndWordsIsNotTheSameIdentity(){
        LocalStore.UnifiedCollection a=collection("A");long id=passage(a,3,3);seed(id,5);
        long other=store.add(a.groupId,"Luke","Луки 5:3",lines(3,3));assertEquals(0,store.exerciseDone(other,0));assertEquals(0,store.masteryCount(other));
    }
    @Test public void failedClearRollsBackAllLinksOrderAndHistory(){
        LocalStore.UnifiedCollection a=collection("A");long id=passage(a,3,3);seed(id,5);store.syncUnifiedCollection(a.id,null);Map<String,String> before=allRows();
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_clear BEFORE DELETE ON card_deck_members BEGIN SELECT RAISE(ABORT,'injected-clear'); END");
        try{store.clearUnifiedCollection(a.id);fail();}catch(SQLiteException expected){}assertEquals(before,allRows());
    }
    @Test public void failedDeleteRollsBackArchivalAndCollectionContainers(){
        LocalStore.UnifiedCollection a=collection("A");long id=passage(a,3,3);seed(id,5);Map<String,String> before=allRows();
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_delete BEFORE DELETE ON groups_ BEGIN SELECT RAISE(ABORT,'injected-delete'); END");
        try{store.deleteUnifiedCollection(a.id);fail();}catch(SQLiteException expected){}assertEquals(before,allRows());
    }
    @Test public void overlappingPassagesWriteOneAuthoritativeVerseSchedule(){
        LocalStore.UnifiedCollection a=collection("A"),b=collection("B");long old=passage(a,3,12),expanded=passage(b,8,16);
        store.markMastered(old,5,6,100,"VERSE");store.scheduleReview(old,"VERSE",5,6,100,false,NOW);
        new StudyProgressRecorder(store.progress(),b.groupId).success(store.entry(expanded),"VERSE",new StudyPlanLogic.Step(0,1,"5:8"),false,false,false,NOW+86400000L);
        assertEquals(1,count("review_schedule"));assertEquals(2,count("review_attempts"));
        assertTrue(store.dueItems(NOW+86400000L).isEmpty());assertEquals(1,store.dueItems(Long.MAX_VALUE).size());
    }
    @Test public void staleMilestoneIsRejectedWithoutWritingProgress(){
        LocalStore.UnifiedCollection a=collection("A");long id=passage(a,3,3);store.clearUnifiedCollection(a.id);
        Map<String,String> before=history();
        try{store.progress().finishExerciseForSession(id,0,1,a.groupId);fail();}catch(IllegalStateException expected){}
        assertEquals(before,history());
    }
    @Test public void legacyCardLessonKeepsItsDeckContextAndStopsAfterRemoval(){
        long deck=store.newCardDeck("Cards");long card=store.addCustomFlashcard(deck,"Own card","Слово истины");
        LocalStore.Flashcard material=store.flashcards(deck).get(0);
        long id=store.findOrCreateCardStudyEntry(material,Collections.singletonList(new Bible.Verse("1:1",material.content)));
        long context=-deck-2;StudyPlanLogic.Step step=new StudyPlanLogic.Step(0,1,"1:1");
        new StudyProgressRecorder(store.progress(),context).success(store.entry(id),"VERSE",step,false,false,false,NOW);
        assertEquals(5,store.exerciseDone(id,0));assertTrue(store.dueItems(Long.MAX_VALUE).isEmpty());
        store.removeFlashcard(deck,card);Map<String,String> before=history();
        try{new StudyProgressRecorder(store.progress(),context).success(store.entry(id),"VERSE",step,false,false,false,NOW+1);fail();}catch(IllegalStateException expected){}
        assertEquals(before,history());
    }

    @Test public void previouslyDeletedCollectionOrphansAreNotActiveAndCanBeReattached(){
        LocalStore.UnifiedCollection a=collection("Old version");long id=passage(a,3,3);seed(id,5);Map<String,String> before=history();
        // Reproduce the previous implementation: only the unified row was removed.
        store.getWritableDatabase().delete("unified_collections","id=?",new String[]{String.valueOf(a.id)});
        assertNull(store.group(a.groupId));assertTrue(store.dueItems(Long.MAX_VALUE).isEmpty());assertEquals(before,history());
        LocalStore.UnifiedCollection b=collection("Restored");assertEquals(id,passage(b,3,3));assertEquals(before,history());
        assertEquals(1,store.dueItems(Long.MAX_VALUE).size());
    }

    @Test public void customCardCannotGuessAnArchivedUserTextByContent(){
        LocalStore.UnifiedCollection a=collection("Own");List<Bible.Verse> text=Collections.singletonList(new Bible.Verse("1:1","Слово истины"));
        long old=store.study().addUserMaterial(a.groupId,"Own","Own",text);seed(old,5);store.deleteUnifiedCollection(a.id);
        long deck=store.newCardDeck("Cards");store.addCustomFlashcard(deck,"Own","Слово истины");LocalStore.Flashcard card=store.flashcards(deck).get(0);
        long fresh=store.findOrCreateCardStudyEntry(card,text);assertNotEquals(old,fresh);assertEquals(0,store.exerciseDone(fresh,0));
        assertEquals(fresh,store.findOrCreateCardStudyEntry(card,text));assertEquals(5,store.exerciseDone(old,0));
    }
    @Test public void userMaterialWithABibleHeadingRetainsIndependentIdentity(){
        LocalStore.UnifiedCollection a=collection("A");long canonical=passage(a,3,3);seed(canonical,5);
        long custom=store.study().addUserMaterial(a.groupId,"Own","Матфея 5:3",lines(3,3));
        assertEquals(0,store.exerciseDone(custom,0));assertEquals(custom,store.canonicalStudyEntryId(custom));
    }

    @Test public void resynchronizationDoesNotResurrectRemovedOverlapsOrLegacyRangeCards()throws Exception{
        Bible bible=new Bible();bible.load(RuntimeEnvironment.getApplication());LocalStore.UnifiedCollection a=collection("Overlap");
        long first=store.addPickedPassage(a.groupId,"Матфея 5:3–4",bible.passage(40,5,3,5,4),bible);
        long remaining=store.addPickedPassage(a.groupId,"Матфея 5:4–5",bible.passage(40,5,4,5,5),bible);
        store.collections().removeStudyEntry(first);store.syncUnifiedCollection(a.id,bible);
        assertEquals(1,store.entries(a.groupId).size());assertEquals(remaining,store.entries(a.groupId).get(0).id);assertEquals(2,store.flashcards(a.deckId).size());
        LocalStore.UnifiedCollection legacy=collection("Legacy range");
        store.addFlashcard(legacy.deckId,40,5,3,5,4,"Матфея 5:3–4",PassageText.join(bible.passage(40,5,3,5,4)));
        store.syncUnifiedCollection(legacy.id,bible);assertEquals(1,store.entries(legacy.groupId).size());
        store.collections().removeStudyEntry(store.entries(legacy.groupId).get(0).id);store.syncUnifiedCollection(legacy.id,bible);
        assertTrue(store.entries(legacy.groupId).isEmpty());assertTrue(store.flashcards(legacy.deckId).isEmpty());
    }

    @Test public void backupRestoreKeepsArchivedHistoryOutOfActiveCollections()throws Exception{
        LocalStore.UnifiedCollection a=collection("Archived");long id=passage(a,3,3);seed(id,5);store.deleteUnifiedCollection(a.id);
        String backup=store.exportJson().toString();store.importJson(backup);
        assertTrue(store.unifiedCollections().isEmpty());assertTrue(store.dueItems(Long.MAX_VALUE).isEmpty());
        assertEquals(2,count("review_attempts"));assertEquals(2,store.allEntries().size());
        LocalStore.UnifiedCollection b=collection("Live");long restored=passage(b,3,3);assertEquals(5,store.exerciseDone(restored,0));
        assertEquals(1,store.dueItems(Long.MAX_VALUE).size());
    }
    @Test public void importedCardIdsAreNotReboundToAnUnrelatedCurrentCard()throws Exception{
        long deck=store.newCardDeck("Cards");store.addCustomFlashcard(deck,"Own","Слово истины");LocalStore.Flashcard card=store.flashcards(deck).get(0);
        long original=store.findOrCreateCardStudyEntry(card,Collections.singletonList(new Bible.Verse("1:1",card.content)));
        store.importJson(store.exportJson().toString());
        for(LocalStore.Entry e:store.allEntries())if(e.id!=original)
            assertFalse(new org.json.JSONArray(e.linesJson).getJSONObject(0).has("study_card_id"));
    }

    @Test public void userCollectionNameCannotAliasThePrivateArchive(){
        LocalStore.UnifiedCollection named=collection(LocalStore.CARD_STUDY_GROUP);long id=passage(named,3,3);seed(id,5);
        store.clearUnifiedCollection(named.id);assertTrue(store.entries(named.groupId).isEmpty());
        assertNotEquals(named.groupId,store.entry(id).groupId);assertTrue(store.dueItems(Long.MAX_VALUE).isEmpty());
        assertNotNull(store.group(named.groupId));assertEquals(5,store.exerciseDone(id,0));
    }

    @Test public void activitySnapshotCannotResurrectADeletedCollectionQueue(){
        CollectionController first=actions();LocalStore.UnifiedCollection a=first.createUnifiedCollection("A");passage(a,3,3);first.startStudyCollection(a.groupId);
        android.os.Bundle snapshot=first.saveState();store.deleteUnifiedCollection(a.id);
        CollectionController restored=actions();restored.restoreState(snapshot);
        assertTrue(restored.collectionQueue.isEmpty());assertFalse(restored.executingCollectionQueue);
        assertEquals(-1,restored.collectionQueueGroup);assertNull(restored.activeUnified());
    }

    @Test public void anotherCustomCardWithEqualTextCannotKeepADeletedCardSessionAlive(){
        long deck=store.newCardDeck("Cards");long source=store.addCustomFlashcard(deck,"Own","Слово истины");
        store.addCustomFlashcard(deck,"Own","Слово истины");LocalStore.Flashcard card=null;
        for(LocalStore.Flashcard item:store.flashcards(deck))if(item.id==source)card=item;
        assertNotNull(card);long id=store.findOrCreateCardStudyEntry(card,Collections.singletonList(new Bible.Verse("1:1",card.content)));
        store.removeFlashcard(deck,source);assertFalse(store.collections().validSession(id,"VERSE",0,1,-deck-2));
        assertEquals(1,store.flashcards(deck).size());
    }

}
