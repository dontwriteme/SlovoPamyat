package org.biblememory.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

/** Isolated Android timing and backup coordination. The timing excludes View rendering. */
public final class R44WritingCheck extends Instrumentation {
    private Bundle options;
    private Context app;

    @Override public void onCreate(Bundle args){super.onCreate(args);options=args;start();}

    @Override public void onStart(){
        Bundle report=new Bundle();
        try{
            app=getTargetContext();
            require("org.biblememory.app.r44fixture".equals(app.getPackageName()),"Isolated package required");
            if("backup".equals(options.getString("mode")))backup();
            else{
                Bible bible=new Bible();bible.load(app);
                measure(report,"short",bible.passage(1,1,1,1,1),0);
                measure(report,"esther",bible.passage(17,8,9,8,9),0);
                measure(report,"sermon",bible.passage(40,5,1,7,29),0);
                measure(report,"paste",bible.passage(40,5,1,7,29),6000);
            }
            report.putString("result","PASS "+options.getString("mode","profile"));
            finish(Activity.RESULT_OK,report);
        }catch(Throwable failure){
            report.putString("failure",android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED,report);
        }
    }

    private void measure(Bundle report,String label,List<Bible.Verse> verses,int extra){
        List<String> texts=new ArrayList<>();
        for(Bible.Verse verse:verses)texts.add(verse.text);
        String expected=String.join(" ",texts);
        StringBuilder actual=new StringBuilder(expected.replaceFirst("\\p{L}+","подмена"));
        for(int i=0;i<extra;i++)actual.append(" слово");
        boolean special=label.equals("sermon")||label.equals("paste");
        WritingAssessment.Input input=new WritingAssessment.Input(expected,actual.toString(),special,true,false,
            texts,Collections.emptyList(),Collections.emptyList());
        int samples=Integer.parseInt(options.getString("samples","7"));
        int warmups=Integer.parseInt(options.getString("warmups","3"));
        require(samples>0&&warmups>=0,"Invalid sample counts");
        long[][] times=new long[2][samples];
        for(int i=-warmups;i<samples;i++){
            int first=Math.floorMod(i,2);
            for(int k=0;k<2;k++){
                int version=(first+k)%2;
                long start=SystemClock.elapsedRealtimeNanos();
                WritingAssessment.Result result=version==0?old(input):WritingAssessment.evaluate(input);
                if(i>=0)times[version][i]=SystemClock.elapsedRealtimeNanos()-start;
                require(result.feedback()!=null,"Expected wrong answer");
            }
        }
        for(long[] sample:times)Arrays.sort(sample);
        report.putString(label,String.format(Locale.ROOT,"n=%d baseline_ms=%.3f current_ms=%.3f",samples,
            times[0][samples/2]/1e6,times[1][samples/2]/1e6));
    }

    private WritingAssessment.Result old(WritingAssessment.Input input){
        String expected=input.expected(),actual=input.actual();
        boolean exact=AcceptedTextLogic.exact(expected,actual,input.special());
        String closest=expected;
        if(!exact&&input.special()){
            int best=-1;
            for(String candidate:AcceptedTextLogic.accepted(expected,true)){
                int score=oldScore(candidate,actual);
                if(score>best){best=score;closest=candidate;}
            }
        }
        int score=exact?100:Math.min(99,oldScore(closest,actual));
        WeakTransitionLogic.WeakSpot weak=!exact&&input.verses().size()>1?WeakTransitionLogic.locate(input.verses(),actual):null;
        return new WritingAssessment.Result(exact,score,closest,weak,
            exact?null:AnswerReviewLogic.compare(expected,actual,input.special()));
    }

    private int oldScore(String expected,String actual){
        List<String> a=ReviewLogic.words(expected),b=ReviewLogic.words(actual);
        int total=Math.max(a.size(),b.size());
        if(total==0)return 100;
        int[] prev=new int[b.size()+1],cur=new int[b.size()+1];
        for(int i=0;i<prev.length;i++)prev[i]=i;
        for(int i=1;i<=a.size();i++){
            cur[0]=i;
            for(int j=1;j<=b.size();j++)cur[j]=Math.min(
                prev[j-1]+(a.get(i-1).equals(b.get(j-1))?0:1),Math.min(prev[j]+1,cur[j-1]+1));
            int[] swap=prev;prev=cur;cur=swap;
        }
        return Math.max(0,100-Math.round(100f*prev[b.size()]/total));
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    private static Field field(String name)throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field;
    }

    private static Object workerField(MainActivity activity,String name)throws Exception{
        Field member=LessonWritingWorker.class.getDeclaredField(name);
        member.setAccessible(true);
        return member.get(field("writingWorker").get(activity));
    }

    private void backup()throws Exception{
        app.deleteDatabase("slovo_pamyat.db");
        Uri uri=Uri.fromFile(new File(app.getCacheDir(),"r44.svbackup"));
        StringBuilder text=new StringBuilder();
        for(int i=0;i<100;i++)text.append("Запомни этот текст. ");
        long entry;
        try(LocalStore store=new LocalStore(app)){
            entry=store.add(store.newGroup("Fixture"),"Long answer","Свой текст",
                Collections.singletonList(new Bible.Verse("1",text.toString())));
            app.getSharedPreferences("study_session",0).edit().clear().putString("review_draft","Из копии").commit();
            SvetochBackup.exportTo(app,store,uri);
        }
        MainActivity activity=(MainActivity)startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CountDownLatch release=new CountDownLatch(1),entered=new CountDownLatch(1);
        ExecutorService worker=(ExecutorService)workerField(activity,"executor");
        worker.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException ignored){Thread.currentThread().interrupt();}});
        require(entered.await(15,TimeUnit.SECONDS),"worker barrier");
        String previous=SvetochBackup.generation(app);
        try{
            runOnMainSync(()->{
                try{
                    LocalStore store=(LocalStore)field("store").get(activity);
                    Method open=MainActivity.class.getDeclaredMethod("openReview",LocalStore.DueItem.class);
                    open.setAccessible(true);
                    open.invoke(activity,new LocalStore.DueItem(entry,"VERSE",0,1,0));
                    ((LessonWritingState)field("lessonWriting").get(activity)).draft=text.toString();
                    Method validate=MainActivity.class.getDeclaredMethod("validateLessonWriting",LocalStore.Entry.class);
                    validate.setAccessible(true);validate.invoke(activity,store.entry(entry));
                    require(workerField(activity,"pending")!=null,"submission queued");
                    BackupCoordinator.request(app,BackupCoordinator.Operation.IMPORT,uri);
                    require(workerField(activity,"pending")==null,"restore retired submission");
                }catch(Exception e){throw new AssertionError(e);}
            });
            long timeout=SystemClock.elapsedRealtime()+45000;
            try(LocalStore store=new LocalStore(app)){
                while(previous.equals(SvetochBackup.generation(app))||SvetochBackup.pending(store)){
                    require(SystemClock.elapsedRealtime()<timeout,"restore timed out");SystemClock.sleep(40);
                }
                require("Из копии".equals(app.getSharedPreferences("study_session",0).getString("review_draft","")),"restored draft");
                try(Cursor rows=store.getReadableDatabase().rawQuery("SELECT count(*) FROM review_attempts",null)){
                    require(rows.moveToFirst()&&rows.getInt(0)==0,"late attempt wrote to restored database");
                }
            }
            require(worker.awaitTermination(15,TimeUnit.SECONDS),"worker shutdown");
        }finally{release.countDown();}
    }
}
