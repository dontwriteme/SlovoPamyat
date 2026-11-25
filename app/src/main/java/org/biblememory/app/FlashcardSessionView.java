package org.biblememory.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.BooleanSupplier;

import static org.biblememory.app.ScreenUi.BORDER;
import static org.biblememory.app.ScreenUi.CARD;

final class FlashcardSessionView {
    private final Context context;
    private final ScreenUi ui;
    private final Runnable next;
    private final BooleanSupplier toggleAnswer;

    FlashcardSessionView(Context context, ScreenUi ui, Runnable next, BooleanSupplier toggleAnswer) {
        this.context = context;
        this.ui = ui;
        this.next = next;
        this.toggleAnswer = toggleAnswer;
    }

    LinearLayout create(String title, String reference, String content,
                        boolean referenceFirst, boolean revealed) {
        LinearLayout root=ui.column();root.setBackground(ui.pageBackground());
        LinearLayout header=ui.column();header.setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(7));
        TextView deckHeading=ui.displayText(title,22);
        deckHeading.setGravity(Gravity.CENTER);
        header.addView(deckHeading,new LinearLayout.LayoutParams(-1,ui.dp(45)));
        root.addView(header,new LinearLayout.LayoutParams(-1,-2));
        SwipeCardLayout swipe=new SwipeCardLayout();
        swipe.setBackground(ui.outlined(CARD,26,BORDER));
        swipe.setClipToOutline(true);
        View readableOverlay=new View(context);
        readableOverlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFFFFFCF8,0xFFFFFAF5,0xFAFFF6EE,0xDDFFF3E6,0x44FFF3E6}));
        swipe.addView(readableOverlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams swipeParams=new LinearLayout.LayoutParams(-1,0,1);
        swipeParams.setMargins(ui.dp(17),ui.dp(16),ui.dp(17),ui.dp(24));root.addView(swipe,swipeParams);
        ScrollView scrolling=new ScrollView(context);scrolling.setFillViewport(true);
        swipe.addView(scrolling,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout vertical=ui.column();vertical.setGravity(Gravity.CENTER);
        vertical.setPadding(ui.dp(22),ui.dp(22),ui.dp(22),ui.dp(22));scrolling.addView(vertical);
        TextView initial=ui.txt(referenceFirst?reference:content,
            referenceFirst?27:20,0xFF3C422E,referenceFirst);
        initial.setTypeface(Typeface.create("serif",referenceFirst?Typeface.BOLD:Typeface.NORMAL));
        initial.setGravity(Gravity.CENTER);ui.add(vertical,initial,0);
        TextView answer=ui.txt(referenceFirst?content:reference,
            referenceFirst?20:25,0xFF694F3F,!referenceFirst);
        answer.setTypeface(Typeface.create("serif",referenceFirst?Typeface.NORMAL:Typeface.BOLD));
        // Reserve an answer area so opening the eye never shifts the original citation/text.
        answer.setGravity(Gravity.CENTER);
        answer.setAlpha(revealed?1f:0f);
        answer.setImportantForAccessibility(revealed?
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        // Fit the entire revealed answer into the existing answer area without
        // a second scroll surface. Normal verses keep their original text size.
        answer.setMaxLines(Integer.MAX_VALUE);
        answer.setEllipsize(null);
        answer.setAutoSizeTextTypeUniformWithConfiguration(
            referenceFirst?11:14,referenceFirst?20:25,1,
            android.util.TypedValue.COMPLEX_UNIT_SP);
        int answerHeight=Math.min(ui.dp(230),Math.max(ui.dp(100),
            context.getResources().getDisplayMetrics().heightPixels*32/100));
        LinearLayout.LayoutParams answerArea=new LinearLayout.LayoutParams(-1,answerHeight);
        answerArea.topMargin=ui.dp(10);vertical.addView(answer,answerArea);
        EyeButton eye=new EyeButton();
        eye.setOpen(revealed,false);
        eye.setContentDescription(revealed?"Скрыть ответ":"Показать ответ");
        eye.setBackground(ui.outlined(0xFFF0F2E7,14,0xFFC9D5C4));
        // Keep the complete eye button (icon and its background) near the card's lower edge.
        // This reserved space is measured by ScrollView, so the button is not clipped.
        FrameLayout eyeSlot=new FrameLayout(context);
        LinearLayout.LayoutParams eyeSlotParams=new LinearLayout.LayoutParams(-1,ui.dp(136));
        eyeSlotParams.topMargin=ui.dp(56);
        vertical.addView(eyeSlot,eyeSlotParams);
        FrameLayout.LayoutParams eyeParams=new FrameLayout.LayoutParams(
            ui.dp(60),ui.dp(60),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        eyeSlot.addView(eye,eyeParams);
        eye.setOnClickListener(v->{
            boolean open=toggleAnswer.getAsBoolean();
            eye.setOpen(open,true);
            answer.animate().cancel();
            answer.animate().alpha(open?1f:0f)
                .setDuration(ValueAnimator.areAnimatorsEnabled()?250L:0L).start();
            answer.setImportantForAccessibility(open?
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            eye.setContentDescription(open?"Скрыть ответ":"Показать ответ");
        });
        return root;
    }

    private final class SwipeCardLayout extends FrameLayout {
        private float x0,y0;private boolean handling=false;
        SwipeCardLayout(){super(context);}
        @Override public boolean onInterceptTouchEvent(MotionEvent ev){
            switch(ev.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    x0=ev.getX();y0=ev.getY();handling=false;return false;
                case MotionEvent.ACTION_MOVE:
                    float dx=ev.getX()-x0,dy=ev.getY()-y0;
                    if(Math.abs(dx)>ui.dp(24)&&Math.abs(dx)>Math.abs(dy)*1.15f){handling=true;return true;}
                    return false;
                default:return false;
            }
        }
        @Override public boolean onTouchEvent(MotionEvent ev){
            if(ev.getActionMasked()==MotionEvent.ACTION_UP){
                float delta=ev.getX()-x0;
                if(Math.abs(delta)>ui.dp(55)){
                    animate().translationX(Math.signum(delta)*Math.max(ui.dp(360),getWidth()))
                        .alpha(0f).setDuration(175).withEndAction(next).start();
                } else animate().translationX(0f).alpha(1f).setDuration(120).start();
                handling=false;return true;
            }
            if(ev.getActionMasked()==MotionEvent.ACTION_MOVE&&handling){
                setTranslationX((ev.getX()-x0)*0.65f);return true;
            }
            if(ev.getActionMasked()==MotionEvent.ACTION_CANCEL){
                animate().translationX(0f).alpha(1f).setDuration(120).start();
                handling=false;return true;
            }
            return true;
        }
    }
    /** Drawn eyelids morph continuously, not a swap between two eye icons. */
    private final class EyeButton extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float openness=0f;
        private ValueAnimator eyelidsAnimator;
        EyeButton(){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setOpen(boolean open,boolean animate){
            if(eyelidsAnimator!=null)eyelidsAnimator.cancel();
            float target=open?1f:0f;
            if(!animate||!ValueAnimator.areAnimatorsEnabled()){
                openness=target;invalidate();return;
            }
            eyelidsAnimator=ValueAnimator.ofFloat(openness,target);
            eyelidsAnimator.setDuration(230L);
            eyelidsAnimator.setInterpolator(new DecelerateInterpolator());
            eyelidsAnimator.addUpdateListener(a->{
                openness=(Float)a.getAnimatedValue();invalidate();
            });
            eyelidsAnimator.start();
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float cx=getWidth()/2f,cy=getHeight()/2f,r=ui.dp(15);
            float open=Math.max(0f,Math.min(1f,openness));
            paint.setColor(0xFF9B5E41);paint.setStrokeWidth(ui.dp(2.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            // At t=0 the lids meet in one curved closed-eye line;
            // at t=1 they frame a fully open, almond-shaped eye.
            float closedArc=ui.dp(3);
            float arc=closedArc+(r*.82f-closedArc)*open;
            Path upper=new Path();upper.moveTo(cx-r,cy);
            upper.cubicTo(cx-r*.55f,cy-arc,cx+r*.55f,cy-arc,cx+r,cy);
            Path lower=new Path();lower.moveTo(cx-r,cy);
            lower.cubicTo(cx-r*.55f,cy-closedArc+(arc+closedArc)*open,
                cx+r*.55f,cy-closedArc+(arc+closedArc)*open,cx+r,cy);
            c.drawPath(upper,paint);
            if(open>0.003f)c.drawPath(lower,paint);
            if(open>0.003f){
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(Math.round(255f*open));
                c.drawCircle(cx,cy,ui.dp(4)*open,paint);
                paint.setAlpha(255);
            }
        }
        @Override protected void onDetachedFromWindow(){
            if(eyelidsAnimator!=null)eyelidsAnimator.cancel();
            super.onDetachedFromWindow();
        }
    }
}
