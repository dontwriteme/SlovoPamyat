package org.biblememory.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Paint;
import android.graphics.Path;
import android.provider.Settings;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A finite, gesture-driven welcome: warm window, then smoke from the chimney. */
final class HomeNavigationIcon extends View {
    static final String TAG = "sv-home-house";
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path smoke = new Path();
    private final Path house = new Path();
    private final Path roof = new Path();
    private final Path window = arch(-6.8f, 1.1f, -.9f, 8f, 2.6f);
    private final Path door = arch(.8f, 1.8f, 6.6f, 10.5f, 2.8f);
    private final Shader walls = new LinearGradient(-7,-3,7,11,
            0xFFFFF2D8,0xFFE9CCA5,Shader.TileMode.CLAMP);
    private final Shader olive = new LinearGradient(0,-8,0,11,
            0xFF99A773,0xFF65774D,Shader.TileMode.CLAMP);
    private final Shader doorShade = new LinearGradient(1,0,7,0,
            0xFF8C9866,0xFF687849,Shader.TileMode.CLAMP);
    private final Shader amber = new LinearGradient(-4,2,-4,8,
            0xFFFFE49B,0xFFFFC94E,Shader.TileMode.CLAMP);
    private ValueAnimator animator;
    private float light, pulse, elapsed;
    private boolean repeating, smoking;

    HomeNavigationIcon(Context context, int ink, boolean selected) {
        super(context);
        house.moveTo(-8.8f,.5f);house.lineTo(0,-6.9f);house.lineTo(8.8f,.5f);
        house.lineTo(8.8f,9.6f);house.quadTo(8.8f,11,-7.6f,11);
        house.quadTo(-8.8f,11,-8.8f,9.6f);house.close();
        roof.moveTo(-10.2f,.8f);roof.lineTo(-.7f,-7.4f);
        roof.quadTo(0,-8,.7f,-7.4f);roof.lineTo(10.2f,.8f);
        setTag(TAG);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSelected(selected);
        settle();
    }

    private boolean motionEnabled() {
        return ValueAnimator.areAnimatorsEnabled()
                && Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }

