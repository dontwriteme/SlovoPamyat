package org.biblememory.app;

/** Stable twelve-hour cadence that does not restart when the app is reopened. */
public final class HomeVerseLogic {
    public static final long ROTATE_MS=12L*60L*60L*1000L;
    private HomeVerseLogic() {}
    public static boolean shouldRotate(long lastSwitch,long now) {
        return shouldRotate(lastSwitch,now,ROTATE_MS);
    }
    public static boolean shouldRotate(long lastSwitch,long now,long intervalMs) {
        return elapsedIntervals(lastSwitch,now,intervalMs)>0;
    }
    public static long elapsedIntervals(long lastSwitch,long now,long intervalMs) {
        return lastSwitch>0 && intervalMs>0 && now>=lastSwitch?
            (now-lastSwitch)/intervalMs:0L;
    }
    public static int advance(int previous,int size,long elapsed) {
        if(size<=0)throw new IllegalArgumentException("Empty list");
        return (int)Math.floorMod((long)previous+elapsed,size);
    }
    public static int next(int previous,int size) {return advance(previous,size,1);}
    public static long nextDelay(long lastSwitch,long now) {
        if(lastSwitch<=0)return ROTATE_MS;
        if(now<lastSwitch)return ROTATE_MS;
        long since=(now-lastSwitch)%ROTATE_MS;
        return ROTATE_MS-since;
    }
}
