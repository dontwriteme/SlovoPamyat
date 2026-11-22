package org.biblememory.app;

import android.database.Cursor;
import java.util.*;

/** Read-only batch of collection progress. Built under one SQLite transaction; no View references. */
public final class CollectionProgressSnapshot {
    private static final class State {
        final LocalStore.Entry entry;
        final List<Bible.Verse> lines;
        final boolean canonical;
        final boolean[] mastered,consolidated;
        int exercises,linked,order=1000000;
        State(LocalStore.Entry e){entry=e;lines=e.lines();canonical=isCanonical(e.reference);
            mastered=new boolean[lines.size()];consolidated=new boolean[lines.size()];}
    }
    private final Map<Long,State> states=new HashMap<>();
    private final Map<List<String>,List<long[]>> locations=new HashMap<>();
    private final Map<List<String>,Set<Long>> masteredOwners=new HashMap<>(),consolidatedOwners=new HashMap<>();
    private final Map<List<String>,Long> canonicalEntries=new HashMap<>();
    private final Map<Long,Integer> mastery=new HashMap<>(),consolidated=new HashMap<>();
    private final Map<Long,List<LocalStore.Entry>> groups=new HashMap<>();
    static boolean isCanonical(String reference){for(String book:Bible.NAMES)if(reference.startsWith(book+" "))return true;return false;}
    private static List<String> key(Bible.Verse v){return Arrays.asList(v.ref,v.text);}
    private static List<String> passage(LocalStore.Entry e){return Arrays.asList(e.reference,e.linesJson);}
    static CollectionProgressSnapshot load(LocalStore store){
        CollectionProgressSnapshot result=new CollectionProgressSnapshot();
        for(LocalStore.Entry e:store.allEntries()){
            State s=new State(e);result.states.put(e.id,s);
            result.groups.computeIfAbsent(e.groupId,k->new ArrayList<>()).add(e);
            if(s.canonical)for(int i=0;i<s.lines.size();i++)
                result.locations.computeIfAbsent(key(s.lines.get(i)),k->new ArrayList<>()).add(new long[]{e.id,i});
        }
        try(Cursor c=store.readQuery("SELECT entry_id,position FROM verse_progress",null)){
            while(c.moveToNext()){State s=result.states.get(c.getLong(0));int pos=c.getInt(1);
                if(s!=null&&pos>=0&&pos<s.lines.size()){s.mastered[pos]=true;
                    if(s.canonical)result.masteredOwners.computeIfAbsent(key(s.lines.get(pos)),k->new HashSet<>()).add(s.entry.id);}}
        }
        try(Cursor c=store.readQuery("SELECT entry_id,first_line FROM recall_activity WHERE stage='VERSE' AND last_line=first_line+1 AND first_success>0 AND last_success-first_success>=86400000 AND success_count>=2 AND fail_streak=0",null)){
            while(c.moveToNext()){State s=result.states.get(c.getLong(0));int pos=c.getInt(1);
                if(s!=null&&pos>=0&&pos<s.lines.size()){s.consolidated[pos]=true;
                    if(s.canonical)result.consolidatedOwners.computeIfAbsent(key(s.lines.get(pos)),k->new HashSet<>()).add(s.entry.id);}}
        }
        try(Cursor c=store.readQuery("SELECT entry_id,SUM(completed) FROM lesson_progress GROUP BY entry_id",null)){
            while(c.moveToNext()){State s=result.states.get(c.getLong(0));if(s!=null)s.exercises=c.getInt(1);}}
        try(Cursor c=store.readQuery("SELECT entry_id,COUNT(*) FROM stage_progress WHERE stage<>'VERSE' GROUP BY entry_id",null)){
            while(c.moveToNext()){State s=result.states.get(c.getLong(0));if(s!=null)s.linked=c.getInt(1);}}
        try(Cursor c=store.readQuery("SELECT entry_id,position FROM study_order",null)){
            while(c.moveToNext()){State s=result.states.get(c.getLong(0));if(s!=null)s.order=c.getInt(1);}}
        for(State s:result.states.values()){
            int learned=0,established=0;
            for(int i=0;i<s.lines.size();i++){
                if(s.mastered[i]||result.shared(s,i,result.masteredOwners))learned++;
                if(s.consolidated[i]||result.shared(s,i,result.consolidatedOwners))established++;
            }
            result.mastery.put(s.entry.id,learned);result.consolidated.put(s.entry.id,established);
        }
        for(State s:result.states.values())if(s.canonical){
            List<String> key=passage(s.entry);Long previous=result.canonicalEntries.get(key);
            int score=result.masteryCount(s.entry.id)*10+s.exercises;
            if(previous==null||score>result.masteryCount(previous)*10+result.states.get(previous).exercises||
                (score==result.masteryCount(previous)*10+result.states.get(previous).exercises&&s.entry.id<previous))
                result.canonicalEntries.put(key,s.entry.id);
        }
        for(List<LocalStore.Entry> group:result.groups.values())group.sort((a,b)->{
            int cmp=Integer.compare(result.states.get(a.id).order,result.states.get(b.id).order);
            return cmp==0?Long.compare(b.id,a.id):cmp;});
        return result;
    }
    private boolean shared(State s,int pos,Map<List<String>,Set<Long>> owners){
        if(!s.canonical)return false;Set<Long> ids=owners.get(key(s.lines.get(pos)));
        return ids!=null&&(!ids.contains(s.entry.id)||ids.size()>1);
    }
    public int masteryCount(long id){return mastery.getOrDefault(id,0);}
    public int consolidatedCount(long id){return consolidated.getOrDefault(id,0);}
    public int linkedCount(long id){State s=states.get(id);return s==null?0:s.linked;}
    public int verseCount(long id){State s=states.get(id);return s==null?0:s.lines.size();}
    public boolean mastered(long id,int pos){State s=states.get(id);return s!=null&&pos>=0&&pos<s.lines.size()&&(s.mastered[pos]||shared(s,pos,masteredOwners));}
    public long canonicalId(long id){State s=states.get(id);return s==null||!s.canonical?id:canonicalEntries.getOrDefault(passage(s.entry),id);}
    public List<LocalStore.Entry> entries(long group){return Collections.unmodifiableList(groups.getOrDefault(group,Collections.emptyList()));}
    List<long[]> matches(long id,int pos){
        State s=states.get(id);List<long[]> result=new ArrayList<>();
        if(s==null||!s.canonical||pos<0||pos>=s.lines.size())return result;
        for(long[] loc:locations.getOrDefault(key(s.lines.get(pos)),Collections.emptyList()))
            if(loc[0]!=id)result.add(new long[]{loc[0],loc[1]});
        return result;
    }
}
