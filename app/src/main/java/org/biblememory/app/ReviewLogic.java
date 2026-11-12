package org.biblememory.app;

import java.text.Normalizer;
import java.util.*;

/** Pure Java training logic; runnable independently of Android. */
public final class ReviewLogic {
    private ReviewLogic() {}

    public static List<String> words(String s) {
        if (s==null) return Collections.emptyList();
        String normalized=Normalizer.normalize(s,Normalizer.Form.NFKC)
           .toLowerCase(Locale.ROOT).replace('ё','е');
        String[] split=normalized.split("[^\\p{L}\\p{N}]+",-1);
        List<String> result=new ArrayList<>();
        for(String w:split) if(!w.isEmpty()) result.add(w);
        return result;
    }
    public static int accuracy(String expected,String actual) {
        List<String> a=words(expected), b=words(actual);
        int m=Math.max(a.size(),b.size());
        if(m==0) return 100;
        // Levenshtein on words: substitutions, insertions and omissions count as errors.
        // Two linear-memory rows keep this viable for multi-verse passages.
        int[] prev=new int[b.size()+1], cur=new int[b.size()+1];
        for(int j=0;j<prev.length;j++) prev[j]=j;
        for(int i=1;i<=a.size();i++) {
            cur[0]=i;
            for(int j=1;j<=b.size();j++) {
                int sub=prev[j-1] + (a.get(i-1).equals(b.get(j-1))?0:1);
                cur[j]=Math.min(sub,Math.min(prev[j]+1,cur[j-1]+1));
            }
            int[] swap=prev;prev=cur;cur=swap;
        }
        return Math.max(0,100-Math.round(100f*prev[b.size()]/m));
    }
    public static String masked(String text,int percent,int seed) {
        if(text==null||percent<=0)return text;
        // Exact whole-word percentages; deterministic, and each 25% step only adds masks.
        String[] pieces=text.split("(?<=\\s)|(?=\\s)");
        List<Integer> wordLocations=new ArrayList<>();
        for(int i=0;i<pieces.length;i++)if(!words(pieces[i]).isEmpty())wordLocations.add(i);
        int total=wordLocations.size();
        int maskCount=Math.min(total,(int)Math.ceil(total*Math.min(percent,100)/100.0));
        List<Integer> order=new ArrayList<>();
        for(int i=0;i<total;i++)order.add(i);
        Collections.shuffle(order,new Random(seed));
        Set<Integer> hideLocations=new HashSet<>();
        for(int i=0;i<maskCount;i++)hideLocations.add(wordLocations.get(order.get(i)));
        StringBuilder result=new StringBuilder();
        for(int i=0;i<pieces.length;i++) {
            if(hideLocations.contains(i))
                result.append(pieces[i].replaceAll("[\\p{L}\\p{N}]","▰"));
            else result.append(pieces[i]);
        }
        return result.toString();
    }
    /** Word offsets for true on-screen Android blur without changing text width or line breaks. */
    public static List<int[]> blurRanges(String text,int percent,int seed) {
        List<int[]> ranges=new ArrayList<>();
        if(text==null||percent<=0)return ranges;
        // Android's java.util.regex.Pattern does not support UNICODE_CHARACTER_CLASS.
        // Iterate Unicode code points instead, preserving UTF-16 offsets for Spannable spans.
        int start=-1;
        for(int i=0;i<text.length();) {
            int cp=text.codePointAt(i);
            int next=i+Character.charCount(cp);
            int type=Character.getType(cp);
            boolean mark=type==Character.NON_SPACING_MARK ||
                type==Character.COMBINING_SPACING_MARK || type==Character.ENCLOSING_MARK;
            boolean word=Character.isLetterOrDigit(cp) || (mark && start>=0);
            if(word && start<0)start=i;
            if(!word && start>=0){ranges.add(new int[]{start,i});start=-1;}
            i=next;
        }
        if(start>=0)ranges.add(new int[]{start,text.length()});
        int count=Math.min(ranges.size(),(int)Math.ceil(ranges.size()*Math.min(100,percent)/100.0));
        List<Integer> order=new ArrayList<>();
        for(int i=0;i<ranges.size();i++)order.add(i);
        Collections.shuffle(order,new Random(seed));
        List<int[]> selected=new ArrayList<>();
        for(int i=0;i<count;i++)selected.add(ranges.get(order.get(i)));
        selected.sort(Comparator.comparingInt(a->a[0]));
        return selected;
    }

    public static final class Schedule {
        public final int repetitions, intervalDays;
        public final double ease;
        public final long nextDue;
        public Schedule(int reps,int days,double ease,long due) {
            this.repetitions=reps;this.intervalDays=days;this.ease=ease;this.nextDue=due;
        }
    }
    public static Schedule update(int repetitions,int previousInterval,double previousEase,
                                   int accuracy,long now,boolean passageCompleted) {
        // SM-2-inspired schedule, based on objective word accuracy.
        int quality=accuracy>=99?5:accuracy>=90?4:accuracy>=75?3:accuracy>=60?2:1;
        if(!passageCompleted) return new Schedule(repetitions,previousInterval,previousEase,now);
        if(quality<3) return new Schedule(0,1,Math.max(1.3,previousEase),now+86400000L);
        double ease=Math.max(1.3,previousEase+0.1-(5-quality)*(0.08+(5-quality)*0.02));
        int nextRep=repetitions+1;
        int interval=nextRep==1?1:nextRep==2?3:Math.max(1,Math.round((float)(previousInterval*ease)));
        return new Schedule(nextRep,interval,ease,now+interval*86400000L);
    }
}
