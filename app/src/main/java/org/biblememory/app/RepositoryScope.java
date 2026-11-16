package org.biblememory.app;

import android.database.sqlite.SQLiteDatabase;

/** One helper/connection and transaction boundary shared by all repositories. */
final class RepositoryScope {
    final LocalStore legacy;
    final StudyRepository study;
    final ProgressRepository progress;
    final FlashcardRepository flashcards;
    RepositoryScope(LocalStore helper){
        legacy=helper;
        study=new StudyRepository(this);
        progress=new ProgressRepository(this);
        flashcards=new FlashcardRepository(this);
    }
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
