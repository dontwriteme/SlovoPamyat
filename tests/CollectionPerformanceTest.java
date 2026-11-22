package org.biblememory.app;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class CollectionPerformanceTest {
 LocalStore store; Bible bible;
 @Before public void setup()throws Exception{
  android.content.Context c=RuntimeEnvironment.getApplication();c.deleteDatabase("slovo_pamyat.db");
  store=new LocalStore(c);bible=new Bible();bible.load(c);
 }
 @After public void close(){store.close();}
 void measure(String name,Runnable action){long q=store.readQueryCount(),t=System.nanoTime();action.run();
  System.out.println("PROFILE "+name+" ms="+((System.nanoTime()-t)/1000000.0)+" queries="+(store.readQueryCount()-q));}
 @Test public void collectionSizes(){
  for(int n:new int[]{1,149,550}){
   long uid=store.createUnifiedCollection("size"+n);LocalStore.UnifiedCollection u=store.unifiedCollection(uid);
   List<Bible.Verse> verses=new ArrayList<>();
   for(int ch=1;ch<=6;ch++)verses.addAll(bible.chapter(48,ch));
   assertEquals(149,verses.size());
   List<Bible.Verse> lines=new ArrayList<>();for(int i=0;i<n;i++)lines.add(verses.get(i%149));
   long id=store.add(u.groupId,"Галатам","Галатам 1:1–6:18",lines);
   long uid2=store.createUnifiedCollection("duplicate"+n);
   long duplicate=store.add(store.unifiedCollection(uid2).groupId,"duplicate","Галатам 1:1–6:18",lines);
   store.markMastered(duplicate,0,1,100,"VERSE");
   for(int pass=0;pass<2;pass++){
    String prefix=n+"/"+pass+"/";
    measure(prefix+"allEntries",()->store.allEntries());
    measure(prefix+"canonical",()->store.canonicalStudyEntryId(id));
    measure(prefix+"mastery",()->store.masteryCount(id));
    measure(prefix+"consolidated",()->store.consolidatedVerseCount(id));
    measure(prefix+"summary",()->{for(LocalStore.Entry e:store.entries(u.groupId))store.masteryCount(store.canonicalStudyEntryId(e.id));});
    measure(prefix+"entryCardData",()->{long source=store.canonicalStudyEntryId(id);store.masteryCount(source);store.consolidatedVerseCount(source);store.masteredLinkedSegments(source);});
    measure(prefix+"batchScreenData",()->{CollectionProgressSnapshot snapshot=store.collectionProgress();
     for(LocalStore.Entry e:snapshot.entries(u.groupId)){long source=snapshot.canonicalId(e.id);snapshot.verseCount(e.id);snapshot.masteryCount(source);snapshot.consolidatedCount(source);snapshot.linkedCount(source);}});
   }
   assertTrue(store.isStepMastered(id,"VERSE",0,1));
   measure(n+"/syncCold",()->store.syncUnifiedCollection(uid,bible));
   measure(n+"/syncWarm",()->store.syncUnifiedCollection(uid,bible));
  }
 }
 @Test public void manySeparateEntries(){
  long uid=store.createUnifiedCollection("550 entries");long group=store.unifiedCollection(uid).groupId;
  for(int i=0;i<550;i++)store.add(group,"verse "+i,"Галатам 1:1",Collections.singletonList(new Bible.Verse("1:1","Павел")));
  measure("550entries/coldBatch",()->assertEquals(550,store.collectionProgress().entries(group).size()));
  measure("550entries/warmBatch",()->assertEquals(550,store.collectionProgress().entries(group).size()));
 }

}
