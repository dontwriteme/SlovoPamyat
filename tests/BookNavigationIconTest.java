package org.biblememory.app;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.lang.reflect.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class BookNavigationIconTest {
    private BookNavigationIcon icon;
    private void scale(float value)throws Exception {
        Method method=ValueAnimator.class.getDeclaredMethod("setDurationScale",float.class);
        method.setAccessible(true);method.invoke(null,value);
    }
    private Object field(String name)throws Exception {
        Field f=BookNavigationIcon.class.getDeclaredField(name);f.setAccessible(true);return f.get(icon);
    }
    private void frame(long time)throws Exception {
        ValueAnimator animator=(ValueAnimator)field("animator");
        assertNotNull(animator);animator.setCurrentPlayTime(time);
        Bitmap image=Bitmap.createBitmap(27,27,Bitmap.Config.ARGB_8888);
        icon.draw(new Canvas(image));
    }
    @Before public void setup()throws Exception {
        scale(1f);
        icon=new BookNavigationIcon(RuntimeEnvironment.getApplication(),0xFFB7553D,false);
        icon.layout(0,0,27,27);
    }
    @After public void cleanup()throws Exception {icon.settle();scale(1f);}
    @Test public void inactiveBookHasBookmarkAndNoCrossOrHalo()throws Exception {
        assertFalse(icon.isSelected());assertEquals(1f,(Float)field("bookmark"),0f);
        assertEquals(0f,(Float)field("crossRise"),0f);assertEquals(0f,(Float)field("glow"),0f);
        assertEquals(-1f,(Float)field("pageTime"),0f);assertNull(field("animator"));
    }
    @Test public void bookmarkThenThreeSheetsThenCrossAndHalo()throws Exception {
        icon.transition(true,false);frame(100);
        assertTrue((Float)field("bookmark")>0f);assertTrue((Float)field("bookmark")<1f);
        assertEquals(0f,(Float)field("crossRise"),0f);
        frame(260);assertEquals(0f,(Float)field("bookmark"),0f);
        assertTrue((Float)field("pageTime")>0f);
        frame(700);assertTrue((Float)field("crossRise")>0f);assertTrue((Float)field("crossRise")<1f);
        assertEquals(0f,(Float)field("glow"),0f);
        frame(940);assertEquals(1f,(Float)field("crossRise"),0f);assertTrue((Float)field("glow")>0f);
        ((ValueAnimator)field("animator")).end();
        assertTrue(icon.isSelected());assertNull(field("animator"));
        assertEquals(1f,(Float)field("crossRise"),0f);assertEquals(1f,(Float)field("glow"),0f);
        assertEquals(0f,(Float)field("bookmark"),0f);assertEquals(-1f,(Float)field("pageTime"),0f);
    }
    @Test public void repeatedTapsReuseMotionThenTurnOnceWithCrossVisible()throws Exception {
        icon.transition(true,false);frame(260);Object motion=field("animator");
        for(int i=0;i<20;i++)icon.transition(true,true);
        assertSame(motion,field("animator"));((ValueAnimator)motion).end();
        icon.transition(true,true);frame(130);
        assertTrue((Boolean)field("repeating"));assertEquals(1f,(Float)field("crossRise"),0f);
        assertEquals(0f,(Float)field("bookmark"),0f);assertTrue((Float)field("glow")>1f);
    }
    @Test public void leavingDuringSheetsNeverInventsVisibleCross()throws Exception {
        icon.transition(true,false);frame(260);float[] state=icon.snapshot();
        ValueAnimator old=(ValueAnimator)field("animator");
        icon.transition(false,true,state);assertFalse(old.isStarted());frame(90);
        assertEquals(0f,(Float)field("crossRise"),0f);assertEquals(0f,(Float)field("glow"),0f);
        ((ValueAnimator)field("animator")).end();
        assertEquals(1f,(Float)field("bookmark"),0f);assertNull(field("animator"));
    }
    @Test public void settleOnPauseOrDetachReleasesMotionAndKeepsSelection()throws Exception {
        icon.transition(true,false);frame(160);icon.settle();
        assertNull(field("animator"));assertEquals(1f,(Float)field("crossRise"),0f);
        Method detach=BookNavigationIcon.class.getDeclaredMethod("onDetachedFromWindow");
        detach.setAccessible(true);icon.transition(true,true);detach.invoke(icon);
        assertNull(field("animator"));assertTrue(icon.isSelected());
    }
    @Test public void disabledAnimationsSelectFinalStateImmediately()throws Exception {
        scale(0f);icon.transition(true,false);
        assertEquals(1f,(Float)field("crossRise"),0f);assertNull(field("animator"));
        assertEquals(0f,(Float)field("bookmark"),0f);
        icon.transition(false,true);
        assertEquals(0f,(Float)field("crossRise"),0f);assertNull(field("animator"));
        assertEquals(1f,(Float)field("bookmark"),0f);
    }
    private Object activityField(MainActivity activity,String name)throws Exception {
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);
    }
    private void navigate(MainActivity activity,int index)throws Exception {
        Method method=MainActivity.class.getDeclaredMethod("goToMainTab",int.class);
        method.setAccessible(true);method.invoke(activity,index);
    }
    @Test public void navigationReusesCachedBibleAndReselectDoesNotRender()throws Exception {
        org.robolectric.android.controller.ActivityController<MainActivity> controller=
                Robolectric.buildActivity(MainActivity.class).create().start().resume();
        try {
            MainActivity activity=controller.get();
            ((Bible)activityField(activity,"bible")).load(activity);
            // Warm the two destination pages synchronously, just as the existing warm-up does.
            Class<?> screens=Class.forName("org.biblememory.app.MainActivity$Screen");
            Method prepare=MainActivity.class.getDeclaredMethod("prepareTab",screens);
            prepare.setAccessible(true);
            for(Object value:screens.getEnumConstants())
                if(value.toString().equals("BOOKS")||value.toString().equals("COLLECTIONS"))
                    prepare.invoke(activity,value);
            navigate(activity,1);
            View biblePage=(View)activityField(activity,"root");
            BookNavigationIcon book=biblePage.findViewWithTag(BookNavigationIcon.TAG);
            assertTrue(book.isSelected());
            // The click listener must only replay the sheet, including when the page is cached.
            ((View)book.getParent()).performClick();
            assertSame(biblePage,activityField(activity,"root"));
            navigate(activity,0);navigate(activity,1);
            assertSame(biblePage,activityField(activity,"root"));
            navigate(activity,2);
            View collections=(View)activityField(activity,"root");
            assertFalse(((BookNavigationIcon)collections.findViewWithTag(BookNavigationIcon.TAG)).isSelected());
            navigate(activity,1);assertSame(biblePage,activityField(activity,"root"));
            controller.pause();assertTrue(book.isSelected());
            Field animator=BookNavigationIcon.class.getDeclaredField("animator");animator.setAccessible(true);
            assertNull(animator.get(book));
            controller.resume();assertTrue(book.isSelected());
            controller.recreate();
            MainActivity restored=controller.get();
            View restoredPage=(View)activityField(restored,"root");
            Method tab=MainActivity.class.getDeclaredMethod("mainTabIndex");tab.setAccessible(true);
            assertEquals(((Integer)tab.invoke(restored))==1,
                    ((BookNavigationIcon)restoredPage.findViewWithTag(BookNavigationIcon.TAG)).isSelected());
        } finally {controller.pause().stop().destroy();}
    }

}
