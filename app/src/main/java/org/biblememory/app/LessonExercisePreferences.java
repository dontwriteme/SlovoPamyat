package org.biblememory.app;

import android.content.SharedPreferences;
import java.util.Random;

/** Adapter for the existing exercise checkpoint keys. Route and lifecycle checks stay outside. */
final class LessonExercisePreferences {
    private LessonExercisePreferences() {}

    static SharedPreferences.Editor write(SharedPreferences.Editor editor,String key,LessonExerciseState s){
        return editor.putString(key+"draft",s.draft())
            .putInt(key+"blur_seed",s.blurSeed())
            .putInt(key+"lesson_variant",s.variant()).putInt(key+"lesson_chunk",s.chunk())
            .putInt(key+"lesson_picked",s.picked()).putLong(key+"lesson_seed",s.seed())
            .putString(key+"assembly_fingerprint",s.assemblyFingerprint())
            .putString(key+"rotation_exercise_key",s.rotationExerciseKey())
            .putLong(key+"rotation_exercise_seed",s.rotationExerciseSeed())
            .putString(key+"rotation_mask",s.rotationMask())
            .putString(key+"lesson_tiles",s.encodedTiles())
            .putBoolean(key+"lesson_had_feedback",false)
            .putInt(key+"write_variant",s.writeVariant())
            .putBoolean(key+"group_intro_done",s.groupIntroDone())
            .putBoolean(key+"group_build_done",s.groupBuildDone())
            .putBoolean(key+"practice_weak",s.practiceWeak())
            .putString(key+"weak_fragment",s.weakFragment());
    }

    static LessonExerciseState read(SharedPreferences pref,String key,boolean linked,
                                    boolean groupIntroDone,boolean groupBuildDone){
        int blurSeed=pref.getInt(key+"blur_seed",new Random().nextInt());
        String draft=LessonExerciseState.resumableDraft(pref.getString(key+"draft",""),
            pref.getBoolean(key+"lesson_had_feedback",false));
        int variant=Math.max(0,pref.getInt(key+"lesson_variant",0));
        int chunk=Math.max(0,pref.getInt(key+"lesson_chunk",0));
        int picked=Math.max(0,pref.getInt(key+"lesson_picked",0));
        String fingerprint=pref.getString(key+"assembly_fingerprint","");
        long seed=pref.getLong(key+"lesson_seed",new Random().nextLong());
        String rotationKey=pref.getString(key+"rotation_exercise_key","");
        long rotationSeed=pref.getLong(key+"rotation_exercise_seed",0L);
        String rotationMask=pref.getString(key+"rotation_mask","");
        int writeVariant=Math.max(0,pref.getInt(key+"write_variant",0));
        boolean intro=linked?pref.getBoolean(key+"group_intro_done",false):groupIntroDone;
        boolean built=linked?pref.getBoolean(key+"group_build_done",false):groupBuildDone;
        boolean practice=pref.getBoolean(key+"practice_weak",false);
        String weak=pref.getString(key+"weak_fragment","");
        return new LessonExerciseState(draft,blurSeed,variant,chunk,picked,seed,fingerprint,
            rotationKey,rotationSeed,rotationMask,
            LessonExerciseState.decodeTiles(pref.getString(key+"lesson_tiles","")),
            writeVariant,intro,built,practice,weak);
    }
}
