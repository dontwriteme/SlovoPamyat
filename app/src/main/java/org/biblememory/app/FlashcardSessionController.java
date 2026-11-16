package org.biblememory.app;
import android.os.Bundle;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Owns the current shuffled card cycle and its persistent checkpoint, without Views. */
final class FlashcardSessionController {
    interface Source { StudyRepository study(); FlashcardRepository flashcards(); SharedPreferences preferences(); }
    private final Source source;
    FlashcardSessionController(Source source){this.source=source;}
    boolean cardFromUnified = false;
    long selectedDeck = -1;
    final List<Long> cardSequence = new ArrayList<>();
    int cardIndex = 0;
    boolean cardShowReference = true;
    boolean cardRevealed = false;
    final Random cardRandom = new Random();
    final List<Long> cardStudyQueue = new ArrayList<>();
    int cardStudyCursor = -1;
    long cardStudyDeckId = -1;
    void stopCardStudy(){cardStudyQueue.clear();cardStudyCursor=-1;cardStudyDeckId=-1;}

    static final class StudyStart {
        final long firstEntry;
        final String message;
        final boolean failed;
        StudyStart(long firstEntry,String message,boolean failed){this.firstEntry=firstEntry;this.message=message;this.failed=failed;}
    }
    StudyStart studyCardDeck(Bible bible){
        List<LocalStore.Flashcard> cards=source.flashcards().flashcards(selectedDeck);
        if(cards.isEmpty())return new StudyStart(0,"Подборка пуста",false);
        if(!bible.isReady())for(LocalStore.Flashcard card:cards)
            if(card.bookId>0)return new StudyStart(0,"Библия ещё загружается",false);
        cardStudyQueue.clear();cardStudyCursor=0;cardStudyDeckId=selectedDeck;
        for(LocalStore.Flashcard card:cards){
            try{
                List<Bible.Verse> verses=card.bookId==0?
                    Collections.singletonList(new Bible.Verse("1:1",card.content)):
                    bible.passage(card.bookId,card.firstChapter,card.firstVerse,card.lastChapter,card.lastVerse);
                long id=source.study().findOrCreateCardStudyEntry(card,verses);
                if(!cardStudyQueue.contains(id))cardStudyQueue.add(id);
            }catch(Exception failure){
                cardStudyQueue.clear();cardStudyCursor=-1;cardStudyDeckId=-1;
                return new StudyStart(0,"Не удалось открыть стих: "+card.reference,true);
            }
        }
        return new StudyStart(cardStudyQueue.isEmpty()?0:cardStudyQueue.get(0),null,false);
    }
    /** -1: no session, 0: finished, positive: next independent study entry. */
    long advanceCardStudy(){
        if(cardStudyCursor<0)return -1;
        if(++cardStudyCursor<cardStudyQueue.size())return cardStudyQueue.get(cardStudyCursor);
        cardStudyQueue.clear();cardStudyCursor=-1;selectedDeck=cardStudyDeckId;cardStudyDeckId=-1;
        return 0;
    }

    void clearSavedCardSession(long deckId){
        source.preferences().edit().remove("deck_"+deckId).apply();
    }

    List<Long> cardIds(List<LocalStore.Flashcard> cards){
        List<Long> ids=new ArrayList<>();for(LocalStore.Flashcard f:cards)ids.add(f.id);return ids;
    }

    List<LocalStore.Flashcard> eligibleSessionCards(){
        List<LocalStore.Flashcard> items=source.flashcards().flashcards(selectedDeck);
        if(!cardFromUnified)return items;
        List<LocalStore.Flashcard> singles=new ArrayList<>();
        for(LocalStore.Flashcard card:items)if(card.bookId==0 ||
            (card.firstChapter==card.lastChapter && card.firstVerse==card.lastVerse))
            singles.add(card);
        return singles;
    }

