package org.biblememory.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.biblememory.app.ScreenUi.*;

final class PassagePicker {
    interface BookAction {
        void run(long group,int book,boolean remove);
    }

    private static final int BULK_INK=0xFF426747;
    private static final int BULK_SELECTED=0xFFE3EDD9;
    private final Context context;
    private final ScreenUi ui;
    private final Bible bible;
    private final PassageSelection passageSelection=new PassageSelection();
    private final Set<String> pendingBooks=new HashSet<>();
    private final Map<Integer,BookToggle> pickerBookActions=new HashMap<>();
    private final SortedSet<Integer> selectedPickerChapters=new TreeSet<>();
    private final Map<Integer,TextView> pickerChapterButtons=new HashMap<>();
    private boolean pickerChooseEnd;
    private boolean savingPickerChapters;
    private TextView pickerSelectionLabel,pickerSaveButton;
    private LinearLayout pickerSelectionPanel;
    private LinearLayout pickerChapterFooter;
    private TextView pickerChapterAdd,pickerChapterCancel;

    PassagePicker(Context context,ScreenUi ui,Bible bible){
        this.context=context;
        this.ui=ui;
        this.bible=bible;
    }

    PassageSelection selection(){return passageSelection;}
    void chooseEnd(boolean end){pickerChooseEnd=end;}
    int boundaryVerse(int chapter){
        int boundaryChapter=pickerChooseEnd?passageSelection.endChapter:passageSelection.startChapter;
        if(chapter!=boundaryChapter)return -1;
        return pickerChooseEnd?passageSelection.endVerse:passageSelection.startVerse;
    }

    void pickBoundaryVerse(int chapter,int verse){
        passageSelection.setBoundary(pickerChooseEnd,chapter,verse);
    }

    void saveBounds(Bundle state){
        state.putBoolean("picker_active",passageSelection.active);
        state.putBoolean("picker_choose_end",pickerChooseEnd);
        state.putIntArray("picker_bounds",new int[]{passageSelection.book,passageSelection.startChapter,
            passageSelection.startVerse,passageSelection.endChapter,passageSelection.endVerse});
    }

    void restoreBounds(Bundle state,long collectionId,int chapter){
        passageSelection.collectionId=collectionId;
        passageSelection.chapter=chapter;
        int[] bounds=state.getIntArray("picker_bounds");
        if(bounds!=null&&bounds.length==5){
            passageSelection.book=bounds[0];
            passageSelection.startChapter=bounds[1];
            passageSelection.startVerse=bounds[2];
            passageSelection.endChapter=bounds[3];
            passageSelection.endVerse=bounds[4];
        }
        passageSelection.active=state.getBoolean("picker_active");
        pickerChooseEnd=state.getBoolean("picker_choose_end");
    }

    LinearLayout rangePanel(){return pickerSelectionPanel;}
    void clearRangePanel(){pickerSelectionPanel=null;}

    void openChapter(int book,int chapter){
        passageSelection.openBook(book);
        passageSelection.chapter=chapter;
    }

