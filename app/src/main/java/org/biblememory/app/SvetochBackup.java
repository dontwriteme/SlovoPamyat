package org.biblememory.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Offline, complete, versioned backup of the application's own SQLite tables and
 * learning/UI preferences. SAF grants access to only the file the user selects.
 * A restore REPLACES saved state (never appends duplicate collections).
 */
final class SvetochBackup {
    private SvetochBackup(){}

    static final String EXTENSION=".svbackup";
    private static final String MAGIC="svetoch-full-backup";
    private static final int FORMAT=1;
    private static final int MAX_UNCOMPRESSED=100 * 1024 * 1024;
    // Parent tables first for inserts, child tables first for deletion.
    private static final String[] TABLES={
        "groups_","card_decks","flashcards","entries","unified_collections",
        "verse_progress","stage_progress","recall_activity","lesson_progress",
        "review_schedule","review_attempts","transition_issues",
        "card_deck_members","study_order","card_order","unified_card_entries",
        "unified_entry_cards","collection_book_chapters"
    };
    private static final String[] PREFERENCES={
        "study_session","collection_study_queue","card_sessions","gap_rotation",
        "unified_library_ui","home_ui","home_verse","bible_last_chapter"
    };

    // The journal exists only between import commit and successful preference replay.
    private static final String JOURNAL="pending_backup_restore";
    private static final int CHUNK=32*1024;
    interface Checkpoint { void reached(String point) throws IOException; }
    private static final Checkpoint NONE=point->{};

