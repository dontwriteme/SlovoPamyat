package org.biblememory.app;

import java.util.*;

/** Validation of user-entered study lines using the same lexical contract as lessons. */
final class CustomStudyText {
    private CustomStudyText() {}

    static List<Bible.Verse> parse(String raw,List<Bible.Verse> previous){
        List<String> texts=new ArrayList<>();
        for(String line:raw.split("\\R",-1)){
            String text=line.trim();
            if(!text.isEmpty())texts.add(text);
        }
        if(texts.isEmpty())throw new IllegalArgumentException("Введите текст для изучения");
        if(texts.size()>500)throw new IllegalArgumentException("Максимум 500 строк");
        for(int i=0;i<texts.size();i++)
            if(ExerciseTextLogic.words(texts.get(i)).isEmpty())
                throw new IllegalArgumentException(message(i));
        List<Bible.Verse> result=new ArrayList<>();
        if(previous==null){
            for(int i=0;i<texts.size();i++)
                result.add(new Bible.Verse(String.valueOf(i+1),texts.get(i)));
            return result;
        }

        // An in-place edit keeps the original coordinates, including adjacency.
        // reviseStudyPassage retains progress only when both ref and text match.
        if(texts.size()==previous.size()){
            for(int i=0;i<texts.size();i++)
                result.add(new Bible.Verse(previous.get(i).ref,texts.get(i)));
            return result;
        }

        // On insertions/removals preserve unambiguous unchanged lines.
        // Never infer a moved duplicate's identity.
        Map<String,Integer> oldCounts=new HashMap<>(),newCounts=new HashMap<>();
        Map<String,Bible.Verse> oldByText=new HashMap<>();
        Set<String> reserved=new HashSet<>(),used=new HashSet<>();
        for(Bible.Verse verse:previous){
            reserved.add(verse.ref);
            oldCounts.merge(verse.text,1,Integer::sum);
            oldByText.put(verse.text,verse);
        }
        for(String text:texts)newCounts.merge(text,1,Integer::sum);
        // Preserve numerical adjacency when appending to a numbered custom passage.
        boolean numbered=true;
        int maxNumber=0;
        for(Bible.Verse verse:previous){
            try{
                int number=Integer.parseInt(verse.ref);
                if(number<1||!Integer.toString(number).equals(verse.ref))numbered=false;
                else maxNumber=Math.max(maxNumber,number);
            }catch(NumberFormatException ignored){numbered=false;}
        }
        long nextNumber=(long)maxNumber+1;
        int next=1;
        for(int i=0;i<texts.size();i++){
            String text=texts.get(i);
            Bible.Verse match=null;
            if(oldCounts.getOrDefault(text,0)==1&&newCounts.get(text)==1)
                match=oldByText.get(text);
            String ref;
            if(match!=null&&used.add(match.ref))ref=match.ref;
            else{
                do{
                    ref=numbered&&nextNumber<=Integer.MAX_VALUE?
                        Long.toString(nextNumber++):"свой:"+(next++);
                }while(reserved.contains(ref)||!used.add(ref));
            }
            result.add(new Bible.Verse(ref,text));
        }
        return result;
    }

    static int firstUnlearnable(List<Bible.Verse> verses,int start,int end){
        for(int i=start;i<end;i++)
            if(ExerciseTextLogic.words(verses.get(i).text).isEmpty())return i;
        return -1;
    }

    static String message(int index){
        return "Строка "+(index+1)+" должна содержать хотя бы одно слово или число";
    }
}
