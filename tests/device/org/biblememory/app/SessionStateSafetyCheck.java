package org.biblememory.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;
import org.json.JSONObject;
import java.lang.reflect.*;
import java.util.*;

/** Tests the existing persistence boundaries, not full Activity recreation or UI. */
public class SessionStateSafetyCheck extends Instrumentation {
    private final List<MainActivity> actors=new ArrayList<>();
    private int passed,failed;
    interface Scenario{void run(SafetySandbox context)throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    static Object get(MainActivity actor,String name)throws Exception{return ActivityTestAccess.get(actor,name);}
    static void set(MainActivity actor,String name,Object value)throws Exception{ActivityTestAccess.set(actor,name,value);}
    static Object call(MainActivity actor,String name,Class<?>[] types,Object... values)throws Exception{
        return ActivityTestAccess.call(actor,name,types,values);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) static void option(MainActivity actor,String field,String value)throws Exception{
        Field f=ActivityTestAccess.field(actor,field);ActivityTestAccess.set(actor,field,Enum.valueOf((Class)f.getType(),value));
    }
    static void equal(Object expected,Object actual){StorageSafetyCheck.require(Objects.equals(expected,actual),"Expected "+expected+", actual "+actual);}
    MainActivity actor(SafetySandbox context)throws Exception{
        MainActivity result=new MainActivity();actors.add(result);
        Method attach=ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);attach.setAccessible(true);attach.invoke(result,context);
        set(result,"store",context.store());return result;
    }
    @SuppressWarnings("unchecked") static <T> List<T> list(MainActivity actor,String field)throws Exception{return (List<T>)get(actor,field);}
    private void run(String name,Scenario scenario){
        final Throwable[] error=new Throwable[1];
        try(SafetySandbox context=new SafetySandbox(getTargetContext())){
            runOnMainSync(()->{try{scenario.run(context);}catch(Throwable e){error[0]=e;}finally{
                for(MainActivity actor:actors)try{((java.util.concurrent.ExecutorService)get(actor,"bibleSearchExecutor")).shutdownNow();}catch(Exception e){if(error[0]==null)error[0]=e;}
                actors.clear();
            }});
            if(error[0]!=null)throw new AssertionError(error[0]);
            passed++;report("SESSION_GUARD_PASS "+name);
        }catch(Throwable e){failed++;report("SESSION_GUARD_FAIL "+name+": "+android.util.Log.getStackTraceString(e));}
    }
    private void report(String message){Bundle b=new Bundle();b.putString("stream",message+"\n");sendStatus(0,b);}
    private void trainingRoundTrip(SafetySandbox c)throws Exception{
        MainActivity first=actor(c);long id=StorageSafetyCheck.entry(c.store(),c.store().newGroup("Session"));
        set(first,"selectedEntry",id);option(first,"studyStage","SECTION");option(first,"trainingMode","HIDE");
        set(first,"stepCursor",3);set(first,"mask",75);set(first,"draft","Незавершённый ответ");
        set(first,"lessonSeed",123456L);set(first,"lessonChunk",2);set(first,"lessonVariant",1);set(first,"lessonPicked",4);
        set(first,"blurSeed",73);set(first,"lessonWriteHadFeedback",true);set(first,"lessonWriteVariant",2);
        set(first,"practiceWeakFragment",true);set(first,"weakFragmentText","Слабый фрагмент");
        list(first,"lessonUsedTiles").addAll(Arrays.asList(1,3));
        set(first,"rotationExerciseKey","verse-1-gaps");set(first,"rotationExerciseSeed",987L);set(first,"rotationMask","0,2,5");
        set(first,"checked",true);set(first,"passed",false);set(first,"testAssisted",true);
        call(first,"saveTrainingSession",new Class<?>[0]);
        MainActivity restored=actor(c);equal(true,call(restored,"restoreTrainingSession",new Class<?>[]{long.class},id));
        equal("SECTION",get(restored,"studyStage").toString());equal("HIDE",get(restored,"trainingMode").toString());
        equal(3,get(restored,"stepCursor"));equal(75,get(restored,"mask"));equal("Незавершённый ответ",get(restored,"draft"));
        equal(123456L,get(restored,"lessonSeed"));equal(2,get(restored,"lessonChunk"));equal(4,get(restored,"lessonPicked"));
        equal(1,get(restored,"lessonVariant"));equal(73,get(restored,"blurSeed"));equal(true,get(restored,"lessonWriteHadFeedback"));equal(2,get(restored,"lessonWriteVariant"));
        equal(true,get(restored,"practiceWeakFragment"));equal("Слабый фрагмент",get(restored,"weakFragmentText"));
        equal(Arrays.asList(1,3),list(restored,"lessonUsedTiles"));equal("0,2,5",get(restored,"rotationMask"));equal(987L,get(restored,"rotationExerciseSeed"));
        equal(false,get(restored,"checked"));equal(false,get(restored,"passed"));equal(false,get(restored,"testAssisted"));
        equal(0L,StorageSafetyCheck.number(c.store().getReadableDatabase(),"SELECT COUNT(*) FROM verse_progress"));
    }
    private void namespaces(SafetySandbox c)throws Exception{
        MainActivity first=actor(c);set(first,"selectedEntry",41L);set(first,"draft","ordinary");call(first,"saveTrainingSession",new Class<?>[0]);
        set(first,"groupMode",true);set(first,"draft","linked");set(first,"groupIntroDone",true);set(first,"groupBuildDone",true);call(first,"saveTrainingSession",new Class<?>[0]);
        set(first,"groupMode",false);set(first,"selectedEntry",42L);set(first,"draft","other-entry");call(first,"saveTrainingSession",new Class<?>[0]);
        MainActivity restored=actor(c);call(restored,"restoreTrainingSession",new Class<?>[]{long.class},41L);equal("ordinary",get(restored,"draft"));
        call(restored,"restoreTrainingSession",new Class<?>[]{long.class,boolean.class},41L,true);equal("linked",get(restored,"draft"));
        equal(true,get(restored,"groupIntroDone"));equal(true,get(restored,"groupBuildDone"));
        call(restored,"restoreTrainingSession",new Class<?>[]{long.class},42L);equal("other-entry",get(restored,"draft"));
        equal(false,call(restored,"restoreTrainingSession",new Class<?>[]{long.class},99L));
    }
    private void corruptMetadata(SafetySandbox c)throws Exception{
        c.getSharedPreferences("study_session",0).edit().putString("entry_41_stage","unknown").putString("entry_41_mode","unknown")
            .putInt("entry_41_step",-8).putInt("entry_41_mask",900).putString("entry_41_lesson_tiles","[1, bad, 2]").commit();
        MainActivity restored=actor(c);equal(true,call(restored,"restoreTrainingSession",new Class<?>[]{long.class},41L));
        equal("VERSE",get(restored,"studyStage").toString());equal("READ",get(restored,"trainingMode").toString());
        equal(0,get(restored,"stepCursor"));equal(0,get(restored,"mask"));equal(Arrays.asList(1,2),list(restored,"lessonUsedTiles"));
    }
    private void runLines(MainActivity actor)throws Exception{
        Class<?> type=Class.forName("org.biblememory.app.StudySessionController$RunLine");
        Constructor<?> ctor=type.getDeclaredConstructor(long.class,int.class,String.class,String.class);ctor.setAccessible(true);
        for(int i=0;i<3;i++)list(actor,"runLines").add(ctor.newInstance(41L,i,"5:"+(i+3),"Текст "+i));
    }
    private void runRoundTrip(SafetySandbox c)throws Exception{
        MainActivity first=actor(c);runLines(first);set(first,"runCollectionId",17L);set(first,"runCursor",1);set(first,"runCorrect",1);
        set(first,"runDraft","Текущий ответ");set(first,"runChecked",true);set(first,"runPassed",false);list(first,"runMistakes").add("5:3");
        call(first,"saveRunSession",new Class<?>[0]);
        MainActivity restored=actor(c);runLines(restored);set(restored,"runCollectionId",17L);call(restored,"restoreRunSession",new Class<?>[0]);
        equal(1,get(restored,"runCursor"));equal(1,get(restored,"runCorrect"));equal("Текущий ответ",get(restored,"runDraft"));
        equal(true,get(restored,"runChecked"));equal(false,get(restored,"runPassed"));equal(Collections.singletonList("5:3"),list(restored,"runMistakes"));
        String key=(String)call(restored,"runKey",new Class<?>[]{long.class},17L);
        c.getSharedPreferences("unified_library_ui",0).edit().putInt(key+"cursor",999).commit();call(restored,"restoreRunSession",new Class<?>[0]);equal(2,get(restored,"runCursor"));
        set(restored,"runCollectionId",18L);call(restored,"restoreRunSession",new Class<?>[0]);equal(0,get(restored,"runCursor"));equal("",get(restored,"runDraft"));
    }
    private void cardCheckpoint(SafetySandbox c)throws Exception{
        MainActivity first=actor(c);set(first,"selectedDeck",12L);list(first,"cardSequence").addAll(Arrays.asList(7L,3L,11L));
        set(first,"cardIndex",1);set(first,"cardShowReference",false);call(first,"saveCardSession",new Class<?>[0]);
        MainActivity next=actor(c);JSONObject json=new JSONObject(next.getSharedPreferences("card_sessions",0).getString("deck_12",""));
        equal(7L,json.getJSONArray("order").getLong(0));equal(3L,json.getJSONArray("order").getLong(1));equal(11L,json.getJSONArray("order").getLong(2));
        equal(1,json.getInt("cursor"));equal(false,json.getBoolean("reference_first"));
        equal(false,next.getSharedPreferences("card_sessions",0).contains("deck_13"));
    }
    private void passedReviewDoesNotOverwrite(SafetySandbox c)throws Exception{
        MainActivity first=actor(c);set(first,"selectedEntry",41L);set(first,"reviewMode",true);set(first,"draft","pending");
        set(first,"currentReview",new LocalStore.DueItem(41,"VERSE_RUN",0,2,100));set(first,"activeStep",new StudyPlanLogic.Step(0,2,"Run"));call(first,"saveTrainingSession",new Class<?>[0]);
        SharedPreferences p=c.getSharedPreferences("study_session",0);equal("pending",p.getString("review_draft",""));
        equal("VERSE_RUN",p.getString("review_review_stage",""));equal(0,p.getInt("review_begin",-1));equal(2,p.getInt("review_end",-1));
        set(first,"checked",true);set(first,"passed",true);set(first,"draft","already-finished");call(first,"saveTrainingSession",new Class<?>[0]);
        equal("pending",p.getString("review_draft",""));
    }
    private void invalidSessionsDoNotWrite(SafetySandbox c)throws Exception{
        MainActivity empty=actor(c);call(empty,"saveTrainingSession",new Class<?>[0]);call(empty,"saveCardSession",new Class<?>[0]);call(empty,"saveRunSession",new Class<?>[0]);
        for(String name:Arrays.asList("study_session","card_sessions","unified_library_ui"))equal(0,c.getSharedPreferences(name,0).getAll().size());
    }
    private void pickerBundle(SafetySandbox c)throws Exception{
        Bundle b=new Bundle();b.putString("picker_screen","PICKER_VERSES");b.putString("picker_target","STUDY_GROUP");b.putString("picker_back","COLLECTION_DETAIL");
        b.putLong("picker_id",7);b.putLong("picker_unified",3);b.putLong("picker_group",7);b.putLong("picker_deck",12);
        b.putInt("picker_book",19);b.putInt("picker_chapter",118);b.putInt("reader_book",40);b.putInt("reader_chapter",5);
        b.putBoolean("picker_active",true);b.putBoolean("picker_choose_end",true);b.putInt("picker_scroll",740);
        b.putIntArray("picker_bounds",new int[]{19,118,1,118,176});b.putIntArray("picker_quick",new int[]{19,118,300,176});b.putLong("picker_insert_entry",41);b.putLong("picker_insert_card",91);
        MainActivity restored=actor(c);call(restored,"restorePickerState",new Class<?>[]{Bundle.class},b);
        equal("PICKER_VERSES",get(restored,"screen").toString());equal(740,get(restored,"pickerRestoredScroll"));equal(41L,get(restored,"insertAfterEntryId"));equal(91L,get(restored,"insertAfterCardId"));
        PassageSelection selection=(PassageSelection)get(restored,"passageSelection");equal(19,selection.book);equal(true,selection.active);equal("[118, 1, 118, 176]",Arrays.toString(selection.bounds()));
        b.putString("picker_screen","bad-state");call(restored,"restorePickerState",new Class<?>[]{Bundle.class},b);equal("HOME",get(restored,"screen").toString());equal(false,selection.active);
    }
    @Override public void onStart(){
        run("training-draft-seeds-tiles-roundtrip",this::trainingRoundTrip);
        run("entry-and-linked-namespaces",this::namespaces);
        run("corrupt-training-metadata-fallback",this::corruptMetadata);
        run("run-checkpoint-and-cursor-clamp",this::runRoundTrip);
        run("card-checkpoint-order-and-deck-isolation",this::cardCheckpoint);
        run("passed-review-does-not-overwrite",this::passedReviewDoesNotOverwrite);
        run("invalid-sessions-do-not-write",this::invalidSessionsDoNotWrite);
        run("picker-bundle-restore-and-invalid-enum",this::pickerBundle);
        Bundle result=new Bundle();result.putString("stream","SESSION_GUARDS_PASSED="+passed+" UNEXPECTED_FAILURES="+failed+"\n");finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
