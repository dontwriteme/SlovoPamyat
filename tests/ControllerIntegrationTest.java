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
    private int renders;
    private String message;
    @Before public void setup(){
        store=new LocalStore(RuntimeEnvironment.getApplication());
        preferences=RuntimeEnvironment.getApplication().getSharedPreferences("card_sessions",0);
        source=new FlashcardSessionController.Source(){public LocalStore store(){return store;}public SharedPreferences preferences(){return preferences;}};
        deck=store.newCardDeck("Controller fixture");ids=new ArrayList<>();
        for(int i=0;i<3;i++)ids.add(store.addCustomFlashcard(deck,"Card "+i,"Saved text "+i));
    }
    @After public void close(){store.close();}
    private FlashcardSessionController controller(){FlashcardSessionController result=new FlashcardSessionController(source);result.selectedDeck=deck;return result;}
    private StudySessionController study(){return new StudySessionController(new StudySessionController.Ports(){
        public LocalStore store(){return store;}
        public SharedPreferences studyPreferences(){return RuntimeEnvironment.getApplication().getSharedPreferences("study_session",0);}
        public SharedPreferences runPreferences(){return RuntimeEnvironment.getApplication().getSharedPreferences("unified_library_ui",0);}
        public SharedPreferences gapPreferences(){return RuntimeEnvironment.getApplication().getSharedPreferences("gap_rotation",0);}
        public boolean collectionQueueActive(){return false;}
        public int lessonTileLimit(){return 14;}
        public void render(){renders++;}
        public void toast(String text){message=text;}
    });}
    private LocalStore.Entry studyEntry(){
        long id=store.add(store.newGroup("Study"),"Text","Пользовательский текст",Arrays.asList(
            new Bible.Verse("1:1","Слово истины"),new Bible.Verse("1:2","Слово живо")));
        return store.entry(id);
    }
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
    @Test public void lessonResetKeepsTheExistingMilestoneToExerciseMapping(){
        StudySessionController session=study();LocalStore.Entry entry=studyEntry();session.selectedEntry=entry.id;
        StudySessionController.TrainingMode[] modes={StudySessionController.TrainingMode.READ,StudySessionController.TrainingMode.HIDE,
            StudySessionController.TrainingMode.ORDER,StudySessionController.TrainingMode.TYPE,StudySessionController.TrainingMode.TYPE,StudySessionController.TrainingMode.TYPE};
        for(int completed=0;completed<=5;completed++){
            if(completed>0)store.finishExercise(entry.id,0,completed);
            session.draft="unfinished";session.lessonWriteHadFeedback=true;session.resetTrainingStep(entry);
            assertEquals(modes[completed],session.trainingMode);assertEquals(completed==1?25:0,session.mask);
            assertEquals("",session.draft);assertFalse(session.lessonWriteHadFeedback);
        }
    }
    @Test public void lockedVerseKeepsAnswerAndAllowedTransitionSavesNewCursor(){
        StudySessionController session=study();LocalStore.Entry entry=studyEntry();session.selectedEntry=entry.id;session.draft="pending";
        session.moveStep(entry,1);assertEquals(0,session.stepCursor);assertEquals("pending",session.draft);assertNotNull(message);assertEquals(0,renders);
        store.finishExercise(entry.id,0,5);session.moveStep(entry,1);
        assertEquals(1,session.stepCursor);assertEquals("",session.draft);assertEquals(1,renders);
        assertEquals(1,RuntimeEnvironment.getApplication().getSharedPreferences("study_session",0).getInt("entry_"+entry.id+"_step",-1));
    }
    @Test public void redrawReusesTheSameGapMaskWithoutAdvancingItsHistory(){
        StudySessionController session=study();session.selectedEntry=studyEntry().id;session.lessonSeed=123;
        session.trainingMode=StudySessionController.TrainingMode.ORDER;
        String[] tokens="один два три четыре пять шесть семь восемь девять десять".split(" ");
        int[] first=session.lessonMissingPositions(tokens,LessonCurriculumLogic.Variant.GAPS);
        SharedPreferences gaps=RuntimeEnvironment.getApplication().getSharedPreferences("gap_rotation",0);
        String key=GapRotationLogic.identity(tokens),saved=gaps.getString(key,"");
        assertArrayEquals(first,session.lessonMissingPositions(tokens,LessonCurriculumLogic.Variant.GAPS));assertEquals(saved,gaps.getString(key,""));
        session.resetLessonTiles();session.lessonMissingPositions(tokens,LessonCurriculumLogic.Variant.GAPS);
        assertEquals(2*GapRotationLogic.amount(tokens.length),Arrays.stream(GapRotationLogic.decodeCounts(gaps.getString(key,""),tokens.length)).sum());
    }
    @Test public void assistedAnswerDoesNotMasterAVerseAndUnaidedPerfectRecallDoes(){
        StudySessionController session=study();LocalStore.Entry entry=studyEntry();session.selectedEntry=entry.id;
        session.reviewMode=true;session.testAssisted=true;session.draft="слово ИСТИНЫ!";
        StudyPlanLogic.Step step=new StudyPlanLogic.Step(0,1,"1:1");session.checkAnswer(entry,step,false);
        assertFalse(session.passed);assertFalse(store.isStepMastered(entry.id,"VERSE",0,1));assertEquals(1,renders);
        session.testAssisted=false;session.checkAnswer(entry,step,false);
        assertTrue(session.passed);assertTrue(store.isStepMastered(entry.id,"VERSE",0,1));assertEquals(2,renders);
        assertEquals(0,store.entry(entry.id).days);assertEquals(0,store.entry(entry.id).repetitions);
    }
}
