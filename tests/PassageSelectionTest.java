package org.biblememory.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class PassageSelectionTest {
    private PassageSelection start(){PassageSelection s=new PassageSelection();s.collectionId=7;s.openBook(40);s.active=true;return s;}
    @Test public void firstPointDoesNotCompleteRange(){
        PassageSelection s=start();s.setBoundary(false,5,3);
        assertFalse(s.complete());assertFalse(s.valid());
    }
    @Test public void bothBoundariesEnableRange(){
        PassageSelection s=start();s.setBoundary(false,5,3);s.setBoundary(true,5,12);
        assertTrue(s.complete());assertTrue(s.valid());assertArrayEquals(new int[]{5,3,5,12},s.bounds());
    }
    @Test public void endCanBeSelectedBeforeStart(){
        PassageSelection s=start();s.setBoundary(true,7,29);
        assertFalse(s.complete());assertEquals(7,s.endChapter);
        s.setBoundary(false,5,3);assertTrue(s.valid());
    }
    @Test public void reversedRangeRemainsVisibleAndInvalid(){
        PassageSelection s=start();s.setBoundary(false,7,29);s.setBoundary(true,5,3);
        assertTrue(s.complete());assertFalse(s.valid());assertArrayEquals(new int[]{7,29,5,3},s.bounds());
    }
    @Test public void independentEditsNeverMoveOtherBoundary(){
        PassageSelection s=start();s.setBoundary(false,5,3);s.setBoundary(true,7,29);
        s.setBoundary(false,8,1);assertFalse(s.valid());assertEquals(7,s.endChapter);assertEquals(29,s.endVerse);
        s.setBoundary(true,9,2);assertTrue(s.valid());assertEquals(8,s.startChapter);assertEquals(1,s.startVerse);
    }
    @Test public void identicalPointsAreASingleVerse(){
        PassageSelection s=start();s.setBoundary(false,5,3);s.setBoundary(true,5,3);
        assertTrue(s.valid());assertArrayEquals(new int[]{5,3,5,3},s.bounds());
    }
    @Test public void navigationDoesNotCommitBoundaryEdits(){
        PassageSelection s=start();s.setBoundary(false,5,3);s.setBoundary(true,7,29);
        s.chapter=118;s.openBook(40);
        assertArrayEquals(new int[]{5,3,7,29},s.bounds());assertTrue(s.active);
    }
    @Test public void cancelAndBookChangeRemoveStaleSelection(){
        PassageSelection s=start();s.setBoundary(false,5,3);s.cancel();
        assertFalse(s.active);assertFalse(s.complete());
        s.active=true;s.setBoundary(false,5,3);s.openBook(41);
        assertFalse(s.active);assertFalse(s.complete());assertEquals(41,s.book);
    }
    @Test public void russianPluralization(){
        assertEquals("1 стих",PassageSelection.verses(1));assertEquals("2 стиха",PassageSelection.verses(2));
        assertEquals("5 стихов",PassageSelection.verses(5));assertEquals("11 стихов",PassageSelection.verses(11));
        assertEquals("14 стихов",PassageSelection.verses(14));assertEquals("21 стих",PassageSelection.verses(21));
        assertEquals("22 стиха",PassageSelection.verses(22));assertEquals("109 стихов",PassageSelection.verses(109));
    }
    @Test public void actualPassageTraversalCountsChapterBoundaries()throws Exception {
        Bible bible=new Bible();
        java.lang.reflect.Field f=Bible.class.getDeclaredField("books");f.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<Integer,java.util.SortedMap<Integer,java.util.List<Bible.Verse>>> books=
            (java.util.Map<Integer,java.util.SortedMap<Integer,java.util.List<Bible.Verse>>>)f.get(bible);
        java.util.SortedMap<Integer,java.util.List<Bible.Verse>> chapters=new java.util.TreeMap<>();
        int[] lengths={48,34,29};
        for(int c=5;c<=7;c++){
            java.util.List<Bible.Verse> verses=new java.util.ArrayList<>();
            for(int v=1;v<=lengths[c-5];v++)verses.add(new Bible.Verse(c+":"+v,"verse "+v));
            chapters.put(c,verses);
        }
        // chapterCount uses contiguous canonical chapter maps.
        for(int c=1;c<5;c++)chapters.put(c,java.util.Collections.singletonList(new Bible.Verse(c+":1","text")));
        books.put(40,chapters);
        java.util.List<Bible.Verse> range=bible.collectionPassage(40,5,3,7,29);
        assertEquals(109,range.size());assertEquals("5:3",range.get(0).ref);assertEquals("7:29",range.get(108).ref);
        assertEquals(10,bible.collectionPassage(40,5,3,5,12).size());
        assertEquals(1,bible.collectionPassage(40,7,29,7,29).size());
    }
}
