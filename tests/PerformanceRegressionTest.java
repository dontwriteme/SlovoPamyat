package org.biblememory.app;

import android.database.*;
import android.database.sqlite.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import static org.junit.Assert.*;

/** Isolated repeatable fixture; durations are observations, never pass/fail thresholds. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class PerformanceRegressionTest {
    private static final class Queries implements SQLiteDatabase.CursorFactory {
        int count;
        public Cursor newCursor(SQLiteDatabase db,SQLiteCursorDriver driver,String table,SQLiteQuery query){
            count++;return new SQLiteCursor(driver,table,query);
        }
    }
    @Test public void masteryReadScalesWithoutPerVerseLibraryScans(){
        for(int size:new int[]{10,100,1000}){
            Queries queries=new Queries();
            try(LocalStore store=new LocalStore(RuntimeEnvironment.getApplication(),queries)){
                long group=store.newGroup("Performance "+size);List<Bible.Verse> lines=new ArrayList<>();
                for(int i=0;i<size;i++)lines.add(new Bible.Verse("1:"+(i+1),"Текст "+i));
                long id=store.add(group,"Fixture","Матфея 1:1",lines);
                long other=store.add(group,"Shared","Луки 1:1",lines);
                store.markMastered(other,0,size/2,100,"VERSE");
                queries.count=0;long start=System.nanoTime();
                int total=store.masteryCount(id);long elapsed=System.nanoTime()-start;
                assertEquals(size/2,total);assertTrue("Per-verse query amplification: "+queries.count,queries.count<=3);
                System.out.println("MASTERY size="+size+" queries="+queries.count+" ns="+elapsed);
                store.getWritableDatabase().delete("groups_","id=?",new String[]{String.valueOf(group)});
            }
        }
    }
    @Test public void longAnswerFixture(){
        String original=String.join(" ",Collections.nCopies(3000,"Слово истины"));
        String changed=original.substring(0,original.length()/2)+" ошибка "+original.substring(original.length()/2);
        for(String input:Arrays.asList(original,changed)){
            long start=System.nanoTime();int accuracy=ReviewLogic.accuracy(original,input);
            long elapsed=System.nanoTime()-start;
            System.out.println("ANSWER words=6000 exact="+input.equals(original)+" accuracy="+accuracy+" ns="+elapsed);
            assertEquals(100,accuracy); // Existing rounded score; strict acceptance is tested separately.
        }
    }
    @Test public void snapshotMatchesPointReadsAndReflectsWritesWithoutStaleCache(){
        try(LocalStore store=new LocalStore(RuntimeEnvironment.getApplication())){
            long group=store.newGroup("Equivalence");
            List<Bible.Verse> lines=Arrays.asList(new Bible.Verse("1:1","Первый"),new Bible.Verse("1:2","Второй"),new Bible.Verse("1:1","Первый"));
            long canonical=store.add(group,"Canonical","Матфея 1:1",lines);
            long shared=store.add(group,"Shared","Луки 1:1",lines);
            long custom=store.add(group,"Custom","Свой текст",lines);
            store.markMastered(shared,0,1,100,"VERSE");store.markMastered(custom,1,2,100,"VERSE");
            for(long id:new long[]{canonical,shared,custom})checkSnapshot(store,id,lines.size());
            assertEquals(2,store.masteryCount(canonical));assertEquals(1,store.masteryCount(custom));
            store.markMastered(canonical,1,2,100,"VERSE");checkSnapshot(store,canonical,3);
            store.getWritableDatabase().delete("entries","id=?",new String[]{String.valueOf(shared)});
            checkSnapshot(store,canonical,3);assertEquals(1,store.masteryCount(canonical));
        }
    }
    private void checkSnapshot(LocalStore store,long id,int size){
        int count=0,first=-1;
        for(int i=0;i<size;i++)if(store.isStepMastered(id,"VERSE",i,i+1))count++;else if(first<0)first=i;
        assertEquals(count,store.masteryCount(id));assertEquals(first<0?0:first,store.progress().firstUnmastered(id,size));
    }
    @Test public void targetedDeckLookupKeepsCountsMissingAndEmptyDecks(){
        try(LocalStore store=new LocalStore(RuntimeEnvironment.getApplication())){
            long empty=store.newCardDeck("Empty"),full=store.newCardDeck("Full");
            store.addCustomFlashcard(full,"Text","Words");
            assertEquals(0,store.flashcards().cardDeck(empty).count);
            assertEquals(1,store.flashcards().cardDeck(full).count);
            assertNull(store.flashcards().cardDeck(-1));
        }
    }

    @Test public void longDiffKeepsThePreviousRenderedPieces(){
        String expected=String.join(" ",Collections.nCopies(6000,"Слово истины"));
        String changed=expected.substring(0,expected.length()/2)+" ошибка "+expected.substring(expected.length()/2);
        for(String actual:Arrays.asList(expected,changed)){
            List<TypingDiffLogic.Piece> pieces=TypingDiffLogic.compare(expected,actual);
            int checksum=1;for(TypingDiffLogic.Piece p:pieces)checksum=31*checksum+Objects.hash(p.kind.ordinal(),p.text);
            assertEquals(actual.equals(expected)?2019403520:-54854086,checksum);
            assertEquals(actual.equals(expected)?1:24000,pieces.size());
        }
    }

}
