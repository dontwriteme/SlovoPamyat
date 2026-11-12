package org.biblememory.app;

/** Explicit reproducible intervals for independent delayed recall of verses and groups. */
public final class SpacedReviewLogic {
    private SpacedReviewLogic() {}
    public static final int[] DAYS={1,3,7,14,30,60};
    public static final class State {
        public final int level,intervalDays;
        public final long due;
        public State(int level,int days,long due){this.level=level;this.intervalDays=days;this.due=due;}
    }
    public static State update(int oldLevel,int accuracy,boolean assisted,long timestamp) {
        // An assisted attempt does not alter retention history or the review schedule.
        if(assisted) return null;
        int level=accuracy==100?Math.min(DAYS.length-1,Math.max(-1,oldLevel)+1):0;
        int days=DAYS[level];
        return new State(level,days,timestamp+days*86400000L);
    }
    public static boolean due(long due,long now){return due>0 && now>=due;}
}
