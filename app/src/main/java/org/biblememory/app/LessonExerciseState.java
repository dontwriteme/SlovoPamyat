package org.biblememory.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable checkpoint of the current exercise, with no dependency on Android views. */
record LessonExerciseState(String draft,int blurSeed,int variant,int chunk,int picked,long seed,
                           String assemblyFingerprint,String rotationExerciseKey,
                           long rotationExerciseSeed,String rotationMask,List<Integer> usedTiles,
                           int writeVariant,boolean groupIntroDone,boolean groupBuildDone,
                           boolean practiceWeak,String weakFragment) {
    LessonExerciseState {
        usedTiles=Collections.unmodifiableList(new ArrayList<>(usedTiles));
    }

    static String resumableDraft(String draft,boolean checkedWithErrors){
        return checkedWithErrors||draft==null?"":draft;
    }

    static List<Integer> decodeTiles(String saved){
        List<Integer> result=new ArrayList<>();
        for(String part:saved.replace("[","").replace("]","").split(","))
            try{result.add(Integer.parseInt(part.trim()));}
            catch(NumberFormatException ignored){}
        return result;
    }

    String encodedTiles(){return usedTiles.toString();}
}
