package org.biblememory.app;

import android.database.Cursor;
import java.util.*;

/** Read-only batch of collection progress. Built under one SQLite transaction; no View references. */
public final class CollectionProgressSnapshot {
    private static final class State {
        final LocalStore.Entry entry;
        final List<Bible.Verse> lines;
        final boolean canonical;
        final int book;
        final boolean[] mastered,consolidated;
        final int[] completed;
        int exercises,linked,order=1000000;
        State(LocalStore.Entry e){entry=e;lines=e.lines();book=LocalStore.bibleBookFromReference(e.reference);canonical=book>0;
            mastered=new boolean[lines.size()];consolidated=new boolean[lines.size()];
            completed=new int[lines.size()];}
    }
    private final Map<Long,State> states=new HashMap<>();
    private final Map<List<String>,List<long[]>> locations=new HashMap<>();
    private final Map<List<String>,Set<Long>> masteredOwners=new HashMap<>(),consolidatedOwners=new HashMap<>();
    private final Map<List<String>,Long> canonicalEntries=new HashMap<>();
    private final Map<Long,Integer> mastery=new HashMap<>(),consolidated=new HashMap<>();
    private final Map<Long,List<LocalStore.Entry>> groups=new HashMap<>();
    private final Map<Long,Integer> uniqueVerseCounts=new HashMap<>();
    static boolean isCanonical(String reference){for(String book:Bible.NAMES)if(reference.startsWith(book+" "))return true;return false;}
    private static List<String> key(State state,int position){
        Bible.Verse v=state.lines.get(position);
        return Arrays.asList(Integer.toString(state.book),v.ref,v.text);
    }
    private static List<String> passage(LocalStore.Entry e){return Arrays.asList(e.reference,e.linesJson);}
    static CollectionProgressSnapshot load(LocalStore store){
        CollectionProgressSnapshot result=new CollectionProgressSnapshot();
        for(LocalStore.Entry e:store.allEntries()){
            State s=new State(e);result.states.put(e.id,s);
            result.groups.computeIfAbsent(e.groupId,k->new ArrayList<>()).add(e);
            if(s.canonical)for(int i=0;i<s.lines.size();i++)
                result.locations.computeIfAbsent(key(s,i),k->new ArrayList<>()).add(new long[]{e.id,i});
        }
        try(Cursor c=store.readQuery("SELECT entry_id,position FROM verse_progress",null)){
            while(c.moveToNext()){State s=result.states.get(c.getLong(0));int pos=c.getInt(1);
                if(s!=null&&pos>=0&&pos<s.lines.size()){s.mastered[pos]=true;
                    if(s.canonical)result.masteredOwners.computeIfAbsent(key(s,pos),k->new HashSet<>()).add(s.entry.id);}}
        }
        try(Cursor c=store.readQuery("SELECT entry_id,first_line FROM recall_activity WHERE stage='VERSE' AND last_line=first_line+1 AND first_success>0 AND last_success-first_success>=86400000 AND success_count>=2 AND fail_streak=0",null)){
            while(c.moveToNext()){State s=result.states.get(c.getLong(0));int pos=c.getInt(1);
                if(s!=null&&pos>=0&&pos<s.lines.size()){s.consolidated[pos]=true;
                    if(s.canonical)result.consolidatedOwners.computeIfAbsent(key(s,pos),k->new HashSet<>()).add(s.entry.id);}}
        }
        try(Cursor c=store.readQuery("SELECT entry_id,position,completed FROM lesson_progress",null)){
            while(c.moveToNext()){
                State s=result.states.get(c.getLong(0));
                if(s!=null){
                    int pos=c.getInt(1),done=c.getInt(2);
                    s.exercises+=done;
                    if(pos>=0&&pos<s.completed.length)s.completed[pos]=done;
                }
            }
        }
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
        for(Map.Entry<Long,List<LocalStore.Entry>> group:result.groups.entrySet()){
            Set<String> canonicalVerses=new HashSet<>();
            int customLines=0;
            for(LocalStore.Entry entry:group.getValue()){
                State state=result.states.get(entry.id);
                int book=LocalStore.bibleBookFromReference(entry.reference);
                for(Bible.Verse verse:state.lines){
                    if(book>0&&LocalStore.verseNumbers(verse.ref)!=null)canonicalVerses.add(book+"/"+verse.ref);
                    else customLines++;
                }
            }
            result.uniqueVerseCounts.put(group.getKey(),canonicalVerses.size()+customLines);
        }
        return result;
    }
    private boolean shared(State s,int pos,Map<List<String>,Set<Long>> owners){
        if(!s.canonical)return false;Set<Long> ids=owners.get(key(s,pos));
        return ids!=null&&(!ids.contains(s.entry.id)||ids.size()>1);
    }
    public int masteryCount(long id){return mastery.getOrDefault(id,0);}
    public int consolidatedCount(long id){return consolidated.getOrDefault(id,0);}
    public int linkedCount(long id){State s=states.get(id);return s==null?0:s.linked;}
    public int uniqueVerseCount(long group){return uniqueVerseCounts.getOrDefault(group,0);}
    public int verseCount(long id){State s=states.get(id);return s==null?0:s.lines.size();}
    public boolean mastered(long id,int pos){State s=states.get(id);return s!=null&&pos>=0&&pos<s.lines.size()&&(s.mastered[pos]||shared(s,pos,masteredOwners));}
    /** Includes shared progress in identical verses, without another SQLite query. */
    public int exerciseDone(long id,int position){
        State s=states.get(id);
        if(s==null||position<0||position>=s.completed.length)return 0;
        if(mastered(id,position))return 5;
        int done=s.completed[position];
        if(s.canonical)for(long[] loc:locations.getOrDefault(key(s,position),Collections.emptyList())){
            if(loc[0]==id)continue;
            State owner=states.get(loc[0]);
            if(owner!=null)done=Math.max(done,owner.completed[(int)loc[1]]);
        }
        return done;
    }
    public long canonicalId(long id){State s=states.get(id);return s==null||!s.canonical?id:canonicalEntries.getOrDefault(passage(s.entry),id);}
    public List<LocalStore.Entry> entries(long group){return Collections.unmodifiableList(groups.getOrDefault(group,Collections.emptyList()));}
    public List<Bible.Verse> verses(long id){
        State state=states.get(id);
        return state==null?Collections.emptyList():Collections.unmodifiableList(state.lines);
    }

