package org.biblememory.app;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import static org.biblememory.app.ScreenUi.*;

final class LessonScreenView {
    record Parts(LinearLayout root,LinearLayout top,LinearLayout content,ScrollView scroll) {}
    private final Context context;
    private final ScreenUi ui;

    LessonScreenView(Context context,ScreenUi ui){this.context=context;this.ui=ui;}

    Parts create(Drawable background,boolean assembling,boolean developerEnabled,Runnable close,Runnable developer){
        LinearLayout root=ui.column();root.setBackground(background);
        LinearLayout top=ui.column();top.setPadding(ui.dp(22),ui.dp(15),ui.dp(22),ui.dp(6));
        LinearLayout topRow=ui.row();
        TextView closeButton=ui.txt("×",30,WHITE,false);closeButton.setGravity(Gravity.CENTER);
        closeButton.setContentDescription("Выйти из урока");
        closeButton.setOnClickListener(v->close.run());
        closeButton.setOnLongClickListener(v->{developer.run();return true;});
        topRow.addView(closeButton,new LinearLayout.LayoutParams(ui.dp(45),ui.dp(45)));
        if(assembling){
            closeButton.setTextColor(LessonAssemblyView.BUILD_INK);
            closeButton.setBackground(ui.outlined(0xFFFFF1E2,24,0xFFFFF9F0));
            closeButton.setElevation(ui.dp(2));
            TextView title=ui.displayText("Сборка",27);
            title.setTextColor(LessonAssemblyView.BUILD_INK);
            title.setGravity(Gravity.CENTER);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            topRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            if(!developerEnabled)
                topRow.addView(new View(context),new LinearLayout.LayoutParams(ui.dp(45),ui.dp(45)));
        }else topRow.addView(new View(context),new LinearLayout.LayoutParams(0,ui.dp(45),1));
        if(developerEnabled){
            TextView devButton=ui.txt("DEV",12,GREEN,true);
            devButton.setGravity(Gravity.CENTER);
            devButton.setContentDescription("Открыть режим разработчика");
            devButton.setOnClickListener(v->developer.run());
            topRow.addView(devButton,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(45)));
        }
        top.addView(topRow);
        root.addView(top,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content=ui.column();content.setPadding(ui.dp(20),ui.dp(11),ui.dp(20),ui.dp(18));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        return new Parts(root,top,content,scroll);
    }

    void footer(LinearLayout root,TextView mainAction,boolean wrapAction){
        LinearLayout footer=ui.column();footer.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(22));
        ui.animateLessonPress(mainAction);
        mainAction.setMinimumHeight(ui.dp(58));
        footer.addView(mainAction,new LinearLayout.LayoutParams(-1,wrapAction?-2:ui.dp(58)));
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
    }

    void phaseHeader(LinearLayout content,String stage,String reference,int ink){
        TextView pill=ui.txt(stage,15,0xFFAA563F,true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(ui.dp(17),ui.dp(8),ui.dp(17),ui.dp(8));
        pill.setBackground(ui.shape(0xFFF9E1D2,24));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,-2);
        pp.gravity=Gravity.CENTER_HORIZONTAL;pp.topMargin=ui.dp(9);
        content.addView(pill,pp);
        TextView cite=ui.displayText(reference,20);
        cite.setTextColor(ink);cite.setGravity(Gravity.CENTER);
        ui.add(content,cite,17);
    }

    /** Separate full-screen lesson: no card carousel or persistent app-tab bar. */
    LinearLayout invalidLine(int invalidLine,Runnable edit,Runnable close){
        LinearLayout root=ui.column();root.setBackground(ui.pageBackground());
        root.setPadding(ui.dp(22),ui.dp(24),ui.dp(22),ui.dp(24));
        LinearLayout panel=ui.card(root);
        ui.add(panel,ui.displayText("Текст требует исправления",22),0);
        ui.helper(panel,CustomStudyText.message(invalidLine)+". Исправь строку, чтобы продолжить обучение.");
        ui.action(panel,"Изменить текст",true,edit);
        ui.action(panel,"Выйти из урока",false,close);
        return root;
    }

}
