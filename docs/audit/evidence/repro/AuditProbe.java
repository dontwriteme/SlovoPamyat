package org.biblememory.app;

import android.content.Context;
import android.view.*;
import android.widget.TextView;
import android.os.Bundle;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, qualifiers="w411dp-h730dp")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class AuditProbe {
    Context context;
    @Before public void init(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("slovo_pamyat.db");
    }
    @Test public void backupRoundTripAndWidePassage()throws Exception{
        try(LocalStore store=new LocalStore(context)){
            Bible bible=new Bible();bible.load(context);
            long id=store.createUnifiedCollection("Audit backup");
            LocalStore.UnifiedCollection u=store.unifiedCollection(id);
            store.addBibleChapters(u.groupId,65,Collections.emptyList(),true,bible);
            LocalStore.Entry entry=store.entries(u.groupId).get(0);
            store.markMastered(entry.id,0,1,100,"VERSE");
            String backup=store.exportJson().toString();
            int initial=store.allEntries().size();
            store.importJson(backup);
            System.out.println("AUDIT_BACKUP visibleCollections="+store.unifiedCollections().size()
                +" entriesBefore="+initial+" entriesAfter="+store.allEntries().size()
                +" containsBookMetadata="+backup.contains("collection_book_chapters")
                +" containsCardDeckMetadata="+backup.contains("card_decks"));
            store.importJson(backup);
            System.out.println("AUDIT_BACKUP_REPEAT entries="+store.allEntries().size());
            List<Bible.Verse> lines=bible.collectionPassage(1,1,1,25,bible.chapter(1,25).size());
            store.add(u.groupId,"Long","Бытие 1–25",lines);
            int before=store.allEntries().size();
            try{store.importJson(store.exportJson().toString());System.out.println("AUDIT_WIDE unexpected success");}
            catch(org.json.JSONException error){System.out.println("AUDIT_WIDE verses="+lines.size()
                +" rejected="+error.getMessage()+" rowsUnchanged="+(before==store.allEntries().size()));}
        }
    }
    @Test public void dueDuplicateAndArchive()throws Exception{
        try(LocalStore store=new LocalStore(context)){
            long a=store.createUnifiedCollection("A"),b=store.createUnifiedCollection("B");
            long g1=store.unifiedCollection(a).groupId,g2=store.unifiedCollection(b).groupId;
            List<Bible.Verse> lines=Collections.singletonList(new Bible.Verse("1:1","Слово"));
            String ref="Бытие 1:1";
            long e1=store.add(g1,ref,ref,lines),e2=store.add(g2,ref,ref,lines);
            long day=86400000L,now=10*day;
            for(long e:new long[]{e1,e2}){
                store.markMastered(e,0,1,100,"VERSE");
                store.scheduleReview(e,"VERSE",0,1,0,false,day);
            }
            System.out.println("AUDIT_DUE before="+store.dueItems(now).size());
            store.scheduleReview(e1,"VERSE",0,1,100,false,now);
            System.out.println("AUDIT_DUE afterOneSuccess="+store.dueItems(now).size()+
                " remainingSameVerse="+(store.dueItems(now).get(0).entryId==e2));
            store.deleteUnifiedCollection(b);
            System.out.println("AUDIT_DUE afterCollectionDelete="+store.dueItems(now).size());
        }
    }
    @Test public void homeMemoryAndReaderOn149Verses()throws Exception{
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a=controller.get();joinLoader();
        LocalStore store=(LocalStore)field(a,"store");Bible bible=(Bible)field(a,"bible");
        LocalStore.UnifiedCollection u=store.unifiedCollection(store.createUnifiedCollection("149 verses"));
        List<Bible.Verse> verses=new ArrayList<>();
        for(int c=1;c<=6;c++)verses.addAll(bible.chapter(48,c));
        store.add(u.groupId,"Галатам","Галатам 1:1–6:18",verses);
        set(a,"activeUnifiedId",u.id);
        for(int i=0;i<2;i++){
            long q=store.readQueryCount(),t=System.nanoTime();
            call(a,"render",new Class<?>[0]);layout(a,411,730);
            System.out.println("AUDIT_HOME verses="+verses.size()+" pass="+i+
                " ms="+(System.nanoTime()-t)/1e6+" queries="+(store.readQueryCount()-q));
        }
        long q=store.readQueryCount(),t=System.nanoTime();
        call(a,"buildRunLines",new Class<?>[]{LocalStore.UnifiedCollection.class},u);
        System.out.println("AUDIT_MEMORY verses="+verses.size()+" ms="+(System.nanoTime()-t)/1e6+
            " queries="+(store.readQueryCount()-q));
        q=store.readQueryCount();t=System.nanoTime();
        store.statusesForChapter("Галатам",1);
        System.out.println("AUDIT_READER verses="+bible.chapter(48,1).size()+" ms="+(System.nanoTime()-t)/1e6+
            " queries="+(store.readQueryCount()-q));
        controller.pause().stop().destroy();
    }
    private static Object field(MainActivity a,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);
    }
    private static void set(MainActivity a,String name,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);
    }
    private static Object call(MainActivity a,String name,Class<?>[] types,Object... args)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(a,args);
    }
    private static void joinLoader()throws Exception{
        for(Thread t:Thread.getAllStackTraces().keySet())if(t.getName().equals("BibleLoader"))t.join(15000);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    private static TextView wanted(MainActivity a,int slot)throws Exception{
        List<?> slots=(List<?>)field(a,"lessonBuildSlots"),tiles=(List<?>)field(a,"lessonBuildTiles");
        String word=WordOrderLogic.comparable(((TextView)slots.get(slot)).getText().toString());
        Collection<?> used=(Collection<?>)field(a,"lessonUsedTiles");
        for(Object v:tiles){
            TextView tile=(TextView)v;
            if(!used.contains(tile.getTag())&&word.equals(WordOrderLogic.comparable(tile.getText().toString())))return tile;
        }
        return null;
    }
    private static void layout(MainActivity a,int w,int h)throws Exception{
        View root=(View)field(a,"root");
        root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),
                     View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
        root.layout(0,0,w,h);
    }
    @Test public void estherBuildAndLifecycle()throws Exception{
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a=controller.get();joinLoader();
        LocalStore store=(LocalStore)field(a,"store");Bible bible=(Bible)field(a,"bible");
        long u=store.createUnifiedCollection("Esther baseline"),g=store.unifiedCollection(u).groupId;
        List<Bible.Verse> lines=bible.passage(17,8,9,8,9);
        long id=store.add(g,"Есфирь 8:9","Есфирь 8:9",lines);
        store.finishExercise(id,0,1);
        set(a,"lessonSeed",429L);
        long start=System.nanoTime();
        call(a,"openTraining",new Class<?>[]{long.class},id);layout(a,411,730);
        System.out.println("AUDIT_ESTHER openLayoutMs="+(System.nanoTime()-start)/1e6);
        int chunks=0,correct=0;List<Double> tapMs=new ArrayList<>();List<Long> reads=new ArrayList<>();
        while("BUILD".equals(call(a,"lessonPhase",new Class<?>[]{LocalStore.Entry.class},store.entry(id)).toString())){
            if(++chunks>100)throw new AssertionError("loop");
            int n=(int)field(a,"lessonBuildRequired");
            for(int i=0;i<n;i++){
                TextView button=wanted(a,i);
                if(button==null)throw new AssertionError("missing correct tile "+i);
                long q=store.readQueryCount(),t=System.nanoTime();
                button.performClick();layout(a,411,730);
                tapMs.add((System.nanoTime()-t)/1e6);reads.add(store.readQueryCount()-q);
                correct++;
            }
            ((View)field(a,"lessonBuildAction")).performClick();layout(a,411,730);
        }
        Collections.sort(tapMs);
        System.out.println("AUDIT_ESTHER chunks="+chunks+" taps="+correct+" p50="+tapMs.get(tapMs.size()/2)
            +" p95="+tapMs.get((int)Math.ceil(tapMs.size()*.95)-1)+" max="+tapMs.get(tapMs.size()-1)
            +" queriesPerTap="+new TreeSet<>(reads));
        Bundle state=new Bundle();controller.saveInstanceState(state);
        System.out.println("AUDIT_RECREATE screenBefore="+field(a,"screen")+" savedKeys="+state.keySet());
        controller.pause().stop().destroy();
        ActivityController<MainActivity> recreated=Robolectric.buildActivity(MainActivity.class).setup(state);
        joinLoader();
        System.out.println("AUDIT_RECREATE screenAfter="+field(recreated.get(),"screen"));
        recreated.pause().stop().destroy();
    }
    @Test public void resizeCanRestoreDifferentAssemblyPrefix()throws Exception{
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a=controller.get();joinLoader();
        LocalStore store=(LocalStore)field(a,"store");Bible bible=(Bible)field(a,"bible");
        long u=store.createUnifiedCollection("Resize fixture"),g=store.unifiedCollection(u).groupId;
        long id=store.add(g,"Есфирь 8:9","Есфирь 8:9",bible.passage(17,8,9,8,9));
        store.finishExercise(id,0,1);
        android.util.DisplayMetrics m=a.getResources().getDisplayMetrics();
        for(long seed=1;seed<=8;seed++){
            m.widthPixels=Math.round(320*m.density);
            call(a,"openTraining",new Class<?>[]{long.class},id);
            set(a,"lessonSeed",seed);set(a,"lessonPicked",0);
            ((Collection<?>)field(a,"lessonUsedTiles")).clear();
            call(a,"render",new Class<?>[0]);
            TextView word=wanted(a,0);String picked=word.getText().toString();word.performClick();
            a.onBackPressed();
            m.widthPixels=Math.round(660*m.density);
            call(a,"openTraining",new Class<?>[]{long.class},id);
            int restored=(int)field(a,"lessonPicked");
            String first=((TextView)((List<?>)field(a,"lessonBuildSlots")).get(0)).getText().toString();
            boolean mismatch=restored==1&&!WordOrderLogic.comparable(first).equals(WordOrderLogic.comparable(picked));
            System.out.println("AUDIT_RESIZE seed="+seed+" retained="+restored+" picked="+picked+" restoredFirst="+first+" mismatch="+mismatch);
            a.onBackPressed();
            if(mismatch)break;
        }
        controller.pause().stop().destroy();
    }

    @Test public void failedReviewPartiallyCommitsOnSqliteError() throws Exception {
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity=controller.get();joinLoader();
        LocalStore store=(LocalStore)field(activity,"store");
        long uid=store.createUnifiedCollection("Failure probe");
        long g=store.unifiedCollection(uid).groupId;
        long id=store.add(g,"Тест","Бытие 1:1",Collections.singletonList(
            new Bible.Verse("1:1","В начале сотворил Бог небо и землю")));
        store.commitSuccessfulRecall(id,"VERSE",0,1,false,true,10000L);
        call(activity,"openReview",new Class<?>[]{LocalStore.DueItem.class},new LocalStore.DueItem(id,"VERSE",0,1,0));
        set(activity,"draft","Абсолютно другой ответ");
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject_review_attempt BEFORE INSERT ON review_attempts "+
            "BEGIN SELECT RAISE(ABORT,'audit fixture: review attempt refused'); END");
        int failuresBefore=store.recentFailures(id,"VERSE",0,1);
        try {
            call(activity,"validateLessonWriting",new Class<?>[]{LocalStore.Entry.class},store.entry(id));
            Assert.fail("Expected an injected SQLite failure");
        } catch(InvocationTargetException error) {
            Assert.assertTrue(error.getCause() instanceof android.database.sqlite.SQLiteException);
        }
        int attempts;
        try (android.database.Cursor cursor=store.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM review_attempts WHERE entry_id=?",new String[]{String.valueOf(id)})) {
            cursor.moveToFirst();
            attempts=cursor.getInt(0);
        }
        int failuresAfter=store.recentFailures(id,"VERSE",0,1);
        System.out.println("AUDIT_FAILED_REVIEW failuresBefore="+failuresBefore+" failuresAfter="+failuresAfter+
                " attempts="+attempts+" expectedDatabaseError=true");
        Assert.assertEquals(failuresBefore+1,failuresAfter);
        Assert.assertEquals(1,attempts);
        controller.pause().stop().destroy();
    }
}
