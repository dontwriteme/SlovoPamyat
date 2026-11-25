package org.biblememory.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

/** Checks a persisted exercise before the Activity opens any route or changes the live lesson. */
final class LessonRestoration {
    record Restored(LocalStore.Entry entry,LocalStore.Entry source,boolean review,boolean linked,
                    boolean complete,boolean inQueue,String stage,int start,int end,String key,
                    long deck,int deckCursor,long[] deckQueue) {}
    private LessonRestoration(){}

    static Restored validate(LocalStore store,SharedPreferences pref,Bundle state,CollectionStudyQueue studyQueue){
        if(state==null)return null;
        long id=state.getLong("entry");
        LocalStore.Entry entry=store.entry(id);
        long sourceId=state.getLong("source_entry");
        LocalStore.Entry source=sourceId==id?entry:store.entry(sourceId);
        if(!LessonCheckpoint.matchesMaterial(state,entry,source))return null;
        long deck=state.getLong("deck",-1);
        long[] deckQueue=state.getLongArray("deck_queue");
        int deckCursor=state.getInt("deck_cursor",-1);
        if(deck>0){
            String material=cardMaterial(store.flashcards(deck));
            if(material.isEmpty()||!material.equals(state.getString("deck_material"))||
                deckQueue==null||deckCursor<0||deckCursor>=deckQueue.length||deckQueue[deckCursor]!=sourceId)return null;
        }else if(store.archivedReviewGroups().contains(source.groupId))return null;
        boolean inQueue=state.containsKey("queue_group");
        if(inQueue&&!studyQueue.matchesCheckpoint(state.getLong("queue_group"),state.getInt("queue_cursor"),sourceId))
            return null;

        boolean review=state.containsKey("review_group");
        boolean linked=state.getBoolean("linked");
        boolean complete=state.getBoolean("complete");
        String stage=state.getString("stage","");
        StudyPlanLogic.Stage lessonStage;
        try{lessonStage=StudyPlanLogic.Stage.valueOf("VERSE_RUN".equals(stage)?"CHAIN":stage);}
        catch(IllegalArgumentException invalid){return null;}
        int start=state.getInt("begin",-1),end=state.getInt("end",-1);
        int cursor=state.getInt("cursor",-1);
        if(start<0||end<=start||end>entry.lines().size()||cursor<0)return null;
        if(!LessonCheckpoint.matchesRecall(store,state,id,stage,start,end,complete))return null;
        if(!review&&!linked){
            List<StudyPlanLogic.Step> steps=LessonPlans.steps(entry,lessonStage);
            if(cursor>=steps.size()||steps.get(cursor).start!=start||steps.get(cursor).end!=end)
                return null;
            if(lessonStage==StudyPlanLogic.Stage.VERSE&&
                (store.exerciseDone(id,start)!=state.getInt("exercise_done")||
                !LessonFlowLogic.canOpen(cursor,store.unlockedPosition(id,steps.size()),steps.size())))return null;
        }

        String key=review?ReviewSessionPreferences.PREFIX:(linked?"linked_":"entry_")+id+"_";
        if(!(review&&complete)&&(!lessonStage.name().equals(pref.getString(key+"stage",""))||
            cursor!=pref.getInt(key+"step",-1)||start!=pref.getInt(key+"begin",-1)||
            end!=pref.getInt(key+"end",-1)))return null;
        if(!review&&!state.getString("material").equals(pref.getString(key+"lesson_material","")))return null;
        return new Restored(entry,source,review,linked,complete,inQueue,stage,start,end,key,deck,deckCursor,deckQueue);
    }

    static void saveRoute(Bundle state,LessonSessionState lesson,ReviewSession reviewSession,CollectionStudyQueue studyQueue,
                          long cardStudyDeckId,int cardStudyCursor,String cardStudyMaterial,List<Long> cardStudyQueue){
        if(reviewSession.active()){
            state.putLong("review_group",reviewSession.group());
        }else if(studyQueue.executing()){
            state.putLong("queue_group",studyQueue.group());
            state.putInt("queue_cursor",studyQueue.cursor());
        }else if(cardStudyCursor>=0){
            state.putLong("deck",cardStudyDeckId);
            state.putInt("deck_cursor",cardStudyCursor);
            state.putString("deck_material",cardStudyMaterial);
            long[] ids=new long[cardStudyQueue.size()];
            for(int i=0;i<ids.length;i++)ids[i]=cardStudyQueue.get(i);
            state.putLongArray("deck_queue",ids);
        }
        if(lesson.lessonSourceEntry!=lesson.selectedEntry)state.putString("source_material",lesson.lessonSourceMaterial);
    }

    static String cardMaterial(List<LocalStore.Flashcard> deck){
        List<String> cards=new ArrayList<>();
        for(LocalStore.Flashcard card:deck){
            cards.add(Long.toString(card.id));cards.add(card.reference);cards.add(card.content);
            cards.add(card.bookId+":"+card.firstChapter+":"+card.firstVerse+":"+card.lastChapter+":"+card.lastVerse);
        }
        return cards.isEmpty()?"":SessionIntegrityLogic.fingerprint(cards);
    }

}
