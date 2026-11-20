package org.biblememory.app;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exhaustive corpus contract test against production exercise code, runnable without Android. */
public final class InlineGapAudit {
    static long texts,positions;
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void check(String source){
        String[] tokens=ExerciseTextLogic.tokens(source);
        int[] all=new int[tokens.length];for(int i=0;i<all.length;i++)all[i]=i;
        int[] valid=InlineGapLogic.wordPositions(tokens,all);
        for(int i:GapRotationLogic.balancedWordPositions(tokens,new int[tokens.length],42))
            require(ExerciseTextLogic.isWord(tokens[i]),"Rotation selected punctuation");
        List<String> expected=new ArrayList<>();
        String[] answers=new String[valid.length];
        for(int k=0;k<valid.length;k++){
            String token=tokens[valid[k]],core=InlineGapLogic.core(token);
            require(!ReviewLogic.words(core).isEmpty(),"Empty target: "+source);
            require(token.equals(InlineGapLogic.prefix(token)+core+InlineGapLogic.suffix(token)),"Punctuation lost");
            require(InlineGapLogic.complete(Collections.singletonList(core),new String[]{core},false),"Correct answer rejected");
            require(!InlineGapLogic.complete(Collections.singletonList(core),new String[]{""},false),"Empty field accepted");
            require(!InlineGapLogic.complete(Collections.singletonList(core),new String[]{"—"},false),"Punctuation answer accepted");
            answers[k]=core;expected.add(core);positions++;
        }
        require(valid.length==0 || InlineGapLogic.complete(expected,answers,false),"Full set rejected");
        for(LessonCurriculumLogic.Variant variant:LessonCurriculumLogic.Variant.values()){
            int[] raw=LessonCurriculumLogic.missingPositions(tokens.length,variant,42);
            int[] mask=InlineGapLogic.wordPositions(tokens,raw);
            for(int i:mask)require(ExerciseTextLogic.isWord(tokens[i]),"Nonword mask");
            String[] legacy=new String[raw.length];for(int i=0;i<raw.length;i++)legacy[i]="answer"+raw[i];
            String[] restored=InlineGapLogic.restore(InlineGapLogic.serialize(legacy),tokens,raw);
            require(restored.length==mask.length,"Draft field count");
            for(int i=0;i<mask.length;i++)require(restored[i].equals("answer"+mask[i]),"Draft shifted");
        }
        String masked=ReviewLogic.masked(source,100,42);
        require(source.replaceAll("[\\p{L}\\p{N}]","").equals(masked.replace("▰","")),"Hidden punctuation");
        List<String> tiles=new ArrayList<>();
        for(String[] chunk:WordOrderLogic.chunks(Collections.singletonList(source)))for(String tile:chunk){
            require(ExerciseTextLogic.isWord(tile),"Punctuation tile");tiles.add(tile);
        }
        if(valid.length>0)require(source.replaceAll("\\s+","").equals(String.join("",tiles).replaceAll("\\s+","")),"Assembly text lost");
        require(valid.length==0 || AcceptedTextLogic.exact(source,String.join(" ",ReviewLogic.words(source)),false),"Punctuation affects grading");
        texts++;
    }
    public static void main(String[] args)throws Exception{
        String[] cases={"слово — слово", "— один - два – три —", "кто-нибудь по-прежнему", "«(Слово)» [другое]", "... — ; : !", "Первый\n\nстих.\tВторой стих.", "а", "Мой текст: 123 — «ответ».", "начало … середина … конец"};
        for(String s:cases)check(s);
        check(String.join("\n",Collections.nCopies(100,cases[1])));
        require(AcceptedLogic(),"Matthew variant");
        require(!InlineGapLogic.complete(Arrays.asList("один","два"),new String[]{"один два",""},false),"Compensated blank accepted");
        for(String line:Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8))
            check(new String(Base64.getDecoder().decode(line),StandardCharsets.UTF_8));
        System.out.println("PASS: texts="+texts+", eligible positions="+positions);
    }
    static boolean AcceptedLogic(){return AcceptedTextLogic.exact("негодна","не годна",true)
        &&InlineGapLogic.complete(Collections.singletonList("негодна"),new String[]{"не годна"},true)
        &&!AcceptedTextLogic.exact("негодна","не годна",false);}
}
