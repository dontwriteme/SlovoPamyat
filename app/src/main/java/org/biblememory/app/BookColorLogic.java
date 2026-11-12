package org.biblememory.app;

/** Reference-based Bible canon color groups. Group colors are stable across sessions. */
public final class BookColorLogic {
    private BookColorLogic() {}
    public enum Group { PENTATEUCH, HISTORY, POETRY, MAJOR_PROPHETS, MINOR_PROPHETS,
                        GOSPELS, ACTS, GENERAL_LETTERS, PAULINE_LETTERS, REVELATION }
    public static Group group(int n) {
        if (n>=1&&n<=5) return Group.PENTATEUCH;
        if (n<=17&&n>=6) return Group.HISTORY;
        if (n<=22&&n>=18) return Group.POETRY;
        if (n<=27&&n>=23) return Group.MAJOR_PROPHETS;
        if (n<=39&&n>=28) return Group.MINOR_PROPHETS;
        if (n<=43&&n>=40) return Group.GOSPELS;
        if (n==44) return Group.ACTS;
        if (n>=45&&n<=58) return Group.PAULINE_LETTERS;
        if (n>=59&&n<=65) return Group.GENERAL_LETTERS;
        return Group.REVELATION;
    }
    public static int color(int bookNumber) {
        if(bookNumber<1||bookNumber>66)return 0xFFB2C5D8;
        switch(group(bookNumber)){
            case PENTATEUCH:return 0xFF807E9C;  // muted grey-purple
            case HISTORY:return 0xFF9D7952;     // bronze
            case POETRY:return 0xFF25914A;      // green
            case MAJOR_PROPHETS:return 0xFFA65A70;// dusty rose
            case MINOR_PROPHETS:return 0xFFA3A456;// olive yellow
            case GOSPELS:return 0xFFC0521B;     // deep orange
            case ACTS:return 0xFF16A3A0;        // teal
            case GENERAL_LETTERS:return 0xFF129C26;// bright green
            case PAULINE_LETTERS:return 0xFFA4AA28;// yellow green
            default:return 0xFFA74F53;         // Revelation muted red
        }
    }
    public static String shortName(int n) {
        String[] a={"Быт","Исх","Лев","Чис","Втор","Нав","Суд","Руфь",
            "1Цар","2Цар","3Цар","4Цар","1Пар","2Пар","Ездр","Неем","Есф","Иов","Пс",
            "Прит","Еккл","Песн","Ис","Иер","Плач","Иез","Дан","Ос","Иоил","Ам",
            "Авд","Ион","Мих","Наум","Авв","Соф","Агг","Зах","Мал","Мат","Мар",
            "Лук","Ин","Деян","Рим","1Кор","2Кор","Гал","Еф","Флп","Кол","1Фес",
            "2Фес","1Тим","2Тим","Тит","Флм","Евр","Иак","1Пет","2Пет","1Ин",
            "2Ин","3Ин","Иуд","Откр"};
        return n>=1&&n<=66?a[n-1]:"";
    }
}
