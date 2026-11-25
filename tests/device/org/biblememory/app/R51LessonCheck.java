package org.biblememory.app;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Android framework test. Arguments select a regression or a fixture for shell-driven process death. */
public final class R51LessonCheck extends Instrumentation {
    private static final String TEXT="Слово истины дает свет";
    private static final String DRAFT="Черновик после уничтожения процесса";
    private final LinkedBlockingQueue<MainActivity> resumed=new LinkedBlockingQueue<>();
    private String mode;
    private Context app;
    private MainActivity activity;
    private Bundle results;

    @Override public void onCreate(Bundle options){
        super.onCreate(options);
        mode=options.getString("mode","regression");
        start();
    }

    @Override public void onStart(){
        results=new Bundle();
        try{
            app=getTargetContext();
            require("org.biblememory.app.r51fixture".equals(app.getPackageName()),"Isolated package required");
            activity=(MainActivity)startActivitySync(new Intent(app,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            onMain(()->{
                activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
                    @Override public void onActivityResumed(Activity current){resumed.add((MainActivity)current);}
                    @Override public void onActivityCreated(Activity current,Bundle state){}
                    @Override public void onActivityStarted(Activity current){}
                    @Override public void onActivityPaused(Activity current){}
                    @Override public void onActivityStopped(Activity current){}
                    @Override public void onActivitySaveInstanceState(Activity current,Bundle state){}
                    @Override public void onActivityDestroyed(Activity current){}
                });
                return null;
            });
            if("seed-process".equals(mode))seedProcess();
            else if("reviews".equals(mode)){
                review(false);
                review(true);
                reviewCompletion();
            }else{
                lesson();
                group();
                review(false);
                review(true);
                deletionAndImport();
            }
            results.putString("result","PASS "+mode);
            finish(Activity.RESULT_OK,results);
        }catch(Throwable failure){
            results.putString("failure",android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED,results);
        }
    }

    private void lesson()throws Exception{
        long id=onMain(()->{
            long entry=add(collection("Занятие"),TEXT);
            open(entry);
            press((View)get("root"),"Скрыть 25% слов");
            return entry;
        });
        recreate();
        onMain(()->{
            checkLesson(id);
            require((int)get("mask")==25,"Intro mask");
            press((View)get("root"),"Продолжить");
            List<?> slots=(List<?>)get("lessonBuildSlots");
            List<?> tiles=(List<?>)get("lessonBuildTiles");
            String first=((TextView)slots.get(0)).getText().toString();
            for(Object tile:tiles)if(first.contentEquals(((TextView)tile).getText())){
                ((View)tile).performClick();
                break;
            }
            require(((LessonAssemblyState)get("lessonAssembly")).picked()==1,"One actual tile selected");
            return null;
        });
        recreate();
        onMain(()->{
            checkLesson(id);
            require(((LessonAssemblyState)get("lessonAssembly")).picked()==1,"Same geometry retains selected tile");
            return null;
        });
        changeOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        onMain(()->{
            checkLesson(id);
            // For this short text the partition and prefix fit both layouts.
            require(((LessonAssemblyState)get("lessonAssembly")).picked()==1,"Rotation retains the same valid prefix");
            store().finishExercise(id,0,3);
            call("render",new Class<?>[0]);
            firstEdit((View)get("root")).setText("Слово истины");
            return null;
        });
        changeOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        onMain(()->{
            checkLesson(id);
            EditText answer=firstEdit((View)get("root"));
            require("Слово истины".contentEquals(answer.getText()),"Written draft visible after rotation");
            answer.setText(TEXT);
            press((View)get("root"),"Проверить");
            require((boolean)get("passed"),"Answer saved");
            return null;
        });
        String checkpoint=onMain(()->store().recallCheckpoint(id,"VERSE",0,1));
        recreate();
        onMain(()->{
            checkLesson(id);
            require((boolean)get("passed")&&checkpoint.equals(store().recallCheckpoint(id,"VERSE",0,1)),
                "Completion displays the recorded attempt exactly once");
            activity.onBackPressed();
            return null;
        });
        results.putString("lesson","intro, assembly, writing, completion, rotation");
    }

