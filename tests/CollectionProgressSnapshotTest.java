package org.biblememory.app;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class CollectionProgressSnapshotTest {
 LocalStore store; android.content.Context context;long group;
 @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("slovo_pamyat.db");store=new LocalStore(context);
  group=store.unifiedCollection(store.createUnifiedCollection("test")).groupId;}
 @After public void close(){store.close();}
 long add(long group,String ref,String... text){List<Bible.Verse> lines=new ArrayList<>();for(int i=0;i<text.length;i++)lines.add(new Bible.Verse("1:"+(i+1),text[i]));return store.add(group,ref,ref,lines);}
 @Test public void sharedProgressCanonicalSelectionOrderAndExternalInvalidation(){
  long a=add(group,"Галатам 1:1–2","один","два");
  long other=store.unifiedCollection(store.createUnifiedCollection("other")).groupId;
  long b=add(other,"Галатам 1:1–2","один","два");
  long custom=add(group,"Свой текст","один","два");
  CollectionProgressSnapshot cold=store.collectionProgress();long q=store.readQueryCount();
  assertSame(cold,store.collectionProgress());assertEquals(2,store.readQueryCount()-q);
  try(LocalStore writer=new LocalStore(context)){writer.markMastered(b,0,1,100,"VERSE");}
  CollectionProgressSnapshot after=store.collectionProgress();assertNotSame(cold,after);
  assertEquals(1,after.masteryCount(a));assertEquals(0,after.masteryCount(custom));
  assertEquals(a,after.canonicalId(b));
  store.getWritableDatabase().execSQL("INSERT INTO lesson_progress VALUES(?,1,3)",new Object[]{b});
  store.getWritableDatabase().execSQL("INSERT INTO study_order VALUES(?,?,0)",new Object[]{a,group});
  store.getWritableDatabase().execSQL("INSERT INTO recall_activity VALUES(?,'VERSE',0,1,86400002,1,86400002,2,0,100)",new Object[]{b});
  store.getWritableDatabase().execSQL("INSERT INTO review_schedule VALUES(?,'VERSE',0,1,2,123456789,100)",new Object[]{b});
  store.getWritableDatabase().execSQL("INSERT INTO review_attempts(entry_id,stage,first_line,last_line,attempted_at,accuracy,assisted) VALUES(?,'VERSE',0,1,111,100,0)",new Object[]{b});
  after=store.collectionProgress();assertEquals(b,after.canonicalId(a));assertEquals(1,after.consolidatedCount(a));
  assertEquals(a,after.entries(group).get(0).id);
  try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT due FROM review_schedule WHERE entry_id=?",new String[]{""+b})){assertTrue(c.moveToFirst());assertEquals(123456789,c.getLong(0));}
  try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM review_attempts",null)){c.moveToFirst();assertEquals(1,c.getInt(0));}
 }
 @Test public void rollbackDoesNotPublishOrPoisonCachedProgress(){
  long a=add(group,"Галатам 1:1","один");CollectionProgressSnapshot before=store.collectionProgress();
  android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
  try{store.markMastered(a,0,1,100,"VERSE");assertEquals(1,store.collectionProgress().masteryCount(a));}
  finally{db.endTransaction();}
  assertEquals(0,store.collectionProgress().masteryCount(a));
  db.beginTransaction();try{store.markMastered(a,0,1,100,"VERSE");assertEquals(1,store.collectionProgress().masteryCount(a));db.setTransactionSuccessful();}finally{db.endTransaction();}
  assertEquals(1,store.collectionProgress().masteryCount(a));
 }
 @Test public void repeatedPositionsDoNotShareWithinSameEntryAndChangedTextDoesNotMatch(){
  List<Bible.Verse> repeated=Arrays.asList(new Bible.Verse("1:1","один"),new Bible.Verse("1:1","один"));
  long a=store.add(group,"a","Галатам 1:1",repeated);store.markMastered(a,0,1,100,"VERSE");
  assertEquals(1,store.masteryCount(a));assertFalse(store.isStepMastered(a,"VERSE",1,2));
  long b=add(group,"Галатам 1:1","другой текст");assertEquals(0,store.masteryCount(b));
  store.getWritableDatabase().execSQL("UPDATE entries SET lines=? WHERE id=?",new Object[]{"[{\"ref\":\"1:1\",\"text\":\"один\"}]",b});
  assertEquals(1,store.masteryCount(b));
 }
 @Test public void syncIsIdempotentAndRepairsRemovedMembership()throws Exception{
  Bible bible=new Bible();bible.load(context);
  long uid=store.createUnifiedCollection("sync");LocalStore.UnifiedCollection u=store.unifiedCollection(uid);
  long a=store.add(u.groupId,"Галатам","Галатам 1:1–2",bible.passage(48,1,1,1,2));
  store.markMastered(a,0,1,100,"VERSE");
  store.syncUnifiedCollection(uid,bible);
  assertEquals(2,store.flashcards(u.deckId).size());assertEquals(1,store.entries(u.groupId).size());
  long reads=store.readQueryCount();store.syncUnifiedCollection(uid,bible);
  assertEquals(5,store.readQueryCount()-reads);assertEquals(1,store.masteryCount(a));
  long card=store.flashcards(u.deckId).get(0).id;
  store.getWritableDatabase().execSQL("DELETE FROM card_deck_members WHERE deck_id=? AND card_id=?",new Object[]{u.deckId,card});
  store.syncUnifiedCollection(uid,bible);
  assertEquals(2,store.flashcards(u.deckId).size());assertEquals(1,store.masteryCount(a));
 }


 @Test public void collectionCountDeduplicatesBibleCoordinatesButCountsEveryCustomLine(){
  add(group,"Галатам 1:1–2","один","два");
  store.add(group,"overlap","Галатам 1:2–3",Arrays.asList(
      new Bible.Verse("1:2","два"),new Bible.Verse("1:3","три")));
  add(group,"Иакова 1:1","один");
  add(group,"Свой текст","строка","строка");
  assertEquals(6,store.collectionProgress().uniqueVerseCount(group));
  assertEquals("21 стих",MainActivity.countedRussian(21,"стих","стиха","стихов"));
  assertEquals("111 стихов",MainActivity.countedRussian(111,"стих","стиха","стихов"));
  assertEquals("22 отрывка",MainActivity.countedRussian(22,"отрывок","отрывка","отрывков"));
  assertEquals("14 отрывков",MainActivity.countedRussian(14,"отрывок","отрывка","отрывков"));
 }
}
