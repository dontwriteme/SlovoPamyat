package org.biblememory.app;

import java.util.List;

final class VerseText {
    private VerseText(){}

    static String join(List<Bible.Verse> verses){
        StringBuilder text=new StringBuilder();
        for(Bible.Verse verse:verses){
            if(text.length()>0)text.append(' ');
            text.append(verse.text);
        }
        return text.toString();
    }
}
