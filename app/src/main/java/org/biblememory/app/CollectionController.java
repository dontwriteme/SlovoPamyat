package org.biblememory.app;
import android.os.Bundle;

import android.content.SharedPreferences;
import java.util.*;

/** Collection commands, membership, ordering and the independent-entry study queue. */
final class CollectionController {
    interface Ports {
        StudyRepository study(); ProgressRepository progress(); FlashcardRepository flashcards(); CollectionRepository collections();
        SharedPreferences activePreferences();
        SharedPreferences queuePreferences();
        Bible bible();
        boolean studyPicker();
        long pickerTargetId();
        int readerBook();
        void stopCardStudy();
        boolean hasPendingGroup(LocalStore.Entry entry);
        void openTraining(long entry);
        void navigate(Screen destination);
        void toast(String message);
        void clearRunHistory(long collection);
        boolean stopped();
    }
    private final Ports ports;
    CollectionController(Ports ports){this.ports=ports;}
    long activeUnifiedId = -1;
    long openUnifiedId = -1;
    long selectedGroup = 1;
    long customTargetGroupId = -1;
    final List<Long> collectionQueue = new ArrayList<>();
    int collectionQueueCursor = -1;
    long collectionQueueGroup = -1;
    boolean executingCollectionQueue = false;
    boolean collectionQueueNewStep = false;
    long insertAfterEntryId = -1;
    long insertAfterCardId = -1;
    final List<Long> studyInsertionTrail = new ArrayList<>();
    final List<Long> cardInsertionTrail = new ArrayList<>();

    void restoreCollectionQueue(){
        collectionQueue.clear();
        android.content.SharedPreferences p=ports.queuePreferences();
        if(!p.getBoolean("active",false))return;
        collectionQueueGroup=p.getLong("group",-1);
        collectionQueueCursor=p.getInt("cursor",0);
        String ids=p.getString("ids","");
        for(String raw:ids.split(","))try{
            long id=Long.parseLong(raw);if(ports.study().entry(id)!=null)collectionQueue.add(id);
        }catch(NumberFormatException ignored){}
        if(!queueValid())clearCollectionQueue();
    }

    boolean queueValid(){
        if(collectionQueueCursor<0||collectionQueueCursor>=collectionQueue.size()||
            !ports.collections().isVisibleGroup(collectionQueueGroup))return false;
        for(long id:collectionQueue)if(!ports.study().containsEntry(collectionQueueGroup,id))return false;
        return true;
    }

    void persistCollectionQueue(){
        StringBuilder ids=new StringBuilder();
        for(long id:collectionQueue){if(ids.length()>0)ids.append(',');ids.append(id);}
        ports.queuePreferences().edit()
            .putBoolean("active",collectionQueueCursor>=0)
            .putLong("group",collectionQueueGroup).putInt("cursor",collectionQueueCursor)
            .putString("ids",ids.toString()).apply();
    }

    void clearCollectionQueue(){
        collectionQueue.clear();collectionQueueCursor=-1;collectionQueueGroup=-1;
        executingCollectionQueue=false;collectionQueueNewStep=false;
        ports.queuePreferences().edit().clear().apply();
    }

    void startStudyCollection(long groupId){
        List<LocalStore.Entry> entries=ports.study().entries(groupId);
        if(entries.isEmpty()){ports.toast("Коллекция пока пустая");return;}
        ports.stopCardStudy();
        executingCollectionQueue=true;
        if(collectionQueueGroup==groupId&&collectionQueueCursor>=0&&
            collectionQueueCursor<collectionQueue.size()){
            long id=collectionQueue.get(collectionQueueCursor);
            if(queueValid()){collectionQueueNewStep=false;ports.openTraining(id);return;}
        }
        collectionQueue.clear();
        for(LocalStore.Entry e:entries)collectionQueue.add(e.id);
        collectionQueueGroup=groupId;collectionQueueCursor=0;
        for(int i=0;i<entries.size();i++){
            LocalStore.Entry item=entries.get(i);
            if(ports.progress().masteryCount(ports.study().canonicalStudyEntryId(item.id))<item.lines().size() ||
                ports.hasPendingGroup(item)){collectionQueueCursor=i;break;}
        }
        collectionQueueNewStep=true;
        persistCollectionQueue();ports.openTraining(collectionQueue.get(collectionQueueCursor));
    }

    boolean resumeCollectionTraining(){
        if(!queueValid()){clearCollectionQueue();return false;}
        executingCollectionQueue=true;
        long id=collectionQueue.get(collectionQueueCursor);
        if(ports.study().entry(id)==null){clearCollectionQueue();return false;}
        collectionQueueNewStep=false;
        ports.openTraining(id);return true;
    }

