package org.biblememory.app;

import android.text.InputType;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardPreferencesTest {
    @Test public void enabledRequestsAutocorrectAndClearsNoSuggestions(){
        int base=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        int result=KeyboardPreferences.inputType(base,true);
        assertEquals(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,result);
    }
    @Test public void disabledRequestsNoSuggestionsAndClearsAutocorrect(){
        int base=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
        assertEquals(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,KeyboardPreferences.inputType(base,false));
    }
    @Test public void multilineAndCapitalizationArePreserved(){
        int base=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        assertEquals(base|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,KeyboardPreferences.inputType(base,true));
        assertEquals(base|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            KeyboardPreferences.inputType(base,false));
    }
    @Test public void disabledRemovesAutocompleteAndReplacesTextVariation(){
        int base=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE|InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE;
        assertEquals(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            KeyboardPreferences.inputType(base,false));
    }
    @Test public void numericInputIsUnaffected(){
        int base=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED;
        assertEquals(base,KeyboardPreferences.inputType(base,true));
        assertEquals(base,KeyboardPreferences.inputType(base,false));
    }
}
