package org.biblememory.app;

import java.text.Normalizer;
import java.util.*;

/** Shared lexical contract. Display tokens retain punctuation and legacy source indices;
 * comparison expands punctuation-separated components (including hyphenated words).
 */
public final class ExerciseTextLogic {
    private ExerciseTextLogic() {}
    public static String[] tokens(String text) {
        return text==null||text.trim().isEmpty()?new String[0]:text.trim().split("\\s+");
    }
    public static boolean isWord(String token) { return !words(token).isEmpty(); }
    public static List<String> words(String s) {
        if (s==null) return Collections.emptyList();
        String normalized=Normalizer.normalize(s,Normalizer.Form.NFKC)
           .toLowerCase(Locale.ROOT).replace('ё','е');
        String[] split=normalized.split("[^\\p{L}\\p{N}]+",-1);
        List<String> result=new ArrayList<>();
        for(String w:split) if(!w.isEmpty()) result.add(w);
        return result;
    }
}
