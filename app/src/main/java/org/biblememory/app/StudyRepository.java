package org.biblememory.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
import org.biblememory.app.LocalStore.*;

/** SQL operations for this domain, sharing the owner's connection and transactions. */
public final class StudyRepository {
    private final RepositoryScope scope;
    StudyRepository(RepositoryScope scope){this.scope=scope;}
    private static final String FIELDS="id,group_id,title,reference,lines,learned,repetitions,days,ease,due,last_score";

    public boolean containsEntry(long groupId,long entryId){
        try(Cursor c=scope.read().rawQuery("SELECT 1 FROM entries WHERE group_id=? AND id=? LIMIT 1",
            new String[]{String.valueOf(groupId),String.valueOf(entryId)})){return c.moveToFirst();}
    }

    public long add(long groupId,String title,String ref,List<Bible.Verse> verses) {
        if(verses.isEmpty()) throw new IllegalArgumentException("Пустой текст");
        JSONArray array=new JSONArray();
        for(Bible.Verse v:verses) {
            JSONObject row=new JSONObject();
            try { row.put("ref",v.ref);row.put("text",v.text); } catch(JSONException ex) {throw new IllegalStateException(ex);}
            array.put(row);
        }
        ContentValues cv=new ContentValues();cv.put("group_id",groupId);
        cv.put("title",title);cv.put("reference",ref);cv.put("lines",array.toString());
        return scope.write().insertOrThrow("entries",null,cv);
    }

    /** Optional metadata in the existing verse JSON; old readers ignore these keys. */
    static int canonicalBook(Entry entry){
        try{
            JSONArray lines=new JSONArray(entry.linesJson);
            if(lines.length()==0||lines.getJSONObject(0).optBoolean("user_material",false))return 0;
        }
        catch(JSONException error){throw new IllegalStateException("Invalid saved passage",error);}
        return CollectionRepository.bibleBookFromReference(entry.reference);
    }
    private void tagEntry(SQLiteDatabase db,long id,String key,Object value){
        try{
            JSONArray lines=new JSONArray(entry(id).linesJson);lines.getJSONObject(0).put(key,value);
            ContentValues values=new ContentValues();values.put("lines",lines.toString());
            db.update("entries",values,"id=?",new String[]{String.valueOf(id)});
        }catch(JSONException error){throw new IllegalStateException("Invalid material metadata",error);}
    }
    public long addUserMaterial(long group,String title,String reference,List<Bible.Verse> verses){
        return scope.transaction(db->{long id=add(group,title,reference,verses);tagEntry(db,id,"user_material",true);return id;});
    }
    private boolean cardIdentity(Entry entry,long cardId){
        try{if(new JSONArray(entry.linesJson).getJSONObject(0).optLong("study_card_id",-1)==cardId)return true;}
        catch(JSONException error){throw new IllegalStateException("Invalid saved passage",error);}
        try(Cursor c=scope.read().rawQuery("SELECT 1 FROM unified_card_entries WHERE card_id=? AND entry_id=? LIMIT 1",
            new String[]{String.valueOf(cardId),String.valueOf(entry.id)})){return c.moveToFirst();}
    }

    public long canonicalStudyEntryId(long entryId){
        Entry selected=entry(entryId);if(selected==null)return entryId;
        // Only canonical Bible references share study history, not user-authored titles.
        boolean bibleReference=false;
        for(String book:Bible.NAMES){
            if(selected.reference.startsWith(book+" ")){bibleReference=true;break;}
        }
        if(!bibleReference||canonicalBook(selected)==0)return entryId;
        long best=entryId;int score=-1;
        try(Cursor c=scope.read().rawQuery(
            "SELECT id FROM entries WHERE reference=? AND lines=? ORDER BY id",
            new String[]{selected.reference,selected.linesJson})){
            while(c.moveToNext()){
                long id=c.getLong(0);
                int current=scope.progress.masteryCount(id)*10+scope.progress.completedExerciseCount(id);
                if(current>score){score=current;best=id;}
            }
        }
        return best;
    }

    public long findOrCreateCardStudyEntry(Flashcard card,List<Bible.Verse> verses){
        if(card==null||verses==null||verses.isEmpty())throw new IllegalArgumentException("Пустая карточка");
        return scope.transaction(db->{
            List<Entry> candidates=allEntries();
            if(card.bookId==0)candidates.sort(Comparator.comparingLong(entry->entry.id));
            for(Entry old:candidates){
                if(!card.reference.equals(old.reference))continue;
                if(card.bookId==0&&!cardIdentity(old,card.id))continue;
                if(card.bookId>0&&canonicalBook(old)==0)continue;
                List<Bible.Verse> lines=old.lines();
                if(lines.size()!=verses.size())continue;
                boolean identical=true;
                for(int i=0;i<lines.size();i++){
                    if(!lines.get(i).ref.equals(verses.get(i).ref) ||
                        !lines.get(i).text.equals(verses.get(i).text)){
                        identical=false;break;
                    }
                }
                if(identical){
                    if(card.bookId==0){tagEntry(db,old.id,"user_material",true);tagEntry(db,old.id,"study_card_id",card.id);return old.id;}
                    return canonicalStudyEntryId(old.id);
                }
            }
            long group=libraryId(db);
            long id=add(group,card.reference,card.reference,verses);
            if(card.bookId==0){tagEntry(db,id,"user_material",true);tagEntry(db,id,"study_card_id",card.id);}
            return id;
        });
    }

