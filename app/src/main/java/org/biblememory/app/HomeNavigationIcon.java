package org.biblememory.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.provider.Settings;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A finite, gesture-driven welcome: warm window, then smoke from the chimney. */
final class HomeNavigationIcon extends View {
    static final String TAG = "sv-home-house";
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();
    private final Path smoke = new Path();
    private final int ink;
    private ValueAnimator animator;
    private float light, pulse, elapsed;
    private boolean repeating, smoking;

    HomeNavigationIcon(Context context, int ink, boolean selected) {
        super(context);
        this.ink = ink;
        setTag(TAG);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSelected(selected);
        settle();
    }

    private boolean motionEnabled() {
        return ValueAnimator.areAnimatorsEnabled()
                && Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) >= 1f;
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
                light = smooth(elapsed / 260f);
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
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        float unit = Math.min(getWidth(), getHeight()) / 27f;
        canvas.scale(unit, unit);
        // Only the house breathes. The smoke has its own bounded drawing area above it.
        canvas.save();
        float breath = smoking && !repeating
                ? .035f * (float) Math.sin(Math.PI * smooth(elapsed / 140f)) : 0f;
        canvas.scale(1f + breath, 1f + breath);
        pen.setColor(ink);
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(1.77f); // Same optical weight as NavigationGlyph's 23/24 * 1.85.
        pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStrokeJoin(Paint.Join.ROUND);
        // Preserve the familiar gabled silhouette and centred door.
        outline.reset();
        outline.moveTo(-9.6f, -.9f);
        outline.lineTo(0f, -9.6f);
        outline.lineTo(9.6f, -.9f);
        outline.lineTo(9.6f, 9.6f);
        outline.lineTo(-9.6f, 9.6f);
        outline.close();
        canvas.drawPath(outline, pen);
        canvas.drawRoundRect(-1.9f, 3f, 1.9f, 9.6f, .8f, .8f, pen);
        // A short chimney on the right slope, contained inside the existing icon slot.
        outline.reset();
        outline.moveTo(5.3f, -4.8f);
        outline.lineTo(5.3f, -7.6f);
        outline.lineTo(7.8f, -7.6f);
        outline.lineTo(7.8f, -2.5f);
        canvas.drawPath(outline, pen);
        // The small window interpolates to amber, with no glow outside its frame.
        float brightness = Math.min(1f, light * .86f + pulse);
        int red = (int) (Color.red(ink) + (245 - Color.red(ink)) * brightness);
        int green = (int) (Color.green(ink) + (196 - Color.green(ink)) * brightness);
        int blue = (int) (Color.blue(ink) + (106 - Color.blue(ink)) * brightness);
        pen.setStyle(Paint.Style.FILL);
        pen.setColor(Color.argb((int) (255 * light), red, green, blue));
        canvas.drawRoundRect(4f, 2.3f, 7.2f, 5.5f, .55f, .55f, pen);
        pen.setColor(ink);
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(1f);
        canvas.drawRoundRect(4f, 2.3f, 7.2f, 5.5f, .55f, .55f, pen);
        canvas.restore();

        if (smoking) {
            int curls = repeating ? 1 : 3;
            float delay = repeating ? 0f : 180f;
            float lifetime = repeating ? 620f : 650f;
            pen.setStrokeWidth(.85f);
            for (int i = 0; i < curls; i++) {
                float t = (elapsed - delay - i * 110f) / lifetime;
                if (t <= 0f || t >= 1f) continue;
                float alpha = smooth(t / .15f) * (1f - smooth(t));
                pen.setColor(ink);
                pen.setAlpha((int) (140 * alpha));
                float x = 6.55f + .7f * t + .25f * (float) Math.sin(t * Math.PI * 2);
                float y = -8.1f - 2.7f * smooth(t);
                // All control points (including stroke) remain above -12.7, within ±13.5.
                smoke.reset();
                smoke.moveTo(x, y);
                smoke.cubicTo(x - .9f, y - .55f, x + .9f, y - 1.1f, x + .3f, y - 1.5f);
                canvas.drawPath(smoke, pen);
            }
            pen.setAlpha(255);
        }
        canvas.restore();
    }
}
