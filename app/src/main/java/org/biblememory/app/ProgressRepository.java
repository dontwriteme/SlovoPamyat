package org.biblememory.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
import org.biblememory.app.LocalStore.*;

/** SQL operations for this domain, sharing the owner's connection and transactions. */
public final class ProgressRepository {
    private final RepositoryScope scope;
    ProgressRepository(RepositoryScope scope){this.scope=scope;}

    /** One submitted answer: receipt, history, mastery and schedule commit together.
     * Existing recall timestamps serve as receipts; no schema migration is needed. */
    boolean commitStudyAttempt(Entry expected,int start,int end,long attemptedAt,Runnable writes){
        return scope.transaction(db->{
            Entry current=scope.study.entry(expected.id);
            if(current==null || !Objects.equals(current.linesJson,expected.linesJson) ||
                !Objects.equals(current.reference,expected.reference) || start<0 ||
                end<=start || end>current.lines().size())
                throw new IllegalStateException("Study material changed; reopen the lesson");
            try(Cursor c=db.rawQuery("SELECT 1 FROM recall_activity WHERE entry_id=? AND last_attempt=? LIMIT 1",
                    new String[]{String.valueOf(expected.id),String.valueOf(attemptedAt)})){
                if(c.moveToFirst())return false;
            }
            writes.run();
            return true;
        });
    }

    public void recordWeakTransition(long entryId,int from,int to,long now){
        if(from<0||to!=from+1)return;
        scope.transaction(transactionDb->{
            SQLiteDatabase db=scope.write();
            db.execSQL("INSERT OR IGNORE INTO transition_issues(entry_id,from_position,to_position) VALUES(?,?,?)",
                new Object[]{entryId,from,to});
            db.execSQL("UPDATE transition_issues SET failures=failures+1,last_failed=? WHERE entry_id=? AND from_position=? AND to_position=?",
                new Object[]{now,entryId,from,to});
            return null;
        });
    }

    public void rescheduleWeakVerse(long id,int position,long now){
        if(!isStepMastered(id,"VERSE",position,position+1))return;
        SQLiteDatabase db=scope.write();
        long due=now+86400000L;
        ContentValues v=new ContentValues();v.put("entry_id",id);v.put("stage","VERSE");
        v.put("first_line",position);v.put("last_line",position+1);
        v.put("level",0);v.put("due",due);v.put("last_score",0);
        // Don't replace an even-earlier scheduled repeat.
        try(Cursor c=db.rawQuery("SELECT due FROM review_schedule WHERE entry_id=? AND stage='VERSE' AND first_line=? AND last_line=?",
            new String[]{String.valueOf(id),String.valueOf(position),String.valueOf(position+1)})){
            if(c.moveToFirst() && c.getLong(0)>0 && c.getLong(0)<due)return;
        }
        db.replaceOrThrow("review_schedule",null,v);
    }

    private List<long[]> matchingVerseLocations(long id,int position){
        List<long[]> matches=new ArrayList<>();
        Entry original=scope.study.entry(id);
        if(original==null)return matches;
        List<Bible.Verse> lines=original.lines();
        if(position<0||position>=lines.size())return matches;
        Bible.Verse verse=lines.get(position);
        // User-authored verses never inherit unrelated canonical-Bible milestones.
        boolean canonical=false;
        for(String book:Bible.NAMES){
            if(original.reference.startsWith(book+" ")){canonical=true;break;}
        }
        if(!canonical)return matches;
        for(Entry candidate:scope.study.allEntries()){
            if(candidate.id==id)continue;
            boolean fromBible=false;
            for(String book:Bible.NAMES){
                if(candidate.reference.startsWith(book+" ")){fromBible=true;break;}
            }
            if(!fromBible)continue;
            List<Bible.Verse> other=candidate.lines();
            for(int i=0;i<other.size();i++){
                if(verse.ref.equals(other.get(i).ref)&&verse.text.equals(other.get(i).text)){
                    matches.add(new long[]{candidate.id,i});
                }
            }
        }
        return matches;
    }

    private int sharedVerseCompletion(long entryId,int position){
        int completed=0;
        for(long[] match:matchingVerseLocations(entryId,position)){
            try(Cursor c=scope.read().rawQuery(
                "SELECT completed FROM lesson_progress WHERE entry_id=? AND position=?",
                new String[]{String.valueOf(match[0]),String.valueOf(match[1])})){
                if(c.moveToFirst())completed=Math.max(completed,c.getInt(0));
            }
            try(Cursor c=scope.read().rawQuery(
                "SELECT 1 FROM verse_progress WHERE entry_id=? AND position=?",
                new String[]{String.valueOf(match[0]),String.valueOf(match[1])})){
                if(c.moveToFirst())completed=Math.max(completed,5);
            }
        }
        return completed;
    }

