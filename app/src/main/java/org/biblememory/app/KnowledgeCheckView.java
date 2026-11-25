package org.biblememory.app;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.widget.*;
import java.util.function.Consumer;

import static org.biblememory.app.ScreenUi.*;

/** Native presentation for a diagnostic task and summary. Writes are delegated to callbacks. */
final class KnowledgeCheckView {
    record Layout(LinearLayout root, LinearLayout body) {}

    private final ScreenUi ui;

    KnowledgeCheckView(ScreenUi ui) {
        this.ui = ui;
    }

    Layout task(Context context,KnowledgeCheckSession.Task task,
            Runnable exitAction,Consumer<String> edit,Runnable check,Runnable advance){
        LinearLayout root=ui.column();root.setBackground(ui.pageBackground());
        LinearLayout top=ui.row();top.setPadding(ui.dp(17),ui.dp(12),ui.dp(17),ui.dp(7));
        TextView exit=ui.txt("×",30,WHITE,false);exit.setGravity(Gravity.CENTER);
        exit.setContentDescription("Сохранить проверку памяти и выйти");
        exit.setOnClickListener(v->exitAction.run());
        top.addView(exit,new LinearLayout.LayoutParams(ui.dp(43),ui.dp(46)));
        TextView count=ui.txt((task.position()+1)+" из "+task.total(),15,MUTED,true);
        count.setGravity(Gravity.CENTER);
        top.addView(count,new LinearLayout.LayoutParams(0,ui.dp(45),1));
        root.addView(top,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(true);
        LinearLayout content=ui.column();content.setPadding(ui.dp(21),ui.dp(18),ui.dp(21),ui.dp(24));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        ui.progress(content,task.position(),task.total());
        TextView heading=ui.displayText(task.question().reference(),24);heading.setGravity(Gravity.CENTER);
        ui.add(content,heading,20);
        TextView instruction=ui.txt(task.checked()?
            task.passed()?"Нажми «Следующий стих», чтобы продолжить.":
                "Сравни ответ с правильным текстом и перейди к следующему стиху.":
            "Напиши стих по памяти и нажми «Проверить». Если стих не вспоминается, нажми «Пропустить стих».",16,MUTED,false);
        instruction.setGravity(Gravity.CENTER);ui.add(content,instruction,12);
        EditText write=ui.input("Начни писать стих…",true);
        ui.forbidKeyboardHints(write,true);write.setMinLines(5);
        write.setText(task.draft());write.setSelection(write.length());
        write.setEnabled(!task.checked());
        write.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){
                edit.accept(s.toString());
            }
            public void afterTextChanged(Editable e){}
        });
        ui.add(content,write,22);
        if(task.checked()){
            ui.add(content,ui.txt(task.passed()?"✓ Без ошибок":"Обнаружены ошибки",20,
                task.passed()?SUCCESS:RED,true),16);
            if(!task.passed()){
                ui.add(content,typedFeedback(task.feedbackExpected(),task.draft()),9);
                ui.label(content,"Правильный текст");ui.add(content,ui.prose(task.question().text(),17),8);
            }
        }
        LinearLayout footer=ui.column();footer.setPadding(ui.dp(20),ui.dp(10),ui.dp(20),ui.dp(20));
        TextView go=ui.button(task.checked()?"Следующий стих": "Проверить",true,
            task.checked()?advance:check);
        ui.animateLessonPress(go);
        footer.addView(go,new LinearLayout.LayoutParams(-1,ui.dp(55)));
        if(!task.checked()){
            TextView skip=ui.button("Пропустить стих",false,advance);
            ui.animateLessonPress(skip);
            footer.addView(skip,new LinearLayout.LayoutParams(-1,ui.dp(47)));
        }
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        return new Layout(root,content);
    }
    void summary(LinearLayout content,KnowledgeCheckSession.Result result,Runnable home){
        LinearLayout report=ui.headerCard(content);
        ui.add(report,ui.txt(result.correct()+" из "+result.total()+" стихов без ошибок",22,WHITE,true),0);
        ui.progress(report,result.correct(),result.total());
        if(!result.mistakes().isEmpty()){
            ui.label(content,"Требуют внимания");
            for(String ref:result.mistakes())ui.add(content,ui.txt("• "+ref,16,RED,false),7);
        }else ui.helper(content,"Все стихи воспроизведены без ошибок!");
        ui.helper(content,"Итог сохранён в истории коллекции. Статусы освоения и расписание повторений остаются прежними.");
        ui.action(content,"На главную",true,home);
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
        TextView tv=ui.txt("",17,WHITE,false);tv.setText(annotated);return tv;
    }
}
