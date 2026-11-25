package org.biblememory.app;

enum Screen {
    HOME,BOOKS,CHAPTERS,VERSES,CHAPTER,ENTRIES,CUSTOM,TRAIN,DUE,CARDS,CARD_DECK,CARD_PICK,BIBLE_PICKER,PICKER_BOOKS,PICKER_CHAPTERS,PICKER_VERSES,PICKER_TEXT,CARD_SESSION,REORDER_STUDY,REORDER_CARDS,COLLECTIONS,COLLECTION_DETAIL,COLLECTION_BOOK,COLLECTION_CHAPTER,SETTINGS,RUN,RUN_SUMMARY;

    static final int TAB_COUNT=4;
    static Screen tab(int index){
        switch(index){
            case 0:return HOME;
            case 1:return BOOKS;
            case 2:return COLLECTIONS;
            case 3:return SETTINGS;
            default:throw new IllegalArgumentException("Invalid tab: "+index);
        }
    }

    boolean isMainTab(){
        return this==HOME||this==BOOKS||this==COLLECTIONS||this==SETTINGS;
    }

    int mainTabIndex(boolean orphanedStudyPicker){
        switch(this){
            case BOOKS:case CHAPTERS:case VERSES:case CHAPTER:return 1;
            case COLLECTIONS:case COLLECTION_DETAIL:case COLLECTION_BOOK:case COLLECTION_CHAPTER:
            case ENTRIES:case REORDER_STUDY:
            case CARDS:case CARD_DECK:case CARD_PICK:case CARD_SESSION:case REORDER_CARDS:return 2;
            case SETTINGS:return 3;
            case BIBLE_PICKER:case PICKER_BOOKS:case PICKER_CHAPTERS:
            case PICKER_VERSES:case PICKER_TEXT:return orphanedStudyPicker?0:2;
            default:return 0;
        }
    }
}
