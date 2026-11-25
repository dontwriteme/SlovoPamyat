package org.biblememory.app;

import android.animation.ValueAnimator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

final class TabTransition {
    private final ScreenUi ui;
    private View tabSwitchOldNav;
    private ValueAnimator tabSwitchAnimator;
    private LinearLayout tabSwitchLeaving,tabSwitchEntering;
    private boolean tabSwitchInProgress=false;

    TabTransition(ScreenUi ui){this.ui=ui;}

    boolean inProgress(){return tabSwitchInProgress;}

    void start(ViewGroup window,LinearLayout leaving,LinearLayout entering,boolean leavingHasNav,
               int width,int height,int navHeight,int direction,Runnable settled){
        // Нижняя панель не уезжает вместе с экраном.
        if (leavingHasNav && leaving.getChildCount() > 0) {

            tabSwitchOldNav =
                    leaving.getChildAt(leaving.getChildCount() - 1);

            leaving.removeViewAt(
                    leaving.getChildCount() - 1
            );
        }

        FrameLayout.LayoutParams overlay =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(ui.dp(1), height - navHeight),
                        Gravity.TOP
                );

        window.addView(leaving, overlay);

        tabSwitchLeaving = leaving;
        tabSwitchEntering = entering;
        tabSwitchInProgress = true;

        // Новый экран уже расположен рядом с текущим.
        for (int i = 0;
             i < entering.getChildCount() - 1;
             i++) {

            entering.getChildAt(i)
                    .setTranslationX(direction * width);
        }

        ValueAnimator animator =
                ValueAnimator.ofFloat(0f, 1f);

        tabSwitchAnimator = animator;

        animator.setDuration(225L);
        animator.setInterpolator(
                new DecelerateInterpolator(1.4f)
        );

        animator.addUpdateListener(a -> {
            if (tabSwitchAnimator != animator) return;

            float fraction = (Float) a.getAnimatedValue();

            leaving.setTranslationX(
                    -direction * width * fraction
            );

            for (int i = 0;
                 i < entering.getChildCount() - 1;
                 i++) {

                entering.getChildAt(i).setTranslationX(
                        direction * width * (1f - fraction)
                );
            }
        });

        animator.addListener(
                new android.animation.AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationEnd(
                            android.animation.Animator animation) {

                        if (tabSwitchAnimator != animator) return;

                        tabSwitchAnimator = null;

                        finish();
                        settled.run();
                    }
                }
        );

        animator.start();
    }

    /** Drop a previous transition before rebuilding a page or processing system Back. */

    void finish() {
        ValueAnimator active = tabSwitchAnimator;
        tabSwitchAnimator = null;

        if (active != null) {
            active.cancel();
        }

        if (tabSwitchLeaving != null) {
            tabSwitchLeaving.setTranslationX(0f);

            if (tabSwitchLeaving.getParent() instanceof ViewGroup) {
                ((ViewGroup) tabSwitchLeaving.getParent())
                        .removeView(tabSwitchLeaving);
            }

            // Возвращаем нижнюю панель на сохранённый экран.
            if (tabSwitchOldNav != null
                    && tabSwitchOldNav.getParent() == null) {
                tabSwitchLeaving.addView(tabSwitchOldNav);
            }
        }

        tabSwitchOldNav = null;

        if (tabSwitchEntering != null) {
            for (int i = 0;
                 i < tabSwitchEntering.getChildCount() - 1;
                 i++) {

                tabSwitchEntering.getChildAt(i)
                        .setTranslationX(0f);
            }
        }

        tabSwitchLeaving = null;
        tabSwitchEntering = null;
        tabSwitchInProgress = false;
    }


}
