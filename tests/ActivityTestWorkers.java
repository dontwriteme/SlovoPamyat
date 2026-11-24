package org.biblememory.app;

import android.os.Looper;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.assertTrue;

/** Wait for work owned by this Activity before Robolectric resets its SQLite shadow. */
final class ActivityTestWorkers {
    private ActivityTestWorkers() {}

    static ExecutorService executor(MainActivity activity,String name)throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return (ExecutorService)field.get(activity);
    }

    static void awaitStartup(MainActivity activity)throws Exception{
        awaitCollectionWork(activity);
    }

    static void awaitCollectionWork(MainActivity activity)throws Exception{
        executor(activity,"collectionExecutor").submit(()->{}).get(30,TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    static void destroy(ActivityController<MainActivity> controller)throws Exception{
        MainActivity old=controller.get();
        controller.pause().stop().destroy();
        awaitDestroyed(old);
    }

    static void recreate(ActivityController<MainActivity> controller)throws Exception{
        MainActivity old=controller.get();
        controller.recreate();
        awaitDestroyed(old);
        awaitStartup(controller.get());
    }

    static void awaitDestroyed(MainActivity activity)throws Exception{
        for(String name:new String[]{"collectionExecutor","bibleSearchExecutor"}){
            assertTrue(name+" did not finish during teardown",
                executor(activity,name).awaitTermination(30,TimeUnit.SECONDS));
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
}
