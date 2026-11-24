package org.biblememory.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class BackupDurabilityTest {
    private static final String[] SPACES={"study_session","collection_study_queue","card_sessions",
        "gap_rotation","unified_library_ui","home_ui","home_verse","bible_last_chapter"};
    private Context context;
    private LocalStore store;
    private File archive;
    private Uri uri;

    @Before public void setup()throws Exception{
        context=RuntimeEnvironment.getApplication();
        store=new LocalStore(context);
        store.createUnifiedCollection("Old");
        for(String name:SPACES)context.getSharedPreferences(name,0).edit().putString("marker","old").commit();
        archive=new File(context.getCacheDir(),"fixture.svbackup");
        Files.copy(new File("../tests/fixtures/r1-v1-v9.svbackup").toPath(),archive.toPath());
        uri=Uri.fromFile(archive);
    }
    @After public void close(){store.close();}

    private String scalar(String sql){
        try(Cursor c=store.getReadableDatabase().rawQuery(sql,null)){assertTrue(c.moveToFirst());return c.getString(0);}
    }

    private void checkIntegrity(){
        assertEquals("ok",scalar("PRAGMA integrity_check"));
        try(Cursor c=store.getReadableDatabase().rawQuery("PRAGMA foreign_key_check",null)){assertFalse(c.moveToFirst());}
        assertEquals(9,store.getReadableDatabase().getVersion());
    }

    private void checkSpaces(String marker){
        for(String name:SPACES)assertEquals(name,marker,context.getSharedPreferences(name,0).getString("marker",""));
    }

    private void checkOld(){
        assertEquals("Old",scalar("SELECT title FROM unified_collections"));
        assertEquals("0",scalar("SELECT COUNT(*) FROM review_attempts"));
        checkSpaces("old");
        assertFalse(SvetochBackup.pending(store));
        checkIntegrity();
    }

    private void checkImported(){
        assertEquals("1",scalar("SELECT COUNT(*) FROM unified_collections"));
        assertEquals("Fixture",scalar("SELECT title FROM unified_collections"));
        assertEquals("Body",scalar("SELECT content FROM flashcards"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM review_attempts"));
        assertEquals("123456789",scalar("SELECT attempted_at FROM review_attempts"));
        assertEquals("209856789",scalar("SELECT due FROM review_schedule"));
        assertTrue(store.isStepMastered(1,"VERSE",0,1));
        checkSpaces("new");
        assertFalse(SvetochBackup.pending(store));
        checkIntegrity();
    }

    private SvetochBackup.Checkpoint stopAt(String target){
        return point->{if(point.equals(target))throw new IOException(target);};
    }

    private void interrupt(String point)throws Exception{
        IOException ex=assertThrows(IOException.class,()->SvetochBackup.restoreFrom(context,store,uri,stopAt(point)));
        assertEquals(point,ex.getMessage());
        store.close();store=new LocalStore(context);
    }

    @Test public void beforeCommitRollsBackJournalAndRows()throws Exception{
        interrupt("beforeCommit");checkOld();
    }

    @Test public void sqliteConstraintFailureLeavesOriginalData()throws Exception{
        store.getWritableDatabase().execSQL("CREATE TRIGGER reject BEFORE INSERT ON review_schedule "+
            "BEGIN SELECT RAISE(ABORT,'simulated disk failure'); END");
        assertThrows(SQLiteException.class,()->SvetochBackup.restoreFrom(context,store,uri));
        checkOld();
    }

    @Test public void afterCommitRecoversWithoutArchiveAfterRepeatedInterruption()throws Exception{
        interrupt("afterCommit");
        assertTrue(archive.delete());
        assertTrue(SvetochBackup.pending(store));checkSpaces("old");
        assertEquals("Fixture",scalar("SELECT title FROM unified_collections"));
        assertThrows(IOException.class,()->SvetochBackup.exportTo(context,store,uri));
        assertThrows(IOException.class,()->SvetochBackup.recover(context,store,stopAt("card_sessions")));
        assertTrue(SvetochBackup.pending(store));
        assertEquals("new",context.getSharedPreferences("card_sessions",0).getString("marker",""));
        assertEquals("old",context.getSharedPreferences("gap_rotation",0).getString("marker",""));
        assertThrows(IOException.class,()->SvetochBackup.recover(context,store,stopAt("beforeCleanup")));
        checkSpaces("new");assertTrue(SvetochBackup.pending(store));
        SvetochBackup.recover(context,store);
        String generation=SvetochBackup.generation(context);
        SvetochBackup.recover(context,store);
        assertEquals(generation,SvetochBackup.generation(context));checkImported();
    }

    @Test public void failedEditorCommitKeepsJournalAndCanRetry()throws Exception{
        Context failing=new ContextWrapper(context){
            @Override public SharedPreferences getSharedPreferences(String name,int mode){
                SharedPreferences real=super.getSharedPreferences(name,mode);
                if(!name.equals("gap_rotation"))return real;
                return (SharedPreferences)Proxy.newProxyInstance(getClassLoader(),new Class[]{SharedPreferences.class},
                    (prefs,method,args)->{
                        if(!method.getName().equals("edit"))return method.invoke(real,args);
                        SharedPreferences.Editor editor=real.edit();
                        return Proxy.newProxyInstance(getClassLoader(),new Class[]{SharedPreferences.Editor.class},
                            (proxy,write,values)->{
                                if(write.getName().equals("commit"))return false;
                                Object result=write.invoke(editor,values);
                                return result==editor?proxy:result;
                            });
                    });
            }
        };
        assertThrows(IOException.class,()->SvetochBackup.restoreFrom(failing,store,uri));
        assertTrue(SvetochBackup.pending(store));
        assertEquals("old",context.getSharedPreferences("gap_rotation",0).getString("marker",""));
        SvetochBackup.recover(context,store);checkImported();
    }

    @Test public void priorExporterFixtureRestoresTwice()throws Exception{
        for(int i=0;i<2;i++){SvetochBackup.restoreFrom(context,store,uri);checkImported();}
    }

    @Test public void typedPreferencesRetainTheirTypesAndReplaceAbsentKeys()throws Exception{
        SharedPreferences prefs=context.getSharedPreferences("gap_rotation",0);
        Set<String> set=new HashSet<>(Arrays.asList("раз","два"));
        prefs.edit().putInt("i",Integer.MIN_VALUE).putLong("small",1L).putLong("large",Long.MAX_VALUE)
            .putFloat("f",1.5f).putBoolean("flag",false).putStringSet("words",set).commit();
        SvetochBackup.exportTo(context,store,uri);
        prefs.edit().clear().putString("obsolete","old").commit();
        interrupt("beforeCleanup");SvetochBackup.recover(context,store);
        assertFalse(prefs.contains("obsolete"));
        assertEquals(Integer.MIN_VALUE,prefs.getInt("i",0));
        assertEquals(1L,prefs.getLong("small",0));
        assertEquals(Long.MAX_VALUE,prefs.getLong("large",0));
        assertEquals(1.5f,prefs.getFloat("f",0),0);
        assertFalse(prefs.getBoolean("flag",true));
        assertEquals(set,prefs.getStringSet("words",null));
    }

    private JSONObject envelope()throws Exception{
        try(GZIPInputStream input=new GZIPInputStream(new FileInputStream(archive))){
            return new JSONObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    private void save(JSONObject envelope)throws IOException{
        try(GZIPOutputStream output=new GZIPOutputStream(new FileOutputStream(archive))){
            output.write(envelope.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    @Test public void incompatibleVersionBadDigestAndMalformedPreferencesAreRejected()throws Exception{
        JSONObject wrapper=envelope();
        save(wrapper.put("format",2));
        assertThrows(IOException.class,()->SvetochBackup.restoreFrom(context,store,uri));checkOld();
        save(wrapper.put("format",1).put("sha256","bad"));
        assertThrows(IOException.class,()->SvetochBackup.restoreFrom(context,store,uri));checkOld();
        JSONObject data=new JSONObject(wrapper.getString("body"));
        for(boolean invalidSchema:new boolean[]{true,false}){
            data.put("schema",invalidSchema?10:9);
            if(!invalidSchema)data.getJSONObject("preferences").remove("home_ui");
            String body=data.toString();
            StringBuilder hash=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)))
                hash.append(String.format(Locale.ROOT,"%02x",b&255));
            save(wrapper.put("body",body).put("sha256",hash));
            assertThrows(Exception.class,()->SvetochBackup.restoreFrom(context,store,uri));checkOld();
        }
    }

    @Test public void longPassageAndMultiWindowDraftSurviveJournalReplay()throws Exception{
        Bible bible=new Bible();bible.load(context);
        List<Bible.Verse> passage=bible.passage(40,5,1,7,29);
        assertTrue(passage.size()>100);
        long id=store.add(store.unifiedCollections().get(0).groupId,"Нагорная проповедь","Матфея 5–7",passage);
        String draft=String.join("",Collections.nCopies(150000,"Большой черновик "));
        context.getSharedPreferences("study_session",0).edit().putString("draft",draft).commit();
        SvetochBackup.exportTo(context,store,uri);
        context.getSharedPreferences("study_session",0).edit().clear().commit();
        interrupt("afterCommit");SvetochBackup.recover(context,store);
        assertEquals(passage.size(),store.entry(id).lines().size());
        assertEquals(passage.get(110).text,store.entry(id).lines().get(110).text);
        assertEquals(draft,context.getSharedPreferences("study_session",0).getString("draft",""));
        checkIntegrity();
    }
}