    void renderVerses(LinearLayout content,int book,int chapter,int targetVerse,boolean scrollToTarget,
                      Supplier<ScrollView> activeScroller,List<LocalStore.Entry> entries,
                      IntFunction<Boolean> toggleVerse){
        final ScrollView scroller=activeScroller.get();
        final Set<String> present=new HashSet<>(),included=new HashSet<>();
        if(toggleVerse!=null){
            for(LocalStore.Entry entry:entries){
                present.add(entry.reference);
                if(entry.lines().size()>1&&entry.reference.startsWith(Bible.NAMES[book-1]+" "))
                    for(Bible.Verse part:entry.lines())included.add(part.ref);
            }
        }
        for(Bible.Verse verse:bible.chapter(book,chapter)){
            final int number=Integer.parseInt(verse.ref.substring(verse.ref.indexOf(':')+1));
            final String reference=Bible.reference(book,chapter,number,chapter,number);
            LinearLayout line=ui.card(content);
            LinearLayout head=ui.row();head.setGravity(Gravity.CENTER_VERTICAL);
            TextView marker=ui.txt(verse.ref,14,ui.bookInk(book),true);
            head.addView(marker,new LinearLayout.LayoutParams(0,-2,1));
            if(toggleVerse!=null){
                boolean standalone=present.contains(reference),covered=!standalone&&included.contains(verse.ref);
                TextView toggle=ui.button(standalone?"−":covered?"✓":"+",false,()->{});
                toggle.setContentDescription((standalone?"Убрать ":covered?"Уже в отрывке: ":"Добавить ")+reference);
                head.addView(toggle,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
                toggle.setOnClickListener(v->{
                    if(included.contains(verse.ref)&&!present.contains(reference))return;
                    Boolean added=toggleVerse.apply(number);
                    if(added!=null){
                        if(added)present.add(reference);else present.remove(reference);
                        toggle.setText(added?"−":included.contains(verse.ref)?"✓":"+");
                        toggle.setContentDescription((added?"Убрать ":included.contains(verse.ref)?"Уже в отрывке: ":"Добавить ")+reference);
                    }
                });
            }
            ui.add(line,head,0);ui.add(line,ui.prose(verse.text,19),8);
            if(targetVerse==number){
                BibleReader.afterLayout(line,()->{
                    if(scroller==null||activeScroller.get()!=scroller)return;
                    if(scrollToTarget)
                        scroller.scrollTo(0,Math.max(0,line.getTop()-ui.dp(16)));
                    line.setBackground(ui.outlined(0xFFFFE8B5,20,BORDER));
                    line.postDelayed(()->{if(activeScroller.get()==scroller)line.setBackground(ui.outlined(CARD,20,BORDER));},1100);
                });
            }
        }
    }

    boolean beginSaveRange(){
        if(!passageSelection.active||!passageSelection.valid()||!pickerSaveButton.isEnabled())return false;
        pickerSaveButton.setEnabled(false);
        return true;
    }

    private String pickerBoundaryReference(boolean end){
        int c=end?passageSelection.endChapter:passageSelection.startChapter;
        int v=end?passageSelection.endVerse:passageSelection.startVerse;
        return c>0&&v>0?Bible.reference(passageSelection.book,c,v,c,v):"Не выбрано";
    }
    void renderRange(LinearLayout content,Consumer<Boolean> choose,Runnable save){
        TextView start=ui.button("Задать начало отрывка\n"+pickerBoundaryReference(false),
            passageSelection.startChapter>0,()->choose.accept(false));
        start.setMinHeight(ui.dp(82));ui.add(content,start,10);
        TextView end=ui.button("Задать конец отрывка\n"+pickerBoundaryReference(true),
            passageSelection.endChapter>0,()->choose.accept(true));
        end.setMinHeight(ui.dp(82));ui.add(content,end,10);
        pickerSelectionLabel=ui.prose("",18);ui.add(content,pickerSelectionLabel,18);
        pickerSelectionPanel=ui.column();pickerSelectionPanel.setPadding(ui.dp(19),ui.dp(8),ui.dp(19),ui.dp(8));
        pickerSelectionPanel.setBackground(ui.outlined(CARD,16,BORDER));
        pickerSaveButton=ui.button("Добавить отрывок",true,save);
        ui.add(pickerSelectionPanel,pickerSaveButton,0);updatePickerRangeSummary();
    }
    void updatePickerRangeSummary(){
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

    void beginBookCatalog(){pickerBookActions.clear();}

    View bookAction(long group,int book,boolean added,BookAction change){
        BookToggle toggle=new BookToggle();
        toggle.setFocusable(true);
        applyBookToggle(toggle,group,book,added,change);
        ui.animateLessonPress(toggle);
        pickerBookActions.put(book,toggle);
        return toggle;
    }

    void finishBookChange(long group,int book,boolean remove,boolean success,
                          boolean currentCatalog,BookAction change){
        pendingBooks.remove(group+":"+book);
        if(!currentCatalog)return;
        BookToggle current=pickerBookActions.get(book);
        if(current!=null)applyBookToggle(current,group,book,success?!remove:remove,change);
    }

    private final class BookToggle extends View {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean added,busy;

        BookToggle(){
            super(context);
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

    private void applyBookToggle(BookToggle toggle,long group,int book,boolean added,BookAction change){
        boolean busy=pendingBooks.contains(group+":"+book);
        toggle.showState(added,busy);
        toggle.setContentDescription((busy?"Сохраняется ":added?"Убрать книгу ":"Добавить книгу ")+Bible.NAMES[book-1]);
        toggle.setOnClickListener(v->{
            if(!pendingBooks.add(group+":"+book))return;
            applyBookToggle(toggle,group,book,added,change);
            change.run(group,book,added);
        });
        toggle.setEnabled(!busy);
    }


    boolean hasChapters(){return !selectedPickerChapters.isEmpty();}
    void resetChapters(){selectedPickerChapters.clear();}
    void beginChapterGrid(){pickerChapterButtons.clear();}

    void configureChapter(int chapter,TextView button,int selectedChapter){
        pickerChapterButtons.put(chapter,button);
        button.setOnLongClickListener(v->{togglePickerChapter(chapter,selectedChapter);return true;});
        stylePickerChapter(chapter,button,selectedChapter);
    }

    Set<Integer> beginSaveChapters(int selectedChapter){
        if(savingPickerChapters||selectedPickerChapters.isEmpty())return Collections.emptySet();
        Set<Integer> chapters=new TreeSet<>(selectedPickerChapters);
        savingPickerChapters=true;
        updatePickerChapterSelection(selectedChapter);
        return chapters;
    }

    void finishSaveChapters(boolean success,boolean currentGrid,int selectedChapter){
        savingPickerChapters=false;
        if(!currentGrid)return;
        if(success)clearPickerChapterSelection(selectedChapter);
        else updatePickerChapterSelection(selectedChapter);
    }

    void togglePickerChapter(int chapter,int selectedChapter){
        if(savingPickerChapters)return;
        if(!selectedPickerChapters.add(chapter))selectedPickerChapters.remove(chapter);
        updatePickerChapterSelection(selectedChapter);
    }

    void clearPickerChapterSelection(int selectedChapter){
        selectedPickerChapters.clear();
        updatePickerChapterSelection(selectedChapter);
    }

    private void stylePickerChapter(int chapter,TextView button,int selectedChapter){
        boolean selected=selectedPickerChapters.contains(chapter);
        boolean highlighted=selectedPickerChapters.isEmpty()&&chapter==selectedChapter;
        button.setContentDescription("Глава "+chapter+(selected?", выделена":highlighted?", выбрана":""));
        button.setTextColor(selected?BULK_INK:highlighted?PRIMARY_ON:WHITE);
        button.setBackground(selected?ui.outlined(BULK_SELECTED,18,0xFFABC5A7):
            highlighted?ui.softAccent():ui.outlined(CARD_ALT,18,BORDER));
    }

    private void updatePickerChapterSelection(int selectedChapter){
        for(Map.Entry<Integer,TextView> chapter:pickerChapterButtons.entrySet())
            stylePickerChapter(chapter.getKey(),chapter.getValue(),selectedChapter);
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

    LinearLayout chapterFooter(int selectedChapter,Runnable save){
        pickerChapterFooter=ui.row();
        pickerChapterFooter.setPadding(ui.dp(19),ui.dp(8),ui.dp(19),ui.dp(8));
        pickerChapterFooter.setBackgroundColor(CARD);
        pickerChapterAdd=ui.button("",true,save);
        pickerChapterAdd.setBackground(ui.shape(BULK_INK,18));
        pickerChapterAdd.setTextColor(Color.WHITE);
        ui.animateLessonPress(pickerChapterAdd);
        pickerChapterFooter.addView(pickerChapterAdd,new LinearLayout.LayoutParams(0,-2,1));
        pickerChapterCancel=ui.txt("×",26,MUTED,false);
        pickerChapterCancel.setGravity(Gravity.CENTER);
        pickerChapterCancel.setContentDescription("Отменить выделение глав");
        pickerChapterCancel.setFocusable(true);
        pickerChapterCancel.setOnClickListener(v->clearPickerChapterSelection(selectedChapter));
        pickerChapterFooter.addView(pickerChapterCancel,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(49)));
        updatePickerChapterSelection(selectedChapter);
        return pickerChapterFooter;
    }

}
