package org.biblememory.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Executable full Synodal corpus check, no Android/JUnit dependency. */
public final class OrthographyCorpusAudit {
    private static long cases,mutations;
    private static void check(boolean okay,String message){
        if(!okay)throw new AssertionError(message);
    }
    private static void audit(String expected){
        List<String> tokens=ReviewLogic.words(expected);
        if(tokens.isEmpty())return; // punctuation-only is not a valid memorization answer
        String typed=String.join(" ",tokens);
        check(AcceptedTextLogic.exact(expected,expected,false),"Self answer rejected: "+expected);
        check(AcceptedTextLogic.exact(expected,typed,false),"Normalized answer rejected: "+expected);
        check(ReviewLogic.accuracy(expected,typed)==100,"Normalized answer not 100%: "+expected);
        AnswerReviewLogic.Result result=AnswerReviewLogic.compare(expected,typed,false);
        check(result.issues.isEmpty() && result.actualMarks.isEmpty(),
            "Correct answer was visually flagged: "+expected);
        // An actual lexical substitution must never be accepted as correctly memorized.
        ArrayList<String> altered=new ArrayList<>(tokens);
        int middle=altered.size()/2;
        altered.set(middle,altered.get(middle)+"ошибка");
        check(!AcceptedTextLogic.exact(expected,String.join(" ",altered),false),
            "Mutation incorrectly accepted: "+expected);
        mutations++;
        cases++;
    }
    private static void equivalent(String expected,String actual,boolean special){
        check(AcceptedTextLogic.exact(expected,actual,special),"Rejected accepted variant: "+actual);
        String preferred=AcceptedTextLogic.bestExpected(expected,actual,special);
        check(ReviewLogic.accuracy(preferred,actual)==100,"Accuracy should be 100: "+actual);
        AnswerReviewLogic.Result result=AnswerReviewLogic.compare(expected,actual,special);
        check(result.issues.isEmpty() && result.actualMarks.isEmpty(),
            "False review error for correct answer: "+actual);
        for(TypingDiffLogic.Piece piece:TypingDiffLogic.compare(preferred,actual))
            check(piece.kind==TypingDiffLogic.Kind.CORRECT,
                "False inline mark: "+piece.text+" in "+actual);
    }
    private static void edgeCases(){
        equivalent("В начале сотворил Бог небо и землю.",
            "«В НАЧАЛЕ, сотворил Бог — небо и землю!»",false);
        equivalent("Твоё слово, свет","ТВОЕ СЛОВО СВЕТ",false);
        equivalent("Слово Господа","Сло\u0301во Госпо\u0301да",false);
        equivalent("Сло\u0301во Господа","слово ГОСПОДА",false);
        equivalent("123 слова","１２３ слова",false);
        equivalent("кто-нибудь по-прежнему","кто нибудь по прежнему",false);
        equivalent("Она уже ни к чему негодна","она уже ни к чему не годна",true);
        equivalent("Она уже ни к чему не годна","она уже ни к чему негодна",true);
        check(!AcceptedTextLogic.exact("Она ни к чему негодна",
            "Она ни к чему не годна",false),"Context-free Matthew exception");
        check(!AcceptedTextLogic.exact("небо и земля","земля и небо",false),"Wrong order accepted");
        check(!AcceptedTextLogic.exact("небо и земля","небо и земля земля",false),"Extra word accepted");
        check(!AcceptedTextLogic.exact("небо и земля","небо и земла",false),"Spelling error accepted");
        check(InlineGapLogic.complete(Arrays.asList("небо","земля"),
            new String[]{"НЕБО","земля"},false),"Correct blanks rejected");
        check(!InlineGapLogic.complete(Arrays.asList("небо","земля"),
            new String[]{"небо земля",""},false),"Compensating blank accepted");
        check(InlineGapLogic.complete(Collections.singletonList("негодна"),
            new String[]{"не годна"},true),"Matthew blank rejected");
        check(!InlineGapLogic.complete(Collections.singletonList("негодна"),
            new String[]{"не годна"},false),"Matthew exception leaked");
        check(!AcceptedTextLogic.exact("—","—",false),"Punctuation-only credit");
        Random random=new Random(73199);
        String[] original="В начале сотворил Бог небо и землю".split(" ");
        for(int caseNumber=0;caseNumber<700;caseNumber++){
            List<String> edited=new ArrayList<>(Arrays.asList(original));
            for(int k=0;k<1+random.nextInt(3);k++){
                int at=random.nextInt(edited.size()+1);
                switch(random.nextInt(3)){
                    case 0: if(at<edited.size())edited.remove(at);break;
                    case 1: edited.add(at,"лишнее");break;
                    default: if(at<edited.size())edited.set(at,"ошибко");break;
                }
            }
            AnswerReviewLogic.Result diff=AnswerReviewLogic.compare(
                "В начале сотворил Бог небо и землю",String.join(" ",edited),false);
            for(AnswerReviewLogic.Mark mark:diff.actualMarks)
                check(mark.start>=0 && mark.start<=mark.end &&
                    mark.end<=diff.displayedActual.length(),"Invalid UI span");
        }
        System.out.println("PASS: Unicode, Matthew 5:13, blanks, real typos, 700 feedback fuzz cases");
    }
    public static void main(String[] args)throws Exception{
        check(args.length==1,"Usage: OrthographyCorpusAudit <base64 corpus path>");
        for(String encoded:Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8)){
            if(!encoded.isEmpty())
                audit(new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8));
        }
        check(AcceptedTextLogic.matthew513Verse("Матфея 5:13"),"Matthew reference");
        check(AcceptedTextLogic.exact("негодна","не годна",true),"Matthew variant");
        check(!AcceptedTextLogic.exact("негодна","не годна",false),"Variant must be contextual");
        check(AcceptedTextLogic.exact("сло\u0301во","слово",false),"Stress accent");
        edgeCases();
        System.out.println("PASS: "+cases+" corpus texts; "+mutations+" rejected lexical mutations");
    }
}
