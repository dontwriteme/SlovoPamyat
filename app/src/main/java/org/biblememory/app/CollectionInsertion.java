package org.biblememory.app;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

final class CollectionInsertion {
    private long insertAfterEntryId=-1,insertAfterCardId=-1;
    private final List<Long> studyInsertionTrail=new ArrayList<>();
    private final List<Long> cardInsertionTrail=new ArrayList<>();

    long afterStudy(){return insertAfterEntryId;}
    long afterCard(){return insertAfterCardId;}
    void appendStudy(long id){insertAfterEntryId=id;studyInsertionTrail.add(id);}
    void appendCard(long id){insertAfterCardId=id;cardInsertionTrail.add(id);}

    void save(Bundle out){
        out.putLong("picker_insert_entry",insertAfterEntryId);
        out.putLong("picker_insert_card",insertAfterCardId);
    }

    void restore(Bundle state){
        insertAfterEntryId=state.getLong("picker_insert_entry",-1);
        insertAfterCardId=state.getLong("picker_insert_card",-1);
    }

    /** Reset only when leaving the shared picker or starting a distinct insertion action. */
    void clear(){
        insertAfterEntryId=-1;insertAfterCardId=-1;
        studyInsertionTrail.clear();cardInsertionTrail.clear();
    }
    void remove(long id,boolean card){
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
}
