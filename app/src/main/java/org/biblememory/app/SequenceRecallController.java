package org.biblememory.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/** Isolated lesson view for remembering verse beginnings and consecutive transitions.
 * No database migration: remembers only this drill's answers and result in private preferences.
 */
public final class SequenceRecallController {
    private SequenceRecallController() {}
    private static final String PREFS="sv_sequence_v1";
    private static final int BG=0xFFFFF6EF, SURFACE=0xFFFFFFFF;
    private static final int TEXT=0xFF35452F, GREEN=0xFF587D5B, MUTED=0xFF806F66;
    private static final int ERROR=0xFFBE5145, BORDER=0xFFE6D8CF;
    private static int dp(Context c,float v) { return (int)(c.getResources().getDisplayMetrics().density*v+.5f); }
    private static GradientDrawable surface(Context c,int color,int border,int radius){
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(color);drawable.setCornerRadius(dp(c,radius));
        if(border!=0)drawable.setStroke(dp(c,1),border);
        return drawable;
    }
    private static TextView text(Activity a,String s,int sp,int color,boolean bold){
        TextView t=new TextView(a);t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        t.setIncludeFontPadding(false);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private static void add(LinearLayout parent,View child,int topMargin){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(parent.getContext(),topMargin);
        parent.addView(child,lp);
    }
    // SV_STARTS_WORD_COUNT_1: explicit length of the expected beginning, no answer leakage.
    private static String requiredWordsHint(SequenceRecallLogic.Type type,int number){
        String which=type==SequenceRecallLogic.Type.STARTS?" стиха":" следующего стиха";
        if(number==1)return "Впиши первое слово"+which;
        return "Впиши первые "+number+(number>=2&&number<=4?" слова":" слов")+which;
    }
    private static String enteredWordsHint(int entered,int required){
        return "Введено "+entered+" из "+required+(required==1?" слова":" слов");
    }
    private static String fingerprint(List<String> refs) { return Integer.toHexString(refs.toString().hashCode()); }
    private static String prefix(long entry,String fp){return "e:"+entry+":"+fp+":";}

    private static Set<String> completed(SharedPreferences p,String prefix){
        Set<String> result=new HashSet<>();
        for(Map.Entry<String,?> e:p.getAll().entrySet()) {
            if(e.getKey().startsWith(prefix+"done:") && Boolean.TRUE.equals(e.getValue()))
                result.add(e.getKey().substring((prefix+"done:").length()));
        }
        return result;
    }
    private static Set<Integer> weak(SharedPreferences p,String prefix){
        Set<Integer> result=new HashSet<>();
        String raw=p.getString(prefix+"weak","");
        for(String token:raw.split(","))try{result.add(Integer.parseInt(token));}
        catch(NumberFormatException ignored){}
        return result;
    }
    private static void saveWeak(SharedPreferences p,String prefix,Set<Integer> weak){
        StringBuilder b=new StringBuilder();
        for(int idx:weak){if(b.length()>0)b.append(',');b.append(idx);}
        p.edit().putString(prefix+"weak",b.toString()).apply();
    }
    private static String[] refs(LocalStore.Entry entry){
        List<Bible.Verse> lines=entry.lines();
        String[] output=new String[lines.size()];
        for(int i=0;i<lines.size();i++)output[i]=lines.get(i).ref;
        return output;
    }

    public static boolean launchIfDue(Activity a,LocalStore.Entry entry,boolean[] ready,
                                      IntUnaryOperator verseCount,Runnable continueLesson){
        String[] refs=refs(entry);
        if(refs.length<3)return false;
        SharedPreferences p=a.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String fingerprint=fingerprint(Arrays.asList(refs)),key=prefix(entry.id,fingerprint);
        SequenceRecallLogic.Plan next=SequenceRecallLogic.next(Arrays.asList(refs),ready,
                verseCount,completed(p,key),weak(p,key));
        if(next==null)return false;
        p.edit().putLong("active_entry",entry.id).putString("active_fingerprint",fingerprint)
                .putString("active_plan",next.id).apply();
        show(a,entry,next,key,continueLesson,false);
        return true;
    }
    public static boolean resumeIfPending(Activity a,LocalStore.Entry entry,
                                          boolean[] ready,IntUnaryOperator verseCount,
                                          Runnable continueLesson){
        SharedPreferences p=a.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String[] refs=refs(entry);
        String fp=fingerprint(Arrays.asList(refs));
        if(p.getLong("active_entry",-1)!=entry.id || !fp.equals(p.getString("active_fingerprint","")))
            return false;
        String key=prefix(entry.id,fp);
        SequenceRecallLogic.Plan plan=SequenceRecallLogic.next(Arrays.asList(refs),ready,
                verseCount,completed(p,key),weak(p,key));
        if(plan==null || !plan.id.equals(p.getString("active_plan",""))){
            p.edit().remove("active_entry").remove("active_fingerprint").remove("active_plan").apply();
            return false;
        }
        show(a,entry,plan,key,continueLesson,false);
        return true;
    }

    /** Opens the exact same exercise UI without modifying the real study queue or mastery.
     * Debug-only: the target list is explicitly chosen and all persistence is isolated.
     */
    static void preview(Activity a,LocalStore.Entry entry,
                        SequenceRecallLogic.Type type,List<Integer> targets,
                        boolean finalCheck){
        if(!DeveloperModeController.isEnabled(a) || targets==null || targets.isEmpty())return;
        SequenceRecallLogic.Plan plan=new SequenceRecallLogic.Plan(
            "DEBUG:"+type.name()+":"+targets.toString(),type,targets,finalCheck);
        show(a,entry,plan,"preview:",()->{},true);
    }

    private static void show(Activity a,LocalStore.Entry entry,SequenceRecallLogic.Plan plan,
                             String key,Runnable continueLesson,boolean preview){
        SharedPreferences p=a.getSharedPreferences(preview?"sv_sequence_dev":PREFS,Context.MODE_PRIVATE);
        List<Bible.Verse> lines=entry.lines();
        List<String> texts=new ArrayList<>();for(Bible.Verse line:lines)texts.add(line.text);
        if(!preview)a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        ScrollView scroller=new ScrollView(a);scroller.setFillViewport(false);
        scroller.setClipToPadding(false);
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(a,20),dp(a,21),dp(a,20),dp(a,24));
        scroller.addView(body,new android.widget.FrameLayout.LayoutParams(-1,-2));
        root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));

        TextView title=text(a,plan.type==SequenceRecallLogic.Type.STARTS?
                "Начала стихов":"Переходы между стихами",24,TEXT,true);
        add(body,title,0);
        if(preview)add(body,text(a,"РЕЖИМ РАЗРАБОТЧИКА · результаты не засчитываются",12,ERROR,true),8);
        add(body,text(a,entry.title+" · "+plan.targets.size()+" ответов",14,MUTED,false),7);
        add(body,text(a,plan.type==SequenceRecallLogic.Type.STARTS?
                "Вспомни начало по номеру стиха. Текст подсказкой не показывается.":
                "Прочитай конец предыдущего стиха и вспомни начало следующего.",15,MUTED,false),14);
        if(plan.finalCheck)add(body,text(a,"Итоговая проверка изученного отрывка",13,GREEN,true),12);

        List<EditText> inputs=new ArrayList<>();
        List<String> expected=new ArrayList<>();
        List<TextView> feedback=new ArrayList<>();
        List<LinearLayout> panels=new ArrayList<>();
        List<Integer> positions=plan.targets;
        for(int i=0;i<positions.size();i++){
            int target=positions.get(i);
            Bible.Verse line=lines.get(target);
            String answer=SequenceRecallLogic.beginning(line.text,texts);
            expected.add(answer);
            final int requiredWordCount=SequenceRecallLogic.words(answer).size();
            LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(a,14),dp(a,13),dp(a,14),dp(a,14));
            panel.setBackground(surface(a,SURFACE,BORDER,17));
            add(body,panel,i==0?16:9);panels.add(panel);
            if(plan.type==SequenceRecallLogic.Type.STARTS){
                add(panel,text(a,line.ref,15,GREEN,true),0);
            }else{
                add(panel,text(a,"После "+lines.get(target-1).ref,14,GREEN,true),0);
                add(panel,text(a,SequenceRecallLogic.ending(lines.get(target-1).text,7),15,TEXT,false),7);
            }
            add(panel,text(a,requiredWordsHint(plan.type,requiredWordCount),14,TEXT,true),11);
            EditText input=new EditText(a);
            input.setSingleLine(true);
            input.setTextSize(16);input.setTextColor(TEXT);input.setHintTextColor(0xFFA69A92);
            input.setHint("Впиши начало стиха…");
            input.setInputType(InputType.TYPE_CLASS_TEXT |
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            input.setPrivateImeOptions("nm");
            input.setBackgroundTintList(ColorStateList.valueOf(GREEN));
            input.setContentDescription("Начало "+line.ref);
            String answerKey=key+"answer:"+plan.id+":"+target;
            input.setText(p.getString(answerKey,""));
            input.setSelection(input.length());
            // Counter starts from the saved draft and stays in a fixed-height row.
            int initialEntered=SequenceRecallLogic.words(input.getText().toString()).size();
            TextView wordCounter=text(a,enteredWordsHint(initialEntered,requiredWordCount),
                    13,initialEntered>requiredWordCount?ERROR:MUTED,false);
            wordCounter.setMinHeight(dp(a,22));
            input.addTextChangedListener(new TextWatcher(){
                public void beforeTextChanged(CharSequence s,int st,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){
                    p.edit().putString(answerKey,s.toString()).apply();
                    int entered=SequenceRecallLogic.words(s.toString()).size();
                    wordCounter.setText(enteredWordsHint(entered,requiredWordCount));
                    // A red counter means only that too many words were typed,
                    // never an automatic correctness verdict.
                    wordCounter.setTextColor(entered>requiredWordCount?ERROR:MUTED);
                }
                public void afterTextChanged(Editable value){}
            });
            add(panel,input,8);inputs.add(input);
            add(panel,wordCounter,4);
            TextView note=text(a,"",13,ERROR,false);
            note.setVisibility(View.GONE);add(panel,note,5);feedback.add(note);
        }

        if(preview){
            TextView autofill=text(a,"Для проверки: заполнить правильными ответами",14,GREEN,true);
            autofill.setGravity(Gravity.CENTER);
            autofill.setPadding(dp(a,10),dp(a,13),dp(a,10),dp(a,13));
            autofill.setBackground(surface(a,0xFFE2EEE1,BORDER,13));
            add(body,autofill,15);
            autofill.setOnClickListener(v->{
                for(int i=0;i<inputs.size();i++){
                    if(inputs.get(i).isEnabled())inputs.get(i).setText(expected.get(i));
                }
            });
        }
        LinearLayout footer=new LinearLayout(a);footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(a,20),dp(a,11),dp(a,20),dp(a,18));
        footer.setBackgroundColor(SURFACE);
        TextView action=text(a,"Проверить",17,Color.WHITE,true);
        action.setGravity(Gravity.CENTER);action.setMinHeight(dp(a,54));
        action.setBackground(surface(a,GREEN,0,17));
        footer.addView(action,new LinearLayout.LayoutParams(-1,dp(a,54)));
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        final Dialog debugDialog;
        if(preview){
            debugDialog=new Dialog(a);
            debugDialog.setContentView(root);
            debugDialog.show();
            if(debugDialog.getWindow()!=null){
                debugDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                debugDialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
                debugDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        }else{
            debugDialog=null;
            a.setContentView(root);
        }

        action.setOnClickListener(v->{
            String label=action.getText().toString();
            if(label.equals("Продолжить")){
                if(preview){
                    if(debugDialog!=null)debugDialog.dismiss();
                    return;
                }
                p.edit().putBoolean(key+"done:"+plan.id,true)
                    .remove("active_entry").remove("active_fingerprint").remove("active_plan").apply();
                continueLesson.run();
                return;
            }
            if(label.equals("Повторить ошибки")){
                for(int i=0;i<inputs.size();i++){
                    EditText input=inputs.get(i);
                    boolean correct=SequenceRecallLogic.correct(expected.get(i),input.getText().toString());
                    if(!correct){input.setText("");input.setEnabled(true);}
                    feedback.get(i).setVisibility(View.GONE);
                    input.setBackgroundTintList(ColorStateList.valueOf(GREEN));
                }
                action.setText("Проверить");
                return;
            }
            boolean allCorrect=true;
            Set<Integer> failed=weak(p,key);
            for(int i=0;i<inputs.size();i++){
                EditText input=inputs.get(i);
                boolean ok=SequenceRecallLogic.correct(expected.get(i),input.getText().toString());
                int target=positions.get(i);
                if(ok){
                    input.setBackgroundTintList(ColorStateList.valueOf(GREEN));
                    input.setEnabled(false);failed.remove(target);
                    feedback.get(i).setText("✓ Верно");feedback.get(i).setTextColor(GREEN);
                }else{
                    allCorrect=false;failed.add(target);
                    input.setBackgroundTintList(ColorStateList.valueOf(ERROR));
                    feedback.get(i).setText("Правильно: "+expected.get(i));
                    feedback.get(i).setTextColor(ERROR);
                }
                feedback.get(i).setVisibility(View.VISIBLE);
            }
            saveWeak(p,key,failed);
            InputMethodManager keyboard=(InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if(keyboard!=null && !inputs.isEmpty())keyboard.hideSoftInputFromWindow(inputs.get(0).getWindowToken(),0);
            action.setText(allCorrect?"Продолжить":"Повторить ошибки");
            if(!allCorrect)scroller.smoothScrollTo(0,0);
        });
    }
}
