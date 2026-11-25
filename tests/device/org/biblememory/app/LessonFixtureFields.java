package org.biblememory.app;

import java.lang.reflect.Field;

/** Field access for framework regression fixtures in the isolated test APK. */
final class LessonFixtureFields {
    private LessonFixtureFields(){}

    static Object get(MainActivity activity,String name)throws ReflectiveOperationException{
        try{
            Field member=MainActivity.class.getDeclaredField(name);
            member.setAccessible(true);
            return member.get(activity);
        }catch(NoSuchFieldException moved){
            return LessonSessionState.class.getDeclaredField(name).get(get(activity,"lesson"));
        }
    }

    static void set(MainActivity activity,String name,Object value)throws ReflectiveOperationException{
        try{
            Field member=MainActivity.class.getDeclaredField(name);
            member.setAccessible(true);
            member.set(activity,value);
        }catch(NoSuchFieldException moved){
            LessonSessionState.class.getDeclaredField(name).set(get(activity,"lesson"),value);
        }
    }
}
