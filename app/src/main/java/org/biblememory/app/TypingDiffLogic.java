package org.biblememory.app;

import java.util.*;

/** Character-aligned corrections. Includes explicit omissions, extras and substitutions. */
public final class TypingDiffLogic {
    private TypingDiffLogic() {}
    public enum Kind {CORRECT,WRONG,EXTRA,MISSING}
    public static final class Piece {
        public final Kind kind;public final String text;
        Piece(Kind k,String t){kind=k;text=t;}
    }
    private static boolean equivalent(int a,int b) {
        if(a=='ё'||a=='Ё')a='е';
        if(b=='ё'||b=='Ё')b='е';
        return Character.toLowerCase(a)==Character.toLowerCase(b);
    }
    private static boolean same(String a,String b){
        return ReviewLogic.words(a).equals(ReviewLogic.words(b)) || a.trim().isEmpty()&&b.trim().isEmpty();
    }
    /** Input punctuation is never an error; normalize separators, retain typed spelling/case. */
    private static String withoutPunctuation(String s){
        StringBuilder out=new StringBuilder();boolean space=false;
        for(int at=0;at<s.length();){
            int cp=s.codePointAt(at);at+=Character.charCount(cp);
            if(Character.isLetterOrDigit(cp)){
                if(space && out.length()>0)out.append(' ');
                out.appendCodePoint(cp);space=false;
            }else space=true;
        }
        return out.toString();
    }
    public static final class Issue {
        public final String label,actual,expected;
        Issue(String label,String actual,String expected){
            this.label=label;this.actual=actual;this.expected=expected;
        }
    }
    /** Word-aligned, human-readable errors; punctuation is never part of an issue. */
    public static List<Issue> issues(String expected,String actual){
        List<String> a=ReviewLogic.words(expected),b=ReviewLogic.words(actual);
        List<Issue> out=new ArrayList<>();
        if((long)a.size()*b.size()>120000L){
            for(int k=0;k<Math.max(a.size(),b.size())&&out.size()<100;k++){
                String x=k<a.size()?a.get(k):"",y=k<b.size()?b.get(k):"";
                if(!x.equals(y))out.add(new Issue(x.isEmpty()?"Лишнее слово":y.isEmpty()?"Пропуск слова":"Проверь слово",y,x));
            }
            return out;
        }
        int[][] dp=new int[a.size()+1][b.size()+1];
        for(int i=0;i<=a.size();i++)dp[i][0]=i;
        for(int j=0;j<=b.size();j++)dp[0][j]=j;
        for(int i=1;i<=a.size();i++)for(int j=1;j<=b.size();j++)
            dp[i][j]=Math.min(dp[i-1][j-1]+(a.get(i-1).equals(b.get(j-1))?0:1),
                Math.min(dp[i-1][j]+1,dp[i][j-1]+1));
        int i=a.size(),j=b.size();List<Issue> reversed=new ArrayList<>();
        while(i>0||j>0){
            if(i>0&&j>0&&dp[i][j]==dp[i-1][j-1]+(a.get(i-1).equals(b.get(j-1))?0:1)){
                if(!a.get(i-1).equals(b.get(j-1)))
                    reversed.add(new Issue("Неправильное слово",b.get(j-1),a.get(i-1)));
                i--;j--;
            }else if(j>0&&dp[i][j]==dp[i][j-1]+1){
                reversed.add(new Issue("Лишнее слово",b.get(j-1),""));j--;
            }else{
                reversed.add(new Issue("Пропущенное слово","",a.get(i-1)));i--;
            }
        }
        Collections.reverse(reversed);return reversed;
    }
    private static void append(List<Piece> result,Kind kind,String text){
        if(text.isEmpty())return;
        if(!result.isEmpty() && result.get(result.size()-1).kind==kind){
            Piece p=result.remove(result.size()-1);result.add(new Piece(kind,p.text+text));
        }else result.add(new Piece(kind,text));
    }
    public static List<Piece> compare(String expected,String input) {
        if(expected==null)expected="";if(input==null)input="";
        expected=withoutPunctuation(expected);input=withoutPunctuation(input);
        // For long chapters, use a linear-memory word-alignment fallback to prevent UI OOM.
        if((long)expected.length()*input.length()>1800000L)return byWords(expected,input);
        return characters(expected,input);
    }
    private static List<Piece> characters(String expected,String input){
        int[] a=expected.codePoints().toArray(),b=input.codePoints().toArray();
        int[][] d=new int[a.length+1][b.length+1];
        for(int i=0;i<=a.length;i++)d[i][0]=i;
        for(int j=0;j<=b.length;j++)d[0][j]=j;
        for(int i=1;i<=a.length;i++)for(int j=1;j<=b.length;j++)
            d[i][j]=Math.min(d[i-1][j-1]+(equivalent(a[i-1],b[j-1])?0:1),
                Math.min(d[i-1][j]+1,d[i][j-1]+1));
        List<Piece> backwards=new ArrayList<>();int i=a.length,j=b.length;
        while(i>0||j>0){
            if(i>0&&j>0&&d[i][j]==d[i-1][j-1]+(equivalent(a[i-1],b[j-1])?0:1)){
                backwards.add(new Piece(equivalent(a[i-1],b[j-1])?Kind.CORRECT:Kind.WRONG,
                    new String(Character.toChars(b[j-1]))));i--;j--;
            }else if(j>0&&d[i][j]==d[i][j-1]+1){
                backwards.add(new Piece(Kind.EXTRA,new String(Character.toChars(b[j-1]))));j--;
            }else{
                backwards.add(new Piece(Kind.MISSING,new String(Character.toChars(a[i-1]))));i--;
            }
        }
        Collections.reverse(backwards);List<Piece> result=new ArrayList<>();
        for(Piece p:backwards)append(result,p.kind,p.text);
        return result;
    }
    private static List<Piece> byWords(String expected,String input){
        // Long passages: bound memory via a token-aligned prefix/suffix resynchronization.
        // Each inaccurate word is still highlighted at its actual location.
        String[] a=expected.split("(?<=\\s)|(?=\\s)");
        String[] b=input.split("(?<=\\s)|(?=\\s)");
        int i=0,j=0;List<Piece> result=new ArrayList<>();
        while(i<a.length||j<b.length){
            if(i<a.length&&j<b.length&&same(a[i],b[j])){append(result,Kind.CORRECT,b[j]);i++;j++;}
            else if(i+1<a.length && j<b.length && same(a[i+1],b[j])){append(result,Kind.MISSING,a[i]);i++;}
            else if(j+1<b.length && i<a.length && same(a[i],b[j+1])){append(result,Kind.EXTRA,b[j]);j++;}
            else if(i<a.length&&j<b.length){
                for(Piece p:characters(a[i],b[j]))append(result,p.kind,p.text);i++;j++;
            }else if(i<a.length){append(result,Kind.MISSING,a[i++]);}
            else{append(result,Kind.EXTRA,b[j++]);}
        }
        return result;
    }
}