    void advanceStudyCollection(){
        if(collectionQueueCursor<0||!executingCollectionQueue)return;
        if(!queueValid()){clearCollectionQueue();ports.navigate(Screen.HOME);return;}
        collectionQueueCursor++;
        if(collectionQueueCursor>=collectionQueue.size()){
            clearCollectionQueue();
            ports.toast("Все отрывки коллекции пройдены");
            ports.navigate(Screen.HOME);return;
        }
        persistCollectionQueue();
        collectionQueueNewStep=true;
        ports.openTraining(collectionQueue.get(collectionQueueCursor));
    }

    String studyProgressFor(long groupId){
        int all=0,mastered=0;
        for(LocalStore.Entry e:ports.study().entries(groupId)){
            all+=e.lines().size();
            mastered+=ports.progress().masteryCount(ports.study().canonicalStudyEntryId(e.id));
        }
        return mastered+" из "+all+" стихов";
    }

    boolean pickerCollectionContainsVerse(int book,int chapter,int verse){
        if(!ports.studyPicker() || ports.pickerTargetId()<0)return false;
        String ref=Bible.reference(book,chapter,verse,chapter,verse);
        for(LocalStore.Entry e:ports.study().entries(ports.pickerTargetId()))
            if(ref.equals(e.reference))return true;
        return false;
    }

    boolean togglePickerCollectionVerse(int book,int chapter,int verse){
        if(!ports.studyPicker() ||
            ports.collections().unifiedForGroup(ports.pickerTargetId())==null)return false;
        String ref=Bible.reference(book,chapter,verse,chapter,verse);
        try{
            if(pickerCollectionContainsVerse(book,chapter,verse)){
                if(!ports.collections().removeUnifiedPassage(ports.pickerTargetId(),ref))return false;
                ports.toast("Убрано из коллекции: "+ref);
            }else{
                // Avoid creating duplicate standalone entries inside a saved long range.
                for(LocalStore.Entry entry:ports.study().entries(ports.pickerTargetId())){
                    if(!entry.reference.startsWith(Bible.NAMES[book-1]+" "))continue;
                    if(entry.reference.equals(ref))continue;
                    for(Bible.Verse line:entry.lines())if(line.ref.equals(chapter+":"+verse)){
                        ports.toast("Стих уже входит в отрывок этой коллекции");
                        return false;
                    }
                }
                ports.collections().addPickedPassage(ports.pickerTargetId(),ref,
                    ports.bible().passage(book,chapter,verse,chapter,verse),ports.bible());
                ports.toast("Добавлено в коллекцию: "+ref);
            }
            return true;
        }catch(Exception ignored){
            ports.toast("Не удалось изменить коллекцию");return false;
        }
    }

    boolean quickBibleVerseExists(int book,int chapter,int verse){
        LocalStore.UnifiedCollection active=activeUnified();
        if(active==null)return false;
        String ref=Bible.reference(book,chapter,verse,chapter,verse);
        for(LocalStore.Entry entry:ports.study().entries(active.groupId))
            if(ref.equals(entry.reference))return true;
        return false;
    }

    boolean toggleQuickBibleVerse(int book,int chapter,int verse){
        LocalStore.UnifiedCollection active=activeUnified();
        if(active==null){
            ports.toast("Сначала создай коллекцию на главной странице");
            return false;
        }
        String ref=Bible.reference(book,chapter,verse,chapter,verse);
        try{
            if(quickBibleVerseExists(book,chapter,verse)){
                if(!ports.collections().removeUnifiedPassage(active.groupId,ref))return false;
                ports.toast("Убрано из коллекции: "+ref);
            }else{
                ports.study().restoreOrAddStudyPassage(active.groupId,ref,
                    ports.bible().passage(book,chapter,verse,chapter,verse));
                ports.toast("Добавлено в «"+active.title+"»: "+ref);
            }
            return true;
        }catch(Exception ex){
            ports.toast("Не удалось изменить коллекцию");return false;
        }
    }

    boolean pickerHasReference(String ref,int book,int chapter,int verse){
        if(ports.studyPicker()){
            for(LocalStore.Entry e:ports.study().entries(ports.pickerTargetId()))if(ref.equals(e.reference))return true;
        }else{
            for(LocalStore.Flashcard f:ports.flashcards().flashcards(ports.pickerTargetId()))
                if(f.bookId==book&&f.firstChapter==chapter&&f.lastChapter==chapter&&
                    f.firstVerse==verse&&f.lastVerse==verse)return true;
        }
        return false;
    }

    boolean savePickedPassage(int sc,int sv,int ec,int ev){
        return savePickedPassageFor(ports.readerBook(),sc,sv,ec,ev);
    }

