package org.biblememory.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.BlurMaskFilter;
import android.text.style.MaskFilterSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
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
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import android.view.inputmethod.EditorInfo;
import java.util.*;

/** Bright, entirely native Android UI with zero runtime dependencies. */
public final class MainActivity extends Activity {
    // The warm landscape-based interface is shared by every screen, not just the home hero.
    // Accessible dark ink sits over opaque ivory reading surfaces. Study logic is unchanged.
    private static final int BG=0xFFFFF1E8, CARD=0xFFFFFAF5, CARD_ALT=0xFFF7E4D8;
    private static final int WHITE=0xFF27372D, MUTED=0xFF735D51, GREEN=0xFFBC6448;
    private static final int VIOLET=0xFF657F75, BORDER=0xFFE8CFBE, RED=0xFFC34040;
    private static final int HOME_SURFACE=0xFFFFF8F2, HOME_BORDER=0xFFE7CCB8;
    private static final int SUCCESS=0xFF4D815C, GOLD=0xFFD8A75C;
    private static final int PRIMARY_ON=0xFFFFFFFF;
    private static final int EXPORT=10,IMPORT=11;

    private Screen screen=Screen.HOME;
    private Screen pendingRestoredScreen;
    private int pendingRestoredY,pendingSearchPosition,pendingSearchTop;
    private boolean pendingSearchScroll,pendingLessonFeedback;
    private static final String UNIFIED_PREFS="unified_library_ui";

