package org.biblememory.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class ReviewPerformanceRegressionTest {
    private static final long NOW=86_400_000L;

    private void term(LocalStore store,long id,String stage,int start,int end,long due){
        store.markMastered(id,start,end,100,stage);
        SQLiteDatabase db=store.getWritableDatabase();
        db.execSQL("INSERT INTO review_schedule VALUES(?,?,?,?,0,?,100)",new Object[]{id,stage,start,end,due});
    }

    private long collection(LocalStore store,String title){
        return store.createUnifiedCollection(title);
    }

    private long group(LocalStore store,long id){return store.unifiedCollection(id).groupId;}

    private List<List<Object>> sequence(List<LocalStore.DueItem> items){
        List<List<Object>> result=new ArrayList<>();
        for(LocalStore.DueItem x:items)result.add(Arrays.asList(x.entryId,x.stage,x.start,x.end,x.due));
        return result;
    }

    @Test public void dueCutoffAgreesWithFullProjectionIncludingArchivedTermsAndWriteInvalidation()throws Exception{
        Context context=RuntimeEnvironment.getApplication();
        Bible bible=new Bible();bible.load(context);
        try(LocalStore store=new LocalStore(context,bible);LocalStore writer=new LocalStore(context,bible)){
            assertEquals(0,store.dueCount(NOW));assertTrue(store.dueItems(NOW).isEmpty());
            long removed=collection(store,"Removed"), active=collection(store,"Active"), other=collection(store,"Other");
            long activeGroup=group(store,active),otherGroup=group(store,other);
            long archived=store.add(group(store,removed),"Verse","Бытие 1:2",bible.passage(1,1,2,1,2));
            long passage=store.add(activeGroup,"Passage","Бытие 1:1–3",bible.passage(1,1,1,1,3));
            long custom=store.add(activeGroup,"Custom","Свой текст",Arrays.asList(
                new Bible.Verse("1","Первая строка"),new Bible.Verse("2","Вторая строка")));
            long duplicate=store.add(otherGroup,"Same verse","Бытие 1:2",bible.passage(1,1,2,1,2));
            term(store,archived,"VERSE",0,1,NOW);
            term(store,passage,"VERSE",1,2,NOW+1);
            term(store,duplicate,"VERSE",0,1,NOW+2);
            term(store,passage,"CHAIN",0,2,NOW);
            term(store,custom,"VERSE",0,1,NOW);
            term(store,custom,"VERSE",1,2,NOW+3);
            for(int i=0;i<20;i++){
                long future=store.add(activeGroup,"Future","Свой текст",Collections.singletonList(new Bible.Verse("1","Позже")));
                term(store,future,"VERSE",0,1,NOW+4);
            }
            store.deleteUnifiedCollection(removed);
            for(long cutoff:new long[]{NOW-1,NOW,NOW+4}){
                for(long scope:new long[]{-1L,activeGroup,otherGroup}){
                    assertEquals(sequence(store.reviewSchedule().due(cutoff,scope)),
                        sequence(store.dueItems(cutoff,scope)));
                }
                assertEquals(store.dueItems(cutoff).size(),store.dueCount(cutoff));
            }
            assertEquals(Arrays.asList(Arrays.asList(passage,"CHAIN",0,2,NOW),
                Arrays.asList(passage,"VERSE",1,2,NOW),Arrays.asList(custom,"VERSE",0,1,NOW)),
                sequence(store.dueItems(NOW)));
            assertEquals(duplicate,store.dueItems(NOW,otherGroup).get(0).entryId);
            writer.commitSuccessfulRecall(duplicate,"VERSE",0,1,false,false,NOW);
            assertEquals(2,store.dueCount(NOW));
            assertTrue(store.dueItems(NOW,otherGroup).isEmpty());
            assertEquals(sequence(store.reviewSchedule().due(NOW,-1)),sequence(store.dueItems(NOW)));
        }
    }

    @Test public void plannerKeepsRangePrecedenceAndReadsEachEntryOnce(){
        List<ReviewQueueLogic.Item> input=Arrays.asList(
            new ReviewQueueLogic.Item(1,"CHAPTER",0,6,NOW),
            new ReviewQueueLogic.Item(1,"CHAIN",1,3,NOW),
            new ReviewQueueLogic.Item(1,"SECTION",2,4,NOW),
            new ReviewQueueLogic.Item(1,"VERSE",0,1,NOW),
            new ReviewQueueLogic.Item(1,"VERSE",2,3,NOW),
            new ReviewQueueLogic.Item(1,"VERSE",3,4,NOW),
            new ReviewQueueLogic.Item(1,"VERSE",4,5,NOW),
            new ReviewQueueLogic.Item(1,"VERSE",5,6,NOW),
            new ReviewQueueLogic.Item(2,"VERSE",0,1,NOW));
        Map<Long,Integer> referenceReads=new HashMap<>(),textReads=new HashMap<>();
        List<ReviewQueueLogic.Item> result=ReviewQueueLogic.plan(input,new ReviewQueueLogic.Source(){
            public List<String> references(long id){
                referenceReads.merge(id,1,Integer::sum);
                return Arrays.asList("1:1","1:2","1:3","1:4","1:5","2:1");
            }
            public List<String> texts(long id){
                textReads.merge(id,1,Integer::sum);
                return Collections.nCopies(6,"Тест");
            }
        });
        List<String> summary=new ArrayList<>();
        for(ReviewQueueLogic.Item x:result)summary.add(x.entryId+" "+x.stage+" "+x.start+" "+x.end);
        assertEquals(Arrays.asList("1 VERSE 0 1","1 CHAIN 1 3","1 VERSE_RUN 3 5",
            "1 VERSE 5 6","2 VERSE 0 1"),summary);
        assertEquals(Integer.valueOf(1),referenceReads.get(1L));
        assertEquals(Integer.valueOf(1),referenceReads.get(2L));
        assertEquals(Collections.singletonMap(1L,1),textReads);
    }
}
