package org.biblememory.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Regression contract: every accepted answer must also look correct in both UIs. */
public class OrthographyContractTest {
    private static void accepted(String expected,String typed,boolean special){
        assertTrue("Rejected: "+typed,AcceptedTextLogic.exact(expected,typed,special));
        String closest=AcceptedTextLogic.bestExpected(expected,typed,special);
        assertEquals("Accuracy for "+typed,100,ReviewLogic.accuracy(closest,typed));
        AnswerReviewLogic.Result review=AnswerReviewLogic.compare(expected,typed,special);
        assertTrue("False review marks: "+typed,review.issues.isEmpty());
        assertTrue("False underline: "+typed,review.actualMarks.isEmpty());
        for(TypingDiffLogic.Piece piece:TypingDiffLogic.compare(closest,typed))
            assertEquals("False legacy highlight: "+piece.text,
                TypingDiffLogic.Kind.CORRECT,piece.kind);
    }

    @Test public void exactVerseAndNormalizedPunctuationAreAlwaysAccepted(){
        String verse="В начале сотворил Бог небо и землю.";
        accepted(verse,verse,false);
        accepted(verse,"В НАЧАЛЕ   СОТВОРИЛ БОГ НЕБО И ЗЕМЛЮ",false);
        accepted(verse,"«В начале, сотворил Бог — небо и землю!»",false);
        accepted("слово — слово, и снова слово","слово слово и снова слово",false);
        accepted("кто-нибудь по-прежнему","кто нибудь по прежнему",false);
    }

    @Test public void stressedRussianWordsDoNotCreatePhantomErrors(){
        accepted("Слово Господа","Сло\u0301во Госпо\u0301да",false);
        accepted("Сло\u0301во Госпо\u0301да","Слово Господа",false);
        accepted("Бог возлюбил мир","Бог возлю\u0301бил мир",false);
        assertEquals(Arrays.asList("слово","господа"),
            ExerciseTextLogic.words("Сло\u0301во Госпо\u0301да"));
    }

    @Test public void yoCaseAndUnicodeWidthAreNeutral(){
        accepted("Твоё слово — свет","ТВОЕ СЛОВО СВЕТ",false);
        accepted("123 слова","１２３ СЛОВА",false);
        accepted("Ёж","еж",false);
    }

    @Test public void matthew513VariantWorksInLessonsAndMemory(){
        assertTrue(AcceptedTextLogic.matthew513Verse("Матфея 5:13"));
        assertFalse(AcceptedTextLogic.matthew513Verse("Матфея 5:14"));
        assertFalse(AcceptedTextLogic.matthew513Verse("Марка 5:13"));
        assertTrue(AcceptedTextLogic.matthew513("Матфея 5:12–14",
            Arrays.asList("5:12","5:13","5:14")));
        accepted("Она ни к чему негодна","Она ни к чему не годна",true);
        accepted("Она ни к чему не годна","Она ни к чему негодна",true);
        assertFalse(AcceptedTextLogic.exact("Она ни к чему негодна",
            "Она ни к чему не годна",false));
        assertEquals(100,ReviewLogic.accuracy(
            AcceptedTextLogic.bestExpected("Она ни к чему негодна",
                "Она ни к чему не годна",true),"Она ни к чему не годна"));
    }

    @Test public void separateGapsRequireTheRightWordInEachField(){
        assertTrue(InlineGapLogic.complete(Arrays.asList("землю","и"),
            new String[]{"ЗЕМЛЮ","и"},false));
        assertTrue(InlineGapLogic.complete(Collections.singletonList("негодна"),
            new String[]{"не годна"},true));
        assertFalse(InlineGapLogic.complete(Collections.singletonList("негодна"),
            new String[]{"не годна"},false));
        assertFalse(InlineGapLogic.complete(Arrays.asList("небо","землю"),
            new String[]{"небо землю",""},false));
        assertFalse(InlineGapLogic.complete(Arrays.asList("небо","землю"),
            new String[]{"небо","—"},false));
    }

    @Test public void realMistakesStillBlockCompletion(){
        String verse="В начале сотворил Бог небо и землю";
        for(String bad:new String[]{
            "В начале сотварил Бог небо и землю",
            "В начале сотворил Бог землю",
            "В начале сотворил небо Бог и землю",
            "В начале сотворил Бог небо и землю землю",
            "В начале сотворил Бог небо и землу"
        }) {
            assertFalse("False pass: "+bad,AcceptedTextLogic.exact(verse,bad,false));
            assertTrue("Accuracy overstated for: "+bad,ReviewLogic.accuracy(verse,bad)<100);
        }
    }

    @Test public void feedbackMarksAreAlwaysInsideDisplayedText(){
        String source="В начале сотворил Бог небо и землю";
        String[] words=source.split(" ");
        Random random=new Random(73199);
        for(int caseNumber=0;caseNumber<700;caseNumber++){
            List<String> edited=new ArrayList<>(Arrays.asList(words));
            for(int k=0;k<1+random.nextInt(3);k++){
                int index=random.nextInt(edited.size()+1);
                switch(random.nextInt(3)){
                    case 0:
                        if(index<edited.size())edited.remove(index);
                        break;
                    case 1:
                        edited.add(index,"лишнее");
                        break;
                    default:
                        if(index<edited.size())edited.set(index,"ошибко");
                        break;
                }
            }
            String actual=String.join(" ",edited);
            AnswerReviewLogic.Result diff=AnswerReviewLogic.compare(source,actual,false);
            for(AnswerReviewLogic.Mark mark:diff.actualMarks){
                assertTrue("Negative mark start for "+actual,mark.start>=0);
                assertTrue("Reversed mark for "+actual,mark.end>=mark.start);
                assertTrue("Out-of-bounds mark for "+actual,
                    mark.end<=diff.displayedActual.length());
            }
        }
    }

    @Test public void feedbackAndGradingAgreeForManyNormalizedPhrases(){
        List<String> examples=Arrays.asList(
            "В начале сотворил Бог небо и землю.",
            "И сказал Господь: да будет свет.",
            "Во Христе Иисусе — жизнь вечная!",
            "Твоё слово есть истина",
            "123 — 456, кто-нибудь — по-прежнему",
            "Мы любим Господа нашего");
        for(String expected:examples){
            accepted(expected,expected,false);
            String normalized=String.join(" ",ReviewLogic.words(expected));
            accepted(expected,normalized,false);
            accepted(expected,normalized.toUpperCase(Locale.ROOT),false);
            accepted(expected,"« "+normalized+" ». ",false);
        }
    }
}
