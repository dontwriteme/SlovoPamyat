package org.biblememory.app;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** One helper/connection and transaction boundary shared by all repositories. */
final class RepositoryScope {
    private final SQLiteOpenHelper helper;
    final StudyRepository study;
    final ProgressRepository progress;
    final FlashcardRepository flashcards;
    final CollectionRepository collections;
    final LibraryBackup backup;
    RepositoryScope(SQLiteOpenHelper helper){
        this.helper=helper;
        study=new StudyRepository(this);
        progress=new ProgressRepository(this);
        flashcards=new FlashcardRepository(this);
        collections=new CollectionRepository(this);
        backup=new LibraryBackup(this);
    }
    SQLiteDatabase read(){return helper.getReadableDatabase();}
    SQLiteDatabase write(){return helper.getWritableDatabase();}

    interface Work<T,E extends Exception> {T run(SQLiteDatabase database) throws E;}
    <T,E extends Exception> T transaction(Work<T,E> work) throws E {
        SQLiteDatabase database=write();database.beginTransaction();
        try{
            T result=work.run(database);database.setTransactionSuccessful();return result;
        }finally{database.endTransaction();}
    }
}