    boolean savePickedPassageFor(int book,int sc,int sv,int ec,int ev){
        try{
            if(!PassageRangeLogic.valid(sc,sv,ec,ev)){
                ports.toast("Конец отрывка раньше начала");return false;
            }
            List<Bible.Verse> verses=ports.studyPicker()?
                ports.bible().collectionPassage(book,sc,sv,ec,ev):ports.bible().passage(book,sc,sv,ec,ev);
            String ref=Bible.reference(book,sc,sv,ec,ev);
            if(ports.studyPicker()){
                if(ports.collections().unifiedForGroup(ports.pickerTargetId())==null){ports.toast("Коллекция больше не существует");return false;}
                for(LocalStore.Entry e:ports.study().entries(ports.pickerTargetId()))if(ref.equals(e.reference)){
                    ports.toast("Уже в коллекции");return false;
                }
                long added=ports.collections().addPickedPassage(ports.pickerTargetId(),ref,verses,ports.bible(),insertAfterEntryId);
                if(insertAfterEntryId>=0){
                    insertAfterEntryId=added;
                    studyInsertionTrail.add(added);
                }
            }else{
                boolean added=ports.flashcards().addFlashcard(ports.pickerTargetId(),book,sc,sv,ec,ev,ref,PassageText.join(verses));
                if(!added){ports.toast("Карточка уже в подборке");return false;}
                if(insertAfterCardId>=0){
                    for(LocalStore.Flashcard fc:ports.flashcards().flashcards(ports.pickerTargetId())){
                        if(ref.equals(fc.reference)){
                            repositionCardAfter(ports.pickerTargetId(),fc.id,insertAfterCardId);
                            insertAfterCardId=fc.id;
                            cardInsertionTrail.add(fc.id);
                            break;
                        }
                    }
                }
            }
            ports.toast("Добавлено: "+ref);return true;
        }catch(Exception ex){ports.toast(ex.getMessage()==null?"Не удалось добавить":ex.getMessage());return false;}
    }

    boolean removePickedVerse(String ref,int book,int chapter,int verse){
        try{
            if(ports.studyPicker()){
                long removedId=-1;
                for(LocalStore.Entry e:ports.study().entries(ports.pickerTargetId()))
                    if(ref.equals(e.reference)){removedId=e.id;break;}
                boolean removed=ports.collections().detachStudyReference(ports.pickerTargetId(),ref);
                if(removed&&removedId>=0)removedInsertionCandidate(removedId,false);
                return removed;
            }
            for(LocalStore.Flashcard f:ports.flashcards().flashcards(ports.pickerTargetId())){
                if(f.bookId==book&&f.firstChapter==chapter&&f.lastChapter==chapter&&
                    f.firstVerse==verse&&f.lastVerse==verse){
                    ports.flashcards().removeFlashcard(ports.pickerTargetId(),f.id);
                    removedInsertionCandidate(f.id,true);
                    return true;
                }
            }
        }catch(Exception ex){ports.toast("Не удалось убрать стих");}
        return false;
    }

    void saveBibleVerseToGroup(long groupId,int book,int chapter,int verse){
        try{
            String ref=Bible.reference(book,chapter,verse,chapter,verse);
            for(LocalStore.Entry e:ports.study().entries(groupId))if(ref.equals(e.reference)){
                ports.toast("Стих уже в подборке");return;
            }
            ports.study().restoreOrAddStudyPassage(groupId,ref,ports.bible().passage(book,chapter,verse,chapter,verse));
            ports.toast("Добавлено: "+ref);
        }catch(Exception ex){ports.toast("Не удалось добавить стих");}
    }

    void clearInsertionContext(){
        insertAfterEntryId=-1;insertAfterCardId=-1;
        studyInsertionTrail.clear();cardInsertionTrail.clear();
    }

    void removedInsertionCandidate(long id,boolean card){
        List<Long> trail=card?cardInsertionTrail:studyInsertionTrail;
        trail.remove(id);
        if(card){
            if(insertAfterCardId==id)
                insertAfterCardId=trail.isEmpty()?-1:trail.get(trail.size()-1);
        }else{
            if(insertAfterEntryId==id)
                insertAfterEntryId=trail.isEmpty()?-1:trail.get(trail.size()-1);
        }
    }

    void repositionStudyAfter(long group,long added,long after){
        List<Long> ids=new ArrayList<>();
        for(LocalStore.Entry item:ports.study().entries(group))if(item.id!=added)ids.add(item.id);
        int index=ids.indexOf(after);ids.add(index<0?ids.size():index+1,added);
        ports.study().reorderStudy(group,ids);
    }

    void repositionCardAfter(long deck,long added,long after){
        List<Long> ids=new ArrayList<>();
        for(LocalStore.Flashcard f:ports.flashcards().flashcards(deck))if(f.id!=added)ids.add(f.id);
        int index=ids.indexOf(after);ids.add(index<0?ids.size():index+1,added);
        ports.flashcards().reorderCards(deck,ids);
    }

