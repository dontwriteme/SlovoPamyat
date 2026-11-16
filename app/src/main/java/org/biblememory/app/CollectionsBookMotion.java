package org.biblememory.app;

/** Pure geometry for the three independently painted books; no collection data. */
final class CollectionsBookMotion {
    static final int DURATION = 1500;
    static final class Pose {
        float x, y, angle, scale, depth;
        int slot;
        void copy(Pose p) { x=p.x; y=p.y; angle=p.angle; scale=p.scale; depth=p.depth; slot=p.slot; }
    }
    private static final float[] WIDTH = {2.525f,2.5f,7.5f};
    private static final float[] HEIGHT = {6.6f,6.3f,2.2f};
    private static final float[] HOVER_X = {-7.4f,-.8f,4.6f};
    private static final float[] HOVER_Y = {-3.7f,-3.2f,4.6f};
    private static final float[] C1X = {-11.2f,1f,10.4f}, C1Y = {-1.9f,-9f,1.2f};
    private static final float[] C2X = {-8.3f,-4.5f,8.7f}, C2Y = {5.4f,-10.4f,-7.5f};
    final Pose[] poses = {new Pose(),new Pose(),new Pose()};
    private final Pose[] source = {new Pose(),new Pose(),new Pose()};
    private final Pose[] target = {new Pose(),new Pose(),new Pose()};
    private int cycle, fromCycle, toCycle;
    private boolean running;
    private float elapsed, starPulse;

    static float width(int book) { return WIDTH[book]; }
    static float height(int book) { return HEIGHT[book]; }
    int cycle() { return cycle; }
    int settledCycle() { return running ? toCycle : cycle; }
    boolean isRunning() { return running; }
    float elapsed() { return elapsed; }
    float starPulse() { return starPulse; }
    void bind(boolean selected, int order) {
        cycle=selected ? Math.floorMod(order,3) : 0;
        running=false; elapsed=starPulse=0; layout();
    }
    void reset() { bind(false,0); }
    void start() {
        if(running)return;
        fromCycle=cycle;toCycle=(cycle+1)%3;running=true;
        for(int i=0;i<3;i++){ fit(i,slot(i,fromCycle),source[i]);fit(i,slot(i,toCycle),target[i]); }
        sample(0);
    }
    void finish() {
        if(running)cycle=toCycle;
        running=false;elapsed=starPulse=0;layout();
    }
    private void layout() { for(int i=0;i<3;i++)fit(i,slot(i,cycle),poses[i]); }
    private static int slot(int book,int order) { return (book+2*order)%3; }
    private static void fit(int book,int slot,Pose out) {
        boolean horizontal=slot==2;
        out.angle=book==2 ? (horizontal?0:90) : (horizontal?-90:0);
        float w=(book==2)==horizontal?WIDTH[book]:HEIGHT[book];
        float h=(book==2)==horizontal?HEIGHT[book]:WIDTH[book];
        float capW=slot==0?2.525f:slot==1?2.5f:7.5f;
        // The lower bay has extra headroom: fit a rotated upright book to the
        // full stack width without squashing it or leaving a large empty gap.
        float capH=slot==0?6.6f:slot==1?6.3f:3.1f;
        out.scale=Math.min(capW/w,capH/h);
        out.x=slot==0?-4.78f:slot==1?-2.25f:1.4f-w*out.scale/2;
        out.y=(horizontal?5.88f:.87f)-h*out.scale/2;
        out.depth=0;out.slot=slot;
    }
    void sample(float milliseconds) {
        if(!running)return;
        elapsed=Math.max(0,Math.min(DURATION,milliseconds));
        for(int book=0;book<3;book++) {
            Pose a=source[book], b=target[book], p=poses[book];
            int role=a.slot;
            float hx=HOVER_X[role],hy=HOVER_Y[role];
            float tx=b.x+(b.slot==0?-.45f:b.slot==1?.45f:0),ty=b.y-.65f;
            p.slot=b.slot;
            if(elapsed<=250) {
                float t=smooth((elapsed-role*25)/200);
                p.x=mix(a.x,hx,t);p.y=mix(a.y,hy,t);p.angle=a.angle;
                p.scale=a.scale*(1+.1f*t);p.depth=t+role*.01f;
            } else if(elapsed<400) {
                float t=(elapsed-250)/150;
                float envelope=(float)Math.pow(Math.sin(Math.PI*t),2);
                p.x=hx;p.y=hy-.1f*envelope;
                p.angle=a.angle+1.5f*(float)Math.sin(2*Math.PI*t)*envelope;
                p.scale=a.scale*1.1f;p.depth=1+role*.01f;
            } else if(elapsed<900) {
                float t=smooth((elapsed-400-role*20)/(500-role*20));
                p.x=bezier(hx,C1X[role],C2X[role],tx,t);
                p.y=bezier(hy,C1Y[role],C2Y[role],ty,t);
                p.angle=mix(a.angle,b.angle,t);p.scale=mix(a.scale,b.scale,t)*1.1f;
                p.depth=1+(role==0?0:role==1?.2f:.4f);
            } else if(elapsed<1250) {
                float t=smooth((elapsed-900)/350);
                p.x=mix(tx,b.x,t);p.y=mix(ty,b.y,t)-.06f*(float)Math.sin(Math.PI*t);
                p.angle=b.angle;p.scale=b.scale*(1+.1f*(1-t));p.depth=1-t+role*.01f;
            } else p.copy(b);
        }
        starPulse=elapsed>1250?(float)Math.sin(Math.PI*smooth((elapsed-1250)/250)):0;
    }
    private static float mix(float a,float b,float t) { return a+(b-a)*t; }
    private static float smooth(float t) { t=Math.max(0,Math.min(1,t));return t*t*(3-2*t); }
    private static float bezier(float a,float b,float c,float d,float t) {
        float u=1-t;return u*u*u*a+3*u*u*t*b+3*u*t*t*c+t*t*t*d;
    }
}
