package org.biblememory.app;

/** Canonical, cross-chapter Scripture-range selection, independent of Android views. */
public final class PassageRangeLogic {
    private PassageRangeLogic(){}
    public static boolean valid(int a,int x,int b,int y){
        return a>=1 && b>=1 && x>=1 && y>=1 && (a<b || a==b&&x<=y);
    }
    public static boolean contains(int a,int x,int b,int y,int chapter,int verse){
        return valid(a,x,b,y) && chapter*1000+verse >=a*1000+x &&
            chapter*1000+verse <=b*1000+y;
    }
    public static int[] between(int a,int x,int b,int y){
        if(a<1||x<1||b<1||y<1)throw new IllegalArgumentException("Invalid scripture coordinates");
        return a<b || a==b && x<=y?new int[]{a,x,b,y}:new int[]{b,y,a,x};
    }
}
