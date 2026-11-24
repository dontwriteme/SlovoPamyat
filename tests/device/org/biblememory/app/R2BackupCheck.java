package org.biblememory.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import org.json.JSONObject;

/** Each kill/verify or rekill invocation is a distinct Android target process. */
public final class R2BackupCheck extends Instrumentation {
    private static final String[] SPACES={"study_session","collection_study_queue","card_sessions",
        "gap_rotation","unified_library_ui","home_ui","home_verse","bible_last_chapter"};
    private Context app;
    private File archive;
    private Bundle args;

    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}

    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            app=getTargetContext();
            check("org.biblememory.app.r2fixture".equals(app.getPackageName()),"Requires isolated application ID");
            archive=new File(app.getCacheDir(),"r2.svbackup");
            String mode=args.getString("mode","");
            String point=args.getString("point","");
            switch(mode){
                case "kill":seedOld();crash(point,false);break;
                case "rekill":crash(point,true);break;
                case "verify":verify("beforeCommit".equals(point));break;
                case "parallel":parallel(false);break;
                case "parallelImport":parallel(true);break;
                case "profile":profile(result);break;
                default:throw new IllegalArgumentException("Missing mode");
            }
            result.putString("result","PASS "+mode+" "+point+" pid="+Process.myPid());
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){
            result.putString("failure",android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED,result);
        }
    }

    private void seedOld()throws Exception{
        app.deleteDatabase("slovo_pamyat.db");
        try(LocalStore store=new LocalStore(app)){store.createUnifiedCollection("Old");}
        for(String name:SPACES)check(app.getSharedPreferences(name,0).edit().clear()
            .putString("marker","old").commit(),"seed "+name);
        try(InputStream input=getContext().getAssets().open("r1-v1-v9.svbackup")){
            Files.copy(input,archive.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void crash(String point,boolean replay)throws Exception{
        try(LocalStore store=new LocalStore(app)){
            SvetochBackup.Checkpoint kill=phase->{
                if(point.equals(phase)){
                    if(archive.exists()&&!archive.delete())throw new IOException("Cannot remove fixture URI");
                    Bundle status=new Bundle();
                    status.putString("checkpoint",point+" pid="+Process.myPid());
                    sendStatus(2,status);
                    Process.killProcess(Process.myPid());
                }
            };
            if(replay)SvetochBackup.recover(app,store,kill);
            else SvetochBackup.restoreFrom(app,store,Uri.fromFile(archive),kill);
            throw new AssertionError("Missed kill point "+point);
        }
    }

    private MainActivity launch(){
        return (MainActivity)startActivitySync(new Intent(app,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static Object field(MainActivity activity,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);
    }

    private void verify(boolean old)throws Exception{
        check(!archive.exists(),"Recovery must not require URI");
        MainActivity activity=launch();
        long deadline=SystemClock.elapsedRealtime()+45000;
        AtomicBoolean ready=new AtomicBoolean();
        do{
            runOnMainSync(()->{
                try{ready.set(field(activity,"root")!=null);}
                catch(Exception e){throw new AssertionError(e);}
            });
            if(!ready.get())SystemClock.sleep(30);
        }while(!ready.get()&&SystemClock.elapsedRealtime()<deadline);
        check(ready.get(),"First data screen failed to open");
        for(String name:SPACES)check((old?"old":"new").equals(app.getSharedPreferences(name,0)
            .getString("marker","")),name);
        try(LocalStore store=new LocalStore(app)){
            check(!SvetochBackup.pending(store),"journal left behind");
            check(store.unifiedCollections().size()==1,"collection count");
            check((old?"Old":"Fixture").equals(store.unifiedCollections().get(0).title),"collection title");
            check(store.isStepMastered(1,"VERSE",0,1)!=old,"mastery");
            check(scalar(store,"SELECT count(*) FROM review_attempts").equals(old?"0":"1"),"attempt count");
            if(!old){
                check("123456789".equals(scalar(store,"SELECT attempted_at FROM review_attempts")),"history");
                check("209856789".equals(scalar(store,"SELECT due FROM review_schedule")),"schedule");
                check("Body".equals(scalar(store,"SELECT content FROM flashcards WHERE id=1")),"card");
            }
            check("ok".equals(scalar(store,"PRAGMA integrity_check")),"integrity");
            try(Cursor rows=store.getReadableDatabase().rawQuery("PRAGMA foreign_key_check",null)){
                check(!rows.moveToFirst(),"foreign keys");
            }
        }
    }

    private static String scalar(LocalStore store,String sql){
        try(Cursor rows=store.getReadableDatabase().rawQuery(sql,null)){
            check(rows.moveToFirst(),sql);return rows.getString(0);
        }
    }

    private void parallel(boolean restore)throws Exception{
        seedOld();
        if(!restore)Files.delete(archive.toPath());
        final long entry;
        try(LocalStore store=new LocalStore(app)){
            entry=store.add(1,"Example","1:1",Collections.singletonList(new Bible.Verse("1:1","Verse")));
        }
        MainActivity activity=launch();
        CountDownLatch allow=new CountDownLatch(1),done=new CountDownLatch(1),started=new CountDownLatch(1);
        Method mutation=MainActivity.class.getDeclaredMethod("runCollectionMutation",Runnable.class,Consumer.class);
        mutation.setAccessible(true);
        runOnMainSync(()->{
            try{
                LocalStore writer=(LocalStore)field(activity,"collectionStore");
                Runnable write=()->{
                    started.countDown();
                    try{check(allow.await(30,TimeUnit.SECONDS),"release");}
                    catch(InterruptedException e){throw new AssertionError(e);}
                    writer.commitSuccessfulRecall(entry,"VERSE",0,1,false,true,1000L);
                };
                Consumer<Boolean> ui=saved->{
                    check(saved,"write failed");
                    app.getSharedPreferences("study_session",0).edit().putString("draft","paired").apply();
                    done.countDown();
                };
                mutation.invoke(activity,write,ui);
            }catch(Exception e){throw new AssertionError(e);}
        });
        check(started.await(45,TimeUnit.SECONDS),"writer did not start");
        String priorGeneration=SvetochBackup.generation(app);
        runOnMainSync(()->BackupCoordinator.request(app,restore?BackupCoordinator.Operation.IMPORT:
            BackupCoordinator.Operation.EXPORT,Uri.fromFile(archive)));
        runOnMainSync(()->{
            check(priorGeneration.equals(SvetochBackup.generation(app)),"restore overtook writer");
            if(!restore)check(!archive.exists(),"export overtook writer");
        });
        allow.countDown();
        check(done.await(30,TimeUnit.SECONDS),"paired UI update lost");
        long until=SystemClock.elapsedRealtime()+45000;
        if(restore){
            try(LocalStore observer=new LocalStore(app)){
                while(priorGeneration.equals(SvetochBackup.generation(app))||SvetochBackup.pending(observer)){
                    check(SystemClock.elapsedRealtime()<until,"import did not finish");SystemClock.sleep(50);
                }
                check("Fixture".equals(observer.unifiedCollections().get(0).title),"stale collection");
                check("123456789".equals(scalar(observer,"SELECT attempted_at FROM review_attempts")),"stale attempt");
                check("1".equals(scalar(observer,"SELECT COUNT(*) FROM review_attempts")),"duplicate attempt");
                check(!app.getSharedPreferences("study_session",0).contains("draft"),"late draft");
            }
            return;
        }
        JSONObject data=null;
        do{
            if(archive.length()>0){
                try(GZIPInputStream in=new GZIPInputStream(new FileInputStream(archive))){
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
                    while((n=in.read(b))!=-1)bytes.write(b,0,n);
                    data=new JSONObject(new JSONObject(bytes.toString("UTF-8")).getString("body"));
                }catch(EOFException incomplete){/* Export is still writing. */}
            }
            if(data==null)SystemClock.sleep(50);
        }while(data==null&&SystemClock.elapsedRealtime()<until);
        check(data!=null,"export did not complete");
        check(data.getJSONObject("tables").getJSONArray("review_attempts").length()==1,"unpaired attempt");
        check("paired".equals(data.getJSONObject("preferences").getJSONObject("study_session")
            .getJSONObject("draft").getString("v")),"unpaired draft");
        check(!data.getJSONObject("tables").has("pending_backup_restore"),"private table exported");
    }

    private interface Task { void run()throws Exception; }
    private void measure(String name,Task task,Bundle result)throws Exception{
        Runtime rt=Runtime.getRuntime();rt.gc();
        long base=rt.totalMemory()-rt.freeMemory();
        long[] peak={base};
        AtomicBoolean sampling=new AtomicBoolean(true);
        Thread sampler=new Thread(()->{
            while(sampling.get()){
                peak[0]=Math.max(peak[0],rt.totalMemory()-rt.freeMemory());SystemClock.sleep(10);
            }
        });
        sampler.start();
        long begin=SystemClock.elapsedRealtime();
        try{task.run();}
        finally{sampling.set(false);sampler.join();}
        result.putString(name,"baseline="+base+" peakSample="+peak[0]+" ms="+(SystemClock.elapsedRealtime()-begin));
    }

    private void profile(Bundle result)throws Exception{
        seedOld();
        try(LocalStore store=new LocalStore(app)){
            char[] payload=new char[32768];Arrays.fill(payload,'я');
            for(int i=0;i<32;i++)store.addCustomFlashcard(1,"Card "+i,new String(payload));
            app.getSharedPreferences("study_session",0).edit().putString("draft",new String(payload)).commit();
            measure("export",()->SvetochBackup.exportTo(app,store,Uri.fromFile(archive)),result);
            measure("import",()->SvetochBackup.restoreFrom(app,store,Uri.fromFile(archive)),result);
            check(store.flashcards(1).size()==32,"card count");
            try(GZIPInputStream input=new GZIPInputStream(new FileInputStream(archive))){
                byte[] chunk=new byte[8192];int n,total=0;
                while((n=input.read(chunk))!=-1)total+=n;
                result.putString("bytes","gzip="+archive.length()+" plain="+total);
            }
        }
    }
}
