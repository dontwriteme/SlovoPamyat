package org.biblememory.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Private on-device SQLite database. No account, server or network permission. */
public final class LocalStore extends SQLiteOpenHelper implements LessonWritingFlow.RecallWriter {
    private static final String ARCHIVED_GROUP_PREFIX="__slovo_archived_group__:";
    private static final String CARD_STUDY_GROUP="__slovo_cards_study_library__";
    public static final class Group {
        public long id; public String title; public int count;
    }
    public static final class Entry {
        public long id, groupId, due;
        public String title, reference, linesJson;
        public int learned, repetitions, days, lastScore;
        public double ease;
        public List<Bible.Verse> lines() {
            List<Bible.Verse> out=new ArrayList<>();
            try {
                JSONArray arr=new JSONArray(linesJson);
                for(int i=0;i<arr.length();i++) {
                    JSONObject o=arr.getJSONObject(i);
                    out.add(new Bible.Verse(o.optString("ref"),o.getString("text")));
                }
            } catch(JSONException e) { throw new IllegalStateException("Invalid saved passage",e); }
            return out;
        }
    }
    private final java.util.concurrent.atomic.AtomicLong readQueries=new java.util.concurrent.atomic.AtomicLong();
    long readQueryCount(){return readQueries.get();}
    Cursor readQuery(String sql,String[] args){
        readQueries.incrementAndGet();
        return getReadableDatabase().rawQuery(sql,args);
    }
    private CollectionProgressSnapshot progressSnapshot;
    private long snapshotChanges=-1,snapshotDataVersion=-1;
    /** The transaction pins the primary connection: total_changes is connection-local.
     * data_version additionally invalidates writes made through another helper/connection.
     * Do not retain a snapshot built inside a writer's uncommitted transaction. */
    public CollectionProgressSnapshot collectionProgress(){
        SQLiteDatabase db=getReadableDatabase();
        boolean nested=db.inTransaction();
        db.beginTransactionNonExclusive();
        try{
            CollectionProgressSnapshot snapshot=readProgressSnapshot(nested);
            db.setTransactionSuccessful();
            return snapshot;
        }finally{db.endTransaction();}
    }

    /** Called with the primary connection pinned by the caller. */
    private synchronized CollectionProgressSnapshot readProgressSnapshot(boolean nested){
        // A caller-owned transaction builds a private snapshot. It must never
        // consult or update the cache based on uncommitted data.
        if(nested)return CollectionProgressSnapshot.load(this);
        long changes,version;
        try(Cursor c=readQuery("SELECT total_changes()",null)){changes=c.moveToFirst()?c.getLong(0):-1;}
        try(Cursor c=readQuery("PRAGMA data_version",null)){version=c.moveToFirst()?c.getLong(0):-1;}
        if(changes<0||version<0||progressSnapshot==null||changes!=snapshotChanges||version!=snapshotDataVersion){
            progressSnapshot=CollectionProgressSnapshot.load(this);
            snapshotChanges=changes;
            snapshotDataVersion=version;
        }
        return progressSnapshot;
    }
    private final Bible reviewBible;

    public LocalStore(Context context){this(context,Bible.shared());}
    LocalStore(Context context,Bible bible){
        super(context,"slovo_pamyat.db",null,9);
        reviewBible=bible;
        setWriteAheadLoggingEnabled(true);
    }

    boolean confirmReviewVerse(Entry entry,Bible.Verse verse){
        int book=bibleBookFromReference(entry.reference);
        int[] coordinate=verseNumbers(verse.ref);
        if(book==0||coordinate==null||coordinate[0]<1||coordinate[1]<1)return false;
        return reviewBible.matchesVerse(book,coordinate[0],coordinate[1],verse);
    }

    Set<Long> archivedReviewGroups(){
        Set<Long> ids=new HashSet<>();
        try(Cursor rows=readQuery("SELECT g.id FROM groups_ g WHERE (g.title=? OR g.title GLOB ?) "+
                "AND NOT EXISTS(SELECT 1 FROM unified_collections u WHERE u.group_id=g.id)",
                new String[]{CARD_STUDY_GROUP,ARCHIVED_GROUP_PREFIX+"*"})){
            while(rows.moveToNext())ids.add(rows.getLong(0));
        }
        return ids;
    }
    @Override public void onConfigure(SQLiteDatabase db){ db.setForeignKeyConstraintsEnabled(true); }

