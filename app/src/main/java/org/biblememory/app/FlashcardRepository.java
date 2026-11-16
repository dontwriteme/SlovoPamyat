package org.biblememory.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
import org.biblememory.app.LocalStore.*;

/** SQL operations for this domain, sharing the owner's connection and transactions. */
public final class FlashcardRepository {
    private final RepositoryScope scope;
    FlashcardRepository(RepositoryScope scope){this.scope=scope;}

    public long newCardDeck(String title){
        ContentValues v=new ContentValues();v.put("title",title.trim());
        return scope.write().insertOrThrow("card_decks",null,v);
    }

    public List<CardDeck> cardDecks(){
        List<CardDeck> list=new ArrayList<>();
        try(Cursor c=scope.read().rawQuery("SELECT d.id,d.title,COUNT(m.card_id) FROM card_decks d LEFT JOIN card_deck_members m ON m.deck_id=d.id GROUP BY d.id ORDER BY d.id",null)){
            while(c.moveToNext()){
                CardDeck d=new CardDeck();d.id=c.getLong(0);d.title=c.getString(1);d.count=c.getInt(2);list.add(d);
            }
        }
        return list;
    }

    public CardDeck cardDeck(long id){for(CardDeck d:cardDecks())if(d.id==id)return d;return null;}

    public void renameCardDeck(long id,String title){
        ContentValues v=new ContentValues();v.put("title",title.trim());
        scope.write().update("card_decks",v,"id=?",new String[]{String.valueOf(id)});
    }

    public void deleteCardDeck(long id){
        SQLiteDatabase db=scope.write();
        db.delete("card_decks","id=?",new String[]{String.valueOf(id)});
        db.execSQL("DELETE FROM flashcards WHERE NOT EXISTS (SELECT 1 FROM card_deck_members m WHERE m.card_id=flashcards.id)");
    }

    public boolean addFlashcard(long deckId,int bookId,int sc,int sv,int ec,int ev,String reference,String content){
        if(bookId<1||bookId>66||!PassageRangeLogic.valid(sc,sv,ec,ev)||content.trim().isEmpty())
            throw new IllegalArgumentException("Неверный библейский отрывок");
        SQLiteDatabase db=scope.write();db.beginTransaction();
        try{
            ContentValues v=new ContentValues();v.put("book_id",bookId);
            v.put("first_chapter",sc);v.put("first_verse",sv);v.put("last_chapter",ec);v.put("last_verse",ev);
            v.put("reference",reference);v.put("content",content);
            db.insertWithOnConflict("flashcards",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            long card=-1;
            try(Cursor c=db.rawQuery("SELECT id FROM flashcards WHERE book_id=? AND first_chapter=? AND first_verse=? AND last_chapter=? AND last_verse=?",
                new String[]{String.valueOf(bookId),String.valueOf(sc),String.valueOf(sv),String.valueOf(ec),String.valueOf(ev)})){
                if(c.moveToFirst())card=c.getLong(0);
            }
            if(card<0)throw new IllegalStateException("Не удалось сохранить карточку");
            ContentValues m=new ContentValues();m.put("deck_id",deckId);m.put("card_id",card);
            boolean added=db.insertWithOnConflict("card_deck_members",null,m,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
            db.setTransactionSuccessful();return added;
        }finally{db.endTransaction();}
    }

    public long addCustomFlashcard(long deckId,String reference,String content){
        if(reference==null||reference.trim().isEmpty()||content==null||content.trim().isEmpty())
            throw new IllegalArgumentException("Укажи название и текст");
        SQLiteDatabase db=scope.write();db.beginTransaction();
        try{
            int unique;
            try(Cursor c=db.rawQuery("SELECT COALESCE(MIN(first_verse),0)-1 FROM flashcards WHERE book_id=0",null)){
                c.moveToFirst();unique=c.getInt(0);
            }
            ContentValues v=new ContentValues();v.put("book_id",0);
            v.put("first_chapter",0);v.put("first_verse",unique);
            v.put("last_chapter",0);v.put("last_verse",unique);
            v.put("reference",reference.trim());v.put("content",content.trim());
            long id=db.insertOrThrow("flashcards",null,v);
            ContentValues m=new ContentValues();m.put("deck_id",deckId);m.put("card_id",id);
            db.insertOrThrow("card_deck_members",null,m);
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }

    public List<Flashcard> flashcards(long deckId){
        List<Flashcard> result=new ArrayList<>();
        try(Cursor c=scope.read().rawQuery(
            "SELECT f.id,f.book_id,f.first_chapter,f.first_verse,f.last_chapter,f.last_verse,f.reference,f.content FROM flashcards f JOIN card_deck_members m ON m.card_id=f.id LEFT JOIN card_order o ON o.deck_id=m.deck_id AND o.card_id=f.id WHERE m.deck_id=? ORDER BY COALESCE(o.position,1000000),f.book_id,f.first_chapter,f.first_verse",
            new String[]{String.valueOf(deckId)})){
            while(c.moveToNext()){
                Flashcard f=new Flashcard();f.id=c.getLong(0);f.bookId=c.getInt(1);f.firstChapter=c.getInt(2);
                f.firstVerse=c.getInt(3);f.lastChapter=c.getInt(4);f.lastVerse=c.getInt(5);
                f.reference=c.getString(6);f.content=c.getString(7);result.add(f);
            }
        }
        return result;
    }

    public void removeFlashcard(long deckId,long cardId){
        SQLiteDatabase db=scope.write();
        db.delete("card_deck_members","deck_id=? AND card_id=?",new String[]{String.valueOf(deckId),String.valueOf(cardId)});
        db.execSQL("DELETE FROM flashcards WHERE id=? AND NOT EXISTS (SELECT 1 FROM card_deck_members WHERE card_id=?)",new Object[]{cardId,cardId});
    }

    public void reorderCards(long deckId,List<Long> ids){
        SQLiteDatabase db=scope.write();db.beginTransaction();
        try{
            db.delete("card_order","deck_id=?",new String[]{String.valueOf(deckId)});
            int pos=0;
            for(long id:ids){
                ContentValues values=new ContentValues();values.put("deck_id",deckId);
                values.put("card_id",id);values.put("position",pos++);
                db.insertWithOnConflict("card_order",null,values,SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    public void changeCardInDeck(long deckId,Flashcard old,int book,int sc,int sv,int ec,int ev,
            String reference,String content){
        if(old.bookId==0){
            long fresh=addCustomFlashcard(deckId,reference,content);
            removeFlashcard(deckId,old.id);
        }else{
            if(old.bookId==book&&old.firstChapter==sc&&old.firstVerse==sv&&
                old.lastChapter==ec&&old.lastVerse==ev)return;
            if(!addFlashcard(deckId,book,sc,sv,ec,ev,reference,content))
                throw new IllegalArgumentException("Такая карточка уже есть в подборке");
            removeFlashcard(deckId,old.id);
        }
    }
}
