package org.biblememory.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import java.util.Arrays;

final class LessonProgressView {
    private final Context context;
    private final ScreenUi ui;
    private LessonSegmentedBar lessonHotbarStrip;

    LessonProgressView(Context context,ScreenUi ui){this.context=context;this.ui=ui;}

    boolean visible(){return lessonHotbarStrip!=null;}
    void update(LessonHotbarLogic.Model model){lessonHotbarStrip.setModel(model);}
    void clear(){lessonHotbarStrip=null;}

    /** Use the same compact phase-based progress strip throughout the lesson. */
    void render(LinearLayout top,LessonHotbarLogic.Model model){
        LinearLayout bar=ui.column();bar.setTag("lesson-progress");
        lessonHotbarStrip=new LessonSegmentedBar(context,ui,model);
        lessonHotbarStrip.setTag("lesson-progress-strip");
        bar.setBackground(ui.shape(0xBFFFF8EE,12));
        bar.setPadding(ui.dp(4),ui.dp(3),ui.dp(4),ui.dp(3));
        bar.addView(lessonHotbarStrip,new LinearLayout.LayoutParams(-1,ui.dp(9)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ui.dp(190),-2);
        lp.gravity=Gravity.CENTER_HORIZONTAL;
        lp.topMargin=ui.dp(8);
        top.addView(bar,lp);
    }

    /** One segment per scheduled phase; each fills according to its completed tasks. */
    static final class LessonSegmentedBar extends View {
        private final ScreenUi ui;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private LessonHotbarLogic.Model model;
        private final float[] fill=new float[3];
        private ValueAnimator fillAnimator;
        LessonSegmentedBar(Context context,ScreenUi ui,LessonHotbarLogic.Model model){
            super(context);
            this.ui=ui;
            setModel(model);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        void setModel(LessonHotbarLogic.Model value){
            if(model==null||!Arrays.equals(value.finished,model.finished)||
                    !Arrays.equals(value.totals,model.totals)){
                if(fillAnimator!=null)fillAnimator.cancel();
                float[] target=new float[3];
                for(int i=0;i<3;i++)if(value.totals[i]>0)target[i]=value.finished[i]/(float)value.totals[i];
                if(model!=null&&isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled()){
                    float[] start=fill.clone();
                    fillAnimator=ValueAnimator.ofFloat(0f,1f);
                    fillAnimator.setDuration(190);
                    fillAnimator.setInterpolator(new DecelerateInterpolator());
                    fillAnimator.addUpdateListener(animation->{
                        float fraction=(float)animation.getAnimatedValue();
                        for(int i=0;i<3;i++)fill[i]=start[i]+(target[i]-start[i])*fraction;
                        invalidate();
                    });
                    fillAnimator.start();
                }else System.arraycopy(target,0,fill,0,fill.length);
            }
            model=value;
            String[] names={"Читай","Собери","Напиши"};
            setContentDescription(names[model.activePhase]+". Текущее задание "+model.currentNumber+
                " из "+model.total+". Выполнено "+model.completedCount+" из "+model.total);
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(model.total<=0||getWidth()<=0)return;
            drawLessonPhases(canvas);
        }
        private void drawLessonPhases(Canvas canvas){
            int groups=0;
            for(int total:model.totals)if(total>0)groups++;
            if(groups==0)return;
            float gap=ui.dp(4),width=(getWidth()-gap*(groups-1))/groups;
            if(width<=0)return;
            float radius=getHeight()/2f,left=0f;
            for(int i=0;i<3;i++){
                if(model.totals[i]==0)continue;
                paint.setColor(0xFFD2C5B7);
                canvas.drawRoundRect(left,0,left+width,getHeight(),radius,radius,paint);
                if(fill[i]>0f){
                    paint.setColor(0xFF648C3C);
                    canvas.drawRoundRect(left,0,left+width*fill[i],getHeight(),radius,radius,paint);
                }
                left+=width+gap;
            }
        }
        @Override protected void onDetachedFromWindow(){
            if(fillAnimator!=null)fillAnimator.cancel();
            super.onDetachedFromWindow();
        }
        @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(android.widget.ProgressBar.class.getName());
            if(model.total>0)info.setRangeInfo(android.view.accessibility.AccessibilityNodeInfo.RangeInfo.obtain(
                android.view.accessibility.AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,0,model.total,model.completedCount));
        }
    }
}