    /** Reset study/recall history without deleting any collections, saved Bible
     * passages, custom texts, book memberships or flashcards. Atomic on failure. */
    public void resetLearningProgress(){
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            for(String table:new String[]{
                "review_attempts","review_schedule","recall_activity","transition_issues",
                "lesson_progress","stage_progress","verse_progress"
            })db.delete(table,null,null);
            db.execSQL("UPDATE entries SET learned=0,repetitions=0,days=0,"+
                "ease=2.5,due=0,last_score=-1");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        synchronized(this){
            progressSnapshot=null;
            snapshotChanges=-1;
            snapshotDataVersion=-1;
        }
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE groups_ (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL)");
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, group_id INTEGER NOT NULL REFERENCES groups_(id) ON DELETE CASCADE, title TEXT NOT NULL, reference TEXT NOT NULL, lines TEXT NOT NULL, learned INTEGER NOT NULL DEFAULT 0, repetitions INTEGER NOT NULL DEFAULT 0, days INTEGER NOT NULL DEFAULT 0, ease REAL NOT NULL DEFAULT 2.5, due INTEGER NOT NULL DEFAULT 0, last_score INTEGER NOT NULL DEFAULT -1)");
        createProgressTables(db);
        createRecallTables(db);
        createLessonTables(db);
        createWeakTransitionsTable(db);
        createFlashcardTables(db);
        createOrderTables(db);
        createUnifiedTables(db);
        migrateLegacyCollections(db);
        createCollectionBookTable(db);
    }
    private static void createProgressTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS verse_progress (entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, position INTEGER NOT NULL, best_score INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(entry_id,position))");
        db.execSQL("CREATE TABLE IF NOT EXISTS stage_progress (entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, stage TEXT NOT NULL, first_line INTEGER NOT NULL, last_line INTEGER NOT NULL, best_score INTEGER NOT NULL, PRIMARY KEY(entry_id,stage,first_line,last_line))");
    }
    private static void createRecallTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS recall_activity (entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, stage TEXT NOT NULL, first_line INTEGER NOT NULL, last_line INTEGER NOT NULL, last_attempt INTEGER NOT NULL DEFAULT 0, first_success INTEGER NOT NULL DEFAULT 0, last_success INTEGER NOT NULL DEFAULT 0, success_count INTEGER NOT NULL DEFAULT 0, fail_streak INTEGER NOT NULL DEFAULT 0, last_score INTEGER NOT NULL DEFAULT -1, PRIMARY KEY(entry_id,stage,first_line,last_line))");
    }
    private static void createLessonTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS lesson_progress (entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, position INTEGER NOT NULL, completed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(entry_id,position))");
        db.execSQL("CREATE TABLE IF NOT EXISTS review_schedule (entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, stage TEXT NOT NULL, first_line INTEGER NOT NULL, last_line INTEGER NOT NULL, level INTEGER NOT NULL DEFAULT 0, due INTEGER NOT NULL, last_score INTEGER NOT NULL DEFAULT -1, PRIMARY KEY(entry_id,stage,first_line,last_line))");
        db.execSQL("CREATE TABLE IF NOT EXISTS review_attempts (id INTEGER PRIMARY KEY AUTOINCREMENT, entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, stage TEXT NOT NULL, first_line INTEGER NOT NULL, last_line INTEGER NOT NULL, attempted_at INTEGER NOT NULL, accuracy INTEGER NOT NULL, assisted INTEGER NOT NULL)");
    }
    private static void createWeakTransitionsTable(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS transition_issues (entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, from_position INTEGER NOT NULL, to_position INTEGER NOT NULL, failures INTEGER NOT NULL DEFAULT 0, last_failed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(entry_id,from_position,to_position))");
    }

    private static void createFlashcardTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS card_decks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS flashcards (id INTEGER PRIMARY KEY AUTOINCREMENT, book_id INTEGER NOT NULL, first_chapter INTEGER NOT NULL, first_verse INTEGER NOT NULL, last_chapter INTEGER NOT NULL, last_verse INTEGER NOT NULL, reference TEXT NOT NULL, content TEXT NOT NULL, UNIQUE(book_id,first_chapter,first_verse,last_chapter,last_verse))");
        db.execSQL("CREATE TABLE IF NOT EXISTS card_deck_members (deck_id INTEGER NOT NULL REFERENCES card_decks(id) ON DELETE CASCADE, card_id INTEGER NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE, PRIMARY KEY(deck_id,card_id))");
    }
    private static void createOrderTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS study_order (entry_id INTEGER PRIMARY KEY REFERENCES entries(id) ON DELETE CASCADE, group_id INTEGER NOT NULL, position INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS card_order (deck_id INTEGER NOT NULL REFERENCES card_decks(id) ON DELETE CASCADE, card_id INTEGER NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE, position INTEGER NOT NULL, PRIMARY KEY(deck_id,card_id))");
    }

    /** A unified library uses existing study groups and card decks without rewriting progress. */
    private static void createUnifiedTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS unified_collections (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, group_id INTEGER NOT NULL UNIQUE REFERENCES groups_(id), deck_id INTEGER NOT NULL UNIQUE REFERENCES card_decks(id), origin TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS unified_card_entries (collection_id INTEGER NOT NULL REFERENCES unified_collections(id) ON DELETE CASCADE, card_id INTEGER NOT NULL, entry_id INTEGER NOT NULL, PRIMARY KEY(collection_id,card_id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS unified_entry_cards (collection_id INTEGER NOT NULL REFERENCES unified_collections(id) ON DELETE CASCADE, entry_id INTEGER NOT NULL, verse_position INTEGER NOT NULL, card_id INTEGER NOT NULL, PRIMARY KEY(collection_id,entry_id,verse_position))");
    }
    private static void createCollectionBookTable(SQLiteDatabase db){
        db.execSQL("CREATE TABLE collection_book_chapters ("+
            "collection_id INTEGER NOT NULL REFERENCES unified_collections(id) ON DELETE CASCADE,"+
            "book INTEGER NOT NULL, chapter INTEGER NOT NULL,"+
            "entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE,"+
            "independent INTEGER NOT NULL, PRIMARY KEY(collection_id,book,chapter),"+
            "UNIQUE(collection_id,entry_id))");
    }
    /** The migration is atomic because SQLiteOpenHelper wraps onUpgrade in a transaction. */
    private static void migrateLegacyCollections(SQLiteDatabase db){
        try(Cursor c=db.rawQuery("SELECT id,title FROM groups_ WHERE title!='__slovo_cards_study_library__' ORDER BY id",null)){
            while(c.moveToNext()){
                long group=c.getLong(0);String title=c.getString(1);
                ContentValues deck=new ContentValues();deck.put("title",title);
                long deckId=db.insertOrThrow("card_decks",null,deck);
                ContentValues both=new ContentValues();both.put("title",title);both.put("group_id",group);
                both.put("deck_id",deckId);both.put("origin","study");
                db.insertOrThrow("unified_collections",null,both);
            }
        }
        try(Cursor c=db.rawQuery("SELECT id,title FROM card_decks WHERE id NOT IN (SELECT deck_id FROM unified_collections) ORDER BY id",null)){
            while(c.moveToNext()){
                long deck=c.getLong(0);String title=c.getString(1);
                ContentValues group=new ContentValues();group.put("title",title);
                long groupId=db.insertOrThrow("groups_",null,group);
                ContentValues both=new ContentValues();both.put("title",title);both.put("group_id",groupId);
                both.put("deck_id",deck);both.put("origin","cards");
                db.insertOrThrow("unified_collections",null,both);
            }
        }
    }
    public static final class UnifiedCollection {
        public long id,groupId,deckId;
        public String title,origin;
        public int entries,cards;
    }
    public List<UnifiedCollection> unifiedCollections(){
        List<UnifiedCollection> result=new ArrayList<>();
        try(Cursor c=readQuery(
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
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
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
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            ContentValues v=new ContentValues();v.put("title",name);
            db.update("unified_collections",v,"id=?",new String[]{String.valueOf(id)});
            db.update("groups_",v,"id=?",new String[]{String.valueOf(u.groupId)});
            db.update("card_decks",v,"id=?",new String[]{String.valueOf(u.deckId)});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    /** Hide a deleted collection without destroying its underlying entries or review history. */
    public void deleteUnifiedCollection(long id){
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            UnifiedCollection collection=unifiedCollection(id);
            if(collection!=null){
                markArchivedGroup(db,collection.groupId);
                db.delete("unified_collections","id=?",new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    private void markArchivedGroup(SQLiteDatabase db,long groupId){
        db.execSQL("UPDATE groups_ SET title=? || title WHERE id=?",
            new Object[]{ARCHIVED_GROUP_PREFIX,groupId});
    }

    private void archiveStudyGroup(SQLiteDatabase db,long groupId){
        ContentValues archived=new ContentValues();archived.put("group_id",libraryId(db));
        db.update("entries",archived,"group_id=?",new String[]{String.valueOf(groupId)});
    }
    /** Clear only a collection's membership. Preserve every entry row and all
     * progress/review tables by moving entries into the private study library.
     * Clear card memberships and per-collection cross-links in one transaction.
     * Does NOT delete shared verse records or their history. */
    public void clearUnifiedCollection(long id){
        UnifiedCollection u=unifiedCollection(id);
        if(u==null)return;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            String[] collection={String.valueOf(u.id)};
            String[] group={String.valueOf(u.groupId)};
            String[] deck={String.valueOf(u.deckId)};
            // Remove collection-specific order and mapped relationships first.
            db.delete("collection_book_chapters","collection_id=?",collection);
            db.delete("unified_entry_cards","collection_id=?",collection);
            db.delete("unified_card_entries","collection_id=?",collection);
            db.delete("study_order","group_id=?",group);
            db.delete("card_order","deck_id=?",deck);
            db.delete("card_deck_members","deck_id=?",deck);
            archiveStudyGroup(db,u.groupId);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public boolean containsEntry(long groupId,long entryId){
        try(Cursor c=readQuery("SELECT 1 FROM entries WHERE group_id=? AND id=? LIMIT 1",
            new String[]{String.valueOf(groupId),String.valueOf(entryId)})){return c.moveToFirst();}
    }
    public boolean belongsToGroupMaterial(long groupId,DueItem due){
        return reviewSchedule().belongs(groupId,due);
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
    /** Backfill both legacy kinds after the bundled Bible has loaded. Idempotent by mapping keys. */
    public void syncUnifiedCollection(long collectionId,Bible bible){
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransactionNonExclusive();
        try{
            syncUnifiedCollectionInTransaction(collectionId,bible);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private void syncUnifiedCollectionInTransaction(long collectionId,Bible bible){
        UnifiedCollection u=unifiedCollection(collectionId);
        if(u==null)return;
        Set<Long> mappedCards=new HashSet<>();
        Set<String> mappedVerses=new HashSet<>();
        try(Cursor c=readQuery("SELECT card_id FROM unified_card_entries WHERE collection_id=?",
            new String[]{String.valueOf(collectionId)})){while(c.moveToNext())mappedCards.add(c.getLong(0));}
        try(Cursor c=readQuery("SELECT x.entry_id,x.verse_position FROM unified_entry_cards x "+
            "JOIN card_deck_members m ON m.card_id=x.card_id AND m.deck_id=? WHERE x.collection_id=?",
            new String[]{String.valueOf(u.deckId),String.valueOf(collectionId)})){
            while(c.moveToNext())mappedVerses.add(c.getLong(0)+":"+c.getInt(1));}
        List<Entry> groupEntries=entries(u.groupId);
        List<Flashcard> deckCards=flashcards(u.deckId);
        // Legacy decks previously had no study entries. Preserve their original card records.
        for(Flashcard card:deckCards){
            if(mappedCards.contains(card.id))continue;
            List<Bible.Verse> verses;
            try{
                if(card.bookId>0){if(bible==null||!bible.isReady())continue;
                    verses=bible.passage(card.bookId,card.firstChapter,card.firstVerse,card.lastChapter,card.lastVerse);
                }else verses=Collections.singletonList(new Bible.Verse("1",card.content));
                long entryId=-1;
                for(Entry old:groupEntries){
                    if(old.reference.equals(card.reference)&&old.lines().size()==verses.size()){
                        boolean identical=true;List<Bible.Verse> prior=old.lines();
                        for(int i=0;i<prior.size();i++)if(!(prior.get(i).ref.equals(verses.get(i).ref)||
                            card.bookId==0&&"1:1".equals(prior.get(i).ref))||
                            !prior.get(i).text.equals(verses.get(i).text)){identical=false;break;}
                        if(identical){entryId=old.id;break;}
                    }
                }
                if(entryId<0){entryId=add(u.groupId,card.reference,card.reference,verses);groupEntries.add(entry(entryId));}
                mapCardEntry(collectionId,card.id,entryId);
                // A pre-existing single-verse/custom card already supplies this entry.
                // Do not manufacture a duplicate when syncing study entries below.
                if(verses.size()==1){mapEntryCard(collectionId,entryId,0,card.id);mappedVerses.add(entryId+":0");}
            }catch(Exception error){android.util.Log.e("LocalStore","Unable to synchronize legacy card "+card.id,error);}
        }
        // Study passages automatically supply one flashcard per canonical verse.
        for(Entry entry:groupEntries){
            int book=bibleBookFromReference(entry.reference);
            List<Bible.Verse> verses=entry.lines();
            for(int i=0;i<verses.size();i++){
                if(mappedVerses.contains(entry.id+":"+i))continue;
                Bible.Verse line=verses.get(i);
                int[] ref=verseNumbers(line.ref);
                long cardId=-1;
                if(book>0&&ref!=null){
                    addFlashcard(u.deckId,book,ref[0],ref[1],ref[0],ref[1],
                        Bible.reference(book,ref[0],ref[1],ref[0],ref[1]),line.text);
                    try(Cursor c=readQuery("SELECT id FROM flashcards WHERE book_id=? AND first_chapter=? AND first_verse=? AND last_chapter=? AND last_verse=?",
                        new String[]{String.valueOf(book),String.valueOf(ref[0]),String.valueOf(ref[1]),String.valueOf(ref[0]),String.valueOf(ref[1])})){
                        if(c.moveToFirst())cardId=c.getLong(0);
                    }
                }else{
                    cardId=addCustomFlashcard(u.deckId,entry.reference,line.text);
                }
                if(cardId>0){mapEntryCard(collectionId,entry.id,i,cardId);
                    // The generated card is already represented by its source entry.
                    mapCardEntry(collectionId,cardId,entry.id);
                }
            }
        }
    }
    private boolean mappedCardEntry(long collection,long card){
        try(Cursor c=readQuery("SELECT 1 FROM unified_card_entries WHERE collection_id=? AND card_id=? LIMIT 1",
            new String[]{String.valueOf(collection),String.valueOf(card)})){return c.moveToFirst();}
    }
    private boolean mappedEntryCard(long collection,long entry,int pos){
        try(Cursor c=readQuery(
            "SELECT 1 FROM unified_entry_cards x JOIN unified_collections u ON u.id=x.collection_id "+
            "JOIN card_deck_members m ON m.deck_id=u.deck_id AND m.card_id=x.card_id "+
            "WHERE x.collection_id=? AND x.entry_id=? AND x.verse_position=? LIMIT 1",
            new String[]{String.valueOf(collection),String.valueOf(entry),String.valueOf(pos)})){return c.moveToFirst();}
    }
    private void mapCardEntry(long collection,long card,long entry){
        ContentValues v=new ContentValues();v.put("collection_id",collection);v.put("card_id",card);v.put("entry_id",entry);
        getWritableDatabase().insertWithOnConflict("unified_card_entries",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    private void mapEntryCard(long collection,long entry,int pos,long card){
        ContentValues v=new ContentValues();v.put("collection_id",collection);v.put("entry_id",entry);
        v.put("verse_position",pos);v.put("card_id",card);
        getWritableDatabase().insertWithOnConflict("unified_entry_cards",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static final class CardDeck {
        public long id; public String title; public int count;
    }
    public static final class Flashcard {
        public long id; public String reference,content;
        public int bookId,firstChapter,firstVerse,lastChapter,lastVerse;
    }
    public long newCardDeck(String title){
        ContentValues v=new ContentValues();v.put("title",title.trim());
        return getWritableDatabase().insertOrThrow("card_decks",null,v);
    }
    public List<CardDeck> cardDecks(){
        List<CardDeck> list=new ArrayList<>();
        try(Cursor c=readQuery("SELECT d.id,d.title,COUNT(m.card_id) FROM card_decks d LEFT JOIN card_deck_members m ON m.deck_id=d.id GROUP BY d.id ORDER BY d.id",null)){
            while(c.moveToNext()){
                CardDeck d=new CardDeck();d.id=c.getLong(0);d.title=c.getString(1);d.count=c.getInt(2);list.add(d);
            }
        }
        return list;
    }
    public CardDeck cardDeck(long id){for(CardDeck d:cardDecks())if(d.id==id)return d;return null;}
    public void renameCardDeck(long id,String title){
        ContentValues v=new ContentValues();v.put("title",title.trim());
        getWritableDatabase().update("card_decks",v,"id=?",new String[]{String.valueOf(id)});
    }
    public void deleteCardDeck(long id){
        SQLiteDatabase db=getWritableDatabase();
        db.delete("card_decks","id=?",new String[]{String.valueOf(id)});
        db.execSQL("DELETE FROM flashcards WHERE NOT EXISTS (SELECT 1 FROM card_deck_members m WHERE m.card_id=flashcards.id)");
    }
    public boolean addFlashcard(long deckId,int bookId,int sc,int sv,int ec,int ev,String reference,String content){
        if(bookId<1||bookId>66||!PassageRangeLogic.valid(sc,sv,ec,ev)||content.trim().isEmpty())
            throw new IllegalArgumentException("Неверный библейский отрывок");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
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
    /** User-authored card: distinct key-space (book_id=0), no Bible passage lookup. */
    public long addCustomFlashcard(long deckId,String reference,String content){
        if(reference==null||reference.trim().isEmpty()||content==null||content.trim().isEmpty())
            throw new IllegalArgumentException("Укажи название и текст");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
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
        try(Cursor c=readQuery(
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
        SQLiteDatabase db=getWritableDatabase();
        db.delete("card_deck_members","deck_id=? AND card_id=?",new String[]{String.valueOf(deckId),String.valueOf(cardId)});
        db.execSQL("DELETE FROM flashcards WHERE id=? AND NOT EXISTS (SELECT 1 FROM card_deck_members WHERE card_id=?)",new Object[]{cardId,cardId});
    }

    /** Keep known weak transitions as independent diagnostic state. */
    private void recordWeakTransition(SQLiteDatabase db,long entryId,int from,int to,long now){
        if(from<0||to!=from+1)return;
        db.execSQL("INSERT OR IGNORE INTO transition_issues(entry_id,from_position,to_position) VALUES(?,?,?)",
            new Object[]{entryId,from,to});
        db.execSQL("UPDATE transition_issues SET failures=failures+1,last_failed=? WHERE entry_id=? AND from_position=? AND to_position=?",
            new Object[]{now,entryId,from,to});
    }
    public static final class DueItem {
        public long entryId, due;
        public String stage;public int start,end;
        public DueItem(long id,String stage,int start,int end,long due){
            this.entryId=id;this.stage=stage;this.start=start;this.end=end;this.due=due;
        }
    }
    public int exerciseDone(long id,int position) {
        return collectionProgress().exerciseDone(id,position);
    }
    public int unlockedPosition(long id,int count) {
        if(count==0)return 0;
        try(Cursor c=readQuery(
            "SELECT MAX(position) FROM lesson_progress WHERE entry_id=? AND completed>=5",
            new String[]{String.valueOf(id)})){
            if(c.moveToFirst() && !c.isNull(0))return Math.min(count-1,c.getInt(0)+1);
        }
        // Old mastered verses with sparse positions: preserve their accessibility too.
        try(Cursor c=readQuery("SELECT MAX(position) FROM verse_progress WHERE entry_id=?",
            new String[]{String.valueOf(id)})){
            if(c.moveToFirst() && !c.isNull(0))return Math.min(count-1,c.getInt(0)+1);
        }
        return 0;
    }
    public void finishExercise(long id,int position,int completed) {
        if(completed<1||completed>5)throw new IllegalArgumentException("Invalid lesson stage");
        finishExerciseIfNeeded(id,position,completed,exerciseDone(id,position));
    }
    private void finishExerciseIfNeeded(long id,int position,int completed,int old){
        if(completed>old){
            ContentValues cv=new ContentValues();cv.put("entry_id",id);cv.put("position",position);
            cv.put("completed",completed);
            getWritableDatabase().insertWithOnConflict("lesson_progress",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
        }
    }
    public void scheduleReview(long id,String stage,int start,int end,int accuracy,boolean assisted,long now) {
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            ReviewSchedule review=assisted?null:new ReviewSchedule(this,collectionProgress(),false);
            boolean mastered=accuracy!=100||assisted||review.mastered(id,stage,start,end);
            scheduleReviewKnownMastery(db,review,id,stage,start,end,accuracy,assisted,now,mastered);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private void scheduleReviewKnownMastery(SQLiteDatabase db,ReviewSchedule schedule,long id,String stage,int start,int end,
            int accuracy,boolean assisted,long now,boolean mastered){
        ContentValues a=new ContentValues();a.put("entry_id",id);a.put("stage",stage);
        a.put("first_line",start);a.put("last_line",end);
        a.put("attempted_at",now);a.put("accuracy",accuracy);a.put("assisted",assisted?1:0);
        db.insertOrThrow("review_attempts",null,a);
        if(!assisted)schedule.update(db,id,stage,start,end,accuracy,mastered,now);
    }

    ReviewSchedule reviewSchedule(){return reviewSchedule(null);}

    /** The effective term has the minimum due date across locations, including archived ones.
     * Future terms cannot win an overdue comparison. Resume, which can accept future terms
     * after a failed attempt, still requests all terms through the no-argument overload. */
    ReviewSchedule reviewSchedule(Long dueBy){
        SQLiteDatabase db=getReadableDatabase();
        boolean nested=db.inTransaction();
        db.beginTransactionNonExclusive();
        try{
            ReviewSchedule schedule=new ReviewSchedule(this,readProgressSnapshot(nested),true,dueBy);
            db.setTransactionSuccessful();return schedule;
        }finally{db.endTransaction();}
    }
    /** Counts due verse/group units before the exercise planner combines or suppresses overlaps. */
    public List<DueItem> dueItems(long now){return dueItems(now,-1);}
    List<DueItem> dueItems(long now,long group){return reviewSchedule(now).due(now,group);}
    public int dueCount(long now){return reviewSchedule(now).countDue(now);}
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to) {
        if(from<2) {
            createProgressTables(db);
            // Keep progress from v0.2.0: its 'learned' count was a contiguous prefix.
            try(Cursor c=db.rawQuery("SELECT id, learned FROM entries",null)) {
                while(c.moveToNext()) {
                    long id=c.getLong(0);int learned=c.getInt(1);
                    for(int pos=0;pos<learned;pos++) {
                        ContentValues v=new ContentValues();v.put("entry_id",id);
                        v.put("position",pos);v.put("best_score",90);
                        db.insertWithOnConflict("verse_progress",null,v,SQLiteDatabase.CONFLICT_IGNORE);
                    }
                }
            }
        }
        if(from<3) createRecallTables(db);
        if(from<4) {
            createLessonTables(db);
            // V0.5 progress is authoritative: never lock an already mastered verse.
            db.execSQL("INSERT OR IGNORE INTO lesson_progress (entry_id,position,completed) SELECT entry_id,position,5 FROM verse_progress");
            // Seed existing learned verses without inventing successful delayed reviews.
            db.execSQL("INSERT OR IGNORE INTO review_schedule (entry_id,stage,first_line,last_line,level,due,last_score) " +
                "SELECT v.entry_id,'VERSE',v.position,v.position+1,0, " +
                "COALESCE((SELECT CASE WHEN last_success>0 THEN last_success+86400000 ELSE NULL END FROM recall_activity r " +
                "WHERE r.entry_id=v.entry_id AND r.stage='VERSE' AND r.first_line=v.position AND r.last_line=v.position+1),0),v.best_score FROM verse_progress v");
        }
        if(from<5)createWeakTransitionsTable(db);
        if(from<6){
            createFlashcardTables(db);
            // Remove only the empty legacy default, never user material merely
            // because its collection happens to have the old default title.
            db.execSQL("DELETE FROM groups_ WHERE title='Мои стихи' AND "+
                "NOT EXISTS(SELECT 1 FROM entries e WHERE e.group_id=groups_.id)");
        }
        if(from<7)createOrderTables(db);
        if(from<8){createUnifiedTables(db);migrateLegacyCollections(db);}
        if(from<9)createCollectionBookTable(db);
    }
    public int masteredLinkedSegments(long entryId){
        try(Cursor c=readQuery(
            "SELECT COUNT(*) FROM stage_progress WHERE entry_id=? AND stage<>'VERSE'",
            new String[]{String.valueOf(entryId)})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    public int masteryCount(long entryId){return collectionProgress().masteryCount(entryId);}
    public int firstUnmastered(long entryId,int length){
        CollectionProgressSnapshot snapshot=collectionProgress();
        for(int i=0;i<length;i++)if(!snapshot.mastered(entryId,i))return i;
        return 0;
    }
    public boolean isStepMastered(long entryId,String stage,int start,int end){
        if("VERSE".equals(stage)&&end==start+1)return collectionProgress().mastered(entryId,start);
        try(Cursor c=readQuery(
            "SELECT 1 FROM stage_progress WHERE entry_id=? AND stage=? AND first_line=? AND last_line=? LIMIT 1",
            new String[]{String.valueOf(entryId),stage,String.valueOf(start),String.valueOf(end)})){
            return c.moveToFirst();
        }
    }
    public void markMastered(long entryId,int start,int end,int score,String stage) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(int i=start;"VERSE".equals(stage) && i<end;i++) {
                ContentValues cv=new ContentValues();cv.put("entry_id",entryId);
                cv.put("position",i);cv.put("best_score",score);
                db.insertWithOnConflict("verse_progress",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
            }
            ContentValues step=new ContentValues();step.put("entry_id",entryId);
            step.put("stage",stage);step.put("first_line",start);step.put("last_line",end);
            step.put("best_score",score);
            db.insertWithOnConflict("stage_progress",null,step,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally{db.endTransaction();}
    }
    /** One successful answer is committed as one unit. */
    public void commitSuccessfulRecall(long id,String stage,int start,int end,
            boolean verseRun,boolean finishLesson,long now){
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            // Mastery cannot change during this transaction except through the
            // explicit final write below. Read it once, not once for every verse.
            CollectionProgressSnapshot before=collectionProgress();
            ReviewSchedule review=new ReviewSchedule(this,before,false);
            if(verseRun){
                for(int i=start;i<end;i++){
                    recordRecall(db,id,"VERSE",i,i+1,100,false,now);
                    scheduleReviewKnownMastery(db,review,id,"VERSE",i,i+1,100,false,now,review.mastered(id,"VERSE",i,i+1));
                }
            }else{
                boolean mastered=review.mastered(id,stage,start,end);
                recordRecall(db,id,stage,start,end,100,false,now);
                scheduleReviewKnownMastery(db,review,id,stage,start,end,100,false,now,mastered);
                if(finishLesson)finishExerciseIfNeeded(id,start,5,before.exerciseDone(id,start));
                if(!mastered)markMastered(id,start,end,100,stage);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    /** Record a failed full answer without advancing mastery or lesson progress. */
    public void commitFailedRecall(long id,String stage,int start,int end,int accuracy,
            boolean schedule,WeakTransitionLogic.WeakSpot weak,long now){
        boolean verseRun="VERSE_RUN".equals(stage);
        int weakVerse=weak==null||weak.verseIndex<0?-1:start+weak.verseIndex;
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            boolean weakInRange=weakVerse>=start&&weakVerse<end;
            boolean shorten=weakInRange&&weak.errors>0;
            ReviewSchedule review=(schedule||weakInRange)?new ReviewSchedule(this,collectionProgress(),false):null;
            if(!verseRun)recordRecall(db,id,stage,start,end,accuracy,false,now);
            if(shorten){
                review.rescheduleWeakVerse(db,id,weakVerse,now);
                if(weak.transitionFrom>=0)
                    recordWeakTransition(db,id,start+weak.transitionFrom,weakVerse,now);
            }
            if(verseRun){
                // Only the weak verse receives a failed attempt. Other verses remain due.
                if(weakInRange){
                    recordRecall(db,id,"VERSE",weakVerse,weakVerse+1,0,false,now);
                    scheduleReviewKnownMastery(db,review,id,"VERSE",weakVerse,weakVerse+1,0,false,now,true);
                }
            }else if(schedule){
                scheduleReviewKnownMastery(db,review,id,stage,start,end,accuracy,false,now,true);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    /** Every typed attempt is retained locally, with delayed recall recorded separately. */
    public void recordRecall(long entryId,String stage,int start,int end,int accuracy,
                             boolean assisted,long now) {
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            recordRecall(db,entryId,stage,start,end,accuracy,assisted,now);
            db.setTransactionSuccessful();
        } finally{db.endTransaction();}
    }

    private void recordRecall(SQLiteDatabase db,long entryId,String stage,int start,int end,
                              int accuracy,boolean assisted,long now){
        long first=0,last=0;int successes=0,fails=0;
        try(Cursor c=db.rawQuery("SELECT first_success,last_success,success_count,fail_streak FROM recall_activity WHERE entry_id=? AND stage=? AND first_line=? AND last_line=?",
            new String[]{String.valueOf(entryId),stage,String.valueOf(start),String.valueOf(end)})) {
            if(c.moveToFirst()){first=c.getLong(0);last=c.getLong(1);successes=c.getInt(2);fails=c.getInt(3);}
        }
        if(accuracy==100 && !assisted) {
            if(first==0)first=now;
            // Repeating immediately is practice, not evidence of delayed retention.
            if(successes==0 || (last>0 && now-last>=24L*3600000L))successes++;
            last=now;fails=0;
        } else if(accuracy<90)fails++;
        ContentValues v=new ContentValues();v.put("entry_id",entryId);v.put("stage",stage);
        v.put("first_line",start);v.put("last_line",end);v.put("last_attempt",now);
        v.put("first_success",first);v.put("last_success",last);v.put("success_count",successes);
        v.put("fail_streak",fails);v.put("last_score",accuracy);
        db.replaceOrThrow("recall_activity",null,v);
    }
    public int recentFailures(long id,String stage,int start,int end) {
        try(Cursor c=readQuery(
            "SELECT fail_streak FROM recall_activity WHERE entry_id=? AND stage=? AND first_line=? AND last_line=?",
            new String[]{String.valueOf(id),stage,String.valueOf(start),String.valueOf(end)})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    /** A saved route must not replay an answer committed after Android saved the task. */
    String recallCheckpoint(long id,String stage,int start,int end){
        boolean run="VERSE_RUN".equals(stage);
        String bounds=run?"first_line>=? AND last_line<=? AND last_line=first_line+1":
            "first_line=? AND last_line=?";
        StringBuilder result=new StringBuilder();
        try(Cursor c=readQuery("SELECT first_line,last_attempt,last_success,last_score FROM recall_activity "+
                "WHERE entry_id=? AND stage=? AND "+bounds+" ORDER BY first_line",
                new String[]{String.valueOf(id),run?"VERSE":stage,String.valueOf(start),String.valueOf(end)})){
            while(c.moveToNext())result.append(c.getInt(0)).append(':').append(c.getLong(1))
                .append(':').append(c.getLong(2)).append(':').append(c.getInt(3)).append(';');
        }
        return result.toString();
    }
    private boolean consolidatedLocal(long id,String stage,int start,int end) {
        try(Cursor c=readQuery(
            "SELECT first_success,last_success,success_count,fail_streak FROM recall_activity WHERE entry_id=? AND stage=? AND first_line=? AND last_line=?",
            new String[]{String.valueOf(id),stage,String.valueOf(start),String.valueOf(end)})) {
            return c.moveToFirst() && c.getLong(0)>0 &&
                c.getLong(1)-c.getLong(0)>=24L*3600000L && c.getInt(2)>=2 && c.getInt(3)==0;
        }
    }
    public boolean consolidated(long id,String stage,int start,int end){
        if("VERSE".equals(stage)&&end==start+1)
            return collectionProgress().verseConsolidated(id,start);
        return consolidatedLocal(id,stage,start,end);
    }
    public boolean recallDue(long id,String stage,int start,int end,long now) {
        try(Cursor c=readQuery(
            "SELECT last_success,success_count FROM recall_activity WHERE entry_id=? AND stage=? AND first_line=? AND last_line=?",
            new String[]{String.valueOf(id),stage,String.valueOf(start),String.valueOf(end)})) {
            if(!c.moveToFirst() || c.getLong(0)<=0)return false;
            long days=c.getInt(1)>=2?3L:1L;
            return now>=c.getLong(0) && now-c.getLong(0)>=days*86400000L;
        }
    }
    public AdaptiveLearningLogic.Progress progressFor(final long id) {
        return new AdaptiveLearningLogic.Progress() {
            public boolean mastered(StudyPlanLogic.Stage stage,int start,int end) {
                return isStepMastered(id,stage.name(),start,end);
            }
            public boolean isDue(StudyPlanLogic.Stage stage,int start,int end,long now) {
                return recallDue(id,stage.name(),start,end,now);
            }
            public int recentFailures(StudyPlanLogic.Stage stage,int start,int end) {
                return LocalStore.this.recentFailures(id,stage.name(),start,end);
            }
        };
    }
    public long newGroup(String title) {
        ContentValues v=new ContentValues();v.put("title",title.trim());
        return getWritableDatabase().insertOrThrow("groups_",null,v);
    }
    public List<Group> groups() {
        List<Group> list=new ArrayList<>();
        try(Cursor c=readQuery("SELECT g.id,g.title,COUNT(e.id) FROM groups_ g LEFT JOIN entries e ON e.group_id=g.id WHERE g.title!=? AND (g.title NOT GLOB ? OR EXISTS(SELECT 1 FROM unified_collections u WHERE u.group_id=g.id)) GROUP BY g.id ORDER BY g.id",
            new String[]{CARD_STUDY_GROUP,ARCHIVED_GROUP_PREFIX+"*"})) {
            while(c.moveToNext()) { Group g=new Group();g.id=c.getLong(0);g.title=c.getString(1);g.count=c.getInt(2);list.add(g); }
        }
        return list;
    }
    public Group group(long id) {
        for(Group g:groups()) if(g.id==id) return g;
        return null;
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
        return getWritableDatabase().insertOrThrow("entries",null,cv);
    }
    /** Return a previously saved copy of this same Scripture passage when possible.
     * Shared progress must never be inferred from an unrelated passage or text variant.
     */
    public long canonicalStudyEntryId(long entryId){return collectionProgress().canonicalId(entryId);}
    private int completedExerciseCount(long id){
        try(Cursor c=readQuery(
            "SELECT COALESCE(SUM(completed),0) FROM lesson_progress WHERE entry_id=?",
            new String[]{String.valueOf(id)})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    /** Each card is an independent saved passage. Its own adjacent verses may link. */
    public long findOrCreateCardStudyEntry(Flashcard card,List<Bible.Verse> verses){
        if(card==null||verses==null||verses.isEmpty())throw new IllegalArgumentException("Пустая карточка");
        for(Entry old:allEntries()){
            if(!card.reference.equals(old.reference))continue;
            List<Bible.Verse> lines=old.lines();
            if(lines.size()!=verses.size())continue;
            boolean identical=true;
            for(int i=0;i<lines.size();i++){
                if(!(lines.get(i).ref.equals(verses.get(i).ref)||card.bookId==0&&"1:1".equals(lines.get(i).ref)) ||
                    !lines.get(i).text.equals(verses.get(i).text)){
                    identical=false;break;
                }
            }
            if(identical)return canonicalStudyEntryId(old.id);
        }
        SQLiteDatabase db=getWritableDatabase();long group=-1;
        try(Cursor c=db.rawQuery("SELECT id FROM groups_ WHERE title=? LIMIT 1",new String[]{CARD_STUDY_GROUP})){
            if(c.moveToFirst())group=c.getLong(0);
        }
        if(group<0)group=newGroup(CARD_STUDY_GROUP);
        return add(group,card.reference,card.reference,verses);
    }

    /** Move within the current collection only; original records and progress stay unchanged. */
    public void reorderStudy(long groupId,List<Long> ids){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
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
    public void reorderCards(long deckId,List<Long> ids){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
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
    private long libraryId(SQLiteDatabase db){
        try(Cursor c=db.rawQuery("SELECT id FROM groups_ WHERE title=? LIMIT 1",new String[]{CARD_STUDY_GROUP})){
            if(c.moveToFirst())return c.getLong(0);
        }
        ContentValues values=new ContentValues();values.put("title",CARD_STUDY_GROUP);
        return db.insertOrThrow("groups_",null,values);
    }
    /** Remove collection membership on both sides, preserving the study row in the hidden library. */
    public boolean removeUnifiedPassage(long groupId,String reference){
        UnifiedCollection u=unifiedForGroup(groupId);
        if(u==null)return detachStudyReference(groupId,reference);
        SQLiteDatabase db=getWritableDatabase();
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
    }
    public boolean detachStudyReference(long groupId,String reference){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            long library=libraryId(db);
            ContentValues values=new ContentValues();values.put("group_id",library);
            int changed=db.update("entries",values,"group_id=? AND reference=?",
                new String[]{String.valueOf(groupId),reference});
            db.setTransactionSuccessful();return changed>0;
        }finally{db.endTransaction();}
    }
    /** One transaction for the passage and its per-verse card memberships. */
    public long addPickedPassage(long groupId,String reference,List<Bible.Verse> verses,Bible bible){
        return addPickedPassage(groupId,reference,verses,bible,-1);
    }
    public long addPickedPassage(long groupId,String reference,List<Bible.Verse> verses,Bible bible,long after){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            UnifiedCollection collection=unifiedForGroup(groupId);
            if(collection==null)throw new IllegalStateException("Коллекция больше не существует");
            long id=restoreOrAddStudyPassage(groupId,reference,verses);
            db.execSQL("UPDATE collection_book_chapters SET independent=1 WHERE collection_id=? AND entry_id=?",
                new Object[]{collection.id,id});
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
                for(Entry item:entries(groupId))if(item.id!=id)ids.add(item.id);
                int index=ids.indexOf(after);ids.add(index<0?ids.size():index+1,id);
                reorderStudy(groupId,ids);
            }
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    public SortedMap<Integer,SortedMap<Integer,Long>> collectionBooks(long groupId){
        SortedMap<Integer,SortedMap<Integer,Long>> books=new TreeMap<>();
        try(Cursor c=readQuery("SELECT b.book,b.chapter,b.entry_id FROM collection_book_chapters b "+
            "JOIN unified_collections u ON u.id=b.collection_id WHERE u.group_id=?",
            new String[]{String.valueOf(groupId)})){
            while(c.moveToNext())
                books.computeIfAbsent(c.getInt(0),key->new TreeMap<>()).put(c.getInt(1),c.getLong(2));
        }
        return books;
    }

    /** Promote a generated chapter when an identical range is saved independently. */
    public boolean retainBookChapter(long groupId,long entryId){
        ContentValues values=new ContentValues();
        values.put("independent",1);
        return getWritableDatabase().update("collection_book_chapters",values,
            "entry_id=? AND independent=0 AND collection_id=(SELECT id FROM unified_collections WHERE group_id=?)",
            new String[]{String.valueOf(entryId),String.valueOf(groupId)})>0;
    }

    /** Each chapter stays a normal study entry. A full book owns only these extra memberships. */
    public void addBibleChapters(long groupId,int book,Collection<Integer> chapters,boolean wholeBook,Bible bible){
        int chapterCount=bible.chapterCount(book);
        if(chapterCount==0)throw new IllegalArgumentException("Книга не найдена");
        SortedSet<Integer> selected=new TreeSet<>(chapters);
        if(wholeBook)for(int chapter=1;chapter<=chapterCount;chapter++)selected.add(chapter);
        if(selected.isEmpty())return;
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransactionNonExclusive();
        try{
            UnifiedCollection collection=unifiedForGroup(groupId);
            if(collection==null)throw new IllegalStateException("Коллекция больше не существует");
            SortedMap<Integer,Long> owned=collectionBooks(groupId).getOrDefault(book,new TreeMap<>());
            if(wholeBook&&owned.size()==chapterCount){db.setTransactionSuccessful();return;}
            long archive=libraryId(db);
            Map<String,List<Entry>> candidates=new HashMap<>();
            String prefix=Bible.NAMES[book-1]+" ";
            try(Cursor c=readQuery("SELECT "+FIELDS+" FROM entries WHERE (group_id IN (?,?) OR group_id IN ("+
                "SELECT g.id FROM groups_ g WHERE g.title GLOB ? AND NOT EXISTS("+
                "SELECT 1 FROM unified_collections u WHERE u.group_id=g.id))) AND reference>=? AND reference<? ORDER BY CASE WHEN group_id=? THEN 0 ELSE 1 END,id",
                new String[]{String.valueOf(groupId),String.valueOf(archive),ARCHIVED_GROUP_PREFIX+"*",prefix,Bible.NAMES[book-1]+"!",String.valueOf(groupId)})){
                while(c.moveToNext()){
                    Entry entry=readEntry(c);
                    candidates.computeIfAbsent(entry.reference,key->new ArrayList<>()).add(entry);
                }
            }
            Map<Long,Long> cardIds=new HashMap<>();
            try(Cursor c=readQuery("SELECT first_chapter,first_verse,id FROM flashcards "+
                "WHERE book_id=? AND first_chapter=last_chapter AND first_verse=last_verse",
                new String[]{String.valueOf(book)})){
                while(c.moveToNext())cardIds.put(verseCoordinate(c.getInt(0),c.getInt(1)),c.getLong(2));
            }
            int nextOrder;
            try(Cursor c=readQuery("SELECT COALESCE(MAX(position),-1)+1 FROM study_order WHERE group_id=?",
                new String[]{String.valueOf(groupId)})){c.moveToFirst();nextOrder=c.getInt(0);}
            for(int chapter:selected){
                Long ownedEntry=owned.get(chapter);
                if(ownedEntry!=null){
                    if(!wholeBook)db.execSQL("UPDATE collection_book_chapters SET independent=1 "+
                        "WHERE collection_id=? AND book=? AND chapter=?",new Object[]{collection.id,book,chapter});
                    continue;
                }
                List<Bible.Verse> verses=bible.chapter(book,chapter);
                if(verses.isEmpty())throw new IllegalArgumentException("Глава не найдена");
                String reference=Bible.reference(book,chapter,1,chapter,verses.size());
                Entry match=null;
                for(Entry candidate:candidates.getOrDefault(reference,Collections.emptyList())){
                    if(sameVerses(candidate.lines(),verses)){match=candidate;break;}
                }
                boolean alreadyInGroup=match!=null&&match.groupId==groupId;
                long entryId;
                if(match==null)entryId=add(groupId,reference,reference,verses);
                else{
                    entryId=match.id;
                    if(!alreadyInGroup)db.execSQL("UPDATE entries SET group_id=? WHERE id=?",new Object[]{groupId,entryId});
                }
                if(!alreadyInGroup)db.execSQL("INSERT OR REPLACE INTO study_order(entry_id,group_id,position) VALUES(?,?,?)",
                    new Object[]{entryId,groupId,nextOrder++});
                if(wholeBook)db.execSQL("INSERT INTO collection_book_chapters VALUES(?,?,?,?,?)",
                    new Object[]{collection.id,book,chapter,entryId,alreadyInGroup?1:0});
                linkBulkChapter(db,collection,book,chapter,entryId,verses,cardIds);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    private static boolean sameVerses(List<Bible.Verse> first,List<Bible.Verse> second){
        if(first.size()!=second.size())return false;
        for(int i=0;i<first.size();i++)
            if(!first.get(i).ref.equals(second.get(i).ref)||!first.get(i).text.equals(second.get(i).text))return false;
        return true;
    }

    private static long verseCoordinate(int chapter,int verse){
        return ((long)chapter<<32)|verse;
    }

    private void linkBulkChapter(SQLiteDatabase db,UnifiedCollection collection,int book,int chapter,
            long entryId,List<Bible.Verse> verses,Map<Long,Long> cardIds){
        for(int i=0;i<verses.size();i++){
            Bible.Verse verse=verses.get(i);
            int number=Integer.parseInt(verse.ref.substring(verse.ref.indexOf(':')+1));
            long coordinate=verseCoordinate(chapter,number);
            Long cardId=cardIds.get(coordinate);
            if(cardId==null){
                ContentValues card=new ContentValues();
                card.put("book_id",book);card.put("first_chapter",chapter);card.put("last_chapter",chapter);
                card.put("first_verse",number);card.put("last_verse",number);
                card.put("reference",Bible.reference(book,chapter,number,chapter,number));card.put("content",verse.text);
                cardId=db.insertOrThrow("flashcards",null,card);
                cardIds.put(coordinate,cardId);
            }
            db.execSQL("INSERT OR IGNORE INTO card_deck_members(deck_id,card_id) VALUES(?,?)",
                new Object[]{collection.deckId,cardId});
            db.execSQL("INSERT OR REPLACE INTO unified_entry_cards(collection_id,entry_id,verse_position,card_id) VALUES(?,?,?,?)",
                new Object[]{collection.id,entryId,i,cardId});
            db.execSQL("INSERT OR IGNORE INTO unified_card_entries(collection_id,card_id,entry_id) VALUES(?,?,?)",
                new Object[]{collection.id,cardId,entryId});
        }
    }

    public void removeBibleBook(long groupId,int book){
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransactionNonExclusive();
        try{
            UnifiedCollection collection=unifiedForGroup(groupId);
            if(collection==null)throw new IllegalStateException("Коллекция больше не существует");
            String detached="SELECT entry_id FROM collection_book_chapters "+
                "WHERE collection_id=? AND book=? AND independent=0";
            Object[] owner={collection.id,book};
            db.execSQL("UPDATE entries SET group_id=? WHERE group_id=? AND id IN ("+detached+")",
                new Object[]{libraryId(db),groupId,collection.id,book});
            db.execSQL("DELETE FROM study_order WHERE entry_id IN ("+detached+")",owner);
            db.execSQL("DELETE FROM card_deck_members WHERE deck_id=? AND card_id IN ("+
                "SELECT card_id FROM unified_entry_cards WHERE collection_id=? AND entry_id IN ("+detached+")) "+
                "AND card_id NOT IN (SELECT x.card_id FROM unified_entry_cards x JOIN entries e ON e.id=x.entry_id "+
                "WHERE x.collection_id=? AND e.group_id=?)",
                new Object[]{collection.deckId,collection.id,collection.id,book,collection.id,groupId});
            db.execSQL("DELETE FROM unified_entry_cards WHERE collection_id=? AND entry_id IN ("+detached+")",
                new Object[]{collection.id,collection.id,book});
            db.execSQL("DELETE FROM unified_card_entries WHERE collection_id=? AND entry_id IN ("+detached+") "+
                "AND card_id NOT IN (SELECT card_id FROM card_deck_members WHERE deck_id=?)",
                new Object[]{collection.id,collection.id,book,collection.deckId});
            db.execSQL("DELETE FROM collection_book_chapters WHERE collection_id=? AND book=?",owner);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    /** Moving the original row back retains all FK-linked lesson/recall history. */
    public long restoreOrAddStudyPassage(long groupId,String reference,List<Bible.Verse> verses){
        if(verses==null||verses.isEmpty())throw new IllegalArgumentException("Пустой отрывок");
        for(Entry old:allEntries()){
            if(!old.reference.equals(reference))continue;
            if(old.groupId==groupId)return old.id;
            Group existing=group(old.groupId);
            if(existing!=null && !CARD_STUDY_GROUP.equals(existing.title))continue;
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
                getWritableDatabase().update("entries",values,"id=?",new String[]{String.valueOf(old.id)});
                return old.id;
            }
        }
        return add(groupId,reference,reference,verses);
    }
    /** Preserve unchanged verses and ranges, remapping their indices on any edit. */
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
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
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

    private Entry readEntry(Cursor c) {
        Entry e=new Entry();e.id=c.getLong(0);e.groupId=c.getLong(1);
        e.title=c.getString(2);e.reference=c.getString(3);e.linesJson=c.getString(4);
        e.learned=c.getInt(5);e.repetitions=c.getInt(6);e.days=c.getInt(7);
        e.ease=c.getDouble(8);e.due=c.getLong(9);e.lastScore=c.getInt(10);
        return e;
    }
    private static final String FIELDS="id,group_id,title,reference,lines,learned,repetitions,days,ease,due,last_score";
    public Entry entry(long id) {
        try(Cursor c=readQuery("SELECT "+FIELDS+" FROM entries WHERE id=?",new String[]{String.valueOf(id)})) {
            return c.moveToFirst()?readEntry(c):null;
        }
    }
    public List<Entry> entries(long groupId) {
        List<Entry> out=new ArrayList<>();
        try(Cursor c=readQuery("SELECT entries.id,entries.group_id,entries.title,entries.reference,entries.lines,entries.learned,entries.repetitions,entries.days,entries.ease,entries.due,entries.last_score FROM entries LEFT JOIN study_order o ON o.entry_id=entries.id WHERE entries.group_id=? ORDER BY COALESCE(o.position,1000000),entries.id DESC",new String[]{String.valueOf(groupId)})) {
            while(c.moveToNext()) out.add(readEntry(c));
        }
        return out;
    }
    public List<Entry> allEntries() {
        List<Entry> out=new ArrayList<>();
        try(Cursor c=readQuery("SELECT "+FIELDS+" FROM entries ORDER BY id DESC",null)) {
            while(c.moveToNext()) out.add(readEntry(c));
        }
        return out;
    }
    /** Independent schedule, not the continuation of a lesson. */
    public List<Entry> due() {
        List<Entry> result=new ArrayList<>();Set<Long> seen=new HashSet<>();
        for(DueItem d:dueItems(System.currentTimeMillis()))if(seen.add(d.entryId)){
            Entry e=entry(d.entryId);if(e!=null)result.add(e);
        }
        return result;
    }
    public void update(Entry e,ReviewLogic.Schedule schedule,int learned,int score) {
        ContentValues v=new ContentValues();v.put("learned",learned);
        v.put("repetitions",schedule.repetitions);v.put("days",schedule.intervalDays);
        v.put("ease",schedule.ease);v.put("due",schedule.nextDue);
        v.put("last_score",score);
        getWritableDatabase().update("entries",v,"id=?",new String[]{String.valueOf(e.id)});
    }
    public int consolidatedVerseCount(long entryId){return collectionProgress().consolidatedCount(entryId);}
    /** Cross-collection verse state. Reading alone never marks a verse learned. */
    public static final class VerseStatus {
        public String state="Не изучен";
        public int repetitions=0,successful=0;
        public long last=0,nextDue=0;
        public long entryId=-1;
        public int position=-1;
        public int rank=0;
    }
    public Map<Integer,VerseStatus> statusesForChapter(String bookName,int chapter) {
        SQLiteDatabase db=getReadableDatabase();
        boolean nested=db.inTransaction();
        db.beginTransactionNonExclusive();
        try{
            CollectionProgressSnapshot snapshot=readProgressSnapshot(nested);
            List<CollectionProgressSnapshot.ChapterLocation> locations=snapshot.chapterLocations(bookName,chapter);
            Map<Integer,VerseStatus> result=new HashMap<>();
            // Three bound variables per location, below the older SQLite limit of 999.
            int batchSize=300;
            for(int first=0;first<locations.size();first+=batchSize){
                loadChapterStats(snapshot,locations.subList(first,Math.min(first+batchSize,locations.size())),result);
            }
            db.setTransactionSuccessful();
            return result;
        }finally{db.endTransaction();}
    }

    private void loadChapterStats(CollectionProgressSnapshot snapshot,
            List<CollectionProgressSnapshot.ChapterLocation> locations,Map<Integer,VerseStatus> result){
        String[] args=new String[locations.size()*3];
        for(int i=0;i<locations.size();i++){
            CollectionProgressSnapshot.ChapterLocation location=locations.get(i);
            args[i*3]=Long.toString(location.entryId());
            args[i*3+1]=Integer.toString(location.position());
            args[i*3+2]=Integer.toString(location.number());
        }
        String rows=String.join(",",Collections.nCopies(locations.size(),"(CAST(? AS INTEGER),CAST(? AS INTEGER),CAST(? AS INTEGER))"));
        // Aggregate only the matching verse positions in SQLite. History rows never reach Java.
        try(Cursor c=readQuery("WITH wanted(entry_id,pos,number) AS (VALUES "+rows+") "+
                "SELECT w.entry_id,w.pos,w.number,COALESCE(r.last_success,0),COALESCE(s.due,0),"+
                "COUNT(DISTINCT a.attempted_at / 86400000),"+
                "COUNT(DISTINCT CASE WHEN a.accuracy=100 THEN a.attempted_at / 86400000 END) "+
                "FROM wanted w LEFT JOIN recall_activity r "+
                "ON r.entry_id=w.entry_id AND r.stage='VERSE' AND r.first_line=w.pos AND r.last_line=w.pos+1 "+
                "LEFT JOIN review_schedule s "+
                "ON s.entry_id=w.entry_id AND s.stage='VERSE' AND s.first_line=w.pos AND s.last_line=w.pos+1 "+
                "LEFT JOIN review_attempts a "+
                "ON a.entry_id=w.entry_id AND a.stage='VERSE' AND a.first_line=w.pos AND a.last_line=w.pos+1 "+
                "AND a.assisted=0 AND r.first_success>0 AND a.attempted_at>=r.first_success+86400000 "+
                "GROUP BY w.entry_id,w.pos,w.number ORDER BY w.entry_id DESC,w.pos",args)){
            while(c.moveToNext()){
                long id=c.getLong(0);
                int position=c.getInt(1),number=c.getInt(2);
                int completed=snapshot.exerciseDone(id,position);
                int rank=snapshot.verseConsolidated(id,position)?3:
                    snapshot.mastered(id,position)||completed>=5?2:completed>0?1:0;
                int repetitions=c.getInt(5);
                VerseStatus old=result.get(number);
                if(old!=null&&(rank<old.rank||rank==old.rank&&repetitions<=old.repetitions))continue;
                VerseStatus candidate=new VerseStatus();
                candidate.entryId=id;candidate.position=position;candidate.rank=rank;
                candidate.state=rank==3?"Выучен":rank==2?"Первоначально освоен":rank==1?"В процессе":"Не изучен";
                candidate.last=c.getLong(3);candidate.nextDue=c.getLong(4);
                candidate.repetitions=repetitions;candidate.successful=c.getInt(6);
                result.put(number,candidate);
            }
        }
    }
    public void renameGroup(long id,String name) {
        ContentValues v=new ContentValues();v.put("title",name.trim());
        getWritableDatabase().update("groups_",v,"id=?",new String[]{String.valueOf(id)});
    }
    public void renameEntry(long id,String name) {
        ContentValues v=new ContentValues();v.put("title",name.trim());
        getWritableDatabase().update("entries",v,"id=?",new String[]{String.valueOf(id)});
    }
    public void deleteEntry(long id) {getWritableDatabase().delete("entries","id=?",new String[]{String.valueOf(id)});}
    public void deleteGroup(long id){
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            UnifiedCollection collection=unifiedForGroup(id);
            if(collection!=null)deleteUnifiedCollection(collection.id);
            else if(group(id)!=null)markArchivedGroup(db,id);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    private List<Group> groupsForBackup(){
        List<Group> list=new ArrayList<>();
        try(Cursor c=readQuery(
            "SELECT g.id,g.title,COUNT(e.id) FROM groups_ g LEFT JOIN entries e ON e.group_id=g.id GROUP BY g.id ORDER BY g.id",null)){
            while(c.moveToNext()){
                Group g=new Group();g.id=c.getLong(0);g.title=c.getString(1);g.count=c.getInt(2);list.add(g);
            }
        }
        return list;
    }
    public JSONObject exportJson() throws JSONException {
        JSONObject top=new JSONObject().put("format","slovo-pamyat-v5");
        JSONArray groups=new JSONArray();
        for(Group group:groupsForBackup()) {
            JSONObject g=new JSONObject().put("title",group.title);
            JSONArray entries=new JSONArray();
            for(Entry e:entries(group.id)) {
                JSONObject item=new JSONObject()
                    .put("title",e.title).put("reference",e.reference).put("lines",new JSONArray(e.linesJson))
                    .put("learned",e.learned).put("repetitions",e.repetitions)
                    .put("days",e.days).put("ease",e.ease).put("due",e.due).put("last_score",e.lastScore);
                JSONArray mastered=new JSONArray();
                try(Cursor c=readQuery(
                    "SELECT position FROM verse_progress WHERE entry_id=? ORDER BY position",
                    new String[]{String.valueOf(e.id)})) {
                    while(c.moveToNext())mastered.put(c.getInt(0));
                }
                JSONArray stages=new JSONArray();
                try(Cursor c=readQuery(
                    "SELECT stage,first_line,last_line,best_score FROM stage_progress WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})) {
                    while(c.moveToNext())stages.put(new JSONObject()
                        .put("stage",c.getString(0)).put("start",c.getInt(1))
                        .put("end",c.getInt(2)).put("score",c.getInt(3)));
                }
                JSONArray recalls=new JSONArray();
                try(Cursor c=readQuery(
                    "SELECT stage,first_line,last_line,last_attempt,first_success,last_success,success_count,fail_streak,last_score FROM recall_activity WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})) {
                    while(c.moveToNext())recalls.put(new JSONObject()
                        .put("stage",c.getString(0)).put("start",c.getInt(1))
                        .put("end",c.getInt(2)).put("last_attempt",c.getLong(3))
                        .put("first_success",c.getLong(4)).put("last_success",c.getLong(5))
                        .put("success_count",c.getInt(6)).put("fail_streak",c.getInt(7))
                        .put("last_score",c.getInt(8)));
                }
                JSONArray lessons=new JSONArray();
                try(Cursor c=readQuery(
                    "SELECT position,completed FROM lesson_progress WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})){
                    while(c.moveToNext()) lessons.put(new JSONObject().put("position",c.getInt(0)).put("completed",c.getInt(1)));
                }
                JSONArray schedules=new JSONArray();
                try(Cursor c=readQuery(
                    "SELECT stage,first_line,last_line,level,due,last_score FROM review_schedule WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})){
                    while(c.moveToNext())schedules.put(new JSONObject().put("stage",c.getString(0))
                        .put("start",c.getInt(1)).put("end",c.getInt(2)).put("level",c.getInt(3))
                        .put("due",c.getLong(4)).put("score",c.getInt(5)));
                }
                JSONArray attempts=new JSONArray();
                try(Cursor c=readQuery(
                    "SELECT stage,first_line,last_line,attempted_at,accuracy,assisted FROM review_attempts WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})){
                    while(c.moveToNext())attempts.put(new JSONObject().put("stage",c.getString(0))
                        .put("start",c.getInt(1)).put("end",c.getInt(2)).put("at",c.getLong(3))
                        .put("score",c.getInt(4)).put("assisted",c.getInt(5)));
                }
                JSONArray transitions=new JSONArray();
                try(Cursor c=readQuery(
                    "SELECT from_position,to_position,failures,last_failed FROM transition_issues WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})){
                    while(c.moveToNext())transitions.put(new JSONObject()
                        .put("from",c.getInt(0)).put("to",c.getInt(1))
                        .put("failures",c.getInt(2)).put("last_failed",c.getLong(3)));
                }
                entries.put(item.put("mastered_positions",mastered).put("stage_results",stages)
                    .put("recall_activity",recalls).put("lesson_progress",lessons)
                    .put("review_schedule",schedules).put("review_attempts",attempts)
                    .put("transition_issues",transitions));
            }
            groups.put(g.put("entries",entries));
        }
        return top.put("groups",groups);
    }
    public void importJson(String json) throws JSONException {
        JSONObject top=new JSONObject(json);
        boolean v5="slovo-pamyat-v5".equals(top.optString("format"));
        boolean v4=v5 || "slovo-pamyat-v4".equals(top.optString("format"));
        boolean v3=v4 || "slovo-pamyat-v3".equals(top.optString("format"));
        boolean modern=v3 || "slovo-pamyat-v2".equals(top.optString("format"));
        if(!modern && !"slovo-pamyat-v1".equals(top.optString("format")))
            throw new JSONException("Неверный формат копии");
        JSONArray groups=top.getJSONArray("groups");
        if(groups.length()>1000) throw new JSONException("Слишком много подборок");
        // Validate the complete structure before making any changes; imports APPEND data.
        int count=0;
        for(int i=0;i<groups.length();i++) {
            JSONObject group=groups.getJSONObject(i); group.getString("title");
            JSONArray items=group.getJSONArray("entries");
            count+=items.length();
            if(count>10000) throw new JSONException("Слишком много отрывков");
            for(int j=0;j<items.length();j++) {
                JSONObject e=items.getJSONObject(j);
                e.getString("title");e.getString("reference");
                JSONArray vs=e.getJSONArray("lines");
                if(vs.length()==0 || vs.length()>500) throw new JSONException("Неверная длина отрывка");
                for(int k=0;k<vs.length();k++) {vs.getJSONObject(k).getString("text");vs.getJSONObject(k).getString("ref");}
                if(modern) {
                    JSONArray positions=e.getJSONArray("mastered_positions");
                    boolean[] taken=new boolean[vs.length()];
                    if(positions.length()>vs.length())throw new JSONException("Повторены освоенные позиции");
                    for(int k=0;k<positions.length();k++) {
                        int pos=positions.getInt(k);
                        if(pos<0||pos>=vs.length()||taken[pos])throw new JSONException("Неверные освоенные позиции");
                        taken[pos]=true;
                    }
                    JSONArray stages=e.getJSONArray("stage_results");
                    if(stages.length()>10000)throw new JSONException("Слишком много отметок этапов");
                    for(int k=0;k<stages.length();k++) {
                        JSONObject st=stages.getJSONObject(k);
                        String stage=st.getString("stage");
                        int a=st.getInt("start"),b=st.getInt("end");
                        if(!stage.equals("VERSE")&&!stage.equals("CHAIN")&&
                          !stage.equals("SECTION")&&!stage.equals("CHAPTER"))
                            throw new JSONException("Неизвестный этап");
                        if(a<0||b<=a||b>vs.length())throw new JSONException("Неверные границы этапа");
                    }
                }
                if(v4){
                    for(int m=0;m<e.getJSONArray("lesson_progress").length();m++){
                        JSONObject l=e.getJSONArray("lesson_progress").getJSONObject(m);
                        if(l.getInt("position")<0||l.getInt("position")>=vs.length() ||
                           l.getInt("completed")<0||l.getInt("completed")>5)throw new JSONException("Неверный этап урока");
                    }
                    for(int m=0;m<e.getJSONArray("review_schedule").length();m++){
                        JSONObject r=e.getJSONArray("review_schedule").getJSONObject(m);
                        if(r.getInt("start")<0||r.getInt("end")>vs.length()||r.getInt("start")>=r.getInt("end")||
                           r.getInt("level")<0||r.getInt("level")>=SpacedReviewLogic.DAYS.length)
                            throw new JSONException("Неверный интервал");
                    }
                }
                if(v5){
                    JSONArray weak=e.getJSONArray("transition_issues");
                    if(weak.length()>10000)throw new JSONException("Слишком много слабых переходов");
                    for(int k=0;k<weak.length();k++){
                        JSONObject w=weak.getJSONObject(k);
                        int from=w.getInt("from"),to=w.getInt("to");
                        if(from<0||to!=from+1||to>=vs.length()||w.getInt("failures")<0)
                            throw new JSONException("Неверные границы слабого перехода");
                    }
                }
                if(v3){
                    JSONArray recalls=e.getJSONArray("recall_activity");
                    if(recalls.length()>10000)throw new JSONException("Слишком много попыток");
                    for(int k=0;k<recalls.length();k++){
                        JSONObject r=recalls.getJSONObject(k);
                        String stage=r.getString("stage");
                        int a=r.getInt("start"),b=r.getInt("end");
                        if(!(stage.equals("VERSE")||stage.equals("CHAIN")||stage.equals("SECTION")||stage.equals("CHAPTER")) || a<0 || b<=a || b>vs.length())
                            throw new JSONException("Неверная запись повторения");
                    }
                }
            }
        }
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(int i=0;i<groups.length();i++) {
                JSONObject g=groups.getJSONObject(i);
                ContentValues gv=new ContentValues();gv.put("title",g.getString("title"));
                long id=db.insertOrThrow("groups_",null,gv);
                JSONArray items=g.getJSONArray("entries");
                for(int j=0;j<items.length();j++) {
                    JSONObject e=items.getJSONObject(j);ContentValues cv=new ContentValues();
                    JSONArray lines=e.getJSONArray("lines");
                    cv.put("group_id",id);cv.put("title",e.getString("title"));
                    cv.put("reference",e.getString("reference"));cv.put("lines",lines.toString());
                    cv.put("learned",modern?e.getJSONArray("mastered_positions").length():
                        Math.max(0,Math.min(lines.length(),e.optInt("learned",0))));
                    cv.put("repetitions",Math.max(0,e.optInt("repetitions",0)));
                    cv.put("days",Math.max(0,e.optInt("days",0)));
                    cv.put("ease",Math.max(1.3,e.optDouble("ease",2.5)));
                    cv.put("due",Math.max(0,e.optLong("due",0)));
                    cv.put("last_score",e.optInt("last_score",-1));
                    long importedId=db.insertOrThrow("entries",null,cv);
                    JSONArray positions=modern?e.getJSONArray("mastered_positions"):null;
                    int mastered=modern?positions.length():cv.getAsInteger("learned");
                    for(int k=0;k<mastered;k++) {
                        ContentValues pv=new ContentValues();pv.put("entry_id",importedId);
                        pv.put("position",modern?positions.getInt(k):k);pv.put("best_score",90);
                        db.insertOrThrow("verse_progress",null,pv);
                    }
                    if(modern) {
                        JSONArray stages=e.getJSONArray("stage_results");
                        for(int k=0;k<stages.length();k++) {
                            JSONObject st=stages.getJSONObject(k);ContentValues pv=new ContentValues();
                            pv.put("entry_id",importedId);pv.put("stage",st.getString("stage"));
                            pv.put("first_line",st.getInt("start"));pv.put("last_line",st.getInt("end"));
                            pv.put("best_score",st.optInt("score",90));
                            db.insertWithOnConflict("stage_progress",null,pv,SQLiteDatabase.CONFLICT_REPLACE);
                        }
                    }
                    if(v3){
                        JSONArray recalls=e.getJSONArray("recall_activity");
                        for(int k=0;k<recalls.length();k++){
                            JSONObject r=recalls.getJSONObject(k);ContentValues rv=new ContentValues();
                            rv.put("entry_id",importedId);rv.put("stage",r.getString("stage"));
                            rv.put("first_line",r.getInt("start"));rv.put("last_line",r.getInt("end"));
                            rv.put("last_attempt",Math.max(0,r.optLong("last_attempt",0)));
                            rv.put("first_success",Math.max(0,r.optLong("first_success",0)));
                            rv.put("last_success",Math.max(0,r.optLong("last_success",0)));
                            rv.put("success_count",Math.max(0,r.optInt("success_count",0)));
                            rv.put("fail_streak",Math.max(0,r.optInt("fail_streak",0)));
                            rv.put("last_score",r.optInt("last_score",-1));
                            db.insertWithOnConflict("recall_activity",null,rv,SQLiteDatabase.CONFLICT_REPLACE);
                        }
                    }
                    if(v4){
                        JSONArray lessons=e.getJSONArray("lesson_progress");
                        for(int k=0;k<lessons.length();k++){
                            JSONObject l=lessons.getJSONObject(k);ContentValues cvl=new ContentValues();
                            cvl.put("entry_id",importedId);cvl.put("position",l.getInt("position"));
                            cvl.put("completed",l.getInt("completed"));
                            db.insertWithOnConflict("lesson_progress",null,cvl,SQLiteDatabase.CONFLICT_REPLACE);
                        }
                        JSONArray schedules=e.getJSONArray("review_schedule");
                        for(int k=0;k<schedules.length();k++){
                            JSONObject r=schedules.getJSONObject(k);ContentValues rv=new ContentValues();
                            rv.put("entry_id",importedId);rv.put("stage",r.getString("stage"));
                            rv.put("first_line",r.getInt("start"));rv.put("last_line",r.getInt("end"));
                            rv.put("level",r.getInt("level"));rv.put("due",r.getLong("due"));
                            rv.put("last_score",r.optInt("score",-1));
                            db.insertWithOnConflict("review_schedule",null,rv,SQLiteDatabase.CONFLICT_REPLACE);
                        }
                        JSONArray attempts=e.getJSONArray("review_attempts");
                        for(int k=0;k<attempts.length();k++){
                            JSONObject r=attempts.getJSONObject(k);ContentValues rv=new ContentValues();
                            rv.put("entry_id",importedId);rv.put("stage",r.getString("stage"));
                            rv.put("first_line",r.getInt("start"));rv.put("last_line",r.getInt("end"));
                            rv.put("attempted_at",Math.max(0,r.optLong("at",0)));
                            rv.put("accuracy",r.getInt("score"));rv.put("assisted",r.optInt("assisted",0));
                            db.insertOrThrow("review_attempts",null,rv);
                        }
                    } else {
                        // Import from older versions: keep their mastered verses accessible.
                        db.execSQL("INSERT OR IGNORE INTO lesson_progress (entry_id,position,completed) " +
                            "SELECT entry_id,position,5 FROM verse_progress WHERE entry_id=?",
                            new Object[]{importedId});
                        db.execSQL("INSERT OR IGNORE INTO review_schedule (entry_id,stage,first_line,last_line,level,due,last_score) " +
                            "SELECT entry_id,'VERSE',position,position+1,0,0,best_score FROM verse_progress WHERE entry_id=?",
                            new Object[]{importedId});
                    }
                    if(v5){
                        JSONArray weak=e.getJSONArray("transition_issues");
                        for(int k=0;k<weak.length();k++){
                            JSONObject w=weak.getJSONObject(k);ContentValues vv=new ContentValues();
                            vv.put("entry_id",importedId);vv.put("from_position",w.getInt("from"));
                            vv.put("to_position",w.getInt("to"));
                            vv.put("failures",w.getInt("failures"));
                            vv.put("last_failed",Math.max(0,w.optLong("last_failed",0)));
                            db.insertWithOnConflict("transition_issues",null,vv,SQLiteDatabase.CONFLICT_REPLACE);
                        }
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
