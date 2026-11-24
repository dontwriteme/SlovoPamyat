package org.biblememory.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.io.File;
import java.util.*;
import org.biblememory.app.LocalStore.Entry;
import org.biblememory.app.LocalStore.VerseStatus;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class ChapterStatusTest {
    private static final long DAY=86_400_000L;
    private Context context;
    private LocalStore store;
    private final Bible bible=new Bible();

    @Before public void setup() throws Exception {
        context=RuntimeEnvironment.getApplication();bible.load(context);
        store=new LocalStore(context,bible);
    }
    @After public void close(){store.close();}

    private long add(long group,int chapter,int from,int to){
        String ref=Bible.reference(48,chapter,from,chapter,to);
        return store.add(group,ref,ref,bible.passage(48,chapter,from,chapter,to));
    }

    private void attempt(LocalStore writer,long entry,int pos,long at,int score,boolean assisted){
        writer.getWritableDatabase().execSQL(
            "INSERT INTO review_attempts(entry_id,stage,first_line,last_line,attempted_at,accuracy,assisted) "+
                "VALUES(?,'VERSE',?,?,?,?,?)",new Object[]{entry,pos,pos+1,at,score,assisted?1:0});
    }

    private Map<Integer,VerseStatus> compare(String book,int chapter){
        Map<Integer,VerseStatus> expected=legacy(store,book,chapter);
        Map<Integer,VerseStatus> actual=store.statusesForChapter(book,chapter);
        assertEquals(expected.keySet(),actual.keySet());
        for(Integer number:expected.keySet()){
            VerseStatus a=expected.get(number),b=actual.get(number);
            assertEquals("verse "+number,Arrays.asList(a.entryId,a.position,a.state,a.rank,
                a.repetitions,a.successful,a.last,a.nextDue),Arrays.asList(b.entryId,b.position,b.state,
                b.rank,b.repetitions,b.successful,b.last,b.nextDue));
        }
        return actual;
    }

    @Test public void preservesRankSelectionAndDaysAcrossOverlappingAndArchivedEntries(){
        long collection=store.createUnifiedCollection("First");
        long first=add(store.unifiedCollection(collection).groupId,1,1,5);
        long other=store.newGroup("Second");
        long newer=add(other,1,3,6);
        store.finishExercise(first,1,2);
        store.markMastered(first,2,3,100,"VERSE");
        store.markMastered(first,3,4,100,"VERSE");
        store.recordRecall(first,"VERSE",3,4,100,false,DAY+123);
        store.recordRecall(first,"VERSE",3,4,100,false,3*DAY+123);
        attempt(store,first,3,2*DAY+122,100,false); // earlier than the 24h threshold
        attempt(store,first,3,2*DAY+123,50,false);
        attempt(store,first,3,2*DAY+124,100,false);
        attempt(store,first,3,4*DAY+124,50,false);
        attempt(store,first,3,5*DAY+124,100,true);
        attempt(store,first,4,4*DAY,100,false); // no first success
        store.deleteUnifiedCollection(collection);
        Map<Integer,VerseStatus> statuses=compare("Галатам",1);
        assertEquals(0,statuses.get(1).rank);
        assertEquals(1,statuses.get(2).rank);
        assertEquals(2,statuses.get(3).rank);
        assertEquals(newer,statuses.get(3).entryId); // exact tie selects the newest row
        assertEquals(3,statuses.get(4).rank);
        assertEquals(first,statuses.get(4).entryId); // history breaks the rank tie
        assertEquals(2,statuses.get(4).repetitions);
        assertEquals(1,statuses.get(4).successful);
        assertEquals(3*DAY+123,statuses.get(4).last);
        assertEquals(0,statuses.get(5).repetitions);
        assertTrue(compare("Галатам",2).isEmpty());
    }

    @Test public void preservesLegacyDisplayedDeadlineWhenAnotherCopyOwnsTheEffectiveTerm()throws Exception{
        long a=add(store.newGroup("First"),1,2,2);
        long b=add(store.newGroup("Second"),1,2,2);
        store.commitSuccessfulRecall(a,"VERSE",0,1,false,true,DAY);
        assertEquals(1,store.dueCount(2*DAY));
        VerseStatus before=compare("Галатам",1).get(2);
        assertEquals(b,before.entryId);
        assertEquals(0,before.nextDue);
        try(LocalStore writer=new LocalStore(context,bible)){
            writer.commitSuccessfulRecall(b,"VERSE",0,1,false,true,2*DAY);
            writer.finishExercise(addWith(writer,1,3),0,2);
        }
        Map<Integer,VerseStatus> after=store.statusesForChapter("Галатам",1);
        assertEquals(1,after.get(3).rank);
        assertEquals(5*DAY,after.get(2).nextDue);
        assertEquals(2*DAY,after.get(2).last);
        compare("Галатам",1);

        File archive=File.createTempFile("chapter-progress",".svbackup",context.getCacheDir());
        Uri uri=Uri.fromFile(archive);
        SvetochBackup.exportTo(context,store,uri);
        store.resetLearningProgress();
        assertEquals(0,store.statusesForChapter("Галатам",1).get(2).rank);
        try(LocalStore writer=new LocalStore(context,bible)){
            SvetochBackup.restoreFrom(context,writer,uri);
        }
        assertEquals(2,store.statusesForChapter("Галатам",1).get(2).rank);
        assertEquals(5*DAY,compare("Галатам",1).get(2).nextDue);
    }

    private long addWith(LocalStore writer,int chapter,int verse){
        String ref=Bible.reference(48,chapter,verse,chapter,verse);
        return writer.add(writer.newGroup("External"),ref,ref,bible.passage(48,chapter,verse,chapter,verse));
    }

    @Test public void batchesAcrossOlderSqliteVariableLimitWithoutChangingTieOrder(){
        List<Bible.Verse> lines=bible.chapter(19,118);
        String ref="Псалтирь 118:1–176";
        long group=store.newGroup("Duplicates");
        store.add(group,ref,ref,lines);
        long newest=store.add(group,ref,ref,lines);
        long reads=store.readQueryCount();
        Map<Integer,VerseStatus> cold=store.statusesForChapter("Псалтирь",118);
        assertTrue(store.readQueryCount()-reads<=10);
        assertEquals(176,cold.size());
        assertEquals(newest,cold.get(176).entryId);
        reads=store.readQueryCount();
        store.statusesForChapter("Псалтирь",118);
        assertEquals(4,store.readQueryCount()-reads);
        compare("Псалтирь",118);
    }

    // Frozen R3 reader for comparison of every field against the same SQLite fixture.
    private Map<Integer,VerseStatus> legacy(LocalStore store,String bookName,int chapter) {
        Map<Integer,VerseStatus> out=new HashMap<>();
        String match=chapter+":";
        for(Entry e:store.allEntries()) {
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
                int completed=store.exerciseDone(e.id,i);
                boolean mastered=store.isStepMastered(e.id,"VERSE",i,i+1)||completed>=5;
                boolean consolidated=store.consolidated(e.id,"VERSE",i,i+1);
                candidate.rank=consolidated?3:mastered?2:completed>0?1:0;
                candidate.state=candidate.rank==3?"Выучен":candidate.rank==2?
                    "Первоначально освоен":candidate.rank==1?"В процессе":"Не изучен";
                String[] args={String.valueOf(e.id),String.valueOf(i),String.valueOf(i+1)};
                long firstSuccess=0;
                try(Cursor c=store.readQuery(
                    "SELECT first_success,last_success FROM recall_activity WHERE entry_id=? AND stage='VERSE' AND first_line=? AND last_line=?",args)){
                    if(c.moveToFirst()){firstSuccess=c.getLong(0);candidate.last=c.getLong(1);}
                }
                if(firstSuccess>0){
                    try(Cursor c=store.readQuery(
                        "SELECT COUNT(DISTINCT attempted_at / 86400000) AS total,"+
                        "COUNT(DISTINCT CASE WHEN accuracy=100 THEN attempted_at / 86400000 END) AS good "+
                        "FROM review_attempts WHERE entry_id=? AND stage='VERSE' AND first_line=? AND last_line=? "+
                        "AND assisted=0 AND attempted_at>=?",new String[]{args[0],args[1],args[2],String.valueOf(firstSuccess+86400000L)})){
                        if(c.moveToFirst()){candidate.repetitions=c.getInt(0);candidate.successful=c.getInt(1);}
                    }
                }
                try(Cursor c=store.readQuery(
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
}
