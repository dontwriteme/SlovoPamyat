package org.biblememory.app;

import android.content.Context;
import android.util.JsonReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
    private static final Bible SHARED=new Bible();
    static Bible shared(){return SHARED;}

    interface CorpusSource { InputStream open(Context context) throws IOException; }

    private final CorpusSource source;
    private final Object loadLock=new Object();
    // Readers see either no corpus or the complete, immutable result of a successful parse.
    private volatile Map<Integer, SortedMap<Integer, List<Verse>>> books;
    private CompletableFuture<Void> backgroundLoad;

    public Bible(){this(context->context.getAssets().open("synodal.json"));}
    Bible(CorpusSource source){this.source=source;}

    public boolean isReady(){return books!=null;}

    synchronized CompletableFuture<Void> loadInBackground(Context context){
        if(isReady())return CompletableFuture.completedFuture(null);
        if(backgroundLoad==null){
            Context application=context.getApplicationContext();
            backgroundLoad=CompletableFuture.runAsync(()->{
                try{load(application);}
                catch(IOException failure){throw new CompletionException(failure);}
            },task->new Thread(task,"BibleCorpus").start());
        }
        return backgroundLoad;
    }

    public int chapterCount(int bookId){
        Map<Integer,List<Verse>> chapters=chapters(books,bookId);
        return chapters.size();
    }

    private static Map<Integer,List<Verse>> chapters(
            Map<Integer,SortedMap<Integer,List<Verse>>> corpus,int book){
        if(corpus==null)return Collections.emptyMap();
        Map<Integer,List<Verse>> chapters=corpus.get(book);
        return chapters==null?Collections.emptyMap():chapters;
    }

    static final class Unavailable extends IllegalStateException {
        Unavailable(){super("Встроенный текст Библии ещё недоступен");}
    }

    boolean matchesVerse(int book,int chapter,int number,Verse verse){
        Map<Integer,SortedMap<Integer,List<Verse>>> corpus=books;
        if(corpus==null)throw new Unavailable();
        List<Verse> lines=chapters(corpus,book).get(chapter);
        if(lines==null||number<1)return false;
        // Numbering is taken from the corpus, including chapters with gaps.
        String reference=chapter+":"+number;
        if(!reference.equals(verse.ref))return false;
        if(number<=lines.size()&&lines.get(number-1).ref.equals(reference))
            return lines.get(number-1).text.equals(verse.text);
        for(Verse line:lines)if(line.ref.equals(reference))return line.text.equals(verse.text);
        return false;
    }
    public List<Verse> chapter(int bookId,int chapter){
        List<Verse> lines=chapters(books,bookId).get(chapter);
        return lines==null?Collections.emptyList():new ArrayList<>(lines);
    }

    public List<Verse> passage(int b,int sc,int sv,int ec,int ev){
        return passage(b,sc,sv,ec,ev,500);
    }

    /** Collection selection may span any number of chapters inside one book. */
    public List<Verse> collectionPassage(int b,int sc,int sv,int ec,int ev){
        return passage(b,sc,sv,ec,ev,Integer.MAX_VALUE);
    }

    private List<Verse> passage(int b,int sc,int sv,int ec,int ev,int limit){
        Map<Integer,List<Verse>> chapters=chapters(books,b);
        if(b<1||b>66||sc<1||sv<1||ec<sc||ev<1||ec>chapters.size())
            throw new IllegalArgumentException("Неверный диапазон глав или стихов");
        List<Verse> out = new ArrayList<>();
        for (int c=sc;c<=ec;c++) {
            List<Verse> vs=chapters.get(c);
            if (vs==null||vs.isEmpty()) throw new IllegalArgumentException("Глава не найдена");
            int first=c==sc?sv:1;
            int last=c==ec?ev:vs.size();
            if (first>last || last>vs.size()) throw new IllegalArgumentException("Неверный номер стиха");
            for (int i=first;i<=last;i++) out.add(vs.get(i-1));
        }
        if (out.size()>limit) throw new IllegalArgumentException("Максимум 500 стихов в одном отрывке");
        return out;
    }
    /** Synchronous entry point for background callers and isolated tests. */
    public void load(Context context) throws IOException {
        if(isReady())return;
        synchronized(loadLock){
            if(!isReady())books=readCorpus(context);
        }
    }

    private Map<Integer,SortedMap<Integer,List<Verse>>> readCorpus(Context context)throws IOException{
        Map<Integer,SortedMap<Integer,List<Verse>>> result=new HashMap<>();
        try(JsonReader reader=new JsonReader(new InputStreamReader(source.open(context),StandardCharsets.UTF_8))){
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
        return Collections.unmodifiableMap(result);
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
        if(bookId>=1 && bookId<=66) output.put(bookId,Collections.unmodifiableSortedMap(chapters));
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
        chapters.put(chapter,Collections.unmodifiableList(out));
    }
    private static final class RawVerse {
        final int number; final String text;
        RawVerse(int n,String s){number=n;text=s;}
    }
    public static String reference(int b,int sc,int sv,int ec,int ev) {
        return NAMES[b-1]+" "+sc+":"+sv + (sc==ec && sv==ev ? "" : "–"+(sc==ec?"":ec+":")+ev);
    }
}
