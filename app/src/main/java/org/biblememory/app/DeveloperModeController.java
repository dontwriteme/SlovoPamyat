package org.biblememory.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** Debug-only, opt-in testing panel. Never modifies LocalStore or the real sequence session. */
public final class DeveloperModeController {
    private DeveloperModeController() {}
    private static final String PREFS = "sv_developer_mode";
    private static final String TEST_PREFS = "sv_sequence_dev";
    private static final int GREEN = 0xFF587D5B;
    private static final int INK = 0xFF35452F;
    private static final int BG = 0xFFFFF6EF;
    private static final int SUBTLE = 0xFF776D65;

    private static boolean isDebugBuild(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
    public static boolean isEnabled(Context context) {
        return isDebugBuild(context) && context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getBoolean("enabled",false);
    }

    private static int dp(Context context, int x) {
        return (int) (x * context.getResources().getDisplayMetrics().density + 0.5f);
    }
    private static GradientDrawable surface(Context context,int fill,int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(context,radius));
        return background;
    }
    private static TextView item(Activity a,String caption,int size,int color,boolean bold) {
        TextView text=new TextView(a);
        text.setText(caption);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setPadding(dp(a,13),dp(a,13),dp(a,13),dp(a,13));
        if(bold)text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return text;
    }
    private static void add(LinearLayout parent,View child,int margin) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(parent.getContext(),margin);
        parent.addView(child,lp);
    }
    private static void action(Activity a,LinearLayout root,String title,Runnable call) {
        TextView button=item(a,title,15,INK,true);
        button.setBackground(surface(a,Color.WHITE,12));
        button.setOnClickListener(v->call.run());
        add(root,button,8);
    }

    /** Access is available by long-pressing the lesson's X button. */
    public static void open(Activity a,LocalStore.Entry entry,
                            IntUnaryOperator versesInChapter,int learnedCount) {
        if(!isDebugBuild(a))return;
        if(!isEnabled(a)) {
            new AlertDialog.Builder(a)
                .setTitle("Режим разработчика")
                .setMessage("Включить тестирование упражнений без изменения настоящего прогресса? " +
                            "Доступно только в отладочной сборке.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Включить",(d,w)->{
                    a.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                        .putBoolean("enabled",true).apply();
                    showPanel(a,entry,versesInChapter,learnedCount);
                }).show();
            return;
        }
        showPanel(a,entry,versesInChapter,learnedCount);
    }

    private static int continuousCount(LocalStore.Entry entry,IntUnaryOperator chapterSize) {
        List<Bible.Verse> verses=entry.lines();
        if(verses.isEmpty())return 0;
        List<String> refs=new ArrayList<>();
        for(Bible.Verse verse:verses)refs.add(verse.ref);
        boolean[] allReady=new boolean[verses.size()];
        Arrays.fill(allReady,true);
        return SequenceRecallLogic.learnedPrefix(refs,allReady,chapterSize);
    }
    private static List<Integer> targetRange(int fromInclusive,int toExclusive) {
        List<Integer> indices=new ArrayList<>();
        for(int i=fromInclusive;i<toExclusive;i++)indices.add(i);
        return indices;
    }
    private static void launch(Activity a,LocalStore.Entry entry,
                               SequenceRecallLogic.Type type,int begin,int end,boolean finalCheck) {
        SequenceRecallController.preview(a,entry,type,targetRange(begin,end),finalCheck);
    }
    private static void showPanel(Activity a,LocalStore.Entry entry,
                                  IntUnaryOperator verseCount,int learnedCount) {
        final int contiguous=continuousCount(entry,verseCount);
        LinearLayout layout=new LinearLayout(a);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(a,18),dp(a,12),dp(a,18),dp(a,18));
        layout.setBackgroundColor(BG);

        add(layout,item(a,"Режим разработчика",22,INK,true),0);
        add(layout,item(a,"Только для отладочной сборки · реальное обучение не изменяется",12,SUBTLE,false),0);
        add(layout,item(a,"Отрывок: "+entry.title+"\n"+
                "Всего стихов: "+entry.lines().size()+
                " · Первоначально освоено: "+learnedCount+"\n"+
                "Непрерывный фрагмент для тестов: "+contiguous,14,INK,false),8);

        if(contiguous>=3){
            action(a,layout,"Проверить переходы · 3 стиха",()->
                launch(a,entry,SequenceRecallLogic.Type.BRIDGES,1,3,false));
        }
        if(contiguous>=6){
            action(a,layout,"Начала стихов · первые 6",()->
                launch(a,entry,SequenceRecallLogic.Type.STARTS,0,6,false));
            action(a,layout,"Переходы · первые 6",()->
                launch(a,entry,SequenceRecallLogic.Type.BRIDGES,1,6,false));
        }
        if(contiguous>=9){
            action(a,layout,"Начала стихов · 7–9",()->
                launch(a,entry,SequenceRecallLogic.Type.STARTS,6,9,false));
            action(a,layout,"Переходы · 7–9",()->
                launch(a,entry,SequenceRecallLogic.Type.BRIDGES,6,9,false));
        }
        if(contiguous>=10){
            action(a,layout,"Итоговый тест начал · 10 стихов",()->
                launch(a,entry,SequenceRecallLogic.Type.STARTS,0,10,true));
            action(a,layout,"Итоговый тест переходов · 10 стихов",()->
                launch(a,entry,SequenceRecallLogic.Type.BRIDGES,1,10,true));
        }
        if(contiguous>10){
            // Also test later windows, including the crossing transition from prior window.
            for(int first=10;first<contiguous;first+=10){
                final int begin=first;
                final int end=Math.min(contiguous,first+10);
                action(a,layout,"Итоговые начала · стихи "+(begin+1)+"–"+end,()->
                    launch(a,entry,SequenceRecallLogic.Type.STARTS,begin,end,true));
                action(a,layout,"Итоговые переходы · стихи "+(begin+1)+"–"+end,()->
                    launch(a,entry,SequenceRecallLogic.Type.BRIDGES,begin,end,true));
            }
        }
        if(contiguous<3){
            add(layout,item(a,"Для теста нужен непрерывный библейский отрывок из 3 или более стихов. " +
                    "Сохраните такой отрывок и откройте его в уроке.",14,SUBTLE,false),8);
        }
        action(a,layout,"Очистить только тестовые ответы",()->{
            a.getSharedPreferences(TEST_PREFS,Context.MODE_PRIVATE).edit().clear().apply();
            new AlertDialog.Builder(a).setMessage("Тестовые ответы очищены. " +
                    "Прогресс изучения не изменён.").setPositiveButton("OK",null).show();
        });
        action(a,layout,"Выключить режим разработчика",()->{
            a.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                    .putBoolean("enabled",false).apply();
            new AlertDialog.Builder(a).setMessage("Режим разработчика выключен. " +
                    "Вернитесь в урок, чтобы обновить верхнюю панель.")
                    .setPositiveButton("OK",null).show();
        });
        ScrollView scroll=new ScrollView(a);
        scroll.setFillViewport(false);
        scroll.addView(layout);
        new AlertDialog.Builder(a)
                .setView(scroll)
                .setNegativeButton("Закрыть",null)
                .show();
    }
}
