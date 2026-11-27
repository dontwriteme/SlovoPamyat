package org.biblememory.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;

/** A durable intent is written before deleting anything. After process death,
 * repeating the operation is safe and removes stale preferences as well. */
final class LearningReset {
    private static final String JOURNAL="learning_reset_journal";
    private static final String PENDING="pending";
    private static final String[] SESSION_GROUPS={
        "study_session","collection_study_queue","gap_rotation","card_sessions"
    };
    private LearningReset(){}

    static synchronized void request(Context context,LocalStore store)throws IOException{
        SharedPreferences journal=context.getSharedPreferences(JOURNAL,Context.MODE_PRIVATE);
        require(journal.edit().putBoolean(PENDING,true).commit());
        recover(context,store);
    }

    static synchronized boolean recover(Context context,LocalStore store)throws IOException{
        SharedPreferences journal=context.getSharedPreferences(JOURNAL,Context.MODE_PRIVATE);
        if(!journal.getBoolean(PENDING,false))return false;
        store.resetLearningProgress();
        require(context.getSharedPreferences("home_verse",Context.MODE_PRIVATE).edit().clear().commit());
        for(String name:SESSION_GROUPS)
            require(context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit());
        SharedPreferences unified=context.getSharedPreferences("unified_library_ui",Context.MODE_PRIVATE);
        SharedPreferences.Editor edit=unified.edit();
        for(String key:unified.getAll().keySet())
            if(key.startsWith("run_history_")||key.startsWith("writing_run_"))edit.remove(key);
        require(edit.commit());
        // Clear the journal only after every step was committed successfully.
        require(journal.edit().remove(PENDING).commit());
        return true;
    }

    private static void require(boolean success)throws IOException{
        if(!success)throw new IOException("Не удалось сохранить изменения на устройстве");
    }
}
