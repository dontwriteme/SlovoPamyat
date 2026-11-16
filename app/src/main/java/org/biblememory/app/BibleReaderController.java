package org.biblememory.app;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.*;

/** Reading, range selection and cancellable search. No View or Activity dependency. */
final class BibleReaderController {
    interface Ports {
        Screen screen();
        boolean destroyed();
        Context applicationContext();
        void postToUi(Runnable callback);
        boolean replaceVisibleSearch(List<BibleSearchLogic.Hit> hits,String query,String message);
        void renderSearchKeepingInput();
        void navigate(Screen destination);
        void clearSavedScroll(Screen destination);
        int currentScroll();
        SharedPreferences chapterPreferences();
        void synchronizeCollections(Bible bible);
        void contentReady();
    }
    private final Ports ports;
    private volatile boolean closed;
    private Thread loader;
    BibleReaderController(Ports ports){this(ports,java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"OfflineBibleSearch")));}
    BibleReaderController(Ports ports,java.util.concurrent.ExecutorService executor){this.ports=ports;this.bibleSearchExecutor=executor;}
    private boolean unavailable(){return closed||ports.destroyed();}
    final Bible bible = new Bible();
    volatile String bibleError = "";
    int selectedBook = 40;
    int selectedChapter = 5;
    int focusVerse = -1;
    int readerVerseNumber = 1;
    boolean chapterFromVerseGrid = false;
    boolean selectingPassage = false;
    int rangeStartChapter = -1;
    int rangeStartVerse = -1;
    int rangeEndChapter = -1;
    int rangeEndVerse = -1;
    String bibleSearchQuery = "";
    String bibleSearchError = "";
    boolean bibleSearchActive = false;
    boolean bibleSearchLoading = false;
    List<BibleSearchLogic.Hit> bibleSearchHits = new ArrayList<>();
    int searchVisibleLimit = 25;
    int searchGeneration = 0;
    final Handler bibleSearchHandler = new Handler(Looper.getMainLooper());
    final java.util.concurrent.ExecutorService bibleSearchExecutor;
    java.util.concurrent.Future<?> bibleSearchTask;
    Runnable bibleSearchDebounce;
    boolean bibleSearchNeedsRefresh = false;
    BibleTarget pickerTarget = BibleTarget.FLASHCARD_DECK;
    long pickerTargetId = -1;
    boolean pickerRange = false;
    boolean pickerHasAnchor = false;
    boolean pickerChooseEnd = false;
    Screen pickerBackScreen = Screen.CARD_DECK;
    final PassageSelection passageSelection = new PassageSelection();
    int pickerRestoredScroll = -1;
    int pickerQuickBook = 1;
    int pickerQuickChapter = 1;
    int pickerQuickScroll = 0;
    int pickerVerseNumber = 1;
    int readerBookBeforePicker = 1;
    int readerChapterBeforePicker = 1;

    enum BibleTarget { STUDY_GROUP, FLASHCARD_DECK }

    boolean isPickerScreen(Screen value){
        return value==Screen.PICKER_BOOKS||value==Screen.PICKER_CHAPTERS||
            value==Screen.PICKER_VERSES||value==Screen.PICKER_TEXT||value==Screen.BIBLE_PICKER;
    }

    boolean isBibleSearchScreen(){return ports.screen()==Screen.BOOKS||ports.screen()==Screen.PICKER_BOOKS;}

    void cancelBibleSearchWork(){
        searchGeneration++;
        if(bibleSearchDebounce!=null)bibleSearchHandler.removeCallbacks(bibleSearchDebounce);
        bibleSearchDebounce=null;
        if(bibleSearchTask!=null)bibleSearchTask.cancel(true);
        bibleSearchTask=null;
    }

    void resumeBibleSearchIfNeeded(){
        if(!isBibleSearchScreen()||!bibleSearchNeedsRefresh)return;
        bibleSearchNeedsRefresh=false;
        if(BibleSearchLogic.isBlankQuery(bibleSearchQuery))return;
        bibleSearchActive=true;bibleSearchLoading=true;
        final int generation=searchGeneration;final Screen origin=ports.screen();
        bibleSearchHandler.post(()->{
            if(ports.screen()==origin&&generation==searchGeneration&&!unavailable())submitBibleSearch(bibleSearchQuery);
        });
    }

    void submitBibleSearch(String query){
        if(!isBibleSearchScreen()||unavailable())return;
        cancelBibleSearchWork();bibleSearchNeedsRefresh=false;
        bibleSearchQuery=query==null?"":query;
        if(BibleSearchLogic.isBlankQuery(bibleSearchQuery)){
            boolean changed=bibleSearchActive||bibleSearchLoading||!bibleSearchHits.isEmpty()||!bibleSearchError.isEmpty();
            bibleSearchActive=false;bibleSearchLoading=false;bibleSearchError="";
            bibleSearchHits=java.util.Collections.emptyList();
            if(changed)ports.renderSearchKeepingInput();
            return;
        }
        bibleSearchActive=true;bibleSearchLoading=true;bibleSearchError="";
        bibleSearchHits=java.util.Collections.emptyList();
        ports.replaceVisibleSearch(bibleSearchHits,bibleSearchQuery,"Поиск...");
        final String search=bibleSearchQuery;
        final int generation=searchGeneration;
        final Screen origin=ports.screen();
        bibleSearchTask=bibleSearchExecutor.submit(()->{
            List<BibleSearchLogic.Hit> results;
            String error="";
            try{
                bible.load(ports.applicationContext());BibleSearchLogic.prepare(ports.applicationContext());
                results=BibleSearchLogic.search(bible,search);
            }catch(Exception failure){results=java.util.Collections.emptyList();error="Не удалось выполнить поиск.";}
            if(Thread.currentThread().isInterrupted())return;
            final List<BibleSearchLogic.Hit> ready=results;
            final String failure=error;
            ports.postToUi(()->{
                if(generation!=searchGeneration||ports.screen()!=origin||unavailable())return;
                bibleSearchLoading=false;bibleSearchHits=ready;bibleSearchError=failure;
                if(!failure.isEmpty()||!ports.replaceVisibleSearch(ready,search,null))ports.renderSearchKeepingInput();
            });
        });
    }

    void openBiblePicker(BibleTarget target,long targetId,Screen back){
        readerBookBeforePicker=selectedBook;readerChapterBeforePicker=selectedChapter;
        passageSelection.cancel();passageSelection.book=0;passageSelection.collectionId=targetId;
        pickerTarget=target;pickerTargetId=targetId;pickerBackScreen=back;
        pickerRange=false;pickerHasAnchor=false;pickerChooseEnd=false;
        pickerVerseNumber=1;pickerRestoredScroll=-1;
        clearPassageSelection();focusVerse=-1;
        ports.clearSavedScroll(Screen.PICKER_BOOKS);
        ports.clearSavedScroll(Screen.PICKER_CHAPTERS);
        ports.clearSavedScroll(Screen.PICKER_VERSES);
        bibleSearchActive=false;bibleSearchQuery="";bibleSearchHits=new ArrayList<>();
        ports.navigate(Screen.PICKER_BOOKS);
    }

    void openPickerRange(){
        pickerQuickBook=selectedBook;pickerQuickChapter=selectedChapter;
        pickerQuickScroll=ports.currentScroll();
        passageSelection.openBook(selectedBook);passageSelection.active=true;
        ports.navigate(Screen.BIBLE_PICKER);
    }

    void beginPickerBoundary(boolean end){
        pickerChooseEnd=end;
        selectedBook=passageSelection.book;
        int previous=end?passageSelection.endChapter:passageSelection.startChapter;
        selectedChapter=previous>0?previous:passageSelection.startChapter>0?
            passageSelection.startChapter:pickerQuickChapter;
        ports.clearSavedScroll(Screen.PICKER_CHAPTERS);ports.clearSavedScroll(Screen.PICKER_VERSES);
        pickerRestoredScroll=-1;
        ports.navigate(selectedBook>0?Screen.PICKER_CHAPTERS:Screen.PICKER_BOOKS);
    }

    String pickerBoundaryReference(boolean end){
        int c=end?passageSelection.endChapter:passageSelection.startChapter;
        int v=end?passageSelection.endVerse:passageSelection.startVerse;
        return c>0&&v>0?Bible.reference(passageSelection.book,c,v,c,v):"Не выбрано";
    }

    int lastReadChapter(int book){
        int saved=ports.chapterPreferences().getInt(String.valueOf(book),1);
        return Math.max(1,Math.min(Math.max(1,bible.chapterCount(book)),saved));
    }

    void rememberChapter(int book,int chapter){
        ports.chapterPreferences().edit()
            .putInt(String.valueOf(book),chapter).apply();
    }

    void openReaderVerseGrid(int chapter) {
        selectedChapter=chapter;readerVerseNumber=1;
        rememberChapter(selectedBook,chapter);
        ports.clearSavedScroll(Screen.VERSES);
        ports.navigate(Screen.VERSES);
    }

    void clearPassageSelection(){
        rangeStartChapter=-1;rangeStartVerse=-1;rangeEndChapter=-1;rangeEndVerse=-1;
    }

    boolean inPassageRange(int chapter,int verse){
        if(rangeStartChapter<0)return false;
        return PassageRangeLogic.contains(rangeStartChapter,rangeStartVerse,
            rangeEndChapter<0?rangeStartChapter:rangeEndChapter,
            rangeEndChapter<0?rangeStartVerse:rangeEndVerse,chapter,verse);
    }

    String passageSummary(){
        if(rangeStartChapter<0)return "Отрывок не выбран";
        int ec=rangeEndChapter<0?rangeStartChapter:rangeEndChapter;
        int ev=rangeEndChapter<0?rangeStartVerse:rangeEndVerse;
        return Bible.reference(selectedBook,rangeStartChapter,rangeStartVerse,ec,ev);
    }
    void startLoading(){
        if(loader!=null||closed)return;
        final Context application=ports.applicationContext();
        loader=new Thread(()->{
            try{
                bible.load(application);
                try{BibleSearchLogic.prepare(application);}catch(Exception ignored){}
                if(unavailable()||Thread.currentThread().isInterrupted())return;
                ports.synchronizeCollections(bible);
            }catch(Exception failure){
                if(!unavailable())bibleError=failure.getMessage()==null?"Невозможно открыть текст":failure.getMessage();
            }
            if(unavailable()||Thread.currentThread().isInterrupted())return;
            ports.postToUi(()->{if(!unavailable())ports.contentReady();});
        },"BibleLoader");loader.start();
    }
    void close(){
        closed=true;cancelBibleSearchWork();bibleSearchHandler.removeCallbacksAndMessages(null);
        bibleSearchExecutor.shutdownNow();if(loader!=null)loader.interrupt();
    }
    void onQueryChanged(String value){
        if(!isBibleSearchScreen()||unavailable())return;
        cancelBibleSearchWork();bibleSearchNeedsRefresh=false;bibleSearchQuery=value;
        if(BibleSearchLogic.isBlankQuery(value)){
            boolean changed=bibleSearchActive||bibleSearchLoading||!bibleSearchHits.isEmpty();
            bibleSearchActive=false;bibleSearchLoading=false;bibleSearchError="";bibleSearchHits=Collections.emptyList();
            if(changed)ports.renderSearchKeepingInput();return;
        }
        bibleSearchActive=true;bibleSearchLoading=true;bibleSearchError="";bibleSearchHits=Collections.emptyList();
        ports.replaceVisibleSearch(bibleSearchHits,value,"Поиск...");
        final Screen origin=ports.screen();final int generation=searchGeneration;
        bibleSearchDebounce=()->{if(ports.screen()==origin&&generation==searchGeneration&&!unavailable())submitBibleSearch(value);};
        bibleSearchHandler.postDelayed(bibleSearchDebounce,300);
    }
    void selectVerse(boolean reader,int verse){
        Screen destination;
        if(reader){readerVerseNumber=verse;focusVerse=verse;chapterFromVerseGrid=true;destination=Screen.CHAPTER;}
        else if(passageSelection.active){passageSelection.setBoundary(pickerChooseEnd,selectedChapter,verse);destination=Screen.BIBLE_PICKER;}
        else{pickerVerseNumber=verse;focusVerse=verse;destination=Screen.PICKER_TEXT;}
        ports.navigate(destination);
    }
    void savePickerState(Bundle out){
        out.putString("picker_screen",ports.screen().name());out.putString("picker_target",pickerTarget.name());
        out.putString("picker_back",pickerBackScreen.name());out.putLong("picker_id",pickerTargetId);
        out.putInt("picker_book",selectedBook);out.putInt("picker_chapter",selectedChapter);
        out.putInt("reader_book",readerBookBeforePicker);out.putInt("reader_chapter",readerChapterBeforePicker);
        out.putInt("picker_scroll",ports.currentScroll());out.putBoolean("picker_active",passageSelection.active);
        out.putBoolean("picker_choose_end",pickerChooseEnd);
        out.putIntArray("picker_quick",new int[]{pickerQuickBook,pickerQuickChapter,pickerQuickScroll,pickerVerseNumber});
        out.putIntArray("picker_bounds",new int[]{passageSelection.book,passageSelection.startChapter,passageSelection.startVerse,passageSelection.endChapter,passageSelection.endVerse});
    }
    boolean restorePickerState(Bundle state){
        if(state==null||!state.containsKey("picker_screen"))return false;
        try{
            pickerTarget=BibleTarget.valueOf(state.getString("picker_target"));pickerBackScreen=Screen.valueOf(state.getString("picker_back"));
            pickerTargetId=state.getLong("picker_id");selectedBook=state.getInt("picker_book");selectedChapter=state.getInt("picker_chapter");
            readerBookBeforePicker=state.getInt("reader_book",1);readerChapterBeforePicker=state.getInt("reader_chapter",1);
            passageSelection.collectionId=pickerTargetId;passageSelection.chapter=selectedChapter;
            int[] b=state.getIntArray("picker_bounds");
            if(b!=null&&b.length==5){passageSelection.book=b[0];passageSelection.startChapter=b[1];passageSelection.startVerse=b[2];passageSelection.endChapter=b[3];passageSelection.endVerse=b[4];}
            passageSelection.active=state.getBoolean("picker_active");pickerChooseEnd=state.getBoolean("picker_choose_end");
            int[] quick=state.getIntArray("picker_quick");
            if(quick!=null&&quick.length==4){pickerQuickBook=quick[0];pickerQuickChapter=quick[1];pickerQuickScroll=quick[2];pickerVerseNumber=quick[3];}
            pickerRestoredScroll=state.getInt("picker_scroll",0);return true;
        }catch(IllegalArgumentException|NullPointerException invalid){passageSelection.cancel();return false;}
    }


    /** Activity/process recreation snapshot; durable progress still belongs to LocalStore. */
    Bundle saveState(){
        Bundle state=new Bundle();
        state.putInt("selectedBook",selectedBook);
        state.putInt("selectedChapter",selectedChapter);
        state.putInt("focusVerse",focusVerse);
        state.putInt("readerVerseNumber",readerVerseNumber);
        state.putBoolean("chapterFromVerseGrid",chapterFromVerseGrid);
        state.putBoolean("selectingPassage",selectingPassage);
        state.putInt("rangeStartChapter",rangeStartChapter);
        state.putInt("rangeStartVerse",rangeStartVerse);
        state.putInt("rangeEndChapter",rangeEndChapter);
        state.putInt("rangeEndVerse",rangeEndVerse);
        state.putString("bibleSearchQuery",bibleSearchQuery);
        state.putInt("searchVisibleLimit",searchVisibleLimit);
        state.putString("pickerTarget",pickerTarget.name());
        state.putLong("pickerTargetId",pickerTargetId);
        state.putBoolean("pickerRange",pickerRange);
        state.putBoolean("pickerHasAnchor",pickerHasAnchor);
        state.putBoolean("pickerChooseEnd",pickerChooseEnd);
        state.putString("pickerBackScreen",pickerBackScreen.name());
        state.putInt("pickerQuickBook",pickerQuickBook);
        state.putInt("pickerQuickChapter",pickerQuickChapter);
        state.putInt("pickerQuickScroll",pickerQuickScroll);
        state.putInt("pickerVerseNumber",pickerVerseNumber);
        state.putInt("readerBookBeforePicker",readerBookBeforePicker);
        state.putInt("readerChapterBeforePicker",readerChapterBeforePicker);
        state.putBoolean("searchActive",bibleSearchActive);
        state.putLong("selectionCollection",passageSelection.collectionId);
        state.putIntArray("selection",new int[]{passageSelection.book,passageSelection.chapter,passageSelection.startChapter,passageSelection.startVerse,passageSelection.endChapter,passageSelection.endVerse});
        state.putBoolean("selectionActive",passageSelection.active);
        return state;
    }
    void restoreState(Bundle state){
        if(state==null)return;
        selectedBook=state.getInt("selectedBook",selectedBook);
        selectedChapter=state.getInt("selectedChapter",selectedChapter);
        focusVerse=state.getInt("focusVerse",focusVerse);
        readerVerseNumber=state.getInt("readerVerseNumber",readerVerseNumber);
        chapterFromVerseGrid=state.getBoolean("chapterFromVerseGrid",chapterFromVerseGrid);
        selectingPassage=state.getBoolean("selectingPassage",selectingPassage);
        rangeStartChapter=state.getInt("rangeStartChapter",rangeStartChapter);
        rangeStartVerse=state.getInt("rangeStartVerse",rangeStartVerse);
        rangeEndChapter=state.getInt("rangeEndChapter",rangeEndChapter);
        rangeEndVerse=state.getInt("rangeEndVerse",rangeEndVerse);
        bibleSearchQuery=state.getString("bibleSearchQuery",bibleSearchQuery);
        searchVisibleLimit=state.getInt("searchVisibleLimit",searchVisibleLimit);
        try{pickerTarget=BibleTarget.valueOf(state.getString("pickerTarget",pickerTarget.name()));}catch(IllegalArgumentException ignored){}
        pickerTargetId=state.getLong("pickerTargetId",pickerTargetId);
        pickerRange=state.getBoolean("pickerRange",pickerRange);
        pickerHasAnchor=state.getBoolean("pickerHasAnchor",pickerHasAnchor);
        pickerChooseEnd=state.getBoolean("pickerChooseEnd",pickerChooseEnd);
        try{pickerBackScreen=Screen.valueOf(state.getString("pickerBackScreen",pickerBackScreen.name()));}catch(IllegalArgumentException ignored){}
        pickerQuickBook=state.getInt("pickerQuickBook",pickerQuickBook);
        pickerQuickChapter=state.getInt("pickerQuickChapter",pickerQuickChapter);
        pickerQuickScroll=state.getInt("pickerQuickScroll",pickerQuickScroll);
        pickerVerseNumber=state.getInt("pickerVerseNumber",pickerVerseNumber);
        readerBookBeforePicker=state.getInt("readerBookBeforePicker",readerBookBeforePicker);
        readerChapterBeforePicker=state.getInt("readerChapterBeforePicker",readerChapterBeforePicker);
        cancelBibleSearchWork();bibleSearchLoading=false;bibleSearchHits=Collections.emptyList();bibleSearchError="";
        bibleSearchActive=state.getBoolean("searchActive");bibleSearchNeedsRefresh=bibleSearchActive&&!BibleSearchLogic.isBlankQuery(bibleSearchQuery);
        passageSelection.collectionId=state.getLong("selectionCollection",-1);
        int[] bounds=state.getIntArray("selection");if(bounds!=null&&bounds.length==6){
            passageSelection.book=bounds[0];passageSelection.chapter=bounds[1];passageSelection.startChapter=bounds[2];
            passageSelection.startVerse=bounds[3];passageSelection.endChapter=bounds[4];passageSelection.endVerse=bounds[5];
        }passageSelection.active=state.getBoolean("selectionActive");
    }
}
