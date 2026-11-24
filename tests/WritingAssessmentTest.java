package org.biblememory.app;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WritingAssessmentTest {
    @Test public void comparesScoresAndAllFeedbackFieldsWithTheOldPath(){
        compare("И сказал Бог: да будет свет.","И сказал Бог да будет свет",false);
        compare("И сказал Бог: да будет свет.","И сказала Бог будет стол",false);
        compare("Соль негодна, и нет соли.","Соль не годна и нет соли",true);
        compare("Соль негодна, и нет соли.","Соль не годна и нет воды",true);
        compare("Первый стих.\nВторой стих.\nТретий стих.","Первый стих Третий стих",false);
        compare("Начало длинного текста "+"слово мир ".repeat(700),"совсем иной "+"лишнее ".repeat(3000),false);
        compare("Да будет свет","",false);
        compare("Да будет свет","…",false);
    }

    @Test public void exactDistanceMatchesUntrimmedRecurrenceAcrossRepeatedTokens(){
        Random random=new Random(513);
        List<String> words=Arrays.asList("и","да","Бог","бог","не","свет","стол","ё");
        for(int i=0;i<300;i++){
            List<String> a=new ArrayList<>(),b=new ArrayList<>();
            for(int n=random.nextInt(18);n>0;n--)a.add(words.get(random.nextInt(words.size())));
            for(int n=random.nextInt(18);n>0;n--)b.add(words.get(random.nextInt(words.size())));
            assertEquals(oldAccuracy(a,b),ReviewLogic.accuracy(a,b,false));
        }
    }

    @Test public void partialAnswerStillRequiresEveryGapAndSnapshotsTheInputs(){
        List<String> answers=new ArrayList<>(Arrays.asList("да",""));
        WritingAssessment.Input input=new WritingAssessment.Input("да свет","да свет",false,false,false,
            Collections.emptyList(),Arrays.asList("да","свет"),answers);
        answers.set(1,"свет");
        assertFalse(WritingAssessment.evaluate(input).exact());
        assertTrue(WritingAssessment.evaluate(new WritingAssessment.Input("да свет","да свет",false,false,false,
            Collections.emptyList(),Arrays.asList("да","свет"),answers)).exact());
    }

    private void compare(String expected,String actual,boolean special){
        List<String> verses=Arrays.asList(expected.split("\n"));
        WritingAssessment.Result result=WritingAssessment.evaluate(new WritingAssessment.Input(expected,actual,
            special,true,false,verses,Collections.emptyList(),Collections.emptyList()));
        boolean empty=actual.trim().isEmpty();
        boolean exact=!empty&&AcceptedTextLogic.exact(expected,actual,special);
        assertEquals(exact,result.exact());
        if(exact){assertEquals(100,result.accuracy());assertNull(result.feedback());return;}
        int best=-1;
        String closest=expected;
        for(String candidate:AcceptedTextLogic.accepted(expected,special)){
            int score=oldAccuracy(ReviewLogic.words(candidate),ReviewLogic.words(actual));
            if(score>best){best=score;closest=candidate;}
        }
        assertEquals(empty?0:Math.min(99,best),result.accuracy());
        assertEquals(empty?expected:closest,result.closest());
        WeakTransitionLogic.WeakSpot weak=empty||verses.size()<2?null:WeakTransitionLogic.locate(verses,actual);
        assertEquals(weakFields(weak),weakFields(result.weak()));
        assertEquals(fields(AnswerReviewLogic.compare(expected,actual,!empty&&special)),fields(result.feedback()));
    }

    private Object weakFields(WeakTransitionLogic.WeakSpot weak){
        return weak==null?null:Arrays.asList(weak.verseIndex,weak.transitionFrom,weak.errors);
    }

    private List<Object> fields(AnswerReviewLogic.Result feedback){
        List<Object> fields=new ArrayList<>(Arrays.asList(feedback.summary,feedback.displayedActual));
        for(AnswerReviewLogic.Mark mark:feedback.actualMarks)fields.add(Arrays.asList(mark.kind,mark.start,mark.end));
        for(AnswerReviewLogic.Issue issue:feedback.issues)
            fields.add(Arrays.asList(issue.kind,issue.expected,issue.actual,issue.actualStart,issue.actualEnd));
        return fields;
    }

    private int oldAccuracy(List<String> expected,List<String> actual){
        int total=Math.max(expected.size(),actual.size());
        if(total==0)return 100;
        int[] prev=new int[actual.size()+1],cur=new int[actual.size()+1];
        for(int j=0;j<prev.length;j++)prev[j]=j;
        for(int i=1;i<=expected.size();i++){
            cur[0]=i;
            for(int j=1;j<=actual.size();j++){
                int sub=prev[j-1]+(expected.get(i-1).equals(actual.get(j-1))?0:1);
                cur[j]=Math.min(sub,Math.min(prev[j]+1,cur[j-1]+1));
            }
            int[] swap=prev;prev=cur;cur=swap;
        }
        return Math.max(0,100-Math.round(100f*prev[actual.size()]/total));
    }
}
