package org.biblememory.app;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Lesson/checkpoint state and transitions; Views remain with the host Activity. */
final class StudySessionController {
    interface Ports {
        LocalStore store();
        SharedPreferences studyPreferences();
        SharedPreferences runPreferences();
        SharedPreferences gapPreferences();
        boolean collectionQueueActive();
        int lessonTileLimit();
        void render();
        void toast(String message);
    }
    private final Ports ports;
    StudySessionController(Ports ports){this.ports=ports;}
    long reviewCollectionGroup = -1;
    final List<RunLine> runLines = new ArrayList<>();
    long runCollectionId = -1;
    int runCursor = 0;
    int runCorrect = 0;
    final List<String> runMistakes = new ArrayList<>();
    String runDraft = "";
    boolean runChecked = false;
    boolean runPassed = false;
    int mask = 0;
    int stepCursor = 0;
    StudyPlanLogic.Stage studyStage = StudyPlanLogic.Stage.VERSE;
    long selectedEntry = 0;
    boolean checked = false;
    boolean passed = false;
    TrainingMode trainingMode = TrainingMode.READ;
    int orderChunk = 0;
    int orderPosition = 0;
    int orderWrongTile = -1;
    int orderMistakes = 0;
    int[] orderBankOrder = new int[0];
    final List<Integer> usedWordTiles = new ArrayList<>();
    boolean testAssisted = false;
    String draft = "";
    String lastExpected = "";
    String lastTestRef = "";
    String lastTestStep = "";
    int lastAccuracy = -1;
    LocalStore.Entry activeEntry;
    StudyPlanLogic.Step activeStep;
    boolean activeMastered;
    boolean reviewMode = false;
    boolean groupMode = false;
    int returnVerseCursor = 0;
    StudyPlanLogic.Step forcedStep;
    LocalStore.DueItem currentReview;
    int blurSeed = new Random().nextInt();
    boolean orderCompleted = false;
    int lessonVariant = 0;
    int lessonChunk = 0;
    int lessonPicked = 0;
    int lessonWrongTile = -1;
    long lessonSeed = new Random().nextLong();
    final List<Integer> lessonUsedTiles = new ArrayList<>();
    boolean lessonPracticeAgain = false;
    boolean lessonWriteHadFeedback = false;
    final List<LocalStore.DueItem> reviewQueue = new ArrayList<>();
    int reviewPointer = 0;
    int lessonBuildRequired = 0;
    int weakVerse = -1;
    int weakTransitionFrom = -1;
    int lessonWriteVariant = 0;
    boolean groupBuildDone = false;
    boolean groupIntroDone = false;
    String[] inlineDrafts = new String[0];
    final List<String> inlineExpected = new ArrayList<>();
    boolean practiceWeakFragment = false;
    String weakFragmentText = "";
    String rotationExerciseKey = "";
    String rotationMask = "";
    long rotationExerciseSeed = 0L;

    static final class RunLine {
        final long entryId;final int position;final String reference,text;
        RunLine(long entryId,int position,String reference,String text){
            this.entryId=entryId;this.position=position;this.reference=reference;this.text=text;
        }
    }

    enum TrainingMode {READ, HIDE, ORDER, TYPE}

    void saveTrainingSession() {
        if(selectedEntry<=0 || (reviewMode&&checked&&passed))return;
        String key=reviewMode?"review_":(groupMode?"linked_":"entry_")+selectedEntry+"_";
        ports.studyPreferences().edit()
            .putLong("entry",selectedEntry).putString(key+"stage",studyStage.name())
            .putInt(key+"step",stepCursor).putString(key+"mode",trainingMode.name())
            .putInt(key+"mask",mask).putString(key+"draft",draft)
            .putInt(key+"blur_seed",blurSeed)
            .putInt(key+"lesson_variant",lessonVariant).putInt(key+"lesson_chunk",lessonChunk)
            .putInt(key+"lesson_picked",lessonPicked).putLong(key+"lesson_seed",lessonSeed)
            .putString(key+"rotation_exercise_key",rotationExerciseKey)
            .putLong(key+"rotation_exercise_seed",rotationExerciseSeed)
            .putString(key+"rotation_mask",rotationMask)
            .putString(key+"lesson_tiles",lessonUsedTiles.toString())
            .putBoolean(key+"lesson_had_feedback",lessonWriteHadFeedback)
            .putInt(key+"write_variant",lessonWriteVariant)
            .putBoolean(key+"group_intro_done",groupIntroDone)
            .putBoolean(key+"group_build_done",groupBuildDone)
            .putBoolean(key+"practice_weak",practiceWeakFragment)
            .putString(key+"weak_fragment",weakFragmentText)
            .putBoolean(key+"pending",reviewMode||groupMode)
            .putLong(key+"review_entry",reviewMode?selectedEntry:0)
            .putString(key+"review_stage",reviewMode&&currentReview!=null?currentReview.stage:"")
            .putInt(key+"begin",activeStep==null?0:activeStep.start)
            .putInt(key+"end",activeStep==null?0:activeStep.end)
            .putString(key+"group_stage",studyStage.name())
            .putInt(key+"return_cursor",returnVerseCursor).apply();
    }

