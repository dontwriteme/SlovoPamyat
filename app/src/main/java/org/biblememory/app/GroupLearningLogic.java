package org.biblememory.app;

import java.util.*;

/** Recommend only contiguous, independently mastered verses and never cross chapter gaps. */
public final class GroupLearningLogic {
    private GroupLearningLogic() {}
    public interface Progress {
        boolean verseMastered(int index);
        boolean groupMastered(String stage,int start,int end);
    }
    public static final class Candidate {
        public final String stage;public final int start,end;public final String reason;
        Candidate(String st,int s,int e,String reason){stage=st;start=s;end=e;this.reason=reason;}
    }
    public static Candidate candidate(String stage,int start,int end,String reason){
        return new Candidate(stage,start,end,reason);
    }
    /** A verse is followed by its incoming bridge and, periodically, a small completed group.
     * This prevents the old behavior of requesting all 2/3-verse windows over and over.
     */
    public static Candidate afterVerse(List<String> refs,Progress p,int current,boolean sermon){
        if(current<=0 || current>=refs.size() || !p.verseMastered(current))return null;
        if(p.verseMastered(current-1) && adjacent(refs.get(current-1),refs.get(current)) &&
           !p.groupMastered("CHAIN",current-1,current+1))
            return candidate("CHAIN",current-1,current+1,"Проверь переход между соседними стихами");
        // Group every 3 consecutive verses once, rather than a growing prefix on each step.
        if((current+1)%3==0 && current>=2 &&
           !p.groupMastered("CHAIN",current-2,current+1)){
            boolean valid=true;
            for(int k=current-2;k<=current;k++)if(!p.verseMastered(k) ||
               (k>current-2&&!adjacent(refs.get(k-1),refs.get(k))))valid=false;
            if(valid)return candidate("CHAIN",current-2,current+1,"Воспроизведи три стиха без остановки");
        }
        // Canonically grounded section tests are offered at section boundaries.
        if(sermon)for(StudyPlanLogic.Step step:StudyPlanLogic.build(refs,StudyPlanLogic.Stage.SECTION,true)){
            if(step.end!=current+1 || step.end-step.start<3 || step.end-step.start>10 ||
               p.groupMastered("SECTION",step.start,step.end))continue;
            boolean all=true;
            for(int k=step.start;k<step.end;k++)if(!p.verseMastered(k)||
                (k>step.start&&!adjacent(refs.get(k-1),refs.get(k))))all=false;
            if(all)return candidate("SECTION",step.start,step.end,"Проверь пройденный смысловой раздел");
        }
        return null;
    }
    private static boolean adjacent(String a,String b){
        // Only chapter:verse references are contiguous; user-defined unrelated texts are not merged.
        try {
            String[] x=a.split(":"),y=b.split(":");
            if(x.length==1&&y.length==1)
                return Integer.parseInt(y[0])==Integer.parseInt(x[0])+1;
            if(x.length!=2||y.length!=2)return false;
            int chapterA=Integer.parseInt(x[0]),verseA=Integer.parseInt(x[1]);
            int chapterB=Integer.parseInt(y[0]),verseB=Integer.parseInt(y[1]);
            return chapterB==chapterA&&verseB==verseA+1 ||
                chapterB==chapterA+1&&verseB==1;
            // Cross-chapter adjacency is permitted only for contiguous saved passages.
            // A Bible range is constructed with all intervening verses by Bible.passage().
        }catch(Exception ex){return false;}
    }
    public static Candidate next(List<String> refs,Progress p,boolean sermon) {
        // A two-verse bridge is created as soon as two consecutive verses have been learnt.
        for(int size:new int[]{2,3}) for(int i=0;i+size<=refs.size();i++) {
            boolean eligible=true;
            for(int j=i;j<i+size;j++) {
                if(!p.verseMastered(j) || (j>i&&!adjacent(refs.get(j-1),refs.get(j)))){
                    eligible=false;break;
                }
            }
            if(eligible&&!p.groupMastered("CHAIN",i,i+size))
                return new Candidate("CHAIN",i,i+size,size==2?"Соедини два соседних стиха":"Закрепи переходы между тремя стихами");
        }
        // Only after short bridges are complete, recommend long, grounded sections and chapters.
        for(StudyPlanLogic.Stage stage:new StudyPlanLogic.Stage[]{StudyPlanLogic.Stage.SECTION,StudyPlanLogic.Stage.CHAPTER}) {
            for(StudyPlanLogic.Step step:StudyPlanLogic.build(refs,stage,sermon)) {
                if(step.end-step.start<2 || p.groupMastered(stage.name(),step.start,step.end))continue;
                boolean eligible=true;
                for(int j=step.start;j<step.end;j++) {
                    if(!p.verseMastered(j) || (j>step.start&&!adjacent(refs.get(j-1),refs.get(j)))) {
                        eligible=false;break;
                    }
                }
                if(eligible)return new Candidate(stage.name(),step.start,step.end,
                    stage==StudyPlanLogic.Stage.SECTION?"Проверь смысловой блок":"Попробуй воспроизвести целую главу");
            }
        }
        return null;
    }
}