    void ensureActiveUnified(){
        if(ports.collections().unifiedCollection(activeUnifiedId)!=null)return;
        activeUnifiedId=-1;
        ports.activePreferences().edit().putLong("active",activeUnifiedId).apply();
    }

    LocalStore.UnifiedCollection activeUnified(){
        ensureActiveUnified();return ports.collections().unifiedCollection(activeUnifiedId);
    }

    void activateUnified(long id){
        if(ports.collections().unifiedCollection(id)==null)return;
        activeUnifiedId=id;
        ports.activePreferences().edit().putLong("active",id).apply();
    }
    LocalStore.UnifiedCollection createUnifiedCollection(String title){
        boolean first=ports.collections().unifiedCollections().isEmpty();
        long id=ports.collections().createUnifiedCollection(title);
        if(first)activateUnified(id);
        return ports.collections().unifiedCollection(id);
    }
    void renameUnifiedCollection(long id,String title){ports.collections().renameUnifiedCollection(id,title);}
    void deleteUnifiedCollection(long id){
        LocalStore.UnifiedCollection u=ports.collections().unifiedCollection(id);
        ports.collections().deleteUnifiedCollection(id);
        if(u!=null&&collectionQueueGroup==u.groupId)clearCollectionQueue();
        ports.clearRunHistory(id);ensureActiveUnified();
    }
    void clearUnifiedCollection(long id){
        LocalStore.UnifiedCollection u=ports.collections().unifiedCollection(id);
        ports.collections().clearUnifiedCollection(id);
        if(u!=null&&collectionQueueGroup==u.groupId)clearCollectionQueue();
        ports.clearRunHistory(id);
    }
    void reorderStudy(long group,List<Long> ids){ports.study().reorderStudy(group,ids);}
    void reorderCards(long deck,List<Long> ids){ports.flashcards().reorderCards(deck,ids);}
    void synchronizeCollections(Bible bible){
        if(ports.stopped())return;
        for(LocalStore.UnifiedCollection collection:ports.collections().unifiedCollections()){
            if(ports.stopped())return;
            try{ports.collections().syncUnifiedCollection(collection.id,bible);}catch(Exception error){android.util.Log.e("Collections","Startup synchronization failed for collection "+collection.id+"; error="+error.getClass().getSimpleName());}
        }
    }


    /** Activity/process recreation snapshot; durable progress still belongs to LocalStore. */
    Bundle saveState(){
        Bundle state=new Bundle();
        state.putLong("activeUnifiedId",activeUnifiedId);
        state.putLong("openUnifiedId",openUnifiedId);
        state.putLong("selectedGroup",selectedGroup);
        state.putLong("customTargetGroupId",customTargetGroupId);
        SessionSnapshot.putLongs(state,"collectionQueue",collectionQueue);
        state.putInt("collectionQueueCursor",collectionQueueCursor);
        state.putLong("collectionQueueGroup",collectionQueueGroup);
        state.putBoolean("executingCollectionQueue",executingCollectionQueue);
        state.putBoolean("collectionQueueNewStep",collectionQueueNewStep);
        state.putLong("insertAfterEntryId",insertAfterEntryId);
        state.putLong("insertAfterCardId",insertAfterCardId);
        SessionSnapshot.putLongs(state,"studyInsertionTrail",studyInsertionTrail);
        SessionSnapshot.putLongs(state,"cardInsertionTrail",cardInsertionTrail);
        return state;
    }
    void restoreState(Bundle state){
        if(state==null)return;
        activeUnifiedId=state.getLong("activeUnifiedId",activeUnifiedId);
        openUnifiedId=state.getLong("openUnifiedId",openUnifiedId);
        selectedGroup=state.getLong("selectedGroup",selectedGroup);
        customTargetGroupId=state.getLong("customTargetGroupId",customTargetGroupId);
        SessionSnapshot.getLongs(state,"collectionQueue",collectionQueue);
        collectionQueueCursor=state.getInt("collectionQueueCursor",collectionQueueCursor);
        collectionQueueGroup=state.getLong("collectionQueueGroup",collectionQueueGroup);
        executingCollectionQueue=state.getBoolean("executingCollectionQueue",executingCollectionQueue);
        collectionQueueNewStep=state.getBoolean("collectionQueueNewStep",collectionQueueNewStep);
        insertAfterEntryId=state.getLong("insertAfterEntryId",insertAfterEntryId);
        insertAfterCardId=state.getLong("insertAfterCardId",insertAfterCardId);
        SessionSnapshot.getLongs(state,"studyInsertionTrail",studyInsertionTrail);
        SessionSnapshot.getLongs(state,"cardInsertionTrail",cardInsertionTrail);
        ensureActiveUnified();
        if(!queueValid())clearCollectionQueue();
    }
}
