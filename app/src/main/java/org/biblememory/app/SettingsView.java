package org.biblememory.app;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import static org.biblememory.app.ScreenUi.MUTED;

final class SettingsView {
    private final Context context;
    private final ScreenUi ui;
    private final ScreenArtwork artwork;

    SettingsView(Context context,ScreenUi ui,ScreenArtwork artwork){
        this.context=context;this.ui=ui;this.artwork=artwork;
    }

    /** Keep settings in the same warm-card style as the existing collection screens. */
    void render(LinearLayout content,Runnable export,Runnable restore,Runnable resetProgress){
        TextView heading=ui.displayText("Настройки",28);
        heading.setGravity(Gravity.CENTER);
        ui.add(content,heading,18);

        LinearLayout backup=ui.column();
        backup.setPadding(ui.dp(18),ui.dp(18),ui.dp(18),ui.dp(18));
        backup.setBackground(artwork.homeSurface());
        backup.setElevation(ui.dp(2));
        ui.add(backup,ui.displayText("Резервная копия",20),0);
        TextView backupHint=ui.txt(
            "Сохраните коллекции, собственные тексты, карточки, прогресс и "+
            "расписание повторений в один файл. Всё работает без интернета.",14,MUTED,false);
        backupHint.setLineSpacing(ui.dp(3),1.1f);
        ui.add(backup,backupHint,9);
        ui.add(backup,ui.button("Создать резервную копию",true,export),14);
        ui.add(backup,ui.button("Восстановить из копии",false,restore),10);
        TextView warning=ui.txt(
            "При восстановлении текущие данные заменятся содержимым файла. "+
            "Перед этим рекомендуется сохранить новую копию.",13,MUTED,false);
        ui.add(backup,warning,12);
        ui.add(content,backup,14);

        LinearLayout section=ui.column();
        section.setPadding(ui.dp(18),ui.dp(18),ui.dp(18),ui.dp(18));
        section.setBackground(artwork.homeSurface());
        section.setElevation(ui.dp(2));
        TextView title=ui.displayText("Прогресс изучения",20);
        ui.add(section,title,9);
        TextView explanation=ui.txt(
            "Сбросить освоение стихов, историю проверок и расписание повторений. "+
            "Коллекции, книги, карточки и сохранённые тексты останутся.",14,MUTED,false);
        explanation.setLineSpacing(ui.dp(3),1.1f);
        ui.add(section,explanation,14);
        TextView reset=ui.button("Сбросить прогресс",false,resetProgress);
        reset.setTextColor(0xFFAC523F);
        reset.setContentDescription("Сбросить весь прогресс изучения");
        ui.add(section,reset,0);
        ui.add(content,section,16);
    }
    void confirmReset(Runnable confirm){
        ui.styledDialog(new AlertDialog.Builder(context,R.style.MemoryDialogTheme)
            .setTitle("Сбросить весь прогресс?")
            .setMessage("Будут безвозвратно удалены отметки об освоении стихов, "+
                "история проверок, незавершённые уроки и расписание повторений "+
                "во всех коллекциях.\n\nСами коллекции, книги, карточки и тексты сохранятся.")
            .setPositiveButton("Сбросить",(dialog,which)->confirm.run())
            .setNegativeButton("Отмена",null).create());
    }
    void confirmImport(Runnable confirm){
        ui.styledDialog(new AlertDialog.Builder(context,R.style.MemoryDialogTheme)
            .setTitle("Восстановить резервную копию?")
            .setMessage("Все текущие коллекции, стихи, карточки, прогресс "+
                "и незавершённые уроки будут заменены данными из выбранного "+
                "файла. Это действие нельзя отменить.\n\n"+
                "Рекомендуется сначала создать отдельную копию текущих данных.")
            .setPositiveButton("Восстановить",(d,w)->confirm.run())
            .setNegativeButton("Отмена",null).create());
    }
}
