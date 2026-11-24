package org.biblememory.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class ReviewQueueProbe {
    private static final long NOW=86_400_000L;
    private static final int SAMPLES=15;
    private final Map<Long,List<Bible.Verse>> verseData=new HashMap<>();

    @Test public void compare()throws Exception{
        Context context=RuntimeEnvironment.getApplication();
        Bible bible=new Bible();bible.load(context);
        for(int rows:new int[]{100,1000,10000}){
            context.deleteDatabase("slovo_pamyat.db");
            try(LocalStore store=new LocalStore(context,bible)){
                seed(store,bible,rows);
                for(int percentage:new int[]{5,100}){
                    schedule(store,percentage);
                    measure(store,rows,percentage,"count",()->store.dueCount(NOW));
                    measure(store,rows,percentage,"due",()->store.dueItems(NOW));
                    measure(store,rows,percentage,"plan",()->plan(store));
                }
            }
            verseData.clear();
        }
    }

    private void seed(LocalStore store,Bible bible,int total){
        SQLiteDatabase db=store.getWritableDatabase();
        db.beginTransaction();
        try{
            long removed=store.createUnifiedCollection("Archive");
            long archivedGroup=store.unifiedCollection(removed).groupId;
            long currentGroup=store.unifiedCollection(store.createUnifiedCollection("Current")).groupId;
            List<Bible.Verse> lines=bible.chapter(1,1).subList(0,Math.min(total/5,31));
            int inserted=0;
            for(long group:new long[]{archivedGroup,currentGroup}){
                long id=store.add(group,"Genesis","Бытие 1:1",lines);
                verseData.put(id,new ArrayList<>(lines));
                inserted=addRows(db,id,lines.size(),inserted);
            }
            store.deleteUnifiedCollection(removed);
            while(inserted<total){
                int count=Math.min(total-inserted,50);
                List<Bible.Verse> custom=new ArrayList<>();
                for(int i=0;i<count;i++)custom.add(new Bible.Verse(Integer.toString(i+1),"Тестовая строка "+i));
                long id=store.add(currentGroup,"Custom","Свой текст",custom);
                verseData.put(id,custom);
                inserted=addRows(db,id,count,inserted);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    private int addRows(SQLiteDatabase db,long id,int size,int ordinal){
        for(int position=0;position<size;position++){
            db.execSQL("INSERT INTO verse_progress VALUES(?,?,100)",new Object[]{id,position});
            db.execSQL("INSERT INTO review_schedule VALUES(?,'VERSE',?,?,0,?,100)",
                new Object[]{id,position,position+1,ordinal++});
        }
        return ordinal;
    }

    private void schedule(LocalStore store,int percentage){
        store.getWritableDatabase().execSQL("UPDATE review_schedule SET due=CASE "+
            "WHEN (entry_id*71+first_line*31)%100<? THEN ? ELSE ? END",
            new Object[]{percentage,NOW,NOW+1});
    }

    private List<ReviewQueueLogic.Item> plan(LocalStore store){
        List<ReviewQueueLogic.Item> raw=new ArrayList<>();
        for(LocalStore.DueItem item:store.dueItems(NOW))
            raw.add(new ReviewQueueLogic.Item(item.entryId,item.stage,item.start,item.end,item.due));
        return ReviewQueueLogic.plan(raw,new ReviewQueueLogic.Source(){
            public List<String> references(long id){
                List<String> result=new ArrayList<>();
                for(Bible.Verse verse:verseData.get(id))result.add(verse.ref);
                return result;
            }
            public List<String> texts(long id){
                List<String> result=new ArrayList<>();
                for(Bible.Verse verse:verseData.get(id))result.add(verse.text);
                return result;
            }
        });
    }

    private void measure(LocalStore store,int rows,int pct,String action,Supplier<Object> task)throws Exception{
        long[] times=new long[SAMPLES];
        Object result=null;
        long reads=0;
        int eligible;
        try(Cursor c=store.readQuery("SELECT count(*) FROM review_schedule WHERE due<=?",new String[]{Long.toString(NOW)})){
            c.moveToFirst();eligible=c.getInt(0);
        }
        for(int i=-4;i<SAMPLES;i++){
            long before=store.readQueryCount(), start=System.nanoTime();
            result=task.get();
            if(i>=0){times[i]=System.nanoTime()-start;reads=store.readQueryCount()-before;}
        }
        Arrays.sort(times);
        System.out.printf(Locale.ROOT,"REVIEW %d %d %d %s n=%d reads=%d median=%.3f signature=%s%n",
            rows,pct,eligible,action,SAMPLES,reads,times[SAMPLES/2]/1e6,signature(result));
    }

    private String signature(Object result)throws Exception{
        if(result instanceof Integer)return result.toString();
        List<?> items=(List<?>)result;
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(Object raw:items){
            String line;
            if(raw instanceof LocalStore.DueItem){
                LocalStore.DueItem x=(LocalStore.DueItem)raw;
                line=x.entryId+":"+x.stage+":"+x.start+":"+x.end+":"+x.due+"\n";
            }else{
                ReviewQueueLogic.Item x=(ReviewQueueLogic.Item)raw;
                line=x.entryId+":"+x.stage+":"+x.start+":"+x.end+":"+x.due+"\n";
            }
            digest.update(line.getBytes(StandardCharsets.UTF_8));
        }
        return items.size()+"/"+Base64.getEncoder().encodeToString(digest.digest());
    }
}
