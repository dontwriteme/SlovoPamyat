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
            if(name.startsWith("collection"))return collectionField(activity,name);
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
