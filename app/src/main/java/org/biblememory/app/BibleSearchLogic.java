
package org.biblememory.app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.regex.Pattern;

public final class BibleSearchLogic {

    private BibleSearchLogic() {}

    public static final class Hit {
        public final int book, chapter, verse, score;
        public final String reference, text;

        public Hit(
                int book,
                int chapter,
                int verse,
                String reference,
                String text,
                int score) {

            this.book = book;
            this.chapter = chapter;
            this.verse = verse;
            this.reference = reference;
            this.text = text;
            this.score = score;
        }
    }

    private static final String[] ENDINGS = {
            "иями","ями","ами","остью","ости",
            "овать","евать","ящего","ющих",
            "ному","ными","имым","емый",
            "шая","шее","ших","ого","ему",
            "иях","ыми","ими","ах","ях",
            "ов","ев","ей","ою","ею",
            "ые","ие","ий","ый","ая","яя",
            "ое","ее","ому","ых","их",
            "ам","ям","ом","ем","ую","юю",
            "ить","еть","ть","ся","ет","ют",
            "ат","ят","ит","ал","ил","ела",
            "а","я","ы","и","е","о","у","ю"
    };

    private static final Pattern SEPARATORS=Pattern.compile("[^\\p{L}\\p{Nd}]+");
    public static final Pattern WORDS=Pattern.compile("[\\p{L}]+");

    public static boolean isBlankQuery(String query){
        if(query==null)return true;
        for(int i=0;i<query.length();i++){
            char c=query.charAt(i);
            if(!Character.isWhitespace(c)&&!Character.isSpaceChar(c))return false;
        }
        return true;
    }

    public static String normalized(String value) {
        if (value == null) return "";

        String normalized=Normalizer.normalize(value.toLowerCase(Locale.ROOT),Normalizer.Form.NFKC).replace('ё','е');
        return SEPARATORS.matcher(normalized).replaceAll(" ").trim();
    }

    private static String stemWord(String word) {
        switch (word) {
            case "любовь":
            case "любви":
            case "любовью":
            case "любовию":
                return "любов";

            case "жизнь":
            case "жизни":
            case "жизнью":
                return "жизн";

            case "бога":
            case "богу":
            case "богом":
            case "боге":
                return "бог";

            case "господа":
            case "господу":
            case "господом":
                return "господ";
        }

        if (word.length() < 5) return word;

        for (String ending : ENDINGS) {
            if (word.endsWith(ending) &&
                    word.length() - ending.length() >= 4) {

                return word.substring(
                        0,
                        word.length() - ending.length()
                );
            }
        }

        return word;
    }

    public static String stem(String value) {
        return stemWord(normalized(value));
    }

    private static String[] wordStems(String text) {
        if (text.isEmpty()) return new String[0];

        String[] words = text.split(" +");

        for (int i = 0; i < words.length; i++) {
            words[i] = stemWord(words[i]);
        }

        return words;
    }

    private static boolean matching(
            String[] needed,
            String[] words) {

        for (String term : needed) {
            boolean found = false;

            for (String word : words) {
                if (term.equals(word) ||
                        (term.length() >= 4 &&
                                word.length() >= 4 &&
                                (term.startsWith(word) ||
                                        word.startsWith(term)))) {

                    found = true;
                    break;
                }
            }

            if (!found) return false;
        }

        return true;
    }

    public static boolean allTermsMatch(
            String query,
            String text) {

        String q = normalized(query);

        return !q.isEmpty() &&
                matching(
                        wordStems(q),
                        wordStems(normalized(text))
                );
    }

