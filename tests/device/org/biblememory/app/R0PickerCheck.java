package org.biblememory.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Real Android traversal, search executor and frame layout on an isolated test install. */
public final class R0PickerCheck extends Instrumentation {
    private MainActivity activity;

    @Override public void onCreate(Bundle arguments){
        super.onCreate(arguments);
        start();
    }

    private Object field(String name){
        try{
            Field field=MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }

    private View find(View node,Predicate<View> matches){
        if(matches.test(node))return node;
        if(node instanceof ViewGroup){
            ViewGroup group=(ViewGroup)node;
            for(int i=0;i<group.getChildCount();i++){
                View hit=find(group.getChildAt(i),matches);
                if(hit!=null)return hit;
            }
        }
        return null;
    }

    private TextView text(String label){
        return (TextView)find((View)field("root"),
            view->view instanceof TextView&&label.contentEquals(((TextView)view).getText()));
    }

    private EditText input(){
        return (EditText)find((View)field("root"),view->view instanceof EditText&&
            "Поиск по словам Библии".equals(view.getContentDescription()));
    }

    private void await(BooleanSupplier condition,String label)throws InterruptedException{
        CountDownLatch ready=new CountDownLatch(1);
        Runnable check=new Runnable(){
            @Override public void run(){
                if(ready.getCount()==0)return;
                if(condition.getAsBoolean())ready.countDown();
                else activity.getWindow().getDecorView().postOnAnimation(this);
            }
        };
        runOnMainSync(check);
        boolean satisfied=ready.await(30,TimeUnit.SECONDS);
        ready.countDown();
        require(satisfied,label);
    }

    private static void require(boolean condition,String label){
        if(!condition)throw new AssertionError(label);
    }

    private void select(String label,boolean parent){
        runOnMainSync(()->{
            View view=text(label);
            require(view!=null,label);
            require((parent?(View)view.getParent():view).performClick(),label);
        });
    }

    private void invoke(String name){
        try{
            java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(activity);
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }

    private View answerControl(String description){
        return find((View)field("root"),view->description.contentEquals(
            view.getContentDescription()==null?"":view.getContentDescription()));
    }

    private void flashcards()throws InterruptedException{
        long[] deck=new long[1];
        long[] first=new long[1];
        runOnMainSync(()->{
            LocalStore store=(LocalStore)field("store");
            deck[0]=store.newCardDeck("Карточная сессия R8");
            store.addCustomFlashcard(deck[0],"Ссылка А","Текст А");
            store.addCustomFlashcard(deck[0],"Ссылка Б","Текст Б");
            try{
                Field selection=MainActivity.class.getDeclaredField("selectedDeck");
                selection.setAccessible(true);
                selection.setLong(activity,deck[0]);
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
            invoke("startCardSession");
            first[0]=((FlashcardSession)field("flashcards")).currentId();
            View eye=answerControl("Показать ответ");
            require(eye!=null&&eye.performClick(),"card reveals answer");
            require(answerControl("Скрыть ответ")!=null,"revealed control");
        });
        runOnMainSync(()->{
            View swipe=((ViewGroup)field("root")).getChildAt(1);
            long down=SystemClock.uptimeMillis();
            float startX=swipe.getWidth()*0.75f,y=swipe.getHeight()*0.4f;
            for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
                float x=action==MotionEvent.ACTION_DOWN?startX:startX/2;
                MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
                swipe.dispatchTouchEvent(event);
                event.recycle();
            }
        });
        await(()->((FlashcardSession)field("flashcards")).currentId()!=first[0]
            &&answerControl("Показать ответ")!=null,"swipe advances and hides answer");
        runOnMainSync(()->{
            long second=((FlashcardSession)field("flashcards")).currentId();
            activity.onBackPressed();
            invoke("startCardSession");
            require(((FlashcardSession)field("flashcards")).currentId()==second,"card resumes at saved position");
            activity.onBackPressed();
            ((LocalStore)field("store")).deleteCardDeck(deck[0]);
        });
    }

    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->((Bible)field("bible")).isReady(),"Bible ready");
            runOnMainSync(()->{
                BookNavigationIcon icon=((View)field("root")).findViewWithTag(BookNavigationIcon.TAG);
                ((View)icon.getParent()).performClick();
            });
            await(()->input()!=null&&text("Бытие")!=null,"catalog and search input");
            runOnMainSync(()->input().setText("qzxvnotfound"));
            await(()->input()!=null&&text("Ничего не найдено")!=null,"no-match header");
            runOnMainSync(()->{
                require("qzxvnotfound".contentEquals(input().getText()),"query retained");
                input().setText("");
            });
            await(()->text("Бытие")!=null,"clear returns to catalog");
            runOnMainSync(()->{
                ScrollView catalog=(ScrollView)field("currentScroller");
                // Keep the header partly in view as it would be during an ordinary edit.
                catalog.scrollTo(0,16);
                require(catalog.getScrollY()==16,"initial catalog scroll");
                input().setText("бог");
            });
            await(()->input()!=null&&field("currentScroller")==null
                &&!((BibleSearchSession)field("bibleSearch")).loading()&&
                !((BibleSearchSession)field("bibleSearch")).hits().isEmpty(),"virtualized result layout");
            runOnMainSync(()->input().setText(""));
            await(()->text("Бытие")!=null&&field("currentScroller") instanceof ScrollView&&
                ((ScrollView)field("currentScroller")).getScrollY()==16,"catalog scroll restored");

            select("Псалтирь",true);
            select("118",false);
            select("176",false);
            await(()->{
                if(!(field("currentScroller") instanceof ScrollView))return false;
                String verse=((Bible)field("bible")).chapter(19,118).get(175).text;
                TextView target=text(verse);
                if(target==null)return false;
                View card=(View)target.getParent();
                ScrollView scroller=(ScrollView)field("currentScroller");
                return scroller.getScrollY()>0&&card.getTop()<scroller.getScrollY()+scroller.getHeight()
                    &&card.getBottom()>scroller.getScrollY();
            },"Psalm 118:176 visible");
            runOnMainSync(()->{
                activity.onBackPressed();
                require(text("176")!=null,"verse grid restored");
                activity.onBackPressed();
                activity.onBackPressed();
                require(text("Бытие")!=null,"catalog restored");
            });
            flashcards();
            runOnMainSync(activity::finish);
            result.putString("stream","PASS: input, no match, clear, catalog scroll, Psalm 118:176, back stack, flashcard reveal, swipe and resume");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){
            result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
