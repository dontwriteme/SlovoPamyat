package org.biblememory.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class DatabaseUpgradeRegressionTest {
    private Context context;
    private LocalStore store;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("slovo_pamyat.db");
    }
    @After public void close(){if(store!=null)store.close();}

    @Test public void oldDefaultCollectionWithLearnedVerseSurvivesUpgrade(){
        // Historical version 1: the two original tables, no later progress tables.
        try(SQLiteDatabase db=context.openOrCreateDatabase("slovo_pamyat.db",Context.MODE_PRIVATE,null)){
            db.execSQL("CREATE TABLE groups_ (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL)");
            db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, group_id INTEGER NOT NULL REFERENCES groups_(id) ON DELETE CASCADE, title TEXT NOT NULL, reference TEXT NOT NULL, lines TEXT NOT NULL, learned INTEGER NOT NULL DEFAULT 0, repetitions INTEGER NOT NULL DEFAULT 0, days INTEGER NOT NULL DEFAULT 0, ease REAL NOT NULL DEFAULT 2.5, due INTEGER NOT NULL DEFAULT 0, last_score INTEGER NOT NULL DEFAULT -1)");
            db.execSQL("INSERT INTO groups_(id,title) VALUES(1,'Мои стихи'),(2,'Мои стихи')");
            db.execSQL("INSERT INTO entries(id,group_id,title,reference,lines,learned) VALUES(7,1,?,?,?,1)",
                new Object[]{"Бытие","Бытие 1:1","[{\"ref\":\"1:1\",\"text\":\"В начале сотворил Бог небо и землю\"}]"});
            db.setVersion(1);
        }
        store=new LocalStore(context);
        assertEquals(9,store.getWritableDatabase().getVersion());
        assertEquals(1,store.unifiedCollections().size());
        LocalStore.UnifiedCollection u=store.unifiedCollections().get(0);
        assertEquals("Мои стихи",u.title);assertEquals(1,u.groupId);
        assertEquals(7,store.entries(u.groupId).get(0).id);
        assertTrue(store.isStepMastered(7,"VERSE",0,1));
        assertEquals(5,store.exerciseDone(7,0));
        assertNull(store.group(2));
        assertIntegrity();
    }
    @Test public void versionEightUpgradePreservesDataAndAddsBookMembershipTable(){
        store=new LocalStore(context);
        LocalStore.UnifiedCollection u=store.unifiedCollection(store.createUnifiedCollection("Сохранить"));
        long id=store.add(u.groupId,"Бытие","Бытие 1:1",
            Collections.singletonList(new Bible.Verse("1:1","В начале сотворил Бог небо и землю")));
        store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,10000L);
        store.addCustomFlashcard(u.deckId,"Свой текст","Сохранённая карточка");
        SQLiteDatabase db=store.getWritableDatabase();
        db.execSQL("DROP TABLE collection_book_chapters");db.setVersion(8);
        store.close();store=new LocalStore(context);
        assertEquals(9,store.getWritableDatabase().getVersion());
        assertEquals("Сохранить",store.unifiedCollection(u.id).title);
        assertNotNull(store.entry(id));assertTrue(store.isStepMastered(id,"VERSE",0,1));
        assertEquals(5,store.exerciseDone(id,0));
        assertEquals(1,store.flashcards(u.deckId).size());
        assertEquals("Сохранённая карточка",store.flashcards(u.deckId).get(0).content);
        assertTrue(store.collectionBooks(u.groupId).isEmpty());
        assertIntegrity();
    }
    private void assertIntegrity(){
        try(Cursor c=store.getReadableDatabase().rawQuery("PRAGMA integrity_check",null)){
            assertTrue(c.moveToFirst());assertEquals("ok",c.getString(0));
        }
        try(Cursor c=store.getReadableDatabase().rawQuery("PRAGMA foreign_key_check",null)){
            assertFalse(c.moveToFirst());
        }
    }
}