    public int exerciseDone(long id,int position) {
        int direct=0;
        try(Cursor c=scope.read().rawQuery("SELECT completed FROM lesson_progress WHERE entry_id=? AND position=?",
            new String[]{String.valueOf(id),String.valueOf(position)})){
            if(c.moveToFirst())direct=c.getInt(0);
        }
        int shared=sharedVerseCompletion(id,position);
        // A familiar verse in a second collection must not be locked again.
        return Math.max(Math.max(direct,shared),isStepMastered(id,"VERSE",position,position+1)?5:0);
    }

    public int unlockedPosition(long id,int count) {
        if(count==0)return 0;
        try(Cursor c=scope.read().rawQuery(
            "SELECT MAX(position) FROM lesson_progress WHERE entry_id=? AND completed>=5",
            new String[]{String.valueOf(id)})){
            if(c.moveToFirst() && !c.isNull(0))return Math.min(count-1,c.getInt(0)+1);
        }
        // Old mastered verses with sparse positions: preserve their accessibility too.
        try(Cursor c=scope.read().rawQuery("SELECT MAX(position) FROM verse_progress WHERE entry_id=?",
            new String[]{String.valueOf(id)})){
            if(c.moveToFirst() && !c.isNull(0))return Math.min(count-1,c.getInt(0)+1);
        }
        return 0;
    }

    public void finishExercise(long id,int position,int completed) {
        if(completed<1||completed>5)throw new IllegalArgumentException("Invalid lesson stage");
        int old=exerciseDone(id,position);
        if(completed>old){
            ContentValues cv=new ContentValues();cv.put("entry_id",id);cv.put("position",position);
            cv.put("completed",completed);
            scope.write().replaceOrThrow("lesson_progress",null,cv);
        }
    }

