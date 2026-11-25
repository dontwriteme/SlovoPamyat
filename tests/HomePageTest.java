package org.biblememory.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.widget.ScrollView;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class HomePageTest {
    private final Activity context=Robolectric.buildActivity(Activity.class).setup().get();
    private final ScreenUi ui=new ScreenUi(context);
    private final AtomicInteger changes=new AtomicInteger();
    private final HomePage page=new HomePage(context,ui,new ScreenArtwork(context,ui),Bible.shared(),
        new ModeInfoPopover(ui),changes::incrementAndGet);

    @Test public void readsLegacyVerseIndexAndAdvancesAtTheSameInterval(){
        SharedPreferences prefs=context.getSharedPreferences("home_verse",0);
        prefs.edit().putInt("index",4).putLong("last_switch",System.currentTimeMillis()).commit();
        page.open();
        assertEquals(4,page.index());
        assertEquals(0,changes.get());
        prefs.edit().putLong("last_switch",System.currentTimeMillis()-2*HomeVerseLogic.ROTATE_MS-100).commit();
        page.refresh(true);
        assertEquals(0,page.index());
        assertEquals(0,prefs.getInt("index",-1));
        assertEquals(1,changes.get());
        page.refresh(true);
        assertEquals(1,changes.get());
    }

    @Test public void restoresLegacyScrollOnlyForCurrentView(){
        SharedPreferences prefs=context.getSharedPreferences("home_ui",0);
        prefs.edit().putInt("scroll_y",73).commit();
        page.open();
        ScrollView scroll=new ScrollView(context);
        View contents=new View(context);
        contents.setMinimumHeight(1000);
        scroll.addView(contents,new ScrollView.LayoutParams(300,1000));
        context.setContentView(scroll);
        scroll.measure(View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(150,View.MeasureSpec.EXACTLY));
        scroll.layout(0,0,300,150);
        page.restoreScroll(scroll,()->false,true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0,scroll.getScrollY());
        page.rememberScroll(scroll);
        assertEquals(73,prefs.getInt("scroll_y",-1));
        page.restoreScroll(scroll,()->true,true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(73,scroll.getScrollY());
        scroll.scrollTo(0,120);
        page.rememberScroll(scroll);
        assertEquals(120,prefs.getInt("scroll_y",-1));
    }
}
