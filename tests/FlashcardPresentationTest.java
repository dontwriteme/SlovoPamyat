package org.biblememory.app;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.SQLiteMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class FlashcardPresentationTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private LocalStore store;
    private long deck;
    private final String longVerse=String.join(" ",java.util.Collections.nCopies(80,
        "И да будут они светильниками на тверди небесной"));

    private Object field(String name)throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);
        return field.get(activity);
    }
    private void set(String name,Object value)throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);
        field.set(activity,value);
    }
    private void call(String name)throws Exception{
        Method method=MainActivity.class.getDeclaredMethod(name);method.setAccessible(true);
        method.invoke(activity);
    }
    private TextView text(View view,String value){
        if(view instanceof TextView && value.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            TextView found=text(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;
        }
        return null;
    }
    private View described(View view,String value){
        if(view.getContentDescription()!=null && value.contentEquals(view.getContentDescription()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View found=described(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;
        }
        return null;
    }
    @Before public void setup()throws Exception{
        controller=Robolectric.buildActivity(MainActivity.class).create().start().resume();
        activity=controller.get();store=(LocalStore)field("store");
        deck=store.newCardDeck("Тестовая подборка");
        store.addFlashcard(deck,1,1,15,1,15,"Бытие 1:15",longVerse);
        store.addFlashcard(deck,1,1,16,1,16,"Бытие 1:16","Короткий стих.");
        set("selectedDeck",deck);
        List<Long> order=(List<Long>)field("cardSequence");
        for(LocalStore.Flashcard card:store.flashcards(deck))order.add(card.id);
        set("cardIndex",0);set("cardRevealed",false);
    }
    @After public void close(){controller.pause().stop().destroy();}

    @Test public void referenceFirstRevealsFullLongTextWithoutChangingOrder()throws Exception{
        set("cardShowReference",true);call("renderCardSession");
        View root=(View)field("root");
        assertNotNull(text(root,"1 / 2"));
        assertNotNull(text(root,"Бытие 1:15"));
        assertNull(text(root,"Карточка"));
        TextView answer=text(root,longVerse);assertNotNull(answer);assertEquals(0f,answer.getAlpha(),0f);
        assertNull(answer.getEllipsize());
        View eye=described(root,"Показать ответ");assertNotNull(eye);
        eye.performClick();
        assertTrue((Boolean)field("cardRevealed"));
        assertEquals(0,field("cardIndex"));
        assertEquals("Скрыть ответ",eye.getContentDescription());
        eye.performClick();assertFalse((Boolean)field("cardRevealed"));
        call("nextFlashcard");
        assertNotNull(text((View)field("root"),"2 / 2"));
        assertNotNull(text((View)field("root"),"Короткий стих."));
    }

    @Test @Config(qualifiers="w320dp-h560dp")
    public void textFirstKeepsReferenceHiddenAndLongVerseScrollable()throws Exception{
        set("cardShowReference",false);call("renderCardSession");
        View root=(View)field("root");
        root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(560,View.MeasureSpec.EXACTLY));
        root.layout(0,0,320,560);
        assertNotNull(text(root,longVerse));
        TextView reference=text(root,"Бытие 1:15");assertNotNull(reference);
        assertEquals(0f,reference.getAlpha(),0f);
        assertNull(text(root,longVerse).getEllipsize());
        described(root,"Показать ответ").performClick();
        assertEquals("Бытие 1:15",reference.getText().toString());
        assertEquals(0,field("cardIndex"));
    }
}
