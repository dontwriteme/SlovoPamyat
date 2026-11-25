package org.biblememory.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** The shared completion illustration. Animations run only while the view is visible. */
final class SuccessLampView extends View {
    static final String TAG="success-lamp";
    static final long IGNITION_MS=1450L;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bowl=new Path(),neck=new Path(),flame=new Path(),innerFlame=new Path();
    private final Shader bodyGradient,flameGradient,haloGradient;
    private ValueAnimator ignition,flicker;
    private float progress=0f,phase=0f;
    private boolean attached,aggregated=true;

    SuccessLampView(Context context){
        super(context);
        setTag(TAG);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        bodyGradient=new LinearGradient(0,19,0,69,0xFFE3CFA3,0xFFB49A65,Shader.TileMode.CLAMP);
        flameGradient=new LinearGradient(0,-105,0,-10,0xFFF0CB7F,0xFFD6A04F,Shader.TileMode.CLAMP);
        haloGradient=new RadialGradient(0,-53,132,
            new int[]{0xB0FFF4CF,0x65F5D58F,0x00F5D58F},
            new float[]{0f,.45f,1f},Shader.TileMode.CLAMP);
        bowl.moveTo(-48,42);
        bowl.cubicTo(-39,61,-22,66,0,66);
        bowl.cubicTo(22,66,39,61,48,42);
        bowl.close();
        neck.moveTo(-16,42);neck.lineTo(-16,10);
        neck.cubicTo(-16,0,-8,-7,0,-7);
        neck.cubicTo(8,-7,16,0,16,10);
        neck.lineTo(16,42);neck.close();
        // Both flames pivot at the wick, never move the lamp body.
        flame.moveTo(0,-101);
        flame.cubicTo(-8,-80,-36,-49,-23,-24);
        flame.cubicTo(-14,-5,13,-5,23,-24);
        flame.cubicTo(37,-49,13,-78,0,-101);flame.close();
        innerFlame.moveTo(0,-63);
        innerFlame.cubicTo(-8,-47,-17,-29,-8,-18);
        innerFlame.cubicTo(-3,-11,8,-12,12,-21);
        innerFlame.cubicTo(19,-36,6,-48,0,-63);innerFlame.close();
        if(!ValueAnimator.areAnimatorsEnabled())progress=1f;
    }

    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();attached=true;updateMotion();
    }
    @Override protected void onDetachedFromWindow(){
        attached=false;stopMotion();super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility){
        super.onWindowVisibilityChanged(visibility);updateMotion();
    }
    @Override public void onVisibilityAggregated(boolean visible){
        super.onVisibilityAggregated(visible);aggregated=visible;updateMotion();
    }
    private boolean canAnimate(){
        return attached&&aggregated&&getWindowVisibility()==VISIBLE&&isShown()
            &&ValueAnimator.areAnimatorsEnabled();
    }
    private void updateMotion(){
        if(!canAnimate()){
            stopMotion();
            if(!ValueAnimator.areAnimatorsEnabled()){progress=1f;phase=0f;invalidate();}
            return;
        }
        if(ignition!=null||flicker!=null)return;
        if(progress>=1f){startFlicker();return;}
        ValueAnimator animator=ValueAnimator.ofFloat(progress,1f);
        ignition=animator;
        animator.setInterpolator(new LinearInterpolator());
        animator.setDuration(Math.max(1L,(long)(IGNITION_MS*(1f-progress))));
        animator.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});
        animator.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator animation){
                if(ignition!=animation)return;
                ignition=null;progress=1f;invalidate();
                if(canAnimate())startFlicker();
            }
        });
        animator.start();
    }
    private void startFlicker(){
        if(flicker!=null||!canAnimate())return;
        flicker=ValueAnimator.ofFloat(0f,1f);
        flicker.setDuration(2800L);
        flicker.setInterpolator(new LinearInterpolator());
        flicker.setRepeatCount(ValueAnimator.INFINITE);
        flicker.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});
        flicker.start();
    }
    private void stopMotion(){
        ValueAnimator intro=ignition,loop=flicker;
        ignition=null;flicker=null;
        if(intro!=null)intro.cancel();
        if(loop!=null)loop.cancel();
        phase=0f;
    }

    boolean hasRunningMotion(){return ignition!=null||flicker!=null;}
    float ignitionProgress(){return progress;}

    private static float smooth(float start,float end,float value){
        float t=Math.max(0f,Math.min(1f,(value-start)/(end-start)));
        return t*t*(3f-2f*t);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float scale=Math.min(getWidth()/300f,getHeight()/270f);
        if(scale<=0f)return;
        float lit=smooth(.10f,.53f,progress);
        float spread=smooth(.15f,1f,progress);
        float rays=smooth(.45f,.85f,progress);
        float wave=(float)Math.sin(phase*2*Math.PI);
        float sway=(float)Math.sin(phase*4*Math.PI);
        c.save();c.translate(getWidth()/2f,getHeight()/2f+14f*scale);c.scale(scale,scale);
        // Warm light spreads out from the flame, not from the screen edges.
        if(spread>0f){
            c.save();c.translate(0,-53);
            float haloScale=.35f+.65f*spread;c.scale(haloScale,haloScale);
            c.translate(0,53);
            paint.setShader(haloGradient);
            paint.setAlpha((int)(255*spread*(1f+.018f*wave)));
            c.drawCircle(0,-53,132,paint);paint.setShader(null);paint.setAlpha(255);
            c.restore();
        }
        paint.setColor(0x18A18B62);
        c.drawOval(-54,59,54,72,paint);
        paint.setShader(bodyGradient);c.drawPath(bowl,paint);paint.setShader(null);
        paint.setColor(0xFFA78D5E);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.6f);
        c.drawPath(bowl,paint);paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFD9C395);c.drawOval(-49,29,49,51,paint);
        paint.setColor(0xFFA78D5E);paint.setStyle(Paint.Style.STROKE);
        c.drawOval(-49,29,49,51,paint);paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE5D1A7);c.drawPath(neck,paint);
        paint.setColor(0xFFA78D5E);paint.setStyle(Paint.Style.STROKE);
        c.drawPath(neck,paint);paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF8C7352);paint.setStrokeWidth(3f);paint.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(0,-7,0,-15,paint);
        if(lit>0f){
            c.save();c.translate(0,-12);
            c.rotate(1.3f*sway);
            c.scale(lit*(1f-.025f*wave),lit*(1f+.04f*wave));
            c.translate(0,12);
            paint.setShader(flameGradient);paint.setAlpha((int)(255*lit));
            c.drawPath(flame,paint);paint.setShader(null);
            paint.setColor(0xFFFFF0B5);paint.setAlpha((int)(255*lit));c.drawPath(innerFlame,paint);
            paint.setAlpha(255);c.restore();
        }
        if(rays>0f){
            paint.setColor(0xFFC4A46B);paint.setAlpha((int)(210*rays));paint.setStrokeWidth(1.7f);
            for(int angle=-60;angle<=60;angle+=30){
                c.save();c.rotate(angle,0,-49);
                c.drawLine(0,-115,0,-115-11*rays,paint);c.restore();
            }
            paint.setAlpha(255);
        }
        c.restore();
    }
}
