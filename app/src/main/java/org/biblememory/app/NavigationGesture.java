package org.biblememory.app;

import android.view.MotionEvent;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class NavigationGesture {
    private final ScreenUi ui;
    private float swipeStartX,swipeStartY;
    private boolean swipeIntercepted;

    NavigationGesture(ScreenUi ui){this.ui=ui;}

    boolean handle(MotionEvent event,IntConsumer changeTab,Consumer<MotionEvent> cancelChild){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            swipeStartX=event.getX();swipeStartY=event.getY();swipeIntercepted=false;
        }
        if(action==MotionEvent.ACTION_MOVE&&!swipeIntercepted){
            float dx=event.getX()-swipeStartX,dy=event.getY()-swipeStartY;
            if(Math.abs(dx)>ui.dp(90)&&Math.abs(dx)>Math.abs(dy)*1.6f){
                swipeIntercepted=true;
                MotionEvent cancel=MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                cancelChild.accept(cancel);cancel.recycle();return true;
            }
        }
        if(swipeIntercepted){
            if(action==MotionEvent.ACTION_UP){
                float dx=event.getX()-swipeStartX;
                if(Math.abs(dx)>ui.dp(60))changeTab.accept(dx<0?1:-1);
                swipeIntercepted=false;return true;
            }
            if(action==MotionEvent.ACTION_CANCEL){swipeIntercepted=false;return true;}
            return true;
        }
        return false;
    }
}
