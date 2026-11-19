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
        int[] first=result.actualMarks.get(0);
        assertEquals("начеле","В начеле сатворил Бог небо и землю".substring(first[0],first[1]));
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