    void transition(boolean selected, boolean previouslySelected) {
        if (!motionEnabled()) {
            setSelected(selected);
            settle();
            return;
        }
        // Repeated taps finish the current welcome; they never queue more smoke.
        if (selected && isSelected() && animator != null) return;
        cancelMotion();
        setSelected(selected);
        repeating = selected && previouslySelected;
        light = previouslySelected ? 1f : 0f;
        pulse = elapsed = 0f;
        smoking = selected;
        final float startLight = light;
        final long duration = selected ? (repeating ? 650L : 1050L) : 180L;
        ValueAnimator motion = ValueAnimator.ofFloat(0f, duration);
        animator = motion;
        motion.setDuration(duration);
        motion.setInterpolator(new LinearInterpolator());
        motion.addUpdateListener(a -> {
            elapsed = (Float) a.getAnimatedValue();
            if (!selected) {
                light = startLight * (1f - smooth(elapsed / duration));
                pulse = 0f;
            } else if (repeating) {
                light = 1f;
                pulse = .18f * (float) Math.sin(Math.PI * Math.min(1f, elapsed / 300f));
            } else {
                light = smooth((elapsed - 100f) / 250f);
                pulse = .16f * (float) Math.sin(Math.PI * smooth((elapsed - 170f) / 240f));
            }
            invalidate();
        });
        motion.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator == motion) { animator = null; settle(); }
            }
        });
        motion.start();
    }

    void settle() {
        cancelMotion();
        light = isSelected() ? 1f : 0f;
        elapsed = pulse = 0f;
        smoking = repeating = false;
        invalidate();
    }

    private void cancelMotion() {
        ValueAnimator previous = animator;
        animator = null;
        if (previous != null) {
            previous.removeAllUpdateListeners();
            previous.removeAllListeners();
            previous.cancel();
        }
    }

    private static float smooth(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }

    @Override protected void onDetachedFromWindow() {
        settle();
        super.onDetachedFromWindow();
    }

    private static Path arch(float left,float top,float right,float bottom,float radius) {
        Path path=new Path();
        path.moveTo(left,bottom);path.lineTo(left,top+radius);
        path.cubicTo(left,top,right,top,right,top+radius);
        path.lineTo(right,bottom);path.close();return path;
    }

    private void fill(Canvas canvas,Path path,int color,Shader shader) {
        pen.setStyle(Paint.Style.FILL);pen.setColor(color);pen.setShader(shader);
        canvas.drawPath(path,pen);pen.setShader(null);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(getWidth()/2f,getHeight()/2f);
        float unit=Math.min(getWidth(),getHeight())/27f;
        canvas.scale(unit,unit);
        canvas.save();
        float breath=smoking&&!repeating
                ? .035f*(float)Math.sin(Math.PI*smooth(elapsed/150f)):0f;
        canvas.scale(1f+breath,1f+breath);
        pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);

        // Native miniature of the supplied reference: olive roof, cream facade,
        // arched window on the left, door on the right and cross in the gable.
        pen.setStyle(Paint.Style.FILL);pen.setColor(0xFF7E905F);
        canvas.drawRoundRect(-11,7.2f,-6.8f,11.3f,2,2,pen);
        canvas.drawRoundRect(7,7.8f,11,11.3f,1.9f,1.9f,pen);
        fill(canvas,house,0xFFFFF2D8,walls);
        pen.setColor(0xFFF2D9B6);
        canvas.drawRoundRect(4.1f,-6.8f,7.4f,-1.7f,.4f,.4f,pen);
        pen.setColor(0xFF7B8E58);
        canvas.drawRoundRect(3.7f,-7.5f,7.8f,-6.1f,.6f,.6f,pen);
        pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(2.7f);
        pen.setShader(olive);canvas.drawPath(roof,pen);pen.setShader(null);
        pen.setStrokeWidth(.85f);pen.setColor(0xFF748652);
        canvas.drawLine(0,-3.7f,0,-1.2f,pen);
        canvas.drawLine(-.85f,-2.8f,.85f,-2.8f,pen);

        // The window is almost twice as wide as before, with an opaque amber interior.
        fill(canvas,window,0xFF9DA17F,null);
        pen.setStyle(Paint.Style.FILL);pen.setShader(amber);
        pen.setAlpha((int)(255*light));canvas.drawPath(window,pen);pen.setShader(null);
        pen.setColor(0xFFFFF5C4);pen.setAlpha((int)(255*pulse*light));
        canvas.drawPath(window,pen);
        pen.setColor(0xFF946D35);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(.7f);
        canvas.drawPath(window,pen);
        canvas.drawLine(-3.85f,1.9f,-3.85f,8,pen);
        canvas.drawLine(-6.6f,4.7f,-1.1f,4.7f,pen);
        pen.setStyle(Paint.Style.FILL);pen.setColor(0xFF7E8F57);
        canvas.drawRoundRect(-7.3f,7.8f,-.4f,8.8f,.45f,.45f,pen);

        fill(canvas,door,0xFF788951,doorShade);
        pen.setStyle(Paint.Style.STROKE);pen.setColor(0xFF5D7045);pen.setStrokeWidth(.5f);
        canvas.drawPath(door,pen);
        canvas.drawLine(2.7f,2.6f,2.7f,10.3f,pen);
        canvas.drawLine(4.6f,2.7f,4.6f,10.3f,pen);
        pen.setStyle(Paint.Style.FILL);pen.setColor(0xFFF1C775);
        canvas.drawCircle(5.4f,6.9f,.5f,pen);
        pen.setColor(0xFFF6DDB6);
        canvas.drawRoundRect(.1f,10.1f,7.2f,11.3f,.5f,.5f,pen);
        canvas.restore();

        // The chimney outlet is at (5.75,-7.5). Reserve the top 5dp for finite curls.
        // On the unchanged pale indicator, dark sage is more legible than pale smoke.
        if(smoking) {
            int curls=repeating?1:3;
            float delay=repeating?0f:180f;
            float lifetime=repeating?620f:440f;
            pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(.6f);
            for(int i=0;i<curls;i++) {
                float t=(elapsed-delay-i*210f)/lifetime;
                if(t<=0f||t>=1f)continue;
                float alpha=smooth(t/.15f)*(1f-smooth(t));
                pen.setColor(0xFF486C55);pen.setAlpha((int)(210*alpha));
                float x=5.75f+.65f*t+.3f*(float)Math.sin(t*Math.PI*2);
                float y=-7.8f-2.1f*smooth(t);
                smoke.reset();smoke.moveTo(x,y);
                smoke.cubicTo(x-2.4f,y-.4f,x+2.4f,y-1f,x+.1f,y-1.5f);
                smoke.cubicTo(x-1.6f,y-1.9f,x+1.2f,y-2.3f,x+.7f,y-2.6f);
                canvas.drawPath(smoke,pen);
            }
            pen.setAlpha(255);
        }
        canvas.restore();
    }
}