    /** Query work is done once; each visible word is normalized/stemmed once. */
    public static final class WordMatcher {
        private final String[] terms;
        public WordMatcher(String query){terms=wordStems(normalized(query));}
        public boolean matches(String word){
            String stem=stemWord(normalized(word));
            for(String term:terms)if(term.equals(stem)||term.length()>=4&&stem.length()>=4&&
                (term.startsWith(stem)||stem.startsWith(term)))return true;
            return false;
        }
    }
    private static final class SearchText {
        final String text;
        final Hit exact,other;
        private volatile String[] stems;
        SearchText(int book,int chapter,int number,Bible.Verse verse){
            text=normalized(verse.text);
            String ref=Bible.reference(book,chapter,number,chapter,number);
            exact=new Hit(book,chapter,number,ref,verse.text,10);
            other=new Hit(book,chapter,number,ref,verse.text,1);
        }
        String[] stems(){
            String[] ready=stems;
            if(ready==null){ready=wordStems(text);stems=ready;}
            return ready;
        }
    }
    private static final class Corpus {
        final Bible bible;
        final AtomicReferenceArray<SearchText> text;
        Corpus(Bible bible,int count){this.bible=bible;text=new AtomicReferenceArray<>(count);}
    }
    private static final Pattern REFERENCE=Pattern.compile(
        "^(.+?)(?:\\s+(\\d+)(?:\\s*:\\s*(\\d+))?(?:\\s*[-–—]\\s*(?:(\\d+)\\s*:\\s*)?(\\d+))?)?$");
    private static final Map<String,Integer> BOOKS=bookNames();
    private static Map<String,Integer> bookNames(){
        Map<String,Integer> result=new HashMap<>();
        for(int b=1;b<=66;b++){
            result.put(normalized(Bible.NAMES[b-1]).replace(" ",""),b);
            result.put(normalized(BookColorLogic.shortName(b)).replace(" ",""),b);
        }
        result.put("мф",40);result.put("мк",41);result.put("лк",42);
        return result;
    }
    /** Null means a text query; an empty list means a recognized, invalid reference. */
    private static List<Hit> referenceHits(Bible bible,String query){
        java.util.regex.Matcher match=REFERENCE.matcher(query.trim());
        if(!match.matches())return null;
        Integer book=BOOKS.get(normalized(match.group(1)).replace(" ",""));
        if(book==null)return null;
        try{
            int sc=match.group(2)==null?1:Integer.parseInt(match.group(2));
            int sv=match.group(3)==null?1:Integer.parseInt(match.group(3));
            int ec=match.group(2)==null?bible.chapterCount(book):sc;
            int ev=match.group(3)==null?bible.chapter(book,ec).size():sv;
            if(match.group(5)!=null){
                if(match.group(4)!=null){ec=Integer.parseInt(match.group(4));ev=Integer.parseInt(match.group(5));}
                else if(match.group(3)==null){ec=Integer.parseInt(match.group(5));ev=bible.chapter(book,ec).size();}
                else ev=Integer.parseInt(match.group(5));
            }
            List<Hit> result=new ArrayList<>();
            for(Bible.Verse verse:bible.collectionPassage(book,sc,sv,ec,ev)){
                if(Thread.currentThread().isInterrupted())return Collections.emptyList();
                String[] coords=verse.ref.split(":",2);
                int c=Integer.parseInt(coords[0]),v=Integer.parseInt(coords[1]);
                result.add(new Hit(book,c,v,Bible.reference(book,c,v,c,v),verse.text,10));
            }
            return result;
        }catch(IllegalArgumentException invalid){return Collections.emptyList();}
    }
    // Бинарный индекс хранится готовым внутри APK.
    private static final int HEADER = 24;
    private static final int KEY_SIZE = 96;
    private static final int RECORD_SIZE = 104;

    private static final class Index {

        final ByteBuffer data;
        final int verseCount;
        final int termCount;
        final int dictionaryAt;
        final int postingsAt;
        private Corpus corpus;
        synchronized Corpus corpus(Bible bible){
            if(corpus==null||corpus.bible!=bible)corpus=new Corpus(bible,verseCount);
            return corpus;
        }

        Index(ByteBuffer buffer) throws IOException {
            data = buffer;

            if (data.limit() < HEADER ||
                    data.getInt(0) != 0x53564958 ||
                    data.getInt(4) != 1) {

                throw new IOException(
                        "Некорректный поисковый индекс"
                );
            }

            verseCount = data.getInt(8);
            termCount = data.getInt(12);
            dictionaryAt = data.getInt(16);
            postingsAt = data.getInt(20);

            if (verseCount <= 0 ||
                    termCount <= 0 ||
                    dictionaryAt != HEADER + verseCount * 12 ||
                    postingsAt !=
                            dictionaryAt + termCount * RECORD_SIZE ||
                    postingsAt > data.limit()) {

                throw new IOException(
                        "Поисковый индекс повреждён"
                );
            }
        }

        // Быстрый двоичный поиск слова.
        int findTerm(byte[] key) {
            int low = 0;
            int high = termCount - 1;

            while (low <= high) {
                int middle = (low + high) >>> 1;

                int address =
                        dictionaryAt + middle * RECORD_SIZE;

                int comparison = 0;

                for (int j = 0; j < KEY_SIZE; j++) {
                    int left =
                            key.length > j ? key[j] & 255 : 0;

                    int right = data.get(address + j) & 255;

                    if (left != right) {
                        comparison = left < right ? -1 : 1;
                        break;
                    }

                    if (left == 0) break;
                }

                if (comparison == 0) return address;

                if (comparison < 0) {
                    high = middle - 1;
                } else {
                    low = middle + 1;
                }
            }

            return -1;
        }

