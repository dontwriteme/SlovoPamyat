package org.biblememory.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Owns the existing on-device database, schema and repository scope.
 * Domain SQL lives in the typed repositories; legacy methods delegate for compatibility.
 * Nested model names remain stable for existing callers and saved-data tooling. */
public final class LocalStore extends SQLiteOpenHelper {
    private final RepositoryScope scope=new RepositoryScope(this);
    RepositoryScope repositoryScope(){return scope;}
    public StudyRepository study(){return scope.study;}
    public ProgressRepository progress(){return scope.progress;}
    public FlashcardRepository flashcards(){return scope.flashcards;}
    public CollectionRepository collections(){return scope.collections;}
    public LibraryBackup backup(){return scope.backup;}
    static final String CARD_STUDY_GROUP="__slovo_cards_study_library__";
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
    public LocalStore(Context context) { this(context,null); }
    LocalStore(Context context,SQLiteDatabase.CursorFactory cursorFactory) { super(context,"slovo_pamyat.db",cursorFactory,8); }
    @Override public void onConfigure(SQLiteDatabase db){ db.setForeignKeyConstraintsEnabled(true); }
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
    public List<UnifiedCollection> unifiedCollections(){return scope.collections.unifiedCollections();}
    public UnifiedCollection unifiedCollection(long id){return scope.collections.unifiedCollection(id);}
    public UnifiedCollection unifiedForGroup(long groupId){return scope.collections.unifiedForGroup(groupId);}
    public long createUnifiedCollection(String title){return scope.collections.createUnifiedCollection(title);}
    public void renameUnifiedCollection(long id,String title){scope.collections.renameUnifiedCollection(id,title);}
    /** Hide a deleted collection without destroying its underlying entries or review history. */
    public void deleteUnifiedCollection(long id){scope.collections.deleteUnifiedCollection(id);}
    /** Clear only a collection's membership. Preserve every entry row and all
     * progress/review tables by moving entries into the private study library.
     * Clear card memberships and per-collection cross-links in one transaction.
     * Does NOT delete shared verse records or their history. */
    public void clearUnifiedCollection(long id){scope.collections.clearUnifiedCollection(id);}
    public boolean containsEntry(long groupId,long entryId){return scope.study.containsEntry(groupId,entryId);}
    /** A due verse can belong to this collection through an identical Bible passage
     * even when its recall history lives on a different old study-entry row. */
    public boolean belongsToGroupMaterial(long groupId,DueItem due){return scope.collections.belongsToGroupMaterial(groupId,due);}

    /** Backfill both legacy kinds after the bundled Bible has loaded. Idempotent by mapping keys. */
    public void syncUnifiedCollection(long collectionId,Bible bible){scope.collections.syncUnifiedCollection(collectionId,bible);}

    public static final class CardDeck {
        public long id; public String title; public int count;
    }
    public static final class Flashcard {
        public long id; public String reference,content;
        public int bookId,firstChapter,firstVerse,lastChapter,lastVerse;
    }
    public long newCardDeck(String title){return scope.flashcards.newCardDeck(title);}
    public List<CardDeck> cardDecks(){return scope.flashcards.cardDecks();}
    public CardDeck cardDeck(long id){return scope.flashcards.cardDeck(id);}
    public void renameCardDeck(long id,String title){scope.flashcards.renameCardDeck(id,title);}
    public void deleteCardDeck(long id){scope.flashcards.deleteCardDeck(id);}
    public boolean addFlashcard(long deckId,int bookId,int sc,int sv,int ec,int ev,String reference,String content){return scope.flashcards.addFlashcard(deckId,bookId,sc,sv,ec,ev,reference,content);}
    /** User-authored card: distinct key-space (book_id=0), no Bible passage lookup. */
    public long addCustomFlashcard(long deckId,String reference,String content){return scope.flashcards.addCustomFlashcard(deckId,reference,content);}
    public List<Flashcard> flashcards(long deckId){return scope.flashcards.flashcards(deckId);}
    public void removeFlashcard(long deckId,long cardId){scope.flashcards.removeFlashcard(deckId,cardId);}

    /** Keep known weak transitions as independent diagnostic state. */
    public void recordWeakTransition(long entryId,int from,int to,long now){scope.progress.recordWeakTransition(entryId,from,to,now);}
    /** Reschedule a mastered weak verse sooner; never make a new verse 'mastered'. */
    public void rescheduleWeakVerse(long id,int position,long now){scope.progress.rescheduleWeakVerse(id,position,now);}
    public static final class DueItem {
        public long entryId, due;
        public String stage;public int start,end;
        public DueItem(long id,String stage,int start,int end,long due){
            this.entryId=id;this.stage=stage;this.start=start;this.end=end;this.due=due;
        }
    }