    private void group()throws Exception{
        long id=onMain(()->{
            long group=collection("Связка");
            long entry=store().add(group,"Связка","Связка",Arrays.asList(
                new Bible.Verse("1:1","Первый стих"),new Bible.Verse("1:2","Второй стих"),
                new Bible.Verse("1:3","Третий стих")));
            for(int i=0;i<2;i++)store().markMastered(entry,i,i+1,100,"VERSE");
            open(entry);
            set("stepCursor",1);
            call("openGroup",new Class<?>[]{GroupLearningLogic.Candidate.class},
                GroupLearningLogic.candidate("CHAIN",0,2,"fixture"));
            press((View)get("root"),"Продолжить");
            return entry;
        });
        recreate();
        onMain(()->{
            checkLesson(id);
            require((boolean)get("groupMode")&&(boolean)get("groupIntroDone"),"Group phase");
            StudyPlanLogic.Step step=(StudyPlanLogic.Step)get("activeStep");
            require(step.start==0&&step.end==2&&(int)get("returnVerseCursor")==1,"Group range and return verse");
            activity.onBackPressed();
            return null;
        });
        results.putString("group","phase, range and return position");
    }

    private void review(boolean run)throws Exception{
        awaitBible();
        long id=onMain(()->{
            long group=collection(run?"Повторение отрывка":"Повторение стиха");
            long entry=run?store().add(group,"Повторение","Повторение",Arrays.asList(
                new Bible.Verse("1:1","Первый стих"),new Bible.Verse("1:2","Второй стих"))):add(group,TEXT);
            for(int i=0;i<(run?2:1);i++){
                store().markMastered(entry,i,i+1,100,"VERSE");
                store().scheduleReview(entry,"VERSE",i,i+1,100,false,1000L);
            }
            call("startReviewSession",new Class<?>[]{long.class},group);
            firstEdit((View)get("root")).setText("Незавершённое повторение");
            return entry;
        });
        recreate();
        awaitBible();
        onMain(()->{
            checkLesson(id);
            LocalStore.DueItem item=((ReviewSession)get("reviewSession")).current().item();
            require((run?"VERSE_RUN":"VERSE").equals(item.stage),"Review kind");
            require("Незавершённое повторение".equals(((LessonWritingState)get("lessonWriting")).draft),"Review draft");
            activity.onBackPressed();
            return null;
        });
        results.putString(run?"verse_run":"review","stage and draft");
    }

    private void reviewCompletion()throws Exception{
        awaitBible();
        long[] entries=onMain(()->{
            long group=collection("Очередь повторения");
            long first=add(group,TEXT), next=add(group,"Другой стих для повторения");
            store().commitSuccessfulRecall(first,"VERSE",0,1,false,true,1000);
            store().commitSuccessfulRecall(next,"VERSE",0,1,false,true,2000);
            String before=store().recallCheckpoint(first,"VERSE",0,1);
            call("startReviewSession",new Class<?>[]{long.class},group);
            checkLesson(first);
            firstEdit((View)get("root")).setText(TEXT);
            press((View)get("root"),"Проверить");
            require((boolean)get("passed"),"Review committed");
            require(!before.equals(store().recallCheckpoint(first,"VERSE",0,1)),"Checkpoint changed");
            return new long[]{first,next};
        });
        String checkpoint=onMain(()->store().recallCheckpoint(entries[0],"VERSE",0,1));
        recreate();
        awaitBible();
        onMain(()->{
            checkLesson(entries[0]);
            require((boolean)get("passed"),"Committed review restored");
            View continued=find((View)get("root"),false,"Продолжить");
            require(continued!=null&&continued.performClick(),"Advance to the next review");
            checkLesson(entries[1]);
            continued.performClick();
            checkLesson(entries[1]);
            require(checkpoint.equals(store().recallCheckpoint(entries[0],"VERSE",0,1)),
                "The restored completion did not record another attempt");
            activity.onBackPressed();
            return null;
        });
        results.putString("review_completion","restored checkpoint and one continuation");
    }

