package org.biblememory.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.graphics.RadialGradient;
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
    // Keep the supplied painted artwork; only the glass and smoke are animated.
    private static Bitmap illustration;
    private final RectF illustrationBounds = new RectF(-13f,-11.9f,13f,14.1f);
    private final Path glass = new Path();
    private final Shader amber = new RadialGradient(584,591,115,
            new int[]{0xFFFFF4B2,0xFFFFD269,0xFFE59C36},
            new float[]{0f,.55f,1f},Shader.TileMode.CLAMP);
    private ValueAnimator animator;
    private float light, pulse, elapsed;
    private boolean repeating, smoking;

    HomeNavigationIcon(Context context, int ink, boolean selected) {
        super(context);
        if(illustration==null){
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inScaled=false;options.inSampleSize=4;
            illustration=BitmapFactory.decodeResource(getResources(),R.drawable.home_house_illustration,options);
        }
        // Coordinates are normalized to the PNG's 1000-unit square. Mask only the
        // four glass panes, leaving the original arch, wooden frame and mullions intact.
        glass.moveTo(499,583);glass.cubicTo(499,544,533,509,571,508);
        glass.lineTo(571,583);glass.close();
        glass.moveTo(597,508);glass.cubicTo(636,510,667,545,668,583);
        glass.lineTo(597,583);glass.close();
        glass.addRoundRect(499,608,571,686,2,2,Path.Direction.CW);
        glass.addRoundRect(597,608,668,686,2,2,Path.Direction.CW);
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
        pen.setStyle(Paint.Style.FILL);pen.setShader(null);pen.setAlpha(255);
        pen.setFilterBitmap(true);
        canvas.drawBitmap(illustration,null,illustrationBounds,pen);
        canvas.save();
        canvas.translate(illustrationBounds.left,illustrationBounds.top);
        canvas.scale(illustrationBounds.width()/1000f,illustrationBounds.height()/1000f);
        pen.setShader(amber);pen.setAlpha((int)(245*light));
        canvas.drawPath(glass,pen);pen.setShader(null);
        pen.setColor(0xFFFFF6C6);pen.setAlpha((int)(255*pulse*light));
        canvas.drawPath(glass,pen);
        canvas.restore();
        canvas.restore();

        // The PNG chimney outlet is at approximately (5.4,-7.5). Reserve the top 5dp for finite curls.
        // On the unchanged pale indicator, dark sage is more legible than pale smoke.
        if(smoking) {
            int curls=repeating?1:3;
            float delay=repeating?0f:180f;
            float lifetime=repeating?620f:440f;
            pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(.6f);
            pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);
            for(int i=0;i<curls;i++) {
                float t=(elapsed-delay-i*210f)/lifetime;
                if(t<=0f||t>=1f)continue;
                float alpha=smooth(t/.15f)*(1f-smooth(t));
                pen.setColor(0xFF486C55);pen.setAlpha((int)(210*alpha));
                float x=5.4f+.65f*t+.3f*(float)Math.sin(t*Math.PI*2);
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
