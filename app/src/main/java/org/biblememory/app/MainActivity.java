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
    private static final String UNIFIED_PREFS=ActiveCollection.PREFS;
    private final ActiveCollection activeCollection=new ActiveCollection(this);
    private long openUnifiedId=-1;
    private boolean cardFromUnified=false;
    private final ReviewSession reviewSession=new ReviewSession();
    private KnowledgeCheckSession knowledgeCheck;
    private final KnowledgeCheckView knowledgeCheckView = new KnowledgeCheckView(ui);

    private final ActivityDataLifetime dataLifetime=new ActivityDataLifetime(this);
    private final Bible bible=Bible.shared();
    private final CollectionEntryEditor collectionEditor=new CollectionEntryEditor(this,ui,bible,this::toast);
    private final HomePage home=new HomePage(this,ui,artwork,this.bible,modeInfo,()->{
        if(this.navigation.screen==Screen.HOME)render();
    });
    private final PassagePicker passagePicker=new PassagePicker(this,ui,bible);
    private final BibleReader bibleReader=new BibleReader(this,ui,bible);
    private final BibleStartup bibleStartup=new BibleStartup(this::onBibleLoaded);
    private volatile String bibleError="";
    private int selectedBook=40,selectedChapter=5;
    private int focusVerse=-1;
    private boolean focusPickerSearchHit;
    private long selectedGroup=1;
    private long customTargetGroupId=-1; // A text created via a collection menu belongs to that collection.
    private ScrollView currentScroller;
    // The Home page is a single scroll surface; its expansion and scroll position survive navigation.
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

    private final NavigationState navigation=new NavigationState();
    private void prepareOtherTabs(){
        if(isBibleSearchScreen()&&bibleSearch.active())return;
        tabCache.warm(navigation.screen,bible.isReady(),()->navigation.screen,tabTransition::inProgress,this::prepareTab);
    }

    private void prepareTab(Screen destination) {
        if (tabCache.containsKey(destination)) return;

        Screen previousScreen = navigation.screen;
        Screen previousRendered = navigation.lastRenderedScreen;

        LinearLayout previousRoot = root;
        LinearLayout previousBody = content;
        ScrollView previousScroller = currentScroller;

        int previousNav = bottomNavigation.lastIndex();

        try {
            navigation.screen = destination;

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
                bibleSearchView.searchBar(content,bibleSearch,navigation.screen!=Screen.BOOKS);
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
                            bibleSearchView.results(this,bibleSearch,navigation.screen!=Screen.BOOKS),
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
            navigation.screen = previousScreen;
            navigation.lastRenderedScreen = previousRendered;

            root = previousRoot;
            content = previousBody;
            currentScroller = previousScroller;

            bottomNavigation.setLastIndex(previousNav);
        }
    }

    private final CollectionStudyQueue studyQueue=new CollectionStudyQueue(this);
    private final CollectionInsertion insertion=new CollectionInsertion();
    private boolean resetInProgress=false;
    private boolean backupInProgress=false;
    private boolean resumed;
    private Bundle startupState;
    private Bundle lessonWaitingForBible;
    private String cardStudyMaterial="";
    private final BackupCoordinator.Owner backupOwner=new BackupCoordinator.Owner(){
        @Override public void waitForBackup(BackupCoordinator.Operation operation){beginBackupWait(operation);}
        @Override public void backupFinished(BackupCoordinator.Operation operation,Exception failure){
            finishBackup(operation,failure);
        }
    };
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






    private final CollectionIo collectionIo=new CollectionIo();
    private final CollectionDetailLoader collectionDetail=new CollectionDetailLoader(collectionIo);
    private int openedCollectionChapter;
    private SortedMap<Integer,Long> openedCollectionBook;
    private CollectionProgressSnapshot openedBookProgress;
    private String openedBookName;


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
        if(BackupCoordinator.attach(backupOwner))openData();
    }

    private void openData(){
        ActivityDataLifetime.Open result=dataLifetime.open(bible);
        if(result.recoveryNeeded()){
            BackupCoordinator.request(this,BackupCoordinator.Operation.RECOVER,null);
            return;
        }
        if(result.failure()!=null){showBackupFailure(result.failure());return;}
        Bundle savedInstanceState=startupState;
        startupState=null;
        savedInstanceState=dataLifetime.acceptState(savedInstanceState);
        if(savedInstanceState!=null&&savedInstanceState.getBoolean("backup_return_settings"))
            navigation.screen=Screen.SETTINGS;
        collectionIo.open(getApplicationContext());
        try{
            dataLifetime.recoverReset();
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
        bibleStartup.load(bible,getApplicationContext());
    }

    private void onBibleLoaded(Throwable failure){
        if(isDestroyed()||isFinishing()||!dataLifetime.ownsData()||backupInProgress)return;
        if(failure!=null){
            Throwable cause=failure.getCause()==null?failure:failure.getCause();
            bibleError=cause.getMessage()==null?"Невозможно открыть текст":cause.getMessage();
            android.util.Log.e("BibleLoader","Unable to load Bible corpus",cause);
        }
        Bundle lesson=lessonWaitingForBible;
        lessonWaitingForBible=null;
        if(failure==null&&navigation.screen==Screen.HOME&&restoreActiveLesson(lesson))render();
        else refreshAfterBibleLoad();
        if(failure==null)collectionIo.submit(this::syncCollectionsAfterBibleLoad);
    }

    private void syncCollectionsAfterBibleLoad(LocalStore collectionStore){
        try{BibleSearchLogic.prepare(getApplicationContext());}
        catch(Exception error){android.util.Log.w("BibleLoader","Unable to prepare Bible search",error);}
        try{
            for(LocalStore.UnifiedCollection u:collectionStore.unifiedCollections()){
                try{collectionStore.syncUnifiedCollection(u.id,bible);}
                catch(RuntimeException error){android.util.Log.w("BibleLoader","Unable to sync collection "+u.id,error);}
            }
        }catch(RuntimeException error){android.util.Log.w("BibleLoader","Unable to list collections",error);}
        collectionIo.postRefresh(()->{
            if(navigation.screen==Screen.HOME||navigation.screen==Screen.COLLECTIONS||navigation.screen==Screen.COLLECTION_DETAIL||
                isPickerScreen(navigation.screen))refreshAfterBibleLoad();
        });
    }

    private void refreshAfterBibleLoad(){
        if(isDestroyed()||isFinishing()||backupInProgress)return;
        if(isBibleSearchScreen())renderBibleSearchKeepingInput();
        else if(navigation.screen==Screen.HOME||navigation.screen==Screen.CHAPTERS||navigation.screen==Screen.VERSES
                ||navigation.screen==Screen.CHAPTER||navigation.screen==Screen.CARD_PICK||isPickerScreen(navigation.screen)
                ||navigation.screen==Screen.COLLECTIONS||navigation.screen==Screen.COLLECTION_DETAIL)render();
    }

    private boolean isPickerScreen(Screen value){
        return value==Screen.PICKER_BOOKS||value==Screen.PICKER_CHAPTERS||
            value==Screen.PICKER_VERSES||value==Screen.PICKER_TEXT||value==Screen.BIBLE_PICKER;
    }
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);
        if(!dataLifetime.ownsData()){
            if(startupState!=null)out.putAll(startupState);
            return;
        }
        out.putString("data_generation",dataLifetime.generation());
        if(backupInProgress&&navigation.screen==Screen.SETTINGS)out.putBoolean("backup_return_settings",true);
        if(!backupInProgress&&!resetInProgress){
            Bundle lesson=navigation.screen==Screen.TRAIN?saveActiveLesson():lessonWaitingForBible;
            if(lesson!=null)out.putBundle("active_lesson",lesson);
        }
        if(!isPickerScreen(navigation.screen))return;
        out.putString("picker_screen",navigation.screen.name());out.putString("picker_target",pickerTarget.name());
        out.putString("picker_back",pickerBackScreen.name());out.putLong("picker_id",pickerTargetId);
        out.putLong("picker_unified",openUnifiedId);out.putLong("picker_group",selectedGroup);
        out.putLong("picker_deck",selectedDeck);
        out.putInt("picker_book",selectedBook);out.putInt("picker_chapter",selectedChapter);
        out.putInt("reader_book",readerBookBeforePicker);out.putInt("reader_chapter",readerChapterBeforePicker);
        out.putInt("picker_scroll",currentScroller==null?0:currentScroller.getScrollY());
        passagePicker.saveBounds(out);
        out.putIntArray("picker_quick",new int[]{pickerQuickBook,pickerQuickChapter,pickerQuickScroll,pickerVerseNumber});
        insertion.save(out);
    }
    private void restorePickerState(Bundle state){
        if(state==null||!state.containsKey("picker_screen"))return;
        try{
            navigation.screen=Screen.valueOf(state.getString("picker_screen"));
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
            insertion.restore(state);
        }catch(IllegalArgumentException ignored){navigation.screen=Screen.HOME;passagePicker.selection().cancel();}
    }
    @Override protected void onResume() {
        super.onResume();
        resumed=true;
        if(!dataLifetime.ownsData()||backupInProgress)return;
        bottomNavigation.settleBibleIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleBibleIcon(tab.page);
        bottomNavigation.settleHomeIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleHomeIcon(tab.page);
        bottomNavigation.settleCollectionsIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleCollectionsIcon(tab.page);
        if(dataLifetime.store()!=null && root!=null){home.refresh(true);home.schedule();}
    }
    @Override protected void onPause() {
        cancelWritingCheck();
        resumed=false;
        if(!dataLifetime.ownsData()||backupInProgress){super.onPause();return;}
        modeInfo.dismiss(false);
        bottomNavigation.settleBibleIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleBibleIcon(tab.page);
        bottomNavigation.settleHomeIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleHomeIcon(tab.page);
        bottomNavigation.settleCollectionsIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleCollectionsIcon(tab.page);
        readingView.releaseReveal();
        if(navigation.screen==Screen.TRAIN)saveTrainingSession();
        if(navigation.screen==Screen.CARD_SESSION)flashcards.save(selectedDeck);
        if(navigation.screen==Screen.RUN)knowledgeCheck.save();
        rememberHomeScroll();
        home.pause();
        super.onPause();
    }
    @Override public void onBackPressed() {
        if(resetInProgress||backupInProgress)return;
        if(modeInfo.dismiss(true))return;
        lessonWaitingForBible=null;
        Screen parent=navigation.screen.backDestination();
        if(parent!=null){navigation.screen=parent;render();return;}
        switch(navigation.screen) {
            case HOME: super.onBackPressed(); break;
            case COLLECTION_BOOK:
                openedCollectionBook=null;openedBookProgress=null;
                navigation.screen=Screen.COLLECTION_DETAIL;render();break;
            case RUN:knowledgeCheck.save();navigation.screen=Screen.HOME;render();break;
            case BIBLE_PICKER:
                passagePicker.selection().active=false;passagePicker.chooseEnd(false);
                selectedBook=pickerQuickBook;selectedChapter=pickerQuickChapter;
                pickerRestoredScroll=pickerQuickScroll;focusVerse=-1;
                navigation.screen=Screen.PICKER_TEXT;render();break;
            case PICKER_BOOKS:
                if(passagePicker.selection().active){navigation.screen=Screen.BIBLE_PICKER;render();break;}
                clearInsertionContext();passagePicker.selection().cancel();
                selectedBook=readerBookBeforePicker;selectedChapter=readerChapterBeforePicker;
                navigation.screen=pickerBackScreen;render();break;
            case PICKER_CHAPTERS:
                if(passagePicker.hasChapters()){passagePicker.clearPickerChapterSelection(selectedChapter);break;}
                navigation.screen=passagePicker.selection().active?Screen.BIBLE_PICKER:Screen.PICKER_BOOKS;render();break;
            case CARD_SESSION:flashcards.save(selectedDeck);navigation.screen=cardFromUnified?Screen.HOME:Screen.CARD_DECK;render();break;
            case CHAPTER:navigation.screen=bibleReader.fromVerseGrid()?Screen.VERSES:Screen.CHAPTERS;render();break;
            case REORDER_STUDY:
                {LocalStore.UnifiedCollection u=dataLifetime.store().unifiedForGroup(selectedGroup);
                navigation.screen=u==null?Screen.ENTRIES:Screen.COLLECTION_DETAIL;
                if(u!=null)openUnifiedId=u.id;render();break;}
            case CUSTOM:{
                LocalStore.UnifiedCollection u=dataLifetime.store().unifiedForGroup(customTargetGroupId);
                customTargetGroupId=-1;navigation.screen=u==null?Screen.HOME:Screen.COLLECTION_DETAIL;
                if(u!=null)openUnifiedId=u.id;render();break;}
            case TRAIN:
                studyQueue.setExecuting(false);
                cardStudyQueue.clear();cardStudyCursor=-1;cardStudyDeckId=-1;
                saveTrainingSession();
                lesson.answerReview=null;
                reviewSession.clear();lesson.groupMode=false;lesson.forcedStep=null;
                // The lesson is an isolated activity: every exit goes to HOME.
                navigation.screen=Screen.HOME;render();break;
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
        if(navigation.lastRenderedScreen==Screen.HOME)home.rememberScroll(currentScroller);
    }
    private void render() {
        cancelWritingCheck();
        readingView.clear();
        assemblyView.clear();
        writingView.clear();
        lessonProgress.clear();
        if(backupInProgress)return;
        if(navigation.screen!=Screen.HOME)lessonWaitingForBible=null;
        boolean collectionWasLoading=collectionDetail.retire();
        modeInfo.dismiss(false);
        getWindow().setNavigationBarColor(navigation.screen==Screen.TRAIN||navigation.screen==Screen.RUN?0xFFF8E5D9:CARD);
        bottomNavigation.captureCollectionsIcon(root);
        if(isBibleSearchScreen())bibleSearch.resume();
        else bibleSearch.suspend();
        tabTransition.finish();
        tabCache.invalidate();
        rememberHomeScroll();
        if(currentScroller!=null && navigation.lastRenderedScreen!=null && navigation.lastRenderedScreen!=Screen.HOME
            && !(navigation.lastRenderedScreen==Screen.COLLECTION_DETAIL&&collectionWasLoading)){
            navigation.put(navigation.lastRenderedScreen,currentScroller.getScrollY());
        }
        home.cancelAnimation();
        if(navigation.screen==Screen.TRAIN){navigation.lastRenderedScreen=navigation.screen;renderFullscreenLesson();return;}
        if(navigation.screen==Screen.CARD_SESSION){navigation.lastRenderedScreen=navigation.screen;renderCardSession();return;}
        if(navigation.screen==Screen.RUN){navigation.lastRenderedScreen=navigation.screen;renderWritingRun();return;}
        boolean focusedReaderChapter=navigation.screen==Screen.CHAPTER && bibleReader.fromVerseGrid() && focusVerse>0;
        boolean virtualBibleSearch=isBibleSearchScreen()&&bibleSearch.active()&&!bibleSearch.loading()&&bibleSearch.error().isEmpty();
        root=ui.column();root.setBackground(ui.pageBackground());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        currentScroller=scroll;
        content=ui.column();content.setPadding(ui.dp(19),ui.dp(17),ui.dp(19),ui.dp(27));scroll.addView(content);
        if(isBibleSearchScreen()&&!virtualBibleSearch)bibleSearchView.searchBar(content,bibleSearch,navigation.screen!=Screen.BOOKS);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(navigation.screen) {
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
            ListView results = bibleSearchView.results(this,bibleSearch,navigation.screen!=Screen.BOOKS);

            root.addView(
                    results,
                    new LinearLayout.LayoutParams(
                            -1, 0, 1
                    )
            );

            currentScroller = null;
        }


        if(navigation.screen==Screen.BIBLE_PICKER && passagePicker.rangePanel()!=null)root.addView(passagePicker.rangePanel());
        if(navigation.screen==Screen.PICKER_CHAPTERS&&canBulkPick())addPickerChapterFooter();
        if(navigation.screen==Screen.COLLECTIONS||navigation.screen==Screen.CARDS||
            navigation.screen==Screen.COLLECTION_DETAIL) addUnifiedCollectionFooter();
        if(navigation.screen!=Screen.CUSTOM && navigation.screen!=Screen.RUN_SUMMARY) addNav(root);

        if(isPickerScreen(navigation.screen)){
            scroll.setFocusableInTouchMode(true);
            scroll.requestFocus();
        }
        setContentView(root);
        navigation.lastRenderedScreen = navigation.screen;

        if (navigation.screen.isMainTab()) {
            tabCache.put(
                    navigation.screen,
                    new TabCache.ReadyTab(
                            root,
                            content,
                            currentScroller,
                            home.index()
                    )
            );

            prepareOtherTabs();
        }

        boolean restoredPickerScroll=isPickerScreen(navigation.screen)&&bible.isReady()&&pickerRestoredScroll>=0;
        if(restoredPickerScroll){
            int y=pickerRestoredScroll;pickerRestoredScroll=-1;
            BibleReader.afterLayout(content,()->{if(currentScroller==scroll)scroll.scrollTo(0,y);});
        }
        if(!restoredPickerScroll && !focusedReaderChapter && navigation.screen!=Screen.HOME && navigation.containsKey(navigation.screen) &&
            !(navigation.screen==Screen.BOOKS&&bibleSearch.active()) &&
            !(navigation.screen==Screen.PICKER_BOOKS&&bibleSearch.active()) &&
            navigation.screen!=Screen.PICKER_TEXT){
            int y=navigation.get(navigation.screen);
            Runnable restore=()->{if(currentScroller==scroll)scroll.scrollTo(0,y);};
            if(isPickerScreen(navigation.screen))BibleReader.afterLayout(content,restore);
            else scroll.post(restore);
        }
        if(navigation.screen==Screen.HOME){
            home.restoreScroll(scroll,()->navigation.screen==Screen.HOME&&currentScroller==scroll,true);
        }
    }
    private boolean isBibleSearchScreen(){
        return navigation.screen==Screen.BOOKS||navigation.screen==Screen.PICKER_BOOKS;
    }

    private BibleSearchSession.Scope activeSearchScope(){
        if(isDestroyed())return null;
        if(navigation.screen==Screen.BOOKS)return BibleSearchSession.Scope.READER;
        return navigation.screen==Screen.PICKER_BOOKS?BibleSearchSession.Scope.PICKER:null;
    }

    private void renderBibleSearchKeepingInput(){
        BibleSearchView.preserveInput(()->root,this::render);
    }

    private void openSearchHit(BibleSearchLogic.Hit hit){
        selectedBook=hit.book;
        selectedChapter=hit.chapter;
        if(!isPickerScreen(navigation.screen))bibleReader.rememberChapter(hit.book,hit.chapter);
        focusVerse=hit.verse;
        boolean inPicker=navigation.screen==Screen.PICKER_BOOKS||navigation.screen==Screen.BIBLE_PICKER;
        bibleSearch.reset();
        if(inPicker){
            focusPickerSearchHit=true;
            pickerRestoredScroll=-1;
            navigation.screen=Screen.PICKER_TEXT;
        }else{
            bibleReader.fromSearch();
            navigation.screen=Screen.CHAPTER;
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
            if(index==0&&navigation.screen==Screen.HOME){bottomNavigation.animateHomeIcon(root,true,true);return;}
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
        return navigation.screen.mainTabIndex(isPickerScreen(navigation.screen)&&pickerTarget==BibleTarget.STUDY_GROUP
            &&dataLifetime.store().unifiedForGroup(pickerTargetId)==null);
    }
    /** One continuous two-page slide; the bottom bar is never moved or blanked. */

    private void goToMainTab(int index) {
        if (index < 0 || index >= Screen.TAB_COUNT || tabTransition.inProgress() || resetInProgress || backupInProgress) {
            return;
        }

        if (navigation.screen == Screen.TRAIN
                || navigation.screen == Screen.CARD_SESSION || navigation.screen == Screen.RUN) {
            return;
        }

        final Screen destination=Screen.tab(index);

        if (navigation.screen == destination) return;
        modeInfo.dismiss(false);
        if(isBibleSearchScreen())bibleSearch.suspend();
        if(isPickerScreen(navigation.screen)){
            passagePicker.selection().cancel();clearInsertionContext();
            selectedBook=readerBookBeforePicker;selectedChapter=readerChapterBeforePicker;
        }
        // Tapping an already selected section from its details simply opens the root list.
        if(mainTabIndex()==index && !navigation.screen.isMainTab()){
            navigation.screen=destination;render();return;
        }

        final int oldIndex = mainTabIndex();
        // Reset only this decorative icon before detaching the cached outgoing page.
        if(oldIndex==2 && index!=2)bottomNavigation.animateCollectionsIcon(root,false,true);
        final int direction = index > oldIndex ? 1 : -1;

        final Screen previousScreen = navigation.screen;
        final boolean leavingHasNav =
                navigation.screen != Screen.CUSTOM;

        if (navigation.screen == Screen.BIBLE_PICKER) {
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
        if (navigation.lastRenderedScreen == Screen.HOME) {
            rememberHomeScroll();
        } else if (navigation.lastRenderedScreen != null
                && currentScroller != null) {

            navigation.put(
                    navigation.lastRenderedScreen,
                    currentScroller.getScrollY()
            );
        }

        TabCache.ReadyTab cached = tabCache.validPage(destination,home.index());

        navigation.screen = destination;
        bibleSearch.resume();

        if (cached != null) {

            // Главное изменение:
            // показываем готовый экран без повторного render().
            root = cached.page;
            content = cached.body;
            currentScroller = cached.scroller;

            setContentView(root);

            navigation.lastRenderedScreen = navigation.screen;
            bottomNavigation.setLastIndex(index);

            if (destination == Screen.HOME) {
                final ScrollView active = currentScroller;

                home.restoreScroll(active,()->currentScroller==active,false);

            } else if (currentScroller!=null && navigation.containsKey(destination)) {

                final ScrollView active = currentScroller;
                final int y = navigation.get(destination);

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
        if(navigation.screen!=Screen.TRAIN&&navigation.screen!=Screen.CARD_SESSION&&!tabTransition.inProgress()
            &&navigationGesture.handle(event,delta->goToMainTab(mainTabIndex()+delta),e->super.dispatchTouchEvent(e)))
            return true;
        return super.dispatchTouchEvent(event);
    }

    private void restoreCollectionQueue(){studyQueue.restore(dataLifetime.store());}
    private void clearCollectionQueue(){studyQueue.clear();}

    private void startStudyCollection(long groupId){
        CollectionProgressSnapshot snapshot=dataLifetime.store().collectionProgress();
        List<LocalStore.Entry> entries=snapshot.entries(groupId);
        if(entries.isEmpty()){
            if(studyQueue.group()==groupId)studyQueue.clear();
            toast("Коллекция пока пустая");return;
        }
        cardStudyQueue.clear();cardStudyCursor=-1;cardStudyDeckId=-1;
        openTraining(studyQueue.start(dataLifetime.store(),groupId,entries,snapshot,(entry,progress)->nextGroup(entry,progress)!=null));
    }

    private boolean resumeCollectionTraining(){
        long id=studyQueue.resume(dataLifetime.store());
        if(id<0)return false;
        openTraining(id);return true;
    }

    private void advanceStudyCollection(){
        if(studyQueue.cursor()<0||!studyQueue.executing())return;
        long id=studyQueue.advance();
        if(id<0){
            toast("Все отрывки коллекции пройдены");
            navigation.screen=Screen.HOME;render();return;
        }
        openTraining(id);
    }
    private String studyProgressFor(long groupId){
        return CollectionViews.studyProgress(groupId,dataLifetime.store().collectionProgress());
    }
    private void chooseCardDeck(Pick action){
        List<LocalStore.UnifiedCollection> collections=dataLifetime.store().unifiedCollections();
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
            else{openUnifiedId=u.id;navigation.screen=Screen.COLLECTION_DETAIL;render();}
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
        for(LocalStore.CardDeck deck:dataLifetime.store().cardDecks()){
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
            panel.setOnClickListener(v->{selectedDeck=deck.id;navigation.screen=Screen.CARD_DECK;render();});
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
                dataLifetime.store().renameCardDeck(deck.id,value(name));render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void confirmDeleteCardDeck(LocalStore.CardDeck deck){
        ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Удалить подборку?")
            .setPositiveButton("Удалить",(d,w)->{
                dataLifetime.store().deleteCardDeck(deck.id);flashcards.clearSaved(deck.id);
                navigation.screen=Screen.CARDS;render();
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
                dataLifetime.store().addCustomFlashcard(deckId,value(reference),value(verse));
                if(navigation.screen==Screen.CARD_DECK||navigation.screen==Screen.CARDS)render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void renderCardDeck(){
        LocalStore.CardDeck deck=dataLifetime.store().cardDeck(selectedDeck);
        if(deck==null){navigation.screen=Screen.CARDS;render();return;}
        title(deck.title,null);
        List<LocalStore.Flashcard> cards=dataLifetime.store().flashcards(selectedDeck);
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

        boolean hasEntries = !dataLifetime.store().entries(group.id).isEmpty();

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
        navigation.remove(Screen.PICKER_BOOKS);
        navigation.remove(Screen.PICKER_CHAPTERS);
        navigation.remove(Screen.PICKER_VERSES);
        bibleSearch.reset();
        navigation.screen=Screen.PICKER_BOOKS;render();
    }
    /** Range settings reuse the same catalog and numeric grids as quick insertion. */
    private void openPickerRange(){
        pickerQuickBook=selectedBook;pickerQuickChapter=selectedChapter;
        pickerQuickScroll=currentScroller==null?0:currentScroller.getScrollY();
        passagePicker.selection().openBook(selectedBook);passagePicker.selection().active=true;
        navigation.screen=Screen.BIBLE_PICKER;render();
    }
    private void beginPickerBoundary(boolean end){
        passagePicker.chooseEnd(end);
        selectedBook=passagePicker.selection().book;
        int previous=end?passagePicker.selection().endChapter:passagePicker.selection().startChapter;
        selectedChapter=previous>0?previous:passagePicker.selection().startChapter>0?
            passagePicker.selection().startChapter:pickerQuickChapter;
        navigation.remove(Screen.PICKER_CHAPTERS);navigation.remove(Screen.PICKER_VERSES);
        pickerRestoredScroll=-1;
        navigation.screen=selectedBook>0?Screen.PICKER_CHAPTERS:Screen.PICKER_BOOKS;render();
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
                bibleReader.selectVerse(verse);focusVerse=verse;navigation.screen=Screen.CHAPTER;
            }else if(boundary){
                passagePicker.pickBoundaryVerse(selectedChapter,verse);
                navigation.screen=Screen.BIBLE_PICKER;
            }else{
                pickerVerseNumber=verse;focusVerse=verse;pickerRestoredScroll=-1;
                focusPickerSearchHit=false;navigation.screen=Screen.PICKER_TEXT;
            }
            render();
        });
    }
    private void renderPickerVerseText(){
        passagePicker.clearRangePanel();
        if(bibleNotReady())return;
        passagePicker.openChapter(selectedBook,selectedChapter);
        title(Bible.NAMES[selectedBook-1]+" "+selectedChapter,null);
        LinearLayout chapterLinks=ui.row();
        chapterLinks.addView(ui.button("Главы · "+selectedChapter,false,()->{
            navigation.remove(Screen.PICKER_CHAPTERS);navigation.screen=Screen.PICKER_CHAPTERS;render();
        }),new LinearLayout.LayoutParams(0,-2,1));
        chapterLinks.addView(ui.button("Стихи",false,()->{
            navigation.remove(Screen.PICKER_VERSES);navigation.screen=Screen.PICKER_VERSES;render();
        }),new LinearLayout.LayoutParams(0,-2,1));
        ui.add(content,chapterLinks,4);
        ui.action(content,"Задать диапазон",false,this::openPickerRange);
        final int book=selectedBook,chapter=selectedChapter;
        final int targetVerse=focusVerse;focusVerse=-1;
        final boolean scrollToTarget=pickerRestoredScroll<0&&(focusPickerSearchHit||targetVerse>1);
        focusPickerSearchHit=false;
        boolean study=pickerTarget==BibleTarget.STUDY_GROUP;
        passagePicker.renderVerses(content,book,chapter,targetVerse,scrollToTarget,
            ()->navigation.screen==Screen.PICKER_TEXT?currentScroller:null,
            study?dataLifetime.store().entries(pickerTargetId):Collections.emptyList(),
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
            navigation.screen=pickerBackScreen;render();
        }else passagePicker.updatePickerRangeSummary();
    }
    private boolean pickerCollectionContainsVerse(int book,int chapter,int verse){
        if(pickerTarget!=BibleTarget.STUDY_GROUP || pickerTargetId<0)return false;
        String ref=Bible.reference(book,chapter,verse,chapter,verse);
        for(LocalStore.Entry e:dataLifetime.store().entries(pickerTargetId))
            if(ref.equals(e.reference))return true;
        return false;
    }
    /** Toggle one standalone verse in the collection that opened the picker.
     * Detaching preserves progress; it never deletes the underlying study row. */
    private boolean togglePickerCollectionVerse(int book,int chapter,int verse){
        if(pickerTarget!=BibleTarget.STUDY_GROUP ||
            dataLifetime.store().unifiedForGroup(pickerTargetId)==null)return false;
        String ref=Bible.reference(book,chapter,verse,chapter,verse);
        try{
            if(pickerCollectionContainsVerse(book,chapter,verse)){
                if(!dataLifetime.store().removeUnifiedPassage(pickerTargetId,ref))return false;
                toast("Убрано из коллекции: "+ref);
            }else{
                // Avoid creating duplicate standalone entries inside a saved long range.
                for(LocalStore.Entry entry:dataLifetime.store().entries(pickerTargetId)){
                    if(!entry.reference.startsWith(Bible.NAMES[book-1]+" "))continue;
                    if(entry.reference.equals(ref))continue;
                    for(Bible.Verse line:entry.lines())if(line.ref.equals(chapter+":"+verse)){
                        toast("Стих уже входит в отрывок этой коллекции");
                        return false;
                    }
                }
                dataLifetime.store().addPickedPassage(pickerTargetId,ref,
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
        for(LocalStore.Entry entry:dataLifetime.store().entries(active.groupId))
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
                if(!dataLifetime.store().removeUnifiedPassage(active.groupId,ref))return false;
                toast("Убрано из коллекции: "+ref);
            }else{
                dataLifetime.store().restoreOrAddStudyPassage(active.groupId,ref,
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
            for(LocalStore.Entry e:dataLifetime.store().entries(pickerTargetId))if(ref.equals(e.reference))return true;
        }else{
            for(LocalStore.Flashcard f:dataLifetime.store().flashcards(pickerTargetId))
                if(f.bookId==book&&f.firstChapter==chapter&&f.lastChapter==chapter&&
                    f.firstVerse==verse&&f.lastVerse==verse)return true;
        }
        return false;
    }
    private boolean savePickedPassage(int sc,int sv,int ec,int ev){
        return savePickedPassageFor(selectedBook,sc,sv,ec,ev);
    }
    private boolean savePickedPassageFor(int book,int sc,int sv,int ec,int ev){
        return CollectionPassages.save(dataLifetime.store(),bible,insertion,pickerTarget==BibleTarget.STUDY_GROUP,
            pickerTargetId,book,sc,sv,ec,ev,this::toast);
    }
    private void saveBibleVerseToGroup(long groupId,int book,int chapter,int verse){
        try{
            String ref=Bible.reference(book,chapter,verse,chapter,verse);
            for(LocalStore.Entry e:dataLifetime.store().entries(groupId))if(ref.equals(e.reference)){
                toast("Стих уже в подборке");return;
            }
            dataLifetime.store().restoreOrAddStudyPassage(groupId,ref,bible.passage(book,chapter,verse,chapter,verse));
            toast("Добавлено: "+ref);
        }catch(Exception ex){toast("Не удалось добавить стих");}
    }
    /** Study each card as its own entry; a range retains its internal contiguous links. */
    private void studyCardDeck(){
        List<LocalStore.Flashcard> cards=dataLifetime.store().flashcards(selectedDeck);
        if(cards.isEmpty()){toast("Подборка пуста");return;}
        // Flashcards remain free-form; only starting a memorization lesson requires words.
        for(LocalStore.Flashcard card:cards)if(card.bookId==0&&ExerciseTextLogic.words(card.content).isEmpty()){
            toast("Исправь текст карточки «"+card.reference+"»: нужно хотя бы одно слово или число");
            return;
        }
        if(!bible.isReady())for(LocalStore.Flashcard f:cards)
            if(f.bookId>0){toast("Библия ещё загружается");return;}
        cardStudyQueue.clear();cardStudyCursor=0;cardStudyDeckId=selectedDeck;
        cardStudyMaterial=LessonRestoration.cardMaterial(cards);
        for(LocalStore.Flashcard card:cards){
            try{
                List<Bible.Verse> verses=card.bookId==0?
                    Collections.singletonList(new Bible.Verse("1",card.content)):
                    bible.passage(card.bookId,card.firstChapter,card.firstVerse,card.lastChapter,card.lastVerse);
                long id=dataLifetime.store().findOrCreateCardStudyEntry(card,verses);
                if(!cardStudyQueue.contains(id))cardStudyQueue.add(id);
            }catch(Exception ex){
                studyQueue.setExecuting(false);
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
        navigation.screen=Screen.CARD_DECK;render();
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
            String text=VerseText.join(lines);
            boolean added=dataLifetime.store().addFlashcard(deckId,book,sc,sv,ec,ev,Bible.reference(book,sc,sv,ec,ev),text);
            toast(added?"Карточка добавлена":"Карточка уже есть в подборке");
            if(navigation.screen==Screen.CARD_PICK){navigation.screen=Screen.CARD_DECK;render();}
        }catch(Exception ex){toast(ex.getMessage()==null?"Не удалось добавить карточку":ex.getMessage());}
    }
    private List<LocalStore.Flashcard> eligibleSessionCards(){
        List<LocalStore.Flashcard> items=dataLifetime.store().flashcards(selectedDeck);
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
        navigation.screen=Screen.CARD_SESSION;render();
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
        navigation.screen=cardFromUnified?Screen.HOME:Screen.CARD_DECK;render();
    }

    private void renderCardSession(){
        long currentId=flashcards.currentId();
        for(LocalStore.Flashcard current:dataLifetime.store().flashcards(selectedDeck)){
            if(current.id!=currentId)continue;
            LocalStore.CardDeck deck=dataLifetime.store().cardDeck(selectedDeck);
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
        home.render(content,active,active==null?null:dataLifetime.store().collectionProgress(),this::bookForEntry,
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
        boolean bulk=navigation.screen==Screen.PICKER_BOOKS&&canBulkPick();
        Set<Integer> addedBooks=bulk?dataLifetime.store().collectionBooks(pickerTargetId).keySet():Collections.emptySet();
        if(bulk)passagePicker.beginBookCatalog();
        bibleReader.renderBooks(content,this::openCatalogBook,bulk?book->
            passagePicker.bookAction(pickerTargetId,book,addedBooks.contains(book),this::changeWholeBook):null);
    }

    private void openCatalogBook(int book){
        selectedBook=book;selectedChapter=navigation.screen==Screen.PICKER_BOOKS&&passagePicker.selection().book==book?
            passagePicker.selection().chapter:bibleReader.lastReadChapter(book);
        if(navigation.screen==Screen.PICKER_BOOKS){
            boolean rangeActive=passagePicker.selection().active;
            passagePicker.selection().openBook(book);passagePicker.selection().active=rangeActive;
            passagePicker.resetChapters();
            navigation.remove(Screen.PICKER_CHAPTERS);
            navigation.remove(Screen.PICKER_VERSES);
            pickerRestoredScroll=-1;
            navigation.screen=Screen.PICKER_CHAPTERS;
        }else {selectingPassage=false;clearPassageSelection();bibleReader.resetVerse();
            navigation.remove(Screen.VERSES);navigation.screen=Screen.CHAPTERS;}
        render();
    }

    private void openReaderVerseGrid(int chapter) {
        selectedChapter=chapter;bibleReader.resetVerse();
        bibleReader.rememberChapter(selectedBook,chapter);
        navigation.remove(Screen.VERSES);
        navigation.screen=Screen.VERSES;render();
    }
    private void renderChapters() {
        if(bibleNotReady())return;
        int n=bible.chapterCount(selectedBook);
        boolean picker=navigation.screen==Screen.PICKER_CHAPTERS;
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
            navigation.remove(Screen.PICKER_VERSES);
            pickerRestoredScroll=-1;
            navigation.screen=Screen.PICKER_VERSES;render();
        },bulk?(c,button)->{
            passagePicker.configureChapter(c,button,selected);
        }:null);
    }

    private boolean canBulkPick(){
        return pickerTarget==BibleTarget.STUDY_GROUP&&pickerTargetId>0&&!passagePicker.selection().active&&insertion.afterStudy()<0;
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
        runCollectionMutation(collectionStore->collectionStore.addBibleChapters(group,book,chapters,false,bible),success->{
            passagePicker.finishSaveChapters(success,navigation.screen==Screen.PICKER_CHAPTERS&&pickerTargetId==group
                &&selectedBook==book,selectedChapter);
            if(success){
                toast("Главы добавлены");
                refreshCollectionAfterBulkChange(group);
            }
        });
    }

    private void changeWholeBook(long group,int book,boolean remove){
        runCollectionMutation(collectionStore->{
            if(remove)collectionStore.removeBibleBook(group,book);
            else collectionStore.addBibleChapters(group,book,Collections.emptyList(),true,bible);
        },success->{
            passagePicker.finishBookChange(group,book,remove,success,
                navigation.screen==Screen.PICKER_BOOKS&&pickerTargetId==group,this::changeWholeBook);
            if(success)refreshCollectionAfterBulkChange(group);
        });
    }

    private void runCollectionMutation(java.util.function.Consumer<LocalStore> write,java.util.function.Consumer<Boolean> finished){
        runCollectionMutation(write,finished,null);
    }

    private void runCollectionMutation(java.util.function.Consumer<LocalStore> write,java.util.function.Consumer<Boolean> finished,
            Runnable detachedSuccess){
        if(isDestroyed()||isFinishing()||backupInProgress)return;
        collectionIo.mutate(write,success->{
            if(isDestroyed()||isFinishing()){
                if(success&&detachedSuccess!=null)detachedSuccess.run();
                return;
            }
            if(!success)toast("Не удалось изменить коллекцию");
            finished.accept(success);
        });
    }

    private void refreshCollectionAfterBulkChange(long group){
        if(navigation.screen==Screen.COLLECTION_DETAIL&&selectedGroup==group)render();
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
        Map<Integer,LocalStore.VerseStatus> statuses=dataLifetime.store().statusesForChapter(
            Bible.NAMES[selectedBook-1],selectedChapter);
        if(bibleReader.renderChapter(content,selectedBook,verses,statuses,focusVerse,
            ()->navigation.screen==Screen.CHAPTER?currentScroller:null,this::date))focusVerse=-1;
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
                (navigation.screen!=Screen.BIBLE_PICKER && navigation.screen!=Screen.CHAPTER && navigation.screen!=Screen.CARD_PICK))return;
            suppress[0]=false;matchStart.run();
        });
        update.run();
        ui.action(builder,navigation.screen==Screen.BIBLE_PICKER?"Добавить отрывок":cardPicker?"Добавить карточку":"Просмотреть отрывок",true,()->{
            update.run();
            if(!PassageRangeLogic.valid(rangeStartChapter,rangeStartVerse,rangeEndChapter,rangeEndVerse)){
                toast("Конец не может быть раньше начала");return;
            }
            if(navigation.screen==Screen.BIBLE_PICKER){
                if(savePickedPassage(rangeStartChapter,rangeStartVerse,rangeEndChapter,rangeEndVerse)){
                    clearInsertionContext();navigation.screen=pickerBackScreen;render();
                }
            }
            else if(cardPicker)addCardFromSelection(selectedDeck);
            else previewPassage();
        });
        if(!cardPicker&&navigation.screen!=Screen.BIBLE_PICKER)ui.action(builder,"Добавить в карточки",false,()->{
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
        for(LocalStore.Entry e:dataLifetime.store().allEntries())if(ref.equals(e.reference)){
            existing=e;break;
        }
        int mastered=existing==null?0:dataLifetime.store().masteryCount(existing.id);
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
                chooseGroup(groupId->{dataLifetime.store().add(groupId,ref,ref,verses);toast("Добавлено в подборку");});
            });
        }else{
            dialog.setPositiveButton("Начать изучение",(d,w)->{
                chooseGroup(groupId->{long id=dataLifetime.store().add(groupId,ref,ref,verses);
                    selectingPassage=false;openTraining(id);});
            });
            dialog.setNeutralButton("Добавить в подборку",(d,w)->{
                chooseGroup(groupId->{dataLifetime.store().add(groupId,ref,ref,verses);toast("Добавлено в подборку");});
            });
        }
        dialog.show();
    }
    private interface Pick {void apply(long groupId);}
    private void chooseGroup(Pick action) {
        List<LocalStore.UnifiedCollection> collections=dataLifetime.store().unifiedCollections();
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
            else{openUnifiedId=u.id;navigation.screen=Screen.COLLECTION_DETAIL;render();}
        });
    }
    /** Study collections get only these four approved actions, in this exact order. */
    private void showStudyGroupActions(LocalStore.Group group,View anchor){
        String[] actions={"Добавить свой текст","Переименовать","Удалить"};
        String[] icons={"file","pencil","trash"};
        menus.showActionsMenu(anchor,actions,icons,which->{
            selectedGroup=group.id;
            switch(which){
                case 0:customTargetGroupId=group.id;navigation.screen=Screen.CUSTOM;render();break;
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
                dataLifetime.store().deleteGroup(group.id);navigation.screen=Screen.HOME;render();
            }).setNegativeButton("Отмена",null).create());
    }


    private void renderEntries() {

        LocalStore.Group group = dataLifetime.store().group(selectedGroup);

        if (group == null) {
            navigation.screen = Screen.HOME;
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
                dataLifetime.store().entries(group.id)) {

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
                dataLifetime.store().renameGroup(group.id,value(name));render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void clearInsertionContext(){insertion.clear();}
    /** Dedicated drag-to-reorder mode; dragging never starts in normal study lists. */
    private void renderStudyReorder(){
        title("Изменить порядок", "Удерживай строку и перетащи её на новое место");
        List<LocalStore.Entry> list=dataLifetime.store().entries(selectedGroup);
        List<Long> ids=new ArrayList<>();for(LocalStore.Entry e:list)ids.add(e.id);
        for(LocalStore.Entry e:list)renderReorderRow(e.id,e.reference,ids,false);
        ui.action(content,"Готово",true,()->{
            LocalStore.UnifiedCollection u=dataLifetime.store().unifiedForGroup(selectedGroup);
            navigation.screen=u==null?Screen.ENTRIES:Screen.COLLECTION_DETAIL;
            if(u!=null)openUnifiedId=u.id;render();
        });
    }
    private void renderCardReorder(){
        title("Изменить порядок", "Удерживай строку и перетащи её на новое место");
        List<LocalStore.Flashcard> list=dataLifetime.store().flashcards(selectedDeck);
        List<Long> ids=new ArrayList<>();for(LocalStore.Flashcard f:list)ids.add(f.id);
        for(LocalStore.Flashcard f:list)renderReorderRow(f.id,f.reference,ids,true);
        ui.action(content,"Готово",true,()->{navigation.screen=Screen.CARD_DECK;render();});
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
                if(cards)dataLifetime.store().reorderCards(selectedDeck,ids);
                else dataLifetime.store().reorderStudy(selectedGroup,ids);
                render();return true;
            }
            return true;
        });
        ui.add(content,item,7);
    }

    private void editStudyEntry(LocalStore.Entry entry){
        collectionEditor.study(dataLifetime.store(),entry,bookForEntry(entry),this::render);
    }
    private void editCustomStudyEntry(LocalStore.Entry entry){collectionEditor.custom(dataLifetime.store(),entry,this::render);}
    private void editCardEntry(LocalStore.Flashcard card){
        collectionEditor.card(dataLifetime.store(),card,()->selectedDeck,this::render);
    }

    private void showEntryActions(LocalStore.Entry entry,View anchor){
        String[] labels={"Изменить отрывок","Добавить стих / отрывок после него","Удалить"};
        String[] icons={"pencil","book","trash"};
        menus.showActionsMenu(anchor,labels,icons,which->{
            switch(which){
                case 0:editStudyEntry(entry);break;
                case 1:
                    clearInsertionContext();
                    insertion.appendStudy(entry.id);
                    openBiblePicker(BibleTarget.STUDY_GROUP,entry.groupId,navigation.screen==Screen.COLLECTION_DETAIL?Screen.COLLECTION_DETAIL:Screen.ENTRIES);break;
                case 2:
                    ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                        .setTitle("Убрать отрывок из коллекции?")
                        .setPositiveButton("Убрать",(d,w)->{
                            dataLifetime.store().removeUnifiedPassage(entry.groupId,entry.reference);render();
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
                    insertion.appendCard(card.id);
                    openBiblePicker(BibleTarget.FLASHCARD_DECK,selectedDeck,Screen.CARD_DECK);break;
                case 2:
                    ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                        .setTitle("Убрать карточку из подборки?")
                        .setPositiveButton("Убрать",(d,w)->{
                            dataLifetime.store().removeFlashcard(selectedDeck,card.id);render();
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
                            dataLifetime.store().renameEntry(entry.id,value(name));render();
                        }).setNegativeButton("Отмена",null).show();
                } else new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                    .setTitle("Удалить отрывок?")
                    .setMessage("Будут удалены и его результаты обучения.")
                    .setPositiveButton("Удалить",(dd,w)->{dataLifetime.store().deleteEntry(entry.id);render();})
                    .setNegativeButton("Отмена",null).show();
            }).show();
    }
    private void entryCard(LocalStore.Entry e) {
        entryCard(e,dataLifetime.store().collectionProgress());
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
                if(dataLifetime.store().group(groupId)==null){toast("Коллекция не найдена");return;}
                customTargetGroupId=-1;
                long id=dataLifetime.store().add(groupId,t,r.isEmpty()?"Свой текст":r,items);
                openTraining(id);
            }else{
                chooseGroup(groupId->{long id=dataLifetime.store().add(groupId,t,r.isEmpty()?"Свой текст":r,items);openTraining(id);});
            }
        });

    }
    /** Pressing 'Повторить изученное' starts the first due exercise immediately. */
    private void startReviewSession(long group) {
        lessonWaitingForBible=null;
        if(!bibleReadyForRecall())return;
        long now=System.currentTimeMillis();
        ReviewSession.Start start=reviewSession.start(dataLifetime.store(),getSharedPreferences(SESSION_PREFS,MODE_PRIVATE),group,now);
        LocalStore.DueItem item=start.first();
        while(item!=null){
            if(prepareReview(item)){
                if(item==start.first())lesson.lessonWriting.draft=start.draft();
                render();return;
            }
            item=reviewSession.advance(dataLifetime.store(),now);
        }
        navigation.screen=Screen.DUE;render();
    }

    private void renderDue() {
        LearningHelp help=reviewSession.group()>0?LearningHelp.REVIEW:LearningHelp.ALL_REVIEWS;
        title(help.title,null);
        LinearLayout c=ui.headerCard(content);
        ui.add(c,ui.txt("Пока нет заданий на повторение",21,WHITE,true),0);
        ui.helper(c,reviewSession.group()>0?"В выбранной коллекции сейчас нет заданий, срок которых наступил.":
            "Сейчас нет запланированных заданий, срок которых наступил.");
        ui.action(content,"На главную",true,()->{navigation.screen=Screen.HOME;render();});
    }
    private boolean openReview(LocalStore.DueItem item){
        if(!prepareReview(item))return false;
        render();return true;
    }
    private boolean prepareReview(LocalStore.DueItem item){
        lesson.answerReview=null;
        LocalStore.Entry entry=dataLifetime.store().entry(item.entryId);
        if(!reviewSession.open(entry,item))return false;
        lesson.lessonSourceEntry=entry.id;lesson.lessonSourceGroup=entry.groupId;
        lesson.selectedEntry=entry.id;lesson.lessonPracticeAgain=false;
        lesson.studyStage=reviewSession.current().studyStage();
        lesson.groupMode=false;lesson.forcedStep=null;lesson.stepCursor=0;
        lesson.weakVerse=-1;lesson.weakTransitionFrom=-1;
        resetTrainingStep(entry);
        lesson.lessonWriting.clearGaps();
        lesson.trainingMode=TrainingMode.TYPE;navigation.screen=Screen.TRAIN;
        return true;
    }
    private void advanceReviewSession(){
        long now=System.currentTimeMillis();
        LocalStore.DueItem next;
        while((next=reviewSession.advance(dataLifetime.store(),now))!=null){
            if(openReview(next))return;
        }
        lesson.forcedStep=null;
        ReviewSessionPreferences.clearPending(getSharedPreferences(SESSION_PREFS,MODE_PRIVATE));
        navigation.screen=Screen.HOME;render();toast("Все запланированные повторения завершены");
    }
    private void rememberRecent(long id) {
        JSONArray updated=new JSONArray();updated.put(id);
        int count=1;
        for(Long prev:recentIds()) {
            if(prev==id || dataLifetime.store().entry(prev)==null)continue;
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
        Bundle state=LessonCheckpoint.capture(dataLifetime.store(),lesson,reviewSession);
        LessonRestoration.saveRoute(state,lesson,reviewSession,studyQueue,cardStudyDeckId,cardStudyCursor,
            cardStudyMaterial,cardStudyQueue);
        return state;
    }

    private boolean restoreActiveLesson(Bundle state){
        SharedPreferences pref=getSharedPreferences(SESSION_PREFS,MODE_PRIVATE);
        LessonRestoration.Restored saved=LessonRestoration.validate(dataLifetime.store(),pref,state,studyQueue);
        if(saved==null)return false;
        long id=saved.entry().id;
        boolean review=saved.review(),linked=saved.linked(),complete=saved.complete();
        int start=saved.start(),end=saved.end();
        String stage=saved.stage(),key=saved.key();
        if(review){
            LocalStore.DueItem item=reviewSession.restore(dataLifetime.store(),pref,state.getLong("review_group",-1),
                System.currentTimeMillis(),saved.entry(),new LocalStore.DueItem(id,stage,start,end,0),complete);
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
        lesson.lessonSourceEntry=saved.source().id;lesson.lessonSourceGroup=saved.source().groupId;
        lesson.lessonSessionMaterial=state.getString("material","");
        lesson.lessonSourceMaterial=state.getString("source_material","");
        studyQueue.setExecuting(saved.inQueue());
        if(saved.deck()>0){
            cardStudyDeckId=saved.deck();cardStudyCursor=saved.deckCursor();
            cardStudyMaterial=state.getString("deck_material","");
            for(long next:saved.deckQueue())cardStudyQueue.add(next);
        }
        lesson.lessonPracticeAgain=state.getBoolean("practice_again");
        lesson.testAssisted=state.getBoolean("assisted");
        lesson.checked=lesson.passed=complete;
        if(complete)lesson.lastAccuracy=100;
        navigation.screen=Screen.TRAIN;
        return true;
    }

    private void saveTrainingSession(){
        LessonSessionPreferences.save(getSharedPreferences(SESSION_PREFS,MODE_PRIVATE),lesson,reviewSession);
    }
    private boolean restoreTrainingSession(long id) {return restoreTrainingSession(id,false);}
    private boolean restoreTrainingSession(long id,boolean linked) {
        return restoreTrainingSession((linked?"linked_":"entry_")+id+"_",linked);
    }
    private boolean restoreTrainingSession(String key,boolean linked){
        return LessonSessionPreferences.restore(getSharedPreferences(SESSION_PREFS,MODE_PRIVATE),key,linked,lesson);
    }
    private void openTraining(long id) {
        if(navigation.screen==Screen.TRAIN)saveTrainingSession();
        lesson.answerReview=null;
        reviewSession.clear();lesson.groupMode=false;lesson.forcedStep=null;
        if(navigation.screen==Screen.CARD_SESSION)flashcards.save(selectedDeck);
        LocalStore.Entry entry=LessonLaunch.select(dataLifetime.store(),lesson,id);
        if(entry==null){toast("Отрывок не найден");navigation.screen=Screen.HOME;render();return;}
        id=entry.id;
        SharedPreferences saved=getSharedPreferences(SESSION_PREFS,MODE_PRIVATE);
        LessonLaunch.GroupDraft link=LessonLaunch.pendingGroup(dataLifetime.store(),saved,lesson,entry);
        if(link!=null){
            lesson.returnVerseCursor=link.returnCursor();
            prepareGroup(link.candidate());restoreTrainingSession(id,true);
            lesson.returnVerseCursor=link.returnCursor();render();return;
        }
        if(!LessonLaunch.sameDraft(lesson,saved,"entry_"+id+"_")||!restoreTrainingSession(id)){
            selectRecommendation(entry);resetTrainingStep(entry);
        } else if(lesson.trainingMode!=TrainingMode.TYPE) {
            List<StudyPlanLogic.Step> steps=studySteps(entry);
            if(!steps.isEmpty()) {
                StudyPlanLogic.Step current=steps.get(Math.min(lesson.stepCursor,steps.size()-1));
                lesson.testAssisted=dataLifetime.store().isStepMastered(entry.id,lesson.studyStage.name(),current.start,current.end);
            }
        }
        // If an old session points beyond the newly protected lesson boundary, restore safely.
        if(lesson.studyStage==StudyPlanLogic.Stage.VERSE)
            lesson.stepCursor=Math.min(lesson.stepCursor,dataLifetime.store().unlockedPosition(id,entry.lines().size()));
        if(studyQueue.executing() && studyQueue.newStep()){
            // Revisit every record even if it was previously mastered. Do not erase progress.
            lesson.studyStage=StudyPlanLogic.Stage.VERSE;
            lesson.stepCursor=0;lesson.groupMode=false;lesson.forcedStep=null;
            lesson.checked=false;lesson.passed=false;lesson.lessonWriting.draft="";
            lesson.lessonWriting.hadFeedback=false;lesson.lessonPracticeAgain=false;
            resetTrainingStep(entry);studyQueue.consumeNewStep();
        }
        rememberRecent(id);saveTrainingSession();
        navigation.screen=Screen.TRAIN;render();
    }
    private AdaptiveLearningLogic.Recommendation selectRecommendation(LocalStore.Entry entry){
        return LessonPlans.recommend(dataLifetime.store(),lesson,entry,System.currentTimeMillis());
    }
    private List<StudyPlanLogic.Step> studySteps(LocalStore.Entry entry){
        return LessonPlans.steps(entry,lesson,reviewSession);
    }
    private void resetTrainingStep(LocalStore.Entry entry){LessonPlans.reset(dataLifetime.store(),lesson,reviewSession,entry);}
    private int bookForEntry(LocalStore.Entry e){
        if(e==null||e.reference==null)return -1;
        for(int n=1;n<=66;n++)if(e.reference.startsWith(Bible.NAMES[n-1]+" "))return n;
        return -1;
    }
    private GroupLearningLogic.Candidate nextGroup(LocalStore.Entry e){
        return nextGroup(e,dataLifetime.store().collectionProgress());
    }
    private GroupLearningLogic.Candidate nextGroup(LocalStore.Entry e,CollectionProgressSnapshot snapshot){
        List<String> refs=new ArrayList<>();for(Bible.Verse line:snapshot.verses(e.id))refs.add(line.ref);
        return GroupLearningLogic.next(refs,snapshot.groupProgress(e.id),LessonPlans.isSermon(e));
    }
    private GroupLearningLogic.Candidate pendingAfterCurrent(LocalStore.Entry e,int current){
        if(current<0||current>=e.lines().size())return null;
        List<String> refs=new ArrayList<>();for(Bible.Verse v:e.lines())refs.add(v.ref);
        return GroupLearningLogic.afterVerse(refs,dataLifetime.store().collectionProgress().groupProgress(e.id),
            current,LessonPlans.isSermon(e));
    }
    private void openGroup(GroupLearningLogic.Candidate group){
        prepareGroup(group);
        saveTrainingSession();
        render();
    }
    private void prepareGroup(GroupLearningLogic.Candidate group){
        LessonPlans.prepareGroup(dataLifetime.store(),lesson,reviewSession,group);
        navigation.screen=Screen.TRAIN;
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
        LocalStore.Entry e=dataLifetime.store().entry(lesson.selectedEntry);
        if(e==null){navigation.screen=Screen.HOME;render();return;}
        List<StudyPlanLogic.Step> steps=studySteps(e);
        if(steps.isEmpty()){
            LocalStore.UnifiedCollection u=dataLifetime.store().unifiedForGroup(e.groupId);
            navigation.screen=u==null?Screen.HOME:Screen.COLLECTION_DETAIL;
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
                if(root!=parts.root()||navigation.screen!=Screen.TRAIN)return;
                if(lesson.groupMode)lesson.groupIntroDone=true;
                else dataLifetime.store().finishExercise(e.id,lesson.activeStep.start,1);
                lesson.lessonVariant=0;lesson.lessonChunk=0;lesson.lessonWriting.variant=0;resetLessonTiles();lesson.mask=0;
                saveTrainingSession();render();
            });
        } else if(phase==LessonCurriculumLogic.Phase.BUILD){
            lesson.trainingMode=TrainingMode.ORDER;
            assemblyView.reference(content,lessonFullReference(e));
            boolean complete=renderLessonBuild(e);
            mainAction=ui.button("Продолжить",true,()->{
                if(root==parts.root()&&navigation.screen==Screen.TRAIN&&lesson.lessonAssembly.picked()>=assemblyView.required())
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
            dataLifetime.store().masteryCount(entry.id));
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
                if(root!=completionRoot||navigation.screen!=Screen.TRAIN)return;
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
            pending,nextVerse,e.lines().size(),studyQueue.executing()&&studyQueue.cursor()>=0,
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
            ReviewLogic.words(VerseText.join(passage)).size());
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
        return new LessonStageFlow.State(lesson.studyStage,lesson.groupMode?0:dataLifetime.store().exerciseDone(e.id,lesson.activeStep.start),
            reviewSession.active(),lesson.groupMode,studyQueue.executing(),lesson.lessonPracticeAgain,lesson.groupIntroDone,lesson.groupBuildDone);
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
            ReviewLogic.words(VerseText.join(e.lines().subList(lesson.activeStep.start,lesson.activeStep.end))).size());
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
            ReviewLogic.words(VerseText.join(e.lines().subList(lesson.activeStep.start,lesson.activeStep.end))).size());
        int chunkCount=lessonChunks(source,kinds.get(Math.min(lesson.lessonVariant,kinds.size()-1))).size();
        LessonStageFlow.BuildTransition next=LessonStageFlow.advanceBuild(
            lesson.lessonVariant,lesson.lessonChunk,kinds.size(),chunkCount,lesson.groupMode);
        if(next.persistedCompletion()>0)
            dataLifetime.store().finishExercise(e.id,lesson.activeStep.start,next.persistedCompletion());
        if(next.groupCompleted())lesson.groupBuildDone=true;
        lesson.lessonVariant=next.nextVariant();lesson.lessonChunk=next.nextChunk();
        if(next.beginWriting())lesson.lessonWriting.variant=0;
        resetLessonTiles();
        if(next.beginWriting()){lesson.lessonWriting.draft="";lesson.lastAccuracy=-1;lesson.lessonWriting.hadFeedback=false;}
        saveTrainingSession();render();
    }
    private List<LessonCurriculumLogic.Variant> writingVariants(LocalStore.Entry e){
        return writingVariants(VerseText.join(e.lines().subList(lesson.activeStep.start,lesson.activeStep.end)));
    }
    private List<LessonCurriculumLogic.Variant> writingVariants(String passage){
        return LessonStageFlow.writingVariants(passage,reviewSession.active(),lesson.lessonPracticeAgain,lesson.lessonWriting.practiceWeakFragment);
    }
    private void renderLessonWriting(){
        List<Bible.Verse> lines=lesson.activeEntry.lines().subList(lesson.activeStep.start,lesson.activeStep.end);
        String source=VerseText.join(lines);
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

    private final LessonWritingWorker<LessonWritingSubmission.Request> writingWorker=new LessonWritingWorker<>(
        this::applyWritingAssessment,error->{
            android.util.Log.e("Learning","Unable to check written answer",error);
            toast("Не удалось проверить ответ. Попробуй ещё раз.");
        });

    private void cancelWritingCheck(){writingWorker.cancel();}

    private void validateLessonWriting(LocalStore.Entry e){
        if(writingWorker.busy()||navigation.screen!=Screen.TRAIN||backupInProgress||resetInProgress||
            lesson.answerReview!=null||(lesson.checked&&lesson.passed))return;
        LessonWritingSubmission.Request check=LessonWritingSubmission.capture(e,lesson,reviewSession,
            this::lessonMissingPositions,writingView.expectedGaps());
        writingWorker.submit(check,check.input());
    }

    private void applyWritingAssessment(LessonWritingSubmission.Request check,WritingAssessment.Result result){
        LocalStore.Entry e=check.entry();
        if(isDestroyed()||isFinishing()||backupInProgress||resetInProgress||navigation.screen!=Screen.TRAIN||
            !check.matches(lesson))return;
        LessonWritingFlow.Transition transition;
        try{
            transition=LessonWritingSubmission.persist(dataLifetime.store(),lesson,reviewSession,check,result,System.currentTimeMillis());
            if(transition==null){toast("Материал изменился. Открой повторение заново.");return;}
        }catch(RuntimeException error){
            showRecallSaveError(error);
            return;
        }
        AnswerReview feedback=result.exact()?null:new AnswerReview(check.input().expected(),check.input().actual(),result.feedback());
        switch(transition.outcome()){
            case FEEDBACK:
                openAnswerReview(feedback);
                return;
            case FRAGMENT_PRACTICED:
                int fullVariant=LessonCurriculumLogic.variants(
                    ReviewLogic.words(VerseText.join(e.lines().subList(lesson.activeStep.start,lesson.activeStep.end))).size()).size()-1;
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
    private void ensureActiveUnified(){activeCollection.ensure(dataLifetime.store());}
    private LocalStore.UnifiedCollection activeUnified(){return activeCollection.get(dataLifetime.store());}
    private void activateUnified(long id){activeCollection.select(dataLifetime.store(),id);}
    private interface UnifiedCreated {void accept(LocalStore.UnifiedCollection collection);}
    private void newUnifiedCollection(UnifiedCreated after){
        collectionDialogs.create(name->{
            boolean firstCollection=dataLifetime.store().unifiedCollections().isEmpty();
            long id=dataLifetime.store().createUnifiedCollection(name);
            if(firstCollection)activateUnified(id);
            LocalStore.UnifiedCollection collection=dataLifetime.store().unifiedCollection(id);
            return ()->{
                if(after!=null)after.accept(collection);
                else{openUnifiedId=id;navigation.screen=Screen.COLLECTION_DETAIL;render();}
            };
        });
    }
    private void showUnifiedSelector(){
        collectionDialogs.select(dataLifetime.store().unifiedCollections(),activeCollection.id(),id->{activateUnified(id);render();});
    }
    private void openCollectionToAdd(LocalStore.UnifiedCollection u){
        openUnifiedId=u.id;selectedGroup=u.groupId;selectedDeck=u.deckId;
        navigation.screen=Screen.COLLECTION_DETAIL;render();
    }
    private void startActiveStudy(){
        LocalStore.UnifiedCollection u=activeUnified();if(u==null){newUnifiedCollection(null);return;}
        if(bible.isReady())dataLifetime.store().syncUnifiedCollection(u.id,bible);
        if(dataLifetime.store().entries(u.groupId).isEmpty()){openCollectionToAdd(u);return;}
        selectedGroup=u.groupId;startStudyCollection(u.groupId);
    }
    private void startActiveCards(){
        LocalStore.UnifiedCollection u=activeUnified();if(u==null)return;
        if(bible.isReady())dataLifetime.store().syncUnifiedCollection(u.id,bible);
        selectedDeck=u.deckId;cardFromUnified=true;
        if(eligibleSessionCards().isEmpty()){
            if(!bible.isReady()&&!dataLifetime.store().entries(u.groupId).isEmpty()){
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
        List<LocalStore.UnifiedCollection> collections=dataLifetime.store().unifiedCollections();
        CollectionProgressSnapshot snapshot=collections.isEmpty()?null:dataLifetime.store().collectionProgress();
        collectionViews.list(content,collections,snapshot,activeCollection.id(),id->{
            openUnifiedId=id;navigation.screen=Screen.COLLECTION_DETAIL;render();
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
        dataLifetime.resetProgress(collectionIo,error->{
            if(isDestroyed())return;
            resetting.dismiss();
            if(error==null){
                    studyQueue.reset();
                    cardStudyQueue.clear();cardStudyCursor=-1;cardStudyDeckId=-1;
                    reviewSession.clear();
                    knowledgeCheck.clear();
                    lesson.savedAssemblyFingerprint="";assemblyLayout.clear();
                    lesson.lessonWriting.draft="";lesson.checked=false;lesson.passed=false;lesson.answerReview=null;
                    lesson.lessonWriting.hadFeedback=false;lesson.lessonPracticeAgain=false;
                    tabCache.invalidate();navigation.clear();
                    resetInProgress=false;
                    navigation.screen=Screen.SETTINGS;render();
                    toast("Прогресс изучения сброшен");
            }else{
                    // An interrupted operation is still pending. Do not allow a
                    // new lesson to be saved until it has been recovered.
                    new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                        .setTitle("Не удалось завершить сброс")
                        .setMessage("Закрой приложение и открой снова для завершения операции.")
                        .setCancelable(false).setPositiveButton("Закрыть",(d,w)->finish()).show();
            }
        });
    }
    static String countedRussian(int count,String one,String few,String many){
        return CollectionViews.countedRussian(count,one,few,many);
    }
    private void showUnifiedCollectionSettings(LocalStore.UnifiedCollection u){
        collectionViews.settings(content,()->renameUnified(u),()->{
            if(dataLifetime.store().entries(u.groupId).size()<2){
                toast("Для изменения порядка нужны хотя бы два отрывка");return;
            }
            selectedGroup=u.groupId;openUnifiedId=u.id;
            navigation.screen=Screen.REORDER_STUDY;render();
        },()->clearUnified(u),()->deleteUnified(u));
    }
    private void clearUnified(LocalStore.UnifiedCollection u){
        if(u.entries==0&&u.cards==0){toast("Коллекция уже пуста");return;}
        collectionDialogs.confirmClear(()->{
            dataLifetime.store().clearUnifiedCollection(u.id);
            knowledgeCheck.clearCollection(u.id,true);
            if(studyQueue.group()==u.groupId)clearCollectionQueue();
            render();toast("Коллекция очищена");
        });
    }
    private void renameUnified(LocalStore.UnifiedCollection u){
        collectionDialogs.rename(u.title,name->dataLifetime.store().renameUnifiedCollection(u.id,name),this::render);
    }
    private void deleteUnified(LocalStore.UnifiedCollection u){
        collectionDialogs.confirmDelete(()->{
            Screen origin=navigation.screen;
            runCollectionMutation(collectionStore->collectionStore.deleteUnifiedCollection(u.id),success->{
                if(!success)return;
                if(studyQueue.group()==u.groupId)clearCollectionQueue();
                knowledgeCheck.clearCollection(u.id,false);
                ensureActiveUnified();
                if(navigation.screen==origin&&(origin!=Screen.COLLECTION_DETAIL||openUnifiedId==u.id)){
                    navigation.screen=Screen.COLLECTIONS;render();
                }
            },()->{
                // Rotation can retire the owner before its deletion callback reaches the looper.
                // Compare persisted IDs so we preserve a newer selection made by another screen.
                SharedPreferences queue=studyQueue.preferences();
                if(queue.getLong("group",-1)==u.groupId)queue.edit().clear().apply();
                activeCollection.clearSavedIfSelected(u.id);
                knowledgeCheck.clearCollection(u.id,false);
            });
        });
    }
    private void renderUnifiedCollectionDetail(){
        LocalStore.UnifiedCollection u=dataLifetime.store().unifiedCollection(openUnifiedId);
        if(u==null){navigation.screen=Screen.COLLECTIONS;render();return;}
        selectedGroup=u.groupId;selectedDeck=u.deckId;
        collectionViews.heading(content,u.title);
        showUnifiedCollectionSettings(u);
        final LinearLayout target=content;
        int restoreScroll=navigation.getOrDefault(Screen.COLLECTION_DETAIL,0);
        collectionDetail.load(u.groupId,bible,target,()->!isDestroyed()&&!isFinishing()&&!backupInProgress
                &&navigation.screen==Screen.COLLECTION_DETAIL&&openUnifiedId==u.id&&content==target,
            restoreScroll,detail->populateCollectionDetail(u,detail),this::entryCard,()->currentScroller,
            ()->toast("Не удалось загрузить коллекцию. Открой её ещё раз."));
    }
    private void populateCollectionDetail(LocalStore.UnifiedCollection u,CollectionDetailLoader.Detail detail){
        CollectionProgressSnapshot snapshot=detail.snapshot();
        SortedMap<Integer,SortedMap<Integer,Long>> books=detail.books();
        collectionViews.progress(content,u.groupId,snapshot,getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE)
            .getString("run_history_"+u.id,"[]"));
        for(Map.Entry<Integer,SortedMap<Integer,Long>> book:books.entrySet()){
            String name=Bible.NAMES[book.getKey()-1];
            collectionViews.bookRow(content,name,countedRussian(book.getValue().size(),"глава","главы","глав"),()->{
                openedBookName=name;
                openedCollectionBook=book.getValue();
                openedBookProgress=snapshot;
                navigation.remove(Screen.COLLECTION_BOOK);
                navigation.screen=Screen.COLLECTION_BOOK;render();
            });
        }
        if(detail.entries().isEmpty()&&books.isEmpty())ui.helper(content,"Добавь первый стих или отрывок.");
    }

    private void renderCollectionBook(){
        title(openedBookName,null);
        for(Map.Entry<Integer,Long> chapter:openedCollectionBook.entrySet()){
            long id=chapter.getValue();
            collectionViews.bookRow(content,"Глава "+chapter.getKey(),countedRussian(openedBookProgress.verseCount(id),
                "стих","стиха","стихов"),()->{
                openedCollectionChapter=chapter.getKey();
                navigation.remove(Screen.COLLECTION_CHAPTER);
                navigation.screen=Screen.COLLECTION_CHAPTER;render();
            });
        }
    }

    private void renderCollectionChapter(){
        long id=openedCollectionBook.get(openedCollectionChapter);
        title(openedBookName+" "+openedCollectionChapter,null);
        ui.action(content,"Изучать главу",true,()->openTraining(id));
        ui.action(content,"Добавить стих / отрывок после главы",false,()->{
            clearInsertionContext();
            insertion.appendStudy(id);
            openBiblePicker(BibleTarget.STUDY_GROUP,selectedGroup,Screen.COLLECTION_DETAIL);
        });
        collectionViews.chapterVerses(content,openedBookProgress,id);
    }

    private void addUnifiedCollectionFooter(){
        if(navigation.screen==Screen.COLLECTIONS||navigation.screen==Screen.CARDS){
            collectionViews.listFooter(root,()->newUnifiedCollection(null));
        }else if(navigation.screen==Screen.COLLECTION_DETAIL){
            LocalStore.UnifiedCollection u=dataLifetime.store().unifiedCollection(openUnifiedId);
            if(u==null)return;
            collectionViews.detailFooter(root,()->{
                clearInsertionContext();
                openBiblePicker(BibleTarget.STUDY_GROUP,u.groupId,Screen.COLLECTION_DETAIL);
            },()->{customTargetGroupId=u.groupId;navigation.screen=Screen.CUSTOM;render();});
        }
    }

    private void startActiveRun(){
        LocalStore.UnifiedCollection u=activeUnified();if(u==null)return;
        KnowledgeCheckSession.Availability available=knowledgeCheck.prepare(u.id,
            KnowledgeCheckMaterial.questions(dataLifetime.store().collectionProgress(),u.groupId));
        if(available==KnowledgeCheckSession.Availability.EMPTY){
            if(dataLifetime.store().entries(u.groupId).isEmpty()){openCollectionToAdd(u);return;}
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
        navigation.screen=Screen.RUN;render();
    }

    private void renderWritingRun(){
        KnowledgeCheckSession.Task task=knowledgeCheck.task();
        if(task==null){navigation.screen=Screen.HOME;render();return;}
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        KnowledgeCheckView.Layout layout=knowledgeCheckView.task(this,task,this::onBackPressed,
            knowledgeCheck::edit,()->{
                if(!knowledgeCheck.check()){toast("Введи стих или выбери «Пропустить»");return;}
                render();
            },()->{
                if(navigation.screen!=Screen.RUN)return;
                KnowledgeCheckSession.Advance next=knowledgeCheck.advance(System.currentTimeMillis());
                if(next==KnowledgeCheckSession.Advance.SAVE_FAILED)toast("Не удалось сохранить результат проверки");
                if(next!=KnowledgeCheckSession.Advance.CONTINUE)navigation.screen=Screen.RUN_SUMMARY;
                render();
            });
        root=layout.root();content=layout.body();
        setContentView(root);navigation.lastRenderedScreen=navigation.screen;
    }

    private void renderRunSummary(){
        title("Проверка памяти завершена",null);
        knowledgeCheckView.summary(content,knowledgeCheck.result(),()->{navigation.screen=Screen.HOME;render();});
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
        if(!dataLifetime.ownsData()||backupInProgress||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==EXPORT)runBackupTask(uri,false);
        else if(request==IMPORT)settingsView.confirmImport(()->runBackupTask(uri,true));
    }
    private void runBackupTask(Uri uri,boolean restore){
        if(backupInProgress||resetInProgress)return;
        BackupCoordinator.request(this,restore?BackupCoordinator.Operation.IMPORT:
            BackupCoordinator.Operation.EXPORT,uri);
    }

    private void beginBackupWait(BackupCoordinator.Operation operation){
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
        if(!dataLifetime.ownsData())return;
        // Flush the same drafts as onPause before retiring this generation of views.
        if(navigation.screen==Screen.TRAIN)saveTrainingSession();
        if(navigation.screen==Screen.CARD_SESSION)flashcards.save(selectedDeck);
        if(navigation.screen==Screen.RUN)knowledgeCheck.save();
        rememberHomeScroll();
        home.pause();
        tabCache.cancelWarmup();
        bibleSearch.cancel();
        // Earlier writes may post a queue/preference update back to the main looper.
        // The FIFO sentinel lets those callbacks finish before destruction.
        collectionIo.afterPending(()->{if(!isFinishing()&&!isDestroyed())recreate();});
    }

    private void finishBackup(BackupCoordinator.Operation operation,Exception failure){
        if(isFinishing()||isDestroyed())return;
        if(backupDialog!=null){backupDialog.dismiss();backupDialog=null;}
        if(failure!=null){
            showBackupFailure(failure);
            return;
        }
        backupInProgress=false;
        if(operation!=BackupCoordinator.Operation.EXPORT)startupState=null;
        openData();
        if(dataLifetime.ownsData()){
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
        BackupCoordinator.detach(backupOwner);
        bibleStartup.close();
        if(backupDialog!=null)backupDialog.dismiss();
        tabCache.cancelWarmup();
        tabTransition.finish();
        collectionDetail.close();
        dataLifetime.closeCollectionIo(collectionIo);
        modeInfo.dismiss(false);
        bottomNavigation.settleBibleIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleBibleIcon(tab.page);
        bottomNavigation.settleHomeIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleHomeIcon(tab.page);
        bottomNavigation.settleCollectionsIcon(root);
        for(TabCache.ReadyTab tab:tabCache.pages())bottomNavigation.settleCollectionsIcon(tab.page);
        tabCache.close();
        bibleSearch.close();
        home.pause();super.onDestroy();dataLifetime.closeForeground();
    }
}
