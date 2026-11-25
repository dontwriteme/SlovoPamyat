package org.biblememory.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @SQLiteMode(SQLiteMode.Mode.NATIVE)
public class ReviewSessionTest {
    private static final long NOW=10*86_400_000L;
    private final Context context=RuntimeEnvironment.getApplication();
    private final SharedPreferences prefs=context.getSharedPreferences("study_session",0);
    private final ReviewSession session=new ReviewSession();
    private LocalStore store;

    @Before public void setUp() {
        context.deleteDatabase("slovo_pamyat.db");
        prefs.edit().clear().commit();
        store=new LocalStore(context);
    }

    @After public void close() { store.close(); }

    @Test public void scopeAndCompletionPreserveTheScheduleOrder() {
        long local=group("Локальная"), other=group("Другая");
        long later=due(local,"Первый текст",3);
        long earliest=due(other,"Второй текст",1);
        long next=due(local,"Третий текст",2);

        assertEquals(next,session.start(store,prefs,local,NOW).first().entryId);
        assertEquals(local,session.group());
        assertEquals(later,session.advance(store,NOW).entryId);
        assertNull(session.advance(store,NOW));
        assertFalse(session.active());

        LocalStore.DueItem first=session.start(store,prefs,-1,NOW).first();
        assertEquals(-1,session.group());
        assertEquals(earliest,first.entryId);
        assertTrue(session.open(store.entry(earliest),first));
        assertEquals(next,session.advance(store,NOW).entryId);
        assertEquals(later,session.advance(store,NOW).entryId);
        assertNull(session.advance(store,NOW));
        assertNull(session.current());
    }

    @Test public void validDraftSurvivesVisitingAnUnrelatedScope() {
        long group=group("Сохранённое повторение"), other=group("Другая область");
        long saved=due(group,"Первый текст",2);
        long older=due(group,"Второй текст",1);
        saveDraft(saved,"Начало ответа");

        assertNull(session.start(store,prefs,other,NOW).first());
        assertTrue(prefs.getBoolean("review_pending",false));
        ReviewSession.Start resumed=session.start(store,prefs,group,NOW);
        assertEquals(saved,resumed.first().entryId);
        assertEquals("Начало ответа",resumed.draft());
        assertEquals(older,session.advance(store,NOW).entryId);
        assertNull(session.advance(store,NOW));
    }

    @Test public void queuedItemRetiredThroughAnotherWriteIsSkipped() {
        long group=group("Очередь");
        long first=due(group,"Первый текст",1);
        long retired=due(group,"Второй текст",2);
        long last=due(group,"Третий текст",3);
        assertEquals(first,session.start(store,prefs,group,NOW).first().entryId);
        try (LocalStore writer=new LocalStore(context)) {
            writer.commitSuccessfulRecall(retired,"VERSE",0,1,false,false,NOW);
        }
        assertEquals(last,session.advance(store,NOW).entryId);
        assertNull(session.advance(store,NOW));
    }

    @Test public void invalidatedRunReturnsOnlyItsStillDueVersesInOrder() {
        long group=group("Последовательность");
        long first=due(group,"Вступление",1);
        long run=store.add(group,"Длинный отрывок","Длинный отрывок",List.of(
            new Bible.Verse("1:1","Первый стих"),new Bible.Verse("1:2","Второй стих"),
            new Bible.Verse("1:3","Третий стих")));
        for(int verse=0;verse<3;verse++)
            store.commitSuccessfulRecall(run,"VERSE",verse,verse+1,false,true,2);

        assertEquals(first,session.start(store,prefs,group,NOW).first().entryId);
        store.commitSuccessfulRecall(run,"VERSE",1,2,false,false,NOW);
        LocalStore.DueItem firstRemaining=session.advance(store,NOW);
        LocalStore.DueItem lastRemaining=session.advance(store,NOW);
        assertEquals(run,firstRemaining.entryId);
        assertEquals("VERSE",firstRemaining.stage);
        assertEquals(0,firstRemaining.start);
        assertEquals(run,lastRemaining.entryId);
        assertEquals("VERSE",lastRemaining.stage);
        assertEquals(2,lastRemaining.start);
        assertNull(session.advance(store,NOW));
    }

    @Test public void modifiedMaterialRejectsTheSavedDraftAndBundleItem() {
        long group=group("Изменённое повторение");
        long id=due(group,"Первый текст",1);
        saveDraft(id,"Начало ответа");
        store.getWritableDatabase().execSQL("UPDATE entries SET lines=? WHERE id=?",
            new Object[]{"[{\"ref\":\"1:1\",\"text\":\"Новый текст\"}]",id});

        LocalStore.DueItem oldItem=new LocalStore.DueItem(id,"VERSE",0,1,0);
        assertNull(session.restore(store,prefs,group,NOW,store.entry(id),oldItem,false));
        ReviewSession.Start start=session.start(store,prefs,group,NOW);
        assertEquals(id,start.first().entryId);
        assertEquals("",start.draft());
        assertFalse(prefs.contains("review_pending"));
        assertTrue(session.open(store.entry(id),start.first()));
        assertEquals("Новый текст",store.entry(id).lines().get(0).text);
    }

    private long group(String title) {
        return store.unifiedCollection(store.createUnifiedCollection(title)).groupId;
    }

    private long due(long group,String text,long answeredAt) {
        long id=store.add(group,text,text,List.of(new Bible.Verse("1:1",text)));
        store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,answeredAt);
        return id;
    }

    private void saveDraft(long entry,String draft) {
        LocalStore.DueItem item=new LocalStore.DueItem(entry,"VERSE",0,1,0);
        assertTrue(session.open(store.entry(entry),item));
        SharedPreferences.Editor editor=prefs.edit().putString("review_draft",draft)
            .putInt("review_begin",0).putInt("review_end",1);
        session.writeMetadata(editor,ReviewSessionPreferences.PREFIX,entry,false).commit();
    }
}
