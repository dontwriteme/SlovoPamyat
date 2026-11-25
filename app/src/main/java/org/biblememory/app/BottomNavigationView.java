package org.biblememory.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;
import static org.biblememory.app.ScreenUi.*;

final class BottomNavigationView {
    private final Context context;
    private final ScreenUi ui;
    private int lastNavIndex;
    BottomNavigationView(Context context,ScreenUi ui){this.context=context;this.ui=ui;}
    int lastIndex(){return lastNavIndex;}
    void setLastIndex(int index){lastNavIndex=index;}

    void captureCollectionsIcon(View root){
        CollectionsNavigationIcon icon=root==null?null:root.findViewWithTag(CollectionsNavigationIcon.TAG);
        if(icon!=null&&icon.isSelected())collectionsIconCycle=icon.settledCycle();
    }

    private int collectionsIconCycle=1;
    /** A raised four-destination navigation pill. */
    void render(LinearLayout parent,int current,IntConsumer select){
        final String[] names={"Главная","Библия","Коллекции","Настройки"};
        LinearLayout outer=ui.column();outer.setPadding(ui.dp(16),ui.dp(4),ui.dp(16),ui.dp(12));
        FrameLayout floating=new FrameLayout(context);
        floating.setBackground(ui.outlined(CARD,36,BORDER));
        floating.setClipToOutline(false);
        final View indicator=new View(context);
        indicator.setTag("sv-nav-indicator");
        indicator.setBackground(ui.shape(0xFFF7DED0,29));
        floating.addView(indicator,new FrameLayout.LayoutParams(ui.dp(80),ui.dp(57),Gravity.TOP|Gravity.LEFT));
        LinearLayout row=ui.row();row.setPadding(ui.dp(7),ui.dp(7),ui.dp(7),ui.dp(7));
        for(int i=0;i<Screen.TAB_COUNT;i++){
            final int index=i;
            int ink=i==current?0xFFB7553D:MUTED;
            LinearLayout tab=ui.column();tab.setGravity(Gravity.CENTER);
            tab.setPadding(ui.dp(5),ui.dp(3),ui.dp(5),ui.dp(3));
            View symbol=i==0 ? new HomeNavigationIcon(context,ink,current==0)
                    : i==1 ? new BookNavigationIcon(context,ink,current==1)
                    : i==2 ? new CollectionsNavigationIcon(context,ink,current==2,collectionsIconCycle)
                    : new NavigationGlyph(context,"settings",ink);
            LinearLayout.LayoutParams glyph=new LinearLayout.LayoutParams(ui.dp(27),ui.dp(27));
            glyph.gravity=Gravity.CENTER_HORIZONTAL;tab.addView(symbol,glyph);
            TextView name=ui.txt(names[i],12,ink,i==current);
            name.setGravity(Gravity.CENTER);ui.add(tab,name,1);
            tab.setClickable(true);tab.setFocusable(true);
            tab.setContentDescription("Открыть раздел "+names[i]);
            tab.setOnClickListener(v->select.accept(index));
            row.addView(tab,new LinearLayout.LayoutParams(0,ui.dp(57),1));
        }
        floating.addView(row,new FrameLayout.LayoutParams(-1,ui.dp(71)));
        outer.addView(floating,new LinearLayout.LayoutParams(-1,ui.dp(71)));
        parent.addView(outer,new LinearLayout.LayoutParams(-1,-2));
        final int from=lastNavIndex;
        floating.post(()->{
            int slot=(floating.getWidth()-ui.dp(14))/Screen.TAB_COUNT;
            if(slot<=0)return;
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)indicator.getLayoutParams();
            p.width=slot;p.height=ui.dp(57);p.leftMargin=ui.dp(7);p.topMargin=ui.dp(7);
            indicator.setLayoutParams(p);
            indicator.setTranslationX(slot*from);
            if(from!=current && ValueAnimator.areAnimatorsEnabled()){
                indicator.animate().translationX(slot*current).setDuration(240)
                    .setInterpolator(new DecelerateInterpolator()).start();
            }else indicator.setTranslationX(slot*current);
        });
        lastNavIndex=current;
    }
    void animateCollectionsIcon(View page,boolean selected,boolean previous){
        if(page==null)return;
        CollectionsNavigationIcon icon=page.findViewWithTag(CollectionsNavigationIcon.TAG);
        if(icon!=null)icon.transition(selected,previous);
    }
    void settleCollectionsIcon(View page){
        CollectionsNavigationIcon icon=page.findViewWithTag(CollectionsNavigationIcon.TAG);
        if(icon!=null)icon.settle();
    }
    void animateHomeIcon(View page, boolean selected, boolean previous) {
        if(page==null)return;
        HomeNavigationIcon icon=page.findViewWithTag(HomeNavigationIcon.TAG);
        if(icon!=null)icon.transition(selected,previous);
    }
    void settleHomeIcon(View page) {
        HomeNavigationIcon icon=page.findViewWithTag(HomeNavigationIcon.TAG);
        if(icon!=null)icon.settle();
    }
    void animateBibleIcon(View page, boolean selected, boolean previous, float[] state) {
        if(page==null)return;
        BookNavigationIcon icon=page.findViewWithTag(BookNavigationIcon.TAG);
        if(icon!=null)icon.transition(selected,previous,state);
    }
    void settleBibleIcon(View page) {
        BookNavigationIcon icon=page.findViewWithTag(BookNavigationIcon.TAG);
        if(icon!=null)icon.settle();
    }
    void animatePreparedTabIndicator(
            LinearLayout page,
            int from,
            int to) {

        View indicator =
                page.findViewWithTag("sv-nav-indicator");

        if (indicator == null
                || !(indicator.getParent() instanceof FrameLayout)) {
            return;
        }

        FrameLayout bar = (FrameLayout) indicator.getParent();

        bar.post(() -> {
            if (indicator.getParent() != bar) return;

            int slot = (bar.getWidth() - ui.dp(14)) / 4;
            if (slot <= 0) return;

            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams)
                            indicator.getLayoutParams();

            params.width = slot;
            params.height = ui.dp(57);
            params.leftMargin = ui.dp(7);
            params.topMargin = ui.dp(7);

            indicator.setLayoutParams(params);
            indicator.animate().cancel();

            indicator.setTranslationX(slot * from);

            indicator.animate()
                    .translationX(slot * to)
                    .setDuration(225L)
                    .setInterpolator(
                            new DecelerateInterpolator(1.4f)
                    )
                    .start();
        });
    }


}
