package org.biblememory.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** The supplied open Bible, with independently animated ribbon, sheets and cross. */
final class BookNavigationIcon extends View {
    static final String TAG = "sv-bible-book";
    private static Bitmap book;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bookBounds = new RectF(-13.5f,-13.5f,13.5f,13.5f);
    private final Path sheet = new Path();
    private final Path ribbon = new Path();
    private final Path cross = new Path();
    private final Shader paper = new LinearGradient(-10,0,10,0,
            new int[]{0xFFF4D69E,0xFFFFF5E6,0xFFF5DDB4},null,Shader.TileMode.CLAMP);
    private final Shader ribbonShade = new LinearGradient(4,0,8,6,
            0xFFA4B563,0xFF627D31,Shader.TileMode.CLAMP);
    private final Shader crossShade = new LinearGradient(0,-10,0,-4,
            0xFFFFF6C7,0xFFFFDD82,Shader.TileMode.CLAMP);
    private final Shader halo = new RadialGradient(0,-7.1f,5.9f,
            new int[]{0xD0FFD675,0x70F5BD50,0x00FFD675},
            new float[]{0f,.45f,1f},Shader.TileMode.CLAMP);
    private ValueAnimator animator;
    private float bookmark, crossRise, glow, pageTime;
    private boolean repeating;

    BookNavigationIcon(Context context,int ink,boolean selected) {
        super(context);
        if(book==null){
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inScaled=false;options.inSampleSize=4;
            book=BitmapFactory.decodeResource(getResources(),R.drawable.bible_open_base,options);
        }
        // The right-page ribbon follows the folded top and notched tail of the reference.
        ribbon.moveTo(3.1f,-4.1f);
        ribbon.cubicTo(3.4f,-4.8f,4.2f,-4.8f,5.2f,-4.6f);
        ribbon.cubicTo(6.4f,-4.6f,6.8f,-3.6f,6.8f,-2.7f);
        ribbon.lineTo(6.8f,3.2f);ribbon.quadTo(6.8f,3.6f,6.4f,3.3f);
        ribbon.lineTo(5.6f,2.6f);ribbon.quadTo(5.4f,2.4f,5.2f,2.6f);
        ribbon.lineTo(4.4f,3.3f);ribbon.quadTo(4.1f,3.6f,4.1f,3.1f);
        ribbon.lineTo(4.1f,-2.4f);ribbon.quadTo(4.1f,-4.5f,3.1f,-4.1f);ribbon.close();
        Path arm=new Path();
        cross.addRoundRect(-.65f,-10,.65f,-4,.65f,.65f,Path.Direction.CW);
        arm.addRoundRect(-2.6f,-8.35f,2.6f,-7.05f,.65f,.65f,Path.Direction.CW);
        cross.op(arm,Path.Op.UNION);
        setTag(TAG);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSelected(selected);settle();
    }

