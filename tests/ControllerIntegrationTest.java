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
    private Screen readerScreen=Screen.BOOKS;
    private int searchViewUpdates;
    private final List<BibleReaderController> readers=new ArrayList<>();
    @Before public void setup(){
        store=new LocalStore(RuntimeEnvironment.getApplication());
        preferences=RuntimeEnvironment.getApplication().getSharedPreferences("card_sessions",0);
        source=new FlashcardSessionController.Source(){public StudyRepository study(){return store.study();}public FlashcardRepository flashcards(){return store.flashcards();}public SharedPreferences preferences(){return preferences;}};
        deck=store.newCardDeck("Controller fixture");ids=new ArrayList<>();
        for(int i=0;i<3;i++)ids.add(store.addCustomFlashcard(deck,"Card "+i,"Saved text "+i));
    }
    @After public void close(){for(BibleReaderController reader:readers)reader.close();store.close();}
    private FlashcardSessionController controller(){FlashcardSessionController result=new FlashcardSessionController(source);result.selectedDeck=deck;return result;}
    private StudySessionController study(){return new StudySessionController(new StudySessionController.Ports(){
        public StudyRepository study(){return store.study();}
        public ProgressRepository progress(){return store.progress();}
        public CollectionRepository collections(){return store.collections();}
        public SharedPreferences studyPreferences(){return RuntimeEnvironment.getApplication().getSharedPreferences("study_session",0);}
        public SharedPreferences runPreferences(){return RuntimeEnvironment.getApplication().getSharedPreferences("unified_library_ui",0);}
        public SharedPreferences gapPreferences(){return RuntimeEnvironment.getApplication().getSharedPreferences("gap_rotation",0);}
        public boolean collectionQueueActive(){return false;}
        public int lessonTileLimit(){return 14;}
        public Screen screen(){return readerScreen;}
        public void navigate(Screen destination){readerScreen=destination;renders++;}
        public boolean collectionStepPending(){return false;}
        public void collectionStepStarted(){}
        public boolean collectionQueueReady(){return false;}
        public boolean cardStudyActive(){return false;}
        public void advanceCollection(){}
        public void advanceCardStudy(){}
        public void saveCardCheckpoint(){}
        public void leaveLesson(){readerScreen=Screen.HOME;}
        public void writingErrors(LocalStore.Entry entry,String expected,String actual,boolean detailed){message=actual;}
        public void markInlineAnswers(boolean[] correct){}
        public void render(){renders++;}
        public void toast(String text){message=text;}
    });}
    private LocalStore.Entry studyEntry(){
        long id=store.add(store.newGroup("Study"),"Text","Пользовательский текст",Arrays.asList(
            new Bible.Verse("1:1","Слово истины"),new Bible.Verse("1:2","Слово живо")));
        return store.entry(id);
    }
    private static final class SearchQueue extends java.util.concurrent.AbstractExecutorService {
        final List<Runnable> tasks=new ArrayList<>();boolean stopped;
        public void execute(Runnable task){tasks.add(task);}
        public void shutdown(){stopped=true;}
        public List<Runnable> shutdownNow(){stopped=true;return tasks;}
        public boolean isShutdown(){return stopped;}
        public boolean isTerminated(){return stopped;}
        public boolean awaitTermination(long timeout,java.util.concurrent.TimeUnit unit){return stopped;}
    }
    private BibleReaderController reader(SearchQueue queue){
        BibleReaderController result=new BibleReaderController(new BibleReaderController.Ports(){
            public Screen screen(){return readerScreen;}
            public boolean destroyed(){return false;}
            public android.content.Context applicationContext(){return RuntimeEnvironment.getApplication();}
            public void postToUi(Runnable callback){new android.os.Handler(android.os.Looper.getMainLooper()).post(callback);}
            public boolean replaceVisibleSearch(List<BibleSearchLogic.Hit> hits,String query,String text){searchViewUpdates++;return false;}
            public void renderSearchKeepingInput(){searchViewUpdates++;}
            public void navigate(Screen destination){readerScreen=destination;}
            public void clearSavedScroll(Screen destination){}
            public int currentScroll(){return 321;}
            public SharedPreferences chapterPreferences(){return RuntimeEnvironment.getApplication().getSharedPreferences("bible_last_chapter",0);}
            public void synchronizeCollections(Bible bible){}
            public void contentReady(){}
        },queue);readers.add(result);return result;
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
        session.checked=false;session.testAssisted=false;session.checkAnswer(entry,step,false);
        assertTrue(session.passed);assertTrue(store.isStepMastered(entry.id,"VERSE",0,1));assertEquals(2,renders);
        assertEquals(0,store.entry(entry.id).days);assertEquals(0,store.entry(entry.id).repetitions);
    }
    @Test public void readerAndCollectionBoundarySelectionRemainIndependent(){
        BibleReaderController reader=reader(new SearchQueue());reader.selectedBook=19;reader.selectedChapter=118;reader.readerVerseNumber=99;
        reader.passageSelection.openBook(19);reader.passageSelection.active=true;reader.selectVerse(false,1);
        assertEquals(Screen.BIBLE_PICKER,readerScreen);assertEquals(99,reader.readerVerseNumber);
        int[] bounds=reader.passageSelection.bounds();reader.selectVerse(true,176);
        assertEquals(Screen.CHAPTER,readerScreen);assertEquals(176,reader.readerVerseNumber);assertEquals(176,reader.focusVerse);
        assertArrayEquals(bounds,reader.passageSelection.bounds());
    }
    @Test public void closingReaderCancelsDebouncedWorkAndRejectsLateQueries(){
        SearchQueue queue=new SearchQueue();BibleReaderController reader=reader(queue);reader.onQueryChanged("бог");
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(299));assertTrue(queue.tasks.isEmpty());
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1));assertEquals(1,queue.tasks.size());
        java.util.concurrent.Future<?> pending=reader.bibleSearchTask;int updates=searchViewUpdates;reader.close();
        assertTrue(pending.isCancelled());assertTrue(queue.stopped);for(Runnable task:queue.tasks)task.run();
        reader.onQueryChanged("запоздалый запрос");assertEquals("бог",reader.bibleSearchQuery);assertEquals(updates,searchViewUpdates);
    }
    @Test public void pendingSearchDoesNotStartOnAnotherScreen(){
        SearchQueue queue=new SearchQueue();BibleReaderController reader=reader(queue);reader.bibleSearchQuery="бог";reader.bibleSearchNeedsRefresh=true;
        reader.resumeBibleSearchIfNeeded();readerScreen=Screen.HOME;Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(queue.tasks.isEmpty());
        readerScreen=Screen.BOOKS;reader.bibleSearchNeedsRefresh=true;reader.resumeBibleSearchIfNeeded();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(1,queue.tasks.size());assertEquals("бог",reader.bibleSearchQuery);
    }
    private long collectionPicker=-1,openedEntry=-1,clearedRun=-1;
    private CollectionController collections(){return new CollectionController(new CollectionController.Ports(){
        public StudyRepository study(){return store.study();}
        public ProgressRepository progress(){return store.progress();}
        public FlashcardRepository flashcards(){return store.flashcards();}
        public CollectionRepository collections(){return store.collections();}
        public SharedPreferences activePreferences(){return RuntimeEnvironment.getApplication().getSharedPreferences("unified_library_ui",0);}
        public SharedPreferences queuePreferences(){return RuntimeEnvironment.getApplication().getSharedPreferences("collection_queue",0);}
        public Bible bible(){return new Bible();}
        public boolean studyPicker(){return true;}
        public long pickerTargetId(){return collectionPicker;}
        public int readerBook(){return 1;}
        public void stopCardStudy(){}
        public boolean hasPendingGroup(LocalStore.Entry entry){return false;}
        public void openTraining(long entry){openedEntry=entry;}
        public void navigate(Screen destination){readerScreen=destination;}
        public void toast(String text){message=text;}
        public void clearRunHistory(long collection){clearedRun=collection;}
        public boolean stopped(){return false;}
    });}
    @Test public void onlyFirstCollectionIsSelectedImplicitlyAndRenameKeepsIdentity(){
        CollectionController actions=collections();LocalStore.UnifiedCollection first=actions.createUnifiedCollection("First");
        LocalStore.UnifiedCollection second=actions.createUnifiedCollection("Second");assertEquals(first.id,actions.activeUnifiedId);
        actions.renameUnifiedCollection(second.id,"Renamed");assertEquals("Renamed",store.unifiedCollection(second.id).title);
        actions.activateUnified(second.id);actions.deleteUnifiedCollection(second.id);
        assertEquals(first.id,actions.activeUnifiedId);assertNotNull(store.unifiedCollection(first.id));
    }
    @Test public void clearingCollectionPreservesStudyProgressAndDelegatesOnlyItsRunCleanup(){
        CollectionController actions=collections();LocalStore.UnifiedCollection collection=actions.createUnifiedCollection("Saved");
        long entry=store.add(collection.groupId,"Saved","Пользовательский текст",Collections.singletonList(new Bible.Verse("1:1","Слово живо")));
        store.finishExercise(entry,0,5);actions.clearUnifiedCollection(collection.id);
        assertEquals(collection.id,clearedRun);assertNotNull(store.entry(entry));assertEquals(5,store.exerciseDone(entry,0));
        assertTrue(store.entries(collection.groupId).isEmpty());assertNotNull(store.unifiedCollection(collection.id));
    }
    @Test public void insertionReorderAndRemovalKeepLastSurvivingAnchor(){
        CollectionController actions=collections();LocalStore.Entry entry=studyEntry();long group=entry.groupId;
        long second=store.add(group,"Second","Пользовательский текст",Collections.singletonList(new Bible.Verse("1:1","Два")));
        long third=store.add(group,"Third","Пользовательский текст",Collections.singletonList(new Bible.Verse("1:1","Три")));
        store.reorderStudy(group,Arrays.asList(entry.id,second,third));
        actions.repositionStudyAfter(group,third,entry.id);assertEquals(third,store.entries(group).get(1).id);
        actions.studyInsertionTrail.addAll(Arrays.asList(entry.id,third));actions.insertAfterEntryId=third;
        actions.removedInsertionCandidate(third,false);assertEquals(entry.id,actions.insertAfterEntryId);
        actions.clearInsertionContext();assertEquals(-1,actions.insertAfterEntryId);assertTrue(actions.studyInsertionTrail.isEmpty());
        assertEquals(second,store.entries(group).get(2).id);
    }
    @Test public void collectionQueueRestoresIndependentEntriesAndContinuesInOrder(){
        CollectionController actions=collections();LocalStore.Entry entry=studyEntry();
        long second=store.add(entry.groupId,"Second","Пользовательский текст",Collections.singletonList(new Bible.Verse("1:1","Два")));
        store.reorderStudy(entry.groupId,Arrays.asList(entry.id,second));
        actions.startStudyCollection(entry.groupId);assertEquals(entry.id,openedEntry);assertTrue(actions.executingCollectionQueue);
        CollectionController restored=collections();restored.restoreCollectionQueue();assertEquals(Arrays.asList(entry.id,second),restored.collectionQueue);
        assertTrue(restored.resumeCollectionTraining());assertEquals(entry.id,openedEntry);restored.advanceStudyCollection();assertEquals(second,openedEntry);
        restored.advanceStudyCollection();assertEquals(Screen.HOME,readerScreen);assertTrue(restored.collectionQueue.isEmpty());
    }

    @Test public void correctedWritingAfterFeedbackRequiresAFreshUnaidedAttempt(){
        StudySessionController session=study();LocalStore.Entry entry=studyEntry();store.finishExercise(entry.id,0,3);
        session.selectedEntry=entry.id;session.activeEntry=entry;session.activeStep=new StudyPlanLogic.Step(0,1,"1:1");
        session.trainingMode=StudySessionController.TrainingMode.TYPE;session.draft="Неправильный ответ";session.validateLessonWriting(entry);
        assertTrue(session.lessonWriteHadFeedback);assertFalse(session.passed);assertEquals(3,store.exerciseDone(entry.id,0));
        session.draft="Слово истины";session.validateLessonWriting(entry);
        assertFalse(session.lessonWriteHadFeedback);assertFalse(session.passed);assertEquals("",session.draft);
        assertFalse(store.isStepMastered(entry.id,"VERSE",0,1));assertEquals(3,store.exerciseDone(entry.id,0));
        session.draft="Слово истины";session.validateLessonWriting(entry);
        assertTrue(session.passed);assertTrue(session.checked);assertEquals(5,store.exerciseDone(entry.id,0));assertTrue(store.isStepMastered(entry.id,"VERSE",0,1));
    }
    @Test public void combinedReviewRecordsEachVerseWithoutMasteringASyntheticChain(){
        StudySessionController session=study();LocalStore.Entry entry=studyEntry();
        LocalStore.DueItem due=new LocalStore.DueItem(entry.id,"VERSE_RUN",0,2,0);session.openReview(due);
        session.activeEntry=entry;session.activeStep=session.forcedStep;session.draft="Слово истины Слово живо";session.validateLessonWriting(entry);
        assertTrue(session.passed);assertFalse(store.isStepMastered(entry.id,"CHAIN",0,2));
        try(android.database.Cursor rows=store.getReadableDatabase().rawQuery("SELECT first_line,last_line,last_score FROM review_schedule WHERE entry_id=? ORDER BY first_line",new String[]{String.valueOf(entry.id)})){
            assertEquals(2,rows.getCount());assertTrue(rows.moveToFirst());assertEquals(0,rows.getInt(0));assertEquals(1,rows.getInt(1));
            assertTrue(rows.moveToNext());assertEquals(1,rows.getInt(0));assertEquals(2,rows.getInt(1));
        }
        assertEquals(0,store.exerciseDone(entry.id,0));
    }
    @Test public void finishingReviewQueueClearsOnlyItsCheckpointAndReturnsHome(){
        StudySessionController session=study();LocalStore.Entry entry=studyEntry();
        session.reviewQueue.add(new LocalStore.DueItem(entry.id,"VERSE",0,1,0));session.openReview(session.reviewQueue.get(0));
        SharedPreferences saved=RuntimeEnvironment.getApplication().getSharedPreferences("study_session",0);
        saved.edit().putBoolean("review_pending",true).putString("entry_"+entry.id+"_draft","ordinary draft").apply();
        session.advanceReviewSession();assertEquals(Screen.HOME,readerScreen);assertFalse(session.reviewMode);assertTrue(session.reviewQueue.isEmpty());
        assertFalse(saved.getBoolean("review_pending",false));assertEquals("ordinary draft",saved.getString("entry_"+entry.id+"_draft",""));
    }

    private int attempts(long id){
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM review_attempts WHERE entry_id=?",new String[]{String.valueOf(id)})){c.moveToFirst();return c.getInt(0);}
    }
    @Test public void successfulAnswerReplayAfterLostUiCheckpointDoesNotRecordAgain(){
        StudySessionController session=study();LocalStore.Entry entry=studyEntry();
        session.selectedEntry=entry.id;session.activeEntry=entry;session.activeStep=new StudyPlanLogic.Step(0,1,"1:1");
        session.trainingMode=StudySessionController.TrainingMode.TYPE;session.draft="Слово истины";
        android.os.Bundle before=session.saveState();session.validateLessonWriting(entry);
        assertTrue(session.passed);assertEquals(1,attempts(entry.id));
        StudySessionController recreated=study();recreated.restoreState(before);recreated.validateLessonWriting(entry);
        assertTrue(recreated.passed);assertEquals(1,attempts(entry.id));
        recreated.validateLessonWriting(entry);assertEquals(1,attempts(entry.id));
    }
    @Test public void rejectedMasteryRollsBackWholeAnswerAndRetryCommitsOnce(){
        StudySessionController session=study();LocalStore.Entry entry=studyEntry();
        session.selectedEntry=entry.id;session.activeStep=new StudyPlanLogic.Step(0,1,"1:1");
        session.trainingMode=StudySessionController.TrainingMode.TYPE;session.draft="Слово истины";
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_mastery BEFORE INSERT ON stage_progress BEGIN SELECT RAISE(ABORT,'injected-mastery'); END");
        session.validateLessonWriting(entry);assertFalse(session.passed);assertEquals(0,attempts(entry.id));
        assertFalse(store.isStepMastered(entry.id,"VERSE",0,1));assertEquals(0,store.exerciseDone(entry.id,0));
        store.getWritableDatabase().execSQL("DROP TRIGGER reject_mastery");session.validateLessonWriting(entry);
        assertTrue(session.passed);assertEquals(1,attempts(entry.id));
    }
    @Test public void changedMaterialCannotReceiveAnOldAnswer(){
        StudySessionController session=study();LocalStore.Entry entry=studyEntry();
        session.selectedEntry=entry.id;session.activeStep=new StudyPlanLogic.Step(0,1,"1:1");
        session.trainingMode=StudySessionController.TrainingMode.TYPE;session.draft="Слово истины";
        store.study().reviseStudyPassage(entry.id,entry.reference,Arrays.asList(new Bible.Verse("1:1","Другой текст")));
        session.validateLessonWriting(entry);assertFalse(session.passed);assertEquals(0,attempts(entry.id));
    }
    @Test public void durableCheckpointKeepsInlineAnswersAssistanceAndCompletedFeedback(){
        StudySessionController session=study();LocalStore.Entry entry=studyEntry();session.selectedEntry=entry.id;
        session.inlineDrafts=new String[]{"слово","истины"};session.testAssisted=true;
        session.checked=true;session.passed=false;session.lastAccuracy=75;session.lastExpected="Слово истины";
        session.saveTrainingSession();StudySessionController restored=study();assertTrue(restored.restoreTrainingSession(entry.id));
        assertArrayEquals(session.inlineDrafts,restored.inlineDrafts);assertTrue(restored.testAssisted);
        assertTrue(restored.checked);assertFalse(restored.passed);assertEquals(75,restored.lastAccuracy);
    }

    @Test public void pendingReviewReplayAfterCommitUsesDurableIdentity(){
        LocalStore.Entry entry=studyEntry();StudySessionController first=study();
        first.openReview(new LocalStore.DueItem(entry.id,"VERSE",0,1,1700000000000L));
        first.activeStep=first.forcedStep;first.draft="Слово истины";first.validateLessonWriting(entry);
        assertEquals(1,attempts(entry.id));
        // Simulate death after SQLite commit but before review_pending removal reaches disk.
        RuntimeEnvironment.getApplication().getSharedPreferences("study_session",0).edit().putBoolean("review_pending",true).commit();
        StudySessionController restored=study();restored.startReviewSession();restored.activeStep=restored.forcedStep;
        assertEquals("Слово истины",restored.draft);restored.validateLessonWriting(entry);
        assertTrue(restored.passed);assertEquals(1,attempts(entry.id));
    }

}
