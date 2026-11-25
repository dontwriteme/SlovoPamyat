package org.biblememory.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Lightweight wrapping container for word tiles; uses Android SDK only. */
final class LessonWordWrap extends ViewGroup {
    private final int spacing;
    private final boolean centered;
    LessonWordWrap(Context context,int spacing){this(context,spacing,false);}
    LessonWordWrap(Context context,int spacing,boolean centered){
        super(context);this.spacing=spacing;this.centered=centered;
    }
    @Override protected void onMeasure(int ws,int hs) {
        int width=MeasureSpec.getSize(ws),available=Math.max(1,width-getPaddingLeft()-getPaddingRight());
        int x=0,y=0,lineHeight=0;
        for(int i=0;i<getChildCount();i++) {
            View child=getChildAt(i);if(child.getVisibility()==GONE)continue;
            measureChild(child,ws,hs);
            int cw=child.getMeasuredWidth(),ch=child.getMeasuredHeight();
            if(x>0 && x+cw>available){y+=lineHeight+spacing;x=0;lineHeight=0;}
            x+=cw+spacing;lineHeight=Math.max(lineHeight,ch);
        }
        setMeasuredDimension(resolveSize(width,ws),resolveSize(y+lineHeight+getPaddingTop()+getPaddingBottom(),hs));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        int available=Math.max(1,right-left-getPaddingLeft()-getPaddingRight());
        int y=getPaddingTop();
        for(int start=0;start<getChildCount();){
            int end=start,rowWidth=0,rowHeight=0;
            while(end<getChildCount()){
                View child=getChildAt(end);
                if(child.getVisibility()==GONE){end++;continue;}
                int width=child.getMeasuredWidth();
                int nextWidth=rowWidth==0?width:rowWidth+spacing+width;
                if(rowWidth>0&&nextWidth>available)break;
                rowWidth=nextWidth;
                rowHeight=Math.max(rowHeight,child.getMeasuredHeight());
                end++;
            }
            int x=getPaddingLeft()+(centered?Math.max(0,(available-rowWidth)/2):0);
            for(int i=start;i<end;i++){
                View child=getChildAt(i);
                if(child.getVisibility()==GONE)continue;
                int width=child.getMeasuredWidth();
                child.layout(x,y,x+width,y+child.getMeasuredHeight());
                x+=width+spacing;
            }
            y+=rowHeight+spacing;
            start=end;
        }
    }
}