    private LocalStore store;
    private final CollectionController collectionActions=new CollectionController(new CollectionController.Ports(){
        public StudyRepository study(){return store.study();}
        public ProgressRepository progress(){return store.progress();}
        public FlashcardRepository flashcards(){return store.flashcards();}
        public CollectionRepository collections(){return store.collections();}
        public android.content.SharedPreferences activePreferences(){return getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE);}
        public android.content.SharedPreferences queuePreferences(){return getSharedPreferences(COLLECTION_PREFS,MODE_PRIVATE);}
        public Bible bible(){return bibleReader.bible;}
        public boolean studyPicker(){return bibleReader.pickerTarget==BibleReaderController.BibleTarget.STUDY_GROUP;}
        public long pickerTargetId(){return bibleReader.pickerTargetId;}
        public int readerBook(){return bibleReader.selectedBook;}
        public void stopCardStudy(){flashcardSession.stopCardStudy();}
        public boolean hasPendingGroup(LocalStore.Entry entry){return studySession.nextGroup(entry)!=null;}
        public void openTraining(long entry){studySession.openTraining(entry);}
        public void navigate(Screen destination){screen=destination;MainActivity.this.render();}
        public void toast(String message){MainActivity.this.toast(message);}
        public void clearRunHistory(long collection){studySession.clearRunHistory(collection);}
        public boolean stopped(){return isDestroyed()||Thread.currentThread().isInterrupted();}
    });

    private final BibleReaderController bibleReader=new BibleReaderController(new BibleReaderController.Ports(){
        public Screen screen(){return screen;}
        public boolean destroyed(){return isDestroyed();}
        public android.content.Context applicationContext(){return getApplicationContext();}
        public void postToUi(Runnable callback){runOnUiThread(callback);}
        public boolean replaceVisibleSearch(List<BibleSearchLogic.Hit> hits,String query,String message){
            SearchListState state=visibleSearchResults();if(state==null)return false;state.replace(hits,query,message);restoreVisibleScroll();return true;
        }
        public void renderSearchKeepingInput(){MainActivity.this.renderBibleSearchKeepingInput();}
        public void navigate(Screen destination){screen=destination;MainActivity.this.render();}
        public void clearSavedScroll(Screen destination){savedScrollY.remove(destination);}
        public int currentScroll(){return currentScroller==null?0:currentScroller.getScrollY();}
        public android.content.SharedPreferences chapterPreferences(){return getSharedPreferences("bible_last_chapter",MODE_PRIVATE);}
        public void synchronizeCollections(Bible bible){
            collectionActions.synchronizeCollections(bible);
        }
        public void contentReady(){
            if(screen==Screen.HOME||screen==Screen.BOOKS||screen==Screen.CHAPTERS||screen==Screen.VERSES||screen==Screen.CHAPTER||screen==Screen.CARD_PICK||screen==Screen.BIBLE_PICKER||bibleReader.isPickerScreen(screen)||screen==Screen.COLLECTIONS||screen==Screen.COLLECTION_DETAIL){
                if(bibleReader.isBibleSearchScreen())renderBibleSearchKeepingInput();else MainActivity.this.render();
            }
        }
    });

    private final StudySessionController studySession=new StudySessionController(new StudySessionController.Ports(){
        public StudyRepository study(){return store.study();}
        public ProgressRepository progress(){return store.progress();}
        public CollectionRepository collections(){return store.collections();}
        public android.content.SharedPreferences studyPreferences(){return getSharedPreferences(SESSION_PREFS,MODE_PRIVATE);}
        public android.content.SharedPreferences runPreferences(){return getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE);}
        public android.content.SharedPreferences gapPreferences(){return getSharedPreferences("gap_rotation",MODE_PRIVATE);}
        public boolean collectionQueueActive(){return collectionActions.executingCollectionQueue;}
        public Screen screen(){return screen;}
        public void navigate(Screen destination){screen=destination;MainActivity.this.render();}
        public boolean collectionStepPending(){return collectionActions.collectionQueueNewStep;}
        public void collectionStepStarted(){collectionActions.collectionQueueNewStep=false;}
        public boolean collectionQueueReady(){return collectionActions.executingCollectionQueue&&collectionActions.collectionQueueCursor>=0;}
        public boolean cardStudyActive(){return flashcardSession.cardStudyCursor>=0;}
        public long studyDeckId(){return flashcardSession.cardStudyDeckId;}
        public void advanceCollection(){collectionActions.advanceStudyCollection();}
        public void advanceCardStudy(){MainActivity.this.advanceCardStudy();}
        public void saveCardCheckpoint(){flashcardSession.saveCardSession();}
        public void leaveLesson(){onBackPressed();}
        public void writingErrors(LocalStore.Entry entry,String expected,String actual,boolean detailed){showWritingErrors(entry,expected,actual,detailed);}
        public void markInlineAnswers(boolean[] correct){for(int i=0;i<correct.length&&i<inlineFields.size();i++)inlineFields.get(i).setBackgroundTintList(android.content.res.ColorStateList.valueOf(correct[i]?GREEN:RED));}
        public int lessonTileLimit(){return lessonMaxTiles();}
        public void render(){MainActivity.this.render();}
        public void toast(String message){MainActivity.this.toast(message);}
    });
    private final FlashcardSessionController flashcardSession=new FlashcardSessionController(new FlashcardSessionController.Source(){
        public StudyRepository study(){return store.study();}
        public FlashcardRepository flashcards(){return store.flashcards();}
        public android.content.SharedPreferences preferences(){return getSharedPreferences("card_sessions",MODE_PRIVATE);}
    });

    private ScrollView currentScroller;
    // The Home page is a single scroll surface; its expansion and scroll position survive navigation.
    private static final String HOME_UI_PREFS="home_ui";
    private boolean homeCollectionsExpanded=false;
    private int homeScrollY=0;
    private boolean homeScrollRestored=false;
    private Screen lastRenderedScreen=null;
    private ValueAnimator collectionAnimator;

    private int homeIndex=0;
    private static final int[][] HOME_REFS={{19,118,11},{40,5,14},{43,14,6},{45,12,21},{50,4,13},{40,6,33}};

    private LinearLayout content,root;
    private final Handler homeHandler=new Handler(Looper.getMainLooper());
    private final Runnable homeTicker=()->{refreshHomeIndex(true);scheduleHomeRefresh();};
    private LinearLayout activeTextArea,activeWorkArea;
    private TextView activePassage,activeStatus;
    private final TextView[] bottomModeButtons=new TextView[3];
    private static final String SESSION_PREFS="study_session";

    // Three-phase fullscreen lesson: stable footer, adaptive word-tile exercises.

    private Runnable revealCleanup=()->{};

    private TextView lessonBuildPrompt,lessonBuildCounter,lessonBuildError,lessonBuildAction;
    private final List<TextView> lessonBuildTiles=new ArrayList<>();

    // v0.21.4: per-exercise hotbar. Only its views are updated during a lesson.
    private final List<List<TextView>> lessonHotbarCells=new ArrayList<>();
    private TextView lessonHotbarCounter;

    private final List<EditText> inlineFields=new ArrayList<>();

    private final EnumMap<Screen,Integer> savedScrollY=new EnumMap<>(Screen.class);

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
                || s == Screen.COLLECTIONS;
    }

    private void prepareOtherTabs() {
        if(bibleReader.isBibleSearchScreen()&&bibleReader.bibleSearchActive)return;
        final int generation = ++tabWarmGeneration;

        if (!isMainTab(screen) || !bibleReader.bible.isReady()) return;

        final Screen active = screen;

        final Screen[] order = {
                Screen.HOME,
                Screen.BOOKS,
                Screen.COLLECTIONS
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

            root = column();
            root.setBackground(pageBackground());

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);

            currentScroller = scroll;

            content = column();
            content.setPadding(
                    dp(19), dp(17), dp(19), dp(27)
            );

            scroll.addView(content);

            if (destination == Screen.BOOKS) {
                renderScrollingBibleSearch(content);
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
                        bibleReader.bibleSearchActive &&
                        !bibleReader.bibleSearchLoading &&
                        bibleReader.bibleSearchError.isEmpty()) {

                    root.removeView(scroll);

                    root.addView(
                            createSearchResultsList(),
                            new LinearLayout.LayoutParams(-1, 0, 1)
                    );

                    currentScroller = null;
                }

            } else {
                renderUnifiedCollections();
                addUnifiedCollectionFooter();
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

            int slot = (bar.getWidth() - dp(14)) / 3;
            if (slot <= 0) return;

            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams)
                            indicator.getLayoutParams();

            params.width = slot;
            params.height = dp(57);
            params.leftMargin = dp(7);
            params.topMargin = dp(7);

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
    private float swipeStartX,swipeStartY;
    private boolean swipeIntercepted=false;
    private int lastNavIndex=0;

    private final List<View> pickerQuickButtons=new ArrayList<>();
    private TextView pickerSelectionLabel,pickerSaveButton;
    private LinearLayout pickerSelectionPanel;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(CARD);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        store=new LocalStore(this);
        collectionActions.activeUnifiedId=getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE).getLong("active",-1);
        collectionActions.ensureActiveUnified();
        collectionActions.restoreCollectionQueue();
        android.content.SharedPreferences homeUi=getSharedPreferences(HOME_UI_PREFS,MODE_PRIVATE);
        homeCollectionsExpanded=homeUi.getBoolean("collections_expanded",false);
        homeScrollY=Math.max(0,homeUi.getInt("scroll_y",0));
        refreshHomeIndex(false);
        if(savedInstanceState!=null&&savedInstanceState.containsKey("navigation_v1"))restoreActivityState(savedInstanceState);
        else restorePickerState(savedInstanceState);
        render();
        bibleReader.startLoading();
    }

    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);
        if(screen==Screen.TRAIN)studySession.saveTrainingSession();
        if(screen==Screen.CARD_SESSION)flashcardSession.saveCardSession();
        if(screen==Screen.RUN)studySession.saveRunSession();
        out.putString("navigation_v1",screen.name());
        out.putBundle("study_v1",studySession.saveState());
        out.putBundle("cards_v1",flashcardSession.saveState());
        out.putBundle("reader_v1",bibleReader.saveState());
        out.putBundle("collections_v1",collectionActions.saveState());
        Bundle scrolls=new Bundle();for(Map.Entry<Screen,Integer> item:savedScrollY.entrySet())scrolls.putInt(item.getKey().name(),item.getValue());
        ScrollView visible=findScroll(root);
        boolean pending=pendingRestoredScreen==screen;
        out.putBoolean("scroll_available",pending||(visible!=null&&visible.getHeight()>0));
        out.putInt("scroll_y",pending?pendingRestoredY:visible==null?0:visible.getScrollY());
        out.putBundle("scrolls",scrolls);
        ListView search=visibleSearchResults()==null?null:(ListView)root.getChildAt(0);
        if(search!=null){
            out.putBoolean("search_scroll",true);out.putInt("search_position",search.getFirstVisiblePosition());
            out.putInt("search_top",search.getChildCount()==0?0:search.getChildAt(0).getTop());
        }else if(pendingSearchScroll){
            out.putBoolean("search_scroll",true);out.putInt("search_position",pendingSearchPosition);out.putInt("search_top",pendingSearchTop);
        }
        if(!bibleReader.isPickerScreen(screen))return;
        bibleReader.savePickerState(out);
        out.putLong("picker_unified",collectionActions.openUnifiedId);out.putLong("picker_group",collectionActions.selectedGroup);
        out.putLong("picker_deck",flashcardSession.selectedDeck);
        out.putLong("picker_insert_entry",collectionActions.insertAfterEntryId);out.putLong("picker_insert_card",collectionActions.insertAfterCardId);
    }
    private void restoreActivityState(Bundle state){
        studySession.restoreState(state.getBundle("study_v1"));
        flashcardSession.restoreState(state.getBundle("cards_v1"));
        bibleReader.restoreState(state.getBundle("reader_v1"));
        collectionActions.restoreState(state.getBundle("collections_v1"));
        try{screen=Screen.valueOf(state.getString("navigation_v1","HOME"));}
        catch(IllegalArgumentException invalid){screen=Screen.HOME;}
        Bundle scrolls=state.getBundle("scrolls");
        if(scrolls!=null)for(Screen destination:Screen.values())if(scrolls.containsKey(destination.name()))savedScrollY.put(destination,scrolls.getInt(destination.name()));
        if(state.getBoolean("scroll_available")){pendingRestoredScreen=screen;pendingRestoredY=Math.max(0,state.getInt("scroll_y"));}
        pendingSearchScroll=state.getBoolean("search_scroll");pendingSearchPosition=state.getInt("search_position");pendingSearchTop=state.getInt("search_top");
        if(pendingSearchScroll)pendingRestoredScreen=screen;
        pendingLessonFeedback=screen==Screen.TRAIN&&studySession.lessonWriteHadFeedback&&studySession.lastAccuracy>=0;
    }
    private ScrollView findScroll(View view){
        if(view instanceof ScrollView)return (ScrollView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){ScrollView found=findScroll(group.getChildAt(i));if(found!=null)return found;}
        }return null;
    }
    private void restoreVisibleScroll(){
        if(pendingRestoredScreen!=null&&pendingRestoredScreen!=screen){pendingRestoredScreen=null;pendingSearchScroll=false;return;}
        if(pendingSearchScroll&&bibleReader.isBibleSearchScreen()){
            ListView list=visibleSearchResults()==null?null:(ListView)root.getChildAt(0);
            if(list!=null&&!bibleReader.bibleSearchLoading){
                final View page=root;afterPickerLayout(list,()->{if(root==page){list.setSelectionFromTop(pendingSearchPosition,pendingSearchTop);pendingSearchScroll=false;pendingRestoredScreen=null;}});
            }return;
        }
        if(pendingRestoredScreen!=screen)return;
        if((screen==Screen.CHAPTER||screen==Screen.CHAPTERS||screen==Screen.VERSES||bibleReader.isPickerScreen(screen))&&!bibleReader.bible.isReady())return;
        ScrollView scroll=findScroll(root);if(scroll==null)return;
        final Screen destination=screen;final View page=root;final int y=pendingRestoredY;
        afterPickerLayout(scroll,()->{if(root==page&&screen==destination){scroll.scrollTo(0,y);pendingRestoredScreen=null;}});
    }
    private void restorePickerState(Bundle state){
        if(state==null||!state.containsKey("picker_screen"))return;
        try{screen=Screen.valueOf(state.getString("picker_screen"));}
        catch(IllegalArgumentException|NullPointerException invalid){screen=Screen.HOME;bibleReader.passageSelection.cancel();return;}
        if(!bibleReader.restorePickerState(state)){screen=Screen.HOME;return;}
        collectionActions.openUnifiedId=state.getLong("picker_unified");collectionActions.selectedGroup=state.getLong("picker_group");
        flashcardSession.selectedDeck=state.getLong("picker_deck");
        collectionActions.insertAfterEntryId=state.getLong("picker_insert_entry",-1);collectionActions.insertAfterCardId=state.getLong("picker_insert_card",-1);
    }
    @Override protected void onResume() {
        super.onResume();
        settleBibleIcons();
        settleHomeIcons();
        settleCollectionsIcons();
        if(store!=null){
            if(screen==Screen.TRAIN&&studySession.selectedEntry>0&&!studySession.validSession()){
                invalidateRemovedSessions();screen=Screen.HOME;render();
            }else if(screen==Screen.RUN&&!studySession.validRun()){studySession.runLines.clear();screen=Screen.HOME;render();}
            refreshHomeIndex(true);scheduleHomeRefresh();
        }
    }
    @Override protected void onPause() {
        settleBibleIcons();
        settleHomeIcons();
        settleCollectionsIcons();
        revealCleanup.run();
        if(screen==Screen.TRAIN)studySession.saveTrainingSession();
        if(screen==Screen.CARD_SESSION)flashcardSession.saveCardSession();
        if(screen==Screen.RUN)studySession.saveRunSession();
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
        switch(screen) {
            case HOME: super.onBackPressed(); break;
            case BOOKS:case DUE:case CARDS:case COLLECTIONS:screen=Screen.HOME;render();break;
            case COLLECTION_DETAIL:screen=Screen.COLLECTIONS;render();break;
            case RUN:studySession.saveRunSession();screen=Screen.HOME;render();break;
            case RUN_SUMMARY:screen=Screen.HOME;render();break;
            case CARD_DECK:screen=Screen.CARDS;render();break;
            case CARD_PICK:screen=Screen.CARD_DECK;render();break;
            case BIBLE_PICKER:
                bibleReader.passageSelection.active=false;bibleReader.pickerChooseEnd=false;
                bibleReader.selectedBook=bibleReader.pickerQuickBook;bibleReader.selectedChapter=bibleReader.pickerQuickChapter;
                bibleReader.pickerRestoredScroll=bibleReader.pickerQuickScroll;bibleReader.focusVerse=-1;
                screen=Screen.PICKER_TEXT;render();break;
            case PICKER_BOOKS:
                if(bibleReader.passageSelection.active){screen=Screen.BIBLE_PICKER;render();break;}
                collectionActions.clearInsertionContext();bibleReader.passageSelection.cancel();
                bibleReader.selectedBook=bibleReader.readerBookBeforePicker;bibleReader.selectedChapter=bibleReader.readerChapterBeforePicker;
                screen=bibleReader.pickerBackScreen;render();break;
            case PICKER_CHAPTERS:
                screen=bibleReader.passageSelection.active?Screen.BIBLE_PICKER:Screen.PICKER_BOOKS;render();break;
            case PICKER_VERSES:screen=Screen.PICKER_CHAPTERS;render();break;
            case PICKER_TEXT:screen=Screen.PICKER_VERSES;render();break;
            case CARD_SESSION:flashcardSession.saveCardSession();screen=flashcardSession.cardFromUnified?Screen.HOME:Screen.CARD_DECK;render();break;
            case CHAPTERS:screen=Screen.BOOKS;render();break;
            case VERSES:screen=Screen.CHAPTERS;render();break;
            case CHAPTER:screen=bibleReader.chapterFromVerseGrid?Screen.VERSES:Screen.CHAPTERS;render();break;
            case ENTRIES:screen=Screen.HOME;render();break;
            case REORDER_STUDY:
                {LocalStore.UnifiedCollection u=store.collections().unifiedForGroup(collectionActions.selectedGroup);
                screen=u==null?Screen.ENTRIES:Screen.COLLECTION_DETAIL;
                if(u!=null)collectionActions.openUnifiedId=u.id;render();break;}
            case REORDER_CARDS:screen=Screen.CARD_DECK;render();break;
            case CUSTOM:{
                LocalStore.UnifiedCollection u=store.collections().unifiedForGroup(collectionActions.customTargetGroupId);
                collectionActions.customTargetGroupId=-1;screen=u==null?Screen.HOME:Screen.COLLECTION_DETAIL;
                if(u!=null)collectionActions.openUnifiedId=u.id;render();break;}
            case TRAIN:
                collectionActions.executingCollectionQueue=false;
                flashcardSession.cardStudyQueue.clear();flashcardSession.cardStudyCursor=-1;flashcardSession.cardStudyDeckId=-1;
                studySession.saveTrainingSession();
                studySession.reviewMode=false;studySession.groupMode=false;studySession.forcedStep=null;studySession.currentReview=null;
                studySession.reviewQueue.clear();studySession.reviewPointer=0;
                // The lesson is an isolated activity: every exit goes to HOME.
                screen=Screen.HOME;render();break;
        }
    }
    private int dp(float d) {return (int)(getResources().getDisplayMetrics().density*d+0.5f);}
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private GradientDrawable outlined(int color,int radius,int border) {
        GradientDrawable d=shape(color,radius);d.setStroke(dp(1),border);return d;
    }
    private GradientDrawable gradient() {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFFFFD7BF,0xFFF6D1C3,0xFFE1E9DD});
        d.setCornerRadius(dp(25));d.setStroke(dp(1),BORDER);return d;
    }
    /** Subtle watercolor-like mountain silhouettes outside the opaque text surfaces. */
    private android.graphics.drawable.Drawable pageBackground(){
        return new android.graphics.drawable.Drawable(){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas){
                Rect bounds=getBounds();float w=bounds.width(),h=bounds.height();
                if(w<=0||h<=0)return;
                paint.setShader(new LinearGradient(0,0,0,h,
                    0xFFFFF7F0,0xFFF8E5D9,Shader.TileMode.CLAMP));
                canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
                Path far=new Path();far.moveTo(0,dp(85));
                far.lineTo(w*.09f,dp(64));far.lineTo(w*.20f,dp(85));
                far.lineTo(w*.31f,dp(49));far.lineTo(w*.44f,dp(95));
                far.lineTo(w*.58f,dp(57));far.lineTo(w*.75f,dp(88));
                far.lineTo(w*.90f,dp(62));far.lineTo(w,dp(91));
                far.lineTo(w,0);far.lineTo(0,0);far.close();
                paint.setColor(0x50EEC3B4);canvas.drawPath(far,paint);
                Path foothills=new Path();foothills.moveTo(0,h);
                foothills.lineTo(0,h-dp(72));foothills.lineTo(w*.19f,h-dp(110));
                foothills.lineTo(w*.36f,h-dp(62));foothills.lineTo(w*.57f,h-dp(126));
                foothills.lineTo(w*.79f,h-dp(85));foothills.lineTo(w,h-dp(120));
                foothills.lineTo(w,h);foothills.close();
                paint.setColor(0x55B8CEC2);canvas.drawPath(foothills,paint);
            }
            @Override public void setAlpha(int alpha){}
            @Override public void setColorFilter(android.graphics.ColorFilter filter){}
            @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        };
    }
    private GradientDrawable softAccent(){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFFDB8C6C,0xFFB8583F});d.setCornerRadius(dp(18));return d;
    }
    private TextView displayText(String text,int size){
        TextView view=txt(text,size,WHITE,true);
        view.setTypeface(Typeface.create("serif",Typeface.BOLD));
        view.setLetterSpacing(0.005f);return view;
    }
    private TextView prose(String text,int size){
        TextView view=txt(text,size,WHITE,false);
        view.setTypeface(Typeface.create("serif",Typeface.NORMAL));
        view.setLineSpacing(dp(4),1.14f);return view;
    }
    private int bookInk(int book){
        int hue=BookColorLogic.color(book);
        return Color.rgb((Color.red(hue)*2+Color.red(WHITE))/4,
            (Color.green(hue)*2+Color.green(WHITE))/4,
            (Color.blue(hue)*2+Color.blue(WHITE))/4);
    }
    /** Decorative scenery only; long reading passages remain on opaque ivory cards. */
    private void illustratedBanner(LinearLayout into,String heading,long scene,int height){
        FrameLayout art=new FrameLayout(this);
        art.setBackground(outlined(CARD,22,BORDER));art.setClipToOutline(true);
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
            TextView name=displayText(heading,26);name.setGravity(Gravity.CENTER);
            name.setShadowLayer(dp(4),0,dp(2),0xAAFFF5E9);
            FrameLayout.LayoutParams label=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
            label.setMargins(dp(14),dp(16),dp(14),0);art.addView(name,label);
        }
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(height));
        p.topMargin=dp(12);into.addView(art,p);
    }
    private TextView txt(String value,int sp,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        t.setIncludeFontPadding(true);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        t.setLineSpacing(dp(2),1.10f);
        return t;
    }
    private LinearLayout column() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void add(LinearLayout into,View v,int top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);into.addView(v,p);
    }
    private void gap(LinearLayout into,int d) {View v=new View(this);into.addView(v,new LinearLayout.LayoutParams(1,dp(d)));}
    private LinearLayout card(LinearLayout into) {
        LinearLayout l=column();l.setPadding(dp(17),dp(18),dp(17),dp(18));
        l.setBackground(outlined(CARD,22,BORDER));l.setElevation(dp(2));
        add(into,l,12);return l;
    }
    private LinearLayout headerCard(LinearLayout into) {
        LinearLayout l=column();l.setPadding(dp(19),dp(23),dp(19),dp(23));
        l.setBackground(gradient());add(into,l,10);return l;
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
        d.setCornerRadius(dp(22));d.setStroke(dp(1),HOME_BORDER);return d;
    }
    /** A small decorative scenic preview; accessible names remain the collection labels. */
    private ImageView landscapeThumbnail(long index){
        ImageView thumb=new ImageView(this);
        thumb.setImageResource(landscapeFor(index));
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(shape(CARD_ALT,14));
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
                                dp(24)
                        );
                    }
                }
        );

        hero.setClipToOutline(true);
        hero.setElevation(dp(3));

        // Содержимое определяет реальную высоту.
        LinearLayout body = column();

        body.setGravity(
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );

        body.setPadding(
                dp(22),
                dp(14),
                dp(22),
                dp(18)
        );

        // Оригинальная плашка.
        TextView heading = txt(
                "Стих дня",
                15,
                GREEN,
                true
        );

        heading.setGravity(Gravity.CENTER);

        heading.setPadding(
                dp(19),
                dp(8),
                dp(19),
                dp(8)
        );

        heading.setBackground(
                shape(0xE9FFE4D3, 25)
        );

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(-2, -2);

        headingParams.gravity = Gravity.CENTER_HORIZONTAL;

        body.addView(heading, headingParams);

        // Текст стиха полностью сохраняется.
        TextView verse = prose(
                "«" + verseText + "»",
                22
        );

        verse.setGravity(Gravity.CENTER);

        add(body, verse, 12);

        // Ссылка на стих.
        TextView source = displayText(reference, 17);

        source.setGravity(Gravity.CENTER);
        source.setTextColor(0xFF83503E);

        add(body, source, 10);

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

        heroParams.topMargin = dp(12);

        content.addView(hero, heroParams);
    }

    private TextView button(String label,boolean primary,Runnable action) {
        TextView b=txt(label,15,primary?PRIMARY_ON:WHITE,true);
        b.setGravity(Gravity.CENTER);b.setMinHeight(dp(49));
        b.setPadding(dp(14),dp(11),dp(14),dp(11));
        b.setBackground(primary?softAccent():outlined(CARD_ALT,18,BORDER));
        b.setClickable(true);b.setFocusable(true);b.setOnClickListener(v->action.run());
        return b;
    }
    private void action(LinearLayout into,String label,boolean primary,Runnable runnable) {add(into,button(label,primary,runnable),10);}
    private LinearLayout tapCard(LinearLayout into,String title,String secondary,String arrow,Runnable action) {
        LinearLayout c=card(into);
        LinearLayout line=row();
        LinearLayout left=column();left.addView(txt(title,16,WHITE,true));
        if(secondary!=null&&!secondary.isEmpty())add(left,txt(secondary,13,MUTED,false),4);
        line.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        line.addView(txt(arrow,18,GREEN,true));
        c.addView(line);c.setClickable(true);c.setFocusable(true);c.setOnClickListener(v->action.run());
        return c;
    }
    private void label(LinearLayout into,String label) { add(into,txt(label,15,WHITE,true),14); }
    private void helper(LinearLayout into,String label) { add(into,txt(label,13,MUTED,false),5); }
    private void progress(LinearLayout into,int value,int total) {
        LinearLayout track=column();track.setBackground(shape(CARD_ALT,12));
        LinearLayout.LayoutParams trackP=new LinearLayout.LayoutParams(-1,dp(8));trackP.topMargin=dp(12);
        into.addView(track,trackP);
        View fill=new View(this);fill.setBackground(shape(GREEN,12));
        int pct=total<=0?0:Math.max(0,Math.min(100,Math.round(100f*value/total)));
        track.post(()->{LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(Math.max(dp(3),track.getWidth()*pct/100),dp(8));fill.setLayoutParams(p);});
        track.addView(fill,new LinearLayout.LayoutParams(dp(3),dp(8)));
    }
    private EditText input(String hint,boolean multiline) {
        EditText e=new EditText(this);
        e.setTextColor(WHITE);e.setHintTextColor(MUTED);e.setTextSize(15);
        e.setSingleLine(!multiline);e.setHint(hint);
        e.setInputType(multiline?android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES:android.text.InputType.TYPE_CLASS_TEXT);
        e.setGravity(multiline?Gravity.TOP|Gravity.START:Gravity.CENTER_VERTICAL);
        e.setPadding(dp(15),dp(13),dp(15),dp(13));
        e.setBackground(outlined(CARD_ALT,14,BORDER));
        if(multiline){e.setMinLines(5);e.setMaxLines(12);}
        return e;
    }
    private void forbidKeyboardHints(EditText edit,boolean multiline){
        int flags=android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if(multiline)flags|=android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        edit.setInputType(flags);
        edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        edit.setPrivateImeOptions("nm");
    }
    private EditText numeric(String hint,String initial) {
        EditText e=input(hint,false);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setText(initial);e.setSelectAllOnFocus(true);return e;
    }
    private int number(EditText e){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return -1;}}
    private String value(EditText e){return e.getText().toString().trim();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    /** Compact wordmark for primary tabs: no redundant page title or empty title spacing. */
    private void tabWordmark(){
        // The app name intentionally stays on the Android launcher, not within screens.
    }
    private void title(String main,String subtitle) {
        TextView heading=displayText(main,26);
        heading.setGravity(Gravity.CENTER);
        heading.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        add(content,heading,8);
        if(subtitle!=null&&!subtitle.isEmpty()){
            TextView sub=txt(subtitle,14,MUTED,false);
            sub.setGravity(Gravity.CENTER);add(content,sub,6);
        }
        gap(content,6);
    }
    /** Section boundary is visually centered, with measured whitespace only at the canon split. */
    private void testamentHeading(String text,boolean startOfNewTestament){
        if(startOfNewTestament)gap(content,28);
        LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);
        View left=new View(this),right=new View(this);
        left.setBackgroundColor(HOME_BORDER);right.setBackgroundColor(HOME_BORDER);
        row.addView(left,new LinearLayout.LayoutParams(0,dp(1),1));
        TextView heading=displayText(text,21);
        heading.setGravity(Gravity.CENTER);heading.setPadding(dp(14),dp(6),dp(14),dp(6));
        row.addView(heading,new LinearLayout.LayoutParams(-2,-2));
        row.addView(right,new LinearLayout.LayoutParams(0,dp(1),1));
        add(content,row,startOfNewTestament?3:13);
    }
    private void rememberHomeScroll(){
        if(lastRenderedScreen==Screen.HOME && homeScrollRestored && currentScroller!=null){
            homeScrollY=Math.max(0,currentScroller.getScrollY());
            getSharedPreferences(HOME_UI_PREFS,MODE_PRIVATE).edit().putInt("scroll_y",homeScrollY).apply();
        }
    }
    private void render() {
        CollectionsNavigationIcon previousCollections=root==null?null:root.findViewWithTag(CollectionsNavigationIcon.TAG);
        if(previousCollections!=null && previousCollections.isSelected())
            collectionsIconCycle=previousCollections.settledCycle();
        if(!bibleReader.isBibleSearchScreen()&&bibleReader.bibleSearchLoading){
            bibleReader.cancelBibleSearchWork();bibleReader.bibleSearchLoading=false;bibleReader.bibleSearchNeedsRefresh=true;
        }
        bibleReader.resumeBibleSearchIfNeeded();
        finishTabTransition();
        tabWarmGeneration++;
        readyTabs.clear();
        rememberHomeScroll();
        if(currentScroller!=null && lastRenderedScreen!=null && lastRenderedScreen!=Screen.HOME && pendingRestoredScreen!=lastRenderedScreen){
            savedScrollY.put(lastRenderedScreen,currentScroller.getScrollY());
        }
        if(collectionAnimator!=null){collectionAnimator.cancel();collectionAnimator=null;}
        if(screen==Screen.TRAIN){lastRenderedScreen=screen;renderFullscreenLesson();restoreVisibleScroll();return;}
        if(screen==Screen.CARD_SESSION){lastRenderedScreen=screen;renderCardSession();restoreVisibleScroll();return;}
        if(screen==Screen.RUN){lastRenderedScreen=screen;renderWritingRun();restoreVisibleScroll();return;}
        for(int i=0;i<bottomModeButtons.length;i++)bottomModeButtons[i]=null;
        boolean focusedReaderChapter=screen==Screen.CHAPTER && bibleReader.chapterFromVerseGrid && bibleReader.focusVerse>0;
        boolean virtualBibleSearch=bibleReader.isBibleSearchScreen()&&bibleReader.bibleSearchActive&&!bibleReader.bibleSearchLoading&&bibleReader.bibleSearchError.isEmpty();
        root=column();root.setBackground(pageBackground());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        currentScroller=scroll;
        content=column();content.setPadding(dp(19),dp(17),dp(19),dp(27));scroll.addView(content);
        if(bibleReader.isBibleSearchScreen()&&!virtualBibleSearch)renderScrollingBibleSearch(content);
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
            case TRAIN: renderTraining();break;
            case CARDS: renderUnifiedCollections();break;
            case COLLECTIONS:renderUnifiedCollections();break;
            case COLLECTION_DETAIL:renderUnifiedCollectionDetail();break;
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
            ListView results = createSearchResultsList();

            root.addView(
                    results,
                    new LinearLayout.LayoutParams(
                            -1, 0, 1
                    )
            );

            currentScroller = null;
        }

        if(screen==Screen.BIBLE_PICKER && pickerSelectionPanel!=null)root.addView(pickerSelectionPanel);
        if(screen==Screen.COLLECTIONS||screen==Screen.CARDS||
            screen==Screen.COLLECTION_DETAIL) addUnifiedCollectionFooter();
        if(screen!=Screen.CUSTOM && screen!=Screen.RUN_SUMMARY) addNav(root);

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

        boolean restoredPickerScroll=bibleReader.isPickerScreen(screen)&&bibleReader.bible.isReady()&&bibleReader.pickerRestoredScroll>=0;
        if(restoredPickerScroll){
            int y=bibleReader.pickerRestoredScroll;bibleReader.pickerRestoredScroll=-1;
            afterPickerLayout(content,()->{if(currentScroller==scroll)scroll.scrollTo(0,y);});
        }
        if(!restoredPickerScroll && !focusedReaderChapter && screen!=Screen.HOME && savedScrollY.containsKey(screen) &&
            !(screen==Screen.BOOKS&&bibleReader.bibleSearchActive) &&
            !(screen==Screen.PICKER_BOOKS&&bibleReader.bibleSearchActive) &&
            screen!=Screen.PICKER_TEXT){
            int y=savedScrollY.get(screen);
            scroll.post(()->{if(currentScroller==scroll)scroll.scrollTo(0,y);});
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
        restoreVisibleScroll();
    }
    /** Search is the first scrolling item, both in the catalogue and the search results. */
    private void renderScrollingBibleSearch(LinearLayout into){
        LinearLayout outer=column();
        if(screen!=Screen.BOOKS)outer.setBackgroundColor(BG);
        outer.setPadding(0,dp(8),0,dp(8));
        LinearLayout bar=row();bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14),dp(3),dp(7),dp(3));
        bar.setMinimumHeight(dp(58));
        GradientDrawable frame=shape(0xFFFFFDF9,17);
        frame.setStroke(dp(2),0xFFCE9072);
        bar.setBackground(frame);bar.setElevation(dp(2));
        NavigationGlyph glass=new NavigationGlyph("search",GREEN);
        bar.addView(glass,new LinearLayout.LayoutParams(dp(29),dp(29)));
        EditText query=input("Поиск по словам стиха",false);
        query.setTextSize(17);query.setSingleLine(true);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setPadding(dp(12),dp(9),dp(5),dp(9));
        query.setBackgroundColor(Color.TRANSPARENT);
        query.setText(bibleReader.bibleSearchQuery);query.setSelection(query.length());
        query.setContentDescription("Поиск по словам Библии");
        bar.addView(query,new LinearLayout.LayoutParams(0,-2,1));
        TextView erase=txt("×",24,MUTED,false);erase.setGravity(Gravity.CENTER);
        erase.setContentDescription("Очистить поиск по Библии");
        erase.setVisibility(bibleReader.bibleSearchQuery.isEmpty()?View.GONE:View.VISIBLE);
        bar.addView(erase,new LinearLayout.LayoutParams(dp(40),dp(45)));
        erase.setOnClickListener(v->query.setText(""));
        // The magnifier submits too; the keyboard search action does the same.
        glass.setClickable(true);
        glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        glass.setContentDescription("Найти по Библии");
        glass.setOnClickListener(v->bibleReader.submitBibleSearch(query.getText().toString()));
        query.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH){
                bibleReader.submitBibleSearch(query.getText().toString());return true;
            }
            return false;
        });
        query.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                erase.setVisibility(s.length()==0?View.GONE:View.VISIBLE);
                onBibleSearchChanged(s.toString(),query);
            }
            public void afterTextChanged(Editable e){}
        });
        outer.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        into.addView(outer,new LinearLayout.LayoutParams(-1,-2));
    }

    private EditText findBibleSearchInput(View view){
        if(view instanceof EditText&&"Поиск по словам Библии".contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return (EditText)view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                EditText found=findBibleSearchInput(group.getChildAt(i));if(found!=null)return found;
            }
        }
        return null;
    }
    private void renderBibleSearchKeepingInput(){
        EditText old=root==null?null:findBibleSearchInput(root);
        boolean focused=old!=null&&old.hasFocus();
        int cursor=old==null?0:Math.max(0,old.getSelectionStart());
        int selectionEnd=old==null?0:Math.max(0,old.getSelectionEnd());
        render();
        if(focused){
            EditText next=findBibleSearchInput(root);
            if(next!=null){next.requestFocus();next.setSelection(Math.min(cursor,next.length()),Math.min(selectionEnd,next.length()));}
        }
    }
    private SearchListState visibleSearchResults(){
        if(root!=null&&root.getChildCount()>0&&root.getChildAt(0) instanceof ListView){
            Object state=root.getChildAt(0).getTag();
            if(state instanceof SearchListState)return (SearchListState)state;
        }
        return null;
    }
    private void onBibleSearchChanged(String value,EditText input){
        if(!input.isAttachedToWindow())return;
        bibleReader.onQueryChanged(value);
    }

    private CharSequence highlightSearchWords(String source,BibleSearchLogic.WordMatcher words){
        android.text.SpannableString span=new android.text.SpannableString(source);
        java.util.regex.Matcher matcher=BibleSearchLogic.WORDS.matcher(source);
        while(matcher.find())if(words.matches(matcher.group()))
            span.setSpan(new BackgroundColorSpan(0xFFFFE4BD),matcher.start(),matcher.end(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }
    private static final class SearchListState {
        List<BibleSearchLogic.Hit> hits;
        BibleSearchLogic.WordMatcher words;
        TextView summary;
        BaseAdapter adapter;
        final android.util.LruCache<Integer,CharSequence> highlights=new android.util.LruCache<>(128);
        SearchListState(List<BibleSearchLogic.Hit> hits,String query){this.hits=hits;words=new BibleSearchLogic.WordMatcher(query);}
        void replace(List<BibleSearchLogic.Hit> results,String query,String message){
            hits=results;words=new BibleSearchLogic.WordMatcher(query);highlights.evictAll();
            summary.setText(message!=null?message:results.isEmpty()?"Ничего не найдено":"Найдено стихов: "+results.size());
            adapter.notifyDataSetChanged();
        }
    }

    private void renderSearchResults() {

        if (bibleReader.bibleSearchLoading) {
            helper(content, "Поиск...");
            return;
        }

        if (!bibleReader.bibleSearchError.isEmpty()) {
            helper(content, bibleReader.bibleSearchError);

            action(
                    content,
                    "Повторить поиск",
                    false,
                    () -> bibleReader.submitBibleSearch(bibleReader.bibleSearchQuery)
            );
            return;
        }

        if (bibleReader.bibleSearchHits.isEmpty()) {
            helper(
                    content,
                    "Ничего не найдено"
            );
        }

        // При наличии результатов список создаётся
        // отдельно, методом createSearchResultsList().
    }

    private static final class SearchRowHolder {
        TextView reference;
        TextView verse;
        TextView open;
    }

    private ListView createSearchResultsList() {

        ListView list = new ListView(this);

        list.setBackgroundColor(BG);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        // The scrolling list header contains a usable EditText search field.
        list.setItemsCanFocus(true);

        final SearchListState state=new SearchListState(bibleReader.bibleSearchHits,bibleReader.bibleSearchQuery);
        list.setTag(state);
        // A ListView is used for large results. Its search field is a header so
        // the field scrolls away with result rows instead of remaining pinned.
        LinearLayout searchHeader=column();
        searchHeader.setPadding(dp(19),0,dp(19),0);
        renderScrollingBibleSearch(searchHeader);
        list.addHeaderView(searchHeader,null,false);
        // Показываем реальное число ВСЕХ результатов.
        TextView summary = txt(
                state.hits.isEmpty()?"Ничего не найдено":"Найдено стихов: " + state.hits.size(),
                14,
                MUTED,
                true
        );

        summary.setPadding(
                dp(19), dp(15),
                dp(19), dp(11)
        );

        list.addHeaderView(summary, null, false);

        state.summary=summary;
        BaseAdapter adapter=new BaseAdapter() {

            @Override
            public int getCount() {
                return state.hits.size();
            }

            @Override
            public Object getItem(int position) {
                return state.hits.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(
                    int position,
                    View convertView,
                    ViewGroup parent) {

                SearchRowHolder holder;
                View result = convertView;

                if (result == null) {

                    LinearLayout outer = column();

                    outer.setPadding(
                            dp(19), dp(4),
                            dp(19), dp(7)
                    );

                    LinearLayout card = column();

                    card.setPadding(
                            dp(15), dp(13),
                            dp(15), dp(13)
                    );

                    card.setBackground(
                            outlined(CARD, 18, BORDER)
                    );

                    LinearLayout heading = row();

                    heading.setGravity(
                            Gravity.CENTER_VERTICAL
                    );

                    TextView reference = txt(
                            "", 15, GREEN, true
                    );

                    heading.addView(
                            reference,
                            new LinearLayout.LayoutParams(
                                    0, -2, 1
                            )
                    );

                    card.addView(heading);

                    TextView verse = prose("", 16);

                    LinearLayout.LayoutParams vp =
                            new LinearLayout.LayoutParams(
                                    -1, -2
                            );

                    vp.topMargin = dp(8);
                    card.addView(verse, vp);

                    TextView open = txt(
                            "Показать в главе  ›",
                            13,
                            GREEN,
                            true
                    );

                    open.setPadding(
                            0, dp(11), 0, dp(8)
                    );

                    LinearLayout.LayoutParams op =
                            new LinearLayout.LayoutParams(
                                    -1, dp(48)
                            );

                    op.topMargin = dp(3);
                    card.addView(open, op);

                    outer.addView(
                            card,
                            new LinearLayout.LayoutParams(
                                    -1, -2
                            )
                    );

                    holder = new SearchRowHolder();

                    holder.reference = reference;
                    holder.verse = verse;
                    holder.open = open;

                    outer.setTag(holder);

                    result = outer;

                } else {
                    holder = (SearchRowHolder) result.getTag();
                }

                BibleSearchLogic.Hit hit =
                        state.hits.get(position);

                holder.reference.setText(hit.reference);
                holder.reference.setTextColor(
                        bookInk(hit.book)
                );

                // Подсветка выполняется только для
                // строк, которые сейчас видны.
                CharSequence highlighted=state.highlights.get(position);
                if(highlighted==null){highlighted=highlightSearchWords(hit.text,state.words);state.highlights.put(position,highlighted);}
                holder.verse.setText(highlighted);

                holder.open.setOnClickListener(v -> {

                    bibleReader.selectedBook = hit.book;
                    bibleReader.selectedChapter = hit.chapter;

                    if(!bibleReader.isPickerScreen(screen))bibleReader.rememberChapter(hit.book,hit.chapter);

                    bibleReader.focusVerse = hit.verse;

                    boolean inPicker =
                            screen == Screen.PICKER_BOOKS ||
                            screen == Screen.BIBLE_PICKER;

                    bibleReader.bibleSearchActive = false;
                    bibleReader.bibleSearchQuery = "";
                    bibleReader.bibleSearchHits = new ArrayList<>();

                    if (inPicker) {
                        screen = Screen.PICKER_TEXT;
                    } else {
                        bibleReader.chapterFromVerseGrid = false;
                        screen = Screen.CHAPTER;
                    }

                    render();
                });

                return result;
            }
        };
        state.adapter=adapter;list.setAdapter(adapter);
        return list;
    }

    /** Outline navigation icons, independent of system fonts and Android theme packs. */
    private final class NavigationGlyph extends View {
        private final String kind;
        private final int ink;
        private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        NavigationGlyph(String kind,int ink){super(MainActivity.this);this.kind=kind;this.ink=ink;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float size=dp(23),cx=getWidth()/2f,cy=getHeight()/2f;
            canvas.save();canvas.translate(cx,cy);canvas.scale(size/24f,size/24f);
            pen.setColor(ink);pen.setStrokeWidth(1.85f);pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setStrokeJoin(Paint.Join.ROUND);pen.setStyle(Paint.Style.STROKE);
            Path path=new Path();
            if(kind.equals("home")){
                path.moveTo(-10,-1);path.lineTo(0,-10);path.lineTo(10,-1);
                path.lineTo(10,10);path.lineTo(-10,10);path.close();canvas.drawPath(path,pen);
                canvas.drawRoundRect(-2,3,2,10,1,1,pen);
            }else if(kind.equals("book")){
                path.moveTo(0,-8);path.cubicTo(-4,-11,-8,-10,-11,-8);
                path.lineTo(-11,9);path.cubicTo(-6,7,-2,8,0,10);
                path.cubicTo(2,8,6,7,11,9);path.lineTo(11,-8);
                path.cubicTo(8,-10,4,-11,0,-8);path.close();canvas.drawPath(path,pen);
                canvas.drawLine(0,-8,0,10,pen);
            }else if(kind.equals("refresh")){
                // Symmetric two-arrow circular refresh glyph, fully path-based.
                // Both arrowheads are connected to their arcs: no overlapping strokes.
                path.moveTo(-9,-2);
                path.cubicTo(-8,-6,-4,-9,1,-9);
                path.cubicTo(5,-9,8,-6,9,-2);
                path.moveTo(4,-2);path.lineTo(9,-2);path.lineTo(9,-7);
                canvas.drawPath(path,pen);path.reset();
                path.moveTo(9,2);
                path.cubicTo(8,6,4,9,-1,9);
                path.cubicTo(-5,9,-8,6,-9,2);
                path.moveTo(-4,2);path.lineTo(-9,2);path.lineTo(-9,7);
                canvas.drawPath(path,pen);
            }else if(kind.equals("search")){
                // Magnifying glass, sized to match the collection search bar.
                canvas.drawCircle(-2,-2,6.5f,pen);
                canvas.drawLine(3,3,10,10,pen);
            }else if(kind.equals("brain")){
                // Split cerebral hemispheres and internal folds.
                path.moveTo(0,-9);path.cubicTo(-4,-12,-8,-9,-8,-6);
                path.cubicTo(-12,-5,-12,-1,-10,1);
                path.cubicTo(-12,5,-8,9,-5,8);
                path.cubicTo(-4,12,-1,11,0,9);
                path.moveTo(0,-9);path.cubicTo(4,-12,8,-9,8,-6);
                path.cubicTo(12,-5,12,-1,10,1);
                path.cubicTo(12,5,8,9,5,8);
                path.cubicTo(4,12,1,11,0,9);
                path.moveTo(0,-9);path.lineTo(0,9);
                canvas.drawPath(path,pen);
                canvas.drawLine(-7,-2,-4,0,pen);canvas.drawLine(7,-2,4,0,pen);
                canvas.drawLine(-6,5,-3,4,pen);canvas.drawLine(6,5,3,4,pen);
            }else if(kind.equals("notebook")){
                // Notebook and pencil: strict writing-from-memory examination.
                canvas.drawRoundRect(-10,-10,5,10,2,2,pen);
                canvas.drawLine(-6,-10,-6,10,pen);
                canvas.drawLine(-3,-5,2,-5,pen);
                canvas.drawLine(-3,-1,2,-1,pen);
                path.moveTo(1,8);path.lineTo(3,4);
                path.lineTo(9,-5);path.lineTo(11,-7);
                path.lineTo(8,-9);path.lineTo(6,-7);
                path.lineTo(1,2);path.close();canvas.drawPath(path,pen);
            }else if(kind.equals("repeat")){
                // Two consecutive opposing arrows for the global repeat action.
                canvas.drawLine(-8,-4,8,-4,pen);
                canvas.drawLine(8,-4,4,-8,pen);
                canvas.drawLine(8,-4,4,0,pen);
                canvas.drawLine(8,5,-8,5,pen);
                canvas.drawLine(-8,5,-4,1,pen);
                canvas.drawLine(-8,5,-4,9,pen);
            }else{
                canvas.drawRoundRect(-10,-6,6,9,1.8f,1.8f,pen);
                canvas.drawRoundRect(-6,-10,10,5,1.8f,1.8f,pen);
                canvas.drawLine(-1,-2,4,-2,pen);
            }
            canvas.restore();
        }
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
    /** A raised pill, with an indicator that travels between all three destinations. */
    private void addNav(LinearLayout parent){
        final Screen[] destinations={Screen.HOME,Screen.BOOKS,Screen.COLLECTIONS};
        final String[] names={"Главная","Библия","Коллекции"};
        final String[] glyphs={"home","book","cards"};
        final int current=mainTabIndex();
        LinearLayout outer=column();outer.setPadding(dp(16),dp(4),dp(16),dp(12));
        FrameLayout floating=new FrameLayout(this);
        floating.setBackground(outlined(CARD,36,BORDER));
        floating.setElevation(dp(8));floating.setClipToOutline(false);
        final View indicator=new View(this);
        indicator.setTag("sv-nav-indicator");
        indicator.setBackground(shape(0xFFF7DED0,29));
        floating.addView(indicator,new FrameLayout.LayoutParams(dp(80),dp(57),Gravity.TOP|Gravity.LEFT));
        LinearLayout row=row();row.setPadding(dp(7),dp(7),dp(7),dp(7));
        for(int i=0;i<3;i++){
            final int index=i;
            int ink=i==current?0xFFB7553D:MUTED;
            LinearLayout tab=column();tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(5),dp(3),dp(5),dp(3));
            View symbol=i==0 ? new HomeNavigationIcon(this,ink,current==0)
                    : i==1 ? new BookNavigationIcon(this,ink,current==1)
                    : new CollectionsNavigationIcon(this,ink,current==2,collectionsIconCycle);
            LinearLayout.LayoutParams glyph=new LinearLayout.LayoutParams(dp(27),dp(27));
            glyph.gravity=Gravity.CENTER_HORIZONTAL;tab.addView(symbol,glyph);
            TextView name=txt(names[i],12,ink,i==current);
            name.setGravity(Gravity.CENTER);add(tab,name,1);
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
            row.addView(tab,new LinearLayout.LayoutParams(0,dp(57),1));
        }
        floating.addView(row,new FrameLayout.LayoutParams(-1,dp(71)));
        outer.addView(floating,new LinearLayout.LayoutParams(-1,dp(71)));
        parent.addView(outer,new LinearLayout.LayoutParams(-1,-2));
        final int from=lastNavIndex;
        floating.post(()->{
            int slot=(floating.getWidth()-dp(14))/3;
            if(slot<=0)return;
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)indicator.getLayoutParams();
            p.width=slot;p.height=dp(57);p.leftMargin=dp(7);p.topMargin=dp(7);
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
            case COLLECTIONS:case COLLECTION_DETAIL:case ENTRIES:case REORDER_STUDY:
            case CARDS:case CARD_DECK:case CARD_PICK:case CARD_SESSION:case REORDER_CARDS:return 2;
            case BIBLE_PICKER:case PICKER_BOOKS:case PICKER_CHAPTERS:
            case PICKER_VERSES:case PICKER_TEXT:
                return bibleReader.pickerTarget==BibleReaderController.BibleTarget.STUDY_GROUP &&
                    store.collections().unifiedForGroup(bibleReader.pickerTargetId)==null?0:2;
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
        pendingRestoredScreen=null;pendingSearchScroll=false;
        if (index < 0 || index > 2 || tabSwitchInProgress) {
            return;
        }

        if (screen == Screen.TRAIN
                || screen == Screen.CARD_SESSION || screen == Screen.RUN) {
            return;
        }

        final Screen destination =
                index == 0 ? Screen.HOME :
                        index == 1 ? Screen.BOOKS :
                                Screen.COLLECTIONS;

        if (screen == destination) return;
        if(bibleReader.isBibleSearchScreen()&&bibleReader.bibleSearchLoading){
            bibleReader.cancelBibleSearchWork();bibleReader.bibleSearchLoading=false;bibleReader.bibleSearchNeedsRefresh=true;
        }
        if(bibleReader.isPickerScreen(screen)){
            bibleReader.passageSelection.cancel();collectionActions.clearInsertionContext();
            bibleReader.selectedBook=bibleReader.readerBookBeforePicker;bibleReader.selectedChapter=bibleReader.readerChapterBeforePicker;
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
            collectionActions.clearInsertionContext();
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
            navHeight = dp(84);
        }

        studySession.reviewMode = false;
        studySession.groupMode = false;
        studySession.forcedStep = null;

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
        bibleReader.resumeBibleSearchIfNeeded();

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
                        Math.max(dp(1), height - navHeight),
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
        // Fullscreen training and card carousels retain their own swipe gestures.
        if(screen==Screen.TRAIN||screen==Screen.CARD_SESSION||tabSwitchInProgress)
            return super.dispatchTouchEvent(event);
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            swipeStartX=event.getX();swipeStartY=event.getY();swipeIntercepted=false;
        }
        if(action==MotionEvent.ACTION_MOVE&&!swipeIntercepted){
            float dx=event.getX()-swipeStartX,dy=event.getY()-swipeStartY;
            if(Math.abs(dx)>dp(90)&&Math.abs(dx)>Math.abs(dy)*1.6f){
                swipeIntercepted=true;
                MotionEvent cancel=MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);cancel.recycle();return true;
            }
        }
        if(swipeIntercepted){
            if(action==MotionEvent.ACTION_UP){
                float dx=event.getX()-swipeStartX;
                if(Math.abs(dx)>dp(60))goToMainTab(mainTabIndex()+(dx<0?1:-1));
                swipeIntercepted=false;return true;
            }
            if(action==MotionEvent.ACTION_CANCEL){swipeIntercepted=false;return true;}
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private void chooseCardDeck(Pick action){
        List<LocalStore.UnifiedCollection> collections=store.collections().unifiedCollections();
        String[] options=new String[collections.size()+1];
        for(int i=0;i<collections.size();i++)options[i]=collections.get(i).title;
        options[collections.size()]="＋ Новая коллекция";
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Добавить в коллекцию").setItems(options,(dialog,index)->{
                if(index==collections.size())newCardDeck(action);
                else action.apply(collections.get(index).deckId);
            }).setNegativeButton("Отмена",null).create());
    }
    private void newCardDeck(Pick afterCreate){
        newUnifiedCollection(u->{
            if(afterCreate!=null)afterCreate.apply(u.deckId);
            else{collectionActions.openUnifiedId=u.id;screen=Screen.COLLECTION_DETAIL;render();}
        });
    }
    /** One-tap deck start: a painted vector triangle, not an oversized green font glyph. */
    private View deckPlayButton(LocalStore.CardDeck deck){
        View play=new View(this){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){
                super.onDraw(canvas);
                float cx=getWidth()/2f+dp(1),cy=getHeight()/2f;
                float size=dp(10);
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
        play.setOnClickListener(v->{flashcardSession.selectedDeck=deck.id;startCardSession();});
        return play;
    }
    private void renderCardDecks(){
        tabWordmark();
        action(content,"＋  Создать подборку",true,()->newCardDeck(null));
        for(LocalStore.CardDeck deck:store.flashcards().cardDecks()){
            LinearLayout panel=card(content);panel.setPadding(dp(11),dp(11),dp(9),dp(11));
            panel.setBackground(homeSurface());
            LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
            ImageView illustration=landscapeThumbnail(deck.id);
            LinearLayout.LayoutParams imageP=new LinearLayout.LayoutParams(dp(79),dp(96));
            imageP.rightMargin=dp(12);line.addView(illustration,imageP);
            LinearLayout details=column();
            TextView name=displayText(deck.title,18);name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            add(details,name,0);
            add(details,txt(deck.count+" "+(deck.count==1?"карточка":
                ((deck.count%10>=2&&deck.count%10<=4&&(deck.count%100<12||deck.count%100>14))?
                    "карточки":"карточек")),13,MUTED,false),4);
            line.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            panel.addView(line);
            panel.setOnClickListener(v->{flashcardSession.selectedDeck=deck.id;screen=Screen.CARD_DECK;render();});
        }
    }

    /** The card menu and the study-collection menu share exactly the same popup styling. */
    private void showCardDeckActions(LocalStore.CardDeck deck,View anchor){
        String[] actions={"Изучить","Добавить свой текст","Переименовать","Удалить"};
        String[] icons={"study","file","pencil","trash"};
        showActionsMenu(anchor,actions,icons,which->{
            flashcardSession.selectedDeck=deck.id;
            switch(which){
                case 0:studyCardDeck();break;
                case 1:showNewCustomFlashcard(deck.id);break;
                case 2:renameCardDeck(deck);break;
                case 3:confirmDeleteCardDeck(deck);break;
            }
        });
    }

    private interface ActionSelection {void select(int index);}

    /** Native anchored popup; no new libraries or drawable assets are required. */
    private void showActionsMenu(View anchor,String[] labels,String[] iconKinds,ActionSelection selection){
        if(labels.length!=iconKinds.length)throw new IllegalArgumentException("Menu labels/icons mismatch");
        LinearLayout menu=column();
        menu.setPadding(dp(8),dp(8),dp(8),dp(8));
        menu.setBackground(outlined(0xFFFFFCF9,18,0xFFE8D7CA));
        menu.setClipToOutline(true);
        Rect visibleMenuArea=new Rect();anchor.getWindowVisibleDisplayFrame(visibleMenuArea);
        int available=Math.max(dp(160),visibleMenuArea.width()-dp(24));
        // Give labels space on normal phones; on narrow screens allow two rows.
        final int popupWidth=Math.min(dp(316),available);
        ScrollView menuScroll=new ScrollView(this);
        menuScroll.setVerticalScrollBarEnabled(false);
        menuScroll.addView(menu,new android.widget.FrameLayout.LayoutParams(-1,-2));
        PopupWindow popup=new PopupWindow(menuScroll,popupWidth,dp(270),true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(10));
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setClippingEnabled(true);
        for(int i=0;i<labels.length;i++){
            final int index=i;
            boolean destructive="trash".equals(iconKinds[i]);
            if(destructive){
                View rule=new View(this);rule.setBackgroundColor(0xFFF0E3D9);
                LinearLayout.LayoutParams ruleP=new LinearLayout.LayoutParams(-1,dp(1));
                ruleP.setMargins(dp(8),dp(5),dp(8),dp(5));menu.addView(rule,ruleP);
            }
            LinearLayout item=row();item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(9),0,dp(9),0);
            item.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x24C78E6D),
                shape(Color.TRANSPARENT,12),shape(Color.WHITE,12)));
            int ink=destructive?0xFFB9504D:0xFF91674E;
            MenuGlyph glyph=new MenuGlyph(iconKinds[i],ink);
            glyph.setBackground(shape(destructive?0xFFFBE8E5:0xFFF7EDE5,10));
            LinearLayout.LayoutParams glyphP=new LinearLayout.LayoutParams(dp(35),dp(35));
            glyphP.rightMargin=dp(12);item.addView(glyph,glyphP);
            TextView text=txt(labels[i],14,destructive?0xFFB9504D:0xFF4A4038,true);
            text.setGravity(Gravity.CENTER_VERTICAL);
            text.setSingleLine(false);text.setMaxLines(2);
            item.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            item.setClickable(true);item.setFocusable(true);
            item.setContentDescription(labels[i]);
            item.setOnClickListener(v->{popup.dismiss();selection.select(index);});
            item.setMinimumHeight(dp(labels[i].length()>26?70:54));
            menu.addView(item,new LinearLayout.LayoutParams(-1,-2));
        }
        Rect screen=new Rect();anchor.getWindowVisibleDisplayFrame(screen);
        int[] onScreen=new int[2];anchor.getLocationOnScreen(onScreen);
        int spaceAbove=onScreen[1]-screen.top-dp(8);
        int spaceBelow=screen.bottom-(onScreen[1]+anchor.getHeight())-dp(8);
        // Height follows the actual rendered rows, including multi-line labels.
        menu.measure(View.MeasureSpec.makeMeasureSpec(popupWidth,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int needed=menu.getMeasuredHeight();
        boolean above=spaceBelow<needed&&spaceAbove>spaceBelow;
        int free=Math.max(0,above?spaceAbove:spaceBelow);
        int popupHeight=Math.min(needed,free);
        if(popupHeight<dp(48)){popupHeight=Math.min(needed,Math.max(dp(48),screen.height()-dp(32)));}
        popup.setHeight(popupHeight);
        int x=Math.max(screen.left+dp(8),Math.min(screen.right-popupWidth-dp(8),
            onScreen[0]+anchor.getWidth()-popupWidth));
        int y=above?onScreen[1]-popupHeight-dp(6):onScreen[1]+anchor.getHeight()+dp(6);
        y=Math.max(screen.top+dp(4),Math.min(y,screen.bottom-popupHeight-dp(4)));
        popup.showAtLocation(anchor,Gravity.TOP|Gravity.LEFT,x,y);
    }

    /** Small custom vector icons; all strokes are drawn, not text symbols or emoji. */
    private final class MenuGlyph extends View {
        final String kind;final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        MenuGlyph(String kind,int color){
            super(MainActivity.this);this.kind=kind;
            pen.setColor(color);pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeWidth(1.8f);pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setStrokeJoin(Paint.Join.ROUND);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float size=Math.min(getWidth(),getHeight())*.57f;
            canvas.save();
            canvas.translate((getWidth()-size)/2f,(getHeight()-size)/2f);
            canvas.scale(size/24f,size/24f);
            Path path=new Path();
            switch(kind){
                case "book":
                    path.moveTo(12,6);path.cubicTo(8,3.5f,5,3.5f,3,5);
                    path.lineTo(3,18);path.cubicTo(6,16.5f,9,17,12,19);
                    path.cubicTo(15,17,18,16.5f,21,18);path.lineTo(21,5);
                    path.cubicTo(19,3.5f,16,3.5f,12,6);canvas.drawPath(path,pen);
                    canvas.drawLine(12,6,12,19,pen);break;
                case "file":
                    canvas.drawRoundRect(5,3,19,21,2,2,pen);
                    canvas.drawLine(8,8,16,8,pen);
                    canvas.drawLine(12,11,12,18,pen);canvas.drawLine(8.5f,14.5f,15.5f,14.5f,pen);break;
                case "pencil":
                    path.moveTo(4,20);path.lineTo(8.5f,18.9f);path.lineTo(20,7.4f);
                    path.quadTo(21.3f,6.1f,20,4.8f);path.lineTo(19.2f,4);
                    path.quadTo(17.9f,2.7f,16.6f,4);path.lineTo(5,15.6f);
                    path.close();canvas.drawPath(path,pen);
                    canvas.drawLine(14.8f,5.8f,18.2f,9.2f,pen);break;
                case "shuffle":
                    // Crossing tracks with separate arrowheads (shuffle).
                    path.moveTo(3,6);path.lineTo(6,6);
                    path.cubicTo(12,6,12,18,18,18);path.lineTo(21,18);
                    path.moveTo(18,15);path.lineTo(21,18);path.lineTo(18,21);
                    path.moveTo(3,18);path.lineTo(6,18);
                    path.cubicTo(12,18,12,6,18,6);path.lineTo(21,6);
                    path.moveTo(18,3);path.lineTo(21,6);path.lineTo(18,9);
                    canvas.drawPath(path,pen);break;
                case "eraser":
                    // Eraser icon is deliberately distinct from the delete/trash icon.
                    path.moveTo(4,14);path.lineTo(12,5);path.quadTo(13,4,14,5);
                    path.lineTo(20,11);path.quadTo(21,12,20,13);
                    path.lineTo(13,20);path.lineTo(9,20);path.close();
                    canvas.drawPath(path,pen);canvas.drawLine(12,7,18,13,pen);
                    canvas.drawLine(12,20,21,20,pen);break;
                case "trash":
                    canvas.drawRoundRect(6,7,18,21,1,1,pen);
                    canvas.drawLine(4,6,20,6,pen);canvas.drawLine(10,4,14,4,pen);
                    canvas.drawLine(10,10,10,18,pen);canvas.drawLine(14,10,14,18,pen);break;
                default: // graduation cap, for «Изучить»
                    path.moveTo(2,9);path.lineTo(12,4);path.lineTo(22,9);
                    path.lineTo(12,14);path.close();canvas.drawPath(path,pen);
                    path.reset();path.moveTo(7,12);path.lineTo(7,17);
                    path.quadTo(12,21,17,17);path.lineTo(17,12);canvas.drawPath(path,pen);
                    canvas.drawLine(22,9,22,18,pen);break;
            }
            canvas.restore();
        }
    }
    private void renameCardDeck(LocalStore.CardDeck deck){
        EditText name=input("Название подборки",false);name.setText(deck.title);
        LinearLayout wrap=column();wrap.setPadding(dp(20),dp(5),dp(20),dp(0));add(wrap,name,0);
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Переименовать").setView(wrap)
            .setPositiveButton("Сохранить",(d,w)->{
                if(value(name).isEmpty()){toast("Укажи название");return;}
                store.flashcards().renameCardDeck(deck.id,value(name));render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void confirmDeleteCardDeck(LocalStore.CardDeck deck){
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Удалить подборку?")
            .setPositiveButton("Удалить",(d,w)->{
                store.flashcards().deleteCardDeck(deck.id);flashcardSession.clearSavedCardSession(deck.id);
                screen=Screen.CARDS;render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void showNewCustomFlashcard(long deckId){
        EditText reference=input("Название / источник",false);
        EditText verse=input("Текст",true);verse.setMinLines(4);
        LinearLayout fields=column();fields.setPadding(dp(20),dp(7),dp(20),dp(4));
        add(fields,reference,0);add(fields,verse,12);
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Добавить свой текст").setView(fields)
            .setPositiveButton("Добавить",(dialog,which)->{
                if(value(reference).isEmpty()||value(verse).isEmpty()){
                    toast("Укажи название и текст");return;
                }
                store.flashcards().addCustomFlashcard(deckId,value(reference),value(verse));
                if(screen==Screen.CARD_DECK||screen==Screen.CARDS)render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void renderCardDeck(){
        LocalStore.CardDeck deck=store.flashcards().cardDeck(flashcardSession.selectedDeck);
        if(deck==null){screen=Screen.CARDS;render();return;}
        title(deck.title,null);
        List<LocalStore.Flashcard> cards=store.flashcards().flashcards(flashcardSession.selectedDeck);
        if(cards.isEmpty())helper(content,"Здесь пока нет карточек.");
        for(LocalStore.Flashcard card:cards){
            LinearLayout c=card(content);c.setBackground(homeSurface());
            LinearLayout head=row();
            TextView ref=displayText(card.reference,18);
            head.addView(ref,new LinearLayout.LayoutParams(0,-2,1));
            TextView more=txt("⋮",26,MUTED,true);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription("Действия с карточкой "+card.reference);
            head.addView(more,new LinearLayout.LayoutParams(dp(48),dp(52)));
            more.setOnClickListener(v->showCardActions(card,more));
            add(c,head,0);add(c,prose(card.content,16),8);
        }
        addCardDeckFooter(deck, !cards.isEmpty());
    }
    /** Attached to root rather than the scrolling list, so the footer never overlays a verse. */

    private void addCardDeckFooter(
            LocalStore.CardDeck deck,
            boolean hasCards) {

        // Закреплённая панель под списком карточек.
        LinearLayout footer = column();

        footer.setPadding(
                dp(19), dp(10),
                dp(19), dp(10)
        );

        footer.setBackgroundColor(BG);

        // Кнопка запуска карусели карточек.
        TextView start = button(
                "▶  Начать",
                true,
                () -> {
                    flashcardSession.selectedDeck = deck.id;
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

        greenBackground.setCornerRadius(dp(18));

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
                new LinearLayout.LayoutParams(-1, dp(54))
        );

        // Вторая кнопка — добавление из Библии.
        TextView addBible = button(
                "＋  Добавить из Библии",
                false,
                () -> bibleReader.openBiblePicker(
                        BibleReaderController.BibleTarget.FLASHCARD_DECK,
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
                new LinearLayout.LayoutParams(-1, dp(52));

        addParams.topMargin = dp(9);

        footer.addView(addBible, addParams);

        // Добавляем панель вне ScrollView.
        root.addView(
                footer,
                new LinearLayout.LayoutParams(-1, -2)
        );
    }

    private void addBibleFooter(BibleReaderController.BibleTarget target,long groupId,Screen back){
        LinearLayout footer=column();footer.setPadding(dp(19),dp(8),dp(19),dp(10));
        footer.setBackgroundColor(BG);
        TextView add=button("＋  Добавить из Библии",true,()->bibleReader.openBiblePicker(target,groupId,back));
        add.setContentDescription("Добавить стихи из Библии в текущую коллекцию");
        footer.addView(add,new LinearLayout.LayoutParams(-1,dp(54)));
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
    }

    private void addStudyCollectionFooter(LocalStore.Group group) {

        // Панель находится вне прокручиваемого списка.
        LinearLayout footer = column();
        footer.setPadding(
                dp(19), dp(10),
                dp(19), dp(10)
        );
        footer.setBackgroundColor(BG);

        // Основная кнопка — начало изучения коллекции.
        TextView start = button(
                "▶  Начать изучение",
                true,
                () -> collectionActions.startStudyCollection(group.id)
        );

        GradientDrawable greenBackground =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{
                                0xFF789D72,
                                0xFF4E7654
                        }
                );

        greenBackground.setCornerRadius(dp(18));
        start.setBackground(greenBackground);
        start.setTextColor(Color.WHITE);
        start.setTextSize(16);
        start.setContentDescription(
                "Начать изучение коллекции " + group.title
        );

        boolean hasEntries = !store.study().entries(group.id).isEmpty();

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
                new LinearLayout.LayoutParams(-1, dp(54))
        );

        // Вторичная кнопка — добавление новых стихов.
        TextView addBible = button(
                "＋  Добавить из Библии",
                false,
                () -> bibleReader.openBiblePicker(
                        BibleReaderController.BibleTarget.STUDY_GROUP,
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
                new LinearLayout.LayoutParams(-1, dp(52));

        addParams.topMargin = dp(9);

        footer.addView(addBible, addParams);

        // Закрепляем обе кнопки внизу открытой коллекции.
        root.addView(
                footer,
                new LinearLayout.LayoutParams(-1, -2)
        );
    }

    /** Unified Bible entry: catalogue -> chapter grid -> verse grid -> highlighted chapter text. */

    /** Range settings reuse the same catalog and numeric grids as quick insertion. */

    private void renderUnifiedBiblePicker(){
        pickerSelectionPanel=null;
        if(bibleNotReady())return;
        title("Задать диапазон",bibleReader.passageSelection.book>0?Bible.NAMES[bibleReader.passageSelection.book-1]:null);
        TextView start=button("Задать начало отрывка\n"+bibleReader.pickerBoundaryReference(false),
            bibleReader.passageSelection.startChapter>0,()->bibleReader.beginPickerBoundary(false));
        start.setMinHeight(dp(82));add(content,start,10);
        TextView end=button("Задать конец отрывка\n"+bibleReader.pickerBoundaryReference(true),
            bibleReader.passageSelection.endChapter>0,()->bibleReader.beginPickerBoundary(true));
        end.setMinHeight(dp(82));add(content,end,10);
        pickerSelectionLabel=prose("",18);add(content,pickerSelectionLabel,18);
        pickerSelectionPanel=column();pickerSelectionPanel.setPadding(dp(19),dp(8),dp(19),dp(8));
        pickerSelectionPanel.setBackground(outlined(CARD,16,BORDER));
        pickerSaveButton=button("Добавить отрывок",true,this::savePickerRange);
        add(pickerSelectionPanel,pickerSaveButton,0);updatePickerRangeSummary();
    }
    private void updatePickerRangeSummary(){
        boolean valid=bibleReader.passageSelection.valid();
        int count=0;
        if(valid){
            int[] b=bibleReader.passageSelection.bounds();
            try{count=bibleReader.bible.collectionPassage(bibleReader.passageSelection.book,b[0],b[1],b[2],b[3]).size();}
            catch(IllegalArgumentException ignored){valid=false;}
        }
        if(valid){
            int[] b=bibleReader.passageSelection.bounds();
            pickerSelectionLabel.setText(Bible.reference(bibleReader.passageSelection.book,b[0],b[1],b[2],b[3])+" · "+PassageSelection.verses(count));
        }else pickerSelectionLabel.setText(bibleReader.passageSelection.complete()?
            "Конец раньше начала. Измените начало или конец отрывка.":"Выберите начало и конец отрывка.");
        pickerSelectionLabel.setTextColor(bibleReader.passageSelection.complete()&&!valid?RED:WHITE);
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
    private void scrollPickerGridTo(View row,Screen destination){
        ScrollView scroller=currentScroller;
        afterPickerLayout(row,()->{if(screen==destination&&currentScroller==scroller)
            scroller.scrollTo(0,Math.max(0,row.getTop()-dp(8)));});
    }
    /** Four-column number grid, identical to the chapters grid. */
    private void renderPickerVerseGrid(){renderVerseGrid(false);}
    private void renderVerseGrid(boolean reader){
        if(bibleNotReady())return;
        List<Bible.Verse> verses=bibleReader.bible.chapter(bibleReader.selectedBook,bibleReader.selectedChapter);
        title(Bible.NAMES[bibleReader.selectedBook-1]+" "+bibleReader.selectedChapter,"Стихи");
        if(verses.isEmpty()){helper(content,"В главе нет стихов");return;}
        for(int base=1;base<=verses.size();base+=4){
            LinearLayout r=row();
            for(int k=base;k<base+4;k++){
                if(k>verses.size()){
                    r.addView(new View(this),new LinearLayout.LayoutParams(0,dp(50),1));
                    continue;
                }
                final int verse=k;
                int boundaryChapter=bibleReader.pickerChooseEnd?bibleReader.passageSelection.endChapter:bibleReader.passageSelection.startChapter;
                int boundaryVerse=bibleReader.pickerChooseEnd?bibleReader.passageSelection.endVerse:bibleReader.passageSelection.startVerse;
                boolean selected=reader?verse==bibleReader.readerVerseNumber:bibleReader.passageSelection.active?
                    bibleReader.selectedChapter==boundaryChapter&&verse==boundaryVerse:verse==bibleReader.pickerVerseNumber;
                TextView b=button(String.valueOf(verse),selected,()->{
                    bibleReader.selectVerse(reader,verse);
                });
                b.setPadding(dp(4),dp(4),dp(4),dp(4));
                b.setContentDescription((!reader&&bibleReader.passageSelection.active?"Выбрать стих ":"Открыть текст стиха ")+verse+
                    (selected?", выбран":""));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);
                p.setMargins(dp(3),dp(4),dp(3),dp(4));r.addView(b,p);
            }
            add(content,r,2);
            if(!reader&&bibleReader.passageSelection.active){
                int c=bibleReader.pickerChooseEnd?bibleReader.passageSelection.endChapter:bibleReader.passageSelection.startChapter;
                int v=bibleReader.pickerChooseEnd?bibleReader.passageSelection.endVerse:bibleReader.passageSelection.startVerse;
                if(bibleReader.selectedChapter==c&&v>=base&&v<base+4)scrollPickerGridTo(r,Screen.PICKER_VERSES);
            }
        }
    }
    /** Shared Bible data and catalog; only this insertion screen owns selection controls. */
    private void renderPickerVerseText(){
        pickerSelectionPanel=null;
        if(bibleNotReady())return;
        bibleReader.passageSelection.openBook(bibleReader.selectedBook);bibleReader.passageSelection.chapter=bibleReader.selectedChapter;
        pickerQuickButtons.clear();
        List<Bible.Verse> verses=bibleReader.bible.chapter(bibleReader.selectedBook,bibleReader.selectedChapter);
        title(Bible.NAMES[bibleReader.selectedBook-1]+" "+bibleReader.selectedChapter,null);
        LinearLayout navigation=row();
        navigation.addView(button("Главы · "+bibleReader.selectedChapter,false,()->{
            savedScrollY.remove(Screen.PICKER_CHAPTERS);screen=Screen.PICKER_CHAPTERS;render();
        }),new LinearLayout.LayoutParams(0,-2,1));
        navigation.addView(button("Стихи",false,()->{
            savedScrollY.remove(Screen.PICKER_VERSES);screen=Screen.PICKER_VERSES;render();
        }),new LinearLayout.LayoutParams(0,-2,1));
        add(content,navigation,4);
        action(content,"Задать диапазон",false,bibleReader::openPickerRange);
        final ScrollView scroller=currentScroller;
        final int targetVerse=bibleReader.focusVerse;bibleReader.focusVerse=-1;
        final Set<String> present=new HashSet<>(),included=new HashSet<>();
        if(bibleReader.pickerTarget==BibleReaderController.BibleTarget.STUDY_GROUP){
            for(LocalStore.Entry entry:store.study().entries(bibleReader.pickerTargetId)){
                present.add(entry.reference);
                if(entry.lines().size()>1&&entry.reference.startsWith(Bible.NAMES[bibleReader.selectedBook-1]+" "))
                    for(Bible.Verse part:entry.lines())included.add(part.ref);
            }
        }
        for(Bible.Verse verse:verses){
            final int number=Integer.parseInt(verse.ref.substring(verse.ref.indexOf(':')+1));
            final int book=bibleReader.selectedBook,chapter=bibleReader.selectedChapter;
            final String reference=Bible.reference(book,chapter,number,chapter,number);
            LinearLayout line=card(content);
            LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);
            TextView marker=txt(verse.ref,14,bookInk(book),true);
            head.addView(marker,new LinearLayout.LayoutParams(0,-2,1));
            if(bibleReader.pickerTarget==BibleReaderController.BibleTarget.STUDY_GROUP){
                boolean standalone=present.contains(reference),covered=!standalone&&included.contains(verse.ref);
                TextView toggle=button(standalone?"−":covered?"✓":"+",false,()->{});
                toggle.setContentDescription((standalone?"Убрать ":covered?"Уже в отрывке: ":"Добавить ")+reference);
                pickerQuickButtons.add(toggle);head.addView(toggle,new LinearLayout.LayoutParams(dp(48),dp(48)));
                toggle.setOnClickListener(v->{
                    if(included.contains(verse.ref)&&!present.contains(reference))return;
                    if(collectionActions.togglePickerCollectionVerse(book,chapter,number)){
                        invalidateRemovedSessions();
                        boolean added=collectionActions.pickerCollectionContainsVerse(book,chapter,number);
                        if(added)present.add(reference);else present.remove(reference);
                        toggle.setText(added?"−":included.contains(verse.ref)?"✓":"+");
                        toggle.setContentDescription((added?"Убрать ":included.contains(verse.ref)?"Уже в отрывке: ":"Добавить ")+reference);
                    }
                });
            }
            add(line,head,0);add(line,prose(verse.text,19),8);
            if(targetVerse==number){
                afterPickerLayout(line,()->{
                    if(currentScroller!=scroller)return;
                    scroller.scrollTo(0,Math.max(0,line.getTop()-dp(16)));
                    line.setBackground(outlined(0xFFFFE8B5,20,BORDER));
                    line.postDelayed(()->{if(currentScroller==scroller)line.setBackground(outlined(CARD,20,BORDER));},1100);
                });
            }
        }
        if(targetVerse<1&&bibleReader.pickerRestoredScroll>=0){
            int y=bibleReader.pickerRestoredScroll;bibleReader.pickerRestoredScroll=-1;
            afterPickerLayout(content,()->{if(currentScroller==scroller)scroller.scrollTo(0,y);});
        }
    }
    private void savePickerRange(){
        if(!bibleReader.passageSelection.active||!bibleReader.passageSelection.valid()||!pickerSaveButton.isEnabled())return;
        pickerSaveButton.setEnabled(false);
        int[] b=bibleReader.passageSelection.bounds();
        if(collectionActions.savePickedPassageFor(bibleReader.passageSelection.book,b[0],b[1],b[2],b[3])){
            bibleReader.passageSelection.cancel();collectionActions.clearInsertionContext();
            bibleReader.selectedBook=bibleReader.readerBookBeforePicker;bibleReader.selectedChapter=bibleReader.readerChapterBeforePicker;
            screen=bibleReader.pickerBackScreen;render();
        }else updatePickerRangeSummary();
    }

    /** Toggle one standalone verse in the collection that opened the picker.
     * Detaching preserves progress; it never deletes the underlying study row. */

    /** Fast Bible-search membership does not alter range-picker state or the scroll. */

    /** Study each card as its own entry; a range retains its internal contiguous links. */
    private void studyCardDeck(){
        FlashcardSessionController.StudyStart result=flashcardSession.studyCardDeck(bibleReader.bible);
        if(result.message!=null){if(result.failed)collectionActions.executingCollectionQueue=false;toast(result.message);return;}
        if(result.firstEntry>0)studySession.openTraining(result.firstEntry);
    }
    private void advanceCardStudy(){
        long next=flashcardSession.advanceCardStudy();
        if(next<0){onBackPressed();return;}
        if(next>0){studySession.openTraining(next);return;}
        screen=Screen.CARD_DECK;render();
    }

    private void renderCardPicker(){
        title("Добавить карточку",null);
        if(bibleNotReady())return;
        action(content,Bible.NAMES[bibleReader.selectedBook-1]+" ▾",false,()->{
            styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                .setTitle("Книга Библии").setItems(Bible.NAMES,(d,which)->{
                    bibleReader.selectedBook=which+1;bibleReader.selectedChapter=bibleReader.lastReadChapter(bibleReader.selectedBook);
                    bibleReader.clearPassageSelection();render();
                }).setNegativeButton("Отмена",null).create());
        });
        renderInlineRangeBuilder(true);
    }
    private void addCardFromSelection(long deckId){
        if(bibleReader.rangeStartChapter<1||bibleReader.rangeStartVerse<1){toast("Укажи границы отрывка");return;}
        addBibleCard(deckId,bibleReader.selectedBook,bibleReader.rangeStartChapter,bibleReader.rangeStartVerse,bibleReader.rangeEndChapter,bibleReader.rangeEndVerse);
    }
    private void addBibleCard(long deckId,int book,int sc,int sv,int ec,int ev){
        if(!bibleReader.bible.isReady()){toast("Библия ещё загружается");return;}
        try{
            List<Bible.Verse> lines=bibleReader.bible.passage(book,sc,sv,ec,ev);
            String text=PassageText.join(lines);
            boolean added=store.flashcards().addFlashcard(deckId,book,sc,sv,ec,ev,Bible.reference(book,sc,sv,ec,ev),text);
            toast(added?"Карточка добавлена":"Карточка уже есть в подборке");
            if(screen==Screen.CARD_PICK){screen=Screen.CARD_DECK;render();}
        }catch(Exception ex){toast(ex.getMessage()==null?"Не удалось добавить карточку":ex.getMessage());}
    }

    private void startCardSession(){
        if(!flashcardSession.startCardSession()){toast("Подборка пуста");return;}
        screen=Screen.CARD_SESSION;render();
    }

    private void nextFlashcard(){
        if(!flashcardSession.nextFlashcard()){
            screen=flashcardSession.cardFromUnified?Screen.HOME:Screen.CARD_DECK;render();return;
        }
        renderCardSession();
    }
    private final class SwipeCardLayout extends FrameLayout {
        private float x0,y0;private boolean handling=false;
        SwipeCardLayout(){super(MainActivity.this);}
        @Override public boolean onInterceptTouchEvent(MotionEvent ev){
            switch(ev.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    x0=ev.getX();y0=ev.getY();handling=false;return false;
                case MotionEvent.ACTION_MOVE:
                    float dx=ev.getX()-x0,dy=ev.getY()-y0;
                    if(Math.abs(dx)>dp(24)&&Math.abs(dx)>Math.abs(dy)*1.15f){handling=true;return true;}
                    return false;
                default:return false;
            }
        }
        @Override public boolean onTouchEvent(MotionEvent ev){
            if(ev.getActionMasked()==MotionEvent.ACTION_UP){
                float delta=ev.getX()-x0;
                if(Math.abs(delta)>dp(55)){
                    animate().translationX(Math.signum(delta)*Math.max(dp(360),getWidth()))
                        .alpha(0f).setDuration(175).withEndAction(()->nextFlashcard()).start();
                } else animate().translationX(0f).alpha(1f).setDuration(120).start();
                handling=false;return true;
            }
            if(ev.getActionMasked()==MotionEvent.ACTION_MOVE&&handling){
                setTranslationX((ev.getX()-x0)*0.65f);return true;
            }
            if(ev.getActionMasked()==MotionEvent.ACTION_CANCEL){
                animate().translationX(0f).alpha(1f).setDuration(120).start();
                handling=false;return true;
            }
            return true;
        }
    }
    /** Drawn eyelids morph continuously, not a swap between two eye icons. */
    private final class EyeButton extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float openness=0f;
        private ValueAnimator eyelidsAnimator;
        EyeButton(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setOpen(boolean open,boolean animate){
            if(eyelidsAnimator!=null)eyelidsAnimator.cancel();
            float target=open?1f:0f;
            if(!animate||!ValueAnimator.areAnimatorsEnabled()){
                openness=target;invalidate();return;
            }
            eyelidsAnimator=ValueAnimator.ofFloat(openness,target);
            eyelidsAnimator.setDuration(230L);
            eyelidsAnimator.setInterpolator(new DecelerateInterpolator());
            eyelidsAnimator.addUpdateListener(a->{
                openness=(Float)a.getAnimatedValue();invalidate();
            });
            eyelidsAnimator.start();
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float cx=getWidth()/2f,cy=getHeight()/2f,r=dp(15);
            float open=Math.max(0f,Math.min(1f,openness));
            paint.setColor(0xFF9B5E41);paint.setStrokeWidth(dp(2.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            // At t=0 the lids meet in one curved closed-eye line;
            // at t=1 they frame a fully open, almond-shaped eye.
            float closedArc=dp(3);
            float arc=closedArc+(r*.82f-closedArc)*open;
            Path upper=new Path();upper.moveTo(cx-r,cy);
            upper.cubicTo(cx-r*.55f,cy-arc,cx+r*.55f,cy-arc,cx+r,cy);
            Path lower=new Path();lower.moveTo(cx-r,cy);
            lower.cubicTo(cx-r*.55f,cy-closedArc+(arc+closedArc)*open,
                cx+r*.55f,cy-closedArc+(arc+closedArc)*open,cx+r,cy);
            c.drawPath(upper,paint);
            if(open>0.003f)c.drawPath(lower,paint);
            if(open>0.003f){
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(Math.round(255f*open));
                c.drawCircle(cx,cy,dp(4)*open,paint);
                paint.setAlpha(255);
            }
        }
        @Override protected void onDetachedFromWindow(){
            if(eyelidsAnimator!=null)eyelidsAnimator.cancel();
            super.onDetachedFromWindow();
        }
    }
    private void renderCardSession(){
        List<LocalStore.Flashcard> items=store.flashcards().flashcards(flashcardSession.selectedDeck);
        LocalStore.Flashcard current=null;
        if(!flashcardSession.cardSequence.isEmpty())for(LocalStore.Flashcard f:items)
            if(f.id==flashcardSession.cardSequence.get(Math.min(flashcardSession.cardIndex,flashcardSession.cardSequence.size()-1))){current=f;break;}
        if(current==null){screen=flashcardSession.cardFromUnified?Screen.HOME:Screen.CARD_DECK;render();return;}
        root=column();root.setBackground(pageBackground());
        LinearLayout header=column();header.setPadding(dp(16),dp(16),dp(16),dp(7));
        LocalStore.CardDeck deck=store.flashcards().cardDeck(flashcardSession.selectedDeck);
        TextView deckHeading=displayText(deck==null?"Карточки":deck.title,22);
        deckHeading.setGravity(Gravity.CENTER);
        header.addView(deckHeading,new LinearLayout.LayoutParams(-1,dp(45)));
        root.addView(header,new LinearLayout.LayoutParams(-1,-2));
        SwipeCardLayout swipe=new SwipeCardLayout();
        swipe.setBackground(outlined(CARD,26,BORDER));
        swipe.setClipToOutline(true);
        ImageView scenicCard=new ImageView(this);
        scenicCard.setImageResource(landscapeFor(flashcardSession.selectedDeck));
        scenicCard.setScaleType(ImageView.ScaleType.CENTER_CROP);
        scenicCard.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams sceneryP=new FrameLayout.LayoutParams(-1,dp(158),Gravity.BOTTOM);
        swipe.addView(scenicCard,sceneryP);
        View readableOverlay=new View(this);
        readableOverlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFFFFFCF8,0xFFFFFAF5,0xFAFFF6EE,0xDDFFF3E6,0x44FFF3E6}));
        swipe.addView(readableOverlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams swipeParams=new LinearLayout.LayoutParams(-1,0,1);
        swipeParams.setMargins(dp(17),dp(16),dp(17),dp(24));root.addView(swipe,swipeParams);
        ScrollView scrolling=new ScrollView(this);scrolling.setFillViewport(true);
        swipe.addView(scrolling,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout vertical=column();vertical.setGravity(Gravity.CENTER);
        vertical.setPadding(dp(22),dp(22),dp(22),dp(22));scrolling.addView(vertical);
        TextView initial=txt(flashcardSession.cardShowReference?current.reference:current.content,
            flashcardSession.cardShowReference?27:20,0xFF3C422E,flashcardSession.cardShowReference);
        initial.setTypeface(Typeface.create("serif",flashcardSession.cardShowReference?Typeface.BOLD:Typeface.NORMAL));
        initial.setGravity(Gravity.CENTER);add(vertical,initial,0);
        TextView answer=txt(flashcardSession.cardShowReference?current.content:current.reference,
            flashcardSession.cardShowReference?20:25,0xFF694F3F,!flashcardSession.cardShowReference);
        answer.setTypeface(Typeface.create("serif",flashcardSession.cardShowReference?Typeface.NORMAL:Typeface.BOLD));
        // Reserve an answer area so opening the eye never shifts the original citation/text.
        answer.setGravity(Gravity.CENTER);
        answer.setAlpha(flashcardSession.cardRevealed?1f:0f);
        answer.setImportantForAccessibility(flashcardSession.cardRevealed?
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        ScrollView answerScroll=new ScrollView(this);
        answerScroll.setFillViewport(true);
        answerScroll.setVerticalScrollBarEnabled(false);
        answerScroll.addView(answer,new ScrollView.LayoutParams(-1,-2));
        int answerHeight=Math.min(dp(230),Math.max(dp(100),
            getResources().getDisplayMetrics().heightPixels*32/100));
        LinearLayout.LayoutParams answerArea=new LinearLayout.LayoutParams(-1,answerHeight);
        answerArea.topMargin=dp(10);vertical.addView(answerScroll,answerArea);
        EyeButton eye=new EyeButton();
        eye.setOpen(flashcardSession.cardRevealed,false);
        eye.setContentDescription(flashcardSession.cardRevealed?"Скрыть ответ":"Показать ответ");
        eye.setBackground(outlined(0xFFF0F2E7,14,0xFFC9D5C4));
        LinearLayout.LayoutParams eyeParams=new LinearLayout.LayoutParams(dp(60),dp(60));
        eyeParams.gravity=Gravity.CENTER_HORIZONTAL;eyeParams.topMargin=dp(24);
        vertical.addView(eye,eyeParams);
        eye.setOnClickListener(v->{
            flashcardSession.cardRevealed=!flashcardSession.cardRevealed;
            eye.setOpen(flashcardSession.cardRevealed,true);
            answer.animate().cancel();
            answer.animate().alpha(flashcardSession.cardRevealed?1f:0f)
                .setDuration(ValueAnimator.areAnimatorsEnabled()?250L:0L).start();
            answer.setImportantForAccessibility(flashcardSession.cardRevealed?
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            eye.setContentDescription(flashcardSession.cardRevealed?"Скрыть ответ":"Показать ответ");
        });
        setContentView(root);
    }

    private void renderHome() {
        tabWordmark();
        int[] vr=HOME_REFS[Math.floorMod(homeIndex,HOME_REFS.length)];
        List<Bible.Verse> homeLines=bibleReader.bible.isReady()?bibleReader.bible.chapter(vr[0],vr[1]):Collections.emptyList();
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
            p.setColor(MUTED);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.5f));
            p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            setRotation(open?180f:0f);
            setContentDescription(open?"Свернуть":"Развернуть");
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float x=getWidth()/2f,y=getHeight()/2f;
            Path path=new Path();path.moveTo(x-dp(7),y-dp(3));
            path.lineTo(x,y+dp(4));path.lineTo(x+dp(7),y-dp(3));
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
        tile.setBackground(outlined(CARD,22,BORDER));tile.setClipToOutline(true);
        tile.setElevation(dp(2));
        ImageView scene=new ImageView(this);
        scene.setImageResource(landscapeFor(group.id));
        scene.setScaleType(ImageView.ScaleType.CENTER_CROP);
        scene.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(scene,new FrameLayout.LayoutParams(-1,-1));
        View light=new View(this);
        light.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFFFFF2E6,0xE8FFF0E2,0x40FFF0E5,0x08FFF0E5}));
        tile.addView(light,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout caption=column();caption.setPadding(dp(12),dp(11),dp(12),dp(4));
        TextView name=displayText(group.title,17);name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        add(caption,name,0);
        add(caption,txt(group.count+" "+collectionCountNoun(group.count),12,MUTED,false),5);
        tile.addView(caption,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        tile.setContentDescription(group.title+", "+group.count+" "+collectionCountNoun(group.count));
        tile.setClickable(true);tile.setFocusable(true);
        return tile;
    }
    private void renderHomeCollections(){
        LinearLayout section=column();add(content,section,13);
        LinearLayout controls=row();add(section,controls,0);
        LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(12),dp(5),dp(9),dp(5));
        heading.setBackground(homeSurface());
        heading.setElevation(dp(1));
        NavigationGlyph folder=new NavigationGlyph("cards",0xFFAC684C);
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(dp(30),dp(30));
        iconP.rightMargin=dp(10);heading.addView(folder,iconP);
        TextView label=displayText("Мои коллекции",20);
        label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        ChevronView arrow=new ChevronView(homeCollectionsExpanded);
        heading.addView(arrow,new LinearLayout.LayoutParams(dp(30),dp(38)));
        LinearLayout.LayoutParams headingP=new LinearLayout.LayoutParams(0,dp(55),1);
        headingP.rightMargin=dp(9);controls.addView(heading,headingP);
        TextView plus=txt("+",28,0xFFB7553D,false);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(outlined(0xFFFFE7D9,18,BORDER));
        plus.setClickable(true);plus.setFocusable(true);
        plus.setContentDescription("Создать коллекцию");
        plus.setOnClickListener(v->createHomeCollection());
        controls.addView(plus,new LinearLayout.LayoutParams(dp(55),dp(55)));
        FrameLayout clip=new FrameLayout(this);clip.setClipChildren(true);
        LinearLayout collectionList=column();collectionList.setPadding(0,0,0,dp(4));
        // One readable compact list, part of the main page's single scroll surface.
        for(LocalStore.Group group:store.collections().groups()){
            LinearLayout tile=row();tile.setPadding(dp(9),dp(8),dp(8),dp(8));
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setMinimumHeight(dp(72));
            tile.setBackground(homeSurface());
            ImageView thumb=landscapeThumbnail(group.id);
            LinearLayout.LayoutParams thumbP=new LinearLayout.LayoutParams(dp(48),dp(48));
            thumbP.rightMargin=dp(12);tile.addView(thumb,thumbP);
            LinearLayout caption=column();
            TextView collectionName=displayText(group.title,16);
            collectionName.setMaxLines(2);
            collectionName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            add(caption,collectionName,0);
            add(caption,txt(group.count+" "+collectionCountNoun(group.count),12,MUTED,false),3);
            tile.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
            tile.setClickable(true);tile.setFocusable(true);
            tile.setOnClickListener(v->{collectionActions.selectedGroup=group.id;screen=Screen.ENTRIES;render();});
            add(collectionList,tile,7);
        }
        clip.addView(collectionList,new FrameLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,
            homeCollectionsExpanded?-2:0);cp.topMargin=dp(2);section.addView(clip,cp);
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
        if(availableWidth<=0)availableWidth=Math.max(dp(200),
            getResources().getDisplayMetrics().widthPixels-dp(38));
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
        LinearLayout col=column();col.addView(txt(number,25,WHITE,true));col.addView(txt(desc,11,0xFFE1FFFA,false));
        row.addView(col,new LinearLayout.LayoutParams(0,-2,1));
    }
    private boolean bibleNotReady() {
        if(bibleReader.bible.isReady())return false;
        if(!bibleReader.bibleError.isEmpty()) helper(content,"Ошибка встроенного текста: "+bibleReader.bibleError);
        else helper(content,"Загружаем текст Библии из памяти устройства…");
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
        if(bibleReader.bibleSearchActive){renderSearchResults();return;}
        if(bibleNotReady())return;
        BookColorLogic.Group previous=null;
        LinearLayout groupedRows=null;
        for(int b=1;b<=66;b++){
            if(b==1)testamentHeading("ВЕТХИЙ ЗАВЕТ",false);
            if(b==40)testamentHeading("НОВЫЙ ЗАВЕТ",true);
            BookColorLogic.Group group=BookColorLogic.group(b);
            if(group!=previous){
                // The ten large serif category headers and spacing are from v0.16.0.
                TextView groupTitle = displayText(bookGroupName(group), 21);
                groupTitle.setGravity(Gravity.CENTER);
                add(content, groupTitle, 17);
                LinearLayout panel=column();
                panel.setBackground(outlined(CARD,18,BORDER));
                panel.setPadding(dp(11),dp(7),dp(11),dp(7));
                add(content,panel,4);
                groupedRows=column();panel.addView(groupedRows,new LinearLayout.LayoutParams(-1,-2));
                previous=group;
            }
            final int book=b;
            LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
            TextView abbreviated=txt(BookColorLogic.shortName(b),14,bookInk(b),true);
            line.addView(abbreviated,new LinearLayout.LayoutParams(dp(68),-2));
            TextView full=txt(Bible.NAMES[b-1],16,WHITE,false);
            full.setSingleLine(true);full.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line.addView(full,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=txt("›",22,GREEN,false);arrow.setGravity(Gravity.CENTER);
            line.addView(arrow,new LinearLayout.LayoutParams(dp(20),-2));
            line.setMinimumHeight(dp(48));
            line.setContentDescription(Bible.NAMES[b-1]);
            line.setOnClickListener(v->{
                bibleReader.selectedBook=book;bibleReader.selectedChapter=screen==Screen.PICKER_BOOKS&&bibleReader.passageSelection.book==book?
                    bibleReader.passageSelection.chapter:bibleReader.lastReadChapter(book);
                if(screen==Screen.PICKER_BOOKS){
                    boolean rangeActive=bibleReader.passageSelection.active;
                    bibleReader.passageSelection.openBook(book);bibleReader.passageSelection.active=rangeActive;
                    bibleReader.pickerHasAnchor=false;
                    screen=Screen.PICKER_CHAPTERS;
                }else {bibleReader.selectingPassage=false;bibleReader.clearPassageSelection();bibleReader.readerVerseNumber=1;
                    savedScrollY.remove(Screen.VERSES);screen=Screen.CHAPTERS;}
                render();
            });
            groupedRows.addView(line,new LinearLayout.LayoutParams(-1,-2));
            if(b<66&&BookColorLogic.group(b+1)==group){
                View separator=new View(this);separator.setBackgroundColor(0xFFEADACE);
                groupedRows.addView(separator,new LinearLayout.LayoutParams(-1,dp(1)));
            }
        }
    }

    private void renderChapters() {
        if(bibleNotReady())return;
        int n=bibleReader.bible.chapterCount(bibleReader.selectedBook);
        title(Bible.NAMES[bibleReader.selectedBook-1],screen==Screen.CHAPTERS?null:
            n+" "+(n%10==1 && n%100!=11?"глава":
            n%10>=2 && n%10<=4 && (n%100<12||n%100>14)?"главы":"глав"));
        int selected=screen==Screen.PICKER_CHAPTERS?bibleReader.selectedChapter:bibleReader.lastReadChapter(bibleReader.selectedBook);
        for(int base=1;base<=n;base+=4){
            LinearLayout r=row();
            for(int k=base;k<base+4;k++){
                if(k>n){r.addView(new View(this),new LinearLayout.LayoutParams(0,dp(50),1));continue;}
                final int c=k;
                TextView b=button(String.valueOf(k),c==selected,()->{
                    if(screen==Screen.PICKER_CHAPTERS&&!bibleReader.passageSelection.active&&bibleReader.selectedChapter!=c)bibleReader.pickerVerseNumber=1;
                    bibleReader.selectedChapter=c;
                    if(screen==Screen.PICKER_CHAPTERS)screen=Screen.PICKER_VERSES;
                    else{bibleReader.openReaderVerseGrid(c);return;}
                    render();
                });
                if(screen==Screen.PICKER_CHAPTERS){
                    b.setPadding(dp(4),dp(4),dp(4),dp(4));
                    b.setContentDescription("Глава "+c+(c==selected?", выбрана":""));
                }else if(c==selected)b.setContentDescription("Глава "+c+", последняя прочитанная");
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);
                p.setMargins(dp(3),dp(4),dp(3),dp(4));r.addView(b,p);
            }
            add(content,r,2);
            if(screen==Screen.PICKER_CHAPTERS&&selected>=base&&selected<base+4)
                scrollPickerGridTo(r,Screen.PICKER_CHAPTERS);
        }
    }

    private void selectPassageVerse(int c,int v){
        if(bibleReader.rangeStartChapter<0 || bibleReader.rangeEndChapter>=0){
            bibleReader.rangeStartChapter=c;bibleReader.rangeStartVerse=v;bibleReader.rangeEndChapter=-1;bibleReader.rangeEndVerse=-1;
        } else if(c<bibleReader.rangeStartChapter || (c==bibleReader.rangeStartChapter && v<bibleReader.rangeStartVerse)) {
            bibleReader.rangeEndChapter=bibleReader.rangeStartChapter;bibleReader.rangeEndVerse=bibleReader.rangeStartVerse;
            bibleReader.rangeStartChapter=c;bibleReader.rangeStartVerse=v;
        } else {bibleReader.rangeEndChapter=c;bibleReader.rangeEndVerse=v;}
        int scroll=currentScroller==null?0:currentScroller.getScrollY();
        render();
        if(currentScroller!=null)currentScroller.post(()->currentScroller.scrollTo(0,scroll));
    }

    private void renderChapter() {
        title(Bible.NAMES[bibleReader.selectedBook-1]+" "+bibleReader.selectedChapter,null);
        if(bibleNotReady())return;
        List<Bible.Verse> verses=bibleReader.bible.chapter(bibleReader.selectedBook,bibleReader.selectedChapter);
        if(verses.isEmpty()){helper(content,"Нет текста главы.");return;}
        bibleReader.rememberChapter(bibleReader.selectedBook,bibleReader.selectedChapter);
        action(content,"Глава "+bibleReader.selectedChapter+"  ▾",false,this::showChapterJump);
        Map<Integer,LocalStore.VerseStatus> statuses=store.progress().statusesForChapter(
            Bible.NAMES[bibleReader.selectedBook-1],bibleReader.selectedChapter);
        for(Bible.Verse verse:verses){
            final int verseNumber;
            try{verseNumber=Integer.parseInt(verse.ref.substring(verse.ref.indexOf(':')+1));}
            catch(Exception ex){continue;}
            boolean highlighted=verseNumber==bibleReader.focusVerse;
            LocalStore.VerseStatus stat=statuses.get(verseNumber);
            LinearLayout c=card(content);
            if(highlighted){
                c.setBackground(outlined(0xFFEDEBDD,20,SUCCESS));
                if(bibleReader.chapterFromVerseGrid){
                    final ScrollView scroller=currentScroller;
                    afterPickerLayout(c,()->{if(screen==Screen.CHAPTER&&currentScroller==scroller){
                        scroller.scrollTo(0,Math.max(0,c.getTop()-dp(30)));
                        c.postDelayed(()->{
                            if(currentScroller==scroller)c.setBackground(outlined(CARD,22,BORDER));
                        },1100);
                    }});
                }else c.post(()->{if(currentScroller!=null){
                    int top=c.getTop();View node=c.getParent() instanceof View?(View)c.getParent():null;
                    if(node!=null)top+=node.getTop();
                    currentScroller.smoothScrollTo(0,Math.max(0,top-dp(30)));
                }});
                bibleReader.focusVerse=-1;
            }
            LinearLayout head=row();
            head.addView(txt(verse.ref,14,bookInk(bibleReader.selectedBook),true));
            if(highlighted&&!bibleReader.chapterFromVerseGrid){TextView tick=txt("✓ Выбран",12,GREEN,true);
                head.addView(tick);}
            add(c,head,0);
            add(c,prose(verse.text,19),8);
            {
                int color=stat==null?MUTED:stat.rank==3?SUCCESS:
                    stat.rank==2?0xFF517993:stat.rank==1?0xFF99723D:MUTED;
                String label=stat==null?"Не изучен":stat.state;
                int rep=stat==null?0:stat.repetitions;
                add(c,txt("● "+label+"   ·   Повторений: "+rep,12,color,true),12);
                if(stat!=null && stat.nextDue>0 && stat.rank>0)
                    add(c,txt(stat.nextDue<=System.currentTimeMillis()?"Повторить сегодня":
                        "Следующее повторение: "+date(stat.nextDue),12,MUTED,false),4);
            }
        }
    }
    private ArrayAdapter<String> spinnerAdapter(String[] values){
        return new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,values){
            @Override public View getView(int position,View reusable,ViewGroup parent){
                TextView text=txt(getItem(position),15,WHITE,true);
                text.setGravity(Gravity.CENTER_VERTICAL);
                text.setPadding(dp(8),dp(5),dp(4),dp(5));
                return text;
            }
            @Override public View getDropDownView(int position,View reusable,ViewGroup parent){
                TextView text=txt(getItem(position),16,WHITE,false);
                text.setPadding(dp(14),dp(14),dp(14),dp(14));
                text.setBackgroundColor(CARD_ALT);
                return text;
            }
        };
    }
    private Spinner chapterPicker(int selected){
        int count=bibleReader.bible.chapterCount(bibleReader.selectedBook);String[] names=new String[count];
        for(int i=0;i<count;i++)names[i]="Глава "+(i+1);
        Spinner spin=new Spinner(this);
        spin.setAdapter(spinnerAdapter(names));
        spin.setSelection(Math.max(0,Math.min(count-1,selected-1)));return spin;
    }
    private void updateVersePicker(Spinner verses,int chapter,int chosen){
        int count=bibleReader.bible.chapter(bibleReader.selectedBook,chapter).size();
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
        if(!bibleReader.bible.isReady())return;
        Dialog dialog=new Dialog(this,R.style.MemoryDialogTheme);
        LinearLayout layout=column();layout.setPadding(dp(18),dp(24),dp(18),dp(20));
        layout.setBackgroundColor(BG);
        TextView name=displayText(Bible.NAMES[bibleReader.selectedBook-1],25);
        name.setGravity(Gravity.CENTER);add(layout,name,0);
        EditText number=numeric("Введите номер главы","");
        number.setSingleLine(true);number.setImeOptions(EditorInfo.IME_ACTION_DONE);
        add(layout,number,15);
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=column();list.setPadding(0,dp(8),0,dp(16));scroll.addView(list);
        int total=bibleReader.bible.chapterCount(bibleReader.selectedBook);
        Runnable refresh=()->{
            list.removeAllViews();
            int numberFilter=number(number);
            if(numberFilter>0){
                if(numberFilter<=total){
                    final int selection=numberFilter;
                    TextView one=button("Глава "+selection,true,()->{
                        dialog.dismiss();bibleReader.openReaderVerseGrid(selection);
                    });
                    add(list,one,5);
                }else helper(list,"В этой книге только "+total+" глав.");
                return;
            }
            for(int base=1;base<=total;base+=4){
                LinearLayout row=row();
                for(int k=base;k<base+4;k++){
                    if(k>total){row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(52),1));continue;}
                    final int chapter=k;
                    TextView b=button(String.valueOf(k),k==bibleReader.selectedChapter,()->{
                        dialog.dismiss();bibleReader.openReaderVerseGrid(chapter);
                    });
                    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);
                    p.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(b,p);
                }
                add(list,row,2);
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
            if(chosen>=1&&chosen<=total){dialog.dismiss();bibleReader.selectedChapter=chosen;
                bibleReader.rememberChapter(bibleReader.selectedBook,chosen);render();}
            else toast("Укажи главу от 1 до "+total);
            return true;
        });
        refresh.run();
        layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView close=button("Закрыть",false,dialog::dismiss);
        add(layout,close,8);
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
        LinearLayout builder=card(content);
        add(builder,txt("Выбрать отрывок",19,WHITE,true),0);
        if(!cardPicker)helper(builder,"Укажи начало и конец. Можно выбрать несколько глав.");
        int sc=bibleReader.rangeStartChapter<1?bibleReader.selectedChapter:bibleReader.rangeStartChapter;
        int ec=bibleReader.rangeEndChapter<1?sc:bibleReader.rangeEndChapter;
        int sv=bibleReader.rangeStartVerse<1?1:bibleReader.rangeStartVerse;
        int ev=bibleReader.rangeEndVerse<1?sv:bibleReader.rangeEndVerse;
        Spinner startChapter=chapterPicker(sc),endChapter=chapterPicker(ec);
        Spinner startVerse=new Spinner(this),endVerse=new Spinner(this);
        updateVersePicker(startVerse,sc,sv);updateVersePicker(endVerse,ec,ev);
        LinearLayout first=row(),last=row();
        TextView firstHeading=txt("НАЧАЛО",12,GREEN,true);add(builder,firstHeading,14);
        first.addView(startChapter,new LinearLayout.LayoutParams(0,dp(53),1));
        first.addView(startVerse,new LinearLayout.LayoutParams(0,dp(53),1));add(builder,first,5);
        add(builder,txt("КОНЕЦ",12,GREEN,true),12);
        last.addView(endChapter,new LinearLayout.LayoutParams(0,dp(53),1));
        last.addView(endVerse,new LinearLayout.LayoutParams(0,dp(53),1));add(builder,last,5);
        TextView info=txt("",13,MUTED,false);add(builder,info,9);
        Runnable update=()->{
            int a=startChapter.getSelectedItemPosition()+1,b=startVerse.getSelectedItemPosition()+1;
            int c=endChapter.getSelectedItemPosition()+1,d=endVerse.getSelectedItemPosition()+1;
            boolean valid=PassageRangeLogic.valid(a,b,c,d);
            info.setText(valid?Bible.reference(bibleReader.selectedBook,a,b,c,d):"Конец отрывка раньше начала");
            info.setTextColor(valid?GREEN:RED);
            bibleReader.rangeStartChapter=a;bibleReader.rangeStartVerse=b;bibleReader.rangeEndChapter=c;bibleReader.rangeEndVerse=d;
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
        action(builder,screen==Screen.BIBLE_PICKER?"Добавить отрывок":cardPicker?"Добавить карточку":"Просмотреть отрывок",true,()->{
            update.run();
            if(!PassageRangeLogic.valid(bibleReader.rangeStartChapter,bibleReader.rangeStartVerse,bibleReader.rangeEndChapter,bibleReader.rangeEndVerse)){
                toast("Конец не может быть раньше начала");return;
            }
            if(screen==Screen.BIBLE_PICKER){
                if(collectionActions.savePickedPassage(bibleReader.rangeStartChapter,bibleReader.rangeStartVerse,bibleReader.rangeEndChapter,bibleReader.rangeEndVerse)){
                    collectionActions.clearInsertionContext();screen=bibleReader.pickerBackScreen;render();
                }
            }
            else if(cardPicker)addCardFromSelection(flashcardSession.selectedDeck);
            else previewPassage();
        });
        if(!cardPicker&&screen!=Screen.BIBLE_PICKER)action(builder,"Добавить в карточки",false,()->{
            update.run();
            if(!PassageRangeLogic.valid(bibleReader.rangeStartChapter,bibleReader.rangeStartVerse,bibleReader.rangeEndChapter,bibleReader.rangeEndVerse)){
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
        if(bibleReader.rangeStartChapter<0){toast("Выбери стих или укажи границы");return;}
        int ec=bibleReader.rangeEndChapter<0?bibleReader.rangeStartChapter:bibleReader.rangeEndChapter;
        int ev=bibleReader.rangeEndChapter<0?bibleReader.rangeStartVerse:bibleReader.rangeEndVerse;
        List<Bible.Verse> verses;
        try{verses=bibleReader.bible.passage(bibleReader.selectedBook,bibleReader.rangeStartChapter,bibleReader.rangeStartVerse,ec,ev);}
        catch(IllegalArgumentException ex){toast(ex.getMessage());return;}
        String ref=Bible.reference(bibleReader.selectedBook,bibleReader.rangeStartChapter,bibleReader.rangeStartVerse,ec,ev);
        LocalStore.Entry existing=null;
        for(LocalStore.Entry e:store.study().allEntries())if(ref.equals(e.reference)){
            existing=e;break;
        }
        int mastered=existing==null?0:store.progress().masteryCount(existing.id);
        String description=ref+"\n"+verses.size()+" стихов · освоено "+mastered;
        AlertDialog.Builder dialog=new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setTitle("Твой отрывок")
            .setMessage(description)
            .setNegativeButton("Назад",null);
        if(existing!=null){
            final long entryId=existing.id;
            dialog.setPositiveButton("Продолжить изучение",(d,w)->{
                bibleReader.selectingPassage=false;studySession.openTraining(entryId);
            });
            dialog.setNeutralButton("Добавить в другую подборку",(d,w)->{
                chooseGroup(groupId->{store.study().add(groupId,ref,ref,verses);toast("Добавлено в подборку");});
            });
        }else{
            dialog.setPositiveButton("Начать изучение",(d,w)->{
                chooseGroup(groupId->{long id=store.study().add(groupId,ref,ref,verses);
                    bibleReader.selectingPassage=false;studySession.openTraining(id);});
            });
            dialog.setNeutralButton("Добавить в подборку",(d,w)->{
                chooseGroup(groupId->{store.study().add(groupId,ref,ref,verses);toast("Добавлено в подборку");});
            });
        }
        dialog.show();
    }
    private void styledDialog(AlertDialog d){
        d.setOnShowListener(x->{
            if(d.getWindow()!=null){
                d.getWindow().setBackgroundDrawable(shape(CARD,22));
                d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.91f),-2);
            }
            int[] ids={android.R.id.button1,android.R.id.button2,android.R.id.button3};
            for(int id:ids){View v=d.findViewById(id);if(v instanceof TextView)((TextView)v).setTextColor(GREEN);}
        });
        d.show();
    }
    private interface Pick {void apply(long groupId);}
    private void chooseGroup(Pick action) {
        List<LocalStore.UnifiedCollection> collections=store.collections().unifiedCollections();
        String[] names=new String[collections.size()+1];
        for(int i=0;i<collections.size();i++)names[i]=collections.get(i).title;
        names[collections.size()]="＋ Новая коллекция";
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Добавить в коллекцию").setItems(names,(d,which)->{
                if(which==collections.size())newGroup(action);
                else action.apply(collections.get(which).groupId);
            }).setNegativeButton("Отмена",null).create());
    }
    private void newGroup(Pick afterCreate) {
        newUnifiedCollection(u->{
            if(afterCreate!=null)afterCreate.apply(u.groupId);
            else{collectionActions.openUnifiedId=u.id;screen=Screen.COLLECTION_DETAIL;render();}
        });
    }
    /** Study collections get only these four approved actions, in this exact order. */
    private void showStudyGroupActions(LocalStore.Group group,View anchor){
        String[] actions={"Добавить свой текст","Переименовать","Удалить"};
        String[] icons={"file","pencil","trash"};
        showActionsMenu(anchor,actions,icons,which->{
            collectionActions.selectedGroup=group.id;
            switch(which){
                case 0:collectionActions.customTargetGroupId=group.id;screen=Screen.CUSTOM;render();break;
                case 1:renameGroup(group);break;
                case 2:confirmDeleteStudyGroup(group);break;
            }
        });
    }
    private void confirmDeleteStudyGroup(LocalStore.Group group){
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Удалить коллекцию?")
            .setMessage("Коллекция и её содержимое будут удалены. Прогресс изучения стихов сохранится")
            .setPositiveButton("Удалить",(d,w)->{
                store.collections().deleteGroup(group.id);invalidateRemovedSessions();screen=Screen.HOME;render();
            }).setNegativeButton("Отмена",null).create());
    }

    private void renderEntries() {

        LocalStore.Group group = store.collections().group(collectionActions.selectedGroup);

        if (group == null) {
            screen = Screen.HOME;
            render();
            return;
        }

        // Название открытой коллекции.
        title(group.title, null);

        // Общий прогресс изучения.
        helper(
                content,
                "Освоено: " + collectionActions.studyProgressFor(group.id)
        );

        // Все стихи коллекции прокручиваются независимо
        // от нижних кнопок.
        for (LocalStore.Entry entry :
                store.study().entries(group.id)) {

            entryCard(entry);
        }

        // Основное и вторичное действия внизу экрана.
        addStudyCollectionFooter(group);
    }

    private void renameGroup(LocalStore.Group group) {
        EditText name=input("Название коллекции",false);name.setText(group.title);
        LinearLayout box=column();box.setPadding(dp(20),dp(5),dp(20),dp(5));box.addView(name);
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setTitle("Переименовать коллекцию").setView(box)
            .setPositiveButton("Сохранить",(d,w)->{
                if(value(name).isEmpty()){toast("Укажи название");return;}
                store.collections().renameGroup(group.id,value(name));render();
            }).setNegativeButton("Отмена",null).create());
    }

    /** Dedicated drag-to-reorder mode; dragging never starts in normal study lists. */
    private void renderStudyReorder(){
        title("Изменить порядок", "Удерживай строку и перетащи её на новое место");
        List<LocalStore.Entry> list=store.study().entries(collectionActions.selectedGroup);
        List<Long> ids=new ArrayList<>();for(LocalStore.Entry e:list)ids.add(e.id);
        for(LocalStore.Entry e:list)renderReorderRow(e.id,e.reference,ids,false);
        action(content,"Готово",true,()->{
            LocalStore.UnifiedCollection u=store.collections().unifiedForGroup(collectionActions.selectedGroup);
            screen=u==null?Screen.ENTRIES:Screen.COLLECTION_DETAIL;
            if(u!=null)collectionActions.openUnifiedId=u.id;render();
        });
    }
    private void renderCardReorder(){
        title("Изменить порядок", "Удерживай строку и перетащи её на новое место");
        List<LocalStore.Flashcard> list=store.flashcards().flashcards(flashcardSession.selectedDeck);
        List<Long> ids=new ArrayList<>();for(LocalStore.Flashcard f:list)ids.add(f.id);
        for(LocalStore.Flashcard f:list)renderReorderRow(f.id,f.reference,ids,true);
        action(content,"Готово",true,()->{screen=Screen.CARD_DECK;render();});
    }
    private void renderReorderRow(long id,String label,List<Long> ids,boolean cards){
        LinearLayout item=row();item.setPadding(dp(16),dp(12),dp(16),dp(12));
        item.setBackground(outlined(CARD,14,BORDER));
        MenuGlyph dragGlyph=new MenuGlyph("shuffle",GREEN);
        item.addView(dragGlyph,new LinearLayout.LayoutParams(dp(40),dp(44)));
        item.addView(txt(label,16,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
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
                if(cards)collectionActions.reorderCards(flashcardSession.selectedDeck,ids);
                else collectionActions.reorderStudy(collectionActions.selectedGroup,ids);
                render();return true;
            }
            return true;
        });
        add(content,item,7);
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
        LinearLayout wrap=column();wrap.setPadding(dp(20),dp(8),dp(20),dp(10));
        add(wrap,txt(Bible.NAMES[book-1],17,WHITE,true),0);
        EditText startChapter=numeric("Начальная глава",String.valueOf(sc));
        EditText startVerse=numeric("Начальный стих",String.valueOf(sv));
        EditText endChapter=numeric("Конечная глава",String.valueOf(ec));
        EditText endVerse=numeric("Конечный стих",String.valueOf(ev));
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
        add(wrap,txt("НАЧАЛО",12,GREEN,true),13);
        LinearLayout first=row();first.addView(startChapter,new LinearLayout.LayoutParams(0,dp(54),1));
        first.addView(startVerse,new LinearLayout.LayoutParams(0,dp(54),1));add(wrap,first,5);
        add(wrap,txt("КОНЕЦ",12,GREEN,true),12);
        LinearLayout last=row();last.addView(endChapter,new LinearLayout.LayoutParams(0,dp(54),1));
        last.addView(endVerse,new LinearLayout.LayoutParams(0,dp(54),1));add(wrap,last,5);
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle(heading).setView(wrap)
            .setPositiveButton("Сохранить",(d,w)->{
                int a=number(startChapter),b=number(startVerse),c=number(endChapter),end=number(endVerse);
                if(!PassageRangeLogic.valid(a,b,c,end)){
                    toast("Неверный диапазон");return;
                }
                try{bibleReader.bible.passage(book,a,b,c,end);save.save(a,b,c,end);}
                catch(Exception err){toast(err.getMessage()==null?"Ошибка изменения диапазона":err.getMessage());}
            }).setNegativeButton("Отмена",null).create());
    }
    private void editStudyEntry(LocalStore.Entry e){
        int book=studySession.bookForEntry(e);
        if(book>0 && !e.lines().isEmpty()){
            int[] first=parseBibleLocation(e.lines().get(0).ref);
            int[] last=parseBibleLocation(e.lines().get(e.lines().size()-1).ref);
            if(first!=null&&last!=null){
                editBibleRange("Изменить отрывок",book,first[0],first[1],last[0],last[1],
                    (a,b,c,d)->{
                        List<Bible.Verse> lines=bibleReader.bible.passage(book,a,b,c,d);
                        store.study().reviseStudyPassage(e.id,Bible.reference(book,a,b,c,d),lines);
                        render();
                    });
                return;
            }
        }
        EditText source=input("Название / источник",false);source.setText(e.reference);
        EditText words=input("Текст",true);words.setMinLines(5);
        StringBuilder original=new StringBuilder();
        for(Bible.Verse v:e.lines()){
            if(original.length()>0)original.append('\n');original.append(v.text);
        }
        words.setText(original.toString());
        LinearLayout fields=column();fields.setPadding(dp(20),dp(5),dp(20),dp(12));
        add(fields,source,0);add(fields,words,10);
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Изменить свой текст").setView(fields)
            .setPositiveButton("Сохранить",(d,w)->{
                if(value(source).isEmpty()||value(words).isEmpty()){toast("Заполни поля");return;}
                String[] fragments=value(words).split("\\n");
                List<Bible.Verse> lines=new ArrayList<>();
                for(int i=0;i<fragments.length;i++)if(!fragments[i].trim().isEmpty()){
                    lines.add(new Bible.Verse("свой:"+(i+1),fragments[i].trim()));
                }
                store.study().reviseStudyPassage(e.id,value(source),lines);
                render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void editCardEntry(LocalStore.Flashcard card){
        if(card.bookId>0){
            editBibleRange("Изменить карточку",card.bookId,card.firstChapter,card.firstVerse,
                card.lastChapter,card.lastVerse,(a,b,c,d)->{
                    String ref=Bible.reference(card.bookId,a,b,c,d);
                    store.flashcards().changeCardInDeck(flashcardSession.selectedDeck,card,card.bookId,a,b,c,d,
                        ref,PassageText.join(bibleReader.bible.passage(card.bookId,a,b,c,d)));
                    render();
                });
            return;
        }
        EditText source=input("Название / источник",false);source.setText(card.reference);
        EditText words=input("Текст",true);words.setMinLines(4);words.setText(card.content);
        LinearLayout fields=column();fields.setPadding(dp(20),dp(5),dp(20),dp(12));
        add(fields,source,0);add(fields,words,9);
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Изменить карточку").setView(fields)
            .setPositiveButton("Сохранить",(d,w)->{
                if(value(source).isEmpty()||value(words).isEmpty()){toast("Заполни поля");return;}
                store.flashcards().changeCardInDeck(flashcardSession.selectedDeck,card,0,0,0,0,0,value(source),value(words));
                render();
            }).setNegativeButton("Отмена",null).create());
    }

    private void showEntryActions(LocalStore.Entry entry,View anchor){
        String[] labels={"Изменить отрывок","Добавить стих / отрывок после него","Удалить"};
        String[] icons={"pencil","book","trash"};
        showActionsMenu(anchor,labels,icons,which->{
            switch(which){
                case 0:editStudyEntry(entry);break;
                case 1:
                    collectionActions.clearInsertionContext();
                    collectionActions.insertAfterEntryId=entry.id;collectionActions.studyInsertionTrail.add(entry.id);
                    bibleReader.openBiblePicker(BibleReaderController.BibleTarget.STUDY_GROUP,entry.groupId,screen==Screen.COLLECTION_DETAIL?Screen.COLLECTION_DETAIL:Screen.ENTRIES);break;
                case 2:
                    styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                        .setTitle("Убрать отрывок из коллекции?")
                        .setPositiveButton("Убрать",(d,w)->{
                            store.collections().removeStudyEntry(entry.id);invalidateRemovedSessions();render();
                        }).setNegativeButton("Отмена",null).create());break;
            }
        });
    }
    private void showCardActions(LocalStore.Flashcard card,View anchor){
        String[] labels={"Изменить отрывок","Добавить стих / отрывок после него","Удалить"};
        String[] icons={"pencil","book","trash"};
        showActionsMenu(anchor,labels,icons,which->{
            switch(which){
                case 0:editCardEntry(card);break;
                case 1:
                    collectionActions.clearInsertionContext();
                    collectionActions.insertAfterCardId=card.id;collectionActions.cardInsertionTrail.add(card.id);
                    bibleReader.openBiblePicker(BibleReaderController.BibleTarget.FLASHCARD_DECK,flashcardSession.selectedDeck,Screen.CARD_DECK);break;
                case 2:
                    styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                        .setTitle("Убрать карточку из подборки?")
                        .setPositiveButton("Убрать",(d,w)->{
                            store.flashcards().removeFlashcard(flashcardSession.selectedDeck,card.id);render();
                        }).setNegativeButton("Отмена",null).create());break;
            }
        });
    }

    private void manageEntry(LocalStore.Entry entry) {
        new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setTitle(entry.title)
            .setItems(new String[]{"Переименовать отрывок","Удалить отрывок"},(d,choice)->{
                if(choice==0){
                    EditText name=input("Название отрывка",false);name.setText(entry.title);
                    LinearLayout box=column();box.setPadding(dp(20),dp(5),dp(20),dp(5));box.addView(name);
                    new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setTitle("Переименовать отрывок").setView(box)
                        .setPositiveButton("Сохранить",(dd,w)->{
                            if(value(name).isEmpty()){toast("Укажи название");return;}
                            store.study().renameEntry(entry.id,value(name));render();
                        }).setNegativeButton("Отмена",null).show();
                } else new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                    .setTitle("Удалить отрывок?")
                    .setMessage("Отрывок будет удалён из коллекции. История его изучения сохранится.")
                    .setPositiveButton("Удалить",(dd,w)->{store.study().deleteEntry(entry.id);invalidateRemovedSessions();render();})
                    .setNegativeButton("Отмена",null).show();
            }).show();
    }
    private void entryCard(LocalStore.Entry e) {
        long source=store.study().canonicalStudyEntryId(e.id);
        int total=e.lines().size(),learned=store.progress().masteryCount(source);
        int established=store.progress().consolidatedVerseCount(source);
        String progress=established+" выучено · "+Math.max(0,learned-established)+" освоено";
        String detail=e.reference+" · "+progress+" · Связок: "+store.progress().masteredLinkedSegments(source);
        LinearLayout c=card(content);c.setBackground(homeSurface());
        LinearLayout row=row();
        LinearLayout body=column();
        add(body,displayText(e.title,17),0);
        add(body,txt(detail,13,MUTED,false),6);
        row.addView(body,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=txt("⋮",26,MUTED,true);more.setGravity(Gravity.CENTER);
        more.setContentDescription("Действия с отрывком "+e.reference);
        row.addView(more,new LinearLayout.LayoutParams(dp(48),dp(54)));
        more.setOnClickListener(v->showEntryActions(e,more));
        body.setOnClickListener(v->studySession.openTraining(e.id));
        add(c,row,0);
    }
    private String date(long millis){return new SimpleDateFormat("dd.MM",Locale.forLanguageTag("ru-RU")).format(new Date(millis));}
    private void renderCustom() {
        title("Свой текст","Вставь стих, песню Писания или собственный учебный фрагмент.");
        LinearLayout form=card(content);
        label(form,"Название");EditText title=input("Например: Обетования",false);add(form,title,7);
        label(form,"Ссылка / источник");EditText ref=input("Например: Римлянам 8:28",false);add(form,ref,7);
        label(form,"Текст для заучивания");
        EditText lines=input("Вставь текст. Для большого отрывка размести каждый стих с новой строки.",true);
        add(form,lines,7);
        helper(form,"Каждая непустая строка будет отдельным шагом обучения (до 500 строк).");
        action(form,"Сохранить в коллекцию",true,()->{
            String t=value(title),r=value(ref),s=value(lines);
            if(t.isEmpty()||s.isEmpty()){toast("Введи название и текст");return;}
            List<Bible.Verse> items=new ArrayList<>();
            for(String raw:s.split("\\R")) if(!raw.trim().isEmpty())
                items.add(new Bible.Verse(String.valueOf(items.size()+1),raw.trim()));
            if(items.size()>500){toast("Максимум 500 строк");return;}
            if(collectionActions.customTargetGroupId>0){
                long groupId=collectionActions.customTargetGroupId;
                if(store.collections().group(groupId)==null){toast("Коллекция не найдена");return;}
                collectionActions.customTargetGroupId=-1;
                long id=store.study().addUserMaterial(groupId,t,r.isEmpty()?"Свой текст":r,items);
                studySession.openTraining(id);
            }else{
                chooseGroup(groupId->{long id=store.study().addUserMaterial(groupId,t,r.isEmpty()?"Свой текст":r,items);studySession.openTraining(id);});
            }
        });

    }
    /** Pressing 'Повторить изученное' starts the first due exercise immediately. */

    private void renderDue() {
        title("Повторить изученное","Повторения запускаются автоматически по расписанию.");
        LinearLayout c=headerCard(content);
        add(c,txt("На данный момент повторений нет ✓",21,WHITE,true),0);
        helper(c,"Новые стихи не добавляются в повторение до освоения.");

    }

    private void showAnyVerse(LocalStore.Entry e) {
        int max=store.progress().unlockedPosition(e.id,e.lines().size());
        final EditText positionInput=numeric("Номер стиха в подборке",String.valueOf(studySession.stepCursor+1));
        LinearLayout wrapper=column();wrapper.setPadding(dp(20),dp(5),dp(20),dp(5));
        wrapper.addView(positionInput);
        new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setTitle("Открытые стихи: 1–"+(max+1))
            .setView(wrapper).setPositiveButton("Перейти",(d,w)->{
                int n=number(positionInput);
                if(!LessonFlowLogic.canOpen(n-1,max,e.lines().size())){
                    toast("Следующий стих откроется после выполнения заданий");return;
                }
                studySession.studyStage=StudyPlanLogic.Stage.VERSE;studySession.stepCursor=n-1;
                studySession.resetTrainingStep(e);studySession.saveTrainingSession();render();
            }).setNegativeButton("Отмена",null).show();
    }

    private void chooseTrainingMode(StudySessionController.TrainingMode chosen,boolean mastered) {
        if(chosen==StudySessionController.TrainingMode.HIDE) {
            if(studySession.trainingMode!=StudySessionController.TrainingMode.HIDE)studySession.blurSeed=new Random().nextInt();
            studySession.mask=studySession.trainingMode==StudySessionController.TrainingMode.HIDE?studySession.mask+25:25;
            if(studySession.mask>100){studySession.mask=0;chosen=StudySessionController.TrainingMode.READ;}
        }
        if(chosen==StudySessionController.TrainingMode.ORDER && studySession.trainingMode!=StudySessionController.TrainingMode.ORDER)studySession.resetWordTiles();
        if((studySession.reviewMode || studySession.groupMode || (studySession.studyStage==StudyPlanLogic.Stage.VERSE &&
            store.progress().exerciseDone(studySession.selectedEntry,studySession.stepCursor)>=4)) && chosen!=StudySessionController.TrainingMode.TYPE)
            studySession.testAssisted=true;
        StudySessionController.TrainingMode previous=studySession.trainingMode;
        studySession.trainingMode=chosen;
        if(previous==StudySessionController.TrainingMode.TYPE && chosen!=StudySessionController.TrainingMode.TYPE){
            android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
            if(keyboard!=null && root!=null)
                keyboard.hideSoftInputFromWindow(root.getWindowToken(),0);
            if(root!=null)root.requestFocus();
        }
        studySession.saveTrainingSession();
        // Reuse the existing Activity and ScrollView: changing modes must not jump to the top.
        if(previous==StudySessionController.TrainingMode.HIDE && chosen==StudySessionController.TrainingMode.HIDE && activePassage!=null){
            updateBlurText();updateModeBar();return;
        }
        updateActiveTrainingUI();
    }
    /** Training actions belong to the card, not the application's navigation bar. */
    private void addTrainingModeButtons(LinearLayout card) {
        LinearLayout bar=row();bar.setPadding(0,dp(12),0,dp(6));
        StudySessionController.TrainingMode[] modes={StudySessionController.TrainingMode.HIDE,StudySessionController.TrainingMode.ORDER,StudySessionController.TrainingMode.TYPE};
        String[] labels={"◌  Скрыть","▦  Собрать","✎  Написать"};
        for(int i=0;i<modes.length;i++) {
            final StudySessionController.TrainingMode mode=modes[i];
            TextView b=button(labels[i],studySession.trainingMode==mode,()->chooseTrainingMode(mode,studySession.activeMastered));
            b.setTextSize(13);b.setMinHeight(dp(52));b.setPadding(dp(2),dp(7),dp(2),dp(7));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);
            lp.setMargins(dp(2),0,dp(2),0);bar.addView(b,lp);
            bottomModeButtons[i]=b;
        }
        add(card,bar,4);
        updateModeBar();
    }
    private void updateModeBar() {
        StudySessionController.TrainingMode[] modes={StudySessionController.TrainingMode.HIDE,StudySessionController.TrainingMode.ORDER,StudySessionController.TrainingMode.TYPE};
        String[] labels={"◌  Скрыть"+(studySession.trainingMode==StudySessionController.TrainingMode.HIDE?" "+studySession.mask+"%":""),
            "▦  Собрать","✎  Написать"};
        for(int i=0;i<modes.length;i++){
            TextView b=bottomModeButtons[i];if(b==null)continue;
            boolean selected=studySession.trainingMode==modes[i];b.setText(labels[i]);b.setTextColor(selected?PRIMARY_ON:WHITE);
            if(selected){GradientDrawable gd=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xFFDB8C6C,0xFFB8583F});gd.setCornerRadius(dp(14));b.setBackground(gd);}
            else b.setBackground(outlined(CARD_ALT,14,BORDER));
        }
    }
    private void updateBlurText() {
        if(activePassage==null || studySession.activeEntry==null || studySession.activeStep==null)return;
        List<Bible.Verse> all=studySession.activeEntry.lines();
        android.text.SpannableStringBuilder text=new android.text.SpannableStringBuilder();
        int percentage=studySession.trainingMode==StudySessionController.TrainingMode.HIDE?studySession.mask:0;
        try {
            for(Bible.Verse verse:all.subList(studySession.activeStep.start,studySession.activeStep.end)) {
                if(text.length()>0)text.append("\n\n");
                text.append(verse.ref+"   ");int offset=text.length();
                text.append(verse.text);
                for(int[] range:ReviewLogic.blurRanges(verse.text,percentage,verse.ref.hashCode()^studySession.blurSeed)) {
                    text.setSpan(new MaskFilterSpan(new BlurMaskFilter(dp(9),BlurMaskFilter.Blur.NORMAL)),
                        offset+range[0],offset+range[1],android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            activePassage.setLayerType(percentage>0?View.LAYER_TYPE_SOFTWARE:View.LAYER_TYPE_NONE,null);
            activePassage.setText(text,TextView.BufferType.SPANNABLE);
            installTemporaryReveal(activePassage);
        } catch(RuntimeException unsupported) {
            // Unsupported device graphics must never crash the study activity.
            StringBuilder fallback=new StringBuilder();
            for(Bible.Verse verse:all.subList(studySession.activeStep.start,studySession.activeStep.end)) {
                if(fallback.length()>0)fallback.append("\n\n");
                fallback.append(verse.ref).append("   ")
                    .append(ReviewLogic.masked(verse.text,percentage,verse.ref.hashCode()^studySession.blurSeed));
            }
            activePassage.setLayerType(View.LAYER_TYPE_NONE,null);
            activePassage.setText(fallback.toString());
        }
        activePassage.setContentDescription(percentage>0?
            "Скрыто "+percentage+" процентов слов. Попробуйте их вспомнить.":text.toString());
        if(activeStatus!=null)activeStatus.setText(percentage==0?
            "Прочитай стих, затем проверь себя по памяти.":percentage==100?
            "Ещё одно нажатие — и слова откроются":
            "Размыто "+percentage+"% слов. Удерживай скрытое слово, чтобы посмотреть его.");
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
    private void updateActiveTrainingUI() {
        if(activeTextArea==null || activeWorkArea==null || studySession.activeEntry==null || studySession.activeStep==null)return;
        // Preserve scroll position; unlike render(), this does NOT replace the ScrollView.
        final int position=currentScroller==null?0:currentScroller.getScrollY();
        // Release focus from the old answer before removing its view.
        if(activeWorkArea.hasFocus())activeWorkArea.clearFocus();
        activeTextArea.removeAllViews();activeWorkArea.removeAllViews();activePassage=null;activeStatus=null;
        if(studySession.trainingMode==StudySessionController.TrainingMode.READ || studySession.trainingMode==StudySessionController.TrainingMode.HIDE) {
            activePassage=txt("",20,WHITE,false);activePassage.setTextIsSelectable(false);
            add(activeTextArea,activePassage,8);
            activeStatus=txt("",13,MUTED,false);add(activeTextArea,activeStatus,12);
            updateBlurText();
        } else {
            add(activeTextArea,txt(studySession.activeStep.title,19,WHITE,true),9);
            helper(activeTextArea,"Текст закрыт. Вспоминай без подсказок.");
        }
        if(studySession.trainingMode==StudySessionController.TrainingMode.ORDER) {
            showWordOrder(activeWorkArea,studySession.activeEntry.lines().subList(studySession.activeStep.start,studySession.activeStep.end));
        } else if(studySession.trainingMode==StudySessionController.TrainingMode.TYPE) {
            LinearLayout typing=column();typing.setPadding(dp(13),dp(16),dp(13),dp(16));
            typing.setBackground(outlined(CARD_ALT,17,VIOLET));
            add(typing,txt(studySession.reviewMode?"↻  Контрольное повторение":
                (!studySession.groupMode && studySession.studyStage==StudyPlanLogic.Stage.VERSE && store.progress().exerciseDone(studySession.selectedEntry,studySession.stepCursor)>=4)?
                "✦  Контрольная проверка":"✎  Напиши по памяти",19,WHITE,true),0);
            helper(typing,"Введи текст без номера стиха. Контрольная выполняется без подсказок.");
            EditText answer=input("Твой ответ…",true);forbidKeyboardHints(answer,true);
            answer.setText(studySession.draft);answer.setMinLines(7);
            answer.setShowSoftInputOnFocus(true);
            answer.addTextChangedListener(new TextWatcher(){
                @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                    studySession.draft=s.toString();studySession.saveTrainingSession();
                }
                @Override public void afterTextChanged(Editable s){}
            });
            add(typing,answer,12);
            helper(typing,"Регистр, пунктуация и ё/е не влияют на точность. Порог — 90%.");
            action(typing,"✓  Проверить написанное",true,()->studySession.checkAnswer(studySession.activeEntry,studySession.activeStep,studySession.activeMastered));
            add(activeWorkArea,typing,14);
        } else if(studySession.trainingMode!=StudySessionController.TrainingMode.ORDER){
            helper(activeWorkArea,"Выбери «Собрать» или «Написать» для следующей тренировки.");
        }
        if(studySession.trainingMode!=StudySessionController.TrainingMode.READ)
            action(activeWorkArea,"Показать исходный текст",false,()->chooseTrainingMode(StudySessionController.TrainingMode.READ,studySession.activeMastered));
        if(!studySession.reviewMode&&!studySession.groupMode&&studySession.studyStage==StudyPlanLogic.Stage.VERSE){
            final int completed=store.progress().exerciseDone(studySession.selectedEntry,studySession.stepCursor);
            if(completed==0 && studySession.trainingMode==StudySessionController.TrainingMode.READ)
                action(activeWorkArea,"✓  Прочитал · перейти к пропускам",true,()->completeIntro(1));
            if(completed==1 && studySession.trainingMode==StudySessionController.TrainingMode.HIDE && studySession.mask==100)
                action(activeWorkArea,"✓  Восстановил слова · перейти к сборке",true,()->completeIntro(2));
        }
        updateModeBar();
        if(currentScroller!=null)currentScroller.post(()->currentScroller.scrollTo(0,position));
    }
    private void completeIntro(int completed){
        if(!studySession.requireValidSession())return;
        if(studySession.reviewMode||studySession.groupMode||studySession.studyStage!=StudyPlanLogic.Stage.VERSE)return;
        if(store.progress().exerciseDone(studySession.selectedEntry,studySession.stepCursor)==completed-1 &&
            (completed!=2 || (studySession.trainingMode==StudySessionController.TrainingMode.HIDE && studySession.mask==100))){
            if(!studySession.finishExercise(studySession.selectedEntry,studySession.stepCursor,completed))return;
            studySession.trainingMode=completed==1?StudySessionController.TrainingMode.HIDE:StudySessionController.TrainingMode.ORDER;
            studySession.mask=completed==1?25:0;studySession.resetWordTiles();studySession.saveTrainingSession();render();
        }
    }
    private void renderStepNavigator(LinearLayout into,LocalStore.Entry e,int stepCount) {
        LinearLayout controls=row();
        TextView prev=button("←  Назад",false,()->studySession.moveStep(e,-1));
        TextView next=button("Вперёд  →",false,()->studySession.moveStep(e,1));
        prev.setEnabled(studySession.stepCursor>0);prev.setAlpha(studySession.stepCursor>0?1f:0.45f);
        boolean nextAllowed=studySession.stepCursor+1<stepCount && (studySession.groupMode||studySession.reviewMode||studySession.studyStage!=StudyPlanLogic.Stage.VERSE||
            LessonFlowLogic.canOpen(studySession.stepCursor+1,store.progress().unlockedPosition(e.id,e.lines().size()),e.lines().size()));
        next.setEnabled(nextAllowed);next.setAlpha(nextAllowed?1f:0.45f);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(51),1);lp.rightMargin=dp(7);
        controls.addView(prev,lp);controls.addView(next,new LinearLayout.LayoutParams(0,dp(51),1));
        add(into,controls,10);
        helper(into,studySession.groupMode?"Связное воспроизведение укрепляет переходы между стихами.":
            studySession.reviewMode?"Повторение без подсказок.":"Назад можно всегда. Вперёд — после контрольной проверки; Библия читается свободно.");
    }
    /** Lightweight wrapping container for word tiles; uses Android SDK only. */
    private static final class WrapLayout extends ViewGroup {
        private final int spacing;
        WrapLayout(Context context,int spacing) {super(context);this.spacing=spacing;}
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
            int x=0,y=getPaddingTop(),lineHeight=0;
            for(int i=0;i<getChildCount();i++) {
                View child=getChildAt(i);if(child.getVisibility()==GONE)continue;
                int cw=child.getMeasuredWidth(),ch=child.getMeasuredHeight();
                if(x>0&&x+cw>available){y+=lineHeight+spacing;x=0;lineHeight=0;}
                child.layout(getPaddingLeft()+x,y,getPaddingLeft()+x+cw,y+ch);
                x+=cw+spacing;lineHeight=Math.max(lineHeight,ch);
            }
        }
    }
    private TextView wordTile(String word,int bg,int color,int stroke,Runnable tapped) {
        TextView tv=txt(word,16,color,true);
        tv.setMinHeight(dp(43));tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12),dp(8),dp(12),dp(8));tv.setBackground(outlined(bg,12,stroke));
        if(tapped!=null){tv.setClickable(true);tv.setFocusable(true);tv.setOnClickListener(v->tapped.run());}
        return tv;
    }
    private void showWordOrder(LinearLayout card,List<Bible.Verse> verses) {
        List<String> sources=new ArrayList<>();for(Bible.Verse v:verses)sources.add(v.text);
        List<String[]> chunks=WordOrderLogic.chunks(sources);
        if(chunks.isEmpty()){helper(card,"Нет слов для сборки.");return;}
        studySession.orderChunk=Math.min(studySession.orderChunk,chunks.size()-1);
        String[] words=chunks.get(studySession.orderChunk);
        if(studySession.orderBankOrder.length!=words.length)
            studySession.orderBankOrder=WordOrderLogic.shuffledIndices(words.length,System.nanoTime());
        add(card,txt("Собери фрагмент "+(studySession.orderChunk+1)+" из "+chunks.size(),17,WHITE,true),13);
        helper(card,"Нажимай на слова снизу в том порядке, как они идут в тексте.");
        add(card,txt(studySession.orderPosition+" / "+words.length+" слов",13,GREEN,true),13);
        WrapLayout assembled=new WrapLayout(this,dp(7));assembled.setPadding(0,dp(5),0,dp(10));
        for(int i=0;i<studySession.orderPosition;i++) assembled.addView(wordTile(words[i],0xFFE1EDDF,WHITE,SUCCESS,null));
        if(studySession.orderPosition==0)assembled.addView(txt("Здесь появятся выбранные слова…",14,MUTED,false));
        add(card,assembled,6);
        if(studySession.orderPosition<words.length) {
            label(card,"Выбери следующее слово");
            WrapLayout options=new WrapLayout(this,dp(8));
            for(int tileIndex:studySession.orderBankOrder) {
                if(studySession.usedWordTiles.contains(tileIndex))continue;
                final int pressed=tileIndex;
                boolean wrong=studySession.orderWrongTile==tileIndex;
                TextView tile=wordTile(WordOrderLogic.comparable(words[tileIndex]).isEmpty()?words[tileIndex]:
                    words[tileIndex].replaceAll("^[^\\p{L}\\p{Nd}]+|[^\\p{L}\\p{Nd}]+$",""),
                    wrong?0xFFF8D6D2:CARD_ALT,wrong?RED:WHITE,wrong?RED:BORDER,()->{
                        if(WordOrderLogic.accepts(words,studySession.orderPosition,pressed)) {
                            studySession.usedWordTiles.add(pressed);studySession.orderPosition++;studySession.orderWrongTile=-1;
                        } else {studySession.orderWrongTile=pressed;studySession.orderMistakes++;}
                        updateActiveTrainingUI();
                    });
                options.addView(tile);
            }
            add(card,options,8);
            if(studySession.orderWrongTile!=-1)add(card,txt("Не то слово. Попробуй ещё раз.",13,RED,false),10);
        } else {
            add(card,txt("✓  Фрагмент собран правильно",16,GREEN,true),12);
            if(studySession.orderChunk<chunks.size()-1)
                action(card,"→  Следующий фрагмент",true,()->{
                    studySession.orderChunk++;studySession.orderPosition=0;studySession.orderWrongTile=-1;studySession.orderBankOrder=new int[0];studySession.usedWordTiles.clear();updateActiveTrainingUI();
                });
            else {
                if(!studySession.reviewMode&&!studySession.groupMode&&studySession.studyStage==StudyPlanLogic.Stage.VERSE &&
                    store.progress().exerciseDone(studySession.selectedEntry,studySession.stepCursor)==2 && !studySession.orderCompleted){
                    if(!studySession.requireValidSession())return;
                    if(!studySession.finishExercise(studySession.selectedEntry,studySession.stepCursor,3))return;studySession.orderCompleted=true;
                }
                helper(card,"Ты расставил слова с подсказками. Теперь проверь, сможешь ли воспроизвести их без готовых вариантов.");
                action(card,"✎  Написать по памяти",true,()->chooseTrainingMode(StudySessionController.TrainingMode.TYPE,false));
            }
        }
        if(studySession.orderMistakes>0)helper(card,"Ошибочных нажатий: "+studySession.orderMistakes);
        action(card,"↺  Собрать сначала",false,()->{studySession.resetWordTiles();updateActiveTrainingUI();});
    }
    private void renderTraining() {
        if(!studySession.requireValidSession())return;
        LocalStore.Entry e=store.study().entry(studySession.selectedEntry);
        if(e==null){screen=Screen.HOME;render();return;}
        studySession.activeEntry=e;
        List<Bible.Verse> all=e.lines();
        List<StudyPlanLogic.Step> steps=studySession.studySteps(e);
        if(steps.isEmpty()){helper(content,"Нет текста для тренировки.");return;}
        studySession.stepCursor=Math.max(0,Math.min(studySession.stepCursor,steps.size()-1));
        StudyPlanLogic.Step step=steps.get(studySession.stepCursor);studySession.activeStep=step;
        int learned=store.progress().masteryCount(e.id),count=all.size();
        boolean mastered=store.progress().isStepMastered(e.id,studySession.studyStage.name(),step.start,step.end);
        studySession.activeMastered=mastered;
        title(studySession.reviewMode?"Повторение":studySession.groupMode?"Связный фрагмент":e.title,e.reference);
        int bookId=studySession.bookForEntry(e);
        add(content,txt("●  "+(bookId>0?Bible.NAMES[bookId-1]:"Свой текст"),13,
            BookColorLogic.color(bookId),true),5);
        LinearLayout stat=headerCard(content);
        add(stat,txt(learned+" / "+count+" стихов освоено",22,WHITE,true),0);
        progress(stat,learned,count);
        List<String> allRefs=new ArrayList<>();
        for(Bible.Verse v:all)allRefs.add(v.ref);
        if(!studySession.reviewMode&&!studySession.groupMode){
            GroupLearningLogic.Candidate group=studySession.nextGroup(e);
            if(group!=null){
                add(stat,txt("Рекомендуется: "+group.reason,13,WHITE,false),13);
                action(stat,"Тренировать связку",false,()->studySession.openGroup(group));
            }
            if(studySession.studyStage==StudyPlanLogic.Stage.VERSE){
                int completed=store.progress().exerciseDone(e.id,studySession.stepCursor);
                add(stat,txt(completed>=5?"Пять упражнений выполнены":
                    LessonFlowLogic.label(LessonFlowLogic.Exercise.values()[completed]),14,GREEN,true),10);
            }
        }
        if(!studySession.reviewMode&&!studySession.groupMode)renderStepNavigator(content,e,steps.size());
        if(!studySession.reviewMode&&!studySession.groupMode)action(content,"Выбрать открытый стих",false,()->showAnyVerse(e));
        if(studySession.groupMode)action(content,"←  Вернуться к стихам",false,()->{studySession.groupMode=false;studySession.forcedStep=null;studySession.studyStage=StudyPlanLogic.Stage.VERSE;studySession.stepCursor=studySession.returnVerseCursor;studySession.resetTrainingStep(e);render();});
        LinearLayout textCard=card(content);
        add(textCard,txt("КАРТОЧКА "+(studySession.stepCursor+1)+" ИЗ "+steps.size(),12,GREEN,true),0);
        add(textCard,txt(step.title+(mastered?"  ✓":""),18,WHITE,true),5);
        String status=store.progress().consolidated(e.id,studySession.studyStage.name(),step.start,step.end)?"Закреплён":
            mastered?"Предварительно выучен":"Изучается";
        add(textCard,txt(status,13,mastered?GREEN:MUTED,true),8);
        if(!studySession.checked) {
            activeTextArea=column();activeTextArea.setMinimumHeight(dp(92));
            add(textCard,activeTextArea,7);
            addTrainingModeButtons(textCard);
            activeWorkArea=column();add(textCard,activeWorkArea,5);
            updateActiveTrainingUI();
        } else {
            activeTextArea=null;activeWorkArea=null;activePassage=null;
            add(textCard,txt(studySession.passed?"Воспроизведено ✓":
                studySession.testAssisted&&studySession.lastAccuracy>=90?"Проверка с подсказкой не засчитана":"Пока есть ошибки",
                22,studySession.passed?GREEN:RED,true),14);
            add(textCard,txt("Точность: "+studySession.lastAccuracy+"%",21,WHITE,true),10);
            if(studySession.trainingMode==StudySessionController.TrainingMode.TYPE){
                label(textCard,"Твои ошибки по месту ввода");
                add(textCard,typedFeedback(studySession.lastExpected,studySession.draft),9);
            }
            helper(textCard,studySession.passed?"Результат сохранён. Поздняя повторная проверка закрепит стих.":
                "Попробуй ещё раз без подсказок или исправь выделенные ошибки.");
            label(textCard,"Правильный текст");add(textCard,txt(studySession.lastExpected,16,WHITE,false),8);
            action(content,"↺  Исправить ответ",true,()->{
                studySession.checked=false;studySession.passed=false;studySession.lastAccuracy=-1;studySession.trainingMode=StudySessionController.TrainingMode.TYPE;render();
            });
            if(studySession.reviewMode){
                action(content,"←  К повторениям",false,()->{studySession.reviewMode=false;studySession.forcedStep=null;screen=Screen.DUE;render();});
            }else if(studySession.groupMode){
                action(content,"←  К стихам",false,()->{studySession.groupMode=false;studySession.forcedStep=null;studySession.studyStage=StudyPlanLogic.Stage.VERSE;
                    studySession.stepCursor=studySession.returnVerseCursor;studySession.resetTrainingStep(e);render();});
            }else if(studySession.studyStage==StudyPlanLogic.Stage.VERSE){
                int done=store.progress().exerciseDone(e.id,studySession.stepCursor);
                if(studySession.passed&&done==4)action(content,"✦  Начать контрольную проверку",true,()->{
                    studySession.draft="";studySession.checked=false;studySession.passed=false;studySession.testAssisted=false;studySession.trainingMode=StudySessionController.TrainingMode.TYPE;render();
                });
                if(studySession.passed&&done>=5){
                    if(studySession.stepCursor+1<e.lines().size())
                        action(content,"→  Следующий стих",true,()->studySession.moveStep(e,1));
                    GroupLearningLogic.Candidate group=studySession.nextGroup(e);
                    if(group!=null)action(content,"✦  "+group.reason,false,()->studySession.openGroup(group));
                }
            }

        }

    }

    private TextView typedFeedback(String expected,String actual){
        android.text.SpannableStringBuilder annotated=new android.text.SpannableStringBuilder();
        for(TypingDiffLogic.Piece part:TypingDiffLogic.compare(expected,actual)){
            String fragment=part.kind==TypingDiffLogic.Kind.MISSING?"⟨пропуск: "+part.text+"⟩":part.text;
            int start=annotated.length();annotated.append(fragment);
            if(part.kind!=TypingDiffLogic.Kind.CORRECT)
                annotated.setSpan(new ForegroundColorSpan(RED),start,annotated.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView tv=txt("",17,WHITE,false);tv.setText(annotated);return tv;
    }

    /** Separate full-screen lesson: no card carousel or persistent app-tab bar. */
    private void renderFullscreenLesson() {
        if(!studySession.requireValidSession())return;
        LocalStore.Entry e=store.study().entry(studySession.selectedEntry);
        if(e==null){screen=Screen.HOME;render();return;}
        List<StudyPlanLogic.Step> steps=studySession.studySteps(e);
        if(steps.isEmpty()){
            LocalStore.UnifiedCollection u=store.collections().unifiedForGroup(e.groupId);
            screen=u==null?Screen.HOME:Screen.COLLECTION_DETAIL;
            if(u!=null)collectionActions.openUnifiedId=u.id;render();return;
        }
        studySession.stepCursor=Math.max(0,Math.min(studySession.stepCursor,steps.size()-1));
        studySession.activeEntry=e;studySession.activeStep=steps.get(studySession.stepCursor);
        studySession.activeMastered=store.progress().isStepMastered(e.id,studySession.studyStage.name(),studySession.activeStep.start,studySession.activeStep.end);
        LessonCurriculumLogic.Phase phase=studySession.checked&&studySession.passed?
            LessonCurriculumLogic.Phase.COMPLETE:studySession.lessonPhase(e);
        lessonErrorArea=null;
        if(phase==LessonCurriculumLogic.Phase.COMPLETE){renderCompletion(e);return;}
        root=column();root.setBackground(pageBackground());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout top=column();top.setPadding(dp(22),dp(15),dp(22),dp(6));
        LinearLayout topRow=row();
        TextView close=txt("×",30,WHITE,false);close.setGravity(Gravity.CENTER);
        close.setContentDescription("Выйти из урока");
        close.setOnClickListener(v->onBackPressed());
        // SV_DEVELOPER_MODE_UPDATE_1: long-press the close icon for debugging.
        close.setOnLongClickListener(v->{
            final int bookId=studySession.bookForEntry(e);
            DeveloperModeController.open(this,e,
                chapter->bookId>0 && bibleReader.bible.isReady()?bibleReader.bible.chapter(bookId,chapter).size():0,
                store.progress().masteryCount(e.id));
            return true;
        });
        topRow.addView(close,new LinearLayout.LayoutParams(dp(45),dp(45)));
        // Progress design v0.21.4: independently numbered exercises grouped by phase.
        // The complete strip can scroll horizontally for longer verses without
        // shrinking a cell below a comfortable touch/reading size.
        LinearLayout.LayoutParams remaining=new LinearLayout.LayoutParams(0,dp(45),1);
        topRow.addView(new View(this),remaining);
        if(DeveloperModeController.isEnabled(this)){
            TextView devButton=txt("DEV",12,GREEN,true);
            devButton.setGravity(Gravity.CENTER);
            devButton.setContentDescription("Открыть режим разработчика");
            devButton.setOnClickListener(v->{
                final int bookId=studySession.bookForEntry(e);
                DeveloperModeController.open(this,e,
                    chapter->bookId>0 && bibleReader.bible.isReady()?bibleReader.bible.chapter(bookId,chapter).size():0,
                    store.progress().masteryCount(e.id));
            });
            topRow.addView(devButton,new LinearLayout.LayoutParams(dp(48),dp(45)));
        }
        top.addView(topRow);
        renderLessonHotbar(top,phase,e);
        root.addView(top,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setClipToPadding(false);currentScroller=scroll;
        content=column();content.setPadding(dp(20),dp(11),dp(20),dp(18));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView mainAction;
        int number=studySession.stepCursor+1;
        if(phase==LessonCurriculumLogic.Phase.INTRO){
            lessonPhaseHeader("Ознакомление",e);
            renderLessonIntro();
            mainAction=button("Продолжить",true,()->{
                if(!studySession.requireValidSession())return;
                if(studySession.groupMode)studySession.groupIntroDone=true;
                else if(!studySession.finishExercise(e.id,studySession.activeStep.start,1))return;
                studySession.lessonVariant=0;studySession.lessonChunk=0;studySession.lessonWriteVariant=0;studySession.resetLessonTiles();studySession.mask=0;
                studySession.saveTrainingSession();render();
            });
        } else if(phase==LessonCurriculumLogic.Phase.BUILD){
            lessonPhaseHeader("Сборка",e);
            boolean complete=renderLessonBuild(e);
            mainAction=button(complete?"Продолжить":"Сначала собери фразу",complete,()->{
                if(studySession.lessonPicked>=studySession.lessonBuildRequired)studySession.advanceLessonBuild(e);
            });
            mainAction.setEnabled(complete);mainAction.setAlpha(complete?1f:0.5f);
            lessonBuildAction=mainAction;
        } else if(phase==LessonCurriculumLogic.Phase.WRITE){
            lessonPhaseHeader("Написание",e);
            renderLessonWriting();
            mainAction=button("Проверить",true,()->studySession.validateLessonWriting(e));
        } else {
            renderCompletion(e);
            return;
        }
        LinearLayout footer=column();footer.setPadding(dp(24),dp(12),dp(24),dp(22));
        footer.setBackgroundColor(CARD);
        footer.addView(mainAction,new LinearLayout.LayoutParams(-1,dp(58)));
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
    }

    private String lessonFullReference(LocalStore.Entry e){
        if(e.lines().isEmpty())return e.reference;
        int book=studySession.bookForEntry(e);
        if(book<1)return e.reference==null?e.title:e.reference;
        String a=e.lines().get(studySession.activeStep.start).ref;
        String b=e.lines().get(studySession.activeStep.end-1).ref;
        try{
            String[] first=a.split(":",2),last=b.split(":",2);
            return Bible.reference(book,Integer.parseInt(first[0]),Integer.parseInt(first[1]),
                Integer.parseInt(last[0]),Integer.parseInt(last[1]));
        }catch(Exception ex){return e.reference;}
    }
    /** Celebratory page: the approved crown/check composition in the same warm scenic UI. */
    private void renderCompletion(LocalStore.Entry e){
        root=column();root.setBackground(pageBackground());
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
        LinearLayout central=column();central.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);
        central.setPadding(dp(16),dp(16),dp(16),dp(12));
        scenery.addView(central,new FrameLayout.LayoutParams(-1,-1));
        root.addView(scenery,new LinearLayout.LayoutParams(-1,0,1));
        TextView crown=txt("👑",34,GOLD,true);crown.setGravity(Gravity.CENTER);
        central.addView(crown,new LinearLayout.LayoutParams(dp(75),dp(65)));
        TextView reference=displayText(lessonFullReference(e),21);
        reference.setGravity(Gravity.CENTER);reference.setPadding(dp(14),dp(6),dp(14),dp(6));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,-2);
        rp.topMargin=dp(12);central.addView(reference,rp);
        TextView heading=displayText(studySession.groupMode?"Связка освоена!":"Стих освоен!",38);
        heading.setGravity(Gravity.CENTER);heading.setTextColor(0xFF35452E);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.topMargin=dp(14);central.addView(heading,hp);
        SuccessArt art=new SuccessArt();
        int availableArt=Math.min(270,Math.max(140,
            Math.round(getResources().getDisplayMetrics().heightPixels /
                getResources().getDisplayMetrics().density)-410));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(availableArt));
        ap.topMargin=dp(10);central.addView(art,ap);
        LinearLayout footer=column();footer.setPadding(dp(24),dp(14),dp(24),dp(25));
        TextView continueButton=button("Продолжить",true,()->studySession.advanceAfterSuccess(e));
        continueButton.setTextSize(20);
        footer.addView(continueButton,new LinearLayout.LayoutParams(-1,dp(61)));
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
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

    /** Finish all internal bridges and complete-passage checks before advancing to the next independent entry. */

    /** The hotbar is an observer only. It never advances or changes lesson exercises. */

    /** Compact three-part task trail matching the approved visual reference. */
    private void renderLessonHotbar(LinearLayout top,LessonCurriculumLogic.Phase phase,
                                    LocalStore.Entry e){
        lessonHotbarCells.clear();
        final LessonHotbarLogic.Model model=studySession.lessonHotbarModel(phase,e);
        LinearLayout bar=row();bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(9),dp(7),dp(8),dp(7));
        bar.setBackground(shape(0xFFFFF8EE,17));
        LinearLayout strip=row();strip.setGravity(Gravity.CENTER_VERTICAL);
        String[] names={"Ознакомление","Сборка","Письмо"};
        TextView currentCell=null;
        for(int group=0;group<3;group++){
            if(group>0){
                View separator=new View(this);
                separator.setBackgroundColor(0xFFD6CEBF);
                LinearLayout.LayoutParams spl=new LinearLayout.LayoutParams(dp(1),dp(26));
                spl.leftMargin=dp(5);spl.rightMargin=dp(5);
                strip.addView(separator,spl);
            }
            LinearLayout section=column();section.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout headingRow=row();headingRow.setGravity(Gravity.CENTER);
            View glyph=new LessonHotbarIcon(group,0xFF58714C);
            LinearLayout.LayoutParams glyphP=new LinearLayout.LayoutParams(dp(12),dp(12));
            glyphP.rightMargin=dp(4);headingRow.addView(glyph,glyphP);
            TextView heading=txt(names[group],10,
                group==model.activePhase?0xFF344B2D:0xFF52684C,
                group==model.activePhase);
            heading.setSingleLine(true);
            headingRow.addView(heading,new LinearLayout.LayoutParams(-2,-2));
            section.addView(headingRow,new LinearLayout.LayoutParams(-2,dp(17)));
            // A thin rounded bracket groups all tasks under one phase label.
            View bracket=new LessonHotbarBracket(0xFF9BA878);
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(6));
            bp.topMargin=dp(1);
            section.addView(bracket,bp);
            LinearLayout cells=row();cells.setGravity(Gravity.CENTER);
            List<TextView> groupCells=new ArrayList<>();
            for(int task=0;task<model.totals[group];task++){
                int number=model.ordinal(group,task);
                TextView cell=txt(String.valueOf(number),11,MUTED,true);
                cell.setGravity(Gravity.CENTER);
                cell.setIncludeFontPadding(false);
                cell.setContentDescription(names[group]+", задание "+(task+1));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(24),dp(27));
                if(task>0)cp.leftMargin=dp(3);
                cells.addView(cell,cp);
                int visual=model.visual(group,task);
                paintLessonHotbarCell(cell,visual,number,false);
                if(visual==LessonHotbarLogic.CURRENT)currentCell=cell;
                groupCells.add(cell);
            }
            section.addView(cells,new LinearLayout.LayoutParams(-2,dp(27)));
            strip.addView(section,new LinearLayout.LayoutParams(-2,-2));
            lessonHotbarCells.add(groupCells);
        }
        HorizontalScrollView viewport=new HorizontalScrollView(this);
        viewport.setHorizontalScrollBarEnabled(false);
        viewport.setFillViewport(false);
        viewport.setOverScrollMode(View.OVER_SCROLL_NEVER);
        viewport.addView(strip,new android.widget.FrameLayout.LayoutParams(-2,-2));
        bar.addView(viewport,new LinearLayout.LayoutParams(0,-2,1));
        lessonHotbarCounter=txt(model.currentNumber+" / "+model.total,11,0xFF4C623E,true);
        lessonHotbarCounter.setGravity(Gravity.CENTER);
        lessonHotbarCounter.setSingleLine(true);
        lessonHotbarCounter.setPadding(dp(8),dp(6),dp(8),dp(6));
        lessonHotbarCounter.setBackground(shape(0xFFF0EEDD,22));
        LinearLayout.LayoutParams totalP=new LinearLayout.LayoutParams(-2,-2);
        totalP.leftMargin=dp(7);
        bar.addView(lessonHotbarCounter,totalP);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(3);
        top.addView(bar,lp);
        final TextView center=currentCell;
        if(center!=null)viewport.post(()->{
            int left=0;View node=center;
            while(node!=strip && node.getParent() instanceof View){
                left+=node.getLeft();node=(View)node.getParent();
            }
            int scrollX=Math.max(0,left-viewport.getWidth()/2+dp(12));
            viewport.scrollTo(scrollX,0);
        });
    }
    /** Small monochrome book / puzzle / pencil icons independent of emoji fonts. */
    private final class LessonHotbarIcon extends View{
        private final int stage,ink;
        private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        LessonHotbarIcon(int stage,int ink){
            super(MainActivity.this);this.stage=stage;this.ink=ink;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            canvas.save();
            canvas.translate(getWidth()/2f,getHeight()/2f);
            float scale=Math.min(getWidth(),getHeight())/24f;
            canvas.scale(scale,scale);
            pen.setColor(ink);pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeWidth(2.1f);pen.setStrokeJoin(Paint.Join.ROUND);
            pen.setStrokeCap(Paint.Cap.ROUND);
            Path path=new Path();
            if(stage==0){
                path.moveTo(0,-8);path.cubicTo(-4,-11,-8,-10,-11,-8);
                path.lineTo(-11,9);path.cubicTo(-6,7,-2,8,0,10);
                path.cubicTo(2,8,6,7,11,9);path.lineTo(11,-8);
                path.cubicTo(8,-10,4,-11,0,-8);path.close();
                canvas.drawPath(path,pen);canvas.drawLine(0,-8,0,10,pen);
            }else if(stage==1){
                path.moveTo(-10,-9);path.lineTo(-4,-9);
                path.cubicTo(-4,-5,3,-5,3,-9);path.lineTo(10,-9);
                path.lineTo(10,-3);path.cubicTo(6,-3,6,4,10,4);
                path.lineTo(10,10);path.lineTo(3,10);
                path.cubicTo(3,6,-4,6,-4,10);path.lineTo(-10,10);
                path.lineTo(-10,3);path.cubicTo(-6,3,-6,-3,-10,-3);
                path.close();canvas.drawPath(path,pen);
            }else{
                path.moveTo(-9,6);path.lineTo(-7,10);path.lineTo(-3,8);
                path.lineTo(10,-5);path.lineTo(6,-9);path.close();
                canvas.drawPath(path,pen);
                canvas.drawLine(4,-7,8,-3,pen);
            }
            canvas.restore();
        }
    }
    private final class LessonHotbarBracket extends View{
        private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
        LessonHotbarBracket(int color){
            super(MainActivity.this);
            stroke.setColor(color);stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1));stroke.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight(),r=dp(5);
            if(w<=2*r)return;
            Path p=new Path();p.moveTo(dp(1),h);
            p.quadTo(dp(1),dp(1),r,dp(1));
            p.lineTo(w-r,dp(1));
            p.quadTo(w-dp(1),dp(1),w-dp(1),h);
            canvas.drawPath(p,stroke);
        }
    }
    private void paintLessonHotbarCell(TextView cell,int visual,int number,boolean animated){
        Object old=cell.getTag();
        if(old instanceof Integer && ((Integer)old)==visual)return;
        cell.setTag(visual);
        if(visual==LessonHotbarLogic.DONE){
            cell.setText("✓");
            cell.setTextColor(Color.WHITE);
            cell.setBackground(shape(0xFF7E9A60,8));
            cell.setContentDescription("Задание "+number+" выполнено");
            if(animated && ValueAnimator.areAnimatorsEnabled()){
                cell.animate().cancel();cell.setScaleX(.88f);cell.setScaleY(.88f);
                cell.animate().scaleX(1f).scaleY(1f).setDuration(180L).start();
            }
        }else if(visual==LessonHotbarLogic.CURRENT){
            cell.setText(String.valueOf(number));
            cell.setTextColor(0xFFAD654A);
            cell.setBackground(outlined(0xFFFFF8EE,9,0xFFBA765A));
            cell.setContentDescription("Текущее задание "+number);
        }else{
            cell.setText(String.valueOf(number));
            cell.setTextColor(0xFF796B59);
            cell.setBackground(shape(0xFFF0E7DA,9));
            cell.setContentDescription("Будущее задание "+number);
        }
    }
    /** Live updates only the hotbar; typing, tiles and text are not recreated. */
    private void refreshLessonPhaseProgress(LessonCurriculumLogic.Phase phase,LocalStore.Entry e){
        if(lessonHotbarCells.size()!=3||lessonHotbarCounter==null)return;
        LessonHotbarLogic.Model model=studySession.lessonHotbarModel(phase,e);
        lessonHotbarCounter.setText(model.currentNumber+" / "+model.total);
        for(int g=0;g<3;g++){
            List<TextView> cells=lessonHotbarCells.get(g);
            if(cells.size()!=model.totals[g])continue;
            for(int i=0;i<cells.size();i++)
                paintLessonHotbarCell(cells.get(i),model.visual(g,i),model.ordinal(g,i),true);
        }
    }

    private void lessonPhaseHeader(String stage,LocalStore.Entry e){
        TextView pill=txt(stage,15,0xFFAA563F,true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(17),dp(8),dp(17),dp(8));
        pill.setBackground(shape(0xFFF9E1D2,24));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,-2);
        pp.gravity=Gravity.CENTER_HORIZONTAL;pp.topMargin=dp(9);
        content.addView(pill,pp);
        addLessonReference(e);
    }
    private void addLessonReference(LocalStore.Entry e){
        String ref=lessonFullReference(e);
        int book=studySession.bookForEntry(e);
        int ink=book<1?GREEN:bookInk(book);
        TextView cite=displayText(ref,20);
        cite.setTextColor(ink);cite.setGravity(Gravity.CENTER);
        add(content,cite,17);
    }

    private void renderLessonIntro(){
        activeTextArea=content;
        activePassage=prose("",22);
        activePassage.setLineSpacing(dp(7),1.18f);
        activePassage.setTextIsSelectable(false);
        add(content,activePassage,19);
        activeStatus=txt("",14,MUTED,false);
        add(content,activeStatus,15);
        studySession.trainingMode=studySession.mask>0?StudySessionController.TrainingMode.HIDE:StudySessionController.TrainingMode.READ;
        updateBlurText();
        TextView hide=button(studySession.mask==100?"Показать все слова":
            "Скрыть "+(studySession.mask+25)+"% слов",false,()->{});
        hide.setOnClickListener(v->{
            studySession.mask=studySession.mask>=100?0:studySession.mask+25;
            studySession.trainingMode=studySession.mask>0?StudySessionController.TrainingMode.HIDE:StudySessionController.TrainingMode.READ;
            updateBlurText();
            refreshLessonPhaseProgress(LessonCurriculumLogic.Phase.INTRO,studySession.activeEntry);
            hide.setText(studySession.mask==100?"Показать все слова":"Скрыть "+(studySession.mask+25)+"% слов");
            studySession.saveTrainingSession();
        });
        add(content,hide,27);
        // No extra instructional caption: gesture behavior remains unchanged.
    }

    /** Replacing hidden tokens keeps the original measured line widths. No reflow on tap. */
    private static final class HiddenWordSpan extends ReplacementSpan {
        @Override public int getSize(Paint paint,CharSequence text,int start,int end,
                                     Paint.FontMetricsInt metrics){
            return Math.max(1,Math.round(paint.measureText(text,start,end)));
        }
        @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,
                                   int top,int baseline,int bottom,Paint paint){
            int color=paint.getColor();float width=paint.measureText(text,start,end);
            paint.setColor(0xFFBDA490);
            float height=(bottom-top)*0.22f;
            canvas.drawRoundRect(x,baseline-height,x+width,baseline+height,6f,6f,paint);
            paint.setColor(color);
        }
    }
    private CharSequence buildPromptText(String[] tokens,int[] missing,int count){
        android.text.SpannableStringBuilder sb=new android.text.SpannableStringBuilder();
        for(int i=0;i<tokens.length;i++){
            if(i>0)sb.append(' ');
            int start=sb.length();sb.append(tokens[i]);
            int at=Arrays.binarySearch(missing,i);
            if(at>=count && at>=0)
                sb.setSpan(new HiddenWordSpan(),start,sb.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }
    private int lessonMaxTiles(){
        android.util.DisplayMetrics d=getResources().getDisplayMetrics();
        float available=Math.max(280,d.widthPixels/d.density);
        // Fixed for the current screen, based on available width rather than a global verse cutoff.
        return Math.max(10,Math.min(28,Math.round(available/20f)));
    }

    private void updateLessonBuildView(String[] words,int[] missing){
        if(lessonBuildPrompt!=null)lessonBuildPrompt.setText(buildPromptText(words,missing,studySession.lessonPicked));
        if(lessonBuildCounter!=null)lessonBuildCounter.setText(studySession.lessonPicked+" / "+missing.length+" слов");
        if(lessonBuildError!=null)lessonBuildError.setText(studySession.lessonWrongTile>=0?
            "Неверное слово. Попробуй другое.":"");
        for(int i=0;i<lessonBuildTiles.size();i++){
            TextView tile=lessonBuildTiles.get(i);
            int idx=(Integer)tile.getTag();
            tile.setVisibility(studySession.lessonUsedTiles.contains(idx)?View.INVISIBLE:View.VISIBLE);
            boolean wrong=idx==studySession.lessonWrongTile;
            tile.setTextColor(wrong?RED:WHITE);
            tile.setBackground(outlined(wrong?0xFFF8D6D2:CARD_ALT,12,wrong?RED:BORDER));
        }
        if(lessonBuildAction!=null){
            boolean done=studySession.lessonPicked==missing.length;
            lessonBuildAction.setText(done?"Продолжить":"Сначала собери фразу");
            lessonBuildAction.setEnabled(done);lessonBuildAction.setAlpha(done?1f:0.5f);
        }
    }
    /** Choose a balanced random mask once per exercise, never once per screen redraw. */

    private boolean renderLessonBuild(LocalStore.Entry e){
        lessonBuildAction=null;lessonBuildTiles.clear();
        List<String> src=new ArrayList<>();
        for(Bible.Verse v:e.lines().subList(studySession.activeStep.start,studySession.activeStep.end))src.add(v.text);
        List<String[]> chunks=studySession.lessonChunks(src);
        if(chunks.isEmpty())return true;
        studySession.lessonChunk=Math.min(studySession.lessonChunk,chunks.size()-1);
        String[] tokens=chunks.get(studySession.lessonChunk);
        List<LessonCurriculumLogic.Variant> variants=LessonCurriculumLogic.variants(
            ReviewLogic.words(PassageText.join(e.lines().subList(studySession.activeStep.start,studySession.activeStep.end))).size());
        studySession.lessonVariant=Math.min(studySession.lessonVariant,variants.size()-1);
        LessonCurriculumLogic.Variant kind=variants.get(studySession.lessonVariant);
        // The exercise itself illustrates the task. Omit redundant task/fragment captions.
        int[] missing=studySession.lessonMissingPositions(tokens,kind);
        studySession.lessonBuildRequired=missing.length;
        if(studySession.lessonPicked>missing.length || studySession.lessonUsedTiles.size()!=studySession.lessonPicked){
            studySession.lessonPicked=0;studySession.lessonUsedTiles.clear();
        }
        lessonBuildPrompt=txt("",22,WHITE,false);
        lessonBuildPrompt.setLineSpacing(dp(8),1.13f);
        add(content,lessonBuildPrompt,22);
        lessonBuildCounter=txt("",13,GREEN,true);add(content,lessonBuildCounter,17);
        WrapLayout choices=new WrapLayout(this,dp(9));
        int[] shuffled=WordOrderLogic.shuffledIndices(missing.length,studySession.lessonSeed);
        for(int index:shuffled){
            final int pressed=index;
            final int targetPos=missing[index];
            String caption=tokens[targetPos].replaceAll("^[^\\p{L}\\p{Nd}]+|[^\\p{L}\\p{Nd}]+$", "");
            TextView tile=wordTile(caption,CARD_ALT,WHITE,BORDER,()->{
                // The target and bank occupy fixed views. Never call render() on a tile tap.
                if(studySession.lessonUsedTiles.contains(pressed)||studySession.lessonPicked>=missing.length)return;
                int wanted=missing[studySession.lessonPicked];
                if(WordOrderLogic.comparable(tokens[wanted]).equals(WordOrderLogic.comparable(tokens[targetPos]))){
                    studySession.lessonPicked++;studySession.lessonUsedTiles.add(pressed);studySession.lessonWrongTile=-1;
                }else studySession.lessonWrongTile=pressed;
                updateLessonBuildView(tokens,missing);
                refreshLessonPhaseProgress(LessonCurriculumLogic.Phase.BUILD,e);
                studySession.saveTrainingSession();
            });
            tile.setTag(index);lessonBuildTiles.add(tile);choices.addView(tile);
        }
        add(content,choices,26);
        lessonBuildError=txt("",14,RED,false);add(content,lessonBuildError,12);
        updateLessonBuildView(tokens,missing);
        refreshLessonPhaseProgress(LessonCurriculumLogic.Phase.BUILD,e);
        return studySession.lessonPicked==missing.length;
    }

    private LinearLayout lessonErrorArea;

    /** Partial writing is edited directly in the sentence, not in a separate answer field. */
    private void renderLessonWriting(){
        List<LessonCurriculumLogic.Variant> variants=studySession.writingVariants(studySession.activeEntry);
        studySession.lessonWriteVariant=Math.min(studySession.lessonWriteVariant,variants.size()-1);
        LessonCurriculumLogic.Variant variant=variants.get(studySession.lessonWriteVariant);
        boolean full=variant==LessonCurriculumLogic.Variant.FULL;

        // The writing step does not need a separate task-number label.
        if(studySession.practiceWeakFragment){
            // Weak-fragment practice remains separate from the final independent check.
        }else if(full){
            // The verse reference remains visible while the full text is hidden.
        }else{
            renderInlineGapInputs(variant);
        }
        if(full){
            EditText answer=input(studySession.practiceWeakFragment?"Воспроизведи проблемную фразу…":"Напиши стих полностью…",true);
            forbidKeyboardHints(answer,true);
            answer.setMinLines(6);answer.setText(studySession.draft);
            answer.addTextChangedListener(new TextWatcher(){
                @Override public void beforeTextChanged(CharSequence x,int a,int b,int c){}
                @Override public void onTextChanged(CharSequence x,int a,int before,int count){
                    studySession.draft=x.toString();studySession.lastAccuracy=-1;
                    if(lessonErrorArea!=null)lessonErrorArea.removeAllViews();
                    studySession.saveTrainingSession();
                }
                @Override public void afterTextChanged(Editable x){}
            });
            add(content,answer,18);
        }
        lessonErrorArea=column();add(content,lessonErrorArea,12);
        if(pendingLessonFeedback){
            pendingLessonFeedback=false;showWritingErrors(studySession.activeEntry,studySession.lastExpected,studySession.draft,true);
        }
        // Verification is always explicitly triggered by the existing footer button.
    }
    private void renderInlineGapInputs(LessonCurriculumLogic.Variant variant){
        String source=PassageText.join(studySession.activeEntry.lines().subList(studySession.activeStep.start,studySession.activeStep.end));
        String[] tokens=source.trim().split("\\s+");
        int[] missing=studySession.lessonMissingPositions(tokens,variant);
        // A separator marks independently editable gaps, preserving spaces inside one accepted variant.
        studySession.inlineDrafts=InlineGapLogic.restore(studySession.draft,missing.length);
        WrapLayout inline=new WrapLayout(this,dp(6));
        inline.setPadding(0,dp(12),0,dp(12));
        int maxWidth=getResources().getDisplayMetrics().widthPixels-dp(58);
        inlineFields.clear();studySession.inlineExpected.clear();
        List<EditText> fields=inlineFields;
        for(int i=0;i<tokens.length;i++){
            String token=tokens[i];
            int match=Arrays.binarySearch(missing,i);
            if(match<0){
                TextView item=txt(token,19,WHITE,false);
                item.setPadding(dp(1),dp(8),dp(1),dp(8));inline.addView(item);
                continue;
            }
            final int gapIndex=match;
            final String expected=InlineGapLogic.core(token);
            studySession.inlineExpected.add(expected);
            EditText field=new EditText(this);
            field.setTextColor(WHITE);field.setHintTextColor(MUTED);field.setTextSize(19);
            field.setSingleLine(true);forbidKeyboardHints(field,false);
            field.setPadding(dp(3),dp(6),dp(3),dp(6));
            field.setGravity(Gravity.CENTER_VERTICAL);
            // Android native underline: width is initially based on the original word,
            // but the input itself has no maximum length.
            field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
            field.setHint(" ");
            field.setText(studySession.inlineDrafts[gapIndex]);
            field.setSelectAllOnFocus(false);
            int initial=Math.max(dp(32),dp(12)*Math.max(1,InlineGapLogic.letters(expected))+dp(8));
            final int baseline=Math.min(initial,maxWidth);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(baseline,dp(43));
            field.setLayoutParams(params);field.setMinWidth(Math.min(initial,maxWidth));
            field.setContentDescription("Пропущенное слово "+(gapIndex+1));
            fields.add(field);inline.addView(field);
            String suffix=InlineGapLogic.suffix(token);
            if(!suffix.isEmpty()){
                TextView punctuation=txt(suffix,19,WHITE,false);
                punctuation.setPadding(0,dp(8),0,dp(8));inline.addView(punctuation);
            }
        }
        List<String> refsForBlanks=new ArrayList<>();
        for(Bible.Verse line:studySession.activeEntry.lines().subList(studySession.activeStep.start,studySession.activeStep.end))
            refsForBlanks.add(line.ref);
        final boolean special=AcceptedTextLogic.matthew513(studySession.activeEntry.reference,refsForBlanks);
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
                        while(next<fields.size() && !studySession.inlineDrafts[next].trim().isEmpty())next++;
                        if(next<fields.size())fields.get(next).requestFocus();
                        else {
                            field.clearFocus();
                            android.view.inputmethod.InputMethodManager keyboard=
                                (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                            if(keyboard!=null)keyboard.hideSoftInputFromWindow(field.getWindowToken(),0);
                        }
                        // The last space NEVER starts validation; only the footer button does.
                    }
                    studySession.inlineDrafts[gapIndex]=value;
                    field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
                    studySession.draft=InlineGapLogic.serialize(studySession.inlineDrafts);
                    float textPx=field.getPaint().measureText(value)+dp(25);
                    int baselineWidth = Math.min(maxWidth,
                        Math.max(dp(32), dp(12) * Math.max(1,
                            InlineGapLogic.letters(studySession.inlineExpected.get(gapIndex))) + dp(8)));
                    int width=Math.min(maxWidth,Math.max(baselineWidth,(int)Math.ceil(textPx)));
                    ViewGroup.LayoutParams lp=field.getLayoutParams();
                    if(lp.width!=width){lp.width=width;field.setLayoutParams(lp);}
                    if(lessonErrorArea!=null)lessonErrorArea.removeAllViews();
                    studySession.lastAccuracy=-1;studySession.saveTrainingSession();
                }
                @Override public void afterTextChanged(Editable x){}
            });
        }
        add(content,inline,18);
    }

    /** Third mockup: inline colored error markers followed by actionable corrections. */
    private void showModernErrors(LinearLayout target,String expected,String actual){
        LinearLayout panel=column();panel.setPadding(dp(16),dp(16),dp(16),dp(16));
        panel.setBackground(outlined(CARD,18,BORDER));
        add(panel,displayText("Разбор ответа",22),0);
        helper(panel,"Ошибочные места выделены красным. Исправь текст выше.");
        add(panel,typedFeedback(expected,actual),14);
        List<TypingDiffLogic.Issue> issues=TypingDiffLogic.issues(expected,actual);
        add(panel,txt("Ошибок: "+issues.size(),14,RED,true),14);
        int limit=Math.min(issues.size(),15);
        for(int i=0;i<limit;i++){
            TypingDiffLogic.Issue issue=issues.get(i);
            LinearLayout entry=column();entry.setPadding(dp(12),dp(10),dp(12),dp(10));
            entry.setBackground(shape(CARD_ALT,12));
            add(entry,txt(issue.label,13,MUTED,true),0);
            if(!issue.actual.isEmpty())add(entry,txt(issue.actual,16,RED,false),5);
            if(!issue.expected.isEmpty())add(entry,txt("Правильно: "+issue.expected,15,GREEN,true),5);
            add(panel,entry,9);
        }
        if(issues.size()>limit)helper(panel,"Ещё ошибок: "+(issues.size()-limit));
        add(target,panel,0);
    }

    private void showWritingErrors(LocalStore.Entry e,String expected,String actual,boolean detailed){
        if(lessonErrorArea==null)return;
        lessonErrorArea.removeAllViews();showModernErrors(lessonErrorArea,expected,actual);
        if(!detailed)return;
        final String weakPart=StudySessionController.weakPhrase(expected,actual);
        if(!weakPart.isEmpty())action(lessonErrorArea,"Потренировать ошибочный фрагмент",false,()->studySession.practiceFragment(weakPart));
        if(studySession.weakVerse>=0){
            add(lessonErrorArea,txt("Слабый участок: "+e.lines().get(studySession.weakVerse).ref+
                (studySession.weakTransitionFrom>=0?" (переход "+e.lines().get(studySession.weakTransitionFrom).ref+
                " → "+e.lines().get(studySession.weakVerse).ref+")":""),14,MUTED,false),10);
        }
    }

    // ===== Unified Collections (v0.21) =====

    private interface UnifiedCreated {void accept(LocalStore.UnifiedCollection collection);}
    private void newUnifiedCollection(UnifiedCreated after){
        EditText name=input("Например: Нагорная проповедь",false);
        LinearLayout wrap=column();wrap.setPadding(dp(19),dp(12),dp(19),dp(9));add(wrap,name,0);
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Новая коллекция").setView(wrap)
            .setPositiveButton("Создать",(d,w)->{
                if(value(name).isEmpty()){toast("Введите название коллекции");return;}
                LocalStore.UnifiedCollection u=collectionActions.createUnifiedCollection(value(name));
                if(after!=null)after.accept(u);else{collectionActions.openUnifiedId=u.id;screen=Screen.COLLECTION_DETAIL;render();}
            }).setNegativeButton("Отмена",null).create());
    }
    private void showUnifiedSelector(){
        List<LocalStore.UnifiedCollection> collections=store.collections().unifiedCollections();
        String[] items=new String[collections.size()+1];
        for(int i=0;i<collections.size();i++)items[i]=
            (collections.get(i).id==collectionActions.activeUnifiedId?"✓  ":"")+collections.get(i).title;
        items[collections.size()]="＋ Создать коллекцию";
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Выбрать коллекцию").setItems(items,(dialog,which)->{
                if(which==collections.size())newUnifiedCollection(u->{
                    collectionActions.activateUnified(u.id);screen=Screen.HOME;render();
                });
                else{collectionActions.activateUnified(collections.get(which).id);render();}
            }).setNegativeButton("Отмена",null).create());
    }
    private void renderUnifiedHomePanel(){
        LinearLayout panel=card(content);panel.setBackground(homeSurface());
        LocalStore.UnifiedCollection active=collectionActions.activeUnified();
        if(active==null){
            boolean empty=store.collections().unifiedCollections().isEmpty();
            add(panel,displayText(empty?"Пока нет коллекций":"Выбери коллекцию",22),0);
            if(!empty)action(panel,"Выбрать коллекцию",true,this::showUnifiedSelector);
            action(panel,"＋ Создать коллекцию",true,()->newUnifiedCollection(null));
            return;
        }
        // Compact collection switcher: two curved circular arrows, no chevron.
        LinearLayout chooser=row();chooser.setGravity(Gravity.CENTER_VERTICAL);
        chooser.setPadding(dp(12),dp(7),dp(10),dp(7));
        chooser.setBackground(outlined(CARD_ALT,13,BORDER));
        LinearLayout names=column();
        add(names,txt("ВЫБРАННАЯ КОЛЛЕКЦИЯ",10,MUTED,true),0);
        TextView title=txt(active.title,17,WHITE,true);
        title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        add(names,title,3);
        chooser.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        NavigationGlyph cycle=new NavigationGlyph("refresh",GREEN);
        cycle.setContentDescription("Сменить коллекцию");
        LinearLayout.LayoutParams cycleP=new LinearLayout.LayoutParams(dp(30),dp(30));
        cycleP.leftMargin=dp(10);chooser.addView(cycle,cycleP);
        chooser.setContentDescription("Выбранная коллекция: "+active.title+". Нажми, чтобы сменить.");
        chooser.setClickable(true);chooser.setFocusable(true);
        chooser.setOnClickListener(v->showUnifiedSelector());
        add(panel,chooser,0);
        int verses=0,learned=0;
        Set<String> seenVerses=new HashSet<>();
        for(LocalStore.Entry e:store.study().entries(active.groupId)){
            List<Bible.Verse> lines=e.lines();
            for(int i=0;i<lines.size();i++){
                String identity=StudySessionController.runVerseKey(e,lines.get(i));
                if(!seenVerses.add(identity))continue;
                verses++;
                if(store.progress().isStepMastered(e.id,"VERSE",i,i+1) ||
                    store.progress().exerciseDone(e.id,i)>=5)learned++;
            }
        }
        add(panel,txt(verses+" стихов · "+learned+" предварительно освоено",12,MUTED,false),8);
        if(verses==0){
            helper(panel,"Коллекция пуста. Добавь материал для изучения.");
            action(panel,"＋ Добавить из Библии",true,()->{
                collectionActions.clearInsertionContext();
                bibleReader.openBiblePicker(BibleReaderController.BibleTarget.STUDY_GROUP,active.groupId,Screen.HOME);
            });
            action(panel,"＋ Добавить свой текст",false,()->{collectionActions.customTargetGroupId=active.groupId;screen=Screen.CUSTOM;render();});
            return;
        }
        progress(panel,learned,verses);
        unifiedModeRow(panel,"Учить стихи наизусть","book",this::startActiveStudy);
        unifiedModeRow(panel,"Режим карточек","cards",this::startActiveCards);
        unifiedModeRow(panel,"Повторение стихов","brain",()->startActiveReview(false));
        unifiedModeRow(panel,"Проверка памяти","notebook",this::startActiveRun);
        unifiedModeRow(panel,"Повторить все коллекции","repeat",()->startActiveReview(true));
    }
    /** One tap launches a mode; no explanatory subtitles in this compact list. */
    private void unifiedModeRow(LinearLayout panel,String name,String icon,Runnable run){
        LinearLayout item=row();item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12),dp(11),dp(10),dp(11));
        item.setBackground(outlined(CARD,14,BORDER));
        NavigationGlyph symbol=new NavigationGlyph(icon,GREEN);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(27),dp(27));
        ip.rightMargin=dp(13);item.addView(symbol,ip);
        TextView nameView=txt(name,16,WHITE,true);
        item.addView(nameView,new LinearLayout.LayoutParams(0,-2,1));
        TextView go=txt("›",23,GREEN,false);go.setGravity(Gravity.CENTER);
        item.addView(go,new LinearLayout.LayoutParams(dp(22),dp(30)));
        item.setClickable(true);item.setFocusable(true);
        item.setContentDescription(name);
        item.setOnClickListener(v->run.run());
        add(panel,item,9);
    }
    private void startActiveStudy(){
        LocalStore.UnifiedCollection u=collectionActions.activeUnified();if(u==null){newUnifiedCollection(null);return;}
        if(bibleReader.bible.isReady())store.collections().syncUnifiedCollection(u.id,bibleReader.bible);
        if(store.study().entries(u.groupId).isEmpty()){collectionActions.openUnifiedId=u.id;screen=Screen.COLLECTION_DETAIL;render();
            toast("Добавь стихи в коллекцию");return;}
        collectionActions.selectedGroup=u.groupId;collectionActions.startStudyCollection(u.groupId);
    }
    private void startActiveCards(){
        LocalStore.UnifiedCollection u=collectionActions.activeUnified();if(u==null)return;
        if(bibleReader.bible.isReady())store.collections().syncUnifiedCollection(u.id,bibleReader.bible);
        flashcardSession.selectedDeck=u.deckId;flashcardSession.cardFromUnified=true;
        if(flashcardSession.eligibleSessionCards().isEmpty()){
            toast(!bibleReader.bible.isReady()?"Библия ещё загружается":"Сначала добавь стихи в коллекцию");return;
        }
        startCardSession();
    }
    private void startActiveReview(boolean all){
        LocalStore.UnifiedCollection u=collectionActions.activeUnified();
        if(u==null&&!all){toast("Выбери коллекцию");return;}
        studySession.reviewCollectionGroup=all?-1:u.groupId;
        studySession.startReviewSession();
    }
    private void renderUnifiedCollections(){
        // Prominent full-width search field: accent border, magnifier, larger input.
        LinearLayout searchBar=row();searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(14),dp(3),dp(8),dp(3));
        searchBar.setMinimumHeight(dp(58));
        GradientDrawable searchFrame=shape(0xFFFFFDF9,17);
        searchFrame.setStroke(dp(2),0xFFCE9072);
        searchBar.setBackground(searchFrame);
        searchBar.setElevation(dp(2));
        NavigationGlyph magnifier=new NavigationGlyph("search",GREEN);
        searchBar.addView(magnifier,new LinearLayout.LayoutParams(dp(29),dp(29)));
        EditText search=input("Поиск по коллекциям",false);
        search.setTextSize(17);
        search.setSingleLine(true);
        search.setPadding(dp(12),dp(9),dp(5),dp(9));
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setContentDescription("Найти коллекцию по названию");
        searchBar.addView(search,new LinearLayout.LayoutParams(0,-2,1));
        TextView clear=txt("×",24,MUTED,false);
        clear.setGravity(Gravity.CENTER);
        clear.setVisibility(View.GONE);
        clear.setContentDescription("Очистить поиск коллекций");
        searchBar.addView(clear,new LinearLayout.LayoutParams(dp(41),dp(44)));
        clear.setOnClickListener(v->search.setText(""));
        add(content,searchBar,2);
        LinearLayout results=column();add(content,results,12);
        renderUnifiedCollectionRows(results,"");
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                clear.setVisibility(s.length()==0?View.GONE:View.VISIBLE);
                results.removeAllViews();renderUnifiedCollectionRows(results,s.toString());
            }
            public void afterTextChanged(Editable s){}
        });
    }
    private void renderUnifiedCollectionRows(LinearLayout into,String search){
        boolean found=false;
        for(LocalStore.UnifiedCollection u:store.collections().unifiedCollections()){
            if(!u.title.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT)))continue;
            found=true;
            LinearLayout row=row();row.setPadding(dp(13),dp(14),dp(13),dp(14));
            row.setBackground(homeSurface());
            ImageView thumb=landscapeThumbnail(u.id);
            LinearLayout.LayoutParams imageP=new LinearLayout.LayoutParams(dp(59),dp(61));
            imageP.rightMargin=dp(13);row.addView(thumb,imageP);
            LinearLayout details=column();add(details,displayText(u.title,17),0);
            add(details,txt(u.entries+" отрывков · "+u.cards+" карточек",13,MUTED,false),5);
            if(u.id==collectionActions.activeUnifiedId)add(details,txt("●  Выбрана на главной",12,GREEN,true),4);
            row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=txt("›",22,GREEN,false);arrow.setGravity(Gravity.CENTER);
            row.addView(arrow,new LinearLayout.LayoutParams(dp(24),dp(48)));
            row.setClickable(true);row.setFocusable(true);
            row.setContentDescription("Открыть коллекцию "+u.title);
            row.setOnClickListener(v->{collectionActions.openUnifiedId=u.id;screen=Screen.COLLECTION_DETAIL;render();});
            add(into,row,9);
        }
        if(!found)helper(into,search.trim().isEmpty()?"Коллекций пока нет":"Ничего не найдено");
    }
    /** The one explicit location for collection management, inside its detail screen. */
    private void unifiedSettingRow(LinearLayout settings,String label,String glyph,boolean destructive,Runnable command){
        LinearLayout item=row();item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(8),dp(7),dp(5),dp(7));
        MenuGlyph symbol=new MenuGlyph(glyph,destructive?RED:GREEN);
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(dp(28),dp(28));
        iconP.rightMargin=dp(13);item.addView(symbol,iconP);
        TextView name=txt(label,16,destructive?RED:WHITE,true);
        item.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        item.setMinimumHeight(dp(51));
        item.setClickable(true);item.setFocusable(true);
        item.setContentDescription(label);item.setOnClickListener(v->command.run());
        if(settings.getChildCount()>1){
            View divider=new View(this);divider.setBackgroundColor(0xFFECD8CD);
            settings.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        }
        settings.addView(item,new LinearLayout.LayoutParams(-1,-2));
    }
    private void showUnifiedCollectionSettings(LocalStore.UnifiedCollection u){
        LinearLayout settings=column();settings.setBackground(homeSurface());
        settings.setPadding(dp(14),dp(12),dp(14),dp(11));
        add(settings,txt("НАСТРОЙКИ КОЛЛЕКЦИИ",12,MUTED,true),0);
        unifiedSettingRow(settings,"Переименовать","pencil",false,()->renameUnified(u));
        unifiedSettingRow(settings,"Настроить порядок стихов","shuffle",false,()->{
            if(store.study().entries(u.groupId).size()<2){
                toast("Для изменения порядка нужны хотя бы два отрывка");return;
            }
            collectionActions.selectedGroup=u.groupId;collectionActions.openUnifiedId=u.id;
            screen=Screen.REORDER_STUDY;render();
        });
        unifiedSettingRow(settings,"Очистить коллекцию","eraser",true,()->clearUnified(u));
        unifiedSettingRow(settings,"Удалить коллекцию","trash",true,()->deleteUnified(u));
        add(content,settings,14);
    }
    private void invalidateRemovedSessions(){
        if(!collectionActions.queueValid())collectionActions.clearCollectionQueue();
        if(studySession.selectedEntry>0&&!studySession.validSession()){
            studySession.selectedEntry=0;studySession.activeEntry=null;studySession.activeStep=null;
            studySession.checked=false;studySession.passed=false;studySession.reviewQueue.clear();
            studySession.reviewMode=false;studySession.groupMode=false;studySession.forcedStep=null;
            getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit().remove("review_pending").apply();
        }
        readyTabs.clear();
    }

    private void clearUnified(LocalStore.UnifiedCollection u){
        if(u.entries==0&&u.cards==0){toast("Коллекция уже пуста");return;}
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Очистить коллекцию?")
            .setMessage("Все стихи и отрывки будут удалены из этой коллекции. История их изучения сохранится")
            .setPositiveButton("Очистить",(d,w)->{
                try{collectionActions.clearUnifiedCollection(u.id);invalidateRemovedSessions();render();toast("Коллекция очищена");}
                catch(RuntimeException error){android.util.Log.e("Collections","Clear failed",error);toast("Не удалось очистить коллекцию");}
            }).setNegativeButton("Отмена",null).create());
    }
    private void renameUnified(LocalStore.UnifiedCollection u){
        EditText name=input("Название коллекции",false);name.setText(u.title);
        LinearLayout box=column();box.setPadding(dp(18),dp(10),dp(18),dp(8));add(box,name,0);
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Переименовать коллекцию").setView(box)
            .setPositiveButton("Сохранить",(d,w)->{
                if(value(name).isEmpty()){toast("Укажи название");return;}
                collectionActions.renameUnifiedCollection(u.id,value(name));render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void deleteUnified(LocalStore.UnifiedCollection u){
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Удалить коллекцию?")
            .setMessage("Коллекция и её содержимое будут удалены. Прогресс изучения стихов сохранится")
            .setPositiveButton("Удалить",(d,w)->{
                try{collectionActions.deleteUnifiedCollection(u.id);invalidateRemovedSessions();screen=Screen.COLLECTIONS;render();}
                catch(RuntimeException error){android.util.Log.e("Collections","Delete failed",error);toast("Не удалось удалить коллекцию");}
            }).setNegativeButton("Отмена",null).create());
    }
    private void renderUnifiedCollectionDetail(){
        LocalStore.UnifiedCollection u=store.collections().unifiedCollection(collectionActions.openUnifiedId);
        if(u==null){screen=Screen.COLLECTIONS;render();return;}
        collectionActions.selectedGroup=u.groupId;flashcardSession.selectedDeck=u.deckId;
        TextView heading=displayText(u.title,25);
        heading.setGravity(Gravity.CENTER);heading.setMaxLines(2);
        add(content,heading,5);
        showUnifiedCollectionSettings(u);
        helper(content,"Освоено: "+collectionActions.studyProgressFor(u.groupId));
        try{
            JSONArray history=new JSONArray(getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE)
                .getString("run_history_"+u.id,"[]"));
            if(history.length()>0){JSONObject last=history.getJSONObject(0);
                helper(content,"Последняя проверка памяти: "+last.optInt("correct")+
                    " из "+last.optInt("total")+" без ошибок");}
        }catch(JSONException ignored){}
        List<LocalStore.Entry> entries=store.study().entries(u.groupId);
        if(entries.isEmpty())helper(content,"Добавь первый стих или отрывок.");
        for(LocalStore.Entry entry:entries)entryCard(entry);
    }

    /** Fixed bottom actions, independent of the scrolling collection list. */
    private void addUnifiedCollectionFooter(){
        LinearLayout footer=column();
        footer.setPadding(dp(19),dp(9),dp(19),dp(10));
        footer.setBackgroundColor(BG);
        if(screen==Screen.COLLECTIONS||screen==Screen.CARDS){
            TextView addCollection=button("＋  Добавить коллекцию",true,()->newUnifiedCollection(null));
            footer.addView(addCollection,new LinearLayout.LayoutParams(-1,dp(52)));
        }else if(screen==Screen.COLLECTION_DETAIL){
            final LocalStore.UnifiedCollection u=store.collections().unifiedCollection(collectionActions.openUnifiedId);
            if(u==null)return;
            TextView addBible=button("＋  Добавить из Библии",true,()->{
                collectionActions.clearInsertionContext();
                bibleReader.openBiblePicker(BibleReaderController.BibleTarget.STUDY_GROUP,u.groupId,Screen.COLLECTION_DETAIL);
            });
            footer.addView(addBible,new LinearLayout.LayoutParams(-1,dp(51)));
            TextView own=button("＋  Добавить свой текст",false,()->{
                collectionActions.customTargetGroupId=u.groupId;screen=Screen.CUSTOM;render();
            });
            LinearLayout.LayoutParams ownP=new LinearLayout.LayoutParams(-1,dp(49));
            ownP.topMargin=dp(8);footer.addView(own,ownP);
        }else return;
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
    }

    // ===== Independent writing run: diagnostic only, never modifies lesson/review tables. =====

    private void startActiveRun(){
        LocalStore.UnifiedCollection u=collectionActions.activeUnified();if(u==null)return;
        studySession.buildRunLines(u);
        if(studySession.runLines.isEmpty()){
            toast("В коллекции пока нет стихов, закреплённых отложенным повторением");return;
        }
        studySession.runCollectionId=u.id;
        android.content.SharedPreferences p=getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE);
        if(p.getBoolean(studySession.runKey(u.id)+"active",false)){
            styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                .setTitle("Незавершённая проверка памяти")
                .setMessage("Продолжить с сохранённого стиха или начать проверку заново?")
                .setPositiveButton("Продолжить",(d,w)->{studySession.restoreRunSession();screen=Screen.RUN;render();})
                .setNegativeButton("Сначала",(d,w)->{studySession.resetRun();screen=Screen.RUN;render();}).create());
        }else{studySession.resetRun();screen=Screen.RUN;render();}
    }

    private void renderWritingRun(){
        if(!studySession.validRun()){studySession.runLines.clear();screen=Screen.HOME;render();return;}
        if(studySession.runLines.isEmpty()){screen=Screen.HOME;render();return;}
        studySession.runCursor=Math.min(studySession.runCursor,studySession.runLines.size()-1);
        StudySessionController.RunLine line=studySession.runLines.get(studySession.runCursor);
        root=column();root.setBackground(pageBackground());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout top=row();top.setPadding(dp(17),dp(12),dp(17),dp(7));
        TextView exit=txt("×",30,WHITE,false);exit.setGravity(Gravity.CENTER);
        exit.setContentDescription("Сохранить проверку памяти и выйти");
        exit.setOnClickListener(v->onBackPressed());
        top.addView(exit,new LinearLayout.LayoutParams(dp(43),dp(46)));
        TextView count=txt((studySession.runCursor+1)+" из "+studySession.runLines.size(),15,MUTED,true);
        count.setGravity(Gravity.CENTER);
        top.addView(count,new LinearLayout.LayoutParams(0,dp(45),1));
        root.addView(top,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        content=column();content.setPadding(dp(21),dp(18),dp(21),dp(24));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        progress(content,studySession.runCursor,studySession.runLines.size());
        TextView heading=displayText(line.reference,24);heading.setGravity(Gravity.CENTER);
        add(content,heading,20);
        TextView instruction=txt("Напиши весь стих по памяти",16,MUTED,false);
        instruction.setGravity(Gravity.CENTER);add(content,instruction,12);
        EditText write=input("Начни писать стих…",true);
        forbidKeyboardHints(write,true);write.setMinLines(5);
        write.setText(studySession.runDraft);write.setSelection(write.length());
        write.setEnabled(!studySession.runChecked);
        write.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){
                studySession.runDraft=s.toString();studySession.saveRunSession();
            }
            public void afterTextChanged(Editable e){}
        });
        add(content,write,22);
        if(studySession.runChecked){
            add(content,txt(studySession.runPassed?"✓ Без ошибок":"Обнаружены ошибки",20,
                studySession.runPassed?SUCCESS:RED,true),16);
            if(!studySession.runPassed){
                add(content,typedFeedback(line.text,studySession.runDraft),9);
                label(content,"Правильный текст");add(content,prose(line.text,17),8);
            }
        }
        LinearLayout footer=column();footer.setPadding(dp(20),dp(10),dp(20),dp(20));
        footer.setBackgroundColor(CARD);
        TextView go=button(studySession.runChecked?"Следующий стих": "Проверить",true,()->{
            if(studySession.runChecked){studySession.finishRunLine();return;}
            if(studySession.runDraft.trim().isEmpty()){toast("Введи стих или выбери «Пропустить»");return;}
            studySession.runPassed=AcceptedTextLogic.exact(line.text,studySession.runDraft,false);
            studySession.runChecked=true;studySession.saveRunSession();render();
        });
        footer.addView(go,new LinearLayout.LayoutParams(-1,dp(55)));
        if(!studySession.runChecked){
            TextView skip=button("Пропустить стих",false,studySession::finishRunLine);
            footer.addView(skip,new LinearLayout.LayoutParams(-1,dp(47)));
        }
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);lastRenderedScreen=screen;
    }
    private void renderRunSummary(){
        title("Проверка памяти завершена",null);
        LinearLayout report=headerCard(content);
        add(report,txt(studySession.runCorrect+" из "+studySession.runLines.size()+" стихов без ошибок",22,WHITE,true),0);
        progress(report,studySession.runCorrect,studySession.runLines.size());
        if(!studySession.runMistakes.isEmpty()){
            label(content,"Требуют внимания");
            for(String ref:studySession.runMistakes)add(content,txt("• "+ref,16,RED,false),7);
        }else helper(content,"Все стихи воспроизведены без ошибок!");
        helper(content,"Результат проверки памяти не меняет статусы изучения и расписание повторений.");
        action(content,"На главную",true,()->{screen=Screen.HOME;render();});
    }

    private void exportFile() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"slovo-pamyat-backup.json");
        startActivityForResult(i,EXPORT);
    }
    private void importFile() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        startActivityForResult(i,IMPORT);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try {
            if(request==EXPORT) {
                byte[] json=store.backup().exportJson().toString(2).getBytes(StandardCharsets.UTF_8);
                try(OutputStream stream=getContentResolver().openOutputStream(uri)) {
                    if(stream==null)throw new IOException("Cannot write file");
                    stream.write(json);
                }
                toast("Копия сохранена");
            } else if(request==IMPORT) {
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                try(InputStream stream=getContentResolver().openInputStream(uri)) {
                    if(stream==null)throw new IOException("Cannot read file");
                    byte[] chunk=new byte[8192];int n;
                    while((n=stream.read(chunk))!=-1) {
                        if(buffer.size()+n>20_000_000)throw new IOException("Файл больше 20 МБ");
                        buffer.write(chunk,0,n);
                    }
                }
                store.backup().importJson(buffer.toString("UTF-8"));
                toast("Подборки импортированы");render();
            }
        }catch(Exception ex){new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setTitle("Ошибка файла")
            .setMessage(ex.getMessage()).setPositiveButton("OK",null).show();}
    }
    @Override protected void onDestroy(){
        settleBibleIcons();
        settleHomeIcons();
        settleCollectionsIcons();
        bibleReader.close();tabWarmHandler.removeCallbacksAndMessages(null);
        homeHandler.removeCallbacks(homeTicker);super.onDestroy();if(store!=null)store.close();
    }
}
