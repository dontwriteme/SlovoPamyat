package org.biblememory.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;

/** Left-aligned, baseline-aligned verse lines. Punctuation stays with its token.
 * Preferred blank widths are fixed by the caller, not by the user's draft. */
final class VerseFlowLayout extends ViewGroup {
    private final int wordGap,lineGap;
    private final ArrayList<Line> lines=new ArrayList<>();

    VerseFlowLayout(Context context,int wordGap,int lineGap){
        super(context);this.wordGap=wordGap;this.lineGap=lineGap;
    }

    void addToken(boolean joinPrevious,boolean tightToPrevious,View... parts){
        for(int i=0;i<parts.length;i++){
            View child=parts[i];
            ViewGroup.LayoutParams existing=child.getLayoutParams();
            Part p=existing==null?new Part(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT):new Part(existing);
            p.joinPrevious=i>0||joinPrevious;
            p.gap=i>0||tightToPrevious?0:wordGap;
            addView(child,p);
        }
    }

    static boolean keepWithPrevious(String[] tokens,int i){
        if(i<=0)return false;
        return leadingMark(tokens[i-1])||
            (!ExerciseTextLogic.isWord(tokens[i])&&(!leadingMark(tokens[i])||i==tokens.length-1));
    }

    static boolean tightBefore(String[] tokens,int i){
        if(i<=0)return false;
        String previous=tokens[i-1].trim();
        return (leadingMark(previous)&&!dash(previous))||
            (!ExerciseTextLogic.isWord(tokens[i])&&!leadingMark(tokens[i]));
    }

    private static boolean dash(String token){
        return !token.isEmpty()&&token.chars().allMatch(c->"-–—".indexOf(c)>=0);
    }
    private static boolean leadingMark(String token){
        String t=token.trim();
        return dash(t)||(!t.isEmpty()&&t.chars().allMatch(c->"«„“([{".indexOf(c)>=0));
    }

    @Override protected ViewGroup.LayoutParams generateDefaultLayoutParams(){
        return new Part(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);
    }
    @Override public ViewGroup.LayoutParams generateLayoutParams(android.util.AttributeSet attrs){
        return new Part(getContext(),attrs);
    }
    @Override protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p){
        return new Part(p);
    }
    @Override protected boolean checkLayoutParams(ViewGroup.LayoutParams p){return p instanceof Part;}

    private void measurePart(View child,int width,int mode){
        Part p=part(child);
        child.measure(MeasureSpec.makeMeasureSpec(width,mode),
            p.height>=0?MeasureSpec.makeMeasureSpec(p.height,MeasureSpec.EXACTLY):
                MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
    }

    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int horizontal=getPaddingLeft()+getPaddingRight();
        int vertical=getPaddingTop()+getPaddingBottom();
        int width=MeasureSpec.getSize(widthSpec);
        boolean unbounded=MeasureSpec.getMode(widthSpec)==MeasureSpec.UNSPECIFIED;
        int available=unbounded?Integer.MAX_VALUE/4:Math.max(1,width-horizontal);
        lines.clear();
        ArrayList<View> visible=new ArrayList<>();
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);
            if(child.getVisibility()==GONE)continue;
            Part p=part(child);
            measurePart(child,p.width>=0?Math.min(available,p.width):available,
                p.width>=0?MeasureSpec.EXACTLY:MeasureSpec.AT_MOST);
            visible.add(child);
        }
        Line line=new Line();
        for(int start=0;start<visible.size();){
            int end=start+1;
            while(end<visible.size()&&part(visible.get(end)).joinPrevious)end++;
            int groupWidth=groupWidth(visible,start,end);
            // An unusually long word and its punctuation must fit the actual row.
            int excess=groupWidth-available;
            while(excess>0){
                View widest=null;
                for(int i=start;i<end;i++){
                    View child=visible.get(i);
                    if(child.getMeasuredWidth()>1&&
                        (widest==null||child.getMeasuredWidth()>widest.getMeasuredWidth()))widest=child;
                }
                if(widest==null)break;
                int before=widest.getMeasuredWidth();
                measurePart(widest,Math.max(1,before-excess),MeasureSpec.EXACTLY);
                int removed=before-widest.getMeasuredWidth();
                if(removed<=0)break;
                excess-=removed;
            }
            groupWidth=groupWidth(visible,start,end);
            int gap=line.children.isEmpty()?0:part(visible.get(start)).gap;
            if(!line.children.isEmpty()&&line.width+gap+groupWidth>available){
                lines.add(line);line=new Line();gap=0;
            }
            for(int i=start;i<end;i++)line.add(visible.get(i),i==start?gap:part(visible.get(i)).gap);
            start=end;
        }
        if(!line.children.isEmpty())lines.add(line);
        int contentHeight=0,maxWidth=0;
        for(Line row:lines){
            if(contentHeight>0)contentHeight+=lineGap;
            contentHeight+=row.height();
            maxWidth=Math.max(maxWidth,row.width);
        }
        setMeasuredDimension(resolveSize(unbounded?maxWidth+horizontal:width,widthSpec),
            resolveSize(contentHeight+vertical,heightSpec));
    }

    private static Part part(View child){return (Part)child.getLayoutParams();}
    private static int groupWidth(ArrayList<View> children,int start,int end){
        int width=0;
        for(int i=start;i<end;i++)
            width+=children.get(i).getMeasuredWidth()+(i==start?0:part(children.get(i)).gap);
        return width;
    }

    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        int y=getPaddingTop();
        for(Line row:lines){
            for(int i=0;i<row.children.size();i++){
                View child=row.children.get(i);
                int x=getPaddingLeft()+row.offsets.get(i);
                int baseline=child.getBaseline();
                int childTop=baseline>=0?y+row.above-baseline:y+(row.height()-child.getMeasuredHeight())/2;
                child.layout(x,childTop,x+child.getMeasuredWidth(),childTop+child.getMeasuredHeight());
            }
            y+=row.height()+lineGap;
        }
    }

    private static final class Part extends ViewGroup.LayoutParams{
        boolean joinPrevious;
        int gap;
        Part(int w,int h){super(w,h);}
        Part(ViewGroup.LayoutParams source){
            super(source);
            if(source instanceof Part){joinPrevious=((Part)source).joinPrevious;gap=((Part)source).gap;}
        }
        Part(Context c,android.util.AttributeSet attrs){super(c,attrs);}
    }

    private static final class Line{
        final ArrayList<View> children=new ArrayList<>();
        final ArrayList<Integer> offsets=new ArrayList<>();
        int width,above,below,tallest;
        void add(View child,int gap){
            width+=gap;children.add(child);offsets.add(width);width+=child.getMeasuredWidth();
            int h=child.getMeasuredHeight(),baseline=child.getBaseline();
            tallest=Math.max(tallest,h);
            if(baseline>=0){above=Math.max(above,baseline);below=Math.max(below,h-baseline);}
        }
        int height(){return Math.max(tallest,above+below);}
    }
}
