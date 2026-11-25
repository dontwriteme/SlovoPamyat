package org.biblememory.app;

import android.graphics.Color;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Native anchored actions with no dependency on the selected screen. */
final class ScreenMenus {
    private final ScreenUi ui;

    ScreenMenus(ScreenUi ui) {
        this.ui = ui;
    }

    interface ActionSelection {void select(int index);}

    /** Native anchored popup; no new libraries or drawable assets are required. */
    void showActionsMenu(View anchor,String[] labels,String[] iconKinds,ActionSelection selection){
        if(labels.length!=iconKinds.length)throw new IllegalArgumentException("Menu labels/icons mismatch");
        LinearLayout menu=ui.column();
        menu.setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));
        menu.setBackground(ui.outlined(0xFFFFFCF9,18,0xFFE8D7CA));
        menu.setClipToOutline(true);
        Rect visibleMenuArea=new Rect();anchor.getWindowVisibleDisplayFrame(visibleMenuArea);
        int available=Math.max(ui.dp(160),visibleMenuArea.width()-ui.dp(24));
        // Give labels space on normal phones; on narrow screens allow two rows.
        final int popupWidth=Math.min(ui.dp(316),available);
        ScrollView menuScroll=new ScrollView(anchor.getContext());
        menuScroll.setVerticalScrollBarEnabled(false);
        menuScroll.addView(menu,new android.widget.FrameLayout.LayoutParams(-1,-2));
        PopupWindow popup=new PopupWindow(menuScroll,popupWidth,ui.dp(270),true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(ui.dp(10));
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setClippingEnabled(true);
        for(int i=0;i<labels.length;i++){
            final int index=i;
            boolean destructive="trash".equals(iconKinds[i]);
            if(destructive){
                View rule=new View(anchor.getContext());rule.setBackgroundColor(0xFFF0E3D9);
                LinearLayout.LayoutParams ruleP=new LinearLayout.LayoutParams(-1,ui.dp(1));
                ruleP.setMargins(ui.dp(8),ui.dp(5),ui.dp(8),ui.dp(5));menu.addView(rule,ruleP);
            }
            LinearLayout item=ui.row();item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(ui.dp(9),0,ui.dp(9),0);
            item.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x24C78E6D),
                ui.shape(Color.TRANSPARENT,12),ui.shape(Color.WHITE,12)));
            int ink=destructive?0xFFB9504D:0xFF91674E;
            MenuGlyph glyph=new MenuGlyph(anchor.getContext(),iconKinds[i],ink);
            glyph.setBackground(ui.shape(destructive?0xFFFBE8E5:0xFFF7EDE5,10));
            LinearLayout.LayoutParams glyphP=new LinearLayout.LayoutParams(ui.dp(35),ui.dp(35));
            glyphP.rightMargin=ui.dp(12);item.addView(glyph,glyphP);
            TextView text=ui.txt(labels[i],14,destructive?0xFFB9504D:0xFF4A4038,true);
            text.setGravity(Gravity.CENTER_VERTICAL);
            text.setSingleLine(false);text.setMaxLines(2);
            item.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            item.setClickable(true);item.setFocusable(true);
            item.setContentDescription(labels[i]);
            item.setOnClickListener(v->{popup.dismiss();selection.select(index);});
            item.setMinimumHeight(ui.dp(labels[i].length()>26?70:54));
            menu.addView(item,new LinearLayout.LayoutParams(-1,-2));
        }
        Rect screen=new Rect();anchor.getWindowVisibleDisplayFrame(screen);
        int[] onScreen=new int[2];anchor.getLocationOnScreen(onScreen);
        int spaceAbove=onScreen[1]-screen.top-ui.dp(8);
        int spaceBelow=screen.bottom-(onScreen[1]+anchor.getHeight())-ui.dp(8);
        // Height follows the actual rendered rows, including multi-line labels.
        menu.measure(View.MeasureSpec.makeMeasureSpec(popupWidth,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int needed=menu.getMeasuredHeight();
        boolean above=spaceBelow<needed&&spaceAbove>spaceBelow;
        int free=Math.max(0,above?spaceAbove:spaceBelow);
        int popupHeight=Math.min(needed,free);
        if(popupHeight<ui.dp(48)){popupHeight=Math.min(needed,Math.max(ui.dp(48),screen.height()-ui.dp(32)));}
        popup.setHeight(popupHeight);
        int x=Math.max(screen.left+ui.dp(8),Math.min(screen.right-popupWidth-ui.dp(8),
            onScreen[0]+anchor.getWidth()-popupWidth));
        int y=above?onScreen[1]-popupHeight-ui.dp(6):onScreen[1]+anchor.getHeight()+ui.dp(6);
        y=Math.max(screen.top+ui.dp(4),Math.min(y,screen.bottom-popupHeight-ui.dp(4)));
        popup.showAtLocation(anchor,Gravity.TOP|Gravity.LEFT,x,y);
    }

}
