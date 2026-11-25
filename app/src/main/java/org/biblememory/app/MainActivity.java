package org.biblememory.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Path;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.net.Uri;
import android.text.*;
import android.text.method.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import android.view.inputmethod.EditorInfo;
import java.util.*;

import org.biblememory.app.LessonSessionState.AnswerReview;
import org.biblememory.app.LessonSessionState.TrainingMode;

import static org.biblememory.app.ScreenUi.*;

/** Bright, entirely native Android UI with zero runtime dependencies. */
public final class MainActivity extends Activity {
    private final ScreenUi ui = new ScreenUi(this);
    private final TabCache tabCache=new TabCache();
    private final BottomNavigationView bottomNavigation=new BottomNavigationView(this,ui);
    private final TabTransition tabTransition=new TabTransition(ui);
    private final NavigationGesture navigationGesture=new NavigationGesture(ui);
    private final LessonScreenView lessonScreen=new LessonScreenView(this,ui);
    private final LessonCompletionView lessonCompletion=new LessonCompletionView(this,ui);
    private final LessonProgressView lessonProgress=new LessonProgressView(this,ui);
    private final LessonReadingView readingView=new LessonReadingView(this,ui);
    private final ScreenMenus menus = new ScreenMenus(ui);
    private final ModeInfoPopover modeInfo = new ModeInfoPopover(ui);
    private final ScreenArtwork artwork=new ScreenArtwork(this,ui);
    private final SettingsView settingsView=new SettingsView(this,ui,artwork);
    private final CollectionViews collectionViews=new CollectionViews(this,ui,artwork);
    private final CollectionDialogs collectionDialogs=new CollectionDialogs(this,ui);
    // The warm landscape-based interface is shared by every screen, not just the home hero.
    private static final int EXPORT=10,IMPORT=11;
    private Screen screen=Screen.HOME;
    private static final String UNIFIED_PREFS=ActiveCollection.PREFS;
    private final ActiveCollection activeCollection=new ActiveCollection(this);
    private long openUnifiedId=-1;
    private boolean cardFromUnified=false;
    private final ReviewSession reviewSession=new ReviewSession();
    private KnowledgeCheckSession knowledgeCheck;
    private final KnowledgeCheckView knowledgeCheckView = new KnowledgeCheckView(ui);

    private LocalStore store;
    private final Bible bible=Bible.shared();
    private final HomePage home=new HomePage(this,ui,artwork,this.bible,modeInfo,()->{
        if(this.screen==Screen.HOME)render();
    });
    private final PassagePicker passagePicker=new PassagePicker(this,ui,bible);
    private final BibleReader bibleReader=new BibleReader(this,ui,bible);
    private java.util.concurrent.CompletableFuture<Void> bibleStartup;
    private volatile String bibleError="";
    private int selectedBook=40,selectedChapter=5;
    private int focusVerse=-1;
    private boolean focusPickerSearchHit;
    private long selectedGroup=1;
    private long customTargetGroupId=-1; // A text created via a collection menu belongs to that collection.
    private ScrollView currentScroller;
    // The Home page is a single scroll surface; its expansion and scroll position survive navigation.
    private Screen lastRenderedScreen=null;
    private final LessonSessionState lesson=new LessonSessionState();
    private final LessonWritingView writingView=new LessonWritingView(this,ui,lesson.lessonWriting);
    private final LessonAnswerReviewView answerReviewView=new LessonAnswerReviewView(ui);
    private LinearLayout content,root;
    private static final String SESSION_PREFS="study_session";
    private final LessonAssemblyView assemblyView=new LessonAssemblyView(this,ui,lesson.lessonAssembly);
    private final LessonAssemblyLayout assemblyLayout=new LessonAssemblyLayout(this);
    // Cache one active passage's variant-specific fragments. Esther 8:9 is long:
    // recomputing all three layouts on each tile tap stalls the UI thread.

    // One selection model shared by tapping verses and the chapter/verse range picker.
    private boolean selectingPassage=false; // legacy selection mode retained only for old saved preferences
    private int rangeStartChapter=-1,rangeStartVerse=-1,rangeEndChapter=-1,rangeEndVerse=-1;


    private BibleSearchSession bibleSearch;
    private final BibleSearchView bibleSearchView = new BibleSearchView(ui,this::openSearchHit);

    private final NavigationScroll navigationScroll=new NavigationScroll();
    private final List<Long> collectionQueue=new ArrayList<>();
    private int collectionQueueCursor=-1;
    private long collectionQueueGroup=-1;
    private boolean executingCollectionQueue=false;
    private boolean collectionQueueNewStep=false;
    private long insertAfterEntryId=-1,insertAfterCardId=-1;
    // Each subsequent choice goes after the last inserted item, not the original anchor.
    private final List<Long> studyInsertionTrail=new ArrayList<>();
    private final List<Long> cardInsertionTrail=new ArrayList<>();


    private void prepareOtherTabs(){
        if(isBibleSearchScreen()&&bibleSearch.active())return;
        tabCache.warm(screen,bible.isReady(),()->screen,tabTransition::inProgress,this::prepareTab);
    }

