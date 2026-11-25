package org.biblememory.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import static org.biblememory.app.ScreenUi.*;

final class BibleReader {
    private final Context context;
    private final ScreenUi ui;
    private final Bible bible;
    private int verse=1;
    private boolean fromVerseGrid;

    BibleReader(Context context, ScreenUi ui, Bible bible){
        this.context=context;
        this.ui=ui;
        this.bible=bible;
    }

    int verse(){return verse;}
    boolean fromVerseGrid(){return fromVerseGrid;}
    void resetVerse(){verse=1;}
    void selectVerse(int number){verse=number;fromVerseGrid=true;}
    void fromSearch(){fromVerseGrid=false;}

    int lastReadChapter(int book){
        int saved=context.getSharedPreferences("bible_last_chapter",Context.MODE_PRIVATE).getInt(String.valueOf(book),1);
        return Math.max(1,Math.min(Math.max(1,bible.chapterCount(book)),saved));
    }
    void rememberChapter(int book,int chapter){
        context.getSharedPreferences("bible_last_chapter",Context.MODE_PRIVATE).edit()
            .putInt(String.valueOf(book),chapter).apply();
    }

    static String bookGroupName(BookColorLogic.Group group){
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
    /** Section boundary is visually centered, with measured whitespace only at the canon split. */
    private void testamentHeading(LinearLayout content,String text,boolean startOfNewTestament){
        if(startOfNewTestament)ui.gap(content,28);
        LinearLayout row=ui.row();row.setGravity(Gravity.CENTER_VERTICAL);
        View left=new View(context),right=new View(context);
        left.setBackgroundColor(HOME_BORDER);right.setBackgroundColor(HOME_BORDER);
        row.addView(left,new LinearLayout.LayoutParams(0,ui.dp(1),1));
        TextView heading=ui.displayText(text,21);
        heading.setGravity(Gravity.CENTER);heading.setPadding(ui.dp(14),ui.dp(6),ui.dp(14),ui.dp(6));
        row.addView(heading,new LinearLayout.LayoutParams(-2,-2));
        row.addView(right,new LinearLayout.LayoutParams(0,ui.dp(1),1));
        ui.add(content,row,startOfNewTestament?3:13);
    }

    void renderBooks(LinearLayout content,IntConsumer openBook,IntFunction<View> bookAction){
        BookColorLogic.Group previous=null;
        LinearLayout groupedRows=null;
        for(int b=1;b<=66;b++){
            if(b==1)testamentHeading(content,"ВЕТХИЙ ЗАВЕТ",false);
            if(b==40)testamentHeading(content,"НОВЫЙ ЗАВЕТ",true);
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
            if(bookAction!=null){
                line.addView(bookAction.apply(book),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
            }else{
                TextView arrow=ui.txt("›",22,GREEN,false);arrow.setGravity(Gravity.CENTER);
                line.addView(arrow,new LinearLayout.LayoutParams(ui.dp(20),-2));
            }
            line.setMinimumHeight(ui.dp(48));
            line.setContentDescription(Bible.NAMES[b-1]);
            line.setOnClickListener(v->openBook.accept(book));
            groupedRows.addView(line,new LinearLayout.LayoutParams(-1,-2));
            if(b<66&&BookColorLogic.group(b+1)==group){
                View separator=new View(context);separator.setBackgroundColor(0xFFEADACE);
                groupedRows.addView(separator,new LinearLayout.LayoutParams(-1,ui.dp(1)));
            }
        }
    }


    void renderChapters(LinearLayout content,int count,int selected,boolean picker,
                        IntConsumer open,BiConsumer<Integer,TextView> configure){
        renderNumbers(content,count,selected,open,(number,button)->{
            if(picker){
                button.setPadding(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(4));
                button.setContentDescription("Глава "+number+(number==selected?", выбрана":""));
                if(configure!=null)configure.accept(number,button);
            }else if(number==selected)button.setContentDescription("Глава "+number+", последняя прочитанная");
        });
    }

    void renderVerseGrid(LinearLayout content,int count,int selected,boolean boundary,IntConsumer open){
        renderNumbers(content,count,selected,open,(number,button)->{
            button.setPadding(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(4));
            button.setContentDescription((boundary?"Выбрать стих ":"Открыть текст стиха ")+number+
                (number==selected?", выбран":""));
        });
    }

    private void renderNumbers(LinearLayout content,int count,int selected,IntConsumer open,
                               BiConsumer<Integer,TextView> configure){
        for(int base=1;base<=count;base+=4){
            LinearLayout row=ui.row();
            for(int k=base;k<base+4;k++){
                if(k>count){
                    row.addView(new View(context),new LinearLayout.LayoutParams(0,ui.dp(50),1));
                    continue;
                }
                final int number=k;
                TextView button=ui.button(String.valueOf(number),number==selected,()->open.accept(number));
                configure.accept(number,button);
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,ui.dp(50),1);
                params.setMargins(ui.dp(3),ui.dp(4),ui.dp(3),ui.dp(4));row.addView(button,params);
            }
            ui.add(content,row,2);
        }
    }

    boolean renderChapter(LinearLayout content,int book,List<Bible.Verse> verses,
                          Map<Integer,LocalStore.VerseStatus> statuses,int focusVerse,
                          Supplier<ScrollView> activeScroller,LongFunction<String> date){
        boolean focused=false;
        for(Bible.Verse verse:verses){
            final int verseNumber;
            try{verseNumber=Integer.parseInt(verse.ref.substring(verse.ref.indexOf(':')+1));}
            catch(Exception ex){continue;}
            boolean highlighted=verseNumber==focusVerse;
            LocalStore.VerseStatus stat=statuses.get(verseNumber);
            LinearLayout c=ui.card(content);
            if(highlighted){
                c.setBackground(ui.outlined(0xFFEDEBDD,20,SUCCESS));
                if(fromVerseGrid){
                    final ScrollView scroller=activeScroller.get();
                    afterLayout(c,()->{if(scroller!=null&&activeScroller.get()==scroller){
                        scroller.scrollTo(0,Math.max(0,c.getTop()-ui.dp(30)));
                        c.postDelayed(()->{
                            if(activeScroller.get()==scroller)c.setBackground(ui.outlined(CARD,22,BORDER));
                        },1100);
                    }});
                }else{
                    ScrollView scroller=activeScroller.get();
                    c.post(()->{
                        if(scroller==null||activeScroller.get()!=scroller)return;
                        int top=c.getTop();
                        if(c.getParent() instanceof View)top+=((View)c.getParent()).getTop();
                        scroller.smoothScrollTo(0,Math.max(0,top-ui.dp(30)));
                    });
                }
                focused=true;
            }
            LinearLayout head=ui.row();
            head.addView(ui.txt(verse.ref,14,ui.bookInk(book),true));
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
                        "Следующее повторение: "+date.apply(stat.nextDue),12,MUTED,false),4);
            }
        }
        return focused;
    }
    static void afterLayout(View view,Runnable action){
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){
            @Override public void onGlobalLayout(){
                if(view.getHeight()<=0)return;
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);action.run();
            }
        });
    }
}
