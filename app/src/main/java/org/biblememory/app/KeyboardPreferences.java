package org.biblememory.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;

/** One device-local keyboard policy for every text editor, including the DEV lab. */
final class KeyboardPreferences {
    private static final String PREFS="sv_keyboard_settings";
    private static final String AUTOCORRECT="autocorrect_enabled";
    private KeyboardPreferences(){}

    private static SharedPreferences prefs(Context context){
        Context app=context.getApplicationContext();
        return (app==null?context:app).getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static boolean enabled(Context context){return prefs(context).getBoolean(AUTOCORRECT,false);}
    static void setEnabled(Context context,boolean enabled){
        prefs(context).edit().putBoolean(AUTOCORRECT,enabled).apply();
    }
    static int inputType(int base,boolean enabled){
        if((base&InputType.TYPE_MASK_CLASS)!=InputType.TYPE_CLASS_TEXT)return base;
        int type=base&~(InputType.TYPE_TEXT_FLAG_AUTO_CORRECT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|
            InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        if(enabled)return type|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
        // Some keyboards ignore NO_SUGGESTIONS for ordinary text. A visible-password
        // variation additionally requests literal, non-predictive input without masking.
        return (type&~InputType.TYPE_MASK_VARIATION)|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    }
    static void apply(EditText editor,int base){
        boolean enabled=enabled(editor.getContext());
        int type=inputType(base,enabled);
        if(!enabled&&(base&InputType.TYPE_MASK_CLASS)==InputType.TYPE_CLASS_TEXT){
            // Configure normal text layout first; raw input type avoids password
            // typography changes and preserves multiline editing and visible text.
            editor.setInputType(base|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editor.setRawInputType(type);
            editor.setImeOptions(editor.getImeOptions()|
                android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            editor.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        }else editor.setInputType(type);
    }
}
