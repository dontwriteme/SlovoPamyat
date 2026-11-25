package org.biblememory.app;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import static org.biblememory.app.ScreenUi.*;

final class CollectionDialogs {
    private final Context context;
    private final ScreenUi ui;
    CollectionDialogs(Context context,ScreenUi ui){this.context=context;this.ui=ui;}

    void create(Function<String,Runnable> command){
        EditText name=ui.input("Например: Нагорная проповедь",false);
        LinearLayout wrap=ui.column();wrap.setPadding(ui.dp(19),ui.dp(12),ui.dp(19),ui.dp(9));ui.add(wrap,name,0);
        AlertDialog dialog=new AlertDialog.Builder(context,R.style.MemoryDialogTheme)
            .setTitle("Новая коллекция").setView(wrap)
            .setPositiveButton("Создать",null).setNegativeButton("Отмена",null).create();
        ui.styledDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(name.getText().toString().trim().isEmpty()){name.setError("Введите название коллекции");name.requestFocus();return;}
            Runnable after=command.apply(name.getText().toString().trim());
            dialog.dismiss();
            after.run();
        });
    }
    void select(List<LocalStore.UnifiedCollection> collections,long activeId,LongConsumer choose){
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
        int visibleHeight=Math.min(ui.dp(320),context.getResources().getDisplayMetrics().heightPixels/2);
        ScrollView scroll=new ScrollView(context){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                int maximum=MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED?visibleHeight:
                    Math.min(visibleHeight,MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(maximum,MeasureSpec.AT_MOST));
            }
        };
        LinearLayout choices=ui.column();
        scroll.addView(choices);
        ui.add(panel,scroll,12);
        AlertDialog dialog=new AlertDialog.Builder(context,R.style.MemoryDialogTheme).setView(panel).create();
        close.setOnClickListener(v->dialog.dismiss());
        for(LocalStore.UnifiedCollection collection:collections){
            boolean selected=collection.id==activeId;
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
            choice.setOnClickListener(v->{dialog.dismiss();choose.accept(collection.id);});
            ui.add(choices,choice,4);
        }
        if(collections.isEmpty())scroll.setVisibility(View.GONE);
        dialog.setCanceledOnTouchOutside(true);
        ui.styledDialog(dialog);
    }
    void rename(String title,Consumer<String> save,Runnable after){
        EditText name=ui.input("Название коллекции",false);name.setText(title);
        LinearLayout box=ui.column();box.setPadding(ui.dp(18),ui.dp(10),ui.dp(18),ui.dp(8));ui.add(box,name,0);
        AlertDialog dialog=new AlertDialog.Builder(context,R.style.MemoryDialogTheme)
            .setTitle("Переименовать коллекцию").setView(box)
            .setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();
        ui.styledDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(name.getText().toString().trim().isEmpty()){name.setError("Укажи название");name.requestFocus();return;}
            save.accept(name.getText().toString().trim());dialog.dismiss();after.run();
        });
    }
    void confirmClear(Runnable command){
        ui.styledDialog(new AlertDialog.Builder(context,R.style.MemoryDialogTheme)
            .setTitle("Очистить коллекцию?")
            .setMessage("Все стихи, отрывки и карточки будут убраны из этой коллекции. " +
                "Саму коллекцию и прежние результаты заучивания сохраним. " +
                "Отменить очистку нельзя.")
            .setPositiveButton("Очистить",(d,w)->command.run())
            .setNegativeButton("Отмена",null).create());
    }

    void confirmDelete(Runnable command){
        ui.styledDialog(new AlertDialog.Builder(context,R.style.MemoryDialogTheme)
            .setTitle("Удалить коллекцию из списка?")
            .setMessage("Коллекция исчезнет из списка. Стихи и прежний прогресс изучения сохранятся в локальной базе.")
            .setPositiveButton("Удалить",(d,w)->command.run())
            .setNegativeButton("Отмена",null).create());
    }
}
