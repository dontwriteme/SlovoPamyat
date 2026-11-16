package org.biblememory.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
import org.biblememory.app.LocalStore.*;

/** Existing v1–v5 append-only backup codec, sharing the repository transaction scope. */
public final class LibraryBackup {
    private final RepositoryScope scope;
    LibraryBackup(RepositoryScope scope){this.scope=scope;}

    List<Group> groupsForBackup(){
        List<Group> list=new ArrayList<>();
        try(Cursor c=scope.read().rawQuery(
            "SELECT g.id,g.title,COUNT(e.id) FROM groups_ g LEFT JOIN entries e ON e.group_id=g.id GROUP BY g.id ORDER BY g.id",null)){
            while(c.moveToNext()){
                Group g=new Group();g.id=c.getLong(0);g.title=c.getString(1);g.count=c.getInt(2);list.add(g);
            }
        }
        return list;
    }

    public JSONObject exportJson() throws JSONException {
        JSONObject top=new JSONObject().put("format","slovo-pamyat-v5");
        JSONArray groups=new JSONArray();
        for(Group group:groupsForBackup()) {
            JSONObject g=new JSONObject().put("title",group.title);
            JSONArray entries=new JSONArray();
            for(Entry e:scope.study.entries(group.id)) {
                JSONObject item=new JSONObject()
                    .put("title",e.title).put("reference",e.reference).put("lines",new JSONArray(e.linesJson))
                    .put("learned",e.learned).put("repetitions",e.repetitions)
                    .put("days",e.days).put("ease",e.ease).put("due",e.due).put("last_score",e.lastScore);
                JSONArray mastered=new JSONArray();
                try(Cursor c=scope.read().rawQuery(
                    "SELECT position FROM verse_progress WHERE entry_id=? ORDER BY position",
                    new String[]{String.valueOf(e.id)})) {
                    while(c.moveToNext())mastered.put(c.getInt(0));
                }
                JSONArray stages=new JSONArray();
                try(Cursor c=scope.read().rawQuery(
                    "SELECT stage,first_line,last_line,best_score FROM stage_progress WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})) {
                    while(c.moveToNext())stages.put(new JSONObject()
                        .put("stage",c.getString(0)).put("start",c.getInt(1))
                        .put("end",c.getInt(2)).put("score",c.getInt(3)));
                }
                JSONArray recalls=new JSONArray();
                try(Cursor c=scope.read().rawQuery(
                    "SELECT stage,first_line,last_line,last_attempt,first_success,last_success,success_count,fail_streak,last_score FROM recall_activity WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})) {
                    while(c.moveToNext())recalls.put(new JSONObject()
                        .put("stage",c.getString(0)).put("start",c.getInt(1))
                        .put("end",c.getInt(2)).put("last_attempt",c.getLong(3))
                        .put("first_success",c.getLong(4)).put("last_success",c.getLong(5))
                        .put("success_count",c.getInt(6)).put("fail_streak",c.getInt(7))
                        .put("last_score",c.getInt(8)));
                }
                JSONArray lessons=new JSONArray();
                try(Cursor c=scope.read().rawQuery(
                    "SELECT position,completed FROM lesson_progress WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})){
                    while(c.moveToNext()) lessons.put(new JSONObject().put("position",c.getInt(0)).put("completed",c.getInt(1)));
                }
                JSONArray schedules=new JSONArray();
                try(Cursor c=scope.read().rawQuery(
                    "SELECT stage,first_line,last_line,level,due,last_score FROM review_schedule WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})){
                    while(c.moveToNext())schedules.put(new JSONObject().put("stage",c.getString(0))
                        .put("start",c.getInt(1)).put("end",c.getInt(2)).put("level",c.getInt(3))
                        .put("due",c.getLong(4)).put("score",c.getInt(5)));
                }
                JSONArray attempts=new JSONArray();
                try(Cursor c=scope.read().rawQuery(
                    "SELECT stage,first_line,last_line,attempted_at,accuracy,assisted FROM review_attempts WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})){
                    while(c.moveToNext())attempts.put(new JSONObject().put("stage",c.getString(0))
                        .put("start",c.getInt(1)).put("end",c.getInt(2)).put("at",c.getLong(3))
                        .put("score",c.getInt(4)).put("assisted",c.getInt(5)));
                }
                JSONArray transitions=new JSONArray();
                try(Cursor c=scope.read().rawQuery(
                    "SELECT from_position,to_position,failures,last_failed FROM transition_issues WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})){
                    while(c.moveToNext())transitions.put(new JSONObject()
                        .put("from",c.getInt(0)).put("to",c.getInt(1))
                        .put("failures",c.getInt(2)).put("last_failed",c.getLong(3)));
                }
                entries.put(item.put("mastered_positions",mastered).put("stage_results",stages)
                    .put("recall_activity",recalls).put("lesson_progress",lessons)
                    .put("review_schedule",schedules).put("review_attempts",attempts)
                    .put("transition_issues",transitions));
            }
            groups.put(g.put("entries",entries));
        }
        return top.put("groups",groups);
    }

    public void importJson(String json) throws JSONException {
        JSONObject top=new JSONObject(json);
        boolean v5="slovo-pamyat-v5".equals(top.optString("format"));
        boolean v4=v5 || "slovo-pamyat-v4".equals(top.optString("format"));
        boolean v3=v4 || "slovo-pamyat-v3".equals(top.optString("format"));
        boolean modern=v3 || "slovo-pamyat-v2".equals(top.optString("format"));
        if(!modern && !"slovo-pamyat-v1".equals(top.optString("format")))
            throw new JSONException("Неверный формат копии");
        JSONArray groups=top.getJSONArray("groups");
        if(groups.length()>1000) throw new JSONException("Слишком много подборок");
        // Validate the complete structure before making any changes; imports APPEND data.
        int count=0;
        for(int i=0;i<groups.length();i++) {
            JSONObject group=groups.getJSONObject(i); group.getString("title");
            JSONArray items=group.getJSONArray("entries");
            count+=items.length();
            if(count>10000) throw new JSONException("Слишком много отрывков");
            for(int j=0;j<items.length();j++) {
                JSONObject e=items.getJSONObject(j);
                e.getString("title");e.getString("reference");
                JSONArray vs=e.getJSONArray("lines");
                if(vs.length()==0 || vs.length()>500) throw new JSONException("Неверная длина отрывка");
                for(int k=0;k<vs.length();k++) {vs.getJSONObject(k).getString("text");vs.getJSONObject(k).getString("ref");}
                if(modern) {
                    JSONArray positions=e.getJSONArray("mastered_positions");
                    boolean[] taken=new boolean[vs.length()];
                    if(positions.length()>vs.length())throw new JSONException("Повторены освоенные позиции");
                    for(int k=0;k<positions.length();k++) {
                        int pos=positions.getInt(k);
                        if(pos<0||pos>=vs.length()||taken[pos])throw new JSONException("Неверные освоенные позиции");
                        taken[pos]=true;
                    }
                    JSONArray stages=e.getJSONArray("stage_results");
                    if(stages.length()>10000)throw new JSONException("Слишком много отметок этапов");
                    for(int k=0;k<stages.length();k++) {
                        JSONObject st=stages.getJSONObject(k);
                        String stage=st.getString("stage");
                        int a=st.getInt("start"),b=st.getInt("end");
                        if(!stage.equals("VERSE")&&!stage.equals("CHAIN")&&
                          !stage.equals("SECTION")&&!stage.equals("CHAPTER"))
                            throw new JSONException("Неизвестный этап");
                        if(a<0||b<=a||b>vs.length())throw new JSONException("Неверные границы этапа");
                    }
                }
                if(v4){
                    for(int m=0;m<e.getJSONArray("lesson_progress").length();m++){
                        JSONObject l=e.getJSONArray("lesson_progress").getJSONObject(m);
                        if(l.getInt("position")<0||l.getInt("position")>=vs.length() ||
                           l.getInt("completed")<0||l.getInt("completed")>5)throw new JSONException("Неверный этап урока");
                    }
                    for(int m=0;m<e.getJSONArray("review_schedule").length();m++){
                        JSONObject r=e.getJSONArray("review_schedule").getJSONObject(m);
                        if(r.getInt("start")<0||r.getInt("end")>vs.length()||r.getInt("start")>=r.getInt("end")||
                           r.getInt("level")<0||r.getInt("level")>=SpacedReviewLogic.DAYS.length)
                            throw new JSONException("Неверный интервал");
                    }
                }
                if(v5){
                    JSONArray weak=e.getJSONArray("transition_issues");
                    if(weak.length()>10000)throw new JSONException("Слишком много слабых переходов");
                    for(int k=0;k<weak.length();k++){
                        JSONObject w=weak.getJSONObject(k);
                        int from=w.getInt("from"),to=w.getInt("to");
                        if(from<0||to!=from+1||to>=vs.length()||w.getInt("failures")<0)
                            throw new JSONException("Неверные границы слабого перехода");
                    }
                }
                if(v3){
                    JSONArray recalls=e.getJSONArray("recall_activity");
                    if(recalls.length()>10000)throw new JSONException("Слишком много попыток");
                    for(int k=0;k<recalls.length();k++){
                        JSONObject r=recalls.getJSONObject(k);
                        String stage=r.getString("stage");
                        int a=r.getInt("start"),b=r.getInt("end");
                        if(!(stage.equals("VERSE")||stage.equals("CHAIN")||stage.equals("SECTION")||stage.equals("CHAPTER")) || a<0 || b<=a || b>vs.length())
                            throw new JSONException("Неверная запись повторения");
                    }
                }
            }
        }
        SQLiteDatabase db=scope.write();db.beginTransaction();
        try {
            for(int i=0;i<groups.length();i++) {
                JSONObject g=groups.getJSONObject(i);
                ContentValues gv=new ContentValues();gv.put("title",g.getString("title"));
                long id=db.insertOrThrow("groups_",null,gv);
                JSONArray items=g.getJSONArray("entries");
                for(int j=0;j<items.length();j++) {
                    JSONObject e=items.getJSONObject(j);ContentValues cv=new ContentValues();
                    JSONArray lines=e.getJSONArray("lines");
                    cv.put("group_id",id);cv.put("title",e.getString("title"));
                    cv.put("reference",e.getString("reference"));cv.put("lines",lines.toString());
                    cv.put("learned",modern?e.getJSONArray("mastered_positions").length():
                        Math.max(0,Math.min(lines.length(),e.optInt("learned",0))));
                    cv.put("repetitions",Math.max(0,e.optInt("repetitions",0)));
                    cv.put("days",Math.max(0,e.optInt("days",0)));
                    cv.put("ease",Math.max(1.3,e.optDouble("ease",2.5)));
                    cv.put("due",Math.max(0,e.optLong("due",0)));
                    cv.put("last_score",e.optInt("last_score",-1));
                    long importedId=db.insertOrThrow("entries",null,cv);
                    JSONArray positions=modern?e.getJSONArray("mastered_positions"):null;
                    int mastered=modern?positions.length():cv.getAsInteger("learned");
                    for(int k=0;k<mastered;k++) {
                        ContentValues pv=new ContentValues();pv.put("entry_id",importedId);
                        pv.put("position",modern?positions.getInt(k):k);pv.put("best_score",90);
                        db.insertOrThrow("verse_progress",null,pv);
                    }
                    if(modern) {
                        JSONArray stages=e.getJSONArray("stage_results");
                        for(int k=0;k<stages.length();k++) {
                            JSONObject st=stages.getJSONObject(k);ContentValues pv=new ContentValues();
                            pv.put("entry_id",importedId);pv.put("stage",st.getString("stage"));
                            pv.put("first_line",st.getInt("start"));pv.put("last_line",st.getInt("end"));
                            pv.put("best_score",st.optInt("score",90));
                            db.insertWithOnConflict("stage_progress",null,pv,SQLiteDatabase.CONFLICT_REPLACE);
                        }
                    }
                    if(v3){
                        JSONArray recalls=e.getJSONArray("recall_activity");
                        for(int k=0;k<recalls.length();k++){
                            JSONObject r=recalls.getJSONObject(k);ContentValues rv=new ContentValues();
                            rv.put("entry_id",importedId);rv.put("stage",r.getString("stage"));
                            rv.put("first_line",r.getInt("start"));rv.put("last_line",r.getInt("end"));
                            rv.put("last_attempt",Math.max(0,r.optLong("last_attempt",0)));
                            rv.put("first_success",Math.max(0,r.optLong("first_success",0)));
                            rv.put("last_success",Math.max(0,r.optLong("last_success",0)));
                            rv.put("success_count",Math.max(0,r.optInt("success_count",0)));
                            rv.put("fail_streak",Math.max(0,r.optInt("fail_streak",0)));
                            rv.put("last_score",r.optInt("last_score",-1));
                            db.insertWithOnConflict("recall_activity",null,rv,SQLiteDatabase.CONFLICT_REPLACE);
                        }
                    }
                    if(v4){
                        JSONArray lessons=e.getJSONArray("lesson_progress");
                        for(int k=0;k<lessons.length();k++){
                            JSONObject l=lessons.getJSONObject(k);ContentValues cvl=new ContentValues();
                            cvl.put("entry_id",importedId);cvl.put("position",l.getInt("position"));
                            cvl.put("completed",l.getInt("completed"));
                            db.insertWithOnConflict("lesson_progress",null,cvl,SQLiteDatabase.CONFLICT_REPLACE);
                        }
                        JSONArray schedules=e.getJSONArray("review_schedule");
                        for(int k=0;k<schedules.length();k++){
                            JSONObject r=schedules.getJSONObject(k);ContentValues rv=new ContentValues();
                            rv.put("entry_id",importedId);rv.put("stage",r.getString("stage"));
                            rv.put("first_line",r.getInt("start"));rv.put("last_line",r.getInt("end"));
                            rv.put("level",r.getInt("level"));rv.put("due",r.getLong("due"));
                            rv.put("last_score",r.optInt("score",-1));
                            db.insertWithOnConflict("review_schedule",null,rv,SQLiteDatabase.CONFLICT_REPLACE);
                        }
                        JSONArray attempts=e.getJSONArray("review_attempts");
                        for(int k=0;k<attempts.length();k++){
                            JSONObject r=attempts.getJSONObject(k);ContentValues rv=new ContentValues();
                            rv.put("entry_id",importedId);rv.put("stage",r.getString("stage"));
                            rv.put("first_line",r.getInt("start"));rv.put("last_line",r.getInt("end"));
                            rv.put("attempted_at",Math.max(0,r.optLong("at",0)));
                            rv.put("accuracy",r.getInt("score"));rv.put("assisted",r.optInt("assisted",0));
                            db.insertOrThrow("review_attempts",null,rv);
                        }
                    } else {
                        // Import from older versions: keep their mastered verses accessible.
                        db.execSQL("INSERT OR IGNORE INTO lesson_progress (entry_id,position,completed) " +
                            "SELECT entry_id,position,5 FROM verse_progress WHERE entry_id=?",
                            new Object[]{importedId});
                        db.execSQL("INSERT OR IGNORE INTO review_schedule (entry_id,stage,first_line,last_line,level,due,last_score) " +
                            "SELECT entry_id,'VERSE',position,position+1,0,0,best_score FROM verse_progress WHERE entry_id=?",
                            new Object[]{importedId});
                    }
                    if(v5){
                        JSONArray weak=e.getJSONArray("transition_issues");
                        for(int k=0;k<weak.length();k++){
                            JSONObject w=weak.getJSONObject(k);ContentValues vv=new ContentValues();
                            vv.put("entry_id",importedId);vv.put("from_position",w.getInt("from"));
                            vv.put("to_position",w.getInt("to"));
                            vv.put("failures",w.getInt("failures"));
                            vv.put("last_failed",Math.max(0,w.optLong("last_failed",0)));
                            db.insertWithOnConflict("transition_issues",null,vv,SQLiteDatabase.CONFLICT_REPLACE);
                        }
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
