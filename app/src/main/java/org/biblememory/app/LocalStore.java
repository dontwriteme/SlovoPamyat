package org.biblememory.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Private on-device SQLite database. No account, server or network permission. */
public final class LocalStore extends SQLiteOpenHelper {
    private final RepositoryScope scope=new RepositoryScope(this);
    RepositoryScope repositoryScope(){return scope;}
    public StudyRepository study(){return scope.study;}
    public ProgressRepository progress(){return scope.progress;}
    public FlashcardRepository flashcards(){return scope.flashcards;}
    public CollectionRepository collections(){return scope.collections;}
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
    public LocalStore(Context context) { super(context,"slovo_pamyat.db",null,8); }
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

    List<Group> groupsForBackup(){
        List<Group> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
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
                try(Cursor c=getReadableDatabase().rawQuery(
                    "SELECT position FROM verse_progress WHERE entry_id=? ORDER BY position",
                    new String[]{String.valueOf(e.id)})) {
                    while(c.moveToNext())mastered.put(c.getInt(0));
                }
                JSONArray stages=new JSONArray();
                try(Cursor c=getReadableDatabase().rawQuery(
                    "SELECT stage,first_line,last_line,best_score FROM stage_progress WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})) {
                    while(c.moveToNext())stages.put(new JSONObject()
                        .put("stage",c.getString(0)).put("start",c.getInt(1))
                        .put("end",c.getInt(2)).put("score",c.getInt(3)));
                }
                JSONArray recalls=new JSONArray();
                try(Cursor c=getReadableDatabase().rawQuery(
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
                try(Cursor c=getReadableDatabase().rawQuery(
                    "SELECT position,completed FROM lesson_progress WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})){
                    while(c.moveToNext()) lessons.put(new JSONObject().put("position",c.getInt(0)).put("completed",c.getInt(1)));
                }
                JSONArray schedules=new JSONArray();
                try(Cursor c=getReadableDatabase().rawQuery(
                    "SELECT stage,first_line,last_line,level,due,last_score FROM review_schedule WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})){
                    while(c.moveToNext())schedules.put(new JSONObject().put("stage",c.getString(0))
                        .put("start",c.getInt(1)).put("end",c.getInt(2)).put("level",c.getInt(3))
                        .put("due",c.getLong(4)).put("score",c.getInt(5)));
                }
                JSONArray attempts=new JSONArray();
                try(Cursor c=getReadableDatabase().rawQuery(
                    "SELECT stage,first_line,last_line,attempted_at,accuracy,assisted FROM review_attempts WHERE entry_id=?",
                    new String[]{String.valueOf(e.id)})){
                    while(c.moveToNext())attempts.put(new JSONObject().put("stage",c.getString(0))
                        .put("start",c.getInt(1)).put("end",c.getInt(2)).put("at",c.getLong(3))
                        .put("score",c.getInt(4)).put("assisted",c.getInt(5)));
                }
                JSONArray transitions=new JSONArray();
                try(Cursor c=getReadableDatabase().rawQuery(
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
