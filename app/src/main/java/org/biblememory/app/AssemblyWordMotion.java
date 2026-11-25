package org.biblememory.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/** Ephemeral assembly visuals only; accepted words and session state belong to the lesson. */
final class AssemblyWordMotion {
    static final long FLIGHT_MS=420, LAND_MS=210;
    private final Map<View,Flight> flights=new IdentityHashMap<>();
    private final Map<View,Landing> landings=new IdentityHashMap<>();
    private final Map<View,ValueAnimator> shakes=new IdentityHashMap<>();

    boolean isFlying(View slot){return flights.containsKey(slot);}
    int flightCount(){return flights.size();}

    static boolean canFly(ViewGroup host,View source,View target){
        return ValueAnimator.areAnimatorsEnabled() && host!=null &&
            host.isAttachedToWindow() && source.isAttachedToWindow() &&
            target.isAttachedToWindow() && visible(source) && visible(target);
    }

    private static boolean visible(View view){
        return view.isShown() && view.getWidth()>0 && view.getHeight()>0 &&
            view.getGlobalVisibleRect(new Rect());
    }

    private static float dp(View view,float value){
        return value*view.getResources().getDisplayMetrics().density;
    }

    /** Coordinates are recomputed relative to the overlay if the lesson scrolls. */
    private static void center(ViewGroup host,View view,int[] origin,int[] location,float[] out){
        host.getLocationInWindow(origin);
        view.getLocationInWindow(location);
        out[0]=location[0]-origin[0]+view.getWidth()/2f;
        out[1]=location[1]-origin[1]+view.getHeight()/2f;
    }

    void fly(ViewGroup host,TextView source,View slot,TextView ghost,Runnable reveal){
        if(!canFly(host,source,slot)){reveal.run();return;}
        Flight flight=new Flight(host,source,slot,ghost,reveal);
        flights.put(slot,flight);
        flight.start();
    }

