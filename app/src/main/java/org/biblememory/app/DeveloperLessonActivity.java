package org.biblememory.app;

import android.content.SharedPreferences;

/** Declared only in src/debug/AndroidManifest.xml. Never opens the real database. */
public final class DeveloperLessonActivity extends MainActivity {
    @Override public SharedPreferences getSharedPreferences(String name,int mode){
        return super.getSharedPreferences("sv_lesson_lab_"+name,mode);
    }
}
