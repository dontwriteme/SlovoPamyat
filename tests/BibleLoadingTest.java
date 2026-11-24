package org.biblememory.app;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class BibleLoadingTest {
    private final Context context=RuntimeEnvironment.getApplication();

    private static final class GatedSource implements Bible.CorpusSource {
        final AtomicInteger opens=new AtomicInteger();
        final CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);

        @Override public java.io.InputStream open(Context context)throws IOException{
            opens.incrementAndGet();
            java.io.InputStream asset=context.getAssets().open("synodal.json");
            return new java.io.FilterInputStream(asset){
                private int remaining=64*1024;
                @Override public int read(byte[] buffer,int offset,int length)throws IOException{
                    if(remaining==0){
                        // JsonReader already consumed the first part of the opening book.
                        entered.countDown();
                        try{
                            if(!release.await(15,TimeUnit.SECONDS))throw new IOException("Timeout at test gate");
                        }catch(InterruptedException error){
                            Thread.currentThread().interrupt();throw new IOException(error);
                        }
                        remaining=-1;
                    }
                    int n=super.read(buffer,offset,remaining>0?Math.min(length,remaining):length);
                    if(n>0&&remaining>0)remaining-=n;
                    return n;
                }
            };
        }
    }

    @Test public void readersReturnBeforePublicationAndConcurrentRequestsParseOnce()throws Exception{
        GatedSource source=new GatedSource();
        Bible bible=new Bible(source);
        ExecutorService readers=Executors.newFixedThreadPool(4);
        CompletableFuture<Void> load=bible.loadInBackground(context);
        try{
            assertTrue(source.entered.await(5,TimeUnit.SECONDS));
            List<Future<?>> requests=new java.util.ArrayList<>();
            for(int i=0;i<8;i++)requests.add(readers.submit(()->{
                assertSame(load,bible.loadInBackground(context));
                assertFalse(bible.isReady());
                assertEquals(0,bible.chapterCount(1));
                assertTrue(bible.chapter(1,1).isEmpty());
                assertThrows(IllegalArgumentException.class,()->bible.passage(1,1,1,1,1));
                assertThrows(Bible.Unavailable.class,()->bible.matchesVerse(1,1,1,new Bible.Verse("1:1","")));
            }));
            for(Future<?> request:requests)request.get(3,TimeUnit.SECONDS);
            assertEquals(1,source.opens.get());
        }finally{
            source.release.countDown();
            readers.shutdownNow();
            load.get(10,TimeUnit.SECONDS);
        }
        bible.load(context);
        assertTrue(bible.isReady());
        assertEquals(1,source.opens.get());
    }

    @Test public void allSavedBooksChaptersAndVersesMatchTheBundledJson()throws Exception{
        Bible bible=new Bible();
        bible.load(context);
        JSONArray books;
        try(java.io.InputStream stream=context.getAssets().open("synodal.json")){
            books=new JSONObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getJSONArray("books");
        }
        assertEquals(66,books.length());
        for(int b=0;b<books.length();b++){
            JSONObject book=books.getJSONObject(b);
            int bookId=book.getInt("bookId");
            JSONArray chapters=book.getJSONArray("chapters");
            assertEquals(chapters.length(),bible.chapterCount(bookId));
            for(int c=0;c<chapters.length();c++){
                JSONObject chapter=chapters.getJSONObject(c);
                int index=chapter.getInt("chapter");
                JSONArray encoded=chapter.getJSONArray("verses");
                List<Bible.Verse> actual=bible.chapter(bookId,index);
                assertEquals(encoded.length(),actual.size());
                java.util.Map<Integer,String> expected=new java.util.TreeMap<>();
                for(int v=0;v<encoded.length();v++){
                    JSONObject verse=encoded.getJSONObject(v);
                    expected.put(verse.getInt("number"),verse.getString("text"));
                }
                int position=0;
                for(java.util.Map.Entry<Integer,String> entry:expected.entrySet()){
                    Bible.Verse verse=actual.get(position++);
                    assertEquals(index+":"+entry.getKey(),verse.ref);
                    assertEquals(entry.getValue(),verse.text);
                }
            }
        }
        bible.chapter(19,118).clear();
        assertEquals(176,bible.chapter(19,118).size());
        assertEquals(109,bible.collectionPassage(40,5,3,7,29).size());
    }

    @Test public void unavailableIdentityRollsBackAndRetryUsesThePublishedText()throws Exception{
        Bible bible=new Bible();
        try(LocalStore store=new LocalStore(context,bible)){
            long group=store.newGroup("Test");
            Bible.Verse verse=new Bible.Verse("1:1","В начале сотворил Бог небо и землю.");
            long id=store.add(group,"Бытие 1:1","Бытие 1:1",List.of(verse));
            assertThrows(Bible.Unavailable.class,()->store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,100));
            for(String table:List.of("review_attempts","review_schedule","recall_activity","verse_progress")){
                try(android.database.Cursor rows=store.getReadableDatabase().rawQuery("SELECT count(*) FROM "+table,null)){
                    assertTrue(rows.moveToFirst());assertEquals(table,0,rows.getInt(0));
                }
            }
            bible.load(context);
            assertTrue(store.confirmReviewVerse(store.entry(id),verse));
            assertFalse(store.confirmReviewVerse(store.entry(id),new Bible.Verse("1:1","Другой текст")));
            store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,100);
            assertTrue(store.isStepMastered(id,"VERSE",0,1));
        }
    }

    @Test public void invalidCorpusCompletesWithErrorAndCannotExposePartialData()throws Exception{
        AtomicInteger opens=new AtomicInteger();
        Bible bible=new Bible(ignored->{
            opens.incrementAndGet();
            return new ByteArrayInputStream("{\"books\":[{\"bookId\":1,\"chapters\":[]}]}".getBytes(StandardCharsets.UTF_8));
        });
        CompletableFuture<Void> load=bible.loadInBackground(context);
        assertThrows(ExecutionException.class,()->load.get(5,TimeUnit.SECONDS));
        assertSame(load,bible.loadInBackground(context));
        assertEquals(1,opens.get());
        assertFalse(bible.isReady());
        assertEquals(0,bible.chapterCount(1));
    }

    private Field field(String name)throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);return field;
    }

    private boolean contains(View view,String text){
        if(view instanceof TextView&&((TextView)view).getText().toString().contains(text))return true;
        if(view instanceof ViewGroup){
            ViewGroup container=(ViewGroup)view;
            for(int i=0;i<container.getChildCount();i++)if(contains(container.getChildAt(i),text))return true;
        }
        return false;
    }

    @Test public void oldScreenIsRetiredWhileAReplacementUsesTheSameInFlightCorpus()throws Exception{
        GatedSource source=new GatedSource();
        Bible bible=new Bible(source);
        ActivityController<MainActivity> first=Robolectric.buildActivity(MainActivity.class);
        ActivityController<MainActivity> next=null;
        boolean destroyed=false;
        MainActivity old=first.get();
        try{
            field("bible").set(old,bible);
            first.setup();
            assertTrue(source.entered.await(5,TimeUnit.SECONDS));
            Method tab=MainActivity.class.getDeclaredMethod("goToMainTab",int.class);tab.setAccessible(true);
            tab.invoke(old,1);
            View loading=(View)field("root").get(old);
            assertTrue(contains(loading,"Загружаем текст Библии"));
            ActivityTestWorkers.destroy(first);
            destroyed=true;
            assertFalse(bible.isReady());
            next=Robolectric.buildActivity(MainActivity.class);
            field("bible").set(next.get(),bible);
            next.setup();
            tab.invoke(next.get(),1);
            source.release.countDown();
            ActivityTestWorkers.awaitStartup(next.get());
            assertTrue(bible.isReady());
            assertEquals(1,source.opens.get());
            assertTrue(contains((View)field("root").get(next.get()),"Бытие"));
            assertSame(loading,field("root").get(old));
        }finally{
            source.release.countDown();
            if(!destroyed)ActivityTestWorkers.destroy(first);
            if(next!=null)ActivityTestWorkers.destroy(next);
        }
    }

    @Test public void processCorpusIsAlsoSharedByReplacementActivityAndDefaultStore()throws Exception{
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
        try{
            ActivityTestWorkers.awaitStartup(controller.get());
            Bible loaded=(Bible)field("bible").get(controller.get());
            assertSame(Bible.shared(),loaded);
            ActivityTestWorkers.recreate(controller);
            assertSame(loaded,field("bible").get(controller.get()));
            try(LocalStore store=new LocalStore(context)){
                long id=store.add(store.newGroup("Independent"),"Бытие 1:1","Бытие 1:1",loaded.chapter(1,1).subList(0,1));
                assertTrue(store.confirmReviewVerse(store.entry(id),loaded.chapter(1,1).get(0)));
            }
        }finally{ActivityTestWorkers.destroy(controller);}
    }
}
