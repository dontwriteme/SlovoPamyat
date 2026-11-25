package org.biblememory.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import static org.biblememory.app.ScreenUi.GOLD;

final class LessonCompletionView {
    record Page(LinearLayout root,FrameLayout scenery,Runnable animate) {}
    private final Context context;
    private final ScreenUi ui;

    LessonCompletionView(Context context,ScreenUi ui){this.context=context;this.ui=ui;}

    /** Celebratory page: the approved crown/check composition in the same warm scenic UI. */
    Page create(int landscape,String caption,boolean groupMode,Runnable next){
        LinearLayout root=ui.column();
        FrameLayout scenery=new FrameLayout(context);
        ImageView image=new ImageView(context);
        image.setImageResource(landscape);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        scenery.addView(image,new FrameLayout.LayoutParams(-1,-1));
        View haze=new View(context);
        haze.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFFFFF2E9,0xFEFFF0E6,0xDFFFF0E5,0x7DFFF0E4,0x77FFF0E4}));
        scenery.addView(haze,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout central=ui.column();central.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);
        central.setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(12));
        root.addView(central,new LinearLayout.LayoutParams(-1,0,1));
        TextView crown=ui.txt("👑",34,GOLD,true);crown.setGravity(Gravity.CENTER);
        central.addView(crown,new LinearLayout.LayoutParams(ui.dp(75),ui.dp(65)));
        TextView reference=ui.displayText(caption,21);
        reference.setGravity(Gravity.CENTER);reference.setPadding(ui.dp(14),ui.dp(6),ui.dp(14),ui.dp(6));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,-2);
        rp.topMargin=ui.dp(12);central.addView(reference,rp);
        TextView heading=ui.displayText(groupMode?"Связка освоена!":"Стих освоен!",38);
        heading.setGravity(Gravity.CENTER);heading.setTextColor(0xFF35452E);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.topMargin=ui.dp(14);central.addView(heading,hp);
        SuccessArt art=new SuccessArt();
        int availableArt=Math.min(270,Math.max(140,
            Math.round(context.getResources().getDisplayMetrics().heightPixels /
                context.getResources().getDisplayMetrics().density)-410));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,ui.dp(availableArt));
        ap.topMargin=ui.dp(10);central.addView(art,ap);
        LinearLayout footer=ui.column();footer.setPadding(ui.dp(24),ui.dp(14),ui.dp(24),ui.dp(25));
        TextView continueButton=ui.button("Продолжить",true,next);
        ui.animateLessonPress(continueButton);
        continueButton.setTextSize(20);
        footer.addView(continueButton,new LinearLayout.LayoutParams(-1,ui.dp(61)));
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        scenery.addView(root,new FrameLayout.LayoutParams(-1,-1));
        return new Page(root,scenery,()->animate(reference,heading,art));
    }

    private void animate(TextView reference,TextView heading,SuccessArt art){
        reference.setAlpha(0f);heading.setAlpha(0f);
        if(ValueAnimator.areAnimatorsEnabled()){
            reference.animate().alpha(1f).setDuration(170).start();
            heading.animate().alpha(1f).setStartDelay(140).setDuration(180).start();
            art.start();
        }else{reference.setAlpha(1f);heading.setAlpha(1f);art.setFinished();}
    }
    private final class SuccessArt extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float reveal=0f;
        SuccessArt(){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void start(){ValueAnimator animator=ValueAnimator.ofFloat(0f,1f);
            animator.setStartDelay(270);animator.setDuration(670);
            animator.addUpdateListener(a->{reveal=(float)a.getAnimatedValue();invalidate();});animator.start();}
        void setFinished(){reveal=1f;invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float scale=Math.min(getWidth()/350f,getHeight()/300f);
            float cx=getWidth()/2f,cy=getHeight()/2f;
            c.save();c.translate(cx,cy);c.scale(scale,scale);
            paint.setShader(new RadialGradient(0,0,145,new int[]{0xAA6BB37B,0x5567A86E,0x00FFF0E5},null,Shader.TileMode.CLAMP));
            c.drawCircle(0,0,145,paint);paint.setShader(null);
            paint.setColor(0x444ED383);c.drawCircle(0,0,105,paint);
            paint.setShader(new LinearGradient(-84,-90,80,94,0xFFB9D47A,0xFF4E9464,Shader.TileMode.CLAMP));
            c.drawCircle(0,0,91,paint);paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.3f);
            paint.setColor(0xFFB1FAC7);c.drawCircle(0,0,90,paint);
            paint.setStrokeWidth(13f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Color.WHITE);
            Path tick=new Path();tick.moveTo(-36,0);tick.lineTo(-10,25);tick.lineTo(44,-33);
            Path partial=new Path();PathMeasure pm=new PathMeasure(tick,false);
            pm.getSegment(0,pm.getLength()*Math.max(0,reveal),partial,true);c.drawPath(partial,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFD565);
            sparkle(c,-113,-85,13);sparkle(c,116,78,12);sparkle(c,-139,-36,7);
            paint.setColor(0xFF7CF3AB);
            for(int i=0;i<3;i++){
                c.save();c.rotate(-22+i*20,-130,60);c.drawRoundRect(-133,45+i*15,-128,60+i*15,2,2,paint);c.restore();
                c.save();c.rotate(-15+i*20,120,-70);c.drawRoundRect(118,-104+i*17,123,-86+i*17,2,2,paint);c.restore();
            }
            c.restore();
        }
        private void sparkle(Canvas c,float x,float y,float r){
            Path p=new Path();p.moveTo(x,y-r);p.quadTo(x+r*.18f,y-r*.2f,x+r,y);
            p.quadTo(x+r*.2f,y+r*.18f,x,y+r);p.quadTo(x-r*.18f,y+r*.18f,x-r,y);
            p.quadTo(x-r*.2f,y-r*.2f,x,y-r);c.drawPath(p,paint);
        }
    }
}
