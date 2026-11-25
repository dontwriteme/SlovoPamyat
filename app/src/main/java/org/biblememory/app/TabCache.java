package org.biblememory.app;

import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.Collection;
import java.util.EnumMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Ready views are scoped to a single window and retired when its route is rebuilt. */
final class TabCache {
    static final class ReadyTab {
        final LinearLayout page;
        final LinearLayout body;
        final ScrollView scroller;
        final int homeVerseIndex;

        ReadyTab(
                LinearLayout page,
                LinearLayout body,
                ScrollView scroller,
                int homeVerseIndex) {

            this.page = page;
            this.body = body;
            this.scroller = scroller;
            this.homeVerseIndex = homeVerseIndex;
        }
    }

    private final EnumMap<Screen,ReadyTab> readyTabs=new EnumMap<>(Screen.class);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int generation;

    void put(Screen route,ReadyTab tab){readyTabs.put(route,tab);}
    Collection<ReadyTab> pages(){return readyTabs.values();}
    boolean containsKey(Screen route){return readyTabs.containsKey(route);}
    void cancelWarmup(){generation++;}
    void invalidate(){cancelWarmup();readyTabs.clear();}
    void close(){invalidate();handler.removeCallbacksAndMessages(null);}

    ReadyTab validPage(Screen route,int homeVerseIndex){
        ReadyTab tab=readyTabs.get(route);
        if(route==Screen.HOME&&tab!=null&&tab.homeVerseIndex!=homeVerseIndex){
            readyTabs.remove(route);
            return null;
        }
        return tab;
    }

    void warm(Screen active,boolean ready,Supplier<Screen> route,BooleanSupplier switching,Consumer<Screen> prepare){
        int request=++generation;
        if(!ready||!active.isMainTab())return;
        int delay=75;
        for(int index=0;index<Screen.TAB_COUNT;index++){
            Screen destination=Screen.tab(index);
            if(destination==active||readyTabs.containsKey(destination))continue;
            handler.postDelayed(()->{
                if(request!=generation||!route.get().isMainTab()||switching.getAsBoolean())return;
                prepare.accept(destination);
            },delay);
            delay+=85;
        }
    }
}
