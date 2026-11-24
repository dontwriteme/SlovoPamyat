package org.biblememory.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class LessonExercisePreferencesTest {
    private SharedPreferences prefs(){
        SharedPreferences p=RuntimeEnvironment.getApplication().getSharedPreferences("exercise_codec_fixture",Context.MODE_PRIVATE);
        p.edit().clear().commit();return p;
    }

    @Test public void roundTripKeepsExistingKeysAndSingleCallerCommit(){
        SharedPreferences p=prefs();
        String key="linked_42_";
        LessonExerciseState state=new LessonExerciseState("ответ",17,1,2,2,99L,
            "geometry","rotation",99L,"0,2",Arrays.asList(2,0),1,true,false,true,"фрагмент");
        SharedPreferences.Editor editor=p.edit().putLong("entry",42L);
        assertSame(editor,LessonExercisePreferences.write(editor,key,state));
        assertFalse(p.contains(key+"draft"));
        editor.commit();
        assertEquals(42L,p.getLong("entry",0));
        assertEquals("ответ",p.getString(key+"draft",""));
        assertEquals("[2, 0]",p.getString(key+"lesson_tiles",""));
        assertTrue(p.contains(key+"lesson_had_feedback"));
        assertFalse(p.getBoolean(key+"lesson_had_feedback",true));
        assertEquals(state,LessonExercisePreferences.read(p,key,true,false,false));
        Set<String> expected=new HashSet<>(Arrays.asList("entry"));
        for(String field:new String[]{"draft","blur_seed","lesson_variant","lesson_chunk","lesson_picked",
            "lesson_seed","assembly_fingerprint","rotation_exercise_key","rotation_exercise_seed",
            "rotation_mask","lesson_tiles","lesson_had_feedback","write_variant","group_intro_done",
            "group_build_done","practice_weak","weak_fragment"})expected.add(key+field);
        assertEquals(expected,p.getAll().keySet());
    }

    @Test public void legacyDefaultsClampIndicesAndKeepUnlinkedGroupFlags(){
        SharedPreferences p=prefs();String key="entry_42_";
        p.edit().putString(key+"draft","видимый ответ").putBoolean(key+"lesson_had_feedback",true)
            .putInt(key+"lesson_variant",-4).putInt(key+"lesson_chunk",-1)
            .putInt(key+"lesson_picked",-2).putInt(key+"write_variant",-1)
            .putString(key+"lesson_tiles","[1, broken, 0]")
            .putBoolean(key+"group_intro_done",false).putBoolean(key+"group_build_done",true)
            .commit();
        LessonExerciseState state=LessonExercisePreferences.read(p,key,false,true,false);
        assertEquals("",state.draft());
        assertEquals(0,state.variant());assertEquals(0,state.chunk());
        assertEquals(0,state.picked());assertEquals(0,state.writeVariant());
        assertEquals(Arrays.asList(1,0),state.usedTiles());
        assertTrue(state.groupIntroDone());assertFalse(state.groupBuildDone());
        assertFalse(state.practiceWeak());assertEquals("",state.weakFragment());
        LessonExerciseState linked=LessonExercisePreferences.read(p,key,true,true,false);
        assertFalse(linked.groupIntroDone());assertTrue(linked.groupBuildDone());
    }
}
