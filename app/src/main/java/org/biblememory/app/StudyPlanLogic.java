package org.biblememory.app;

import java.util.*;
import java.util.regex.*;

/** Pure Java step builder. Stages are independent; none is a prerequisite. */
public final class StudyPlanLogic {
    private StudyPlanLogic() {}
    public enum Stage {VERSE, CHAIN, SECTION, CHAPTER}
    public static final class Step {
        public final int start, end; // [start,end) line positions in the saved passage
        public final String title;
        public Step(int start,int end,String title) {
            this.start=start;this.end=end;this.title=title;
        }
    }
    private static final Pattern REF=Pattern.compile("^(\\d+):(\\d+)$");
    private static final Map<Integer,int[]> MATT_SECTIONS=new HashMap<>();
    static {
        MATT_SECTIONS.put(5,new int[]{1,13,17,21,27,31,33,38,43});
        MATT_SECTIONS.put(6,new int[]{1,5,16,19,25});
        MATT_SECTIONS.put(7,new int[]{1,6,7,13,15,21,24,28});
    }
    public static List<Step> build(List<String> refs, Stage stage, boolean sermon) {
        List<Step> out=new ArrayList<>();
        int n=refs.size(); if(n==0)return out;
        if(stage==Stage.VERSE) {
            for(int i=0;i<n;i++)out.add(new Step(i,i+1,refs.get(i)));
        } else if(stage==Stage.CHAIN) {
            if(n==1)out.add(new Step(0,1,refs.get(0)));
            else for(int i=0;i<n-1;i++) {
                int to=Math.min(n,i+3);
                out.add(new Step(i,to,refs.get(i)+" — "+refs.get(to-1)));
            }
        } else if(stage==Stage.SECTION) {
            // Named biblical sections are provided only for Matthew 5–7.
            // Other material uses neutral 5-verse blocks rather than guessed semantic divisions.
            if(sermon && parse(refs.get(0))!=null) {
                int start=0;
                for(int i=1;i<=n;i++) {
                    if(i==n || isSectionStart(refs.get(i),refs.get(i-1))) {
                        out.add(new Step(start,i,sectionTitle(refs.get(start))+" · "+refs.get(start)+"–"+refs.get(i-1)));
                        start=i;
                    }
                }
            } else for(int i=0;i<n;i+=5)
                out.add(new Step(i,Math.min(n,i+5),"Блок "+(out.size()+1)));
        } else { // whole chapters, grouped by the chapter number where available
            int start=0;
            for(int i=1;i<=n;i++) {
                if(i==n || chapter(refs.get(i))!=chapter(refs.get(i-1))) {
                    String label=chapter(refs.get(start))>=0?"Глава "+chapter(refs.get(start)):"Весь отрывок";
                    out.add(new Step(start,i,label));start=i;
                }
            }
            if(out.size()>1)out.add(new Step(0,n,"Весь отрывок"));
        }
        return out;
    }
    private static String sectionTitle(String ref) {
        int[] cv=parse(ref);
        if(cv==null)return "Раздел";
        if(cv[0]==5) {
            if(cv[1]<13)return "Заповеди блаженства";
            if(cv[1]<17)return "Соль земли и свет мира";
            if(cv[1]<21)return "Закон и пророки";
            if(cv[1]<27)return "О гневе";
            if(cv[1]<31)return "О прелюбодеянии";
            if(cv[1]<33)return "О разводе";
            if(cv[1]<38)return "О клятвах";
            if(cv[1]<43)return "О возмездии";
            return "Любовь к врагам";
        }
        if(cv[0]==6) {
            if(cv[1]<5)return "О милостыне";
            if(cv[1]<16)return "О молитве";
            if(cv[1]<19)return "О посте";
            if(cv[1]<25)return "Сокровища на небесах";
            return "Не заботьтесь о завтрашнем дне";
        }
        if(cv[0]==7) {
            if(cv[1]<6)return "Не судите";
            if(cv[1]<7)return "О святыне";
            if(cv[1]<13)return "Просите — и дано будет";
            if(cv[1]<15)return "Узкие врата";
            if(cv[1]<21)return "По плодам узнаете";
            if(cv[1]<24)return "Исполнение воли Отца";
            if(cv[1]<28)return "Дом на камне";
            return "Завершение проповеди";
        }
        return "Раздел";
    }
    private static int[] parse(String ref) {
        Matcher m=REF.matcher(ref==null?"":ref.trim());
        if(!m.matches())return null;
        try{return new int[]{Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2))};}
        catch(NumberFormatException ex){return null;}
    }
    private static int chapter(String s){int[] p=parse(s);return p==null?-1:p[0];}
    private static boolean isSectionStart(String current,String prev) {
        int[] c=parse(current),p=parse(prev);
        if(c==null||p==null)return false;
        if(c[0]!=p[0])return true;
        int[] starts=MATT_SECTIONS.get(c[0]);
        if(starts==null)return false;
        for(int s:starts)if(c[1]==s)return true;
        return false;
    }
}
