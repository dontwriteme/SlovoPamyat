package org.biblememory.app;

import android.content.Context;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class R42ProcessProbe {
    @Test public void countParsesAcrossProcessLifecycle()throws Exception{
        Context context=RuntimeEnvironment.getApplication();
        System.out.println("R42 phase launch");
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
        try{
            ActivityTestWorkers.awaitStartup(controller.get());
            System.out.println("R42 phase reader");
            Method action=MainActivity.class.getDeclaredMethod("goToMainTab",int.class);
            action.setAccessible(true);
            action.invoke(controller.get(),1);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            System.out.println("R42 phase default-store-review");
            try(LocalStore store=new LocalStore(context)){
                long group=store.newGroup("probe");
                long id=store.add(group,"Бытие 1:1","Бытие 1:1",Collections.singletonList(
                    new Bible.Verse("1:1","В начале сотворил Бог небо и землю.")));
                store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,1);
                store.dueItems(86_400_001);
            }
            System.out.println("R42 phase recreation");
            ActivityTestWorkers.recreate(controller);
        }finally{ActivityTestWorkers.destroy(controller);}
        System.out.println("R42 phase finished");
    }
}
