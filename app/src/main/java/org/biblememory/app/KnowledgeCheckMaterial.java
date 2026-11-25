package org.biblememory.app;

import java.util.*;

/** Read-only projection of consolidated verses in collection order. */
final class KnowledgeCheckMaterial {
    private KnowledgeCheckMaterial() {}

    static List<String> identity(LocalStore.Entry entry,int book,int position,Bible.Verse verse) {
        return book>0?Arrays.asList("bible",Integer.toString(book),verse.ref,verse.text):
            Arrays.asList("custom",Long.toString(entry.id),Integer.toString(position));
    }

    static List<KnowledgeCheckSession.Question> questions(CollectionProgressSnapshot snapshot,long group) {
        List<KnowledgeCheckSession.Question> result=new ArrayList<>();
        Set<List<String>> seen=new HashSet<>();
        for(LocalStore.Entry entry:snapshot.entries(group)){
            int book=LocalStore.bibleBookFromReference(entry.reference);
            List<Bible.Verse> lines=snapshot.verses(entry.id);
            for(int i=0;i<lines.size();i++){
                Bible.Verse line=lines.get(i);
                if(!snapshot.verseConsolidated(entry.id,i)||!seen.add(identity(entry,book,i,line)))continue;
                String reference=book>0?Bible.NAMES[book-1]+" "+line.ref:
                    entry.reference+(lines.size()>1?" · "+(i+1):"");
                result.add(new KnowledgeCheckSession.Question(entry.id,i,reference,line.text));
            }
        }
        return result;
    }
}
