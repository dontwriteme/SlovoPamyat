package org.biblememory.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;

/** Real gestures, deterministic native frames, lifecycle and data-preservation checks. */
public class CollectionsIconCheck extends HomeIconCheck {
    private boolean disabled;
    @Override public void onCreate(Bundle args){disabled=args!=null&&"true".equals(args.getString("disabled"));super.onCreate(args);}
    CollectionsNavigationIcon icon()throws Exception{return root().findViewWithTag(CollectionsNavigationIcon.TAG);}
    void freeze(long ms,boolean click)throws Exception {
        main(()->{try{
            if(click)((View)icon().getParent()).performClick();
            motion=(ValueAnimator)field(icon(),"animator");check(motion!=null,"Missing Collections animator");
            motion.pause();motion.setCurrentPlayTime(ms);
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Override void capture(String name)throws Exception {
        // The icon clock is paused; let the independent existing page transition finish.
        Thread.sleep(300);
        Bitmap screen=getUiAutomation().takeScreenshot();check(screen!=null,"No screenshot");
        try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){screen.compress(Bitmap.CompressFormat.PNG,100,f);}screen.recycle();
        Bitmap detail=Bitmap.createBitmap(270,270,Bitmap.Config.ARGB_8888);
        main(()->{try{
            Canvas canvas=new Canvas(detail);canvas.drawColor(0xFFF7DED0);
            canvas.scale(270f/icon().getWidth(),270f/icon().getHeight());icon().draw(canvas);
        }catch(Exception e){throw new RuntimeException(e);}});
        try(FileOutputStream f=new FileOutputStream(new File(out,name+"-detail.png"))){detail.compress(Bitmap.CompressFormat.PNG,100,f);}detail.recycle();
        Bundle progress=new Bundle();progress.putString("stream","Captured "+name+"\n");sendStatus(0,progress);
    }
    String data()throws Exception{return ((LocalStore)field(activity,"store")).exportJson().toString();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            out=new File(getTargetContext().getExternalFilesDir(null),"collections-icon-check");out.mkdirs();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(3000);waitForIdleSync();String before=data();
            check(icon().motion.cycle()==0,"Inactive icon not in reference order");capture("00-reference");
            if(disabled){
                click(2);check(icon().isSelected(),"Collections navigation blocked");
                check(icon().motion.cycle()==1,"Disabled animation did not settle");
                check(field(icon(),"animator")==null,"Disabled motion allocated animator");
                click(2);check(icon().motion.cycle()==2,"Disabled repeat lost permutation");
                click(0);check(icon().motion.cycle()==0,"Disabled exit did not reset");
                capture("disabled-reference");
            }else{
                freeze(125,true);check("COLLECTIONS".equals(field(activity,"screen").toString()),"Navigation waited for animation");capture("01-extraction");
                freeze(325,false);check(icon().motion.poses[0].scale>1&&icon().motion.poses[1].scale>1&&icon().motion.poses[2].scale>1,"Not all three books extracted");capture("02-hover");
                freeze(650,false);check(icon().motion.poses[0].angle<0&&icon().motion.poses[2].angle>0,"No cross-shelf rotation");capture("03-flight");
                freeze(1000,false);capture("04-landing");
                freeze(1375,false);check(icon().motion.starPulse()>.9f,"Star not flashing");capture("05-star");
                main(()->motion.end());check(icon().motion.cycle()==1,"Wrong final order");capture("06-sorted");
                freeze(650,true);
                main(()->{try{
                    float x=icon().motion.poses[0].x;
                    for(int n=0;n<20;n++){
                        ValueAnimator old=(ValueAnimator)field(icon(),"animator");
                        ((View)icon().getParent()).performClick();
                        check(!old.isRunning(),"Old animator still running");
                        check(Math.abs(icon().motion.poses[0].x-x)<.001,"Rapid repeat rewound pose");
                        motion=(ValueAnimator)field(icon(),"animator");motion.pause();
                    }
                    motion.end();
                }catch(Exception e){throw new RuntimeException(e);}});
                check(icon().motion.cycle()==2,"Repeat queued additional sorts");capture("07-repeat-sorted");
                main(()->{try{
                    Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
                    check(icon().motion.cycle()==2,"Page rebuild reset the active order");
                }catch(Exception e){throw new RuntimeException(e);}});
                main(()->{try{
                    // Exercise the existing section-root handler from a detail state,
                    // without creating or mutating a real collection fixture.
                    java.lang.reflect.Field screen=MainActivity.class.getDeclaredField("screen");screen.setAccessible(true);
                    for(Object value:screen.getType().getEnumConstants())if("COLLECTION_DETAIL".equals(value.toString()))screen.set(activity,value);
                    View previous=root();((View)icon().getParent()).performClick();
                    check("COLLECTIONS".equals(field(activity,"screen").toString())&&root()!=previous,"Reselect did not preserve detail-to-root navigation");
                    ((ValueAnimator)field(icon(),"animator")).end();
                }catch(Exception e){throw new RuntimeException(e);}});
                freeze(650,true);CollectionsNavigationIcon leaving=icon();
                click(0);Thread.sleep(350);check(leaving.motion.cycle()==0,"Cached outgoing icon not reset");
                check(!leaving.isSelected()&&field(leaving,"animator")==null,"Outgoing sort survived exit");
                check(icon().motion.cycle()==0,"Inactive reference order lost");capture("08-exit-reference");
                freeze(650,true);
                main(()->{callActivityOnPause(activity);callActivityOnResume(activity);});
                check(icon().isSelected()&&field(icon(),"animator")==null,"Resume replayed motion or lost selection");
                check(icon().motion.cycle()==1,"Pause/resume did not settle");capture("09-resumed");
                click(1);Thread.sleep(350);click(0);Thread.sleep(350);
                check(root().findViewWithTag(HomeNavigationIcon.TAG)!=null,"Home icon lost");
                check(root().findViewWithTag(BookNavigationIcon.TAG)!=null,"Bible icon lost");
            }
            check(before.equals(data()),"Collection data changed during decorative sorting");
            result.putString("stream","PASS: Collections reference, three independent books, cross-shelf turns, star, repeat/restart, cached exit reset, active rebuild, lifecycle, disabled="+disabled+", unchanged collection export\nImages: "+out);
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
