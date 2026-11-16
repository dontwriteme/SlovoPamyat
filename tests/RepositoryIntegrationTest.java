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
}
