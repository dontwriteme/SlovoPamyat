package org.biblememory.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
import org.biblememory.app.LocalStore.*;

/** SQL operations for this domain, sharing the owner's connection and transactions. */
public final class CollectionRepository {
    private final RepositoryScope scope;
    CollectionRepository(RepositoryScope scope){this.scope=scope;}

    public List<UnifiedCollection> unifiedCollections(){
        List<UnifiedCollection> result=new ArrayList<>();
        try(Cursor c=scope.read().rawQuery(
            "SELECT u.id,u.title,u.group_id,u.deck_id,u.origin,"+
            "(SELECT COUNT(*) FROM entries e WHERE e.group_id=u.group_id),"+
            "(SELECT COUNT(*) FROM card_deck_members m WHERE m.deck_id=u.deck_id) "+
            "FROM unified_collections u ORDER BY u.id",null)){
            while(c.moveToNext()){
                UnifiedCollection u=new UnifiedCollection();u.id=c.getLong(0);u.title=c.getString(1);
                u.groupId=c.getLong(2);u.deckId=c.getLong(3);u.origin=c.getString(4);
                u.entries=c.getInt(5);u.cards=c.getInt(6);result.add(u);
            }
        }
        return result;
    }

    public UnifiedCollection unifiedCollection(long id){
        for(UnifiedCollection u:unifiedCollections())if(u.id==id)return u;
        return null;
    }

    public UnifiedCollection unifiedForGroup(long groupId){
        for(UnifiedCollection u:unifiedCollections())if(u.groupId==groupId)return u;
        return null;
    }