    boolean restoreTrainingSession(long id) {return restoreTrainingSession(id,false);}

    boolean restoreTrainingSession(long id,boolean linked) {
        android.content.SharedPreferences pref=ports.studyPreferences();
        String key=(linked?"linked_":"entry_")+id+"_";
        if(!pref.contains(key+"stage"))return false;
        try {
            studyStage=StudyPlanLogic.Stage.valueOf(pref.getString(key+"stage","VERSE"));
            trainingMode=TrainingMode.valueOf(pref.getString(key+"mode","READ"));
        }catch(IllegalArgumentException ex){studyStage=StudyPlanLogic.Stage.VERSE;trainingMode=TrainingMode.READ;}
        stepCursor=Math.max(0,pref.getInt(key+"step",0));
        mask=trainingMode==TrainingMode.HIDE?Math.max(0,Math.min(100,pref.getInt(key+"mask",0))):0;
        blurSeed=pref.getInt(key+"blur_seed",new Random().nextInt());
        draft=pref.getString(key+"draft","");
        if(draft==null)draft="";
        lessonVariant=Math.max(0,pref.getInt(key+"lesson_variant",0));
        lessonChunk=Math.max(0,pref.getInt(key+"lesson_chunk",0));
        lessonPicked=Math.max(0,pref.getInt(key+"lesson_picked",0));
        lessonSeed=pref.getLong(key+"lesson_seed",new Random().nextLong());
        rotationExerciseKey=pref.getString(key+"rotation_exercise_key","");
        rotationExerciseSeed=pref.getLong(key+"rotation_exercise_seed",0L);
        rotationMask=pref.getString(key+"rotation_mask","");
        lessonUsedTiles.clear();lessonWrongTile=-1;lessonPracticeAgain=false;
        lessonWriteHadFeedback=pref.getBoolean(key+"lesson_had_feedback",false);
        lessonWriteVariant=Math.max(0,pref.getInt(key+"write_variant",0));
        if(linked)groupIntroDone=pref.getBoolean(key+"group_intro_done",false);
        if(linked)groupBuildDone=pref.getBoolean(key+"group_build_done",false);
        practiceWeakFragment=pref.getBoolean(key+"practice_weak",false);
        weakFragmentText=pref.getString(key+"weak_fragment","");
        String saved=pref.getString(key+"lesson_tiles","").replace("[", "").replace("]", "");
        for(String part:saved.split(","))try{lessonUsedTiles.add(Integer.parseInt(part.trim()));}
            catch(NumberFormatException ignored){}
        checked=false;passed=false;testAssisted=false;lastAccuracy=-1;resetWordTiles();
        return true;
    }

    void resetWordTiles() {
        orderChunk=0;orderPosition=0;orderWrongTile=-1;orderMistakes=0;
        orderBankOrder=new int[0];usedWordTiles.clear();orderCompleted=false;
    }

    AdaptiveLearningLogic.Recommendation selectRecommendation(LocalStore.Entry e) {
        List<String> refs=new ArrayList<>();
        for(Bible.Verse v:e.lines())refs.add(v.ref);
        AdaptiveLearningLogic.Recommendation advice=AdaptiveLearningLogic.recommend(
            refs,isSermon(e),ports.store().progressFor(e.id),System.currentTimeMillis());
        studyStage=advice.stage;stepCursor=advice.index;
        return advice;
    }

    boolean isSermon(LocalStore.Entry e) {
        return e.reference!=null && (e.reference.startsWith("Матфея 5:") ||
          e.reference.startsWith("Матфея 6:") || e.reference.startsWith("Матфея 7:"));
    }

