package org.biblememory.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import static org.biblememory.app.ScreenUi.*;

final class CollectionViews {
    private final Context context;
    private final ScreenUi ui;
    private final ScreenArtwork artwork;
    CollectionViews(Context context,ScreenUi ui,ScreenArtwork artwork){
        this.context=context;this.ui=ui;this.artwork=artwork;
    }

    void list(LinearLayout content,List<LocalStore.UnifiedCollection> collections,
              CollectionProgressSnapshot snapshot,long activeId,LongConsumer open){
        // Prominent full-width search field: accent border, magnifier, larger input.
        LinearLayout searchBar=ui.row();searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(ui.dp(14),ui.dp(3),ui.dp(8),ui.dp(3));
        searchBar.setMinimumHeight(ui.dp(58));
        GradientDrawable searchFrame=ui.shape(0xFFFFFDF9,17);
        searchFrame.setStroke(ui.dp(2),0xFFCE9072);
        searchBar.setBackground(searchFrame);
        searchBar.setElevation(ui.dp(2));
        NavigationGlyph magnifier=new NavigationGlyph(context,"search",GREEN);
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
        renderUnifiedCollectionRows(results,"",collections,snapshot,activeId,open);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                clear.setVisibility(s.length()==0?View.GONE:View.VISIBLE);
                results.removeAllViews();renderUnifiedCollectionRows(results,s.toString(),collections,snapshot,activeId,open);
            }
            public void afterTextChanged(Editable s){}
        });
    }
    private void renderUnifiedCollectionRows(LinearLayout into,String search,
            List<LocalStore.UnifiedCollection> collections,CollectionProgressSnapshot snapshot,long activeId,LongConsumer open){
        boolean found=false;
        for(LocalStore.UnifiedCollection u:collections){
            if(!u.title.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT)))continue;
            found=true;
            LinearLayout row=ui.row();row.setPadding(ui.dp(13),ui.dp(14),ui.dp(13),ui.dp(14));
            row.setBackground(artwork.homeSurface());
            ImageView thumb=artwork.landscapeThumbnail(u.id);
            LinearLayout.LayoutParams imageP=new LinearLayout.LayoutParams(ui.dp(59),ui.dp(61));
            imageP.rightMargin=ui.dp(13);row.addView(thumb,imageP);
            LinearLayout details=ui.column();ui.add(details,ui.displayText(u.title,17),0);
            String passages=CollectionViews.countedRussian(u.entries,"отрывок","отрывка","отрывков");
            String verses=CollectionViews.countedRussian(snapshot.uniqueVerseCount(u.groupId),"стих","стиха","стихов");
            ui.add(details,ui.txt(passages+" · "+verses,13,MUTED,false),5);
            if(u.id==activeId)ui.add(details,ui.txt("●  Выбрана на главной",12,GREEN,true),4);
            row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=ui.txt("›",22,GREEN,false);arrow.setGravity(Gravity.CENTER);
            row.addView(arrow,new LinearLayout.LayoutParams(ui.dp(24),ui.dp(48)));
            row.setClickable(true);row.setFocusable(true);
            row.setContentDescription("Открыть коллекцию "+u.title);
            row.setOnClickListener(v->open.accept(u.id));
            ui.add(into,row,9);
        }
        if(!found)ui.helper(into,search.trim().isEmpty()?"Коллекций пока нет":"Ничего не найдено");
    }
    /** The one explicit location for collection management, inside its detail screen. */
    private void unifiedSettingRow(LinearLayout settings,String label,String glyph,boolean destructive,Runnable command){
        LinearLayout item=ui.row();item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(ui.dp(8),ui.dp(7),ui.dp(5),ui.dp(7));
        MenuGlyph symbol=new MenuGlyph(context,glyph,destructive?RED:GREEN);
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28));
        iconP.rightMargin=ui.dp(13);item.addView(symbol,iconP);
        TextView name=ui.txt(label,16,destructive?RED:WHITE,true);
        item.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        item.setMinimumHeight(ui.dp(51));
        item.setClickable(true);item.setFocusable(true);
        item.setContentDescription(label);item.setOnClickListener(v->command.run());
        if(settings.getChildCount()>1){
            View divider=new View(context);divider.setBackgroundColor(0xFFECD8CD);
            settings.addView(divider,new LinearLayout.LayoutParams(-1,ui.dp(1)));
        }
        settings.addView(item,new LinearLayout.LayoutParams(-1,-2));
    }
    void settings(LinearLayout content,Runnable rename,Runnable reorder,Runnable clear,Runnable delete){
        LinearLayout settings=ui.column();settings.setBackground(artwork.homeSurface());
        settings.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(11));
        ui.add(settings,ui.txt("НАСТРОЙКИ КОЛЛЕКЦИИ",12,MUTED,true),0);
        unifiedSettingRow(settings,"Переименовать","pencil",false,rename);
        unifiedSettingRow(settings,"Настроить порядок стихов","shuffle",false,reorder);
        unifiedSettingRow(settings,"Очистить коллекцию","eraser",true,clear);
        unifiedSettingRow(settings,"Удалить коллекцию","trash",true,delete);
        ui.add(content,settings,14);
    }
    void heading(LinearLayout content,String title){
        TextView heading=ui.displayText(title,25);
        heading.setGravity(Gravity.CENTER);heading.setMaxLines(2);
        ui.add(content,heading,5);
    }

    void progress(LinearLayout content,long groupId,CollectionProgressSnapshot snapshot,String historyJson){
        ui.helper(content,"Освоено: "+studyProgress(groupId,snapshot));
        try{
            JSONArray history=new JSONArray(historyJson);
            if(history.length()>0){JSONObject last=history.getJSONObject(0);
                ui.helper(content,"Последняя проверка памяти: "+last.optInt("correct")+
                    " из "+last.optInt("total")+" без ошибок");}
        }catch(JSONException ignored){}
    }

    static String studyProgress(long groupId,CollectionProgressSnapshot progress){
        int all=0,mastered=0;
        for(LocalStore.Entry e:progress.entries(groupId)){
            all+=progress.verseCount(e.id);
            mastered+=progress.masteryCount(progress.canonicalId(e.id));
        }
        return mastered+" из "+all+" стихов";
    }

    void bookRow(LinearLayout content,String title,String description,Runnable open){
        LinearLayout frame=ui.card(content);
        frame.setBackground(artwork.homeSurface());
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

    void chapterVerses(LinearLayout content,CollectionProgressSnapshot progress,long id){
        List<Bible.Verse> lines=progress.verses(id);
        for(int i=0;i<lines.size();i++){
            Bible.Verse verse=lines.get(i);
            LinearLayout item=ui.card(content);
            String status=progress.verseConsolidated(id,i)?"закреплён":
                progress.mastered(id,i)?"предварительно освоен":"не изучен";
            ui.add(item,ui.txt(verse.ref+" · "+status,14,GREEN,true),0);
            ui.add(item,ui.prose(verse.text,19),8);
        }
    }

    private LinearLayout footer(){
        LinearLayout footer=ui.column();
        footer.setPadding(ui.dp(19),ui.dp(9),ui.dp(19),ui.dp(10));
        footer.setBackgroundColor(BG);
        return footer;
    }

    void listFooter(LinearLayout root,Runnable create){
        LinearLayout footer=footer();
        TextView addCollection=ui.button("＋  Добавить коллекцию",true,create);
        footer.addView(addCollection,new LinearLayout.LayoutParams(-1,ui.dp(52)));
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
    }

    void detailFooter(LinearLayout root,Runnable bible,Runnable custom){
        LinearLayout footer=footer();
        TextView addBible=ui.button("＋  Добавить из Библии",true,bible);
        footer.addView(addBible,new LinearLayout.LayoutParams(-1,ui.dp(51)));
        TextView own=ui.button("＋  Добавить свой текст",false,custom);
        LinearLayout.LayoutParams ownP=new LinearLayout.LayoutParams(-1,ui.dp(49));
        ownP.topMargin=ui.dp(8);footer.addView(own,ownP);
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
    }
    static String countedRussian(int count,String one,String few,String many){
        int last=count%10, lastTwo=count%100;
        String noun=lastTwo>=11&&lastTwo<=14?many:last==1?one:last>=2&&last<=4?few:many;
        return count+" "+noun;
    }
}
