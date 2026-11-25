package org.biblememory.app;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import static org.biblememory.app.ScreenUi.GREEN;
import static org.biblememory.app.ScreenUi.WHITE;

final class CollectionEntryEditor {
    private final Context context;
    private final ScreenUi ui;
    private final Bible bible;
    private final Consumer<String> message;

    CollectionEntryEditor(Context context,ScreenUi ui,Bible bible,Consumer<String> message){
        this.context=context;this.ui=ui;this.bible=bible;this.message=message;
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
        ui.styledDialog(new AlertDialog.Builder(context,R.style.MemoryDialogTheme)
            .setTitle(heading).setView(wrap)
            .setPositiveButton("Сохранить",(d,w)->{
                int a=number(startChapter),b=number(startVerse),c=number(endChapter),end=number(endVerse);
                if(!PassageRangeLogic.valid(a,b,c,end)){
                    message.accept("Неверный диапазон");return;
                }
                try{bible.passage(book,a,b,c,end);save.save(a,b,c,end);}
                catch(Exception err){message.accept(err.getMessage()==null?"Ошибка изменения диапазона":err.getMessage());}
            }).setNegativeButton("Отмена",null).create());
    }
    void study(LocalStore store,LocalStore.Entry e,int book,Runnable updated){
        if(book>0 && !e.lines().isEmpty()){
            int[] first=parseBibleLocation(e.lines().get(0).ref);
            int[] last=parseBibleLocation(e.lines().get(e.lines().size()-1).ref);
            if(first!=null&&last!=null){
                editBibleRange("Изменить отрывок",book,first[0],first[1],last[0],last[1],
                    (a,b,c,d)->{
                        List<Bible.Verse> lines=bible.passage(book,a,b,c,d);
                        store.reviseStudyPassage(e.id,Bible.reference(book,a,b,c,d),lines);
                        updated.run();
                    });
                return;
            }
        }
        custom(store,e,updated);
    }
    void custom(LocalStore store,LocalStore.Entry e,Runnable updated){
        EditText source=ui.input("Название / источник",false);source.setText(e.reference);
        EditText words=ui.input("Текст",true);words.setMinLines(5);
        StringBuilder original=new StringBuilder();
        for(Bible.Verse v:e.lines()){
            if(original.length()>0)original.append('\n');original.append(v.text);
        }
        words.setText(original.toString());
        LinearLayout fields=ui.column();fields.setPadding(ui.dp(20),ui.dp(5),ui.dp(20),ui.dp(12));
        ui.add(fields,source,0);ui.add(fields,words,10);
        AlertDialog dialog=new AlertDialog.Builder(context,R.style.MemoryDialogTheme)
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
            updated.run();
        });
    }
    void card(LocalStore store,LocalStore.Flashcard card,LongSupplier deck,Runnable updated){
        if(card.bookId>0){
            editBibleRange("Изменить карточку",card.bookId,card.firstChapter,card.firstVerse,
                card.lastChapter,card.lastVerse,(a,b,c,d)->{
                    String ref=Bible.reference(card.bookId,a,b,c,d);
                    store.changeCardInDeck(deck.getAsLong(),card,card.bookId,a,b,c,d,
                        ref,CollectionPassages.texts(bible.passage(card.bookId,a,b,c,d)));
                    updated.run();
                });
            return;
        }
        EditText source=ui.input("Название / источник",false);source.setText(card.reference);
        EditText words=ui.input("Текст",true);words.setMinLines(4);words.setText(card.content);
        LinearLayout fields=ui.column();fields.setPadding(ui.dp(20),ui.dp(5),ui.dp(20),ui.dp(12));
        ui.add(fields,source,0);ui.add(fields,words,9);
        ui.styledDialog(new AlertDialog.Builder(context,R.style.MemoryDialogTheme)
            .setTitle("Изменить карточку").setView(fields)
            .setPositiveButton("Сохранить",(d,w)->{
                if(value(source).isEmpty()||value(words).isEmpty()){message.accept("Заполни поля");return;}
                store.changeCardInDeck(deck.getAsLong(),card,0,0,0,0,0,value(source),value(words));
                updated.run();
            }).setNegativeButton("Отмена",null).create());
    }


    private static int number(EditText field){
        try{return Integer.parseInt(value(field));}catch(Exception invalid){return -1;}
    }

    private static String value(EditText field){return field.getText().toString().trim();}
}
