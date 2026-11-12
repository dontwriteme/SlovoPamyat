package org.biblememory.app;

public final class ChapterCountLogic {
    private ChapterCountLogic(){}
    public static String format(int count){
        int last2=count%100,last=count%10;
        String suffix=(last2>=11&&last2<=14)?"глав":last==1?"глава":
            (last>=2&&last<=4)?"главы":"глав";
        return count+" "+suffix;
    }
}
