package org.biblememory.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class SettingsViewTest {
    private final Activity context=Robolectric.buildActivity(Activity.class).setup().get();
    private final ScreenUi ui=new ScreenUi(context);
    private final SettingsView settings=new SettingsView(context,ui,new ScreenArtwork(context,ui));

    @Test public void exportAndRestoreHaveSeparateActions(){
        AtomicInteger exports=new AtomicInteger(), imports=new AtomicInteger(), resets=new AtomicInteger();
        LinearLayout content=ui.column();
        settings.render(content,exports::incrementAndGet,imports::incrementAndGet,resets::incrementAndGet);
        assertTrue(button(content,"Создать резервную копию").performClick());
        assertTrue(button(content,"Восстановить из копии").performClick());
        assertEquals(1,exports.get());
        assertEquals(1,imports.get());
        assertEquals(0,resets.get());
    }

    @Test public void destructiveActionsOnlyRunAfterConfirmation(){
        AtomicInteger imports=new AtomicInteger(), resets=new AtomicInteger();
        settings.confirmImport(imports::incrementAndGet);
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        settings.confirmReset(resets::incrementAndGet);
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0,imports.get());
        assertEquals(0,resets.get());

        settings.confirmImport(imports::incrementAndGet);
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        settings.confirmReset(resets::incrementAndGet);
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1,imports.get());
        assertEquals(1,resets.get());
    }

    private static View button(View view,String label){
        if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                View match=button(group.getChildAt(i),label);
                if(match!=null)return match;
            }
        }
        return null;
    }
}
