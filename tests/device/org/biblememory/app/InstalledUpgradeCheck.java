package org.biblememory.app;

import android.app.*;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.os.Bundle;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.json.*;

/** Seed with the pre-refactor APK; verify the identical private fixture after pm install -r. */
public final class InstalledUpgradeCheck extends Instrumentation {
    private String phase;
    private String fixture="";
    @Override public void onCreate(Bundle args){if(args!=null)fixture=args.getString("fixture","");if(!fixture.matches("[A-Za-z0-9_-]*"))throw new IllegalArgumentException("Invalid fixture identifier");phase=args==null?"verify":args.getString("phase","verify");super.onCreate(args);start();}
    private static final class Fixture extends ContextWrapper {
        final File directory;
        Fixture(Context context,String suffix){super(context);directory=new File(context.getCacheDir(),"architecture_stage3_installed_upgrade"+suffix);}
        @Override public Context getApplicationContext(){return this;}
        @Override public File getDatabasePath(String name){return new File(directory,new File(name).getName());}
        @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);}
        @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(),factory,handler);}
    }
    private JSONObject snapshot(LocalStore store)throws Exception{
        SQLiteDatabase database=store.getReadableDatabase();JSONObject result=new JSONObject();
        result.put("schema",StorageSafetyCheck.rows(database,"SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name"));
        List<String> tables=new ArrayList<>();try(Cursor cursor=database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",null)){while(cursor.moveToNext())tables.add(cursor.getString(0));}
        for(String table:tables)result.put(table,StorageSafetyCheck.rows(database,"SELECT * FROM "+table+" ORDER BY rowid"));
        result.put("version",database.getVersion());result.put("path",database.getPath());return result;
    }
    private void seed(LocalStore store)throws Exception{
        LocalStore.UnifiedCollection collection=store.unifiedCollection(store.createUnifiedCollection("Installed upgrade fixture"));
        long entry=store.add(collection.groupId,"User text","Пользовательский текст",StorageSafetyCheck.verses());
        store.finishExercise(entry,0,5);store.finishExercise(entry,1,3);store.markMastered(entry,0,1,100,"VERSE");
        long now=1700000000000L;store.recordRecall(entry,"VERSE",0,1,100,false,now-86400000L);store.recordRecall(entry,"VERSE",0,1,100,false,now);
        store.scheduleReview(entry,"VERSE",0,1,100,false,now);store.scheduleReview(entry,"VERSE",0,1,40,false,now+1);store.scheduleReview(entry,"VERSE",0,1,100,true,now+2);
        store.recordWeakTransition(entry,0,1,now);store.syncUnifiedCollection(collection.id,null);store.reorderStudy(collection.groupId,Collections.singletonList(entry));
        List<Long> order=new ArrayList<>();for(LocalStore.Flashcard card:store.flashcards(collection.deckId))order.add(card.id);Collections.reverse(order);store.reorderCards(collection.deckId,order);
        LocalStore.UnifiedCollection archived=store.unifiedCollection(store.createUnifiedCollection("Archived fixture"));
        long old=StorageSafetyCheck.entry(store,archived.groupId);store.finishExercise(old,0,2);store.clearUnifiedCollection(archived.id);
    }
    @Override public void onStart(){
        Bundle result=new Bundle();Fixture context=new Fixture(getTargetContext(),fixture);LocalStore store=null;
        try{
            File expected=new File(context.directory,"expected.json");
            if("seed".equals(phase)){
                boolean alreadyRefactored=false;try{LocalStore.class.getDeclaredMethod("study");alreadyRefactored=true;}catch(NoSuchMethodException expectedOldApi){}
                StorageSafetyCheck.require(!alreadyRefactored,"Seed must run in the pre-extraction APK");
                StorageSafetyCheck.require(!context.directory.exists(),"Upgrade fixture already exists; refusing to overwrite it");
                StorageSafetyCheck.require(context.directory.mkdir(),"Cannot create fixture directory");store=new LocalStore(context);seed(store);
                Files.write(expected.toPath(),snapshot(store).toString().getBytes(StandardCharsets.UTF_8));
                result.putString("stream","UPGRADE_FIXTURE_SEEDED_WITH_PRE_REFACTOR_APK\n");
            }else{
                StorageSafetyCheck.require(context.getDatabasePath("slovo_pamyat.db").isFile()&&expected.isFile(),"Missing existing pre-refactor database");
                StorageSafetyCheck.require(LocalStore.class.getDeclaredMethod("study")!=null,"New repository API missing after APK upgrade");
                JSONObject before=new JSONObject(new String(Files.readAllBytes(expected.toPath()),StandardCharsets.UTF_8));store=new LocalStore(context);
                JSONObject after=snapshot(store);StorageSafetyCheck.require(before.length()==after.length(),"Table set changed");
                Iterator<String> keys=before.keys();while(keys.hasNext()){String key=keys.next();StorageSafetyCheck.require(before.get(key).toString().equals(after.get(key).toString()),"Changed existing data/schema: "+key);}
                for(LocalStore.UnifiedCollection collection:store.unifiedCollections())for(LocalStore.Entry entry:store.entries(collection.groupId)){
                    store.exerciseDone(entry.id,0);store.statusesForChapter("Матфея",5);
                }
                JSONObject read=snapshot(store);keys=after.keys();while(keys.hasNext()){String key=keys.next();StorageSafetyCheck.require(after.get(key).toString().equals(read.get(key).toString()),"Read mutated "+key);}
                result.putString("stream","INSTALLED_UPGRADE_PASS: same file, v8 schema, IDs, text, partial exercises, mastery, recall, attempts, schedules, collections, cards, order; readonly repositories unchanged\n");
            }
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","INSTALLED_UPGRADE_FAIL: "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
        finally{if(store!=null)store.close();}
    }
}
