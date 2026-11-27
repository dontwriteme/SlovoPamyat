package org.biblememory.app;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActivityStreakLogicTest {
    private final LocalDate today=LocalDate.of(2026,11,27);
    @Test public void emptyHistory(){assertEquals(0,ActivityStreakLogic.current(new HashMap<>(),today));}
    @Test public void yesterdayPreservesStreakUntilTodayEnds(){
        Map<LocalDate,Integer> days=new HashMap<>();
        days.put(today.minusDays(1),1);days.put(today.minusDays(2),3);
        assertEquals(2,ActivityStreakLogic.current(days,today));
        days.put(today,1);assertEquals(3,ActivityStreakLogic.current(days,today));
    }
    @Test public void gapEndsCurrentButNotBest(){
        Map<LocalDate,Integer> days=new HashMap<>();
        days.put(today.minusDays(3),1);days.put(today.minusDays(4),1);
        days.put(today.plusDays(1),10);
        assertEquals(0,ActivityStreakLogic.current(days,today));
        assertEquals(2,ActivityStreakLogic.best(days,today));
    }
    @Test public void levels(){assertEquals(0,ActivityStreakLogic.level(0));assertEquals(4,ActivityStreakLogic.level(11));}
}
