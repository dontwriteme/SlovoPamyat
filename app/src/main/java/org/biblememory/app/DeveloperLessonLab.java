package org.biblememory.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Debug-only launcher. All lesson screens use the production renderer in an isolated Activity. */
final class DeveloperLessonLab {
    static final String EXTRA="lesson_lab_spec";
    enum Test {
        FLOW("Полный цикл обучения"),
        INTRO("Ознакомление · 0%"), INTRO25("Ознакомление · 25%"),
        INTRO50("Ознакомление · 50%"), INTRO75("Ознакомление · 75%"),
        INTRO100("Ознакомление · 100%"),
        BUILD_GAPS("Сборка · пропуски"), BUILD_FIRST("Сборка · скрыто начало"),
        BUILD_LAST("Сборка · скрыт конец"), BUILD_FULL("Сборка · все слова"),
        WRITE_GAPS("Написание · пропуски"), WRITE_FIRST("Написание · скрыто начало"),
        WRITE_LAST("Написание · скрыт конец"), WRITE_FULL("Написание · весь текст"),
        WRITE_WEAK("Написание · слабый фрагмент"),
        EMPTY("Проверка · пустой ответ"), WRONG("Проверка · ошибочный ответ"),
        SUCCESS("Завершение стиха / связки"), COLLECTION("Завершение коллекции"),
        STARTS("Начала стихов"), BRIDGES("Переходы между стихами"),
        FINAL_STARTS("Итоговая проверка начал"), FINAL_BRIDGES("Итоговая проверка переходов");
        final String label;
        Test(String label){this.label=label;}
        @Override public String toString(){return label;}
        boolean sequence(){return this==STARTS||this==BRIDGES||this==FINAL_STARTS||this==FINAL_BRIDGES;}
        boolean build(){return name().startsWith("BUILD_");}
        boolean write(){return name().startsWith("WRITE_")||this==EMPTY||this==WRONG;}
        boolean expanded(){return build()||write();}
        int variant(){return name().endsWith("GAPS")?0:
            name().endsWith("FIRST")||name().endsWith("LAST")?1:2;}
        int mask(){switch(this){case INTRO25:return 25;case INTRO50:return 50;case INTRO75:return 75;case INTRO100:return 100;default:return 0;}}
    }
    static final class Spec {
        final String reference; final List<Bible.Verse> lines; final Test test; final int landscape;
        Spec(String reference,List<Bible.Verse> lines,Test test,int landscape){
            this.reference=reference;this.lines=new ArrayList<>(lines);this.test=test;this.landscape=landscape;
        }
        String encode(){
            try{
                JSONObject object=new JSONObject().put("reference",reference).put("test",test.name()).put("landscape",landscape);
                JSONArray array=new JSONArray();
                for(Bible.Verse v:lines)array.put(new JSONObject().put("ref",v.ref).put("text",v.text));
                String result=object.put("lines",array).toString();
                if(result.length()>100000||lines.size()>200)throw new IllegalArgumentException("Выбери меньший отрывок (до 200 стихов).");
                return result;
            }catch(JSONException e){throw new IllegalArgumentException(e);}
        }
        static Spec decode(String raw){
            try{
                if(raw==null||raw.length()>100000)throw new IllegalArgumentException("Invalid lab material");
                JSONObject o=new JSONObject(raw);JSONArray a=o.getJSONArray("lines");
                if(a.length()<1||a.length()>200)throw new IllegalArgumentException("Invalid lab length");
                List<Bible.Verse> lines=new ArrayList<>();
                for(int i=0;i<a.length();i++){
                    JSONObject v=a.getJSONObject(i);lines.add(new Bible.Verse(v.getString("ref"),v.getString("text")));
                }
                return new Spec(o.getString("reference"),lines,Test.valueOf(o.getString("test")),o.optInt("landscape",-1));
            }catch(JSONException e){throw new IllegalArgumentException(e);}
        }
    }
    private static final class Source {
        final String name,reference;final int book;final List<Bible.Verse> lines;
        Source(String name,String reference,int book,List<Bible.Verse> lines){
            this.name=name;this.reference=reference;this.book=book;this.lines=lines;
        }
        @Override public String toString(){return name+" · "+lines.size()+" ст.";}
        String ref(List<Bible.Verse> selected){
            if(book>0)try{
                String[] a=selected.get(0).ref.split(":"),b=selected.get(selected.size()-1).ref.split(":");
                return Bible.reference(book,Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(b[0]),Integer.parseInt(b[1]));
            }catch(RuntimeException ignored){}
            return reference;
        }
    }
    private static void chapter(List<Source> sources,Bible bible,int book,int chapter,String name){
        List<Bible.Verse> lines=bible.chapter(book,chapter);
        if(!lines.isEmpty())sources.add(new Source(name,Bible.NAMES[book-1]+" "+chapter,book,lines));
    }
    private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
    private static void label(Activity a,LinearLayout box,String s){
        TextView t=new TextView(a);t.setText(s);t.setTextColor(0xFF35452F);t.setTextSize(14);
        t.setPadding(0,dp(a,12),0,dp(a,4));box.addView(t);
    }
    private static <T> Spinner spinner(Activity a,LinearLayout box,List<T> items){
        Spinner v=new Spinner(a);
        ArrayAdapter<T> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_item,items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        v.setAdapter(adapter);box.addView(v,new LinearLayout.LayoutParams(-1,dp(a,48)));return v;
    }
    private static EditText number(Activity a,LinearLayout box,String initial){
        EditText v=new EditText(a);v.setInputType(InputType.TYPE_CLASS_NUMBER);v.setSingleLine(true);v.setText(initial);box.addView(v);return v;
    }
    private static void listen(Spinner spinner,Runnable callback){
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int position,long id){callback.run();}
            public void onNothingSelected(AdapterView<?> p){}
        });
    }
    static void open(Activity a,LocalStore.Entry entry){
        if(!DeveloperModeController.isEnabled(a))return;
        ArrayList<Source> sources=new ArrayList<>();
        sources.add(new Source("Текущий отрывок",entry.reference,LocalStore.bibleBookFromReference(entry.reference),entry.lines()));
        Bible bible=Bible.shared();
        if(bible.isReady()){
            chapter(sources,bible,48,1,"Галатам 1");
            chapter(sources,bible,40,5,"Матфея 5");
            chapter(sources,bible,19,118,"Псалом 118");
            chapter(sources,bible,17,8,"Есфирь 8 · длинные стихи");
            chapter(sources,bible,43,11,"Иоанна 11 · короткий стих 35");
        }
        LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a,20),dp(a,8),dp(a,20),dp(a,16));
        label(a,box,"Тестовая среда · настоящий прогресс не изменяется");
        label(a,box,"Источник текста");
        Spinner source=spinner(a,box,sources);
        label(a,box,"Объём текста");
        List<String> lengths=Arrays.asList("1 стих","2 стиха","3 стиха","4 стиха","5 стихов","6 стихов","10 стихов","20 стихов","Весь оставшийся отрывок","Своё количество");
        Spinner length=spinner(a,box,lengths);
        label(a,box,"Начать с позиции (в источнике)");
        EditText start=number(a,box,"1");
        label(a,box,"Количество стихов (для своего объёма)");
        EditText count=number(a,box,"3");count.setEnabled(false);
        listen(length,()->count.setEnabled(length.getSelectedItemPosition()==9));
        label(a,box,"Упражнение");
        Spinner test=spinner(a,box,Arrays.asList(Test.values()));
        label(a,box,"Пейзаж");
        List<String> landscapes=Arrays.asList("Случайный","Рассвет","Озеро","Осень","Лес","Закат","Долина","Пустыня","Побережье","Луг","Зима","Водопад","Ночь","Каньон","Река","Оливковая роща","Туман");
        Spinner landscape=spinner(a,box,landscapes);
        ScrollView scroll=new ScrollView(a);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Тесты обучения (DEV)").setView(scroll)
            .setNegativeButton("Отмена",null).setPositiveButton("Запустить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                Source material=(Source)source.getSelectedItem();
                int begin=Integer.parseInt(start.getText().toString())-1;
                int choice=length.getSelectedItemPosition();
                int[] sizes={1,2,3,4,5,6,10,20};
                int n=choice<8?sizes[choice]:choice==8?material.lines.size()-begin:Integer.parseInt(count.getText().toString());
                if(begin<0||n<=0||begin>=material.lines.size()||n>material.lines.size()-begin)
                    throw new IllegalArgumentException("В выбранном источнике нет такого диапазона.");
                List<Bible.Verse> lines=new ArrayList<>(material.lines.subList(begin,begin+n));
                Test selected=(Test)test.getSelectedItem();
                Spec spec=new Spec(material.ref(lines),lines,selected,landscape.getSelectedItemPosition()-1);
                if(selected.sequence()){
                    boolean bridges=selected==Test.BRIDGES||selected==Test.FINAL_BRIDGES;
                    if(bridges&&n<2)throw new IllegalArgumentException("Для переходов выбери не менее двух стихов.");
                    LocalStore.Entry preview=new LocalStore.Entry();preview.id=entry.id;preview.title=spec.reference;preview.reference=spec.reference;
                    preview.linesJson=new JSONObject(spec.encode()).getJSONArray("lines").toString();
                    List<Integer> targets=new ArrayList<>();
                    for(int i=bridges?1:0;i<n;i++)targets.add(i);
                    dialog.dismiss();
                    SequenceRecallController.preview(a,preview,bridges?SequenceRecallLogic.Type.BRIDGES:SequenceRecallLogic.Type.STARTS,
                        targets,selected==Test.FINAL_STARTS||selected==Test.FINAL_BRIDGES);
                }else{
                    Intent intent=new Intent(a,DeveloperLessonActivity.class).putExtra(EXTRA,spec.encode());
                    a.startActivity(intent);dialog.dismiss();
                    if(a instanceof DeveloperLessonActivity)a.finish();
                }
            }catch(IllegalArgumentException|JSONException e){
                Toast.makeText(a,e.getMessage()==null?"Проверь диапазон":e.getMessage(),Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }
}
