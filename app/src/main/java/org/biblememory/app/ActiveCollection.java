package org.biblememory.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;

final class ActiveCollection {
    static final String PREFS="unified_library_ui";
    private final Context context;
    private long activeUnifiedId=-1;

    ActiveCollection(Context context){this.context=context;}
    long id(){return activeUnifiedId;}
    void load(){activeUnifiedId=preferences().getLong("active",-1);}
    private SharedPreferences preferences(){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    void clearSavedIfSelected(long id){
        SharedPreferences saved=preferences();
        if(saved.getLong("active",-1)==id)saved.edit().remove("active").apply();
    }

    void ensure(LocalStore store){
        if(store.unifiedCollection(activeUnifiedId)!=null)return;
        List<LocalStore.UnifiedCollection> collections=store.unifiedCollections();
        activeUnifiedId=collections.isEmpty()?-1:collections.get(0).id;
        preferences().edit().putLong("active",activeUnifiedId).apply();
    }
    LocalStore.UnifiedCollection get(LocalStore store){
        ensure(store);return store.unifiedCollection(activeUnifiedId);
    }
    void select(LocalStore store,long id){
        if(store.unifiedCollection(id)==null)return;
        activeUnifiedId=id;
        preferences().edit().putLong("active",id).apply();
    }
}
