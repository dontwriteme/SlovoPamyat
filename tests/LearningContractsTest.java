package org.biblememory.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Existing learning rules, frozen before introducing controllers/repositories. */
public class LearningContractsTest {
    private static final long NOW=1700000000000L,DAY=86400000L;
    @Test public void persistedLessonMilestonesRetainTheirMeaning(){
        LessonCurriculumLogic.Phase[] expected={LessonCurriculumLogic.Phase.INTRO,LessonCurriculumLogic.Phase.BUILD,
            LessonCurriculumLogic.Phase.BUILD,LessonCurriculumLogic.Phase.WRITE,LessonCurriculumLogic.Phase.WRITE,LessonCurriculumLogic.Phase.COMPLETE};
        for(int n=0;n<=5;n++){
            assertEquals(expected[n],LessonCurriculumLogic.phase(n,false));
            assertEquals(LessonCurriculumLogic.Phase.WRITE,LessonCurriculumLogic.phase(n,true));
        }
    }
    @Test public void verseLengthKeepsExistingExerciseThresholds(){
        assertEquals(Collections.singletonList(LessonCurriculumLogic.Variant.FULL),LessonCurriculumLogic.variants(8));
        assertEquals(Arrays.asList(LessonCurriculumLogic.Variant.GAPS,LessonCurriculumLogic.Variant.FULL),LessonCurriculumLogic.variants(9));
        assertEquals(2,LessonCurriculumLogic.variants(20).size());assertEquals(3,LessonCurriculumLogic.variants(21).size());
    }
    @Test public void savedSeedRestoresTheSameGapExercise(){
        int[] first=LessonCurriculumLogic.missingPositions(25,LessonCurriculumLogic.Variant.GAPS,123);
        assertArrayEquals(first,LessonCurriculumLogic.missingPositions(25,LessonCurriculumLogic.Variant.GAPS,123));assertEquals(10,first.length);
        for(int i=0;i<first.length;i++){assertTrue(first[i]>=0&&first[i]<25);if(i>0)assertTrue(first[i]>first[i-1]);}
        assertArrayEquals(new int[]{0,1,2},LessonCurriculumLogic.missingPositions(7,LessonCurriculumLogic.Variant.ENDING,0));
        assertArrayEquals(new int[]{3,4,5,6},LessonCurriculumLogic.missingPositions(7,LessonCurriculumLogic.Variant.ENDING,1));
    }
    @Test public void exactRecallIgnoresCasePunctuationAndYoButNotWords(){
        assertTrue(LessonCurriculumLogic.exact("Ёлка — свет!","елка, СВЕТ."));
        assertEquals(100,ReviewLogic.accuracy("Ёлка — свет!","елка, СВЕТ."));
        assertFalse(LessonCurriculumLogic.exact("один два три","один три"));
        assertFalse(LessonCurriculumLogic.exact("",""));
        assertEquals(67,ReviewLogic.accuracy("один два три","один три"));
        assertEquals(75,ReviewLogic.accuracy("один два три","один два три четыре"));
    }
    @Test public void classicScheduleKeepsItsExistingIntervalsAndEase(){
        ReviewLogic.Schedule one=ReviewLogic.update(0,0,2.5,100,NOW,true);
        assertEquals(1,one.repetitions);assertEquals(1,one.intervalDays);assertEquals(2.6,one.ease,.00001);assertEquals(NOW+DAY,one.nextDue);
        ReviewLogic.Schedule two=ReviewLogic.update(one.repetitions,one.intervalDays,one.ease,100,NOW+DAY,true);
        assertEquals(2,two.repetitions);assertEquals(3,two.intervalDays);
        ReviewLogic.Schedule three=ReviewLogic.update(two.repetitions,two.intervalDays,two.ease,100,NOW+4*DAY,true);
        assertEquals(3,three.repetitions);assertEquals(8,three.intervalDays);
        ReviewLogic.Schedule fail=ReviewLogic.update(3,8,2.8,50,NOW,true);
        assertEquals(0,fail.repetitions);assertEquals(1,fail.intervalDays);assertEquals(2.8,fail.ease,.00001);
    }
    @Test public void incompleteExerciseDoesNotAdvanceScheduleCounters(){
        ReviewLogic.Schedule state=ReviewLogic.update(4,14,2.4,100,NOW,false);
        assertEquals(4,state.repetitions);assertEquals(14,state.intervalDays);assertEquals(2.4,state.ease,.00001);assertEquals(NOW,state.nextDue);
    }
    @Test public void independentRecallUsesFixedDaysAndCapsAtSixty(){
        int level=-1;int[] expected={1,3,7,14,30,60,60};
        for(int days:expected){SpacedReviewLogic.State state=SpacedReviewLogic.update(level,100,false,NOW);
            assertEquals(days,state.intervalDays);assertEquals(NOW+days*DAY,state.due);level=state.level;}
        assertEquals(5,level);assertFalse(SpacedReviewLogic.due(NOW,NOW-1));assertTrue(SpacedReviewLogic.due(NOW,NOW));assertFalse(SpacedReviewLogic.due(0,NOW));
    }
    @Test public void assistedRecallNeverSuppliesAScheduleAndImperfectRecallResets(){
        assertNull(SpacedReviewLogic.update(4,100,true,NOW));assertNull(SpacedReviewLogic.update(4,0,true,NOW));
        SpacedReviewLogic.State state=SpacedReviewLogic.update(4,99,false,NOW);assertEquals(0,state.level);assertEquals(NOW+DAY,state.due);
    }
    @Test public void chapterAndChainPlansKeepOriginalOrderAndBoundaries(){
        List<String> refs=Arrays.asList("5:3","5:4","6:1","6:2","7:1");
        List<StudyPlanLogic.Step> chapters=StudyPlanLogic.build(refs,StudyPlanLogic.Stage.CHAPTER,true);
        assertEquals(4,chapters.size());assertRange(chapters.get(0),0,2);assertRange(chapters.get(1),2,4);assertRange(chapters.get(2),4,5);assertRange(chapters.get(3),0,5);
        List<StudyPlanLogic.Step> chains=StudyPlanLogic.build(refs,StudyPlanLogic.Stage.CHAIN,true);
        assertEquals(4,chains.size());assertRange(chains.get(0),0,3);assertRange(chains.get(3),3,5);
        assertEquals(Arrays.asList("5:3","5:4","6:1","6:2","7:1"),refs);
    }
    @Test public void longPsalmPlansKeepAll176VersePositions(){
        List<String> refs=new ArrayList<>();for(int n=1;n<=176;n++)refs.add("118:"+n);
        List<StudyPlanLogic.Step> verses=StudyPlanLogic.build(refs,StudyPlanLogic.Stage.VERSE,false);
        assertEquals(176,verses.size());for(int n=0;n<176;n++)assertRange(verses.get(n),n,n+1);
        assertEquals(175,StudyPlanLogic.build(refs,StudyPlanLogic.Stage.CHAIN,false).size());
        assertEquals(36,StudyPlanLogic.build(refs,StudyPlanLogic.Stage.SECTION,false).size());
        assertRange(StudyPlanLogic.build(refs,StudyPlanLogic.Stage.CHAPTER,false).get(0),0,176);
        for(StudyPlanLogic.Stage stage:StudyPlanLogic.Stage.values())assertTrue(StudyPlanLogic.build(Collections.emptyList(),stage,false).isEmpty());
    }
    @Test public void typingDiffKeepsMissingAndExtraWordsExplicit(){
        List<TypingDiffLogic.Issue> missing=TypingDiffLogic.issues("Свет и истина","Свет истина");
        assertEquals(1,missing.size());assertEquals("и",missing.get(0).expected);assertEquals("",missing.get(0).actual);
        List<TypingDiffLogic.Issue> extra=TypingDiffLogic.issues("Свет","Свет мира");assertEquals(1,extra.size());assertEquals("мира",extra.get(0).actual);
        assertTrue(TypingDiffLogic.issues("Ёлка, свет!","елка СВЕТ").isEmpty());
        assertTrue(TypingDiffLogic.compare("Свет и истина","Свет истина").stream().anyMatch(p->p.kind==TypingDiffLogic.Kind.MISSING));
    }
    @Test(timeout=5000) public void longTextDiffFallbackRetainsCorrectTextAndFindsAnError(){
        String expected="Слово истины ".repeat(300).trim();
        assertTrue(TypingDiffLogic.compare(expected,expected).stream().allMatch(p->p.kind==TypingDiffLogic.Kind.CORRECT));
        assertTrue(TypingDiffLogic.issues(expected,expected).isEmpty());
        String changed=expected.substring(0,expected.lastIndexOf(' ')+1)+"мира";
        assertFalse(TypingDiffLogic.issues(expected,changed).isEmpty());
        assertTrue(TypingDiffLogic.compare(expected,changed).stream().anyMatch(p->p.kind!=TypingDiffLogic.Kind.CORRECT));
    }
    @Test public void flashcardCyclesPreserveIdsAndAvoidImmediateBoundaryRepeat(){
        List<Long> ids=Arrays.asList(12L,27L,39L,42L);long previous=12;Random random=new Random(123);
        for(int i=0;i<20;i++){List<Long> cycle=FlashcardDeckLogic.shuffled(ids,previous,random);
            assertEquals(4,cycle.size());assertEquals(new HashSet<>(ids),new HashSet<>(cycle));assertNotEquals(previous,(long)cycle.get(0));previous=cycle.get(3);}
        assertEquals(Arrays.asList(12L,27L,39L,42L),ids);assertEquals(Collections.singletonList(12L),FlashcardDeckLogic.shuffled(Collections.singletonList(12L),12,random));
    }
    private void assertRange(StudyPlanLogic.Step step,int start,int end){assertEquals(start,step.start);assertEquals(end,step.end);}
    @Test public void answerEvaluationPreservesExistingNormalizationAndStrictWordChecks(){
        String expected="Ёлка — свет миру!";
        assertTrue(StudyAnswerEvaluation.lesson(expected,"елка СВЕТ миру",false).exact);
        for(String actual:Arrays.asList("елка цвет миру","елка миру","елка свет свет миру","елка свет миру всем","")){
            StudyAnswerEvaluation result=StudyAnswerEvaluation.lesson(expected,actual,false);
            assertFalse(result.exact);assertTrue(result.accuracy<100);
            assertFalse(TypingDiffLogic.issues(expected,actual).isEmpty());
        }
        String longText=String.join(" ",Collections.nCopies(500,"Слово истины"));
        assertTrue(StudyAnswerEvaluation.lesson(longText,longText,false).exact);
        assertFalse(StudyAnswerEvaluation.lesson(longText,longText+" лишнее",false).exact);
    }

}