    public int exerciseDone(long id,int position){return scope.progress.exerciseDone(id,position);}
    public int unlockedPosition(long id,int count){return scope.progress.unlockedPosition(id,count);}
    public void finishExercise(long id,int position,int completed){scope.progress.finishExercise(id,position,completed);}
    public void scheduleReview(long id,String stage,int start,int end,int accuracy,boolean assisted,long now){scope.progress.scheduleReview(id,stage,start,end,accuracy,assisted,now);}
    /** Each due verse/group is independent. Never include unmastered, new material. */
    public List<DueItem> dueItems(long now){return scope.progress.dueItems(now);}
    public int dueCount(long now){return scope.progress.dueCount(now);}
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
            // Remove only the empty starter collection; a title is not proof
            // that a populated group (and its FK-linked history) is disposable.
            db.execSQL("DELETE FROM groups_ WHERE title='Мои стихи' AND NOT EXISTS " +
                "(SELECT 1 FROM entries WHERE group_id=groups_.id)");
        }
        if(from<7)createOrderTables(db);
        if(from<8){createUnifiedTables(db);migrateLegacyCollections(db);}
    }
    public int masteredLinkedSegments(long entryId){return scope.progress.masteredLinkedSegments(entryId);}
    public int masteryCount(long entryId){return scope.progress.masteryCount(entryId);}
    public int firstUnmastered(long entryId,int length){return scope.progress.firstUnmastered(entryId,length);}
    public boolean isStepMastered(long entryId,String stage,int start,int end){return scope.progress.isStepMastered(entryId,stage,start,end);}
    public void markMastered(long entryId,int start,int end,int score,String stage){scope.progress.markMastered(entryId,start,end,score,stage);}
    /** Every typed attempt is retained locally, with delayed recall recorded separately. */
    public void recordRecall(long entryId,String stage,int start,int end,int accuracy,
                             boolean assisted,long now){scope.progress.recordRecall(entryId,stage,start,end,accuracy,assisted,now);}
    public int recentFailures(long id,String stage,int start,int end){return scope.progress.recentFailures(id,stage,start,end);}

    public boolean consolidated(long id,String stage,int start,int end){return scope.progress.consolidated(id,stage,start,end);}
    public boolean recallDue(long id,String stage,int start,int end,long now){return scope.progress.recallDue(id,stage,start,end,now);}
    public AdaptiveLearningLogic.Progress progressFor(final long id){return scope.progress.progressFor(id);}
    public long newGroup(String title){return scope.collections.newGroup(title);}
    public List<Group> groups(){return scope.collections.groups();}
    public Group group(long id){return scope.collections.group(id);}
    public long add(long groupId,String title,String ref,List<Bible.Verse> verses){return scope.study.add(groupId,title,ref,verses);}
    /** Return a previously saved copy of this same Scripture passage when possible.
     * Shared progress must never be inferred from an unrelated passage or text variant.
     */
    public long canonicalStudyEntryId(long entryId){return scope.study.canonicalStudyEntryId(entryId);}

    /** Each card is an independent saved passage. Its own adjacent verses may link. */
    public long findOrCreateCardStudyEntry(Flashcard card,List<Bible.Verse> verses){return scope.study.findOrCreateCardStudyEntry(card,verses);}

    /** Move within the current collection only; original records and progress stay unchanged. */
    public void reorderStudy(long groupId,List<Long> ids){scope.study.reorderStudy(groupId,ids);}
    public void reorderCards(long deckId,List<Long> ids){scope.flashcards.reorderCards(deckId,ids);}

    /** Remove collection membership on both sides, preserving the study row in the hidden library. */
    public boolean removeUnifiedPassage(long groupId,String reference){return scope.collections.removeUnifiedPassage(groupId,reference);}
    public boolean detachStudyReference(long groupId,String reference){return scope.collections.detachStudyReference(groupId,reference);}
    /** One transaction for the passage and its per-verse card memberships. */
    public long addPickedPassage(long groupId,String reference,List<Bible.Verse> verses,Bible bible){return scope.collections.addPickedPassage(groupId,reference,verses,bible);}
    public long addPickedPassage(long groupId,String reference,List<Bible.Verse> verses,Bible bible,long after){return scope.collections.addPickedPassage(groupId,reference,verses,bible,after);}
    /** Moving the original row back retains all FK-linked lesson/recall history. */
    public long restoreOrAddStudyPassage(long groupId,String reference,List<Bible.Verse> verses){return scope.study.restoreOrAddStudyPassage(groupId,reference,verses);}
    /** Preserve unchanged verses and ranges, remapping their indices on any edit. */
    public void reviseStudyPassage(long id,String reference,List<Bible.Verse> verses){scope.study.reviseStudyPassage(id,reference,verses);}
    public void changeCardInDeck(long deckId,Flashcard old,int book,int sc,int sv,int ec,int ev,
            String reference,String content){scope.flashcards.changeCardInDeck(deckId,old,book,sc,sv,ec,ev,reference,content);}

    public Entry entry(long id){return scope.study.entry(id);}
    public List<Entry> entries(long groupId){return scope.study.entries(groupId);}
    public List<Entry> allEntries(){return scope.study.allEntries();}
    /** Independent schedule, not the continuation of a lesson. */
    public List<Entry> due(){return scope.progress.due();}
    public void update(Entry e,ReviewLogic.Schedule schedule,int learned,int score){scope.progress.update(e,schedule,learned,score);}
    public int consolidatedVerseCount(long entryId){return scope.progress.consolidatedVerseCount(entryId);}
    /** Cross-collection verse state. Reading alone never marks a verse learned. */
    public static final class VerseStatus {
        public String state="Не изучен";
        public int repetitions=0,successful=0;
        public long last=0,nextDue=0;
        public long entryId=-1;
        public int position=-1;
        public int rank=0;
    }
    public Map<Integer,VerseStatus> statusesForChapter(String bookName,int chapter){return scope.progress.statusesForChapter(bookName,chapter);}
    public void renameGroup(long id,String name){scope.collections.renameGroup(id,name);}
    public void renameEntry(long id,String name){scope.study.renameEntry(id,name);}
    public void deleteEntry(long id){scope.study.deleteEntry(id);}
    public void deleteGroup(long id){scope.collections.deleteGroup(id);}

    public JSONObject exportJson() throws JSONException{return scope.backup.exportJson();}
    public void importJson(String json) throws JSONException{scope.backup.importJson(json);}
}
