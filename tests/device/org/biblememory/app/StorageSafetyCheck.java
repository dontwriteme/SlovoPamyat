package org.biblememory.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.*;

/** Real SQLiteOpenHelper upgrades and fault injection, exclusively in private fixtures. */
public class StorageSafetyCheck extends Instrumentation {
    interface Scenario { void run(SafetySandbox context)throws Exception; }
    private int passed,expectedFailures,failed;
    private static final long NOW=LegacyDatabaseFixture.NOW,DAY=LegacyDatabaseFixture.DAY;
    private static final String[] HISTORY={"verse_progress","stage_progress","recall_activity","lesson_progress","review_schedule","review_attempts","transition_issues"};
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static final class RiskViolation extends AssertionError { RiskViolation(String message){super(message);} }
    private static void riskRequire(boolean condition,String message){if(!condition)throw new RiskViolation(message);}
    static long number(SQLiteDatabase db,String sql){try(Cursor c=db.rawQuery(sql,null)){require(c.moveToFirst(),sql);return c.getLong(0);}}
    static String rows(SQLiteDatabase db,String sql){
        StringBuilder out=new StringBuilder();
        try(Cursor c=db.rawQuery(sql,null)){while(c.moveToNext()){
            for(int i=0;i<c.getColumnCount();i++){String v=c.isNull(i)?"<null>":c.getString(i);out.append(v.length()).append(':').append(v).append('|');}out.append('\n');
        }}return out.toString();
    }
    static List<Bible.Verse> verses(){return Arrays.asList(new Bible.Verse("5:3","Альфа"),new Bible.Verse("5:4","Бета"));}
    static long entry(LocalStore store,long group){return store.add(group,"Fixture","Тестовый текст",verses());}
    static void progress(LocalStore store,long id){
        store.finishExercise(id,0,5);store.markMastered(id,0,1,100,"VERSE");
        store.recordRecall(id,"VERSE",0,1,100,false,NOW-DAY);
        store.recordRecall(id,"VERSE",0,1,100,false,NOW);
        store.scheduleReview(id,"VERSE",0,1,100,false,NOW);
        store.recordWeakTransition(id,0,1,NOW);
    }
    static String history(LocalStore store,long id){
        StringBuilder out=new StringBuilder();
        for(String table:HISTORY)out.append(table).append(rows(store.getReadableDatabase(),"SELECT * FROM "+table+" WHERE entry_id="+id+" ORDER BY rowid"));
        return out.toString();
    }
    private void report(String text){Bundle b=new Bundle();b.putString("stream",text+"\n");sendStatus(0,b);}
    private void run(String name,boolean knownRisk,Scenario scenario){
        try(SafetySandbox context=new SafetySandbox(getTargetContext())){
            scenario.run(context);
            if(knownRisk)report("RISK_NOT_REPRODUCED "+name);else{passed++;report("GUARD_PASS "+name);}
        }catch(AssertionError e){
            if(knownRisk&&e instanceof RiskViolation&&e.getSuppressed().length==0){expectedFailures++;report("EXPECTED_FAILURE "+name+": "+e.getMessage());}
            else{failed++;report("GUARD_FAIL "+name+": "+android.util.Log.getStackTraceString(e));}
        }catch(Exception e){failed++;report("UNEXPECTED_FAILURE "+name+": "+android.util.Log.getStackTraceString(e));}
    }
    private void migration(SafetySandbox context,int version)throws Exception{
        LegacyDatabaseFixture.create(context,version,"Migration sentinel");
        Map<String,String> before=new LinkedHashMap<>();
        try(SQLiteDatabase old=SQLiteDatabase.openDatabase(context.getDatabasePath("slovo_pamyat.db").getPath(),null,SQLiteDatabase.OPEN_READONLY)){
            for(String table:new String[]{"entries","verse_progress","stage_progress","recall_activity","lesson_progress","review_schedule","review_attempts","transition_issues","flashcards","card_deck_members","study_order","card_order"})
                if(number(old,"SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='"+table+"'")>0)before.put(table,rows(old,"SELECT * FROM "+table+" ORDER BY rowid"));
        }
        LocalStore store=context.store();SQLiteDatabase db=store.getWritableDatabase();
        require(db.getVersion()==8,"Migration did not reach v8");require(number(db,"PRAGMA foreign_keys")==1,"Foreign keys disabled");
        require(store.entry(41)!=null&&store.entry(41).groupId==7,"Entry identity lost");
        require("Migration sentinel".equals(store.group(7).title),"Group identity/title lost");
        for(Map.Entry<String,String> table:before.entrySet())require(table.getValue().equals(rows(db,"SELECT * FROM "+table.getKey()+" ORDER BY rowid")),"Changed existing rows in "+table.getKey());
        require(store.isStepMastered(41,"VERSE",0,1),"Mastered verse locked");
        require(store.isStepMastered(41,"VERSE",version==1?1:2,version==1?2:3),"Sparse/prefix mastery lost");
        if(version<=3)require(number(db,"SELECT completed FROM lesson_progress WHERE entry_id=41 AND position=0")==5,"Old mastery not backfilled");
        if(version==3)require(number(db,"SELECT due FROM review_schedule WHERE entry_id=41 AND first_line=0")==NOW+DAY,"Recall-derived schedule changed");
        require(store.unifiedForGroup(7)!=null,"Legacy study collection missing");
        if(version>=6)require(store.flashcards(12).get(0).id==91,"Existing flashcard identity lost");
        require(rows(db,"PRAGMA foreign_key_check").isEmpty(),"Broken foreign keys after migration");
        String collections=rows(db,"SELECT * FROM unified_collections ORDER BY id"),hist=history(store,41);
        context.closeStore();store=context.store();
        require(collections.equals(rows(store.getReadableDatabase(),"SELECT * FROM unified_collections ORDER BY id")),"Reopen duplicated legacy collections");
        require(hist.equals(history(store,41)),"Reopen changed progress");
    }
    private void migrationRollback(SafetySandbox context){
        LegacyDatabaseFixture.create(context,7,"Migration sentinel");
        try(SQLiteDatabase old=SQLiteDatabase.openDatabase(context.getDatabasePath("slovo_pamyat.db").getPath(),null,0)){
            old.execSQL("CREATE TRIGGER fail_upgrade BEFORE INSERT ON card_decks BEGIN SELECT RAISE(ABORT,'injected-upgrade-failure'); END");
        }
        try{context.store().getWritableDatabase();throw new AssertionError("Upgrade failure was swallowed");}
        catch(SQLiteException expected){require(expected.getMessage().contains("injected-upgrade-failure"),"Wrong failure");}
        context.closeStore();
        try(SQLiteDatabase old=SQLiteDatabase.openDatabase(context.getDatabasePath("slovo_pamyat.db").getPath(),null,0)){
            require(old.getVersion()==7,"Failed upgrade advanced user_version");
            require(number(old,"SELECT COUNT(*) FROM sqlite_master WHERE name='unified_collections'")==0,"DDL was partially committed");
            require(number(old,"SELECT repetitions FROM entries WHERE id=41")==3,"Failed upgrade lost entry");
            require(number(old,"SELECT COUNT(*) FROM verse_progress WHERE entry_id=41")==2,"Failed upgrade lost mastery");
            old.execSQL("DROP TRIGGER fail_upgrade");
        }
        require(context.store().getWritableDatabase().getVersion()==8,"Retry after migration failure failed");
    }
    private static void mappingFailure(LocalStore store){store.getWritableDatabase().execSQL("CREATE TRIGGER fail_mapping BEFORE INSERT ON unified_entry_cards BEGIN SELECT RAISE(ABORT,'injected-mapping-failure'); END");}
    private void atomicPicker(SafetySandbox context){
        LocalStore s=context.store();LocalStore.UnifiedCollection u=s.unifiedCollection(s.createUnifiedCollection("Atomic"));mappingFailure(s);
        try{s.addPickedPassage(u.groupId,"Тестовый текст",verses(),null);throw new AssertionError("Incomplete passage accepted");}
        catch(SQLiteException expected){require(expected.getMessage().contains("injected-mapping-failure"),"Wrong rollback error");}
        for(String table:new String[]{"entries","flashcards","card_deck_members","unified_card_entries","unified_entry_cards"})
            require(number(s.getReadableDatabase(),"SELECT COUNT(*) FROM "+table)==0,"Partial picker write in "+table);
        require(s.unifiedCollection(u.id)!=null,"Rollback removed the collection itself");
    }
    private void clearPreservesHistory(SafetySandbox context){
        LocalStore s=context.store();LocalStore.UnifiedCollection u=s.unifiedCollection(s.createUnifiedCollection("Clear"));
        long id=s.addPickedPassage(u.groupId,"Тестовый текст",verses(),null);progress(s,id);String saved=history(s,id);
        s.clearUnifiedCollection(u.id);
        require(s.entries(u.groupId).isEmpty()&&s.flashcards(u.deckId).isEmpty(),"Membership not cleared");
        require(s.entry(id)!=null&&s.entry(id).groupId!=u.groupId,"Entry was deleted rather than archived");
        require(saved.equals(history(s,id)),"Clear destroyed learning/recall history");
    }
    private void archiveRestore(SafetySandbox context){
        LocalStore s=context.store();LocalStore.UnifiedCollection u=s.unifiedCollection(s.createUnifiedCollection("Restore"));
        long id=s.addPickedPassage(u.groupId,"Матфея 5:3–4",verses(),null);progress(s,id);String saved=history(s,id);
        require(s.removeUnifiedPassage(u.groupId,"Матфея 5:3–4"),"Remove failed");
        long restored=s.addPickedPassage(u.groupId,"Матфея 5:3–4",verses(),null);
        require(id==restored&&saved.equals(history(s,id)),"Re-add changed identity or progress");
        require(s.flashcards(u.deckId).size()==2,"Re-add duplicated cards");
    }
    private void sharedMastery(SafetySandbox context){
        LocalStore s=context.store();long a=s.newGroup("A"),b=s.newGroup("B");
        long original=s.add(a,"A","Матфея 5:3–4",verses()),copy=s.add(b,"B","Матфея 5:3–4",verses()),custom=entry(s,b);
        s.markMastered(original,0,1,100,"VERSE");
        require(s.exerciseDone(copy,0)==5&&s.canonicalStudyEntryId(copy)==original,"Canonical progress not shared");
        require(s.exerciseDone(custom,0)==0,"Custom text inherited unrelated Bible progress");
        require(s.exerciseDone(copy,1)==0,"Unmastered adjacent verse unlocked as mastered");
    }
    private void reviewSchedule(SafetySandbox context){
        LocalStore s=context.store();long id=entry(s,s.newGroup("Review"));long time=NOW;
        int[] days={1,3,7,14,30,60,60};
        for(int i=0;i<days.length;i++){
            s.scheduleReview(id,"VERSE",0,1,100,false,time);
            require(number(s.getReadableDatabase(),"SELECT due FROM review_schedule")==time+days[i]*DAY,"Interval changed at "+i);
            if(i==0){require(s.dueItems(time+DAY).isEmpty(),"Unmastered material appeared due");s.markMastered(id,0,1,100,"VERSE");}
            time+=days[i]*DAY;
        }
        String schedule=rows(s.getReadableDatabase(),"SELECT * FROM review_schedule");
        s.scheduleReview(id,"VERSE",0,1,100,true,time);
        require(schedule.equals(rows(s.getReadableDatabase(),"SELECT * FROM review_schedule")),"Assisted attempt changed interval");
        require(number(s.getReadableDatabase(),"SELECT COUNT(*) FROM review_attempts WHERE assisted=1")==1,"Assisted attempt not retained");
        require(s.dueItems(time).size()==1,"Mastered due verse missing");
        s.scheduleReview(id,"VERSE",0,1,99,false,time);
        require(number(s.getReadableDatabase(),"SELECT level FROM review_schedule")==0&&number(s.getReadableDatabase(),"SELECT due FROM review_schedule")==time+DAY,"Imperfect recall no longer resets interval");
    }
    private void recallEvidence(SafetySandbox context){
        LocalStore s=context.store();long id=entry(s,s.newGroup("Recall"));
        s.recordRecall(id,"VERSE",0,1,100,false,NOW);s.recordRecall(id,"VERSE",0,1,100,false,NOW+60000);
        require(!s.consolidated(id,"VERSE",0,1),"Immediate practice counted as delayed retention");
        require(number(s.getReadableDatabase(),"SELECT success_count FROM recall_activity")==1,"Immediate recall advanced count");
        s.recordRecall(id,"VERSE",0,1,100,false,NOW+DAY+60000);
        require(s.consolidated(id,"VERSE",0,1),"Independent delayed recall not consolidated");
        s.recordRecall(id,"VERSE",0,1,100,true,NOW+4*DAY);
        require(number(s.getReadableDatabase(),"SELECT success_count FROM recall_activity")==2,"Assisted recall advanced retention");
    }
    private void idempotentSync(SafetySandbox context){
        LocalStore s=context.store();LocalStore.UnifiedCollection u=s.unifiedCollection(s.createUnifiedCollection("Sync"));long id=entry(s,u.groupId);progress(s,id);
        s.syncUnifiedCollection(u.id,null);String cards=rows(s.getReadableDatabase(),"SELECT * FROM flashcards ORDER BY id"),saved=history(s,id);
        s.syncUnifiedCollection(u.id,null);s.syncUnifiedCollection(u.id,null);
        require(s.entries(u.groupId).size()==1&&s.flashcards(u.deckId).size()==2,"Successful sync duplicated material");
        require(cards.equals(rows(s.getReadableDatabase(),"SELECT * FROM flashcards ORDER BY id"))&&saved.equals(history(s,id)),"Sync changed identity/history");
    }
    private void sharedCards(SafetySandbox context){
        LocalStore s=context.store();LocalStore.UnifiedCollection a=s.unifiedCollection(s.createUnifiedCollection("A")),b=s.unifiedCollection(s.createUnifiedCollection("B"));
        s.addPickedPassage(a.groupId,"Матфея 5:3–4",verses(),null);s.addPickedPassage(b.groupId,"Матфея 5:3–4",verses(),null);
        require(s.flashcards(a.deckId).get(0).id==s.flashcards(b.deckId).get(0).id,"Canonical flashcard duplicated across decks");
        s.clearUnifiedCollection(a.id);require(s.flashcards(b.deckId).size()==2,"Clearing one collection destroyed shared membership");
    }
    private void backupStudyHistory(SafetySandbox context)throws Exception{
        LocalStore s=context.store();long id=entry(s,s.newGroup("Backup"));progress(s,id);String json=s.exportJson().toString();
        try(SafetySandbox destination=new SafetySandbox(getTargetContext())){
            LocalStore restored=destination.store();entry(restored,restored.newGroup("Existing"));restored.importJson(json);
            LocalStore.Entry copy=restored.entries(restored.groups().get(1).id).get(0);
            require(copy.id!=id&&copy.linesJson.equals(s.entry(id).linesJson),"Import failed to remap entry identity");
            require(restored.exerciseDone(copy.id,0)==5&&restored.consolidated(copy.id,"VERSE",0,1),"Import lost mastery/recall");
            require(number(restored.getReadableDatabase(),"SELECT COUNT(*) FROM transition_issues WHERE entry_id="+copy.id)==1,"Import lost transition history");
            require(number(restored.getReadableDatabase(),"SELECT due FROM review_schedule WHERE entry_id="+copy.id)==NOW+DAY,"Import changed review due date");
            String before=restored.exportJson().toString();
            try{restored.importJson("{\"format\":\"slovo-pamyat-v5\",\"groups\":[{\"title\":\"Broken\",\"entries\":[{}]}]}");throw new AssertionError("Invalid backup accepted");}
            catch(org.json.JSONException expected){}
            require(before.equals(restored.exportJson().toString()),"Invalid import partially wrote rows");
        }
    }
    private void defaultTitleLoss(SafetySandbox context){
        LegacyDatabaseFixture.create(context,5,"Мои стихи");
        riskRequire(context.store().entry(41)!=null,"v5→v8 deleted a populated collection named Мои стихи and its entry/history");
    }
    private void syncAtomicity(SafetySandbox context){
        LocalStore s=context.store();LocalStore.UnifiedCollection u=s.unifiedCollection(s.createUnifiedCollection("Fault"));entry(s,u.groupId);mappingFailure(s);
        boolean threw=false;try{s.syncUnifiedCollection(u.id,null);}catch(SQLiteException expected){threw=true;}
        riskRequire(threw&&s.flashcards(u.deckId).isEmpty(),"sync threw="+threw+"; committed card memberships="+s.flashcards(u.deckId).size()+", entry mappings="+number(s.getReadableDatabase(),"SELECT COUNT(*) FROM unified_entry_cards"));
    }
    private void syncRetry(SafetySandbox context){
        LocalStore s=context.store();LocalStore.UnifiedCollection u=s.unifiedCollection(s.createUnifiedCollection("Retry"));entry(s,u.groupId);mappingFailure(s);
        try{s.syncUnifiedCollection(u.id,null);throw new AssertionError("Injected mapping failure did not occur");}
        catch(SQLiteException expected){require(expected.getMessage().contains("injected-mapping-failure"),"Wrong injected failure");}
        s.getWritableDatabase().execSQL("DROP TRIGGER fail_mapping");s.syncUnifiedCollection(u.id,null);
        riskRequire(s.flashcards(u.deckId).size()==2,"Retry after rejected mappings duplicated custom cards: "+s.flashcards(u.deckId).size());
    }
    private void rejectedSchedule(SafetySandbox context){
        LocalStore s=context.store();long id=entry(s,s.newGroup("Fault"));
        s.getWritableDatabase().execSQL("CREATE TRIGGER fail_schedule BEFORE INSERT ON review_schedule BEGIN SELECT RAISE(ABORT,'injected-review-failure'); END");
        try{s.scheduleReview(id,"VERSE",0,1,100,false,NOW);throw new AssertionError("Injected schedule failure did not occur");}
        catch(SQLiteException expected){require(expected.getMessage().contains("injected-review-failure"),"Wrong review failure");}
        require(number(s.getReadableDatabase(),"SELECT COUNT(*) FROM review_attempts")==0,"Review attempt committed despite rejected schedule");
    }
    private String installedDatabaseHash()throws Exception{
        File file=getTargetContext().getDatabasePath("slovo_pamyat.db");if(!file.exists())return "absent";
        MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];
        try(FileInputStream in=new FileInputStream(file)){int n;while((n=in.read(buffer))!=-1)hash.update(buffer,0,n);}
        return Arrays.toString(hash.digest());
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            String installed=installedDatabaseHash();
            for(int v=1;v<=7;v++){final int version=v;run("upgrade-v"+v+"-to-v8",false,c->migration(c,version));}
            run("failed-upgrade-rollback-and-retry",false,this::migrationRollback);
            run("picker-write-rollback",false,this::atomicPicker);
            run("clear-preserves-all-history",false,this::clearPreservesHistory);
            run("archive-readd-keeps-identity",false,this::archiveRestore);
            run("shared-mastery-excludes-custom-text",false,this::sharedMastery);
            run("review-intervals-assisted-and-failure",false,this::reviewSchedule);
            run("delayed-recall-evidence",false,this::recallEvidence);
            run("successful-sync-idempotent",false,this::idempotentSync);
            run("shared-card-membership",false,this::sharedCards);
            run("backup-study-history-and-invalid-import",false,this::backupStudyHistory);
            run("review-rejected-schedule-rolls-back",false,this::rejectedSchedule);
            // Desired safety properties: expected failures are REPORTED, not counted as passed guards.
            run("P0-populated-default-title-migration",true,this::defaultTitleLoss);
            run("P1-sync-rejected-mapping",true,this::syncAtomicity);
            run("P1-sync-retry-duplicates",true,this::syncRetry);
            require(installed.equals(installedDatabaseHash()),"Installed app database changed during isolated tests");
            report("INSTALLED_DATABASE_UNCHANGED");
            result.putString("stream","GUARDS_PASSED="+passed+" EXPECTED_FAILURES="+expectedFailures+" UNEXPECTED_FAILURES="+failed+"\n");
            finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
