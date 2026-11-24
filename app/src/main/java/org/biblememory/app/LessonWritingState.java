package org.biblememory.app;

import java.util.Arrays;
import java.util.List;

/** Main-thread lesson writing state, independent of views and persistence. */
final class LessonWritingState {
    String draft="";
    int variant;
    boolean hadFeedback;
    boolean practiceWeakFragment;
    String weakFragmentText="";
    private String[] gaps=new String[0];

    String resumableDraft(boolean reviewVisible){
        return LessonExerciseState.resumableDraft(draft,reviewVisible||hadFeedback);
    }

    void restoreGaps(String[] tokens,int[] positions){
        gaps=InlineGapLogic.restore(draft,tokens,positions);
    }

    String gap(int index){return gaps[index];}

    void editGap(int index,String value){
        gaps[index]=value;
        draft=InlineGapLogic.serialize(gaps);
    }

    String gapResponse(){return InlineGapLogic.response(gaps);}
    List<String> gapAnswers(){return Arrays.asList(gaps);}

    void clearGaps(){gaps=new String[0];}

    void retry(){
        draft="";clearGaps();hadFeedback=false;
    }

    void nextVariant(){
        variant++;retry();
    }

    void finishFragment(int fullVariant){
        practiceWeakFragment=false;weakFragmentText="";
        retry();variant=fullVariant;
    }

    void restore(LessonExerciseState checkpoint){
        draft=checkpoint.draft();variant=checkpoint.writeVariant();hadFeedback=false;
        practiceWeakFragment=checkpoint.practiceWeak();weakFragmentText=checkpoint.weakFragment();
    }
}
