package org.biblememory.app;

import android.content.SharedPreferences;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import static org.junit.Assert.*;

/** Controller contracts exercise real state/storage without constructing Activity Views. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class ControllerIntegrationTest {
    private LocalStore store;
    private SharedPreferences preferences;
    private long deck;
    private List<Long> ids;
    private FlashcardSessionController.Source source;
    @Before public void setup(){
        store=new LocalStore(RuntimeEnvironment.getApplication());
        preferences=RuntimeEnvironment.getApplication().getSharedPreferences("card_sessions",0);
        source=new FlashcardSessionController.Source(){public LocalStore store(){return store;}public SharedPreferences preferences(){return preferences;}};
        deck=store.newCardDeck("Controller fixture");ids=new ArrayList<>();
        for(int i=0;i<3;i++)ids.add(store.addCustomFlashcard(deck,"Card "+i,"Saved text "+i));
    }
    @After public void close(){store.close();}
    private FlashcardSessionController controller(){FlashcardSessionController result=new FlashcardSessionController(source);result.selectedDeck=deck;return result;}
    @Test public void cardCheckpointRestoresExactOrderCursorAndDirection(){
        FlashcardSessionController first=controller();first.cardSequence.addAll(Arrays.asList(ids.get(2),ids.get(0),ids.get(1)));
        first.cardIndex=1;first.cardShowReference=false;first.cardRevealed=true;first.saveCardSession();
        FlashcardSessionController restored=controller();assertTrue(restored.startCardSession());
        assertEquals(first.cardSequence,restored.cardSequence);assertEquals(1,restored.cardIndex);
        assertFalse(restored.cardShowReference);assertFalse(restored.cardRevealed);
    }
    @Test public void staleCheckpointRebuildsOnlyFromExistingMaterial(){
        FlashcardSessionController first=controller();assertTrue(first.startCardSession());
        store.removeFlashcard(deck,ids.get(1));
        FlashcardSessionController restored=controller();assertTrue(restored.startCardSession());
        assertEquals(new HashSet<>(Arrays.asList(ids.get(0),ids.get(2))),new HashSet<>(restored.cardSequence));assertEquals(0,restored.cardIndex);
    }
    @Test public void nextCycleAvoidsRepeatingThePreviousLastCard(){
        FlashcardSessionController session=controller();session.cardRandom.setSeed(12);assertTrue(session.startCardSession());
        session.cardIndex=2;long last=session.cardSequence.get(2);session.cardRevealed=true;
        assertTrue(session.nextFlashcard());assertEquals(0,session.cardIndex);assertNotEquals(last,(long)session.cardSequence.get(0));
        assertEquals(new HashSet<>(ids),new HashSet<>(session.cardSequence));assertFalse(session.cardRevealed);
    }
    @Test public void emptyDeckDoesNotOverwriteAnotherDeckCheckpoint(){
        FlashcardSessionController session=controller();session.startCardSession();String saved=preferences.getString("deck_"+deck,"");
        session.selectedDeck=store.newCardDeck("Empty");assertFalse(session.startCardSession());
        assertEquals(saved,preferences.getString("deck_"+deck,""));
    }
    @Test public void unifiedCardSessionKeepsOnlySingleVerseOrCustomCards(){
        store.addFlashcard(deck,40,5,3,5,4,"Матфея 5:3–4","Range");
        store.addFlashcard(deck,40,5,5,5,5,"Матфея 5:5","Single");
        FlashcardSessionController session=controller();session.cardFromUnified=true;
        assertEquals(4,session.eligibleSessionCards().size());assertTrue(session.startCardSession());assertEquals(4,session.cardSequence.size());
    }
    @Test public void cardStudyKeepsIndependentEntriesAndReusesTheirIdentity(){
        FlashcardSessionController session=controller();FlashcardSessionController.StudyStart start=session.studyCardDeck(new Bible());
        assertNull(start.message);assertTrue(start.firstEntry>0);assertEquals(3,session.cardStudyQueue.size());
        List<Long> first=new ArrayList<>(session.cardStudyQueue);assertEquals(first.get(1).longValue(),session.advanceCardStudy());
        session.studyCardDeck(new Bible());assertEquals(first,session.cardStudyQueue);
        session.advanceCardStudy();session.advanceCardStudy();assertEquals(0,session.advanceCardStudy());assertEquals(-1,session.cardStudyCursor);assertEquals(deck,session.selectedDeck);
    }
}
