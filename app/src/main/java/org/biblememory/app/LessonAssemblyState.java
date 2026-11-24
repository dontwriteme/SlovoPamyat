package org.biblememory.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Mutable, view-independent selection state for the current assembly fragment.
 * Main-thread confined by its caller; geometry and seed are supplied by the lesson.
 */
final class LessonAssemblyState {
    enum Tap { IGNORED, CORRECT, WRONG }

    private int picked;
    private int wrongTile=-1;
    private final List<Integer> usedTiles=new ArrayList<>();
    private final List<Integer> readOnlyTiles=Collections.unmodifiableList(usedTiles);

    int picked(){return picked;}
    int wrongTile(){return wrongTile;}
    boolean used(int index){return usedTiles.contains(index);}
    List<Integer> usedTiles(){return readOnlyTiles;}

    void reset(){
        picked=0;wrongTile=-1;usedTiles.clear();
    }

    void restore(int count,List<Integer> selected){
        // The caller validates the saved prefix against the actual exercise geometry.
        reset();picked=count;usedTiles.addAll(selected);
    }

    void validatePrefix(String[] tokens,int[] missing){
        if(!SessionIntegrityLogic.validAssemblyPrefix(tokens,missing,picked,usedTiles))reset();
    }

    Tap tap(String[] tokens,int[] missing,int pressed){
        if(pressed<0||pressed>=missing.length||used(pressed)||picked>=missing.length)
            return Tap.IGNORED;
        int wanted=missing[picked],target=missing[pressed];
        if(WordOrderLogic.comparable(tokens[wanted]).equals(WordOrderLogic.comparable(tokens[target]))){
            picked++;usedTiles.add(pressed);wrongTile=-1;
            return Tap.CORRECT;
        }
        wrongTile=pressed;
        return Tap.WRONG;
    }
}
