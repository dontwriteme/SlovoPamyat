package org.biblememory.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class LearningDurabilityTest {
    private Context context;
    private LocalStore store;
    private long uid,id;

    @Before public void setup()throws Exception{
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("slovo_pamyat.db");
        for(String group:Arrays.asList("learning_reset_journal","study_session",
                "collection_study_queue","gap_rotation","card_sessions","unified_library_ui"))
            prefs(group).edit().clear().commit();
        Bible.shared().load(context);
        store=new LocalStore(context);
        uid=store.createUnifiedCollection("Сохранить коллекцию");
        LocalStore.UnifiedCollection u=store.unifiedCollection(uid);
        id=store.add(u.groupId,"Бытие","Бытие 1:1",
            Collections.singletonList(new Bible.Verse("1:1","В начале сотворил Бог небо и землю")));
    }
    @After public void close(){if(store!=null)store.close();}
    private SharedPreferences prefs(String name){return context.getSharedPreferences(name,Context.MODE_PRIVATE);}
    private int rows(String table){
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){
            c.moveToFirst();return c.getInt(0);
        }
    }
    @Test public void successfulRecallPersistsAllComponentsAcrossReopening(){
        store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,10000L);
        store.close();store=new LocalStore(context);
        assertTrue(store.isStepMastered(id,"VERSE",0,1));
        assertEquals(5,store.exerciseDone(id,0));
        assertEquals(1,rows("lesson_progress"));
        assertEquals(1,rows("review_schedule"));assertEquals(1,rows("review_attempts"));
        assertEquals(1,rows("recall_activity"));
    }
    @Test public void failedMasteryWriteRollsBackAllPartsOfAnswer(){
        SQLiteDatabase db=store.getWritableDatabase();
        db.execSQL("CREATE TRIGGER reject_mastery BEFORE INSERT ON verse_progress "+
            "BEGIN SELECT RAISE(ABORT,'simulated storage failure'); END");
        try{
            store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,10000L);
            fail("Expected SQLite failure");
        }catch(SQLiteException expected){}
        assertEquals(0,rows("review_schedule"));assertEquals(0,rows("review_attempts"));
        assertEquals(0,rows("recall_activity"));assertEquals(0,rows("lesson_progress"));
        assertFalse(store.isStepMastered(id,"VERSE",0,1));
        db.execSQL("DROP TRIGGER reject_mastery");
        store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,10001L);
        assertEquals(1,rows("review_attempts"));
        assertTrue(store.isStepMastered(id,"VERSE",0,1));
    }
    @Test public void interruptedResetRecoversAfterReopeningWithoutDeletingContent()throws Exception{
        store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,10000L);
        prefs("study_session").edit().putString("draft","Старый ответ").commit();
        prefs("collection_study_queue").edit().putBoolean("active",true).commit();
        prefs("unified_library_ui").edit().putLong("active",uid)
            .putString("writing_run_"+uid+"_draft","Черновик")
            .putString("run_history_"+uid,"[]").commit();
        // Simulate termination after SQLite deletion, before preferences cleanup.
        prefs("learning_reset_journal").edit().putBoolean("pending",true).commit();
        store.resetLearningProgress();
        store.close();store=new LocalStore(context);
        assertTrue(LearningReset.recover(context,store));
        assertFalse(LearningReset.recover(context,store));
        assertTrue(prefs("study_session").getAll().isEmpty());
        assertTrue(prefs("collection_study_queue").getAll().isEmpty());
        assertEquals(uid,prefs("unified_library_ui").getLong("active",-1));
        assertFalse(prefs("unified_library_ui").contains("writing_run_"+uid+"_draft"));
        assertFalse(prefs("unified_library_ui").contains("run_history_"+uid));
        assertNotNull(store.entry(id));assertNotNull(store.unifiedCollection(uid));
        assertEquals(0,store.exerciseDone(id,0));assertEquals(0,rows("review_schedule"));
        // No stale journal may erase progress created after the reset.
        store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,20000L);
        assertFalse(LearningReset.recover(context,store));
        assertTrue(store.isStepMastered(id,"VERSE",0,1));
    }
}
