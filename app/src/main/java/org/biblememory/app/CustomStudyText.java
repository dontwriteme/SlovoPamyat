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

        // First reserve the identities of all unambiguous unchanged lines,
        // even if the edit keeps the same number of lines but moves them.
        Map<String,Integer> oldCounts=new HashMap<>(),newCounts=new HashMap<>();
        Map<String,Integer> oldIndex=new HashMap<>();
        Set<String> reserved=new HashSet<>(),used=new HashSet<>();
        for(int i=0;i<previous.size();i++){
            Bible.Verse verse=previous.get(i);
            reserved.add(verse.ref);
            oldCounts.merge(verse.text,1,Integer::sum);
            oldIndex.put(verse.text,i);
        }
        for(String text:texts)newCounts.merge(text,1,Integer::sum);
        String[] refs=new String[texts.size()];
        boolean moved=false;
        for(int i=0;i<texts.size();i++){
            String text=texts.get(i);
            if(oldCounts.getOrDefault(text,0)==1&&newCounts.get(text)==1){
                int from=oldIndex.get(text);
                if(used.add(previous.get(from).ref))refs[i]=previous.get(from).ref;
                if(from!=i)moved=true;
            }
        }

        // In a positional correction retain the coordinates of unchanged
        // duplicates at their own positions and of genuinely new text.
        // Never reuse a moved/ambiguous duplicate's identity by position.
        if(texts.size()==previous.size()&&!moved){
            for(int i=0;i<texts.size();i++){
                if(refs[i]!=null)continue;
                Bible.Verse old=previous.get(i);
                String text=texts.get(i);
                boolean same=old.text.equals(text)&&
                    oldCounts.getOrDefault(text,0).equals(newCounts.get(text));
                if((same||!oldCounts.containsKey(text))&&used.add(old.ref))refs[i]=old.ref;
            }
        }

        // Fresh identities cannot collide with any old identity. Preserve
        // numerical adjacency when appending to a numbered custom passage.
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
            String ref=refs[i];
            if(ref==null){
                do{
                    ref=numbered&&nextNumber<=Integer.MAX_VALUE?
                        Long.toString(nextNumber++):"свой:"+(next++);
                }while(reserved.contains(ref)||!used.add(ref));
            }
            result.add(new Bible.Verse(ref,texts.get(i)));
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
