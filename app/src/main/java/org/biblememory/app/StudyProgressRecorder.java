package org.biblememory.app;

/** Coordinates a whole answer result using the repository's shared SQLite transaction. */
final class StudyProgressRecorder {
    private final ProgressRepository progress;
    private final long sessionGroup;
    StudyProgressRecorder(ProgressRepository progress){this(progress,-1);}
    StudyProgressRecorder(ProgressRepository progress,long group){this.progress=progress;this.sessionGroup=group;}
    void recall(LocalStore.Entry e,StudyPlanLogic.Step step,String stage,int accuracy,
                boolean testAssisted,boolean independent,boolean passed,boolean mastered,
                boolean reviewMode,boolean groupMode,long now){
        progress.commitStudyAttempt(e,step.start,step.end,now,sessionGroup,"VERSE".equals(stage),()->{
            progress.recordRecall(e.id,stage,step.start,step.end,accuracy,
                testAssisted||!independent,now);
            if(independent){
                progress.scheduleReview(e.id,stage,step.start,step.end,accuracy,testAssisted,now);
                if(passed&&!mastered)progress.markMastered(e.id,step.start,step.end,accuracy,stage);
            }
            if(!reviewMode&&!groupMode&&"VERSE".equals(stage)){
                int done=progress.exerciseDone(e.id,step.start);
                if(done==3&&passed)progress.finishExercise(e.id,step.start,4);
                else if(done==4&&passed)progress.finishExercise(e.id,step.start,5);
            }
            int learned=progress.masteryCount(e.id);
            ReviewLogic.Schedule unchanged=new ReviewLogic.Schedule(e.repetitions,e.days,e.ease,e.due);
            // Per-verse and grouped schedules are now authoritative; do not update legacy entry-wide schedule.
            progress.update(e,unchanged,learned,accuracy);
        });
    }
    void correction(LocalStore.Entry e,String stage,StudyPlanLogic.Step step,long now){
        progress.commitStudyAttempt(e,step.start,step.end,now,sessionGroup,"VERSE".equals(stage),()->
            progress.recordRecall(e.id,stage,step.start,step.end,100,true,now));
    }
    void success(LocalStore.Entry e,String stage,StudyPlanLogic.Step step,boolean combined,
                 boolean review,boolean group,long now){
        progress.commitStudyAttempt(e,step.start,step.end,now,sessionGroup,combined||"VERSE".equals(stage),()->{
            if(combined){
                for(int i=step.start;i<step.end;i++){
                    progress.recordRecall(e.id,"VERSE",i,i+1,100,false,now);
                    progress.scheduleReview(e.id,"VERSE",i,i+1,100,false,now);
                }
            }else{
                progress.recordRecall(e.id,stage,step.start,step.end,100,false,now);
                progress.scheduleReview(e.id,stage,step.start,step.end,100,false,now);
                if(!progress.isStepMastered(e.id,stage,step.start,step.end))
                    progress.markMastered(e.id,step.start,step.end,100,stage);
            }
            if(!review&&!group&&"VERSE".equals(stage))progress.finishExercise(e.id,step.start,5);
        });
    }
    void failure(LocalStore.Entry e,String stage,StudyPlanLogic.Step step,int accuracy,
                 boolean combined,boolean scheduled,int weakVerse,int weakFrom,boolean rescheduleWeak,long now){
        progress.commitStudyAttempt(e,step.start,step.end,now,sessionGroup,combined||"VERSE".equals(stage),()->{
            if(!combined)progress.recordRecall(e.id,stage,step.start,step.end,accuracy,false,now);
            if(rescheduleWeak){
                progress.rescheduleWeakVerse(e.id,weakVerse,now);
                if(weakFrom>=0)progress.recordWeakTransition(e.id,weakFrom,weakVerse,now);
            }
            if(combined){
                if(weakVerse>=step.start&&weakVerse<step.end){
                    progress.recordRecall(e.id,"VERSE",weakVerse,weakVerse+1,0,false,now);
                    progress.scheduleReview(e.id,"VERSE",weakVerse,weakVerse+1,0,false,now);
                }
            }else if(scheduled)progress.scheduleReview(e.id,stage,step.start,step.end,accuracy,false,now);
        });
    }
}
