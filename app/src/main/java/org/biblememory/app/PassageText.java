package org.biblememory.app;

import java.util.List;

/** The same passage text is used by lessons, cards and collection commands. */
final class PassageText {
    private PassageText(){}
    static String join(List<Bible.Verse> lines){
        StringBuilder text=new StringBuilder();
        for(Bible.Verse verse:lines){if(text.length()>0)text.append(' ');text.append(verse.text);}
        return text.toString();
    }
}
