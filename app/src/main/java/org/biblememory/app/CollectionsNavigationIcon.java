package org.biblememory.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.provider.Settings;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Reference artwork split into an immovable cabinet and three genuine book layers. */
final class CollectionsNavigationIcon extends View {
    static final String TAG="sv-collections-sorting";
    private static final class Sprite {
        final Bitmap bitmap;
        final Rect source;
        Sprite(Bitmap bitmap,Rect source){this.bitmap=bitmap;this.source=source;}
    }
    private static final class Art {
        final Bitmap cabinet;
        final Sprite[] books;
        final Sprite star;
        Art(Context context) {
            cabinet=decode(context,R.drawable.collections_cabinet_base);
            books=new Sprite[]{sprite(context,R.drawable.collections_book_green),
                    sprite(context,R.drawable.collections_book_cream),
                    sprite(context,R.drawable.collections_book_horizontal)};
            star=sprite(context,R.drawable.collections_star);
        }
    }
    private static Art cachedArt;
    private final Art art;
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint starLight=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF cabinetBounds=new RectF(-13.5f,-13.5f,13.5f,13.5f);
    private final RectF destination=new RectF();
    private final RectF starBounds=new RectF(-.52f,-9.25f,1.48f,-6.75f);
    private final Shader halo=new RadialGradient(.48f,-8,2.2f,
            new int[]{0xFFFFDA72,0x00FFDA72},null,Shader.TileMode.CLAMP);
    private final int[] order={0,1,2};
    final CollectionsBookMotion motion=new CollectionsBookMotion();
    private ValueAnimator animator;

    CollectionsNavigationIcon(Context context,int ink,boolean selected,int cycle) {
        super(context);
        if(cachedArt==null)cachedArt=new Art(context.getApplicationContext());
        art=cachedArt;motion.bind(selected,cycle);
        starLight.setColorFilter(new PorterDuffColorFilter(0xFFFFF1AE,PorterDuff.Mode.SRC_ATOP));
        setTag(TAG);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setSelected(selected);
    }
    private static Bitmap decode(Context context,int resource) {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;options.inSampleSize=4;
        return BitmapFactory.decodeResource(context.getResources(),resource,options);
    }
    private static Sprite sprite(Context context,int resource) {
        Bitmap bitmap=decode(context,resource);
        // Find visible alpha bounds ONCE, including faint edge antialiasing.
        int left=bitmap.getWidth(),top=bitmap.getHeight(),right=0,bottom=0;
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++) {
            if((bitmap.getPixel(x,y)>>>24)>8){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);}
        }
        return new Sprite(bitmap,new Rect(left,top,right,bottom));
    }
    int settledCycle(){return motion.settledCycle();}
    private boolean motionEnabled(){
        return ValueAnimator.areAnimatorsEnabled()&&Settings.Global.getFloat(getContext().getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;
    }
    void transition(boolean selected,boolean previouslySelected) {
        setSelected(selected);
        if(!selected){cancelMotion();motion.reset();invalidate();return;}
        if(!previouslySelected){cancelMotion();motion.reset();}
        // Restart the remaining path from the visible pose, never rewind or enqueue.
        float start=motion.isRunning()?motion.elapsed():0;
        cancelMotion();
        if(!motion.isRunning())motion.start();
        if(!motionEnabled()){motion.finish();invalidate();return;}
        ValueAnimator next=ValueAnimator.ofFloat(start,CollectionsBookMotion.DURATION);
        animator=next;next.setDuration(Math.max(1,CollectionsBookMotion.DURATION-(long)start));
        next.setInterpolator(new LinearInterpolator());
        next.addUpdateListener(a->{motion.sample((Float)a.getAnimatedValue());invalidate();});
        next.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator animation){
                if(animator==next){cancelMotion();motion.finish();invalidate();}
            }
        });next.start();
    }
    private void cancelMotion(){
        ValueAnimator previous=animator;animator=null;
        if(previous!=null){previous.removeAllUpdateListeners();previous.removeAllListeners();previous.cancel();}
    }
    void settle(){cancelMotion();if(isSelected())motion.finish();else motion.reset();invalidate();}
    @Override protected void onDetachedFromWindow(){settle();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);
        float unit=Math.min(getWidth(),getHeight())/27f;canvas.scale(unit,unit);
        canvas.clipRect(cabinetBounds);pen.setAlpha(255);pen.setShader(null);
        canvas.drawBitmap(art.cabinet,null,cabinetBounds,pen);
        float pulse=motion.starPulse();
        if(pulse>0){pen.setShader(halo);pen.setAlpha((int)(180*pulse));canvas.drawCircle(.48f,-8,2.2f,pen);pen.setShader(null);}
        pen.setAlpha(255);canvas.save();canvas.scale(1+.18f*pulse,1+.18f*pulse,.48f,-8);
        canvas.drawBitmap(art.star.bitmap,art.star.source,starBounds,pen);
        if(pulse>0){starLight.setAlpha((int)(75*pulse));canvas.drawBitmap(art.star.bitmap,art.star.source,starBounds,starLight);}
        canvas.restore();
        for(int i=0;i<3;i++)order[i]=i;
        for(int i=1;i<3;i++)for(int j=i;j>0&&motion.poses[order[j-1]].depth>motion.poses[order[j]].depth;j--){int k=order[j];order[j]=order[j-1];order[j-1]=k;}
        for(int i=0;i<3;i++){
            int book=order[i];CollectionsBookMotion.Pose p=motion.poses[book];
            canvas.save();canvas.translate(p.x,p.y);canvas.rotate(p.angle);canvas.scale(p.scale,p.scale);
            float w=CollectionsBookMotion.width(book)/2,h=CollectionsBookMotion.height(book)/2;
            destination.set(-w,-h,w,h);Sprite sprite=art.books[book];
            canvas.drawBitmap(sprite.bitmap,sprite.source,destination,pen);canvas.restore();
        }
        canvas.restore();
    }
}