    private void deletionAndImport()throws Exception{
        onMain(()->{
            LocalStore.UnifiedCollection collection=store().unifiedCollection(store().createUnifiedCollection("Удаление"));
            long entry=add(collection.groupId,TEXT);
            open(entry);
            store().deleteUnifiedCollection(collection.id);
            require(store().entry(entry)!=null,"Archive kept");
            return null;
        });
        recreate();
        onMain(()->{require("HOME".equals(get("screen").toString()),"Removed source returned home");return null;});
        Uri uri=Uri.fromFile(new File(app.getCacheDir(),"r51.svbackup"));
        try(LocalStore helper=new LocalStore(app)){SvetochBackup.exportTo(app,helper,uri);}
        String generation=SvetochBackup.generation(app);
        onMain(()->{
            long entry=add(collection("После экспорта"),TEXT);
            open(entry);
            BackupCoordinator.request(app,BackupCoordinator.Operation.IMPORT,uri);
            return null;
        });
        awaitReplacement();
        long timeout=SystemClock.elapsedRealtime()+60000;
        while(generation.equals(SvetochBackup.generation(app))||!onMain(()->(boolean)get("ownsData"))){
            require(SystemClock.elapsedRealtime()<timeout,"Backup coordination timed out");
            SystemClock.sleep(100);
        }
        onMain(()->{require("HOME".equals(get("screen").toString()),"Import retired active lesson");return null;});
        results.putString("guards","deleted source and backup import");
    }

    private void seedProcess()throws Exception{
        onMain(()->{
            LocalStore.UnifiedCollection collection=store().unifiedCollection(
                store().createUnifiedCollection("Проверка процесса"));
            call("activateUnified",new Class<?>[]{long.class},collection.id);
            long id=add(collection.groupId,TEXT);
            store().finishExercise(id,0,3);
            call("startStudyCollection",new Class<?>[]{long.class},collection.groupId);
            firstEdit((View)get("root")).setText(DRAFT);
            activity.onBackPressed();
            return null;
        });
        results.putString("process_draft",DRAFT);
    }

    private void recreate()throws Exception{
        onMain(()->{activity.recreate();return null;});
        awaitReplacement();
    }
    private void changeOrientation(int orientation)throws Exception{
        onMain(()->{activity.setRequestedOrientation(orientation);return null;});
        awaitReplacement();
    }
    private void awaitReplacement()throws Exception{
        MainActivity old=activity;
        long deadline=SystemClock.elapsedRealtime()+60000;
        do{
            activity=resumed.poll(5,TimeUnit.SECONDS);
            if(activity!=null&&activity!=old)return;
        }while(SystemClock.elapsedRealtime()<deadline);
        throw new AssertionError("Replacement Activity did not resume");
    }

    private void awaitBible()throws Exception{
        long deadline=SystemClock.elapsedRealtime()+60000;
        while(!onMain(()->((Bible)get("bible")).isReady())){
            require(SystemClock.elapsedRealtime()<deadline,"Bible not loaded");
            SystemClock.sleep(100);
        }
    }
    private long collection(String title){
        return store().unifiedCollection(store().createUnifiedCollection(title)).groupId;
    }
    private long add(long group,String text){
        return store().add(group,text,text,Collections.singletonList(new Bible.Verse("1:1",text)));
    }
    private void open(long id){call("openTraining",new Class<?>[]{long.class},id);}
    private void checkLesson(long id){
        require("TRAIN".equals(get("screen").toString())&&(long)get("selectedEntry")==id,"Activity restores TRAIN " + id);
    }
    private LocalStore store(){return (LocalStore)get("store");}
    private Object get(String name){
        try{
            Object owner=name.startsWith("lessonBuild")?get("assemblyView"):activity;
            Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
        }
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private void set(String name,Object value){
        try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private Object call(String name,Class<?>[] types,Object... args){
        try{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private <T> T onMain(Callable<T> callable)throws Exception{
        FutureTask<T> task=new FutureTask<>(callable);
        runOnMainSync(task);
        return task.get();
    }
    private static EditText firstEdit(View root){return (EditText)find(root,true,"");}
    private static void press(View root,String text){
        View button=find(root,false,text);
        require(button!=null&&button.performClick(),"Press "+text);
    }
    private static View find(View root,boolean edit,String text){
        if(edit?root instanceof EditText:root instanceof TextView&&root.isClickable()&&
            text.contentEquals(((TextView)root).getText()))return root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                View found=find(group.getChildAt(i),edit,text);
                if(found!=null)return found;
            }
        }
        return null;
    }
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
}
