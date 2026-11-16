package org.biblememory.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class RepositoryIntegrationTest {
    private LocalStore store;
    @Before public void open(){store=new LocalStore(RuntimeEnvironment.getApplication());}
    @After public void close(){store.close();}
    private Map<String,String> schema(SQLiteDatabase database){
        Map<String,String> result=new TreeMap<>();
        try(Cursor c=database.rawQuery("SELECT name,sql FROM sqlite_master WHERE name!='android_metadata' AND sql IS NOT NULL ORDER BY name",null)){
            while(c.moveToNext())result.put(c.getString(0),c.getString(1).replaceAll("(?i)IF NOT EXISTS ","").replaceAll("\\s+"," ").trim());
        }return result;
    }
    @Test public void exactV8SchemaAndExistingFilenameArePreserved()throws Exception{
        try(SQLiteDatabase baseline=SQLiteDatabase.create(null);InputStream source=getClass().getResourceAsStream("/sqlite-v8-schema.sql")){
            assertNotNull(source);for(String sql:new String(source.readAllBytes(),StandardCharsets.UTF_8).split(";"))if(!sql.trim().isEmpty())baseline.execSQL(sql.trim());
            assertEquals(schema(baseline),schema(store.getReadableDatabase()));
            assertEquals(8,store.getReadableDatabase().getVersion());assertTrue(store.getDatabaseName().endsWith("slovo_pamyat.db"));
            try(Cursor c=store.getReadableDatabase().rawQuery("PRAGMA foreign_keys",null)){assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));}
        }
    }
    @Test public void scopeUsesOneDatabaseAndRollsBackFailedWork(){
        RepositoryScope scope=store.repositoryScope();assertSame(store.getWritableDatabase(),scope.write());assertSame(scope.write(),scope.read());
        try{scope.transaction(db->{db.execSQL("INSERT INTO groups_(title) VALUES('rollback fixture')");throw new IllegalStateException("injected");});fail();}
        catch(IllegalStateException expected){assertEquals("injected",expected.getMessage());}
        try(Cursor c=scope.read().rawQuery("SELECT COUNT(*) FROM groups_",null)){assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0));}
    }
    @Test public void caughtNestedFailureStillPreventsPartialOuterCommit(){
        RepositoryScope scope=store.repositoryScope();scope.transaction(db->{
            db.execSQL("INSERT INTO groups_(title) VALUES('outer')");
            try{scope.transaction(inner->{assertSame(db,inner);inner.execSQL("INSERT INTO groups_(title) VALUES('inner')");throw new IllegalStateException("nested");});}
            catch(IllegalStateException expected){assertEquals("nested",expected.getMessage());}return null;
        });
        try(Cursor c=scope.read().rawQuery("SELECT COUNT(*) FROM groups_",null)){assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0));}
    }
    private Map<String,String> savedRows(){
        Map<String,String> rows=new TreeMap<>();List<String> tables=new ArrayList<>();
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name!='android_metadata' ORDER BY name",null)){while(c.moveToNext())tables.add(c.getString(0));}
        for(String table:tables){StringBuilder values=new StringBuilder();
            try(Cursor c=store.getReadableDatabase().rawQuery("SELECT * FROM "+table+" ORDER BY rowid",null)){
                while(c.moveToNext()){for(int column=0;column<c.getColumnCount();column++)values.append(c.isNull(column)?"<null>":c.getString(column)).append('\t');values.append('\n');}
            }rows.put(table,values.toString());
        }return rows;
    }
    @Test public void editingMaterialRemapsUnchangedProgressAndKeepsEntryIdentity(){
        long group=store.newGroup("Edit fixture");long id=store.study().add(group,"Text","Свой текст",Arrays.asList(new Bible.Verse("1:1","Альфа"),new Bible.Verse("1:2","Бета")));
        store.finishExercise(id,0,5);store.finishExercise(id,1,3);store.markMastered(id,0,1,100,"VERSE");store.scheduleReview(id,"VERSE",0,1,100,false,1700000000000L);
        store.study().reviseStudyPassage(id,"Свой текст",Arrays.asList(new Bible.Verse("0:1","Новое"),new Bible.Verse("1:1","Альфа"),new Bible.Verse("1:2","Бета")));
        assertEquals(id,store.study().entry(id).id);assertEquals(group,store.study().entry(id).groupId);
        assertEquals(5,store.exerciseDone(id,1));assertEquals(3,store.exerciseDone(id,2));assertFalse(store.isStepMastered(id,"VERSE",0,1));assertTrue(store.isStepMastered(id,"VERSE",1,2));
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT first_line,last_line FROM review_attempts WHERE entry_id=?",new String[]{String.valueOf(id)})){assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));assertEquals(2,c.getInt(1));}
    }
    @Test public void failureAfterProgressRemapRollsBackMaterialAndEveryHistoryTable(){
        long group=store.newGroup("Rollback fixture");long id=store.study().add(group,"Text","Свой текст",Collections.singletonList(new Bible.Verse("1:1","Альфа")));
        store.finishExercise(id,0,3);store.recordRecall(id,"VERSE",0,1,40,false,1700000000000L);store.scheduleReview(id,"VERSE",0,1,40,false,1700000000000L);
        Map<String,String> before=savedRows();store.getWritableDatabase().execSQL("CREATE TRIGGER reject_revision BEFORE UPDATE ON entries BEGIN SELECT RAISE(ABORT,'injected-revision'); END");
        try{store.study().reviseStudyPassage(id,"Новая ссылка",Collections.singletonList(new Bible.Verse("1:1","Изменённый текст")));fail();}catch(android.database.sqlite.SQLiteException expected){}
        assertEquals(before,savedRows());
    }

    @Test public void progressRepositoryKeepsAssistedDueAndRollsBackRejectedSchedule(){
        long group=store.newGroup("Review fixture");long id=store.study().add(group,"Text","Свой текст",Collections.singletonList(new Bible.Verse("1:1","Альфа")));
        ProgressRepository progress=store.progress();assertSame(progress,store.progress());progress.markMastered(id,0,1,100,"VERSE");
        long now=1700000000000L;progress.scheduleReview(id,"VERSE",0,1,100,false,now);
        long due;try(Cursor c=store.getReadableDatabase().rawQuery("SELECT due FROM review_schedule WHERE entry_id=?",new String[]{String.valueOf(id)})){assertTrue(c.moveToFirst());due=c.getLong(0);assertEquals(now+86400000L,due);}
        progress.scheduleReview(id,"VERSE",0,1,100,true,now+1);
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT due FROM review_schedule WHERE entry_id=?",new String[]{String.valueOf(id)})){assertTrue(c.moveToFirst());assertEquals(due,c.getLong(0));}
        Map<String,String> before=savedRows();store.getWritableDatabase().execSQL("CREATE TRIGGER reject_schedule BEFORE INSERT ON review_schedule BEGIN SELECT RAISE(ABORT,'injected-schedule'); END");
        try{progress.scheduleReview(id,"VERSE",0,1,40,false,now+2);fail();}catch(android.database.sqlite.SQLiteException expected){}
        assertEquals(before,savedRows());
    }

}
