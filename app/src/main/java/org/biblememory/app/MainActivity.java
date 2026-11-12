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
    private enum Screen {HOME,BOOKS,CHAPTERS,CHAPTER,ENTRIES,CUSTOM,TRAIN,DUE,CARDS,CARD_DECK,CARD_PICK,BIBLE_PICKER,PICKER_BOOKS,PICKER_CHAPTERS,PICKER_VERSES,PICKER_TEXT,CARD_SESSION,REORDER_STUDY,REORDER_CARDS,COLLECTIONS,COLLECTION_DETAIL,RUN,RUN_SUMMARY}
    private Screen screen=Screen.HOME;
    private static final String UNIFIED_PREFS="unified_library_ui";
    private long activeUnifiedId=-1,openUnifiedId=-1;
    private boolean cardFromUnified=false;
    private long reviewCollectionGroup=-1;
    private static final class RunLine {
        final long entryId;final int position;final String reference,text;
        RunLine(long entryId,int position,String reference,String text){
            this.entryId=entryId;this.position=position;this.reference=reference;this.text=text;
        }
    }
    private final List<RunLine> runLines=new ArrayList<>();
    private long runCollectionId=-1;
    private int runCursor=0,runCorrect=0;
    private final List<String> runMistakes=new ArrayList<>();
    private String runDraft="";
    private boolean runChecked=false,runPassed=false;

    private LocalStore store;
    private final Bible bible=new Bible();
    private volatile String bibleError="";
    private int selectedBook=40,selectedChapter=5, mask=0,stepCursor=0;
    private int focusVerse=-1;
    private StudyPlanLogic.Stage studyStage=StudyPlanLogic.Stage.VERSE;
    private long selectedGroup=1,selectedEntry=0;
    private long customTargetGroupId=-1; // A text created via a collection menu belongs to that collection.
    private boolean checked=false,passed=false;
    private enum TrainingMode {READ, HIDE, ORDER, TYPE}
    private TrainingMode trainingMode=TrainingMode.READ;
    private int orderChunk=0, orderPosition=0, orderWrongTile=-1, orderMistakes=0;
    private int[] orderBankOrder=new int[0];
    private final List<Integer> usedWordTiles=new ArrayList<>();
    private boolean testAssisted=false;
    private ScrollView currentScroller;
    // The Home page is a single scroll surface; its expansion and scroll position survive navigation.
    private static final String HOME_UI_PREFS="home_ui";
    private boolean homeCollectionsExpanded=false;
    private int homeScrollY=0;
    private boolean homeScrollRestored=false;
    private Screen lastRenderedScreen=null;
    private ValueAnimator collectionAnimator;

    private String draft="",lastExpected="",lastTestRef="",lastTestStep="";
    private int homeIndex=0;
    private static final int[][] HOME_REFS={{19,118,11},{40,5,14},{43,14,6},{45,12,21},{50,4,13},{40,6,33}};
    private int lastAccuracy=-1;
    private LinearLayout content,root;
    private final Handler homeHandler=new Handler(Looper.getMainLooper());
    private final Runnable homeTicker=()->{refreshHomeIndex(true);scheduleHomeRefresh();};
    private LinearLayout activeTextArea,activeWorkArea;
    private TextView activePassage,activeStatus;
    private final TextView[] bottomModeButtons=new TextView[3];
    private static final String SESSION_PREFS="study_session";
    private LocalStore.Entry activeEntry;
    private StudyPlanLogic.Step activeStep;
    private boolean activeMastered;
    private boolean reviewMode=false,groupMode=false;
    private int returnVerseCursor=0;
    private StudyPlanLogic.Step forcedStep;
    private LocalStore.DueItem currentReview;
    private int blurSeed=new Random().nextInt();
    private boolean orderCompleted=false;
    // Three-phase fullscreen lesson: stable footer, adaptive word-tile exercises.
    private int lessonVariant=0,lessonChunk=0,lessonPicked=0,lessonWrongTile=-1;
    private long lessonSeed=new Random().nextLong();
    private final List<Integer> lessonUsedTiles=new ArrayList<>();
    private boolean lessonPracticeAgain=false,lessonWriteHadFeedback=false;
    private Runnable revealCleanup=()->{};
    private final List<LocalStore.DueItem> reviewQueue=new ArrayList<>();
    private int reviewPointer=0;
    private TextView lessonBuildPrompt,lessonBuildCounter,lessonBuildError,lessonBuildAction;
    private final List<TextView> lessonBuildTiles=new ArrayList<>();
    private int lessonBuildRequired=0;
    // v0.21.4: per-exercise hotbar. Only its views are updated during a lesson.
    private final List<List<TextView>> lessonHotbarCells=new ArrayList<>();
    private TextView lessonHotbarCounter;

    private int weakVerse=-1,weakTransitionFrom=-1;
    // One selection model shared by tapping verses and the chapter/verse range picker.
    private boolean selectingPassage=false; // legacy selection mode retained only for old saved preferences
    private int rangeStartChapter=-1,rangeStartVerse=-1,rangeEndChapter=-1,rangeEndVerse=-1;
    private boolean showBibleStatuses=true;
    private int lessonWriteVariant=0;
    private boolean groupBuildDone=false;
    private boolean groupIntroDone=false;
    private String[] inlineDrafts=new String[0];
    private final List<EditText> inlineFields=new ArrayList<>();
    private final List<String> inlineExpected=new ArrayList<>();


    private String bibleSearchQuery="";
    private String bibleSearchError="";
    private boolean bibleSearchActive=false,bibleSearchLoading=false;
    private List<BibleSearchLogic.Hit> bibleSearchHits=new ArrayList<>();

    private int searchVisibleLimit = 25;
    private int searchGeneration=0;
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
                || s == Screen.COLLECTIONS;
    }

    private void prepareOtherTabs() {
        final int generation = ++tabWarmGeneration;

        if (!isMainTab(screen) || !bible.isReady()) return;

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
                        bibleSearchActive &&
                        !bibleSearchLoading &&
                        bibleSearchError.isEmpty() &&
                        !bibleSearchHits.isEmpty()) {

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
                            root, content, scroll, homeIndex
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
    private long selectedDeck=-1;
    private final List<Long> cardSequence=new ArrayList<>();
    private int cardIndex=0;
    private boolean cardShowReference=true,cardRevealed=false;
    private final Random cardRandom=new Random();
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
    private boolean practiceWeakFragment=false;
    private String weakFragmentText="";
    // Preserve the mask of the current unfinished GAPS exercise across renders and restarts.
    private String rotationExerciseKey="",rotationMask="";
    private long rotationExerciseSeed=0L;






    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(CARD);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        store=new LocalStore(this);
        activeUnifiedId=getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE).getLong("active",-1);
        ensureActiveUnified();
        restoreCollectionQueue();
        showBibleStatuses=getSharedPreferences("bible_view",MODE_PRIVATE).getBoolean("verse_status",true);
        android.content.SharedPreferences homeUi=getSharedPreferences(HOME_UI_PREFS,MODE_PRIVATE);
        homeCollectionsExpanded=homeUi.getBoolean("collections_expanded",false);
        homeScrollY=Math.max(0,homeUi.getInt("scroll_y",0));
        refreshHomeIndex(false);
        restorePickerState(savedInstanceState);
        render();
        new Thread(()->{
            try { bible.load(getApplicationContext());
                try{BibleSearchLogic.prepare(getApplicationContext());}
                catch(Exception ignored){ /* Search failure never blocks Bible or collections. */ }
                for(LocalStore.UnifiedCollection u:store.unifiedCollections())try{
                    store.syncUnifiedCollection(u.id,bible);
                }catch(Exception ignored){ /* Preserve legacy rows for later recovery. */ }
            }
            catch(Exception ex) {bibleError=ex.getMessage()==null?"Невозможно открыть текст":ex.getMessage();}
            runOnUiThread(()->{if(screen==Screen.HOME||screen==Screen.BOOKS||screen==Screen.CHAPTERS||screen==Screen.CHAPTER||screen==Screen.CARD_PICK||screen==Screen.BIBLE_PICKER||isPickerScreen(screen)||screen==Screen.COLLECTIONS||screen==Screen.COLLECTION_DETAIL) render();});
        },"BibleLoader").start();
    }
    private boolean isPickerScreen(Screen value){
        return value==Screen.PICKER_BOOKS||value==Screen.PICKER_CHAPTERS||
            value==Screen.PICKER_VERSES||value==Screen.PICKER_TEXT||value==Screen.BIBLE_PICKER;
    }
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);
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
        if(store!=null){refreshHomeIndex(true);scheduleHomeRefresh();}
    }
    @Override protected void onPause() {
        revealCleanup.run();
        if(screen==Screen.TRAIN)saveTrainingSession();
        if(screen==Screen.CARD_SESSION)saveCardSession();
        if(screen==Screen.RUN)saveRunSession();
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
            case RUN:saveRunSession();screen=Screen.HOME;render();break;
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
                screen=passageSelection.active?Screen.BIBLE_PICKER:Screen.PICKER_BOOKS;render();break;
            case PICKER_VERSES:screen=Screen.PICKER_CHAPTERS;render();break;
            case PICKER_TEXT:screen=Screen.PICKER_VERSES;render();break;
            case CARD_SESSION:saveCardSession();screen=cardFromUnified?Screen.HOME:Screen.CARD_DECK;render();break;
            case CHAPTERS:screen=Screen.BOOKS;render();break;
            case CHAPTER:screen=Screen.CHAPTERS;render();break;
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
                reviewMode=false;groupMode=false;forcedStep=null;currentReview=null;
                reviewQueue.clear();reviewPointer=0;
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
        finishTabTransition();
        tabWarmGeneration++;
        readyTabs.clear();
        rememberHomeScroll();
        if(currentScroller!=null && lastRenderedScreen!=null && lastRenderedScreen!=Screen.HOME){
            savedScrollY.put(lastRenderedScreen,currentScroller.getScrollY());
        }
        if(collectionAnimator!=null){collectionAnimator.cancel();collectionAnimator=null;}
        if(screen==Screen.TRAIN){lastRenderedScreen=screen;renderFullscreenLesson();return;}
        if(screen==Screen.CARD_SESSION){lastRenderedScreen=screen;renderCardSession();return;}
        if(screen==Screen.RUN){lastRenderedScreen=screen;renderWritingRun();return;}
        for(int i=0;i<bottomModeButtons.length;i++)bottomModeButtons[i]=null;
        root=column();root.setBackground(pageBackground());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        currentScroller=scroll;
        content=column();content.setPadding(dp(19),dp(17),dp(19),dp(27));scroll.addView(content);
        if(screen==Screen.BOOKS||screen==Screen.PICKER_BOOKS)renderScrollingBibleSearch(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(screen) {
            case HOME: renderHome();break;
            case BOOKS: renderBooks();break;
            case CHAPTERS: renderChapters();break;
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
            case PICKER_BOOKS:renderBooks();break;
            case PICKER_CHAPTERS:renderChapters();break;
            case PICKER_VERSES:renderPickerVerseGrid();break;
            case PICKER_TEXT:renderPickerVerseText();break;
        }


        boolean showFullSearchResults =
                (screen == Screen.BOOKS ||
                        screen == Screen.PICKER_BOOKS) &&
                        bibleSearchActive &&
                        !bibleSearchLoading &&
                        bibleSearchError.isEmpty() &&
                        !bibleSearchHits.isEmpty();

        if (showFullSearchResults) {

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
                            scroll,
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
        if(!restoredPickerScroll && screen!=Screen.HOME && savedScrollY.containsKey(screen) &&
            !(screen==Screen.BOOKS&&bibleSearchActive) &&
            !(screen==Screen.PICKER_BOOKS&&bibleSearchActive) &&
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
    }
    /** Search is the first scrolling item, both in the catalogue and the search results. */
    private void renderScrollingBibleSearch(LinearLayout into){
        LinearLayout outer=column();outer.setBackgroundColor(BG);
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
        query.setText(bibleSearchQuery);query.setSelection(query.length());
        query.setContentDescription("Поиск по словам Библии");
        bar.addView(query,new LinearLayout.LayoutParams(0,-2,1));
        TextView erase=txt("×",24,MUTED,false);erase.setGravity(Gravity.CENTER);
        erase.setContentDescription("Очистить поиск по Библии");
        erase.setVisibility(bibleSearchQuery.isEmpty()?View.GONE:View.VISIBLE);
        bar.addView(erase,new LinearLayout.LayoutParams(dp(40),dp(45)));
        erase.setOnClickListener(v->{
            query.setText("");bibleSearchQuery="";searchGeneration++;
            bibleSearchActive=false;bibleSearchLoading=false;
            bibleSearchHits=new ArrayList<>();render();
        });
        // The magnifier submits too; the keyboard search action does the same.
        glass.setClickable(true);
        glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        glass.setContentDescription("Найти по Библии");
        glass.setOnClickListener(v->submitBibleSearch(query.getText().toString()));
        query.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH){
                submitBibleSearch(query.getText().toString());return true;
            }
            return false;
        });
        query.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                erase.setVisibility(s.length()==0?View.GONE:View.VISIBLE);
                if(s.length()==0 && (bibleSearchActive||bibleSearchLoading)){
                    searchGeneration++;bibleSearchActive=false;bibleSearchLoading=false;
                    bibleSearchQuery="";bibleSearchHits=new ArrayList<>();
                    query.post(()->render());
                }
            }
            public void afterTextChanged(Editable e){}
        });
        outer.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        into.addView(outer,new LinearLayout.LayoutParams(-1,-2));
    }

    private void submitBibleSearch(String query) {
        bibleSearchQuery =
                query == null ? "" : query.trim();

        final int generation = ++searchGeneration;

        if (bibleSearchQuery.isEmpty()) {
            bibleSearchActive = false;
            bibleSearchLoading = false;
            bibleSearchHits = new ArrayList<>();
            render();
            return;
        }

        if (!bible.isReady()) {
            toast("Библия ещё загружается");
            return;
        }

        bibleSearchActive = true;
        bibleSearchLoading = true;
        bibleSearchError = "";

        final String search = bibleSearchQuery;

        // Не пересоздаём экран до получения ответа.
        // Короткий поиск не будет мигать состоянием загрузки.
        new Thread(() -> {
            List<BibleSearchLogic.Hit> results;
            String error = "";

            try {
                // Все совпадения, без LIMIT 100.
                results = BibleSearchLogic.search(
                        bible, search
                );

            } catch (Exception ex) {
                results = new ArrayList<>();
                error = "Не удалось выполнить поиск.";
            }

            final List<BibleSearchLogic.Hit> ready = results;
            final String failure = error;

            runOnUiThread(() -> {
                if (generation != searchGeneration) return;

                bibleSearchLoading = false;
                bibleSearchHits = ready;
                bibleSearchError = failure;

                render();
            });

        }, "OfflineBibleSearch").start();
    }

    private CharSequence highlightSearchWords(String source,String query){
        android.text.SpannableString span=new android.text.SpannableString(source);
        String[] terms=BibleSearchLogic.normalized(query).split(" +");
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("[\\p{L}]+").matcher(source);
        while(m.find()){
            for(String term:terms){
                if(term.isEmpty())continue;
                if(BibleSearchLogic.allTermsMatch(term,m.group())){
                    span.setSpan(new BackgroundColorSpan(0xFFFFE4BD),m.start(),m.end(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
            }
        }
        return span;
    }


    private void renderSearchResults() {

        if (bibleSearchLoading) {
            helper(content, "Поиск...");
            return;
        }

        if (!bibleSearchError.isEmpty()) {
            helper(content, bibleSearchError);

            action(
                    content,
                    "Повторить поиск",
                    false,
                    () -> submitBibleSearch(bibleSearchQuery)
            );
            return;
        }

        if (bibleSearchHits.isEmpty()) {
            helper(
                    content,
                    "Совпадений не найдено. Попробуй другие слова."
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

        final List<BibleSearchLogic.Hit> hits =
                bibleSearchHits;

        final String query = bibleSearchQuery;
        // A ListView is used for large results. Its search field is a header so
        // the field scrolls away with result rows instead of remaining pinned.
        LinearLayout searchHeader=column();
        searchHeader.setPadding(dp(19),0,dp(19),0);
        renderScrollingBibleSearch(searchHeader);
        list.addHeaderView(searchHeader,null,false);
        // Показываем реальное число ВСЕХ результатов.
        TextView summary = txt(
                "Найдено стихов: " + hits.size(),
                14,
                MUTED,
                true
        );

        summary.setPadding(
                dp(19), dp(15),
                dp(19), dp(11)
        );

        list.addHeaderView(summary, null, false);

        list.setAdapter(new BaseAdapter() {

            @Override
            public int getCount() {
                return hits.size();
            }

            @Override
            public Object getItem(int position) {
                return hits.get(position);
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
                        hits.get(position);

                holder.reference.setText(hit.reference);
                holder.reference.setTextColor(
                        bookInk(hit.book)
                );

                // Подсветка выполняется только для
                // строк, которые сейчас видны.
                holder.verse.setText(
                        highlightSearchWords(
                                hit.text, query
                        )
                );

                holder.open.setOnClickListener(v -> {

                    selectedBook = hit.book;
                    selectedChapter = hit.chapter;

                    if(!isPickerScreen(screen))rememberChapter(hit.book,hit.chapter);

                    focusVerse = hit.verse;

                    boolean inPicker =
                            screen == Screen.PICKER_BOOKS ||
                            screen == Screen.BIBLE_PICKER;

                    bibleSearchActive = false;
                    bibleSearchQuery = "";
                    bibleSearchHits = new ArrayList<>();

                    if (inPicker) {
                        screen = Screen.PICKER_TEXT;
                    } else {
                        screen = Screen.CHAPTER;
                    }

                    render();
                });

                return result;
            }
        });

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
            NavigationGlyph symbol=new NavigationGlyph(glyphs[i],ink);
            LinearLayout.LayoutParams glyph=new LinearLayout.LayoutParams(dp(27),dp(27));
            glyph.gravity=Gravity.CENTER_HORIZONTAL;tab.addView(symbol,glyph);
            TextView name=txt(names[i],12,ink,i==current);
            name.setGravity(Gravity.CENTER);add(tab,name,1);
            tab.setClickable(true);tab.setFocusable(true);
            tab.setContentDescription("Открыть раздел "+names[i]);
            tab.setOnClickListener(v->goToMainTab(index));
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
    private int mainTabIndex(){
        switch(screen){
            case BOOKS:case CHAPTERS:case CHAPTER:return 1;
            case COLLECTIONS:case COLLECTION_DETAIL:case ENTRIES:case REORDER_STUDY:
            case CARDS:case CARD_DECK:case CARD_PICK:case CARD_SESSION:case REORDER_CARDS:return 2;
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
        if(isPickerScreen(screen)){
            passageSelection.cancel();clearInsertionContext();
            selectedBook=readerBookBeforePicker;selectedChapter=readerChapterBeforePicker;
        }
        // Tapping an already selected section from its details simply opens the root list.
        if(mainTabIndex()==index && !isMainTab(screen)){
            screen=destination;render();return;
        }

        final int oldIndex = mainTabIndex();
        final int direction = index > oldIndex ? 1 : -1;

        final Screen previousScreen = screen;
        final boolean leavingHasNav =
                screen != Screen.CUSTOM;

        if (screen == Screen.BIBLE_PICKER) {
            clearInsertionContext();
        }

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

            } else if (savedScrollY.containsKey(destination)) {

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


    /** Collection queue contains independent records; never links across entries/books. */
    private void restoreCollectionQueue(){
        android.content.SharedPreferences p=getSharedPreferences(COLLECTION_PREFS,MODE_PRIVATE);
        if(!p.getBoolean("active",false))return;
        collectionQueueGroup=p.getLong("group",-1);
        collectionQueueCursor=p.getInt("cursor",0);
        String ids=p.getString("ids","");
        for(String raw:ids.split(","))try{
            long id=Long.parseLong(raw);if(store.entry(id)!=null)collectionQueue.add(id);
        }catch(NumberFormatException ignored){}
        if(collectionQueue.isEmpty()||collectionQueueCursor>=collectionQueue.size())clearCollectionQueue();
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
        List<LocalStore.Entry> entries=store.entries(groupId);
        if(entries.isEmpty()){toast("Коллекция пока пустая");return;}
        cardStudyQueue.clear();cardStudyCursor=-1;cardStudyDeckId=-1;
        executingCollectionQueue=true;
        if(collectionQueueGroup==groupId&&collectionQueueCursor>=0&&
            collectionQueueCursor<collectionQueue.size()){
            long id=collectionQueue.get(collectionQueueCursor);
            if(store.entry(id)!=null){collectionQueueNewStep=false;openTraining(id);return;}
        }
        collectionQueue.clear();
        for(LocalStore.Entry e:entries)collectionQueue.add(e.id);
        collectionQueueGroup=groupId;collectionQueueCursor=0;
        for(int i=0;i<entries.size();i++){
            LocalStore.Entry item=entries.get(i);
            if(store.masteryCount(store.canonicalStudyEntryId(item.id))<item.lines().size() ||
                nextGroup(item)!=null){collectionQueueCursor=i;break;}
        }
        collectionQueueNewStep=true;
        persistCollectionQueue();openTraining(collectionQueue.get(collectionQueueCursor));
    }
    private boolean resumeCollectionTraining(){
        if(collectionQueueCursor<0||collectionQueueCursor>=collectionQueue.size())return false;
        executingCollectionQueue=true;
        long id=collectionQueue.get(collectionQueueCursor);
        if(store.entry(id)==null){clearCollectionQueue();return false;}
        collectionQueueNewStep=false;
        openTraining(id);return true;
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
        int all=0,mastered=0;
        for(LocalStore.Entry e:store.entries(groupId)){
            all+=e.lines().size();
            mastered+=store.masteryCount(store.canonicalStudyEntryId(e.id));
        }
        return mastered+" из "+all+" стихов";
    }

    private void chooseCardDeck(Pick action){
        List<LocalStore.UnifiedCollection> collections=store.unifiedCollections();
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
            else{openUnifiedId=u.id;screen=Screen.COLLECTION_DETAIL;render();}
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
        play.setOnClickListener(v->{selectedDeck=deck.id;startCardSession();});
        return play;
    }
    private void renderCardDecks(){
        tabWordmark();
        action(content,"＋  Создать подборку",true,()->newCardDeck(null));
        for(LocalStore.CardDeck deck:store.cardDecks()){
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
            panel.setOnClickListener(v->{selectedDeck=deck.id;screen=Screen.CARD_DECK;render();});
        }
    }

    /** The card menu and the study-collection menu share exactly the same popup styling. */
    private void showCardDeckActions(LocalStore.CardDeck deck,View anchor){
        String[] actions={"Изучить","Добавить свой текст","Переименовать","Удалить"};
        String[] icons={"study","file","pencil","trash"};
        showActionsMenu(anchor,actions,icons,which->{
            selectedDeck=deck.id;
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
                store.renameCardDeck(deck.id,value(name));render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void confirmDeleteCardDeck(LocalStore.CardDeck deck){
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Удалить подборку?")
            .setPositiveButton("Удалить",(d,w)->{
                store.deleteCardDeck(deck.id);clearSavedCardSession(deck.id);
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
                store.addCustomFlashcard(deckId,value(reference),value(verse));
                if(screen==Screen.CARD_DECK||screen==Screen.CARDS)render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void renderCardDeck(){
        LocalStore.CardDeck deck=store.cardDeck(selectedDeck);
        if(deck==null){screen=Screen.CARDS;render();return;}
        title(deck.title,null);
        List<LocalStore.Flashcard> cards=store.flashcards(selectedDeck);
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
                new LinearLayout.LayoutParams(-1, dp(52));

        addParams.topMargin = dp(9);

        footer.addView(addBible, addParams);

        // Добавляем панель вне ScrollView.
        root.addView(
                footer,
                new LinearLayout.LayoutParams(-1, -2)
        );
    }


    private void addBibleFooter(BibleTarget target,long groupId,Screen back){
        LinearLayout footer=column();footer.setPadding(dp(19),dp(8),dp(19),dp(10));
        footer.setBackgroundColor(BG);
        TextView add=button("＋  Добавить из Библии",true,()->openBiblePicker(target,groupId,back));
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

        greenBackground.setCornerRadius(dp(18));
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
                new LinearLayout.LayoutParams(-1, dp(54))
        );

        // Вторичная кнопка — добавление новых стихов.
        TextView addBible = button(
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
    private void openBiblePicker(BibleTarget target,long targetId,Screen back){
        readerBookBeforePicker=selectedBook;readerChapterBeforePicker=selectedChapter;
        passageSelection.cancel();passageSelection.book=0;passageSelection.collectionId=targetId;
        pickerTarget=target;pickerTargetId=targetId;pickerBackScreen=back;
        pickerRange=false;pickerHasAnchor=false;pickerChooseEnd=false;
        pickerVerseNumber=1;pickerRestoredScroll=-1;
        clearPassageSelection();focusVerse=-1;
        savedScrollY.remove(Screen.PICKER_BOOKS);
        savedScrollY.remove(Screen.PICKER_CHAPTERS);
        savedScrollY.remove(Screen.PICKER_VERSES);
        bibleSearchActive=false;bibleSearchQuery="";bibleSearchHits=new ArrayList<>();
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
        TextView start=button("Задать начало отрывка\n"+pickerBoundaryReference(false),
            passageSelection.startChapter>0,()->beginPickerBoundary(false));
        start.setMinHeight(dp(82));add(content,start,10);
        TextView end=button("Задать конец отрывка\n"+pickerBoundaryReference(true),
            passageSelection.endChapter>0,()->beginPickerBoundary(true));
        end.setMinHeight(dp(82));add(content,end,10);
        pickerSelectionLabel=prose("",18);add(content,pickerSelectionLabel,18);
        pickerSelectionPanel=column();pickerSelectionPanel.setPadding(dp(19),dp(8),dp(19),dp(8));
        pickerSelectionPanel.setBackground(outlined(CARD,16,BORDER));
        pickerSaveButton=button("Добавить отрывок",true,this::savePickerRange);
        add(pickerSelectionPanel,pickerSaveButton,0);updatePickerRangeSummary();
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
            "Конец раньше начала. Измените начало или конец отрывка.":"Выберите начало и конец отрывка.");
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
    private void scrollPickerGridTo(View row,Screen destination){
        ScrollView scroller=currentScroller;
        afterPickerLayout(row,()->{if(screen==destination&&currentScroller==scroller)
            scroller.scrollTo(0,Math.max(0,row.getTop()-dp(8)));});
    }
    /** Four-column number grid, identical to the chapters grid. */
    private void renderPickerVerseGrid(){
        if(bibleNotReady())return;
        List<Bible.Verse> verses=bible.chapter(selectedBook,selectedChapter);
        title(Bible.NAMES[selectedBook-1]+" "+selectedChapter,"Стихи");
        if(verses.isEmpty()){helper(content,"В главе нет стихов");return;}
        for(int base=1;base<=verses.size();base+=4){
            LinearLayout r=row();
            for(int k=base;k<base+4;k++){
                if(k>verses.size()){
                    r.addView(new View(this),new LinearLayout.LayoutParams(0,dp(50),1));
                    continue;
                }
                final int verse=k;
                int boundaryChapter=pickerChooseEnd?passageSelection.endChapter:passageSelection.startChapter;
                int boundaryVerse=pickerChooseEnd?passageSelection.endVerse:passageSelection.startVerse;
                boolean selected=passageSelection.active?
                    selectedChapter==boundaryChapter&&verse==boundaryVerse:verse==pickerVerseNumber;
                TextView b=button(String.valueOf(verse),selected,()->{
                    if(passageSelection.active){
                        passageSelection.setBoundary(pickerChooseEnd,selectedChapter,verse);
                        screen=Screen.BIBLE_PICKER;
                    }else{pickerVerseNumber=verse;focusVerse=verse;screen=Screen.PICKER_TEXT;}
                    render();
                });
                b.setPadding(dp(4),dp(4),dp(4),dp(4));
                b.setContentDescription((passageSelection.active?"Выбрать стих ":"Открыть текст стиха ")+verse+
                    (selected?", выбран":""));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);
                p.setMargins(dp(3),dp(4),dp(3),dp(4));r.addView(b,p);
            }
            add(content,r,2);
            if(passageSelection.active){
                int c=pickerChooseEnd?passageSelection.endChapter:passageSelection.startChapter;
                int v=pickerChooseEnd?passageSelection.endVerse:passageSelection.startVerse;
                if(selectedChapter==c&&v>=base&&v<base+4)scrollPickerGridTo(r,Screen.PICKER_VERSES);
            }
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
        LinearLayout navigation=row();
        navigation.addView(button("Главы · "+selectedChapter,false,()->{
            savedScrollY.remove(Screen.PICKER_CHAPTERS);screen=Screen.PICKER_CHAPTERS;render();
        }),new LinearLayout.LayoutParams(0,-2,1));
        navigation.addView(button("Стихи",false,()->{
            savedScrollY.remove(Screen.PICKER_VERSES);screen=Screen.PICKER_VERSES;render();
        }),new LinearLayout.LayoutParams(0,-2,1));
        add(content,navigation,4);
        action(content,"Задать диапазон",false,this::openPickerRange);
        final ScrollView scroller=currentScroller;
        final int targetVerse=focusVerse;focusVerse=-1;
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
            LinearLayout line=card(content);
            LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);
            TextView marker=txt(verse.ref,14,bookInk(book),true);
            head.addView(marker,new LinearLayout.LayoutParams(0,-2,1));
            if(pickerTarget==BibleTarget.STUDY_GROUP){
                boolean standalone=present.contains(reference),covered=!standalone&&included.contains(verse.ref);
                TextView toggle=button(standalone?"−":covered?"✓":"+",false,()->{});
                toggle.setContentDescription((standalone?"Убрать ":covered?"Уже в отрывке: ":"Добавить ")+reference);
                pickerQuickButtons.add(toggle);head.addView(toggle,new LinearLayout.LayoutParams(dp(48),dp(48)));
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
        if(targetVerse<1&&pickerRestoredScroll>=0){
            int y=pickerRestoredScroll;pickerRestoredScroll=-1;
            afterPickerLayout(content,()->{if(currentScroller==scroller)scroller.scrollTo(0,y);});
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
                for(LocalStore.Entry e:store.entries(pickerTargetId))if(ref.equals(e.reference)){
                    toast("Уже в коллекции");return false;
                }
                long added=store.addPickedPassage(pickerTargetId,ref,verses,bible,insertAfterEntryId);
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
        if(!bible.isReady())for(LocalStore.Flashcard f:cards)
            if(f.bookId>0){toast("Библия ещё загружается");return;}
        cardStudyQueue.clear();cardStudyCursor=0;cardStudyDeckId=selectedDeck;
        for(LocalStore.Flashcard card:cards){
            try{
                List<Bible.Verse> verses=card.bookId==0?
                    Collections.singletonList(new Bible.Verse("1:1",card.content)):
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
        action(content,Bible.NAMES[selectedBook-1]+" ▾",false,()->{
            styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
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
    private void clearSavedCardSession(long deckId){
        getSharedPreferences("card_sessions",MODE_PRIVATE).edit().remove("deck_"+deckId).apply();
    }
    private List<Long> cardIds(List<LocalStore.Flashcard> cards){
        List<Long> ids=new ArrayList<>();for(LocalStore.Flashcard f:cards)ids.add(f.id);return ids;
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
        List<LocalStore.Flashcard> all=eligibleSessionCards();
        if(all.isEmpty()){toast("Подборка пуста");return;}
        List<Long> available=cardIds(all);
        cardSequence.clear();cardIndex=0;
        String saved=getSharedPreferences("card_sessions",MODE_PRIVATE).getString("deck_"+selectedDeck,"");
        if(!saved.isEmpty())try{
            JSONObject json=new JSONObject(saved);JSONArray a=json.getJSONArray("order");
            for(int i=0;i<a.length();i++)cardSequence.add(a.getLong(i));
            cardIndex=Math.max(0,Math.min(cardSequence.size()-1,json.optInt("cursor",0)));
            cardShowReference=json.optBoolean("reference_first",true);
        }catch(Exception ignored){cardSequence.clear();}
        if(cardSequence.size()!=available.size()||
            !new HashSet<>(cardSequence).equals(new HashSet<>(available))){
            cardSequence.clear();cardSequence.addAll(FlashcardDeckLogic.shuffled(available,-1,cardRandom));
            cardIndex=0;cardShowReference=cardRandom.nextBoolean();
        }
        cardRevealed=false;screen=Screen.CARD_SESSION;saveCardSession();render();
    }
    private void saveCardSession(){
        if(selectedDeck<1||cardSequence.isEmpty())return;
        try{
            JSONObject json=new JSONObject();json.put("order",new JSONArray(cardSequence));
            json.put("cursor",cardIndex);json.put("reference_first",cardShowReference);
            getSharedPreferences("card_sessions",MODE_PRIVATE).edit()
                .putString("deck_"+selectedDeck,json.toString()).apply();
        }catch(JSONException ignored){}
    }
    private void nextFlashcard(){
        if(cardSequence.isEmpty()){screen=cardFromUnified?Screen.HOME:Screen.CARD_DECK;render();return;}
        if(cardIndex+1<cardSequence.size())cardIndex++;
        else{
            long previous=cardSequence.get(cardSequence.size()-1);
            List<Long> all=cardIds(eligibleSessionCards());
            cardSequence.clear();cardSequence.addAll(FlashcardDeckLogic.shuffled(all,previous,cardRandom));
            cardIndex=0;
        }
        cardRevealed=false;cardShowReference=cardRandom.nextBoolean();
        saveCardSession();renderCardSession();
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
        List<LocalStore.Flashcard> items=store.flashcards(selectedDeck);
        LocalStore.Flashcard current=null;
        if(!cardSequence.isEmpty())for(LocalStore.Flashcard f:items)
            if(f.id==cardSequence.get(Math.min(cardIndex,cardSequence.size()-1))){current=f;break;}
        if(current==null){screen=cardFromUnified?Screen.HOME:Screen.CARD_DECK;render();return;}
        root=column();root.setBackground(pageBackground());
        LinearLayout header=column();header.setPadding(dp(16),dp(16),dp(16),dp(7));
        LocalStore.CardDeck deck=store.cardDeck(selectedDeck);
        TextView deckHeading=displayText(deck==null?"Карточки":deck.title,22);
        deckHeading.setGravity(Gravity.CENTER);
        header.addView(deckHeading,new LinearLayout.LayoutParams(-1,dp(45)));
        root.addView(header,new LinearLayout.LayoutParams(-1,-2));
        SwipeCardLayout swipe=new SwipeCardLayout();
        swipe.setBackground(outlined(CARD,26,BORDER));
        swipe.setClipToOutline(true);
        ImageView scenicCard=new ImageView(this);
        scenicCard.setImageResource(landscapeFor(selectedDeck));
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
        TextView initial=txt(cardShowReference?current.reference:current.content,
            cardShowReference?27:20,0xFF3C422E,cardShowReference);
        initial.setTypeface(Typeface.create("serif",cardShowReference?Typeface.BOLD:Typeface.NORMAL));
        initial.setGravity(Gravity.CENTER);add(vertical,initial,0);
        TextView answer=txt(cardShowReference?current.content:current.reference,
            cardShowReference?20:25,0xFF694F3F,!cardShowReference);
        answer.setTypeface(Typeface.create("serif",cardShowReference?Typeface.NORMAL:Typeface.BOLD));
        // Reserve an answer area so opening the eye never shifts the original citation/text.
        answer.setGravity(Gravity.CENTER);
        answer.setAlpha(cardRevealed?1f:0f);
        answer.setImportantForAccessibility(cardRevealed?
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
        eye.setOpen(cardRevealed,false);
        eye.setContentDescription(cardRevealed?"Скрыть ответ":"Показать ответ");
        eye.setBackground(outlined(0xFFF0F2E7,14,0xFFC9D5C4));
        LinearLayout.LayoutParams eyeParams=new LinearLayout.LayoutParams(dp(60),dp(60));
        eyeParams.gravity=Gravity.CENTER_HORIZONTAL;eyeParams.topMargin=dp(24);
        vertical.addView(eye,eyeParams);
        eye.setOnClickListener(v->{
            cardRevealed=!cardRevealed;
            eye.setOpen(cardRevealed,true);
            answer.animate().cancel();
            answer.animate().alpha(cardRevealed?1f:0f)
                .setDuration(ValueAnimator.areAnimatorsEnabled()?250L:0L).start();
            answer.setImportantForAccessibility(cardRevealed?
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            eye.setContentDescription(cardRevealed?"Скрыть ответ":"Показать ответ");
        });
        setContentView(root);
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
        for(LocalStore.Group group:store.groups()){
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
            tile.setOnClickListener(v->{selectedGroup=group.id;screen=Screen.ENTRIES;render();});
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
        if(bible.isReady())return false;
        if(!bibleError.isEmpty()) helper(content,"Ошибка встроенного текста: "+bibleError);
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
        if(bibleSearchActive){renderSearchResults();return;}
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
                selectedBook=book;selectedChapter=screen==Screen.PICKER_BOOKS&&passageSelection.book==book?
                    passageSelection.chapter:lastReadChapter(book);
                if(screen==Screen.PICKER_BOOKS){
                    boolean rangeActive=passageSelection.active;
                    passageSelection.openBook(book);passageSelection.active=rangeActive;
                    pickerHasAnchor=false;
                    screen=Screen.PICKER_CHAPTERS;
                }else {selectingPassage=false;clearPassageSelection();screen=Screen.CHAPTERS;}
                render();
            });
            groupedRows.addView(line,new LinearLayout.LayoutParams(-1,-2));
            if(b<66&&BookColorLogic.group(b+1)==group){
                View separator=new View(this);separator.setBackgroundColor(0xFFEADACE);
                groupedRows.addView(separator,new LinearLayout.LayoutParams(-1,dp(1)));
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
    private void renderChapters() {
        if(bibleNotReady())return;
        int n=bible.chapterCount(selectedBook);
        title(Bible.NAMES[selectedBook-1],n+" "+(n%10==1 && n%100!=11?"глава":
            n%10>=2 && n%10<=4 && (n%100<12||n%100>14)?"главы":"глав"));
        int selected=screen==Screen.PICKER_CHAPTERS?selectedChapter:lastReadChapter(selectedBook);
        for(int base=1;base<=n;base+=4){
            LinearLayout r=row();
            for(int k=base;k<base+4;k++){
                if(k>n){r.addView(new View(this),new LinearLayout.LayoutParams(0,dp(50),1));continue;}
                final int c=k;
                TextView b=button(String.valueOf(k),c==selected,()->{
                    if(screen==Screen.PICKER_CHAPTERS&&!passageSelection.active&&selectedChapter!=c)pickerVerseNumber=1;
                    selectedChapter=c;
                    if(screen==Screen.PICKER_CHAPTERS)screen=Screen.PICKER_VERSES;
                    else{rememberChapter(selectedBook,c);screen=Screen.CHAPTER;}
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
        if(verses.isEmpty()){helper(content,"Нет текста главы.");return;}
        rememberChapter(selectedBook,selectedChapter);
        action(content,"Глава "+selectedChapter+"  ▾",false,this::showChapterJump);
        Map<Integer,LocalStore.VerseStatus> statuses=store.statusesForChapter(
            Bible.NAMES[selectedBook-1],selectedChapter);
        int mastered=0,early=0;
        for(LocalStore.VerseStatus stat:statuses.values()){
            if(stat.rank==3)mastered++;
            if(stat.rank==2)early++;
        }
        LinearLayout statsControls=row();
        add(statsControls,txt("Выучено "+mastered+" из "+verses.size(),13,MUTED,false),0);
        TextView toggle=button(showBibleStatuses?"Скрыть статусы":"Показать статусы",false,()->{
            showBibleStatuses=!showBibleStatuses;
            getSharedPreferences("bible_view",MODE_PRIVATE).edit()
                .putBoolean("verse_status",showBibleStatuses).apply();render();
        });
        statsControls.addView(toggle,new LinearLayout.LayoutParams(0,dp(42),1));
        add(content,statsControls,12);
        label(content,"Текст главы");
        for(Bible.Verse verse:verses){
            final int verseNumber;
            try{verseNumber=Integer.parseInt(verse.ref.substring(verse.ref.indexOf(':')+1));}
            catch(Exception ex){continue;}
            boolean highlighted=verseNumber==focusVerse;
            LocalStore.VerseStatus stat=statuses.get(verseNumber);
            LinearLayout c=card(content);
            if(highlighted){
                c.setBackground(outlined(0xFFEDEBDD,20,SUCCESS));
                c.post(()->{if(currentScroller!=null){
                    int top=c.getTop();View node=c.getParent() instanceof View?(View)c.getParent():null;
                    if(node!=null)top+=node.getTop();
                    currentScroller.smoothScrollTo(0,Math.max(0,top-dp(30)));
                }});
                focusVerse=-1;
            }
            LinearLayout head=row();
            head.addView(txt(verse.ref,14,bookInk(selectedBook),true));
            if(highlighted){TextView tick=txt("✓ Выбран",12,GREEN,true);
                head.addView(tick);}
            add(c,head,0);
            add(c,prose(verse.text,19),8);
            if(showBibleStatuses){
                int color=stat==null?MUTED:stat.rank==3?SUCCESS:
                    stat.rank==2?0xFF517993:stat.rank==1?0xFF99723D:MUTED;
                String label=stat==null?"Не изучен":stat.state;
                int rep=stat==null?0:stat.repetitions;
                add(c,txt("● "+label+"   ·   Повторений: "+rep,12,color,true),12);
                if(stat!=null && stat.nextDue>0 && stat.rank>0)
                    add(c,txt(stat.nextDue<=System.currentTimeMillis()?"Повторить сегодня":
                        "Следующее повторение: "+date(stat.nextDue),12,MUTED,false),4);
            }
            c.setOnClickListener(v->showVerseInfo(verse,stat,verseNumber));
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
        LinearLayout layout=column();layout.setPadding(dp(18),dp(24),dp(18),dp(20));
        layout.setBackgroundColor(BG);
        TextView name=displayText(Bible.NAMES[selectedBook-1],25);
        name.setGravity(Gravity.CENTER);add(layout,name,0);
        EditText number=numeric("Введите номер главы","");
        number.setSingleLine(true);number.setImeOptions(EditorInfo.IME_ACTION_DONE);
        add(layout,number,15);
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=column();list.setPadding(0,dp(8),0,dp(16));scroll.addView(list);
        int total=bible.chapterCount(selectedBook);
        Runnable refresh=()->{
            list.removeAllViews();
            int numberFilter=number(number);
            if(numberFilter>0){
                if(numberFilter<=total){
                    final int selection=numberFilter;
                    TextView one=button("Глава "+selection,true,()->{
                        dialog.dismiss();selectedChapter=selection;
                        rememberChapter(selectedBook,selection);render();
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
                    TextView b=button(String.valueOf(k),k==selectedChapter,()->{
                        dialog.dismiss();selectedChapter=chapter;
                        rememberChapter(selectedBook,chapter);render();
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
            if(chosen>=1&&chosen<=total){dialog.dismiss();selectedChapter=chosen;
                rememberChapter(selectedBook,chosen);render();}
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
        int sc=rangeStartChapter<1?selectedChapter:rangeStartChapter;
        int ec=rangeEndChapter<1?sc:rangeEndChapter;
        int sv=rangeStartVerse<1?1:rangeStartVerse;
        int ev=rangeEndVerse<1?sv:rangeEndVerse;
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
        action(builder,screen==Screen.BIBLE_PICKER?"Добавить отрывок":cardPicker?"Добавить карточку":"Просмотреть отрывок",true,()->{
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
        if(!cardPicker&&screen!=Screen.BIBLE_PICKER)action(builder,"Добавить в карточки",false,()->{
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
    private void showVerseInfo(Bible.Verse verse,LocalStore.VerseStatus status,int verseNo){
        String label=status==null?"Не изучен":status.state;
        int repeated=status==null?0:status.repetitions;
        StringBuilder info=new StringBuilder(verse.text);
        info.append("\n\nСтатус: ").append(label).append("\nПовторений: ").append(repeated);
        if(status!=null){
            info.append("\nУспешных: ").append(status.successful);
            if(status.last>0)info.append("\nПоследнее воспроизведение: ").append(date(status.last));
            if(status.nextDue>0)info.append("\nСледующее повторение: ").append(date(status.nextDue));
        }
        String reference=Bible.reference(selectedBook,selectedChapter,verseNo,selectedChapter,verseNo);
        long savedEntry=-1;
        for(LocalStore.Entry entry:store.allEntries())if(reference.equals(entry.reference)){
            savedEntry=entry.id;break;
        }
        final long trainingEntry=savedEntry;
        LinearLayout choices=column();choices.setPadding(dp(20),dp(8),dp(20),dp(12));
        add(choices,txt(info.toString(),15,WHITE,false),5);
        AlertDialog d=new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle(Bible.NAMES[selectedBook-1]+" "+verse.ref)
            .setView(choices).setNegativeButton("Закрыть",null).create();
        if(trainingEntry>=0)action(choices,"Тренировать стих",true,()->{
            d.dismiss();openTraining(trainingEntry);
        });
        styledDialog(d);
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
        List<LocalStore.UnifiedCollection> collections=store.unifiedCollections();
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
            else{openUnifiedId=u.id;screen=Screen.COLLECTION_DETAIL;render();}
        });
    }
    /** Study collections get only these four approved actions, in this exact order. */
    private void showStudyGroupActions(LocalStore.Group group,View anchor){
        String[] actions={"Добавить свой текст","Переименовать","Удалить"};
        String[] icons={"file","pencil","trash"};
        showActionsMenu(anchor,actions,icons,which->{
            selectedGroup=group.id;
            switch(which){
                case 0:customTargetGroupId=group.id;screen=Screen.CUSTOM;render();break;
                case 1:renameGroup(group);break;
                case 2:confirmDeleteStudyGroup(group);break;
            }
        });
    }
    private void confirmDeleteStudyGroup(LocalStore.Group group){
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Удалить коллекцию и все стихи?")
            .setMessage("Это действие нельзя отменить.")
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
        helper(
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
        EditText name=input("Название коллекции",false);name.setText(group.title);
        LinearLayout box=column();box.setPadding(dp(20),dp(5),dp(20),dp(5));box.addView(name);
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setTitle("Переименовать коллекцию").setView(box)
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
        action(content,"Готово",true,()->{
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
                if(cards)store.reorderCards(selectedDeck,ids);
                else store.reorderStudy(selectedGroup,ids);
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
                store.reviseStudyPassage(e.id,value(source),lines);
                render();
            }).setNegativeButton("Отмена",null).create());
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
        EditText source=input("Название / источник",false);source.setText(card.reference);
        EditText words=input("Текст",true);words.setMinLines(4);words.setText(card.content);
        LinearLayout fields=column();fields.setPadding(dp(20),dp(5),dp(20),dp(12));
        add(fields,source,0);add(fields,words,9);
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
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
        showActionsMenu(anchor,labels,icons,which->{
            switch(which){
                case 0:editStudyEntry(entry);break;
                case 1:
                    clearInsertionContext();
                    insertAfterEntryId=entry.id;studyInsertionTrail.add(entry.id);
                    openBiblePicker(BibleTarget.STUDY_GROUP,entry.groupId,screen==Screen.COLLECTION_DETAIL?Screen.COLLECTION_DETAIL:Screen.ENTRIES);break;
                case 2:
                    styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
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
        showActionsMenu(anchor,labels,icons,which->{
            switch(which){
                case 0:editCardEntry(card);break;
                case 1:
                    clearInsertionContext();
                    insertAfterCardId=card.id;cardInsertionTrail.add(card.id);
                    openBiblePicker(BibleTarget.FLASHCARD_DECK,selectedDeck,Screen.CARD_DECK);break;
                case 2:
                    styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
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
                    EditText name=input("Название отрывка",false);name.setText(entry.title);
                    LinearLayout box=column();box.setPadding(dp(20),dp(5),dp(20),dp(5));box.addView(name);
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
        long source=store.canonicalStudyEntryId(e.id);
        int total=e.lines().size(),learned=store.masteryCount(source);
        int established=store.consolidatedVerseCount(source);
        String progress=established+" выучено · "+Math.max(0,learned-established)+" освоено";
        String detail=e.reference+" · "+progress+" · Связок: "+store.masteredLinkedSegments(source);
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
        body.setOnClickListener(v->openTraining(e.id));
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
        reviewQueue.clear();reviewPointer=0;
        List<ReviewQueueLogic.Item> raw=new ArrayList<>();
        Set<String> reviewSeen=new HashSet<>();
        for(LocalStore.DueItem due:store.dueItems(System.currentTimeMillis())){
            LocalStore.Entry source=store.entry(due.entryId);
            if(source==null || (reviewCollectionGroup>0 &&
                !store.belongsToGroupMaterial(reviewCollectionGroup,due)))continue;
            // Across collections, check each identical Bible segment only once.
            String key="entry:"+due.entryId+":"+due.stage+":"+due.start+":"+due.end;
            List<Bible.Verse> lines=source.lines();
            if(bookForEntry(source)>0 && due.start>=0 && due.end<=lines.size() && due.start<due.end){
                StringBuilder unique=new StringBuilder("bible:").append(bookForEntry(source))
                    .append(':').append(due.stage);
                for(int i=due.start;i<due.end;i++)unique.append('|').append(lines.get(i).ref)
                    .append('~').append(lines.get(i).text);
                key=unique.toString();
            }
            if(reviewSeen.add(key))raw.add(new ReviewQueueLogic.Item(
                due.entryId,due.stage,due.start,due.end,due.due));
        }
        List<ReviewQueueLogic.Item> items=ReviewQueueLogic.plan(raw,new ReviewQueueLogic.Source(){
            @Override public List<String> references(long id){
                LocalStore.Entry e=store.entry(id);List<String> refs=new ArrayList<>();
                if(e!=null)for(Bible.Verse v:e.lines())refs.add(v.ref);return refs;
            }
            @Override public List<String> texts(long id){
                LocalStore.Entry e=store.entry(id);List<String> texts=new ArrayList<>();
                if(e!=null)for(Bible.Verse v:e.lines())texts.add(v.text);return texts;
            }
        });
        for(ReviewQueueLogic.Item item:items)
            reviewQueue.add(new LocalStore.DueItem(item.entryId,item.stage,item.start,item.end,item.due));
        android.content.SharedPreferences old=getSharedPreferences(SESSION_PREFS,MODE_PRIVATE);
        if(old.getBoolean("review_pending",false)){
            long id=old.getLong("review_review_entry",-1);
            LocalStore.Entry e=store.entry(id);
            int a=old.getInt("review_begin",-1),b=old.getInt("review_end",-1);
            String stage=old.getString("review_review_stage","");
            if(e!=null && (reviewCollectionGroup<0 ||
                store.belongsToGroupMaterial(reviewCollectionGroup,
                    new LocalStore.DueItem(id,stage,a,b,0))) &&
                a>=0&&b>a&&b<=e.lines().size() &&
                ("VERSE_RUN".equals(stage)||"VERSE".equals(stage)||"CHAIN".equals(stage)||
                 "SECTION".equals(stage)||"CHAPTER".equals(stage))){
                final long targetId=id;final int first=a,last=b;
                // A saved unfinished review takes precedence; avoid duplicate overlapping tasks.
                reviewQueue.removeIf(t->t.entryId==targetId && t.start<last && first<t.end);
                reviewQueue.add(0,new LocalStore.DueItem(id,stage,a,b,0));
            }else old.edit().remove("review_pending").apply();
        }
        if(reviewQueue.isEmpty()){screen=Screen.DUE;render();return;}
        LocalStore.DueItem first=reviewQueue.get(0);
        boolean restore=old.getBoolean("review_pending",false) &&
            old.getLong("review_review_entry",-1)==first.entryId &&
            old.getInt("review_begin",-1)==first.start&&old.getInt("review_end",-1)==first.end;
        openReview(first);
        if(restore){
            draft=old.getString("review_draft","");
            lessonWriteHadFeedback=old.getBoolean("review_lesson_had_feedback",false);
            render();
        }
    }
    private void renderDue() {
        title("Повторить изученное","Повторения запускаются автоматически по расписанию.");
        LinearLayout c=headerCard(content);
        add(c,txt("На данный момент повторений нет ✓",21,WHITE,true),0);
        helper(c,"Новые стихи не добавляются в повторение до освоения.");

    }
    private void openReview(LocalStore.DueItem item){
        LocalStore.Entry e=store.entry(item.entryId);if(e==null)return;
        selectedEntry=item.entryId;lessonPracticeAgain=false;
        // VERSE_RUN is a temporary coherent unit, never recorded as a mastered group.
        studyStage="VERSE_RUN".equals(item.stage)?StudyPlanLogic.Stage.CHAIN:
            StudyPlanLogic.Stage.valueOf(item.stage);
        forcedStep=new StudyPlanLogic.Step(item.start,item.end,"Повторение · "+e.lines().get(item.start).ref);
        currentReview=item;groupMode=false;reviewMode=true;stepCursor=0;
        weakVerse=-1;weakTransitionFrom=-1;
        mask=0;checked=false;passed=false;draft="";testAssisted=false;lastAccuracy=-1;
        lessonWriteHadFeedback=false;lessonPracticeAgain=false;
        trainingMode=TrainingMode.TYPE;resetWordTiles();screen=Screen.TRAIN;render();
    }
    private void advanceReviewSession(){
        reviewPointer++;
        if(reviewPointer<reviewQueue.size()){openReview(reviewQueue.get(reviewPointer));return;}
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
    private void saveTrainingSession() {
        if(selectedEntry<=0 || (reviewMode&&checked&&passed))return;
        String key=reviewMode?"review_":(groupMode?"linked_":"entry_")+selectedEntry+"_";
        getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit()
            .putLong("entry",selectedEntry).putString(key+"stage",studyStage.name())
            .putInt(key+"step",stepCursor).putString(key+"mode",trainingMode.name())
            .putInt(key+"mask",mask).putString(key+"draft",draft)
            .putInt(key+"blur_seed",blurSeed)
            .putInt(key+"lesson_variant",lessonVariant).putInt(key+"lesson_chunk",lessonChunk)
            .putInt(key+"lesson_picked",lessonPicked).putLong(key+"lesson_seed",lessonSeed)
            .putString(key+"rotation_exercise_key",rotationExerciseKey)
            .putLong(key+"rotation_exercise_seed",rotationExerciseSeed)
            .putString(key+"rotation_mask",rotationMask)
            .putString(key+"lesson_tiles",lessonUsedTiles.toString())
            .putBoolean(key+"lesson_had_feedback",lessonWriteHadFeedback)
            .putInt(key+"write_variant",lessonWriteVariant)
            .putBoolean(key+"group_intro_done",groupIntroDone)
            .putBoolean(key+"group_build_done",groupBuildDone)
            .putBoolean(key+"practice_weak",practiceWeakFragment)
            .putString(key+"weak_fragment",weakFragmentText)
            .putBoolean(key+"pending",reviewMode||groupMode)
            .putLong(key+"review_entry",reviewMode?selectedEntry:0)
            .putString(key+"review_stage",reviewMode&&currentReview!=null?currentReview.stage:"")
            .putInt(key+"begin",activeStep==null?0:activeStep.start)
            .putInt(key+"end",activeStep==null?0:activeStep.end)
            .putString(key+"group_stage",studyStage.name())
            .putInt(key+"return_cursor",returnVerseCursor).apply();
    }
    private boolean restoreTrainingSession(long id) {return restoreTrainingSession(id,false);}
    private boolean restoreTrainingSession(long id,boolean linked) {
        android.content.SharedPreferences pref=getSharedPreferences(SESSION_PREFS,MODE_PRIVATE);
        String key=(linked?"linked_":"entry_")+id+"_";
        if(!pref.contains(key+"stage"))return false;
        try {
            studyStage=StudyPlanLogic.Stage.valueOf(pref.getString(key+"stage","VERSE"));
            trainingMode=TrainingMode.valueOf(pref.getString(key+"mode","READ"));
        }catch(IllegalArgumentException ex){studyStage=StudyPlanLogic.Stage.VERSE;trainingMode=TrainingMode.READ;}
        stepCursor=Math.max(0,pref.getInt(key+"step",0));
        mask=trainingMode==TrainingMode.HIDE?Math.max(0,Math.min(100,pref.getInt(key+"mask",0))):0;
        blurSeed=pref.getInt(key+"blur_seed",new Random().nextInt());
        draft=pref.getString(key+"draft","");
        if(draft==null)draft="";
        lessonVariant=Math.max(0,pref.getInt(key+"lesson_variant",0));
        lessonChunk=Math.max(0,pref.getInt(key+"lesson_chunk",0));
        lessonPicked=Math.max(0,pref.getInt(key+"lesson_picked",0));
        lessonSeed=pref.getLong(key+"lesson_seed",new Random().nextLong());
        rotationExerciseKey=pref.getString(key+"rotation_exercise_key","");
        rotationExerciseSeed=pref.getLong(key+"rotation_exercise_seed",0L);
        rotationMask=pref.getString(key+"rotation_mask","");
        lessonUsedTiles.clear();lessonWrongTile=-1;lessonPracticeAgain=false;
        lessonWriteHadFeedback=pref.getBoolean(key+"lesson_had_feedback",false);
        lessonWriteVariant=Math.max(0,pref.getInt(key+"write_variant",0));
        if(linked)groupIntroDone=pref.getBoolean(key+"group_intro_done",false);
        if(linked)groupBuildDone=pref.getBoolean(key+"group_build_done",false);
        practiceWeakFragment=pref.getBoolean(key+"practice_weak",false);
        weakFragmentText=pref.getString(key+"weak_fragment","");
        String saved=pref.getString(key+"lesson_tiles","").replace("[", "").replace("]", "");
        for(String part:saved.split(","))try{lessonUsedTiles.add(Integer.parseInt(part.trim()));}
            catch(NumberFormatException ignored){}
        checked=false;passed=false;testAssisted=false;lastAccuracy=-1;resetWordTiles();
        return true;
    }
    private void resetWordTiles() {
        orderChunk=0;orderPosition=0;orderWrongTile=-1;orderMistakes=0;
        orderBankOrder=new int[0];usedWordTiles.clear();orderCompleted=false;
    }
    private void openTraining(long id) {
        reviewMode=false;groupMode=false;forcedStep=null;currentReview=null;
        if(screen==Screen.TRAIN)saveTrainingSession();
        if(screen==Screen.CARD_SESSION)saveCardSession();
        selectedEntry=store.canonicalStudyEntryId(id);
        id=selectedEntry;
        LocalStore.Entry entry=store.entry(id);
        if(entry==null){toast("Отрывок не найден");screen=Screen.HOME;render();return;}
        android.content.SharedPreferences linked=getSharedPreferences(SESSION_PREFS,MODE_PRIVATE);
        String lk="linked_"+id+"_";
        if(linked.getBoolean(lk+"pending",false)){
            int a=linked.getInt(lk+"begin",0),b=linked.getInt(lk+"end",0);
            String stage=linked.getString(lk+"group_stage","CHAIN");
            if(a>=0&&b>a&&b<=entry.lines().size()&&
                !store.isStepMastered(id,stage,a,b)){
                GroupLearningLogic.Candidate cand=GroupLearningLogic.candidate(stage,a,b,"Продолжить связку");
                returnVerseCursor=linked.getInt(lk+"return_cursor",0);
                openGroup(cand);restoreTrainingSession(id,true);
                returnVerseCursor=linked.getInt(lk+"return_cursor",0);render();return;
            }
            linked.edit().remove(lk+"pending").apply();
        }
        if(!restoreTrainingSession(id)){
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
            checked=false;passed=false;draft="";
            lessonWriteHadFeedback=false;lessonPracticeAgain=false;
            resetTrainingStep(entry);collectionQueueNewStep=false;
        }
        rememberRecent(id);saveTrainingSession();
        screen=Screen.TRAIN;render();
    }
    private AdaptiveLearningLogic.Recommendation selectRecommendation(LocalStore.Entry e) {
        List<String> refs=new ArrayList<>();
        for(Bible.Verse v:e.lines())refs.add(v.ref);
        AdaptiveLearningLogic.Recommendation advice=AdaptiveLearningLogic.recommend(
            refs,isSermon(e),store.progressFor(e.id),System.currentTimeMillis());
        studyStage=advice.stage;stepCursor=advice.index;
        return advice;
    }
    private void followRecommendation(LocalStore.Entry e) {
        selectRecommendation(e);resetTrainingStep(e);saveTrainingSession();render();
    }
    private void showAnyVerse(LocalStore.Entry e) {
        int max=store.unlockedPosition(e.id,e.lines().size());
        final EditText positionInput=numeric("Номер стиха в подборке",String.valueOf(stepCursor+1));
        LinearLayout wrapper=column();wrapper.setPadding(dp(20),dp(5),dp(20),dp(5));
        wrapper.addView(positionInput);
        new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setTitle("Открытые стихи: 1–"+(max+1))
            .setView(wrapper).setPositiveButton("Перейти",(d,w)->{
                int n=number(positionInput);
                if(!LessonFlowLogic.canOpen(n-1,max,e.lines().size())){
                    toast("Следующий стих откроется после выполнения заданий");return;
                }
                studyStage=StudyPlanLogic.Stage.VERSE;stepCursor=n-1;
                resetTrainingStep(e);saveTrainingSession();render();
            }).setNegativeButton("Отмена",null).show();
    }
    private boolean isSermon(LocalStore.Entry e) {
        return e.reference!=null && (e.reference.startsWith("Матфея 5:") ||
          e.reference.startsWith("Матфея 6:") || e.reference.startsWith("Матфея 7:"));
    }
    private List<StudyPlanLogic.Step> studySteps(LocalStore.Entry e) {
        if((reviewMode||groupMode)&&forcedStep!=null)return Collections.singletonList(forcedStep);
        List<String> refs=new ArrayList<>();
        for(Bible.Verse v:e.lines())refs.add(v.ref);
        return StudyPlanLogic.build(refs,studyStage,isSermon(e));
    }
    private void resetTrainingStep(LocalStore.Entry e) {
        lessonVariant=0;lessonChunk=0;lessonPicked=0;lessonWrongTile=-1;lessonWriteVariant=0;groupBuildDone=false;groupIntroDone=false;
        practiceWeakFragment=false;weakFragmentText="";
        lessonSeed=new Random().nextLong();lessonUsedTiles.clear();lessonPracticeAgain=false;
        rotationExerciseKey="";rotationMask="";rotationExerciseSeed=0L;
        lessonWriteHadFeedback=false;
        mask=0;checked=false;passed=false;draft="";lastAccuracy=-1;
        blurSeed=new Random().nextInt();testAssisted=false;resetWordTiles();
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
    private void moveStep(LocalStore.Entry e,int delta) {
        List<StudyPlanLogic.Step> steps=studySteps(e);
        if(steps.isEmpty())return;
        int target=Math.max(0,Math.min(steps.size()-1,stepCursor+delta));
        if(!reviewMode&&!groupMode&&studyStage==StudyPlanLogic.Stage.VERSE &&
            !LessonFlowLogic.canOpen(target,store.unlockedPosition(e.id,e.lines().size()),e.lines().size())){
            toast("Следующий стих откроется после контрольной проверки");return;
        }
        stepCursor=target;
        resetTrainingStep(e);saveTrainingSession();render();
    }
    private String texts(List<Bible.Verse> lines) {
        StringBuilder sb=new StringBuilder();
        for(Bible.Verse v:lines) {if(sb.length()>0)sb.append(' ');sb.append(v.text);}
        return sb.toString();
    }
    private void chooseTrainingMode(TrainingMode chosen,boolean mastered) {
        if(chosen==TrainingMode.HIDE) {
            if(trainingMode!=TrainingMode.HIDE)blurSeed=new Random().nextInt();
            mask=trainingMode==TrainingMode.HIDE?mask+25:25;
            if(mask>100){mask=0;chosen=TrainingMode.READ;}
        }
        if(chosen==TrainingMode.ORDER && trainingMode!=TrainingMode.ORDER)resetWordTiles();
        if((reviewMode || groupMode || (studyStage==StudyPlanLogic.Stage.VERSE &&
            store.exerciseDone(selectedEntry,stepCursor)>=4)) && chosen!=TrainingMode.TYPE)
            testAssisted=true;
        TrainingMode previous=trainingMode;
        trainingMode=chosen;
        if(previous==TrainingMode.TYPE && chosen!=TrainingMode.TYPE){
            android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
            if(keyboard!=null && root!=null)
                keyboard.hideSoftInputFromWindow(root.getWindowToken(),0);
            if(root!=null)root.requestFocus();
        }
        saveTrainingSession();
        // Reuse the existing Activity and ScrollView: changing modes must not jump to the top.
        if(previous==TrainingMode.HIDE && chosen==TrainingMode.HIDE && activePassage!=null){
            updateBlurText();updateModeBar();return;
        }
        updateActiveTrainingUI();
    }
    /** Training actions belong to the card, not the application's navigation bar. */
    private void addTrainingModeButtons(LinearLayout card) {
        LinearLayout bar=row();bar.setPadding(0,dp(12),0,dp(6));
        TrainingMode[] modes={TrainingMode.HIDE,TrainingMode.ORDER,TrainingMode.TYPE};
        String[] labels={"◌  Скрыть","▦  Собрать","✎  Написать"};
        for(int i=0;i<modes.length;i++) {
            final TrainingMode mode=modes[i];
            TextView b=button(labels[i],trainingMode==mode,()->chooseTrainingMode(mode,activeMastered));
            b.setTextSize(13);b.setMinHeight(dp(52));b.setPadding(dp(2),dp(7),dp(2),dp(7));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);
            lp.setMargins(dp(2),0,dp(2),0);bar.addView(b,lp);
            bottomModeButtons[i]=b;
        }
        add(card,bar,4);
        updateModeBar();
    }
    private void updateModeBar() {
        TrainingMode[] modes={TrainingMode.HIDE,TrainingMode.ORDER,TrainingMode.TYPE};
        String[] labels={"◌  Скрыть"+(trainingMode==TrainingMode.HIDE?" "+mask+"%":""),
            "▦  Собрать","✎  Написать"};
        for(int i=0;i<modes.length;i++){
            TextView b=bottomModeButtons[i];if(b==null)continue;
            boolean selected=trainingMode==modes[i];b.setText(labels[i]);b.setTextColor(selected?PRIMARY_ON:WHITE);
            if(selected){GradientDrawable gd=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xFFDB8C6C,0xFFB8583F});gd.setCornerRadius(dp(14));b.setBackground(gd);}
            else b.setBackground(outlined(CARD_ALT,14,BORDER));
        }
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
        if(activeTextArea==null || activeWorkArea==null || activeEntry==null || activeStep==null)return;
        // Preserve scroll position; unlike render(), this does NOT replace the ScrollView.
        final int position=currentScroller==null?0:currentScroller.getScrollY();
        // Release focus from the old answer before removing its view.
        if(activeWorkArea.hasFocus())activeWorkArea.clearFocus();
        activeTextArea.removeAllViews();activeWorkArea.removeAllViews();activePassage=null;activeStatus=null;
        if(trainingMode==TrainingMode.READ || trainingMode==TrainingMode.HIDE) {
            activePassage=txt("",20,WHITE,false);activePassage.setTextIsSelectable(false);
            add(activeTextArea,activePassage,8);
            activeStatus=txt("",13,MUTED,false);add(activeTextArea,activeStatus,12);
            updateBlurText();
        } else {
            add(activeTextArea,txt(activeStep.title,19,WHITE,true),9);
            helper(activeTextArea,"Текст закрыт. Вспоминай без подсказок.");
        }
        if(trainingMode==TrainingMode.ORDER) {
            showWordOrder(activeWorkArea,activeEntry.lines().subList(activeStep.start,activeStep.end));
        } else if(trainingMode==TrainingMode.TYPE) {
            LinearLayout typing=column();typing.setPadding(dp(13),dp(16),dp(13),dp(16));
            typing.setBackground(outlined(CARD_ALT,17,VIOLET));
            add(typing,txt(reviewMode?"↻  Контрольное повторение":
                (!groupMode && studyStage==StudyPlanLogic.Stage.VERSE && store.exerciseDone(selectedEntry,stepCursor)>=4)?
                "✦  Контрольная проверка":"✎  Напиши по памяти",19,WHITE,true),0);
            helper(typing,"Введи текст без номера стиха. Контрольная выполняется без подсказок.");
            EditText answer=input("Твой ответ…",true);forbidKeyboardHints(answer,true);
            answer.setText(draft);answer.setMinLines(7);
            answer.setShowSoftInputOnFocus(true);
            answer.addTextChangedListener(new TextWatcher(){
                @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                    draft=s.toString();saveTrainingSession();
                }
                @Override public void afterTextChanged(Editable s){}
            });
            add(typing,answer,12);
            helper(typing,"Регистр, пунктуация и ё/е не влияют на точность. Порог — 90%.");
            action(typing,"✓  Проверить написанное",true,()->checkAnswer(activeEntry,activeStep,activeMastered));
            add(activeWorkArea,typing,14);
        } else if(trainingMode!=TrainingMode.ORDER){
            helper(activeWorkArea,"Выбери «Собрать» или «Написать» для следующей тренировки.");
        }
        if(trainingMode!=TrainingMode.READ)
            action(activeWorkArea,"Показать исходный текст",false,()->chooseTrainingMode(TrainingMode.READ,activeMastered));
        if(!reviewMode&&!groupMode&&studyStage==StudyPlanLogic.Stage.VERSE){
            final int completed=store.exerciseDone(selectedEntry,stepCursor);
            if(completed==0 && trainingMode==TrainingMode.READ)
                action(activeWorkArea,"✓  Прочитал · перейти к пропускам",true,()->completeIntro(1));
            if(completed==1 && trainingMode==TrainingMode.HIDE && mask==100)
                action(activeWorkArea,"✓  Восстановил слова · перейти к сборке",true,()->completeIntro(2));
        }
        updateModeBar();
        if(currentScroller!=null)currentScroller.post(()->currentScroller.scrollTo(0,position));
    }
    private void completeIntro(int completed){
        if(reviewMode||groupMode||studyStage!=StudyPlanLogic.Stage.VERSE)return;
        if(store.exerciseDone(selectedEntry,stepCursor)==completed-1 &&
            (completed!=2 || (trainingMode==TrainingMode.HIDE && mask==100))){
            store.finishExercise(selectedEntry,stepCursor,completed);
            trainingMode=completed==1?TrainingMode.HIDE:TrainingMode.ORDER;
            mask=completed==1?25:0;resetWordTiles();saveTrainingSession();render();
        }
    }
    private void renderStepNavigator(LinearLayout into,LocalStore.Entry e,int stepCount) {
        LinearLayout controls=row();
        TextView prev=button("←  Назад",false,()->moveStep(e,-1));
        TextView next=button("Вперёд  →",false,()->moveStep(e,1));
        prev.setEnabled(stepCursor>0);prev.setAlpha(stepCursor>0?1f:0.45f);
        boolean nextAllowed=stepCursor+1<stepCount && (groupMode||reviewMode||studyStage!=StudyPlanLogic.Stage.VERSE||
            LessonFlowLogic.canOpen(stepCursor+1,store.unlockedPosition(e.id,e.lines().size()),e.lines().size()));
        next.setEnabled(nextAllowed);next.setAlpha(nextAllowed?1f:0.45f);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(51),1);lp.rightMargin=dp(7);
        controls.addView(prev,lp);controls.addView(next,new LinearLayout.LayoutParams(0,dp(51),1));
        add(into,controls,10);
        helper(into,groupMode?"Связное воспроизведение укрепляет переходы между стихами.":
            reviewMode?"Повторение без подсказок.":"Назад можно всегда. Вперёд — после контрольной проверки; Библия читается свободно.");
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
        orderChunk=Math.min(orderChunk,chunks.size()-1);
        String[] words=chunks.get(orderChunk);
        if(orderBankOrder.length!=words.length)
            orderBankOrder=WordOrderLogic.shuffledIndices(words.length,System.nanoTime());
        add(card,txt("Собери фрагмент "+(orderChunk+1)+" из "+chunks.size(),17,WHITE,true),13);
        helper(card,"Нажимай на слова снизу в том порядке, как они идут в тексте.");
        add(card,txt(orderPosition+" / "+words.length+" слов",13,GREEN,true),13);
        WrapLayout assembled=new WrapLayout(this,dp(7));assembled.setPadding(0,dp(5),0,dp(10));
        for(int i=0;i<orderPosition;i++) assembled.addView(wordTile(words[i],0xFFE1EDDF,WHITE,SUCCESS,null));
        if(orderPosition==0)assembled.addView(txt("Здесь появятся выбранные слова…",14,MUTED,false));
        add(card,assembled,6);
        if(orderPosition<words.length) {
            label(card,"Выбери следующее слово");
            WrapLayout options=new WrapLayout(this,dp(8));
            for(int tileIndex:orderBankOrder) {
                if(usedWordTiles.contains(tileIndex))continue;
                final int pressed=tileIndex;
                boolean wrong=orderWrongTile==tileIndex;
                TextView tile=wordTile(WordOrderLogic.comparable(words[tileIndex]).isEmpty()?words[tileIndex]:
                    words[tileIndex].replaceAll("^[^\\p{L}\\p{Nd}]+|[^\\p{L}\\p{Nd}]+$",""),
                    wrong?0xFFF8D6D2:CARD_ALT,wrong?RED:WHITE,wrong?RED:BORDER,()->{
                        if(WordOrderLogic.accepts(words,orderPosition,pressed)) {
                            usedWordTiles.add(pressed);orderPosition++;orderWrongTile=-1;
                        } else {orderWrongTile=pressed;orderMistakes++;}
                        updateActiveTrainingUI();
                    });
                options.addView(tile);
            }
            add(card,options,8);
            if(orderWrongTile!=-1)add(card,txt("Не то слово. Попробуй ещё раз.",13,RED,false),10);
        } else {
            add(card,txt("✓  Фрагмент собран правильно",16,GREEN,true),12);
            if(orderChunk<chunks.size()-1)
                action(card,"→  Следующий фрагмент",true,()->{
                    orderChunk++;orderPosition=0;orderWrongTile=-1;orderBankOrder=new int[0];usedWordTiles.clear();updateActiveTrainingUI();
                });
            else {
                if(!reviewMode&&!groupMode&&studyStage==StudyPlanLogic.Stage.VERSE &&
                    store.exerciseDone(selectedEntry,stepCursor)==2 && !orderCompleted){
                    orderCompleted=true;store.finishExercise(selectedEntry,stepCursor,3);
                }
                helper(card,"Ты расставил слова с подсказками. Теперь проверь, сможешь ли воспроизвести их без готовых вариантов.");
                action(card,"✎  Написать по памяти",true,()->chooseTrainingMode(TrainingMode.TYPE,false));
            }
        }
        if(orderMistakes>0)helper(card,"Ошибочных нажатий: "+orderMistakes);
        action(card,"↺  Собрать сначала",false,()->{resetWordTiles();updateActiveTrainingUI();});
    }
    private void renderTraining() {
        LocalStore.Entry e=store.entry(selectedEntry);
        if(e==null){screen=Screen.HOME;render();return;}
        activeEntry=e;
        List<Bible.Verse> all=e.lines();
        List<StudyPlanLogic.Step> steps=studySteps(e);
        if(steps.isEmpty()){helper(content,"Нет текста для тренировки.");return;}
        stepCursor=Math.max(0,Math.min(stepCursor,steps.size()-1));
        StudyPlanLogic.Step step=steps.get(stepCursor);activeStep=step;
        int learned=store.masteryCount(e.id),count=all.size();
        boolean mastered=store.isStepMastered(e.id,studyStage.name(),step.start,step.end);
        activeMastered=mastered;
        title(reviewMode?"Повторение":groupMode?"Связный фрагмент":e.title,e.reference);
        int bookId=bookForEntry(e);
        add(content,txt("●  "+(bookId>0?Bible.NAMES[bookId-1]:"Свой текст"),13,
            BookColorLogic.color(bookId),true),5);
        LinearLayout stat=headerCard(content);
        add(stat,txt(learned+" / "+count+" стихов освоено",22,WHITE,true),0);
        progress(stat,learned,count);
        List<String> allRefs=new ArrayList<>();
        for(Bible.Verse v:all)allRefs.add(v.ref);
        if(!reviewMode&&!groupMode){
            GroupLearningLogic.Candidate group=nextGroup(e);
            if(group!=null){
                add(stat,txt("Рекомендуется: "+group.reason,13,WHITE,false),13);
                action(stat,"Тренировать связку",false,()->openGroup(group));
            }
            if(studyStage==StudyPlanLogic.Stage.VERSE){
                int completed=store.exerciseDone(e.id,stepCursor);
                add(stat,txt(completed>=5?"Пять упражнений выполнены":
                    LessonFlowLogic.label(LessonFlowLogic.Exercise.values()[completed]),14,GREEN,true),10);
            }
        }
        if(!reviewMode&&!groupMode)renderStepNavigator(content,e,steps.size());
        if(!reviewMode&&!groupMode)action(content,"Выбрать открытый стих",false,()->showAnyVerse(e));
        if(groupMode)action(content,"←  Вернуться к стихам",false,()->{groupMode=false;forcedStep=null;studyStage=StudyPlanLogic.Stage.VERSE;stepCursor=returnVerseCursor;resetTrainingStep(e);render();});
        LinearLayout textCard=card(content);
        add(textCard,txt("КАРТОЧКА "+(stepCursor+1)+" ИЗ "+steps.size(),12,GREEN,true),0);
        add(textCard,txt(step.title+(mastered?"  ✓":""),18,WHITE,true),5);
        String status=store.consolidated(e.id,studyStage.name(),step.start,step.end)?"Закреплён":
            mastered?"Предварительно выучен":"Изучается";
        add(textCard,txt(status,13,mastered?GREEN:MUTED,true),8);
        if(!checked) {
            activeTextArea=column();activeTextArea.setMinimumHeight(dp(92));
            add(textCard,activeTextArea,7);
            addTrainingModeButtons(textCard);
            activeWorkArea=column();add(textCard,activeWorkArea,5);
            updateActiveTrainingUI();
        } else {
            activeTextArea=null;activeWorkArea=null;activePassage=null;
            add(textCard,txt(passed?"Воспроизведено ✓":
                testAssisted&&lastAccuracy>=90?"Проверка с подсказкой не засчитана":"Пока есть ошибки",
                22,passed?GREEN:RED,true),14);
            add(textCard,txt("Точность: "+lastAccuracy+"%",21,WHITE,true),10);
            if(trainingMode==TrainingMode.TYPE){
                label(textCard,"Твои ошибки по месту ввода");
                add(textCard,typedFeedback(lastExpected,draft),9);
            }
            helper(textCard,passed?"Результат сохранён. Поздняя повторная проверка закрепит стих.":
                "Попробуй ещё раз без подсказок или исправь выделенные ошибки.");
            label(textCard,"Правильный текст");add(textCard,txt(lastExpected,16,WHITE,false),8);
            action(content,"↺  Исправить ответ",true,()->{
                checked=false;passed=false;lastAccuracy=-1;trainingMode=TrainingMode.TYPE;render();
            });
            if(reviewMode){
                action(content,"←  К повторениям",false,()->{reviewMode=false;forcedStep=null;screen=Screen.DUE;render();});
            }else if(groupMode){
                action(content,"←  К стихам",false,()->{groupMode=false;forcedStep=null;studyStage=StudyPlanLogic.Stage.VERSE;
                    stepCursor=returnVerseCursor;resetTrainingStep(e);render();});
            }else if(studyStage==StudyPlanLogic.Stage.VERSE){
                int done=store.exerciseDone(e.id,stepCursor);
                if(passed&&done==4)action(content,"✦  Начать контрольную проверку",true,()->{
                    draft="";checked=false;passed=false;testAssisted=false;trainingMode=TrainingMode.TYPE;render();
                });
                if(passed&&done>=5){
                    if(stepCursor+1<e.lines().size())
                        action(content,"→  Следующий стих",true,()->moveStep(e,1));
                    GroupLearningLogic.Candidate group=nextGroup(e);
                    if(group!=null)action(content,"✦  "+group.reason,false,()->openGroup(group));
                }
            }

        }

    }
    private void checkAnswer(LocalStore.Entry e,StudyPlanLogic.Step step,boolean mastered) {
        String expected=texts(e.lines().subList(step.start,step.end));
        int accuracy=ReviewLogic.accuracy(expected,draft);
        boolean finalStage=!reviewMode&&!groupMode && studyStage==StudyPlanLogic.Stage.VERSE &&
            store.exerciseDone(e.id,step.start)==4;
        boolean independent=reviewMode||groupMode||finalStage;
        passed=LessonCurriculumLogic.exact(expected,draft) && (!independent||!testAssisted);
        lastAccuracy=accuracy;lastExpected=expected;lastTestRef=step.title;
        lastTestStep=independent?"КОНТРОЛЬНАЯ ПРОВЕРКА":"ТРЕНИРОВКА";
        long now=System.currentTimeMillis();
        store.recordRecall(e.id,studyStage.name(),step.start,step.end,accuracy,
            testAssisted||!independent,now);
        if(independent){
            store.scheduleReview(e.id,studyStage.name(),step.start,step.end,accuracy,testAssisted,now);
            if(passed&&!mastered)store.markMastered(e.id,step.start,step.end,accuracy,studyStage.name());
        }
        if(!reviewMode&&!groupMode&&studyStage==StudyPlanLogic.Stage.VERSE){
            int done=store.exerciseDone(e.id,step.start);
            if(done==3&&passed)store.finishExercise(e.id,step.start,4);
            else if(done==4&&passed)store.finishExercise(e.id,step.start,5);
        }
        int learned=store.masteryCount(e.id);
        ReviewLogic.Schedule unchanged=new ReviewLogic.Schedule(e.repetitions,e.days,e.ease,e.due);
        // Per-verse and grouped schedules are now authoritative; do not update legacy entry-wide schedule.
        store.update(e,unchanged,learned,accuracy);
        checked=true;saveTrainingSession();render();
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
    private int bookForEntry(LocalStore.Entry e){
        if(e==null||e.reference==null)return -1;
        for(int n=1;n<=66;n++)if(e.reference.startsWith(Bible.NAMES[n-1]+" "))return n;
        return -1;
    }
    private GroupLearningLogic.Candidate nextGroup(LocalStore.Entry e){
        List<String> refs=new ArrayList<>();for(Bible.Verse line:e.lines())refs.add(line.ref);
        return GroupLearningLogic.next(refs,new GroupLearningLogic.Progress(){
            @Override public boolean verseMastered(int i){return store.isStepMastered(e.id,"VERSE",i,i+1);}
            @Override public boolean groupMastered(String stage,int a,int b){return store.isStepMastered(e.id,stage,a,b);}
        },isSermon(e));
    }
    private GroupLearningLogic.Candidate pendingAfterCurrent(LocalStore.Entry e,int current){
        if(current<0||current>=e.lines().size())return null;
        List<String> refs=new ArrayList<>();for(Bible.Verse v:e.lines())refs.add(v.ref);
        return GroupLearningLogic.afterVerse(refs,new GroupLearningLogic.Progress(){
            @Override public boolean verseMastered(int i){return store.isStepMastered(e.id,"VERSE",i,i+1);}
            @Override public boolean groupMastered(String stage,int a,int b){return store.isStepMastered(e.id,stage,a,b);}
        },current,isSermon(e));
    }
    private void openGroup(GroupLearningLogic.Candidate group){
        if(!groupMode) returnVerseCursor=studyStage==StudyPlanLogic.Stage.VERSE?stepCursor:
            store.firstUnmastered(selectedEntry,store.entry(selectedEntry).lines().size());
        reviewMode=false;groupMode=true;currentReview=null;
        getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit().putBoolean("linked_"+selectedEntry+"_pending",true).apply();
        studyStage=StudyPlanLogic.Stage.valueOf(group.stage);
        forcedStep=new StudyPlanLogic.Step(group.start,group.end,"Связка "+(group.start+1)+"–"+group.end);
        stepCursor=0;resetWordTiles();mask=0;draft="";checked=false;passed=false;
        lessonVariant=0;lessonChunk=0;
        testAssisted=false;lessonWriteHadFeedback=false;lessonPracticeAgain=false;
        lessonWriteVariant=0;groupBuildDone=false;groupIntroDone=false;practiceWeakFragment=false;weakFragmentText="";resetLessonTiles();
        trainingMode=TrainingMode.ORDER;screen=Screen.TRAIN;render();
    }
    /** Separate full-screen lesson: no card carousel or persistent app-tab bar. */
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
        activeMastered=store.isStepMastered(e.id,studyStage.name(),activeStep.start,activeStep.end);
        LessonCurriculumLogic.Phase phase=checked&&passed?
            LessonCurriculumLogic.Phase.COMPLETE:lessonPhase(e);
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
            final int bookId=bookForEntry(e);
            DeveloperModeController.open(this,e,
                chapter->bookId>0 && bible.isReady()?bible.chapter(bookId,chapter).size():0,
                store.masteryCount(e.id));
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
                final int bookId=bookForEntry(e);
                DeveloperModeController.open(this,e,
                    chapter->bookId>0 && bible.isReady()?bible.chapter(bookId,chapter).size():0,
                    store.masteryCount(e.id));
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
        int number=stepCursor+1;
        if(phase==LessonCurriculumLogic.Phase.INTRO){
            lessonPhaseHeader("Ознакомление",e);
            renderLessonIntro();
            mainAction=button("Продолжить",true,()->{
                if(groupMode)groupIntroDone=true;
                else store.finishExercise(e.id,activeStep.start,1);
                lessonVariant=0;lessonChunk=0;lessonWriteVariant=0;resetLessonTiles();mask=0;
                saveTrainingSession();render();
            });
        } else if(phase==LessonCurriculumLogic.Phase.BUILD){
            lessonPhaseHeader("Сборка",e);
            boolean complete=renderLessonBuild(e);
            mainAction=button(complete?"Продолжить":"Сначала собери фразу",complete,()->{
                if(lessonPicked>=lessonBuildRequired)advanceLessonBuild(e);
            });
            mainAction.setEnabled(complete);mainAction.setAlpha(complete?1f:0.5f);
            lessonBuildAction=mainAction;
        } else if(phase==LessonCurriculumLogic.Phase.WRITE){
            lessonPhaseHeader("Написание",e);
            renderLessonWriting();
            mainAction=button("Проверить",true,()->validateLessonWriting(e));
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
        TextView heading=displayText(groupMode?"Связка освоена!":"Стих освоен!",38);
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
        TextView continueButton=button("Продолжить",true,()->advanceAfterSuccess(e));
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
    private void advanceAfterSuccess(LocalStore.Entry e){
        final GroupLearningLogic.Candidate pending=!reviewMode&&!groupMode?
            pendingAfterCurrent(e,activeStep.start):null;
        if(reviewMode){advanceReviewSession();}
        else if(groupMode){
            getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit()
                .remove("linked_"+selectedEntry+"_pending").apply();
            groupMode=false;forcedStep=null;studyStage=StudyPlanLogic.Stage.VERSE;
            stepCursor=returnVerseCursor;resetTrainingStep(e);saveTrainingSession();
            GroupLearningLogic.Candidate more=pendingAfterCurrent(e,stepCursor);
            if(more!=null){openGroup(more);return;}
            if(stepCursor+1<e.lines().size()){
                stepCursor++;resetTrainingStep(e);saveTrainingSession();render();
            }else if(executingCollectionQueue&&collectionQueueCursor>=0){advanceStudyCollectionAfterGroups(e);}
            else if(cardStudyCursor>=0){advanceCardStudy();}
            else render();
        }else if(pending!=null)openGroup(pending);
        else if(activeStep.end<e.lines().size()){
            stepCursor=activeStep.end;studyStage=StudyPlanLogic.Stage.VERSE;
            resetTrainingStep(e);saveTrainingSession();render();
        }else if(executingCollectionQueue&&collectionQueueCursor>=0)advanceStudyCollectionAfterGroups(e);
        else if(cardStudyCursor>=0)advanceCardStudy();
        else onBackPressed();
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
        int chunks=Math.max(1,lessonChunks(source).size());
        int variants=Math.max(1,LessonCurriculumLogic.variants(
            ReviewLogic.words(texts(passage)).size()).size());
        int buildTotal=chunks*variants;
        int currentBuild=Math.max(0,lessonVariant*chunks+lessonChunk);
        int buildDone=currentBuild;
        if(phase==LessonCurriculumLogic.Phase.BUILD && lessonBuildRequired>0 &&
            lessonPicked>=lessonBuildRequired)buildDone++;
        int writingTotal=Math.max(1,writingVariants(e).size());
        // In a scheduled review the lesson legitimately starts at writing.
        boolean previouslyLearned=executingCollectionQueue &&
            studyStage==StudyPlanLogic.Stage.VERSE &&
            store.exerciseDone(e.id,activeStep.start)>=5;
        boolean writingOnly=!groupMode && (reviewMode || lessonPracticeAgain ||
            studyStage!=StudyPlanLogic.Stage.VERSE || previouslyLearned);
        return LessonHotbarLogic.calculate(phase.ordinal(),4,buildTotal,writingTotal,
            mask/25,buildDone,lessonWriteVariant,writingOnly);
    }
    /** Compact three-part task trail matching the approved visual reference. */
    private void renderLessonHotbar(LinearLayout top,LessonCurriculumLogic.Phase phase,
                                    LocalStore.Entry e){
        lessonHotbarCells.clear();
        final LessonHotbarLogic.Model model=lessonHotbarModel(phase,e);
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
        LessonHotbarLogic.Model model=lessonHotbarModel(phase,e);
        lessonHotbarCounter.setText(model.currentNumber+" / "+model.total);
        for(int g=0;g<3;g++){
            List<TextView> cells=lessonHotbarCells.get(g);
            if(cells.size()!=model.totals[g])continue;
            for(int i=0;i<cells.size();i++)
                paintLessonHotbarCell(cells.get(i),model.visual(g,i),model.ordinal(g,i),true);
        }
    }
    private LessonCurriculumLogic.Phase lessonPhase(LocalStore.Entry e){
        if(groupMode)return !groupIntroDone?LessonCurriculumLogic.Phase.INTRO:
            (groupBuildDone?LessonCurriculumLogic.Phase.WRITE:LessonCurriculumLogic.Phase.BUILD);
        boolean learnedBefore=executingCollectionQueue&&
            studyStage==StudyPlanLogic.Stage.VERSE &&
            store.exerciseDone(e.id,activeStep.start)>=5;
        return LessonCurriculumLogic.phase(store.exerciseDone(e.id,activeStep.start),
            reviewMode||lessonPracticeAgain||studyStage!=StudyPlanLogic.Stage.VERSE||learnedBefore);
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
        int book=bookForEntry(e);
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
        trainingMode=mask>0?TrainingMode.HIDE:TrainingMode.READ;
        updateBlurText();
        TextView hide=button(mask==100?"Показать все слова":
            "Скрыть "+(mask+25)+"% слов",false,()->{});
        hide.setOnClickListener(v->{
            mask=mask>=100?0:mask+25;
            trainingMode=mask>0?TrainingMode.HIDE:TrainingMode.READ;
            updateBlurText();
            refreshLessonPhaseProgress(LessonCurriculumLogic.Phase.INTRO,activeEntry);
            hide.setText(mask==100?"Показать все слова":"Скрыть "+(mask+25)+"% слов");
            saveTrainingSession();
        });
        add(content,hide,27);
        // No extra instructional caption: gesture behavior remains unchanged.
    }
    private void resetLessonTiles(){
        lessonPicked=0;lessonWrongTile=-1;lessonUsedTiles.clear();lessonSeed=new Random().nextLong();
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
    private List<String[]> lessonChunks(List<String> lines){
        return WordOrderLogic.chunksForBudget(lines,lessonMaxTiles());
    }
    private void updateLessonBuildView(String[] words,int[] missing){
        if(lessonBuildPrompt!=null)lessonBuildPrompt.setText(buildPromptText(words,missing,lessonPicked));
        if(lessonBuildCounter!=null)lessonBuildCounter.setText(lessonPicked+" / "+missing.length+" слов");
        if(lessonBuildError!=null)lessonBuildError.setText(lessonWrongTile>=0?
            "Неверное слово. Попробуй другое.":"");
        for(int i=0;i<lessonBuildTiles.size();i++){
            TextView tile=lessonBuildTiles.get(i);
            int idx=(Integer)tile.getTag();
            tile.setVisibility(lessonUsedTiles.contains(idx)?View.INVISIBLE:View.VISIBLE);
            boolean wrong=idx==lessonWrongTile;
            tile.setTextColor(wrong?RED:WHITE);
            tile.setBackground(outlined(wrong?0xFFF8D6D2:CARD_ALT,12,wrong?RED:BORDER));
        }
        if(lessonBuildAction!=null){
            boolean done=lessonPicked==missing.length;
            lessonBuildAction.setText(done?"Продолжить":"Сначала собери фразу");
            lessonBuildAction.setEnabled(done);lessonBuildAction.setAlpha(done?1f:0.5f);
        }
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
        int amount=GapRotationLogic.amount(tokens.length);
        int[] positions=GapRotationLogic.balancedPositions(counts,amount,lessonSeed);
        prefs.edit().putString(historyKey,GapRotationLogic.encodeCounts(counts)).apply();
        rotationExerciseKey=exerciseKey;rotationExerciseSeed=lessonSeed;
        rotationMask=GapRotationLogic.encodeMask(positions);
        saveTrainingSession();
        return positions;
    }
    private boolean renderLessonBuild(LocalStore.Entry e){
        lessonBuildAction=null;lessonBuildTiles.clear();
        List<String> src=new ArrayList<>();
        for(Bible.Verse v:e.lines().subList(activeStep.start,activeStep.end))src.add(v.text);
        List<String[]> chunks=lessonChunks(src);
        if(chunks.isEmpty())return true;
        lessonChunk=Math.min(lessonChunk,chunks.size()-1);
        String[] tokens=chunks.get(lessonChunk);
        List<LessonCurriculumLogic.Variant> variants=LessonCurriculumLogic.variants(
            ReviewLogic.words(texts(e.lines().subList(activeStep.start,activeStep.end))).size());
        lessonVariant=Math.min(lessonVariant,variants.size()-1);
        LessonCurriculumLogic.Variant kind=variants.get(lessonVariant);
        // The exercise itself illustrates the task. Omit redundant task/fragment captions.
        int[] missing=lessonMissingPositions(tokens,kind);
        lessonBuildRequired=missing.length;
        if(lessonPicked>missing.length || lessonUsedTiles.size()!=lessonPicked){
            lessonPicked=0;lessonUsedTiles.clear();
        }
        lessonBuildPrompt=txt("",22,WHITE,false);
        lessonBuildPrompt.setLineSpacing(dp(8),1.13f);
        add(content,lessonBuildPrompt,22);
        lessonBuildCounter=txt("",13,GREEN,true);add(content,lessonBuildCounter,17);
        WrapLayout choices=new WrapLayout(this,dp(9));
        int[] shuffled=WordOrderLogic.shuffledIndices(missing.length,lessonSeed);
        for(int index:shuffled){
            final int pressed=index;
            final int targetPos=missing[index];
            String caption=tokens[targetPos].replaceAll("^[^\\p{L}\\p{Nd}]+|[^\\p{L}\\p{Nd}]+$", "");
            TextView tile=wordTile(caption,CARD_ALT,WHITE,BORDER,()->{
                // The target and bank occupy fixed views. Never call render() on a tile tap.
                if(lessonUsedTiles.contains(pressed)||lessonPicked>=missing.length)return;
                int wanted=missing[lessonPicked];
                if(WordOrderLogic.comparable(tokens[wanted]).equals(WordOrderLogic.comparable(tokens[targetPos]))){
                    lessonPicked++;lessonUsedTiles.add(pressed);lessonWrongTile=-1;
                }else lessonWrongTile=pressed;
                updateLessonBuildView(tokens,missing);
                refreshLessonPhaseProgress(LessonCurriculumLogic.Phase.BUILD,e);
                saveTrainingSession();
            });
            tile.setTag(index);lessonBuildTiles.add(tile);choices.addView(tile);
        }
        add(content,choices,26);
        lessonBuildError=txt("",14,RED,false);add(content,lessonBuildError,12);
        updateLessonBuildView(tokens,missing);
        refreshLessonPhaseProgress(LessonCurriculumLogic.Phase.BUILD,e);
        return lessonPicked==missing.length;
    }
    private void advanceLessonBuild(LocalStore.Entry e){
        List<String> source=new ArrayList<>();
        for(Bible.Verse v:e.lines().subList(activeStep.start,activeStep.end))source.add(v.text);
        int chunkCount=lessonChunks(source).size();
        int variants=LessonCurriculumLogic.variants(ReviewLogic.words(texts(
            e.lines().subList(activeStep.start,activeStep.end))).size()).size();
        if(lessonChunk+1<chunkCount){lessonChunk++;resetLessonTiles();}
        else if(lessonVariant+1<variants){
            if(!groupMode)store.finishExercise(e.id,activeStep.start,2);
            lessonVariant++;lessonChunk=0;resetLessonTiles();
        } else {
            if(groupMode)groupBuildDone=true;
            else store.finishExercise(e.id,activeStep.start,3);
            lessonVariant=0;lessonChunk=0;lessonWriteVariant=0;resetLessonTiles();draft="";lastAccuracy=-1;
            lessonWriteHadFeedback=false;
        }
        saveTrainingSession();render();
    }
    private LinearLayout lessonErrorArea;
    private List<LessonCurriculumLogic.Variant> writingVariants(LocalStore.Entry e){
        int n=ReviewLogic.words(texts(e.lines().subList(activeStep.start,activeStep.end))).size();
        if(reviewMode||lessonPracticeAgain||practiceWeakFragment)
            return Collections.singletonList(LessonCurriculumLogic.Variant.FULL);
        return LessonCurriculumLogic.variants(n);
    }
    private String writingExpected(LocalStore.Entry e,LessonCurriculumLogic.Variant variant){
        String original=texts(e.lines().subList(activeStep.start,activeStep.end));
        if(practiceWeakFragment)return weakFragmentText;
        if(variant==LessonCurriculumLogic.Variant.FULL)return original;
        String[] tokens=original.trim().split("\\s+");
        int[] gaps=lessonMissingPositions(tokens,variant);
        StringBuilder response=new StringBuilder();
        for(int position:gaps){if(response.length()>0)response.append(' ');response.append(tokens[position]);}
        return response.toString();
    }
    /** Partial writing is edited directly in the sentence, not in a separate answer field. */
    private void renderLessonWriting(){
        List<LessonCurriculumLogic.Variant> variants=writingVariants(activeEntry);
        lessonWriteVariant=Math.min(lessonWriteVariant,variants.size()-1);
        LessonCurriculumLogic.Variant variant=variants.get(lessonWriteVariant);
        boolean full=variant==LessonCurriculumLogic.Variant.FULL;

        // The writing step does not need a separate task-number label.
        if(practiceWeakFragment){
            // Weak-fragment practice remains separate from the final independent check.
        }else if(full){
            // The verse reference remains visible while the full text is hidden.
        }else{
            renderInlineGapInputs(variant);
        }
        if(full){
            EditText answer=input(practiceWeakFragment?"Воспроизведи проблемную фразу…":"Напиши стих полностью…",true);
            forbidKeyboardHints(answer,true);
            answer.setMinLines(6);answer.setText(draft);
            answer.addTextChangedListener(new TextWatcher(){
                @Override public void beforeTextChanged(CharSequence x,int a,int b,int c){}
                @Override public void onTextChanged(CharSequence x,int a,int before,int count){
                    draft=x.toString();lastAccuracy=-1;
                    if(lessonErrorArea!=null)lessonErrorArea.removeAllViews();
                    saveTrainingSession();
                }
                @Override public void afterTextChanged(Editable x){}
            });
            add(content,answer,18);
        }
        lessonErrorArea=column();add(content,lessonErrorArea,12);
        // Verification is always explicitly triggered by the existing footer button.
    }
    private void renderInlineGapInputs(LessonCurriculumLogic.Variant variant){
        String source=texts(activeEntry.lines().subList(activeStep.start,activeStep.end));
        String[] tokens=source.trim().split("\\s+");
        int[] missing=lessonMissingPositions(tokens,variant);
        // A separator marks independently editable gaps, preserving spaces inside one accepted variant.
        inlineDrafts=InlineGapLogic.restore(draft,missing.length);
        WrapLayout inline=new WrapLayout(this,dp(6));
        inline.setPadding(0,dp(12),0,dp(12));
        int maxWidth=getResources().getDisplayMetrics().widthPixels-dp(58);
        inlineFields.clear();inlineExpected.clear();
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
            inlineExpected.add(expected);
            EditText field=new EditText(this);
            field.setTextColor(WHITE);field.setHintTextColor(MUTED);field.setTextSize(19);
            field.setSingleLine(true);forbidKeyboardHints(field,false);
            field.setPadding(dp(3),dp(6),dp(3),dp(6));
            field.setGravity(Gravity.CENTER_VERTICAL);
            // Android native underline: width is initially based on the original word,
            // but the input itself has no maximum length.
            field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
            field.setHint(" ");
            field.setText(inlineDrafts[gapIndex]);
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
                        while(next<fields.size() && !inlineDrafts[next].trim().isEmpty())next++;
                        if(next<fields.size())fields.get(next).requestFocus();
                        else {
                            field.clearFocus();
                            android.view.inputmethod.InputMethodManager keyboard=
                                (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                            if(keyboard!=null)keyboard.hideSoftInputFromWindow(field.getWindowToken(),0);
                        }
                        // The last space NEVER starts validation; only the footer button does.
                    }
                    inlineDrafts[gapIndex]=value;
                    field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
                    draft=InlineGapLogic.serialize(inlineDrafts);
                    float textPx=field.getPaint().measureText(value)+dp(25);
                    int baselineWidth = Math.min(maxWidth,
                        Math.max(dp(32), dp(12) * Math.max(1,
                            InlineGapLogic.letters(inlineExpected.get(gapIndex))) + dp(8)));
                    int width=Math.min(maxWidth,Math.max(baselineWidth,(int)Math.ceil(textPx)));
                    ViewGroup.LayoutParams lp=field.getLayoutParams();
                    if(lp.width!=width){lp.width=width;field.setLayoutParams(lp);}
                    if(lessonErrorArea!=null)lessonErrorArea.removeAllViews();
                    lastAccuracy=-1;saveTrainingSession();
                }
                @Override public void afterTextChanged(Editable x){}
            });
        }
        add(content,inline,18);
    }
    private static String weakPhrase(String expected,String actual){
        List<TypingDiffLogic.Issue> errors=TypingDiffLogic.issues(expected,actual);
        if(errors.isEmpty())return "";
        List<String> words=ReviewLogic.words(expected);
        if(words.isEmpty())return "";
        int where=0;
        String target=errors.get(0).expected;
        if(!target.isEmpty())for(int i=0;i<words.size();i++)if(words.get(i).equals(target)){
            where=i;break;
        }
        int a=Math.max(0,where-2),b=Math.min(words.size(),where+3);
        return String.join(" ",words.subList(a,b));
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
    private void validateLessonWriting(LocalStore.Entry e){
        List<LessonCurriculumLogic.Variant> variants=writingVariants(e);
        lessonWriteVariant=Math.min(lessonWriteVariant,variants.size()-1);
        LessonCurriculumLogic.Variant kind=variants.get(lessonWriteVariant);
        boolean full=kind==LessonCurriculumLogic.Variant.FULL;
        String original=writingExpected(e,kind);
        String entered=full?draft:InlineGapLogic.response(inlineDrafts);
        if(entered.trim().isEmpty()){toast("Напиши текст, затем нажми «Проверить»");return;}
        List<String> refs=new ArrayList<>();
        for(Bible.Verse line:e.lines().subList(activeStep.start,activeStep.end))refs.add(line.ref);
        boolean special=AcceptedTextLogic.matthew513(e.reference,refs);
        boolean exact=AcceptedTextLogic.exact(original,entered,special);
        original=AcceptedTextLogic.bestExpected(original,entered,special);
        int accuracy=exact?100:Math.min(99,ReviewLogic.accuracy(original,entered));
        if(practiceWeakFragment){
            if(!exact){
                if(lessonErrorArea!=null){lessonErrorArea.removeAllViews();showModernErrors(lessonErrorArea,original,entered);}
                return;
            }
            practiceWeakFragment=false;weakFragmentText="";inlineDrafts=new String[0];draft="";lessonWriteHadFeedback=false;
            lessonWriteVariant=LessonCurriculumLogic.variants(
                ReviewLogic.words(texts(e.lines().subList(activeStep.start,activeStep.end))).size()).size()-1;
            if(reviewMode)lessonWriteVariant=0;
            saveTrainingSession();render();
            toast("Фрагмент отработан. Напиши весь стих заново без подсказок.");
            return;
        }
        if(exact && !full){
            lessonWriteVariant++;inlineDrafts=new String[0];draft="";lastAccuracy=-1;lessonWriteHadFeedback=false;
            lessonSeed=new Random().nextLong();saveTrainingSession();render();return;
        }
        long now=System.currentTimeMillis();
        if(!exact && !full){
            for(int i=0;i<inlineFields.size()&&i<inlineExpected.size()&&i<inlineDrafts.length;i++){
                boolean ok=LessonCurriculumLogic.exact(inlineExpected.get(i),inlineDrafts[i]);
                inlineFields.get(i).setBackgroundTintList(android.content.res.ColorStateList.valueOf(ok?GREEN:RED));
            }
            if(lessonErrorArea!=null){lessonErrorArea.removeAllViews();
                showModernErrors(lessonErrorArea,original,entered);
            }
            return;
        }
        if(!exact){
            weakVerse=-1;weakTransitionFrom=-1;
            if(!(reviewMode && currentReview!=null && "VERSE_RUN".equals(currentReview.stage)))
                store.recordRecall(e.id,studyStage.name(),activeStep.start,activeStep.end,accuracy,false,now);
            lessonWriteHadFeedback=true;
            saveTrainingSession();
            lastAccuracy=accuracy;lastExpected=original;
            if(activeStep.end-activeStep.start>1){
                List<String> sources=new ArrayList<>();
                for(Bible.Verse v:e.lines().subList(activeStep.start,activeStep.end))sources.add(v.text);
                WeakTransitionLogic.WeakSpot weak=WeakTransitionLogic.locate(sources,entered);
                weakVerse=activeStep.start+weak.verseIndex;
                weakTransitionFrom=weak.transitionFrom<0?-1:activeStep.start+weak.transitionFrom;
                if(weakVerse>=activeStep.start && weak.errors>0){
                    store.rescheduleWeakVerse(e.id,weakVerse,now);
                    if(weakTransitionFrom>=0)
                        store.recordWeakTransition(e.id,weakTransitionFrom,weakVerse,now);
                }
            }
            if(lessonErrorArea!=null){
                lessonErrorArea.removeAllViews();
                showModernErrors(lessonErrorArea,original,entered);
                final String weakPart=weakPhrase(original,entered);
                if(!weakPart.isEmpty())action(lessonErrorArea,"Потренировать ошибочный фрагмент",false,()->{
                    weakFragmentText=weakPart;practiceWeakFragment=true;draft="";
                    saveTrainingSession();render();
                });
                if(weakVerse>=0){
                    add(lessonErrorArea,txt("Слабый участок: "+e.lines().get(weakVerse).ref+
                        (weakTransitionFrom>=0?" (переход "+e.lines().get(weakTransitionFrom).ref+
                        " → "+e.lines().get(weakVerse).ref+")":""),14,MUTED,false),10);
                }
            }
            if(reviewMode && currentReview!=null && "VERSE_RUN".equals(currentReview.stage)){
                // A failed combined attempt is attributed only to the weak verse.
                // Other due verses remain due; none is falsely marked as successfully recalled.
                if(weakVerse>=activeStep.start && weakVerse<activeStep.end){
                    store.recordRecall(e.id,"VERSE",weakVerse,weakVerse+1,0,false,now);
                    store.scheduleReview(e.id,"VERSE",weakVerse,weakVerse+1,0,false,now);
                }
            } else if(reviewMode || groupMode || studyStage!=StudyPlanLogic.Stage.VERSE)
                store.scheduleReview(e.id,studyStage.name(),activeStep.start,activeStep.end,accuracy,false,now);
            return;
        }
        if(lessonWriteHadFeedback){
            // Correcting a visible diff is practice, not unaided recall. Demand a fresh clean attempt.
            store.recordRecall(e.id,studyStage.name(),activeStep.start,activeStep.end,100,true,now);
            lessonWriteHadFeedback=false;draft="";lastAccuracy=-1;saveTrainingSession();
            render();
            toast("Исправлено. Теперь напиши стих заново без подсказок для зачёта.");
            return;
        }
        if(reviewMode && currentReview!=null && "VERSE_RUN".equals(currentReview.stage)){
            for(int i=activeStep.start;i<activeStep.end;i++){
                store.recordRecall(e.id,"VERSE",i,i+1,100,false,now);
                store.scheduleReview(e.id,"VERSE",i,i+1,100,false,now);
            }
        }else{
            store.recordRecall(e.id,studyStage.name(),activeStep.start,activeStep.end,100,false,now);
            store.scheduleReview(e.id,studyStage.name(),activeStep.start,activeStep.end,100,false,now);
            if(!store.isStepMastered(e.id,studyStage.name(),activeStep.start,activeStep.end))
                store.markMastered(e.id,activeStep.start,activeStep.end,100,studyStage.name());
        }
        if(!reviewMode&&!groupMode&&studyStage==StudyPlanLogic.Stage.VERSE)
            store.finishExercise(e.id,activeStep.start,5);
        lessonPracticeAgain=false;draft="";lastAccuracy=100;passed=true;checked=true;
        if(reviewMode)getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit()
            .remove("review_pending").apply();
        if(!reviewMode&&!groupMode)saveTrainingSession();
        render();
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
        EditText name=input("Например: Нагорная проповедь",false);
        LinearLayout wrap=column();wrap.setPadding(dp(19),dp(12),dp(19),dp(9));add(wrap,name,0);
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Новая коллекция").setView(wrap)
            .setPositiveButton("Создать",(d,w)->{
                if(value(name).isEmpty()){toast("Введите название коллекции");return;}
                boolean firstCollection=store.unifiedCollections().isEmpty();
                long id=store.createUnifiedCollection(value(name));
                // Only the first collection is selected implicitly. All subsequent
                // changes of the active collection happen through Home's selector.
                if(firstCollection)activateUnified(id);
                LocalStore.UnifiedCollection u=store.unifiedCollection(id);
                if(after!=null)after.accept(u);else{openUnifiedId=id;screen=Screen.COLLECTION_DETAIL;render();}
            }).setNegativeButton("Отмена",null).create());
    }
    private void showUnifiedSelector(){
        List<LocalStore.UnifiedCollection> collections=store.unifiedCollections();
        String[] items=new String[collections.size()+1];
        for(int i=0;i<collections.size();i++)items[i]=
            (collections.get(i).id==activeUnifiedId?"✓  ":"")+collections.get(i).title;
        items[collections.size()]="＋ Создать коллекцию";
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Выбрать коллекцию").setItems(items,(dialog,which)->{
                if(which==collections.size())newUnifiedCollection(u->{
                    activateUnified(u.id);screen=Screen.HOME;render();
                });
                else{activateUnified(collections.get(which).id);render();}
            }).setNegativeButton("Отмена",null).create());
    }
    private void renderUnifiedHomePanel(){
        LinearLayout panel=card(content);panel.setBackground(homeSurface());
        LocalStore.UnifiedCollection active=activeUnified();
        if(active==null){
            add(panel,displayText("Пока нет коллекций",22),0);
            action(panel,"＋ Создать первую коллекцию",true,()->newUnifiedCollection(null));
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
        for(LocalStore.Entry e:store.entries(active.groupId)){
            List<Bible.Verse> lines=e.lines();
            for(int i=0;i<lines.size();i++){
                String identity=runVerseKey(e,lines.get(i));
                if(!seenVerses.add(identity))continue;
                verses++;
                if(store.isStepMastered(e.id,"VERSE",i,i+1) ||
                    store.exerciseDone(e.id,i)>=5)learned++;
            }
        }
        add(panel,txt(verses+" стихов · "+learned+" предварительно освоено",12,MUTED,false),8);
        if(verses>0)progress(panel,learned,verses);
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
        LocalStore.UnifiedCollection u=activeUnified();if(u==null){newUnifiedCollection(null);return;}
        if(bible.isReady())store.syncUnifiedCollection(u.id,bible);
        if(store.entries(u.groupId).isEmpty()){openUnifiedId=u.id;screen=Screen.COLLECTION_DETAIL;render();
            toast("Добавь стихи в коллекцию");return;}
        selectedGroup=u.groupId;startStudyCollection(u.groupId);
    }
    private void startActiveCards(){
        LocalStore.UnifiedCollection u=activeUnified();if(u==null)return;
        if(bible.isReady())store.syncUnifiedCollection(u.id,bible);
        selectedDeck=u.deckId;cardFromUnified=true;
        if(eligibleSessionCards().isEmpty()){
            toast(!bible.isReady()?"Библия ещё загружается":"Сначала добавь стихи в коллекцию");return;
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
        for(LocalStore.UnifiedCollection u:store.unifiedCollections()){
            if(!u.title.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT)))continue;
            found=true;
            LinearLayout row=row();row.setPadding(dp(13),dp(14),dp(13),dp(14));
            row.setBackground(homeSurface());
            ImageView thumb=landscapeThumbnail(u.id);
            LinearLayout.LayoutParams imageP=new LinearLayout.LayoutParams(dp(59),dp(61));
            imageP.rightMargin=dp(13);row.addView(thumb,imageP);
            LinearLayout details=column();add(details,displayText(u.title,17),0);
            add(details,txt(u.entries+" отрывков · "+u.cards+" карточек",13,MUTED,false),5);
            if(u.id==activeUnifiedId)add(details,txt("●  Выбрана на главной",12,GREEN,true),4);
            row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=txt("›",22,GREEN,false);arrow.setGravity(Gravity.CENTER);
            row.addView(arrow,new LinearLayout.LayoutParams(dp(24),dp(48)));
            row.setClickable(true);row.setFocusable(true);
            row.setContentDescription("Открыть коллекцию "+u.title);
            row.setOnClickListener(v->{openUnifiedId=u.id;screen=Screen.COLLECTION_DETAIL;render();});
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
            if(store.entries(u.groupId).size()<2){
                toast("Для изменения порядка нужны хотя бы два отрывка");return;
            }
            selectedGroup=u.groupId;openUnifiedId=u.id;
            screen=Screen.REORDER_STUDY;render();
        });
        unifiedSettingRow(settings,"Очистить коллекцию","eraser",true,()->clearUnified(u));
        unifiedSettingRow(settings,"Удалить коллекцию","trash",true,()->deleteUnified(u));
        add(content,settings,14);
    }
    private void clearUnified(LocalStore.UnifiedCollection u){
        if(u.entries==0&&u.cards==0){toast("Коллекция уже пуста");return;}
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Очистить коллекцию?")
            .setMessage("Все стихи, отрывки и карточки будут убраны из этой коллекции. " +
                "Саму коллекцию и прежние результаты заучивания сохраним. " +
                "Отменить очистку нельзя.")
            .setPositiveButton("Очистить",(d,w)->{
                store.clearUnifiedCollection(u.id);
                String run=runKey(u.id);
                getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE).edit()
                    .remove("run_history_"+u.id).remove(run+"active")
                    .remove(run+"cursor").remove(run+"correct")
                    .remove(run+"draft").remove(run+"checked")
                    .remove(run+"passed").remove(run+"missed").apply();
                if(runCollectionId==u.id){runLines.clear();runCollectionId=-1;}
                render();toast("Коллекция очищена");
            }).setNegativeButton("Отмена",null).create());
    }
    private void renameUnified(LocalStore.UnifiedCollection u){
        EditText name=input("Название коллекции",false);name.setText(u.title);
        LinearLayout box=column();box.setPadding(dp(18),dp(10),dp(18),dp(8));add(box,name,0);
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Переименовать коллекцию").setView(box)
            .setPositiveButton("Сохранить",(d,w)->{
                if(value(name).isEmpty()){toast("Укажи название");return;}
                store.renameUnifiedCollection(u.id,value(name));render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void deleteUnified(LocalStore.UnifiedCollection u){
        styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
            .setTitle("Удалить коллекцию из списка?")
            .setMessage("Коллекция исчезнет из списка. Стихи и прежний прогресс изучения сохранятся в локальной базе.")
            .setPositiveButton("Удалить",(d,w)->{
                store.deleteUnifiedCollection(u.id);ensureActiveUnified();
                screen=Screen.COLLECTIONS;render();
            }).setNegativeButton("Отмена",null).create());
    }
    private void renderUnifiedCollectionDetail(){
        LocalStore.UnifiedCollection u=store.unifiedCollection(openUnifiedId);
        if(u==null){screen=Screen.COLLECTIONS;render();return;}
        selectedGroup=u.groupId;selectedDeck=u.deckId;
        TextView heading=displayText(u.title,25);
        heading.setGravity(Gravity.CENTER);heading.setMaxLines(2);
        add(content,heading,5);
        showUnifiedCollectionSettings(u);
        helper(content,"Освоено: "+studyProgressFor(u.groupId));
        try{
            JSONArray history=new JSONArray(getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE)
                .getString("run_history_"+u.id,"[]"));
            if(history.length()>0){JSONObject last=history.getJSONObject(0);
                helper(content,"Последняя проверка памяти: "+last.optInt("correct")+
                    " из "+last.optInt("total")+" без ошибок");}
        }catch(JSONException ignored){}
        List<LocalStore.Entry> entries=store.entries(u.groupId);
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
            final LocalStore.UnifiedCollection u=store.unifiedCollection(openUnifiedId);
            if(u==null)return;
            TextView addBible=button("＋  Добавить из Библии",true,()->{
                clearInsertionContext();
                openBiblePicker(BibleTarget.STUDY_GROUP,u.groupId,Screen.COLLECTION_DETAIL);
            });
            footer.addView(addBible,new LinearLayout.LayoutParams(-1,dp(51)));
            TextView own=button("＋  Добавить свой текст",false,()->{
                customTargetGroupId=u.groupId;screen=Screen.CUSTOM;render();
            });
            LinearLayout.LayoutParams ownP=new LinearLayout.LayoutParams(-1,dp(49));
            ownP.topMargin=dp(8);footer.addView(own,ownP);
        }else return;
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
    }

    // ===== Independent writing run: diagnostic only, never modifies lesson/review tables. =====
    private String runKey(long collection){return "writing_run_"+collection+"_";}
    private static String runVerseKey(LocalStore.Entry e,Bible.Verse v){
        boolean canonical=false;
        for(String book:Bible.NAMES)if(e.reference.startsWith(book+" ")){canonical=true;break;}
        String bookName="";
        if(canonical)for(String book:Bible.NAMES)if(e.reference.startsWith(book+" ")){
            bookName=book;break;
        }
        return canonical?bookName+"|"+v.ref+"|"+v.text:
            "custom:"+e.id+":"+v.ref;
    }
    private void buildRunLines(LocalStore.UnifiedCollection u){
        runLines.clear();
        Map<String,Boolean> confirmed=new HashMap<>();
        for(LocalStore.Entry e:store.allEntries()){
            List<Bible.Verse> lines=e.lines();
            for(int i=0;i<lines.size();i++)if(store.consolidated(e.id,"VERSE",i,i+1))
                confirmed.put(runVerseKey(e,lines.get(i)),true);
        }
        Set<String> seen=new HashSet<>();
        for(LocalStore.Entry e:store.entries(u.groupId)){
            int book=bookForEntry(e);
            List<Bible.Verse> lines=e.lines();
            for(int i=0;i<lines.size();i++){
                Bible.Verse line=lines.get(i);String key=runVerseKey(e,line);
                if(!seen.add(key) || !confirmed.containsKey(key))continue;
                String reference=book>0?Bible.NAMES[book-1]+" "+line.ref:
                    e.reference+(lines.size()>1?" · "+(i+1):"");
                runLines.add(new RunLine(e.id,i,reference,line.text));
            }
        }
    }
    private void startActiveRun(){
        LocalStore.UnifiedCollection u=activeUnified();if(u==null)return;
        buildRunLines(u);
        if(runLines.isEmpty()){
            toast("В коллекции пока нет стихов, закреплённых отложенным повторением");return;
        }
        runCollectionId=u.id;
        android.content.SharedPreferences p=getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE);
        if(p.getBoolean(runKey(u.id)+"active",false)){
            styledDialog(new AlertDialog.Builder(this,R.style.MemoryDialogTheme)
                .setTitle("Незавершённая проверка памяти")
                .setMessage("Продолжить с сохранённого стиха или начать проверку заново?")
                .setPositiveButton("Продолжить",(d,w)->{restoreRunSession();screen=Screen.RUN;render();})
                .setNegativeButton("Сначала",(d,w)->{resetRun();screen=Screen.RUN;render();}).create());
        }else{resetRun();screen=Screen.RUN;render();}
    }
    private void resetRun(){
        runCursor=0;runCorrect=0;runMistakes.clear();runChecked=false;runPassed=false;runDraft="";
        saveRunSession();
    }
    private void restoreRunSession(){
        String key=runKey(runCollectionId);
        android.content.SharedPreferences p=getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE);
        runCursor=Math.min(Math.max(0,p.getInt(key+"cursor",0)),Math.max(0,runLines.size()-1));
        runCorrect=p.getInt(key+"correct",0);
        runDraft=p.getString(key+"draft","");
        runChecked=p.getBoolean(key+"checked",false);
        runPassed=p.getBoolean(key+"passed",false);
        runMistakes.clear();
        try{JSONArray arr=new JSONArray(p.getString(key+"missed","[]"));
            for(int i=0;i<arr.length();i++)runMistakes.add(arr.getString(i));
        }catch(Exception ignored){}
    }
    private void saveRunSession(){
        if(runCollectionId<0||runLines.isEmpty())return;
        String key=runKey(runCollectionId);
        getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE).edit()
            .putBoolean(key+"active",true).putInt(key+"cursor",runCursor)
            .putInt(key+"correct",runCorrect).putString(key+"draft",runDraft)
            .putBoolean(key+"checked",runChecked).putBoolean(key+"passed",runPassed)
            .putString(key+"missed",new JSONArray(runMistakes).toString()).apply();
    }
    private void finishRunLine(){
        if(runLines.isEmpty())return;
        RunLine line=runLines.get(runCursor);
        if(runChecked&&runPassed)runCorrect++;
        else runMistakes.add(line.reference);
        runCursor++;runDraft="";runChecked=false;runPassed=false;
        if(runCursor>=runLines.size()){
            saveCompletedRun();
            getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE).edit()
                .remove(runKey(runCollectionId)+"active").apply();
            screen=Screen.RUN_SUMMARY;render();
        }else{saveRunSession();render();}
    }
    private void saveCompletedRun(){
        try{
            android.content.SharedPreferences p=getSharedPreferences(UNIFIED_PREFS,MODE_PRIVATE);
            String historyKey="run_history_"+runCollectionId;
            JSONArray history=new JSONArray(p.getString(historyKey,"[]"));
            JSONObject result=new JSONObject();
            result.put("timestamp",System.currentTimeMillis());
            result.put("total",runLines.size());result.put("correct",runCorrect);
            result.put("mistakes",new JSONArray(runMistakes));
            JSONArray updated=new JSONArray();updated.put(result);
            for(int i=0;i<Math.min(history.length(),19);i++)updated.put(history.getJSONObject(i));
            p.edit().putString(historyKey,updated.toString()).apply();
        }catch(JSONException ignored){}
    }
    private void renderWritingRun(){
        if(runLines.isEmpty()){screen=Screen.HOME;render();return;}
        runCursor=Math.min(runCursor,runLines.size()-1);
        RunLine line=runLines.get(runCursor);
        root=column();root.setBackground(pageBackground());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout top=row();top.setPadding(dp(17),dp(12),dp(17),dp(7));
        TextView exit=txt("×",30,WHITE,false);exit.setGravity(Gravity.CENTER);
        exit.setContentDescription("Сохранить проверку памяти и выйти");
        exit.setOnClickListener(v->onBackPressed());
        top.addView(exit,new LinearLayout.LayoutParams(dp(43),dp(46)));
        TextView count=txt((runCursor+1)+" из "+runLines.size(),15,MUTED,true);
        count.setGravity(Gravity.CENTER);
        top.addView(count,new LinearLayout.LayoutParams(0,dp(45),1));
        root.addView(top,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        content=column();content.setPadding(dp(21),dp(18),dp(21),dp(24));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        progress(content,runCursor,runLines.size());
        TextView heading=displayText(line.reference,24);heading.setGravity(Gravity.CENTER);
        add(content,heading,20);
        TextView instruction=txt("Напиши весь стих по памяти",16,MUTED,false);
        instruction.setGravity(Gravity.CENTER);add(content,instruction,12);
        EditText write=input("Начни писать стих…",true);
        forbidKeyboardHints(write,true);write.setMinLines(5);
        write.setText(runDraft);write.setSelection(write.length());
        write.setEnabled(!runChecked);
        write.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){
                runDraft=s.toString();saveRunSession();
            }
            public void afterTextChanged(Editable e){}
        });
        add(content,write,22);
        if(runChecked){
            add(content,txt(runPassed?"✓ Без ошибок":"Обнаружены ошибки",20,
                runPassed?SUCCESS:RED,true),16);
            if(!runPassed){
                add(content,typedFeedback(line.text,runDraft),9);
                label(content,"Правильный текст");add(content,prose(line.text,17),8);
            }
        }
        LinearLayout footer=column();footer.setPadding(dp(20),dp(10),dp(20),dp(20));
        footer.setBackgroundColor(CARD);
        TextView go=button(runChecked?"Следующий стих": "Проверить",true,()->{
            if(runChecked){finishRunLine();return;}
            if(runDraft.trim().isEmpty()){toast("Введи стих или выбери «Пропустить»");return;}
            runPassed=AcceptedTextLogic.exact(line.text,runDraft,false);
            runChecked=true;saveRunSession();render();
        });
        footer.addView(go,new LinearLayout.LayoutParams(-1,dp(55)));
        if(!runChecked){
            TextView skip=button("Пропустить стих",false,this::finishRunLine);
            footer.addView(skip,new LinearLayout.LayoutParams(-1,dp(47)));
        }
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);lastRenderedScreen=screen;
    }
    private void renderRunSummary(){
        title("Проверка памяти завершена",null);
        LinearLayout report=headerCard(content);
        add(report,txt(runCorrect+" из "+runLines.size()+" стихов без ошибок",22,WHITE,true),0);
        progress(report,runCorrect,runLines.size());
        if(!runMistakes.isEmpty()){
            label(content,"Требуют внимания");
            for(String ref:runMistakes)add(content,txt("• "+ref,16,RED,false),7);
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
                byte[] json=store.exportJson().toString(2).getBytes(StandardCharsets.UTF_8);
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
                store.importJson(buffer.toString("UTF-8"));
                toast("Подборки импортированы");render();
            }
        }catch(Exception ex){new AlertDialog.Builder(this,R.style.MemoryDialogTheme).setTitle("Ошибка файла")
            .setMessage(ex.getMessage()).setPositiveButton("OK",null).show();}
    }
    @Override protected void onDestroy(){homeHandler.removeCallbacks(homeTicker);super.onDestroy();if(store!=null)store.close();}
}