    /** Binding/restoration is static; only a navigation gesture starts motion. */
    float[] snapshot(){return new float[]{bookmark,crossRise,glow};}
    void transition(boolean selected,boolean previouslySelected){transition(selected,previouslySelected,null);}
    void transition(boolean selected,boolean previouslySelected,float[] state) {
        if(selected&&isSelected()&&animator!=null&&ValueAnimator.areAnimatorsEnabled())return;
        cancelMotion();setSelected(selected);
        repeating=selected&&previouslySelected;
        bookmark=state==null?(previouslySelected?0f:1f):state[0];
        crossRise=state==null?(previouslySelected?1f:0f):state[1];
        glow=state==null?(previouslySelected?1f:0f):state[2];
        final float startBookmark=bookmark,startCross=crossRise,startGlow=glow;
        pageTime=-1f;
        if(!ValueAnimator.areAnimatorsEnabled()){settle();return;}
        final long duration=selected?(repeating?380L:1100L):190L;
        ValueAnimator motion=ValueAnimator.ofFloat(0f,duration);
        animator=motion;motion.setDuration(duration);
        motion.setInterpolator(new LinearInterpolator());
        motion.addUpdateListener(a->{
            float ms=(Float)a.getAnimatedValue();
            if(!selected){
                bookmark=startBookmark+(1f-startBookmark)*smooth(ms/190f);
                crossRise=startCross*(1f-smooth(ms/190f));glow=startGlow*(1f-smooth(ms/150f));
                pageTime=-1f;
            }else if(repeating){
                bookmark=0f;crossRise=1f;pageTime=ms;
                glow=1f+.14f*(float)Math.sin(Math.PI*Math.min(1f,ms/380f));
            }else{
                bookmark=startBookmark*(1f-smooth(ms/210f));pageTime=ms-140f;
                crossRise=startCross+(1f-startCross)*smooth((ms-580f)/300f);
                glow=(startGlow+(1f-startGlow)*smooth((ms-800f)/220f))
                        *(1f+.12f*(float)Math.sin(Math.PI*smooth((ms-820f)/280f)));
            }
            invalidate();
        });
        motion.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator animation){
                if(animator==motion){animator=null;settle();}
            }
        });
        motion.start();
    }

    void settle() {
        cancelMotion();bookmark=isSelected()?0f:1f;
        crossRise=glow=isSelected()?1f:0f;
        pageTime=-1f;repeating=false;invalidate();
    }
    private void cancelMotion() {
        ValueAnimator previous=animator;animator=null;
        if(previous!=null){previous.removeAllUpdateListeners();previous.removeAllListeners();previous.cancel();}
    }
    private static float smooth(float value) {
        float t=Math.max(0f,Math.min(1f,value));return t*t*(3f-2f*t);
    }
    @Override protected void onDetachedFromWindow(){settle();super.onDetachedFromWindow();}

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);canvas.save();canvas.clipRect(0,0,getWidth(),getHeight());
        canvas.translate(getWidth()/2f,getHeight()/2f);
        float unit=Math.min(getWidth(),getHeight())/27f;canvas.scale(unit,unit);
        pen.setStyle(Paint.Style.FILL);pen.setShader(null);pen.setAlpha(255);
        // Cross and halo go BEHIND the opaque book. At zero rise neither is drawn.
        if(crossRise>0f){
            canvas.save();canvas.translate(0,8.5f*(1f-crossRise));
            float size=.82f+.18f*crossRise;
            canvas.scale(size,size,0,-7);
            if(glow>0f){
                pen.setShader(halo);pen.setAlpha((int)Math.min(220f,180f*glow));
                canvas.drawCircle(0,-7.1f,5.9f,pen);pen.setShader(null);
            }
            pen.setShader(crossShade);pen.setAlpha((int)(255*smooth(crossRise*3)));
            canvas.drawPath(cross,pen);pen.setShader(null);
            canvas.restore();
        }
        pen.setAlpha(255);canvas.drawBitmap(book,null,bookBounds,pen);
        if(bookmark>0f){
            canvas.save();canvas.translate(0,-3f*(1f-bookmark));
            pen.setShader(ribbonShade);pen.setAlpha((int)(255*bookmark));
            canvas.drawPath(ribbon,pen);pen.setShader(null);canvas.restore();
        }
        // Each independently bent sheet crosses the spine, not the entire bitmap.
        if(pageTime>=0f){
            int count=repeating?1:3;
            for(int i=0;i<count;i++){
                float t=(pageTime-i*120f)/250f;
                if(t<=0f||t>=1f)continue;
                drawSheet(canvas,t);
            }
        }
        pen.setShader(null);pen.setAlpha(255);canvas.restore();
    }

    private void drawSheet(Canvas canvas,float t) {
        float angle=(float)(Math.PI*t),spine=-.17f;
        float edge=spine+10.2f*(float)Math.cos(angle);
        float bend=2.5f*(float)Math.sin(angle);
        sheet.reset();sheet.moveTo(spine,-1.65f);
        sheet.cubicTo(spine+(edge-spine)*.3f,-3.7f-bend,
                edge,-4.6f-bend*.2f,edge,-4.2f);
        sheet.quadTo(edge-bend*.35f,1.5f,edge,8.2f);
        sheet.cubicTo((spine+edge)*.6f,6.7f-bend*.25f,spine+1,8.2f,spine,9.1f);
        sheet.close();
        pen.setStyle(Paint.Style.FILL);pen.setShader(paper);pen.setAlpha(255);
        canvas.drawPath(sheet,pen);pen.setShader(null);
        pen.setStyle(Paint.Style.STROKE);pen.setStrokeJoin(Paint.Join.ROUND);
        pen.setStrokeWidth(.3f);pen.setColor(0xFFC7A66E);canvas.drawPath(sheet,pen);
        pen.setStyle(Paint.Style.FILL);
    }
}
