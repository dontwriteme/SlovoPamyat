package org.biblememory.app;

import java.lang.reflect.*;

/** Test-only bridge to the real state owner during incremental extraction. */
final class ActivityTestAccess {
    private static final String[] COMPONENTS={"flashcardSession","studySession","bibleReader","collectionActions"};
    private static Object component(MainActivity activity,String name)throws Exception{
        try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
        catch(NoSuchFieldException absent){return null;}
    }
    static Object owner(MainActivity activity,String name)throws Exception{
        try{MainActivity.class.getDeclaredField(name);return activity;}catch(NoSuchFieldException absent){}
        for(String component:COMPONENTS){Object value=component(activity,component);if(value==null)continue;
            try{value.getClass().getDeclaredField(name);return value;}catch(NoSuchFieldException absent){}
        }
        throw new NoSuchFieldException(name);
    }
    static Field field(MainActivity activity,String name)throws Exception{
        Field f=owner(activity,name).getClass().getDeclaredField(name);f.setAccessible(true);return f;
    }
    static Object get(MainActivity activity,String name)throws Exception{return field(activity,name).get(owner(activity,name));}
    static void set(MainActivity activity,String name,Object value)throws Exception{field(activity,name).set(owner(activity,name),value);}
    static Object call(MainActivity activity,String name,Class<?>[] types,Object... values)throws Exception{
        try{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,values);}
        catch(NoSuchMethodException absent){}
        for(String component:COMPONENTS){Object target=component(activity,component);if(target==null)continue;
            try{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,values);}catch(NoSuchMethodException absent){}
        }
        throw new NoSuchMethodException(name);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) static Object option(MainActivity activity,String type,String value)throws Exception{
        String name=type.equals("Screen")?"screen":type.equals("BibleTarget")?"pickerTarget":"trainingMode";
        return Enum.valueOf((Class)field(activity,name).getType(),value);
    }
}