    static boolean pending(LocalStore store){
        try(Cursor rows=store.getReadableDatabase().rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{JOURNAL})){
            return rows.moveToFirst();
        }
    }

    static String generation(Context context){
        return context.getSharedPreferences("backup_generation",Context.MODE_PRIVATE).getString("id","");
    }

    static void exportTo(Context context,LocalStore store,Uri uri) throws Exception {
        if(pending(store))throw new IOException("Сначала завершите восстановление");
        JSONObject data=new JSONObject();
        SQLiteDatabase db=store.getReadableDatabase();
        data.put("schema",db.getVersion());
        JSONObject tables=new JSONObject();
        // One SQLite read transaction ensures that every table belongs to the same
        // committed snapshot, including while another thread writes using WAL.
        db.beginTransactionNonExclusive();
        try {
            for(String table:TABLES){
                JSONArray rows=new JSONArray();
                try(Cursor cursor=db.query(table,null,null,null,null,null,null)){
                    String[] columns=cursor.getColumnNames();
                    while(cursor.moveToNext()){
                        JSONObject row=new JSONObject();
                        for(int i=0;i<columns.length;i++){
                            switch(cursor.getType(i)){
                                case Cursor.FIELD_TYPE_NULL:row.put(columns[i],JSONObject.NULL);break;
                                case Cursor.FIELD_TYPE_INTEGER:row.put(columns[i],cursor.getLong(i));break;
                                case Cursor.FIELD_TYPE_FLOAT:row.put(columns[i],cursor.getDouble(i));break;
                                case Cursor.FIELD_TYPE_BLOB:
                                    row.put(columns[i],new JSONObject().put("$blob",
                                        Base64.encodeToString(cursor.getBlob(i),Base64.NO_WRAP)));
                                    break;
                                default:row.put(columns[i],cursor.getString(i));
                            }
                        }
                        rows.put(row);
                    }
                }
                tables.put(table,rows);
            }
            data.put("preferences",encodePreferences(snapshotPreferences(context)));
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        data.put("tables",tables);
        data.put("createdAt",System.currentTimeMillis());

        String body=data.toString();
        JSONObject packageData=new JSONObject()
            .put("magic",MAGIC).put("format",FORMAT)
            .put("sha256",sha256(body)).put("body",body);
        byte[] bytes=packageData.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_UNCOMPRESSED)
            throw new IOException("Резервная копия превышает безопасный размер");
        try(OutputStream target=context.getContentResolver().openOutputStream(uri,"w")){
            if(target==null)throw new IOException("Не удалось открыть выбранный файл");
            try(GZIPOutputStream gzip=new GZIPOutputStream(new BufferedOutputStream(target))){
                gzip.write(bytes);
            }
        }
    }

    static void restoreFrom(Context context,LocalStore store,Uri uri) throws Exception {
        restoreFrom(context,store,uri,NONE);
    }

    static void restoreFrom(Context context,LocalStore store,Uri uri,Checkpoint checkpoint) throws Exception {
        if(pending(store))throw new IOException("Сначала завершите восстановление");
        byte[] buffer;
        try(InputStream input=context.getContentResolver().openInputStream(uri)){
            if(input==null)throw new IOException("Не удалось открыть выбранный файл");
            try(GZIPInputStream gzip=new GZIPInputStream(new BufferedInputStream(input));
                ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] chunk=new byte[8192];
                int read;
                while((read=gzip.read(chunk))!=-1){
                    if(out.size()>MAX_UNCOMPRESSED-read)
                        throw new IOException("Резервная копия слишком большая");
                    out.write(chunk,0,read);
                }
                buffer=out.toByteArray();
            }
        }
        JSONObject envelope=new JSONObject(new String(buffer,StandardCharsets.UTF_8));
        if(!MAGIC.equals(envelope.optString("magic")) || envelope.optInt("format")!=FORMAT)
            throw new IOException("Неизвестный формат резервной копии");
        String body=envelope.getString("body");
        if(!sha256(body).equals(envelope.optString("sha256")))
            throw new IOException("Контрольная сумма не совпадает: файл повреждён");
        JSONObject data=new JSONObject(body);
        SQLiteDatabase db=store.getWritableDatabase();
        if(data.getInt("schema")!=db.getVersion())
            throw new IOException("Версия базы данных в резервной копии несовместима с приложением");
        JSONObject tables=data.getJSONObject("tables");
        if(tables.length()!=TABLES.length)throw new IOException("Неверный набор таблиц в копии");
        // Check the archive representation before opening the replacement transaction.
        // SQLite enforces keys and relationships on the uncommitted replacement.
        for(String table:TABLES){
            JSONArray rows=tables.getJSONArray(table);
            Set<String> columns=new HashSet<>();
            try(Cursor cursor=db.query(table,null,"0",null,null,null,null)){
                Collections.addAll(columns,cursor.getColumnNames());
            }
            for(int index=0;index<rows.length();index++){
                JSONObject row=rows.getJSONObject(index);
                if(row.length()!=columns.size())
                    throw new IOException("Неверная структура таблицы "+table);
                for(Iterator<String> it=row.keys();it.hasNext();){
                    String column=it.next();
                    if(!columns.contains(column))throw new IOException("Неизвестное поле в таблице "+table);
                    Object value=row.get(column);
                    if(value instanceof JSONObject){
                        JSONObject blob=(JSONObject)value;
                        if(blob.length()!=1||!(blob.opt("$blob") instanceof String))
                            throw new IOException("Неверное двоичное поле в таблице "+table);
                        Base64.decode(blob.getString("$blob"),Base64.DEFAULT);
                    }else if(!(value instanceof String||value instanceof Number||value==JSONObject.NULL)){
                        throw new IOException("Неверный тип данных в таблице "+table);
                    }
                    if(column.equals("id")&&value==JSONObject.NULL)
                        throw new IOException("Отсутствует идентификатор в таблице "+table);
                }
            }
        }
        JSONObject preferences=data.getJSONObject("preferences");
        if(preferences.length()!=PREFERENCES.length)throw new IOException("Неверный набор настроек в копии");
        for(String name:PREFERENCES)decodeGroup(preferences.getJSONObject(name));
        db.beginTransaction();
        try {
            // Deleting child rows first prevents FK violations. The insert order is
            // the reverse, so original IDs and collection relationships are retained.
            for(int i=TABLES.length-1;i>=0;i--)db.delete(TABLES[i],null,null);
            for(String table:TABLES){
                JSONArray rows=tables.getJSONArray(table);
                for(int index=0;index<rows.length();index++){
                    JSONObject row=rows.getJSONObject(index);
                    ContentValues values=new ContentValues();
                    for(Iterator<String> it=row.keys();it.hasNext();){
                        String column=it.next();
                        Object value=row.get(column);
                        if(value==JSONObject.NULL)values.putNull(column);
                        else if(value instanceof JSONObject)
                            values.put(column,Base64.decode(
                                ((JSONObject)value).getString("$blob"),Base64.DEFAULT));
                        else if(value instanceof Integer)values.put(column,(Integer)value);
                        else if(value instanceof Long)values.put(column,(Long)value);
                        else if(value instanceof Number)values.put(column,((Number)value).doubleValue());
                        else if(value instanceof String)values.put(column,(String)value);
                        else throw new IOException("Неверный тип данных в копии");
                    }
                    db.insertOrThrow(table,null,values);
                }
            }
            try(Cursor check=db.rawQuery("PRAGMA foreign_key_check",null)){
                if(check.moveToFirst())throw new IOException(
                    "Нарушена целостность связей между сохранёнными данными");
            }
            db.execSQL("CREATE TABLE "+JOURNAL+
                " (space TEXT NOT NULL, part INTEGER NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(space,part))");
            writeChunks(db,"generation",UUID.randomUUID().toString());
            for(String name:PREFERENCES)writeChunks(db,name,preferences.getJSONObject(name).toString());
            checkpoint.reached("beforeCommit");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        checkpoint.reached("afterCommit");
        recover(context,store,checkpoint);
    }

    private static void writeChunks(SQLiteDatabase db,String name,String value){
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        for(int offset=0;offset<bytes.length;offset+=CHUNK){
            db.execSQL("INSERT INTO "+JOURNAL+" VALUES(?,?,?)",new Object[]{name,offset/CHUNK,
                Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+CHUNK))});
        }
    }

    private static String readChunks(SQLiteDatabase db,String name)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(Cursor rows=db.query(JOURNAL,new String[]{"part","payload"},"space=?",new String[]{name},
                null,null,"part")){
            int expected=0;
            while(rows.moveToNext()){
                if(rows.getInt(0)!=expected++)throw new IOException("Повреждён журнал восстановления");
                bytes.write(rows.getBlob(1));
            }
            if(expected==0)throw new IOException("В журнале отсутствует "+name);
        }
        return bytes.toString("UTF-8");
    }

    static void recover(Context context,LocalStore store)throws Exception{
        recover(context,store,NONE);
    }

    static void recover(Context context,LocalStore store,Checkpoint checkpoint)throws Exception{
        if(!pending(store))return;
        SQLiteDatabase db=store.getWritableDatabase();
        for(String name:PREFERENCES){
            Map<String,Object> values=decodeGroup(new JSONObject(readChunks(db,name)));
            applyGroup(context,name,values);
            checkpoint.reached(name);
        }
        if(!context.getSharedPreferences("backup_generation",Context.MODE_PRIVATE).edit()
                .putString("id",readChunks(db,"generation")).commit())
            throw new IOException("Не удалось сохранить отметку восстановления");
        checkpoint.reached("beforeCleanup");
        db.execSQL("DROP TABLE "+JOURNAL);
    }

    private static String sha256(String text) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(
            text.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();
        for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }

    private static Map<String,Map<String,Object>> snapshotPreferences(Context context){
        Map<String,Map<String,Object>> result=new LinkedHashMap<>();
        for(String name:PREFERENCES)
            result.put(name,new LinkedHashMap<>(
                context.getSharedPreferences(name,Context.MODE_PRIVATE).getAll()));
        return result;
    }

    private static JSONObject encodePreferences(Map<String,Map<String,Object>> values)
            throws JSONException {
        JSONObject out=new JSONObject();
        for(Map.Entry<String,Map<String,Object>> group:values.entrySet()){
            JSONObject entries=new JSONObject();
            for(Map.Entry<String,Object> e:group.getValue().entrySet()){
                Object value=e.getValue();
                JSONObject packed=new JSONObject();
                if(value instanceof String)packed.put("t","string").put("v",value);
                else if(value instanceof Integer)packed.put("t","int").put("v",value);
                else if(value instanceof Long)packed.put("t","long").put("v",value);
                else if(value instanceof Float)packed.put("t","float").put("v",value);
                else if(value instanceof Boolean)packed.put("t","boolean").put("v",value);
                else if(value instanceof Set){
                    JSONArray list=new JSONArray();
                    for(Object s:(Set<?>)value){
                        if(!(s instanceof String))throw new JSONException("Invalid preference set");
                        list.put(s);
                    }
                    packed.put("t","set").put("v",list);
                }else throw new JSONException("Unsupported preference value: "+e.getKey());
                entries.put(e.getKey(),packed);
            }
            out.put(group.getKey(),entries);
        }
        return out;
    }

    private static Map<String,Object> decodeGroup(JSONObject group)throws Exception {
        Map<String,Object> entries=new LinkedHashMap<>();
        for(Iterator<String> it=group.keys();it.hasNext();){
            String key=it.next();
            JSONObject packed=group.getJSONObject(key);
            String type=packed.getString("t");
            Object value=packed.get("v");
            switch(type){
                case "string":if(!(value instanceof String))throw invalidPreference();break;
                case "int":
                    if(!(value instanceof Integer))throw invalidPreference();
                    break;
                case "long":
                    if(!(value instanceof Integer||value instanceof Long))throw invalidPreference();
                    value=((Number)value).longValue();break;
                case "float":
                    if(!(value instanceof Number))throw invalidPreference();
                    float number=((Number)value).floatValue();
                    if(!Float.isFinite(number))throw invalidPreference();
                    value=number;break;
                case "boolean":if(!(value instanceof Boolean))throw invalidPreference();break;
                case "set":
                    Set<String> words=new HashSet<>();
                    JSONArray items=packed.getJSONArray("v");
                    for(int i=0;i<items.length();i++){
                        if(!(items.get(i) instanceof String))throw invalidPreference();
                        words.add(items.getString(i));
                    }
                    value=words;break;
                default:throw new IOException("Неизвестный параметр в копии");
            }
            entries.put(key,value);
        }
        return entries;
    }

    private static IOException invalidPreference(){
        return new IOException("Неверный тип сохранённого параметра");
    }

    private static void applyGroup(Context context,String name,Map<String,Object> values)throws IOException{
        SharedPreferences.Editor edit=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear();
        for(Map.Entry<String,Object> entry:values.entrySet()){
            String k=entry.getKey();Object v=entry.getValue();
            if(v instanceof String)edit.putString(k,(String)v);
            else if(v instanceof Integer)edit.putInt(k,(Integer)v);
            else if(v instanceof Long)edit.putLong(k,(Long)v);
            else if(v instanceof Float)edit.putFloat(k,(Float)v);
            else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);
            else if(v instanceof Set){
                @SuppressWarnings("unchecked") Set<String> strings=(Set<String>)v;
                edit.putStringSet(k,strings);
            }
        }
        if(!edit.commit())throw new IOException(
            "Не удалось записать сохранённые параметры: "+name);
    }
}
