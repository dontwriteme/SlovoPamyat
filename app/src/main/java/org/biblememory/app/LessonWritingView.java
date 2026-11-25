package org.biblememory.app;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import static org.biblememory.app.ScreenUi.*;

final class LessonWritingView {
    private final Context context;
    private final ScreenUi ui;
    private final LessonWritingState lessonWriting;
    private final List<String> inlineExpected=new ArrayList<>();
    private Object activeForm;

    LessonWritingView(Context context,ScreenUi ui,LessonWritingState lessonWriting){
        this.context=context;this.ui=ui;this.lessonWriting=lessonWriting;
    }

    void clear(){activeForm=null;inlineExpected.clear();}
    List<String> expectedGaps(){return inlineExpected;}

    void render(LinearLayout content,String source,LessonCurriculumLogic.Variant variant,
                boolean special,Function<String[],int[]> positions,Runnable beforeEdit,Runnable afterEdit){
        clear();
        Object form=new Object();
        activeForm=form;
        boolean full=variant==LessonCurriculumLogic.Variant.FULL;

        ui.helper(content,lessonWriting.practiceWeakFragment?LearningHelp.WRITE_FRAGMENT:
            full?LearningHelp.WRITE_FULL:LearningHelp.WRITE_GAPS);
        if(!lessonWriting.practiceWeakFragment&&!full)renderInlineGapInputs(content,source,special,positions,beforeEdit,afterEdit,form);
        if(full){
            EditText answer=ui.input(lessonWriting.practiceWeakFragment?"Воспроизведи проблемную фразу…":"Напиши стих полностью…",true);
            ui.forbidKeyboardHints(answer,true);
            answer.setMinLines(6);answer.setText(lessonWriting.draft);
            answer.addTextChangedListener(new TextWatcher(){
                @Override public void beforeTextChanged(CharSequence x,int a,int b,int c){}
                @Override public void onTextChanged(CharSequence x,int a,int before,int count){
                    if(activeForm!=form)return;
                    beforeEdit.run();
                    lessonWriting.draft=x.toString();
                    afterEdit.run();
                }
                @Override public void afterTextChanged(Editable x){}
            });
            ui.add(content,answer,18);
        }
        // Verification is always explicitly triggered by the existing footer button.
    }
    private void renderInlineGapInputs(LinearLayout content,String source,boolean special,
            Function<String[],int[]> positions,Runnable beforeEdit,Runnable afterEdit,Object form){
        String[] tokens=ExerciseTextLogic.tokens(source);
        int[] originalPositions=positions.apply(tokens);
        int[] missing=InlineGapLogic.wordPositions(tokens,originalPositions);
        // A separator marks independently editable gaps, preserving spaces inside one accepted variant.
        lessonWriting.restoreGaps(tokens,originalPositions);
        LessonWordWrap inline=new LessonWordWrap(context,ui.dp(6));
        inline.setPadding(0,ui.dp(12),0,ui.dp(12));
        int maxWidth=context.getResources().getDisplayMetrics().widthPixels-ui.dp(58);
        List<EditText> fields=new ArrayList<>();
        for(int i=0;i<tokens.length;i++){
            String token=tokens[i];
            int match=Arrays.binarySearch(missing,i);
            if(match<0){
                TextView item=ui.txt(token,19,WHITE,false);
                item.setPadding(ui.dp(1),ui.dp(8),ui.dp(1),ui.dp(8));inline.addView(item);
                continue;
            }
            final int gapIndex=match;
            final String expected=InlineGapLogic.core(token);
            inlineExpected.add(expected);
            String prefix=InlineGapLogic.prefix(token);
            if(!prefix.isEmpty()){
                TextView punctuation=ui.txt(prefix,19,WHITE,false);
                punctuation.setPadding(0,ui.dp(8),0,ui.dp(8));inline.addView(punctuation);
            }
            EditText field=new EditText(context);
            field.setTextColor(WHITE);field.setHintTextColor(MUTED);field.setTextSize(19);
            field.setSingleLine(true);ui.forbidKeyboardHints(field,false);
            field.setPadding(ui.dp(3),ui.dp(6),ui.dp(3),ui.dp(6));
            field.setGravity(Gravity.CENTER_VERTICAL);
            // Android native underline: width is initially based on the original word,
            // but the input itself has no maximum length.
            field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
            field.setHint(" ");
            field.setText(lessonWriting.gap(gapIndex));
            field.setSelectAllOnFocus(false);
            int initial=Math.max(ui.dp(32),ui.dp(12)*Math.max(1,InlineGapLogic.letters(expected))+ui.dp(8));
            final int baseline=Math.min(initial,maxWidth);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(baseline,ui.dp(43));
            field.setLayoutParams(params);field.setMinWidth(Math.min(initial,maxWidth));
            field.setContentDescription("Пропущенное слово "+(gapIndex+1));
            fields.add(field);inline.addView(field);
            String suffix=InlineGapLogic.suffix(token);
            if(!suffix.isEmpty()){
                TextView punctuation=ui.txt(suffix,19,WHITE,false);
                punctuation.setPadding(0,ui.dp(8),0,ui.dp(8));inline.addView(punctuation);
            }
        }
        for(int k=0;k<fields.size();k++){
            final int gapIndex=k;
            EditText field=fields.get(k);
            field.addTextChangedListener(new TextWatcher(){
                private boolean changing=false;
                @Override public void beforeTextChanged(CharSequence x,int a,int b,int c){}
                @Override public void onTextChanged(CharSequence x,int a,int before,int count){
                    if(changing||activeForm!=form)return;
                    String value=x.toString();
                    boolean containsSpace=value.endsWith(" ");
                    boolean allowed=containsSpace && InlineGapLogic.keepSpaceInside(
                        InlineGapLogic.core(tokens[missing[gapIndex]]),value,
                        special);
                    if(containsSpace && !allowed){
                        changing=true;
                        String trimmed=value.substring(0,value.length()-1);
                        field.setText(trimmed);field.setSelection(field.length());
                        changing=false;value=trimmed;
                        int next=gapIndex+1;
                        while(next<fields.size() && !lessonWriting.gap(next).trim().isEmpty())next++;
                        if(next<fields.size())fields.get(next).requestFocus();
                        else {
                            field.clearFocus();
                            android.view.inputmethod.InputMethodManager keyboard=
                                (android.view.inputmethod.InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
                            if(keyboard!=null)keyboard.hideSoftInputFromWindow(field.getWindowToken(),0);
                        }
                        // The last space NEVER starts validation; only the footer button does.
                    }
                    beforeEdit.run();
                    lessonWriting.editGap(gapIndex,value);
                    field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
                    float textPx=field.getPaint().measureText(value)+ui.dp(25);
                    int baselineWidth = Math.min(maxWidth,
                        Math.max(ui.dp(32), ui.dp(12) * Math.max(1,
                            InlineGapLogic.letters(inlineExpected.get(gapIndex))) + ui.dp(8)));
                    int width=Math.min(maxWidth,Math.max(baselineWidth,(int)Math.ceil(textPx)));
                    ViewGroup.LayoutParams lp=field.getLayoutParams();
                    if(lp.width!=width){lp.width=width;field.setLayoutParams(lp);}
                    afterEdit.run();
                }
                @Override public void afterTextChanged(Editable x){}
            });
        }
        ui.add(content,inline,18);
    }
}
