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

    public long canonicalStudyEntryId(long entryId){
        Entry selected=entry(entryId);if(selected==null)return entryId;
        // Only canonical Bible references share study history, not user-authored titles.
        boolean bibleReference=false;
        for(String book:Bible.NAMES){
            if(selected.reference.startsWith(book+" ")){bibleReference=true;break;}
        }
        if(!bibleReference)return entryId;
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
        for(Entry old:allEntries()){
            if(!card.reference.equals(old.reference))continue;
            List<Bible.Verse> lines=old.lines();
            if(lines.size()!=verses.size())continue;
            boolean identical=true;
            for(int i=0;i<lines.size();i++){
                if(!lines.get(i).ref.equals(verses.get(i).ref) ||
                    !lines.get(i).text.equals(verses.get(i).text)){
                    identical=false;break;
                }
            }
            if(identical)return canonicalStudyEntryId(old.id);
        }
        SQLiteDatabase db=scope.write();long group=-1;
        try(Cursor c=db.rawQuery("SELECT id FROM groups_ WHERE title=? LIMIT 1",new String[]{LocalStore.CARD_STUDY_GROUP})){
            if(c.moveToFirst())group=c.getLong(0);
        }
        if(group<0)group=scope.collections.newGroup(LocalStore.CARD_STUDY_GROUP);
        return add(group,card.reference,card.reference,verses);
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
        try(Cursor c=db.rawQuery("SELECT id FROM groups_ WHERE title=? LIMIT 1",new String[]{LocalStore.CARD_STUDY_GROUP})){
            if(c.moveToFirst())return c.getLong(0);
        }
        ContentValues values=new ContentValues();values.put("title",LocalStore.CARD_STUDY_GROUP);
        return db.insertOrThrow("groups_",null,values);
    }

    public long restoreOrAddStudyPassage(long groupId,String reference,List<Bible.Verse> verses){
        if(verses==null||verses.isEmpty())throw new IllegalArgumentException("Пустой отрывок");
        for(Entry old:allEntries()){
            if(!old.reference.equals(reference))continue;
            if(old.groupId==groupId)return old.id;
            Group existing=scope.collections.group(old.groupId);
            if(existing!=null && !LocalStore.CARD_STUDY_GROUP.equals(existing.title))continue;
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
            String[] tables={"verse_progress","lesson_progress","stage_progress","recall_activity",
                "review_schedule","review_attempts","transition_issues"};
            for(String table:tables){
                List<ContentValues> saved=new ArrayList<>();
                try(Cursor c=db.query(table,null,"entry_id=?",new String[]{String.valueOf(id)},null,null,null)){
                    while(c.moveToNext()){
                        ContentValues cv=new ContentValues();
                        for(int col=0;col<c.getColumnCount();col++){
                            String key=c.getColumnName(col);
                            if(key.equals("id"))continue;
                            switch(c.getType(col)){
                                case Cursor.FIELD_TYPE_INTEGER:cv.put(key,c.getLong(col));break;
                                case Cursor.FIELD_TYPE_FLOAT:cv.put(key,c.getDouble(col));break;
                                case Cursor.FIELD_TYPE_STRING:cv.put(key,c.getString(col));break;
                                case Cursor.FIELD_TYPE_BLOB:cv.put(key,c.getBlob(col));break;
                                default:cv.putNull(key);break;
                            }
                        }
                        if(table.equals("verse_progress")||table.equals("lesson_progress")){
                            Integer to=remap.get(cv.getAsInteger("position"));
                            if(to==null)continue;
                            cv.put("position",to);
                        }else if(table.equals("transition_issues")){
                            Integer first=remap.get(cv.getAsInteger("from_position"));
                            Integer next=remap.get(cv.getAsInteger("to_position"));
                            if(first==null||next==null||next!=first+1)continue;
                            cv.put("from_position",first);cv.put("to_position",next);
                        }else{
                            int first=cv.getAsInteger("first_line"),last=cv.getAsInteger("last_line");
                            Integer mapped=remap.get(first);
                            if(mapped==null)continue;
                            boolean whole=true;
                            for(int i=first;i<last;i++){
                                if(!remap.containsKey(i)||remap.get(i)!=mapped+i-first){whole=false;break;}
                            }
                            if(!whole)continue;
                            cv.put("first_line",mapped);cv.put("last_line",mapped+last-first);
                        }
                        saved.add(cv);
                    }
                }
                db.delete(table,"entry_id=?",new String[]{String.valueOf(id)});
                for(ContentValues cv:saved)db.insertWithOnConflict(table,null,cv,SQLiteDatabase.CONFLICT_REPLACE);
            }
            JSONArray arr=new JSONArray();
            for(Bible.Verse v:verses){
                JSONObject j=new JSONObject();
                try{j.put("ref",v.ref);j.put("text",v.text);}catch(JSONException err){throw new IllegalStateException(err);}
                arr.put(j);
            }
            ContentValues values=new ContentValues();values.put("reference",reference);
            values.put("title",reference);values.put("lines",arr.toString());
            db.update("entries",values,"id=?",new String[]{String.valueOf(id)});
            // The edited text/range invalidates its previously generated cards.
            // Remap them on next sync, preserving other passages' shared cards.
            List<long[]> linked=new ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT x.collection_id,u.deck_id,x.card_id FROM unified_entry_cards x "+
                "JOIN unified_collections u ON u.id=x.collection_id WHERE x.entry_id=?",
                new String[]{String.valueOf(id)})){
                while(c.moveToNext())linked.add(new long[]{c.getLong(0),c.getLong(1),c.getLong(2)});
            }
            db.delete("unified_entry_cards","entry_id=?",new String[]{String.valueOf(id)});
            for(long[] row:linked){
                try(Cursor c=db.rawQuery("SELECT 1 FROM unified_entry_cards WHERE collection_id=? AND card_id=? LIMIT 1",
                    new String[]{String.valueOf(row[0]),String.valueOf(row[2])})){
                    if(!c.moveToFirst()){
                        db.delete("card_deck_members","deck_id=? AND card_id=?",
                            new String[]{String.valueOf(row[1]),String.valueOf(row[2])});
                        db.delete("unified_card_entries","collection_id=? AND card_id=?",
                            new String[]{String.valueOf(row[0]),String.valueOf(row[2])});
                    }
                }
            }
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

    public void deleteEntry(long id) {scope.write().delete("entries","id=?",new String[]{String.valueOf(id)});}
}
