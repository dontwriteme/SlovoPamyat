package org.biblememory.app;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Real-device regression and deterministic native frames, without Robolectric. */
public class BibleIconCheck extends HomeIconCheck {
    private boolean disabled;
    @Override public void onCreate(Bundle args){disabled=args!=null&&"true".equals(args.getString("disabled"));super.onCreate(args);}
    BookNavigationIcon bible()throws Exception{return root().findViewWithTag(BookNavigationIcon.TAG);}
    void freezeAt(long ms,boolean click)throws Exception {
        main(()->{try {
            if(click)((View)bible().getParent()).performClick();
            motion=(ValueAnimator)field(bible(),"animator");check(motion!=null,"Missing book animator");
            motion.pause();motion.setCurrentPlayTime(ms);
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    void state(float bookmark,float cross,float glow)throws Exception {
        float[] value=bible().snapshot();
        check(Math.abs(value[0]-bookmark)<.001f,"Wrong bookmark");
        check(Math.abs(value[1]-cross)<.001f,"Wrong cross");
        check(Math.abs(value[2]-glow)<.001f,"Wrong glow");
    }
    @Override void capture(String name)throws Exception {
        Thread.sleep(180);
        Bitmap screen=getUiAutomation().takeScreenshot();check(screen!=null,"No screenshot");
        try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){screen.compress(Bitmap.CompressFormat.PNG,100,f);}screen.recycle();
        Bitmap detail=Bitmap.createBitmap(270,270,Bitmap.Config.ARGB_8888);
        main(()->{try {
            Canvas canvas=new Canvas(detail);canvas.drawColor(0xFFF7DED0);
            canvas.scale(270f/bible().getWidth(),270f/bible().getHeight());bible().draw(canvas);
        }catch(Exception e){throw new RuntimeException(e);}});
        try(FileOutputStream f=new FileOutputStream(new File(out,name+"-detail.png"))){detail.compress(Bitmap.CompressFormat.PNG,100,f);}detail.recycle();
        Bundle progress=new Bundle();progress.putString("stream","Captured "+name+"\n");sendStatus(0,progress);
    }
    TextView text(View view,String value){
        if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){TextView found=text(group.getChildAt(i),value);if(found!=null)return found;}}
        return null;
    }
    void readerNavigation()throws Exception {
        main(()->{try {
            ((Bible)field(activity,"bible")).load(activity);
            Class<?> screen=Class.forName("org.biblememory.app.MainActivity$Screen");
            Object chapters=null;for(Object value:screen.getEnumConstants())if("CHAPTERS".equals(value.toString()))chapters=value;
            Field mode=MainActivity.class.getDeclaredField("screen");mode.setAccessible(true);mode.set(activity,chapters);
            Field book=MainActivity.class.getDeclaredField("selectedBook");book.setAccessible(true);book.set(activity,19);
            Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
            text(root(),"118").performClick();check("VERSES".equals(field(activity,"screen").toString()),"Skipped verse grid");
            check(text(root(),"176")!=null,"Psalm 118 missing verse 176");text(root(),"176").performClick();
            check("CHAPTER".equals(field(activity,"screen").toString()),"Verse did not open text");
            ((View)bible().getParent()).performClick();
            check("BOOKS".equals(field(activity,"screen").toString()),"Reselect did not return catalog");
            View before=root();((View)bible().getParent()).performClick();check(before==root(),"Catalog recreated on reselect");
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            out=new File(getTargetContext().getExternalFilesDir(null),"bible-icon-check");out.mkdirs();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(3000);waitForIdleSync();state(1,0,0);capture("00-inactive-open-book");
            if(disabled){
                click(1);state(0,1,1);check(field(bible(),"animator")==null,"Disabled motion created animator");
                click(0);state(1,0,0);check(field(bible(),"animator")==null,"Disabled exit created animator");
                capture("disabled-restored");
            }else{
                freezeAt(100,true);check(bible().snapshot()[0]>0&&bible().snapshot()[0]<1,"Ribbon not withdrawing");
                check(bible().snapshot()[1]==0,"Cross appeared too soon");capture("01-bookmark-withdraws");
                freezeAt(320,false);state(0,0,0);capture("02-pages-turn");
                freezeAt(560,false);capture("03-third-page");
                freezeAt(700,false);check(bible().snapshot()[1]>0&&bible().snapshot()[1]<1,"Cross not rising");capture("04-cross-rises-behind-book");
                freezeAt(950,false);check(bible().snapshot()[2]>0,"No glow");capture("05-glow");
                main(()->motion.end());state(0,1,1);capture("06-active-resting");
                View before=root();freezeAt(130,true);check(before==root(),"Catalog replaced");
                check(bible().snapshot()[0]==0&&bible().snapshot()[1]==1,"Repeat reset cross/ribbon");capture("07-repeat-one-page");
                main(()->{try {Object same=field(bible(),"animator");for(int i=0;i<20;i++)((View)bible().getParent()).performClick();
                    check(same==field(bible(),"animator"),"Queued book animations");}catch(Exception e){throw new RuntimeException(e);}});
                readerNavigation();Thread.sleep(400);click(0);Thread.sleep(400);state(1,0,0);
                freezeAt(260,true);Thread.sleep(400);
                main(()->{try {((View)home().getParent()).performClick();
                    check(bible().snapshot()[1]==0,"Leaving early invented a cross");
                    ValueAnimator exit=(ValueAnimator)field(bible(),"animator");if(exit!=null)exit.end();
                }catch(Exception e){throw new RuntimeException(e);}});
                state(1,0,0);capture("08-leave-during-pages");Thread.sleep(400);
                freezeAt(950,true);
                main(()->{callActivityOnPause(activity);callActivityOnResume(activity);});
                state(0,1,1);check(field(bible(),"animator")==null,"Resume restarted animation");capture("09-resumed-active");
            }
            result.putString("stream","PASS: "+(disabled?"disabled animations: both endpoints":"bookmark/pages/cross/glow, repeat, rapid exit, catalog, 176-verse grid, lifecycle")+"\nImages: "+out);
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
