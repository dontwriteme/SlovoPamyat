package org.biblememory.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.FrameMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Opt-in profiler on an isolated package. No production tracing or fixture data is installed. */
public final class R456Profile extends Instrumentation {
    private Context app;
    private MainActivity activity;
    private Bundle options;
    private HandlerThread frameThread;
    private final List<Long> frames=new ArrayList<>();
    private int droppedSamples;
    private long[] ids;
    private long esther;

    @Override public void onCreate(Bundle args){super.onCreate(args);options=args;start();}

    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            app=getTargetContext();
            require("org.biblememory.app.r456fixture".equals(app.getPackageName()),"Use isolated package");
            String mode=options.getString("mode","ui");
            switch(mode){
                case "sql":profileSql();break;
                case "thumb":profileUi(true);break;
                case "ui":profileUi(false);break;
                default:throw new IllegalArgumentException(mode);
            }
            result.putString("result","PASS "+mode);finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){
            result.putString("failure",android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED,result);
        }finally{if(frameThread!=null)frameThread.quitSafely();}
    }

    private void emit(String line){
        Bundle b=new Bundle();b.putString("metric",line);sendStatus(1,b);
        android.util.Log.i("SvetochProfile",line);
    }

    private static void require(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }
    private static String fmt(String format,Object... values){return String.format(Locale.ROOT,format,values);}
    private static long millis(){return SystemClock.elapsedRealtime();}
    private static double elapsed(long start){return (System.nanoTime()-start)/1e6;}

    private void profileSql()throws Exception{
        Bible bible=new Bible();bible.load(app);
        for(int size:new int[]{149,500,1100}){
            app.deleteDatabase("slovo_pamyat.db");
            try(LocalStore main=new LocalStore(app,bible);LocalStore worker=new LocalStore(app,bible)){
                long[] fixture=seedSql(main,bible,size);
                LocalStore.Entry last=main.entry(fixture[0]);
                require(last!=null,"entry");
                worker.getWritableDatabase();
                long before=main.readQueryCount(),t=System.nanoTime();
                CollectionProgressSnapshot first=main.collectionProgress();
                double coldMain=elapsed(t);long coldQueries=main.readQueryCount()-before;
                before=worker.readQueryCount();t=System.nanoTime();
                CollectionProgressSnapshot second=worker.collectionProgress();
                double coldWorker=elapsed(t);long workerQueries=worker.readQueryCount()-before;
                require(first!=second,"independent helper snapshots");
                long[] warm=new long[5];before=main.readQueryCount();
                for(int i=0;i<warm.length;i++){
                    t=System.nanoTime();require(first==main.collectionProgress(),"warm cache reused");
                    warm[i]=System.nanoTime()-t;
                }
                Arrays.sort(warm);
                emit(fmt("SQL size=%d cold_main_ms=%.3f reads=%d cold_worker_ms=%.3f reads=%d warm_median_ms=%.3f warm_reads=%d",
                    size,coldMain,coldQueries,coldWorker,workerQueries,warm[2]/1e6,(main.readQueryCount()-before)/warm.length));
                contend(main,worker,fixture[0],size);
            }
        }
    }

    private long[] seedSql(LocalStore store,Bible bible,int count){
        List<Bible.Verse> material=count==149?bible.collectionPassage(48,1,1,6,18):psalmLines(bible,count);
        require(material.size()==count,"seed size");
        return addCollection(store,"SQL "+count,count==149?"Галатам":"Псалтирь",material,24,true);
    }

    private void contend(LocalStore main,LocalStore worker,long entry,int size)throws Exception{
        ExecutorService threads=Executors.newFixedThreadPool(2);
        CountDownLatch transaction=new CountDownLatch(1),reading=new CountDownLatch(1),entered=new CountDownLatch(1);
        long[] times=new long[4],queries=new long[1];
        try{
            Future<?> writer=threads.submit(()->{
                SQLiteDatabase db=worker.getWritableDatabase();
                long writeStart=System.nanoTime();
                db.beginTransactionNonExclusive();
                try{
                    transaction.countDown();
                    require(reading.await(20,TimeUnit.SECONDS),"reader started");
                    // A fixed hold is confined to the contention experiment.
                    SystemClock.sleep(80);
                    long before=worker.readQueryCount(),start=System.nanoTime();
                    worker.commitSuccessfulRecall(entry,"VERSE",0,1,false,true,123456L);
                    times[2]=System.nanoTime()-start;
                    queries[0]=worker.readQueryCount()-before;
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();times[3]=System.nanoTime()-writeStart;entered.countDown();}
                return null;
            });
            require(transaction.await(20,TimeUnit.SECONDS),"writer lock");
            Future<CollectionProgressSnapshot> reader=threads.submit(()->{
                // Match the leading SQLite call on the UI helper. Its transaction acquisition waits for the writer.
                SQLiteDatabase db=main.getReadableDatabase();
                long start=System.nanoTime();
                reading.countDown();
                db.beginTransactionNonExclusive();
                times[0]=System.nanoTime()-start;
                db.setTransactionSuccessful();
                db.endTransaction();
                CollectionProgressSnapshot snapshot=main.collectionProgress();
                times[1]=System.nanoTime()-start;
                return snapshot;
            });
            CollectionProgressSnapshot updated=reader.get(30,TimeUnit.SECONDS);
            writer.get(30,TimeUnit.SECONDS);
            require(updated.mastered(entry,0),"reader saw the committed attempt");
            require(entered.getCount()==0,"writer exited");
            emit(fmt("LOCK size=%d wait_ms=%.3f read_ms=%.3f attempt_ms=%.3f write_hold_ms=%.3f writer_reads=%d",
                size,times[0]/1e6,times[1]/1e6,times[2]/1e6,times[3]/1e6,queries[0]));
        }finally{threads.shutdownNow();}
    }

    private List<Bible.Verse> psalmLines(Bible bible,int count){
        List<Bible.Verse> lines=new ArrayList<>();
        for(int chapter=1;lines.size()<count;chapter++)lines.addAll(bible.chapter(19,chapter));
        return new ArrayList<>(lines.subList(0,count));
    }

    private long[] addCollection(LocalStore store,String title,String book,List<Bible.Verse> verses,int slice,boolean fill){
        long id=store.createUnifiedCollection(title),group=store.unifiedCollection(id).groupId,first=-1;
        SQLiteDatabase db=store.getWritableDatabase();
        db.beginTransactionNonExclusive();
        try{
            for(int i=0;i<verses.size();i+=slice){
                List<Bible.Verse> part=verses.subList(i,Math.min(i+slice,verses.size()));
                String label=book+" "+part.get(0).ref;
                long entry=store.add(group,label,label,part);
                if(first<0)first=entry;
                if(fill)for(int j=0;j<part.size();j++){
                    db.execSQL("INSERT INTO lesson_progress VALUES(?,?,3)",new Object[]{entry,j});
                    if((i+j)%3==1)db.execSQL("INSERT INTO verse_progress VALUES(?,?,100)",new Object[]{entry,j});
                }
            }
            db.setTransactionSuccessful();return new long[]{first,id};
        }finally{db.endTransaction();}
    }

    private void profileUi(boolean thumbsOnly)throws Exception{
        app.deleteDatabase("slovo_pamyat.db");
        long start=System.nanoTime();
        activity=(MainActivity)startActivitySync(new Intent(app,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        emit(fmt("UI launch_return_ms=%.3f",elapsed(start)));
        waitUntil("Bible ready",()->Bible.shared().isReady());
        emit(fmt("UI launch_to_bible_ready_ms=%.3f",elapsed(start)));
        ((ExecutorService)field("collectionExecutor")).submit(()->{}).get(90,TimeUnit.SECONDS);
        waitForIdleSync();

        ids=new long[3];
        Bible bible=Bible.shared();
        try(LocalStore fixture=new LocalStore(app)){
            ids[0]=addCollection(fixture,"149 стихов","Галатам",bible.collectionPassage(48,1,1,6,18),24,false)[1];
            ids[1]=addCollection(fixture,"500 стихов","Псалтирь",psalmLines(bible,500),24,false)[1];
            ids[2]=addCollection(fixture,"1100 стихов","Псалтирь",psalmLines(bible,1100),24,false)[1];
            for(int i=0;i<3;i++)fixture.createUnifiedCollection("Пейзаж "+i);
            List<Bible.Verse> line=bible.passage(17,8,9,8,9);
            esther=fixture.add(fixture.unifiedCollection(ids[0]).groupId,"Есфирь 8:9","Есфирь 8:9",line);
            fixture.finishExercise(esther,0,1);
        }

        frameThread=new HandlerThread("R456FrameMetrics");frameThread.start();
        Handler handler=new Handler(frameThread.getLooper());
        Window window=activity.getWindow();
        window.addOnFrameMetricsAvailableListener((w,metric,dropped)->{
            long duration=metric.getMetric(FrameMetrics.TOTAL_DURATION);
            synchronized(frames){if(duration>0)frames.add(duration);droppedSamples+=dropped;}
        },handler);
        emit(fmt("UI display_refresh_hz=%.2f density=%d",window.getWindowManager().getDefaultDisplay().getRefreshRate(),
            app.getResources().getDisplayMetrics().densityDpi));

        measure("thumbnails",()->screen("COLLECTIONS"),()->true);
        bitmapAndMemory("thumb_loaded");
        // The same rows and fixtures remain loaded for the three repeated traversals.
        for(int i=0;i<3;i++){
            measure("thumb_reopen_"+i,()->{screen("HOME");screen("COLLECTIONS");},()->true);
        }
        bitmapAndMemory("thumb_repeat");
        if(thumbsOnly)return;

        measure("tab_home",()->call("goToMainTab",new Class<?>[]{int.class},0),()->"HOME".equals(field("screen").toString())&&!(Boolean)field("tabSwitchInProgress"));
        measure("tab_bible",()->call("goToMainTab",new Class<?>[]{int.class},1),()->"BOOKS".equals(field("screen").toString())&&!(Boolean)field("tabSwitchInProgress"));
        measure("psalm118",()->{set("selectedBook",19);set("selectedChapter",118);screen("CHAPTER");},()->true);
        for(int i=0;i<ids.length;i++){
            long id=ids[i];
            measure("collection_"+new int[]{149,500,1100}[i],()->{set("openUnifiedId",id);screen("COLLECTION_DETAIL");},
                ()->!(Boolean)field("collectionDetailLoading"));
        }
        bitmapAndMemory("large_collection");
        measure("esther_build",()->call("openTraining",new Class<?>[]{long.class},esther),
            ()->number("lessonBuildRequired")>0);
        measure("esther_tile",this::pickNextTile,()->true);
        main(()->{
            while(((LessonAssemblyState)field("lessonAssembly")).picked()<number("lessonBuildRequired"))pickNextTile();
        });
        measure("esther_next",()->((View)field("lessonBuildAction")).performClick(),()->true);
        measure("answer_review",()->{
            call("openReview",new Class<?>[]{LocalStore.DueItem.class},
                new LocalStore.DueItem(esther,"VERSE",0,1,0));
            EditText edit=(EditText)find((View)field("root"),v->v instanceof EditText);
            require(edit!=null,"answer editor");
            edit.setText("Ошибочный ответ");
            View submit=find((View)field("root"),v->v instanceof TextView&&"Проверить".contentEquals(((TextView)v).getText()));
            require(submit!=null,"check button");submit.performClick();
        },()->field("answerReview")!=null);
        for(int i=0;i<3;i++){
            measure("detail_cycle_"+i,()->{set("openUnifiedId",ids[0]);screen("COLLECTION_DETAIL");},
                ()->!(Boolean)field("collectionDetailLoading"));
            main(()->activity.onBackPressed());settleFrames();
        }
        bitmapAndMemory("after_cycles");
    }

    private void pickNextTile()throws Exception{
        int position=((LessonAssemblyState)field("lessonAssembly")).picked();
        View slot=((List<View>)field("lessonBuildSlots")).get(position);
        Field answer=slot.getClass().getDeclaredField("token");answer.setAccessible(true);
        String key=WordOrderLogic.comparable((String)answer.get(slot));
        for(Object item:(List<?>)field("lessonBuildTiles")){
            TextView tile=(TextView)item;
            if(tile.getVisibility()==View.VISIBLE&&key.equals(WordOrderLogic.comparable(tile.getText().toString()))){
                tile.performClick();
                require(((LessonAssemblyState)field("lessonAssembly")).picked()==position+1,"tile accepted");
                return;
            }
        }
        throw new AssertionError("Matching tile absent");
    }

    private void bitmapAndMemory(String label)throws Exception{
        Set<Bitmap> bitmaps=Collections.newSetFromMap(new IdentityHashMap<>());
        int[] images={0};
        main(()->find((View)field("root"),view->{
            if(view instanceof ImageView&&((ImageView)view).getDrawable() instanceof BitmapDrawable){
                images[0]++;bitmaps.add(((BitmapDrawable)((ImageView)view).getDrawable()).getBitmap());
            }
            return false;
        }));
        long bytes=0;
        List<String> dimensions=new ArrayList<>();
        for(Bitmap bitmap:bitmaps){
            bytes+=bitmap.getAllocationByteCount();
            dimensions.add(bitmap.getWidth()+"x"+bitmap.getHeight());
        }
        Collections.sort(dimensions);
        Debug.MemoryInfo info=new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        Runtime runtime=Runtime.getRuntime();
        emit(fmt("MEM %s java_used_kib=%d pss_kib=%d native_kib=%d image_views=%d unique_bitmaps=%d bitmap_bytes=%d dimensions=%s",
            label,(runtime.totalMemory()-runtime.freeMemory())/1024,info.getTotalPss(),Debug.getNativeHeapAllocatedSize()/1024,
            images[0],bitmaps.size(),bytes,dimensions));
    }

    private View find(View root,Predicate<View> predicate){
        if(predicate.test(root))return root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                View found=find(group.getChildAt(i),predicate);if(found!=null)return found;
            }
        }
        return null;
    }

    private interface Action{void run()throws Exception;}
    private interface Condition{boolean ready()throws Exception;}

    private void main(Action action)throws Exception{
        Throwable[] failure={null};
        runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});
        if(failure[0]!=null)throw new AssertionError(failure[0]);
    }

    private void waitUntil(String label,Condition condition)throws Exception{
        long until=millis()+90_000;
        while(millis()<until){
            boolean[] ready={false};
            main(()->ready[0]=condition.ready());
            if(ready[0])return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Timed out: "+label);
    }

    private void settleFrames()throws Exception{
        CountDownLatch drawn=new CountDownLatch(1);
        main(()->Choreographer.getInstance().postFrameCallback(first->
            Choreographer.getInstance().postFrameCallback(next->drawn.countDown())));
        require(drawn.await(45,TimeUnit.SECONDS),"frame callbacks");
        // Flush delivery of metrics sent before the second vsync.
        SystemClock.sleep(60);
    }

    private void measure(String label,Action action,Condition ready)throws Exception{
        settleFrames();
        synchronized(frames){frames.clear();droppedSamples=0;}
        long start=System.nanoTime();
        final double[] synchronous={0};
        main(()->{long t=System.nanoTime();action.run();synchronous[0]=elapsed(t);});
        waitUntil(label,ready);
        double readyMs=elapsed(start);
        settleFrames();
        synchronized(frames){
            Collections.sort(frames);
            double p50=frames.isEmpty()?0:frames.get(frames.size()/2)/1e6;
            double p95=frames.isEmpty()?0:frames.get((int)Math.ceil(frames.size()*.95)-1)/1e6;
            long slow=frames.stream().filter(n->n>16_666_667L).count();
            emit(fmt("UI %s main_ms=%.3f ready_ms=%.3f frames=%d p50_ms=%.3f p95_ms=%.3f over_16_7ms=%d lost_callbacks=%d",
                label,synchronous[0],readyMs,frames.size(),p50,p95,slow,droppedSamples));
        }
    }

    private Field declared(String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private Object field(String name)throws Exception{return declared(name).get(activity);}
    private int number(String name)throws Exception{return declared(name).getInt(activity);}
    private void set(String name,Object value)throws Exception{declared(name).set(activity,value);}
    private void call(String name,Class<?>[] types,Object... args)throws Exception{
        Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(activity,args);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private void screen(String value)throws Exception{
        Field f=declared("screen");f.set(activity,Enum.valueOf((Class)f.getType(),value));call("render",new Class<?>[0]);
    }
}
