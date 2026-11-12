package org.biblememory.app;
import java.util.*;
/** Uniform, never-ending shuffled cycles; no repeats within each cycle. */
public final class FlashcardDeckLogic {
    private FlashcardDeckLogic(){}
    public static List<Long> shuffled(List<Long> ids,long previous,Random random){
        List<Long> result=new ArrayList<>(ids);
        Collections.shuffle(result,random);
        if(result.size()>1 && result.get(0)==previous){
            Long temp=result.get(0);result.set(0,result.get(1));result.set(1,temp);
        }
        return result;
    }
}
