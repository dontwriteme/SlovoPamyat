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
        System.out.println("PASS: "+cases+" corpus texts; "+mutations+" rejected lexical mutations");
    }
}
