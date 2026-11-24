package org.biblememory.app;

import java.util.*;

/** Pure Java training logic; runnable independently of Android. */
public final class ReviewLogic {
    private ReviewLogic() {}

    public static List<String> words(String s) {
        return ExerciseTextLogic.words(s);
    }

    public static int accuracy(String expected,String actual) {
        return accuracy(words(expected),words(actual),false);
    }

    static int accuracy(List<String> expected,List<String> actual,boolean cancellable){
        int total=Math.max(expected.size(),actual.size());
        if(total==0)return 100;
        int start=0,endA=expected.size(),endB=actual.size();
        // Common edges can be removed without changing Levenshtein distance.
        while(start<endA&&start<endB&&expected.get(start).equals(actual.get(start)))start++;
        while(endA>start&&endB>start&&expected.get(endA-1).equals(actual.get(endB-1))){
            endA--;endB--;
        }
        List<String> a=expected.subList(start,endA),b=actual.subList(start,endB);
        if(a.size()<b.size()){
            List<String> swap=a;a=b;b=swap;
        }
        if(b.isEmpty())return score(a.size(),total);
        int[] prev=new int[b.size()+1],cur=new int[b.size()+1];
        for(int j=0;j<prev.length;j++)prev[j]=j;
        for(int i=1;i<=a.size();i++){
            if(cancellable&&Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException();
            cur[0]=i;
            String word=a.get(i-1);
            for(int j=1;j<=b.size();j++){
                int sub=prev[j-1]+(word.equals(b.get(j-1))?0:1);
                cur[j]=Math.min(sub,Math.min(prev[j]+1,cur[j-1]+1));
            }
            int[] swap=prev;prev=cur;cur=swap;
        }
        return score(prev[b.size()],total);
    }

    private static int score(int distance,int total){
        return Math.max(0,100-Math.round(100f*distance/total));
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

}
