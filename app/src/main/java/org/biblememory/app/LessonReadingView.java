package org.biblememory.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.text.style.MaskFilterSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import static org.biblememory.app.ScreenUi.MUTED;

final class LessonReadingView {
    private final Context context;
    private final ScreenUi ui;
    private TextView activePassage;
    private Runnable revealCleanup;

    LessonReadingView(Context context,ScreenUi ui){this.context=context;this.ui=ui;}

    void releaseReveal(){
        if(revealCleanup!=null)revealCleanup.run();
    }

    void clear(){
        releaseReveal();
        if(activePassage!=null)activePassage.setOnTouchListener(null);
        activePassage=null;
        revealCleanup=null;
    }

    void render(LinearLayout content,List<Bible.Verse> lines,int mask,int blurSeed,
                IntSupplier currentMask,IntConsumer changeMask,Runnable refreshProgress,Runnable save){
        clear();
        activePassage=ui.prose("",22);
        activePassage.setTag("lesson-reading-passage");
        activePassage.setLineSpacing(ui.dp(7),1.18f);
        activePassage.setTextIsSelectable(false);
        ui.add(content,activePassage,19);
        // Both hints are measured at the actual content width, including font scale.
        LessonIntroHint hint=new LessonIntroHint();
        ui.add(content,hint,15);
        hint.show(mask>0,false);
        updateBlurText(lines,mask,blurSeed);
        TextView hide=ui.button(mask==100?"Показать все слова":
            "Скрыть "+(mask+25)+"% слов",false,()->{});
        hide.setOnClickListener(v->{
            if(activePassage==null||activePassage.getParent()!=content)return;
            int previous=currentMask.getAsInt();
            int next=previous>=100?0:previous+25;
            changeMask.accept(next);
            updateBlurText(lines,next,blurSeed);
            hint.show(next>0,true);
            refreshProgress.run();
            hide.setText(next==100?"Показать все слова":"Скрыть "+(next+25)+"% слов");
            save.run();
        });
        ui.animateLessonPress(hide);
        ui.add(content,hide,27);
        // No extra instructional caption: gesture behavior remains unchanged.
    }
    /** The taller of the two hints determines the height at every screen width.
     * INVISIBLE children still participate in measurement, unlike GONE children. */
    private final class LessonIntroHint extends FrameLayout {
        private final TextView readHint,holdHint;
        private boolean hidden;

        LessonIntroHint(){
            super(context);
            setTag("lesson-intro-hint");
            setMeasureAllChildren(true);
            readHint=ui.txt(LearningHelp.INTRO_READ,14,MUTED,false);
            holdHint=ui.txt(LearningHelp.INTRO_HOLD,14,MUTED,false);
            addView(readHint,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
            addView(holdHint,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
            holdHint.setVisibility(View.INVISIBLE);
        }

        void show(boolean wordsHidden,boolean animate){
            if(wordsHidden==hidden)return;
            hidden=wordsHidden;
            TextView outgoing=hidden?readHint:holdHint;
            TextView incoming=hidden?holdHint:readHint;
            outgoing.animate().cancel();
            incoming.animate().cancel();
            outgoing.setVisibility(View.INVISIBLE);
            outgoing.setAlpha(1f);
            boolean fade=animate&&ValueAnimator.areAnimatorsEnabled();
            incoming.setAlpha(fade?0f:1f);
            incoming.setVisibility(View.VISIBLE);
            if(fade)incoming.animate().alpha(1f).setDuration(150)
                .setInterpolator(new DecelerateInterpolator()).start();
        }

        @Override protected void onDetachedFromWindow(){
            readHint.animate().cancel();
            holdHint.animate().cancel();
            readHint.setAlpha(1f);
            holdHint.setAlpha(1f);
            super.onDetachedFromWindow();
        }
    }

    private void updateBlurText(List<Bible.Verse> lines,int percentage,int blurSeed) {
        releaseReveal();
        android.text.SpannableStringBuilder text=new android.text.SpannableStringBuilder();
        try {
            for(Bible.Verse verse:lines) {
                if(text.length()>0)text.append("\n\n");
                text.append(verse.ref+"   ");int offset=text.length();
                text.append(verse.text);
                for(int[] range:ReviewLogic.blurRanges(verse.text,percentage,verse.ref.hashCode()^blurSeed)) {
                    text.setSpan(new MaskFilterSpan(new BlurMaskFilter(ui.dp(9),BlurMaskFilter.Blur.NORMAL)),
                        offset+range[0],offset+range[1],android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            activePassage.setLayerType(percentage>0?View.LAYER_TYPE_SOFTWARE:View.LAYER_TYPE_NONE,null);
            activePassage.setText(text,TextView.BufferType.SPANNABLE);
            installTemporaryReveal(activePassage);
        } catch(RuntimeException unsupported) {
            // Unsupported device graphics must never crash the study activity.
            StringBuilder fallback=new StringBuilder();
            for(Bible.Verse verse:lines) {
                if(fallback.length()>0)fallback.append("\n\n");
                fallback.append(verse.ref).append("   ")
                    .append(ReviewLogic.masked(verse.text,percentage,verse.ref.hashCode()^blurSeed));
            }
            activePassage.setLayerType(View.LAYER_TYPE_NONE,null);
            activePassage.setText(fallback.toString());
        }
        activePassage.setContentDescription(percentage>0?
            "Скрыто "+percentage+" процентов слов. Попробуйте их вспомнить.":text.toString());
    }
    /** Immediate reveal held until the ORIGINAL finger lifts, even when it moves away. */
    private void installTemporaryReveal(TextView view) {
        final MaskFilterSpan[] selected={null};
        final int[] begin={-1},end={-1},pointer={-1};
        final android.text.Spannable[] activeText={null};
        Runnable close=()->{
            if(selected[0]!=null && activeText[0]!=null && begin[0]>=0 &&
                end[0]<=activeText[0].length() && activeText[0].getSpanStart(selected[0])<0)
                activeText[0].setSpan(selected[0],begin[0],end[0],
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            selected[0]=null;activeText[0]=null;begin[0]=-1;end[0]=-1;pointer[0]=-1;
            view.invalidate();
            if(view.getParent()!=null)view.getParent().requestDisallowInterceptTouchEvent(false);
        };
        revealCleanup=close;
        view.setOnTouchListener((v,event)->{
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                close.run();
                if(!(view.getText() instanceof android.text.Spannable))return false;
                android.text.Spannable span=(android.text.Spannable)view.getText();
                int at=Math.max(0,Math.min(span.length(),view.getOffsetForPosition(event.getX(),event.getY())));
                MaskFilterSpan[] hits=span.getSpans(at,Math.min(span.length(),at+1),MaskFilterSpan.class);
                if(hits.length==0)return false;
                selected[0]=hits[0];begin[0]=span.getSpanStart(hits[0]);end[0]=span.getSpanEnd(hits[0]);
                if(begin[0]<0){close.run();return false;}
                activeText[0]=span;pointer[0]=event.getPointerId(0);
                span.removeSpan(selected[0]);view.invalidate();
                if(view.getParent()!=null)view.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if(selected[0]==null)return false;
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){close.run();return true;}
            if(action==MotionEvent.ACTION_POINTER_UP &&
                event.getPointerId(event.getActionIndex())==pointer[0]){close.run();return true;}
            // MOVE and a second finger never switch or prematurely dismiss the selected word.
            return true;
        });
    }
}
