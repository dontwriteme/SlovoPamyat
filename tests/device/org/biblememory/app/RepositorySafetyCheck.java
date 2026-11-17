package org.biblememory.app;

import android.app.*;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.os.Bundle;
import java.util.*;

/** Transaction fault injection against the real SQLite engine and the extracted repositories. */
public final class RepositorySafetyCheck extends Instrumentation {
    interface Scenario{void run(SafetySandbox context)throws Exception;}
    int passed,failed;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    void report(String text){Bundle output=new Bundle();output.putString("stream",text+"\n");sendStatus(0,output);}
    static void require(boolean value,String message){StorageSafetyCheck.require(value,message);}
    static Map<String,String> rows(LocalStore store){
        Map<String,String> result=new TreeMap<>();List<String> tables=new ArrayList<>();SQLiteDatabase db=store.getReadableDatabase();
        try(Cursor cursor=db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",null)){while(cursor.moveToNext())tables.add(cursor.getString(0));}
        for(String table:tables)result.put(table,StorageSafetyCheck.rows(db,"SELECT * FROM "+table+" ORDER BY rowid"));return result;
    }
    static void rejected(Runnable operation){try{operation.run();throw new AssertionError("Injected failure was swallowed");}catch(SQLiteException expected){require(expected.getMessage().contains("injected-"),"Unexpected database error");}}
    void run(String name,Scenario action){try(SafetySandbox context=new SafetySandbox(getTargetContext())){action.run(context);passed++;report("REPOSITORY_GUARD_PASS "+name);}catch(Throwable error){failed++;report("REPOSITORY_GUARD_FAIL "+name+": "+android.util.Log.getStackTraceString(error));}}
    LocalStore.UnifiedCollection collection(LocalStore store){return store.collections().unifiedCollection(store.collections().createUnifiedCollection("Repository fixture"));}
    void collectionRemoval(SafetySandbox context){
        LocalStore store=context.store();LocalStore.UnifiedCollection u=collection(store);long entry=StorageSafetyCheck.entry(store,u.groupId);StorageSafetyCheck.progress(store,entry);store.collections().syncUnifiedCollection(u.id,null);
        Map<String,String> before=rows(store);store.getWritableDatabase().execSQL("CREATE TRIGGER reject_members BEFORE DELETE ON card_deck_members BEGIN SELECT RAISE(ABORT,'injected-members'); END");
        rejected(()->store.collections().removeUnifiedPassage(u.groupId,store.study().entry(entry).reference));require(before.equals(rows(store)),"Detached entry/history committed before failed membership cleanup");
    }
    void cardRemoval(SafetySandbox context){
        LocalStore store=context.store();long deck=store.flashcards().newCardDeck("Cards");long card=store.flashcards().addCustomFlashcard(deck,"Saved","Text");Map<String,String> before=rows(store);
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_card BEFORE DELETE ON flashcards BEGIN SELECT RAISE(ABORT,'injected-card'); END");
        rejected(()->store.flashcards().removeFlashcard(deck,card));require(before.equals(rows(store)),"Membership deleted before failed orphan cleanup");
    }
    void cardChange(SafetySandbox context){
        LocalStore store=context.store();long deck=store.flashcards().newCardDeck("Cards");store.flashcards().addCustomFlashcard(deck,"Saved","Text");LocalStore.Flashcard card=store.flashcards().flashcards(deck).get(0);Map<String,String> before=rows(store);
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_old BEFORE DELETE ON flashcards BEGIN SELECT RAISE(ABORT,'injected-old'); END");
        rejected(()->store.flashcards().changeCardInDeck(deck,card,0,0,0,0,0,"Replacement","New text"));require(before.equals(rows(store)),"Replacement committed before failed removal");
    }
    void deckRemoval(SafetySandbox context){
        LocalStore store=context.store();long deck=store.flashcards().newCardDeck("Deck rollback");store.flashcards().addCustomFlashcard(deck,"Saved","Text");Map<String,String> before=rows(store);
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_orphan BEFORE DELETE ON flashcards BEGIN SELECT RAISE(ABORT,'injected-orphan'); END");
        rejected(()->store.flashcards().deleteCardDeck(deck));require(before.equals(rows(store)),"Deck/membership committed before failed orphan cleanup");
    }
    void independentStudyCreation(SafetySandbox context){
        LocalStore store=context.store();long deck=store.flashcards().newCardDeck("Study rollback");store.flashcards().addCustomFlashcard(deck,"Saved","Text");LocalStore.Flashcard card=store.flashcards().flashcards(deck).get(0);Map<String,String> before=rows(store);
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_study BEFORE INSERT ON entries BEGIN SELECT RAISE(ABORT,'injected-study'); END");
        rejected(()->store.study().findOrCreateCardStudyEntry(card,Collections.singletonList(new Bible.Verse("1:1","Text"))));require(before.equals(rows(store)),"Hidden library committed before failed material creation");
    }
    void weakTransition(SafetySandbox context){
        LocalStore store=context.store();long entry=StorageSafetyCheck.entry(store,store.collections().newGroup("Progress"));Map<String,String> before=rows(store);
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_weak BEFORE UPDATE ON transition_issues BEGIN SELECT RAISE(ABORT,'injected-weak'); END");
        rejected(()->store.progress().recordWeakTransition(entry,0,1,1700000000000L));require(before.equals(rows(store)),"Empty weak-transition row leaked after failed update");
    }
    void rename(SafetySandbox context){
        LocalStore store=context.store();LocalStore.UnifiedCollection u=collection(store);Map<String,String> before=rows(store);
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_rename BEFORE UPDATE ON card_decks BEGIN SELECT RAISE(ABORT,'injected-rename'); END");
        rejected(()->store.collections().renameUnifiedCollection(u.id,"Changed"));require(before.equals(rows(store)),"Rename left inconsistent titles");
    }
    void revise(SafetySandbox context){
        LocalStore store=context.store();long entry=StorageSafetyCheck.entry(store,store.collections().newGroup("Revise"));StorageSafetyCheck.progress(store,entry);store.progress().scheduleReview(entry,"VERSE",0,1,100,false,1700000000000L);Map<String,String> before=rows(store);
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_edit BEFORE UPDATE ON entries BEGIN SELECT RAISE(ABORT,'injected-edit'); END");
        rejected(()->store.study().reviseStudyPassage(entry,"Replacement",Collections.singletonList(new Bible.Verse("1:1","New"))));require(before.equals(rows(store)),"Failed edit rewrote progress/history");
    }
    void clear(SafetySandbox context){
        LocalStore store=context.store();LocalStore.UnifiedCollection u=collection(store);long entry=StorageSafetyCheck.entry(store,u.groupId);StorageSafetyCheck.progress(store,entry);store.collections().syncUnifiedCollection(u.id,null);Map<String,String> before=rows(store);
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_clear BEFORE UPDATE ON entries BEGIN SELECT RAISE(ABORT,'injected-clear'); END");
        rejected(()->store.collections().clearUnifiedCollection(u.id));require(before.equals(rows(store)),"Clear partially committed mappings or memberships");
    }
    void sharedTransaction(SafetySandbox context){
        LocalStore store=context.store();Map<String,String> before=rows(store);
        try{store.repositoryScope().transaction(db->{long group=store.collections().newGroup("Outer");long entry=StorageSafetyCheck.entry(store,group);store.progress().markMastered(entry,0,1,100,"VERSE");store.flashcards().addCustomFlashcard(store.flashcards().newCardDeck("Nested"),"Text","Words");throw new IllegalStateException("rollback");});throw new AssertionError("No error");}catch(IllegalStateException expected){require("rollback".equals(expected.getMessage()),"Wrong exception");}
        require(before.equals(rows(store)),"Repositories committed on different connections");
    }
    static final class QueryCounter implements SQLiteDatabase.CursorFactory {
        int fullCardScans,totalQueries;
        public Cursor newCursor(SQLiteDatabase db,SQLiteCursorDriver driver,String table,SQLiteQuery query){
            totalQueries++;
            if(query.toString().contains("SELECT f.id,f.book_id,f.first_chapter"))fullCardScans++;return new SQLiteCursor(driver,table,query);
        }
    }
    void syncQueries(SafetySandbox context){
        QueryCounter counted=new QueryCounter();
        try(LocalStore store=new LocalStore(context,counted)){
            LocalStore.UnifiedCollection u=collection(store);List<Bible.Verse> lines=new ArrayList<>();for(int i=3;i<15;i++)lines.add(new Bible.Verse("5:"+i,"Verse "+i));
            store.study().add(u.groupId,"Fixture","Матфея 5:3–14",lines);counted.fullCardScans=0;
            store.collections().syncUnifiedCollection(u.id,null);int scans=counted.fullCardScans;report("SYNC_FULL_CARD_SCANS_FOR_12_VERSES="+scans);
            String plan=StorageSafetyCheck.rows(store.getReadableDatabase(),"EXPLAIN QUERY PLAN SELECT f.id FROM flashcards f JOIN card_deck_members m ON m.card_id=f.id WHERE m.deck_id=1 AND f.book_id=40 AND f.first_chapter=5 AND f.first_verse=3 AND f.last_chapter=5 AND f.last_verse=3");
            report("SYNC_POINT_LOOKUP_PLAN="+plan.replace('\n',' '));
            require(store.flashcards().flashcards(u.deckId).size()==12,"Incomplete synchronized cards");require(scans==1,"Per-verse full-deck scans remain: "+scans);
            Map<String,String> before=rows(store);store.collections().syncUnifiedCollection(u.id,null);require(before.equals(rows(store)),"Repeated sync changed stored rows/order");
        }
    }
    void studyAttempt(SafetySandbox context){
        LocalStore store=context.store();long id=StorageSafetyCheck.entry(store,store.newGroup("Attempt"));
        LocalStore.Entry entry=store.study().entry(id);StudyProgressRecorder writer=new StudyProgressRecorder(store.progress());
        StudyPlanLogic.Step step=new StudyPlanLogic.Step(0,1,"1:1");long now=1700000000000L;
        Map<String,String> before=rows(store);
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_attempt BEFORE INSERT ON stage_progress BEGIN SELECT RAISE(ABORT,'injected-attempt'); END");
        rejected(()->writer.success(entry,"VERSE",step,false,false,false,now));
        require(before.equals(rows(store)),"Partial answer survived mastery failure");
        store.getWritableDatabase().execSQL("DROP TRIGGER reject_attempt");
        writer.success(entry,"VERSE",step,false,false,false,now);Map<String,String> committed=rows(store);
        writer.success(entry,"VERSE",step,false,false,false,now);
        require(committed.equals(rows(store)),"Replayed attempt advanced history/schedule");
        require(store.progress().exerciseDone(id,0)==5,"Lesson did not complete");
    }
    void combinedAttempt(SafetySandbox context){
        LocalStore store=context.store();long id=StorageSafetyCheck.entry(store,store.newGroup("Combined"));
        LocalStore.Entry entry=store.study().entry(id);StudyProgressRecorder writer=new StudyProgressRecorder(store.progress());
        StudyPlanLogic.Step step=new StudyPlanLogic.Step(0,2,"1:1–2");Map<String,String> before=rows(store);
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_second BEFORE INSERT ON review_attempts WHEN NEW.first_line=1 BEGIN SELECT RAISE(ABORT,'injected-second'); END");
        rejected(()->writer.success(entry,"CHAIN",step,true,true,false,1700000000000L));
        require(before.equals(rows(store)),"First verse committed before second failed");
    }
    void masterySnapshot(SafetySandbox context){
        QueryCounter queries=new QueryCounter();
        try(LocalStore store=new LocalStore(context,queries)){
            long group=store.newGroup("Large fixture");List<Bible.Verse> lines=new ArrayList<>();
            for(int i=0;i<1000;i++)lines.add(new Bible.Verse("1:"+(i+1),"Text "+i));
            long target=store.add(group,"Target","Матфея 1:1",lines);
            long shared=store.add(group,"Shared","Матфея 1:1",lines);store.markMastered(shared,0,500,100,"VERSE");
            queries.totalQueries=0;long start=System.nanoTime();int count=store.masteryCount(target);
            report("MASTERY_1000_QUERIES="+queries.totalQueries+" NS="+(System.nanoTime()-start));
            require(count==500,"Shared mastery count changed");require(queries.totalQueries==3,"Per-verse queries returned");
            require(store.progress().firstUnmastered(target,1000)==500,"First unmastered changed");
            report("MASTERY_LOOKUP_PLAN="+StorageSafetyCheck.rows(store.getReadableDatabase(),"EXPLAIN QUERY PLAN SELECT entry_id,position FROM verse_progress WHERE entry_id="+target).replace('\n',' '));
            report("DECK_LOOKUP_PLAN="+StorageSafetyCheck.rows(store.getReadableDatabase(),"EXPLAIN QUERY PLAN SELECT d.id,d.title,COUNT(m.card_id) FROM card_decks d LEFT JOIN card_deck_members m ON m.deck_id=d.id WHERE d.id=1 GROUP BY d.id").replace('\n',' '));
        }
    }
    void concurrentAttempt(SafetySandbox context)throws Exception{
        LocalStore store=context.store();long id=StorageSafetyCheck.entry(store,store.newGroup("Concurrent"));
        LocalStore.Entry entry=store.entry(id);StudyPlanLogic.Step step=new StudyPlanLogic.Step(0,1,"1:1");
        java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch gate=new java.util.concurrent.CountDownLatch(1);
        try{
            java.util.concurrent.Callable<Void> submit=()->{gate.await();
                new StudyProgressRecorder(store.progress()).success(entry,"VERSE",step,false,false,false,1700000000000L);return null;};
            java.util.concurrent.Future<Void> a=pool.submit(submit),b=pool.submit(submit);gate.countDown();
            a.get(30,java.util.concurrent.TimeUnit.SECONDS);b.get(30,java.util.concurrent.TimeUnit.SECONDS);
            require(StorageSafetyCheck.number(store.getReadableDatabase(),"SELECT COUNT(*) FROM review_attempts WHERE entry_id="+id)==1,"Concurrent attempt recorded twice");
            require(store.exerciseDone(id,0)==5,"Completion missing");
        }finally{pool.shutdownNow();pool.awaitTermination(30,java.util.concurrent.TimeUnit.SECONDS);}
    }
    void deletionHistory(SafetySandbox context){
        LocalStore store=context.store();LocalStore.UnifiedCollection a=collection(store);
        List<Bible.Verse> verses=Arrays.asList(new Bible.Verse("5:3","Первый стих"),new Bible.Verse("5:4","Второй стих"));
        long id=store.restoreOrAddStudyPassage(a.groupId,"Матфея 5:3–4",verses);
        store.finishExercise(id,0,3);store.markMastered(id,1,2,100,"VERSE");store.scheduleReview(id,"VERSE",1,2,100,false,1700000000000L);
        LocalStore.Entry expected=store.entry(id);Map<String,String> before=rows(store);
        store.deleteUnifiedCollection(a.id);Map<String,String> after=rows(store);
        for(String table:new String[]{"lesson_progress","verse_progress","stage_progress","review_attempts","review_schedule","recall_activity","transition_issues"})
            require(before.get(table).equals(after.get(table)),"Lost history table "+table);
        require(store.dueItems(Long.MAX_VALUE).isEmpty(),"Archived material remains due");
        try{new StudyProgressRecorder(store.progress(),a.groupId).success(expected,"VERSE",new StudyPlanLogic.Step(0,1,"5:3"),false,false,false,1700000000001L);throw new AssertionError("Stale session accepted");}catch(IllegalStateException correct){}
        LocalStore.UnifiedCollection b=collection(store);long restored=store.restoreOrAddStudyPassage(b.groupId,"Матфея 5:3–4",verses);
        require(restored==id&&store.exerciseDone(id,0)==3,"Stable identity/partial exercise lost");
        require(store.dueItems(Long.MAX_VALUE).size()==1,"Restored review missing");
    }
    void deletionRollback(SafetySandbox context){
        LocalStore store=context.store();LocalStore.UnifiedCollection a=collection(store);long id=StorageSafetyCheck.entry(store,a.groupId);
        StorageSafetyCheck.progress(store,id);store.syncUnifiedCollection(a.id,null);Map<String,String> before=rows(store);
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_collection_delete BEFORE DELETE ON groups_ BEGIN SELECT RAISE(ABORT,'injected-delete'); END");
        rejected(()->store.deleteUnifiedCollection(a.id));require(before.equals(rows(store)),"Partial collection deletion committed");
    }
    void overlappingReviewOwner(SafetySandbox context){
        LocalStore store=context.store();LocalStore.UnifiedCollection a=collection(store),b=collection(store);
        List<Bible.Verse> before=Arrays.asList(new Bible.Verse("5:3","Первый стих"),new Bible.Verse("5:4","Второй стих"));
        long id=store.restoreOrAddStudyPassage(a.groupId,"Матфея 5:3–4",before);
        long copy=store.restoreOrAddStudyPassage(b.groupId,"Матфея 5:4",Collections.singletonList(before.get(1)));
        store.markMastered(id,1,2,100,"VERSE");store.scheduleReview(id,"VERSE",1,2,100,false,1700000000000L);
        store.deleteUnifiedCollection(a.id);
        new StudyProgressRecorder(store.progress(),b.groupId).success(store.entry(copy),"VERSE",new StudyPlanLogic.Step(0,1,"5:4"),false,false,false,1700086400000L);
        require(StorageSafetyCheck.number(store.getReadableDatabase(),"SELECT COUNT(*) FROM review_schedule")==1,"Duplicated shared schedule");
        require(store.dueItems(1700086400000L).isEmpty(),"Old due remains after shared review");
    }
    void customCardSource(SafetySandbox context){
        LocalStore store=context.store();long deck=store.newCardDeck("Identity");long source=store.addCustomFlashcard(deck,"Own","Слово истины");
        store.addCustomFlashcard(deck,"Own","Слово истины");LocalStore.Flashcard original=null;
        for(LocalStore.Flashcard card:store.flashcards(deck))if(card.id==source)original=card;
        require(original!=null,"Missing source");
        long entry=store.findOrCreateCardStudyEntry(original,Collections.singletonList(new Bible.Verse("1:1",original.content)));
        store.removeFlashcard(deck,source);Map<String,String> before=rows(store);
        try{new StudyProgressRecorder(store.progress(),-deck-2).success(store.entry(entry),"VERSE",new StudyPlanLogic.Step(0,1,"1:1"),false,false,false,1700000000000L);throw new AssertionError("Deleted source accepted by equal contents");}
        catch(IllegalStateException expected){}
        require(before.equals(rows(store)),"Stale custom card wrote progress");
    }
    @Override public void onStart(){
        run("custom-card-source-identity",this::customCardSource);
        run("collection-delete-history-readd-and-stale-session",this::deletionHistory);run("collection-delete-atomic-rollback",this::deletionRollback);run("overlapping-review-owner",this::overlappingReviewOwner);
        run("mastery-snapshot-large-passage",this::masterySnapshot);run("concurrent-attempt-once",this::concurrentAttempt);
        run("study-attempt-rollback-and-replay",this::studyAttempt);run("combined-attempt-rollback",this::combinedAttempt);
        run("cross-repository-rollback",this::sharedTransaction);run("collection-detach-and-membership-rollback",this::collectionRemoval);run("card-delete-rollback",this::cardRemoval);run("card-replacement-rollback",this::cardChange);run("deck-delete-rollback",this::deckRemoval);run("independent-study-create-rollback",this::independentStudyCreation);run("weak-transition-rollback",this::weakTransition);run("rename-all-titles-rollback",this::rename);run("revision-history-rollback",this::revise);run("clear-all-links-rollback",this::clear);run("sync-query-count-and-idempotence",this::syncQueries);
        Bundle result=new Bundle();result.putString("stream","REPOSITORY_GUARDS_PASSED="+passed+" UNEXPECTED_FAILURES="+failed+"\n");finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
