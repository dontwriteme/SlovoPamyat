package org.biblememory.app;

import android.content.Context;
import android.util.JsonReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Read-only local Scripture data. The Bible JSON is bundled into the APK at build time. */
public final class Bible {
    public static final String[] NAMES = {
        "Бытие","Исход","Левит","Числа","Второзаконие","Иисус Навин","Судьи","Руфь",
        "1 Царств","2 Царств","3 Царств","4 Царств","1 Паралипоменон","2 Паралипоменон",
        "Ездра","Неемия","Есфирь","Иов","Псалтирь","Притчи","Екклесиаст",
        "Песнь Песней","Исаия","Иеремия","Плач Иеремии","Иезекииль","Даниил",
        "Осия","Иоиль","Амос","Авдий","Иона","Михей","Наум","Аввакум","Софония",
        "Аггей","Захария","Малахия","Матфея","Марка","Луки","Иоанна","Деяния",
        "Римлянам","1 Коринфянам","2 Коринфянам","Галатам","Ефесянам","Филиппийцам",
        "Колоссянам","1 Фессалоникийцам","2 Фессалоникийцам","1 Тимофею","2 Тимофею",
        "Титу","Филимону","Евреям","Иакова","1 Петра","2 Петра","1 Иоанна",
        "2 Иоанна","3 Иоанна","Иуды","Откровение"
    };

    public static final class Verse {
        public final String ref, text;
        public Verse(String ref, String text) { this.ref=ref; this.text=text; }
    }
    private final Map<Integer, SortedMap<Integer, List<Verse>>> books = new HashMap<>();
    private boolean ready;
    public synchronized boolean isReady() { return ready; }
    public synchronized int chapterCount(int bookId) {
        SortedMap<Integer,List<Verse>> chapters=books.get(bookId);
        return chapters == null ? 0 : chapters.size();
    }
    public synchronized List<Verse> chapter(int bookId,int c) {
        SortedMap<Integer,List<Verse>> chapters = books.get(bookId);
        return chapters == null || !chapters.containsKey(c) ? Collections.emptyList()
                 : new ArrayList<>(chapters.get(c));
    }
    public synchronized List<Verse> passage(int b, int sc, int sv, int ec, int ev) {
        if (b<1 || b>66 || sc<1 || sv<1 || ec<sc || ev<1 || ec>chapterCount(b))
            throw new IllegalArgumentException("Неверный диапазон глав или стихов");
        List<Verse> out = new ArrayList<>();
        for (int c=sc;c<=ec;c++) {
            List<Verse> vs=chapter(b,c);
            if (vs.isEmpty()) throw new IllegalArgumentException("Глава не найдена");
            int first=c==sc?sv:1;
            int last=c==ec?ev:vs.size();
            if (first>last || last>vs.size()) throw new IllegalArgumentException("Неверный номер стиха");
            for (int i=first;i<=last;i++) out.add(vs.get(i-1));
        }
        if (out.size()>500) throw new IllegalArgumentException("Максимум 500 стихов в одном отрывке");
        return out;
    }
    public synchronized void load(Context context) throws IOException {
        if (ready) return;
        Map<Integer, SortedMap<Integer, List<Verse>>> result = new HashMap<>();
        try (JsonReader reader = new JsonReader(new InputStreamReader(
                context.getAssets().open("synodal.json"), StandardCharsets.UTF_8))) {
            reader.beginObject();
            while(reader.hasNext()) {
                String field=reader.nextName();
                if (!field.equals("books")) { reader.skipValue(); continue; }
                reader.beginArray();
                while(reader.hasNext()) readBook(reader,result);
                reader.endArray();
            }
            reader.endObject();
        }
        if (result.size()!=66) throw new IOException("Набор Библии неполный (ожидалось 66 книг)");
        books.clear(); books.putAll(result); ready=true;
    }
    private void readBook(JsonReader r, Map<Integer,SortedMap<Integer,List<Verse>>> output) throws IOException {
        int bookId=0;
        SortedMap<Integer,List<Verse>> chapters=new TreeMap<>();
        r.beginObject();
        while(r.hasNext()) {
            switch(r.nextName()) {
                case "bookId": bookId=r.nextInt(); break;
                case "chapters":
                    r.beginArray();
                    while(r.hasNext()) readChapter(r,chapters);
                    r.endArray(); break;
                default: r.skipValue();
            }
        }
        r.endObject();
        if(bookId>=1 && bookId<=66) output.put(bookId,chapters);
    }
    private void readChapter(JsonReader r, SortedMap<Integer,List<Verse>> chapters) throws IOException {
        int chapter=0; List<RawVerse> verses=new ArrayList<>();
        r.beginObject();
        while(r.hasNext()) {
            switch(r.nextName()) {
                case "chapter": chapter=r.nextInt(); break;
                case "verses":
                    r.beginArray();
                    while(r.hasNext()) {
                        int n=0; String text="";
                        r.beginObject();
                        while(r.hasNext()) {
                            String key=r.nextName();
                            if(key.equals("number")) n=r.nextInt();
                            else if(key.equals("text")) text=r.nextString();
                            else r.skipValue();
                        }
                        r.endObject();
                        if(n>0) verses.add(new RawVerse(n,text));
                    }
                    r.endArray(); break;
                default: r.skipValue();
            }
        }
        r.endObject();
        if(chapter<1) return;
        verses.sort(Comparator.comparingInt(v->v.number));
        // Reference strings are assigned when a passage is selected, using the book id.
        // Until then chapter verses store chapter:verse; the UI prepends the book name.
        List<Verse> out=new ArrayList<>();
        for(RawVerse v:verses) out.add(new Verse(chapter+":"+v.number,v.text));
        chapters.put(chapter,out);
    }
    private static final class RawVerse {
        final int number; final String text;
        RawVerse(int n,String s){number=n;text=s;}
    }
    public static String reference(int b,int sc,int sv,int ec,int ev) {
        return NAMES[b-1]+" "+sc+":"+sv + (sc==ec && sv==ev ? "" : "–"+(sc==ec?"":ec+":")+ev);
    }
}