    private void prepareTab(Screen destination) {
        if (tabCache.containsKey(destination)) return;

        Screen previousScreen = screen;
        Screen previousRendered = lastRenderedScreen;

        LinearLayout previousRoot = root;
        LinearLayout previousBody = content;
        ScrollView previousScroller = currentScroller;

        int previousNav = bottomNavigation.lastIndex();

        try {
            screen = destination;

            root = ui.column();
            root.setBackground(ui.pageBackground());

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);

            currentScroller = scroll;

            content = ui.column();
            content.setPadding(
                    ui.dp(19), ui.dp(17), ui.dp(19), ui.dp(27)
            );

            scroll.addView(content);

            if (destination == Screen.BOOKS) {
                bibleSearchView.searchBar(content,bibleSearch,screen!=Screen.BOOKS);
            }

            root.addView(
                    scroll,
                    new LinearLayout.LayoutParams(-1, 0, 1)
            );

            if (destination == Screen.HOME) {
                renderHome();
            } else if (destination == Screen.BOOKS) {
                renderBooks();

                if (destination == Screen.BOOKS &&
                        bibleSearch.active() &&
                        !bibleSearch.loading() &&
                        bibleSearch.error().isEmpty()) {

                    root.removeView(scroll);

                    root.addView(
                            bibleSearchView.results(this,bibleSearch,screen!=Screen.BOOKS),
                            new LinearLayout.LayoutParams(-1, 0, 1)
                    );

                    currentScroller = null;
                }

            } else if(destination==Screen.COLLECTIONS) {
                renderUnifiedCollections();
                addUnifiedCollectionFooter();
            } else {
                renderSettings();
            }

            addNav(root);

            tabCache.put(
                    destination,
                    new TabCache.ReadyTab(
                            root, content, currentScroller, home.index()
                    )
            );

        } finally {
            screen = previousScreen;
            lastRenderedScreen = previousRendered;

            root = previousRoot;
            content = previousBody;
            currentScroller = previousScroller;

            bottomNavigation.setLastIndex(previousNav);
        }
    }

    private final String COLLECTION_PREFS="collection_study_queue";
    private boolean resetInProgress=false;
    private boolean backupInProgress=false;
    private boolean ownsData,resumed;
    private Bundle startupState;
    private Bundle lessonWaitingForBible;
    private String cardStudyMaterial="";
    private String dataGeneration="";
    private AlertDialog backupDialog;
    private long selectedDeck=-1;
    private FlashcardSession flashcards;
    private enum BibleTarget { STUDY_GROUP, FLASHCARD_DECK }
    private BibleTarget pickerTarget=BibleTarget.FLASHCARD_DECK;
    private long pickerTargetId=-1;
    private Screen pickerBackScreen=Screen.CARD_DECK;
    private int pickerRestoredScroll=-1;
    private int pickerQuickBook=1,pickerQuickChapter=1,pickerQuickScroll=0,pickerVerseNumber=1;
    private int readerBookBeforePicker=1,readerChapterBeforePicker=1;

    // A card collection is studied as separate saved passages, never as one concatenated text.
    private final List<Long> cardStudyQueue=new ArrayList<>();
    private int cardStudyCursor=-1;
    private long cardStudyDeckId=-1;






    private final java.util.concurrent.ExecutorService collectionExecutor=
        java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"CollectionProgress"));
    private final Handler collectionHandler=new Handler(android.os.Looper.getMainLooper());
    private java.util.concurrent.Future<?> collectionTask;
    private LocalStore collectionStore;
    private int openedCollectionChapter;
    private SortedMap<Integer,Long> openedCollectionBook;
    private CollectionProgressSnapshot openedBookProgress;
    private String openedBookName;

    private long collectionGeneration;
    private boolean collectionDetailLoading;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        flashcards=new FlashcardSession(getSharedPreferences("card_sessions",MODE_PRIVATE),new Random());
        bibleSearch = new BibleSearchSession(getApplicationContext(),bible,new BibleSearchSession.Host(){
            @Override public BibleSearchSession.Scope scope(){return activeSearchScope();}
            @Override public void showSearchUpdate(boolean reuse,String message){
                if(reuse&&bibleSearchView.updateList(root,bibleSearch,message))return;
                if(message==null)renderBibleSearchKeepingInput();
            }
        });
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(CARD);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        startupState=savedInstanceState;
        if(BackupCoordinator.attach(this))openData();
    }

    private void openData(){
        store=new LocalStore(this,bible);
        try{
            if(SvetochBackup.pending(store)){
                store.close();store=null;
                BackupCoordinator.request(this,BackupCoordinator.Operation.RECOVER,null);
                return;
            }
        }catch(RuntimeException failure){
            store.close();store=null;
            showBackupFailure(failure);
            return;
        }
        ownsData=true;
        BackupCoordinator.opened();
        dataGeneration=SvetochBackup.generation(this);
        Bundle savedInstanceState=startupState;
        startupState=null;
        if(savedInstanceState!=null&&!dataGeneration.equals(savedInstanceState.getString("data_generation","")))
            savedInstanceState=null;
        if(savedInstanceState!=null&&savedInstanceState.getBoolean("backup_return_settings"))
            screen=Screen.SETTINGS;
        collectionStore=new LocalStore(getApplicationContext());
        try{
            LearningReset.recover(getApplicationContext(),store);
        }catch(Exception error){
            android.util.Log.e("LearningReset","Interrupted reset could not be completed",error);
            new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                .setTitle("Не удалось завершить сброс")
                .setMessage("Закрой приложение и открой снова, чтобы завершить начатый сброс прогресса.")
                .setCancelable(false).setPositiveButton("Закрыть",(d,w)->finish()).show();
            return;
        }
        knowledgeCheck = new KnowledgeCheckSession(getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE));
        activeCollection.load();
        ensureActiveUnified();
        restoreCollectionQueue();
        home.open();
        restorePickerState(savedInstanceState);
        Bundle lesson=savedInstanceState==null?null:savedInstanceState.getBundle("active_lesson");
        if(lesson!=null&&lesson.containsKey("review_group")&&!bible.isReady())
            lessonWaitingForBible=lesson;
        else restoreActiveLesson(lesson);
        render();
        // The worker owns collectionStore until its queued close in onDestroy.
        if(resumed)home.schedule();
        bibleStartup=loadBibleForScreen(bible,getApplicationContext(),this);
    }

    private static java.util.concurrent.CompletableFuture<Void> loadBibleForScreen(
            Bible bible,Context context,MainActivity activity){
        java.lang.ref.WeakReference<MainActivity> target=new java.lang.ref.WeakReference<>(activity);
        Handler main=new Handler(Looper.getMainLooper());
        return bible.loadInBackground(context).whenComplete((ignored,failure)->
            main.post(()->{
                MainActivity current=target.get();
                if(current!=null)current.onBibleLoaded(failure);
            }));
    }

    private void onBibleLoaded(Throwable failure){
        if(isDestroyed()||isFinishing()||!ownsData||backupInProgress)return;
        if(failure!=null){
            Throwable cause=failure.getCause()==null?failure:failure.getCause();
            bibleError=cause.getMessage()==null?"Невозможно открыть текст":cause.getMessage();
            android.util.Log.e("BibleLoader","Unable to load Bible corpus",cause);
        }
        Bundle lesson=lessonWaitingForBible;
        lessonWaitingForBible=null;
        if(failure==null&&screen==Screen.HOME&&restoreActiveLesson(lesson))render();
        else refreshAfterBibleLoad();
        if(failure==null)collectionExecutor.execute(this::syncCollectionsAfterBibleLoad);
    }

    private void syncCollectionsAfterBibleLoad(){
        try{BibleSearchLogic.prepare(getApplicationContext());}
        catch(Exception error){android.util.Log.w("BibleLoader","Unable to prepare Bible search",error);}
        try{
            for(LocalStore.UnifiedCollection u:collectionStore.unifiedCollections()){
                try{collectionStore.syncUnifiedCollection(u.id,bible);}
                catch(RuntimeException error){android.util.Log.w("BibleLoader","Unable to sync collection "+u.id,error);}
            }
        }catch(RuntimeException error){android.util.Log.w("BibleLoader","Unable to list collections",error);}
        collectionHandler.post(()->{
            if(screen==Screen.HOME||screen==Screen.COLLECTIONS||screen==Screen.COLLECTION_DETAIL||
                isPickerScreen(screen))refreshAfterBibleLoad();
        });
    }

    private void refreshAfterBibleLoad(){
        if(isDestroyed()||isFinishing()||backupInProgress)return;
        if(isBibleSearchScreen())renderBibleSearchKeepingInput();
        else if(screen==Screen.HOME||screen==Screen.CHAPTERS||screen==Screen.VERSES
                ||screen==Screen.CHAPTER||screen==Screen.CARD_PICK||isPickerScreen(screen)
                ||screen==Screen.COLLECTIONS||screen==Screen.COLLECTION_DETAIL)render();
    }

    private boolean isPickerScreen(Screen value){
        return value==Screen.PICKER_BOOKS||value==Screen.PICKER_CHAPTERS||
            value==Screen.PICKER_VERSES||value==Screen.PICKER_TEXT||value==Screen.BIBLE_PICKER;
    }
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);
        if(!ownsData){
            if(startupState!=null)out.putAll(startupState);
            return;
        }
        out.putString("data_generation",dataGeneration);
        if(backupInProgress&&screen==Screen.SETTINGS)out.putBoolean("backup_return_settings",true);
        if(!backupInProgress&&!resetInProgress){
            Bundle lesson=screen==Screen.TRAIN?saveActiveLesson():lessonWaitingForBible;
            if(lesson!=null)out.putBundle("active_lesson",lesson);
        }
        if(!isPickerScreen(screen))return;
        out.putString("picker_screen",screen.name());out.putString("picker_target",pickerTarget.name());
        out.putString("picker_back",pickerBackScreen.name());out.putLong("picker_id",pickerTargetId);
        out.putLong("picker_unified",openUnifiedId);out.putLong("picker_group",selectedGroup);
        out.putLong("picker_deck",selectedDeck);
        out.putInt("picker_book",selectedBook);out.putInt("picker_chapter",selectedChapter);
        out.putInt("reader_book",readerBookBeforePicker);out.putInt("reader_chapter",readerChapterBeforePicker);
        out.putInt("picker_scroll",currentScroller==null?0:currentScroller.getScrollY());
        passagePicker.saveBounds(out);
        out.putIntArray("picker_quick",new int[]{pickerQuickBook,pickerQuickChapter,pickerQuickScroll,pickerVerseNumber});
        out.putLong("picker_insert_entry",insertAfterEntryId);out.putLong("picker_insert_card",insertAfterCardId);
    }
    private void restorePickerState(Bundle state){
        if(state==null||!state.containsKey("picker_screen"))return;
        try{
            screen=Screen.valueOf(state.getString("picker_screen"));
            pickerTarget=BibleTarget.valueOf(state.getString("picker_target"));
            pickerBackScreen=Screen.valueOf(state.getString("picker_back"));
            pickerTargetId=state.getLong("picker_id");openUnifiedId=state.getLong("picker_unified");
            selectedGroup=state.getLong("picker_group");selectedDeck=state.getLong("picker_deck");
            selectedBook=state.getInt("picker_book");selectedChapter=state.getInt("picker_chapter");
            readerBookBeforePicker=state.getInt("reader_book",1);readerChapterBeforePicker=state.getInt("reader_chapter",1);
            passagePicker.restoreBounds(state,pickerTargetId,selectedChapter);
            int[] quick=state.getIntArray("picker_quick");
            if(quick!=null&&quick.length==4){pickerQuickBook=quick[0];pickerQuickChapter=quick[1];
                pickerQuickScroll=quick[2];pickerVerseNumber=quick[3];}
            pickerRestoredScroll=state.getInt("picker_scroll",0);
            insertAfterEntryId=state.getLong("picker_insert_entry",-1);insertAfterCardId=state.getLong("picker_insert_card",-1);
        }catch(IllegalArgumentException ignored){screen=Screen.HOME;passagePicker.selection().cancel();}
    }
    @Override protected void onResume() {
        super.onResume();
        resumed=true;
        if(!ownsData||backupInProgress)return;
        bottomNavigation.settleBibleIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleBibleIcon(tab.page);
        bottomNavigation.settleHomeIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleHomeIcon(tab.page);
        bottomNavigation.settleCollectionsIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleCollectionsIcon(tab.page);
        if(store!=null && root!=null){home.refresh(true);home.schedule();}
    }
    @Override protected void onPause() {
        cancelWritingCheck();
        resumed=false;
        if(!ownsData||backupInProgress){super.onPause();return;}
        modeInfo.dismiss(false);
        bottomNavigation.settleBibleIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleBibleIcon(tab.page);
        bottomNavigation.settleHomeIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleHomeIcon(tab.page);
        bottomNavigation.settleCollectionsIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleCollectionsIcon(tab.page);
        readingView.releaseReveal();
        if(screen==Screen.TRAIN)saveTrainingSession();
        if(screen==Screen.CARD_SESSION)flashcards.save(selectedDeck);
        if(screen==Screen.RUN)knowledgeCheck.save();
        rememberHomeScroll();
        home.pause();
        super.onPause();
    }
    @Override public void onBackPressed() {
        if(resetInProgress||backupInProgress)return;
        if(modeInfo.dismiss(true))return;
        lessonWaitingForBible=null;
        switch(screen) {
            case HOME: super.onBackPressed(); break;
            case BOOKS:case DUE:case CARDS:case COLLECTIONS:case SETTINGS:screen=Screen.HOME;render();break;
            case COLLECTION_DETAIL:screen=Screen.COLLECTIONS;render();break;
            case COLLECTION_BOOK:
                openedCollectionBook=null;openedBookProgress=null;
                screen=Screen.COLLECTION_DETAIL;render();break;
            case COLLECTION_CHAPTER:screen=Screen.COLLECTION_BOOK;render();break;
            case RUN:knowledgeCheck.save();screen=Screen.HOME;render();break;
            case RUN_SUMMARY:screen=Screen.HOME;render();break;
            case CARD_DECK:screen=Screen.CARDS;render();break;
            case CARD_PICK:screen=Screen.CARD_DECK;render();break;
            case BIBLE_PICKER:
                passagePicker.selection().active=false;passagePicker.chooseEnd(false);
                selectedBook=pickerQuickBook;selectedChapter=pickerQuickChapter;
                pickerRestoredScroll=pickerQuickScroll;focusVerse=-1;
                screen=Screen.PICKER_TEXT;render();break;
            case PICKER_BOOKS:
                if(passagePicker.selection().active){screen=Screen.BIBLE_PICKER;render();break;}
                clearInsertionContext();passagePicker.selection().cancel();
                selectedBook=readerBookBeforePicker;selectedChapter=readerChapterBeforePicker;
                screen=pickerBackScreen;render();break;
            case PICKER_CHAPTERS:
                if(passagePicker.hasChapters()){passagePicker.clearPickerChapterSelection(selectedChapter);break;}
                screen=passagePicker.selection().active?Screen.BIBLE_PICKER:Screen.PICKER_BOOKS;render();break;
            case PICKER_VERSES:screen=Screen.PICKER_CHAPTERS;render();break;
            case PICKER_TEXT:screen=Screen.PICKER_VERSES;render();break;
            case CARD_SESSION:flashcards.save(selectedDeck);screen=cardFromUnified?Screen.HOME:Screen.CARD_DECK;render();break;
            case CHAPTERS:screen=Screen.BOOKS;render();break;
            case VERSES:screen=Screen.CHAPTERS;render();break;
            case CHAPTER:screen=bibleReader.fromVerseGrid()?Screen.VERSES:Screen.CHAPTERS;render();break;
            case ENTRIES:screen=Screen.HOME;render();break;
            case REORDER_STUDY:
                {LocalStore.UnifiedCollection u=store.unifiedForGroup(selectedGroup);
                screen=u==null?Screen.ENTRIES:Screen.COLLECTION_DETAIL;
                if(u!=null)openUnifiedId=u.id;render();break;}
            case REORDER_CARDS:screen=Screen.CARD_DECK;render();break;
            case CUSTOM:{
                LocalStore.UnifiedCollection u=store.unifiedForGroup(customTargetGroupId);
                customTargetGroupId=-1;screen=u==null?Screen.HOME:Screen.COLLECTION_DETAIL;
                if(u!=null)openUnifiedId=u.id;render();break;}
            case TRAIN:
                executingCollectionQueue=false;
                cardStudyQueue.clear();cardStudyCursor=-1;cardStudyDeckId=-1;
                saveTrainingSession();
                lesson.answerReview=null;
                reviewSession.clear();lesson.groupMode=false;lesson.forcedStep=null;
                // The lesson is an isolated activity: every exit goes to HOME.
                screen=Screen.HOME;render();break;
        }
    }
    /** Decorative scenery only; long reading passages remain on opaque ivory cards. */
    private void illustratedBanner(LinearLayout into,String heading,long scene,int height){
        FrameLayout art=new FrameLayout(this);
        art.setBackground(ui.outlined(CARD,22,BORDER));art.setClipToOutline(true);
        ImageView landscape=new ImageView(this);
        landscape.setImageResource(artwork.landscapeFor(scene));
        landscape.setScaleType(ImageView.ScaleType.CENTER_CROP);
        landscape.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        art.addView(landscape,new FrameLayout.LayoutParams(-1,-1));
        View scrim=new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x55FFF7EC,0x33FFF2E5,0x99FFF0DF}));
        art.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        if(heading!=null&&!heading.isEmpty()){
            TextView name=ui.displayText(heading,26);name.setGravity(Gravity.CENTER);
            name.setShadowLayer(ui.dp(4),0,ui.dp(2),0xAAFFF5E9);
            FrameLayout.LayoutParams label=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
            label.setMargins(ui.dp(14),ui.dp(16),ui.dp(14),0);art.addView(name,label);
        }
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,ui.dp(height));
        p.topMargin=ui.dp(12);into.addView(art,p);
    }
    private int number(EditText e){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return -1;}}
    private String value(EditText e){return e.getText().toString().trim();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    /** Compact wordmark for primary tabs: no redundant page title or empty title spacing. */
    private void tabWordmark(){
        // The app name intentionally stays on the Android launcher, not within screens.
    }
    private void title(String main,String subtitle) {
        TextView heading=ui.displayText(main,26);
        heading.setGravity(Gravity.CENTER);
        heading.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        ui.add(content,heading,8);
        if(subtitle!=null&&!subtitle.isEmpty()){
            TextView sub=ui.txt(subtitle,14,MUTED,false);
            sub.setGravity(Gravity.CENTER);ui.add(content,sub,6);
        }
        ui.gap(content,6);
    }
    private void rememberHomeScroll(){
        if(lastRenderedScreen==Screen.HOME)home.rememberScroll(currentScroller);
    }
    private void render() {
        cancelWritingCheck();
        readingView.clear();
        assemblyView.clear();
        writingView.clear();
        lessonProgress.clear();
        if(backupInProgress)return;
        if(screen!=Screen.HOME)lessonWaitingForBible=null;
        boolean collectionWasLoading=collectionDetailLoading;
        collectionDetailLoading=false;
        collectionGeneration++;
        if(collectionTask!=null){collectionTask.cancel(false);collectionTask=null;}
        modeInfo.dismiss(false);
        getWindow().setNavigationBarColor(screen==Screen.TRAIN||screen==Screen.RUN?0xFFF8E5D9:CARD);
        bottomNavigation.captureCollectionsIcon(root);
        if(isBibleSearchScreen())bibleSearch.resume();
        else bibleSearch.suspend();
        tabTransition.finish();
        tabCache.invalidate();
        rememberHomeScroll();
        if(currentScroller!=null && lastRenderedScreen!=null && lastRenderedScreen!=Screen.HOME
            && !(lastRenderedScreen==Screen.COLLECTION_DETAIL&&collectionWasLoading)){
            navigationScroll.put(lastRenderedScreen,currentScroller.getScrollY());
        }
        home.cancelAnimation();
        if(screen==Screen.TRAIN){lastRenderedScreen=screen;renderFullscreenLesson();return;}
        if(screen==Screen.CARD_SESSION){lastRenderedScreen=screen;renderCardSession();return;}
        if(screen==Screen.RUN){lastRenderedScreen=screen;renderWritingRun();return;}
        boolean focusedReaderChapter=screen==Screen.CHAPTER && bibleReader.fromVerseGrid() && focusVerse>0;
        boolean virtualBibleSearch=isBibleSearchScreen()&&bibleSearch.active()&&!bibleSearch.loading()&&bibleSearch.error().isEmpty();
        root=ui.column();root.setBackground(ui.pageBackground());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        currentScroller=scroll;
        content=ui.column();content.setPadding(ui.dp(19),ui.dp(17),ui.dp(19),ui.dp(27));scroll.addView(content);
        if(isBibleSearchScreen()&&!virtualBibleSearch)bibleSearchView.searchBar(content,bibleSearch,screen!=Screen.BOOKS);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(screen) {
            case HOME: renderHome();break;
            case BOOKS: if(!virtualBibleSearch)renderBooks();break;
            case CHAPTERS: renderChapters();break;
            case VERSES: renderVerseGrid(true);break;
            case CHAPTER: renderChapter();break;
            case ENTRIES: renderEntries();break;
            case REORDER_STUDY: renderStudyReorder();break;
            case REORDER_CARDS: renderCardReorder();break;
            case CUSTOM: renderCustom();break;
            case DUE: renderDue();break;
            case CARDS: renderUnifiedCollections();break;
            case COLLECTIONS:renderUnifiedCollections();break;
            case SETTINGS:renderSettings();break;
            case COLLECTION_DETAIL:renderUnifiedCollectionDetail();break;
            case COLLECTION_BOOK:renderCollectionBook();break;
            case COLLECTION_CHAPTER:renderCollectionChapter();break;
            case RUN_SUMMARY:renderRunSummary();break;
            case CARD_DECK: renderCardDeck();break;
            case CARD_PICK: renderCardPicker();break;
            case BIBLE_PICKER:renderUnifiedBiblePicker();break;
            case PICKER_BOOKS:if(!virtualBibleSearch)renderBooks();break;
            case PICKER_CHAPTERS:renderChapters();break;
            case PICKER_VERSES:renderPickerVerseGrid();break;
            case PICKER_TEXT:renderPickerVerseText();break;
        }


        if (virtualBibleSearch) {

            // Убираем обычную прокрутку, которая
            // создаёт все элементы сразу.
            root.removeView(scroll);

            // Заменяем на список с переиспользованием строк.
            ListView results = bibleSearchView.results(this,bibleSearch,screen!=Screen.BOOKS);

            root.addView(
                    results,
                    new LinearLayout.LayoutParams(
                            -1, 0, 1
                    )
            );

            currentScroller = null;
        }


        if(screen==Screen.BIBLE_PICKER && passagePicker.rangePanel()!=null)root.addView(passagePicker.rangePanel());
        if(screen==Screen.PICKER_CHAPTERS&&canBulkPick())addPickerChapterFooter();
        if(screen==Screen.COLLECTIONS||screen==Screen.CARDS||
            screen==Screen.COLLECTION_DETAIL) addUnifiedCollectionFooter();
        if(screen!=Screen.CUSTOM && screen!=Screen.RUN_SUMMARY) addNav(root);

        if(isPickerScreen(screen)){
            scroll.setFocusableInTouchMode(true);
            scroll.requestFocus();
        }
        setContentView(root);
        lastRenderedScreen = screen;

        if (screen.isMainTab()) {
            tabCache.put(
                    screen,
                    new TabCache.ReadyTab(
                            root,
                            content,
                            currentScroller,
                            home.index()
                    )
            );

            prepareOtherTabs();
        }

        boolean restoredPickerScroll=isPickerScreen(screen)&&bible.isReady()&&pickerRestoredScroll>=0;
        if(restoredPickerScroll){
            int y=pickerRestoredScroll;pickerRestoredScroll=-1;
            BibleReader.afterLayout(content,()->{if(currentScroller==scroll)scroll.scrollTo(0,y);});
        }
        if(!restoredPickerScroll && !focusedReaderChapter && screen!=Screen.HOME && navigationScroll.containsKey(screen) &&
            !(screen==Screen.BOOKS&&bibleSearch.active()) &&
            !(screen==Screen.PICKER_BOOKS&&bibleSearch.active()) &&
            screen!=Screen.PICKER_TEXT){
            int y=navigationScroll.get(screen);
            Runnable restore=()->{if(currentScroller==scroll)scroll.scrollTo(0,y);};
            if(isPickerScreen(screen))BibleReader.afterLayout(content,restore);
            else scroll.post(restore);
        }
        if(screen==Screen.HOME){
            home.restoreScroll(scroll,()->screen==Screen.HOME&&currentScroller==scroll,true);
        }
    }
    private boolean isBibleSearchScreen(){
        return screen==Screen.BOOKS||screen==Screen.PICKER_BOOKS;
    }

    private BibleSearchSession.Scope activeSearchScope(){
        if(isDestroyed())return null;
        if(screen==Screen.BOOKS)return BibleSearchSession.Scope.READER;
        return screen==Screen.PICKER_BOOKS?BibleSearchSession.Scope.PICKER:null;
    }

    private void renderBibleSearchKeepingInput(){
        BibleSearchView.preserveInput(()->root,this::render);
    }

    private void openSearchHit(BibleSearchLogic.Hit hit){
        selectedBook=hit.book;
        selectedChapter=hit.chapter;
        if(!isPickerScreen(screen))bibleReader.rememberChapter(hit.book,hit.chapter);
        focusVerse=hit.verse;
        boolean inPicker=screen==Screen.PICKER_BOOKS||screen==Screen.BIBLE_PICKER;
        bibleSearch.reset();
        if(inPicker){
            focusPickerSearchHit=true;
            pickerRestoredScroll=-1;
            screen=Screen.PICKER_TEXT;
        }else{
            bibleReader.fromSearch();
            screen=Screen.CHAPTER;
        }
        render();
    }

    /** Quiet category illustration beside each compact group of Bible books. */
    private final class BibleGroupGlyph extends View {
        private final BookColorLogic.Group category;
        private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        BibleGroupGlyph(BookColorLogic.Group category){super(MainActivity.this);this.category=category;
            setContentDescription(BibleReader.bookGroupName(category));}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float cx=getWidth()/2f,cy=getHeight()/2f;
            canvas.save();canvas.translate(cx,cy);
            float scale=Math.min(getWidth(),getHeight())/90f;canvas.scale(scale,scale);
            pen.setStrokeWidth(3.5f);pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setStrokeJoin(Paint.Join.ROUND);pen.setStyle(Paint.Style.STROKE);
            pen.setColor(0xFF965F4D);
            Path shape=new Path();
            switch(category){
                case PENTATEUCH:
                    canvas.drawRoundRect(-25,-25,-2,30,5,5,pen);
                    canvas.drawRoundRect(2,-25,25,30,5,5,pen);
                    for(int y=-12;y<=16;y+=13){canvas.drawLine(-20,y,-8,y,pen);canvas.drawLine(8,y,20,y,pen);}
                    break;
                case HISTORY:
                    shape.moveTo(-27,22);shape.lineTo(-30,-17);shape.lineTo(-12,-3);
                    shape.lineTo(0,-23);shape.lineTo(12,-3);shape.lineTo(30,-17);
                    shape.lineTo(27,22);shape.close();canvas.drawPath(shape,pen);
                    break;
                case POETRY:
                    canvas.drawArc(-23,-26,23,27,35,115,false,pen);
                    canvas.drawArc(-23,-26,23,27,215,115,false,pen);
                    for(int x=-9;x<=9;x+=9)canvas.drawLine(x,-23,x,22,pen);
                    canvas.drawLine(-20,24,20,24,pen);break;
                case GOSPELS:case ACTS:
                    canvas.drawRoundRect(-26,-21,0,24,6,6,pen);
                    canvas.drawRoundRect(0,-21,26,24,6,6,pen);
                    canvas.drawLine(0,-21,0,24,pen);break;
                case PAULINE_LETTERS:case GENERAL_LETTERS:
                    canvas.drawRoundRect(-27,-16,27,20,4,4,pen);
                    shape.moveTo(-27,-16);shape.lineTo(0,3);shape.lineTo(27,-16);canvas.drawPath(shape,pen);break;
                default:
                    canvas.drawCircle(0,0,21,pen);
                    canvas.drawLine(-14,0,14,0,pen);
                    canvas.drawLine(0,-14,0,14,pen);break;
            }
            canvas.restore();
        }
    }
    private void addNav(LinearLayout parent){
        bottomNavigation.render(parent,mainTabIndex(),index->{
            if(index==0&&screen==Screen.HOME){bottomNavigation.animateHomeIcon(root,true,true);return;}
            if(index==1&&mainTabIndex()==1){
                goToMainTab(index);bottomNavigation.animateBibleIcon(root,true,true,null);return;
            }
            if(index==2&&mainTabIndex()==2){
                goToMainTab(index);bottomNavigation.animateCollectionsIcon(root,true,true);return;
            }
            goToMainTab(index);
        });
    }

    private int mainTabIndex(){
        return screen.mainTabIndex(isPickerScreen(screen)&&pickerTarget==BibleTarget.STUDY_GROUP
            &&store.unifiedForGroup(pickerTargetId)==null);
    }
    /** One continuous two-page slide; the bottom bar is never moved or blanked. */

    private void goToMainTab(int index) {
        if (index < 0 || index >= Screen.TAB_COUNT || tabTransition.inProgress() || resetInProgress || backupInProgress) {
            return;
        }

        if (screen == Screen.TRAIN
                || screen == Screen.CARD_SESSION || screen == Screen.RUN) {
            return;
        }

        final Screen destination=Screen.tab(index);

        if (screen == destination) return;
        modeInfo.dismiss(false);
        if(isBibleSearchScreen())bibleSearch.suspend();
        if(isPickerScreen(screen)){
            passagePicker.selection().cancel();clearInsertionContext();
            selectedBook=readerBookBeforePicker;selectedChapter=readerChapterBeforePicker;
        }
        // Tapping an already selected section from its details simply opens the root list.
        if(mainTabIndex()==index && !screen.isMainTab()){
            screen=destination;render();return;
        }

        final int oldIndex = mainTabIndex();
        // Reset only this decorative icon before detaching the cached outgoing page.
        if(oldIndex==2 && index!=2)bottomNavigation.animateCollectionsIcon(root,false,true);
        final int direction = index > oldIndex ? 1 : -1;

        final Screen previousScreen = screen;
        final boolean leavingHasNav =
                screen != Screen.CUSTOM;

        if (screen == Screen.BIBLE_PICKER) {
            clearInsertionContext();
        }

        // Read the visible icon before detaching the outgoing cached page.
        BookNavigationIcon leavingBook=root==null?null:root.findViewWithTag(BookNavigationIcon.TAG);
        final float[] bibleIconState=leavingBook==null?null:leavingBook.snapshot();
        final LinearLayout leaving = root;
        final LinearLayout leavingBody = content;
        final ScrollView leavingScroller = currentScroller;

        int width = leaving == null ? 0 : leaving.getWidth();
        int height = leaving == null ? 0 : leaving.getHeight();

        int navHeight = 0;

        if (leavingHasNav
                && leaving != null
                && leaving.getChildCount() > 0) {

            navHeight = leaving
                    .getChildAt(leaving.getChildCount() - 1)
                    .getHeight();

        } else if (!leavingHasNav) {
            navHeight = ui.dp(84);
        }

        reviewSession.clear();
        lesson.groupMode = false;
        lesson.forcedStep = null;

        // Сохраняем текущую прокрутку.
        if (lastRenderedScreen == Screen.HOME) {
            rememberHomeScroll();
        } else if (lastRenderedScreen != null
                && currentScroller != null) {

            navigationScroll.put(
                    lastRenderedScreen,
                    currentScroller.getScrollY()
            );
        }

        TabCache.ReadyTab cached = tabCache.validPage(destination,home.index());

        screen = destination;
        bibleSearch.resume();

        if (cached != null) {

            // Главное изменение:
            // показываем готовый экран без повторного render().
            root = cached.page;
            content = cached.body;
            currentScroller = cached.scroller;

            setContentView(root);

            lastRenderedScreen = screen;
            bottomNavigation.setLastIndex(index);

            if (destination == Screen.HOME) {
                final ScrollView active = currentScroller;

                home.restoreScroll(active,()->currentScroller==active,false);

            } else if (currentScroller!=null && navigationScroll.containsKey(destination)) {

                final ScrollView active = currentScroller;
                final int y = navigationScroll.get(destination);

                active.post(() -> {
                    if (currentScroller == active) {
                        active.scrollTo(0, y);
                    }
                });
            }

            bottomNavigation.animatePreparedTabIndicator(
                    root,
                    oldIndex,
                    index
            );

        } else {

            // Резервный путь, если экран ещё не подготовлен.
            render();

            if (previousScreen.isMainTab() && leaving != null) {
                tabCache.put(
                        previousScreen,
                        new TabCache.ReadyTab(
                                leaving,
                                leavingBody,
                                leavingScroller,
                                home.index()
                        )
                );
            }
        }

        // The destination can be a cached page: animate its icon without rebuilding it.
        if(oldIndex!=index && (oldIndex==2 || index==2))
            bottomNavigation.animateCollectionsIcon(root,index==2,oldIndex==2);
        if(oldIndex!=index)bottomNavigation.animateBibleIcon(root,index==1,oldIndex==1,bibleIconState);
        if(oldIndex!=index && (oldIndex==0 || index==0))
            bottomNavigation.animateHomeIcon(root,index==0,oldIndex==0);
        final LinearLayout entering = root;

        if (leaving == null
                || width <= 0
                || height <= 0
                || !ValueAnimator.areAnimatorsEnabled()) {

            prepareOtherTabs();
            return;
        }

        final ViewGroup window =
                findViewById(android.R.id.content);

        if (window == null) return;

        tabTransition.start(window,leaving,entering,leavingHasNav,width,height,navHeight,direction,this::prepareOtherTabs);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event){
        modeInfo.onActivityTouch(event);
        if(screen!=Screen.TRAIN&&screen!=Screen.CARD_SESSION&&!tabTransition.inProgress()
            &&navigationGesture.handle(event,delta->goToMainTab(mainTabIndex()+delta),e->super.dispatchTouchEvent(e)))
            return true;
        return super.dispatchTouchEvent(event);
    }

    /** Archived entries still exist: a saved queue is valid only while its entire
     * ordered membership matches the currently selected collection. */
    private boolean collectionQueueMatches(long groupId,List<LocalStore.Entry> entries){
        if(groupId<0||store.unifiedForGroup(groupId)==null||
            collectionQueueGroup!=groupId||collectionQueueCursor<0||
            collectionQueueCursor>=collectionQueue.size()||collectionQueue.size()!=entries.size())
            return false;
        for(int i=0;i<entries.size();i++)
            if(collectionQueue.get(i)!=entries.get(i).id)return false;
        return true;
    }
    private void restoreCollectionQueue(){
        android.content.SharedPreferences p=getSharedPreferences(COLLECTION_PREFS,MODE_PRIVATE);
        if(!p.getBoolean("active",false))return;
        collectionQueue.clear();
        collectionQueueGroup=p.getLong("group",-1);
        collectionQueueCursor=p.getInt("cursor",0);
        String ids=p.getString("ids","");
        for(String raw:ids.split(","))try{
            collectionQueue.add(Long.parseLong(raw));
        }catch(NumberFormatException ignored){}
        if(!collectionQueueMatches(collectionQueueGroup,store.entries(collectionQueueGroup)))
            clearCollectionQueue();
    }
    private void persistCollectionQueue(){
        StringBuilder ids=new StringBuilder();
        for(long id:collectionQueue){if(ids.length()>0)ids.append(',');ids.append(id);}
        getSharedPreferences(COLLECTION_PREFS,MODE_PRIVATE).edit()
            .putBoolean("active",collectionQueueCursor>=0)
            .putLong("group",collectionQueueGroup).putInt("cursor",collectionQueueCursor)
            .putString("ids",ids.toString()).apply();
    }
    private void clearCollectionQueue(){
        collectionQueue.clear();collectionQueueCursor=-1;collectionQueueGroup=-1;
        executingCollectionQueue=false;collectionQueueNewStep=false;
        getSharedPreferences(COLLECTION_PREFS,MODE_PRIVATE).edit().clear().apply();
    }
    private void startStudyCollection(long groupId){
        CollectionProgressSnapshot snapshot=store.collectionProgress();
        List<LocalStore.Entry> entries=snapshot.entries(groupId);
        if(entries.isEmpty()){
            if(collectionQueueGroup==groupId)clearCollectionQueue();
            toast("Коллекция пока пустая");return;
        }
        cardStudyQueue.clear();cardStudyCursor=-1;cardStudyDeckId=-1;
        if(collectionQueueMatches(groupId,entries)){
            executingCollectionQueue=true;
            collectionQueueNewStep=false;
            openTraining(collectionQueue.get(collectionQueueCursor));return;
        }
        // The membership/order changed: discard only the old queue, not study history.
        clearCollectionQueue();
        for(LocalStore.Entry e:entries)collectionQueue.add(e.id);
        collectionQueueGroup=groupId;collectionQueueCursor=0;
        executingCollectionQueue=true;
        for(int i=0;i<entries.size();i++){
            LocalStore.Entry item=entries.get(i);
            if(snapshot.masteryCount(snapshot.canonicalId(item.id))<snapshot.verseCount(item.id) ||
                nextGroup(item,snapshot)!=null){collectionQueueCursor=i;break;}
        }
        collectionQueueNewStep=true;
        persistCollectionQueue();openTraining(collectionQueue.get(collectionQueueCursor));
    }
    private boolean resumeCollectionTraining(){
        if(!collectionQueueMatches(collectionQueueGroup,store.entries(collectionQueueGroup))){
            clearCollectionQueue();return false;
        }
        executingCollectionQueue=true;
        collectionQueueNewStep=false;
        openTraining(collectionQueue.get(collectionQueueCursor));return true;
    }
    private void advanceStudyCollection(){
        if(collectionQueueCursor<0||!executingCollectionQueue)return;
        collectionQueueCursor++;
        if(collectionQueueCursor>=collectionQueue.size()){
            clearCollectionQueue();
            toast("Все отрывки коллекции пройдены");
            screen=Screen.HOME;render();return;
        }
        persistCollectionQueue();
        collectionQueueNewStep=true;
        openTraining(collectionQueue.get(collectionQueueCursor));
    }
    private String studyProgressFor(long groupId){
        return CollectionViews.studyProgress(groupId,store.collectionProgress());
    }
    private void chooseCardDeck(Pick action){
        List<LocalStore.UnifiedCollection> collections=store.unifiedCollections();
        String[] options=new String[collections.size()+1];
        for(int i=0;i<collections.size();i++)options[i]=collections.get(i).title;
        options[collections.size()]="＋ Новая коллекция";
        ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Добавить в коллекцию").setItems(options,(dialog,index)->{
                if(index==collections.size())newCardDeck(action);
                else action.apply(collections.get(index).deckId);
            }).setNegativeButton("Отмена",null).create());
    }
    private void newCardDeck(Pick afterCreate){
        newUnifiedCollection(u->{
            if(afterCreate!=null)afterCreate.apply(u.deckId);
            else{openUnifiedId=u.id;screen=Screen.COLLECTION_DETAIL;render();}
        });
    }
    /** One-tap deck start: a painted vector triangle, not an oversized green font glyph. */
    private View deckPlayButton(LocalStore.CardDeck deck){
        View play=new View(this){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){
                super.onDraw(canvas);
                float cx=getWidth()/2f+ui.dp(1),cy=getHeight()/2f;
                float size=ui.dp(10);
                Path triangle=new Path();
                triangle.moveTo(cx-size*.62f,cy-size);
                triangle.lineTo(cx+size*.9f,cy);
                triangle.lineTo(cx-size*.62f,cy+size);
                triangle.close();
                paint.setColor(0xFFFFFFFF);paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(triangle,paint);
            }
        };
        // The parent pill supplies the background; this is only the painted play symbol.
        play.setBackgroundColor(Color.TRANSPARENT);
        play.setClickable(true);play.setFocusable(true);
        play.setContentDescription("Начать тренировку: "+deck.title);
        play.setOnClickListener(v->{selectedDeck=deck.id;startCardSession();});
        return play;
    }
    private void renderCardDecks(){
        tabWordmark();
        ui.action(content,"＋  Создать подборку",true,()->newCardDeck(null));
        for(LocalStore.CardDeck deck:store.cardDecks()){
            LinearLayout panel=ui.card(content);panel.setPadding(ui.dp(11),ui.dp(11),ui.dp(9),ui.dp(11));
            panel.setBackground(artwork.homeSurface());
            LinearLayout line=ui.row();line.setGravity(Gravity.CENTER_VERTICAL);
            ImageView illustration=artwork.landscapeThumbnail(deck.id);
            LinearLayout.LayoutParams imageP=new LinearLayout.LayoutParams(ui.dp(79),ui.dp(ScreenArtwork.MAX_LANDSCAPE_THUMBNAIL_DP));
            imageP.rightMargin=ui.dp(12);line.addView(illustration,imageP);
            LinearLayout details=ui.column();
            TextView name=ui.displayText(deck.title,18);name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ui.add(details,name,0);
            ui.add(details,ui.txt(deck.count+" "+(deck.count==1?"карточка":
                ((deck.count%10>=2&&deck.count%10<=4&&(deck.count%100<12||deck.count%100>14))?
                    "карточки":"карточек")),13,MUTED,false),4);
            line.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            panel.addView(line);
            panel.setOnClickListener(v->{selectedDeck=deck.id;screen=Screen.CARD_DECK;render();});
        }
    }

    /** The card menu and the study-collection menu share exactly the same popup styling. */
    private void showCardDeckActions(LocalStore.CardDeck deck,View anchor){
        String[] actions={"Изучить","Добавить свой текст","Переименовать","Удалить"};
        String[] icons={"study","file","pencil","trash"};
        menus.showActionsMenu(anchor,actions,icons,which->{
            selectedDeck=deck.id;
            switch(which){
                case 0:studyCardDeck();break;
                case 1:showNewCustomFlashcard(deck.id);break;
                case 2:renameCardDeck(deck);break;
                case 3:confirmDeleteCardDeck(deck);break;
            }
        });
    }

    private void renameCardDeck(LocalStore.CardDeck deck){
        EditText name=ui.input("Название подборки",false);name.setText(deck.title);
        LinearLayout wrap=ui.column();wrap.setPadding(ui.dp(20),ui.dp(5),ui.dp(20),ui.dp(0));ui.add(wrap,name,0);
        ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Переименовать").setView(wrap)
            .setPositiveButton("Сохранить",(d,w)->{
                if(value(name).isEmpty()){toast("Укажи название");return;}
                store.renameCardDeck(deck.id,value(name));render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void confirmDeleteCardDeck(LocalStore.CardDeck deck){
        ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Удалить подборку?")
            .setPositiveButton("Удалить",(d,w)->{
                store.deleteCardDeck(deck.id);flashcards.clearSaved(deck.id);
                screen=Screen.CARDS;render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void showNewCustomFlashcard(long deckId){
        EditText reference=ui.input("Название / источник",false);
        EditText verse=ui.input("Текст",true);verse.setMinLines(4);
        LinearLayout fields=ui.column();fields.setPadding(ui.dp(20),ui.dp(7),ui.dp(20),ui.dp(4));
        ui.add(fields,reference,0);ui.add(fields,verse,12);
        ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Добавить свой текст").setView(fields)
            .setPositiveButton("Добавить",(dialog,which)->{
                if(value(reference).isEmpty()||value(verse).isEmpty()){
                    toast("Укажи название и текст");return;
                }
                store.addCustomFlashcard(deckId,value(reference),value(verse));
                if(screen==Screen.CARD_DECK||screen==Screen.CARDS)render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void renderCardDeck(){
        LocalStore.CardDeck deck=store.cardDeck(selectedDeck);
        if(deck==null){screen=Screen.CARDS;render();return;}
        title(deck.title,null);
        List<LocalStore.Flashcard> cards=store.flashcards(selectedDeck);
        if(cards.isEmpty())ui.helper(content,"Здесь пока нет карточек.");
        for(LocalStore.Flashcard card:cards){
            LinearLayout c=ui.card(content);c.setBackground(artwork.homeSurface());
            LinearLayout head=ui.row();
            TextView ref=ui.displayText(card.reference,18);
            head.addView(ref,new LinearLayout.LayoutParams(0,-2,1));
            TextView more=ui.txt("⋮",26,MUTED,true);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription("Действия с карточкой "+card.reference);
            head.addView(more,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(52)));
            more.setOnClickListener(v->showCardActions(card,more));
            ui.add(c,head,0);ui.add(c,ui.prose(card.content,16),8);
        }
        addCardDeckFooter(deck, !cards.isEmpty());
    }
    /** Attached to root rather than the scrolling list, so the footer never overlays a verse. */


    private void addCardDeckFooter(
            LocalStore.CardDeck deck,
            boolean hasCards) {

        // Закреплённая панель под списком карточек.
        LinearLayout footer = ui.column();

        footer.setPadding(
                ui.dp(19), ui.dp(10),
                ui.dp(19), ui.dp(10)
        );

        footer.setBackgroundColor(BG);

        // Кнопка запуска карусели карточек.
        TextView start = ui.button(
                "▶  Начать",
                true,
                () -> {
                    selectedDeck = deck.id;
                    startCardSession();
                }
        );

        GradientDrawable greenBackground =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{
                                0xFF789D72,
                                0xFF4E7654
                        }
                );

        greenBackground.setCornerRadius(ui.dp(18));

        start.setBackground(greenBackground);
        start.setTextColor(Color.WHITE);
        start.setTextSize(16);

        start.setContentDescription(
                "Начать тренировку карточек: " + deck.title
        );

        // Пустую подборку запускать нельзя.
        start.setEnabled(hasCards);
        start.setAlpha(hasCards ? 1f : 0.42f);

        // Мягкая анимация нажатия.
        start.setOnTouchListener((v, event) -> {

            if (!hasCards) {
                return false;
            }

            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN) {

                v.animate()
                        .scaleX(0.98f)
                        .scaleY(0.98f)
                        .setDuration(90L)
                        .start();

            } else if (
                    action == MotionEvent.ACTION_UP ||
                            action == MotionEvent.ACTION_CANCEL
            ) {

                v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(140L)
                        .start();
            }

            return false;
        });

        footer.addView(
                start,
                new LinearLayout.LayoutParams(-1, ui.dp(54))
        );

        // Вторая кнопка — добавление из Библии.
        TextView addBible = ui.button(
                "＋  Добавить из Библии",
                false,
                () -> openBiblePicker(
                        BibleTarget.FLASHCARD_DECK,
                        deck.id,
                        Screen.CARD_DECK
                )
        );

        addBible.setTextSize(15);

        addBible.setContentDescription(
                "Добавить стихи из Библии в подборку " +
                        deck.title
        );

        LinearLayout.LayoutParams addParams =
                new LinearLayout.LayoutParams(-1, ui.dp(52));

        addParams.topMargin = ui.dp(9);

        footer.addView(addBible, addParams);

        // Добавляем панель вне ScrollView.
        root.addView(
                footer,
                new LinearLayout.LayoutParams(-1, -2)
        );
    }


    private void addBibleFooter(BibleTarget target,long groupId,Screen back){
        LinearLayout footer=ui.column();footer.setPadding(ui.dp(19),ui.dp(8),ui.dp(19),ui.dp(10));
        footer.setBackgroundColor(BG);
        TextView add=ui.button("＋  Добавить из Библии",true,()->openBiblePicker(target,groupId,back));
        add.setContentDescription("Добавить стихи из Библии в текущую коллекцию");
        footer.addView(add,new LinearLayout.LayoutParams(-1,ui.dp(54)));
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
    }


    private void addStudyCollectionFooter(LocalStore.Group group) {

        // Панель находится вне прокручиваемого списка.
        LinearLayout footer = ui.column();
        footer.setPadding(
                ui.dp(19), ui.dp(10),
                ui.dp(19), ui.dp(10)
        );
        footer.setBackgroundColor(BG);

        // Основная кнопка — начало изучения коллекции.
        TextView start = ui.button(
                "▶  Начать изучение",
                true,
                () -> startStudyCollection(group.id)
        );

        GradientDrawable greenBackground =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{
                                0xFF789D72,
                                0xFF4E7654
                        }
                );

        greenBackground.setCornerRadius(ui.dp(18));
        start.setBackground(greenBackground);
        start.setTextColor(Color.WHITE);
        start.setTextSize(16);
        start.setContentDescription(
                "Начать изучение коллекции " + group.title
        );

        boolean hasEntries = !store.entries(group.id).isEmpty();

        start.setEnabled(hasEntries);
        start.setAlpha(hasEntries ? 1f : 0.42f);

        // Мягкий отклик при нажатии.
        start.setOnTouchListener((v, event) -> {

            if (!hasEntries) {
                return false;
            }

            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN) {
                v.animate()
                        .scaleX(0.98f)
                        .scaleY(0.98f)
                        .setDuration(90L)
                        .start();

            } else if (
                    action == MotionEvent.ACTION_UP ||
                            action == MotionEvent.ACTION_CANCEL
            ) {
                v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(140L)
                        .start();
            }

            return false;
        });

        footer.addView(
                start,
                new LinearLayout.LayoutParams(-1, ui.dp(54))
        );

        // Вторичная кнопка — добавление новых стихов.
        TextView addBible = ui.button(
                "＋  Добавить из Библии",
                false,
                () -> openBiblePicker(
                        BibleTarget.STUDY_GROUP,
                        group.id,
                        Screen.ENTRIES
                )
        );

        addBible.setTextSize(15);
        addBible.setContentDescription(
                "Добавить стихи из Библии в коллекцию " +
                        group.title
        );

        LinearLayout.LayoutParams addParams =
                new LinearLayout.LayoutParams(-1, ui.dp(52));

        addParams.topMargin = ui.dp(9);

        footer.addView(addBible, addParams);

        // Закрепляем обе кнопки внизу открытой коллекции.
        root.addView(
                footer,
                new LinearLayout.LayoutParams(-1, -2)
        );
    }


    /** Unified Bible entry: catalogue -> chapter grid -> verse grid -> highlighted chapter text. */
    private void openBiblePicker(BibleTarget target,long targetId,Screen back){
        passagePicker.resetChapters();
        readerBookBeforePicker=selectedBook;readerChapterBeforePicker=selectedChapter;
        passagePicker.selection().cancel();passagePicker.selection().book=0;passagePicker.selection().collectionId=targetId;
        pickerTarget=target;pickerTargetId=targetId;pickerBackScreen=back;
        passagePicker.chooseEnd(false);
        pickerVerseNumber=1;pickerRestoredScroll=-1;
        clearPassageSelection();focusVerse=-1;
        navigationScroll.remove(Screen.PICKER_BOOKS);
        navigationScroll.remove(Screen.PICKER_CHAPTERS);
        navigationScroll.remove(Screen.PICKER_VERSES);
        bibleSearch.reset();
        screen=Screen.PICKER_BOOKS;render();
    }
    /** Range settings reuse the same catalog and numeric grids as quick insertion. */
    private void openPickerRange(){
        pickerQuickBook=selectedBook;pickerQuickChapter=selectedChapter;
        pickerQuickScroll=currentScroller==null?0:currentScroller.getScrollY();
        passagePicker.selection().openBook(selectedBook);passagePicker.selection().active=true;
        screen=Screen.BIBLE_PICKER;render();
    }
    private void beginPickerBoundary(boolean end){
        passagePicker.chooseEnd(end);
        selectedBook=passagePicker.selection().book;
        int previous=end?passagePicker.selection().endChapter:passagePicker.selection().startChapter;
        selectedChapter=previous>0?previous:passagePicker.selection().startChapter>0?
            passagePicker.selection().startChapter:pickerQuickChapter;
        navigationScroll.remove(Screen.PICKER_CHAPTERS);navigationScroll.remove(Screen.PICKER_VERSES);
        pickerRestoredScroll=-1;
        screen=selectedBook>0?Screen.PICKER_CHAPTERS:Screen.PICKER_BOOKS;render();
    }
    private void renderUnifiedBiblePicker(){
        passagePicker.clearRangePanel();
        if(bibleNotReady())return;
        title("Задать диапазон",passagePicker.selection().book>0?Bible.NAMES[passagePicker.selection().book-1]:null);
        passagePicker.renderRange(content,this::beginPickerBoundary,this::savePickerRange);
    }

    /** Four-column number grid, identical to the chapters grid. */
    private void renderPickerVerseGrid(){renderVerseGrid(false);}
    private void renderVerseGrid(boolean reader){
        if(bibleNotReady())return;
        List<Bible.Verse> verses=bible.chapter(selectedBook,selectedChapter);
        title(Bible.NAMES[selectedBook-1]+" "+selectedChapter,"Стихи");
        if(verses.isEmpty()){ui.helper(content,"В главе нет стихов");return;}
        boolean boundary=!reader&&passagePicker.selection().active;
        int selected=reader?bibleReader.verse():boundary?
            passagePicker.boundaryVerse(selectedChapter):pickerVerseNumber;
        bibleReader.renderVerseGrid(content,verses.size(),selected,boundary,verse->{
            if(reader){
                bibleReader.selectVerse(verse);focusVerse=verse;screen=Screen.CHAPTER;
            }else if(boundary){
                passagePicker.pickBoundaryVerse(selectedChapter,verse);
                screen=Screen.BIBLE_PICKER;
            }else{
                pickerVerseNumber=verse;focusVerse=verse;pickerRestoredScroll=-1;
                focusPickerSearchHit=false;screen=Screen.PICKER_TEXT;
            }
            render();
        });
    }
    private void renderPickerVerseText(){
        passagePicker.clearRangePanel();
        if(bibleNotReady())return;
        passagePicker.openChapter(selectedBook,selectedChapter);
        title(Bible.NAMES[selectedBook-1]+" "+selectedChapter,null);
        LinearLayout navigation=ui.row();
        navigation.addView(ui.button("Главы · "+selectedChapter,false,()->{
            navigationScroll.remove(Screen.PICKER_CHAPTERS);screen=Screen.PICKER_CHAPTERS;render();
        }),new LinearLayout.LayoutParams(0,-2,1));
        navigation.addView(ui.button("Стихи",false,()->{
            navigationScroll.remove(Screen.PICKER_VERSES);screen=Screen.PICKER_VERSES;render();
        }),new LinearLayout.LayoutParams(0,-2,1));
        ui.add(content,navigation,4);
        ui.action(content,"Задать диапазон",false,this::openPickerRange);
        final int book=selectedBook,chapter=selectedChapter;
        final int targetVerse=focusVerse;focusVerse=-1;
        final boolean scrollToTarget=pickerRestoredScroll<0&&(focusPickerSearchHit||targetVerse>1);
        focusPickerSearchHit=false;
        boolean study=pickerTarget==BibleTarget.STUDY_GROUP;
        passagePicker.renderVerses(content,book,chapter,targetVerse,scrollToTarget,
            ()->screen==Screen.PICKER_TEXT?currentScroller:null,
            study?store.entries(pickerTargetId):Collections.emptyList(),
            study?number->{
                if(!togglePickerCollectionVerse(book,chapter,number))return null;
                return pickerCollectionContainsVerse(book,chapter,number);
            }:null);
    }
    private void savePickerRange(){
        if(!passagePicker.beginSaveRange())return;
        int[] b=passagePicker.selection().bounds();
        if(savePickedPassageFor(passagePicker.selection().book,b[0],b[1],b[2],b[3])){
            passagePicker.selection().cancel();clearInsertionContext();
            selectedBook=readerBookBeforePicker;selectedChapter=readerChapterBeforePicker;
            screen=pickerBackScreen;render();
        }else passagePicker.updatePickerRangeSummary();
    }
    private boolean pickerCollectionContainsVerse(int book,int chapter,int verse){
        if(pickerTarget!=BibleTarget.STUDY_GROUP || pickerTargetId<0)return false;
        String ref=Bible.reference(book,chapter,verse,chapter,verse);
        for(LocalStore.Entry e:store.entries(pickerTargetId))
            if(ref.equals(e.reference))return true;
        return false;
    }
    /** Toggle one standalone verse in the collection that opened the picker.
     * Detaching preserves progress; it never deletes the underlying study row. */
    private boolean togglePickerCollectionVerse(int book,int chapter,int verse){
        if(pickerTarget!=BibleTarget.STUDY_GROUP ||
            store.unifiedForGroup(pickerTargetId)==null)return false;
        String ref=Bible.reference(book,chapter,verse,chapter,verse);
        try{
            if(pickerCollectionContainsVerse(book,chapter,verse)){
                if(!store.removeUnifiedPassage(pickerTargetId,ref))return false;
                toast("Убрано из коллекции: "+ref);
            }else{
                // Avoid creating duplicate standalone entries inside a saved long range.
                for(LocalStore.Entry entry:store.entries(pickerTargetId)){
                    if(!entry.reference.startsWith(Bible.NAMES[book-1]+" "))continue;
                    if(entry.reference.equals(ref))continue;
                    for(Bible.Verse line:entry.lines())if(line.ref.equals(chapter+":"+verse)){
                        toast("Стих уже входит в отрывок этой коллекции");
                        return false;
                    }
                }
                store.addPickedPassage(pickerTargetId,ref,
                    bible.passage(book,chapter,verse,chapter,verse),bible);
                toast("Добавлено в коллекцию: "+ref);
            }
            return true;
        }catch(Exception ignored){
            toast("Не удалось изменить коллекцию");return false;
        }
    }
    /** Fast Bible-search membership does not alter range-picker state or the scroll. */
    private boolean quickBibleVerseExists(int book,int chapter,int verse){
        LocalStore.UnifiedCollection active=activeUnified();
        if(active==null)return false;
        String ref=Bible.reference(book,chapter,verse,chapter,verse);
        for(LocalStore.Entry entry:store.entries(active.groupId))
            if(ref.equals(entry.reference))return true;
        return false;
    }
    private boolean toggleQuickBibleVerse(int book,int chapter,int verse){
        LocalStore.UnifiedCollection active=activeUnified();
        if(active==null){
            toast("Сначала создай коллекцию на главной странице");
            return false;
        }
        String ref=Bible.reference(book,chapter,verse,chapter,verse);
        try{
            if(quickBibleVerseExists(book,chapter,verse)){
                if(!store.removeUnifiedPassage(active.groupId,ref))return false;
                toast("Убрано из коллекции: "+ref);
            }else{
                store.restoreOrAddStudyPassage(active.groupId,ref,
                    bible.passage(book,chapter,verse,chapter,verse));
                toast("Добавлено в «"+active.title+"»: "+ref);
            }
            return true;
        }catch(Exception ex){
            toast("Не удалось изменить коллекцию");return false;
        }
    }
    private boolean pickerHasReference(String ref,int book,int chapter,int verse){
        if(pickerTarget==BibleTarget.STUDY_GROUP){
            for(LocalStore.Entry e:store.entries(pickerTargetId))if(ref.equals(e.reference))return true;
        }else{
            for(LocalStore.Flashcard f:store.flashcards(pickerTargetId))
                if(f.bookId==book&&f.firstChapter==chapter&&f.lastChapter==chapter&&
                    f.firstVerse==verse&&f.lastVerse==verse)return true;
        }
        return false;
    }
    private boolean savePickedPassage(int sc,int sv,int ec,int ev){
        return savePickedPassageFor(selectedBook,sc,sv,ec,ev);
    }
    private boolean savePickedPassageFor(int book,int sc,int sv,int ec,int ev){
        try{
            if(!PassageRangeLogic.valid(sc,sv,ec,ev)){
                toast("Конец отрывка раньше начала");return false;
            }
            List<Bible.Verse> verses=pickerTarget==BibleTarget.STUDY_GROUP?
                bible.collectionPassage(book,sc,sv,ec,ev):bible.passage(book,sc,sv,ec,ev);
            String ref=Bible.reference(book,sc,sv,ec,ev);
            if(pickerTarget==BibleTarget.STUDY_GROUP){
                if(store.unifiedForGroup(pickerTargetId)==null){toast("Коллекция больше не существует");return false;}
                long added=-1;
                for(LocalStore.Entry e:store.entries(pickerTargetId))if(ref.equals(e.reference)){
                    if(!store.retainBookChapter(pickerTargetId,e.id)){
                        toast("Уже в коллекции");return false;
                    }
                    added=e.id;break;
                }
                if(added<0)added=store.addPickedPassage(pickerTargetId,ref,verses,bible,insertAfterEntryId);
                if(insertAfterEntryId>=0){
                    insertAfterEntryId=added;
                    studyInsertionTrail.add(added);
                }
            }else{
                boolean added=store.addFlashcard(pickerTargetId,book,sc,sv,ec,ev,ref,texts(verses));
                if(!added){toast("Карточка уже в подборке");return false;}
                if(insertAfterCardId>=0){
                    for(LocalStore.Flashcard fc:store.flashcards(pickerTargetId)){
                        if(ref.equals(fc.reference)){
                            repositionCardAfter(pickerTargetId,fc.id,insertAfterCardId);
                            insertAfterCardId=fc.id;
                            cardInsertionTrail.add(fc.id);
                            break;
                        }
                    }
                }
            }
            toast("Добавлено: "+ref);return true;
        }catch(Exception ex){toast(ex.getMessage()==null?"Не удалось добавить":ex.getMessage());return false;}
    }
    private boolean removePickedVerse(String ref,int book,int chapter,int verse){
        try{
            if(pickerTarget==BibleTarget.STUDY_GROUP){
                long removedId=-1;
                for(LocalStore.Entry e:store.entries(pickerTargetId))
                    if(ref.equals(e.reference)){removedId=e.id;break;}
                boolean removed=store.detachStudyReference(pickerTargetId,ref);
                if(removed&&removedId>=0)removedInsertionCandidate(removedId,false);
                return removed;
            }
            for(LocalStore.Flashcard f:store.flashcards(pickerTargetId)){
                if(f.bookId==book&&f.firstChapter==chapter&&f.lastChapter==chapter&&
                    f.firstVerse==verse&&f.lastVerse==verse){
                    store.removeFlashcard(pickerTargetId,f.id);
                    removedInsertionCandidate(f.id,true);
                    return true;
                }
            }
        }catch(Exception ex){toast("Не удалось убрать стих");}
        return false;
    }
    private void saveBibleVerseToGroup(long groupId,int book,int chapter,int verse){
        try{
            String ref=Bible.reference(book,chapter,verse,chapter,verse);
            for(LocalStore.Entry e:store.entries(groupId))if(ref.equals(e.reference)){
                toast("Стих уже в подборке");return;
            }
            store.restoreOrAddStudyPassage(groupId,ref,bible.passage(book,chapter,verse,chapter,verse));
            toast("Добавлено: "+ref);
        }catch(Exception ex){toast("Не удалось добавить стих");}
    }
    /** Study each card as its own entry; a range retains its internal contiguous links. */
    private void studyCardDeck(){
        List<LocalStore.Flashcard> cards=store.flashcards(selectedDeck);
        if(cards.isEmpty()){toast("Подборка пуста");return;}
        // Flashcards remain free-form; only starting a memorization lesson requires words.
        for(LocalStore.Flashcard card:cards)if(card.bookId==0&&ExerciseTextLogic.words(card.content).isEmpty()){
            toast("Исправь текст карточки «"+card.reference+"»: нужно хотя бы одно слово или число");
            return;
        }
        if(!bible.isReady())for(LocalStore.Flashcard f:cards)
            if(f.bookId>0){toast("Библия ещё загружается");return;}
        cardStudyQueue.clear();cardStudyCursor=0;cardStudyDeckId=selectedDeck;
        cardStudyMaterial=cardLessonMaterial(cards);
        for(LocalStore.Flashcard card:cards){
            try{
                List<Bible.Verse> verses=card.bookId==0?
                    Collections.singletonList(new Bible.Verse("1",card.content)):
                    bible.passage(card.bookId,card.firstChapter,card.firstVerse,card.lastChapter,card.lastVerse);
                long id=store.findOrCreateCardStudyEntry(card,verses);
                if(!cardStudyQueue.contains(id))cardStudyQueue.add(id);
            }catch(Exception ex){
                executingCollectionQueue=false;
                cardStudyQueue.clear();cardStudyCursor=-1;cardStudyDeckId=-1;
                toast("Не удалось открыть стих: "+card.reference);return;
            }
        }
        if(!cardStudyQueue.isEmpty())openTraining(cardStudyQueue.get(0));
    }
    private void advanceCardStudy(){
        if(cardStudyCursor<0){onBackPressed();return;}
        if(++cardStudyCursor<cardStudyQueue.size()){
            openTraining(cardStudyQueue.get(cardStudyCursor));return;
        }
        cardStudyQueue.clear();cardStudyCursor=-1;
        selectedDeck=cardStudyDeckId;cardStudyDeckId=-1;
        screen=Screen.CARD_DECK;render();
    }

    private void renderCardPicker(){
        title("Добавить карточку",null);
        if(bibleNotReady())return;
        ui.action(content,Bible.NAMES[selectedBook-1]+" ▾",false,()->{
            ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                .setTitle("Книга Библии").setItems(Bible.NAMES,(d,which)->{
                    selectedBook=which+1;selectedChapter=bibleReader.lastReadChapter(selectedBook);
                    clearPassageSelection();render();
                }).setNegativeButton("Отмена",null).create());
        });
        renderInlineRangeBuilder(true);
    }
    private void addCardFromSelection(long deckId){
        if(rangeStartChapter<1||rangeStartVerse<1){toast("Укажи границы отрывка");return;}
        addBibleCard(deckId,selectedBook,rangeStartChapter,rangeStartVerse,rangeEndChapter,rangeEndVerse);
    }
    private void addBibleCard(long deckId,int book,int sc,int sv,int ec,int ev){
        if(!bible.isReady()){toast("Библия ещё загружается");return;}
        try{
            List<Bible.Verse> lines=bible.passage(book,sc,sv,ec,ev);
            String text=texts(lines);
            boolean added=store.addFlashcard(deckId,book,sc,sv,ec,ev,Bible.reference(book,sc,sv,ec,ev),text);
            toast(added?"Карточка добавлена":"Карточка уже есть в подборке");
            if(screen==Screen.CARD_PICK){screen=Screen.CARD_DECK;render();}
        }catch(Exception ex){toast(ex.getMessage()==null?"Не удалось добавить карточку":ex.getMessage());}
    }
    private List<LocalStore.Flashcard> eligibleSessionCards(){
        List<LocalStore.Flashcard> items=store.flashcards(selectedDeck);
        if(!cardFromUnified)return items;
        List<LocalStore.Flashcard> singles=new ArrayList<>();
        for(LocalStore.Flashcard card:items)if(card.bookId==0 ||
            (card.firstChapter==card.lastChapter && card.firstVerse==card.lastVerse))
            singles.add(card);
        return singles;
    }
    private void startCardSession(){
        if(!flashcards.start(selectedDeck,sessionCardIds())){
            toast("Подборка пуста");return;
        }
        screen=Screen.CARD_SESSION;render();
    }

    private List<Long> sessionCardIds(){
        List<Long> ids=new ArrayList<>();
        for(LocalStore.Flashcard card:eligibleSessionCards())ids.add(card.id);
        return ids;
    }

    private void nextFlashcard(){
        if(backupInProgress)return;
        if(!flashcards.advance(selectedDeck,this::sessionCardIds)){leaveCardSession();return;}
        renderCardSession();
    }

    private void leaveCardSession(){
        screen=cardFromUnified?Screen.HOME:Screen.CARD_DECK;render();
    }

    private void renderCardSession(){
        long currentId=flashcards.currentId();
        for(LocalStore.Flashcard current:store.flashcards(selectedDeck)){
            if(current.id!=currentId)continue;
            LocalStore.CardDeck deck=store.cardDeck(selectedDeck);
            FlashcardSessionView view=new FlashcardSessionView(this,ui,this::nextFlashcard,flashcards::toggleAnswer);
            root=view.create(deck==null?"Карточки":deck.title,current.reference,current.content,
                flashcards.referenceFirst(),flashcards.revealed());
            setContentView(root);
            return;
        }
        leaveCardSession();
    }

    private void renderHome(){
        LocalStore.UnifiedCollection active=activeUnified();
        home.render(content,active,active==null?null:store.collectionProgress(),this::bookForEntry,
            new HomePage.Actions(()->newUnifiedCollection(null),this::showUnifiedSelector,this::startActiveStudy,
                this::startActiveCards,()->startActiveReview(false),this::startActiveRun,()->startActiveReview(true)));
    }

    private FrameLayout collectionTile(LocalStore.Group group){
        FrameLayout tile=new FrameLayout(this);
        tile.setBackground(ui.outlined(CARD,22,BORDER));tile.setClipToOutline(true);
        tile.setElevation(ui.dp(2));
        ImageView scene=new ImageView(this);
        scene.setImageResource(artwork.landscapeFor(group.id));
        scene.setScaleType(ImageView.ScaleType.CENTER_CROP);
        scene.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(scene,new FrameLayout.LayoutParams(-1,-1));
        View light=new View(this);
        light.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFFFFF2E6,0xE8FFF0E2,0x40FFF0E5,0x08FFF0E5}));
        tile.addView(light,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout caption=ui.column();caption.setPadding(ui.dp(12),ui.dp(11),ui.dp(12),ui.dp(4));
        TextView name=ui.displayText(group.title,17);name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ui.add(caption,name,0);
        ui.add(caption,ui.txt(group.count+" "+HomePage.collectionCountNoun(group.count),12,MUTED,false),5);
        tile.addView(caption,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        tile.setContentDescription(group.title+", "+group.count+" "+HomePage.collectionCountNoun(group.count));
        tile.setClickable(true);tile.setFocusable(true);
        return tile;
    }
    private void stat(LinearLayout row,String number,String desc) {
        LinearLayout col=ui.column();col.addView(ui.txt(number,25,WHITE,true));col.addView(ui.txt(desc,11,0xFFE1FFFA,false));
        row.addView(col,new LinearLayout.LayoutParams(0,-2,1));
    }
    private boolean bibleNotReady() {
        if(bible.isReady())return false;
        if(!bibleError.isEmpty()) ui.helper(content,"Ошибка встроенного текста: "+bibleError);
        else ui.helper(content,"Загружаем текст Библии из памяти устройства…");
        return true;
    }
    private void renderBooks() {
        if(bibleSearch.active()){bibleSearchView.placeholders(content,bibleSearch);return;}
        if(bibleNotReady())return;
        boolean bulk=screen==Screen.PICKER_BOOKS&&canBulkPick();
        Set<Integer> addedBooks=bulk?store.collectionBooks(pickerTargetId).keySet():Collections.emptySet();
        if(bulk)passagePicker.beginBookCatalog();
        bibleReader.renderBooks(content,this::openCatalogBook,bulk?book->
            passagePicker.bookAction(pickerTargetId,book,addedBooks.contains(book),this::changeWholeBook):null);
    }

    private void openCatalogBook(int book){
        selectedBook=book;selectedChapter=screen==Screen.PICKER_BOOKS&&passagePicker.selection().book==book?
            passagePicker.selection().chapter:bibleReader.lastReadChapter(book);
        if(screen==Screen.PICKER_BOOKS){
            boolean rangeActive=passagePicker.selection().active;
            passagePicker.selection().openBook(book);passagePicker.selection().active=rangeActive;
            passagePicker.resetChapters();
            navigationScroll.remove(Screen.PICKER_CHAPTERS);
            navigationScroll.remove(Screen.PICKER_VERSES);
            pickerRestoredScroll=-1;
            screen=Screen.PICKER_CHAPTERS;
        }else {selectingPassage=false;clearPassageSelection();bibleReader.resetVerse();
            navigationScroll.remove(Screen.VERSES);screen=Screen.CHAPTERS;}
        render();
    }

    private void openReaderVerseGrid(int chapter) {
        selectedChapter=chapter;bibleReader.resetVerse();
        bibleReader.rememberChapter(selectedBook,chapter);
        navigationScroll.remove(Screen.VERSES);
        screen=Screen.VERSES;render();
    }
    private void renderChapters() {
        if(bibleNotReady())return;
        int n=bible.chapterCount(selectedBook);
        boolean picker=screen==Screen.PICKER_CHAPTERS;
        title(Bible.NAMES[selectedBook-1],picker?
            n+" "+(n%10==1 && n%100!=11?"глава":
            n%10>=2 && n%10<=4 && (n%100<12||n%100>14)?"главы":"глав"):null);
        int selected=picker?selectedChapter:bibleReader.lastReadChapter(selectedBook);
        boolean bulk=picker&&canBulkPick();
        if(bulk)passagePicker.beginChapterGrid();
        bibleReader.renderChapters(content,n,selected,picker,c->{
            if(bulk&&passagePicker.hasChapters()){passagePicker.togglePickerChapter(c,selectedChapter);return;}
            if(picker&&!passagePicker.selection().active&&selectedChapter!=c)pickerVerseNumber=1;
            selectedChapter=c;
            if(!picker){openReaderVerseGrid(c);return;}
            navigationScroll.remove(Screen.PICKER_VERSES);
            pickerRestoredScroll=-1;
            screen=Screen.PICKER_VERSES;render();
        },bulk?(c,button)->{
            passagePicker.configureChapter(c,button,selected);
        }:null);
    }

    private boolean canBulkPick(){
        return pickerTarget==BibleTarget.STUDY_GROUP&&pickerTargetId>0&&!passagePicker.selection().active&&insertAfterEntryId<0;
    }

    private void addPickerChapterFooter(){
        root.addView(passagePicker.chapterFooter(selectedChapter,this::commitPickerChapters),
            new LinearLayout.LayoutParams(-1,-2));
    }

    private void commitPickerChapters(){
        final long group=pickerTargetId;
        final int book=selectedBook;
        Set<Integer> chapters=passagePicker.beginSaveChapters(selectedChapter);
        if(chapters.isEmpty())return;
        runCollectionMutation(()->collectionStore.addBibleChapters(group,book,chapters,false,bible),success->{
            passagePicker.finishSaveChapters(success,screen==Screen.PICKER_CHAPTERS&&pickerTargetId==group
                &&selectedBook==book,selectedChapter);
            if(success){
                toast("Главы добавлены");
                refreshCollectionAfterBulkChange(group);
            }
        });
    }

    private void changeWholeBook(long group,int book,boolean remove){
        runCollectionMutation(()->{
            if(remove)collectionStore.removeBibleBook(group,book);
            else collectionStore.addBibleChapters(group,book,Collections.emptyList(),true,bible);
        },success->{
            passagePicker.finishBookChange(group,book,remove,success,
                screen==Screen.PICKER_BOOKS&&pickerTargetId==group,this::changeWholeBook);
            if(success)refreshCollectionAfterBulkChange(group);
        });
    }

    private void runCollectionMutation(Runnable write,java.util.function.Consumer<Boolean> finished){
        runCollectionMutation(write,finished,null);
    }

    private void runCollectionMutation(Runnable write,java.util.function.Consumer<Boolean> finished,
            Runnable detachedSuccess){
        if(isDestroyed()||isFinishing()||backupInProgress)return;
        collectionExecutor.execute(()->{
            boolean saved=false;
            try{write.run();saved=true;}
            catch(RuntimeException error){android.util.Log.e("CollectionBooks","Unable to save Bible chapters",error);}
            boolean success=saved;
            // Persistent cleanup must survive rotation and run before the worker's release notice.
            runOnUiThread(()->{
                if(isDestroyed()||isFinishing()){
                    if(success&&detachedSuccess!=null)detachedSuccess.run();
                    return;
                }
                if(!success)toast("Не удалось изменить коллекцию");
                finished.accept(success);
            });
        });
    }

    private void refreshCollectionAfterBulkChange(long group){
        if(screen==Screen.COLLECTION_DETAIL&&selectedGroup==group)render();
    }

    private void clearPassageSelection(){
        rangeStartChapter=-1;rangeStartVerse=-1;rangeEndChapter=-1;rangeEndVerse=-1;
    }
    private void selectPassageVerse(int c,int v){
        if(rangeStartChapter<0 || rangeEndChapter>=0){
            rangeStartChapter=c;rangeStartVerse=v;rangeEndChapter=-1;rangeEndVerse=-1;
        } else if(c<rangeStartChapter || (c==rangeStartChapter && v<rangeStartVerse)) {
            rangeEndChapter=rangeStartChapter;rangeEndVerse=rangeStartVerse;
            rangeStartChapter=c;rangeStartVerse=v;
        } else {rangeEndChapter=c;rangeEndVerse=v;}
        int scroll=currentScroller==null?0:currentScroller.getScrollY();
        render();
        if(currentScroller!=null)currentScroller.post(()->currentScroller.scrollTo(0,scroll));
    }
    private boolean inPassageRange(int chapter,int verse){
        if(rangeStartChapter<0)return false;
        return PassageRangeLogic.contains(rangeStartChapter,rangeStartVerse,
            rangeEndChapter<0?rangeStartChapter:rangeEndChapter,
            rangeEndChapter<0?rangeStartVerse:rangeEndVerse,chapter,verse);
    }
    private String passageSummary(){
        if(rangeStartChapter<0)return "Отрывок не выбран";
        int ec=rangeEndChapter<0?rangeStartChapter:rangeEndChapter;
        int ev=rangeEndChapter<0?rangeStartVerse:rangeEndVerse;
        return Bible.reference(selectedBook,rangeStartChapter,rangeStartVerse,ec,ev);
    }
    private void renderChapter() {
        title(Bible.NAMES[selectedBook-1]+" "+selectedChapter,null);
        if(bibleNotReady())return;
        List<Bible.Verse> verses=bible.chapter(selectedBook,selectedChapter);
        if(verses.isEmpty()){ui.helper(content,"Нет текста главы.");return;}
        bibleReader.rememberChapter(selectedBook,selectedChapter);
        ui.action(content,"Глава "+selectedChapter+"  ▾",false,this::showChapterJump);
        Map<Integer,LocalStore.VerseStatus> statuses=store.statusesForChapter(
            Bible.NAMES[selectedBook-1],selectedChapter);
        if(bibleReader.renderChapter(content,selectedBook,verses,statuses,focusVerse,
            ()->screen==Screen.CHAPTER?currentScroller:null,this::date))focusVerse=-1;
    }
    private ArrayAdapter<String> spinnerAdapter(String[] values){
        return new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,values){
            @Override public View getView(int position,View reusable,ViewGroup parent){
                TextView text=ui.txt(getItem(position),15,WHITE,true);
                text.setGravity(Gravity.CENTER_VERTICAL);
                text.setPadding(ui.dp(8),ui.dp(5),ui.dp(4),ui.dp(5));
                return text;
            }
            @Override public View getDropDownView(int position,View reusable,ViewGroup parent){
                TextView text=ui.txt(getItem(position),16,WHITE,false);
                text.setPadding(ui.dp(14),ui.dp(14),ui.dp(14),ui.dp(14));
                text.setBackgroundColor(CARD_ALT);
                return text;
            }
        };
    }
    private Spinner chapterPicker(int selected){
        int count=bible.chapterCount(selectedBook);String[] names=new String[count];
        for(int i=0;i<count;i++)names[i]="Глава "+(i+1);
        Spinner spin=new Spinner(this);
        spin.setAdapter(spinnerAdapter(names));
        spin.setSelection(Math.max(0,Math.min(count-1,selected-1)));return spin;
    }
    private void updateVersePicker(Spinner verses,int chapter,int chosen){
        int count=bible.chapter(selectedBook,chapter).size();
        String[] labels=new String[count];for(int i=0;i<count;i++)labels[i]="Стих "+(i+1);
        verses.setAdapter(spinnerAdapter(labels));
        verses.setSelection(Math.max(0,Math.min(count-1,chosen-1)));
    }
    private void bindChapterVerse(Spinner chapter,Spinner verse){
        chapter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                int prev=verse.getSelectedItemPosition()+1;
                updateVersePicker(verse,pos+1,Math.max(1,prev));
            }
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
    }
    private void showChapterJump(){
        if(!bible.isReady())return;
        Dialog dialog=new Dialog(this,R.style.MemoryDialogTheme);
        LinearLayout layout=ui.column();layout.setPadding(ui.dp(18),ui.dp(24),ui.dp(18),ui.dp(20));
        layout.setBackgroundColor(BG);
        TextView name=ui.displayText(Bible.NAMES[selectedBook-1],25);
        name.setGravity(Gravity.CENTER);ui.add(layout,name,0);
        EditText number=ui.numeric("Введите номер главы","");
        number.setSingleLine(true);number.setImeOptions(EditorInfo.IME_ACTION_DONE);
        ui.add(layout,number,15);
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=ui.column();list.setPadding(0,ui.dp(8),0,ui.dp(16));scroll.addView(list);
        int total=bible.chapterCount(selectedBook);
        Runnable refresh=()->{
            list.removeAllViews();
            int numberFilter=number(number);
            if(numberFilter>0){
                if(numberFilter<=total){
                    final int selection=numberFilter;
                    TextView one=ui.button("Глава "+selection,true,()->{
                        dialog.dismiss();openReaderVerseGrid(selection);
                    });
                    ui.add(list,one,5);
                }else ui.helper(list,"В этой книге только "+total+" глав.");
                return;
            }
            for(int base=1;base<=total;base+=4){
                LinearLayout row=ui.row();
                for(int k=base;k<base+4;k++){
                    if(k>total){row.addView(new View(this),new LinearLayout.LayoutParams(0,ui.dp(52),1));continue;}
                    final int chapter=k;
                    TextView b=ui.button(String.valueOf(k),k==selectedChapter,()->{
                        dialog.dismiss();openReaderVerseGrid(chapter);
                    });
                    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ui.dp(52),1);
                    p.setMargins(ui.dp(3),ui.dp(4),ui.dp(3),ui.dp(4));row.addView(b,p);
                }
                ui.add(list,row,2);
            }
        };
        number.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){refresh.run();}
            public void afterTextChanged(Editable editable){}
        });
        number.setOnEditorActionListener((v,action,event)->{
            if(action!=EditorInfo.IME_ACTION_DONE)return false;
            int chosen=number(number);
            if(chosen>=1&&chosen<=total){dialog.dismiss();selectedChapter=chosen;
                bibleReader.rememberChapter(selectedBook,chosen);render();}
            else toast("Укажи главу от 1 до "+total);
            return true;
        });
        refresh.run();
        layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView close=ui.button("Закрыть",false,dialog::dismiss);
        ui.add(layout,close,8);
        dialog.setContentView(layout);
        Window window=dialog.getWindow();
        if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);}
        dialog.show();
        window=dialog.getWindow();if(window!=null)window.setLayout(-1,-1);
    }
    /** Always-visible, four-field passage composer. Tapping Scripture never selects ranges. */
    private void renderInlineRangeBuilder(){renderInlineRangeBuilder(false);}
    private void renderInlineRangeBuilder(boolean cardPicker){
        LinearLayout builder=ui.card(content);
        ui.add(builder,ui.txt("Выбрать отрывок",19,WHITE,true),0);
        if(!cardPicker)ui.helper(builder,"Укажи начало и конец. Можно выбрать несколько глав.");
        int sc=rangeStartChapter<1?selectedChapter:rangeStartChapter;
        int ec=rangeEndChapter<1?sc:rangeEndChapter;
        int sv=rangeStartVerse<1?1:rangeStartVerse;
        int ev=rangeEndVerse<1?sv:rangeEndVerse;
        Spinner startChapter=chapterPicker(sc),endChapter=chapterPicker(ec);
        Spinner startVerse=new Spinner(this),endVerse=new Spinner(this);
        updateVersePicker(startVerse,sc,sv);updateVersePicker(endVerse,ec,ev);
        LinearLayout first=ui.row(),last=ui.row();
        TextView firstHeading=ui.txt("НАЧАЛО",12,GREEN,true);ui.add(builder,firstHeading,14);
        first.addView(startChapter,new LinearLayout.LayoutParams(0,ui.dp(53),1));
        first.addView(startVerse,new LinearLayout.LayoutParams(0,ui.dp(53),1));ui.add(builder,first,5);
        ui.add(builder,ui.txt("КОНЕЦ",12,GREEN,true),12);
        last.addView(endChapter,new LinearLayout.LayoutParams(0,ui.dp(53),1));
        last.addView(endVerse,new LinearLayout.LayoutParams(0,ui.dp(53),1));ui.add(builder,last,5);
        TextView info=ui.txt("",13,MUTED,false);ui.add(builder,info,9);
        Runnable update=()->{
            int a=startChapter.getSelectedItemPosition()+1,b=startVerse.getSelectedItemPosition()+1;
            int c=endChapter.getSelectedItemPosition()+1,d=endVerse.getSelectedItemPosition()+1;
            boolean valid=PassageRangeLogic.valid(a,b,c,d);
            info.setText(valid?Bible.reference(selectedBook,a,b,c,d):"Конец отрывка раньше начала");
            info.setTextColor(valid?GREEN:RED);
            rangeStartChapter=a;rangeStartVerse=b;rangeEndChapter=c;rangeEndVerse=d;
        };
        final boolean[] endWasManual={sc!=ec||sv!=ev},suppress={true};
        Runnable matchStart=()->{
            if(endWasManual[0]){
                // Keep an intentional end unless a later start would invalidate it.
                int a=startChapter.getSelectedItemPosition()+1;
                int b=startVerse.getSelectedItemPosition()+1;
                int c=endChapter.getSelectedItemPosition()+1;
                int d=endVerse.getSelectedItemPosition()+1;
                if(PassageRangeLogic.valid(a,b,c,d)){update.run();return;}
                endWasManual[0]=false;
            }
            int targetChapter=startChapter.getSelectedItemPosition()+1;
            int targetVerse=startVerse.getSelectedItemPosition()+1;
            suppress[0]=true;
            if(endChapter.getSelectedItemPosition()!=targetChapter-1)
                endChapter.setSelection(targetChapter-1);
            updateVersePicker(endVerse,targetChapter,targetVerse);
            suppress[0]=false;
            update.run();
        };
        android.widget.AdapterView.OnItemSelectedListener startListener=new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                if(!suppress[0])parent.post(matchStart);
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        };
        android.widget.AdapterView.OnItemSelectedListener endListener=new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                if(!suppress[0]){endWasManual[0]=true;parent.post(update);}
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        };
        startVerse.setOnItemSelectedListener(startListener);
        endVerse.setOnItemSelectedListener(endListener);
        startChapter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                int previous=startVerse.getSelectedItemPosition()+1;
                updateVersePicker(startVerse,position+1,Math.max(1,previous));
                parent.post(matchStart);
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        endChapter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                if(!suppress[0])endWasManual[0]=true;
                int previous=endVerse.getSelectedItemPosition()+1;
                updateVersePicker(endVerse,position+1,Math.max(1,previous));
                parent.post(update);
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        builder.post(()->{
            if(builder.getWindowToken()==null ||
                (screen!=Screen.BIBLE_PICKER && screen!=Screen.CHAPTER && screen!=Screen.CARD_PICK))return;
            suppress[0]=false;matchStart.run();
        });
        update.run();
        ui.action(builder,screen==Screen.BIBLE_PICKER?"Добавить отрывок":cardPicker?"Добавить карточку":"Просмотреть отрывок",true,()->{
            update.run();
            if(!PassageRangeLogic.valid(rangeStartChapter,rangeStartVerse,rangeEndChapter,rangeEndVerse)){
                toast("Конец не может быть раньше начала");return;
            }
            if(screen==Screen.BIBLE_PICKER){
                if(savePickedPassage(rangeStartChapter,rangeStartVerse,rangeEndChapter,rangeEndVerse)){
                    clearInsertionContext();screen=pickerBackScreen;render();
                }
            }
            else if(cardPicker)addCardFromSelection(selectedDeck);
            else previewPassage();
        });
        if(!cardPicker&&screen!=Screen.BIBLE_PICKER)ui.action(builder,"Добавить в карточки",false,()->{
            update.run();
            if(!PassageRangeLogic.valid(rangeStartChapter,rangeStartVerse,rangeEndChapter,rangeEndVerse)){
                toast("Неверный диапазон");return;
            }
            chooseCardDeck(this::addCardFromSelection);
        });
    }
    private void bindChapterVerseAndUpdate(Spinner chapter,Spinner verse,Runnable update){
        chapter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                int before=verse.getSelectedItemPosition()+1;
                updateVersePicker(verse,pos+1,Math.max(1,before));
                verse.post(update);
            }
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
    }
    private void previewPassage(){
        if(rangeStartChapter<0){toast("Выбери стих или укажи границы");return;}
        int ec=rangeEndChapter<0?rangeStartChapter:rangeEndChapter;
        int ev=rangeEndChapter<0?rangeStartVerse:rangeEndVerse;
        List<Bible.Verse> verses;
        try{verses=bible.passage(selectedBook,rangeStartChapter,rangeStartVerse,ec,ev);}
        catch(IllegalArgumentException ex){toast(ex.getMessage());return;}
        String ref=Bible.reference(selectedBook,rangeStartChapter,rangeStartVerse,ec,ev);
        LocalStore.Entry existing=null;
        for(LocalStore.Entry e:store.allEntries())if(ref.equals(e.reference)){
            existing=e;break;
        }
        int mastered=existing==null?0:store.masteryCount(existing.id);
        String description=ref+"\n"+verses.size()+" стихов · освоено "+mastered;
        AlertDialog.Builder dialog=new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setTitle("Твой отрывок")
            .setMessage(description)
            .setNegativeButton("Назад",null);
        if(existing!=null){
            final long entryId=existing.id;
            dialog.setPositiveButton("Продолжить изучение",(d,w)->{
                selectingPassage=false;openTraining(entryId);
            });
            dialog.setNeutralButton("Добавить в другую подборку",(d,w)->{
                chooseGroup(groupId->{store.add(groupId,ref,ref,verses);toast("Добавлено в подборку");});
            });
        }else{
            dialog.setPositiveButton("Начать изучение",(d,w)->{
                chooseGroup(groupId->{long id=store.add(groupId,ref,ref,verses);
                    selectingPassage=false;openTraining(id);});
            });
            dialog.setNeutralButton("Добавить в подборку",(d,w)->{
                chooseGroup(groupId->{store.add(groupId,ref,ref,verses);toast("Добавлено в подборку");});
            });
        }
        dialog.show();
    }
    private interface Pick {void apply(long groupId);}
    private void chooseGroup(Pick action) {
        List<LocalStore.UnifiedCollection> collections=store.unifiedCollections();
        String[] names=new String[collections.size()+1];
        for(int i=0;i<collections.size();i++)names[i]=collections.get(i).title;
        names[collections.size()]="＋ Новая коллекция";
        ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Добавить в коллекцию").setItems(names,(d,which)->{
                if(which==collections.size())newGroup(action);
                else action.apply(collections.get(which).groupId);
            }).setNegativeButton("Отмена",null).create());
    }
    private void newGroup(Pick afterCreate) {
        newUnifiedCollection(u->{
            if(afterCreate!=null)afterCreate.apply(u.groupId);
            else{openUnifiedId=u.id;screen=Screen.COLLECTION_DETAIL;render();}
        });
    }
    /** Study collections get only these four approved actions, in this exact order. */
    private void showStudyGroupActions(LocalStore.Group group,View anchor){
        String[] actions={"Добавить свой текст","Переименовать","Удалить"};
        String[] icons={"file","pencil","trash"};
        menus.showActionsMenu(anchor,actions,icons,which->{
            selectedGroup=group.id;
            switch(which){
                case 0:customTargetGroupId=group.id;screen=Screen.CUSTOM;render();break;
                case 1:renameGroup(group);break;
                case 2:confirmDeleteStudyGroup(group);break;
            }
        });
    }
    private void confirmDeleteStudyGroup(LocalStore.Group group){
        ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Удалить коллекцию из списка?")
            .setMessage("Коллекция исчезнет из списка. Стихи и прежний прогресс изучения сохранятся в локальной базе.")
            .setPositiveButton("Удалить",(d,w)->{
                store.deleteGroup(group.id);screen=Screen.HOME;render();
            }).setNegativeButton("Отмена",null).create());
    }


    private void renderEntries() {

        LocalStore.Group group = store.group(selectedGroup);

        if (group == null) {
            screen = Screen.HOME;
            render();
            return;
        }

        // Название открытой коллекции.
        title(group.title, null);

        // Общий прогресс изучения.
        ui.helper(
                content,
                "Освоено: " + studyProgressFor(group.id)
        );

        // Все стихи коллекции прокручиваются независимо
        // от нижних кнопок.
        for (LocalStore.Entry entry :
                store.entries(group.id)) {

            entryCard(entry);
        }

        // Основное и вторичное действия внизу экрана.
        addStudyCollectionFooter(group);
    }

    private void renameGroup(LocalStore.Group group) {
        EditText name=ui.input("Название коллекции",false);name.setText(group.title);
        LinearLayout box=ui.column();box.setPadding(ui.dp(20),ui.dp(5),ui.dp(20),ui.dp(5));box.addView(name);
        ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setTitle("Переименовать коллекцию").setView(box)
            .setPositiveButton("Сохранить",(d,w)->{
                if(value(name).isEmpty()){toast("Укажи название");return;}
                store.renameGroup(group.id,value(name));render();
            }).setNegativeButton("Отмена",null).create());
    }
    /** Reset only when leaving the shared picker or starting a distinct insertion action. */
    private void clearInsertionContext(){
        insertAfterEntryId=-1;insertAfterCardId=-1;
        studyInsertionTrail.clear();cardInsertionTrail.clear();
    }
    private void removedInsertionCandidate(long id,boolean card){
        List<Long> trail=card?cardInsertionTrail:studyInsertionTrail;
        trail.remove(id);
        if(card){
            if(insertAfterCardId==id)
                insertAfterCardId=trail.isEmpty()?-1:trail.get(trail.size()-1);
        }else{
            if(insertAfterEntryId==id)
                insertAfterEntryId=trail.isEmpty()?-1:trail.get(trail.size()-1);
        }
    }
    private void repositionStudyAfter(long group,long added,long after){
        List<Long> ids=new ArrayList<>();
        for(LocalStore.Entry item:store.entries(group))if(item.id!=added)ids.add(item.id);
        int index=ids.indexOf(after);ids.add(index<0?ids.size():index+1,added);
        store.reorderStudy(group,ids);
    }
    private void repositionCardAfter(long deck,long added,long after){
        List<Long> ids=new ArrayList<>();
        for(LocalStore.Flashcard f:store.flashcards(deck))if(f.id!=added)ids.add(f.id);
        int index=ids.indexOf(after);ids.add(index<0?ids.size():index+1,added);
        store.reorderCards(deck,ids);
    }
    /** Dedicated drag-to-reorder mode; dragging never starts in normal study lists. */
    private void renderStudyReorder(){
        title("Изменить порядок", "Удерживай строку и перетащи её на новое место");
        List<LocalStore.Entry> list=store.entries(selectedGroup);
        List<Long> ids=new ArrayList<>();for(LocalStore.Entry e:list)ids.add(e.id);
        for(LocalStore.Entry e:list)renderReorderRow(e.id,e.reference,ids,false);
        ui.action(content,"Готово",true,()->{
            LocalStore.UnifiedCollection u=store.unifiedForGroup(selectedGroup);
            screen=u==null?Screen.ENTRIES:Screen.COLLECTION_DETAIL;
            if(u!=null)openUnifiedId=u.id;render();
        });
    }
    private void renderCardReorder(){
        title("Изменить порядок", "Удерживай строку и перетащи её на новое место");
        List<LocalStore.Flashcard> list=store.flashcards(selectedDeck);
        List<Long> ids=new ArrayList<>();for(LocalStore.Flashcard f:list)ids.add(f.id);
        for(LocalStore.Flashcard f:list)renderReorderRow(f.id,f.reference,ids,true);
        ui.action(content,"Готово",true,()->{screen=Screen.CARD_DECK;render();});
    }
    private void renderReorderRow(long id,String label,List<Long> ids,boolean cards){
        LinearLayout item=ui.row();item.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12));
        item.setBackground(ui.outlined(CARD,14,BORDER));
        MenuGlyph dragGlyph=new MenuGlyph(this,"shuffle",GREEN);
        item.addView(dragGlyph,new LinearLayout.LayoutParams(ui.dp(40),ui.dp(44)));
        item.addView(ui.txt(label,16,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        item.setContentDescription("Удерживай и перемести: "+label);
        item.setOnLongClickListener(v->{
            android.content.ClipData data=android.content.ClipData.newPlainText("study-row",String.valueOf(id));
            v.startDragAndDrop(data,new View.DragShadowBuilder(v),id,0);return true;
        });
        item.setOnDragListener((v,event)->{
            if(event.getAction()==DragEvent.ACTION_DROP){
                if(!(event.getLocalState() instanceof Long))return false;
                long moving=(Long)event.getLocalState();
                if(moving==id)return true;
                int a=ids.indexOf(moving),b=ids.indexOf(id);
                if(a<0||b<0)return false;
                ids.remove(a);ids.add(b,moving);
                if(cards)store.reorderCards(selectedDeck,ids);
                else store.reorderStudy(selectedGroup,ids);
                render();return true;
            }
            return true;
        });
        ui.add(content,item,7);
    }

    private interface OnBibleRangeEdit {void save(int sc,int sv,int ec,int ev);}
    private int[] parseBibleLocation(String reference){
        if(reference==null||!reference.contains(":"))return null;
        try{
            String[] a=reference.split(":",2);
            return new int[]{Integer.parseInt(a[0]),Integer.parseInt(a[1])};
        }catch(Exception err){return null;}
    }
    private void editBibleRange(String heading,int book,int sc,int sv,int ec,int ev,OnBibleRangeEdit save){
        LinearLayout wrap=ui.column();wrap.setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(10));
        ui.add(wrap,ui.txt(Bible.NAMES[book-1],17,WHITE,true),0);
        EditText startChapter=ui.numeric("Начальная глава",String.valueOf(sc));
        EditText startVerse=ui.numeric("Начальный стих",String.valueOf(sv));
        EditText endChapter=ui.numeric("Конечная глава",String.valueOf(ec));
        EditText endVerse=ui.numeric("Конечный стих",String.valueOf(ev));
        final boolean[] changed={false};
        TextWatcher autoEnd=new TextWatcher(){
            public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            public void onTextChanged(CharSequence text,int start,int before,int count){
                if(changed[0])return;
                String a=startChapter.getText().toString();String b=startVerse.getText().toString();
                if(!a.equals(endChapter.getText().toString()))endChapter.setText(a);
                if(!b.equals(endVerse.getText().toString()))endVerse.setText(b);
            }
            public void afterTextChanged(Editable e){}
        };
        startChapter.addTextChangedListener(autoEnd);startVerse.addTextChangedListener(autoEnd);
        TextWatcher markManual=new TextWatcher(){
            public void beforeTextChanged(CharSequence t,int st,int c,int after){}
            public void onTextChanged(CharSequence t,int st,int b,int count){changed[0]=true;}
            public void afterTextChanged(Editable e){}
        };
        endChapter.addTextChangedListener(markManual);endVerse.addTextChangedListener(markManual);
        ui.add(wrap,ui.txt("НАЧАЛО",12,GREEN,true),13);
        LinearLayout first=ui.row();first.addView(startChapter,new LinearLayout.LayoutParams(0,ui.dp(54),1));
        first.addView(startVerse,new LinearLayout.LayoutParams(0,ui.dp(54),1));ui.add(wrap,first,5);
        ui.add(wrap,ui.txt("КОНЕЦ",12,GREEN,true),12);
        LinearLayout last=ui.row();last.addView(endChapter,new LinearLayout.LayoutParams(0,ui.dp(54),1));
        last.addView(endVerse,new LinearLayout.LayoutParams(0,ui.dp(54),1));ui.add(wrap,last,5);
        ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle(heading).setView(wrap)
            .setPositiveButton("Сохранить",(d,w)->{
                int a=number(startChapter),b=number(startVerse),c=number(endChapter),end=number(endVerse);
                if(!PassageRangeLogic.valid(a,b,c,end)){
                    toast("Неверный диапазон");return;
                }
                try{bible.passage(book,a,b,c,end);save.save(a,b,c,end);}
                catch(Exception err){toast(err.getMessage()==null?"Ошибка изменения диапазона":err.getMessage());}
            }).setNegativeButton("Отмена",null).create());
    }
    private void editStudyEntry(LocalStore.Entry e){
        int book=bookForEntry(e);
        if(book>0 && !e.lines().isEmpty()){
            int[] first=parseBibleLocation(e.lines().get(0).ref);
            int[] last=parseBibleLocation(e.lines().get(e.lines().size()-1).ref);
            if(first!=null&&last!=null){
                editBibleRange("Изменить отрывок",book,first[0],first[1],last[0],last[1],
                    (a,b,c,d)->{
                        List<Bible.Verse> lines=bible.passage(book,a,b,c,d);
                        store.reviseStudyPassage(e.id,Bible.reference(book,a,b,c,d),lines);
                        render();
                    });
                return;
            }
        }
        editCustomStudyEntry(e);
    }
    private void editCustomStudyEntry(LocalStore.Entry e){
        EditText source=ui.input("Название / источник",false);source.setText(e.reference);
        EditText words=ui.input("Текст",true);words.setMinLines(5);
        StringBuilder original=new StringBuilder();
        for(Bible.Verse v:e.lines()){
            if(original.length()>0)original.append('\n');original.append(v.text);
        }
        words.setText(original.toString());
        LinearLayout fields=ui.column();fields.setPadding(ui.dp(20),ui.dp(5),ui.dp(20),ui.dp(12));
        ui.add(fields,source,0);ui.add(fields,words,10);
        AlertDialog dialog=new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Изменить свой текст").setView(fields)
            .setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();
        ui.styledDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(value(source).isEmpty()){source.setError("Укажи название");source.requestFocus();return;}
            List<Bible.Verse> lines;
            try{lines=CustomStudyText.parse(value(words),e.lines());}
            catch(IllegalArgumentException invalid){
                words.setError(invalid.getMessage());words.requestFocus();return;
            }
            store.reviseStudyPassage(e.id,value(source),lines);
            dialog.dismiss();
            render();
        });
    }
    private void editCardEntry(LocalStore.Flashcard card){
        if(card.bookId>0){
            editBibleRange("Изменить карточку",card.bookId,card.firstChapter,card.firstVerse,
                card.lastChapter,card.lastVerse,(a,b,c,d)->{
                    String ref=Bible.reference(card.bookId,a,b,c,d);
                    store.changeCardInDeck(selectedDeck,card,card.bookId,a,b,c,d,
                        ref,texts(bible.passage(card.bookId,a,b,c,d)));
                    render();
                });
            return;
        }
        EditText source=ui.input("Название / источник",false);source.setText(card.reference);
        EditText words=ui.input("Текст",true);words.setMinLines(4);words.setText(card.content);
        LinearLayout fields=ui.column();fields.setPadding(ui.dp(20),ui.dp(5),ui.dp(20),ui.dp(12));
        ui.add(fields,source,0);ui.add(fields,words,9);
        ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Изменить карточку").setView(fields)
            .setPositiveButton("Сохранить",(d,w)->{
                if(value(source).isEmpty()||value(words).isEmpty()){toast("Заполни поля");return;}
                store.changeCardInDeck(selectedDeck,card,0,0,0,0,0,value(source),value(words));
                render();
            }).setNegativeButton("Отмена",null).create());
    }

    private void showEntryActions(LocalStore.Entry entry,View anchor){
        String[] labels={"Изменить отрывок","Добавить стих / отрывок после него","Удалить"};
        String[] icons={"pencil","book","trash"};
        menus.showActionsMenu(anchor,labels,icons,which->{
            switch(which){
                case 0:editStudyEntry(entry);break;
                case 1:
                    clearInsertionContext();
                    insertAfterEntryId=entry.id;studyInsertionTrail.add(entry.id);
                    openBiblePicker(BibleTarget.STUDY_GROUP,entry.groupId,screen==Screen.COLLECTION_DETAIL?Screen.COLLECTION_DETAIL:Screen.ENTRIES);break;
                case 2:
                    ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                        .setTitle("Убрать отрывок из коллекции?")
                        .setPositiveButton("Убрать",(d,w)->{
                            store.removeUnifiedPassage(entry.groupId,entry.reference);render();
                        }).setNegativeButton("Отмена",null).create());break;
            }
        });
    }
    private void showCardActions(LocalStore.Flashcard card,View anchor){
        String[] labels={"Изменить отрывок","Добавить стих / отрывок после него","Удалить"};
        String[] icons={"pencil","book","trash"};
        menus.showActionsMenu(anchor,labels,icons,which->{
            switch(which){
                case 0:editCardEntry(card);break;
                case 1:
                    clearInsertionContext();
                    insertAfterCardId=card.id;cardInsertionTrail.add(card.id);
                    openBiblePicker(BibleTarget.FLASHCARD_DECK,selectedDeck,Screen.CARD_DECK);break;
                case 2:
                    ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                        .setTitle("Убрать карточку из подборки?")
                        .setPositiveButton("Убрать",(d,w)->{
                            store.removeFlashcard(selectedDeck,card.id);render();
                        }).setNegativeButton("Отмена",null).create());break;
            }
        });
    }

    private void manageEntry(LocalStore.Entry entry) {
        new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setTitle(entry.title)
            .setItems(new String[]{"Переименовать отрывок","Удалить отрывок"},(d,choice)->{
                if(choice==0){
                    EditText name=ui.input("Название отрывка",false);name.setText(entry.title);
                    LinearLayout box=ui.column();box.setPadding(ui.dp(20),ui.dp(5),ui.dp(20),ui.dp(5));box.addView(name);
                    new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setTitle("Переименовать отрывок").setView(box)
                        .setPositiveButton("Сохранить",(dd,w)->{
                            if(value(name).isEmpty()){toast("Укажи название");return;}
                            store.renameEntry(entry.id,value(name));render();
                        }).setNegativeButton("Отмена",null).show();
                } else new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                    .setTitle("Удалить отрывок?")
                    .setMessage("Будут удалены и его результаты обучения.")
                    .setPositiveButton("Удалить",(dd,w)->{store.deleteEntry(entry.id);render();})
                    .setNegativeButton("Отмена",null).show();
            }).show();
    }
    private void entryCard(LocalStore.Entry e) {
        entryCard(e,store.collectionProgress());
    }
    private void entryCard(LocalStore.Entry e,CollectionProgressSnapshot snapshot){
        long source=snapshot.canonicalId(e.id);
        int learned=snapshot.masteryCount(source);
        int established=snapshot.consolidatedCount(source);
        String progress=established+" закреплено · "+Math.max(0,learned-established)+" предварительно освоено";
        String detail=e.reference+" · "+progress+" · Связок: "+snapshot.linkedCount(source);
        LinearLayout c=ui.card(content);c.setBackground(artwork.homeSurface());
        LinearLayout row=ui.row();
        LinearLayout body=ui.column();
        ui.add(body,ui.displayText(e.title,17),0);
        ui.add(body,ui.txt(detail,13,MUTED,false),6);
        row.addView(body,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=ui.txt("⋮",26,MUTED,true);more.setGravity(Gravity.CENTER);
        more.setContentDescription("Действия с отрывком "+e.reference);
        row.addView(more,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(54)));
        more.setOnClickListener(v->showEntryActions(e,more));
        body.setFocusable(true);
        body.setContentDescription("Изучать "+e.reference);
        body.setOnClickListener(v->openTraining(e.id));
        ui.add(c,row,0);
    }
    private String date(long millis){return new SimpleDateFormat("dd.MM",Locale.forLanguageTag("ru-RU")).format(new Date(millis));}
    private void renderCustom() {
        title("Свой текст","Вставь стих, песню Писания или собственный учебный фрагмент.");
        LinearLayout form=ui.card(content);
        ui.label(form,"Название");EditText title=ui.input("Например: Обетования",false);ui.add(form,title,7);
        ui.label(form,"Ссылка / источник");EditText ref=ui.input("Например: Римлянам 8:28",false);ui.add(form,ref,7);
        ui.label(form,"Текст для заучивания");
        EditText lines=ui.input("Вставь текст. Для большого отрывка размести каждый стих с новой строки.",true);
        ui.add(form,lines,7);
        ui.helper(form,"Каждая непустая строка будет отдельным шагом обучения (до 500 строк).");
        ui.action(form,"Сохранить в коллекцию",true,()->{
            String t=value(title),r=value(ref),s=value(lines);
            if(t.isEmpty()||s.isEmpty()){toast("Введи название и текст");return;}
            List<Bible.Verse> items;
            try{items=CustomStudyText.parse(s,null);}
            catch(IllegalArgumentException invalid){
                lines.setError(invalid.getMessage());lines.requestFocus();return;
            }
            if(customTargetGroupId>0){
                long groupId=customTargetGroupId;
                if(store.group(groupId)==null){toast("Коллекция не найдена");return;}
                customTargetGroupId=-1;
                long id=store.add(groupId,t,r.isEmpty()?"Свой текст":r,items);
                openTraining(id);
            }else{
                chooseGroup(groupId->{long id=store.add(groupId,t,r.isEmpty()?"Свой текст":r,items);openTraining(id);});
            }
        });

    }
    /** Pressing 'Повторить изученное' starts the first due exercise immediately. */
    private void startReviewSession(long group) {
        lessonWaitingForBible=null;
        if(!bibleReadyForRecall())return;
        long now=System.currentTimeMillis();
        ReviewSession.Start start=reviewSession.start(store,getSharedPreferences(SESSION_PREFS,MODE_PRIVATE),group,now);
        LocalStore.DueItem item=start.first();
        while(item!=null){
            if(prepareReview(item)){
                if(item==start.first())lesson.lessonWriting.draft=start.draft();
                render();return;
            }
            item=reviewSession.advance(store,now);
        }
        screen=Screen.DUE;render();
    }

    private void renderDue() {
        LearningHelp help=reviewSession.group()>0?LearningHelp.REVIEW:LearningHelp.ALL_REVIEWS;
        title(help.title,null);
        LinearLayout c=ui.headerCard(content);
        ui.add(c,ui.txt("Пока нет заданий на повторение",21,WHITE,true),0);
        ui.helper(c,reviewSession.group()>0?"В выбранной коллекции сейчас нет заданий, срок которых наступил.":
            "Сейчас нет запланированных заданий, срок которых наступил.");
        ui.action(content,"На главную",true,()->{screen=Screen.HOME;render();});
    }
    private boolean openReview(LocalStore.DueItem item){
        if(!prepareReview(item))return false;
        render();return true;
    }
    private boolean prepareReview(LocalStore.DueItem item){
        lesson.answerReview=null;
        LocalStore.Entry entry=store.entry(item.entryId);
        if(!reviewSession.open(entry,item))return false;
        lesson.lessonSourceEntry=entry.id;lesson.lessonSourceGroup=entry.groupId;
        lesson.selectedEntry=entry.id;lesson.lessonPracticeAgain=false;
        lesson.studyStage=reviewSession.current().studyStage();
        lesson.groupMode=false;lesson.forcedStep=null;lesson.stepCursor=0;
        lesson.weakVerse=-1;lesson.weakTransitionFrom=-1;
        resetTrainingStep(entry);
        lesson.lessonWriting.clearGaps();
        lesson.trainingMode=TrainingMode.TYPE;screen=Screen.TRAIN;
        return true;
    }
    private void advanceReviewSession(){
        long now=System.currentTimeMillis();
        LocalStore.DueItem next;
        while((next=reviewSession.advance(store,now))!=null){
            if(openReview(next))return;
        }
        lesson.forcedStep=null;
        ReviewSessionPreferences.clearPending(getSharedPreferences(SESSION_PREFS,MODE_PRIVATE));
        screen=Screen.HOME;render();toast("Все запланированные повторения завершены");
    }
    private void rememberRecent(long id) {
        JSONArray updated=new JSONArray();updated.put(id);
        int count=1;
        for(Long prev:recentIds()) {
            if(prev==id || store.entry(prev)==null)continue;
            updated.put(prev);
            if(++count>=7)break;
        }
        getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit().putString("recent",updated.toString()).apply();
    }
    private List<Long> recentIds() {
        List<Long> ids=new ArrayList<>();
        try {
            JSONArray array=new JSONArray(getSharedPreferences(SESSION_PREFS,MODE_PRIVATE)
                .getString("recent","[]"));
            for(int i=0;i<Math.min(7,array.length());i++)ids.add(array.getLong(i));
        }catch(JSONException ex){ /* Corrupt metadata must not block the app. */ }
        return ids;
    }
    private Bundle saveActiveLesson(){
        if(!lesson.hasActiveMaterial())return null;
        saveTrainingSession();
        Bundle state=LessonCheckpoint.capture(store,lesson,reviewSession);
        if(reviewSession.active()){
            state.putLong("review_group",reviewSession.group());
        }else if(executingCollectionQueue){
            state.putLong("queue_group",collectionQueueGroup);
            state.putInt("queue_cursor",collectionQueueCursor);
        }else if(cardStudyCursor>=0){
            state.putLong("deck",cardStudyDeckId);
            state.putInt("deck_cursor",cardStudyCursor);
            state.putString("deck_material",cardStudyMaterial);
            long[] ids=new long[cardStudyQueue.size()];
            for(int i=0;i<ids.length;i++)ids[i]=cardStudyQueue.get(i);
            state.putLongArray("deck_queue",ids);
        }
        if(lesson.lessonSourceEntry!=lesson.selectedEntry)state.putString("source_material",lesson.lessonSourceMaterial);
        return state;
    }

    private static String cardLessonMaterial(List<LocalStore.Flashcard> deck){
        List<String> cards=new ArrayList<>();
        for(LocalStore.Flashcard card:deck){
            cards.add(Long.toString(card.id));cards.add(card.reference);cards.add(card.content);
            cards.add(card.bookId+":"+card.firstChapter+":"+card.firstVerse+":"+card.lastChapter+":"+card.lastVerse);
        }
        return cards.isEmpty()?"":SessionIntegrityLogic.fingerprint(cards);
    }

    private boolean restoreActiveLesson(Bundle state){
        if(state==null)return false;
        long id=state.getLong("entry");
        LocalStore.Entry entry=store.entry(id);
        long sourceId=state.getLong("source_entry");
        LocalStore.Entry source=sourceId==id?entry:store.entry(sourceId);
        if(!LessonCheckpoint.matchesMaterial(state,entry,source))return false;
        long deck=state.getLong("deck",-1);
        long[] deckQueue=state.getLongArray("deck_queue");
        int deckCursor=state.getInt("deck_cursor",-1);
        if(deck>0){
            String material=cardLessonMaterial(store.flashcards(deck));
            if(material.isEmpty()||!material.equals(state.getString("deck_material"))||
                deckQueue==null||deckCursor<0||deckCursor>=deckQueue.length||deckQueue[deckCursor]!=sourceId)return false;
        }else if(store.archivedReviewGroups().contains(source.groupId))return false;
        boolean inQueue=state.containsKey("queue_group");
        if(inQueue&&(collectionQueueGroup!=state.getLong("queue_group")||
            collectionQueueCursor!=state.getInt("queue_cursor")||collectionQueueCursor<0||
            collectionQueue.get(collectionQueueCursor)!=sourceId))return false;

        boolean review=state.containsKey("review_group");
        boolean linked=state.getBoolean("linked");
        boolean complete=state.getBoolean("complete");
        String stage=state.getString("stage","");
        StudyPlanLogic.Stage lessonStage;
        try{lessonStage=StudyPlanLogic.Stage.valueOf("VERSE_RUN".equals(stage)?"CHAIN":stage);}
        catch(IllegalArgumentException invalid){return false;}
        int start=state.getInt("begin",-1),end=state.getInt("end",-1);
        int cursor=state.getInt("cursor",-1);
        if(start<0||end<=start||end>entry.lines().size()||cursor<0)return false;
        if(!LessonCheckpoint.matchesRecall(store,state,id,stage,start,end,complete))return false;
        if(!review&&!linked){
            List<StudyPlanLogic.Step> steps=studySteps(entry,lessonStage);
            if(cursor>=steps.size()||steps.get(cursor).start!=start||steps.get(cursor).end!=end)
                return false;
            if(lessonStage==StudyPlanLogic.Stage.VERSE&&
                (store.exerciseDone(id,start)!=state.getInt("exercise_done")||
                !LessonFlowLogic.canOpen(cursor,store.unlockedPosition(id,steps.size()),steps.size())))return false;
        }

        String key=review?ReviewSessionPreferences.PREFIX:(linked?"linked_":"entry_")+id+"_";
        SharedPreferences pref=getSharedPreferences(SESSION_PREFS,MODE_PRIVATE);
        if(!(review&&complete)&&(!lessonStage.name().equals(pref.getString(key+"stage",""))||
            cursor!=pref.getInt(key+"step",-1)||start!=pref.getInt(key+"begin",-1)||
            end!=pref.getInt(key+"end",-1)))return false;
        if(!review&&!state.getString("material").equals(pref.getString(key+"lesson_material","")))return false;
        if(review){
            LocalStore.DueItem item=reviewSession.restore(store,pref,state.getLong("review_group",-1),
                System.currentTimeMillis(),entry,new LocalStore.DueItem(id,stage,start,end,0),complete);
            if(item==null)return false;
            if(!prepareReview(item)){reviewSession.clear();return false;}
        }else{
            lesson.selectedEntry=id;
            if(linked){
                prepareGroup(GroupLearningLogic.candidate(stage,start,end,"Продолжить связку"));
                lesson.returnVerseCursor=pref.getInt(key+"return_cursor",0);
            }
        }
        if(!(review&&complete))restoreTrainingSession(key,linked);
        lesson.lessonSourceEntry=sourceId;lesson.lessonSourceGroup=source.groupId;
        lesson.lessonSessionMaterial=state.getString("material","");
        lesson.lessonSourceMaterial=state.getString("source_material","");
        executingCollectionQueue=inQueue;
        if(deck>0){
            cardStudyDeckId=deck;cardStudyCursor=deckCursor;
            cardStudyMaterial=state.getString("deck_material","");
            for(long next:deckQueue)cardStudyQueue.add(next);
        }
        lesson.lessonPracticeAgain=state.getBoolean("practice_again");
        lesson.testAssisted=state.getBoolean("assisted");
        lesson.checked=lesson.passed=complete;
        if(complete)lesson.lastAccuracy=100;
        screen=Screen.TRAIN;
        return true;
    }

    private void saveTrainingSession(){
        LessonSessionPreferences.save(getSharedPreferences(SESSION_PREFS,MODE_PRIVATE),lesson,reviewSession);
    }
    private boolean restoreTrainingSession(long id) {return restoreTrainingSession(id,false);}
    private boolean restoreTrainingSession(long id,boolean linked) {
        return restoreTrainingSession((linked?"linked_":"entry_")+id+"_",linked);
    }
    private boolean sameLessonDraft(SharedPreferences preferences,String key){
        return lesson.lessonSessionMaterial.equals(preferences.getString(key+"lesson_material",lesson.lessonSessionMaterial));
    }
    private boolean restoreTrainingSession(String key,boolean linked){
        return LessonSessionPreferences.restore(getSharedPreferences(SESSION_PREFS,MODE_PRIVATE),key,linked,lesson);
    }
    private void openTraining(long id) {
        if(screen==Screen.TRAIN)saveTrainingSession();
        lesson.answerReview=null;
        reviewSession.clear();lesson.groupMode=false;lesson.forcedStep=null;
        if(screen==Screen.CARD_SESSION)flashcards.save(selectedDeck);
        LocalStore.Entry source=store.entry(id);
        lesson.lessonSourceEntry=id;lesson.lessonSourceGroup=source==null?-1:source.groupId;
        lesson.selectedEntry=store.canonicalStudyEntryId(id);
        id=lesson.selectedEntry;
        LocalStore.Entry entry=store.entry(id);
        if(entry==null){toast("Отрывок не найден");screen=Screen.HOME;render();return;}
        lesson.lessonSessionMaterial=LessonCheckpoint.material(entry);
        lesson.lessonSourceMaterial=source==null||source.id==entry.id?"":LessonCheckpoint.material(source);
        android.content.SharedPreferences linked=getSharedPreferences(SESSION_PREFS,MODE_PRIVATE);
        String lk="linked_"+id+"_";
        if(linked.getBoolean(lk+"pending",false)){
            int a=linked.getInt(lk+"begin",0),b=linked.getInt(lk+"end",0);
            String stage=linked.getString(lk+"group_stage","CHAIN");
            if(a>=0&&b>a&&b<=entry.lines().size()&&
                !store.isStepMastered(id,stage,a,b)&&sameLessonDraft(linked,lk)){
                GroupLearningLogic.Candidate cand=GroupLearningLogic.candidate(stage,a,b,"Продолжить связку");
                lesson.returnVerseCursor=linked.getInt(lk+"return_cursor",0);
                prepareGroup(cand);restoreTrainingSession(id,true);
                lesson.returnVerseCursor=linked.getInt(lk+"return_cursor",0);render();return;
            }
            linked.edit().remove(lk+"pending").apply();
        }
        if(!sameLessonDraft(linked,"entry_"+id+"_")||!restoreTrainingSession(id)){
            selectRecommendation(entry);resetTrainingStep(entry);
        } else if(lesson.trainingMode!=TrainingMode.TYPE) {
            List<StudyPlanLogic.Step> steps=studySteps(entry);
            if(!steps.isEmpty()) {
                StudyPlanLogic.Step current=steps.get(Math.min(lesson.stepCursor,steps.size()-1));
                lesson.testAssisted=store.isStepMastered(entry.id,lesson.studyStage.name(),current.start,current.end);
            }
        }
        // If an old session points beyond the newly protected lesson boundary, restore safely.
        if(lesson.studyStage==StudyPlanLogic.Stage.VERSE)
            lesson.stepCursor=Math.min(lesson.stepCursor,store.unlockedPosition(id,entry.lines().size()));
        if(executingCollectionQueue && collectionQueueNewStep){
            // Revisit every record even if it was previously mastered. Do not erase progress.
            lesson.studyStage=StudyPlanLogic.Stage.VERSE;
            lesson.stepCursor=0;lesson.groupMode=false;lesson.forcedStep=null;
            lesson.checked=false;lesson.passed=false;lesson.lessonWriting.draft="";
            lesson.lessonWriting.hadFeedback=false;lesson.lessonPracticeAgain=false;
            resetTrainingStep(entry);collectionQueueNewStep=false;
        }
        rememberRecent(id);saveTrainingSession();
        screen=Screen.TRAIN;render();
    }
    private AdaptiveLearningLogic.Recommendation selectRecommendation(LocalStore.Entry e) {
        List<String> refs=new ArrayList<>();
        for(Bible.Verse v:e.lines())refs.add(v.ref);
        AdaptiveLearningLogic.Recommendation advice=AdaptiveLearningLogic.recommend(
            refs,isSermon(e),store.collectionProgress().recommendationProgress(e.id),System.currentTimeMillis());
        lesson.studyStage=advice.stage;lesson.stepCursor=advice.index;
        return advice;
    }
    private boolean isSermon(LocalStore.Entry e) {
        return e.reference!=null && (e.reference.startsWith("Матфея 5:") ||
          e.reference.startsWith("Матфея 6:") || e.reference.startsWith("Матфея 7:"));
    }
    private List<StudyPlanLogic.Step> studySteps(LocalStore.Entry e) {
        if(reviewSession.active())return Collections.singletonList(reviewSession.current().step());
        if(lesson.groupMode&&lesson.forcedStep!=null)return Collections.singletonList(lesson.forcedStep);
        return studySteps(e,lesson.studyStage);
    }
    private List<StudyPlanLogic.Step> studySteps(LocalStore.Entry e,StudyPlanLogic.Stage stage) {
        List<String> refs=new ArrayList<>();
        for(Bible.Verse v:e.lines())refs.add(v.ref);
        return StudyPlanLogic.build(refs,stage,isSermon(e));
    }
    private void resetTrainingStep(LocalStore.Entry e) {
        lesson.resetExercise();
        boolean mastered=false;
        if(e!=null) {
            List<StudyPlanLogic.Step> steps=studySteps(e);
            if(!steps.isEmpty()) {
                lesson.stepCursor=Math.max(0,Math.min(lesson.stepCursor,steps.size()-1));
                StudyPlanLogic.Step s=steps.get(lesson.stepCursor);
                mastered=store.isStepMastered(e.id,lesson.studyStage.name(),s.start,s.end);
            }
        }
        int done=e!=null && lesson.studyStage==StudyPlanLogic.Stage.VERSE?
            store.exerciseDone(e.id,lesson.stepCursor):0;
        lesson.trainingMode=done>=4?TrainingMode.TYPE:
            done==3?TrainingMode.TYPE:done==2?TrainingMode.ORDER:
            done==1?TrainingMode.HIDE:TrainingMode.READ;
        if(done==1)lesson.mask=25;
        // A final examination becomes assisted only if a hint is viewed DURING that exam.
        if(reviewSession.active() || (lesson.studyStage==StudyPlanLogic.Stage.VERSE&&done>=4))lesson.testAssisted=false;
    }
    private String texts(List<Bible.Verse> lines) {
        StringBuilder sb=new StringBuilder();
        for(Bible.Verse v:lines) {if(sb.length()>0)sb.append(' ');sb.append(v.text);}
        return sb.toString();
    }
    private int bookForEntry(LocalStore.Entry e){
        if(e==null||e.reference==null)return -1;
        for(int n=1;n<=66;n++)if(e.reference.startsWith(Bible.NAMES[n-1]+" "))return n;
        return -1;
    }
    private GroupLearningLogic.Candidate nextGroup(LocalStore.Entry e){
        return nextGroup(e,store.collectionProgress());
    }
    private GroupLearningLogic.Candidate nextGroup(LocalStore.Entry e,CollectionProgressSnapshot snapshot){
        List<String> refs=new ArrayList<>();for(Bible.Verse line:snapshot.verses(e.id))refs.add(line.ref);
        return GroupLearningLogic.next(refs,snapshot.groupProgress(e.id),isSermon(e));
    }
    private GroupLearningLogic.Candidate pendingAfterCurrent(LocalStore.Entry e,int current){
        if(current<0||current>=e.lines().size())return null;
        List<String> refs=new ArrayList<>();for(Bible.Verse v:e.lines())refs.add(v.ref);
        return GroupLearningLogic.afterVerse(refs,store.collectionProgress().groupProgress(e.id),
            current,isSermon(e));
    }
    private void openGroup(GroupLearningLogic.Candidate group){
        prepareGroup(group);
        saveTrainingSession();
        render();
    }
    private void prepareGroup(GroupLearningLogic.Candidate group){
        if(!lesson.groupMode) lesson.returnVerseCursor=lesson.studyStage==StudyPlanLogic.Stage.VERSE?lesson.stepCursor:
            store.firstUnmastered(lesson.selectedEntry,store.entry(lesson.selectedEntry).lines().size());
        reviewSession.clear();lesson.groupMode=true;
        lesson.studyStage=StudyPlanLogic.Stage.valueOf(group.stage);
        lesson.forcedStep=new StudyPlanLogic.Step(group.start,group.end,"Связка "+(group.start+1)+"–"+group.end);
        lesson.stepCursor=0;lesson.mask=0;lesson.lessonWriting.draft="";lesson.checked=false;lesson.passed=false;
        lesson.lessonVariant=0;lesson.lessonChunk=0;lesson.savedAssemblyFingerprint="";
        lesson.testAssisted=false;lesson.lessonWriting.hadFeedback=false;lesson.lessonPracticeAgain=false;
        lesson.lessonWriting.variant=0;lesson.groupBuildDone=false;lesson.groupIntroDone=false;lesson.lessonWriting.practiceWeakFragment=false;lesson.lessonWriting.weakFragmentText="";resetLessonTiles();
        lesson.activeStep=lesson.forcedStep;
        lesson.answerReview=null;
        lesson.trainingMode=TrainingMode.ORDER;screen=Screen.TRAIN;
    }
    private void renderUnlearnableLine(LocalStore.Entry entry,int invalidLine){
        currentScroller=null;
        root=lessonScreen.invalidLine(invalidLine,()->{
            onBackPressed();
            editCustomStudyEntry(entry);
        },this::onBackPressed);
        setContentView(root);
    }

    private void renderFullscreenLesson() {
        LocalStore.Entry e=store.entry(lesson.selectedEntry);
        if(e==null){screen=Screen.HOME;render();return;}
        List<StudyPlanLogic.Step> steps=studySteps(e);
        if(steps.isEmpty()){
            LocalStore.UnifiedCollection u=store.unifiedForGroup(e.groupId);
            screen=u==null?Screen.HOME:Screen.COLLECTION_DETAIL;
            if(u!=null)openUnifiedId=u.id;render();return;
        }
        lesson.stepCursor=Math.max(0,Math.min(lesson.stepCursor,steps.size()-1));
        lesson.activeEntry=e;lesson.activeStep=steps.get(lesson.stepCursor);
        LessonCurriculumLogic.Phase phase=lesson.checked&&lesson.passed?
            LessonCurriculumLogic.Phase.COMPLETE:lessonPhase(e);
        if(phase!=LessonCurriculumLogic.Phase.WRITE)lesson.answerReview=null;
        if(phase==LessonCurriculumLogic.Phase.COMPLETE){renderCompletion(e);return;}
        int invalidLine=CustomStudyText.firstUnlearnable(e.lines(),lesson.activeStep.start,lesson.activeStep.end);
        if(invalidLine>=0){renderUnlearnableLine(e,invalidLine);return;}
        boolean assembling=phase==LessonCurriculumLogic.Phase.BUILD;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LessonScreenView.Parts parts=lessonScreen.create(assembling?assemblyView.background():ui.pageBackground(),
            assembling,DeveloperModeController.isEnabled(this),this::onBackPressed,()->openLessonDeveloper(e));
        root=parts.root();content=parts.content();currentScroller=parts.scroll();
        TextView mainAction;
        if(phase==LessonCurriculumLogic.Phase.INTRO){
            lessonPhaseHeader("Ознакомление",e);
            renderLessonIntro();
            mainAction=ui.button("Продолжить",true,()->{
                if(root!=parts.root()||screen!=Screen.TRAIN)return;
                if(lesson.groupMode)lesson.groupIntroDone=true;
                else store.finishExercise(e.id,lesson.activeStep.start,1);
                lesson.lessonVariant=0;lesson.lessonChunk=0;lesson.lessonWriting.variant=0;resetLessonTiles();lesson.mask=0;
                saveTrainingSession();render();
            });
        } else if(phase==LessonCurriculumLogic.Phase.BUILD){
            lesson.trainingMode=TrainingMode.ORDER;
            assemblyView.reference(content,lessonFullReference(e));
            boolean complete=renderLessonBuild(e);
            mainAction=ui.button("Продолжить",true,()->{
                if(root==parts.root()&&screen==Screen.TRAIN&&lesson.lessonAssembly.picked()>=assemblyView.required())
                    advanceLessonBuild(e);
            });
            assemblyView.installAction(mainAction,complete);
        } else if(phase==LessonCurriculumLogic.Phase.WRITE){
            lesson.trainingMode=TrainingMode.TYPE;
            if(lesson.answerReview!=null){
                answerReviewView.render(content,lesson.answerReview.expected,lesson.answerReview.result);
                mainAction=ui.button("Написать ещё раз",true,()->{if(root==parts.root())retryLessonWriting();});
            }else{
                lessonPhaseHeader("Написание",e);
                renderLessonWriting();
                mainAction=ui.button("Проверить",true,()->{if(root==parts.root())validateLessonWriting(e);});
            }
        } else {
            renderCompletion(e);
            return;
        }
        lessonProgress.render(parts.top(),lessonHotbarModel(phase,e));
        lessonScreen.footer(root,mainAction,assembling||lesson.answerReview!=null);
        setContentView(root);
    }

    private void openLessonDeveloper(LocalStore.Entry entry){
        int book=bookForEntry(entry);
        DeveloperModeController.open(this,entry,
            chapter->book>0&&bible.isReady()?bible.chapter(book,chapter).size():0,
            store.masteryCount(entry.id));
    }

    private String lessonFullReference(LocalStore.Entry e){
        if(e.lines().isEmpty())return e.reference;
        int book=bookForEntry(e);
        if(book<1)return e.reference==null?e.title:e.reference;
        String a=e.lines().get(lesson.activeStep.start).ref;
        String b=e.lines().get(lesson.activeStep.end-1).ref;
        try{
            String[] first=a.split(":",2),last=b.split(":",2);
            return Bible.reference(book,Integer.parseInt(first[0]),Integer.parseInt(first[1]),
                Integer.parseInt(last[0]),Integer.parseInt(last[1]));
        }catch(Exception ex){return e.reference;}
    }
    private void renderCompletion(LocalStore.Entry e){
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        ReviewSession.Exercise review=reviewSession.current();
        LessonCompletionView.Page page=lessonCompletion.create(artwork.landscapeFor(e.id),lessonFullReference(e),lesson.groupMode,
            completionRoot->{
                if(root!=completionRoot||screen!=Screen.TRAIN)return;
                if(review!=null&&(reviewSession.current()!=review||!lesson.checked||!lesson.passed))return;
                advanceAfterSuccess(e);
            });
        root=page.root();
        setContentView(page.scenery());
        page.animate().run();
    }
    private void advanceAfterSuccess(LocalStore.Entry e){
        if(reviewSession.active()){
            applyLessonDestination(e,LessonNavigationFlow.afterSuccess(true,false,null,0,0,false,false));
            return;
        }
        boolean returnedFromGroup=lesson.groupMode;
        GroupLearningLogic.Candidate pending;
        int nextVerse;
        if(returnedFromGroup){
            getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit()
                .remove("linked_"+lesson.selectedEntry+"_pending").apply();
            lesson.groupMode=false;lesson.forcedStep=null;lesson.studyStage=StudyPlanLogic.Stage.VERSE;
            lesson.stepCursor=lesson.returnVerseCursor;resetTrainingStep(e);saveTrainingSession();
            pending=pendingAfterCurrent(e,lesson.stepCursor);
            nextVerse=lesson.stepCursor+1;
        }else{
            pending=pendingAfterCurrent(e,lesson.activeStep.start);
            nextVerse=lesson.activeStep.end;
        }
        applyLessonDestination(e,LessonNavigationFlow.afterSuccess(false,returnedFromGroup,
            pending,nextVerse,e.lines().size(),executingCollectionQueue&&collectionQueueCursor>=0,
            cardStudyCursor>=0));
    }

    private void applyLessonDestination(LocalStore.Entry e,LessonNavigationFlow.Decision decision){
        switch(decision.destination()){
            case REVIEW:advanceReviewSession();return;
            case GROUP:openGroup(decision.group());return;
            case VERSE:
                lesson.stepCursor=decision.verse();lesson.studyStage=StudyPlanLogic.Stage.VERSE;
                resetTrainingStep(e);saveTrainingSession();render();return;
            case COLLECTION_AFTER_GROUPS:advanceStudyCollectionAfterGroups(e);return;
            case CARD:advanceCardStudy();return;
            case GROUPS_OR_EXIT:advanceStudyCollectionOrExit(e);return;
            case EXIT:onBackPressed();return;
        }
    }

    private void advanceStudyCollectionOrExit(LocalStore.Entry e){
        GroupLearningLogic.Candidate unfinished=nextGroup(e);
        if(unfinished!=null){openGroup(unfinished);return;}
        onBackPressed();
    }
    /** Finish all internal bridges and complete-passage checks before advancing to the next independent entry. */
    private void advanceStudyCollectionAfterGroups(LocalStore.Entry e){
        GroupLearningLogic.Candidate unfinished=nextGroup(e);
        if(unfinished!=null){openGroup(unfinished);return;}
        advanceStudyCollection();
    }
    /** The hotbar is an observer only. It never advances or changes lesson exercises. */
    private LessonHotbarLogic.Model lessonHotbarModel(LessonCurriculumLogic.Phase phase,
                                                      LocalStore.Entry e){
        List<Bible.Verse> passage=e.lines().subList(lesson.activeStep.start,lesson.activeStep.end);
        List<String> source=new ArrayList<>();
        for(Bible.Verse line:passage)source.add(line.text);
        List<LessonCurriculumLogic.Variant> variants=LessonCurriculumLogic.variants(
            ReviewLogic.words(texts(passage)).size());
        int buildTotal=0,buildDone=0;
        boolean assembled=phase==LessonCurriculumLogic.Phase.BUILD&&
            assemblyView.required()>0&&lesson.lessonAssembly.picked()==assemblyView.required();
        for(int i=0;i<variants.size();i++){
            int count=Math.max(1,lessonChunks(source,variants.get(i)).size());
            buildTotal+=count;
            if(i<lesson.lessonVariant)buildDone+=count;
            else if(i==lesson.lessonVariant)
                buildDone+=Math.min(lesson.lessonChunk,count-1)+(assembled?1:0);
        }
        int writingTotal=Math.max(1,writingVariants(e).size());
        // In a scheduled review the lesson legitimately starts at writing.
        LessonStageFlow.State state=lessonStageState(e);
        return LessonHotbarLogic.calculate(phase.ordinal(),1,buildTotal,writingTotal,
            state.introDone(),buildDone,lesson.lessonWriting.variant,state.writingOnly());
    }
    private void refreshLessonPhaseProgress(LessonCurriculumLogic.Phase phase,LocalStore.Entry e){
        if(lessonProgress.visible())lessonProgress.update(lessonHotbarModel(phase,e));
    }
    private LessonStageFlow.State lessonStageState(LocalStore.Entry e){
        return new LessonStageFlow.State(lesson.studyStage,lesson.groupMode?0:store.exerciseDone(e.id,lesson.activeStep.start),
            reviewSession.active(),lesson.groupMode,executingCollectionQueue,lesson.lessonPracticeAgain,lesson.groupIntroDone,lesson.groupBuildDone);
    }
    private LessonCurriculumLogic.Phase lessonPhase(LocalStore.Entry e){
        return lessonStageState(e).phase();
    }
    private void lessonPhaseHeader(String stage,LocalStore.Entry e){
        int book=bookForEntry(e);
        lessonScreen.phaseHeader(content,stage,lessonFullReference(e),book<1?GREEN:ui.bookInk(book));
    }

    private void renderLessonIntro(){
        lesson.trainingMode=lesson.mask>0?TrainingMode.HIDE:TrainingMode.READ;
        readingView.render(content,lesson.activeEntry.lines().subList(lesson.activeStep.start,lesson.activeStep.end),lesson.mask,lesson.blurSeed,()->lesson.mask,
            next->{lesson.mask=next;lesson.trainingMode=next>0?TrainingMode.HIDE:TrainingMode.READ;},
            ()->refreshLessonPhaseProgress(LessonCurriculumLogic.Phase.INTRO,lesson.activeEntry),this::saveTrainingSession);
    }
    private void resetLessonTiles(){
        lesson.lessonAssembly.reset();lesson.lessonSeed=new Random().nextLong();
    }
    private List<String[]> lessonChunks(List<String> lines,LessonCurriculumLogic.Variant kind){
        return assemblyLayout.chunks(lesson.selectedEntry,lesson.studyStage,lesson.activeStep,lines,kind);
    }
    private int[] lessonMissingPositions(String[] tokens,LessonCurriculumLogic.Variant kind){
        return lesson.missingPositions(getSharedPreferences("gap_rotation",MODE_PRIVATE),tokens,kind,this::saveTrainingSession);
    }
    private boolean renderLessonBuild(LocalStore.Entry e){
        List<String> src=new ArrayList<>();
        for(Bible.Verse v:e.lines().subList(lesson.activeStep.start,lesson.activeStep.end))src.add(v.text);
        List<LessonCurriculumLogic.Variant> variants=LessonCurriculumLogic.variants(
            ReviewLogic.words(texts(e.lines().subList(lesson.activeStep.start,lesson.activeStep.end))).size());
        lesson.lessonVariant=Math.min(lesson.lessonVariant,variants.size()-1);
        LessonCurriculumLogic.Variant kind=variants.get(lesson.lessonVariant);
        List<String[]> chunks=lessonChunks(src,kind);
        if(chunks.isEmpty()){assemblyView.clear();return true;}
        String fingerprint=SessionIntegrityLogic.assemblyFingerprint(kind,chunks);
        if(!fingerprint.equals(lesson.savedAssemblyFingerprint)||
            lesson.lessonChunk<0||lesson.lessonChunk>=chunks.size()){
            // A different screen/font/layout or older saved format must never
            // skip words or transfer selected indices to another fragment.
            lesson.lessonChunk=0;resetLessonTiles();
            lesson.rotationExerciseKey="";lesson.rotationMask="";
        }
        lesson.savedAssemblyFingerprint=fingerprint;
        String[] tokens=chunks.get(lesson.lessonChunk);
        // The assembly tiles and answer slots show the task directly;
        // do not add an instruction card for any assembly variant.
        int[] missing=InlineGapLogic.wordPositions(tokens,lessonMissingPositions(tokens,kind));
        assemblyView.render(content,tokens,missing,lesson.lessonSeed,
            ()->refreshLessonPhaseProgress(LessonCurriculumLogic.Phase.BUILD,e),this::saveTrainingSession);
        return lesson.lessonAssembly.picked()==missing.length;
    }
    private void advanceLessonBuild(LocalStore.Entry e){
        List<String> source=new ArrayList<>();
        for(Bible.Verse v:e.lines().subList(lesson.activeStep.start,lesson.activeStep.end))source.add(v.text);
        List<LessonCurriculumLogic.Variant> kinds=LessonCurriculumLogic.variants(
            ReviewLogic.words(texts(e.lines().subList(lesson.activeStep.start,lesson.activeStep.end))).size());
        int chunkCount=lessonChunks(source,kinds.get(Math.min(lesson.lessonVariant,kinds.size()-1))).size();
        LessonStageFlow.BuildTransition next=LessonStageFlow.advanceBuild(
            lesson.lessonVariant,lesson.lessonChunk,kinds.size(),chunkCount,lesson.groupMode);
        if(next.persistedCompletion()>0)
            store.finishExercise(e.id,lesson.activeStep.start,next.persistedCompletion());
        if(next.groupCompleted())lesson.groupBuildDone=true;
        lesson.lessonVariant=next.nextVariant();lesson.lessonChunk=next.nextChunk();
        if(next.beginWriting())lesson.lessonWriting.variant=0;
        resetLessonTiles();
        if(next.beginWriting()){lesson.lessonWriting.draft="";lesson.lastAccuracy=-1;lesson.lessonWriting.hadFeedback=false;}
        saveTrainingSession();render();
    }
    private List<LessonCurriculumLogic.Variant> writingVariants(LocalStore.Entry e){
        return writingVariants(texts(e.lines().subList(lesson.activeStep.start,lesson.activeStep.end)));
    }
    private List<LessonCurriculumLogic.Variant> writingVariants(String passage){
        return LessonStageFlow.writingVariants(passage,reviewSession.active(),lesson.lessonPracticeAgain,lesson.lessonWriting.practiceWeakFragment);
    }
    private String writingExpected(String original,LessonCurriculumLogic.Variant variant){
        if(lesson.lessonWriting.practiceWeakFragment)return lesson.lessonWriting.weakFragmentText;
        if(variant==LessonCurriculumLogic.Variant.FULL)return original;
        String[] tokens=ExerciseTextLogic.tokens(original);
        int[] gaps=InlineGapLogic.wordPositions(tokens,lessonMissingPositions(tokens,variant));
        StringBuilder response=new StringBuilder();
        for(int position:gaps){if(response.length()>0)response.append(' ');response.append(tokens[position]);}
        return response.toString();
    }
    private void renderLessonWriting(){
        List<Bible.Verse> lines=lesson.activeEntry.lines().subList(lesson.activeStep.start,lesson.activeStep.end);
        String source=texts(lines);
        List<LessonCurriculumLogic.Variant> variants=writingVariants(source);
        lesson.lessonWriting.variant=Math.min(lesson.lessonWriting.variant,variants.size()-1);
        LessonCurriculumLogic.Variant variant=variants.get(lesson.lessonWriting.variant);
        List<String> refs=new ArrayList<>();
        for(Bible.Verse verse:lines)refs.add(verse.ref);
        writingView.render(content,source,variant,AcceptedTextLogic.matthew513(lesson.activeEntry.reference,refs),
            tokens->lessonMissingPositions(tokens,variant),this::cancelWritingCheck,()->{
                lesson.lastAccuracy=-1;
                saveTrainingSession();
            });
    }

    private void openAnswerReview(AnswerReview review){
        lesson.answerReview=review;
        saveTrainingSession();
        View focused=getCurrentFocus();
        if(focused!=null){
            android.view.inputmethod.InputMethodManager keyboard=getSystemService(android.view.inputmethod.InputMethodManager.class);
            if(keyboard!=null)keyboard.hideSoftInputFromWindow(focused.getWindowToken(),0);
            focused.clearFocus();
        }
        render();
    }

    private void retryLessonWriting(){
        if(lesson.answerReview==null)return;
        lesson.answerReview=null;
        lesson.lessonWriting.retry();
        lesson.lastAccuracy=-1;
        saveTrainingSession();
        render();
    }

    private record WritingCheck(WritingAssessment.Input input,LocalStore.Entry entry,
                                StudyPlanLogic.Step step,String draft) {}
    private final LessonWritingWorker<WritingCheck> writingWorker=new LessonWritingWorker<>(
        this::applyWritingAssessment,error->{
            android.util.Log.e("Learning","Unable to check written answer",error);
            toast("Не удалось проверить ответ. Попробуй ещё раз.");
        });

    private void cancelWritingCheck(){writingWorker.cancel();}

    private void validateLessonWriting(LocalStore.Entry e){
        if(writingWorker.busy()||screen!=Screen.TRAIN||backupInProgress||resetInProgress||
            lesson.answerReview!=null||(lesson.checked&&lesson.passed))return;
        List<Bible.Verse> lines=e.lines().subList(lesson.activeStep.start,lesson.activeStep.end);
        String passage=texts(lines);
        List<LessonCurriculumLogic.Variant> variants=writingVariants(passage);
        lesson.lessonWriting.variant=Math.min(lesson.lessonWriting.variant,variants.size()-1);
        LessonCurriculumLogic.Variant kind=variants.get(lesson.lessonWriting.variant);
        boolean full=kind==LessonCurriculumLogic.Variant.FULL;
        String expected=writingExpected(passage,kind);
        String entered=full?lesson.lessonWriting.draft:lesson.lessonWriting.gapResponse();
        List<String> refs=new ArrayList<>(), verses=new ArrayList<>();
        for(Bible.Verse line:lines){refs.add(line.ref);verses.add(line.text);}
        WritingAssessment.Input input=new WritingAssessment.Input(expected,entered,
            AcceptedTextLogic.matthew513(e.reference,refs),full,lesson.lessonWriting.practiceWeakFragment,verses,
            full?Collections.emptyList():writingView.expectedGaps(), full?Collections.emptyList():lesson.lessonWriting.gapAnswers());
        WritingCheck check=new WritingCheck(input,e,lesson.activeStep,lesson.lessonWriting.draft);
        writingWorker.submit(check,input);
    }

    private void applyWritingAssessment(WritingCheck check,WritingAssessment.Result result){
        LocalStore.Entry e=check.entry();
        if(isDestroyed()||isFinishing()||backupInProgress||resetInProgress||screen!=Screen.TRAIN||
            lesson.selectedEntry!=e.id||lesson.activeStep!=check.step()||!lesson.lessonWriting.draft.equals(check.draft()))return;
        WritingAssessment.Input input=check.input();
        AnswerReview feedback=result.exact()?null:new AnswerReview(input.expected(),input.actual(),result.feedback());
        LessonWritingFlow.Context context=new LessonWritingFlow.Context(
            e.id,lesson.studyStage,lesson.activeStep.start,lesson.activeStep.end,reviewSession.active(),lesson.groupMode,
            reviewSession.active()&&"VERSE_RUN".equals(reviewSession.current().item().stage),
            lesson.lessonWriting.practiceWeakFragment,lesson.lessonWriting.hadFeedback);
        LessonWritingFlow.Transition transition=LessonWritingFlow.decide(context,input,result);
        try{
            if(reviewSession.active()&&!reviewSession.matchesMaterial(store.entry(e.id))){
                toast("Материал изменился. Открой повторение заново.");
                return;
            }
            transition.persist(store,System.currentTimeMillis());
        }catch(RuntimeException error){
            showRecallSaveError(error);
            return;
        }
        switch(transition.outcome()){
            case FEEDBACK:
                openAnswerReview(feedback);
                return;
            case FRAGMENT_PRACTICED:
                int fullVariant=LessonCurriculumLogic.variants(
                    ReviewLogic.words(texts(e.lines().subList(lesson.activeStep.start,lesson.activeStep.end))).size()).size()-1;
                lesson.lessonWriting.finishFragment(reviewSession.active()?0:fullVariant);
                saveTrainingSession();render();
                toast("Фрагмент отработан. Напиши весь стих заново без подсказок.");
                return;
            case NEXT_VARIANT:
                lesson.lessonWriting.nextVariant();lesson.lastAccuracy=-1;
                lesson.lessonSeed=new Random().nextLong();saveTrainingSession();render();
                return;
            case FAILED_RECALL:
                lesson.weakVerse=transition.weakVerse();
                lesson.weakTransitionFrom=transition.weakTransitionFrom();
                lesson.lessonWriting.hadFeedback=true;
                lesson.lastAccuracy=result.accuracy();
                openAnswerReview(feedback);
                return;
            case ASSISTED_CORRECTION:
                lesson.lessonWriting.hadFeedback=false;lesson.lessonWriting.draft="";lesson.lastAccuracy=-1;saveTrainingSession();
                render();
                toast("Исправлено. Теперь напиши стих заново без подсказок для зачёта.");
                return;
            case SUCCESSFUL_RECALL:
                lesson.lessonPracticeAgain=false;lesson.lessonWriting.draft="";lesson.lastAccuracy=100;lesson.passed=true;lesson.checked=true;
                if(reviewSession.active())ReviewSessionPreferences.clearPending(getSharedPreferences(SESSION_PREFS,MODE_PRIVATE));
                if(!reviewSession.active()&&!lesson.groupMode)saveTrainingSession();
                render();
                return;
        }
    }

    private boolean bibleReadyForRecall(){
        if(bible.isReady())return true;
        toast(bibleError.isEmpty()?"Библия ещё загружается":"Ошибка встроенного текста: "+bibleError);
        return false;
    }

    private void showRecallSaveError(RuntimeException error){
        if(error instanceof Bible.Unavailable){bibleReadyForRecall();return;}
        android.util.Log.e("Learning","Unable to save recall",error);
        toast("Не удалось сохранить ответ. Попробуй ещё раз.");
    }

    // ===== Unified Collections (v0.21) =====
    private void ensureActiveUnified(){activeCollection.ensure(store);}
    private LocalStore.UnifiedCollection activeUnified(){return activeCollection.get(store);}
    private void activateUnified(long id){activeCollection.select(store,id);}
    private interface UnifiedCreated {void accept(LocalStore.UnifiedCollection collection);}
    private void newUnifiedCollection(UnifiedCreated after){
        collectionDialogs.create(name->{
            boolean firstCollection=store.unifiedCollections().isEmpty();
            long id=store.createUnifiedCollection(name);
            if(firstCollection)activateUnified(id);
            LocalStore.UnifiedCollection collection=store.unifiedCollection(id);
            return ()->{
                if(after!=null)after.accept(collection);
                else{openUnifiedId=id;screen=Screen.COLLECTION_DETAIL;render();}
            };
        });
    }
    private void showUnifiedSelector(){
        collectionDialogs.select(store.unifiedCollections(),activeCollection.id(),id->{activateUnified(id);render();});
    }
    private void openCollectionToAdd(LocalStore.UnifiedCollection u){
        openUnifiedId=u.id;selectedGroup=u.groupId;selectedDeck=u.deckId;
        screen=Screen.COLLECTION_DETAIL;render();
    }
    private void startActiveStudy(){
        LocalStore.UnifiedCollection u=activeUnified();if(u==null){newUnifiedCollection(null);return;}
        if(bible.isReady())store.syncUnifiedCollection(u.id,bible);
        if(store.entries(u.groupId).isEmpty()){openCollectionToAdd(u);return;}
        selectedGroup=u.groupId;startStudyCollection(u.groupId);
    }
    private void startActiveCards(){
        LocalStore.UnifiedCollection u=activeUnified();if(u==null)return;
        if(bible.isReady())store.syncUnifiedCollection(u.id,bible);
        selectedDeck=u.deckId;cardFromUnified=true;
        if(eligibleSessionCards().isEmpty()){
            if(!bible.isReady()&&!store.entries(u.groupId).isEmpty()){
                toast("Библия ещё загружается");return;
            }
            openCollectionToAdd(u);return;
        }
        startCardSession();
    }
    private void startActiveReview(boolean all){
        LocalStore.UnifiedCollection u=activeUnified();
        if(u==null&&!all){toast("Выбери коллекцию");return;}
        startReviewSession(all?-1:u.groupId);
    }
    private void renderUnifiedCollections(){
        List<LocalStore.UnifiedCollection> collections=store.unifiedCollections();
        CollectionProgressSnapshot snapshot=collections.isEmpty()?null:store.collectionProgress();
        collectionViews.list(content,collections,snapshot,activeCollection.id(),id->{
            openUnifiedId=id;screen=Screen.COLLECTION_DETAIL;render();
        });
    }
    private void renderSettings(){
        settingsView.render(content,this::exportFile,this::importFile,this::confirmResetProgress);
    }

    private void confirmResetProgress(){
        if(!resetInProgress)settingsView.confirmReset(this::resetAllLearningProgress);
    }
    private void resetAllLearningProgress(){
        if(resetInProgress||backupInProgress)return;
        resetInProgress=true;
        AlertDialog resetting=new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Сброс прогресса").setMessage("Удаляем историю изучения…")
            .setCancelable(false).create();
        ui.styledDialog(resetting);
        collectionExecutor.execute(()->{
            // Independent helper survives destruction of this Activity.
            try(LocalStore worker=new LocalStore(getApplicationContext())){
                LearningReset.request(getApplicationContext(),worker);
                runOnUiThread(()->{
                    if(isDestroyed())return;
                    resetting.dismiss();
                    collectionQueue.clear();collectionQueueCursor=-1;collectionQueueGroup=-1;
                    executingCollectionQueue=false;collectionQueueNewStep=false;
                    cardStudyQueue.clear();cardStudyCursor=-1;cardStudyDeckId=-1;
                    reviewSession.clear();
                    knowledgeCheck.clear();
                    lesson.savedAssemblyFingerprint="";assemblyLayout.clear();
                    lesson.lessonWriting.draft="";lesson.checked=false;lesson.passed=false;lesson.answerReview=null;
                    lesson.lessonWriting.hadFeedback=false;lesson.lessonPracticeAgain=false;
                    tabCache.invalidate();navigationScroll.clear();
                    resetInProgress=false;
                    screen=Screen.SETTINGS;render();
                    toast("Прогресс изучения сброшен");
                });
            }catch(Exception error){
                android.util.Log.e("Settings","Unable to reset learning progress",error);
                runOnUiThread(()->{
                    if(isDestroyed())return;
                    resetting.dismiss();
                    // An interrupted operation is still pending. Do not allow a
                    // new lesson to be saved until it has been recovered.
                    new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                        .setTitle("Не удалось завершить сброс")
                        .setMessage("Закрой приложение и открой снова для завершения операции.")
                        .setCancelable(false).setPositiveButton("Закрыть",(d,w)->finish()).show();
                });
            }
        });
    }
    static String countedRussian(int count,String one,String few,String many){
        return CollectionViews.countedRussian(count,one,few,many);
    }
    private void showUnifiedCollectionSettings(LocalStore.UnifiedCollection u){
        collectionViews.settings(content,()->renameUnified(u),()->{
            if(store.entries(u.groupId).size()<2){
                toast("Для изменения порядка нужны хотя бы два отрывка");return;
            }
            selectedGroup=u.groupId;openUnifiedId=u.id;
            screen=Screen.REORDER_STUDY;render();
        },()->clearUnified(u),()->deleteUnified(u));
    }
    private void clearUnified(LocalStore.UnifiedCollection u){
        if(u.entries==0&&u.cards==0){toast("Коллекция уже пуста");return;}
        collectionDialogs.confirmClear(()->{
            store.clearUnifiedCollection(u.id);
            knowledgeCheck.clearCollection(u.id,true);
            if(collectionQueueGroup==u.groupId)clearCollectionQueue();
            render();toast("Коллекция очищена");
        });
    }
    private void renameUnified(LocalStore.UnifiedCollection u){
        collectionDialogs.rename(u.title,name->store.renameUnifiedCollection(u.id,name),this::render);
    }
    private void deleteUnified(LocalStore.UnifiedCollection u){
        collectionDialogs.confirmDelete(()->{
            Screen origin=screen;
            runCollectionMutation(()->collectionStore.deleteUnifiedCollection(u.id),success->{
                if(!success)return;
                if(collectionQueueGroup==u.groupId)clearCollectionQueue();
                knowledgeCheck.clearCollection(u.id,false);
                ensureActiveUnified();
                if(screen==origin&&(origin!=Screen.COLLECTION_DETAIL||openUnifiedId==u.id)){
                    screen=Screen.COLLECTIONS;render();
                }
            },()->{
                // Rotation can retire the owner before its deletion callback reaches the looper.
                // Compare persisted IDs so we preserve a newer selection made by another screen.
                SharedPreferences queue=getSharedPreferences(COLLECTION_PREFS,MODE_PRIVATE);
                if(queue.getLong("group",-1)==u.groupId)queue.edit().clear().apply();
                activeCollection.clearSavedIfSelected(u.id);
                knowledgeCheck.clearCollection(u.id,false);
            });
        });
    }
    private void renderUnifiedCollectionDetail(){
        LocalStore.UnifiedCollection u=store.unifiedCollection(openUnifiedId);
        if(u==null){screen=Screen.COLLECTIONS;render();return;}
        selectedGroup=u.groupId;selectedDeck=u.deckId;
        collectionViews.heading(content,u.title);
        showUnifiedCollectionSettings(u);
        final LinearLayout target=content;
        final long generation=collectionGeneration;
        final int restoreScroll=navigationScroll.getOrDefault(Screen.COLLECTION_DETAIL,0);
        collectionDetailLoading=true;
        collectionTask=collectionExecutor.submit(()->{
            try{
                CollectionProgressSnapshot snapshot=collectionStore.collectionProgress();
                SortedMap<Integer,SortedMap<Integer,Long>> books=collectionStore.collectionBooks(u.groupId);
                List<LocalStore.Entry> visible=snapshot.visibleCollectionEntries(u.groupId,books,bible);
                if(Thread.currentThread().isInterrupted())return;
                collectionHandler.post(()->{
                    if(isDestroyed()||isFinishing()||generation!=collectionGeneration||
                        screen!=Screen.COLLECTION_DETAIL||openUnifiedId!=u.id||content!=target)return;
                    populateCollectionDetail(u,snapshot,books,visible,generation,restoreScroll);
                });
            }catch(RuntimeException error){
                android.util.Log.e("CollectionProgress","Unable to read collection progress",error);
                collectionHandler.post(()->{
                    if(!isDestroyed()&&generation==collectionGeneration&&content==target){
                        collectionDetailLoading=false;
                        toast("Не удалось загрузить коллекцию. Открой её ещё раз.");
                    }
                });
            }
        });
    }
    private void populateCollectionDetail(LocalStore.UnifiedCollection u,CollectionProgressSnapshot snapshot,
            SortedMap<Integer,SortedMap<Integer,Long>> books,List<LocalStore.Entry> entries,long generation,int restoreScroll){
        collectionViews.progress(content,u.groupId,snapshot,getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE)
            .getString("run_history_"+u.id,"[]"));
        for(Map.Entry<Integer,SortedMap<Integer,Long>> book:books.entrySet()){
            String name=Bible.NAMES[book.getKey()-1];
            collectionViews.bookRow(content,name,countedRussian(book.getValue().size(),"глава","главы","глав"),()->{
                openedBookName=name;
                openedCollectionBook=book.getValue();
                openedBookProgress=snapshot;
                navigationScroll.remove(Screen.COLLECTION_BOOK);
                screen=Screen.COLLECTION_BOOK;render();
            });
        }
        if(entries.isEmpty()&&books.isEmpty())ui.helper(content,"Добавь первый стих или отрывок.");
        appendCollectionEntries(entries,snapshot,0,generation,restoreScroll);
    }
    private void appendCollectionEntries(List<LocalStore.Entry> entries,CollectionProgressSnapshot snapshot,
                                         int from,long generation,int restoreScroll){
        if(isDestroyed()||generation!=collectionGeneration||screen!=Screen.COLLECTION_DETAIL)return;
        int end=Math.min(entries.size(),from+12);
        for(int i=from;i<end;i++)entryCard(entries.get(i),snapshot);
        if(end<entries.size()){
            content.postOnAnimation(()->appendCollectionEntries(entries,snapshot,end,generation,restoreScroll));
        }else{
            collectionDetailLoading=false;
            final ScrollView scroller=currentScroller;
            if(scroller!=null)scroller.post(()->{
                if(generation==collectionGeneration&&currentScroller==scroller)scroller.scrollTo(0,restoreScroll);
            });
        }
    }

    private void renderCollectionBook(){
        title(openedBookName,null);
        for(Map.Entry<Integer,Long> chapter:openedCollectionBook.entrySet()){
            long id=chapter.getValue();
            collectionViews.bookRow(content,"Глава "+chapter.getKey(),countedRussian(openedBookProgress.verseCount(id),
                "стих","стиха","стихов"),()->{
                openedCollectionChapter=chapter.getKey();
                navigationScroll.remove(Screen.COLLECTION_CHAPTER);
                screen=Screen.COLLECTION_CHAPTER;render();
            });
        }
    }

    private void renderCollectionChapter(){
        long id=openedCollectionBook.get(openedCollectionChapter);
        title(openedBookName+" "+openedCollectionChapter,null);
        ui.action(content,"Изучать главу",true,()->openTraining(id));
        ui.action(content,"Добавить стих / отрывок после главы",false,()->{
            clearInsertionContext();
            insertAfterEntryId=id;
            studyInsertionTrail.add(id);
            openBiblePicker(BibleTarget.STUDY_GROUP,selectedGroup,Screen.COLLECTION_DETAIL);
        });
        collectionViews.chapterVerses(content,openedBookProgress,id);
    }

    private void addUnifiedCollectionFooter(){
        if(screen==Screen.COLLECTIONS||screen==Screen.CARDS){
            collectionViews.listFooter(root,()->newUnifiedCollection(null));
        }else if(screen==Screen.COLLECTION_DETAIL){
            LocalStore.UnifiedCollection u=store.unifiedCollection(openUnifiedId);
            if(u==null)return;
            collectionViews.detailFooter(root,()->{
                clearInsertionContext();
                openBiblePicker(BibleTarget.STUDY_GROUP,u.groupId,Screen.COLLECTION_DETAIL);
            },()->{customTargetGroupId=u.groupId;screen=Screen.CUSTOM;render();});
        }
    }

    private void startActiveRun(){
        LocalStore.UnifiedCollection u=activeUnified();if(u==null)return;
        KnowledgeCheckSession.Availability available=knowledgeCheck.prepare(u.id,
            KnowledgeCheckMaterial.questions(store.collectionProgress(),u.groupId));
        if(available==KnowledgeCheckSession.Availability.EMPTY){
            if(store.entries(u.groupId).isEmpty()){openCollectionToAdd(u);return;}
            ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                .setTitle("Пока нет стихов для проверки памяти")
                .setMessage("Сначала изучи стихи и закрепи их отложенным повторением.")
                .setPositiveButton("Изучение стихов",(d,w)->startActiveStudy())
                .setNeutralButton("Повторение",(d,w)->startActiveReview(false))
                .setNegativeButton("Закрыть",null).create());
            return;
        }
        if(available==KnowledgeCheckSession.Availability.RESUME){
            ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                .setTitle("Незавершённая проверка памяти")
                .setMessage("Продолжить с сохранённого стиха или начать проверку заново?")
                .setPositiveButton("Продолжить",(d,w)->openKnowledgeCheck(true))
                .setNegativeButton("Сначала",(d,w)->openKnowledgeCheck(false)).create());
        }else{
            if(available==KnowledgeCheckSession.Availability.CHANGED)
                toast("Состав стихов изменился. Начинаем новую проверку.");
            openKnowledgeCheck(false);
        }
    }

    private void openKnowledgeCheck(boolean resume){
        if(resume)knowledgeCheck.restore();
        else knowledgeCheck.restart();
        screen=Screen.RUN;render();
    }

    private void renderWritingRun(){
        KnowledgeCheckSession.Task task=knowledgeCheck.task();
        if(task==null){screen=Screen.HOME;render();return;}
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        KnowledgeCheckView.Layout layout=knowledgeCheckView.task(this,task,this::onBackPressed,
            knowledgeCheck::edit,()->{
                if(!knowledgeCheck.check()){toast("Введи стих или выбери «Пропустить»");return;}
                render();
            },()->{
                if(screen!=Screen.RUN)return;
                KnowledgeCheckSession.Advance next=knowledgeCheck.advance(System.currentTimeMillis());
                if(next==KnowledgeCheckSession.Advance.SAVE_FAILED)toast("Не удалось сохранить результат проверки");
                if(next!=KnowledgeCheckSession.Advance.CONTINUE)screen=Screen.RUN_SUMMARY;
                render();
            });
        root=layout.root();content=layout.body();
        setContentView(root);lastRenderedScreen=screen;
    }

    private void renderRunSummary(){
        title("Проверка памяти завершена",null);
        knowledgeCheckView.summary(content,knowledgeCheck.result(),()->{screen=Screen.HOME;render();});
    }

    /** Android system picker: the app never requests access to other storage. */
    private void exportFile() {
        if(backupInProgress||resetInProgress)return;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        String date=new SimpleDateFormat("yyyy-MM-dd_HH-mm",Locale.ROOT)
            .format(new Date());
        i.putExtra(Intent.EXTRA_TITLE,"Светоч_"+date+SvetochBackup.EXTENSION);
        startActivityForResult(i,EXPORT);
    }
    private void importFile() {
        if(backupInProgress||resetInProgress)return;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,IMPORT);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(!ownsData||backupInProgress||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==EXPORT)runBackupTask(uri,false);
        else if(request==IMPORT)settingsView.confirmImport(()->runBackupTask(uri,true));
    }
    private void runBackupTask(Uri uri,boolean restore){
        if(backupInProgress||resetInProgress)return;
        BackupCoordinator.request(this,restore?BackupCoordinator.Operation.IMPORT:
            BackupCoordinator.Operation.EXPORT,uri);
    }

    void waitForBackup(BackupCoordinator.Operation operation){
        cancelWritingCheck();
        backupInProgress=true;
        if(isFinishing()||isDestroyed())return;
        boolean export=operation==BackupCoordinator.Operation.EXPORT;
        if(backupDialog!=null)backupDialog.dismiss();
        backupDialog=new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle(export?"Резервное копирование":"Восстановление")
            .setMessage(export?"Сохраняем коллекции, карточки и прогресс…":"Восстанавливаем данные…")
            .setCancelable(false).create();
        ui.styledDialog(backupDialog);
        if(!ownsData)return;
        // Flush the same drafts as onPause before retiring this generation of views.
        if(screen==Screen.TRAIN)saveTrainingSession();
        if(screen==Screen.CARD_SESSION)flashcards.save(selectedDeck);
        if(screen==Screen.RUN)knowledgeCheck.save();
        rememberHomeScroll();
        home.pause();
        tabCache.cancelWarmup();
        bibleSearch.cancel();
        // Earlier writes may post a queue/preference update back to the main looper.
        // The FIFO sentinel lets those callbacks finish before destruction.
        collectionExecutor.execute(()->runOnUiThread(()->{
            if(!isFinishing()&&!isDestroyed())recreate();
        }));
    }

    void backupFinished(BackupCoordinator.Operation operation,Exception failure){
        if(isFinishing()||isDestroyed())return;
        if(backupDialog!=null){backupDialog.dismiss();backupDialog=null;}
        if(failure!=null){
            showBackupFailure(failure);
            return;
        }
        backupInProgress=false;
        if(operation!=BackupCoordinator.Operation.EXPORT)startupState=null;
        openData();
        if(ownsData){
            toast(operation==BackupCoordinator.Operation.EXPORT?
                "Резервная копия создана":"Резервная копия восстановлена");
        }
    }

    private void showBackupFailure(Exception failure){
        backupInProgress=true;
        backupDialog=new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Не удалось завершить операцию")
            .setMessage(failure.getMessage()==null?"Ошибка сохранения данных":failure.getMessage())
            .setCancelable(false).setPositiveButton("Продолжить",(d,w)->{
                backupInProgress=false;
                // Probe the journal again before allowing even a read of session data.
                openData();
            }).setNegativeButton("Закрыть",(d,w)->finish()).create();
        ui.styledDialog(backupDialog);
    }

    @Override protected void onDestroy(){
        cancelWritingCheck();
        readingView.clear();
        assemblyView.clear();
        writingView.clear();
        lessonProgress.clear();
        writingWorker.close();
        BackupCoordinator.detach(this);
        if(bibleStartup!=null)bibleStartup.cancel(false);
        if(backupDialog!=null)backupDialog.dismiss();
        tabCache.cancelWarmup();
        tabTransition.finish();
        collectionGeneration++;
        if(collectionTask!=null)collectionTask.cancel(false);
        collectionHandler.removeCallbacksAndMessages(null);
        // Queue close behind any running read, never close its database mid-query.
        collectionExecutor.execute(()->{
            try{if(collectionStore!=null)collectionStore.close();}
            finally{if(ownsData)BackupCoordinator.releasedFromWorker();}
        });
        collectionExecutor.shutdown();
        modeInfo.dismiss(false);
        bottomNavigation.settleBibleIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleBibleIcon(tab.page);
        bottomNavigation.settleHomeIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleHomeIcon(tab.page);
        bottomNavigation.settleCollectionsIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleCollectionsIcon(tab.page);
        tabCache.close();
        bibleSearch.close();
        home.pause();super.onDestroy();if(store!=null)store.close();
    }
}
