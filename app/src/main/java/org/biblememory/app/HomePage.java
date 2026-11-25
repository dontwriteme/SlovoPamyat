package org.biblememory.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;
import static org.biblememory.app.ScreenUi.*;

final class HomePage {
    static final class Actions {
        final Runnable create,select,study,cards,review,check,repeat;
        Actions(Runnable create,Runnable select,Runnable study,Runnable cards,
                Runnable review,Runnable check,Runnable repeat){
            this.create=create;this.select=select;this.study=study;this.cards=cards;
            this.review=review;this.check=check;this.repeat=repeat;
        }
    }

    private final Context context;
    private final ScreenUi ui;
    private final ScreenArtwork artwork;
    private final Bible bible;
    private final ModeInfoPopover modeInfo;
    private final Runnable onVerseChanged;

    HomePage(Context context,ScreenUi ui,ScreenArtwork artwork,Bible bible,
             ModeInfoPopover modeInfo,Runnable onVerseChanged){
        this.context=context;this.ui=ui;this.artwork=artwork;this.bible=bible;
        this.modeInfo=modeInfo;this.onVerseChanged=onVerseChanged;
    }

    void open(){
        SharedPreferences prefs=context.getSharedPreferences(HOME_UI_PREFS,Context.MODE_PRIVATE);
        homeCollectionsExpanded=prefs.getBoolean("collections_expanded",false);
        homeScrollY=Math.max(0,prefs.getInt("scroll_y",0));
        refresh(false);
    }

    int index(){return homeIndex;}

    void rememberScroll(ScrollView scroller){
        if(!homeScrollRestored||scroller==null)return;
        homeScrollY=Math.max(0,scroller.getScrollY());
        context.getSharedPreferences(HOME_UI_PREFS,Context.MODE_PRIVATE)
            .edit().putInt("scroll_y",homeScrollY).apply();
    }

    void restoreScroll(ScrollView scroller,BooleanSupplier current,boolean fresh){
        if(fresh)homeScrollRestored=false;
        int destination=homeScrollY;
        scroller.post(()->{
            if(!current.getAsBoolean())return;
            scroller.scrollTo(0,fresh?destination:homeScrollY);
            homeScrollRestored=true;
        });
    }

    void pause(){homeHandler.removeCallbacks(homeTicker);}
    private void tick(){refresh(true);schedule();}
    void cancelAnimation(){
        if(collectionAnimator!=null){collectionAnimator.cancel();collectionAnimator=null;}
    }

    private static final String HOME_UI_PREFS="home_ui";
    private boolean homeCollectionsExpanded=false;
    private int homeScrollY=0;
    private boolean homeScrollRestored=false;
    private ValueAnimator collectionAnimator;

    private int homeIndex=0;
    private static final int[][] HOME_REFS={{19,118,11},{40,5,14},{43,14,6},{45,12,21},{50,4,13},{40,6,33}};
    private final Handler homeHandler=new Handler(Looper.getMainLooper());
    private final Runnable homeTicker=()->{tick();};
    void schedule() {
        homeHandler.removeCallbacks(homeTicker);
        long last=context.getSharedPreferences("home_verse",Context.MODE_PRIVATE)
            .getLong("last_switch",System.currentTimeMillis());
        homeHandler.postDelayed(homeTicker,HomeVerseLogic.nextDelay(last,System.currentTimeMillis())+80L);
    }
    void refresh(boolean rerender) {
        android.content.SharedPreferences prefs=context.getSharedPreferences("home_verse",Context.MODE_PRIVATE);
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
        if(rerender&&homeIndex!=old)onVerseChanged.run();
    }
    /** Restored verse-of-the-day hierarchy, over a light, readable scenic backdrop. */



