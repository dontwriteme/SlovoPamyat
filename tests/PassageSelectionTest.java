package org.biblememory.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class PassageSelectionTest {
    private PassageSelection start(){PassageSelection s=new PassageSelection();s.collectionId=7;s.openBook(40);s.active=true;return s;}
    @Test public void firstPointDoesNotCompleteRange(){
        PassageSelection s=start();s.tap(5,3);
        assertFalse(s.complete());assertTrue(s.contains(5,3));assertFalse(s.contains(5,4));
    }
    @Test public void rangeIncludesExactlyItsBoundaries(){
        PassageSelection s=start();s.tap(5,3);s.tap(5,12);
        assertTrue(s.complete());assertArrayEquals(new int[]{5,3,5,12},s.bounds());
        for(int i=1;i<=48;i++)assertEquals(i>=3&&i<=12,s.contains(5,i));
        assertTrue(s.endpoint(5,3));assertTrue(s.endpoint(5,12));assertFalse(s.endpoint(5,4));
    }
    @Test public void chapterNavigationKeepsAnchor(){
        PassageSelection s=start();s.tap(5,3);s.chapter=6;s.openBook(40);s.chapter=7;s.tap(7,29);
        assertArrayEquals(new int[]{5,3,7,29},s.bounds());
        assertTrue(s.contains(6,34));assertFalse(s.contains(5,2));assertFalse(s.contains(8,1));
    }
    @Test public void reversedRangeAndBothEdits(){
        PassageSelection s=start();s.tap(7,29);s.tap(5,3);
        assertArrayEquals(new int[]{5,3,7,29},s.bounds());
        s.tap(6,34);assertArrayEquals(new int[]{5,3,6,34},s.bounds());
        s.changingStart=true;s.tap(5,8);assertArrayEquals(new int[]{5,8,6,34},s.bounds());
        s.changingStart=true;s.tap(7,1);assertArrayEquals(new int[]{6,34,7,1},s.bounds());
    }
    @Test public void identicalPointsAreASingleVerse(){
        PassageSelection s=start();s.tap(5,3);s.tap(5,3);
        assertTrue(s.complete());assertArrayEquals(new int[]{5,3,5,3},s.bounds());
    }
    @Test public void cancelAndBookChangeRemoveStaleSelection(){
        PassageSelection s=start();s.tap(5,3);s.cancel();
        assertFalse(s.active);assertFalse(s.contains(5,3));assertFalse(s.complete());
        s.active=true;s.tap(5,3);s.openBook(41);
        assertFalse(s.active);assertFalse(s.complete());assertEquals(41,s.book);
    }
    @Test public void ordinaryTextTapsDoNothing(){
        PassageSelection s=start();s.cancel();s.tap(1,1);assertEquals(-1,s.startChapter);
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
