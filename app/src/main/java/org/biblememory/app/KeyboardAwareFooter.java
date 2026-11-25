package org.biblememory.app;

import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;

/**
 * Hide lesson actions while the IME is visible, without changing the editor,
 * its focus, or the window's ADJUST_RESIZE behavior.
 *
 * One binding per screen root. Listeners live only while that root is attached.
 */
final class KeyboardAwareFooter implements View.OnAttachStateChangeListener {
    static final String TAG="keyboard-aware-footer";
    private final View root,footer;
    private final Rect visibleFrame=new Rect();
    private final int[] decorLocation=new int[2];
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener=this::refresh;
    private ViewTreeObserver observer;
    private Boolean keyboardVisible;

    static void bind(View root,View footer){
        KeyboardAwareFooter binding=new KeyboardAwareFooter(root,footer);
        root.addOnAttachStateChangeListener(binding);
        if(root.isAttachedToWindow())binding.onViewAttachedToWindow(root);
    }

    KeyboardAwareFooter(View root,View footer){
        this.root=root;this.footer=footer;
        footer.setTag(TAG);
    }

    @Override public void onViewAttachedToWindow(View view){
        unregister();
        keyboardVisible=null;
        observer=root.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(layoutListener);
        // Android 11+ reports actual IME visibility even for a floating keyboard.
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30)
                updateVisibility(insets.isVisible(WindowInsets.Type.ime()),true);
            else refresh();
            // Do not consume insets; keep normal system resizing and scrolling.
            return v.onApplyWindowInsets(insets);
        });
        refresh();
        root.requestApplyInsets();
    }

    @Override public void onViewDetachedFromWindow(View view){
        unregister();
        root.setOnApplyWindowInsetsListener(null);
        footer.animate().cancel();
        footer.setAlpha(1f);
        keyboardVisible=null;
    }

    private void unregister(){
        if(observer!=null&&observer.isAlive())
            observer.removeOnGlobalLayoutListener(layoutListener);
        observer=null;
    }

    private void refresh(){
        if(!root.isAttachedToWindow())return;
        WindowInsets insets=root.getRootWindowInsets();
        if(Build.VERSION.SDK_INT>=30){
            if(insets!=null)updateVisibility(insets.isVisible(WindowInsets.Type.ime()),true);
            return;
        }
        // Android 8–10: compare obscured height within this window, rather than
        // the physical display (which may include another app in split screen).
        View decor=root.getRootView();
        if(decor.getHeight()<=0)return;
        decor.getLocationOnScreen(decorLocation);
        decor.getWindowVisibleDisplayFrame(visibleFrame);
        int obscured=Math.max(0,decorLocation[1]+decor.getHeight()-visibleFrame.bottom);
        int stableBottom=0;
        if(insets!=null){
            obscured=Math.max(obscured,insets.getSystemWindowInsetBottom());
            stableBottom=insets.getStableInsetBottom();
        }
        int threshold=Math.round(100*root.getResources().getDisplayMetrics().density);
        updateVisibility(legacyKeyboardVisible(obscured,stableBottom,threshold),true);
    }

    static boolean legacyKeyboardVisible(int obscured,int stableBottom,int threshold){
        return obscured-Math.max(0,stableBottom)>threshold;
    }

    void updateVisibility(boolean visible,boolean animate){
        if(keyboardVisible!=null&&keyboardVisible==visible)return;
        boolean initial=keyboardVisible==null;
        keyboardVisible=visible;
        footer.animate().cancel();
        if(visible){
            // Reclaim ALL footer space immediately; the keyboard itself animates
            // the window resize. No invisible reserved height or hidden touch target.
            footer.setAlpha(1f);
            footer.setVisibility(View.GONE);
        }else{
            boolean fade=!initial&&animate&&ValueAnimator.areAnimatorsEnabled();
            footer.setAlpha(fade?0f:1f);
            footer.setVisibility(View.VISIBLE);
            if(fade)footer.animate().alpha(1f).setDuration(180)
                .setInterpolator(new DecelerateInterpolator()).start();
        }
    }
}
