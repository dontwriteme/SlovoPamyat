package org.biblememory.app;

import java.util.*;

/** Immutable snapshot of a partial writing submission in the original verse context. */
final class GapAnswerReview {
    record Gap(String expected,String actual,boolean correct) {}
    record Verse(String reference,List<String> tokens,Map<Integer,Gap> gaps) {}
    private final List<Verse> verses;
    private final int correctCount,errorCount;
    private GapAnswerReview(List<Verse> verses,int correctCount,int errorCount){
        this.verses=Collections.unmodifiableList(verses);
        this.correctCount=correctCount;this.errorCount=errorCount;
    }
    List<Verse> verses(){return verses;}
    int correctCount(){return correctCount;}
    int errorCount(){return errorCount;}

    /** Positions refer to whitespace tokens across the joined passage, not lexical subwords.
     * Return null on a stale/inconsistent mapping rather than attaching an answer to the wrong verse.
     */
    static GapAnswerReview create(List<String> refs,List<String> texts,int[] positions,
                                  List<String> expected,List<String> answers,boolean special){
        if(refs.size()!=texts.size()||positions.length==0||
            positions.length!=expected.size()||positions.length!=answers.size())return null;
        List<Verse> verses=new ArrayList<>();
        int offset=0,cursor=0,correct=0;
        for(int v=0;v<texts.size();v++){
            String[] tokens=ExerciseTextLogic.tokens(texts.get(v));
            Map<Integer,Gap> gaps=new LinkedHashMap<>();
            while(cursor<positions.length&&positions[cursor]<offset+tokens.length){
                int local=positions[cursor]-offset;
                if(local<0||gaps.containsKey(local))return null;
                String word=InlineGapLogic.core(tokens[local]);
                if(!word.equals(expected.get(cursor)))return null;
                String actual=answers.get(cursor)==null?"":answers.get(cursor).trim();
                boolean valid=AcceptedTextLogic.exact(word,actual,special);
                if(valid)correct++;
                gaps.put(local,new Gap(word,actual,valid));
                cursor++;
            }
            verses.add(new Verse(refs.get(v),Collections.unmodifiableList(Arrays.asList(tokens)),
                Collections.unmodifiableMap(gaps)));
            offset+=tokens.length;
        }
        if(cursor!=positions.length)return null;
        return new GapAnswerReview(verses,correct,positions.length-correct);
    }
}
