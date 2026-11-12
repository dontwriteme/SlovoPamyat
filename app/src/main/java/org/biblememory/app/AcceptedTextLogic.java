package org.biblememory.app;

import java.util.*;

/** Strict word-for-word comparison with a documented, verse-specific Synodal variant. */
public final class AcceptedTextLogic {
    private AcceptedTextLogic() {}
    public static boolean matthew513(String passageReference, List<String> refs) {
        if(passageReference==null || !passageReference.startsWith("Матфея "))return false;
        for(String ref:refs)if("5:13".equals(ref))return true;
        return false;
    }
    public static List<String> accepted(String expected,boolean isMatthew513) {
        ArrayList<String> options=new ArrayList<>();
        options.add(expected);
        if(isMatthew513) {
            String variant=expected;
            if(expected.toLowerCase(Locale.ROOT).contains("негодна"))
                variant=expected.replaceAll("(?iu)(?<![\\p{L}\\p{N}])негодна(?![\\p{L}\\p{N}])", "не годна");
            else
                variant=expected.replaceAll("(?iu)(?<![\\p{L}\\p{N}])не\\s+годна(?![\\p{L}\\p{N}])", "негодна");
            if(!variant.equals(expected))options.add(variant);
        }
        return options;
    }
    public static boolean exact(String expected,String actual,boolean matthew513) {
        for(String x:accepted(expected,matthew513))if(LessonCurriculumLogic.exact(x,actual))return true;
        return false;
    }
    public static String bestExpected(String expected,String actual,boolean matthew513) {
        String best=expected;int quality=-1;
        for(String candidate:accepted(expected,matthew513)) {
            int score=ReviewLogic.accuracy(candidate,actual);
            if(score>quality){best=candidate;quality=score;}
        }
        return best;
    }
}