    public void scheduleReview(long id,String stage,int start,int end,int accuracy,boolean assisted,long now) {
        SQLiteDatabase db=scope.write();
        db.beginTransaction();try{
            ContentValues a=new ContentValues();a.put("entry_id",id);a.put("stage",stage);
            a.put("first_line",start);a.put("last_line",end);
            a.put("attempted_at",now);a.put("accuracy",accuracy);a.put("assisted",assisted?1:0);
            db.insertOrThrow("review_attempts",null,a);
            if(!assisted){
                int level=-1;
                try(Cursor c=db.rawQuery("SELECT level FROM review_schedule WHERE entry_id=? AND stage=? AND first_line=? AND last_line=?",
                    new String[]{String.valueOf(id),stage,String.valueOf(start),String.valueOf(end)})){
                    if(c.moveToFirst())level=c.getInt(0);
                }
                // A failed attempt before first mastery must not advance the initial interval.
                if(accuracy==100 && !isStepMastered(id,stage,start,end))level=-1;
                SpacedReviewLogic.State r=SpacedReviewLogic.update(level,accuracy,false,now);
                ContentValues cv=new ContentValues();cv.put("entry_id",id);cv.put("stage",stage);
                cv.put("first_line",start);cv.put("last_line",end);
                cv.put("level",r.level);cv.put("due",r.due);cv.put("last_score",accuracy);
                db.replaceOrThrow("review_schedule",null,cv);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    public List<DueItem> dueItems(long now) {
        List<DueItem> items=new ArrayList<>();
        try(Cursor c=scope.read().rawQuery(
            "SELECT r.entry_id,r.stage,r.first_line,r.last_line,r.due FROM review_schedule r WHERE r.due<=? AND " +
            "(EXISTS(SELECT 1 FROM stage_progress s WHERE s.entry_id=r.entry_id AND s.stage=r.stage AND s.first_line=r.first_line AND s.last_line=r.last_line) OR " +
            "(r.stage='VERSE' AND EXISTS(SELECT 1 FROM verse_progress v WHERE v.entry_id=r.entry_id AND v.position=r.first_line))) ORDER BY r.due ASC",
            new String[]{String.valueOf(now)})){
            while(c.moveToNext())items.add(new DueItem(c.getLong(0),c.getString(1),c.getInt(2),c.getInt(3),c.getLong(4)));
        }
        return items;
    }

    public int dueCount(long now){return dueItems(now).size();}

    public int masteredLinkedSegments(long entryId){
        try(Cursor c=scope.read().rawQuery(
            "SELECT COUNT(*) FROM stage_progress WHERE entry_id=? AND stage<>'VERSE'",
            new String[]{String.valueOf(entryId)})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    public int masteryCount(long entryId){
        Entry entry=scope.study.entry(entryId);
        if(entry==null)return 0;
        int total=0;
        for(int p=0;p<entry.lines().size();p++){
            if(isStepMastered(entryId,"VERSE",p,p+1))total++;
        }
        return total;
    }

    public int firstUnmastered(long entryId,int length){
        for(int i=0;i<length;i++)if(!isStepMastered(entryId,"VERSE",i,i+1))return i;
        return 0;
    }

    public boolean isStepMastered(long entryId,String stage,int start,int end){
        if("VERSE".equals(stage)&&end==start+1){
            try(Cursor c=scope.read().rawQuery(
                "SELECT 1 FROM verse_progress WHERE entry_id=? AND position=? LIMIT 1",
                new String[]{String.valueOf(entryId),String.valueOf(start)})){
                if(c.moveToFirst())return true;
            }
            for(long[] match:matchingVerseLocations(entryId,start)){
                try(Cursor c=scope.read().rawQuery(
                    "SELECT 1 FROM verse_progress WHERE entry_id=? AND position=? LIMIT 1",
                    new String[]{String.valueOf(match[0]),String.valueOf(match[1])})){
                    if(c.moveToFirst())return true;
                }
            }
            return false;
        }
        try(Cursor c=scope.read().rawQuery(
            "SELECT 1 FROM stage_progress WHERE entry_id=? AND stage=? AND first_line=? AND last_line=? LIMIT 1",
            new String[]{String.valueOf(entryId),stage,String.valueOf(start),String.valueOf(end)})){
            return c.moveToFirst();
        }
    }

    public void markMastered(long entryId,int start,int end,int score,String stage) {
        SQLiteDatabase db=scope.write();db.beginTransaction();
        try {
            for(int i=start;"VERSE".equals(stage) && i<end;i++) {
                ContentValues cv=new ContentValues();cv.put("entry_id",entryId);
                cv.put("position",i);cv.put("best_score",score);
                db.replaceOrThrow("verse_progress",null,cv);
            }
            ContentValues step=new ContentValues();step.put("entry_id",entryId);
            step.put("stage",stage);step.put("first_line",start);step.put("last_line",end);
            step.put("best_score",score);
            db.replaceOrThrow("stage_progress",null,step);
            db.setTransactionSuccessful();
        } finally{db.endTransaction();}
    }

    public void recordRecall(long entryId,String stage,int start,int end,int accuracy,
                             boolean assisted,long now) {
        SQLiteDatabase db=scope.write();
        db.beginTransaction();
        try {
            long first=0,last=0;int successes=0,fails=0;
            try(Cursor c=db.rawQuery("SELECT first_success,last_success,success_count,fail_streak FROM recall_activity WHERE entry_id=? AND stage=? AND first_line=? AND last_line=?",
                new String[]{String.valueOf(entryId),stage,String.valueOf(start),String.valueOf(end)})) {
                if(c.moveToFirst()){first=c.getLong(0);last=c.getLong(1);successes=c.getInt(2);fails=c.getInt(3);}
            }
            if(accuracy==100 && !assisted) {
                if(first==0)first=now;
                // Repeating immediately is practice, not evidence of delayed retention.
                if(successes==0 || (last>0 && now-last>=24L*3600000L))successes++;
                last=now;fails=0;
            } else if(accuracy<90)fails++;
            ContentValues v=new ContentValues();v.put("entry_id",entryId);v.put("stage",stage);
            v.put("first_line",start);v.put("last_line",end);v.put("last_attempt",now);
            v.put("first_success",first);v.put("last_success",last);v.put("success_count",successes);
            v.put("fail_streak",fails);v.put("last_score",accuracy);
            db.replaceOrThrow("recall_activity",null,v);
            db.setTransactionSuccessful();
        } finally{db.endTransaction();}
    }

    public int recentFailures(long id,String stage,int start,int end) {
        try(Cursor c=scope.read().rawQuery(
            "SELECT fail_streak FROM recall_activity WHERE entry_id=? AND stage=? AND first_line=? AND last_line=?",
            new String[]{String.valueOf(id),stage,String.valueOf(start),String.valueOf(end)})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    private boolean consolidatedLocal(long id,String stage,int start,int end) {
        try(Cursor c=scope.read().rawQuery(
            "SELECT first_success,last_success,success_count,fail_streak FROM recall_activity WHERE entry_id=? AND stage=? AND first_line=? AND last_line=?",
            new String[]{String.valueOf(id),stage,String.valueOf(start),String.valueOf(end)})) {
            return c.moveToFirst() && c.getLong(0)>0 &&
                c.getLong(1)-c.getLong(0)>=24L*3600000L && c.getInt(2)>=2 && c.getInt(3)==0;
        }
    }

    public boolean consolidated(long id,String stage,int start,int end){
        if(consolidatedLocal(id,stage,start,end))return true;
        if("VERSE".equals(stage)&&end==start+1){
            for(long[] loc:matchingVerseLocations(id,start))
                if(consolidatedLocal(loc[0],"VERSE",(int)loc[1],(int)loc[1]+1))return true;
        }
        return false;
    }

    public boolean recallDue(long id,String stage,int start,int end,long now) {
        try(Cursor c=scope.read().rawQuery(
            "SELECT last_success,success_count FROM recall_activity WHERE entry_id=? AND stage=? AND first_line=? AND last_line=?",
            new String[]{String.valueOf(id),stage,String.valueOf(start),String.valueOf(end)})) {
            if(!c.moveToFirst() || c.getLong(0)<=0)return false;
            long days=c.getInt(1)>=2?3L:1L;
            return now>=c.getLong(0) && now-c.getLong(0)>=days*86400000L;
        }
    }

    public AdaptiveLearningLogic.Progress progressFor(final long id) {
        return new AdaptiveLearningLogic.Progress() {
            public boolean mastered(StudyPlanLogic.Stage stage,int start,int end) {
                return isStepMastered(id,stage.name(),start,end);
            }
            public boolean isDue(StudyPlanLogic.Stage stage,int start,int end,long now) {
                return recallDue(id,stage.name(),start,end,now);
            }
            public int recentFailures(StudyPlanLogic.Stage stage,int start,int end) {
                return ProgressRepository.this.recentFailures(id,stage.name(),start,end);
            }
        };
    }

    int completedExerciseCount(long id){
        try(Cursor c=scope.read().rawQuery(
            "SELECT COALESCE(SUM(completed),0) FROM lesson_progress WHERE entry_id=?",
            new String[]{String.valueOf(id)})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    public List<Entry> due() {
        List<Entry> result=new ArrayList<>();Set<Long> seen=new HashSet<>();
        for(DueItem d:dueItems(System.currentTimeMillis()))if(seen.add(d.entryId)){
            Entry e=scope.study.entry(d.entryId);if(e!=null)result.add(e);
        }
        return result;
    }

    public void update(Entry e,ReviewLogic.Schedule schedule,int learned,int score) {
        ContentValues v=new ContentValues();v.put("learned",learned);
        v.put("repetitions",schedule.repetitions);v.put("days",schedule.intervalDays);
        v.put("ease",schedule.ease);v.put("due",schedule.nextDue);
        v.put("last_score",score);
        scope.write().update("entries",v,"id=?",new String[]{String.valueOf(e.id)});
    }

    public int consolidatedVerseCount(long entryId){
        Entry entry=scope.study.entry(entryId);
        if(entry==null)return 0;
        int total=0;
        for(int i=0;i<entry.lines().size();i++){
            if(consolidated(entryId,"VERSE",i,i+1))total++;
        }
        return total;
    }

    public Map<Integer,VerseStatus> statusesForChapter(String bookName,int chapter) {
        Map<Integer,VerseStatus> out=new HashMap<>();
        String match=chapter+":";
        for(Entry e:scope.study.allEntries()) {
            if(!e.reference.startsWith(bookName+" "))continue;
            List<Bible.Verse> lines=e.lines();
            for(int i=0;i<lines.size();i++){
                Bible.Verse line=lines.get(i);
                if(!line.ref.startsWith(match))continue;
                int number;
                try{number=Integer.parseInt(line.ref.substring(match.length()));}
                catch(NumberFormatException ignored){continue;}
                VerseStatus candidate=new VerseStatus();
                candidate.entryId=e.id;candidate.position=i;
                int completed=exerciseDone(e.id,i);
                boolean mastered=isStepMastered(e.id,"VERSE",i,i+1)||completed>=5;
                boolean consolidated=consolidated(e.id,"VERSE",i,i+1);
                candidate.rank=consolidated?3:mastered?2:completed>0?1:0;
                candidate.state=candidate.rank==3?"Выучен":candidate.rank==2?
                    "Первоначально освоен":candidate.rank==1?"В процессе":"Не изучен";
                String[] args={String.valueOf(e.id),String.valueOf(i),String.valueOf(i+1)};
                long firstSuccess=0;
                try(Cursor c=scope.read().rawQuery(
                    "SELECT first_success,last_success FROM recall_activity WHERE entry_id=? AND stage='VERSE' AND first_line=? AND last_line=?",args)){
                    if(c.moveToFirst()){firstSuccess=c.getLong(0);candidate.last=c.getLong(1);}
                }
                if(firstSuccess>0){
                    try(Cursor c=scope.read().rawQuery(
                        "SELECT COUNT(DISTINCT attempted_at / 86400000) AS total,"+
                        "COUNT(DISTINCT CASE WHEN accuracy=100 THEN attempted_at / 86400000 END) AS good "+
                        "FROM review_attempts WHERE entry_id=? AND stage='VERSE' AND first_line=? AND last_line=? "+
                        "AND assisted=0 AND attempted_at>=?",new String[]{args[0],args[1],args[2],String.valueOf(firstSuccess+86400000L)})){
                        if(c.moveToFirst()){candidate.repetitions=c.getInt(0);candidate.successful=c.getInt(1);}
                    }
                }
                try(Cursor c=scope.read().rawQuery(
                    "SELECT due FROM review_schedule WHERE entry_id=? AND stage='VERSE' AND first_line=? AND last_line=?",args)){
                    if(c.moveToFirst())candidate.nextDue=c.getLong(0);
                }
                VerseStatus old=out.get(number);
                if(old==null||candidate.rank>old.rank || (candidate.rank==old.rank && candidate.repetitions>old.repetitions))
                    out.put(number,candidate);
            }
        }
        return out;
    }
    /** Remap history under the material revision transaction owned by StudyRepository. */
    void remapForRevision(SQLiteDatabase db,long id,Map<Integer,Integer> remap){
        String[] tables={"verse_progress","lesson_progress","stage_progress","recall_activity",
            "review_schedule","review_attempts","transition_issues"};
        for(String table:tables){
            List<ContentValues> saved=new ArrayList<>();
            try(Cursor c=db.query(table,null,"entry_id=?",new String[]{String.valueOf(id)},null,null,null)){
                while(c.moveToNext()){
                    ContentValues cv=new ContentValues();
                    for(int col=0;col<c.getColumnCount();col++){
                        String key=c.getColumnName(col);
                        if(key.equals("id"))continue;
                        switch(c.getType(col)){
                            case Cursor.FIELD_TYPE_INTEGER:cv.put(key,c.getLong(col));break;
                            case Cursor.FIELD_TYPE_FLOAT:cv.put(key,c.getDouble(col));break;
                            case Cursor.FIELD_TYPE_STRING:cv.put(key,c.getString(col));break;
                            case Cursor.FIELD_TYPE_BLOB:cv.put(key,c.getBlob(col));break;
                            default:cv.putNull(key);break;
                        }
                    }
                    if(table.equals("verse_progress")||table.equals("lesson_progress")){
                        Integer to=remap.get(cv.getAsInteger("position"));
                        if(to==null)continue;
                        cv.put("position",to);
                    }else if(table.equals("transition_issues")){
                        Integer first=remap.get(cv.getAsInteger("from_position"));
                        Integer next=remap.get(cv.getAsInteger("to_position"));
                        if(first==null||next==null||next!=first+1)continue;
                        cv.put("from_position",first);cv.put("to_position",next);
                    }else{
                        int first=cv.getAsInteger("first_line"),last=cv.getAsInteger("last_line");
                        Integer mapped=remap.get(first);
                        if(mapped==null)continue;
                        boolean whole=true;
                        for(int i=first;i<last;i++){
                            if(!remap.containsKey(i)||remap.get(i)!=mapped+i-first){whole=false;break;}
                        }
                        if(!whole)continue;
                        cv.put("first_line",mapped);cv.put("last_line",mapped+last-first);
                    }
                    saved.add(cv);
                }
            }
            db.delete(table,"entry_id=?",new String[]{String.valueOf(id)});
            for(ContentValues cv:saved)db.insertWithOnConflict(table,null,cv,SQLiteDatabase.CONFLICT_REPLACE);
        }
    }
}
