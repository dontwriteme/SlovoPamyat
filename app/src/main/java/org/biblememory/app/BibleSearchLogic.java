
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

    public static String normalized(String value) {
        if (value == null) return "";

        return Normalizer.normalize(
                        value.toLowerCase(Locale.ROOT),
                        Normalizer.Form.NFKC
                )
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                .trim();
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

        Index index = INDEX;

        if (index == null ||
                bible == null ||
                !bible.isReady()) {

            return Collections.emptyList();
        }

        String phrase = normalized(query);

        if (phrase.isEmpty()) {
            return Collections.emptyList();
        }

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

            int book = index.verseField(id, 0);
            int chapter = index.verseField(id, 1);
            int number = index.verseField(id, 2);

            int chapterKey = book * 1000 + chapter;

            List<Bible.Verse> verses =
                    chapters.get(chapterKey);

            if (verses == null) {
                verses = bible.chapter(book, chapter);
                chapters.put(chapterKey, verses);
            }

            Bible.Verse verse = null;

            if (number >= 1 &&
                    number <= verses.size() &&
                    verses.get(number - 1).ref.endsWith(
                            ":" + number
                    )) {

                verse = verses.get(number - 1);
            }

            if (verse == null) {
                for (Bible.Verse candidate : verses) {
                    if (candidate.ref.endsWith(
                            ":" + number
                    )) {
                        verse = candidate;
                        break;
                    }
                }
            }

            if (verse == null) continue;

            String text = normalized(verse.text);

            boolean exactPhrase =
                    text.contains(phrase);

            if (!exactPhrase &&
                    !matching(terms, wordStems(text))) {

                continue;
            }

            Hit hit = new Hit(
                    book,
                    chapter,
                    number,
                    Bible.reference(
                            book,
                            chapter,
                            number,
                            chapter,
                            number
                    ),
                    verse.text,
                    exactPhrase ? 10 : 1
            );

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