    public long createUnifiedCollection(String title){
        String name=title==null?"":title.trim();
        if(name.isEmpty())throw new IllegalArgumentException("Укажи название коллекции");
        SQLiteDatabase db=scope.write();db.beginTransaction();
        try{
            ContentValues group=new ContentValues();group.put("title",name);
            long groupId=db.insertOrThrow("groups_",null,group);
            ContentValues deck=new ContentValues();deck.put("title",name);
            long deckId=db.insertOrThrow("card_decks",null,deck);
            ContentValues entry=new ContentValues();entry.put("title",name);
            entry.put("group_id",groupId);entry.put("deck_id",deckId);entry.put("origin","new");
            long id=db.insertOrThrow("unified_collections",null,entry);
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }

    public void renameUnifiedCollection(long id,String title){
        UnifiedCollection u=unifiedCollection(id);if(u==null)return;
        String name=title.trim();if(name.isEmpty())throw new IllegalArgumentException("Пустое название");
        SQLiteDatabase db=scope.write();db.beginTransaction();
        try{
            ContentValues v=new ContentValues();v.put("title",name);
            db.update("unified_collections",v,"id=?",new String[]{String.valueOf(id)});
            db.update("groups_",v,"id=?",new String[]{String.valueOf(u.groupId)});
            db.update("card_decks",v,"id=?",new String[]{String.valueOf(u.deckId)});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    public void deleteUnifiedCollection(long id){
        scope.write().delete("unified_collections","id=?",new String[]{String.valueOf(id)});
    }

    public void clearUnifiedCollection(long id){
        UnifiedCollection u=unifiedCollection(id);
        if(u==null)return;
        SQLiteDatabase db=scope.write();db.beginTransaction();
        try{
            long archiveGroup=scope.study.libraryId(db);
            String[] collection={String.valueOf(u.id)};
            String[] group={String.valueOf(u.groupId)};
            String[] deck={String.valueOf(u.deckId)};
            // Remove collection-specific order and mapped relationships first.
            db.delete("unified_entry_cards","collection_id=?",collection);
            db.delete("unified_card_entries","collection_id=?",collection);
            db.delete("study_order","group_id=?",group);
            db.delete("card_order","deck_id=?",deck);
            db.delete("card_deck_members","deck_id=?",deck);
            ContentValues archived=new ContentValues();archived.put("group_id",archiveGroup);
            db.update("entries",archived,"group_id=?",group);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    public boolean belongsToGroupMaterial(long groupId,DueItem due){
        Entry source=scope.study.entry(due.entryId);
        if(source==null)return false;
        if(source.groupId==groupId)return true;
        int sourceBook=bibleBookFromReference(source.reference);
        if(sourceBook==0)return false;
        List<Bible.Verse> expected=source.lines();
        if(due.start<0||due.end>expected.size()||due.start>=due.end)return false;
        for(Entry target:scope.study.entries(groupId)){
            if(bibleBookFromReference(target.reference)!=sourceBook)continue;
            List<Bible.Verse> lines=target.lines();
            for(int i=0;i+due.end-due.start<=lines.size();i++){
                boolean equal=true;
                for(int j=due.start;j<due.end;j++){
                    Bible.Verse a=expected.get(j),b=lines.get(i+j-due.start);
                    if(!a.ref.equals(b.ref)||!a.text.equals(b.text)){equal=false;break;}
                }
                if(equal)return true;
            }
        }
        return false;
    }

    static int bibleBookFromReference(String reference){
        if(reference==null)return 0;
        for(int i=0;i<Bible.NAMES.length;i++)if(reference.startsWith(Bible.NAMES[i]+" "))return i+1;
        return 0;
    }

    static int[] verseNumbers(String ref){
        try{String[] parts=ref.split(":",2);return new int[]{Integer.parseInt(parts[0]),Integer.parseInt(parts[1])};}
        catch(Exception ignored){return null;}
    }

    public void syncUnifiedCollection(long collectionId,Bible bible){
        SQLiteDatabase db=scope.write();db.beginTransaction();
        try{
            syncUnifiedCollectionInTransaction(collectionId,bible);
            db.setTransactionSuccessful();
        }catch(RuntimeException failure){
            // Never log the saved text or SQL argument values.
            android.util.Log.w("CollectionRepository","Collection sync rolled back; collection="+collectionId+
                "; error="+failure.getClass().getSimpleName());
            throw failure;
        }finally{db.endTransaction();}
    }

    void syncUnifiedCollectionInTransaction(long collectionId,Bible bible){
        UnifiedCollection u=unifiedCollection(collectionId);
        if(u==null)return;
        // Legacy decks previously had no study entries. Preserve their original card records.
        for(Flashcard card:scope.flashcards.flashcards(u.deckId)){
            if(mappedCardEntry(collectionId,card.id))continue;
            List<Bible.Verse> verses;
            try{
                if(card.bookId>0){if(bible==null||!bible.isReady())continue;
                    verses=bible.passage(card.bookId,card.firstChapter,card.firstVerse,card.lastChapter,card.lastVerse);
                }else verses=Collections.singletonList(new Bible.Verse("1:1",card.content));
                long entryId=-1;
                for(Entry old:scope.study.entries(u.groupId)){
                    if(old.reference.equals(card.reference)&&old.lines().size()==verses.size()){
                        boolean identical=true;List<Bible.Verse> prior=old.lines();
                        for(int i=0;i<prior.size();i++)if(!prior.get(i).ref.equals(verses.get(i).ref)||
                            !prior.get(i).text.equals(verses.get(i).text)){identical=false;break;}
                        if(identical){entryId=old.id;break;}
                    }
                }
                if(entryId<0)entryId=scope.study.add(u.groupId,card.reference,card.reference,verses);
                mapCardEntry(collectionId,card.id,entryId);
                // A pre-existing single-verse/custom card already supplies this entry.
                // Do not manufacture a duplicate when syncing study entries below.
                if(verses.size()==1)mapEntryCard(collectionId,entryId,0,card.id);
            }catch(SQLiteException failure){throw failure;}
            catch(Exception failure){
                // Retain inaccessible legacy material, but make the controlled skip observable.
                android.util.Log.w("CollectionRepository","Skipped legacy card; collection="+collectionId+
                    "; card="+card.id+"; error="+failure.getClass().getSimpleName());
            }
        }
        // Study passages automatically supply one flashcard per canonical verse.
        for(Entry entry:scope.study.entries(u.groupId)){
            int book=bibleBookFromReference(entry.reference);
            List<Bible.Verse> verses=entry.lines();
            for(int i=0;i<verses.size();i++){
                if(mappedEntryCard(collectionId,entry.id,i))continue;
                Bible.Verse line=verses.get(i);
                int[] ref=verseNumbers(line.ref);
                long cardId=-1;
                if(book>0&&ref!=null){
                    scope.flashcards.addFlashcard(u.deckId,book,ref[0],ref[1],ref[0],ref[1],
                        Bible.reference(book,ref[0],ref[1],ref[0],ref[1]),line.text);
                    cardId=scope.flashcards.cardIdInDeck(u.deckId,book,ref[0],ref[1],ref[0],ref[1]);
                }else{
                    cardId=scope.flashcards.addCustomFlashcard(u.deckId,entry.reference,line.text);
                }
                if(cardId>0){mapEntryCard(collectionId,entry.id,i,cardId);
                    // The generated card is already represented by its source entry.
                    mapCardEntry(collectionId,cardId,entry.id);
                }
            }
        }
    }

    boolean mappedCardEntry(long collection,long card){
        try(Cursor c=scope.read().rawQuery("SELECT 1 FROM unified_card_entries WHERE collection_id=? AND card_id=? LIMIT 1",
            new String[]{String.valueOf(collection),String.valueOf(card)})){return c.moveToFirst();}
    }

    boolean mappedEntryCard(long collection,long entry,int pos){
        try(Cursor c=scope.read().rawQuery(
            "SELECT 1 FROM unified_entry_cards x JOIN unified_collections u ON u.id=x.collection_id "+
            "JOIN card_deck_members m ON m.deck_id=u.deck_id AND m.card_id=x.card_id "+
            "WHERE x.collection_id=? AND x.entry_id=? AND x.verse_position=? LIMIT 1",
            new String[]{String.valueOf(collection),String.valueOf(entry),String.valueOf(pos)})){return c.moveToFirst();}
    }

    void mapCardEntry(long collection,long card,long entry){
        ContentValues v=new ContentValues();v.put("collection_id",collection);v.put("card_id",card);v.put("entry_id",entry);
        scope.write().insertWithOnConflict("unified_card_entries",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    void mapEntryCard(long collection,long entry,int pos,long card){
        ContentValues v=new ContentValues();v.put("collection_id",collection);v.put("entry_id",entry);
        v.put("verse_position",pos);v.put("card_id",card);
        scope.write().insertWithOnConflict("unified_entry_cards",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public long newGroup(String title) {
        ContentValues v=new ContentValues();v.put("title",title.trim());
        return scope.write().insertOrThrow("groups_",null,v);
    }

    public List<Group> groups() {
        List<Group> list=new ArrayList<>();
        try(Cursor c=scope.read().rawQuery("SELECT g.id,g.title,COUNT(e.id) FROM groups_ g LEFT JOIN entries e ON e.group_id=g.id WHERE g.title!='__slovo_cards_study_library__' GROUP BY g.id ORDER BY g.id",null)) {
            while(c.moveToNext()) { Group g=new Group();g.id=c.getLong(0);g.title=c.getString(1);g.count=c.getInt(2);list.add(g); }
        }
        return list;
    }

    public Group group(long id) {
        for(Group g:groups()) if(g.id==id) return g;
        return null;
    }

    public boolean removeUnifiedPassage(long groupId,String reference){
        return scope.transaction(transactionDb->{
            UnifiedCollection u=unifiedForGroup(groupId);
            if(u==null)return detachStudyReference(groupId,reference);
            SQLiteDatabase db=scope.write();
            // Keep unrelated cards that are also supplied by other passages of this collection.
            List<Long> toHide=new ArrayList<>();
            try(Cursor c=db.rawQuery(
                "SELECT DISTINCT x.card_id FROM unified_entry_cards x JOIN entries e ON e.id=x.entry_id "+
                "WHERE x.collection_id=? AND e.group_id=? AND e.reference=?",
                new String[]{String.valueOf(u.id),String.valueOf(groupId),reference})){
                while(c.moveToNext())toHide.add(c.getLong(0));
            }
            boolean changed=detachStudyReference(groupId,reference);
            if(changed){
                for(long cardId:toHide){
                    try(Cursor c=db.rawQuery("SELECT 1 FROM unified_entry_cards x JOIN entries e ON e.id=x.entry_id "+
                        "WHERE x.collection_id=? AND x.card_id=? AND e.group_id=? LIMIT 1",
                        new String[]{String.valueOf(u.id),String.valueOf(cardId),String.valueOf(groupId)})){
                        if(!c.moveToFirst())db.delete("card_deck_members","deck_id=? AND card_id=?",
                            new String[]{String.valueOf(u.deckId),String.valueOf(cardId)});
                    }
                }
            }
            return changed;
        });
    }

    public boolean detachStudyReference(long groupId,String reference){
        SQLiteDatabase db=scope.write();db.beginTransaction();
        try{
            long library=scope.study.libraryId(db);
            ContentValues values=new ContentValues();values.put("group_id",library);
            int changed=db.update("entries",values,"group_id=? AND reference=?",
                new String[]{String.valueOf(groupId),reference});
            db.setTransactionSuccessful();return changed>0;
        }finally{db.endTransaction();}
    }

    public long addPickedPassage(long groupId,String reference,List<Bible.Verse> verses,Bible bible){
        return addPickedPassage(groupId,reference,verses,bible,-1);
    }

    public long addPickedPassage(long groupId,String reference,List<Bible.Verse> verses,Bible bible,long after){
        SQLiteDatabase db=scope.write();db.beginTransaction();
        try{
            UnifiedCollection collection=unifiedForGroup(groupId);
            if(collection==null)throw new IllegalStateException("Коллекция больше не существует");
            long id=scope.study.restoreOrAddStudyPassage(groupId,reference,verses);
            syncUnifiedCollection(collection.id,bible);
            try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM unified_entry_cards x "+
                "JOIN card_deck_members m ON m.card_id=x.card_id AND m.deck_id=? "+
                "WHERE x.collection_id=? AND x.entry_id=?",
                new String[]{String.valueOf(collection.deckId),String.valueOf(collection.id),String.valueOf(id)})){
                if(!c.moveToFirst()||c.getInt(0)!=verses.size())
                    throw new IllegalStateException("Не удалось сохранить отрывок полностью");
            }
            if(after>=0){
                List<Long> ids=new ArrayList<>();
                for(Entry item:scope.study.entries(groupId))if(item.id!=id)ids.add(item.id);
                int index=ids.indexOf(after);ids.add(index<0?ids.size():index+1,id);
                scope.study.reorderStudy(groupId,ids);
            }
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }

    public void renameGroup(long id,String name) {
        ContentValues v=new ContentValues();v.put("title",name.trim());
        scope.write().update("groups_",v,"id=?",new String[]{String.valueOf(id)});
    }

    public void deleteGroup(long id) {scope.write().delete("groups_","id=?",new String[]{String.valueOf(id)});}
}
