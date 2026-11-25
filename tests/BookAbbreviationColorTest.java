package org.biblememory.app;

import org.junit.Test;
import java.util.EnumMap;
import java.util.HashSet;
import static org.junit.Assert.*;

public class BookAbbreviationColorTest {
    @Test public void allCanonicalGroupsHaveDistinctReadableAbbreviations(){
        EnumMap<BookColorLogic.Group,Integer> colors=
            new EnumMap<>(BookColorLogic.Group.class);
        for(int book=1;book<=66;book++){
            int ink=BookColorLogic.abbreviationColor(book);
            assertEquals(255,ink>>>24);
            assertTrue(BookColorLogic.shortName(book).length()>0);
            assertTrue("Contrast for "+BookColorLogic.shortName(book),
                (luminance(0xFFFFFAF5)+0.05)/(luminance(ink)+0.05)>=5.0);
            Integer old=colors.put(BookColorLogic.group(book),ink);
            if(old!=null)assertEquals(old.intValue(),ink);
        }
        assertEquals(10,colors.size());
        assertEquals(10,new HashSet<>(colors.values()).size());
    }

    private static double luminance(int color){
        return 0.2126*linear((color>>16)&255)+
            0.7152*linear((color>>8)&255)+0.0722*linear(color&255);
    }

    private static double linear(int channel){
        double s=channel/255.0;
        return s<=0.04045?s/12.92:Math.pow((s+0.055)/1.055,2.4);
    }
}
