package org.biblememory.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Two complementary recall drills inside one uninterrupted Biblical passage.
 * No Android dependency; does not modify Bible text or the original mastery state.
 */
public final class SequenceRecallLogic {
    private SequenceRecallLogic() {}
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{Nd}]+(?:[-‐‑–][\\p{L}\\p{Nd}]+)*");
    public enum Type { STARTS, BRIDGES }

    public static final class Plan {
        public final String id;
        public final Type type;
        public final List<Integer> targets; // zero-based target verse indices
        public final boolean finalCheck;
        Plan(String id, Type type, List<Integer> targets, boolean finalCheck) {
            this.id=id;this.type=type;
            this.targets=Collections.unmodifiableList(new ArrayList<>(targets));
            this.finalCheck=finalCheck;
        }
    }

    public static List<String> words(String text) {
        if(text==null||text.isEmpty())return Collections.emptyList();
        List<String> words=new ArrayList<>();
        Matcher m=WORD.matcher(text);
        while(m.find())words.add(m.group());
        return words;
    }

    /** The shortest useful beginning: start with two words, extend for ambiguity. */
    public static String beginning(String text,List<String> passageTexts) {
        List<String> source=words(text);
        if(source.isEmpty())return "";
        int length=Math.min(2,source.size());
        int limit=Math.min(8,source.size());
        while(length<limit && duplicates(source,length,passageTexts))length++;
        return String.join(" ",source.subList(0,length));
    }
    private static boolean duplicates(List<String> source,int length,List<String> others) {
        int duplicates=0;
        for(String text:others) {
            List<String> words=words(text);
            if(words.size()<length)continue;
            boolean same=true;
            for(int i=0;i<length;i++){
                if(!normalize(source.get(i)).equals(normalize(words.get(i)))){
                    same=false;break;
                }
            }
            if(same && ++duplicates>=2)return true;
        }
        return false;
    }
    public static String ending(String text,int limit) {
        List<String> parts=words(text);
        if(parts.isEmpty())return "";
        return "…"+String.join(" ",parts.subList(Math.max(0,parts.size()-limit),parts.size()));
    }

    public static String normalize(String text) {
        if(text==null)return "";
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT),Normalizer.Form.NFKC)
                .replace('ё','е').replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim().replaceAll(" +", " ");
    }
    public static boolean correct(String expected,String entered) {
        return !normalize(expected).isEmpty() && normalize(expected).equals(normalize(entered));
    }
    public static boolean adjacent(String earlier,String later,IntUnaryOperator versesInChapter) {
        try {
            String[] a=earlier.split(":",2), b=later.split(":",2);
            if(a.length!=2||b.length!=2)return false;
            int ca=Integer.parseInt(a[0]), cb=Integer.parseInt(b[0]);
            int va=Integer.parseInt(a[1]), vb=Integer.parseInt(b[1]);
            if(ca==cb)return vb==va+1;
            return cb==ca+1 && vb==1 && va==versesInChapter.applyAsInt(ca);
        }catch(RuntimeException ex){return false;}
    }
    public static int learnedPrefix(List<String> refs,boolean[] ready,IntUnaryOperator versesInChapter) {
        if(refs==null||ready==null||ready.length<refs.size())return 0;
        int n=0;
        for(int i=0;i<refs.size();i++) {
            if(!ready[i] || (i>0 && !adjacent(refs.get(i-1),refs.get(i),versesInChapter)))break;
            n++;
        }
        return n;
    }

    private static List<Integer> range(int begin,int end){
        List<Integer> out=new ArrayList<>();
        for(int i=begin;i<end;i++)out.add(i);
        return out;
    }
    private static Plan notDone(String id,Type type,List<Integer> targets,boolean isFinal,Set<String> done){
        if(targets.isEmpty()||done.contains(id))return null;
        return new Plan(id,type,targets,isFinal);
    }
    /** Suggest at most one next exercise. No unlearned verses, no unrelated references. */
    public static Plan next(List<String> refs,boolean[] learned,IntUnaryOperator verseCount,
                            Set<String> completed,Set<Integer> weak) {
        if(refs==null||refs.size()<3)return null;
        int n=learnedPrefix(refs,learned,verseCount);
        if(n<3)return null;
        Set<String> done=completed==null?Collections.emptySet():completed;
        Set<Integer> errors=weak==null?Collections.emptySet():weak;
        int checkpoint=n/3*3;
        if(checkpoint>=6) {
            List<Integer> starts=new ArrayList<>();
            int from=checkpoint==6?0:Math.max(0,checkpoint-3);
            for(int i=from;i<checkpoint;i++)starts.add(i);
            // Repeat a few failed items without repeating all older verses.
            for(int i=0;i<checkpoint && starts.size()<6;i++)if(errors.contains(i)&&!starts.contains(i))starts.add(i);
            Plan p=notDone("START:"+checkpoint,Type.STARTS,starts,false,done);
            if(p!=null)return p;
        }
        if(checkpoint>=3) {
            List<Integer> transitions=range(Math.max(1,checkpoint-3),checkpoint);
            Plan p=notDone("BRIDGE:"+checkpoint,Type.BRIDGES,transitions,false,done);
            if(p!=null)return p;
        }
        // Concluding exam in 8–12 verse windows. Every index is eventually tested.
        if(n==refs.size()&&n>=10){
            for(int begin=0;begin<n;begin+=10){
                int end=Math.min(n,begin+10);
                Plan p=notDone("FINAL_START:"+begin+":"+end,
                        Type.STARTS,range(begin,end),true,done);
                if(p!=null)return p;
            }
            for(int begin=0;begin<n;begin+=10){
                int end=Math.min(n,begin+10);
                Plan p=notDone("FINAL_BRIDGE:"+begin+":"+end,
                        Type.BRIDGES,range(Math.max(1,begin),end),true,done);
                if(p!=null)return p;
            }
        }
        return null;
    }
}
