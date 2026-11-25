package org.biblememory.app;

import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Main-thread delivery of a pure assessment. The caller owns validation and persistence. */
final class LessonWritingWorker<T> {
    private final ExecutorService executor=Executors.newSingleThreadExecutor(
        work->new Thread(work,"WritingAssessment"));
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BiConsumer<T,WritingAssessment.Result> deliver;
    private final Consumer<RuntimeException> fail;
    private T pending;
    private Future<?> task;

    LessonWritingWorker(BiConsumer<T,WritingAssessment.Result> deliver,Consumer<RuntimeException> fail){
        this.deliver=deliver;this.fail=fail;
    }

    boolean busy(){return pending!=null;}

    void submit(T request,WritingAssessment.Input input){
        if(busy())return;
        pending=request;
        if(!input.requiresWorker()){
            complete(request,WritingAssessment.evaluate(input),null);
            return;
        }
        WeakReference<LessonWritingWorker<T>> target=new WeakReference<>(this);
        Handler handler=main;
        task=executor.submit(()->evaluate(target,handler,request,input));
    }

    private static <T> void evaluate(WeakReference<LessonWritingWorker<T>> target,Handler handler,
                                     T request,WritingAssessment.Input input){
        try{
            WritingAssessment.Result result=WritingAssessment.evaluate(input);
            handler.post(()->notify(target,request,result,null));
        }catch(CancellationException cancelled){
            // The main thread already retired this request.
        }catch(RuntimeException error){
            handler.post(()->notify(target,request,null,error));
        }
    }

    private static <T> void notify(WeakReference<LessonWritingWorker<T>> target,T request,
                                   WritingAssessment.Result result,RuntimeException error){
        LessonWritingWorker<T> worker=target.get();
        if(worker!=null)worker.complete(request,result,error);
    }

    private void complete(T request,WritingAssessment.Result result,RuntimeException error){
        if(pending!=request)return;
        pending=null;task=null;
        if(error==null)deliver.accept(request,result);
        else fail.accept(error);
    }

    void cancel(){
        pending=null;
        if(task!=null){task.cancel(true);task=null;}
        main.removeCallbacksAndMessages(null);
    }

    void close(){cancel();executor.shutdownNow();}
}