    List<StudyPlanLogic.Step> studySteps(LocalStore.Entry e) {
        if((reviewMode||groupMode)&&forcedStep!=null)return Collections.singletonList(forcedStep);
        List<String> refs=new ArrayList<>();
        for(Bible.Verse v:e.lines())refs.add(v.ref);
        return StudyPlanLogic.build(refs,studyStage,isSermon(e));
    }

    void resetTrainingStep(LocalStore.Entry e) {
        lessonVariant=0;lessonChunk=0;lessonPicked=0;lessonWrongTile=-1;lessonWriteVariant=0;groupBuildDone=false;groupIntroDone=false;
        practiceWeakFragment=false;weakFragmentText="";
        lessonSeed=new Random().nextLong();lessonUsedTiles.clear();lessonPracticeAgain=false;
        rotationExerciseKey="";rotationMask="";rotationExerciseSeed=0L;
        lessonWriteHadFeedback=false;
        mask=0;checked=false;passed=false;draft="";lastAccuracy=-1;
        blurSeed=new Random().nextInt();testAssisted=false;resetWordTiles();
        boolean mastered=false;
        if(e!=null) {
            List<StudyPlanLogic.Step> steps=studySteps(e);
            if(!steps.isEmpty()) {
                stepCursor=Math.max(0,Math.min(stepCursor,steps.size()-1));
                StudyPlanLogic.Step s=steps.get(stepCursor);
                mastered=ports.store().isStepMastered(e.id,studyStage.name(),s.start,s.end);
            }
        }
        int done=e!=null && studyStage==StudyPlanLogic.Stage.VERSE?
            ports.store().exerciseDone(e.id,stepCursor):0;
        trainingMode=done>=4?TrainingMode.TYPE:
            done==3?TrainingMode.TYPE:done==2?TrainingMode.ORDER:
            done==1?TrainingMode.HIDE:TrainingMode.READ;
        if(done==1)mask=25;
        // A final examination becomes assisted only if a hint is viewed DURING that exam.
        if(reviewMode || (studyStage==StudyPlanLogic.Stage.VERSE&&done>=4))testAssisted=false;
    }

    void moveStep(LocalStore.Entry e,int delta) {
        List<StudyPlanLogic.Step> steps=studySteps(e);
        if(steps.isEmpty())return;
        int target=Math.max(0,Math.min(steps.size()-1,stepCursor+delta));
        if(!reviewMode&&!groupMode&&studyStage==StudyPlanLogic.Stage.VERSE &&
            !LessonFlowLogic.canOpen(target,ports.store().unlockedPosition(e.id,e.lines().size()),e.lines().size())){
            ports.toast("Следующий стих откроется после контрольной проверки");return;
        }
        stepCursor=target;
        resetTrainingStep(e);saveTrainingSession();ports.render();
    }

    String texts(List<Bible.Verse> lines) {
        return PassageText.join(lines);
    }
    void clearRunHistory(long collection){
        String key=runKey(collection);
        ports.runPreferences().edit().remove("run_history_"+collection).remove(key+"active")
            .remove(key+"cursor").remove(key+"correct").remove(key+"draft")
            .remove(key+"checked").remove(key+"passed").remove(key+"missed").apply();
        if(runCollectionId==collection){runLines.clear();runCollectionId=-1;}
    }

    void checkAnswer(LocalStore.Entry e,StudyPlanLogic.Step step,boolean mastered) {
        String expected=texts(e.lines().subList(step.start,step.end));
        int accuracy=ReviewLogic.accuracy(expected,draft);
        boolean finalStage=!reviewMode&&!groupMode && studyStage==StudyPlanLogic.Stage.VERSE &&
            ports.store().exerciseDone(e.id,step.start)==4;
        boolean independent=reviewMode||groupMode||finalStage;
        passed=LessonCurriculumLogic.exact(expected,draft) && (!independent||!testAssisted);
        lastAccuracy=accuracy;lastExpected=expected;lastTestRef=step.title;
        lastTestStep=independent?"КОНТРОЛЬНАЯ ПРОВЕРКА":"ТРЕНИРОВКА";
        long now=System.currentTimeMillis();
        ports.store().recordRecall(e.id,studyStage.name(),step.start,step.end,accuracy,
            testAssisted||!independent,now);
        if(independent){
            ports.store().scheduleReview(e.id,studyStage.name(),step.start,step.end,accuracy,testAssisted,now);
            if(passed&&!mastered)ports.store().markMastered(e.id,step.start,step.end,accuracy,studyStage.name());
        }
        if(!reviewMode&&!groupMode&&studyStage==StudyPlanLogic.Stage.VERSE){
            int done=ports.store().exerciseDone(e.id,step.start);
            if(done==3&&passed)ports.store().finishExercise(e.id,step.start,4);
            else if(done==4&&passed)ports.store().finishExercise(e.id,step.start,5);
        }
        int learned=ports.store().masteryCount(e.id);
        ReviewLogic.Schedule unchanged=new ReviewLogic.Schedule(e.repetitions,e.days,e.ease,e.due);
        // Per-verse and grouped schedules are now authoritative; do not update legacy entry-wide schedule.
        ports.store().update(e,unchanged,learned,accuracy);
        checked=true;saveTrainingSession();ports.render();
    }

