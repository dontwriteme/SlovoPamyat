package org.biblememory.app;

/** One evaluated written answer becomes a decision, then an optional durable write.
 * Independent of Android views, Activity lifecycle, and the SQLite implementation.
 */
final class LessonWritingFlow {
    private LessonWritingFlow() {}

    enum Outcome {
        FEEDBACK, FRAGMENT_PRACTICED, NEXT_VARIANT,
        FAILED_RECALL, ASSISTED_CORRECTION, SUCCESSFUL_RECALL
    }

    record Context(long entryId, StudyPlanLogic.Stage stage, int start, int end,
                   boolean review, boolean group, boolean verseRun,
                   boolean practiceFragment, boolean hadFeedback) {}

    interface RecallWriter {
        void commitFailedRecall(long id,String stage,int start,int end,int accuracy,
                                boolean schedule,WeakTransitionLogic.WeakSpot weak,long now);
        void recordRecall(long id,String stage,int start,int end,int accuracy,
                          boolean assisted,long now);
        void commitSuccessfulRecall(long id,String stage,int start,int end,
                                    boolean verseRun,boolean finishLesson,long now);
    }

    record Transition(Outcome outcome,Context context,WritingAssessment.Result assessment) {
        /** No state mutation or presentation may precede a required successful write. */
        void persist(RecallWriter writer,long now) {
            Context c=context;
            String stage=c.stage().name();
            switch(outcome){
                case FAILED_RECALL:
                    writer.commitFailedRecall(c.entryId(),c.verseRun()?"VERSE_RUN":stage,
                        c.start(),c.end(),assessment.accuracy(),
                        c.review()||c.group()||c.stage()!=StudyPlanLogic.Stage.VERSE,
                        assessment.weak(),now);
                    break;
                case ASSISTED_CORRECTION:
                    writer.recordRecall(c.entryId(),stage,c.start(),c.end(),100,true,now);
                    break;
                case SUCCESSFUL_RECALL:
                    writer.commitSuccessfulRecall(c.entryId(),stage,c.start(),c.end(),c.verseRun(),
                        !c.review()&&!c.group()&&c.stage()==StudyPlanLogic.Stage.VERSE,now);
                    break;
                default:
                    // Empty answers, gaps and weak-fragment practice never record recall.
                    break;
            }
        }

        int weakVerse() {
            WeakTransitionLogic.WeakSpot weak=assessment.weak();
            return weak==null?-1:context.start()+weak.verseIndex;
        }

        int weakTransitionFrom() {
            WeakTransitionLogic.WeakSpot weak=assessment.weak();
            return weak==null||weak.transitionFrom<0?-1:context.start()+weak.transitionFrom;
        }
    }

    static Transition decide(Context context,WritingAssessment.Input input,WritingAssessment.Result assessment) {
        Outcome outcome;
        if(input.actual().trim().isEmpty())outcome=Outcome.FEEDBACK;
        else if(context.practiceFragment())
            outcome=assessment.exact()?Outcome.FRAGMENT_PRACTICED:Outcome.FEEDBACK;
        else if(!input.full())
            outcome=assessment.exact()?Outcome.NEXT_VARIANT:Outcome.FEEDBACK;
        else if(!assessment.exact())outcome=Outcome.FAILED_RECALL;
        else if(context.hadFeedback())outcome=Outcome.ASSISTED_CORRECTION;
        else outcome=Outcome.SUCCESSFUL_RECALL;
        return new Transition(outcome,context,assessment);
    }
}
