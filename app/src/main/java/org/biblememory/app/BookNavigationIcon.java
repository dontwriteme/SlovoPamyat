package org.biblememory.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A tiny hinged book. All motion stays inside the original 27dp icon slot. */
final class BookNavigationIcon extends View {
    static final String TAG = "sv-bible-book";
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path page = new Path();
    private final int ink;
    private ValueAnimator animator;
    private float opening, turn, lift, squash;

    BookNavigationIcon(Context context, int ink, boolean selected) {
        super(context);
        this.ink = ink;
        setTag(TAG);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSelected(selected);
        settle();
    }

    /** Called only for a navigation gesture; binding/restoration uses settle(). */
    void transition(boolean selected, boolean previouslySelected) {
        // Finish an in-flight opening/turn on repeated taps; never enqueue or snap shut.
        if(selected && isSelected() && animator!=null && ValueAnimator.areAnimatorsEnabled())return;
        cancelMotion();
        setSelected(selected);
        opening = previouslySelected ? 1f : 0f;
        if (!ValueAnimator.areAnimatorsEnabled()) { settle(); return; }
        final float start = opening;
        final boolean repeat = selected && previouslySelected;
        final long duration = selected ? (repeat ? 280L : 540L) : 150L;
        ValueAnimator motion = ValueAnimator.ofFloat(0f, duration);
        animator = motion;
        motion.setDuration(duration);
        motion.setInterpolator(new LinearInterpolator());
        motion.addUpdateListener(a -> {
            float ms = (Float) a.getAnimatedValue();
            if (!selected) {
                opening = start * (1f - smooth(ms / duration));
                turn = lift = squash = 0f;
            } else if (repeat) {
                opening = 1f;
                turn = smooth(ms / 250f);
                lift = .35f * (float) Math.sin(Math.PI * ms / duration);
                squash = 0f;
            } else {
                opening = smooth((ms - 60f) / 220f);
                turn = smooth((ms - 250f) / 210f);
                // Compression, a small rise, then a damped landing; no layout transforms.
                squash = ms < 60f ? .10f * smooth(ms / 60f)
                        : .10f * (1f - smooth((ms - 60f) / 100f));
                lift = ms < 160f ? 1.35f * smooth((ms - 60f) / 100f)
                        : 1.35f * (float) (Math.exp(-(ms - 160f) / 110f)
                        * Math.cos((ms - 160f) * Math.PI / 180f));
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
        opening = isSelected() ? 1f : 0f;
        turn = lift = squash = 0f;
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
        canvas.clipRect(0, 0, getWidth(), getHeight());
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        float scale = Math.min(getWidth(), getHeight()) / 27f;
        canvas.scale(scale, scale);
        canvas.translate(0, -lift);
        canvas.scale(1f, 1f - squash);
        pen.setColor(ink);
        pen.setStrokeWidth(1.65f);
        pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStrokeJoin(Paint.Join.ROUND);
        pen.setStyle(Paint.Style.STROKE);

        // The spine moves from the left edge of a shut cover to the centre of a spread.
        float spine = -6f * (1f - opening);
        float right = 7f + 3f * opening;
        float left = spine + (right - spine) * (1f - 2f * opening);
        leaf(canvas, spine, right, opening);
        if (opening > .01f) leaf(canvas, spine, left, opening);
        canvas.drawLine(spine, -8f, spine, 9f, pen);
        if (opening < .65f) {
            pen.setAlpha((int) (255 * (1f - smooth(opening / .65f))));
            canvas.drawLine(spine + 2.4f, -7f, spine + 2.4f, 8f, pen);
            canvas.drawLine(spine + 2.8f, 9.4f, right - .8f, 9.4f, pen);
            pen.setAlpha(255);
        }
        // A single curved sheet rotates around the spine; its projected width crosses zero.
        if (turn > 0f && turn < 1f) {
            float angle = (float) (Math.PI * turn);
            float edge = spine + (right - spine) * (float) Math.cos(angle);
            float bow = 2.8f * (float) Math.sin(angle);
            page.reset();
            page.moveTo(spine, -8f);
            page.cubicTo(spine + (edge-spine)*.5f, -9f-bow, edge, -9f-bow, edge, -8f-bow*.4f);
            page.quadTo(edge - bow*.35f, 0f, edge, 8f-bow*.4f);
            page.quadTo((spine+edge)*.5f, 7f-bow, spine, 9f);
            page.close();
            pen.setStyle(Paint.Style.FILL);
            pen.setColor(0xFFFFFAF5); // Existing warm paper palette; hides text beneath the sheet.
            canvas.drawPath(page, pen);
            pen.setColor(ink);
            pen.setStyle(Paint.Style.STROKE);
            canvas.drawPath(page, pen);
        }
        canvas.restore();
    }

    private void leaf(Canvas canvas, float spine, float edge, float spread) {
        float width = edge - spine;
        page.reset();
        page.moveTo(spine, -8f);
        page.cubicTo(spine + width*.3f, -8f-2f*spread, edge-width*.2f, -9f- spread, edge, -8f);
        page.lineTo(edge, 8f);
        page.quadTo(spine+width*.5f, 7f-spread, spine, 9f);
        page.close();
        canvas.drawPath(page, pen);
        if (spread > .35f) {
            pen.setAlpha((int) (255 * smooth((spread-.35f)/.65f)));
            pen.setStrokeWidth(1.1f);
            for (int y = -4; y <= 4; y += 4)
                canvas.drawLine(spine+width*.28f, y, spine+width*.77f, y-.7f, pen);
            pen.setAlpha(255);
            pen.setStrokeWidth(1.65f);
        }
    }
}
