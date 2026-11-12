package org.biblememory.app;

import java.util.*;

/** Chooses a coherent, non-overlapping review SESSION from due, already mastered material.
 *  A temporary VERSE_RUN is only a presentation unit: its source verse schedules stay separate.
 */
public final class ReviewQueueLogic {
    private ReviewQueueLogic() {}
    public static final class Item {
        public final long entryId,due;
        public final String stage;
        public final int start,end;
        public Item(long id,String stage,int start,int end,long due){
            this.entryId=id;this.stage=stage;this.start=start;this.end=end;this.due=due;
        }
        public boolean isVerse(){return "VERSE".equals(stage) && end==start+1;}
        public boolean contains(Item other){return entryId==other.entryId && start<=other.start && end>=other.end;}
    }
    /** Access to text, refs and past mastery; never requires Android components. */
    public interface Source {
        List<String> references(long entryId);
        List<String> texts(long entryId);
    }
    private static boolean adjacent(String a,String b){
        try {
            String[] x=a.split(":"),y=b.split(":");
            return x.length==2&&y.length==2 &&
                Integer.parseInt(x[0])==Integer.parseInt(y[0]) &&
                Integer.parseInt(y[1])==Integer.parseInt(x[1])+1;
        }catch(Exception e){return false;}
    }
    private static int words(String s){return ReviewLogic.words(s).size();}
    public static List<Item> plan(Collection<Item> raw,Source source) {
        List<Item> all=new ArrayList<>();
        for(Item item:raw) if(item.start>=0 && item.end>item.start &&
            item.end<=source.references(item.entryId).size())all.add(item);
        // Short completed passages are preferable to a whole chapter when both are due.
        all.sort(Comparator.comparingInt((Item i)->i.isVerse()?1:0)
            .thenComparingInt(i->i.end-i.start)
            .thenComparingLong(i->i.due)
            .thenComparingLong(i->i.entryId)
            .thenComparingInt(i->i.start));
        List<Item> picked=new ArrayList<>();
        for(Item item:all) {
            boolean overlap=false;
            for(Item existing:picked) if(existing.entryId==item.entryId &&
                existing.start<item.end && item.start<existing.end){overlap=true;break;}
            if(!overlap)picked.add(item);
        }
        picked.sort(Comparator.comparingLong((Item i)->i.entryId).thenComparingInt(i->i.start));
        // Coalesce adjacent DUE verses into a short in-order review, without crossing chapters,
        // user-defined unrelated lines, or an existing (mastered) group's planned review.
        List<Item> combined=new ArrayList<>();
        for(int i=0;i<picked.size();) {
            Item first=picked.get(i);if(!first.isVerse()){combined.add(first);i++;continue;}
            List<String> refs=source.references(first.entryId), text=source.texts(first.entryId);
            int j=i+1,end=first.end,count=words(text.get(first.start));
            long earliest=first.due;
            while(j<picked.size()) {
                Item next=picked.get(j);
                if(!next.isVerse()||next.entryId!=first.entryId||next.start!=end ||
                    !adjacent(refs.get(end-1),refs.get(end)))break;
                int more=words(text.get(end));
                if(end-first.start>=4 || count+more>75)break;
                count+=more;end++;earliest=Math.min(earliest,next.due);j++;
                // Prefer paragraph/sentence ends over arbitrarily extending a long review.
                if(text.get(end-1).trim().matches("(?s).*[.!?]$"))break;
            }
            if(end>first.end){combined.add(new Item(first.entryId,"VERSE_RUN",first.start,end,earliest));i=j;}
            else {combined.add(first);i++;}
        }
        // First review of a passage goes before later sections; earlier due entries have priority.
        combined.sort(Comparator.comparingLong((Item a)->a.due)
            .thenComparingLong(a->a.entryId).thenComparingInt(a->a.start));
        return combined;
    }
}
