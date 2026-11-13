package org.biblememory.app;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.animation.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;

public class HomeIconCheck extends Instrumentation {
    MainActivity activity;
    File out;
    ValueAnimator motion;
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    Object field(Object obj,String name)throws Exception {
        Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);
    }
    View root()throws Exception {return (View)field(activity,"root");}
    HomeNavigationIcon home()throws Exception {return root().findViewWithTag(HomeNavigationIcon.TAG);}
    void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    void main(Runnable action){
        final Throwable[] failure=new Throwable[1];
        runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new AssertionError(failure[0]);
        waitForIdleSync();
    }
    void capture(String name)throws Exception {
        Thread.sleep(180);
        Bitmap bitmap=getUiAutomation().takeScreenshot();
        check(bitmap!=null,"Screenshot unavailable");
        try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,f);}
        bitmap.recycle();
        final Bitmap detail=Bitmap.createBitmap(270,270,Bitmap.Config.ARGB_8888);
        main(()->{try {
            Canvas canvas=new Canvas(detail);canvas.drawColor(0xFFF7DED0);
            canvas.scale(270f/home().getWidth(),270f/home().getHeight());home().draw(canvas);
        }catch(Exception e){throw new RuntimeException(e);}});
        try(FileOutputStream f=new FileOutputStream(new File(out,name+"-detail.png"))){detail.compress(Bitmap.CompressFormat.PNG,100,f);}
        detail.recycle();
    }
    View tab(View view,String description) {
        if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){View found=tab(group.getChildAt(i),description);if(found!=null)return found;}}
        return null;
    }
    void click(int index)throws Exception {
        main(()->{try {
            View button=tab(root(),"Открыть раздел "+new String[]{"Главная","Библия","Коллекции"}[index]);
            check(button!=null,"Tab missing");button.performClick();
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    void clickHomeAndFreeze(long time)throws Exception {
        main(()->{try {
            ((View)home().getParent()).performClick();
            motion=(ValueAnimator)field(home(),"animator");
            check(motion!=null,"Home animator was cancelled after tab switch");
            motion.pause();motion.setCurrentPlayTime(time);
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            out=new File(getTargetContext().getExternalFilesDir(null),"home-icon-check");out.mkdirs();
            Intent intent=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=(MainActivity)startActivitySync(intent);
            Thread.sleep(3000);waitForIdleSync();
            click(1);Thread.sleep(500);waitForIdleSync();capture("01-inactive");
            clickHomeAndFreeze(450);capture("02-opening-smoke");
            main(()->{motion.end();});capture("03-lit-resting");
            clickHomeAndFreeze(250);capture("04-repeat-smoke");
            main(()->{try {
                Object before=field(home(),"animator");
                for(int i=0;i<20;i++)((View)home().getParent()).performClick();
                check(before==field(home(),"animator"),"Repeated clicks queued motion");
            }catch(Exception e){throw new RuntimeException(e);}});
            click(1);Thread.sleep(400);capture("05-after-leaving");
            main(()->{try {
                check(!home().isSelected(),"Wrong selection after leaving");
                check(field(home(),"animator")==null,"Closing did not finish");
            }catch(Exception e){throw new RuntimeException(e);}});
            click(2);Thread.sleep(400);clickHomeAndFreeze(600);capture("06-from-collections");
            main(()->{
                callActivityOnPause(activity);callActivityOnResume(activity);
                try {
                    check(field(home(),"animator")==null,"Resume replayed smoke");
                    check(home().isSelected(),"Resume lost selected home");
                }catch(Exception e){throw new RuntimeException(e);}
            });
            capture("07-resumed-lit");
            result.putString("stream","PASS: Bible/Collections tab gestures, animator survives attachment, smoke frames, lit rest, repeat deduplication, exit cleanup and pause/resume\nImages: "+out);
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
