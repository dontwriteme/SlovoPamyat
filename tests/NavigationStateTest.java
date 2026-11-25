package org.biblememory.app;

import android.os.Looper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class NavigationStateTest {
    @Test public void retiredTabWarmupDoesNotPublishAfterSwitchOrTeardown(){
        TabCache cache=new TabCache();
        List<Screen> prepared=new ArrayList<>();
        cache.warm(Screen.HOME,true,()->Screen.HOME,()->false,prepared::add);
        cache.invalidate();
        cache.warm(Screen.BOOKS,true,()->Screen.BOOKS,()->false,prepared::add);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80));
        assertEquals(List.of(Screen.HOME),prepared);
        cache.close();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(List.of(Screen.HOME),prepared);
    }

    @Test public void oldHomePageIsDiscardedWithoutInvalidatingOtherTabs(){
        TabCache cache=new TabCache();
        TabCache.ReadyTab home=new TabCache.ReadyTab(null,null,null,5);
        TabCache.ReadyTab bible=new TabCache.ReadyTab(null,null,null,5);
        cache.put(Screen.HOME,home);
        cache.put(Screen.BOOKS,bible);
        assertSame(home,cache.validPage(Screen.HOME,5));
        assertNull(cache.validPage(Screen.HOME,6));
        assertFalse(cache.containsKey(Screen.HOME));
        assertSame(bible,cache.validPage(Screen.BOOKS,6));
    }

    @Test public void backRoutesKeepTheirOwnScrollPositions(){
        NavigationState state=new NavigationState();
        state.screen=Screen.VERSES;
        state.lastRenderedScreen=Screen.VERSES;
        state.put(Screen.VERSES,130);
        state.put(Screen.CHAPTERS,60);
        state.screen=state.screen.backDestination();
        assertEquals(Screen.CHAPTERS,state.screen);
        assertEquals(Screen.VERSES,state.lastRenderedScreen);
        assertEquals(60,state.get(Screen.CHAPTERS));
        assertEquals(130,state.get(Screen.VERSES));
        assertNull(Screen.CHAPTER.backDestination());
        assertNull(Screen.TRAIN.backDestination());
    }
}
