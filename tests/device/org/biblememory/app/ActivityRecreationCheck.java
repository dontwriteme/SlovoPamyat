package org.biblememory.app;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Native lifecycle counterpart of the JVM recreation suite. Runs only in the test APK. */
public class ActivityRecreationCheck extends HomeIconCheck {
    private Activity renewed;
    private CountDownLatch resumed;
    private int guards;
    private final Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks(){
        public void onActivityCreated(Activity value,Bundle state){if(value instanceof MainActivity&&value!=activity)renewed=value;}
        public void onActivityResumed(Activity value){if(value==renewed&&resumed!=null)resumed.countDown();}
        public void onActivityStarted(Activity value){} public void onActivityPaused(Activity value){}
        public void onActivityStopped(Activity value){} public void onActivitySaveInstanceState(Activity value,Bundle state){}
        public void onActivityDestroyed(Activity value){}
    };
    interface Action{void run()throws Exception;}
    void ui(Action action){main(()->{try{action.run();}catch(Exception error){throw new RuntimeException(error);}});}
    Object get(String key)throws Exception{return ActivityTestAccess.get(activity,key);}
    void set(String key,Object value)throws Exception{ActivityTestAccess.set(activity,key,value);}
    void render(Screen screen)throws Exception{set("screen",screen);ActivityTestAccess.call(activity,"render",new Class<?>[0]);}
    StudySessionController study()throws Exception{return (StudySessionController)get("studySession");}
    FlashcardSessionController cards()throws Exception{return (FlashcardSessionController)get("flashcardSession");}
    BibleReaderController reader()throws Exception{return (BibleReaderController)get("bibleReader");}
    LocalStore store()throws Exception{return (LocalStore)get("store");}
    EditText input(View view){
        if(view instanceof EditText)return (EditText)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){EditText found=input(group.getChildAt(i));if(found!=null)return found;}}
        return null;
    }
    void loaded()throws Exception{
        ((Bible)get("bible")).load(activity);
        for(Thread worker:Thread.getAllStackTraces().keySet())if("BibleLoader".equals(worker.getName())){worker.join(15000);check(!worker.isAlive(),"Loader timeout");}
        waitForIdleSync();
    }
    void recreateActivity()throws Exception{
        renewed=null;resumed=new CountDownLatch(1);ui(()->activity.recreate());
        check(resumed.await(20,TimeUnit.SECONDS),"Recreation did not resume");activity=(MainActivity)renewed;loaded();
    }
    void pass(String scenario){guards++;Bundle value=new Bundle();value.putString("stream","RECREATION_GUARD_PASS "+scenario+"\n");sendStatus(0,value);}
    @Override public void onStart(){
        Bundle result=new Bundle();int outcome=Activity.RESULT_CANCELED;Application application=null;long group=-1,deck=-1;
        try{
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));loaded();
            application=activity.getApplication();application.registerActivityLifecycleCallbacks(callbacks);
            final long[] ids=new long[2];ui(()->{
                ids[0]=store().newGroup("Native recreation fixture");
                ids[1]=store().add(ids[0],"Native fixture","Пользовательский текст",Collections.singletonList(new Bible.Verse("1:1","Слово истины")));
                store().finishExercise(ids[1],0,3);ActivityTestAccess.call(activity,"openTraining",new Class<?>[]{long.class},ids[1]);
                check(input(root())!=null,"No writing input");input(root()).setText("Ответ после поворота");
            });group=ids[0];recreateActivity();ui(()->{
                check(get("screen")==Screen.TRAIN,"Lesson screen lost");check("Ответ после поворота".contentEquals(input(root()).getText()),"Draft lost");
                check(store().exerciseDone(ids[1],0)==3,"Recreation advanced learning");
            });pass("unfinished-answer-no-progress-write");
            final List<Long> order=new ArrayList<>();ui(()->{
                ids[0]=store().newCardDeck("Native cards fixture");store().addCustomFlashcard(ids[0],"One","Один");store().addCustomFlashcard(ids[0],"Two","Два");
                cards().selectedDeck=ids[0];cards().cardFromUnified=false;cards().startCardSession();cards().cardIndex=1;cards().cardShowReference=false;cards().cardRevealed=true;
                order.addAll(cards().cardSequence);render(Screen.CARD_SESSION);
            });deck=ids[0];recreateActivity();ui(()->{
                check(get("screen")==Screen.CARD_SESSION,"Card screen lost");check(order.equals(cards().cardSequence),"Card order changed");
                check(cards().cardIndex==1&&!cards().cardShowReference&&cards().cardRevealed,"Card face/cursor lost");
            });pass("card-order-direction-revealed-face");
            ui(()->{
                reader().selectedBook=19;reader().selectedChapter=118;reader().readerVerseNumber=120;reader().chapterFromVerseGrid=true;reader().focusVerse=-1;render(Screen.CHAPTER);
            });waitForIdleSync();final int[] y=new int[1];ui(()->{ScrollView scroll=(ScrollView)get("currentScroller");scroll.scrollTo(0,1600);y[0]=scroll.getScrollY();check(y[0]>0,"No reader scroll");});
            recreateActivity();waitForIdleSync();ui(()->{
                check(get("screen")==Screen.CHAPTER&&reader().readerVerseNumber==120,"Reader position lost");
                check(((ScrollView)get("currentScroller")).getScrollY()==y[0],"Reader scroll lost");activity.onBackPressed();
                check(get("screen")==Screen.VERSES,"Back stack changed");check(reader().bible.chapter(19,118).size()==176,"Verse grid size changed");
            });pass("reader-scroll-176-verses-system-back");
            result.putString("stream","RECREATION_GUARDS_PASSED="+guards+" UNEXPECTED_FAILURES=0\n");outcome=Activity.RESULT_OK;
        }catch(Throwable error){result.putString("stream","RECREATION_GUARD_FAIL "+android.util.Log.getStackTraceString(error));}
        finally{
            if(application!=null)application.unregisterActivityLifecycleCallbacks(callbacks);
            final long removeGroup=group,removeDeck=deck;
            if(activity!=null)try{ui(()->{if(removeGroup>0)store().deleteGroup(removeGroup);if(removeDeck>0)store().deleteCardDeck(removeDeck);activity.finish();});}catch(Throwable ignored){}
        }
        finish(outcome,result);
    }
}
