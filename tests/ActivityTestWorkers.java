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

    static Object lessonField(MainActivity activity,String name)throws Exception{
        if("screen".equals(name))return ((NavigationState)lessonField(activity,"navigation")).screen;
        if("store".equals(name))return ((ActivityDataLifetime)lessonField(activity,"dataLifetime")).store();
        try{
            Field member=MainActivity.class.getDeclaredField(name);
            member.setAccessible(true);
            return member.get(activity);
        }catch(NoSuchFieldException moved){
            if(name.startsWith("collection"))return collectionField(activity,name);
            return LessonSessionState.class.getDeclaredField(name).get(lessonField(activity,"lesson"));
        }
    }

    static void setLessonField(MainActivity activity,String name,Object value)throws Exception{
        if("screen".equals(name)){((NavigationState)lessonField(activity,"navigation")).screen=(Screen)value;return;}
        try{
            Field member=MainActivity.class.getDeclaredField(name);
            member.setAccessible(true);
            member.set(activity,value);
        }catch(NoSuchFieldException moved){
            LessonSessionState.class.getDeclaredField(name).set(lessonField(activity,"lesson"),value);
        }
    }

    static ExecutorService executor(MainActivity activity,String name)throws Exception{
        if("collectionExecutor".equals(name))return (ExecutorService)collectionField(activity,name);
        if("writingExecutor".equals(name))return (ExecutorService)writingField(activity,"executor");
        Field field=MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return (ExecutorService)field.get(activity);
    }

    static Object writingField(MainActivity activity,String name)throws Exception{
        Field owner=MainActivity.class.getDeclaredField("writingWorker");
        owner.setAccessible(true);
        Field field=LessonWritingWorker.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner.get(activity));
    }

    static void awaitStartup(MainActivity activity)throws Exception{
        Field field=MainActivity.class.getDeclaredField("bibleStartup");
        field.setAccessible(true);
        Field task=BibleStartup.class.getDeclaredField("task");
        task.setAccessible(true);
        ((java.util.concurrent.Future<?>)task.get(field.get(activity))).get(30,TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
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

    static void awaitWriting(MainActivity activity)throws Exception{
        executor(activity,"writingExecutor").submit(()->{}).get(30,TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    static void awaitDestroyed(MainActivity activity)throws Exception{
        for(String name:new String[]{"collectionExecutor","writingExecutor"}){
            assertTrue(name+" did not finish during teardown",
                executor(activity,name).awaitTermination(30,TimeUnit.SECONDS));
        }
        Field search=MainActivity.class.getDeclaredField("bibleSearch");
        search.setAccessible(true);
        Field worker=BibleSearchSession.class.getDeclaredField("executor");
        worker.setAccessible(true);
        ExecutorService executor=(ExecutorService)worker.get(search.get(activity));
        assertTrue("Bible search did not finish during teardown",executor.awaitTermination(30,TimeUnit.SECONDS));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    static Object collectionField(MainActivity activity,String name)throws ReflectiveOperationException{
        String owner;
        String field;
        switch(name){
            case "collectionStore":owner="collectionIo";field="store";break;
            case "collectionExecutor":owner="collectionIo";field="executor";break;
            case "collectionTask":owner="collectionDetail";field="task";break;
            case "collectionDetailLoading":owner="collectionDetail";field="loading";break;
            case "collectionQueueGroup":owner="studyQueue";field=name;break;
            case "collectionQueueCursor":owner="studyQueue";field=name;break;
            default:throw new NoSuchFieldException(name);
        }
        Field component=MainActivity.class.getDeclaredField(owner);
        component.setAccessible(true);
        Object target=component.get(activity);
        Field member=target.getClass().getDeclaredField(field);
        member.setAccessible(true);
        return member.get(target);
    }
}
