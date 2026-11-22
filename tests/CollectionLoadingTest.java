package org.biblememory.app;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class CollectionLoadingTest {
 MainActivity activity;ActivityController<MainActivity> controller;LocalStore store;
 Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
 void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
 Object call(String name,Class<?>[] types,Object... args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
 @SuppressWarnings({"unchecked","rawtypes"}) void screen(String name)throws Exception{set("screen",Enum.valueOf((Class)Class.forName("org.biblememory.app.MainActivity$Screen"),name));call("render",new Class<?>[0]);}
 boolean contains(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return true;
  if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(contains(((ViewGroup)v).getChildAt(i),text))return true;return false;}
 void finishLoad()throws Exception{Future<?> f=(Future<?>)field("collectionTask");if(f!=null)f.get(30,TimeUnit.SECONDS);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
 @Before public void setup()throws Exception{
  RuntimeEnvironment.getApplication().deleteDatabase("slovo_pamyat.db");
  controller=Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();activity=controller.get();store=(LocalStore)field("store");
  for(Thread t:Thread.getAllStackTraces().keySet())if(t.getName().equals("BibleLoader"))t.join(30000);
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
 }
 @After public void close(){controller.pause().stop().destroy();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
 @Test public void openingReturningAndStudyStartKeepTheSelectedCollection()throws Exception{
  long uid=store.createUnifiedCollection("Галатам целиком");LocalStore.UnifiedCollection u=store.unifiedCollection(uid);
  Bible bible=(Bible)field("bible");List<Bible.Verse> lines=new ArrayList<>();for(int i=1;i<=6;i++)lines.addAll(bible.chapter(48,i));assertEquals(149,lines.size());
  long entry=store.add(u.groupId,"Галатам целиком","Галатам 1:1–6:18",lines);set("openUnifiedId",uid);
  long start=System.nanoTime();screen("COLLECTION_DETAIL");long shell=System.nanoTime();
  assertTrue(contains((View)field("content"),"Галатам целиком"));finishLoad();
  System.out.println("PROFILE UI cold shell_ms="+(shell-start)/1e6+" complete_ms="+(System.nanoTime()-start)/1e6);
  assertTrue(contains((View)field("content"),"0 из 149 стихов"));
  screen("COLLECTIONS");start=System.nanoTime();screen("COLLECTION_DETAIL");shell=System.nanoTime();finishLoad();
  System.out.println("PROFILE UI warm shell_ms="+(shell-start)/1e6+" complete_ms="+(System.nanoTime()-start)/1e6);
  assertTrue(contains((View)field("content"),"0 из 149 стихов"));
  call("openTraining",new Class<?>[]{long.class},entry);assertEquals("TRAIN",field("screen").toString());
 }
 @Test public void lateResultDoesNotOverwriteDifferentScreen()throws Exception{
  long uid=store.createUnifiedCollection("Stale collection");set("openUnifiedId",uid);screen("COLLECTION_DETAIL");
  screen("COLLECTIONS");((ExecutorService)field("collectionExecutor")).submit(()->{}).get(30,TimeUnit.SECONDS);
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals("COLLECTIONS",field("screen").toString());
  assertFalse(contains((View)field("content"),"Освоено:"));
 }
 @Test public void largeListBuildsInFramesWithoutFurtherDatabaseQueries()throws Exception{
  long uid=store.createUnifiedCollection("Large list");long group=store.unifiedCollection(uid).groupId;
  for(int i=0;i<550;i++)store.add(group,"Entry #"+i,"Галатам 1:1",Collections.singletonList(new Bible.Verse("1:1","Павел")));
  set("openUnifiedId",uid);screen("COLLECTION_DETAIL");finishLoad();
  LocalStore loader=(LocalStore)field("collectionStore");long reads=loader.readQueryCount();
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
  assertFalse((Boolean)field("collectionDetailLoading"));assertTrue(contains((View)field("content"),"Entry #0"));
  assertEquals(reads,loader.readQueryCount());
 }

}
