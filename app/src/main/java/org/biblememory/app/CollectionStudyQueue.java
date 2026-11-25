package org.biblememory.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

final class CollectionStudyQueue {
    static final String PREFS="collection_study_queue";
    private final Context context;

    CollectionStudyQueue(Context context){this.context=context;}
    SharedPreferences preferences(){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    private final List<Long> collectionQueue=new ArrayList<>();
    private int collectionQueueCursor=-1;
    private long collectionQueueGroup=-1;
    private boolean executingCollectionQueue=false;
    private boolean collectionQueueNewStep=false;
    long group(){return collectionQueueGroup;}
    int cursor(){return collectionQueueCursor;}
    boolean executing(){return executingCollectionQueue;}
    void setExecuting(boolean value){executingCollectionQueue=value;}
    boolean newStep(){return collectionQueueNewStep;}
    void consumeNewStep(){collectionQueueNewStep=false;}

    boolean matchesCheckpoint(long group,int cursor,long source){
        return collectionQueueGroup==group&&collectionQueueCursor==cursor&&cursor>=0&&
            collectionQueue.get(cursor)==source;
    }

    /** Archived entries still exist: a saved queue is valid only while its entire
     * ordered membership matches the currently selected collection. */
    private boolean matches(LocalStore store,long groupId,List<LocalStore.Entry> entries){
        if(groupId<0||store.unifiedForGroup(groupId)==null||
            collectionQueueGroup!=groupId||collectionQueueCursor<0||
            collectionQueueCursor>=collectionQueue.size()||collectionQueue.size()!=entries.size())
            return false;
        for(int i=0;i<entries.size();i++)
            if(collectionQueue.get(i)!=entries.get(i).id)return false;
        return true;
    }
    void restore(LocalStore store){
        android.content.SharedPreferences p=preferences();
        if(!p.getBoolean("active",false))return;
        collectionQueue.clear();
        collectionQueueGroup=p.getLong("group",-1);
        collectionQueueCursor=p.getInt("cursor",0);
        String ids=p.getString("ids","");
        for(String raw:ids.split(","))try{
            collectionQueue.add(Long.parseLong(raw));
        }catch(NumberFormatException ignored){}
        if(!matches(store,collectionQueueGroup,store.entries(collectionQueueGroup)))
            clear();
    }
    private void persistCollectionQueue(){
        StringBuilder ids=new StringBuilder();
        for(long id:collectionQueue){if(ids.length()>0)ids.append(',');ids.append(id);}
        preferences().edit()
            .putBoolean("active",collectionQueueCursor>=0)
            .putLong("group",collectionQueueGroup).putInt("cursor",collectionQueueCursor)
            .putString("ids",ids.toString()).apply();
    }
    void clear(){
        reset();
        preferences().edit().clear().apply();
    }

    void reset(){
        collectionQueue.clear();collectionQueueCursor=-1;collectionQueueGroup=-1;
        executingCollectionQueue=false;collectionQueueNewStep=false;
    }

    long start(LocalStore store,long groupId,List<LocalStore.Entry> entries,CollectionProgressSnapshot snapshot,
               BiPredicate<LocalStore.Entry,CollectionProgressSnapshot> hasGroups){
        if(matches(store,groupId,entries)){
            executingCollectionQueue=true;
            collectionQueueNewStep=false;
            return collectionQueue.get(collectionQueueCursor);
        }
        // The membership/order changed: discard only the old queue, not study history.
        clear();
        for(LocalStore.Entry e:entries)collectionQueue.add(e.id);
        collectionQueueGroup=groupId;collectionQueueCursor=0;
        executingCollectionQueue=true;
        for(int i=0;i<entries.size();i++){
            LocalStore.Entry item=entries.get(i);
            if(snapshot.masteryCount(snapshot.canonicalId(item.id))<snapshot.verseCount(item.id) ||
                hasGroups.test(item,snapshot)){collectionQueueCursor=i;break;}
        }
        collectionQueueNewStep=true;
        persistCollectionQueue();return collectionQueue.get(collectionQueueCursor);
    }
    long resume(LocalStore store){
        if(!matches(store,collectionQueueGroup,store.entries(collectionQueueGroup))){
            clear();return -1;
        }
        executingCollectionQueue=true;
        collectionQueueNewStep=false;
        return collectionQueue.get(collectionQueueCursor);
    }
    long advance(){
        if(collectionQueueCursor<0||!executingCollectionQueue)return -1;
        collectionQueueCursor++;
        if(collectionQueueCursor>=collectionQueue.size()){
            clear();
            return -1;
        }
        persistCollectionQueue();
        collectionQueueNewStep=true;
        return collectionQueue.get(collectionQueueCursor);
    }
}
