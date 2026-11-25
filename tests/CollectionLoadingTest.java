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
  ActivityTestWorkers.awaitStartup(activity);
 }
 private View pickerChapterFooter()throws Exception{
  java.lang.reflect.Field footer=PassagePicker.class.getDeclaredField("pickerChapterFooter");
  footer.setAccessible(true);
  return (View)footer.get(field("passagePicker"));
 }
 @After public void close()throws Exception{if(controller!=null)ActivityTestWorkers.destroy(controller);}
 @Test public void destroyClosesWorkerDatabaseAfterPendingWorkAndIgnoresLateRefresh()throws Exception{
  ExecutorService worker=ActivityTestWorkers.executor(activity,"collectionExecutor");
  LocalStore helper=(LocalStore)field("collectionStore");
  CountDownLatch opened=new CountDownLatch(1), release=new CountDownLatch(1);
  Future<android.database.sqlite.SQLiteDatabase> pending=worker.submit(()->{
   android.database.sqlite.SQLiteDatabase db=helper.getReadableDatabase();
   opened.countDown();
   assertTrue(release.await(30,TimeUnit.SECONDS));
   assertTrue(db.isOpen());
   helper.unifiedCollections();
   new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
    try{call("refreshAfterBibleLoad",new Class<?>[0]);}
    catch(Exception error){throw new AssertionError(error);}
   });
   return db;
  });
  View page=(View)field("root");
  try{
   assertTrue(opened.await(30,TimeUnit.SECONDS));
   controller.pause().stop().destroy();
   controller=null;
  }finally{release.countDown();}
  android.database.sqlite.SQLiteDatabase db=pending.get(30,TimeUnit.SECONDS);
  ActivityTestWorkers.awaitDestroyed(activity);
  assertFalse(db.isOpen());
  assertSame(page,field("root"));
 }
 @Test public void pendingDeletionCleansPersistedSelectionBeforeRetiredWorkerReleasesBackup()throws Exception{
  long uid=store.createUnifiedCollection("To delete");
  LocalStore.UnifiedCollection collection=store.unifiedCollection(uid);
  android.content.SharedPreferences library=activity.getSharedPreferences("unified_library_ui",0);
  android.content.SharedPreferences queue=activity.getSharedPreferences("collection_study_queue",0);
  library.edit().putLong("active",uid).putString("writing_run_"+uid+"_draft","old").commit();
  queue.edit().putLong("group",collection.groupId).putBoolean("active",true).commit();
  CountDownLatch release=new CountDownLatch(1);
  ActivityTestWorkers.executor(activity,"collectionExecutor").submit(()->{
   assertTrue(release.await(30,TimeUnit.SECONDS));return null;
  });
  try{
   call("deleteUnified",new Class<?>[]{LocalStore.UnifiedCollection.class},collection);
   org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
       .getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
   Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
   controller.pause().stop().destroy();
   controller=null;
  }finally{release.countDown();}
  ActivityTestWorkers.awaitDestroyed(activity);
  try(LocalStore observer=new LocalStore(activity.getApplicationContext())){
   assertNull(observer.unifiedCollection(uid));
  }
  assertFalse(queue.contains("group"));assertFalse(library.contains("active"));
  assertFalse(library.contains("writing_run_"+uid+"_draft"));
 }
 @Test public void lateDeletionConfirmationCannotSubmitWorkAfterDestruction()throws Exception{
  long uid=store.createUnifiedCollection("Keep");
  call("deleteUnified",new Class<?>[]{LocalStore.UnifiedCollection.class},store.unifiedCollection(uid));
  org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
      .getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
  controller.pause().stop().destroy();
  controller=null;
  ActivityTestWorkers.awaitDestroyed(activity);
  try(LocalStore observer=new LocalStore(activity.getApplicationContext())){
   assertNotNull(observer.unifiedCollection(uid));
  }
 }
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

 private View findView(View parent,String label,boolean description){
  CharSequence value=description?parent.getContentDescription():parent instanceof TextView?((TextView)parent).getText():null;
  if(value!=null&&label.contentEquals(value))return parent;
  if(parent instanceof ViewGroup)for(int i=0;i<((ViewGroup)parent).getChildCount();i++){
   View found=findView(((ViewGroup)parent).getChildAt(i),label,description);
   if(found!=null)return found;
  }
  return null;
 }

 private View current(String label,boolean description)throws Exception{
  View found=findView((View)field("root"),label,description);
  assertNotNull(label,found);
  return found;
 }

 private void finishMutation()throws Exception{
  ((ExecutorService)field("collectionExecutor")).submit(()->{}).get(30,TimeUnit.SECONDS);
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
 }

 @Test public void bookPickerChapterSelectionAndNestedBookNavigation()throws Exception{
  long uid=store.createUnifiedCollection("Bulk picker");
  long group=store.unifiedCollection(uid).groupId;
  set("openUnifiedId",uid);
  screen("COLLECTION_DETAIL");finishLoad();
  current("＋  Добавить из Библии",false).performClick();

  View plus=current("Добавить книгу Бытие",true);
  plus.performClick();
  assertFalse(plus.isEnabled());
  plus.performClick();
  finishMutation();
  assertSame(plus,current("Убрать книгу Бытие",true));
  current("Бытие",true).performClick();

  View scroller=(View)field("currentScroller");
  current("1",false).performLongClick();
  current("Добавить главу 1",false);
  current("2",false).performClick();
  current("Добавить главы 1–2",false);
  current("3",false).performClick();
  View selectedChapter=current("Глава 2, выделена",true);
  assertSame(selectedChapter,current("2",false));
  selectedChapter.performClick();
  current("Добавить главы (2)",false);
  assertSame(scroller,field("currentScroller"));
  activity.onBackPressed();
  assertEquals("PICKER_CHAPTERS",field("screen").toString());
  assertEquals(View.GONE,((View)pickerChapterFooter()).getVisibility());
  current("1",false).performLongClick();
  current("3",false).performClick();
  current("Добавить главы (2)",false).performClick();
  finishMutation();
  assertEquals(View.GONE,((View)pickerChapterFooter()).getVisibility());

  activity.onBackPressed();activity.onBackPressed();finishLoad();
  View book=current("Бытие",false);
  assertFalse(contains((View)field("content"),"Бытие 1:1–31"));
  View bookCard=(View)book.getParent().getParent().getParent();
  bookCard.performClick();
  assertEquals("COLLECTION_BOOK",field("screen").toString());
  View chapter=current("Глава 1",false);
  ((View)chapter.getParent().getParent().getParent()).performClick();
  assertEquals("COLLECTION_CHAPTER",field("screen").toString());
  current("Бытие 1",false);
  current("1:27 · не изучен",false);
  current("Изучать главу",false);

  activity.onBackPressed();activity.onBackPressed();finishLoad();
  current("＋  Добавить из Библии",false).performClick();
  current("Убрать книгу Бытие",true).performClick();finishMutation();
  current("Добавить книгу Бытие",true);
  assertEquals(2,store.entries(group).size());
 }
}
