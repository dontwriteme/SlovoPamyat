package org.biblememory.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** The corpus load can outlive an Activity, while its UI delivery cannot. */
final class BibleStartup {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Consumer<Throwable> loaded;
    private CompletableFuture<Void> task;
    private boolean closed;

    BibleStartup(Consumer<Throwable> loaded){this.loaded=loaded;}

    void load(Bible bible,Context application){
        WeakReference<BibleStartup> target=new WeakReference<>(this);
        Handler handler=main;
        task=bible.loadInBackground(application).whenComplete((ignored,failure)->
            handler.post(()->{
                BibleStartup startup=target.get();
                if(startup!=null&&!startup.closed)startup.loaded.accept(failure);
            }));
    }

    void close(){
        closed=true;
        if(task!=null)task.cancel(false);
        main.removeCallbacksAndMessages(null);
    }
}
