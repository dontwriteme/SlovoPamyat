package org.biblememory.app;

import android.content.Context;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class ChapterStatusProbe {
    private static final int SAMPLES=30;

    @Test public void benchmark() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        Bible bible=new Bible();
        bible.load(context);
        try(LocalStore seed=new LocalStore(context,bible)){
            seed(seed,bible,48,1,6);
            measure(context,bible,149,48,1,24);
            seed(seed,bible,19,1,40);
            seed(seed,bible,19,118,118);
            int size=seed.allEntries().stream().mapToInt(e->e.lines().size()).sum();
            if(size<=500)throw new AssertionError(size);
            measure(context,bible,size,48,1,24);
            measure(context,bible,size,19,118,176);
        }
    }

    private void seed(LocalStore store,Bible bible,int book,int first,int last){
        long group=store.newGroup("Benchmark "+book+" "+first);
        android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();
        db.beginTransaction();
        try{
            for(int chapter=first;chapter<=last;chapter++){
                List<Bible.Verse> lines=bible.chapter(book,chapter);
                String ref=Bible.reference(book,chapter,1,chapter,lines.size());
                long id=store.add(group,ref,ref,lines);
                for(int i=0;i<lines.size();i++){
                    db.execSQL("INSERT INTO verse_progress VALUES(?,?,100)",new Object[]{id,i});
                    db.execSQL("INSERT INTO recall_activity VALUES(?,'VERSE',?,?,172800002,1,172800002,2,0,100)",new Object[]{id,i,i+1});
                    db.execSQL("INSERT INTO review_schedule VALUES(?,'VERSE',?,?,2,777600002,100)",new Object[]{id,i,i+1});
                    for(int day=1;day<=3;day++)
                        db.execSQL("INSERT INTO review_attempts(entry_id,stage,first_line,last_line,attempted_at,accuracy,assisted) "+
                            "VALUES(?,'VERSE',?,?,?,?,0)",new Object[]{id,i,i+1,day*86400000L+2,day==2?85:100});
                }
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    private void measure(Context context,Bible bible,int saved,int book,int chapter,int size){
        String name=Bible.NAMES[book-1];
        for(String mode:new String[]{"cold","warm"}){
            long[] nanos=new long[SAMPLES];
            long min=Long.MAX_VALUE,max=0;
            try(LocalStore warm=new LocalStore(context,bible)){
                for(int i=-4;i<SAMPLES;i++){
                    try(LocalStore cold=new LocalStore(context,bible)){
                        LocalStore store=mode.equals("cold")?cold:warm;
                        store.getWritableDatabase();
                        long reads=store.readQueryCount(),start=System.nanoTime();
                        int result=store.statusesForChapter(name,chapter).size();
                        long elapsed=System.nanoTime()-start;
                        if(result!=size)throw new AssertionError(result);
                        if(i>=0){
                            nanos[i]=elapsed;
                            long queries=store.readQueryCount()-reads;
                            min=Math.min(min,queries);max=Math.max(max,queries);
                        }
                    }
                }
            }
            Arrays.sort(nanos);
            System.out.printf(Locale.ROOT,"CHAPTER %d %d:%d %s n=%d reads=%d..%d median=%.3fms p95=%.3fms%n",
                saved,book,chapter,mode,SAMPLES,min,max,
                (nanos[SAMPLES/2-1]+nanos[SAMPLES/2])/2e6,nanos[(int)Math.ceil(.95*SAMPLES)-1]/1e6);
        }
    }
}
