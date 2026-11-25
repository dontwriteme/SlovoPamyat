package org.biblememory.app;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ScrollView;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A read and its frame batches share a generation. Only the visible request may publish. */
final class CollectionDetailLoader {
    record Detail(CollectionProgressSnapshot snapshot,
                  SortedMap<Integer,SortedMap<Integer,Long>> books,List<LocalStore.Entry> entries) {}

    private final CollectionIo worker;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Future<?> task;
    private long generation;
    private boolean loading;

    CollectionDetailLoader(CollectionIo worker){this.worker=worker;}

    boolean retire(){
        boolean wasLoading=loading;
        loading=false;
        generation++;
        if(task!=null){task.cancel(false);task=null;}
        return wasLoading;
    }

    void load(long group,Bible bible,View target,BooleanSupplier active,int restoreScroll,
              Consumer<Detail> header,BiConsumer<LocalStore.Entry,CollectionProgressSnapshot> row,
              Supplier<ScrollView> scroller,Runnable failed){
        long request=generation;
        loading=true;
        BooleanSupplier current=()->request==generation&&active.getAsBoolean();
        task=worker.submit(store->{
            try{
                CollectionProgressSnapshot snapshot=store.collectionProgress();
                SortedMap<Integer,SortedMap<Integer,Long>> books=store.collectionBooks(group);
                List<LocalStore.Entry> entries=snapshot.visibleCollectionEntries(group,books,bible);
                if(Thread.currentThread().isInterrupted())return;
                main.post(()->{
                    if(!current.getAsBoolean())return;
                    Detail detail=new Detail(snapshot,books,entries);
                    header.accept(detail);
                    append(detail,0,target,current,restoreScroll,row,scroller);
                });
            }catch(RuntimeException error){
                android.util.Log.e("CollectionProgress","Unable to read collection progress",error);
                main.post(()->{
                    if(!current.getAsBoolean())return;
                    loading=false;
                    failed.run();
                });
            }
        });
    }

    private void append(Detail detail,int from,View target,BooleanSupplier current,int restoreScroll,
                        BiConsumer<LocalStore.Entry,CollectionProgressSnapshot> row,Supplier<ScrollView> scroller){
        if(!current.getAsBoolean())return;
        int end=Math.min(detail.entries().size(),from+12);
        for(int i=from;i<end;i++)row.accept(detail.entries().get(i),detail.snapshot());
        if(end<detail.entries().size()){
            target.postOnAnimation(()->append(detail,end,target,current,restoreScroll,row,scroller));
        }else{
            loading=false;
            ScrollView scroll=scroller.get();
            if(scroll!=null)scroll.post(()->{
                if(current.getAsBoolean()&&scroller.get()==scroll)scroll.scrollTo(0,restoreScroll);
            });
        }
    }

    void close(){retire();main.removeCallbacksAndMessages(null);}
}
