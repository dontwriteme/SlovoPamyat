package org.biblememory.app;

import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class WritingTimingProbe {
    @Test public void compare()throws Exception{
        Bible bible=new Bible();bible.load(RuntimeEnvironment.getApplication());
        measure("short",bible.passage(1,1,1,1,1),0);
        measure("esther",bible.passage(17,8,9,8,9),0);
        measure("sermon",bible.passage(40,5,1,7,29),0);
        measure("paste",bible.passage(40,5,1,7,29),6000);
    }

    private void measure(String label,List<Bible.Verse> verses,int extra){
        List<String> lines=new ArrayList<>();
        for(Bible.Verse verse:verses)lines.add(verse.text);
        String expected=String.join(" ",lines);
        String actual=expected.replaceFirst("\\p{L}+", "подмена")+" слово".repeat(extra);
        long[] times=new long[12];
        int signature=0;
        for(int run=-4;run<times.length;run++){
            long start=System.nanoTime();
            signature=check(expected,actual,lines,label.equals("sermon")||label.equals("paste"));
            if(run>=0)times[run]=System.nanoTime()-start;
        }
        Arrays.sort(times);
        System.out.printf(Locale.ROOT,"WRITE %s n=12 median=%.3f refs=%d words=%d signature=%d%n",
            label,(times[5]+times[6])/2e6,lines.size(),ReviewLogic.words(actual).size(),signature);
    }

    private int check(String expected,String actual,List<String> lines,boolean special){
        try{
            Class<?> assessment=Class.forName("org.biblememory.app.WritingAssessment");
            Class<?> input=Class.forName(assessment.getName()+"$Input");
            Object request=input.getDeclaredConstructors()[0].newInstance(expected,actual,special,
                true,false,lines,Collections.emptyList(),Collections.emptyList());
            Object result=assessment.getDeclaredMethod("evaluate",input).invoke(null,request);
            Class<?> type=result.getClass();
            return signature((boolean)type.getDeclaredMethod("exact").invoke(result),
                (int)type.getDeclaredMethod("accuracy").invoke(result),
                (String)type.getDeclaredMethod("closest").invoke(result),
                (WeakTransitionLogic.WeakSpot)type.getDeclaredMethod("weak").invoke(result),
                (AnswerReviewLogic.Result)type.getDeclaredMethod("feedback").invoke(result));
        }catch(ClassNotFoundException baseline){
            return legacy(expected,actual,lines,special);
        }catch(ReflectiveOperationException error){
            throw new AssertionError(error);
        }
    }

    private int legacy(String expected,String actual,List<String> lines,boolean special){
        boolean exact=AcceptedTextLogic.exact(expected,actual,special);
        String closest=!exact&&special?AcceptedTextLogic.bestExpected(expected,actual,true):expected;
        int accuracy=exact?100:Math.min(99,ReviewLogic.accuracy(closest,actual));
        WeakTransitionLogic.WeakSpot weak=!exact&&lines.size()>1?WeakTransitionLogic.locate(lines,actual):null;
        AnswerReviewLogic.Result feedback=exact?null:AnswerReviewLogic.compare(expected,actual,special);
        return signature(exact,accuracy,closest,weak,feedback);
    }

    private int signature(boolean exact,int accuracy,String closest,WeakTransitionLogic.WeakSpot weak,
            AnswerReviewLogic.Result feedback){
        return Objects.hash(exact,accuracy,closest,weak==null?null:Arrays.asList(weak.verseIndex,weak.transitionFrom,weak.errors),
            feedback==null?null:Arrays.asList(feedback.summary.name(),feedback.displayedActual,feedback.actualMarks.size()));
    }
}
