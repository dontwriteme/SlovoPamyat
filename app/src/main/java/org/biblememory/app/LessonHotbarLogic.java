package org.biblememory.app;

/** Pure presentation model for lesson-progress cells; never edits study progress. */
public final class LessonHotbarLogic {
    private LessonHotbarLogic() {}
    public static final int DONE=0,CURRENT=1,UPCOMING=2;
    public static final class Model {
        public final int[] totals,finished;
        public final int activePhase,currentNumber,total;
        private Model(int[] totals,int[] finished,int phase){
            this.totals=totals;this.finished=finished;this.activePhase=phase;
            int all=0,done=0;
            for(int i=0;i<3;i++){all+=totals[i];done+=finished[i];}
            total=all;
            currentNumber=all==0?0:Math.min(all,done+1);
        }
        public int ordinal(int phase,int index){
            int count=1;
            for(int p=0;p<phase;p++)count+=totals[p];
            return count+index;
        }
        public int visual(int phase,int index){
            if(index<finished[phase])return DONE;
            if(phase==activePhase && index==finished[phase])return CURRENT;
            return UPCOMING;
        }
    }
    /** In review-only sessions the first two phases have no scheduled tasks. */
    public static Model calculate(int phase,int intro,int build,int write,
                                  int introDone,int buildDone,int writeDone,
                                  boolean reviewOnly){
        int[] totals={Math.max(0,intro),Math.max(0,build),Math.max(0,write)};
        if(reviewOnly){totals[0]=0;totals[1]=0;}
        int[] done={Math.max(0,introDone),Math.max(0,buildDone),Math.max(0,writeDone)};
        phase=Math.max(0,Math.min(2,phase));
        for(int i=0;i<3;i++){
            if(reviewOnly && i<2)done[i]=0;
            else if(i<phase)done[i]=totals[i];
            else if(i>phase)done[i]=0;
            done[i]=Math.min(done[i],totals[i]);
        }
        return new Model(totals,done,phase);
    }
}
