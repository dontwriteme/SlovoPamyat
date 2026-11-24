package org.biblememory.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;
import java.util.function.BiConsumer;

/** A transaction-scoped projection of the existing schedule rows. */
final class ReviewSchedule {
    private static final String VERSE="VERSE";
    private static final String RUN="VERSE_RUN";
    private static final Set<String> STAGES=new HashSet<>(Arrays.asList(VERSE,"CHAIN","SECTION","CHAPTER"));
    private record Step(long entry,String stage,int start,int end) {
        Step(LocalStore.DueItem item){this(item.entryId,item.stage,item.start,item.end);}
        Step(long entry,int position){this(entry,VERSE,position,position+1);}
        boolean verse(){return VERSE.equals(stage)&&end==start+1;}
        LocalStore.DueItem item(long due){return new LocalStore.DueItem(entry,stage,start,end,due);}
    }
    private record Term(int level,long due,int score) {}
    private final LocalStore store;
    private final CollectionProgressSnapshot progress;
    private final Map<Step,Term> terms=new LinkedHashMap<>();
    private final Map<Step,List<Step>> verses=new HashMap<>();
    private final Set<Long> loadedEntries=new HashSet<>();
    private final Set<Long> archives;
    private final boolean complete;

    ReviewSchedule(LocalStore store,CollectionProgressSnapshot progress,boolean complete){
        this(store,progress,complete,null);
    }

    ReviewSchedule(LocalStore store,CollectionProgressSnapshot progress,boolean complete,Long dueBy){
        this.store=store;
        this.progress=progress;
        this.complete=complete;
        archives=complete?store.archivedReviewGroups():Collections.emptySet();
        if(complete)loadTerms(null,dueBy);
    }

    private void loadTerms(Long entry,Long dueBy){
        String where=entry!=null?" WHERE entry_id=?":dueBy!=null?" WHERE due<=?":"";
        Long parameter=entry!=null?entry:dueBy;
        try(Cursor c=store.readQuery("SELECT entry_id,stage,first_line,last_line,level,due,last_score "+
                "FROM review_schedule"+where,parameter==null?null:new String[]{parameter.toString()})){
            while(c.moveToNext()){
                terms.put(new Step(c.getLong(0),c.getString(1),c.getInt(2),c.getInt(3)),
                    new Term(c.getInt(4),c.getLong(5),c.getInt(6)));
            }
        }
    }

    List<Bible.Verse> verses(long id){return progress.verses(id);}

    private List<Step> aliases(Step step){
        List<Step> cached=verses.get(step);
        if(cached!=null)return cached;
        if(!step.verse()||!valid(step)||!store.confirmReviewVerse(progress.entry(step.entry),
                progress.verses(step.entry).get(step.start)))return Collections.singletonList(step);
        List<Step> result=new ArrayList<>();
        for(long[] location:progress.reviewLocations(step.entry,step.start))
            result.add(new Step(location[0],(int)location[1]));
        if(result.isEmpty())return Collections.singletonList(step);
        for(Step alias:result)verses.put(alias,result);
        return result;
    }

    private boolean valid(Step step){
        return step.start>=0&&step.end>step.start&&step.end<=progress.verseCount(step.entry);
    }

    private boolean active(Step step,long group){
        LocalStore.Entry entry=progress.entry(step.entry);
        if(entry==null||group>0&&entry.groupId!=group)return false;
        return !archives.contains(entry.groupId);
    }

    private Term effective(List<Step> aliases){
        Term best=null;
        for(Step alias:aliases){
            if(!complete&&loadedEntries.add(alias.entry))loadTerms(alias.entry,null);
            Term candidate=terms.get(alias);
            if(candidate==null)continue;
            if(best==null||candidate.due<best.due||
                candidate.due==best.due&&(candidate.level<best.level||
                    candidate.level==best.level&&candidate.score<best.score))best=candidate;
        }
        return best;
    }

    boolean mastered(long id,String stage,int start,int end){
        return mastered(aliases(new Step(id,stage,start,end)));
    }

    private boolean mastered(List<Step> aliases){
        for(Step alias:aliases)if(progress.locallyMastered(alias.entry,alias.stage,alias.start,alias.end))
            return true;
        return false;
    }

    private boolean available(Term term,long now,boolean resume){
        return term!=null&&(term.due<=now||resume&&term.score<100);
    }

    private int selectDue(long now,long group,BiConsumer<Step,Term> receiver){
        Set<Step> seen=new HashSet<>();
        int count=0;
        for(Map.Entry<Step,Term> row:terms.entrySet()){
            Step step=row.getKey();
            if(row.getValue().due>now||!STAGES.contains(step.stage)||!valid(step)||!seen.add(step))continue;
            List<Step> aliases=aliases(step);
            seen.addAll(aliases);
            Term term=effective(aliases);
            if(!available(term,now,false)||!mastered(aliases))continue;
            // Prefer a longer active passage so adjacent verses can keep forming a VERSE_RUN.
            Step target=null;
            for(Step alias:aliases){
                if(!active(alias,group)||!prefer(alias,target))continue;
                target=alias;
                if(receiver==null)break; // Counting only needs any active representation.
            }
            if(target!=null){
                count++;
                if(receiver!=null)receiver.accept(target,term);
            }
        }
        return count;
    }

