package org.biblememory.app;

import java.util.ArrayList;
import java.util.List;

final class CollectionPassages {
    private CollectionPassages(){}

    static boolean save(LocalStore store,Bible bible,CollectionInsertion insertion,boolean study,long pickerTargetId,
                        int book,int sc,int sv,int ec,int ev,java.util.function.Consumer<String> message){
        try{
            if(!PassageRangeLogic.valid(sc,sv,ec,ev)){
                message.accept("Конец отрывка раньше начала");return false;
            }
            List<Bible.Verse> verses=study?
                bible.collectionPassage(book,sc,sv,ec,ev):bible.passage(book,sc,sv,ec,ev);
            String ref=Bible.reference(book,sc,sv,ec,ev);
            if(study){
                if(store.unifiedForGroup(pickerTargetId)==null){message.accept("Коллекция больше не существует");return false;}
                long added=-1;
                for(LocalStore.Entry e:store.entries(pickerTargetId))if(ref.equals(e.reference)){
                    if(!store.retainBookChapter(pickerTargetId,e.id)){
                        message.accept("Уже в коллекции");return false;
                    }
                    added=e.id;break;
                }
                if(added<0)added=store.addPickedPassage(pickerTargetId,ref,verses,bible,insertion.afterStudy());
                if(insertion.afterStudy()>=0){
                    insertion.appendStudy(added);
                }
            }else{
                boolean added=store.addFlashcard(pickerTargetId,book,sc,sv,ec,ev,ref,texts(verses));
                if(!added){message.accept("Карточка уже в подборке");return false;}
                if(insertion.afterCard()>=0){
                    for(LocalStore.Flashcard fc:store.flashcards(pickerTargetId)){
                        if(ref.equals(fc.reference)){
                            repositionCardAfter(store,pickerTargetId,fc.id,insertion.afterCard());
                            insertion.appendCard(fc.id);
                            break;
                        }
                    }
                }
            }
            message.accept("Добавлено: "+ref);return true;
        }catch(Exception ex){message.accept(ex.getMessage()==null?"Не удалось добавить":ex.getMessage());return false;}
    }
    static void repositionStudyAfter(LocalStore store,long group,long added,long after){
        List<Long> ids=new ArrayList<>();
        for(LocalStore.Entry item:store.entries(group))if(item.id!=added)ids.add(item.id);
        int index=ids.indexOf(after);ids.add(index<0?ids.size():index+1,added);
        store.reorderStudy(group,ids);
    }
    static void repositionCardAfter(LocalStore store,long deck,long added,long after){
        List<Long> ids=new ArrayList<>();
        for(LocalStore.Flashcard f:store.flashcards(deck))if(f.id!=added)ids.add(f.id);
        int index=ids.indexOf(after);ids.add(index<0?ids.size():index+1,added);
        store.reorderCards(deck,ids);
    }

    static String texts(List<Bible.Verse> verses){
        StringBuilder text=new StringBuilder();
        for(Bible.Verse verse:verses){
            if(text.length()>0)text.append(' ');
            text.append(verse.text);
        }
        return text.toString();
    }
}
