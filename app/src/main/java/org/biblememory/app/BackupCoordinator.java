package org.biblememory.app;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Main-looper gate for the single-process app. A data owner is released only after
 * its UI is destroyed and its collection/reset executor has drained and closed. */
final class BackupCoordinator {
    enum Operation { EXPORT, IMPORT, RECOVER }
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    interface Owner {
        void waitForBackup(Operation operation);
        void backupFinished(Operation operation,Exception failure);
    }

    private static final Set<Owner> screens=new LinkedHashSet<>();
    private static int owners;
    private static Job job;

    private BackupCoordinator(){}

    static boolean attach(Owner screen){
        screens.add(screen);
        if(job==null)return true;
        screen.waitForBackup(job.operation);
        return false;
    }

    static void detach(Owner screen){screens.remove(screen);}
    static void opened(){owners++;}

    static void releasedFromWorker(){
        MAIN.post(()->{
            owners--;
            startWhenDrained();
        });
    }

    static void request(Context context,Operation operation,Uri uri){
        if(job!=null)return;
        job=new Job(context.getApplicationContext(),operation,uri);
        for(Owner screen:new ArrayList<>(screens))screen.waitForBackup(operation);
        startWhenDrained();
    }

    private static void startWhenDrained(){
        if(job==null||owners!=0||job.started)return;
        Job next=job;
        next.started=true;
        new Thread(next,"SvetochBackup").start();
    }

    private static final class Job implements Runnable {
        final Context context;
        final Operation operation;
        final Uri uri;
        boolean started;

        Job(Context context,Operation operation,Uri uri){
            this.context=context;this.operation=operation;this.uri=uri;
        }

        @Override public void run(){
            Exception failure=null;
            try(LocalStore store=new LocalStore(context)){
                // A reset requested by an older Activity may have drained with a pending intent.
                if(operation!=Operation.RECOVER)LearningReset.recover(context,store);
                switch(operation){
                    case EXPORT:SvetochBackup.exportTo(context,store,uri);break;
                    case IMPORT:SvetochBackup.restoreFrom(context,store,uri);break;
                    case RECOVER:SvetochBackup.recover(context,store);break;
                }
            }catch(Exception error){
                android.util.Log.e("SvetochBackup","Backup operation failed",error);
                failure=error;
            }
            Exception outcome=failure;
            MAIN.post(()->{
                job=null;
                for(Owner screen:new ArrayList<>(screens))
                    screen.backupFinished(operation,outcome);
            });
        }
    }
}