    int countDue(long now){return selectDue(now,-1,null);}

    List<LocalStore.DueItem> due(long now,long group){
        List<LocalStore.DueItem> items=new ArrayList<>();
        selectDue(now,group,(target,term)->items.add(target.item(term.due)));
        items.sort(Comparator.comparingLong((LocalStore.DueItem x)->x.due)
            .thenComparingLong(x->x.entryId).thenComparingInt(x->x.start).thenComparing(x->x.stage)
            .thenComparingInt(x->x.end));
        return items;
    }

    private boolean prefer(Step candidate,Step current){
        if(current==null)return true;
        int length=progress.verseCount(candidate.entry), previous=progress.verseCount(current.entry);
        return length>previous||length==previous&&(candidate.entry<current.entry||
            candidate.entry==current.entry&&candidate.start<current.start);
    }

    LocalStore.DueItem resolve(LocalStore.DueItem item,long group,long now,boolean resume){
        Step source=new Step(item);
        if(!valid(source))return null;
        if(!RUN.equals(source.stage)){
            if(!STAGES.contains(source.stage))return null;
            List<Step> aliases=aliases(source);
            Term term=effective(aliases);
            if(!available(term,now,resume)||!mastered(aliases))return null;
            if(active(source,group))return source.item(term.due);
            Step target=null;
            for(Step alias:aliases)if(active(alias,group)&&prefer(alias,target))target=alias;
            return target==null?null:target.item(term.due);
        }
        if(source.end-source.start<2||source.end-source.start>4)return null;
        long earliest=Long.MAX_VALUE;
        for(int pos=source.start;pos<source.end;pos++){
            List<Step> aliases=aliases(new Step(source.entry,pos));
            Term term=effective(aliases);
            if(!available(term,now,resume)||!mastered(aliases))return null;
            earliest=Math.min(earliest,term.due);
        }
        if(active(source,group))return source.item(earliest);
        // The saved draft is valid only if the entire run still exists in one active passage.
        for(Step first:aliases(new Step(source.entry,source.start))){
            Step run=new Step(first.entry,RUN,first.start,first.start+source.end-source.start);
            if(valid(run)&&active(run,group)&&sameRun(source,run))return run.item(earliest);
        }
        return null;
    }

    private boolean sameRun(Step source,Step target){
        for(int i=0;i<source.end-source.start;i++)
            if(!aliases(new Step(source.entry,source.start+i)).contains(new Step(target.entry,target.start+i)))
                return false;
        return true;
    }

    boolean overlaps(LocalStore.DueItem pending,LocalStore.DueItem item){
        if(item.entryId==pending.entryId&&item.start<pending.end&&pending.start<item.end)return true;
        Step candidate=new Step(item);
        if(!candidate.verse()||!(VERSE.equals(pending.stage)||RUN.equals(pending.stage)))return false;
        for(int pos=pending.start;pos<pending.end;pos++)
            if(aliases(new Step(pending.entryId,pos)).contains(candidate))return true;
        return false;
    }

    boolean belongs(long group,LocalStore.DueItem item){
        Step step=new Step(item);
        if(!valid(step))return false;
        for(Step alias:aliases(step))if(active(alias,group))return true;
        return false;
    }

    void update(SQLiteDatabase db,long id,String stage,int start,int end,int accuracy,boolean mastered,long now){
        Step source=new Step(id,stage,start,end);
        List<Step> aliases=aliases(source);
        Term old=effective(aliases);
        int level=old==null||accuracy==100&&!mastered?-1:old.level;
        SpacedReviewLogic.State next=SpacedReviewLogic.update(level,accuracy,false,now);
        write(db,source,aliases,new Term(next.level,next.due,accuracy));
    }

    void rescheduleWeakVerse(SQLiteDatabase db,long id,int position,long now){
        Step source=new Step(id,position);
        List<Step> aliases=aliases(source);
        if(!mastered(aliases))return;
        long due=now+86_400_000L;
        Term old=effective(aliases);
        if(old!=null&&old.due>0&&old.due<due)return;
        write(db,source,aliases,new Term(0,due,0));
    }

    private void write(SQLiteDatabase db,Step source,List<Step> aliases,Term term){
        // Update existing rows and the answered location. History stays on the actual attempt.
        for(Step alias:aliases){
            if(!alias.equals(source)&&!terms.containsKey(alias))continue;
            ContentValues row=new ContentValues();
            row.put("entry_id",alias.entry);row.put("stage",alias.stage);
            row.put("first_line",alias.start);row.put("last_line",alias.end);
            row.put("level",term.level);row.put("due",term.due);row.put("last_score",term.score);
            db.replaceOrThrow("review_schedule",null,row);
            terms.put(alias,term);
        }
    }
}
