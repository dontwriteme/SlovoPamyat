package org.biblememory.app;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GapAnswerReviewTest {
    @Test public void mapsRepeatedWordsAndEmptyFieldsAcrossThreeVerses(){
        GapAnswerReview review=GapAnswerReview.create(Arrays.asList("5:3","5:4","5:5"),
            Arrays.asList("слово мир слово.","слово свет!","свет слово мир"),
            new int[]{0,2,3,4,6},Arrays.asList("слово","слово","слово","свет","слово"),
            Arrays.asList("слово","","ошибка","свет","слова"),false);
        assertNotNull(review);
        assertEquals(2,review.correctCount());
        assertEquals(3,review.errorCount());
        assertEquals("",review.verses().get(0).gaps().get(2).actual());
        assertEquals("ошибка",review.verses().get(1).gaps().get(0).actual());
        assertEquals("слова",review.verses().get(2).gaps().get(1).actual());
        assertEquals("слово.",review.verses().get(0).tokens().get(2));
    }

    @Test public void allEmptyAndAcceptedVariantsAreClassifiedPerField(){
        List<String> refs=Arrays.asList("5:13","5:14");
        List<String> texts=Arrays.asList("Соль негодна.","Свет мира.");
        List<String> expected=Arrays.asList("негодна","мира");
        GapAnswerReview empty=GapAnswerReview.create(refs,texts,new int[]{1,3},expected,
            Arrays.asList(""," "),true);
        assertEquals(2,empty.errorCount());
        GapAnswerReview variant=GapAnswerReview.create(refs,texts,new int[]{1,3},expected,
            Arrays.asList("не годна","мира"),true);
        assertEquals(0,variant.errorCount());
    }

    @Test public void rejectsStalePositionsInsteadOfMislabelingErrors(){
        assertNull(GapAnswerReview.create(Arrays.asList("1:1"),Arrays.asList("да свет"),
            new int[]{1},Arrays.asList("да"),Arrays.asList("нет"),false));
        assertNull(GapAnswerReview.create(Arrays.asList("1:1"),Arrays.asList("да свет"),
            new int[]{3},Arrays.asList("да"),Arrays.asList("нет"),false));
        assertNull(GapAnswerReview.create(Arrays.asList("1:1"),Arrays.asList("да свет"),
            new int[]{0,0},Arrays.asList("да","да"),Arrays.asList("да","нет"),false));
    }

    @Test public void gradingMatchesIndividualAcceptedVariantsEvenWhenMixed(){
        List<String> words=Arrays.asList("негодна","негодна");
        List<String> typed=Arrays.asList("не годна","негодна");
        WritingAssessment.Input input=new WritingAssessment.Input("негодна негодна","не годна негодна",
            true,false,false,Collections.emptyList(),words,typed);
        assertTrue(WritingAssessment.evaluate(input).exact());
    }
}