    private void homeVerseHero(
            LinearLayout content,
            int scene,
            String reference,
            String verseText) {

        // Карточка имеет высоту по содержимому.
        FrameLayout hero = new FrameLayout(context);

        final android.graphics.drawable.Drawable landscape =
                context.getResources().getDrawable(
                        artwork.landscapeFor(scene),
                        context.getTheme()
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



    void render(LinearLayout content,LocalStore.UnifiedCollection active,CollectionProgressSnapshot snapshot,
                ToIntFunction<LocalStore.Entry> bookForEntry,Actions actions) {
        int[] vr=HOME_REFS[Math.floorMod(homeIndex,HOME_REFS.length)];
        List<Bible.Verse> homeLines=bible.isReady()?bible.chapter(vr[0],vr[1]):Collections.emptyList();
        String verseText=homeIndex==0?"В сердце моем сокрыл я слово Твое, чтобы не грешить пред Тобою.":"Загружаем стих…";
        if(!homeLines.isEmpty())for(Bible.Verse line:homeLines){
            if(line.ref.equals(vr[1]+":"+vr[2])){verseText=line.text;break;}
        }
        homeVerseHero(content,homeIndex,Bible.NAMES[vr[0]-1]+" "+vr[1]+":"+vr[2],verseText);
        renderPanel(content,active,snapshot,bookForEntry,actions);
    }

    private void renderPanel(LinearLayout content,LocalStore.UnifiedCollection active,
                             CollectionProgressSnapshot snapshot,ToIntFunction<LocalStore.Entry> bookForEntry,Actions actions){
        LinearLayout panel=ui.card(content);panel.setBackground(artwork.homeSurface());
        if(active==null){
            ui.add(panel,ui.displayText("Пока нет коллекций",22),0);
            ui.action(panel,"＋ Создать первую коллекцию",true,actions.create);
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
        NavigationGlyph cycle=new NavigationGlyph(context,"refresh",GREEN);
        cycle.setContentDescription("Сменить коллекцию");
        LinearLayout.LayoutParams cycleP=new LinearLayout.LayoutParams(ui.dp(30),ui.dp(30));
        cycleP.leftMargin=ui.dp(10);chooser.addView(cycle,cycleP);
        chooser.setContentDescription("Выбранная коллекция: "+active.title+". Нажми, чтобы сменить.");
        chooser.setClickable(true);chooser.setFocusable(true);
        chooser.setOnClickListener(v->actions.select.run());
        ui.add(panel,chooser,0);
        int verses=0,learned=0;
        Set<List<String>> seenVerses=new HashSet<>();
        for(LocalStore.Entry e:snapshot.entries(active.groupId)){
            List<Bible.Verse> lines=snapshot.verses(e.id);
            int book=bookForEntry.applyAsInt(e);
            for(int i=0;i<lines.size();i++){
                if(!seenVerses.add(KnowledgeCheckMaterial.identity(e,book,i,lines.get(i))))continue;
                verses++;
                if(snapshot.exerciseDone(e.id,i)>=5)learned++;
            }
        }
        addHelpLabel(panel,ui.txt(verses+" стихов · "+learned+" предварительно освоено",12,MUTED,false),
            LearningHelp.PROGRESS,8);
        if(verses>0)ui.progress(panel,learned,verses);
        unifiedModeRow(panel,LearningHelp.STUDY,"mode-book",actions.study);
        unifiedModeRow(panel,LearningHelp.CARDS,"mode-cards",actions.cards);
        unifiedModeRow(panel,LearningHelp.REVIEW,"mode-brain",actions.review);
        unifiedModeRow(panel,LearningHelp.MEMORY,"mode-check",actions.check);
        unifiedModeRow(panel,LearningHelp.ALL_REVIEWS,"mode-repeat",actions.repeat);
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
        launch.addView(new NavigationGlyph(context,icon,WHITE),iconParams);
        launch.addView(ui.txt(name,16,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        item.addView(launch,new LinearLayout.LayoutParams(0,-2,1));

        item.addView(helpIcon(help,"О режиме «"+name+"»"),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        ui.add(panel,item,9);
    }

    private View helpIcon(LearningHelp help,String description){
        View info=new NavigationGlyph(context,"info",0xFF1976D2);
        info.setContentDescription(description);
        info.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        info.setFocusable(true);
        info.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x241976D2),null,ui.shape(Color.WHITE,24)));
        info.setOnClickListener(v->modeInfo.show(v,help));
        return info;
    }

    private final class ChevronView extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        ChevronView(boolean open){super(context);
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

    void renderCollectionList(LinearLayout content,List<LocalStore.Group> groups,Runnable create,
                              java.util.function.LongConsumer open){
        LinearLayout section=ui.column();ui.add(content,section,13);
        LinearLayout controls=ui.row();ui.add(section,controls,0);
        LinearLayout heading=ui.row();heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(ui.dp(12),ui.dp(5),ui.dp(9),ui.dp(5));
        heading.setBackground(artwork.homeSurface());
        heading.setElevation(ui.dp(1));
        NavigationGlyph folder=new NavigationGlyph(context,"cards",0xFFAC684C);
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
        plus.setOnClickListener(v->create.run());
        controls.addView(plus,new LinearLayout.LayoutParams(ui.dp(55),ui.dp(55)));
        FrameLayout clip=new FrameLayout(context);clip.setClipChildren(true);
        LinearLayout collectionList=ui.column();collectionList.setPadding(0,0,0,ui.dp(4));
        // One readable compact list, part of the main page's single scroll surface.
        for(LocalStore.Group group:groups){
            LinearLayout tile=ui.row();tile.setPadding(ui.dp(9),ui.dp(8),ui.dp(8),ui.dp(8));
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setMinimumHeight(ui.dp(72));
            tile.setBackground(artwork.homeSurface());
            ImageView thumb=artwork.landscapeThumbnail(group.id);
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
            tile.setOnClickListener(v->open.accept(group.id));
            ui.add(collectionList,tile,7);
        }
        clip.addView(collectionList,new FrameLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,
            homeCollectionsExpanded?-2:0);cp.topMargin=ui.dp(2);section.addView(clip,cp);
        heading.setClickable(true);heading.setFocusable(true);
        heading.setContentDescription("Мои коллекции, "+(homeCollectionsExpanded?"свернуть":"раскрыть"));
        heading.setOnClickListener(v->{
            cancelAnimation();
            homeCollectionsExpanded=!homeCollectionsExpanded;
            arrow.setOpen(homeCollectionsExpanded);
            heading.setContentDescription("Мои коллекции, "+(homeCollectionsExpanded?"свернуть":"раскрыть"));
            context.getSharedPreferences(HOME_UI_PREFS,Context.MODE_PRIVATE).edit()
                .putBoolean("collections_expanded",homeCollectionsExpanded).apply();
            animateHomeCollections(clip,collectionList,homeCollectionsExpanded);
        });
    }

    static String collectionCountNoun(int count){
        int mod=count%100,unit=count%10;
        return mod>=11&&mod<=14?"отрывков":unit==1?"отрывок":
            unit>=2&&unit<=4?"отрывка":"отрывков";
    }
    /** Animates only the collection container, never recreating/scrolling Home. */
    private void animateHomeCollections(FrameLayout clip,LinearLayout items,boolean expand){
        int availableWidth=clip.getWidth();
        if(availableWidth<=0)availableWidth=Math.max(ui.dp(200),
            context.getResources().getDisplayMetrics().widthPixels-ui.dp(38));
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
}
