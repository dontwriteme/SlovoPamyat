package org.biblememory.app;

/** Selects the next lesson destination without reading storage or manipulating views.
 * The caller restores the verse cursor before requesting a decision after a group.
 */
final class LessonNavigationFlow {
    private LessonNavigationFlow() {}

    enum Destination {
        REVIEW, GROUP, VERSE, COLLECTION_AFTER_GROUPS, CARD, GROUPS_OR_EXIT, EXIT
    }

    record Decision(Destination destination,int verse,GroupLearningLogic.Candidate group) {}

    static Decision afterSuccess(boolean review,boolean returnedFromGroup,
            GroupLearningLogic.Candidate pending,int nextVerse,int lineCount,
            boolean collectionActive,boolean cardActive){
        if(review)return new Decision(Destination.REVIEW,-1,null);
        if(pending!=null)return new Decision(Destination.GROUP,-1,pending);
        if(nextVerse<lineCount)return new Decision(Destination.VERSE,nextVerse,null);
        if(collectionActive)return new Decision(Destination.COLLECTION_AFTER_GROUPS,-1,null);
        if(cardActive)return new Decision(Destination.CARD,-1,null);
        return new Decision(returnedFromGroup?Destination.GROUPS_OR_EXIT:Destination.EXIT,-1,null);
    }
}
