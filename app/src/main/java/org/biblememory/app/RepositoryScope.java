package org.biblememory.app;

import android.database.sqlite.SQLiteDatabase;

/** One helper/connection and transaction boundary shared by all repositories. */
final class RepositoryScope {
    final LocalStore legacy;
    RepositoryScope(LocalStore helper){legacy=helper;}
    SQLiteDatabase read(){return legacy.getReadableDatabase();}
    SQLiteDatabase write(){return legacy.getWritableDatabase();}

    interface Work<T,E extends Exception> {T run(SQLiteDatabase database) throws E;}
    <T,E extends Exception> T transaction(Work<T,E> work) throws E {
        SQLiteDatabase database=write();database.beginTransaction();
        try{
            T result=work.run(database);database.setTransactionSuccessful();return result;
        }finally{database.endTransaction();}
    }
}
