package org.biblememory.app;

import android.content.Context;
import android.net.Uri;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** End-to-end tests for the user's complete offline data export/restore. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class SvetochBackupTest {
    private Context context;
    private LocalStore store;
    private File backup;

    @Before public void setUp()throws Exception{
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("slovo_pamyat.db");
        for(String name:new String[]{"study_session","collection_study_queue","card_sessions",
                "gap_rotation","unified_library_ui","home_ui","home_verse","bible_last_chapter"})
            context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit();
        Bible.shared().load(context);
        store=new LocalStore(context);
        backup=File.createTempFile("svetoch-backup-",SvetochBackup.EXTENSION,context.getCacheDir());
    }

    @After public void tearDown(){
        if(store!=null)store.close();
        if(backup!=null)backup.delete();
    }

    @Test public void fullRoundTripRestoresAllDataWithoutAppendingDuplicates()throws Exception{
        long uid=store.createUnifiedCollection("Обетования");
        LocalStore.UnifiedCollection u=store.unifiedCollection(uid);
        long verse=store.add(u.groupId,"Бытие","Бытие 1:1",
            Arrays.asList(new Bible.Verse("1:1","В начале сотворил Бог небо и землю")));
        store.markMastered(verse,0,1,100,"VERSE");
        store.scheduleReview(verse,"VERSE",0,1,100,false,System.currentTimeMillis());
        store.addCustomFlashcard(u.deckId,"Свой текст","Моё обетование");
        context.getSharedPreferences("study_session",Context.MODE_PRIVATE).edit()
            .putLong("entry",verse).putString("entry_"+verse+"_draft","Начало").commit();
        context.getSharedPreferences("unified_library_ui",Context.MODE_PRIVATE).edit()
            .putLong("active",uid).commit();
        SvetochBackup.exportTo(context,store,Uri.fromFile(backup));

        store.createUnifiedCollection("Лишняя коллекция");
        store.deleteUnifiedCollection(uid);
        context.getSharedPreferences("study_session",Context.MODE_PRIVATE).edit().clear().commit();

        SvetochBackup.restoreFrom(context,store,Uri.fromFile(backup));
        assertEquals(1,store.unifiedCollections().size());
        assertEquals("Обетования",store.unifiedCollections().get(0).title);
        assertEquals(uid,store.unifiedCollections().get(0).id);
        List<LocalStore.Entry> restored=store.entries(u.groupId);
        assertEquals(1,restored.size());
        assertEquals(verse,restored.get(0).id);
        assertEquals("В начале сотворил Бог небо и землю",restored.get(0).lines().get(0).text);
        assertTrue(store.isStepMastered(verse,"VERSE",0,1));
        assertEquals(1,store.flashcards(u.deckId).size());
        assertEquals("Моё обетование",store.flashcards(u.deckId).get(0).content);
        assertEquals(verse,context.getSharedPreferences("study_session",Context.MODE_PRIVATE)
            .getLong("entry",-1));
        assertEquals("Начало",context.getSharedPreferences("study_session",Context.MODE_PRIVATE)
            .getString("entry_"+verse+"_draft",""));
        assertEquals(uid,context.getSharedPreferences("unified_library_ui",Context.MODE_PRIVATE)
            .getLong("active",-1));

        // Importing the same backup twice is a replace, never a duplicate import.
        SvetochBackup.restoreFrom(context,store,Uri.fromFile(backup));
        assertEquals(1,store.unifiedCollections().size());
        assertEquals(1,store.entries(u.groupId).size());
        assertEquals(1,store.flashcards(u.deckId).size());
    }

    @Test public void corruptFileCannotTouchExistingDataOrPreferences()throws Exception{
        long uid=store.createUnifiedCollection("Не удалять");
        context.getSharedPreferences("study_session",Context.MODE_PRIVATE).edit()
            .putString("draft","Сохранённый ответ").commit();
        try(FileOutputStream output=new FileOutputStream(backup)){
            output.write(new byte[]{1,2,3,4,5});
        }
        try{
            SvetochBackup.restoreFrom(context,store,Uri.fromFile(backup));
            fail("Expected invalid backup rejection");
        }catch(Exception expected){}
        assertEquals("Не удалять",store.unifiedCollections().get(0).title);
        assertEquals(uid,store.unifiedCollections().get(0).id);
        assertEquals("Сохранённый ответ",context.getSharedPreferences(
            "study_session",Context.MODE_PRIVATE).getString("draft",""));
    }
}
