package org.biblememory.app;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.function.Consumer;

/** Resource lease for the UI SQLite handle and the collection worker's drain. */
final class ActivityDataLifetime {
    record Open(boolean recoveryNeeded,RuntimeException failure) {}

    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private LocalStore store;
    private boolean ownsData;
    private String generation="";

    ActivityDataLifetime(Context context){this.context=context;}

    Open open(Bible bible){
        store=new LocalStore(context,bible);
        try{
            if(SvetochBackup.pending(store)){
                closeForeground();
                return new Open(true,null);
            }
        }catch(RuntimeException failure){
            closeForeground();
            return new Open(false,failure);
        }
        ownsData=true;
        BackupCoordinator.opened();
        generation=SvetochBackup.generation(context);
        return new Open(false,null);
    }

    LocalStore store(){return store;}
    boolean ownsData(){return ownsData;}
    String generation(){return generation;}

    Bundle acceptState(Bundle saved){
        return saved!=null&&generation.equals(saved.getString("data_generation",""))?saved:null;
    }

    void recoverReset()throws Exception{
        LearningReset.recover(context.getApplicationContext(),store);
    }

    void resetProgress(CollectionIo io,Consumer<Exception> complete){
        Context app=context.getApplicationContext();
        io.execute(()->{
            // The reset owns a separate helper until its work and close have completed.
            Exception failure=null;
            try(LocalStore worker=new LocalStore(app)){
                LearningReset.request(app,worker);
            }catch(Exception error){
                android.util.Log.e("Settings","Unable to reset learning progress",error);
                failure=error;
            }
            Exception outcome=failure;
            main.post(()->complete.accept(outcome));
        });
    }

    void closeCollectionIo(CollectionIo io){
        boolean release=ownsData;
        ownsData=false;
        io.close(()->{if(release)BackupCoordinator.releasedFromWorker();});
    }

    void closeForeground(){
        if(store!=null){store.close();store=null;}
    }
}
