package org.biblememory.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Serial collection work owns its SQLite helper until the last queued command completes. */
final class CollectionIo {
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->new Thread(r,"CollectionProgress"));
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Handler refresh=new Handler(Looper.getMainLooper());
    private LocalStore store;

    void open(Context context){store=new LocalStore(context.getApplicationContext());}
    Future<?> submit(Consumer<LocalStore> read){return executor.submit(()->read.accept(store));}
    void execute(Runnable action){executor.execute(action);}
    void postRefresh(Runnable action){refresh.post(action);}

    void mutate(Consumer<LocalStore> write,Consumer<Boolean> callback){
        executor.execute(()->{
            boolean saved=false;
            try{write.accept(store);saved=true;}
            catch(RuntimeException error){android.util.Log.e("CollectionBooks","Unable to save Bible chapters",error);}
            boolean success=saved;
            // A committed mutation can still need persisted cleanup after the UI has left.
            main.post(()->callback.accept(success));
        });
    }

    void afterPending(Runnable callback){
        executor.execute(()->main.post(callback));
    }

    void close(Runnable released){
        refresh.removeCallbacksAndMessages(null);
        executor.execute(()->{
            try{if(store!=null)store.close();}
            finally{released.run();}
        });
        executor.shutdown();
    }
}
