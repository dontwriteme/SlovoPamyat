package org.biblememory.app;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.provider.Settings;
import android.view.View;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.lang.reflect.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class HomeNavigationIconTest {
    private HomeNavigationIcon icon;
    private void scale(float value)throws Exception {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE,value);
        Method method=ValueAnimator.class.getDeclaredMethod("setDurationScale",float.class);
        method.setAccessible(true);method.invoke(null,value);
    }
    private Object field(String name)throws Exception {
        Field f=HomeNavigationIcon.class.getDeclaredField(name);f.setAccessible(true);return f.get(icon);
    }
    private void frame(long ms)throws Exception {
        ValueAnimator animator=(ValueAnimator)field("animator");assertNotNull(animator);
        animator.setCurrentPlayTime(ms);
        icon.draw(new Canvas(Bitmap.createBitmap(27,27,Bitmap.Config.ARGB_8888)));
    }
    @Before public void setup()throws Exception {
        scale(1f);
        icon=new HomeNavigationIcon(RuntimeEnvironment.getApplication(),0xFFB7553D,false);
        icon.layout(0,0,27,27);
    }
    @After public void cleanup()throws Exception {icon.settle();scale(1f);}
    @Test public void windowWarmsBeforeSmokeAndStaysLitAfterFiniteWelcome()throws Exception {
        assertEquals(0f,(Float)field("light"),0f);
        icon.transition(true,false);
        frame(100);assertTrue((Float)field("light")>0f);assertTrue((Float)field("light")<1f);
        frame(400);assertEquals(1f,(Float)field("light"),0f);
        assertTrue((Boolean)field("smoking"));
        ValueAnimator motion=(ValueAnimator)field("animator");assertEquals(1050L,motion.getDuration());
        motion.end();assertTrue(icon.isSelected());assertNull(field("animator"));
        assertEquals(1f,(Float)field("light"),0f);assertFalse((Boolean)field("smoking"));
    }
    @Test public void repeatKeepsLightAndDoesNotQueueAnimators()throws Exception {
        icon.setSelected(true);icon.settle();icon.transition(true,true);frame(150);
        assertTrue((Boolean)field("repeating"));assertTrue((Float)field("pulse")>0f);
        assertEquals(1f,(Float)field("light"),0f);
        ValueAnimator motion=(ValueAnimator)field("animator");assertEquals(650L,motion.getDuration());
        for(int i=0;i<30;i++)icon.transition(true,true);
        assertSame(motion,field("animator"));
        motion.end();assertFalse((Boolean)field("smoking"));assertNull(field("animator"));
    }
    @Test public void leavingDuringSmokeCancelsAndFadesWindow()throws Exception {
        icon.transition(true,false);frame(400);
        ValueAnimator old=(ValueAnimator)field("animator");icon.transition(false,true);
        assertFalse(old.isStarted());assertFalse((Boolean)field("smoking"));
        frame(90);assertEquals(.5f,(Float)field("light"),.01f);
        ((ValueAnimator)field("animator")).end();
        assertEquals(0f,(Float)field("light"),0f);assertNull(field("animator"));
    }
    @Test public void pauseAndDetachSettleWithoutRestartingSmoke()throws Exception {
        icon.transition(true,false);frame(300);icon.settle();
        assertNull(field("animator"));assertEquals(1f,(Float)field("light"),0f);
        icon.transition(true,true);
        Method detach=HomeNavigationIcon.class.getDeclaredMethod("onDetachedFromWindow");
        detach.setAccessible(true);detach.invoke(icon);
        assertNull(field("animator"));assertFalse((Boolean)field("smoking"));
        assertTrue(icon.isSelected());
    }
    @Test public void disabledAndReducedMotionImmediatelyUseFinalState()throws Exception {
        for(float setting:new float[]{0f,.5f}) {
            scale(setting);icon.transition(true,false);
            assertEquals(1f,(Float)field("light"),0f);assertNull(field("animator"));
            assertFalse((Boolean)field("smoking"));
            icon.transition(false,true);
            assertEquals(0f,(Float)field("light"),0f);assertNull(field("animator"));
        }
    }
    private Object activityField(MainActivity activity,String name)throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);
    }
    private void navigate(MainActivity activity,int index)throws Exception {
        Method m=MainActivity.class.getDeclaredMethod("goToMainTab",int.class);
        m.setAccessible(true);m.invoke(activity,index);
    }
    @Test public void cachedNavigationReselectAndLifecycleKeepCorrectState()throws Exception {
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create().start().resume();
        try {
            MainActivity activity=controller.get();
            ((Bible)activityField(activity,"bible")).load(activity);
            Class<?> screens=Class.forName("org.biblememory.app.MainActivity$Screen");
            Method prepare=MainActivity.class.getDeclaredMethod("prepareTab",screens);prepare.setAccessible(true);
            for(Object value:screens.getEnumConstants())
                if(value.toString().equals("BOOKS")||value.toString().equals("COLLECTIONS"))prepare.invoke(activity,value);
            navigate(activity,1);navigate(activity,0);
            View home=(View)activityField(activity,"root");
            HomeNavigationIcon house=home.findViewWithTag(HomeNavigationIcon.TAG);
            assertTrue(house.isSelected());assertNotNull(home.findViewWithTag("sv-nav-indicator"));
            View button=(View)house.getParent();button.performClick();
            assertSame(home,activityField(activity,"root"));
            assertEquals(27f,house.getLayoutParams().width/activity.getResources().getDisplayMetrics().density,.1f);
            assertEquals(0f,house.getTranslationX(),0f);assertEquals(1f,house.getScaleX(),0f);
            navigate(activity,2);
            View collections=(View)activityField(activity,"root");
            assertFalse(((HomeNavigationIcon)collections.findViewWithTag(HomeNavigationIcon.TAG)).isSelected());
            navigate(activity,0);assertSame(home,activityField(activity,"root"));
            controller.pause();controller.resume();
            Field motion=HomeNavigationIcon.class.getDeclaredField("animator");motion.setAccessible(true);
            assertNull(motion.get(house));assertTrue(house.isSelected());
            controller.recreate();
            MainActivity restored=controller.get();View page=(View)activityField(restored,"root");
            Method current=MainActivity.class.getDeclaredMethod("mainTabIndex");current.setAccessible(true);
            assertEquals(((Integer)current.invoke(restored))==0,
                    ((HomeNavigationIcon)page.findViewWithTag(HomeNavigationIcon.TAG)).isSelected());
        } finally {controller.pause().stop().destroy();}
    }
}