    public void reorderStudy(long groupId,List<Long> ids){
        SQLiteDatabase db=scope.write();db.beginTransaction();
        try{
            db.delete("study_order","group_id=?",new String[]{String.valueOf(groupId)});
            int pos=0;
            for(long id:ids){
                Entry entry=entry(id);
                if(entry==null||entry.groupId!=groupId)continue;
                ContentValues values=new ContentValues();values.put("group_id",groupId);
                values.put("entry_id",id);values.put("position",pos++);
                db.insertOrThrow("study_order",null,values);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    long libraryId(SQLiteDatabase db){
        try(Cursor c=db.rawQuery("SELECT g.id FROM groups_ g WHERE g.title=? AND NOT EXISTS (SELECT 1 FROM unified_collections u WHERE u.group_id=g.id) LIMIT 1",new String[]{LocalStore.CARD_STUDY_GROUP})){
            if(c.moveToFirst())return c.getLong(0);
        }
        ContentValues values=new ContentValues();values.put("title",LocalStore.CARD_STUDY_GROUP);
        return db.insertOrThrow("groups_",null,values);
    }

    public long restoreOrAddStudyPassage(long groupId,String reference,List<Bible.Verse> verses){
        if(verses==null||verses.isEmpty())throw new IllegalArgumentException("Пустой отрывок");
        boolean canonical=false;
        for(String book:Bible.NAMES)if(reference.startsWith(book+" ")){canonical=true;break;}
        for(Entry old:allEntries()){
            if(!old.reference.equals(reference))continue;
            if(canonical&&canonicalBook(old)==0)continue;
            if(old.groupId==groupId)return old.id;
            if(!canonical)continue; // Equal user text is not a stable identity.
            Group existing=scope.collections.group(old.groupId);
            if(existing!=null)continue;
            // Reattach a previously archived entry with its original progress.
            List<Bible.Verse> saved=old.lines();
            if(saved.size()!=verses.size())continue;
            boolean equal=true;
            for(int i=0;i<verses.size();i++){
                if(!saved.get(i).ref.equals(verses.get(i).ref) ||
                   !saved.get(i).text.equals(verses.get(i).text)){equal=false;break;}
            }
            if(equal){
                ContentValues values=new ContentValues();values.put("group_id",groupId);
                scope.write().update("entries",values,"id=?",new String[]{String.valueOf(old.id)});
                return old.id;
            }
        }
        return add(groupId,reference,reference,verses);
    }

    public void reviseStudyPassage(long id,String reference,List<Bible.Verse> verses){
        Entry old=entry(id);
        if(old==null||verses.isEmpty())throw new IllegalArgumentException("Отрывок не найден");
        List<Bible.Verse> before=old.lines();
        Map<Integer,Integer> remap=new HashMap<>();
        for(int i=0;i<before.size();i++)for(int j=0;j<verses.size();j++){
            if(before.get(i).ref.equals(verses.get(j).ref)&&
               before.get(i).text.equals(verses.get(j).text)){
                remap.put(i,j);break;
            }
        }
        SQLiteDatabase db=scope.write();db.beginTransaction();
        try{
            scope.progress.remapForRevision(db,id,remap);
            JSONArray arr=new JSONArray();
            for(Bible.Verse v:verses){
                JSONObject j=new JSONObject();
                try{j.put("ref",v.ref);j.put("text",v.text);}catch(JSONException err){throw new IllegalStateException(err);}
                arr.put(j);
            }
            try{
                JSONObject previous=new JSONArray(old.linesJson).getJSONObject(0),first=arr.getJSONObject(0);
                if(previous.optBoolean("user_material",false))first.put("user_material",true);
                if(previous.has("study_card_id"))first.put("study_card_id",previous.getLong("study_card_id"));
            }catch(JSONException error){throw new IllegalStateException("Invalid material metadata",error);}
            ContentValues values=new ContentValues();values.put("reference",reference);
            values.put("title",reference);values.put("lines",arr.toString());
            db.update("entries",values,"id=?",new String[]{String.valueOf(id)});
            scope.collections.invalidateEntryCards(db,id);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    private Entry readEntry(Cursor c) {
        Entry e=new Entry();e.id=c.getLong(0);e.groupId=c.getLong(1);
        e.title=c.getString(2);e.reference=c.getString(3);e.linesJson=c.getString(4);
        e.learned=c.getInt(5);e.repetitions=c.getInt(6);e.days=c.getInt(7);
        e.ease=c.getDouble(8);e.due=c.getLong(9);e.lastScore=c.getInt(10);
        return e;
    }

    public Entry entry(long id) {
        try(Cursor c=scope.read().rawQuery("SELECT "+FIELDS+" FROM entries WHERE id=?",new String[]{String.valueOf(id)})) {
            return c.moveToFirst()?readEntry(c):null;
        }
    }

    public List<Entry> entries(long groupId) {
        List<Entry> out=new ArrayList<>();
        try(Cursor c=scope.read().rawQuery("SELECT entries.id,entries.group_id,entries.title,entries.reference,entries.lines,entries.learned,entries.repetitions,entries.days,entries.ease,entries.due,entries.last_score FROM entries LEFT JOIN study_order o ON o.entry_id=entries.id WHERE entries.group_id=? ORDER BY COALESCE(o.position,1000000),entries.id DESC",new String[]{String.valueOf(groupId)})) {
            while(c.moveToNext()) out.add(readEntry(c));
        }
        return out;
    }

    public List<Entry> allEntries() {
        List<Entry> out=new ArrayList<>();
        try(Cursor c=scope.read().rawQuery("SELECT "+FIELDS+" FROM entries ORDER BY id DESC",null)) {
            while(c.moveToNext()) out.add(readEntry(c));
        }
        return out;
    }

    public void renameEntry(long id,String name) {
        ContentValues v=new ContentValues();v.put("title",name.trim());
        scope.write().update("entries",v,"id=?",new String[]{String.valueOf(id)});
    }

    public void deleteEntry(long id) {scope.collections.removeStudyEntry(id);}
}