    int bookForEntry(LocalStore.Entry e){
        if(e==null||e.reference==null)return -1;
        for(int n=1;n<=66;n++)if(e.reference.startsWith(Bible.NAMES[n-1]+" "))return n;
        return -1;
    }

    GroupLearningLogic.Candidate nextGroup(LocalStore.Entry e){
        List<String> refs=new ArrayList<>();for(Bible.Verse line:e.lines())refs.add(line.ref);
        return GroupLearningLogic.next(refs,new GroupLearningLogic.Progress(){
            @Override public boolean verseMastered(int i){return ports.store().isStepMastered(e.id,"VERSE",i,i+1);}
            @Override public boolean groupMastered(String stage,int a,int b){return ports.store().isStepMastered(e.id,stage,a,b);}
        },isSermon(e));
    }

    GroupLearningLogic.Candidate pendingAfterCurrent(LocalStore.Entry e,int current){
        if(current<0||current>=e.lines().size())return null;
        List<String> refs=new ArrayList<>();for(Bible.Verse v:e.lines())refs.add(v.ref);
        return GroupLearningLogic.afterVerse(refs,new GroupLearningLogic.Progress(){
            @Override public boolean verseMastered(int i){return ports.store().isStepMastered(e.id,"VERSE",i,i+1);}
            @Override public boolean groupMastered(String stage,int a,int b){return ports.store().isStepMastered(e.id,stage,a,b);}
        },current,isSermon(e));
    }

    LessonHotbarLogic.Model lessonHotbarModel(LessonCurriculumLogic.Phase phase,
                                                      LocalStore.Entry e){
        List<Bible.Verse> passage=e.lines().subList(activeStep.start,activeStep.end);
        List<String> source=new ArrayList<>();
        for(Bible.Verse line:passage)source.add(line.text);
        int chunks=Math.max(1,lessonChunks(source).size());
        int variants=Math.max(1,LessonCurriculumLogic.variants(
            ReviewLogic.words(texts(passage)).size()).size());
        int buildTotal=chunks*variants;
        int currentBuild=Math.max(0,lessonVariant*chunks+lessonChunk);
        int buildDone=currentBuild;
        if(phase==LessonCurriculumLogic.Phase.BUILD && lessonBuildRequired>0 &&
            lessonPicked>=lessonBuildRequired)buildDone++;
        int writingTotal=Math.max(1,writingVariants(e).size());
        // In a scheduled review the lesson legitimately starts at writing.
        boolean previouslyLearned=ports.collectionQueueActive() &&
            studyStage==StudyPlanLogic.Stage.VERSE &&
            ports.store().exerciseDone(e.id,activeStep.start)>=5;
        boolean writingOnly=!groupMode && (reviewMode || lessonPracticeAgain ||
            studyStage!=StudyPlanLogic.Stage.VERSE || previouslyLearned);
        return LessonHotbarLogic.calculate(phase.ordinal(),4,buildTotal,writingTotal,
            mask/25,buildDone,lessonWriteVariant,writingOnly);
    }

