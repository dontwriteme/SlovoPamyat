package org.biblememory.app;

/** Transient selection for one insertion session. No Android views or database state. */
public final class PassageSelection {
    public long collectionId=-1;
    public int book=0, chapter=1, startChapter=-1, startVerse=-1, endChapter=-1, endVerse=-1;
    public boolean active=false, changingStart=false;

    public void cancel(){ active=false;changingStart=false;startChapter=startVerse=endChapter=endVerse=-1; }
    public void openBook(int value){if(book!=value){cancel();book=value;}}
    public void tap(int c,int v){
        if(!active || c<1 || v<1)return;
        if(startChapter<0 || changingStart){startChapter=c;startVerse=v;changingStart=false;}
        else {endChapter=c;endVerse=v;}
        if(complete()){
            int[] b=bounds();startChapter=b[0];startVerse=b[1];endChapter=b[2];endVerse=b[3];
        }
    }
    public boolean complete(){return startChapter>0 && endChapter>0;}
    public int[] bounds(){return PassageRangeLogic.between(startChapter,startVerse,
        endChapter>0?endChapter:startChapter,endChapter>0?endVerse:startVerse);}
    public boolean contains(int c,int v){
        if(!active || startChapter<0)return false;
        int[] b=bounds();return PassageRangeLogic.contains(b[0],b[1],b[2],b[3],c,v);
    }
    public boolean endpoint(int c,int v){return contains(c,v) &&
        (c==startChapter&&v==startVerse || c==endChapter&&v==endVerse);}
    public static String verses(int count){
        int n=count%100, digit=count%10;
        return count+" "+(n>=11&&n<=14?"стихов":digit==1?"стих":digit>=2&&digit<=4?"стиха":"стихов");
    }
}
