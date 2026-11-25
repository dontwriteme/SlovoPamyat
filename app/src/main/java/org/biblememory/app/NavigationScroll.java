package org.biblememory.app;

import java.util.EnumMap;

/** Positions within routes. The Home scroll continues to live in HomePage. */
final class NavigationScroll {
    private final EnumMap<Screen,Integer> positions=new EnumMap<>(Screen.class);

    void put(Screen route,int y){positions.put(route,y);}
    void remove(Screen route){positions.remove(route);}
    boolean containsKey(Screen route){return positions.containsKey(route);}
    int getOrDefault(Screen route,int fallback){return positions.getOrDefault(route,fallback);}
    int get(Screen route){return positions.get(route);}
    void clear(){positions.clear();}
}
