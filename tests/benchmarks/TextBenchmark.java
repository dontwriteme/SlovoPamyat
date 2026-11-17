import org.biblememory.app.*;
import java.util.*;
/** Run with the same JVM/heap for both revisions; no source text in output. */
public class TextBenchmark {
 public static void main(String[] args){
  String expected=String.join(" ",Collections.nCopies(6000,"Слово истины"));
  for(String actual:Arrays.asList(expected,expected.substring(0,expected.length()/2)+" ошибка "+expected.substring(expected.length()/2))){
   for(int trial=0;trial<6;trial++){
    long start=System.nanoTime();List<TypingDiffLogic.Piece> pieces=TypingDiffLogic.compare(expected,actual);
    long elapsed=System.nanoTime()-start;
    int checksum=1;for(TypingDiffLogic.Piece p:pieces)checksum=31*checksum+Objects.hash(p.kind.ordinal(),p.text);
    System.out.println("DIFF exact="+actual.equals(expected)+" trial="+trial+" ns="+elapsed+" pieces="+pieces.size()+" checksum="+checksum);
   }
  }
 }
}