    LessonCurriculumLogic.Phase lessonPhase(LocalStore.Entry e){
        if(groupMode)return !groupIntroDone?LessonCurriculumLogic.Phase.INTRO:
            (groupBuildDone?LessonCurriculumLogic.Phase.WRITE:LessonCurriculumLogic.Phase.BUILD);
        boolean learnedBefore=ports.collectionQueueActive()&&
            studyStage==StudyPlanLogic.Stage.VERSE &&
            ports.store().exerciseDone(e.id,activeStep.start)>=5;
        return LessonCurriculumLogic.phase(ports.store().exerciseDone(e.id,activeStep.start),
            reviewMode||lessonPracticeAgain||studyStage!=StudyPlanLogic.Stage.VERSE||learnedBefore);
    }

    void resetLessonTiles(){
        lessonPicked=0;lessonWrongTile=-1;lessonUsedTiles.clear();lessonSeed=new Random().nextLong();
    }

    List<String[]> lessonChunks(List<String> lines){
        return WordOrderLogic.chunksForBudget(lines,ports.lessonTileLimit());
    }

    int[] lessonMissingPositions(String[] tokens,LessonCurriculumLogic.Variant kind){
        if(kind!=LessonCurriculumLogic.Variant.GAPS)
            return LessonCurriculumLogic.missingPositions(tokens.length,kind,(int)lessonSeed);
        // Use the original fragment text as a stable identity. If that text changes,
        // its history changes too; do not combine positions from different verses.
        String historyKey=GapRotationLogic.identity(tokens);
        String exerciseKey=historyKey+":"+(groupMode?"group": "verse")+
            ":"+(trainingMode==TrainingMode.TYPE?"write":"build");
        if(exerciseKey.equals(rotationExerciseKey)&&rotationExerciseSeed==lessonSeed){
            int[] cached=GapRotationLogic.decodeMask(rotationMask,tokens.length);
            if(cached!=null)return cached;
        }
        android.content.SharedPreferences prefs=ports.gapPreferences();
        int[] counts=GapRotationLogic.decodeCounts(prefs.getString(historyKey,""),tokens.length);
        int amount=GapRotationLogic.amount(tokens.length);
        int[] positions=GapRotationLogic.balancedPositions(counts,amount,lessonSeed);
        prefs.edit().putString(historyKey,GapRotationLogic.encodeCounts(counts)).apply();
        rotationExerciseKey=exerciseKey;rotationExerciseSeed=lessonSeed;
        rotationMask=GapRotationLogic.encodeMask(positions);
        saveTrainingSession();
        return positions;
    }

    void advanceLessonBuild(LocalStore.Entry e){
        List<String> source=new ArrayList<>();
        for(Bible.Verse v:e.lines().subList(activeStep.start,activeStep.end))source.add(v.text);
        int chunkCount=lessonChunks(source).size();
        int variants=LessonCurriculumLogic.variants(ReviewLogic.words(texts(
            e.lines().subList(activeStep.start,activeStep.end))).size()).size();
        if(lessonChunk+1<chunkCount){lessonChunk++;resetLessonTiles();}
        else if(lessonVariant+1<variants){
            if(!groupMode)ports.store().finishExercise(e.id,activeStep.start,2);
            lessonVariant++;lessonChunk=0;resetLessonTiles();
        } else {
            if(groupMode)groupBuildDone=true;
            else ports.store().finishExercise(e.id,activeStep.start,3);
            lessonVariant=0;lessonChunk=0;lessonWriteVariant=0;resetLessonTiles();draft="";lastAccuracy=-1;
            lessonWriteHadFeedback=false;
        }
        saveTrainingSession();ports.render();
    }

    List<LessonCurriculumLogic.Variant> writingVariants(LocalStore.Entry e){
        int n=ReviewLogic.words(texts(e.lines().subList(activeStep.start,activeStep.end))).size();
        if(reviewMode||lessonPracticeAgain||practiceWeakFragment)
            return Collections.singletonList(LessonCurriculumLogic.Variant.FULL);
        return LessonCurriculumLogic.variants(n);
    }

    String writingExpected(LocalStore.Entry e,LessonCurriculumLogic.Variant variant){
        String original=texts(e.lines().subList(activeStep.start,activeStep.end));
        if(practiceWeakFragment)return weakFragmentText;
        if(variant==LessonCurriculumLogic.Variant.FULL)return original;
        String[] tokens=original.trim().split("\\s+");
        int[] gaps=lessonMissingPositions(tokens,variant);
        StringBuilder response=new StringBuilder();
        for(int position:gaps){if(response.length()>0)response.append(' ');response.append(tokens[position]);}
        return response.toString();
    }

