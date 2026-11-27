package org.biblememory.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/** Device-local daily counts in the existing, backed-up home_verse preference group.
 * Historical dates are lower-bound evidence, never reconstructed from current mastery. */
final class StudyActivityHistory {
    private static final String PREFS="home_verse",PREFIX="activity_day_",HIST="activity_historical_";
    private static final String MIGRATED="activity_initialized";
    private StudyActivityHistory(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static LocalDate day(long timestamp){
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
    }
    static synchronized void initialize(Context c,LocalStore store){
        SharedPreferences p=prefs(c);
        if(p.getBoolean(MIGRATED,false))return;
        Map<LocalDate,Integer> counts=new HashMap<>();
        // Multiple verse rows can be written by one answer. Count distinct timestamps.
        try(Cursor cursor=store.readQuery("SELECT DISTINCT attempted_at FROM review_attempts WHERE accuracy=100 AND assisted=0 AND attempted_at>0",null)){
            while(cursor.moveToNext()){LocalDate date=day(cursor.getLong(0));counts.merge(date,1,Integer::sum);}
        }
        try(Cursor cursor=store.readQuery("SELECT first_success,last_success FROM recall_activity WHERE first_success>0 OR last_success>0",null)){
            while(cursor.moveToNext())for(int i=0;i<2;i++){
                long time=cursor.getLong(i);if(time>0)counts.putIfAbsent(day(time),1);
            }
        }
        SharedPreferences.Editor edit=p.edit();
        for(Map.Entry<LocalDate,Integer> e:counts.entrySet()){
            String date=e.getKey().toString();
            edit.putInt(PREFIX+date,Math.max(p.getInt(PREFIX+date,0),e.getValue()));
            edit.putBoolean(HIST+date,true);
        }
        edit.putBoolean(MIGRATED,true).apply();
    }
    static synchronized void record(Context c){
        SharedPreferences p=prefs(c);
        String key=PREFIX+LocalDate.now();
        int old=p.getInt(key,0);
        if(old<Integer.MAX_VALUE)p.edit().putInt(key,old+1).apply();
    }
    static Map<LocalDate,Integer> days(Context c){
        Map<LocalDate,Integer> out=new HashMap<>();
        for(Map.Entry<String,?> e:prefs(c).getAll().entrySet()){
            if(!e.getKey().startsWith(PREFIX)||!(e.getValue() instanceof Integer))continue;
            try{out.put(LocalDate.parse(e.getKey().substring(PREFIX.length())),(Integer)e.getValue());}
            catch(java.time.DateTimeException ignored){}
        }
        return out;
    }
    static boolean historical(Context c,LocalDate date){return prefs(c).getBoolean(HIST+date,false);}
}
