package org.biblememory.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnswerReviewLogicTest {
    private static final String VERSE="В начале сотворил Бог небо и землю.";

    @Test public void emptyAndOneLetterProduceOnlySummaries(){
        assertEquals(AnswerReviewLogic.Summary.EMPTY,compare(VERSE," ").summary);
        AnswerReviewLogic.Result shortAnswer=compare(VERSE,"а");
        assertEquals(AnswerReviewLogic.Summary.SHORT,shortAnswer.summary);
        assertTrue(shortAnswer.issues.isEmpty());
        assertTrue(shortAnswer.actualMarks.isEmpty());
    }

    @Test public void completeAnswerIgnoresPunctuationCaseAndYo(){
        assertTrue(compare(VERSE,VERSE).issues.isEmpty());
        assertTrue(compare("Твоё слово, свет!","ТВОЕ СЛОВО свет").issues.isEmpty());
        assertTrue(AcceptedTextLogic.exact("Она не годна","она негодна",true));
        assertFalse(AcceptedTextLogic.exact("Она не годна","она негодна",false));
    }

    @Test public void omissionsStayInOrderAndAdjacentWordsAreGrouped(){
        assertIssue(VERSE,"начале сотворил Бог небо и землю",AnswerReviewLogic.Kind.MISSING,"В","");
        assertIssue(VERSE,"В начале сотворил Бог небо и",AnswerReviewLogic.Kind.MISSING,"землю","");
        assertIssue(VERSE,"В начале сотворил Бог",AnswerReviewLogic.Kind.MISSING,"небо и землю","");
        AnswerReviewLogic.Result result=compare(VERSE,"В сотворил Бог и землю");
        assertEquals(2,result.issues.size());
        assertEquals("начале",result.issues.get(0).expected);
        assertEquals("небо",result.issues.get(1).expected);
    }

    @Test public void spellingAndExtrasAreSeparateWholeWordIssues(){
        assertIssue(VERSE,"В начале сатворил Бог небо и землю",AnswerReviewLogic.Kind.SPELLING,"сотворил","сатворил");
        assertIssue(VERSE,"В начале сотворил Бог действительно очень небо и землю",AnswerReviewLogic.Kind.EXTRA,"","действительно очень");
        AnswerReviewLogic.Result result=compare(VERSE,"В начеле сатворил Бог небо и землю");
        assertEquals(2,result.issues.size());
        for(AnswerReviewLogic.Issue issue:result.issues)assertEquals(AnswerReviewLogic.Kind.SPELLING,issue.kind);
        AnswerReviewLogic.Mark first=result.actualMarks.get(0);
        assertEquals("е",result.displayedActual.substring(first.start,first.end));
        assertEquals(AnswerReviewLogic.Kind.SPELLING,first.kind);
    }

    @Test public void reorderedWordsAreNotAcceptedAndRepeatsDoNotCascade(){
        AnswerReviewLogic.Result reordered=compare("свет мир вера надежда","свет вера мир надежда");
        assertEquals(2,reordered.issues.size());
        for(AnswerReviewLogic.Issue issue:reordered.issues)assertNotEquals(AnswerReviewLogic.Kind.SPELLING,issue.kind);
        assertIssue("свет свет мир свет","свет мир свет",AnswerReviewLogic.Kind.MISSING,"свет","");
    }

    @Test public void longPassageRetainsLocalizedEditAndBoundsWholesaleMismatch(){
        StringBuilder prefix=new StringBuilder(),suffix=new StringBuilder();
        for(int i=0;i<1200;i++){prefix.append("слово").append(i).append(' ');suffix.append("строка").append(i).append(' ');}
        assertIssue(prefix+"сотворил "+suffix,prefix+"сатворил "+suffix,
            AnswerReviewLogic.Kind.SPELLING,"сотворил","сатворил");
        assertEquals(AnswerReviewLogic.Summary.DIFFERENT,compare(prefix.toString(),suffix.toString()).summary);
    }

    @Test public void acceptedSynodalVariantDoesNotMaskAnUnrelatedSpellingMistake(){
        AnswerReviewLogic.Result result=AnswerReviewLogic.compare(
            "Она уже не годна для земли","она уже негодна для зимли",true);
        assertEquals(1,result.issues.size());
        assertEquals(AnswerReviewLogic.Kind.SPELLING,result.issues.get(0).kind);
        assertEquals("земли",result.issues.get(0).expected);
    }

    @Test public void referenceExampleMarksSpellingAndInsertsMissingWordsInPlace(){
        String actual="И сказала Бог будет свет и стол свет";
        AnswerReviewLogic.Result result=compare("И сказал Бог: да будет свет. И стал свет",actual);
        assertEquals("И сказала Бог да будет свет и стол свет",result.displayedActual);
        assertMark(result,AnswerReviewLogic.Kind.SPELLING,"а");
        assertMark(result,AnswerReviewLogic.Kind.MISSING,"да");
        assertMark(result,AnswerReviewLogic.Kind.SPELLING,"о");
        assertEquals(actual,result.displayedActual.replace("да ",""));
    }

    @Test public void multipleOmissionsRemainGroupedAndSeparateOmissionsStayInOrder(){
        AnswerReviewLogic.Result group=compare("В начале сотворил Бог небо и землю",
            "В начале сотворил Бог землю");
        assertEquals("В начале сотворил Бог небо и землю",group.displayedActual);
        assertMark(group,AnswerReviewLogic.Kind.MISSING,"небо и");
        AnswerReviewLogic.Result separate=compare("В начале сотворил Бог небо и землю",
            "В начале Бог и землю");
        assertEquals("В начале сотворил Бог небо и землю",separate.displayedActual);
        assertEquals(2,separate.actualMarks.size());
        assertEquals("сотворил",marked(separate,separate.actualMarks.get(0)));
        assertEquals("небо",marked(separate,separate.actualMarks.get(1)));
    }

    @Test public void extrasAreStruckSeparatelyFromSpellingAndRawAnswerStaysIntact(){
        String actual="В начеле сотворил Бог ещё небо и землю";
        AnswerReviewLogic.Result result=compare(VERSE,actual);
        assertEquals(actual,result.displayedActual);
        assertMark(result,AnswerReviewLogic.Kind.SPELLING,"е");
        assertMark(result,AnswerReviewLogic.Kind.EXTRA,"ещё");
    }

    @Test public void spellingMarksOnlyChangedLettersWithDeletionFallback(){
        assertMark(compare("сказал","сказала"),AnswerReviewLogic.Kind.SPELLING,"а");
        assertMark(compare("стал","стол"),AnswerReviewLogic.Kind.SPELLING,"о");
        assertMark(compare("сказал","скзал"),AnswerReviewLogic.Kind.SPELLING,"з");
        assertMark(compare("слово","солво"),AnswerReviewLogic.Kind.SPELLING,"ол");
    }

    @Test public void fallbackAndCorrectAnswerDoNotInventEdits(){
        assertEquals("а",compare(VERSE,"а").displayedActual);
        assertTrue(compare(VERSE,"а").actualMarks.isEmpty());
        assertEquals(" ",compare(VERSE," ").displayedActual);
        assertTrue(compare(VERSE,VERSE).actualMarks.isEmpty());
    }

    private String marked(AnswerReviewLogic.Result result,AnswerReviewLogic.Mark mark){
        return result.displayedActual.substring(mark.start,mark.end);
    }

    private void assertMark(AnswerReviewLogic.Result result,AnswerReviewLogic.Kind kind,String text){
        for(AnswerReviewLogic.Mark mark:result.actualMarks)
            if(mark.kind==kind && text.equals(marked(result,mark)))return;
        fail("Missing "+kind+" mark for "+text+" in "+result.displayedActual);
    }

    private AnswerReviewLogic.Result compare(String expected,String actual){
        return AnswerReviewLogic.compare(expected,actual,false);
    }

    private void assertIssue(String expected,String actual,AnswerReviewLogic.Kind kind,String wanted,String typed){
        AnswerReviewLogic.Result result=compare(expected,actual);
        assertEquals(AnswerReviewLogic.Summary.NONE,result.summary);
        assertEquals(1,result.issues.size());
        AnswerReviewLogic.Issue issue=result.issues.get(0);
        assertEquals(kind,issue.kind);
        assertEquals(wanted,issue.expected);
        assertEquals(typed,issue.actual);
    }
}