    static String weakPhrase(String expected,String actual){
        List<TypingDiffLogic.Issue> errors=TypingDiffLogic.issues(expected,actual);
        if(errors.isEmpty())return "";
        List<String> words=ReviewLogic.words(expected);
        if(words.isEmpty())return "";
        int where=0;
        String target=errors.get(0).expected;
        if(!target.isEmpty())for(int i=0;i<words.size();i++)if(words.get(i).equals(target)){
            where=i;break;
        }
        int a=Math.max(0,where-2),b=Math.min(words.size(),where+3);
        return String.join(" ",words.subList(a,b));
    }

    String runKey(long collection){return "writing_run_"+collection+"_";}

    static String runVerseKey(LocalStore.Entry e,Bible.Verse v){
        boolean canonical=false;
        for(String book:Bible.NAMES)if(e.reference.startsWith(book+" ")){canonical=true;break;}
        String bookName="";
        if(canonical)for(String book:Bible.NAMES)if(e.reference.startsWith(book+" ")){
            bookName=book;break;
        }
        return canonical?bookName+"|"+v.ref+"|"+v.text:
            "custom:"+e.id+":"+v.ref;
    }

    void buildRunLines(LocalStore.UnifiedCollection u){
        runLines.clear();
        Map<String,Boolean> confirmed=new HashMap<>();
        for(LocalStore.Entry e:ports.store().allEntries()){
            List<Bible.Verse> lines=e.lines();
            for(int i=0;i<lines.size();i++)if(ports.store().consolidated(e.id,"VERSE",i,i+1))
                confirmed.put(runVerseKey(e,lines.get(i)),true);
        }
        Set<String> seen=new HashSet<>();
        for(LocalStore.Entry e:ports.store().entries(u.groupId)){
            int book=bookForEntry(e);
            List<Bible.Verse> lines=e.lines();
            for(int i=0;i<lines.size();i++){
                Bible.Verse line=lines.get(i);String key=runVerseKey(e,line);
                if(!seen.add(key) || !confirmed.containsKey(key))continue;
                String reference=book>0?Bible.NAMES[book-1]+" "+line.ref:
                    e.reference+(lines.size()>1?" · "+(i+1):"");
                runLines.add(new RunLine(e.id,i,reference,line.text));
            }
        }
    }

    void resetRun(){
        runCursor=0;runCorrect=0;runMistakes.clear();runChecked=false;runPassed=false;runDraft="";
        saveRunSession();
    }

    void restoreRunSession(){
        String key=runKey(runCollectionId);
        android.content.SharedPreferences p=ports.runPreferences();
        runCursor=Math.min(Math.max(0,p.getInt(key+"cursor",0)),Math.max(0,runLines.size()-1));
        runCorrect=p.getInt(key+"correct",0);
        runDraft=p.getString(key+"draft","");
        runChecked=p.getBoolean(key+"checked",false);
        runPassed=p.getBoolean(key+"passed",false);
        runMistakes.clear();
        try{JSONArray arr=new JSONArray(p.getString(key+"missed","[]"));
            for(int i=0;i<arr.length();i++)runMistakes.add(arr.getString(i));
        }catch(Exception ignored){}
    }

    void saveRunSession(){
        if(runCollectionId<0||runLines.isEmpty())return;
        String key=runKey(runCollectionId);
        ports.runPreferences().edit()
            .putBoolean(key+"active",true).putInt(key+"cursor",runCursor)
            .putInt(key+"correct",runCorrect).putString(key+"draft",runDraft)
            .putBoolean(key+"checked",runChecked).putBoolean(key+"passed",runPassed)
            .putString(key+"missed",new JSONArray(runMistakes).toString()).apply();
    }

    void saveCompletedRun(){
        try{
            android.content.SharedPreferences p=ports.runPreferences();
            String historyKey="run_history_"+runCollectionId;
            JSONArray history=new JSONArray(p.getString(historyKey,"[]"));
            JSONObject result=new JSONObject();
            result.put("timestamp",System.currentTimeMillis());
            result.put("total",runLines.size());result.put("correct",runCorrect);
            result.put("mistakes",new JSONArray(runMistakes));
            JSONArray updated=new JSONArray();updated.put(result);
            for(int i=0;i<Math.min(history.length(),19);i++)updated.put(history.getJSONObject(i));
            p.edit().putString(historyKey,updated.toString()).apply();
        }catch(JSONException ignored){}
    }
}
