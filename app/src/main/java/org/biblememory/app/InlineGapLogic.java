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
