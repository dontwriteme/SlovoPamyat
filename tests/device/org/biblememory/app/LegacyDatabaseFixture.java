package org.biblememory.app;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/** Frozen synthetic pre-v8 schemas, reconstructed from the upgrade contracts.
 * The available initial Git revision already contains v8, so these are NOT
 * databases captured from historical releases or calls to current onCreate. */
final class LegacyDatabaseFixture {
    static final long NOW=1700000000000L, DAY=86400000L;
    static void create(SafetySandbox context,int version,String title){
        try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("slovo_pamyat.db"),null)){
            db.execSQL("PRAGMA foreign_keys=ON");
            db.execSQL("CREATE TABLE groups_ (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL)");
            db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT,group_id INTEGER NOT NULL REFERENCES groups_(id) ON DELETE CASCADE,title TEXT NOT NULL,reference TEXT NOT NULL,lines TEXT NOT NULL,learned INTEGER NOT NULL DEFAULT 0,repetitions INTEGER NOT NULL DEFAULT 0,days INTEGER NOT NULL DEFAULT 0,ease REAL NOT NULL DEFAULT 2.5,due INTEGER NOT NULL DEFAULT 0,last_score INTEGER NOT NULL DEFAULT -1)");
            db.execSQL("INSERT INTO groups_ VALUES(7,?)",new Object[]{title});
            ContentValues e=new ContentValues();e.put("id",41);e.put("group_id",7);e.put("title","Sentinel passage");
            e.put("reference","Матфея 5:3–5");e.put("lines","[{\"ref\":\"5:3\",\"text\":\"Альфа\"},{\"ref\":\"5:4\",\"text\":\"Бета\"},{\"ref\":\"5:5\",\"text\":\"Гамма\"}]");
            e.put("learned",2);e.put("repetitions",3);e.put("days",7);e.put("ease",2.36);e.put("due",NOW);e.put("last_score",100);
            db.insertOrThrow("entries",null,e);
            if(version>=2){
                db.execSQL("CREATE TABLE verse_progress(entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE,position INTEGER NOT NULL,best_score INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(entry_id,position))");
                db.execSQL("CREATE TABLE stage_progress(entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE,stage TEXT NOT NULL,first_line INTEGER NOT NULL,last_line INTEGER NOT NULL,best_score INTEGER NOT NULL,PRIMARY KEY(entry_id,stage,first_line,last_line))");
                db.execSQL("INSERT INTO verse_progress VALUES(41,0,100),(41,2,83)");
                db.execSQL("INSERT INTO stage_progress VALUES(41,'CHAIN',0,3,100)");
            }
            if(version>=3){
                db.execSQL("CREATE TABLE recall_activity(entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE,stage TEXT NOT NULL,first_line INTEGER NOT NULL,last_line INTEGER NOT NULL,last_attempt INTEGER NOT NULL DEFAULT 0,first_success INTEGER NOT NULL DEFAULT 0,last_success INTEGER NOT NULL DEFAULT 0,success_count INTEGER NOT NULL DEFAULT 0,fail_streak INTEGER NOT NULL DEFAULT 0,last_score INTEGER NOT NULL DEFAULT -1,PRIMARY KEY(entry_id,stage,first_line,last_line))");
                db.execSQL("INSERT INTO recall_activity VALUES(41,'VERSE',0,1,?,?,?,2,0,100)",new Object[]{NOW,NOW-3*DAY,NOW});
            }
            if(version>=4){
                db.execSQL("CREATE TABLE lesson_progress(entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE,position INTEGER NOT NULL,completed INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(entry_id,position))");
                db.execSQL("CREATE TABLE review_schedule(entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE,stage TEXT NOT NULL,first_line INTEGER NOT NULL,last_line INTEGER NOT NULL,level INTEGER NOT NULL DEFAULT 0,due INTEGER NOT NULL,last_score INTEGER NOT NULL DEFAULT -1,PRIMARY KEY(entry_id,stage,first_line,last_line))");
                db.execSQL("CREATE TABLE review_attempts(id INTEGER PRIMARY KEY AUTOINCREMENT,entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE,stage TEXT NOT NULL,first_line INTEGER NOT NULL,last_line INTEGER NOT NULL,attempted_at INTEGER NOT NULL,accuracy INTEGER NOT NULL,assisted INTEGER NOT NULL)");
                db.execSQL("INSERT INTO lesson_progress VALUES(41,0,5),(41,1,3)");
                db.execSQL("INSERT INTO review_schedule VALUES(41,'VERSE',0,1,3,?,100)",new Object[]{NOW+14*DAY});
                db.execSQL("INSERT INTO review_attempts VALUES(72,41,'VERSE',0,1,?,100,0)",new Object[]{NOW-DAY});
            }
            if(version>=5){
                db.execSQL("CREATE TABLE transition_issues(entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE,from_position INTEGER NOT NULL,to_position INTEGER NOT NULL,failures INTEGER NOT NULL DEFAULT 0,last_failed INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(entry_id,from_position,to_position))");
                db.execSQL("INSERT INTO transition_issues VALUES(41,0,1,2,?)",new Object[]{NOW});
            }
            if(version>=6){
                db.execSQL("CREATE TABLE card_decks(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL)");
                db.execSQL("CREATE TABLE flashcards(id INTEGER PRIMARY KEY AUTOINCREMENT,book_id INTEGER NOT NULL,first_chapter INTEGER NOT NULL,first_verse INTEGER NOT NULL,last_chapter INTEGER NOT NULL,last_verse INTEGER NOT NULL,reference TEXT NOT NULL,content TEXT NOT NULL,UNIQUE(book_id,first_chapter,first_verse,last_chapter,last_verse))");
                db.execSQL("CREATE TABLE card_deck_members(deck_id INTEGER NOT NULL REFERENCES card_decks(id) ON DELETE CASCADE,card_id INTEGER NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE,PRIMARY KEY(deck_id,card_id))");
                db.execSQL("INSERT INTO card_decks VALUES(12,'Legacy cards')");
                db.execSQL("INSERT INTO flashcards VALUES(91,40,5,3,5,3,'Матфея 5:3','Альфа')");
                db.execSQL("INSERT INTO card_deck_members VALUES(12,91)");
            }
            if(version>=7){
                db.execSQL("CREATE TABLE study_order(entry_id INTEGER PRIMARY KEY REFERENCES entries(id) ON DELETE CASCADE,group_id INTEGER NOT NULL,position INTEGER NOT NULL)");
                db.execSQL("CREATE TABLE card_order(deck_id INTEGER NOT NULL REFERENCES card_decks(id) ON DELETE CASCADE,card_id INTEGER NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE,position INTEGER NOT NULL,PRIMARY KEY(deck_id,card_id))");
                db.execSQL("INSERT INTO study_order VALUES(41,7,7)");db.execSQL("INSERT INTO card_order VALUES(12,91,9)");
            }
            db.setVersion(version);
        }
    }
}
