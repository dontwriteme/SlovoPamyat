package org.biblememory.app;

import java.util.*;

/** Pure-Java three-phase lesson plan. Visible prompts are practice, not proof of recall. */
public final class LessonCurriculumLogic {
    private LessonCurriculumLogic() {}
    public enum Phase { INTRO, BUILD, WRITE, COMPLETE }
    public enum Variant { ENDING, GAPS, FULL }

    /** Existing 0..5 lesson_progress records are retained during upgrades. */
    public static Phase phase(int completed, boolean independentReview) {
        if (independentReview) return Phase.WRITE;
        if (completed >= 5) return Phase.COMPLETE;
        if (completed == 0) return Phase.INTRO;
        if (completed >= 3) return Phase.WRITE;
        return Phase.BUILD;
    }
    /** Varies exercise load by verse length; no needless tiny exercises. */
    public static List<Variant> variants(int wordCount) {
        if (wordCount <= 8) return Collections.singletonList(Variant.FULL);
        if (wordCount <= 20) return Arrays.asList(Variant.GAPS, Variant.FULL);
        return Arrays.asList(Variant.GAPS, Variant.ENDING, Variant.FULL);
    }
    /** The target word positions for an exercise; never changes original order. */
    public static int[] missingPositions(int size, Variant kind, int seed) {
        if (size <= 0) return new int[0];
        List<Integer> indexes = new ArrayList<>();
        if (kind == Variant.FULL) {
            for(int i=0;i<size;i++)indexes.add(i);
        } else if (kind == Variant.ENDING) {
            boolean startFirst=size==1 || (seed&1)==0;
            int midpoint=Math.max(1,size/2);
            for(int i=startFirst?0:midpoint;i<(startFirst?midpoint:size);i++)indexes.add(i);
        } else {
            List<Integer> all=new ArrayList<>();
            for(int i=0;i<size;i++)all.add(i);
            Collections.shuffle(all,new Random(seed));
            int amount=Math.min(size,Math.max(1,(int)Math.ceil(size*.4)));
            for(int i=0;i<amount;i++)indexes.add(all.get(i));
            Collections.sort(indexes);
        }
        int[] result=new int[indexes.size()];for(int i=0;i<result.length;i++)result[i]=indexes.get(i);
        return result;
    }
    /** Perfect word-for-word recall; capitalization, punctuation, е/ё are neutral. */
    public static boolean exact(String expected,String actual) {
        List<String> a=ReviewLogic.words(expected),b=ReviewLogic.words(actual);
        return !a.isEmpty() && a.equals(b);
    }
}
