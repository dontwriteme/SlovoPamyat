package org.biblememory.app;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowMetrics;

/** Crop the full-window illustration when the IME resizes the content view.
 * The artwork is never scaled to the shortened editor viewport. */
final class StableLessonBackground extends Drawable {
    private final Drawable artwork;
    private int fullHeight;

    static Drawable forWindow(Activity activity,Drawable artwork){
        int height;
        if(Build.VERSION.SDK_INT>=30){
            WindowMetrics metrics=activity.getWindowManager().getCurrentWindowMetrics();
            android.graphics.Insets bars=metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            height=metrics.getBounds().height()-bars.top-bars.bottom;
        }else{
            // Display app-area dimensions exclude the IME and are available
            // even when a new writing screen opens with the keyboard visible.
            Point size=new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(size);
            height=size.y;
        }
        return new StableLessonBackground(artwork,Math.max(1,height));
    }

    StableLessonBackground(Drawable artwork,int fullHeight){
        this.artwork=artwork;
        this.fullHeight=Math.max(1,fullHeight);
    }

    @Override protected void onBoundsChange(Rect bounds){
        if(bounds.isEmpty())return;
        // Some OEM window metrics omit a small inset. Grow to the measured
        // content if necessary, but never shrink when adjustResize runs.
        fullHeight=Math.max(fullHeight,bounds.height());
        artwork.setBounds(bounds.left,bounds.top,bounds.right,bounds.top+fullHeight);
    }

    @Override public void draw(Canvas canvas){
        if(getBounds().isEmpty())return;
        int save=canvas.save();
        canvas.clipRect(getBounds());
        artwork.draw(canvas);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha){artwork.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){artwork.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return artwork.getOpacity();}
}
