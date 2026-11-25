package org.biblememory.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.BlurMaskFilter;
import android.text.style.MaskFilterSpan;
import android.text.style.ForegroundColorSpan;
import android.text.SpannableString;
import android.text.style.ReplacementSpan;
import android.text.style.StrikethroughSpan;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.LinearGradient;
import android.graphics.Shader;
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

import static org.biblememory.app.ScreenUi.*;

/** Bright, entirely native Android UI with zero runtime dependencies. */
public final class MainActivity extends Activity {
    private final ScreenUi ui = new ScreenUi(this);
    private final ScreenMenus menus = new ScreenMenus(ui);
    private final ModeInfoPopover modeInfo = new ModeInfoPopover(ui);
    // The warm landscape-based interface is shared by every screen, not just the home hero.
    private static final int EXPORT=10,IMPORT=11;
    private enum Screen {HOME,BOOKS,CHAPTERS,VERSES,CHAPTER,ENTRIES,CUSTOM,TRAIN,DUE,CARDS,CARD_DECK,CARD_PICK,BIBLE_PICKER,PICKER_BOOKS,PICKER_CHAPTERS,PICKER_VERSES,PICKER_TEXT,CARD_SESSION,REORDER_STUDY,REORDER_CARDS,COLLECTIONS,COLLECTION_DETAIL,COLLECTION_BOOK,COLLECTION_CHAPTER,SETTINGS,RUN,RUN_SUMMARY}
    private Screen screen=Screen.HOME;
    private static final String UNIFIED_PREFS="unified_library_ui";
    private long activeUnifiedId=-1,openUnifiedId=-1;
    private boolean cardFromUnified=false;
    private long reviewCollectionGroup=-1;
    private KnowledgeCheckSession knowledgeCheck;
    private final KnowledgeCheckView knowledgeCheckView = new KnowledgeCheckView(ui);

    private LocalStore store;
    private final Bible bible=Bible.shared();
    private java.util.concurrent.CompletableFuture<Void> bibleStartup;
    private volatile String bibleError="";
    private int selectedBook=40,selectedChapter=5, mask=0,stepCursor=0;
    private int focusVerse=-1;
    private boolean focusPickerSearchHit;
    private StudyPlanLogic.Stage studyStage=StudyPlanLogic.Stage.VERSE;
    private long selectedGroup=1,selectedEntry=0;
    private long customTargetGroupId=-1; // A text created via a collection menu belongs to that collection.
    private boolean checked=false,passed=false;
    private enum TrainingMode {READ, HIDE, ORDER, TYPE}
    private TrainingMode trainingMode=TrainingMode.READ;
    private boolean testAssisted=false;
    private ScrollView currentScroller;
    // The Home page is a single scroll surface; its expansion and scroll position survive navigation.
    private static final String HOME_UI_PREFS="home_ui";
    private boolean homeCollectionsExpanded=false;
    private int homeScrollY=0;
    private boolean homeScrollRestored=false;
    private Screen lastRenderedScreen=null;
    private ValueAnimator collectionAnimator;

    private String lastExpected="";
    private final LessonWritingState lessonWriting=new LessonWritingState();
    private int homeIndex=0;
    private static final int[][] HOME_REFS={{19,118,11},{40,5,14},{43,14,6},{45,12,21},{50,4,13},{40,6,33}};
    private int lastAccuracy=-1;
    private LinearLayout content,root;
    private final Handler homeHandler=new Handler(Looper.getMainLooper());
    private final Runnable homeTicker=()->{refreshHomeIndex(true);scheduleHomeRefresh();};
    private LinearLayout activeTextArea;
    private TextView activePassage;
    private static final String SESSION_PREFS="study_session";
    private LocalStore.Entry activeEntry;
    private StudyPlanLogic.Step activeStep;
    private boolean reviewMode=false,groupMode=false;
    private int returnVerseCursor=0;
    private StudyPlanLogic.Step forcedStep;
    private LocalStore.DueItem currentReview;
    private String reviewMaterialFingerprint="";
    private int blurSeed=new Random().nextInt();
    // Three-phase fullscreen lesson: stable footer, adaptive word-tile exercises.
    private int lessonVariant=0,lessonChunk=0;
    private long lessonSeed=new Random().nextLong();
    private final LessonAssemblyState lessonAssembly=new LessonAssemblyState();
    private boolean lessonPracticeAgain=false;
    private Runnable revealCleanup=()->{};
    private final List<LocalStore.DueItem> reviewQueue=new ArrayList<>();
    private int reviewPointer=0;
    private TextView lessonBuildAction;
    private final List<BuildSlot> lessonBuildSlots=new ArrayList<>();
    private static final int BUILD_INK=0xFF553722;
    private final List<TextView> lessonBuildTiles=new ArrayList<>();
    private int lessonBuildRequired=0;
    // Cache one active passage's variant-specific fragments. Esther 8:9 is long:
    // recomputing all three layouts on each tile tap stalls the UI thread.
    private final EnumMap<LessonCurriculumLogic.Variant,List<String[]>> lessonChunkCache=
        new EnumMap<>(LessonCurriculumLogic.Variant.class);
    private String lessonChunkCacheKey="";
    private String savedAssemblyFingerprint="";
    // Presentation only: one adaptive strip, independent of text/answer Views.
    private LessonSegmentedBar lessonHotbarStrip;

    private int weakVerse=-1,weakTransitionFrom=-1;
    private int readerVerseNumber=1;
    private boolean chapterFromVerseGrid=false;
    // One selection model shared by tapping verses and the chapter/verse range picker.
    private boolean selectingPassage=false; // legacy selection mode retained only for old saved preferences
    private int rangeStartChapter=-1,rangeStartVerse=-1,rangeEndChapter=-1,rangeEndVerse=-1;
    private boolean groupBuildDone=false;
    private boolean groupIntroDone=false;
    private final List<EditText> inlineFields=new ArrayList<>();
    private final List<String> inlineExpected=new ArrayList<>();


    private BibleSearchSession bibleSearch;
    private final BibleSearchView bibleSearchView = new BibleSearchView(ui,this::openSearchHit);

    private final EnumMap<Screen,Integer> savedScrollY=new EnumMap<>(Screen.class);
    private final List<Long> collectionQueue=new ArrayList<>();
    private int collectionQueueCursor=-1;
    private long collectionQueueGroup=-1;
    private boolean executingCollectionQueue=false;
    private boolean collectionQueueNewStep=false;
    private long insertAfterEntryId=-1,insertAfterCardId=-1;
    // Each subsequent choice goes after the last inserted item, not the original anchor.
    private final List<Long> studyInsertionTrail=new ArrayList<>();
    private final List<Long> cardInsertionTrail=new ArrayList<>();


    private static final class ReadyTab {
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

    private final EnumMap<Screen, ReadyTab> readyTabs =
            new EnumMap<>(Screen.class);

    private final Handler tabWarmHandler =
            new Handler(Looper.getMainLooper());

    private int tabWarmGeneration = 0;
    private View tabSwitchOldNav;

    private boolean isMainTab(Screen s) {
        return s == Screen.HOME
                || s == Screen.BOOKS
                || s == Screen.COLLECTIONS
                || s == Screen.SETTINGS;
    }

    private void prepareOtherTabs() {
        if(isBibleSearchScreen()&&bibleSearch.active())return;
        final int generation = ++tabWarmGeneration;

        if (!isMainTab(screen) || !bible.isReady()) return;

        final Screen active = screen;

        final Screen[] order = {
                Screen.HOME,
                Screen.BOOKS,
                Screen.COLLECTIONS,
                Screen.SETTINGS
        };

        int delay = 75;

        for (Screen destination : order) {
            if (destination == active
                    || readyTabs.containsKey(destination)) {
                continue;
            }

            tabWarmHandler.postDelayed(() -> {
                if (generation != tabWarmGeneration
                        || !isMainTab(screen)
                        || tabSwitchInProgress) {
                    return;
                }

                prepareTab(destination);
            }, delay);

            delay += 85;
        }
    }

    private void prepareTab(Screen destination) {
        if (readyTabs.containsKey(destination)) return;

        Screen previousScreen = screen;
        Screen previousRendered = lastRenderedScreen;

        LinearLayout previousRoot = root;
        LinearLayout previousBody = content;
        ScrollView previousScroller = currentScroller;

        int previousNav = lastNavIndex;

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

            readyTabs.put(
                    destination,
                    new ReadyTab(
                            root, content, currentScroller, homeIndex
                    )
            );

        } finally {
            screen = previousScreen;
            lastRenderedScreen = previousRendered;

            root = previousRoot;
            content = previousBody;
            currentScroller = previousScroller;

            lastNavIndex = previousNav;
        }
    }