    public boolean verseConsolidated(long id,int position){
        State state=states.get(id);
        return state!=null&&position>=0&&position<state.lines.size()&&
            (state.consolidated[position]||shared(state,position,consolidatedOwners));
    }

    /** Hide nested content while retaining the original entry IDs and their progress. */
    public List<LocalStore.Entry> visibleCollectionEntries(long group,
            Map<Integer,? extends Map<Integer,Long>> books,Bible bible){
        List<LocalStore.Entry> entries=entries(group);
        Map<Integer,Map<Integer,Long>> completeChapters=new HashMap<>();
        Map<Long,Integer> canonicalBooks=new HashMap<>();
        for(LocalStore.Entry entry:entries){
            State state=states.get(entry.id);
            if(!state.canonical||state.lines.isEmpty())continue;
            int book=LocalStore.bibleBookFromReference(entry.reference);
            canonicalBooks.put(entry.id,book);
            if(books.containsKey(book))continue;
            int[] first=LocalStore.verseNumbers(state.lines.get(0).ref);
            if(first==null||first[1]!=1)continue;
            int length=state.lines.size();
            if(!entry.reference.equals(Bible.reference(book,first[0],1,first[0],length)))continue;
            List<Bible.Verse> chapter=bible.chapter(book,first[0]);
            if(chapter.size()==length&&sameCanonicalChapter(chapter,state.lines))
                completeChapters.computeIfAbsent(book,key->new HashMap<>()).putIfAbsent(first[0],entry.id);
        }
        List<LocalStore.Entry> visible=new ArrayList<>();
        for(LocalStore.Entry entry:entries){
            int book=canonicalBooks.getOrDefault(entry.id,0);
            Map<Integer,Long> chapters=books.containsKey(book)?books.get(book):completeChapters.get(book);
            if(chapters==null){visible.add(entry);continue;}
            boolean covered=true;
            for(Bible.Verse verse:states.get(entry.id).lines){
                int[] coordinate=LocalStore.verseNumbers(verse.ref);
                Long owner=coordinate==null?null:chapters.get(coordinate[0]);
                if(owner==null||(!books.containsKey(book)&&owner==entry.id)||
                    !sameCanonicalVerse(owner,coordinate[1],verse)){
                    covered=false;break;
                }
            }
            if(!covered)visible.add(entry);
        }
        return visible;
    }

    private boolean sameCanonicalVerse(long id,int number,Bible.Verse verse){
        State state=states.get(id);
        if(state==null||number<1||number>state.lines.size())return false;
        Bible.Verse canonical=state.lines.get(number-1);
        return canonical.ref.equals(verse.ref)&&canonical.text.equals(verse.text);
    }

    private static boolean sameCanonicalChapter(List<Bible.Verse> canonical,List<Bible.Verse> saved){
        for(int i=0;i<canonical.size();i++)
            if(!canonical.get(i).ref.equals(saved.get(i).ref)||!canonical.get(i).text.equals(saved.get(i).text))return false;
        return true;
    }

    List<long[]> matches(long id,int pos){
        State s=states.get(id);List<long[]> result=new ArrayList<>();
        if(s==null||!s.canonical||pos<0||pos>=s.lines.size())return result;
        for(long[] loc:locations.getOrDefault(key(s,pos),Collections.emptyList()))
            if(loc[0]!=id)result.add(new long[]{loc[0],loc[1]});
        return result;
    }
}
