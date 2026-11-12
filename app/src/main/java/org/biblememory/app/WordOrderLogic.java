package org.biblememory.app;

import java.util.*;

/** Pure Java engine for word-tile memorization. No Android dependencies. */
public final class WordOrderLogic {
    private WordOrderLogic() {}
    public static final int MAX_WORDS = 28;
    public static final int MIN_WORDS = 8;

    /** Split each verse into compact chunks, never merging verse boundaries. */
    public static List<String[]> chunks(List<String> verses) {
        return chunksForBudget(verses,MAX_WORDS);
    }
    /** Adaptive screen budget with punctuation-aware fragment boundaries. */
    public static List<String[]> chunksForBudget(List<String> verses,int maxTiles) {
        final int limit=Math.max(6,Math.min(MAX_WORDS,maxTiles));
        final int min=Math.max(4,Math.min(MIN_WORDS,limit/2));
        List<String[]> result = new ArrayList<>();
        for (String verse : verses) {
            if (verse == null || verse.trim().isEmpty()) continue;
            // Standalone punctuation (e.g. an em dash) is not a separate word tile.
            List<String> tokens = new ArrayList<>();
            String pendingPrefix = "";
            for (String part : verse.trim().split("\\s+")) {
                if (comparable(part).isEmpty()) {
                    if (part.equals("—") || part.equals("–") || part.equals("-") || tokens.isEmpty())
                        pendingPrefix += part + " ";
                    else tokens.set(tokens.size()-1,tokens.get(tokens.size()-1)+" "+part);
                } else {
                    tokens.add(pendingPrefix+part);
                    pendingPrefix="";
                }
            }
            if (!pendingPrefix.isEmpty() && !tokens.isEmpty())
                tokens.set(tokens.size()-1,tokens.get(tokens.size()-1)+" "+pendingPrefix.trim());
            String[] words = tokens.toArray(new String[0]);
            int start=0;
            while(start<words.length){
                int remain=words.length-start;
                if(remain<=limit){result.add(Arrays.copyOfRange(words,start,words.length));break;}
                int end=start+limit;
                // Prefer sentence edges or strong punctuation, without creating tiny fragments.
                for(int i=start+min;i<=start+limit;i++){
                    if(words[i-1].endsWith(".") || words[i-1].endsWith("!") || words[i-1].endsWith("?") || words[i-1].endsWith(";") || words[i-1].endsWith(":")){
                        if(words.length-i>=min||words.length-i==0)end=i;
                    }
                }
                if(words.length-end<min && words.length-end>0){
                    // Balance otherwise tiny trailing chunks, e.g. 30 words -> 15 + 15.
                    end=start+(remain+1)/2;
                }
                result.add(Arrays.copyOfRange(words,start,end));start=end;
            }
        }
        return result;
    }

    /** Text for matching only; actual displayed text retains original spelling and punctuation. */
    public static String comparable(String word) {
        return word.toLowerCase(Locale.ROOT).replace('ё','е')
            .replaceAll("[^\\p{L}\\p{Nd}]", "");
    }

    /** Identical repeated words are interchangeable; they still have distinct tile identities. */
    public static boolean accepts(String[] words, int nextPosition, int tileIndex) {
        return nextPosition >= 0 && nextPosition < words.length &&
            tileIndex >= 0 && tileIndex < words.length &&
            comparable(words[nextPosition]).equals(comparable(words[tileIndex]));
    }

    /** Deterministic under a given seed for tests, random per card in production. */
    public static int[] shuffledIndices(int count, long seed) {
        if (count < 0) throw new IllegalArgumentException("Negative count");
        int[] a = new int[count];
        for (int i = 0; i < count; i++) a[i] = i;
        Random random = new Random(seed);
        for (int i = count - 1; i > 0; i--) {
            int j = random.nextInt(i + 1),tmp = a[i];
            a[i] = a[j]; a[j] = tmp;
        }
        if (count > 1) {
            boolean unchanged = true;
            for (int i = 0; i < count; i++) if (a[i] != i) {unchanged=false;break;}
            if (unchanged) {int tmp=a[0];a[0]=a[1];a[1]=tmp;}
        }
        return a;
    }
}