    private void animatePreparedTabIndicator(
            LinearLayout page,
            int from,
            int to) {

        View indicator =
                page.findViewWithTag("sv-nav-indicator");

        if (indicator == null
                || !(indicator.getParent() instanceof FrameLayout)) {
            return;
        }

        FrameLayout bar = (FrameLayout) indicator.getParent();

        bar.post(() -> {
            if (indicator.getParent() != bar) return;

            int slot = (bar.getWidth() - ui.dp(14)) / 4;
            if (slot <= 0) return;

            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams)
                            indicator.getLayoutParams();

            params.width = slot;
            params.height = ui.dp(57);
            params.leftMargin = ui.dp(7);
            params.topMargin = ui.dp(7);

            indicator.setLayoutParams(params);
            indicator.animate().cancel();

            indicator.setTranslationX(slot * from);

            indicator.animate()
                    .translationX(slot * to)
                    .setDuration(225L)
                    .setInterpolator(
                            new DecelerateInterpolator(1.4f)
                    )
                    .start();
        });
    }


    private ValueAnimator tabSwitchAnimator;
    private LinearLayout tabSwitchLeaving,tabSwitchEntering;
    private boolean tabSwitchInProgress=false;
    private final String COLLECTION_PREFS="collection_study_queue";
    private boolean resetInProgress=false;
    private boolean backupInProgress=false;
    private boolean ownsData,resumed;
    private Bundle startupState;
    private Bundle lessonWaitingForBible;
    private long lessonSourceEntry=-1,lessonSourceGroup=-1;
    private String lessonSessionMaterial="";
    private String lessonSourceMaterial="";
    private String cardStudyMaterial="";
    private String dataGeneration="";
    private AlertDialog backupDialog;
    private float swipeStartX,swipeStartY;
    private boolean swipeIntercepted=false;
    private int lastNavIndex=0;
    private long selectedDeck=-1;
    private FlashcardSession flashcards;
    private enum BibleTarget { STUDY_GROUP, FLASHCARD_DECK }
    private BibleTarget pickerTarget=BibleTarget.FLASHCARD_DECK;
    private long pickerTargetId=-1;
    private boolean pickerRange=false;
    private boolean pickerHasAnchor=false, pickerChooseEnd=false;
    private Screen pickerBackScreen=Screen.CARD_DECK;
    private final PassageSelection passageSelection=new PassageSelection();
    private final List<View> pickerQuickButtons=new ArrayList<>();
    private TextView pickerSelectionLabel,pickerSaveButton;
    private LinearLayout pickerSelectionPanel;
    private int pickerRestoredScroll=-1;
    private int pickerQuickBook=1,pickerQuickChapter=1,pickerQuickScroll=0,pickerVerseNumber=1;
    private int readerBookBeforePicker=1,readerChapterBeforePicker=1;

    // A card collection is studied as separate saved passages, never as one concatenated text.
    private final List<Long> cardStudyQueue=new ArrayList<>();
    private int cardStudyCursor=-1;
    private long cardStudyDeckId=-1;
    // Preserve the mask of the current unfinished GAPS exercise across renders and restarts.
    private String rotationExerciseKey="",rotationMask="";
    private long rotationExerciseSeed=0L;






    private final java.util.concurrent.ExecutorService collectionExecutor=
        java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"CollectionProgress"));
    private final Handler collectionHandler=new Handler(android.os.Looper.getMainLooper());
    private java.util.concurrent.Future<?> collectionTask;
    private LocalStore collectionStore;
    private static final int BULK_INK=0xFF426747;
    private static final int BULK_SELECTED=0xFFE3EDD9;
    private final Set<String> pendingBooks=new HashSet<>();
    private final Map<Integer,BookToggle> pickerBookActions=new HashMap<>();
    private final SortedSet<Integer> selectedPickerChapters=new TreeSet<>();
    private final Map<Integer,TextView> pickerChapterButtons=new HashMap<>();
    private LinearLayout pickerChapterFooter;
    private TextView pickerChapterAdd, pickerChapterCancel;
    private boolean savingPickerChapters;
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
        activeUnifiedId=getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE).getLong("active",-1);
        ensureActiveUnified();
        restoreCollectionQueue();
        android.content.SharedPreferences homeUi=getSharedPreferences(HOME_UI_PREFS,MODE_PRIVATE);
        homeCollectionsExpanded=homeUi.getBoolean("collections_expanded",false);
        homeScrollY=Math.max(0,homeUi.getInt("scroll_y",0));
        refreshHomeIndex(false);
        restorePickerState(savedInstanceState);
        Bundle lesson=savedInstanceState==null?null:savedInstanceState.getBundle("active_lesson");
        if(lesson!=null&&lesson.containsKey("review_group")&&!bible.isReady())
            lessonWaitingForBible=lesson;
        else restoreActiveLesson(lesson);
        render();
        // The worker owns collectionStore until its queued close in onDestroy.
        if(resumed)scheduleHomeRefresh();
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
        out.putBoolean("picker_active",passageSelection.active);
        out.putBoolean("picker_choose_end",pickerChooseEnd);
        out.putIntArray("picker_quick",new int[]{pickerQuickBook,pickerQuickChapter,pickerQuickScroll,pickerVerseNumber});
        out.putIntArray("picker_bounds",new int[]{passageSelection.book,passageSelection.startChapter,
            passageSelection.startVerse,passageSelection.endChapter,passageSelection.endVerse});
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
            passageSelection.collectionId=pickerTargetId;passageSelection.chapter=selectedChapter;
            int[] b=state.getIntArray("picker_bounds");
            if(b!=null&&b.length==5){passageSelection.book=b[0];passageSelection.startChapter=b[1];
                passageSelection.startVerse=b[2];passageSelection.endChapter=b[3];passageSelection.endVerse=b[4];}
            passageSelection.active=state.getBoolean("picker_active");
            pickerChooseEnd=state.getBoolean("picker_choose_end");
            int[] quick=state.getIntArray("picker_quick");
            if(quick!=null&&quick.length==4){pickerQuickBook=quick[0];pickerQuickChapter=quick[1];
                pickerQuickScroll=quick[2];pickerVerseNumber=quick[3];}
            pickerRestoredScroll=state.getInt("picker_scroll",0);
            insertAfterEntryId=state.getLong("picker_insert_entry",-1);insertAfterCardId=state.getLong("picker_insert_card",-1);
        }catch(IllegalArgumentException ignored){screen=Screen.HOME;passageSelection.cancel();}
    }
    @Override protected void onResume() {
        super.onResume();
        resumed=true;
        if(!ownsData||backupInProgress)return;
        settleBibleIcons();
        settleHomeIcons();
        settleCollectionsIcons();
        if(store!=null && root!=null){refreshHomeIndex(true);scheduleHomeRefresh();}
    }
    @Override protected void onPause() {
        cancelWritingCheck();
        resumed=false;
        if(!ownsData||backupInProgress){super.onPause();return;}
        modeInfo.dismiss(false);
        settleBibleIcons();
        settleHomeIcons();
        settleCollectionsIcons();
        revealCleanup.run();
        if(screen==Screen.TRAIN)saveTrainingSession();
        if(screen==Screen.CARD_SESSION)flashcards.save(selectedDeck);
        if(screen==Screen.RUN)knowledgeCheck.save();
        rememberHomeScroll();
        homeHandler.removeCallbacks(homeTicker);
        super.onPause();
    }
    private void scheduleHomeRefresh() {
        homeHandler.removeCallbacks(homeTicker);
        if(store==null)return;
        long last=getSharedPreferences("home_verse",MODE_PRIVATE)
            .getLong("last_switch",System.currentTimeMillis());
        homeHandler.postDelayed(homeTicker,HomeVerseLogic.nextDelay(last,System.currentTimeMillis())+80L);
    }
    private void refreshHomeIndex(boolean rerender) {
        android.content.SharedPreferences prefs=getSharedPreferences("home_verse",MODE_PRIVATE);
        long previous=prefs.getLong("last_switch",0L),now=System.currentTimeMillis();
        int old=homeIndex;
        homeIndex=prefs.getInt("index",0);
        if(previous<=0 || previous>now) {
            // Also recover cleanly from an incorrectly adjusted system clock.
            prefs.edit().putLong("last_switch",now).putInt("index",homeIndex).apply();
        } else {
            long steps=HomeVerseLogic.elapsedIntervals(previous,now,HomeVerseLogic.ROTATE_MS);
            if(steps>0) {
                homeIndex=HomeVerseLogic.advance(homeIndex,HOME_REFS.length,steps);
                prefs.edit().putLong("last_switch",previous+steps*HomeVerseLogic.ROTATE_MS)
                    .putInt("index",homeIndex).apply();
            }
        }
        if(rerender&&screen==Screen.HOME&&homeIndex!=old)render();
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
                passageSelection.active=false;pickerChooseEnd=false;
                selectedBook=pickerQuickBook;selectedChapter=pickerQuickChapter;
                pickerRestoredScroll=pickerQuickScroll;focusVerse=-1;
                screen=Screen.PICKER_TEXT;render();break;
            case PICKER_BOOKS:
                if(passageSelection.active){screen=Screen.BIBLE_PICKER;render();break;}
                clearInsertionContext();passageSelection.cancel();
                selectedBook=readerBookBeforePicker;selectedChapter=readerChapterBeforePicker;
                screen=pickerBackScreen;render();break;
            case PICKER_CHAPTERS:
                if(!selectedPickerChapters.isEmpty()){clearPickerChapterSelection();break;}
                screen=passageSelection.active?Screen.BIBLE_PICKER:Screen.PICKER_BOOKS;render();break;
            case PICKER_VERSES:screen=Screen.PICKER_CHAPTERS;render();break;
            case PICKER_TEXT:screen=Screen.PICKER_VERSES;render();break;
            case CARD_SESSION:flashcards.save(selectedDeck);screen=cardFromUnified?Screen.HOME:Screen.CARD_DECK;render();break;
            case CHAPTERS:screen=Screen.BOOKS;render();break;
            case VERSES:screen=Screen.CHAPTERS;render();break;
            case CHAPTER:screen=chapterFromVerseGrid?Screen.VERSES:Screen.CHAPTERS;render();break;
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
                answerReview=null;
                reviewMode=false;groupMode=false;forcedStep=null;currentReview=null;
                reviewQueue.clear();reviewPointer=0;
                // The lesson is an isolated activity: every exit goes to HOME.
                screen=Screen.HOME;render();break;
        }
    }
    /** Decorative scenery only; long reading passages remain on opaque ivory cards. */
    private void illustratedBanner(LinearLayout into,String heading,long scene,int height){
        FrameLayout art=new FrameLayout(this);
        art.setBackground(ui.outlined(CARD,22,BORDER));art.setClipToOutline(true);
        ImageView landscape=new ImageView(this);
        landscape.setImageResource(landscapeFor(scene));
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
    /** Six original bundled landscapes; no runtime network access or per-verse downloads. */
    private int landscapeFor(long key){
        switch((int)Math.floorMod(key,6L)){
            case 0:return R.drawable.hero_sunrise;
            case 1:return R.drawable.hero_lake;
            case 2:return R.drawable.hero_autumn;
            case 3:return R.drawable.hero_forest;
            case 4:return R.drawable.hero_evening;
            default:return R.drawable.hero_valley;
        }
    }
    /** Warm pine-toned card surface, visually coherent with the new landscape artwork. */
    private GradientDrawable homeSurface(){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFFFFFCF8,0xFFFFF5EF,0xFFFBE9DD});
        d.setCornerRadius(ui.dp(22));d.setStroke(ui.dp(1),HOME_BORDER);return d;
    }
    private static final int MAX_LANDSCAPE_THUMBNAIL_DP=96;
    // The six bundled landscape resources are shared only by thumbnails in this Activity.
    private final android.util.SparseArray<Bitmap> landscapeThumbnails=new android.util.SparseArray<>(6);

    /** A small decorative scenic preview; accessible names remain the collection labels. */
    private ImageView landscapeThumbnail(long index){
        ImageView thumb=new ImageView(this);
        int resource=landscapeFor(index);
        Bitmap bitmap=landscapeThumbnails.get(resource);
        if(bitmap==null){
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inScaled=false;
            options.inJustDecodeBounds=true;
            BitmapFactory.decodeResource(getResources(),resource,options);
            int side=Math.min(options.outWidth,options.outHeight);
            int minimum=ui.dp(MAX_LANDSCAPE_THUMBNAIL_DP);
            options.inSampleSize=1;
            // CENTER_CROP still receives enough source pixels for the largest thumbnail.
            while(side/(options.inSampleSize*2)>=minimum)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;
            bitmap=BitmapFactory.decodeResource(getResources(),resource,options);
            if(bitmap!=null)landscapeThumbnails.put(resource,bitmap);
        }
        if(bitmap!=null)thumb.setImageBitmap(bitmap);
        else thumb.setImageResource(resource);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(ui.shape(CARD_ALT,14));
        thumb.setClipToOutline(true);
        thumb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return thumb;
    }
    /** Restored verse-of-the-day hierarchy, over a light, readable scenic backdrop. */



    private void homeVerseHero(
            int scene,
            String reference,
            String verseText) {

        // Карточка имеет высоту по содержимому.
        FrameLayout hero = new FrameLayout(this);

        final android.graphics.drawable.Drawable landscape =
                getResources().getDrawable(
                        landscapeFor(scene),
                        getTheme()
                );

        // Сохраняем оригинальный светлый градиент.
        final GradientDrawable scrim =
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{
                                0xFAFFF5E8,
                                0xE8FFF3E5,
                                0x70FFF1DF,
                                0x10FFF1DF
                        }
                );

        // Рисуем пейзаж непосредственно в фоне.
        // Он больше НЕ влияет на высоту карточки.
        hero.setBackground(
                new android.graphics.drawable.Drawable() {

                    @Override
                    public void draw(Canvas canvas) {
                        android.graphics.Rect bounds = getBounds();

                        int iw = Math.max(
                                1, landscape.getIntrinsicWidth()
                        );

                        int ih = Math.max(
                                1, landscape.getIntrinsicHeight()
                        );

                        float scale = Math.max(
                                (float) bounds.width() / iw,
                                (float) bounds.height() / ih
                        );

                        int saved = canvas.save();

                        // Поведение как CENTER_CROP.
                        canvas.clipRect(bounds);
                        canvas.translate(
                                bounds.exactCenterX(),
                                bounds.exactCenterY()
                        );

                        canvas.scale(scale, scale);

                        landscape.setBounds(
                                -iw / 2,
                                -ih / 2,
                                iw - iw / 2,
                                ih - ih / 2
                        );

                        landscape.draw(canvas);

                        canvas.restoreToCount(saved);

                        // Градиент поверх изображения.
                        scrim.setBounds(bounds);
                        scrim.draw(canvas);
                    }

                    @Override
                    public void setAlpha(int alpha) {
                        landscape.setAlpha(alpha);
                    }

                    @Override
                    public void setColorFilter(
                            android.graphics.ColorFilter filter) {
                        landscape.setColorFilter(filter);
                    }

                    @Override
                    public int getOpacity() {
                        return android.graphics.PixelFormat.TRANSLUCENT;
                    }
                }
        );

        // Скругление всей карточки, включая изображение.
        hero.setOutlineProvider(
                new ViewOutlineProvider() {
                    @Override
                    public void getOutline(
                            View view,
                            android.graphics.Outline outline) {

                        outline.setRoundRect(
                                0,
                                0,
                                view.getWidth(),
                                view.getHeight(),
                                ui.dp(24)
                        );
                    }
                }
        );

        hero.setClipToOutline(true);
        hero.setElevation(ui.dp(3));

        // Содержимое определяет реальную высоту.
        LinearLayout body = ui.column();

        body.setGravity(
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );

        body.setPadding(
                ui.dp(22),
                ui.dp(14),
                ui.dp(22),
                ui.dp(18)
        );

        // Оригинальная плашка.
        TextView heading = ui.txt(
                "Стих дня",
                15,
                GREEN,
                true
        );

        heading.setGravity(Gravity.CENTER);

        heading.setPadding(
                ui.dp(19),
                ui.dp(8),
                ui.dp(19),
                ui.dp(8)
        );

        heading.setBackground(
                ui.shape(0xE9FFE4D3, 25)
        );

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(-2, -2);

        headingParams.gravity = Gravity.CENTER_HORIZONTAL;

        body.addView(heading, headingParams);

        // Текст стиха полностью сохраняется.
        TextView verse = ui.prose(
                "«" + verseText + "»",
                22
        );

        verse.setGravity(Gravity.CENTER);

        ui.add(body, verse, 12);

        // Ссылка на стих.
        TextView source = ui.displayText(reference, 17);

        source.setGravity(Gravity.CENTER);
        source.setTextColor(0xFF83503E);

        ui.add(body, source, 10);

        hero.addView(
                body,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        // Никакой фиксированной высоты и MATCH_PARENT по высоте.
        LinearLayout.LayoutParams heroParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        heroParams.topMargin = ui.dp(12);

        content.addView(hero, heroParams);
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
    /** Section boundary is visually centered, with measured whitespace only at the canon split. */
    private void testamentHeading(String text,boolean startOfNewTestament){
        if(startOfNewTestament)ui.gap(content,28);
        LinearLayout row=ui.row();row.setGravity(Gravity.CENTER_VERTICAL);
        View left=new View(this),right=new View(this);
        left.setBackgroundColor(HOME_BORDER);right.setBackgroundColor(HOME_BORDER);
        row.addView(left,new LinearLayout.LayoutParams(0,ui.dp(1),1));
        TextView heading=ui.displayText(text,21);
        heading.setGravity(Gravity.CENTER);heading.setPadding(ui.dp(14),ui.dp(6),ui.dp(14),ui.dp(6));
        row.addView(heading,new LinearLayout.LayoutParams(-2,-2));
        row.addView(right,new LinearLayout.LayoutParams(0,ui.dp(1),1));
        ui.add(content,row,startOfNewTestament?3:13);
    }
    private void rememberHomeScroll(){
        if(lastRenderedScreen==Screen.HOME && homeScrollRestored && currentScroller!=null){
            homeScrollY=Math.max(0,currentScroller.getScrollY());
            getSharedPreferences(HOME_UI_PREFS,MODE_PRIVATE).edit().putInt("scroll_y",homeScrollY).apply();
        }
    }
    private void render() {
        cancelWritingCheck();
        if(backupInProgress)return;
        if(screen!=Screen.HOME)lessonWaitingForBible=null;
        boolean collectionWasLoading=collectionDetailLoading;
        collectionDetailLoading=false;
        collectionGeneration++;
        if(collectionTask!=null){collectionTask.cancel(false);collectionTask=null;}
        modeInfo.dismiss(false);
        getWindow().setNavigationBarColor(screen==Screen.TRAIN||screen==Screen.RUN?0xFFF8E5D9:CARD);
        CollectionsNavigationIcon previousCollections=root==null?null:root.findViewWithTag(CollectionsNavigationIcon.TAG);
        if(previousCollections!=null && previousCollections.isSelected())
            collectionsIconCycle=previousCollections.settledCycle();
        if(isBibleSearchScreen())bibleSearch.resume();
        else bibleSearch.suspend();
        finishTabTransition();
        tabWarmGeneration++;
        readyTabs.clear();
        rememberHomeScroll();
        if(currentScroller!=null && lastRenderedScreen!=null && lastRenderedScreen!=Screen.HOME
            && !(lastRenderedScreen==Screen.COLLECTION_DETAIL&&collectionWasLoading)){
            savedScrollY.put(lastRenderedScreen,currentScroller.getScrollY());
        }
        if(collectionAnimator!=null){collectionAnimator.cancel();collectionAnimator=null;}
        if(screen==Screen.TRAIN){lastRenderedScreen=screen;renderFullscreenLesson();return;}
        if(screen==Screen.CARD_SESSION){lastRenderedScreen=screen;renderCardSession();return;}
        if(screen==Screen.RUN){lastRenderedScreen=screen;renderWritingRun();return;}
        boolean focusedReaderChapter=screen==Screen.CHAPTER && chapterFromVerseGrid && focusVerse>0;
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


        if(screen==Screen.BIBLE_PICKER && pickerSelectionPanel!=null)root.addView(pickerSelectionPanel);
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

        if (isMainTab(screen)) {
            readyTabs.put(
                    screen,
                    new ReadyTab(
                            root,
                            content,
                            currentScroller,
                            homeIndex
                    )
            );

            prepareOtherTabs();
        }

        boolean restoredPickerScroll=isPickerScreen(screen)&&bible.isReady()&&pickerRestoredScroll>=0;
        if(restoredPickerScroll){
            int y=pickerRestoredScroll;pickerRestoredScroll=-1;
            afterPickerLayout(content,()->{if(currentScroller==scroll)scroll.scrollTo(0,y);});
        }
        if(!restoredPickerScroll && !focusedReaderChapter && screen!=Screen.HOME && savedScrollY.containsKey(screen) &&
            !(screen==Screen.BOOKS&&bibleSearch.active()) &&
            !(screen==Screen.PICKER_BOOKS&&bibleSearch.active()) &&
            screen!=Screen.PICKER_TEXT){
            int y=savedScrollY.get(screen);
            Runnable restore=()->{if(currentScroller==scroll)scroll.scrollTo(0,y);};
            if(isPickerScreen(screen))afterPickerLayout(content,restore);
            else scroll.post(restore);
        }
        if(screen==Screen.HOME){
            homeScrollRestored=false;
            final int destination=homeScrollY;
            scroll.post(()->{
                if(screen==Screen.HOME && currentScroller==scroll){
                    scroll.scrollTo(0,destination);
                    homeScrollRestored=true;
                }
            });
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
        if(!isPickerScreen(screen))rememberChapter(hit.book,hit.chapter);
        focusVerse=hit.verse;
        boolean inPicker=screen==Screen.PICKER_BOOKS||screen==Screen.BIBLE_PICKER;
        bibleSearch.reset();
        if(inPicker){
            focusPickerSearchHit=true;
            pickerRestoredScroll=-1;
            screen=Screen.PICKER_TEXT;
        }else{
            chapterFromVerseGrid=false;
            screen=Screen.CHAPTER;
        }
        render();
    }

    /** Quiet category illustration beside each compact group of Bible books. */
    private final class BibleGroupGlyph extends View {
        private final BookColorLogic.Group category;
        private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        BibleGroupGlyph(BookColorLogic.Group category){super(MainActivity.this);this.category=category;
            setContentDescription(bookGroupName(category));}
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
    private int collectionsIconCycle=1;
    /** A raised four-destination navigation pill. */
    private void addNav(LinearLayout parent){
        final Screen[] destinations={Screen.HOME,Screen.BOOKS,Screen.COLLECTIONS,Screen.SETTINGS};
        final String[] names={"Главная","Библия","Коллекции","Настройки"};
        final int current=mainTabIndex();
        LinearLayout outer=ui.column();outer.setPadding(ui.dp(16),ui.dp(4),ui.dp(16),ui.dp(12));
        FrameLayout floating=new FrameLayout(this);
        floating.setBackground(ui.outlined(CARD,36,BORDER));
        floating.setClipToOutline(false);
        final View indicator=new View(this);
        indicator.setTag("sv-nav-indicator");
        indicator.setBackground(ui.shape(0xFFF7DED0,29));
        floating.addView(indicator,new FrameLayout.LayoutParams(ui.dp(80),ui.dp(57),Gravity.TOP|Gravity.LEFT));
        LinearLayout row=ui.row();row.setPadding(ui.dp(7),ui.dp(7),ui.dp(7),ui.dp(7));
        for(int i=0;i<destinations.length;i++){
            final int index=i;
            int ink=i==current?0xFFB7553D:MUTED;
            LinearLayout tab=ui.column();tab.setGravity(Gravity.CENTER);
            tab.setPadding(ui.dp(5),ui.dp(3),ui.dp(5),ui.dp(3));
            View symbol=i==0 ? new HomeNavigationIcon(this,ink,current==0)
                    : i==1 ? new BookNavigationIcon(this,ink,current==1)
                    : i==2 ? new CollectionsNavigationIcon(this,ink,current==2,collectionsIconCycle)
                    : new NavigationGlyph(this,"settings",ink);
            LinearLayout.LayoutParams glyph=new LinearLayout.LayoutParams(ui.dp(27),ui.dp(27));
            glyph.gravity=Gravity.CENTER_HORIZONTAL;tab.addView(symbol,glyph);
            TextView name=ui.txt(names[i],12,ink,i==current);
            name.setGravity(Gravity.CENTER);ui.add(tab,name,1);
            tab.setClickable(true);tab.setFocusable(true);
            tab.setContentDescription("Открыть раздел "+names[i]);
            tab.setOnClickListener(v->{
                if(index==0 && screen==Screen.HOME){
                    animateHomeIcon(root,true,true);return;
                }
                if(index==1 && mainTabIndex()==1){
                    // Keep the normal section-root navigation before replaying the sheet.
                    goToMainTab(index);
                    animateBibleIcon(root,true,true,null);return;
                }
                if(index==2 && mainTabIndex()==2){
                    goToMainTab(index);
                    animateCollectionsIcon(root,true,true);return;
                }
                goToMainTab(index);
            });
            row.addView(tab,new LinearLayout.LayoutParams(0,ui.dp(57),1));
        }
        floating.addView(row,new FrameLayout.LayoutParams(-1,ui.dp(71)));
        outer.addView(floating,new LinearLayout.LayoutParams(-1,ui.dp(71)));
        parent.addView(outer,new LinearLayout.LayoutParams(-1,-2));
        final int from=lastNavIndex;
        floating.post(()->{
            int slot=(floating.getWidth()-ui.dp(14))/destinations.length;
            if(slot<=0)return;
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)indicator.getLayoutParams();
            p.width=slot;p.height=ui.dp(57);p.leftMargin=ui.dp(7);p.topMargin=ui.dp(7);
            indicator.setLayoutParams(p);
            indicator.setTranslationX(slot*from);
            if(from!=current && ValueAnimator.areAnimatorsEnabled()){
                indicator.animate().translationX(slot*current).setDuration(240)
                    .setInterpolator(new DecelerateInterpolator()).start();
            }else indicator.setTranslationX(slot*current);
        });
        lastNavIndex=current;
    }
    private void animateCollectionsIcon(View page,boolean selected,boolean previous){
        if(page==null)return;
        CollectionsNavigationIcon icon=page.findViewWithTag(CollectionsNavigationIcon.TAG);
        if(icon!=null)icon.transition(selected,previous);
    }
    private void settleCollectionsIcons(){
        if(root!=null)settleCollectionsIcon(root);
        for(ReadyTab tab:readyTabs.values())settleCollectionsIcon(tab.page);
    }
    private void settleCollectionsIcon(View page){
        CollectionsNavigationIcon icon=page.findViewWithTag(CollectionsNavigationIcon.TAG);
        if(icon!=null)icon.settle();
    }
    private void animateHomeIcon(View page, boolean selected, boolean previous) {
        if(page==null)return;
        HomeNavigationIcon icon=page.findViewWithTag(HomeNavigationIcon.TAG);
        if(icon!=null)icon.transition(selected,previous);
    }
    private void settleHomeIcons() {
        if(root!=null)settleHomeIcon(root);
        for(ReadyTab tab:readyTabs.values())settleHomeIcon(tab.page);
    }
    private void settleHomeIcon(View page) {
        HomeNavigationIcon icon=page.findViewWithTag(HomeNavigationIcon.TAG);
        if(icon!=null)icon.settle();
    }
    private void animateBibleIcon(View page, boolean selected, boolean previous, float[] state) {
        if(page==null)return;
        BookNavigationIcon icon=page.findViewWithTag(BookNavigationIcon.TAG);
        if(icon!=null)icon.transition(selected,previous,state);
    }
    private void settleBibleIcons() {
        if(root!=null)settleBibleIcon(root);
        for(ReadyTab tab:readyTabs.values())settleBibleIcon(tab.page);
    }
    private void settleBibleIcon(View page) {
        BookNavigationIcon icon=page.findViewWithTag(BookNavigationIcon.TAG);
        if(icon!=null)icon.settle();
    }
    private int mainTabIndex(){
        switch(screen){
            case BOOKS:case CHAPTERS:case VERSES:case CHAPTER:return 1;
            case COLLECTIONS:case COLLECTION_DETAIL:case COLLECTION_BOOK:case COLLECTION_CHAPTER:
            case ENTRIES:case REORDER_STUDY:
            case CARDS:case CARD_DECK:case CARD_PICK:case CARD_SESSION:case REORDER_CARDS:return 2;
            case SETTINGS:return 3;
            case BIBLE_PICKER:case PICKER_BOOKS:case PICKER_CHAPTERS:
            case PICKER_VERSES:case PICKER_TEXT:
                return pickerTarget==BibleTarget.STUDY_GROUP &&
                    store.unifiedForGroup(pickerTargetId)==null?0:2;
            default:return 0;
        }
    }
    /** Drop a previous transition before rebuilding a page or processing system Back. */

    private void finishTabTransition() {
        ValueAnimator active = tabSwitchAnimator;
        tabSwitchAnimator = null;

        if (active != null) {
            active.cancel();
        }

        if (tabSwitchLeaving != null) {
            tabSwitchLeaving.setTranslationX(0f);

            if (tabSwitchLeaving.getParent() instanceof ViewGroup) {
                ((ViewGroup) tabSwitchLeaving.getParent())
                        .removeView(tabSwitchLeaving);
            }

            // Возвращаем нижнюю панель на сохранённый экран.
            if (tabSwitchOldNav != null
                    && tabSwitchOldNav.getParent() == null) {
                tabSwitchLeaving.addView(tabSwitchOldNav);
            }
        }

        tabSwitchOldNav = null;

        if (tabSwitchEntering != null) {
            for (int i = 0;
                 i < tabSwitchEntering.getChildCount() - 1;
                 i++) {

                tabSwitchEntering.getChildAt(i)
                        .setTranslationX(0f);
            }
        }

        tabSwitchLeaving = null;
        tabSwitchEntering = null;
        tabSwitchInProgress = false;
    }


    /** One continuous two-page slide; the bottom bar is never moved or blanked. */

    private void goToMainTab(int index) {
        if (index < 0 || index > 3 || tabSwitchInProgress || resetInProgress || backupInProgress) {
            return;
        }

        if (screen == Screen.TRAIN
                || screen == Screen.CARD_SESSION || screen == Screen.RUN) {
            return;
        }

        final Screen destination =
                index == 0 ? Screen.HOME :
                        index == 1 ? Screen.BOOKS :
                        index == 2 ? Screen.COLLECTIONS :
                                Screen.SETTINGS;

        if (screen == destination) return;
        modeInfo.dismiss(false);
        if(isBibleSearchScreen())bibleSearch.suspend();
        if(isPickerScreen(screen)){
            passageSelection.cancel();clearInsertionContext();
            selectedBook=readerBookBeforePicker;selectedChapter=readerChapterBeforePicker;
        }
        // Tapping an already selected section from its details simply opens the root list.
        if(mainTabIndex()==index && !isMainTab(screen)){
            screen=destination;render();return;
        }

        final int oldIndex = mainTabIndex();
        // Reset only this decorative icon before detaching the cached outgoing page.
        if(oldIndex==2 && index!=2)animateCollectionsIcon(root,false,true);
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

        reviewMode = false;
        groupMode = false;
        forcedStep = null;

        // Сохраняем текущую прокрутку.
        if (lastRenderedScreen == Screen.HOME) {
            rememberHomeScroll();
        } else if (lastRenderedScreen != null
                && currentScroller != null) {

            savedScrollY.put(
                    lastRenderedScreen,
                    currentScroller.getScrollY()
            );
        }

        ReadyTab cached = readyTabs.get(destination);

        // Домашний стих мог обновиться автоматически.
        if (destination == Screen.HOME
                && cached != null
                && cached.homeVerseIndex != homeIndex) {

            readyTabs.remove(destination);
            cached = null;
        }

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
            lastNavIndex = index;

            if (destination == Screen.HOME) {
                final ScrollView active = currentScroller;

                active.post(() -> {
                    if (currentScroller == active) {
                        active.scrollTo(0, homeScrollY);
                        homeScrollRestored = true;
                    }
                });

            } else if (currentScroller!=null && savedScrollY.containsKey(destination)) {

                final ScrollView active = currentScroller;
                final int y = savedScrollY.get(destination);

                active.post(() -> {
                    if (currentScroller == active) {
                        active.scrollTo(0, y);
                    }
                });
            }

            animatePreparedTabIndicator(
                    root,
                    oldIndex,
                    index
            );

        } else {

            // Резервный путь, если экран ещё не подготовлен.
            render();

            if (isMainTab(previousScreen) && leaving != null) {
                readyTabs.put(
                        previousScreen,
                        new ReadyTab(
                                leaving,
                                leavingBody,
                                leavingScroller,
                                homeIndex
                        )
                );
            }
        }

        // The destination can be a cached page: animate its icon without rebuilding it.
        if(oldIndex!=index && (oldIndex==2 || index==2))
            animateCollectionsIcon(root,index==2,oldIndex==2);
        if(oldIndex!=index)animateBibleIcon(root,index==1,oldIndex==1,bibleIconState);
        if(oldIndex!=index && (oldIndex==0 || index==0))
            animateHomeIcon(root,index==0,oldIndex==0);
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

        // Нижняя панель не уезжает вместе с экраном.
        if (leavingHasNav && leaving.getChildCount() > 0) {

            tabSwitchOldNav =
                    leaving.getChildAt(leaving.getChildCount() - 1);

            leaving.removeViewAt(
                    leaving.getChildCount() - 1
            );
        }

        FrameLayout.LayoutParams overlay =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(ui.dp(1), height - navHeight),
                        Gravity.TOP
                );

        window.addView(leaving, overlay);

        tabSwitchLeaving = leaving;
        tabSwitchEntering = entering;
        tabSwitchInProgress = true;

        // Новый экран уже расположен рядом с текущим.
        for (int i = 0;
             i < entering.getChildCount() - 1;
             i++) {

            entering.getChildAt(i)
                    .setTranslationX(direction * width);
        }

        ValueAnimator animator =
                ValueAnimator.ofFloat(0f, 1f);

        tabSwitchAnimator = animator;

        animator.setDuration(225L);
        animator.setInterpolator(
                new DecelerateInterpolator(1.4f)
        );

        animator.addUpdateListener(a -> {
            if (tabSwitchAnimator != animator) return;

            float fraction = (Float) a.getAnimatedValue();

            leaving.setTranslationX(
                    -direction * width * fraction
            );

            for (int i = 0;
                 i < entering.getChildCount() - 1;
                 i++) {

                entering.getChildAt(i).setTranslationX(
                        direction * width * (1f - fraction)
                );
            }
        });

        animator.addListener(
                new android.animation.AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationEnd(
                            android.animation.Animator animation) {

                        if (tabSwitchAnimator != animator) return;

                        tabSwitchAnimator = null;

                        finishTabTransition();
                        prepareOtherTabs();
                    }
                }
        );

        animator.start();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event){
        modeInfo.onActivityTouch(event);
        // Fullscreen training and card carousels retain their own swipe gestures.
        if(screen==Screen.TRAIN||screen==Screen.CARD_SESSION||tabSwitchInProgress)
            return super.dispatchTouchEvent(event);
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            swipeStartX=event.getX();swipeStartY=event.getY();swipeIntercepted=false;
        }
        if(action==MotionEvent.ACTION_MOVE&&!swipeIntercepted){
            float dx=event.getX()-swipeStartX,dy=event.getY()-swipeStartY;
            if(Math.abs(dx)>ui.dp(90)&&Math.abs(dx)>Math.abs(dy)*1.6f){
                swipeIntercepted=true;
                MotionEvent cancel=MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);cancel.recycle();return true;
            }
        }
        if(swipeIntercepted){
            if(action==MotionEvent.ACTION_UP){
                float dx=event.getX()-swipeStartX;
                if(Math.abs(dx)>ui.dp(60))goToMainTab(mainTabIndex()+(dx<0?1:-1));
                swipeIntercepted=false;return true;
            }
            if(action==MotionEvent.ACTION_CANCEL){swipeIntercepted=false;return true;}
            return true;
        }
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
        return studyProgressFor(groupId,store.collectionProgress());
    }
    private String studyProgressFor(long groupId,CollectionProgressSnapshot progress){
        int all=0,mastered=0;
        for(LocalStore.Entry e:progress.entries(groupId)){
            all+=progress.verseCount(e.id);
            mastered+=progress.masteryCount(progress.canonicalId(e.id));
        }
        return mastered+" из "+all+" стихов";
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
            panel.setBackground(homeSurface());
            LinearLayout line=ui.row();line.setGravity(Gravity.CENTER_VERTICAL);
            ImageView illustration=landscapeThumbnail(deck.id);
            LinearLayout.LayoutParams imageP=new LinearLayout.LayoutParams(ui.dp(79),ui.dp(MAX_LANDSCAPE_THUMBNAIL_DP));
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
            LinearLayout c=ui.card(content);c.setBackground(homeSurface());
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
        selectedPickerChapters.clear();
        readerBookBeforePicker=selectedBook;readerChapterBeforePicker=selectedChapter;
        passageSelection.cancel();passageSelection.book=0;passageSelection.collectionId=targetId;
        pickerTarget=target;pickerTargetId=targetId;pickerBackScreen=back;
        pickerRange=false;pickerHasAnchor=false;pickerChooseEnd=false;
        pickerVerseNumber=1;pickerRestoredScroll=-1;
        clearPassageSelection();focusVerse=-1;
        savedScrollY.remove(Screen.PICKER_BOOKS);
        savedScrollY.remove(Screen.PICKER_CHAPTERS);
        savedScrollY.remove(Screen.PICKER_VERSES);
        bibleSearch.reset();
        screen=Screen.PICKER_BOOKS;render();
    }
    /** Range settings reuse the same catalog and numeric grids as quick insertion. */
    private void openPickerRange(){
        pickerQuickBook=selectedBook;pickerQuickChapter=selectedChapter;
        pickerQuickScroll=currentScroller==null?0:currentScroller.getScrollY();
        passageSelection.openBook(selectedBook);passageSelection.active=true;
        screen=Screen.BIBLE_PICKER;render();
    }
    private void beginPickerBoundary(boolean end){
        pickerChooseEnd=end;
        selectedBook=passageSelection.book;
        int previous=end?passageSelection.endChapter:passageSelection.startChapter;
        selectedChapter=previous>0?previous:passageSelection.startChapter>0?
            passageSelection.startChapter:pickerQuickChapter;
        savedScrollY.remove(Screen.PICKER_CHAPTERS);savedScrollY.remove(Screen.PICKER_VERSES);
        pickerRestoredScroll=-1;
        screen=selectedBook>0?Screen.PICKER_CHAPTERS:Screen.PICKER_BOOKS;render();
    }
    private String pickerBoundaryReference(boolean end){
        int c=end?passageSelection.endChapter:passageSelection.startChapter;
        int v=end?passageSelection.endVerse:passageSelection.startVerse;
        return c>0&&v>0?Bible.reference(passageSelection.book,c,v,c,v):"Не выбрано";
    }
    private void renderUnifiedBiblePicker(){
        pickerSelectionPanel=null;
        if(bibleNotReady())return;
        title("Задать диапазон",passageSelection.book>0?Bible.NAMES[passageSelection.book-1]:null);
        TextView start=ui.button("Задать начало отрывка\n"+pickerBoundaryReference(false),
            passageSelection.startChapter>0,()->beginPickerBoundary(false));
        start.setMinHeight(ui.dp(82));ui.add(content,start,10);
        TextView end=ui.button("Задать конец отрывка\n"+pickerBoundaryReference(true),
            passageSelection.endChapter>0,()->beginPickerBoundary(true));
        end.setMinHeight(ui.dp(82));ui.add(content,end,10);
        pickerSelectionLabel=ui.prose("",18);ui.add(content,pickerSelectionLabel,18);
        pickerSelectionPanel=ui.column();pickerSelectionPanel.setPadding(ui.dp(19),ui.dp(8),ui.dp(19),ui.dp(8));
        pickerSelectionPanel.setBackground(ui.outlined(CARD,16,BORDER));
        pickerSaveButton=ui.button("Добавить отрывок",true,this::savePickerRange);
        ui.add(pickerSelectionPanel,pickerSaveButton,0);updatePickerRangeSummary();
    }
    private void updatePickerRangeSummary(){
        boolean valid=passageSelection.valid();
        int count=0;
        if(valid){
            int[] b=passageSelection.bounds();
            try{count=bible.collectionPassage(passageSelection.book,b[0],b[1],b[2],b[3]).size();}
            catch(IllegalArgumentException ignored){valid=false;}
        }
        if(valid){
            int[] b=passageSelection.bounds();
            pickerSelectionLabel.setText(Bible.reference(passageSelection.book,b[0],b[1],b[2],b[3])+" · "+PassageSelection.verses(count));
        }else pickerSelectionLabel.setText(passageSelection.complete()?
            "Конец раньше начала. Измените начало или конец отрывка.":"");
        pickerSelectionLabel.setVisibility(valid||passageSelection.complete()?View.VISIBLE:View.GONE);
        pickerSelectionLabel.setTextColor(passageSelection.complete()&&!valid?RED:WHITE);
        pickerSaveButton.setEnabled(valid&&count>0);pickerSaveButton.setAlpha(pickerSaveButton.isEnabled()?1f:.45f);
    }
    private void afterPickerLayout(View view,Runnable action){
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){
            @Override public void onGlobalLayout(){
                if(view.getHeight()<=0)return;
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);action.run();
            }
        });
    }
    /** Four-column number grid, identical to the chapters grid. */
    private void renderPickerVerseGrid(){renderVerseGrid(false);}
    private void renderVerseGrid(boolean reader){
        if(bibleNotReady())return;
        List<Bible.Verse> verses=bible.chapter(selectedBook,selectedChapter);
        title(Bible.NAMES[selectedBook-1]+" "+selectedChapter,"Стихи");
        if(verses.isEmpty()){ui.helper(content,"В главе нет стихов");return;}
        for(int base=1;base<=verses.size();base+=4){
            LinearLayout r=ui.row();
            for(int k=base;k<base+4;k++){
                if(k>verses.size()){
                    r.addView(new View(this),new LinearLayout.LayoutParams(0,ui.dp(50),1));
                    continue;
                }
                final int verse=k;
                int boundaryChapter=pickerChooseEnd?passageSelection.endChapter:passageSelection.startChapter;
                int boundaryVerse=pickerChooseEnd?passageSelection.endVerse:passageSelection.startVerse;
                boolean selected=reader?verse==readerVerseNumber:passageSelection.active?
                    selectedChapter==boundaryChapter&&verse==boundaryVerse:verse==pickerVerseNumber;
                TextView b=ui.button(String.valueOf(verse),selected,()->{
                    if(reader){
                        readerVerseNumber=verse;focusVerse=verse;
                        chapterFromVerseGrid=true;screen=Screen.CHAPTER;
                    }else if(passageSelection.active){
                        passageSelection.setBoundary(pickerChooseEnd,selectedChapter,verse);
                        screen=Screen.BIBLE_PICKER;
                    }else{
                        pickerVerseNumber=verse;
                        focusVerse=verse;
                        pickerRestoredScroll=-1;
                        focusPickerSearchHit=false;
                        screen=Screen.PICKER_TEXT;
                    }
                    render();
                });
                b.setPadding(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(4));
                b.setContentDescription((!reader&&passageSelection.active?"Выбрать стих ":"Открыть текст стиха ")+verse+
                    (selected?", выбран":""));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ui.dp(50),1);
                p.setMargins(ui.dp(3),ui.dp(4),ui.dp(3),ui.dp(4));r.addView(b,p);
            }
            ui.add(content,r,2);

        }
    }
    /** Shared Bible data and catalog; only this insertion screen owns selection controls. */
    private void renderPickerVerseText(){
        pickerSelectionPanel=null;
        if(bibleNotReady())return;
        passageSelection.openBook(selectedBook);passageSelection.chapter=selectedChapter;
        pickerQuickButtons.clear();
        List<Bible.Verse> verses=bible.chapter(selectedBook,selectedChapter);
        title(Bible.NAMES[selectedBook-1]+" "+selectedChapter,null);
        LinearLayout navigation=ui.row();
        navigation.addView(ui.button("Главы · "+selectedChapter,false,()->{
            savedScrollY.remove(Screen.PICKER_CHAPTERS);screen=Screen.PICKER_CHAPTERS;render();
        }),new LinearLayout.LayoutParams(0,-2,1));
        navigation.addView(ui.button("Стихи",false,()->{
            savedScrollY.remove(Screen.PICKER_VERSES);screen=Screen.PICKER_VERSES;render();
        }),new LinearLayout.LayoutParams(0,-2,1));
        ui.add(content,navigation,4);
        ui.action(content,"Задать диапазон",false,this::openPickerRange);
        final ScrollView scroller=currentScroller;
        final int targetVerse=focusVerse;focusVerse=-1;
        // Keep the chapter heading for verse 1; jump to later selections and search hits.
        final boolean scrollToTarget=pickerRestoredScroll<0&&(focusPickerSearchHit||targetVerse>1);
        focusPickerSearchHit=false;
        final Set<String> present=new HashSet<>(),included=new HashSet<>();
        if(pickerTarget==BibleTarget.STUDY_GROUP){
            for(LocalStore.Entry entry:store.entries(pickerTargetId)){
                present.add(entry.reference);
                if(entry.lines().size()>1&&entry.reference.startsWith(Bible.NAMES[selectedBook-1]+" "))
                    for(Bible.Verse part:entry.lines())included.add(part.ref);
            }
        }
        for(Bible.Verse verse:verses){
            final int number=Integer.parseInt(verse.ref.substring(verse.ref.indexOf(':')+1));
            final int book=selectedBook,chapter=selectedChapter;
            final String reference=Bible.reference(book,chapter,number,chapter,number);
            LinearLayout line=ui.card(content);
            LinearLayout head=ui.row();head.setGravity(Gravity.CENTER_VERTICAL);
            TextView marker=ui.txt(verse.ref,14,ui.bookInk(book),true);
            head.addView(marker,new LinearLayout.LayoutParams(0,-2,1));
            if(pickerTarget==BibleTarget.STUDY_GROUP){
                boolean standalone=present.contains(reference),covered=!standalone&&included.contains(verse.ref);
                TextView toggle=ui.button(standalone?"−":covered?"✓":"+",false,()->{});
                toggle.setContentDescription((standalone?"Убрать ":covered?"Уже в отрывке: ":"Добавить ")+reference);
                pickerQuickButtons.add(toggle);head.addView(toggle,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
                toggle.setOnClickListener(v->{
                    if(included.contains(verse.ref)&&!present.contains(reference))return;
                    if(togglePickerCollectionVerse(book,chapter,number)){
                        boolean added=pickerCollectionContainsVerse(book,chapter,number);
                        if(added)present.add(reference);else present.remove(reference);
                        toggle.setText(added?"−":included.contains(verse.ref)?"✓":"+");
                        toggle.setContentDescription((added?"Убрать ":included.contains(verse.ref)?"Уже в отрывке: ":"Добавить ")+reference);
                    }
                });
            }
            ui.add(line,head,0);ui.add(line,ui.prose(verse.text,19),8);
            if(targetVerse==number){
                afterPickerLayout(line,()->{
                    if(currentScroller!=scroller)return;
                    if(scrollToTarget)
                        scroller.scrollTo(0,Math.max(0,line.getTop()-ui.dp(16)));
                    line.setBackground(ui.outlined(0xFFFFE8B5,20,BORDER));
                    line.postDelayed(()->{if(currentScroller==scroller)line.setBackground(ui.outlined(CARD,20,BORDER));},1100);
                });
            }
        }

    }
    private void savePickerRange(){
        if(!passageSelection.active||!passageSelection.valid()||!pickerSaveButton.isEnabled())return;
        pickerSaveButton.setEnabled(false);
        int[] b=passageSelection.bounds();
        if(savePickedPassageFor(passageSelection.book,b[0],b[1],b[2],b[3])){
            passageSelection.cancel();clearInsertionContext();
            selectedBook=readerBookBeforePicker;selectedChapter=readerChapterBeforePicker;
            screen=pickerBackScreen;render();
        }else updatePickerRangeSummary();
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
                    selectedBook=which+1;selectedChapter=lastReadChapter(selectedBook);
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

    private void renderHome() {
        tabWordmark();
        int[] vr=HOME_REFS[Math.floorMod(homeIndex,HOME_REFS.length)];
        List<Bible.Verse> homeLines=bible.isReady()?bible.chapter(vr[0],vr[1]):Collections.emptyList();
        String verseText=homeIndex==0?"В сердце моем сокрыл я слово Твое, чтобы не грешить пред Тобою.":"Загружаем стих…";
        if(!homeLines.isEmpty())for(Bible.Verse line:homeLines){
            if(line.ref.equals(vr[1]+":"+vr[2])){verseText=line.text;break;}
        }
        homeVerseHero(homeIndex,Bible.NAMES[vr[0]-1]+" "+vr[1]+":"+vr[2],verseText);
        renderUnifiedHomePanel();
    }

    private final class ChevronView extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        ChevronView(boolean open){super(MainActivity.this);
            p.setColor(MUTED);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(ui.dp(2.5f));
            p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            setRotation(open?180f:0f);
            setContentDescription(open?"Свернуть":"Развернуть");
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float x=getWidth()/2f,y=getHeight()/2f;
            Path path=new Path();path.moveTo(x-ui.dp(7),y-ui.dp(3));
            path.lineTo(x,y+ui.dp(4));path.lineTo(x+ui.dp(7),y-ui.dp(3));
            canvas.drawPath(path,p);
        }
        void setOpen(boolean open){
            setContentDescription(open?"Свернуть":"Развернуть");
            if(ValueAnimator.areAnimatorsEnabled())animate().rotation(open?180f:0f)
                .setDuration(240L).setInterpolator(new DecelerateInterpolator()).start();
            else setRotation(open?180f:0f);
        }
    }

    /** Creates a collection on Home, showing the list but never forcing a scroll jump. */
    private void createHomeCollection(){
        newGroup(groupId->{
            homeCollectionsExpanded=true;
            getSharedPreferences(HOME_UI_PREFS,MODE_PRIVATE).edit()
                .putBoolean("collections_expanded",true).apply();
            screen=Screen.HOME;render();
        });
    }
    private FrameLayout collectionTile(LocalStore.Group group){
        FrameLayout tile=new FrameLayout(this);
        tile.setBackground(ui.outlined(CARD,22,BORDER));tile.setClipToOutline(true);
        tile.setElevation(ui.dp(2));
        ImageView scene=new ImageView(this);
        scene.setImageResource(landscapeFor(group.id));
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
        ui.add(caption,ui.txt(group.count+" "+collectionCountNoun(group.count),12,MUTED,false),5);
        tile.addView(caption,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        tile.setContentDescription(group.title+", "+group.count+" "+collectionCountNoun(group.count));
        tile.setClickable(true);tile.setFocusable(true);
        return tile;
    }
    private void renderHomeCollections(){
        LinearLayout section=ui.column();ui.add(content,section,13);
        LinearLayout controls=ui.row();ui.add(section,controls,0);
        LinearLayout heading=ui.row();heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(ui.dp(12),ui.dp(5),ui.dp(9),ui.dp(5));
        heading.setBackground(homeSurface());
        heading.setElevation(ui.dp(1));
        NavigationGlyph folder=new NavigationGlyph(this,"cards",0xFFAC684C);
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(ui.dp(30),ui.dp(30));
        iconP.rightMargin=ui.dp(10);heading.addView(folder,iconP);
        TextView label=ui.displayText("Мои коллекции",20);
        label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        ChevronView arrow=new ChevronView(homeCollectionsExpanded);
        heading.addView(arrow,new LinearLayout.LayoutParams(ui.dp(30),ui.dp(38)));
        LinearLayout.LayoutParams headingP=new LinearLayout.LayoutParams(0,ui.dp(55),1);
        headingP.rightMargin=ui.dp(9);controls.addView(heading,headingP);
        TextView plus=ui.txt("+",28,0xFFB7553D,false);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(ui.outlined(0xFFFFE7D9,18,BORDER));
        plus.setClickable(true);plus.setFocusable(true);
        plus.setContentDescription("Создать коллекцию");
        plus.setOnClickListener(v->createHomeCollection());
        controls.addView(plus,new LinearLayout.LayoutParams(ui.dp(55),ui.dp(55)));
        FrameLayout clip=new FrameLayout(this);clip.setClipChildren(true);
        LinearLayout collectionList=ui.column();collectionList.setPadding(0,0,0,ui.dp(4));
        // One readable compact list, part of the main page's single scroll surface.
        for(LocalStore.Group group:store.groups()){
            LinearLayout tile=ui.row();tile.setPadding(ui.dp(9),ui.dp(8),ui.dp(8),ui.dp(8));
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setMinimumHeight(ui.dp(72));
            tile.setBackground(homeSurface());
            ImageView thumb=landscapeThumbnail(group.id);
            LinearLayout.LayoutParams thumbP=new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48));
            thumbP.rightMargin=ui.dp(12);tile.addView(thumb,thumbP);
            LinearLayout caption=ui.column();
            TextView collectionName=ui.displayText(group.title,16);
            collectionName.setMaxLines(2);
            collectionName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ui.add(caption,collectionName,0);
            ui.add(caption,ui.txt(group.count+" "+collectionCountNoun(group.count),12,MUTED,false),3);
            tile.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
            tile.setClickable(true);tile.setFocusable(true);
            tile.setOnClickListener(v->{selectedGroup=group.id;screen=Screen.ENTRIES;render();});
            ui.add(collectionList,tile,7);
        }
        clip.addView(collectionList,new FrameLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,
            homeCollectionsExpanded?-2:0);cp.topMargin=ui.dp(2);section.addView(clip,cp);
        heading.setClickable(true);heading.setFocusable(true);
        heading.setContentDescription("Мои коллекции, "+(homeCollectionsExpanded?"свернуть":"раскрыть"));
        heading.setOnClickListener(v->{
            if(collectionAnimator!=null){collectionAnimator.cancel();collectionAnimator=null;}
            homeCollectionsExpanded=!homeCollectionsExpanded;
            arrow.setOpen(homeCollectionsExpanded);
            heading.setContentDescription("Мои коллекции, "+(homeCollectionsExpanded?"свернуть":"раскрыть"));
            getSharedPreferences(HOME_UI_PREFS,MODE_PRIVATE).edit()
                .putBoolean("collections_expanded",homeCollectionsExpanded).apply();
            animateHomeCollections(clip,collectionList,homeCollectionsExpanded);
        });
    }

    private static String collectionCountNoun(int count){
        int mod=count%100,unit=count%10;
        return mod>=11&&mod<=14?"отрывков":unit==1?"отрывок":
            unit>=2&&unit<=4?"отрывка":"отрывков";
    }
    /** Animates only the collection container, never recreating/scrolling Home. */
    private void animateHomeCollections(FrameLayout clip,LinearLayout items,boolean expand){
        int availableWidth=clip.getWidth();
        if(availableWidth<=0)availableWidth=Math.max(ui.dp(200),
            getResources().getDisplayMetrics().widthPixels-ui.dp(38));
        items.measure(View.MeasureSpec.makeMeasureSpec(availableWidth,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int desired=items.getMeasuredHeight();
        int from=Math.max(0,clip.getHeight()),to=expand?desired:0;
        if(!ValueAnimator.areAnimatorsEnabled() || from==to){
            LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)clip.getLayoutParams();
            p.height=expand?-2:0;clip.setLayoutParams(p);return;
        }
        final boolean expanding=expand;
        ValueAnimator animation=ValueAnimator.ofInt(from,to);
        collectionAnimator=animation;
        animation.setDuration(240L);animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(a->{
            if(collectionAnimator!=animation)return;
            LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)clip.getLayoutParams();
            p.height=(Integer)a.getAnimatedValue();clip.setLayoutParams(p);
        });
        animation.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(android.animation.Animator a){
                if(collectionAnimator!=animation)return;
                LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)clip.getLayoutParams();
                p.height=expanding?-2:0;clip.setLayoutParams(p);
                collectionAnimator=null;
            }
        });
        animation.start();
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
    private String bookGroupName(BookColorLogic.Group group){
        switch(group){
            case PENTATEUCH:return "Закон";
            case HISTORY:return "Исторические книги";
            case POETRY:return "Поэтические книги";
            case MAJOR_PROPHETS:return "Большие пророки";
            case MINOR_PROPHETS:return "Малые пророки";
            case GOSPELS:return "Евангелия";
            case ACTS:return "Деяния апостолов";
            case PAULINE_LETTERS:return "Послания Павла";
            case GENERAL_LETTERS:return "Соборные послания";
            default:return "Откровение";
        }
    }
    private void renderBooks() {
        if(bibleSearch.active()){bibleSearchView.placeholders(content,bibleSearch);return;}
        if(bibleNotReady())return;
        boolean bulk=screen==Screen.PICKER_BOOKS&&canBulkPick();
        Set<Integer> addedBooks=bulk?store.collectionBooks(pickerTargetId).keySet():Collections.emptySet();
        if(bulk)pickerBookActions.clear();
        BookColorLogic.Group previous=null;
        LinearLayout groupedRows=null;
        for(int b=1;b<=66;b++){
            if(b==1)testamentHeading("ВЕТХИЙ ЗАВЕТ",false);
            if(b==40)testamentHeading("НОВЫЙ ЗАВЕТ",true);
            BookColorLogic.Group group=BookColorLogic.group(b);
            if(group!=previous){
                // The ten large serif category headers and spacing are from v0.16.0.
                TextView groupTitle = ui.displayText(bookGroupName(group), 21);
                groupTitle.setGravity(Gravity.CENTER);
                ui.add(content, groupTitle, 17);
                LinearLayout panel=ui.column();
                panel.setBackground(ui.outlined(CARD,18,BORDER));
                panel.setPadding(ui.dp(11),ui.dp(7),ui.dp(11),ui.dp(7));
                ui.add(content,panel,4);
                groupedRows=ui.column();panel.addView(groupedRows,new LinearLayout.LayoutParams(-1,-2));
                previous=group;
            }
            final int book=b;
            LinearLayout line=ui.row();line.setGravity(Gravity.CENTER_VERTICAL);
            TextView abbreviated=ui.txt(BookColorLogic.shortName(b),14,ui.bookInk(b),true);
            line.addView(abbreviated,new LinearLayout.LayoutParams(ui.dp(68),-2));
            TextView full=ui.txt(Bible.NAMES[b-1],16,WHITE,false);
            full.setSingleLine(true);full.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line.addView(full,new LinearLayout.LayoutParams(0,-2,1));
            if(bulk){
                BookToggle toggle=new BookToggle();
                toggle.setFocusable(true);
                applyBookToggle(toggle,pickerTargetId,book,addedBooks.contains(book));
                ui.animateLessonPress(toggle);
                pickerBookActions.put(book,toggle);
                line.addView(toggle,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
            }else{
                TextView arrow=ui.txt("›",22,GREEN,false);arrow.setGravity(Gravity.CENTER);
                line.addView(arrow,new LinearLayout.LayoutParams(ui.dp(20),-2));
            }
            line.setMinimumHeight(ui.dp(48));
            line.setContentDescription(Bible.NAMES[b-1]);
            line.setOnClickListener(v->{
                selectedBook=book;selectedChapter=screen==Screen.PICKER_BOOKS&&passageSelection.book==book?
                    passageSelection.chapter:lastReadChapter(book);
                if(screen==Screen.PICKER_BOOKS){
                    boolean rangeActive=passageSelection.active;
                    passageSelection.openBook(book);passageSelection.active=rangeActive;
                    pickerHasAnchor=false;
                    selectedPickerChapters.clear();
                    savedScrollY.remove(Screen.PICKER_CHAPTERS);
                    savedScrollY.remove(Screen.PICKER_VERSES);
                    pickerRestoredScroll=-1;
                    screen=Screen.PICKER_CHAPTERS;
                }else {selectingPassage=false;clearPassageSelection();readerVerseNumber=1;
                    savedScrollY.remove(Screen.VERSES);screen=Screen.CHAPTERS;}
                render();
            });
            groupedRows.addView(line,new LinearLayout.LayoutParams(-1,-2));
            if(b<66&&BookColorLogic.group(b+1)==group){
                View separator=new View(this);separator.setBackgroundColor(0xFFEADACE);
                groupedRows.addView(separator,new LinearLayout.LayoutParams(-1,ui.dp(1)));
            }
        }
    }

    private int lastReadChapter(int book){
        int saved=getSharedPreferences("bible_last_chapter",MODE_PRIVATE).getInt(String.valueOf(book),1);
        return Math.max(1,Math.min(Math.max(1,bible.chapterCount(book)),saved));
    }
    private void rememberChapter(int book,int chapter){
        getSharedPreferences("bible_last_chapter",MODE_PRIVATE).edit()
            .putInt(String.valueOf(book),chapter).apply();
    }
    private void openReaderVerseGrid(int chapter) {
        selectedChapter=chapter;readerVerseNumber=1;
        rememberChapter(selectedBook,chapter);
        savedScrollY.remove(Screen.VERSES);
        screen=Screen.VERSES;render();
    }
    private void renderChapters() {
        if(bibleNotReady())return;
        int n=bible.chapterCount(selectedBook);
        title(Bible.NAMES[selectedBook-1],screen==Screen.CHAPTERS?null:
            n+" "+(n%10==1 && n%100!=11?"глава":
            n%10>=2 && n%10<=4 && (n%100<12||n%100>14)?"главы":"глав"));
        int selected=screen==Screen.PICKER_CHAPTERS?selectedChapter:lastReadChapter(selectedBook);
        boolean bulk=screen==Screen.PICKER_CHAPTERS&&canBulkPick();
        if(bulk)pickerChapterButtons.clear();
        for(int base=1;base<=n;base+=4){
            LinearLayout r=ui.row();
            for(int k=base;k<base+4;k++){
                if(k>n){r.addView(new View(this),new LinearLayout.LayoutParams(0,ui.dp(50),1));continue;}
                final int c=k;
                TextView b=ui.button(String.valueOf(k),c==selected,()->{
                    if(bulk&&!selectedPickerChapters.isEmpty()){togglePickerChapter(c);return;}
                    if(screen==Screen.PICKER_CHAPTERS&&!passageSelection.active&&selectedChapter!=c)pickerVerseNumber=1;
                    selectedChapter=c;
                    if(screen==Screen.PICKER_CHAPTERS){
                        savedScrollY.remove(Screen.PICKER_VERSES);
                        pickerRestoredScroll=-1;
                        screen=Screen.PICKER_VERSES;
                    }
                    else{openReaderVerseGrid(c);return;}
                    render();
                });
                if(screen==Screen.PICKER_CHAPTERS){
                    b.setPadding(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(4));
                    b.setContentDescription("Глава "+c+(c==selected?", выбрана":""));
                    if(bulk){
                        pickerChapterButtons.put(c,b);
                        b.setOnLongClickListener(v->{togglePickerChapter(c);return true;});
                        stylePickerChapter(c,b);
                    }
                }else if(c==selected)b.setContentDescription("Глава "+c+", последняя прочитанная");
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ui.dp(50),1);
                p.setMargins(ui.dp(3),ui.dp(4),ui.dp(3),ui.dp(4));r.addView(b,p);
            }
            ui.add(content,r,2);
        }
    }
    private boolean canBulkPick(){
        return pickerTarget==BibleTarget.STUDY_GROUP&&pickerTargetId>0&&!passageSelection.active&&insertAfterEntryId<0;
    }

    private final class BookToggle extends View {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean added,busy;

        BookToggle(){
            super(MainActivity.this);
            ink.setStrokeCap(Paint.Cap.ROUND);
        }

        void showState(boolean added,boolean busy){
            this.added=added;
            this.busy=busy;
            int surface=added?BULK_SELECTED:0xFFF9F2E7;
            int edge=added?0xFFB8CBAE:HOME_BORDER;
            // Inset the visible circle while leaving the entire 48 dp view tappable.
            setBackground(new android.graphics.drawable.InsetDrawable(
                new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x24426747),
                    ui.outlined(surface,16,edge),null),ui.dp(8)));
            invalidate();
        }

        @Override public CharSequence getAccessibilityClassName(){return Button.class.getName();}

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float x=getWidth()/2f,y=getHeight()/2f;
            ink.setColor(busy?MUTED:BULK_INK);
            if(busy){
                for(int i=-1;i<=1;i++)canvas.drawCircle(x+i*ui.dp(4),y,ui.dp(1),ink);
            }else{
                float arm=ui.dp(5);
                ink.setStrokeWidth(ui.dp(2));
                canvas.drawLine(x-arm,y,x+arm,y,ink);
                if(!added)canvas.drawLine(x,y-arm,x,y+arm,ink);
            }
        }
    }

    private void applyBookToggle(BookToggle toggle,long group,int book,boolean added){
        boolean busy=pendingBooks.contains(group+":"+book);
        toggle.showState(added,busy);
        toggle.setContentDescription((busy?"Сохраняется ":added?"Убрать книгу ":"Добавить книгу ")+Bible.NAMES[book-1]);
        toggle.setOnClickListener(v->changeWholeBook(group,book,added));
        toggle.setEnabled(!busy);
    }

    private void changeWholeBook(long group,int book,boolean remove){
        String key=group+":"+book;
        if(!pendingBooks.add(key))return;
        applyBookToggle(pickerBookActions.get(book),group,book,remove);
        runCollectionMutation(()->{
            if(remove)collectionStore.removeBibleBook(group,book);
            else collectionStore.addBibleChapters(group,book,Collections.emptyList(),true,bible);
        },success->{
            pendingBooks.remove(key);
            if(screen==Screen.PICKER_BOOKS&&pickerTargetId==group){
                BookToggle current=pickerBookActions.get(book);
                if(current!=null)applyBookToggle(current,group,book,success?!remove:remove);
            }
            if(success)refreshCollectionAfterBulkChange(group);
        });
    }

    private void togglePickerChapter(int chapter){
        if(savingPickerChapters)return;
        if(!selectedPickerChapters.add(chapter))selectedPickerChapters.remove(chapter);
        updatePickerChapterSelection();
    }

    private void clearPickerChapterSelection(){
        selectedPickerChapters.clear();
        updatePickerChapterSelection();
    }

    private void stylePickerChapter(int chapter,TextView button){
        boolean selected=selectedPickerChapters.contains(chapter);
        boolean highlighted=selectedPickerChapters.isEmpty()&&chapter==selectedChapter;
        button.setContentDescription("Глава "+chapter+(selected?", выделена":highlighted?", выбрана":""));
        button.setTextColor(selected?BULK_INK:highlighted?PRIMARY_ON:WHITE);
        button.setBackground(selected?ui.outlined(BULK_SELECTED,18,0xFFABC5A7):
            highlighted?ui.softAccent():ui.outlined(CARD_ALT,18,BORDER));
    }

    private void updatePickerChapterSelection(){
        for(Map.Entry<Integer,TextView> chapter:pickerChapterButtons.entrySet())
            stylePickerChapter(chapter.getKey(),chapter.getValue());
        if(pickerChapterFooter==null)return;
        int count=selectedPickerChapters.size();
        pickerChapterFooter.setVisibility(count>0?View.VISIBLE:View.GONE);
        pickerChapterAdd.setEnabled(!savingPickerChapters);
        pickerChapterCancel.setEnabled(!savingPickerChapters);
        if(count==0)return;
        int first=selectedPickerChapters.first(), last=selectedPickerChapters.last();
        pickerChapterAdd.setText(savingPickerChapters?"Добавляем…":count==1?"Добавить главу "+first:
            last-first+1==count?"Добавить главы "+first+"–"+last:"Добавить главы ("+count+")");
    }

    private void addPickerChapterFooter(){
        pickerChapterFooter=ui.row();
        pickerChapterFooter.setPadding(ui.dp(19),ui.dp(8),ui.dp(19),ui.dp(8));
        pickerChapterFooter.setBackgroundColor(CARD);
        pickerChapterAdd=ui.button("",true,this::commitPickerChapters);
        pickerChapterAdd.setBackground(ui.shape(BULK_INK,18));
        pickerChapterAdd.setTextColor(Color.WHITE);
        ui.animateLessonPress(pickerChapterAdd);
        pickerChapterFooter.addView(pickerChapterAdd,new LinearLayout.LayoutParams(0,-2,1));
        pickerChapterCancel=ui.txt("×",26,MUTED,false);
        pickerChapterCancel.setGravity(Gravity.CENTER);
        pickerChapterCancel.setContentDescription("Отменить выделение глав");
        pickerChapterCancel.setFocusable(true);
        pickerChapterCancel.setOnClickListener(v->clearPickerChapterSelection());
        pickerChapterFooter.addView(pickerChapterCancel,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(49)));
        root.addView(pickerChapterFooter,new LinearLayout.LayoutParams(-1,-2));
        updatePickerChapterSelection();
    }

    private void commitPickerChapters(){
        if(savingPickerChapters||selectedPickerChapters.isEmpty())return;
        final long group=pickerTargetId;
        final int book=selectedBook;
        Set<Integer> chapters=new TreeSet<>(selectedPickerChapters);
        savingPickerChapters=true;
        updatePickerChapterSelection();
        runCollectionMutation(()->collectionStore.addBibleChapters(group,book,chapters,false,bible),success->{
            savingPickerChapters=false;
            if(screen==Screen.PICKER_CHAPTERS&&pickerTargetId==group&&selectedBook==book){
                if(success)clearPickerChapterSelection();
                else updatePickerChapterSelection();
            }
            if(success){
                toast("Главы добавлены");
                refreshCollectionAfterBulkChange(group);
            }
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
        rememberChapter(selectedBook,selectedChapter);
        ui.action(content,"Глава "+selectedChapter+"  ▾",false,this::showChapterJump);
        Map<Integer,LocalStore.VerseStatus> statuses=store.statusesForChapter(
            Bible.NAMES[selectedBook-1],selectedChapter);
        for(Bible.Verse verse:verses){
            final int verseNumber;
            try{verseNumber=Integer.parseInt(verse.ref.substring(verse.ref.indexOf(':')+1));}
            catch(Exception ex){continue;}
            boolean highlighted=verseNumber==focusVerse;
            LocalStore.VerseStatus stat=statuses.get(verseNumber);
            LinearLayout c=ui.card(content);
            if(highlighted){
                c.setBackground(ui.outlined(0xFFEDEBDD,20,SUCCESS));
                if(chapterFromVerseGrid){
                    final ScrollView scroller=currentScroller;
                    afterPickerLayout(c,()->{if(screen==Screen.CHAPTER&&currentScroller==scroller){
                        scroller.scrollTo(0,Math.max(0,c.getTop()-ui.dp(30)));
                        c.postDelayed(()->{
                            if(currentScroller==scroller)c.setBackground(ui.outlined(CARD,22,BORDER));
                        },1100);
                    }});
                }else c.post(()->{if(currentScroller!=null){
                    int top=c.getTop();View node=c.getParent() instanceof View?(View)c.getParent():null;
                    if(node!=null)top+=node.getTop();
                    currentScroller.smoothScrollTo(0,Math.max(0,top-ui.dp(30)));
                }});
                focusVerse=-1;
            }
            LinearLayout head=ui.row();
            head.addView(ui.txt(verse.ref,14,ui.bookInk(selectedBook),true));
            ui.add(c,head,0);
            ui.add(c,ui.prose(verse.text,19),8);
            {
                int color=stat==null?MUTED:stat.rank==3?SUCCESS:
                    stat.rank==2?0xFF517993:stat.rank==1?0xFF99723D:MUTED;
                String label=stat==null?"Не изучен":stat.rank==3?"Закреплён":
                    stat.rank==2?"Предварительно освоен":stat.rank==1?"Изучается":"Не изучен";
                int rep=stat==null?0:stat.repetitions;
                ui.add(c,ui.txt("● "+label+"   ·   Повторений: "+rep,12,color,true),12);
                if(stat!=null && stat.nextDue>0 && stat.rank>0)
                    ui.add(c,ui.txt(stat.nextDue<=System.currentTimeMillis()?"Повторить сегодня":
                        "Следующее повторение: "+date(stat.nextDue),12,MUTED,false),4);
            }
        }
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
                rememberChapter(selectedBook,chosen);render();}
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
        LinearLayout c=ui.card(content);c.setBackground(homeSurface());
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
    private void startReviewSession() {
        lessonWaitingForBible=null;
        if(!bibleReadyForRecall())return;
        reviewQueue.clear();reviewPointer=0;
        long now=System.currentTimeMillis();
        SharedPreferences saved=getSharedPreferences(SESSION_PREFS,MODE_PRIVATE);
        boolean hasPending=saved.getBoolean("review_pending",false);
        ReviewSchedule schedule=hasPending?store.reviewSchedule():store.reviewSchedule(now);
        LocalStore.DueItem pending=null;
        if(hasPending){
            LocalStore.DueItem previous=new LocalStore.DueItem(saved.getLong("review_review_entry",-1),
                saved.getString("review_review_stage",""),saved.getInt("review_begin",-1),
                saved.getInt("review_end",-1),0);
            String fingerprint=saved.getString("review_material","");
            boolean sameMaterial=fingerprint.isEmpty()||
                fingerprint.equals(reviewMaterial(schedule.verses(previous.entryId),previous));
            if(sameMaterial)pending=schedule.resolve(previous,reviewCollectionGroup,now,true);
            if(pending==null&&(!sameMaterial||schedule.resolve(previous,-1,now,true)==null)){
                saved.edit().remove("review_pending").apply();
            }
        }
        populateReviewQueue(schedule,now,pending);
        if(reviewQueue.isEmpty()){screen=Screen.DUE;render();return;}
        String restoredDraft=pending==null||saved.getBoolean("review_practice_weak",false)?"":
            resumableWritingDraft(saved.getString("review_draft",""),
                saved.getBoolean("review_lesson_had_feedback",false));
        if(!prepareReview(reviewQueue.get(0)))return;
        lessonWriting.draft=restoredDraft;
        render();
    }
    private void populateReviewQueue(ReviewSchedule schedule,long now,LocalStore.DueItem current){
        reviewQueue.clear();reviewPointer=0;
        List<LocalStore.DueItem> due=schedule.due(now,reviewCollectionGroup);
        if(current!=null)due.removeIf(item->schedule.overlaps(current,item));
        List<ReviewQueueLogic.Item> raw=new ArrayList<>();
        for(LocalStore.DueItem item:due)raw.add(new ReviewQueueLogic.Item(
            item.entryId,item.stage,item.start,item.end,item.due));
        List<ReviewQueueLogic.Item> items=ReviewQueueLogic.plan(raw,new ReviewQueueLogic.Source(){
            @Override public List<String> references(long id){
                List<String> refs=new ArrayList<>();
                for(Bible.Verse verse:schedule.verses(id))refs.add(verse.ref);
                return refs;
            }
            @Override public List<String> texts(long id){
                List<String> texts=new ArrayList<>();
                for(Bible.Verse verse:schedule.verses(id))texts.add(verse.text);
                return texts;
            }
        });
        if(current!=null)reviewQueue.add(current);
        for(ReviewQueueLogic.Item item:items)
            reviewQueue.add(new LocalStore.DueItem(item.entryId,item.stage,item.start,item.end,item.due));
    }
    private static String reviewMaterial(List<Bible.Verse> lines,LocalStore.DueItem item){
        if(item.start<0||item.end>lines.size()||item.start>=item.end)return "";
        List<String> material=new ArrayList<>();
        for(Bible.Verse verse:lines.subList(item.start,item.end)){
            material.add(verse.ref);material.add(verse.text);
        }
        return SessionIntegrityLogic.fingerprint(material);
    }

    private void renderDue() {
        LearningHelp help=reviewCollectionGroup>0?LearningHelp.REVIEW:LearningHelp.ALL_REVIEWS;
        title(help.title,null);
        LinearLayout c=ui.headerCard(content);
        ui.add(c,ui.txt("Пока нет заданий на повторение",21,WHITE,true),0);
        ui.helper(c,reviewCollectionGroup>0?"В выбранной коллекции сейчас нет заданий, срок которых наступил.":
            "Сейчас нет запланированных заданий, срок которых наступил.");
        ui.action(content,"На главную",true,()->{screen=Screen.HOME;render();});
    }
    private void openReview(LocalStore.DueItem item){
        if(prepareReview(item))render();
    }
    private boolean prepareReview(LocalStore.DueItem item){
        answerReview=null;
        LocalStore.Entry e=store.entry(item.entryId);if(e==null)return false;
        lessonSourceEntry=e.id;lessonSourceGroup=e.groupId;
        selectedEntry=item.entryId;lessonPracticeAgain=false;
        // VERSE_RUN is a temporary coherent unit, never recorded as a mastered group.
        studyStage="VERSE_RUN".equals(item.stage)?StudyPlanLogic.Stage.CHAIN:
            StudyPlanLogic.Stage.valueOf(item.stage);
        forcedStep=new StudyPlanLogic.Step(item.start,item.end,"Повторение · "+e.lines().get(item.start).ref);
        currentReview=item;reviewMaterialFingerprint=reviewMaterial(e.lines(),item);
        groupMode=false;reviewMode=true;stepCursor=0;
        weakVerse=-1;weakTransitionFrom=-1;
        resetTrainingStep(e);
        lessonWriting.clearGaps();
        trainingMode=TrainingMode.TYPE;screen=Screen.TRAIN;
        return true;
    }
    private void advanceReviewSession(){
        if(reviewPointer+1<reviewQueue.size()){
            long now=System.currentTimeMillis();
            ReviewSchedule schedule=store.reviewSchedule(now);
            while(++reviewPointer<reviewQueue.size()){
                LocalStore.DueItem queued=reviewQueue.get(reviewPointer);
                LocalStore.DueItem next=schedule.resolve(queued,reviewCollectionGroup,now,false);
                if(next!=null){openReview(next);return;}
                if("VERSE_RUN".equals(queued.stage)){
                    // An invalidated run can still contain verses that remain due.
                    int insertAt=reviewPointer+1;
                    for(int pos=queued.start;pos<queued.end;pos++){
                        LocalStore.DueItem verse=new LocalStore.DueItem(queued.entryId,"VERSE",pos,pos+1,0);
                        LocalStore.DueItem remaining=schedule.resolve(verse,reviewCollectionGroup,now,false);
                        if(remaining!=null)reviewQueue.add(insertAt++,remaining);
                    }
                }
            }
        }
        reviewMode=false;forcedStep=null;currentReview=null;reviewQueue.clear();reviewPointer=0;
        getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit().remove("review_pending").apply();
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
    private static String resumableWritingDraft(String draft,boolean checkedWithErrors){
        return LessonExerciseState.resumableDraft(draft,checkedWithErrors);
    }
    private Bundle saveActiveLesson(){
        if(activeEntry==null||activeEntry.id!=selectedEntry||activeStep==null||lessonSourceEntry<=0)
            return null;
        saveTrainingSession();
        String stage=reviewMode?currentReview.stage:studyStage.name();
        int start=activeStep.start,end=activeStep.end;
        Bundle state=new Bundle();
        state.putLong("entry",selectedEntry);
        state.putLong("source_entry",lessonSourceEntry);
        state.putLong("source_group",lessonSourceGroup);
        state.putString("material",reviewMode?lessonMaterial(activeEntry):lessonSessionMaterial);
        state.putString("stage",stage);
        state.putInt("begin",start);state.putInt("end",end);
        state.putInt("cursor",stepCursor);
        state.putBoolean("linked",groupMode);
        state.putBoolean("complete",checked&&passed);
        state.putBoolean("practice_again",lessonPracticeAgain);
        state.putBoolean("assisted",testAssisted);
        state.putString("recall",store.recallCheckpoint(selectedEntry,stage,start,end));
        state.putInt("exercise_done",studyStage==StudyPlanLogic.Stage.VERSE&&!reviewMode&&!groupMode?
            store.exerciseDone(selectedEntry,start):-1);
        if(reviewMode){
            state.putLong("review_group",reviewCollectionGroup);
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
        if(lessonSourceEntry!=selectedEntry)state.putString("source_material",lessonSourceMaterial);
        return state;
    }

    private static String lessonMaterial(LocalStore.Entry entry){
        return SessionIntegrityLogic.fingerprint(Arrays.asList(entry.reference,entry.linesJson));
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
        if(entry==null||source==null||source.groupId!=state.getLong("source_group")||
            !lessonMaterial(entry).equals(state.getString("material")))return false;
        if(sourceId!=id&&!lessonMaterial(source).equals(state.getString("source_material")))return false;
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
        String recall=store.recallCheckpoint(id,stage,start,end);
        if(!recall.equals(state.getString("recall"))||complete&&recall.isEmpty())return false;
        if(!review&&!linked){
            List<StudyPlanLogic.Step> steps=studySteps(entry,lessonStage);
            if(cursor>=steps.size()||steps.get(cursor).start!=start||steps.get(cursor).end!=end)
                return false;
            if(lessonStage==StudyPlanLogic.Stage.VERSE&&
                (store.exerciseDone(id,start)!=state.getInt("exercise_done")||
                !LessonFlowLogic.canOpen(cursor,store.unlockedPosition(id,steps.size()),steps.size())))return false;
        }

        String key=review?"review_":(linked?"linked_":"entry_")+id+"_";
        SharedPreferences pref=getSharedPreferences(SESSION_PREFS,MODE_PRIVATE);
        if(!(review&&complete)&&(!lessonStage.name().equals(pref.getString(key+"stage",""))||
            cursor!=pref.getInt(key+"step",-1)||start!=pref.getInt(key+"begin",-1)||
            end!=pref.getInt(key+"end",-1)))return false;
        if(!review&&!state.getString("material").equals(pref.getString(key+"lesson_material","")))return false;
        if(review){
            long group=state.getLong("review_group",-1);
            if(group>0&&store.unifiedForGroup(group)==null)return false;
            LocalStore.DueItem item=new LocalStore.DueItem(id,stage,start,end,0);
            long now=System.currentTimeMillis();
            ReviewSchedule schedule=complete?store.reviewSchedule(now):store.reviewSchedule();
            if(!complete){
                if(!pref.getBoolean("review_pending",false)||id!=pref.getLong("review_review_entry",-1)||
                    !stage.equals(pref.getString("review_review_stage",""))||
                    !reviewMaterial(entry.lines(),item).equals(pref.getString("review_material","")))return false;
                item=schedule.resolve(item,group,now,true);
                if(item==null||item.entryId!=id||item.start!=start||item.end!=end)return false;
            }
            if(!prepareReview(item))return false;
            reviewCollectionGroup=group;
            populateReviewQueue(schedule,now,item);
        }else{
            selectedEntry=id;
            if(linked){
                prepareGroup(GroupLearningLogic.candidate(stage,start,end,"Продолжить связку"));
                returnVerseCursor=pref.getInt(key+"return_cursor",0);
            }
        }
        if(!(review&&complete))restoreTrainingSession(key,linked);
        lessonSourceEntry=sourceId;lessonSourceGroup=source.groupId;
        lessonSessionMaterial=state.getString("material","");
        lessonSourceMaterial=state.getString("source_material","");
        executingCollectionQueue=inQueue;
        if(deck>0){
            cardStudyDeckId=deck;cardStudyCursor=deckCursor;
            cardStudyMaterial=state.getString("deck_material","");
            for(long next:deckQueue)cardStudyQueue.add(next);
        }
        lessonPracticeAgain=state.getBoolean("practice_again");
        testAssisted=state.getBoolean("assisted");
        checked=passed=complete;
        if(complete)lastAccuracy=100;
        screen=Screen.TRAIN;
        return true;
    }

    private void saveTrainingSession() {
        if(selectedEntry<=0 || (reviewMode&&checked&&passed))return;
        String key=reviewMode?"review_":(groupMode?"linked_":"entry_")+selectedEntry+"_";
        SharedPreferences.Editor editor=getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit()
            .putLong("entry",selectedEntry).putString(key+"stage",studyStage.name())
            .putInt(key+"step",stepCursor).putString(key+"mode",trainingMode.name())
            .putInt(key+"mask",mask);
        LessonExerciseState exercise=new LessonExerciseState(
            lessonWriting.resumableDraft(answerReview!=null),
            blurSeed,lessonVariant,lessonChunk,lessonAssembly.picked(),lessonSeed,savedAssemblyFingerprint,
            rotationExerciseKey,rotationExerciseSeed,rotationMask,lessonAssembly.usedTiles(),lessonWriting.variant,
            groupIntroDone,groupBuildDone,lessonWriting.practiceWeakFragment,lessonWriting.weakFragmentText);
        LessonExercisePreferences.write(editor,key,exercise)
            .putBoolean(key+"pending",reviewMode||groupMode)
            .putLong(key+"review_entry",reviewMode?selectedEntry:0)
            .putString(key+"review_stage",reviewMode&&currentReview!=null?currentReview.stage:"")
            .putString(key+"material",reviewMode?reviewMaterialFingerprint:"")
            .putString(key+"lesson_material",reviewMode?"":lessonSessionMaterial)
            .putInt(key+"begin",activeStep==null?0:activeStep.start)
            .putInt(key+"end",activeStep==null?0:activeStep.end)
            .putString(key+"group_stage",studyStage.name())
            .putInt(key+"return_cursor",returnVerseCursor).apply();
    }
    private boolean restoreTrainingSession(long id) {return restoreTrainingSession(id,false);}
    private boolean restoreTrainingSession(long id,boolean linked) {
        return restoreTrainingSession((linked?"linked_":"entry_")+id+"_",linked);
    }
    private boolean sameLessonDraft(SharedPreferences preferences,String key){
        return lessonSessionMaterial.equals(preferences.getString(key+"lesson_material",lessonSessionMaterial));
    }
    private boolean restoreTrainingSession(String key,boolean linked) {
        android.content.SharedPreferences pref=getSharedPreferences(SESSION_PREFS,MODE_PRIVATE);
        if(!pref.contains(key+"stage"))return false;
        try {
            studyStage=StudyPlanLogic.Stage.valueOf(pref.getString(key+"stage","VERSE"));
            trainingMode=TrainingMode.valueOf(pref.getString(key+"mode","READ"));
        }catch(IllegalArgumentException ex){studyStage=StudyPlanLogic.Stage.VERSE;trainingMode=TrainingMode.READ;}
        stepCursor=Math.max(0,pref.getInt(key+"step",0));
        mask=trainingMode==TrainingMode.HIDE?Math.max(0,Math.min(100,pref.getInt(key+"mask",0))):0;
        LessonExerciseState exercise=LessonExercisePreferences.read(pref,key,linked,groupIntroDone,groupBuildDone);
        blurSeed=exercise.blurSeed();lessonWriting.restore(exercise);
        lessonVariant=exercise.variant();lessonChunk=exercise.chunk();
        savedAssemblyFingerprint=exercise.assemblyFingerprint();lessonSeed=exercise.seed();
        rotationExerciseKey=exercise.rotationExerciseKey();rotationExerciseSeed=exercise.rotationExerciseSeed();
        rotationMask=exercise.rotationMask();
        lessonAssembly.restore(exercise.picked(),exercise.usedTiles());
        lessonPracticeAgain=false;
        groupIntroDone=exercise.groupIntroDone();groupBuildDone=exercise.groupBuildDone();
        checked=false;passed=false;testAssisted=false;lastAccuracy=-1;
        return true;
    }
    private void openTraining(long id) {
        if(screen==Screen.TRAIN)saveTrainingSession();
        answerReview=null;
        reviewMode=false;groupMode=false;forcedStep=null;currentReview=null;
        if(screen==Screen.CARD_SESSION)flashcards.save(selectedDeck);
        LocalStore.Entry source=store.entry(id);
        lessonSourceEntry=id;lessonSourceGroup=source==null?-1:source.groupId;
        selectedEntry=store.canonicalStudyEntryId(id);
        id=selectedEntry;
        LocalStore.Entry entry=store.entry(id);
        if(entry==null){toast("Отрывок не найден");screen=Screen.HOME;render();return;}
        lessonSessionMaterial=lessonMaterial(entry);
        lessonSourceMaterial=source==null||source.id==entry.id?"":lessonMaterial(source);
        android.content.SharedPreferences linked=getSharedPreferences(SESSION_PREFS,MODE_PRIVATE);
        String lk="linked_"+id+"_";
        if(linked.getBoolean(lk+"pending",false)){
            int a=linked.getInt(lk+"begin",0),b=linked.getInt(lk+"end",0);
            String stage=linked.getString(lk+"group_stage","CHAIN");
            if(a>=0&&b>a&&b<=entry.lines().size()&&
                !store.isStepMastered(id,stage,a,b)&&sameLessonDraft(linked,lk)){
                GroupLearningLogic.Candidate cand=GroupLearningLogic.candidate(stage,a,b,"Продолжить связку");
                returnVerseCursor=linked.getInt(lk+"return_cursor",0);
                prepareGroup(cand);restoreTrainingSession(id,true);
                returnVerseCursor=linked.getInt(lk+"return_cursor",0);render();return;
            }
            linked.edit().remove(lk+"pending").apply();
        }
        if(!sameLessonDraft(linked,"entry_"+id+"_")||!restoreTrainingSession(id)){
            selectRecommendation(entry);resetTrainingStep(entry);
        } else if(trainingMode!=TrainingMode.TYPE) {
            List<StudyPlanLogic.Step> steps=studySteps(entry);
            if(!steps.isEmpty()) {
                StudyPlanLogic.Step current=steps.get(Math.min(stepCursor,steps.size()-1));
                testAssisted=store.isStepMastered(entry.id,studyStage.name(),current.start,current.end);
            }
        }
        // If an old session points beyond the newly protected lesson boundary, restore safely.
        if(studyStage==StudyPlanLogic.Stage.VERSE)
            stepCursor=Math.min(stepCursor,store.unlockedPosition(id,entry.lines().size()));
        if(executingCollectionQueue && collectionQueueNewStep){
            // Revisit every record even if it was previously mastered. Do not erase progress.
            studyStage=StudyPlanLogic.Stage.VERSE;
            stepCursor=0;groupMode=false;forcedStep=null;
            checked=false;passed=false;lessonWriting.draft="";
            lessonWriting.hadFeedback=false;lessonPracticeAgain=false;
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
        studyStage=advice.stage;stepCursor=advice.index;
        return advice;
    }
    private boolean isSermon(LocalStore.Entry e) {
        return e.reference!=null && (e.reference.startsWith("Матфея 5:") ||
          e.reference.startsWith("Матфея 6:") || e.reference.startsWith("Матфея 7:"));
    }
    private List<StudyPlanLogic.Step> studySteps(LocalStore.Entry e) {
        if((reviewMode||groupMode)&&forcedStep!=null)return Collections.singletonList(forcedStep);
        return studySteps(e,studyStage);
    }
    private List<StudyPlanLogic.Step> studySteps(LocalStore.Entry e,StudyPlanLogic.Stage stage) {
        List<String> refs=new ArrayList<>();
        for(Bible.Verse v:e.lines())refs.add(v.ref);
        return StudyPlanLogic.build(refs,stage,isSermon(e));
    }
    private void resetTrainingStep(LocalStore.Entry e) {
        savedAssemblyFingerprint="";
        lessonVariant=0;lessonChunk=0;lessonAssembly.reset();lessonWriting.variant=0;groupBuildDone=false;groupIntroDone=false;
        lessonWriting.practiceWeakFragment=false;lessonWriting.weakFragmentText="";
        lessonSeed=new Random().nextLong();lessonPracticeAgain=false;
        rotationExerciseKey="";rotationMask="";rotationExerciseSeed=0L;
        lessonWriting.hadFeedback=false;
        mask=0;checked=false;passed=false;lessonWriting.draft="";lastAccuracy=-1;
        blurSeed=new Random().nextInt();testAssisted=false;
        boolean mastered=false;
        if(e!=null) {
            List<StudyPlanLogic.Step> steps=studySteps(e);
            if(!steps.isEmpty()) {
                stepCursor=Math.max(0,Math.min(stepCursor,steps.size()-1));
                StudyPlanLogic.Step s=steps.get(stepCursor);
                mastered=store.isStepMastered(e.id,studyStage.name(),s.start,s.end);
            }
        }
        int done=e!=null && studyStage==StudyPlanLogic.Stage.VERSE?
            store.exerciseDone(e.id,stepCursor):0;
        trainingMode=done>=4?TrainingMode.TYPE:
            done==3?TrainingMode.TYPE:done==2?TrainingMode.ORDER:
            done==1?TrainingMode.HIDE:TrainingMode.READ;
        if(done==1)mask=25;
        // A final examination becomes assisted only if a hint is viewed DURING that exam.
        if(reviewMode || (studyStage==StudyPlanLogic.Stage.VERSE&&done>=4))testAssisted=false;
    }
    private String texts(List<Bible.Verse> lines) {
        StringBuilder sb=new StringBuilder();
        for(Bible.Verse v:lines) {if(sb.length()>0)sb.append(' ');sb.append(v.text);}
        return sb.toString();
    }
    private void updateBlurText() {
        if(activePassage==null || activeEntry==null || activeStep==null)return;
        List<Bible.Verse> all=activeEntry.lines();
        android.text.SpannableStringBuilder text=new android.text.SpannableStringBuilder();
        int percentage=trainingMode==TrainingMode.HIDE?mask:0;
        try {
            for(Bible.Verse verse:all.subList(activeStep.start,activeStep.end)) {
                if(text.length()>0)text.append("\n\n");
                text.append(verse.ref+"   ");int offset=text.length();
                text.append(verse.text);
                for(int[] range:ReviewLogic.blurRanges(verse.text,percentage,verse.ref.hashCode()^blurSeed)) {
                    text.setSpan(new MaskFilterSpan(new BlurMaskFilter(ui.dp(9),BlurMaskFilter.Blur.NORMAL)),
                        offset+range[0],offset+range[1],android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            activePassage.setLayerType(percentage>0?View.LAYER_TYPE_SOFTWARE:View.LAYER_TYPE_NONE,null);
            activePassage.setText(text,TextView.BufferType.SPANNABLE);
            installTemporaryReveal(activePassage);
        } catch(RuntimeException unsupported) {
            // Unsupported device graphics must never crash the study activity.
            StringBuilder fallback=new StringBuilder();
            for(Bible.Verse verse:all.subList(activeStep.start,activeStep.end)) {
                if(fallback.length()>0)fallback.append("\n\n");
                fallback.append(verse.ref).append("   ")
                    .append(ReviewLogic.masked(verse.text,percentage,verse.ref.hashCode()^blurSeed));
            }
            activePassage.setLayerType(View.LAYER_TYPE_NONE,null);
            activePassage.setText(fallback.toString());
        }
        activePassage.setContentDescription(percentage>0?
            "Скрыто "+percentage+" процентов слов. Попробуйте их вспомнить.":text.toString());
    }
    /** Immediate reveal held until the ORIGINAL finger lifts, even when it moves away. */
    private void installTemporaryReveal(TextView view) {
        final MaskFilterSpan[] selected={null};
        final int[] begin={-1},end={-1},pointer={-1};
        final android.text.Spannable[] activeText={null};
        Runnable close=()->{
            if(selected[0]!=null && activeText[0]!=null && begin[0]>=0 &&
                end[0]<=activeText[0].length() && activeText[0].getSpanStart(selected[0])<0)
                activeText[0].setSpan(selected[0],begin[0],end[0],
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            selected[0]=null;activeText[0]=null;begin[0]=-1;end[0]=-1;pointer[0]=-1;
            view.invalidate();
            if(view.getParent()!=null)view.getParent().requestDisallowInterceptTouchEvent(false);
        };
        revealCleanup=close;
        view.setOnTouchListener((v,event)->{
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                close.run();
                if(!(view.getText() instanceof android.text.Spannable))return false;
                android.text.Spannable span=(android.text.Spannable)view.getText();
                int at=Math.max(0,Math.min(span.length(),view.getOffsetForPosition(event.getX(),event.getY())));
                MaskFilterSpan[] hits=span.getSpans(at,Math.min(span.length(),at+1),MaskFilterSpan.class);
                if(hits.length==0)return false;
                selected[0]=hits[0];begin[0]=span.getSpanStart(hits[0]);end[0]=span.getSpanEnd(hits[0]);
                if(begin[0]<0){close.run();return false;}
                activeText[0]=span;pointer[0]=event.getPointerId(0);
                span.removeSpan(selected[0]);view.invalidate();
                if(view.getParent()!=null)view.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if(selected[0]==null)return false;
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){close.run();return true;}
            if(action==MotionEvent.ACTION_POINTER_UP &&
                event.getPointerId(event.getActionIndex())==pointer[0]){close.run();return true;}
            // MOVE and a second finger never switch or prematurely dismiss the selected word.
            return true;
        });
    }
    /** Lightweight wrapping container for word tiles; uses Android SDK only. */
    private static final class WrapLayout extends ViewGroup {
        private final int spacing;
        private final boolean centered;
        WrapLayout(Context context,int spacing){this(context,spacing,false);}
        WrapLayout(Context context,int spacing,boolean centered){
            super(context);this.spacing=spacing;this.centered=centered;
        }
        @Override protected void onMeasure(int ws,int hs) {
            int width=MeasureSpec.getSize(ws),available=Math.max(1,width-getPaddingLeft()-getPaddingRight());
            int x=0,y=0,lineHeight=0;
            for(int i=0;i<getChildCount();i++) {
                View child=getChildAt(i);if(child.getVisibility()==GONE)continue;
                measureChild(child,ws,hs);
                int cw=child.getMeasuredWidth(),ch=child.getMeasuredHeight();
                if(x>0 && x+cw>available){y+=lineHeight+spacing;x=0;lineHeight=0;}
                x+=cw+spacing;lineHeight=Math.max(lineHeight,ch);
            }
            setMeasuredDimension(resolveSize(width,ws),resolveSize(y+lineHeight+getPaddingTop()+getPaddingBottom(),hs));
        }
        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
            int available=Math.max(1,right-left-getPaddingLeft()-getPaddingRight());
            int y=getPaddingTop();
            for(int start=0;start<getChildCount();){
                int end=start,rowWidth=0,rowHeight=0;
                while(end<getChildCount()){
                    View child=getChildAt(end);
                    if(child.getVisibility()==GONE){end++;continue;}
                    int width=child.getMeasuredWidth();
                    int nextWidth=rowWidth==0?width:rowWidth+spacing+width;
                    if(rowWidth>0&&nextWidth>available)break;
                    rowWidth=nextWidth;
                    rowHeight=Math.max(rowHeight,child.getMeasuredHeight());
                    end++;
                }
                int x=getPaddingLeft()+(centered?Math.max(0,(available-rowWidth)/2):0);
                for(int i=start;i<end;i++){
                    View child=getChildAt(i);
                    if(child.getVisibility()==GONE)continue;
                    int width=child.getMeasuredWidth();
                    child.layout(x,y,x+width,y+child.getMeasuredHeight());
                    x+=width+spacing;
                }
                y+=rowHeight+spacing;
                start=end;
            }
        }
    }
    private TextView wordTile(String word,int bg,int color,int stroke,Runnable tapped) {
        TextView tv=ui.txt(word,16,color,true);
        tv.setMinHeight(ui.dp(43));tv.setGravity(Gravity.CENTER);
        tv.setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8));tv.setBackground(ui.outlined(bg,12,stroke));
        if(tapped!=null){tv.setClickable(true);tv.setFocusable(true);tv.setOnClickListener(v->tapped.run());}
        return tv;
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
        if(!groupMode) returnVerseCursor=studyStage==StudyPlanLogic.Stage.VERSE?stepCursor:
            store.firstUnmastered(selectedEntry,store.entry(selectedEntry).lines().size());
        reviewMode=false;groupMode=true;currentReview=null;
        studyStage=StudyPlanLogic.Stage.valueOf(group.stage);
        forcedStep=new StudyPlanLogic.Step(group.start,group.end,"Связка "+(group.start+1)+"–"+group.end);
        stepCursor=0;mask=0;lessonWriting.draft="";checked=false;passed=false;
        lessonVariant=0;lessonChunk=0;savedAssemblyFingerprint="";
        testAssisted=false;lessonWriting.hadFeedback=false;lessonPracticeAgain=false;
        lessonWriting.variant=0;groupBuildDone=false;groupIntroDone=false;lessonWriting.practiceWeakFragment=false;lessonWriting.weakFragmentText="";resetLessonTiles();
        activeStep=forcedStep;
        answerReview=null;
        trainingMode=TrainingMode.ORDER;screen=Screen.TRAIN;
    }
    /** Separate full-screen lesson: no card carousel or persistent app-tab bar. */
    private void renderUnlearnableLine(LocalStore.Entry entry,int invalidLine){
        root=ui.column();root.setBackground(ui.pageBackground());
        root.setPadding(ui.dp(22),ui.dp(24),ui.dp(22),ui.dp(24));
        currentScroller=null;
        activeTextArea=null;activePassage=null;
        LinearLayout panel=ui.card(root);
        ui.add(panel,ui.displayText("Текст требует исправления",22),0);
        ui.helper(panel,CustomStudyText.message(invalidLine)+". Исправь строку, чтобы продолжить обучение.");
        ui.action(panel,"Изменить текст",true,()->{
            onBackPressed();
            editCustomStudyEntry(entry);
        });
        ui.action(panel,"Выйти из урока",false,this::onBackPressed);
        setContentView(root);
    }

    private void renderFullscreenLesson() {
        LocalStore.Entry e=store.entry(selectedEntry);
        if(e==null){screen=Screen.HOME;render();return;}
        List<StudyPlanLogic.Step> steps=studySteps(e);
        if(steps.isEmpty()){
            LocalStore.UnifiedCollection u=store.unifiedForGroup(e.groupId);
            screen=u==null?Screen.HOME:Screen.COLLECTION_DETAIL;
            if(u!=null)openUnifiedId=u.id;render();return;
        }
        stepCursor=Math.max(0,Math.min(stepCursor,steps.size()-1));
        activeEntry=e;activeStep=steps.get(stepCursor);
        LessonCurriculumLogic.Phase phase=checked&&passed?
            LessonCurriculumLogic.Phase.COMPLETE:lessonPhase(e);
        if(phase!=LessonCurriculumLogic.Phase.WRITE)answerReview=null;
        if(phase==LessonCurriculumLogic.Phase.COMPLETE){renderCompletion(e);return;}
        int invalidLine=CustomStudyText.firstUnlearnable(e.lines(),activeStep.start,activeStep.end);
        if(invalidLine>=0){renderUnlearnableLine(e,invalidLine);return;}
        boolean assembling=phase==LessonCurriculumLogic.Phase.BUILD;
        root=ui.column();root.setBackground(assembling?assemblyBackground():ui.pageBackground());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout top=ui.column();top.setPadding(ui.dp(22),ui.dp(15),ui.dp(22),ui.dp(6));
        LinearLayout topRow=ui.row();
        TextView close=ui.txt("×",30,WHITE,false);close.setGravity(Gravity.CENTER);
        close.setContentDescription("Выйти из урока");
        close.setOnClickListener(v->onBackPressed());
        // SV_DEVELOPER_MODE_UPDATE_1: long-press the close icon for debugging.
        close.setOnLongClickListener(v->{
            final int bookId=bookForEntry(e);
            DeveloperModeController.open(this,e,
                chapter->bookId>0 && bible.isReady()?bible.chapter(bookId,chapter).size():0,
                store.masteryCount(e.id));
            return true;
        });
        topRow.addView(close,new LinearLayout.LayoutParams(ui.dp(45),ui.dp(45)));
        if(assembling){
            close.setTextColor(BUILD_INK);
            close.setBackground(ui.outlined(0xFFFFF1E2,24,0xFFFFF9F0));
            close.setElevation(ui.dp(2));
            TextView title=ui.displayText("Сборка",27);
            title.setTextColor(BUILD_INK);
            title.setGravity(Gravity.CENTER);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            topRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            if(!DeveloperModeController.isEnabled(this))
                topRow.addView(new View(this),new LinearLayout.LayoutParams(ui.dp(45),ui.dp(45)));
        }else topRow.addView(new View(this),new LinearLayout.LayoutParams(0,ui.dp(45),1));
        if(DeveloperModeController.isEnabled(this)){
            TextView devButton=ui.txt("DEV",12,GREEN,true);
            devButton.setGravity(Gravity.CENTER);
            devButton.setContentDescription("Открыть режим разработчика");
            devButton.setOnClickListener(v->{
                final int bookId=bookForEntry(e);
                DeveloperModeController.open(this,e,
                    chapter->bookId>0 && bible.isReady()?bible.chapter(bookId,chapter).size():0,
                    store.masteryCount(e.id));
            });
            topRow.addView(devButton,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(45)));
        }
        top.addView(topRow);
        lessonHotbarStrip=null;
        root.addView(top,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setClipToPadding(false);currentScroller=scroll;
        content=ui.column();content.setPadding(ui.dp(20),ui.dp(11),ui.dp(20),ui.dp(18));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView mainAction;
        int number=stepCursor+1;
        if(phase==LessonCurriculumLogic.Phase.INTRO){
            lessonPhaseHeader("Ознакомление",e);
            renderLessonIntro();
            mainAction=ui.button("Продолжить",true,()->{
                if(groupMode)groupIntroDone=true;
                else store.finishExercise(e.id,activeStep.start,1);
                lessonVariant=0;lessonChunk=0;lessonWriting.variant=0;resetLessonTiles();mask=0;
                saveTrainingSession();render();
            });
        } else if(phase==LessonCurriculumLogic.Phase.BUILD){
            trainingMode=TrainingMode.ORDER;
            assemblyReference(e);
            boolean complete=renderLessonBuild(e);
            mainAction=ui.button("Продолжить",true,()->{
                if(lessonAssembly.picked()>=lessonBuildRequired)advanceLessonBuild(e);
            });
            mainAction.setTypeface(Typeface.create("serif",Typeface.BOLD));
            mainAction.setTextSize(22);
            mainAction.setElevation(ui.dp(3));
            styleAssemblyContinue(mainAction,complete);
            lessonBuildAction=mainAction;
        } else if(phase==LessonCurriculumLogic.Phase.WRITE){
            trainingMode=TrainingMode.TYPE;
            if(answerReview!=null){
                renderAnswerReview(answerReview);
                mainAction=ui.button("Написать ещё раз",true,this::retryLessonWriting);
            }else{
                lessonPhaseHeader("Написание",e);
                renderLessonWriting();
                mainAction=ui.button("Проверить",true,()->validateLessonWriting(e));
            }
        } else {
            renderCompletion(e);
            return;
        }
        renderLessonHotbar(top,phase,e);
        LinearLayout footer=ui.column();footer.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(22));
        ui.animateLessonPress(mainAction);
        mainAction.setMinimumHeight(ui.dp(58));
        footer.addView(mainAction,new LinearLayout.LayoutParams(-1,assembling||answerReview!=null?-2:ui.dp(58)));
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
    }

    private String lessonFullReference(LocalStore.Entry e){
        if(e.lines().isEmpty())return e.reference;
        int book=bookForEntry(e);
        if(book<1)return e.reference==null?e.title:e.reference;
        String a=e.lines().get(activeStep.start).ref;
        String b=e.lines().get(activeStep.end-1).ref;
        try{
            String[] first=a.split(":",2),last=b.split(":",2);
            return Bible.reference(book,Integer.parseInt(first[0]),Integer.parseInt(first[1]),
                Integer.parseInt(last[0]),Integer.parseInt(last[1]));
        }catch(Exception ex){return e.reference;}
    }
    /** Celebratory page: the approved crown/check composition in the same warm scenic UI. */
    private void renderCompletion(LocalStore.Entry e){
        root=ui.column();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        FrameLayout scenery=new FrameLayout(this);
        ImageView image=new ImageView(this);
        image.setImageResource(landscapeFor(e.id));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        scenery.addView(image,new FrameLayout.LayoutParams(-1,-1));
        View haze=new View(this);
        haze.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFFFFF2E9,0xFEFFF0E6,0xDFFFF0E5,0x7DFFF0E4,0x77FFF0E4}));
        scenery.addView(haze,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout central=ui.column();central.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);
        central.setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(12));
        root.addView(central,new LinearLayout.LayoutParams(-1,0,1));
        TextView crown=ui.txt("👑",34,GOLD,true);crown.setGravity(Gravity.CENTER);
        central.addView(crown,new LinearLayout.LayoutParams(ui.dp(75),ui.dp(65)));
        TextView reference=ui.displayText(lessonFullReference(e),21);
        reference.setGravity(Gravity.CENTER);reference.setPadding(ui.dp(14),ui.dp(6),ui.dp(14),ui.dp(6));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,-2);
        rp.topMargin=ui.dp(12);central.addView(reference,rp);
        TextView heading=ui.displayText(groupMode?"Связка освоена!":"Стих освоен!",38);
        heading.setGravity(Gravity.CENTER);heading.setTextColor(0xFF35452E);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.topMargin=ui.dp(14);central.addView(heading,hp);
        SuccessArt art=new SuccessArt();
        int availableArt=Math.min(270,Math.max(140,
            Math.round(getResources().getDisplayMetrics().heightPixels /
                getResources().getDisplayMetrics().density)-410));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,ui.dp(availableArt));
        ap.topMargin=ui.dp(10);central.addView(art,ap);
        LinearLayout footer=ui.column();footer.setPadding(ui.dp(24),ui.dp(14),ui.dp(24),ui.dp(25));
        TextView continueButton=ui.button("Продолжить",true,()->advanceAfterSuccess(e));
        ui.animateLessonPress(continueButton);
        continueButton.setTextSize(20);
        footer.addView(continueButton,new LinearLayout.LayoutParams(-1,ui.dp(61)));
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        scenery.addView(root,new FrameLayout.LayoutParams(-1,-1));
        setContentView(scenery);
        reference.setAlpha(0f);heading.setAlpha(0f);
        if(ValueAnimator.areAnimatorsEnabled()){
            reference.animate().alpha(1f).setDuration(170).start();
            heading.animate().alpha(1f).setStartDelay(140).setDuration(180).start();
            art.start();
        }else{reference.setAlpha(1f);heading.setAlpha(1f);art.setFinished();}
    }
    private final class SuccessArt extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float reveal=0f;
        SuccessArt(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void start(){ValueAnimator animator=ValueAnimator.ofFloat(0f,1f);
            animator.setStartDelay(270);animator.setDuration(670);
            animator.addUpdateListener(a->{reveal=(float)a.getAnimatedValue();invalidate();});animator.start();}
        void setFinished(){reveal=1f;invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float scale=Math.min(getWidth()/350f,getHeight()/300f);
            float cx=getWidth()/2f,cy=getHeight()/2f;
            c.save();c.translate(cx,cy);c.scale(scale,scale);
            paint.setShader(new RadialGradient(0,0,145,new int[]{0xAA6BB37B,0x5567A86E,0x00FFF0E5},null,Shader.TileMode.CLAMP));
            c.drawCircle(0,0,145,paint);paint.setShader(null);
            paint.setColor(0x444ED383);c.drawCircle(0,0,105,paint);
            paint.setShader(new LinearGradient(-84,-90,80,94,0xFFB9D47A,0xFF4E9464,Shader.TileMode.CLAMP));
            c.drawCircle(0,0,91,paint);paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.3f);
            paint.setColor(0xFFB1FAC7);c.drawCircle(0,0,90,paint);
            paint.setStrokeWidth(13f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Color.WHITE);
            Path tick=new Path();tick.moveTo(-36,0);tick.lineTo(-10,25);tick.lineTo(44,-33);
            Path partial=new Path();PathMeasure pm=new PathMeasure(tick,false);
            pm.getSegment(0,pm.getLength()*Math.max(0,reveal),partial,true);c.drawPath(partial,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFD565);
            sparkle(c,-113,-85,13);sparkle(c,116,78,12);sparkle(c,-139,-36,7);
            paint.setColor(0xFF7CF3AB);
            for(int i=0;i<3;i++){
                c.save();c.rotate(-22+i*20,-130,60);c.drawRoundRect(-133,45+i*15,-128,60+i*15,2,2,paint);c.restore();
                c.save();c.rotate(-15+i*20,120,-70);c.drawRoundRect(118,-104+i*17,123,-86+i*17,2,2,paint);c.restore();
            }
            c.restore();
        }
        private void sparkle(Canvas c,float x,float y,float r){
            Path p=new Path();p.moveTo(x,y-r);p.quadTo(x+r*.18f,y-r*.2f,x+r,y);
            p.quadTo(x+r*.2f,y+r*.18f,x,y+r);p.quadTo(x-r*.18f,y+r*.18f,x-r,y);
            p.quadTo(x-r*.2f,y-r*.2f,x,y-r);c.drawPath(p,paint);
        }
    }
    private void advanceAfterSuccess(LocalStore.Entry e){
        if(reviewMode){
            applyLessonDestination(e,LessonNavigationFlow.afterSuccess(true,false,null,0,0,false,false));
            return;
        }
        boolean returnedFromGroup=groupMode;
        GroupLearningLogic.Candidate pending;
        int nextVerse;
        if(returnedFromGroup){
            getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit()
                .remove("linked_"+selectedEntry+"_pending").apply();
            groupMode=false;forcedStep=null;studyStage=StudyPlanLogic.Stage.VERSE;
            stepCursor=returnVerseCursor;resetTrainingStep(e);saveTrainingSession();
            pending=pendingAfterCurrent(e,stepCursor);
            nextVerse=stepCursor+1;
        }else{
            pending=pendingAfterCurrent(e,activeStep.start);
            nextVerse=activeStep.end;
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
                stepCursor=decision.verse();studyStage=StudyPlanLogic.Stage.VERSE;
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
        List<Bible.Verse> passage=e.lines().subList(activeStep.start,activeStep.end);
        List<String> source=new ArrayList<>();
        for(Bible.Verse line:passage)source.add(line.text);
        List<LessonCurriculumLogic.Variant> variants=LessonCurriculumLogic.variants(
            ReviewLogic.words(texts(passage)).size());
        int buildTotal=0,buildDone=0;
        boolean assembled=phase==LessonCurriculumLogic.Phase.BUILD&&
            lessonBuildRequired>0&&lessonAssembly.picked()==lessonBuildRequired;
        for(int i=0;i<variants.size();i++){
            int count=Math.max(1,lessonChunks(source,variants.get(i)).size());
            buildTotal+=count;
            if(i<lessonVariant)buildDone+=count;
            else if(i==lessonVariant)
                buildDone+=Math.min(lessonChunk,count-1)+(assembled?1:0);
        }
        int writingTotal=Math.max(1,writingVariants(e).size());
        // In a scheduled review the lesson legitimately starts at writing.
        LessonStageFlow.State state=lessonStageState(e);
        return LessonHotbarLogic.calculate(phase.ordinal(),1,buildTotal,writingTotal,
            state.introDone(),buildDone,lessonWriting.variant,state.writingOnly());
    }
    /** Use the same compact phase-based progress strip throughout the lesson. */
    private void renderLessonHotbar(LinearLayout top,LessonCurriculumLogic.Phase phase,
                                     LocalStore.Entry e){
        LessonHotbarLogic.Model model=lessonHotbarModel(phase,e);
        LinearLayout bar=ui.column();bar.setTag("lesson-progress");
        lessonHotbarStrip=new LessonSegmentedBar(model);
        lessonHotbarStrip.setTag("lesson-progress-strip");
        bar.setBackground(ui.shape(0xBFFFF8EE,12));
        bar.setPadding(ui.dp(4),ui.dp(3),ui.dp(4),ui.dp(3));
        bar.addView(lessonHotbarStrip,new LinearLayout.LayoutParams(-1,ui.dp(9)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ui.dp(190),-2);
        lp.gravity=Gravity.CENTER_HORIZONTAL;
        lp.topMargin=ui.dp(8);
        top.addView(bar,lp);
    }

    /** One segment per scheduled phase; each fills according to its completed tasks. */
    private final class LessonSegmentedBar extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private LessonHotbarLogic.Model model;
        private final float[] fill=new float[3];
        private ValueAnimator fillAnimator;
        LessonSegmentedBar(LessonHotbarLogic.Model model){
            super(MainActivity.this);
            setModel(model);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        void setModel(LessonHotbarLogic.Model value){
            if(model==null||!Arrays.equals(value.finished,model.finished)||
                    !Arrays.equals(value.totals,model.totals)){
                if(fillAnimator!=null)fillAnimator.cancel();
                float[] target=new float[3];
                for(int i=0;i<3;i++)if(value.totals[i]>0)target[i]=value.finished[i]/(float)value.totals[i];
                if(model!=null&&isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled()){
                    float[] start=fill.clone();
                    fillAnimator=ValueAnimator.ofFloat(0f,1f);
                    fillAnimator.setDuration(190);
                    fillAnimator.setInterpolator(new DecelerateInterpolator());
                    fillAnimator.addUpdateListener(animation->{
                        float fraction=(float)animation.getAnimatedValue();
                        for(int i=0;i<3;i++)fill[i]=start[i]+(target[i]-start[i])*fraction;
                        invalidate();
                    });
                    fillAnimator.start();
                }else System.arraycopy(target,0,fill,0,fill.length);
            }
            model=value;
            String[] names={"Читай","Собери","Напиши"};
            setContentDescription(names[model.activePhase]+". Текущее задание "+model.currentNumber+
                " из "+model.total+". Выполнено "+model.completedCount+" из "+model.total);
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(model.total<=0||getWidth()<=0)return;
            drawLessonPhases(canvas);
        }
        private void drawLessonPhases(Canvas canvas){
            int groups=0;
            for(int total:model.totals)if(total>0)groups++;
            if(groups==0)return;
            float gap=ui.dp(4),width=(getWidth()-gap*(groups-1))/groups;
            if(width<=0)return;
            float radius=getHeight()/2f,left=0f;
            for(int i=0;i<3;i++){
                if(model.totals[i]==0)continue;
                paint.setColor(0xFFD2C5B7);
                canvas.drawRoundRect(left,0,left+width,getHeight(),radius,radius,paint);
                if(fill[i]>0f){
                    paint.setColor(0xFF648C3C);
                    canvas.drawRoundRect(left,0,left+width*fill[i],getHeight(),radius,radius,paint);
                }
                left+=width+gap;
            }
        }
        @Override protected void onDetachedFromWindow(){
            if(fillAnimator!=null)fillAnimator.cancel();
            super.onDetachedFromWindow();
        }
        @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(android.widget.ProgressBar.class.getName());
            if(model.total>0)info.setRangeInfo(android.view.accessibility.AccessibilityNodeInfo.RangeInfo.obtain(
                android.view.accessibility.AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,0,model.total,model.completedCount));
        }
    }
    /** Refresh progress without changing exercise state or rendering captions. */
    private void refreshLessonPhaseProgress(LessonCurriculumLogic.Phase phase,LocalStore.Entry e){
        if(lessonHotbarStrip==null)return;
        lessonHotbarStrip.setModel(lessonHotbarModel(phase,e));
    }
    private LessonStageFlow.State lessonStageState(LocalStore.Entry e){
        return new LessonStageFlow.State(studyStage,groupMode?0:store.exerciseDone(e.id,activeStep.start),
            reviewMode,groupMode,executingCollectionQueue,lessonPracticeAgain,groupIntroDone,groupBuildDone);
    }
    private LessonCurriculumLogic.Phase lessonPhase(LocalStore.Entry e){
        return lessonStageState(e).phase();
    }
    private void lessonPhaseHeader(String stage,LocalStore.Entry e){
        TextView pill=ui.txt(stage,15,0xFFAA563F,true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(ui.dp(17),ui.dp(8),ui.dp(17),ui.dp(8));
        pill.setBackground(ui.shape(0xFFF9E1D2,24));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,-2);
        pp.gravity=Gravity.CENTER_HORIZONTAL;pp.topMargin=ui.dp(9);
        content.addView(pill,pp);
        addLessonReference(e);
    }
    private void addLessonReference(LocalStore.Entry e){
        String ref=lessonFullReference(e);
        int book=bookForEntry(e);
        int ink=book<1?GREEN:ui.bookInk(book);
        TextView cite=ui.displayText(ref,20);
        cite.setTextColor(ink);cite.setGravity(Gravity.CENTER);
        ui.add(content,cite,17);
    }

    private void renderLessonIntro(){
        activeTextArea=content;
        activePassage=ui.prose("",22);
        activePassage.setLineSpacing(ui.dp(7),1.18f);
        activePassage.setTextIsSelectable(false);
        ui.add(content,activePassage,19);
        // Both hints are measured at the actual content width, including font scale.
        LessonIntroHint hint=new LessonIntroHint();
        ui.add(content,hint,15);
        trainingMode=mask>0?TrainingMode.HIDE:TrainingMode.READ;
        hint.show(mask>0,false);
        updateBlurText();
        TextView hide=ui.button(mask==100?"Показать все слова":
            "Скрыть "+(mask+25)+"% слов",false,()->{});
        hide.setOnClickListener(v->{
            mask=mask>=100?0:mask+25;
            trainingMode=mask>0?TrainingMode.HIDE:TrainingMode.READ;
            updateBlurText();
            hint.show(mask>0,true);
            refreshLessonPhaseProgress(LessonCurriculumLogic.Phase.INTRO,activeEntry);
            hide.setText(mask==100?"Показать все слова":"Скрыть "+(mask+25)+"% слов");
            saveTrainingSession();
        });
        ui.animateLessonPress(hide);
        ui.add(content,hide,27);
        // No extra instructional caption: gesture behavior remains unchanged.
    }
    /** The taller of the two hints determines the height at every screen width.
     * INVISIBLE children still participate in measurement, unlike GONE children. */
    private final class LessonIntroHint extends FrameLayout {
        private final TextView readHint,holdHint;
        private boolean hidden;

        LessonIntroHint(){
            super(MainActivity.this);
            setTag("lesson-intro-hint");
            setMeasureAllChildren(true);
            readHint=ui.txt(LearningHelp.INTRO_READ,14,MUTED,false);
            holdHint=ui.txt(LearningHelp.INTRO_HOLD,14,MUTED,false);
            addView(readHint,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
            addView(holdHint,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
            holdHint.setVisibility(View.INVISIBLE);
        }

        void show(boolean wordsHidden,boolean animate){
            if(wordsHidden==hidden)return;
            hidden=wordsHidden;
            TextView outgoing=hidden?readHint:holdHint;
            TextView incoming=hidden?holdHint:readHint;
            outgoing.animate().cancel();
            incoming.animate().cancel();
            outgoing.setVisibility(View.INVISIBLE);
            outgoing.setAlpha(1f);
            boolean fade=animate&&ValueAnimator.areAnimatorsEnabled();
            incoming.setAlpha(fade?0f:1f);
            incoming.setVisibility(View.VISIBLE);
            if(fade)incoming.animate().alpha(1f).setDuration(150)
                .setInterpolator(new DecelerateInterpolator()).start();
        }

        @Override protected void onDetachedFromWindow(){
            readHint.animate().cancel();
            holdHint.animate().cancel();
            readHint.setAlpha(1f);
            holdHint.setAlpha(1f);
            super.onDetachedFromWindow();
        }
    }
    private void resetLessonTiles(){
        lessonAssembly.reset();lessonSeed=new Random().nextLong();
    }
    private final class BuildSlot extends TextView {
        private final String token;
        private final SpannableString hidden;
        private boolean filled;

        BuildSlot(String token){
            super(MainActivity.this);
            this.token=token;
            hidden=new SpannableString(token);
            for(int[] range:ReviewLogic.blurRanges(token,100,0))
                hidden.setSpan(new ForegroundColorSpan(Color.TRANSPARENT),range[0],range[1],
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            setTextSize(18);
            setTypeface(Typeface.create("serif",Typeface.BOLD));
            setTextColor(BUILD_INK);
            setPadding(ui.dp(10),ui.dp(8),ui.dp(10),ui.dp(8));
            setGravity(Gravity.CENTER);
            setMinWidth(ui.dp(56));
            setMinHeight(ui.dp(43));
            showWord(false);
        }

        void showWord(boolean filled){
            this.filled=filled;
            setText(filled?token:hidden);
            GradientDrawable frame=ui.shape(filled?0xFFE9EDDA:0x44FFF9EF,12);
            if(filled)frame.setStroke(ui.dp(1),0xFFCCD6B5);
            else frame.setStroke(ui.dp(1),0xFFBEB0A0,ui.dp(5),ui.dp(4));
            setBackground(frame);
            setContentDescription(filled?null:"Пропущенное слово");
        }

        @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(info);
            if(!filled)info.setText(null);
        }
    }

    private void assemblyReference(LocalStore.Entry entry){
        LinearLayout reference=ui.row();
        reference.setGravity(Gravity.CENTER);
        reference.setPadding(ui.dp(15),ui.dp(8),ui.dp(15),ui.dp(8));
        reference.setBackground(ui.outlined(0xFFFFEBD5,26,0xFFFFF8EF));
        reference.setElevation(ui.dp(2));
        reference.addView(new NavigationGlyph(this,"book",BUILD_INK),new LinearLayout.LayoutParams(ui.dp(28),ui.dp(27)));
        TextView label=ui.displayText(lessonFullReference(entry),18);
        label.setTextColor(BUILD_INK);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(-2,-2);
        textParams.leftMargin=ui.dp(8);
        reference.addView(label,textParams);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);
        params.gravity=Gravity.CENTER_HORIZONTAL;
        content.addView(reference,params);
    }

    private void styleAssemblyContinue(TextView action,boolean enabled){
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,enabled?
            new int[]{0xFF7D9C49,0xFF3E692D}:new int[]{0xFFC3C8A7,0xFFA1AA83});
        background.setCornerRadius(ui.dp(32));
        background.setStroke(ui.dp(1),0xFFFFF9EA);
        action.setBackground(background);
        action.setTextColor(Color.WHITE);
        action.setEnabled(enabled);
    }

    private android.graphics.drawable.Drawable assemblyBackground(){
        return new android.graphics.drawable.Drawable(){
            private final android.graphics.drawable.Drawable scenery=getDrawable(R.drawable.hero_sunrise);
            private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            private int horizon;
            private Shader sky,fade;
            @Override protected void onBoundsChange(Rect bounds){
                int height=bounds.height(),width=bounds.width();
                if(height<=0||width<=0)return;
                horizon=bounds.top+Math.round(height*.52f);
                int sceneryHeight=bounds.bottom-horizon;
                float aspect=scenery.getIntrinsicWidth()/(float)scenery.getIntrinsicHeight();
                int drawWidth=Math.max(width,Math.round(sceneryHeight*aspect));
                int drawHeight=Math.round(drawWidth/aspect);
                int left=bounds.centerX()-drawWidth/2;
                scenery.setBounds(left,bounds.bottom-drawHeight,left+drawWidth,bounds.bottom);
                sky=new LinearGradient(0,bounds.top,0,bounds.bottom,
                    new int[]{0xFFF5E7E2,0xFFFFEBD5,0xFFF9D9A7},null,Shader.TileMode.CLAMP);
                fade=new LinearGradient(0,horizon,0,bounds.bottom,
                    new int[]{0xFFFFEBD5,0xA8FFEAD1,0x30FFE5BC,0x58FFE0A8},
                    new float[]{0f,.32f,.72f,1f},Shader.TileMode.CLAMP);
            }
            @Override public void draw(Canvas canvas){
                if(sky==null)return;
                Rect bounds=getBounds();
                paint.setShader(sky);
                canvas.drawRect(bounds,paint);
                canvas.save();
                canvas.clipRect(bounds.left,horizon,bounds.right,bounds.bottom);
                scenery.draw(canvas);
                paint.setShader(fade);
                canvas.drawRect(bounds,paint);
                canvas.restore();
            }
            @Override public void setAlpha(int alpha){}
            @Override public void setColorFilter(android.graphics.ColorFilter filter){}
            @Override public int getOpacity(){return android.graphics.PixelFormat.OPAQUE;}
        };
    }

    /** Allow longer fragments where only a few words must be chosen. The answer
     * and the entire word bank are estimated together, not by word count alone. */
    private List<String[]> lessonChunks(List<String> lines,LessonCurriculumLogic.Variant kind){
        android.util.DisplayMetrics d=getResources().getDisplayMetrics();
        float widthDp=d.widthPixels/d.density;
        // Use stable screen dimensions; the previous root can still be attached
        // while a new lesson is rendering, and its height may not be final.
        float heightDp=d.heightPixels/d.density;
        float fontScale=Math.max(1f,getResources().getConfiguration().fontScale);
        String key=selectedEntry+":"+studyStage+":"+
            (activeStep==null?-1:activeStep.start)+":"+
            (activeStep==null?-1:activeStep.end)+":"+lines.hashCode()+":"+
            d.widthPixels+"x"+d.heightPixels+":"+d.scaledDensity+":"+fontScale;
        if(!key.equals(lessonChunkCacheKey)){
            lessonChunkCache.clear();
            lessonChunkCacheKey=key;
        }
        List<String[]> cached=lessonChunkCache.get(kind);
        if(cached!=null)return cached;
        int maximum=kind==LessonCurriculumLogic.Variant.GAPS?26:
            kind==LessonCurriculumLogic.Variant.ENDING?22:18;
        // A short or narrow display needs fewer words; on larger phones keep the
        // approved caps (26 gaps / 22 partial / 18 full) if the text itself fits.
        float scale=Math.min(1f,Math.min(widthDp/360f,heightDp/780f))/fontScale;
        int limit=Math.max(6,Math.min(maximum,Math.round(maximum*scale)));
        Paint font=new Paint(Paint.ANTI_ALIAS_FLAG);
        font.setTypeface(Typeface.create("serif",Typeface.BOLD));
        font.setTextSize(18f*d.scaledDensity);
        float availableAnswer=Math.max(190f,widthDp-68f);
        float availableBank=Math.max(190f,widthDp-52f);
        // Reserve the header, progress, reference, bottom action and safe insets.
        float availableHeight=Math.max(205f,heightDp-285f);
        List<String[]> best=null;
        // Search the usable budget in O(log N) fragment evaluations instead of
        // measuring every candidate. This matters for long verses like Esther 8:9.
        int low=6,high=limit;
        while(low<=high){
            int budget=(low+high)/2;
            List<String[]> chunks=WordOrderLogic.chunksForBudget(lines,budget);
            boolean fits=true;
            for(String[] chunk:chunks){
                if(!assemblyChunkFits(chunk,kind,font,d.density,availableAnswer,
                    availableBank,availableHeight,fontScale)){
                    fits=false;break;
                }
            }
            if(fits){best=chunks;low=budget+1;}
            else high=budget-1;
        }
        // On very small devices, the existing ScrollView remains the safety net.
        if(best==null)best=WordOrderLogic.chunksForBudget(lines,6);
        lessonChunkCache.put(kind,best);
        return best;
    }
    private boolean assemblyChunkFits(String[] words,LessonCurriculumLogic.Variant kind,
            Paint font,float density,float answerWidth,float bankWidth,
            float heightBudget,float fontScale){
        int n=words.length;
        if(n==0)return true;
        float[] textWidths=new float[n];
        for(int i=0;i<n;i++)textWidths[i]=font.measureText(words[i])/density;
        if(kind==LessonCurriculumLogic.Variant.ENDING){
            // The seed switches which half is hidden between exercises. Account
            // for both layouts so the chunk boundaries never depend on that seed.
            int midpoint=Math.max(1,n/2);
            boolean[] first=new boolean[n],last=new boolean[n];
            for(int i=0;i<n;i++){
                first[i]=i<midpoint;
                last[i]=i>=midpoint;
            }
            return Math.max(assemblyEstimatedHeight(textWidths,first,false,
                answerWidth,bankWidth,fontScale),
                assemblyEstimatedHeight(textWidths,last,false,
                answerWidth,bankWidth,fontScale))<=heightBudget;
        }
        boolean[] missing=new boolean[n];
        if(kind==LessonCurriculumLogic.Variant.FULL){
            Arrays.fill(missing,true);
        }else{
            int selections=Math.max(1,(int)Math.ceil(n*.4));
            for(int i=0;i<selections;i++)missing[i*n/selections]=true;
        }
        return assemblyEstimatedHeight(textWidths,missing,
            kind==LessonCurriculumLogic.Variant.GAPS,
            answerWidth,bankWidth,fontScale)<=heightBudget;
    }
    private float assemblyEstimatedHeight(float[] textWidths,boolean[] missing,
            boolean conservativeBank,float answerWidth,float bankWidth,float fontScale){
        int n=textWidths.length,selections=0;
        for(boolean gap:missing)if(gap)selections++;
        float[] answer=new float[n],bank=new float[selections];
        for(int i=0;i<n;i++)
            answer[i]=missing[i]?Math.max(56f,textWidths[i]+20f):textWidths[i]+8f;
        if(conservativeBank){
            // Gaps rotate; size the bank for the longest plausible selected words.
            float[] longest=textWidths.clone();
            Arrays.sort(longest);
            for(int i=0;i<selections;i++)bank[i]=longest[n-1-i]+24f;
        }else{
            int index=0;
            for(int i=0;i<n;i++)if(missing[i])bank[index++]=textWidths[i]+24f;
        }
        float lineHeight=Math.max(43f,24f*fontScale+18f);
        float answerHeight=assemblyRows(answer,answerWidth,7f)*(lineHeight+7f)+27f;
        float bankHeight=assemblyRows(bank,bankWidth,8f)*(lineHeight+8f)+6f;
        // Reserve space for inter-card spacing and slight font metric differences.
        return answerHeight+bankHeight+32f;
    }
    private static int assemblyRows(float[] widths,float available,float gap){
        int rows=1;float used=0f;
        for(float width:widths){
            if(used>0f&&used+gap+width>available){rows++;used=0f;}
            used+=(used==0f?0f:gap)+width;
        }
        return rows;
    }
    private void updateLessonBuildView(){
        for(int i=0;i<lessonBuildSlots.size();i++){
            BuildSlot slot=lessonBuildSlots.get(i);
            if(slot.filled!=(i<lessonAssembly.picked()))slot.showWord(i<lessonAssembly.picked());
        }
        for(int i=0;i<lessonBuildTiles.size();i++){
            TextView tile=lessonBuildTiles.get(i);
            int idx=(Integer)tile.getTag();
            int visibility=lessonAssembly.used(idx)?View.INVISIBLE:View.VISIBLE;
            if(tile.getVisibility()!=visibility)tile.setVisibility(visibility);
            boolean wrong=idx==lessonAssembly.wrongTile();
            // A wrong tap changes at most two tiles (previous and current).
            // Avoid allocating new GradientDrawables and invalidating every tile.
            if(tile.isSelected()!=wrong){
                tile.setSelected(wrong);
                tile.setTextColor(wrong?0xFF923C37:BUILD_INK);
                tile.setBackground(ui.outlined(wrong?0xFFF8DDD4:0xFFFFEEDC,17,
                    wrong?0xFFE6B4A7:0xFFFFF9F0));
            }
        }
        if(lessonBuildAction!=null&&lessonBuildAction.isEnabled()!=(lessonAssembly.picked()==lessonBuildRequired))
            styleAssemblyContinue(lessonBuildAction,lessonAssembly.picked()==lessonBuildRequired);
    }
    /** Choose a balanced random mask once per exercise, never once per screen redraw. */
    private int[] lessonMissingPositions(String[] tokens,LessonCurriculumLogic.Variant kind){
        if(kind!=LessonCurriculumLogic.Variant.GAPS)
            return LessonCurriculumLogic.missingPositions(tokens.length,kind,(int)lessonSeed);
        // Use the original fragment text as a stable identity. If that text changes,
        // its history changes too; do not combine positions from different verses.
        String historyKey=GapRotationLogic.identity(tokens);
        String exerciseKey=historyKey+":"+(groupMode?"group": "verse")+
            ":"+(trainingMode==TrainingMode.TYPE?"write":"build");
        if(exerciseKey.equals(rotationExerciseKey)&&rotationExerciseSeed==lessonSeed){
            int[] cached=GapRotationLogic.decodeMask(rotationMask,tokens.length);
            if(cached!=null)return cached;
        }
        android.content.SharedPreferences prefs=getSharedPreferences("gap_rotation",MODE_PRIVATE);
        int[] counts=GapRotationLogic.decodeCounts(prefs.getString(historyKey,""),tokens.length);
        int[] positions=GapRotationLogic.balancedWordPositions(tokens,counts,lessonSeed);
        prefs.edit().putString(historyKey,GapRotationLogic.encodeCounts(counts)).apply();
        rotationExerciseKey=exerciseKey;rotationExerciseSeed=lessonSeed;
        rotationMask=GapRotationLogic.encodeMask(positions);
        saveTrainingSession();
        return positions;
    }
    private boolean renderLessonBuild(LocalStore.Entry e){
        lessonBuildAction=null;lessonBuildRequired=0;
        lessonBuildTiles.clear();lessonBuildSlots.clear();
        List<String> src=new ArrayList<>();
        for(Bible.Verse v:e.lines().subList(activeStep.start,activeStep.end))src.add(v.text);
        List<LessonCurriculumLogic.Variant> variants=LessonCurriculumLogic.variants(
            ReviewLogic.words(texts(e.lines().subList(activeStep.start,activeStep.end))).size());
        lessonVariant=Math.min(lessonVariant,variants.size()-1);
        LessonCurriculumLogic.Variant kind=variants.get(lessonVariant);
        List<String[]> chunks=lessonChunks(src,kind);
        if(chunks.isEmpty())return true;
        String fingerprint=SessionIntegrityLogic.assemblyFingerprint(kind,chunks);
        if(!fingerprint.equals(savedAssemblyFingerprint)||
            lessonChunk<0||lessonChunk>=chunks.size()){
            // A different screen/font/layout or older saved format must never
            // skip words or transfer selected indices to another fragment.
            lessonChunk=0;resetLessonTiles();
            rotationExerciseKey="";rotationMask="";
        }
        savedAssemblyFingerprint=fingerprint;
        String[] tokens=chunks.get(lessonChunk);
        // The assembly tiles and answer slots show the task directly;
        // do not add an instruction card for any assembly variant.
        int[] missing=InlineGapLogic.wordPositions(tokens,lessonMissingPositions(tokens,kind));
        lessonBuildRequired=missing.length;
        lessonAssembly.validatePrefix(tokens,missing);
        WrapLayout prompt=new WrapLayout(this,ui.dp(7),true);
        prompt.setPadding(ui.dp(14),ui.dp(16),ui.dp(14),ui.dp(16));
        prompt.setBackground(ui.outlined(0xF9FFF2E1,23,0xFFFFFAF3));
        prompt.setElevation(ui.dp(2));
        prompt.setTag("assembly-answer");
        for(int i=0;i<tokens.length;i++){
            if(Arrays.binarySearch(missing,i)>=0){
                BuildSlot slot=new BuildSlot(tokens[i]);
                lessonBuildSlots.add(slot);
                prompt.addView(slot);
            }else{
                TextView word=ui.displayText(tokens[i],18);
                word.setTextColor(BUILD_INK);
                word.setGravity(Gravity.CENTER);
                word.setMinHeight(ui.dp(43));
                word.setPadding(ui.dp(4),ui.dp(8),ui.dp(4),ui.dp(8));
                prompt.addView(word);
            }
        }
        ui.add(content,prompt,10);
        WrapLayout choices=new WrapLayout(this,ui.dp(8),true);
        choices.setTag("assembly-word-bank");
        choices.setPadding(ui.dp(6),ui.dp(4),ui.dp(6),ui.dp(4));
        choices.setClipChildren(false);
        choices.setClipToPadding(false);
        int[] shuffled=WordOrderLogic.shuffledIndices(missing.length,lessonSeed);
        for(int index:shuffled){
            final int pressed=index;
            final int targetPos=missing[index];
            String caption=tokens[targetPos].replaceAll("[^\\p{L}\\p{N}\\p{M}\\s]", "").trim();
            TextView tile=wordTile(caption,0xFFFFEEDC,BUILD_INK,0xFFFFF9F0,()->{
                // The target and bank occupy fixed views. Never call render() on a tile tap.
                if(lessonAssembly.tap(tokens,missing,pressed)==LessonAssemblyState.Tap.IGNORED)return;
                updateLessonBuildView();
                // The progress bar advances per completed exercise, not per word.
                // Avoid re-querying mastery and recomputing every layout on each tap.
                if(lessonAssembly.picked()==lessonBuildRequired)
                    refreshLessonPhaseProgress(LessonCurriculumLogic.Phase.BUILD,e);
                saveTrainingSession();
            });
            tile.setTypeface(Typeface.create("serif",Typeface.BOLD));
            tile.setTextSize(18);
            tile.setBackground(ui.outlined(0xFFFFEEDC,17,0xFFFFF9F0));
            tile.setElevation(ui.dp(2));
            ui.animateLessonPress(tile);
            tile.setTag(index);lessonBuildTiles.add(tile);choices.addView(tile);
        }
        ui.add(content,choices,14);
        updateLessonBuildView();
        return lessonAssembly.picked()==missing.length;
    }
    private void advanceLessonBuild(LocalStore.Entry e){
        List<String> source=new ArrayList<>();
        for(Bible.Verse v:e.lines().subList(activeStep.start,activeStep.end))source.add(v.text);
        List<LessonCurriculumLogic.Variant> kinds=LessonCurriculumLogic.variants(
            ReviewLogic.words(texts(e.lines().subList(activeStep.start,activeStep.end))).size());
        int chunkCount=lessonChunks(source,kinds.get(Math.min(lessonVariant,kinds.size()-1))).size();
        LessonStageFlow.BuildTransition next=LessonStageFlow.advanceBuild(
            lessonVariant,lessonChunk,kinds.size(),chunkCount,groupMode);
        if(next.persistedCompletion()>0)
            store.finishExercise(e.id,activeStep.start,next.persistedCompletion());
        if(next.groupCompleted())groupBuildDone=true;
        lessonVariant=next.nextVariant();lessonChunk=next.nextChunk();
        if(next.beginWriting())lessonWriting.variant=0;
        resetLessonTiles();
        if(next.beginWriting()){lessonWriting.draft="";lastAccuracy=-1;lessonWriting.hadFeedback=false;}
        saveTrainingSession();render();
    }
    private List<LessonCurriculumLogic.Variant> writingVariants(LocalStore.Entry e){
        return writingVariants(texts(e.lines().subList(activeStep.start,activeStep.end)));
    }
    private List<LessonCurriculumLogic.Variant> writingVariants(String passage){
        return LessonStageFlow.writingVariants(passage,reviewMode,lessonPracticeAgain,lessonWriting.practiceWeakFragment);
    }
    private String writingExpected(String original,LessonCurriculumLogic.Variant variant){
        if(lessonWriting.practiceWeakFragment)return lessonWriting.weakFragmentText;
        if(variant==LessonCurriculumLogic.Variant.FULL)return original;
        String[] tokens=ExerciseTextLogic.tokens(original);
        int[] gaps=InlineGapLogic.wordPositions(tokens,lessonMissingPositions(tokens,variant));
        StringBuilder response=new StringBuilder();
        for(int position:gaps){if(response.length()>0)response.append(' ');response.append(tokens[position]);}
        return response.toString();
    }
    /** Partial writing is edited directly in the sentence, not in a separate answer field. */
    private void renderLessonWriting(){
        List<LessonCurriculumLogic.Variant> variants=writingVariants(activeEntry);
        lessonWriting.variant=Math.min(lessonWriting.variant,variants.size()-1);
        LessonCurriculumLogic.Variant variant=variants.get(lessonWriting.variant);
        boolean full=variant==LessonCurriculumLogic.Variant.FULL;

        ui.helper(content,lessonWriting.practiceWeakFragment?LearningHelp.WRITE_FRAGMENT:
            full?LearningHelp.WRITE_FULL:LearningHelp.WRITE_GAPS);
        if(!lessonWriting.practiceWeakFragment&&!full)renderInlineGapInputs(variant);
        if(full){
            EditText answer=ui.input(lessonWriting.practiceWeakFragment?"Воспроизведи проблемную фразу…":"Напиши стих полностью…",true);
            ui.forbidKeyboardHints(answer,true);
            answer.setMinLines(6);answer.setText(lessonWriting.draft);
            answer.addTextChangedListener(new TextWatcher(){
                @Override public void beforeTextChanged(CharSequence x,int a,int b,int c){}
                @Override public void onTextChanged(CharSequence x,int a,int before,int count){
                    cancelWritingCheck();
                    lessonWriting.draft=x.toString();lastAccuracy=-1;
                    saveTrainingSession();
                }
                @Override public void afterTextChanged(Editable x){}
            });
            ui.add(content,answer,18);
        }
        // Verification is always explicitly triggered by the existing footer button.
    }
    private void renderInlineGapInputs(LessonCurriculumLogic.Variant variant){
        String source=texts(activeEntry.lines().subList(activeStep.start,activeStep.end));
        String[] tokens=ExerciseTextLogic.tokens(source);
        int[] originalPositions=lessonMissingPositions(tokens,variant);
        int[] missing=InlineGapLogic.wordPositions(tokens,originalPositions);
        // A separator marks independently editable gaps, preserving spaces inside one accepted variant.
        lessonWriting.restoreGaps(tokens,originalPositions);
        WrapLayout inline=new WrapLayout(this,ui.dp(6));
        inline.setPadding(0,ui.dp(12),0,ui.dp(12));
        int maxWidth=getResources().getDisplayMetrics().widthPixels-ui.dp(58);
        inlineFields.clear();inlineExpected.clear();
        List<EditText> fields=inlineFields;
        for(int i=0;i<tokens.length;i++){
            String token=tokens[i];
            int match=Arrays.binarySearch(missing,i);
            if(match<0){
                TextView item=ui.txt(token,19,WHITE,false);
                item.setPadding(ui.dp(1),ui.dp(8),ui.dp(1),ui.dp(8));inline.addView(item);
                continue;
            }
            final int gapIndex=match;
            final String expected=InlineGapLogic.core(token);
            inlineExpected.add(expected);
            String prefix=InlineGapLogic.prefix(token);
            if(!prefix.isEmpty()){
                TextView punctuation=ui.txt(prefix,19,WHITE,false);
                punctuation.setPadding(0,ui.dp(8),0,ui.dp(8));inline.addView(punctuation);
            }
            EditText field=new EditText(this);
            field.setTextColor(WHITE);field.setHintTextColor(MUTED);field.setTextSize(19);
            field.setSingleLine(true);ui.forbidKeyboardHints(field,false);
            field.setPadding(ui.dp(3),ui.dp(6),ui.dp(3),ui.dp(6));
            field.setGravity(Gravity.CENTER_VERTICAL);
            // Android native underline: width is initially based on the original word,
            // but the input itself has no maximum length.
            field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
            field.setHint(" ");
            field.setText(lessonWriting.gap(gapIndex));
            field.setSelectAllOnFocus(false);
            int initial=Math.max(ui.dp(32),ui.dp(12)*Math.max(1,InlineGapLogic.letters(expected))+ui.dp(8));
            final int baseline=Math.min(initial,maxWidth);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(baseline,ui.dp(43));
            field.setLayoutParams(params);field.setMinWidth(Math.min(initial,maxWidth));
            field.setContentDescription("Пропущенное слово "+(gapIndex+1));
            fields.add(field);inline.addView(field);
            String suffix=InlineGapLogic.suffix(token);
            if(!suffix.isEmpty()){
                TextView punctuation=ui.txt(suffix,19,WHITE,false);
                punctuation.setPadding(0,ui.dp(8),0,ui.dp(8));inline.addView(punctuation);
            }
        }
        List<String> refsForBlanks=new ArrayList<>();
        for(Bible.Verse line:activeEntry.lines().subList(activeStep.start,activeStep.end))
            refsForBlanks.add(line.ref);
        final boolean special=AcceptedTextLogic.matthew513(activeEntry.reference,refsForBlanks);
        for(int k=0;k<fields.size();k++){
            final int gapIndex=k;
            EditText field=fields.get(k);
            field.addTextChangedListener(new TextWatcher(){
                private boolean changing=false;
                @Override public void beforeTextChanged(CharSequence x,int a,int b,int c){}
                @Override public void onTextChanged(CharSequence x,int a,int before,int count){
                    if(changing)return;
                    String value=x.toString();
                    boolean containsSpace=value.endsWith(" ");
                    boolean allowed=containsSpace && InlineGapLogic.keepSpaceInside(
                        InlineGapLogic.core(tokens[missing[gapIndex]]),value,
                        special);
                    if(containsSpace && !allowed){
                        changing=true;
                        String trimmed=value.substring(0,value.length()-1);
                        field.setText(trimmed);field.setSelection(field.length());
                        changing=false;value=trimmed;
                        int next=gapIndex+1;
                        while(next<fields.size() && !lessonWriting.gap(next).trim().isEmpty())next++;
                        if(next<fields.size())fields.get(next).requestFocus();
                        else {
                            field.clearFocus();
                            android.view.inputmethod.InputMethodManager keyboard=
                                (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                            if(keyboard!=null)keyboard.hideSoftInputFromWindow(field.getWindowToken(),0);
                        }
                        // The last space NEVER starts validation; only the footer button does.
                    }
                    cancelWritingCheck();
                    lessonWriting.editGap(gapIndex,value);
                    field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
                    float textPx=field.getPaint().measureText(value)+ui.dp(25);
                    int baselineWidth = Math.min(maxWidth,
                        Math.max(ui.dp(32), ui.dp(12) * Math.max(1,
                            InlineGapLogic.letters(inlineExpected.get(gapIndex))) + ui.dp(8)));
                    int width=Math.min(maxWidth,Math.max(baselineWidth,(int)Math.ceil(textPx)));
                    ViewGroup.LayoutParams lp=field.getLayoutParams();
                    if(lp.width!=width){lp.width=width;field.setLayoutParams(lp);}
                    lastAccuracy=-1;saveTrainingSession();
                }
                @Override public void afterTextChanged(Editable x){}
            });
        }
        ui.add(content,inline,18);
    }
    private static final class AnswerReview {
        final String expected,actual;
        final AnswerReviewLogic.Result result;
        AnswerReview(String expected,String actual,AnswerReviewLogic.Result result){
            this.expected=expected;
            this.actual=actual;
            this.result=result;
        }
    }
    private AnswerReview answerReview;

    private void openAnswerReview(AnswerReview review){
        answerReview=review;
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
        if(answerReview==null)return;
        answerReview=null;
        lessonWriting.retry();
        lastAccuracy=-1;
        saveTrainingSession();
        render();
    }

    private void renderAnswerReview(AnswerReview review){
        addAnswerReviewCard("Твой ответ",review.result.summary==AnswerReviewLogic.Summary.EMPTY?
            "Ответ не введён":review.result.displayedActual,review.result.actualMarks,false,
            0xFFF9E9E4,0xFFECD2CA,0xFF923C37);
        addAnswerReviewCard("Правильный ответ",review.expected,Collections.emptyList(),true,
            0xFFF0F3E6,0xFFDDE5D4,0xFF436044);
    }

    private LinearLayout answerReviewPanel(int background,int border){
        LinearLayout panel=ui.column();
        panel.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(14));
        panel.setBackground(ui.outlined(background,18,border));
        return panel;
    }

    private static final class ReviewErrorSpan extends ReplacementSpan {
        private final float thickness;
        ReviewErrorSpan(float thickness){this.thickness=thickness;}
        @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics){
            if(metrics!=null){
                Paint.FontMetricsInt source=paint.getFontMetricsInt();
                metrics.top=source.top;metrics.ascent=source.ascent;
                metrics.descent=Math.max(source.descent,(int)Math.ceil(thickness*2));
                metrics.bottom=Math.max(source.bottom,metrics.descent);
                metrics.leading=source.leading;
            }
            boolean originalBold=paint.isFakeBoldText();
            paint.setFakeBoldText(true);
            int width=(int)Math.ceil(paint.measureText(text,start,end));
            paint.setFakeBoldText(originalBold);
            return width;
        }
        @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,
                                   int baseline,int bottom,Paint paint){
            int originalColor=paint.getColor();
            boolean originalBold=paint.isFakeBoldText();
            float originalWidth=paint.getStrokeWidth();
            paint.setColor(0xFFB72F2B);
            paint.setFakeBoldText(true);
            float width=paint.measureText(text,start,end);
            canvas.drawText(text,start,end,x,baseline,paint);
            paint.setStrokeWidth(thickness);
            float underline=Math.min(bottom-thickness/2f,baseline+thickness);
            canvas.drawLine(x,underline,x+width,underline,paint);
            paint.setStrokeWidth(originalWidth);
            paint.setFakeBoldText(originalBold);
            paint.setColor(originalColor);
        }
    }

    private void addAnswerReviewCard(String title,String text,List<AnswerReviewLogic.Mark> marks,
                                     boolean correct,int background,int border,int ink){
        LinearLayout panel=answerReviewPanel(background,border);
        LinearLayout heading=ui.row();
        TextView icon=ui.txt(correct?"✓":"×",15,Color.WHITE,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(ui.shape(ink,20));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconSize=new LinearLayout.LayoutParams(ui.dp(24),ui.dp(24));
        iconSize.rightMargin=ui.dp(8);
        heading.addView(icon,iconSize);
        heading.addView(ui.txt(title,14,ink,true),new LinearLayout.LayoutParams(0,-2,1));
        ui.add(panel,heading,0);
        SpannableString marked=new SpannableString(text);
        for(AnswerReviewLogic.Mark mark:marks){
            if(mark.kind==AnswerReviewLogic.Kind.SPELLING){
                marked.setSpan(new ReviewErrorSpan(ui.dp(2)),mark.start,mark.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }else{
                marked.setSpan(new ForegroundColorSpan(0xFFAD3C37),mark.start,mark.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if(mark.kind==AnswerReviewLogic.Kind.EXTRA)
                    marked.setSpan(new StrikethroughSpan(),mark.start,mark.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        TextView body=answerReviewText(marked,19);
        body.setLineSpacing(ui.dp(3),1.08f);
        ui.add(panel,body,8);
        ui.add(content,panel,content.getChildCount()==0?0:10);
    }

    private TextView answerReviewText(CharSequence text,int size){
        TextView view=ui.txt("",size,WHITE,false);
        view.setText(text);
        return view;
    }

    private final java.util.concurrent.ExecutorService writingExecutor=
        java.util.concurrent.Executors.newSingleThreadExecutor(task->new Thread(task,"WritingAssessment"));
    private final Handler writingHandler=new Handler(Looper.getMainLooper());
    private java.util.concurrent.Future<?> writingTask;
    private record WritingCheck(WritingAssessment.Input input,LocalStore.Entry entry,
                                StudyPlanLogic.Step step,String draft) {}
    private WritingCheck pendingWritingCheck;

    private void cancelWritingCheck(){
        pendingWritingCheck=null;
        if(writingTask!=null){writingTask.cancel(true);writingTask=null;}
        writingHandler.removeCallbacksAndMessages(null);
    }

    private void validateLessonWriting(LocalStore.Entry e){
        if(pendingWritingCheck!=null||screen!=Screen.TRAIN||backupInProgress||resetInProgress||
            answerReview!=null||(checked&&passed))return;
        List<Bible.Verse> lines=e.lines().subList(activeStep.start,activeStep.end);
        String passage=texts(lines);
        List<LessonCurriculumLogic.Variant> variants=writingVariants(passage);
        lessonWriting.variant=Math.min(lessonWriting.variant,variants.size()-1);
        LessonCurriculumLogic.Variant kind=variants.get(lessonWriting.variant);
        boolean full=kind==LessonCurriculumLogic.Variant.FULL;
        String expected=writingExpected(passage,kind);
        String entered=full?lessonWriting.draft:lessonWriting.gapResponse();
        List<String> refs=new ArrayList<>(), verses=new ArrayList<>();
        for(Bible.Verse line:lines){refs.add(line.ref);verses.add(line.text);}
        WritingAssessment.Input input=new WritingAssessment.Input(expected,entered,
            AcceptedTextLogic.matthew513(e.reference,refs),full,lessonWriting.practiceWeakFragment,verses,
            full?Collections.emptyList():inlineExpected, full?Collections.emptyList():lessonWriting.gapAnswers());
        WritingCheck check=new WritingCheck(input,e,activeStep,lessonWriting.draft);
        pendingWritingCheck=check;
        if(!input.requiresWorker()){
            applyWritingAssessment(check,WritingAssessment.evaluate(input));
            return;
        }
        Handler main=writingHandler;
        java.lang.ref.WeakReference<MainActivity> target=new java.lang.ref.WeakReference<>(this);
        writingTask=writingExecutor.submit(()->{
            try{
                WritingAssessment.Result result=WritingAssessment.evaluate(input);
                main.post(()->{
                    MainActivity activity=target.get();
                    if(activity!=null)activity.applyWritingAssessment(check,result);
                });
            }catch(java.util.concurrent.CancellationException cancelled){
                // The main thread already retired this submission.
            }catch(RuntimeException error){
                main.post(()->{
                    MainActivity activity=target.get();
                    if(activity!=null&&activity.pendingWritingCheck==check){
                        activity.cancelWritingCheck();
                        android.util.Log.e("Learning","Unable to check written answer",error);
                        activity.toast("Не удалось проверить ответ. Попробуй ещё раз.");
                    }
                });
            }
        });
    }

    private void applyWritingAssessment(WritingCheck check,WritingAssessment.Result result){
        if(pendingWritingCheck!=check)return;
        pendingWritingCheck=null;writingTask=null;
        LocalStore.Entry e=check.entry();
        if(isDestroyed()||isFinishing()||backupInProgress||resetInProgress||screen!=Screen.TRAIN||
            selectedEntry!=e.id||activeStep!=check.step()||!lessonWriting.draft.equals(check.draft()))return;
        WritingAssessment.Input input=check.input();
        AnswerReview feedback=result.exact()?null:new AnswerReview(input.expected(),input.actual(),result.feedback());
        LessonWritingFlow.Context context=new LessonWritingFlow.Context(
            e.id,studyStage,activeStep.start,activeStep.end,reviewMode,groupMode,
            reviewMode&&currentReview!=null&&"VERSE_RUN".equals(currentReview.stage),
            lessonWriting.practiceWeakFragment,lessonWriting.hadFeedback);
        LessonWritingFlow.Transition transition=LessonWritingFlow.decide(context,input,result);
        try{
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
                    ReviewLogic.words(texts(e.lines().subList(activeStep.start,activeStep.end))).size()).size()-1;
                lessonWriting.finishFragment(reviewMode?0:fullVariant);
                saveTrainingSession();render();
                toast("Фрагмент отработан. Напиши весь стих заново без подсказок.");
                return;
            case NEXT_VARIANT:
                lessonWriting.nextVariant();lastAccuracy=-1;
                lessonSeed=new Random().nextLong();saveTrainingSession();render();
                return;
            case FAILED_RECALL:
                weakVerse=transition.weakVerse();
                weakTransitionFrom=transition.weakTransitionFrom();
                lessonWriting.hadFeedback=true;
                lastAccuracy=result.accuracy();
                lastExpected=result.closest();
                openAnswerReview(feedback);
                return;
            case ASSISTED_CORRECTION:
                lessonWriting.hadFeedback=false;lessonWriting.draft="";lastAccuracy=-1;saveTrainingSession();
                render();
                toast("Исправлено. Теперь напиши стих заново без подсказок для зачёта.");
                return;
            case SUCCESSFUL_RECALL:
                lessonPracticeAgain=false;lessonWriting.draft="";lastAccuracy=100;passed=true;checked=true;
                if(reviewMode)getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit()
                    .remove("review_pending").apply();
                if(!reviewMode&&!groupMode)saveTrainingSession();
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
    private void ensureActiveUnified(){
        if(store.unifiedCollection(activeUnifiedId)!=null)return;
        List<LocalStore.UnifiedCollection> collections=store.unifiedCollections();
        activeUnifiedId=collections.isEmpty()?-1:collections.get(0).id;
        getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE).edit().putLong("active",activeUnifiedId).apply();
    }
    private LocalStore.UnifiedCollection activeUnified(){
        ensureActiveUnified();return store.unifiedCollection(activeUnifiedId);
    }
    private void activateUnified(long id){
        if(store.unifiedCollection(id)==null)return;
        activeUnifiedId=id;
        getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE).edit().putLong("active",id).apply();
    }
    private interface UnifiedCreated {void accept(LocalStore.UnifiedCollection collection);}
    private void newUnifiedCollection(UnifiedCreated after){
        EditText name=ui.input("Например: Нагорная проповедь",false);
        LinearLayout wrap=ui.column();wrap.setPadding(ui.dp(19),ui.dp(12),ui.dp(19),ui.dp(9));ui.add(wrap,name,0);
        AlertDialog dialog=new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Новая коллекция").setView(wrap)
            .setPositiveButton("Создать",null).setNegativeButton("Отмена",null).create();
        ui.styledDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(value(name).isEmpty()){name.setError("Введите название коллекции");name.requestFocus();return;}
            boolean firstCollection=store.unifiedCollections().isEmpty();
            long id=store.createUnifiedCollection(value(name));
            // Only the first collection is selected implicitly.
            if(firstCollection)activateUnified(id);
            LocalStore.UnifiedCollection u=store.unifiedCollection(id);
            dialog.dismiss();
            if(after!=null)after.accept(u);else{openUnifiedId=id;screen=Screen.COLLECTION_DETAIL;render();}
        });
    }
    private void showUnifiedSelector(){
        List<LocalStore.UnifiedCollection> collections=store.unifiedCollections();
        LinearLayout panel=ui.column();
        panel.setPadding(ui.dp(20),ui.dp(20),ui.dp(20),ui.dp(12));
        LinearLayout header=ui.row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(ui.displayText("Выбрать коллекцию",23),new LinearLayout.LayoutParams(0,-2,1));
        TextView close=ui.txt("×",28,MUTED,false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Закрыть выбор коллекции");
        close.setFocusable(true);
        header.addView(close,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        ui.add(panel,header,0);
        int visibleHeight=Math.min(ui.dp(320),getResources().getDisplayMetrics().heightPixels/2);
        ScrollView scroll=new ScrollView(this){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                int maximum=MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED?visibleHeight:
                    Math.min(visibleHeight,MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(maximum,MeasureSpec.AT_MOST));
            }
        };
        LinearLayout choices=ui.column();
        scroll.addView(choices);
        ui.add(panel,scroll,12);
        AlertDialog dialog=new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setView(panel).create();
        close.setOnClickListener(v->dialog.dismiss());
        for(LocalStore.UnifiedCollection collection:collections){
            boolean selected=collection.id==activeUnifiedId;
            LinearLayout choice=ui.row();
            choice.setGravity(Gravity.CENTER_VERTICAL);
            choice.setMinimumHeight(ui.dp(52));
            choice.setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8));
            choice.setBackground(selected?ui.outlined(CARD_ALT,14,BORDER):ui.shape(CARD,14));
            choice.addView(ui.txt(collection.title,17,WHITE,selected),new LinearLayout.LayoutParams(0,-2,1));
            if(selected){
                TextView check=ui.txt("✓",20,GREEN,true);
                check.setGravity(Gravity.CENTER);
                choice.addView(check,new LinearLayout.LayoutParams(ui.dp(32),-2));
            }
            choice.setSelected(selected);
            choice.setFocusable(true);
            choice.setOnClickListener(v->{dialog.dismiss();activateUnified(collection.id);render();});
            ui.add(choices,choice,4);
        }
        if(collections.isEmpty())scroll.setVisibility(View.GONE);
        dialog.setCanceledOnTouchOutside(true);
        ui.styledDialog(dialog);
    }
    private void renderUnifiedHomePanel(){
        LinearLayout panel=ui.card(content);panel.setBackground(homeSurface());
        LocalStore.UnifiedCollection active=activeUnified();
        if(active==null){
            ui.add(panel,ui.displayText("Пока нет коллекций",22),0);
            ui.action(panel,"＋ Создать первую коллекцию",true,()->newUnifiedCollection(null));
            return;
        }
        // Compact collection switcher: two curved circular arrows, no chevron.
        LinearLayout chooser=ui.row();chooser.setGravity(Gravity.CENTER_VERTICAL);
        chooser.setPadding(ui.dp(12),ui.dp(7),ui.dp(10),ui.dp(7));
        chooser.setBackground(ui.outlined(CARD_ALT,13,BORDER));
        LinearLayout names=ui.column();
        ui.add(names,ui.txt("ВЫБРАННАЯ КОЛЛЕКЦИЯ",10,MUTED,true),0);
        TextView title=ui.txt(active.title,17,WHITE,true);
        title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ui.add(names,title,3);
        chooser.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        NavigationGlyph cycle=new NavigationGlyph(this,"refresh",GREEN);
        cycle.setContentDescription("Сменить коллекцию");
        LinearLayout.LayoutParams cycleP=new LinearLayout.LayoutParams(ui.dp(30),ui.dp(30));
        cycleP.leftMargin=ui.dp(10);chooser.addView(cycle,cycleP);
        chooser.setContentDescription("Выбранная коллекция: "+active.title+". Нажми, чтобы сменить.");
        chooser.setClickable(true);chooser.setFocusable(true);
        chooser.setOnClickListener(v->showUnifiedSelector());
        ui.add(panel,chooser,0);
        int verses=0,learned=0;
        CollectionProgressSnapshot snapshot=store.collectionProgress();
        Set<List<String>> seenVerses=new HashSet<>();
        for(LocalStore.Entry e:snapshot.entries(active.groupId)){
            List<Bible.Verse> lines=snapshot.verses(e.id);
            int book=bookForEntry(e);
            for(int i=0;i<lines.size();i++){
                if(!seenVerses.add(KnowledgeCheckMaterial.identity(e,book,i,lines.get(i))))continue;
                verses++;
                if(snapshot.exerciseDone(e.id,i)>=5)learned++;
            }
        }
        addHelpLabel(panel,ui.txt(verses+" стихов · "+learned+" предварительно освоено",12,MUTED,false),
            LearningHelp.PROGRESS,8);
        if(verses>0)ui.progress(panel,learned,verses);
        unifiedModeRow(panel,LearningHelp.STUDY,"mode-book",this::startActiveStudy);
        unifiedModeRow(panel,LearningHelp.CARDS,"mode-cards",this::startActiveCards);
        unifiedModeRow(panel,LearningHelp.REVIEW,"mode-brain",()->startActiveReview(false));
        unifiedModeRow(panel,LearningHelp.MEMORY,"mode-check",this::startActiveRun);
        unifiedModeRow(panel,LearningHelp.ALL_REVIEWS,"mode-repeat",()->startActiveReview(true));
    }

    private void addHelpLabel(LinearLayout parent,TextView label,LearningHelp help,int margin){
        LinearLayout line=ui.row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        line.addView(helpIcon(help,"Подсказка «"+help.title+"»"),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        ui.add(parent,line,margin);
    }

    private void unifiedModeRow(LinearLayout panel,LearningHelp help,String icon,Runnable run){
        String name=help.title;
        LinearLayout item=ui.row();
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(ui.dp(12),0,0,0);
        item.setBackground(ui.outlined(CARD,14,BORDER));

        LinearLayout launch=ui.row();
        launch.setGravity(Gravity.CENTER_VERTICAL);
        launch.setMinimumHeight(ui.dp(48));
        launch.setContentDescription(name);
        launch.setFocusable(true);
        launch.setOnClickListener(v->run.run());
        LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(ui.dp(22),ui.dp(24));
        iconParams.rightMargin=ui.dp(13);
        launch.addView(new NavigationGlyph(this,icon,WHITE),iconParams);
        launch.addView(ui.txt(name,16,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        item.addView(launch,new LinearLayout.LayoutParams(0,-2,1));

        item.addView(helpIcon(help,"О режиме «"+name+"»"),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        ui.add(panel,item,9);
    }

    private View helpIcon(LearningHelp help,String description){
        View info=new NavigationGlyph(this,"info",0xFF1976D2);
        info.setContentDescription(description);
        info.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        info.setFocusable(true);
        info.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x241976D2),null,ui.shape(Color.WHITE,24)));
        info.setOnClickListener(v->modeInfo.show(v,help));
        return info;
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
        reviewCollectionGroup=all?-1:u.groupId;
        startReviewSession();
    }
    private void renderUnifiedCollections(){
        // Prominent full-width search field: accent border, magnifier, larger input.
        LinearLayout searchBar=ui.row();searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(ui.dp(14),ui.dp(3),ui.dp(8),ui.dp(3));
        searchBar.setMinimumHeight(ui.dp(58));
        GradientDrawable searchFrame=ui.shape(0xFFFFFDF9,17);
        searchFrame.setStroke(ui.dp(2),0xFFCE9072);
        searchBar.setBackground(searchFrame);
        searchBar.setElevation(ui.dp(2));
        NavigationGlyph magnifier=new NavigationGlyph(this,"search",GREEN);
        searchBar.addView(magnifier,new LinearLayout.LayoutParams(ui.dp(29),ui.dp(29)));
        EditText search=ui.input("Поиск по коллекциям",false);
        search.setTextSize(17);
        search.setSingleLine(true);
        search.setPadding(ui.dp(12),ui.dp(9),ui.dp(5),ui.dp(9));
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setContentDescription("Найти коллекцию по названию");
        searchBar.addView(search,new LinearLayout.LayoutParams(0,-2,1));
        TextView clear=ui.txt("×",24,MUTED,false);
        clear.setGravity(Gravity.CENTER);
        clear.setVisibility(View.GONE);
        clear.setContentDescription("Очистить поиск коллекций");
        searchBar.addView(clear,new LinearLayout.LayoutParams(ui.dp(41),ui.dp(44)));
        clear.setOnClickListener(v->search.setText(""));
        ui.add(content,searchBar,2);
        LinearLayout results=ui.column();ui.add(content,results,12);
        List<LocalStore.UnifiedCollection> collections=store.unifiedCollections();
        CollectionProgressSnapshot snapshot=collections.isEmpty()?null:store.collectionProgress();
        renderUnifiedCollectionRows(results,"",collections,snapshot);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                clear.setVisibility(s.length()==0?View.GONE:View.VISIBLE);
                results.removeAllViews();renderUnifiedCollectionRows(results,s.toString(),collections,snapshot);
            }
            public void afterTextChanged(Editable s){}
        });
    }
    /** Keep settings in the same warm-card style as the existing collection screens. */
    private void renderSettings(){
        TextView heading=ui.displayText("Настройки",28);
        heading.setGravity(Gravity.CENTER);
        ui.add(content,heading,18);

        LinearLayout backup=ui.column();
        backup.setPadding(ui.dp(18),ui.dp(18),ui.dp(18),ui.dp(18));
        backup.setBackground(homeSurface());
        backup.setElevation(ui.dp(2));
        ui.add(backup,ui.displayText("Резервная копия",20),0);
        TextView backupHint=ui.txt(
            "Сохраните коллекции, собственные тексты, карточки, прогресс и "+
            "расписание повторений в один файл. Всё работает без интернета.",14,MUTED,false);
        backupHint.setLineSpacing(ui.dp(3),1.1f);
        ui.add(backup,backupHint,9);
        ui.add(backup,ui.button("Создать резервную копию",true,this::exportFile),14);
        ui.add(backup,ui.button("Восстановить из копии",false,this::importFile),10);
        TextView warning=ui.txt(
            "При восстановлении текущие данные заменятся содержимым файла. "+
            "Перед этим рекомендуется сохранить новую копию.",13,MUTED,false);
        ui.add(backup,warning,12);
        ui.add(content,backup,14);

        LinearLayout section=ui.column();
        section.setPadding(ui.dp(18),ui.dp(18),ui.dp(18),ui.dp(18));
        section.setBackground(homeSurface());
        section.setElevation(ui.dp(2));
        TextView title=ui.displayText("Прогресс изучения",20);
        ui.add(section,title,9);
        TextView explanation=ui.txt(
            "Сбросить освоение стихов, историю проверок и расписание повторений. "+
            "Коллекции, книги, карточки и сохранённые тексты останутся.",14,MUTED,false);
        explanation.setLineSpacing(ui.dp(3),1.1f);
        ui.add(section,explanation,14);
        TextView reset=ui.button("Сбросить прогресс",false,this::confirmResetProgress);
        reset.setTextColor(0xFFAC523F);
        reset.setContentDescription("Сбросить весь прогресс изучения");
        ui.add(section,reset,0);
        ui.add(content,section,16);
    }
    private void confirmResetProgress(){
        if(resetInProgress)return;
        ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Сбросить весь прогресс?")
            .setMessage("Будут безвозвратно удалены отметки об освоении стихов, "+
                "история проверок, незавершённые уроки и расписание повторений "+
                "во всех коллекциях.\n\nСами коллекции, книги, карточки и тексты сохранятся.")
            .setPositiveButton("Сбросить",(dialog,which)->resetAllLearningProgress())
            .setNegativeButton("Отмена",null).create());
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
                    reviewQueue.clear();reviewPointer=0;reviewMode=false;
                    knowledgeCheck.clear();
                    savedAssemblyFingerprint="";lessonChunkCache.clear();lessonChunkCacheKey="";
                    lessonWriting.draft="";checked=false;passed=false;answerReview=null;
                    lessonWriting.hadFeedback=false;lessonPracticeAgain=false;
                    readyTabs.clear();savedScrollY.clear();
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
        int last=count%10, lastTwo=count%100;
        String noun=lastTwo>=11&&lastTwo<=14?many:last==1?one:last>=2&&last<=4?few:many;
        return count+" "+noun;
    }
    private void renderUnifiedCollectionRows(LinearLayout into,String search,
            List<LocalStore.UnifiedCollection> collections,CollectionProgressSnapshot snapshot){
        boolean found=false;
        for(LocalStore.UnifiedCollection u:collections){
            if(!u.title.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT)))continue;
            found=true;
            LinearLayout row=ui.row();row.setPadding(ui.dp(13),ui.dp(14),ui.dp(13),ui.dp(14));
            row.setBackground(homeSurface());
            ImageView thumb=landscapeThumbnail(u.id);
            LinearLayout.LayoutParams imageP=new LinearLayout.LayoutParams(ui.dp(59),ui.dp(61));
            imageP.rightMargin=ui.dp(13);row.addView(thumb,imageP);
            LinearLayout details=ui.column();ui.add(details,ui.displayText(u.title,17),0);
            String passages=countedRussian(u.entries,"отрывок","отрывка","отрывков");
            String verses=countedRussian(snapshot.uniqueVerseCount(u.groupId),"стих","стиха","стихов");
            ui.add(details,ui.txt(passages+" · "+verses,13,MUTED,false),5);
            if(u.id==activeUnifiedId)ui.add(details,ui.txt("●  Выбрана на главной",12,GREEN,true),4);
            row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=ui.txt("›",22,GREEN,false);arrow.setGravity(Gravity.CENTER);
            row.addView(arrow,new LinearLayout.LayoutParams(ui.dp(24),ui.dp(48)));
            row.setClickable(true);row.setFocusable(true);
            row.setContentDescription("Открыть коллекцию "+u.title);
            row.setOnClickListener(v->{openUnifiedId=u.id;screen=Screen.COLLECTION_DETAIL;render();});
            ui.add(into,row,9);
        }
        if(!found)ui.helper(into,search.trim().isEmpty()?"Коллекций пока нет":"Ничего не найдено");
    }
    /** The one explicit location for collection management, inside its detail screen. */
    private void unifiedSettingRow(LinearLayout settings,String label,String glyph,boolean destructive,Runnable command){
        LinearLayout item=ui.row();item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(ui.dp(8),ui.dp(7),ui.dp(5),ui.dp(7));
        MenuGlyph symbol=new MenuGlyph(this,glyph,destructive?RED:GREEN);
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28));
        iconP.rightMargin=ui.dp(13);item.addView(symbol,iconP);
        TextView name=ui.txt(label,16,destructive?RED:WHITE,true);
        item.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        item.setMinimumHeight(ui.dp(51));
        item.setClickable(true);item.setFocusable(true);
        item.setContentDescription(label);item.setOnClickListener(v->command.run());
        if(settings.getChildCount()>1){
            View divider=new View(this);divider.setBackgroundColor(0xFFECD8CD);
            settings.addView(divider,new LinearLayout.LayoutParams(-1,ui.dp(1)));
        }
        settings.addView(item,new LinearLayout.LayoutParams(-1,-2));
    }
    private void showUnifiedCollectionSettings(LocalStore.UnifiedCollection u){
        LinearLayout settings=ui.column();settings.setBackground(homeSurface());
        settings.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(11));
        ui.add(settings,ui.txt("НАСТРОЙКИ КОЛЛЕКЦИИ",12,MUTED,true),0);
        unifiedSettingRow(settings,"Переименовать","pencil",false,()->renameUnified(u));
        unifiedSettingRow(settings,"Настроить порядок стихов","shuffle",false,()->{
            if(store.entries(u.groupId).size()<2){
                toast("Для изменения порядка нужны хотя бы два отрывка");return;
            }
            selectedGroup=u.groupId;openUnifiedId=u.id;
            screen=Screen.REORDER_STUDY;render();
        });
        unifiedSettingRow(settings,"Очистить коллекцию","eraser",true,()->clearUnified(u));
        unifiedSettingRow(settings,"Удалить коллекцию","trash",true,()->deleteUnified(u));
        ui.add(content,settings,14);
    }
    private void clearUnified(LocalStore.UnifiedCollection u){
        if(u.entries==0&&u.cards==0){toast("Коллекция уже пуста");return;}
        ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Очистить коллекцию?")
            .setMessage("Все стихи, отрывки и карточки будут убраны из этой коллекции. " +
                "Саму коллекцию и прежние результаты заучивания сохраним. " +
                "Отменить очистку нельзя.")
            .setPositiveButton("Очистить",(d,w)->{
                store.clearUnifiedCollection(u.id);
                knowledgeCheck.clearCollection(u.id,true);
                if(collectionQueueGroup==u.groupId)clearCollectionQueue();
                render();toast("Коллекция очищена");
            }).setNegativeButton("Отмена",null).create());
    }
    private void renameUnified(LocalStore.UnifiedCollection u){
        EditText name=ui.input("Название коллекции",false);name.setText(u.title);
        LinearLayout box=ui.column();box.setPadding(ui.dp(18),ui.dp(10),ui.dp(18),ui.dp(8));ui.add(box,name,0);
        AlertDialog dialog=new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Переименовать коллекцию").setView(box)
            .setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();
        ui.styledDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(value(name).isEmpty()){name.setError("Укажи название");name.requestFocus();return;}
            store.renameUnifiedCollection(u.id,value(name));dialog.dismiss();render();
        });
    }
    private void deleteUnified(LocalStore.UnifiedCollection u){
        ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Удалить коллекцию из списка?")
            .setMessage("Коллекция исчезнет из списка. Стихи и прежний прогресс изучения сохранятся в локальной базе.")
            .setPositiveButton("Удалить",(d,w)->{
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
                    SharedPreferences library=getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE);
                    if(library.getLong("active",-1)==u.id)library.edit().remove("active").apply();
                    knowledgeCheck.clearCollection(u.id,false);
                });
            }).setNegativeButton("Отмена",null).create());
    }
    private void renderUnifiedCollectionDetail(){
        LocalStore.UnifiedCollection u=store.unifiedCollection(openUnifiedId);
        if(u==null){screen=Screen.COLLECTIONS;render();return;}
        selectedGroup=u.groupId;selectedDeck=u.deckId;
        TextView heading=ui.displayText(u.title,25);
        heading.setGravity(Gravity.CENTER);heading.setMaxLines(2);
        ui.add(content,heading,5);
        showUnifiedCollectionSettings(u);
        final LinearLayout target=content;
        final long generation=collectionGeneration;
        final int restoreScroll=savedScrollY.getOrDefault(Screen.COLLECTION_DETAIL,0);
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
        ui.helper(content,"Освоено: "+studyProgressFor(u.groupId,snapshot));
        try{
            JSONArray history=new JSONArray(getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE)
                .getString("run_history_"+u.id,"[]"));
            if(history.length()>0){JSONObject last=history.getJSONObject(0);
                ui.helper(content,"Последняя проверка памяти: "+last.optInt("correct")+
                    " из "+last.optInt("total")+" без ошибок");}
        }catch(JSONException ignored){}
        for(Map.Entry<Integer,SortedMap<Integer,Long>> book:books.entrySet()){
            String name=Bible.NAMES[book.getKey()-1];
            collectionBookRow(name,countedRussian(book.getValue().size(),"глава","главы","глав"),()->{
                openedBookName=name;
                openedCollectionBook=book.getValue();
                openedBookProgress=snapshot;
                savedScrollY.remove(Screen.COLLECTION_BOOK);
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

    private void collectionBookRow(String title,String description,Runnable open){
        LinearLayout frame=ui.card(content);
        frame.setBackground(homeSurface());
        LinearLayout line=ui.row();line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels=ui.column();
        ui.add(labels,ui.displayText(title,17),0);
        ui.add(labels,ui.txt(description,13,MUTED,false),6);
        line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        line.addView(ui.txt("›",22,GREEN,false),new LinearLayout.LayoutParams(ui.dp(24),-2));
        ui.add(frame,line,0);
        frame.setFocusable(true);
        frame.setOnClickListener(v->open.run());
    }

    private void renderCollectionBook(){
        title(openedBookName,null);
        for(Map.Entry<Integer,Long> chapter:openedCollectionBook.entrySet()){
            long id=chapter.getValue();
            collectionBookRow("Глава "+chapter.getKey(),countedRussian(openedBookProgress.verseCount(id),
                "стих","стиха","стихов"),()->{
                openedCollectionChapter=chapter.getKey();
                savedScrollY.remove(Screen.COLLECTION_CHAPTER);
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
        List<Bible.Verse> lines=openedBookProgress.verses(id);
        for(int i=0;i<lines.size();i++){
            Bible.Verse verse=lines.get(i);
            LinearLayout item=ui.card(content);
            String status=openedBookProgress.verseConsolidated(id,i)?"закреплён":
                openedBookProgress.mastered(id,i)?"предварительно освоен":"не изучен";
            ui.add(item,ui.txt(verse.ref+" · "+status,14,GREEN,true),0);
            ui.add(item,ui.prose(verse.text,19),8);
        }
    }

    /** Fixed bottom actions, independent of the scrolling collection list. */
    private void addUnifiedCollectionFooter(){
        LinearLayout footer=ui.column();
        footer.setPadding(ui.dp(19),ui.dp(9),ui.dp(19),ui.dp(10));
        footer.setBackgroundColor(BG);
        if(screen==Screen.COLLECTIONS||screen==Screen.CARDS){
            TextView addCollection=ui.button("＋  Добавить коллекцию",true,()->newUnifiedCollection(null));
            footer.addView(addCollection,new LinearLayout.LayoutParams(-1,ui.dp(52)));
        }else if(screen==Screen.COLLECTION_DETAIL){
            final LocalStore.UnifiedCollection u=store.unifiedCollection(openUnifiedId);
            if(u==null)return;
            TextView addBible=ui.button("＋  Добавить из Библии",true,()->{
                clearInsertionContext();
                openBiblePicker(BibleTarget.STUDY_GROUP,u.groupId,Screen.COLLECTION_DETAIL);
            });
            footer.addView(addBible,new LinearLayout.LayoutParams(-1,ui.dp(51)));
            TextView own=ui.button("＋  Добавить свой текст",false,()->{
                customTargetGroupId=u.groupId;screen=Screen.CUSTOM;render();
            });
            LinearLayout.LayoutParams ownP=new LinearLayout.LayoutParams(-1,ui.dp(49));
            ownP.topMargin=ui.dp(8);footer.addView(own,ownP);
        }else return;
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
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
        else if(request==IMPORT){
            ui.styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                .setTitle("Восстановить резервную копию?")
                .setMessage("Все текущие коллекции, стихи, карточки, прогресс "+
                    "и незавершённые уроки будут заменены данными из выбранного "+
                    "файла. Это действие нельзя отменить.\n\n"+
                    "Рекомендуется сначала создать отдельную копию текущих данных.")
                .setPositiveButton("Восстановить",(d,w)->runBackupTask(uri,true))
                .setNegativeButton("Отмена",null).create());
        }
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
        homeHandler.removeCallbacks(homeTicker);
        tabWarmGeneration++;
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
        writingExecutor.shutdownNow();
        BackupCoordinator.detach(this);
        if(bibleStartup!=null)bibleStartup.cancel(false);
        if(backupDialog!=null)backupDialog.dismiss();
        tabWarmGeneration++;
        tabWarmHandler.removeCallbacksAndMessages(null);
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
        settleBibleIcons();
        settleHomeIcons();
        settleCollectionsIcons();
        bibleSearch.close();
        homeHandler.removeCallbacks(homeTicker);super.onDestroy();if(store!=null)store.close();
    }
}
