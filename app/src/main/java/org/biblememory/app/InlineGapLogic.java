package org.biblememory.app;

import java.util.*;

/** Model for editable inline blanks; no dependency on Android UI. */
public final class InlineGapLogic {
    public static final String SEPARATOR="\u001f";
    private InlineGapLogic(){}
    /** Original visible placeholder is measured by letters, not by characters of punctuation. */
    public static int letters(String token) {
        if(token==null)return 0;
        return (int) token.codePoints().filter(Character::isLetter).count();
    }
    public static String core(String token) {
        if(token==null)return "";
        return token.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
    }
    public static String suffix(String token) {
        if(token==null)return "";
        String core=core(token);int last=token.lastIndexOf(core);
        return core.isEmpty()?token:token.substring(last+core.length());
    }
    public static String prefix(String token) {
        String word=core(token);
        return word.isEmpty()?token:token.substring(0,token.indexOf(word));
    }
    /** Keep source indices stable for persisted masks and drafts. */
    public static int[] wordPositions(String[] tokens,int[] positions) {
        return Arrays.stream(positions).filter(i->i>=0&&i<tokens.length&&
            !ReviewLogic.words(tokens[i]).isEmpty()).toArray();
    }
    public static String[] restore(String saved,String[] tokens,int[] oldPositions) {
        int[] valid=wordPositions(tokens,oldPositions);
        String[] parts=saved==null?new String[0]:saved.split(SEPARATOR,-1);
        if(parts.length!=oldPositions.length)return restore(saved,valid.length);
        String[] result=new String[valid.length];
        for(int i=0;i<valid.length;i++)result[i]=parts[Arrays.binarySearch(oldPositions,valid[i])];
        return result;
    }
    /** Each visible field must independently contain its expected lexical answer. */
    public static boolean complete(List<String> expected,String[] answers,boolean special) {
        if(expected.isEmpty()||expected.size()!=answers.length)return false;
        for(int i=0;i<answers.length;i++)
            if(!AcceptedTextLogic.exact(expected.get(i),answers[i],special))return false;
        return true;
    }
    public static String serialize(String[] words){return String.join(SEPARATOR,words);}
    public static String[] restore(String saved,int count) {
        String[] out=new String[count];Arrays.fill(out, "");
        if(saved==null || saved.isEmpty())return out;
        String[] parts=saved.split(SEPARATOR,-1);
        if(parts.length==count)System.arraycopy(parts,0,out,0,count);
        return out;
    }
    public static String response(String[] answers){
        ArrayList<String> result=new ArrayList<>();
        for(String answer:answers)if(answer!=null&&!answer.trim().isEmpty())result.add(answer.trim());
        return String.join(" ",result);
    }
    /** Space is a focus separator; only an allowed two-word variant may retain an interior space. */
    public static boolean keepSpaceInside(String original, String typed, boolean matthew513) {
        if(!matthew513 || !"негодна".equals(ReviewLogic.words(original).stream().findFirst().orElse("")))return false;
        String t=typed.trim().toLowerCase(Locale.ROOT).replace('ё','е');
        return "не".equals(t);
    }
}
