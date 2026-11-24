package org.biblememory.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure calculation for one submission. Persistence and presentation stay with the caller. */
final class WritingAssessment {
    record Input(String expected,String actual,boolean special,boolean full,boolean practice,
                 List<String> verses,List<String> gaps,List<String> answers){
        Input {
            verses=Collections.unmodifiableList(new ArrayList<>(verses));
            gaps=Collections.unmodifiableList(new ArrayList<>(gaps));
            answers=Collections.unmodifiableList(new ArrayList<>(answers));
        }

        boolean requiresWorker(){
            return expected.length()>=1200||actual.length()>=1200;
        }
    }
    record Result(boolean exact,int accuracy,String closest,WeakTransitionLogic.WeakSpot weak,
                  AnswerReviewLogic.Result feedback) {}

    static Result evaluate(Input input){
        String expected=input.expected, actual=input.actual;
        if(actual.trim().isEmpty())
            return new Result(false,0,expected,null,AnswerReviewLogic.compare(expected,actual,false));

        List<String> typed=ReviewLogic.words(actual);
        List<String> candidates=AcceptedTextLogic.accepted(expected,input.special);
        List<List<String>> normalized=new ArrayList<>(candidates.size());
        boolean exact=false;
        for(String candidate:candidates){
            List<String> words=ReviewLogic.words(candidate);
            normalized.add(words);
            if(!words.isEmpty()&&words.equals(typed))exact=true;
        }
        exact=exact&&(input.full||InlineGapLogic.complete(input.gaps,input.answers.toArray(new String[0]),input.special));
        if(exact)return new Result(true,100,expected,null,null);

        String closest=expected;
        int accuracy=0;
        WeakTransitionLogic.WeakSpot weak=null;
        if(input.full&&!input.practice){
            int best=-1;
            for(int i=0;i<normalized.size();i++){
                int score=ReviewLogic.accuracy(normalized.get(i),typed,true);
                if(score>best){best=score;closest=candidates.get(i);}
            }
            accuracy=Math.min(99,best);
            if(input.verses.size()>1)weak=WeakTransitionLogic.locate(input.verses,actual);
        }
        return new Result(false,accuracy,closest,weak,AnswerReviewLogic.compare(expected,actual,input.special));
    }

    private WritingAssessment(){}
}
