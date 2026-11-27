package org.biblememory.app;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeSet;

final class ActivityStreakLogic {
    private ActivityStreakLogic(){}
    static int current(Map<LocalDate,Integer> days,LocalDate today){
        LocalDate day=days.getOrDefault(today,0)>0?today:today.minusDays(1);
        int count=0;
        while(days.getOrDefault(day,0)>0){count++;day=day.minusDays(1);}
        return count;
    }
    static int best(Map<LocalDate,Integer> days,LocalDate today){
        LocalDate previous=null;int run=0,best=0;
        for(LocalDate day:new TreeSet<>(days.keySet())){
            if(day.isAfter(today)||days.getOrDefault(day,0)<=0)continue;
            run=previous!=null&&day.equals(previous.plusDays(1))?run+1:1;
            best=Math.max(best,run);previous=day;
        }
        return best;
    }
    static int level(int count){return count<=0?0:count<=2?1:count<=5?2:count<=10?3:4;}
}