    private final class Flight {
        final ViewGroup host;
        final View slot;
        final TextView ghost;
        final Runnable reveal;
        final int[] origin=new int[2],location=new int[2];
        final float[] start=new float[2],target=new float[2];
        final int width,height;
        final ValueAnimator animator=ValueAnimator.ofFloat(0f,1f);
        boolean ended;
        final View.OnAttachStateChangeListener detach=new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View view){}
            @Override public void onViewDetachedFromWindow(View view){finish(false);}
        };

        Flight(ViewGroup host,View source,View slot,TextView ghost,Runnable reveal){
            this.host=host;this.slot=slot;this.ghost=ghost;this.reveal=reveal;
            width=source.getWidth();height=source.getHeight();
            center(host,source,origin,location,start);
        }

        void start(){
            ghost.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ghost.setClickable(false);ghost.setFocusable(false);
            ghost.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            ghost.layout(0,0,width,height);
            ghost.setPivotX(width/2f);ghost.setPivotY(height/2f);
            ghost.setTranslationX(start[0]-width/2f);
            ghost.setTranslationY(start[1]-height/2f);
            ghost.setElevation(dp(host,8));
            host.getOverlay().add(ghost);
            host.addOnAttachStateChangeListener(detach);
            animator.setDuration(FLIGHT_MS);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a->{
                if(!host.isAttachedToWindow()||!visible(slot)){finish(false);return;}
                frame((float)a.getAnimatedValue());
            });
            animator.addListener(new AnimatorListenerAdapter(){
                @Override public void onAnimationEnd(Animator animation){finish(true);}
            });
            animator.start();
        }

        void frame(float t){
            center(host,slot,origin,location,target);
            float x,y,scale;
            float lift=dp(host,12);
            if(t<=.18f){
                float u=ease(t/.18f);
                x=start[0];y=start[1]-lift*u;scale=1f+.16f*u;
            }else{
                float u=ease((t-.18f)/.82f),v=1f-u;
                float sx=start[0],sy=start[1]-lift;
                float dx=target[0]-sx;
                float bend=dp(host,22)*(dx<0?-1f:1f);
                float cx=(sx+target[0])*.5f+bend;
                float cy=(sy+target[1])*.5f-dp(host,32);
                x=v*v*sx+2*v*u*cx+u*u*target[0];
                y=v*v*sy+2*v*u*cy+u*u*target[1];
                float endScale=Math.min(1f,Math.min(slot.getWidth()/(float)width,
                    slot.getHeight()/(float)height));
                scale=1.16f+(endScale-1.16f)*u;
            }
            ghost.setTranslationX(x-width/2f);ghost.setTranslationY(y-height/2f);
            ghost.setScaleX(scale);ghost.setScaleY(scale);
            ghost.setTranslationZ(dp(host,6)*(1f-t));
        }

        void finish(boolean animateLanding){
            if(ended)return;
            ended=true;
            flights.remove(slot);
            host.removeOnAttachStateChangeListener(detach);
            animator.cancel();
            host.getOverlay().remove(ghost);
            reveal.run();
            if(animateLanding&&host.isAttachedToWindow()&&slot.isAttachedToWindow()&&
                ValueAnimator.areAnimatorsEnabled()){
                Landing landing=new Landing(host,slot);
                landings.put(slot,landing);
                landing.start();
            }
        }
    }

    private static float ease(float t){
        float v=1f-t;return 1f-v*v;
    }

    private final class Landing {
        final ViewGroup host;
        final View slot;
        final GradientDrawable ring=new GradientDrawable();
        final ValueAnimator animator=ValueAnimator.ofFloat(0f,1f);
        final int[] origin=new int[2],location=new int[2];
        final float[] center=new float[2];
        boolean ended;

        Landing(ViewGroup host,View slot){
            this.host=host;this.slot=slot;
            ring.setColor(Color.TRANSPARENT);
            ring.setCornerRadius(dp(host,15));
            ring.setStroke(Math.max(1,Math.round(dp(host,2))),0xFF89A960);
        }

        void start(){
            host.getOverlay().add(ring);
            animator.setDuration(LAND_MS);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a->{
                if(!host.isAttachedToWindow()||!slot.isAttachedToWindow()){finish();return;}
                float t=(float)a.getAnimatedValue();
                float scale=t<.38f?.96f+.12f*ease(t/.38f):
                    1.08f-.08f*ease((t-.38f)/.62f);
                // The ring follows the unscaled layout center.
                slot.setScaleX(1f);slot.setScaleY(1f);
                center(host,slot,origin,location,center);
                float spread=dp(host,10)*t;
                int left=Math.round(center[0]-slot.getWidth()/2f-spread);
                int top=Math.round(center[1]-slot.getHeight()/2f-spread);
                ring.setBounds(left,top,Math.round(center[0]+slot.getWidth()/2f+spread),
                    Math.round(center[1]+slot.getHeight()/2f+spread));
                ring.setAlpha(Math.round(150*(1f-t)));
                slot.setScaleX(scale);slot.setScaleY(scale);
                host.invalidate();
            });
            animator.addListener(new AnimatorListenerAdapter(){
                @Override public void onAnimationEnd(Animator animation){finish();}
            });
            animator.start();
        }

        void finish(){
            if(ended)return;
            ended=true;landings.remove(slot);animator.cancel();
            slot.setScaleX(1f);slot.setScaleY(1f);
            host.getOverlay().remove(ring);
        }
    }

    void stopShake(View tile){
        ValueAnimator old=shakes.remove(tile);
        if(old!=null)old.cancel();
        tile.setTranslationX(0f);
    }

    void shake(View tile){
        stopShake(tile);
        if(!ValueAnimator.areAnimatorsEnabled()||!tile.isAttachedToWindow())return;
        float d=dp(tile,6);
        ValueAnimator a=ValueAnimator.ofFloat(0f,-d,d,-d*.5f,0f);
        a.setDuration(240);
        shakes.put(tile,a);
        a.addUpdateListener(v->tile.setTranslationX((float)v.getAnimatedValue()));
        a.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator animation){
                if(shakes.get(tile)==a){shakes.remove(tile);tile.setTranslationX(0f);}
            }
        });
        a.start();
    }

    /** Settle accepted words synchronously before leaving, rebuilding or pausing a lesson. */
    void settleAll(){
        for(Flight f:new ArrayList<>(flights.values()))f.finish(false);
        for(Landing l:new ArrayList<>(landings.values()))l.finish();
        for(View tile:new ArrayList<>(shakes.keySet()))stopShake(tile);
    }
}
