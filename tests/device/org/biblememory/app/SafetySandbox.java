package org.biblememory.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Test-only context: neither the installed database nor real preferences are opened. */
final class SafetySandbox extends ContextWrapper implements AutoCloseable {
    private final String prefix="architecture_safety_"+UUID.randomUUID()+"_";
    private final Set<String> preferences=new HashSet<>();
    final File directory;
    private LocalStore store;
    SafetySandbox(Context base){
        super(base);directory=new File(base.getCacheDir(),prefix);
        if(!directory.mkdirs())throw new AssertionError("Cannot create isolated database directory");
        if(getDatabasePath("slovo_pamyat.db").equals(base.getDatabasePath("slovo_pamyat.db")))
            throw new AssertionError("Test database is not isolated");
    }
    LocalStore store(){if(store==null)store=new LocalStore(this);return store;}
    // Keep future application-context normalization inside the same test boundary.
    @Override public Context getApplicationContext(){return this;}
    void closeStore(){if(store!=null){store.close();store=null;}}
    @Override public File getDatabasePath(String name){return new File(directory,new File(name).getName());}
    @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){
        return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);
    }
    @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){
        return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(),factory,handler);
    }
    @Override public boolean deleteDatabase(String name){return SQLiteDatabase.deleteDatabase(getDatabasePath(name));}
    @Override public SharedPreferences getSharedPreferences(String name,int mode){
        preferences.add(prefix+name);return getBaseContext().getSharedPreferences(prefix+name,mode);
    }
    @Override public void close(){
        closeStore();
        for(String name:preferences){
            if(!getBaseContext().getSharedPreferences(name,MODE_PRIVATE).edit().clear().commit())
                throw new AssertionError("Cannot flush test preferences");
            getBaseContext().deleteSharedPreferences(name);
        }
        delete(directory);
    }
    private static void delete(File file){
        File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);
        if(file.exists()&&!file.delete())throw new AssertionError("Cannot remove test fixture "+file);
    }
}
