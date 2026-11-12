package org.biblememory.app;

/** Transient selection for one insertion session. No Android views or database state. */
public final class PassageSelection {
    public long collectionId=-1;
    public int book=0, chapter=1, startChapter=-1, startVerse=-1, endChapter=-1, endVerse=-1;
    public boolean active=false;

    public void cancel(){ active=false;startChapter=startVerse=endChapter=endVerse=-1; }
    public void openBook(int value){if(book!=value){cancel();book=value;}}
    /** Explicit endpoints retain the user's order, including an invalid range. */
    public void setBoundary(boolean end,int c,int v){
        if(c<1||v<1)throw new IllegalArgumentException("Invalid scripture coordinates");
        if(end){endChapter=c;endVerse=v;}else{startChapter=c;startVerse=v;}
    }
    public boolean complete(){return startChapter>0&&startVerse>0&&endChapter>0&&endVerse>0;}
    public boolean valid(){return book>0&&complete()&&
        PassageRangeLogic.valid(startChapter,startVerse,endChapter,endVerse);}
    public int[] bounds(){return new int[]{startChapter,startVerse,endChapter,endVerse};}
    public static String verses(int count){
        int n=count%100, digit=count%10;
        return count+" "+(n>=11&&n<=14?"стихов":digit==1?"стих":digit>=2&&digit<=4?"стиха":"стихов");
    }
}
