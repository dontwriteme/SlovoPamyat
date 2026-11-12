package org.biblememory.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class BibleSearchQueryTest {
    @Test public void spacesNeverRepresentATextSearch(){
        for(String query:new String[]{null,"","   ","\t\n","\u00a0\u3000"}){
            assertTrue(BibleSearchLogic.isBlankQuery(query));
            assertTrue(BibleSearchLogic.search(null,query).isEmpty());
        }
        assertFalse(BibleSearchLogic.isBlankQuery("бог"));
        assertFalse(BibleSearchLogic.isBlankQuery("!"));
    }
    @Test public void reusableHighlightMatcherPreservesWordMatching(){
        BibleSearchLogic.WordMatcher matcher=new BibleSearchLogic.WordMatcher("Бог любовь");
        for(String word:new String[]{"Бог","бога","любовью","любовию","вера","дом"})
            assertEquals(BibleSearchLogic.allTermsMatch("бог",word)||BibleSearchLogic.allTermsMatch("любовь",word),matcher.matches(word));
        assertFalse(new BibleSearchLogic.WordMatcher("  ").matches("Бог"));
    }
}