        int count(int record) {
            return data.getInt(record + 100);
        }

        int docId(int record, int position) {
            int relative = data.getInt(record + 96);

            return data.getInt(
                    postingsAt + relative + position * 4
            );
        }

        boolean contains(int record, int id) {
            int low = 0;
            int high = count(record) - 1;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                int value = docId(record, mid);

                if (value == id) return true;

                if (value < id) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }

            return false;
        }

        int verseField(int id, int field) {
            return data.getInt(
                    HEADER + id * 12 + field * 4
            );
        }
    }

    private static volatile Index INDEX;

    // Только открытие готового файла.
    // Никакого построения индекса на телефоне.
    public static void prepare(Context context)
            throws IOException {

        if (INDEX != null) return;

        synchronized (BibleSearchLogic.class) {
            if (INDEX != null) return;

            try (
                    AssetFileDescriptor fd =
                            context.getAssets().openFd("synodal.sidx");

                    FileInputStream file =
                            new FileInputStream(fd.getFileDescriptor())
            ) {

                ByteBuffer mapped =
                        file.getChannel().map(
                                FileChannel.MapMode.READ_ONLY,
                                fd.getStartOffset(),
                                fd.getLength()
                        ).order(ByteOrder.BIG_ENDIAN);

                INDEX = new Index(mapped);
            }
        }
    }

    // Возвращает ВСЕ совпадения одним списком.
    public static List<Hit> search(
            Bible bible,
            String query) {

        if(bible==null||!bible.isReady()||isBlankQuery(query)||
            Thread.currentThread().isInterrupted())return Collections.emptyList();
        List<Hit> reference=referenceHits(bible,query);
        if(reference!=null)return reference;
        Index index=INDEX;
        if(index==null)return Collections.emptyList();
        String phrase=normalized(query);
        if(phrase.isEmpty())return Collections.emptyList();

        String[] terms = wordStems(phrase);
        LinkedHashSet<String> keys =
                new LinkedHashSet<>();

        for (String term : terms) {
            keys.add(
                    term.length() >= 4
                            ? "#" + term.substring(0, 4)
                            : "=" + term
            );
        }

        int[] records = new int[keys.size()];
        int position = 0;
        int smallest = -1;

        for (String key : keys) {
            int record = index.findTerm(
                    key.getBytes(StandardCharsets.UTF_8)
            );

            if (record < 0) {
                return Collections.emptyList();
            }

            records[position++] = record;

            if (smallest < 0 ||
                    index.count(record) <
                            index.count(smallest)) {

                smallest = record;
            }
        }

        Corpus corpus=index.corpus(bible);
        List<Hit> exact = new ArrayList<>();
        List<Hit> other = new ArrayList<>();

        Map<Integer, List<Bible.Verse>> chapters =
                new HashMap<>();

        int candidates = index.count(smallest);

        for (int i = 0; i < candidates; i++) {

            if ((i & 255) == 0 &&
                    Thread.currentThread().isInterrupted()) {

                return Collections.emptyList();
            }

            int id = index.docId(smallest, i);
            boolean present = true;

            for (int record : records) {
                if (!index.contains(record, id)) {
                    present = false;
                    break;
                }
            }

            if (!present) continue;

            SearchText cached=corpus.text.get(id);
            if(cached==null){
                int book=index.verseField(id,0),chapter=index.verseField(id,1),number=index.verseField(id,2);
                int chapterKey=book*1000+chapter;
                List<Bible.Verse> verses=chapters.get(chapterKey);
                if(verses==null){verses=bible.chapter(book,chapter);chapters.put(chapterKey,verses);}
                if(number<1||number>verses.size())continue;
                cached=new SearchText(book,chapter,number,verses.get(number-1));
                corpus.text.compareAndSet(id,null,cached);
            }
            boolean exactPhrase=cached.text.contains(phrase);
            if(!exactPhrase&&!matching(terms,cached.stems()))continue;
            Hit hit=exactPhrase?cached.exact:cached.other;
            if (exactPhrase) {
                exact.add(hit);
            } else {
                other.add(hit);
            }
        }

        exact.addAll(other);

        return exact;
    }

    // Совместимость с прежними вызовами.
    // Старое ограничение результатов игнорируется.
    public static List<Hit> search(
            Bible bible,
            String query,
            int ignoredLimit) {

        return search(bible, query);
    }
}
