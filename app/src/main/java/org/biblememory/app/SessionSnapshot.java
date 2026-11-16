package org.biblememory.app;

import android.os.Bundle;
import java.util.List;

/** Small typed codecs shared by controller-owned Activity snapshots. No database writes. */
final class SessionSnapshot {
    private SessionSnapshot(){}
    static void putLongs(Bundle state,String key,List<Long> values){
        long[] array=new long[values.size()];for(int i=0;i<array.length;i++)array[i]=values.get(i);
        state.putLongArray(key,array);
    }
    static void getLongs(Bundle state,String key,List<Long> values){
        values.clear();long[] array=state.getLongArray(key);if(array!=null)for(long value:array)values.add(value);
    }
    static void putStep(Bundle state,String key,StudyPlanLogic.Step step){
        if(step==null)return;
        Bundle value=new Bundle();value.putInt("start",step.start);value.putInt("end",step.end);value.putString("title",step.title);
        state.putBundle(key,value);
    }
    static StudyPlanLogic.Step getStep(Bundle state,String key){
        Bundle value=state.getBundle(key);if(value==null)return null;
        int start=value.getInt("start"),end=value.getInt("end");
        return start>=0&&end>start?new StudyPlanLogic.Step(start,end,value.getString("title","")):null;
    }
    static Bundle due(LocalStore.DueItem item){
        if(item==null)return null;
        Bundle value=new Bundle();value.putLong("entry",item.entryId);value.putLong("due",item.due);
        value.putString("stage",item.stage);value.putInt("start",item.start);value.putInt("end",item.end);return value;
    }
    static LocalStore.DueItem due(Bundle value){
        return value==null?null:new LocalStore.DueItem(value.getLong("entry"),value.getString("stage","VERSE"),
            value.getInt("start"),value.getInt("end"),value.getLong("due"));
    }
}
