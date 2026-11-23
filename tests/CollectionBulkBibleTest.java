package org.biblememory.app;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class CollectionBulkBibleTest {
    private LocalStore store;
    private Bible bible;
    private LocalStore.UnifiedCollection collection;

    @Before public void setUp() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("slovo_pamyat.db");
        store=new LocalStore(context);
        bible=new Bible();
        bible.load(context);
        collection=store.unifiedCollection(store.createUnifiedCollection("Bulk"));
    }

    @After public void tearDown(){store.close();}

    private void addBook(int book){
        store.addBibleChapters(collection.groupId,book,Collections.emptyList(),true,bible);
    }

    @Test public void psalmsRoundTripPreservesExplicitSourcesAndProgress(){
        long group=collection.groupId;
        int psalms=19;
        store.addBibleChapters(group,psalms,Arrays.asList(3,1,1),false,bible);
        List<LocalStore.Entry> separate=store.entries(group);
        assertEquals(Bible.reference(psalms,1,1,1,bible.chapter(psalms,1).size()),separate.get(0).reference);
        assertEquals(2,separate.size());
        String standaloneRef=Bible.reference(psalms,118,27,118,27);
        long standalone=store.addPickedPassage(group,standaloneRef,bible.passage(psalms,118,27,118,27),bible);
        store.markMastered(standalone,0,1,100,"VERSE");

        long start=System.nanoTime();
        addBook(psalms);
        SortedMap<Integer,SortedMap<Integer,Long>> books=store.collectionBooks(group);
        System.out.println("PSALMS_ADD_MS="+(System.nanoTime()-start)/1_000_000);
        assertEquals(150,books.get(psalms).size());
        long chapter118=books.get(psalms).get(118);
        assertTrue(store.isStepMastered(chapter118,"VERSE",26,27));
        store.markMastered(chapter118,0,1,100,"VERSE");
        CollectionProgressSnapshot snapshot=store.collectionProgress();
        assertEquals(2526,snapshot.uniqueVerseCount(group));
        assertTrue(snapshot.visibleCollectionEntries(group,books,bible).isEmpty());
        assertEquals(2526,store.unifiedCollection(collection.id).cards);
        addBook(psalms);
        assertEquals(151,store.entries(group).size());

        store.addBibleChapters(group,psalms,Arrays.asList(2,3),false,bible);
        start=System.nanoTime();
        store.removeBibleBook(group,psalms);
        System.out.println("PSALMS_REMOVE_MS="+(System.nanoTime()-start)/1_000_000);
        assertTrue(store.collectionBooks(group).isEmpty());
        assertEquals(4,store.entries(group).size());
        assertTrue(store.containsEntry(group,standalone));
        assertTrue(store.containsEntry(group,separate.get(0).id));
        assertTrue(store.isStepMastered(chapter118,"VERSE",0,1));
        assertFalse(store.containsEntry(group,chapter118));
        assertEquals(bible.chapter(psalms,1).size()+bible.chapter(psalms,2).size()+
            bible.chapter(psalms,3).size()+1,store.unifiedCollection(collection.id).cards);

        addBook(psalms);
        assertEquals(Long.valueOf(chapter118),store.collectionBooks(group).get(psalms).get(118));
        assertEquals(151,store.entries(group).size());
    }

    @Test public void failureRollsBackBookAndOtherBooksRemainIndependent(){
        long group=collection.groupId;
        addBook(31);addBook(57);addBook(65);
        assertEquals(Arrays.asList(31,57,65),new ArrayList<>(store.collectionBooks(group).keySet()));
        store.removeBibleBook(group,57);
        assertEquals(Arrays.asList(31,65),new ArrayList<>(store.collectionBooks(group).keySet()));

        int beforeEntries=store.entries(group).size(),beforeCards=store.unifiedCollection(collection.id).cards;
        store.getWritableDatabase().execSQL("CREATE TRIGGER fail_book BEFORE INSERT ON collection_book_chapters "+
            "WHEN NEW.chapter=2 BEGIN SELECT RAISE(ABORT,'test failure'); END");
        try{addBook(1);fail("Expected rollback");}catch(SQLiteException expected){}
        assertFalse(store.collectionBooks(group).containsKey(1));
        assertEquals(beforeEntries,store.entries(group).size());
        assertEquals(beforeCards,store.unifiedCollection(collection.id).cards);
    }

    @Test public void versionEightUpgradeKeepsEntriesAndProgress(){
        long group=collection.groupId;
        String reference=Bible.reference(1,1,1,1,1);
        long entry=store.addPickedPassage(group,reference,bible.passage(1,1,1,1,1),bible);
        store.markMastered(entry,0,1,100,"VERSE");
        android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();
        db.execSQL("DROP TABLE collection_book_chapters");
        db.setVersion(8);
        store.close();
        store=new LocalStore(RuntimeEnvironment.getApplication());
        assertTrue(store.collectionBooks(group).isEmpty());
        assertEquals(reference,store.entry(entry).reference);
        assertTrue(store.isStepMastered(entry,"VERSE",0,1));
        assertEquals(1,store.unifiedCollection(collection.id).cards);
    }
}