    boolean startCardSession(){
        List<LocalStore.Flashcard> all=eligibleSessionCards();
        if(all.isEmpty())return false;
        List<Long> available=cardIds(all);
        cardSequence.clear();cardIndex=0;
        String saved=source.preferences().getString("deck_"+selectedDeck,"");
        if(!saved.isEmpty())try{
            JSONObject json=new JSONObject(saved);JSONArray a=json.getJSONArray("order");
            for(int i=0;i<a.length();i++)cardSequence.add(a.getLong(i));
            cardIndex=Math.max(0,Math.min(cardSequence.size()-1,json.optInt("cursor",0)));
            cardShowReference=json.optBoolean("reference_first",true);
        }catch(Exception ignored){cardSequence.clear();}
        if(cardSequence.size()!=available.size()||
            !new HashSet<>(cardSequence).equals(new HashSet<>(available))){
            cardSequence.clear();cardSequence.addAll(FlashcardDeckLogic.shuffled(available,-1,cardRandom));
            cardIndex=0;cardShowReference=cardRandom.nextBoolean();
        }
        cardRevealed=false;saveCardSession();return true;
    }

    void saveCardSession(){
        if(selectedDeck<1||cardSequence.isEmpty())return;
        try{
            JSONObject json=new JSONObject();json.put("order",new JSONArray(cardSequence));
            json.put("cursor",cardIndex);json.put("reference_first",cardShowReference);
            source.preferences().edit()
                .putString("deck_"+selectedDeck,json.toString()).apply();
        }catch(JSONException ignored){}
    }

    boolean nextFlashcard(){
        if(cardSequence.isEmpty())return false;
        if(cardIndex+1<cardSequence.size())cardIndex++;
        else{
            long previous=cardSequence.get(cardSequence.size()-1);
            List<Long> all=cardIds(eligibleSessionCards());
            cardSequence.clear();cardSequence.addAll(FlashcardDeckLogic.shuffled(all,previous,cardRandom));
            cardIndex=0;
        }
        cardRevealed=false;cardShowReference=cardRandom.nextBoolean();
        saveCardSession();return true;
    }

    /** Activity/process recreation snapshot; durable progress still belongs to LocalStore. */
    Bundle saveState(){
        Bundle state=new Bundle();
        state.putBoolean("cardFromUnified",cardFromUnified);
        state.putLong("selectedDeck",selectedDeck);
        SessionSnapshot.putLongs(state,"cardSequence",cardSequence);
        state.putInt("cardIndex",cardIndex);
        state.putBoolean("cardShowReference",cardShowReference);
        state.putBoolean("cardRevealed",cardRevealed);
        SessionSnapshot.putLongs(state,"cardStudyQueue",cardStudyQueue);
        state.putInt("cardStudyCursor",cardStudyCursor);
        state.putLong("cardStudyDeckId",cardStudyDeckId);
        return state;
    }
    void restoreState(Bundle state){
        if(state==null)return;
        cardFromUnified=state.getBoolean("cardFromUnified",cardFromUnified);
        selectedDeck=state.getLong("selectedDeck",selectedDeck);
        SessionSnapshot.getLongs(state,"cardSequence",cardSequence);
        cardIndex=state.getInt("cardIndex",cardIndex);
        cardShowReference=state.getBoolean("cardShowReference",cardShowReference);
        cardRevealed=state.getBoolean("cardRevealed",cardRevealed);
        SessionSnapshot.getLongs(state,"cardStudyQueue",cardStudyQueue);
        cardStudyCursor=state.getInt("cardStudyCursor",cardStudyCursor);
        cardStudyDeckId=state.getLong("cardStudyDeckId",cardStudyDeckId);
        List<Long> available=cardIds(eligibleSessionCards());
        if(!cardSequence.isEmpty()&&!available.containsAll(cardSequence)){
            cardSequence.retainAll(available);cardRevealed=false;
        }cardIndex=Math.max(0,Math.min(cardIndex,Math.max(0,cardSequence.size()-1)));
    }
}
