package org.biblememory.app;
import android.os.Bundle;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Lesson/checkpoint state and transitions; Views remain with the host Activity. */
final class StudySessionController {
    interface Ports {
        StudyRepository study(); ProgressRepository progress(); CollectionRepository collections();
        SharedPreferences studyPreferences();
        SharedPreferences runPreferences();
        SharedPreferences gapPreferences();
        boolean collectionQueueActive();
        int lessonTileLimit();
        Screen screen();
        void navigate(Screen destination);
        boolean collectionStepPending();
        void collectionStepStarted();
        boolean collectionQueueReady();
        boolean cardStudyActive();
        default long studyDeckId(){return -1;}
        void advanceCollection();
        void advanceCardStudy();
        void saveCardCheckpoint();
        void leaveLesson();
        void writingErrors(LocalStore.Entry entry,String expected,String actual,boolean detailed);
        void markInlineAnswers(boolean[] correct);
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
    long sessionGroup = -1;
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
            .putLong("entry",selectedEntry).putLong(key+"session_group",sessionGroup).putString(key+"stage",studyStage.name())
            .putInt(key+"step",stepCursor).putString(key+"mode",trainingMode.name())
            .putInt(key+"mask",mask).putString(key+"draft",draft)
            .putString(key+"inline_drafts",new JSONArray(Arrays.asList(inlineDrafts)).toString())
            .putBoolean(key+"assisted",testAssisted)
            .putBoolean(key+"checked",checked).putBoolean(key+"passed",passed)
            .putInt(key+"accuracy",lastAccuracy).putString(key+"expected",lastExpected)
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
            .putLong(key+"review_due",reviewMode&&currentReview!=null?currentReview.due:0)
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
        sessionGroup=pref.getLong(key+"session_group",sessionGroup);
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
        checked=pref.getBoolean(key+"checked",false);passed=pref.getBoolean(key+"passed",false);
        testAssisted=pref.getBoolean(key+"assisted",false);
        lastAccuracy=pref.getInt(key+"accuracy",-1);lastExpected=pref.getString(key+"expected","");
        try{JSONArray values=new JSONArray(pref.getString(key+"inline_drafts","[]"));
            inlineDrafts=new String[values.length()];for(int i=0;i<values.length();i++)inlineDrafts[i]=values.getString(i);
        }catch(JSONException invalid){inlineDrafts=new String[0];}
        resetWordTiles();
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
            refs,isSermon(e),ports.progress().progressFor(e.id),System.currentTimeMillis());
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
                mastered=ports.progress().isStepMastered(e.id,studyStage.name(),s.start,s.end);
            }
        }
        int done=e!=null && studyStage==StudyPlanLogic.Stage.VERSE?
            ports.progress().exerciseDone(e.id,stepCursor):0;
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
            !LessonFlowLogic.canOpen(target,ports.progress().unlockedPosition(e.id,e.lines().size()),e.lines().size())){
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
        // Retain completed diagnostic results; invalidate only this collection's checkpoint.
        ports.runPreferences().edit().remove(key+"active")
            .remove(key+"cursor").remove(key+"correct").remove(key+"draft")
            .remove(key+"checked").remove(key+"passed").remove(key+"missed").apply();
        if(runCollectionId==collection){runLines.clear();runCollectionId=-1;}
    }

    boolean finishExercise(long entry,int position,int completed){
        try{ports.progress().finishExerciseForSession(entry,position,completed,sessionGroup);return true;}
        catch(android.database.sqlite.SQLiteException|IllegalStateException error){
            android.util.Log.e("StudySession","Milestone not committed; error="+error.getClass().getSimpleName());
            ports.toast("Не удалось сохранить результат упражнения");return false;
        }
    }

    boolean validSession(){
        LocalStore.Entry entry=ports.study().entry(selectedEntry);if(entry==null)return false;
        StudyPlanLogic.Step step=forcedStep!=null?forcedStep:activeStep;
        int start=step==null?0:step.start,end=step==null?entry.lines().size():step.end;
        return ports.collections().validSession(selectedEntry,studyStage.name(),start,end,sessionGroup);
    }
    boolean requireValidSession(){
        if(validSession())return true;
        checked=false;passed=false;selectedEntry=0;activeEntry=null;activeStep=null;
        reviewQueue.clear();reviewPointer=0;reviewMode=false;groupMode=false;forcedStep=null;
        ports.studyPreferences().edit().remove("review_pending").apply();
        ports.toast("Материал больше не входит в коллекцию");ports.navigate(Screen.HOME);return false;
    }

    private boolean combinedReview(){return reviewMode&&currentReview!=null&&"VERSE_RUN".equals(currentReview.stage);}

    /** Persist identity BEFORE SQLite, so replay after process death uses the same receipt.
     * A new answer/exercise/review due date gets a new timestamp; an unchanged callback reuses it. */
    private long attemptTime(LocalStore.Entry e,StudyPlanLogic.Step step,String kind,String answer){
        String raw=e.id+"|"+e.reference+"|"+e.linesJson+"|"+studyStage+"|"+step.start+"|"+step.end+
            "|"+lessonSeed+"|"+(currentReview==null?0:currentReview.due)+"|"+testAssisted+"|"+kind+"|"+answer;
        String identity;
        try{
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();for(byte value:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",value));
            identity=hex.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        saveTrainingSession();
        SharedPreferences prefs=ports.studyPreferences();
        if(identity.equals(prefs.getString("attempt_identity","")))return prefs.getLong("attempt_time",0);
        long now=Math.max(System.currentTimeMillis(),prefs.getLong("attempt_time",0)+1);
        if(!prefs.edit().putString("attempt_identity",identity).putLong("attempt_time",now).commit())
            throw new IllegalStateException("Cannot save study attempt checkpoint");
        return now;
    }

    void checkAnswer(LocalStore.Entry e,StudyPlanLogic.Step step,boolean mastered){
        try{checkAnswerInternal(e,step,mastered);}
        catch(android.database.sqlite.SQLiteException | IllegalStateException error){
            android.util.Log.e("StudySession","Answer result was not committed",error);
            ports.toast("Не удалось сохранить ответ. Открой занятие заново или повтори попытку.");
        }
    }
    private void checkAnswerInternal(LocalStore.Entry e,StudyPlanLogic.Step step,boolean mastered){
        if(!requireValidSession()||checked)return;
        String expected=texts(e.lines().subList(step.start,step.end));
        StudyAnswerEvaluation evaluation=StudyAnswerEvaluation.recall(expected,draft);
        int accuracy=evaluation.accuracy;
        boolean finalStage=!reviewMode&&!groupMode && studyStage==StudyPlanLogic.Stage.VERSE &&
            ports.progress().exerciseDone(e.id,step.start)==4;
        boolean independent=reviewMode||groupMode||finalStage;
        boolean accepted=evaluation.exact && (!independent||!testAssisted);
        lastAccuracy=accuracy;lastExpected=expected;lastTestRef=step.title;
        lastTestStep=independent?"КОНТРОЛЬНАЯ ПРОВЕРКА":"ТРЕНИРОВКА";
        new StudyProgressRecorder(ports.progress(),sessionGroup).recall(e,step,studyStage.name(),accuracy,
            testAssisted,independent,accepted,mastered,reviewMode,groupMode,
            attemptTime(e,step,"recall",draft));
        passed=accepted;checked=true;saveTrainingSession();ports.render();
    }

    int bookForEntry(LocalStore.Entry e){
        if(e==null)return -1;
        int book=StudyRepository.canonicalBook(e);return book==0?-1:book;
    }

    GroupLearningLogic.Candidate nextGroup(LocalStore.Entry e){
        List<String> refs=new ArrayList<>();for(Bible.Verse line:e.lines())refs.add(line.ref);
        return GroupLearningLogic.next(refs,new GroupLearningLogic.Progress(){
            @Override public boolean verseMastered(int i){return ports.progress().isStepMastered(e.id,"VERSE",i,i+1);}
            @Override public boolean groupMastered(String stage,int a,int b){return ports.progress().isStepMastered(e.id,stage,a,b);}
        },isSermon(e));
    }

    GroupLearningLogic.Candidate pendingAfterCurrent(LocalStore.Entry e,int current){
        if(current<0||current>=e.lines().size())return null;
        List<String> refs=new ArrayList<>();for(Bible.Verse v:e.lines())refs.add(v.ref);
        return GroupLearningLogic.afterVerse(refs,new GroupLearningLogic.Progress(){
            @Override public boolean verseMastered(int i){return ports.progress().isStepMastered(e.id,"VERSE",i,i+1);}
            @Override public boolean groupMastered(String stage,int a,int b){return ports.progress().isStepMastered(e.id,stage,a,b);}
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
            ports.progress().exerciseDone(e.id,activeStep.start)>=5;
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
            ports.progress().exerciseDone(e.id,activeStep.start)>=5;
        return LessonCurriculumLogic.phase(ports.progress().exerciseDone(e.id,activeStep.start),
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
        if(!requireValidSession())return;
        List<String> source=new ArrayList<>();
        for(Bible.Verse v:e.lines().subList(activeStep.start,activeStep.end))source.add(v.text);
        int chunkCount=lessonChunks(source).size();
        int variants=LessonCurriculumLogic.variants(ReviewLogic.words(texts(
            e.lines().subList(activeStep.start,activeStep.end))).size()).size();
        if(lessonChunk+1<chunkCount){lessonChunk++;resetLessonTiles();}
        else if(lessonVariant+1<variants){
            if(!groupMode&&!finishExercise(e.id,activeStep.start,2))return;
            lessonVariant++;lessonChunk=0;resetLessonTiles();
        } else {
            if(groupMode)groupBuildDone=true;
            else if(!finishExercise(e.id,activeStep.start,3))return;
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
        boolean canonical=StudyRepository.canonicalBook(e)>0;
        String bookName="";
        if(canonical)for(String book:Bible.NAMES)if(e.reference.startsWith(book+" ")){
            bookName=book;break;
        }
        return canonical?bookName+"|"+v.ref+"|"+v.text:
            "custom:"+e.id+":"+v.ref;
    }

    void buildRunLines(LocalStore.UnifiedCollection u){
        runLines.clear();
        if(u==null)return;
        Map<String,Boolean> confirmed=new HashMap<>();
        for(LocalStore.Entry e:ports.study().allEntries()){
            List<Bible.Verse> lines=e.lines();
            for(int i=0;i<lines.size();i++)if(ports.progress().consolidated(e.id,"VERSE",i,i+1))
                confirmed.put(runVerseKey(e,lines.get(i)),true);
        }
        Set<String> seen=new HashSet<>();
        for(LocalStore.Entry e:ports.study().entries(u.groupId)){
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
        if(!validRun())return;
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

    /** Activity/process recreation snapshot; durable progress still belongs to LocalStore. */
    Bundle saveState(){
        Bundle state=new Bundle();
        state.putLong("reviewCollectionGroup",reviewCollectionGroup);
        state.putLong("runCollectionId",runCollectionId);
        state.putInt("runCursor",runCursor);
        state.putInt("runCorrect",runCorrect);
        state.putStringArrayList("runMistakes",new ArrayList<>(runMistakes));
        state.putString("runDraft",runDraft);
        state.putBoolean("runChecked",runChecked);
        state.putBoolean("runPassed",runPassed);
        state.putInt("mask",mask);
        state.putInt("stepCursor",stepCursor);
        state.putString("studyStage",studyStage.name());
        state.putLong("selectedEntry",selectedEntry);state.putLong("sessionGroup",sessionGroup);
        state.putBoolean("checked",checked);
        state.putBoolean("passed",passed);
        state.putString("trainingMode",trainingMode.name());
        state.putInt("orderChunk",orderChunk);
        state.putInt("orderPosition",orderPosition);
        state.putInt("orderWrongTile",orderWrongTile);
        state.putInt("orderMistakes",orderMistakes);
        state.putIntArray("orderBankOrder",orderBankOrder.clone());
        state.putIntegerArrayList("usedWordTiles",new ArrayList<>(usedWordTiles));
        state.putBoolean("testAssisted",testAssisted);
        state.putString("draft",draft);
        state.putString("lastExpected",lastExpected);
        state.putString("lastTestRef",lastTestRef);
        state.putString("lastTestStep",lastTestStep);
        state.putInt("lastAccuracy",lastAccuracy);
        state.putBoolean("reviewMode",reviewMode);
        state.putBoolean("groupMode",groupMode);
        state.putInt("returnVerseCursor",returnVerseCursor);
        state.putInt("blurSeed",blurSeed);
        state.putBoolean("orderCompleted",orderCompleted);
        state.putInt("lessonVariant",lessonVariant);
        state.putInt("lessonChunk",lessonChunk);
        state.putInt("lessonPicked",lessonPicked);
        state.putInt("lessonWrongTile",lessonWrongTile);
        state.putLong("lessonSeed",lessonSeed);
        state.putIntegerArrayList("lessonUsedTiles",new ArrayList<>(lessonUsedTiles));
        state.putBoolean("lessonPracticeAgain",lessonPracticeAgain);
        state.putBoolean("lessonWriteHadFeedback",lessonWriteHadFeedback);
        state.putInt("reviewPointer",reviewPointer);
        state.putInt("lessonBuildRequired",lessonBuildRequired);
        state.putInt("weakVerse",weakVerse);
        state.putInt("weakTransitionFrom",weakTransitionFrom);
        state.putInt("lessonWriteVariant",lessonWriteVariant);
        state.putBoolean("groupBuildDone",groupBuildDone);
        state.putBoolean("groupIntroDone",groupIntroDone);
        state.putStringArray("inlineDrafts",inlineDrafts.clone());
        state.putStringArrayList("inlineExpected",new ArrayList<>(inlineExpected));
        state.putBoolean("practiceWeakFragment",practiceWeakFragment);
        state.putString("weakFragmentText",weakFragmentText);
        state.putString("rotationExerciseKey",rotationExerciseKey);
        state.putString("rotationMask",rotationMask);
        state.putLong("rotationExerciseSeed",rotationExerciseSeed);
        SessionSnapshot.putStep(state,"forcedStep",forcedStep);
        state.putBundle("currentReview",SessionSnapshot.due(currentReview));
        ArrayList<Bundle> reviews=new ArrayList<>();for(LocalStore.DueItem item:reviewQueue)reviews.add(SessionSnapshot.due(item));
        state.putParcelableArrayList("reviews",reviews);
        SessionSnapshot.putStep(state,"activeStep",activeStep);
        return state;
    }
    void restoreState(Bundle state){
        if(state==null)return;
        reviewCollectionGroup=state.getLong("reviewCollectionGroup",reviewCollectionGroup);
        runCollectionId=state.getLong("runCollectionId",runCollectionId);
        runCursor=state.getInt("runCursor",runCursor);
        runCorrect=state.getInt("runCorrect",runCorrect);
        runMistakes.clear();if(state.getStringArrayList("runMistakes")!=null)runMistakes.addAll(state.getStringArrayList("runMistakes"));
        runDraft=state.getString("runDraft",runDraft);
        runChecked=state.getBoolean("runChecked",runChecked);
        runPassed=state.getBoolean("runPassed",runPassed);
        mask=state.getInt("mask",mask);
        stepCursor=state.getInt("stepCursor",stepCursor);
        try{studyStage=StudyPlanLogic.Stage.valueOf(state.getString("studyStage",studyStage.name()));}catch(IllegalArgumentException ignored){}
        selectedEntry=state.getLong("selectedEntry",selectedEntry);sessionGroup=state.getLong("sessionGroup",-1);
        checked=state.getBoolean("checked",checked);
        passed=state.getBoolean("passed",passed);
        try{trainingMode=TrainingMode.valueOf(state.getString("trainingMode",trainingMode.name()));}catch(IllegalArgumentException ignored){}
        orderChunk=state.getInt("orderChunk",orderChunk);
        orderPosition=state.getInt("orderPosition",orderPosition);
        orderWrongTile=state.getInt("orderWrongTile",orderWrongTile);
        orderMistakes=state.getInt("orderMistakes",orderMistakes);
        if(state.containsKey("orderBankOrder"))orderBankOrder=state.getIntArray("orderBankOrder");
        usedWordTiles.clear();if(state.getIntegerArrayList("usedWordTiles")!=null)usedWordTiles.addAll(state.getIntegerArrayList("usedWordTiles"));
        testAssisted=state.getBoolean("testAssisted",testAssisted);
        draft=state.getString("draft",draft);
        lastExpected=state.getString("lastExpected",lastExpected);
        lastTestRef=state.getString("lastTestRef",lastTestRef);
        lastTestStep=state.getString("lastTestStep",lastTestStep);
        lastAccuracy=state.getInt("lastAccuracy",lastAccuracy);
        reviewMode=state.getBoolean("reviewMode",reviewMode);
        groupMode=state.getBoolean("groupMode",groupMode);
        returnVerseCursor=state.getInt("returnVerseCursor",returnVerseCursor);
        blurSeed=state.getInt("blurSeed",blurSeed);
        orderCompleted=state.getBoolean("orderCompleted",orderCompleted);
        lessonVariant=state.getInt("lessonVariant",lessonVariant);
        lessonChunk=state.getInt("lessonChunk",lessonChunk);
        lessonPicked=state.getInt("lessonPicked",lessonPicked);
        lessonWrongTile=state.getInt("lessonWrongTile",lessonWrongTile);
        lessonSeed=state.getLong("lessonSeed",lessonSeed);
        lessonUsedTiles.clear();if(state.getIntegerArrayList("lessonUsedTiles")!=null)lessonUsedTiles.addAll(state.getIntegerArrayList("lessonUsedTiles"));
        lessonPracticeAgain=state.getBoolean("lessonPracticeAgain",lessonPracticeAgain);
        lessonWriteHadFeedback=state.getBoolean("lessonWriteHadFeedback",lessonWriteHadFeedback);
        reviewPointer=state.getInt("reviewPointer",reviewPointer);
        lessonBuildRequired=state.getInt("lessonBuildRequired",lessonBuildRequired);
        weakVerse=state.getInt("weakVerse",weakVerse);
        weakTransitionFrom=state.getInt("weakTransitionFrom",weakTransitionFrom);
        lessonWriteVariant=state.getInt("lessonWriteVariant",lessonWriteVariant);
        groupBuildDone=state.getBoolean("groupBuildDone",groupBuildDone);
        groupIntroDone=state.getBoolean("groupIntroDone",groupIntroDone);
        if(state.containsKey("inlineDrafts"))inlineDrafts=state.getStringArray("inlineDrafts");
        inlineExpected.clear();if(state.getStringArrayList("inlineExpected")!=null)inlineExpected.addAll(state.getStringArrayList("inlineExpected"));
        practiceWeakFragment=state.getBoolean("practiceWeakFragment",practiceWeakFragment);
        weakFragmentText=state.getString("weakFragmentText",weakFragmentText);
        rotationExerciseKey=state.getString("rotationExerciseKey",rotationExerciseKey);
        rotationMask=state.getString("rotationMask",rotationMask);
        rotationExerciseSeed=state.getLong("rotationExerciseSeed",rotationExerciseSeed);
        forcedStep=SessionSnapshot.getStep(state,"forcedStep");currentReview=SessionSnapshot.due(state.getBundle("currentReview"));
        reviewQueue.clear();ArrayList<Bundle> reviews=state.getParcelableArrayList("reviews");
        if(reviews!=null)for(Bundle item:reviews)reviewQueue.add(SessionSnapshot.due(item));
        activeEntry=ports.study().entry(selectedEntry);activeStep=SessionSnapshot.getStep(state,"activeStep");
        if(!state.containsKey("sessionGroup")&&activeEntry!=null)sessionGroup=activeEntry.groupId;
        LocalStore.UnifiedCollection collection=ports.collections().unifiedCollection(runCollectionId);
        runLines.clear();if(collection!=null)buildRunLines(collection);
        runCursor=Math.max(0,Math.min(runCursor,runLines.size()));
    }
    void practiceFragment(String fragment){
        weakFragmentText=fragment;practiceWeakFragment=true;draft="";
        saveTrainingSession();ports.render();
    }

    void startReviewSession() {
        reviewQueue.clear();reviewPointer=0;
        List<ReviewQueueLogic.Item> raw=new ArrayList<>();
        Set<String> reviewSeen=new HashSet<>();
        for(LocalStore.DueItem due:ports.progress().dueItems(System.currentTimeMillis())){
            LocalStore.Entry source=ports.study().entry(due.entryId);
            if(source==null || (reviewCollectionGroup>0 &&
                !ports.collections().belongsToGroupMaterial(reviewCollectionGroup,due)))continue;
            // Across collections, check each identical Bible segment only once.
            String key="entry:"+due.entryId+":"+due.stage+":"+due.start+":"+due.end;
            List<Bible.Verse> lines=source.lines();
            if(bookForEntry(source)>0 && due.start>=0 && due.end<=lines.size() && due.start<due.end){
                StringBuilder unique=new StringBuilder("bible:").append(bookForEntry(source))
                    .append(':').append(due.stage);
                for(int i=due.start;i<due.end;i++)unique.append('|').append(lines.get(i).ref)
                    .append('~').append(lines.get(i).text);
                key=unique.toString();
            }
            if(reviewSeen.add(key))raw.add(new ReviewQueueLogic.Item(
                due.entryId,due.stage,due.start,due.end,due.due));
        }
        List<ReviewQueueLogic.Item> items=ReviewQueueLogic.plan(raw,new ReviewQueueLogic.Source(){
            @Override public List<String> references(long id){
                LocalStore.Entry e=ports.study().entry(id);List<String> refs=new ArrayList<>();
                if(e!=null)for(Bible.Verse v:e.lines())refs.add(v.ref);return refs;
            }
            @Override public List<String> texts(long id){
                LocalStore.Entry e=ports.study().entry(id);List<String> texts=new ArrayList<>();
                if(e!=null)for(Bible.Verse v:e.lines())texts.add(v.text);return texts;
            }
        });
        for(ReviewQueueLogic.Item item:items)
            reviewQueue.add(new LocalStore.DueItem(item.entryId,item.stage,item.start,item.end,item.due));
        android.content.SharedPreferences old=ports.studyPreferences();
        if(old.getBoolean("review_pending",false)){
            long id=old.getLong("review_review_entry",-1);
            LocalStore.Entry e=ports.study().entry(id);
            int a=old.getInt("review_begin",-1),b=old.getInt("review_end",-1);
            String stage=old.getString("review_review_stage","");
            if(e!=null && ports.collections().activeMaterial(new LocalStore.DueItem(id,stage,a,b,0)) && (reviewCollectionGroup<0 ||
                ports.collections().belongsToGroupMaterial(reviewCollectionGroup,
                    new LocalStore.DueItem(id,stage,a,b,0))) &&
                a>=0&&b>a&&b<=e.lines().size() &&
                ("VERSE_RUN".equals(stage)||"VERSE".equals(stage)||"CHAIN".equals(stage)||
                 "SECTION".equals(stage)||"CHAPTER".equals(stage))){
                final long targetId=id;final int first=a,last=b;
                // A saved unfinished review takes precedence; avoid duplicate overlapping tasks.
                reviewQueue.removeIf(t->t.entryId==targetId && t.start<last && first<t.end);
                reviewQueue.add(0,new LocalStore.DueItem(id,stage,a,b,old.getLong("review_review_due",0)));
            }else old.edit().remove("review_pending").apply();
        }
        if(reviewQueue.isEmpty()){ports.navigate(Screen.DUE);return;}
        LocalStore.DueItem first=reviewQueue.get(0);
        boolean restore=old.getBoolean("review_pending",false) &&
            old.getLong("review_review_entry",-1)==first.entryId &&
            old.getInt("review_begin",-1)==first.start&&old.getInt("review_end",-1)==first.end;
        openReview(first);
        if(restore){
            draft=old.getString("review_draft","");
            lessonWriteHadFeedback=old.getBoolean("review_lesson_had_feedback",false);
            lessonSeed=old.getLong("review_lesson_seed",lessonSeed);
            testAssisted=old.getBoolean("review_assisted",false);
            ports.render();
        }
    }

    void openReview(LocalStore.DueItem item){
        LocalStore.Entry e=ports.study().entry(item.entryId);if(e==null)return;
        if(!ports.collections().validSession(item.entryId,item.stage,item.start,item.end,reviewCollectionGroup))return;
        selectedEntry=item.entryId;sessionGroup=reviewCollectionGroup;activeStep=null;lessonPracticeAgain=false;
        // VERSE_RUN is a temporary coherent unit, never recorded as a mastered group.
        studyStage="VERSE_RUN".equals(item.stage)?StudyPlanLogic.Stage.CHAIN:
            StudyPlanLogic.Stage.valueOf(item.stage);
        forcedStep=new StudyPlanLogic.Step(item.start,item.end,"Повторение · "+e.lines().get(item.start).ref);
        currentReview=item;groupMode=false;reviewMode=true;stepCursor=0;
        weakVerse=-1;weakTransitionFrom=-1;
        mask=0;checked=false;passed=false;draft="";testAssisted=false;lastAccuracy=-1;
        lessonWriteHadFeedback=false;lessonPracticeAgain=false;
        trainingMode=StudySessionController.TrainingMode.TYPE;resetWordTiles();ports.navigate(Screen.TRAIN);
    }

    void advanceReviewSession(){
        reviewPointer++;
        if(reviewPointer<reviewQueue.size()){openReview(reviewQueue.get(reviewPointer));return;}
        reviewMode=false;forcedStep=null;currentReview=null;reviewQueue.clear();reviewPointer=0;
        ports.studyPreferences().edit().remove("review_pending").apply();
        ports.navigate(Screen.HOME);ports.toast("Все запланированные повторения завершены");
    }

    void rememberRecent(long id) {
        JSONArray updated=new JSONArray();updated.put(id);
        int count=1;
        for(Long prev:recentIds()) {
            if(prev==id || ports.study().entry(prev)==null)continue;
            updated.put(prev);
            if(++count>=7)break;
        }
        ports.studyPreferences().edit().putString("recent",updated.toString()).apply();
    }

    List<Long> recentIds() {
        List<Long> ids=new ArrayList<>();
        try {
            JSONArray array=new JSONArray(ports.studyPreferences()
                .getString("recent","[]"));
            for(int i=0;i<Math.min(7,array.length());i++){
                long id=array.getLong(i);LocalStore.Entry e=ports.study().entry(id);
                if(e!=null&&ports.collections().isVisibleGroup(e.groupId))ids.add(id);
            }
        }catch(JSONException ex){ /* Corrupt metadata must not block the app. */ }
        return ids;
    }

    void openTraining(long id) {
        reviewMode=false;groupMode=false;forcedStep=null;currentReview=null;
        if(ports.screen()==Screen.TRAIN)saveTrainingSession();
        if(ports.screen()==Screen.CARD_SESSION)ports.saveCardCheckpoint();
        LocalStore.Entry origin=ports.study().entry(id);
        long originGroup=origin!=null?origin.groupId:-1;
        if(ports.cardStudyActive()&&ports.studyDeckId()>0)originGroup=-ports.studyDeckId()-2;
        else if(origin==null||!ports.collections().isVisibleGroup(originGroup)){
            ports.toast("Материал больше не входит в коллекцию");ports.navigate(Screen.HOME);return;
        }
        sessionGroup=originGroup;activeStep=null;
        selectedEntry=ports.study().canonicalStudyEntryId(id);
        id=selectedEntry;
        LocalStore.Entry entry=ports.study().entry(id);
        if(entry==null){ports.toast("Отрывок не найден");ports.navigate(Screen.HOME);return;}
        android.content.SharedPreferences linked=ports.studyPreferences();
        String lk="linked_"+id+"_";
        if(linked.getBoolean(lk+"pending",false)){
            int a=linked.getInt(lk+"begin",0),b=linked.getInt(lk+"end",0);
            String stage=linked.getString(lk+"group_stage","CHAIN");
            if(a>=0&&b>a&&b<=entry.lines().size()&&
                !ports.progress().isStepMastered(id,stage,a,b)){
                GroupLearningLogic.Candidate cand=GroupLearningLogic.candidate(stage,a,b,"Продолжить связку");
                returnVerseCursor=linked.getInt(lk+"return_cursor",0);
                openGroup(cand);restoreTrainingSession(id,true);sessionGroup=originGroup;
                returnVerseCursor=linked.getInt(lk+"return_cursor",0);ports.render();return;
            }
            linked.edit().remove(lk+"pending").apply();
        }
        if(!restoreTrainingSession(id)){
            selectRecommendation(entry);resetTrainingStep(entry);
        } else if(trainingMode!=StudySessionController.TrainingMode.TYPE) {
            List<StudyPlanLogic.Step> steps=studySteps(entry);
            if(!steps.isEmpty()) {
                StudyPlanLogic.Step current=steps.get(Math.min(stepCursor,steps.size()-1));
                testAssisted=ports.progress().isStepMastered(entry.id,studyStage.name(),current.start,current.end);
            }
        }
        sessionGroup=originGroup;
        // If an old session points beyond the newly protected lesson boundary, restore safely.
        if(studyStage==StudyPlanLogic.Stage.VERSE)
            stepCursor=Math.min(stepCursor,ports.progress().unlockedPosition(id,entry.lines().size()));
        if(ports.collectionQueueActive() && ports.collectionStepPending()){
            // Revisit every record even if it was previously mastered. Do not erase progress.
            studyStage=StudyPlanLogic.Stage.VERSE;
            stepCursor=0;groupMode=false;forcedStep=null;
            checked=false;passed=false;draft="";
            lessonWriteHadFeedback=false;lessonPracticeAgain=false;
            resetTrainingStep(entry);ports.collectionStepStarted();
        }
        rememberRecent(id);saveTrainingSession();
        ports.navigate(Screen.TRAIN);
    }

    void followRecommendation(LocalStore.Entry e) {
        selectRecommendation(e);resetTrainingStep(e);saveTrainingSession();ports.render();
    }

    void openGroup(GroupLearningLogic.Candidate group){
        if(!groupMode) returnVerseCursor=studyStage==StudyPlanLogic.Stage.VERSE?stepCursor:
            ports.progress().firstUnmastered(selectedEntry,ports.study().entry(selectedEntry).lines().size());
        reviewMode=false;groupMode=true;currentReview=null;
        ports.studyPreferences().edit().putBoolean("linked_"+selectedEntry+"_pending",true).apply();
        studyStage=StudyPlanLogic.Stage.valueOf(group.stage);
        forcedStep=new StudyPlanLogic.Step(group.start,group.end,"Связка "+(group.start+1)+"–"+group.end);
        stepCursor=0;resetWordTiles();mask=0;draft="";checked=false;passed=false;
        lessonVariant=0;lessonChunk=0;
        testAssisted=false;lessonWriteHadFeedback=false;lessonPracticeAgain=false;
        lessonWriteVariant=0;groupBuildDone=false;groupIntroDone=false;practiceWeakFragment=false;weakFragmentText="";resetLessonTiles();
        trainingMode=StudySessionController.TrainingMode.ORDER;ports.navigate(Screen.TRAIN);
    }

    void advanceAfterSuccess(LocalStore.Entry e){
        final GroupLearningLogic.Candidate pending=!reviewMode&&!groupMode?
            pendingAfterCurrent(e,activeStep.start):null;
        if(reviewMode){advanceReviewSession();}
        else if(groupMode){
            ports.studyPreferences().edit()
                .remove("linked_"+selectedEntry+"_pending").apply();
            groupMode=false;forcedStep=null;studyStage=StudyPlanLogic.Stage.VERSE;
            stepCursor=returnVerseCursor;resetTrainingStep(e);saveTrainingSession();
            GroupLearningLogic.Candidate more=pendingAfterCurrent(e,stepCursor);
            if(more!=null){openGroup(more);return;}
            if(stepCursor+1<e.lines().size()){
                stepCursor++;resetTrainingStep(e);saveTrainingSession();ports.render();
            }else if(ports.collectionQueueReady()){advanceStudyCollectionAfterGroups(e);}
            else if(ports.cardStudyActive()){ports.advanceCardStudy();}
            else ports.render();
        }else if(pending!=null)openGroup(pending);
        else if(activeStep.end<e.lines().size()){
            stepCursor=activeStep.end;studyStage=StudyPlanLogic.Stage.VERSE;
            resetTrainingStep(e);saveTrainingSession();ports.render();
        }else if(ports.collectionQueueReady())advanceStudyCollectionAfterGroups(e);
        else if(ports.cardStudyActive())ports.advanceCardStudy();
        else ports.leaveLesson();
    }

    void advanceStudyCollectionAfterGroups(LocalStore.Entry e){
        GroupLearningLogic.Candidate unfinished=nextGroup(e);
        if(unfinished!=null){openGroup(unfinished);return;}
        ports.advanceCollection();
    }

    void validateLessonWriting(LocalStore.Entry e){
        try{validateLessonWritingInternal(e);}
        catch(android.database.sqlite.SQLiteException | IllegalStateException error){
            android.util.Log.e("StudySession","Answer result was not committed",error);
            ports.toast("Не удалось сохранить ответ. Открой занятие заново или повтори попытку.");
        }
    }
    private void validateLessonWritingInternal(LocalStore.Entry e){
        if(!requireValidSession()||(checked&&passed))return;
        List<LessonCurriculumLogic.Variant> variants=writingVariants(e);
        lessonWriteVariant=Math.min(lessonWriteVariant,variants.size()-1);
        LessonCurriculumLogic.Variant kind=variants.get(lessonWriteVariant);
        boolean full=kind==LessonCurriculumLogic.Variant.FULL;
        String original=writingExpected(e,kind);
        String entered=full?draft:InlineGapLogic.response(inlineDrafts);
        if(entered.trim().isEmpty()){ports.toast("Напиши текст, затем нажми «Проверить»");return;}
        List<String> refs=new ArrayList<>();
        for(Bible.Verse line:e.lines().subList(activeStep.start,activeStep.end))refs.add(line.ref);
        boolean special=AcceptedTextLogic.matthew513(e.reference,refs);
        StudyAnswerEvaluation evaluation=StudyAnswerEvaluation.lesson(original,entered,special);
        boolean exact=evaluation.exact;
        original=evaluation.expected;
        int accuracy=evaluation.accuracy;
        if(practiceWeakFragment){
            if(!exact){
                ports.writingErrors(e,original,entered,false);
                return;
            }
            practiceWeakFragment=false;weakFragmentText="";inlineDrafts=new String[0];draft="";lessonWriteHadFeedback=false;
            lessonWriteVariant=LessonCurriculumLogic.variants(
                ReviewLogic.words(PassageText.join(e.lines().subList(activeStep.start,activeStep.end))).size()).size()-1;
            if(reviewMode)lessonWriteVariant=0;
            saveTrainingSession();ports.render();
            ports.toast("Фрагмент отработан. Напиши весь стих заново без подсказок.");
            return;
        }
        if(exact && !full){
            lessonWriteVariant++;inlineDrafts=new String[0];draft="";lastAccuracy=-1;lessonWriteHadFeedback=false;
            lessonSeed=new Random().nextLong();saveTrainingSession();ports.render();return;
        }
        if(!exact && !full){
            boolean[] correct=new boolean[Math.min(inlineExpected.size(),inlineDrafts.length)];
            for(int i=0;i<correct.length;i++)correct[i]=LessonCurriculumLogic.exact(inlineExpected.get(i),inlineDrafts[i]);
            ports.markInlineAnswers(correct);
            ports.writingErrors(e,original,entered,false);
            return;
        }
        if(!exact){
            weakVerse=-1;weakTransitionFrom=-1;
            boolean rescheduleWeak=false;
            if(activeStep.end-activeStep.start>1){
                List<String> sources=new ArrayList<>();
                for(Bible.Verse v:e.lines().subList(activeStep.start,activeStep.end))sources.add(v.text);
                WeakTransitionLogic.WeakSpot weak=WeakTransitionLogic.locate(sources,entered);
                weakVerse=activeStep.start+weak.verseIndex;
                weakTransitionFrom=weak.transitionFrom<0?-1:activeStep.start+weak.transitionFrom;
                rescheduleWeak=weakVerse>=activeStep.start&&weak.errors>0;
            }
            new StudyProgressRecorder(ports.progress(),sessionGroup).failure(e,studyStage.name(),activeStep,accuracy,
                combinedReview(),reviewMode||groupMode||studyStage!=StudyPlanLogic.Stage.VERSE,
                weakVerse,weakTransitionFrom,rescheduleWeak,attemptTime(e,activeStep,"failure",entered));
            lessonWriteHadFeedback=true;lastAccuracy=accuracy;lastExpected=original;
            saveTrainingSession();ports.writingErrors(e,original,entered,true);
            return;
        }
        if(lessonWriteHadFeedback){
            // Correcting a visible diff is practice, not unaided recall. Demand a fresh clean attempt.
            new StudyProgressRecorder(ports.progress(),sessionGroup).correction(e,studyStage.name(),activeStep,
                attemptTime(e,activeStep,"correction",entered));
            lessonWriteHadFeedback=false;draft="";lastAccuracy=-1;saveTrainingSession();
            ports.render();
            ports.toast("Исправлено. Теперь напиши стих заново без подсказок для зачёта.");
            return;
        }
        new StudyProgressRecorder(ports.progress(),sessionGroup).success(e,studyStage.name(),activeStep,combinedReview(),
            reviewMode,groupMode,attemptTime(e,activeStep,"success",entered));
        lessonPracticeAgain=false;draft="";lastAccuracy=100;passed=true;checked=true;
        if(reviewMode)ports.studyPreferences().edit()
            .remove("review_pending").apply();
        if(!reviewMode&&!groupMode)saveTrainingSession();
        ports.render();
    }

    boolean validRun(){
        LocalStore.UnifiedCollection collection=ports.collections().unifiedCollection(runCollectionId);
        if(collection==null||runLines.isEmpty())return false;
        for(RunLine line:runLines)if(!ports.study().containsEntry(collection.groupId,line.entryId))return false;
        return true;
    }

    void finishRunLine(){
        if(runCursor<0||runCursor>=runLines.size())return;
        if(!validRun()){runLines.clear();ports.navigate(Screen.HOME);return;}
        if(runLines.isEmpty())return;
        StudySessionController.RunLine line=runLines.get(runCursor);
        if(runChecked&&runPassed)runCorrect++;
        else runMistakes.add(line.reference);
        runCursor++;runDraft="";runChecked=false;runPassed=false;
        if(runCursor>=runLines.size()){
            saveCompletedRun();
            ports.runPreferences().edit()
                .remove(runKey(runCollectionId)+"active").apply();
            ports.navigate(Screen.RUN_SUMMARY);
        }else{saveRunSession();ports.render();}
    }
}
