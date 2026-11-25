package org.biblememory.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Freeze the assessed answer and persist only an assessment selected by LessonWritingFlow. */
final class LessonWritingSubmission {
    record Request(WritingAssessment.Input input,LocalStore.Entry entry,StudyPlanLogic.Step step,String draft){
        boolean matches(LessonSessionState lesson){
            return lesson.selectedEntry==entry.id&&lesson.activeStep==step&&lesson.lessonWriting.draft.equals(draft);
        }
    }

    private LessonWritingSubmission(){}

    static Request capture(LocalStore.Entry entry,LessonSessionState lesson,ReviewSession review,
                           BiFunction<String[],LessonCurriculumLogic.Variant,int[]> positions,List<String> expectedGaps){
        List<Bible.Verse> lines=entry.lines().subList(lesson.activeStep.start,lesson.activeStep.end);
        String passage=VerseText.join(lines);
        List<LessonCurriculumLogic.Variant> variants=LessonStageFlow.writingVariants(passage,
            review.active(),lesson.lessonPracticeAgain,lesson.lessonWriting.practiceWeakFragment);
        lesson.lessonWriting.variant=Math.min(lesson.lessonWriting.variant,variants.size()-1);
        LessonCurriculumLogic.Variant kind=variants.get(lesson.lessonWriting.variant);
        boolean full=kind==LessonCurriculumLogic.Variant.FULL;
        String expected=expected(lesson,passage,kind,tokens->positions.apply(tokens,kind));
        String entered=full?lesson.lessonWriting.draft:lesson.lessonWriting.gapResponse();
        List<String> refs=new ArrayList<>(),verses=new ArrayList<>();
        for(Bible.Verse line:lines){refs.add(line.ref);verses.add(line.text);}
        WritingAssessment.Input input=new WritingAssessment.Input(expected,entered,
            AcceptedTextLogic.matthew513(entry.reference,refs),full,lesson.lessonWriting.practiceWeakFragment,verses,
            full?Collections.emptyList():expectedGaps,full?Collections.emptyList():lesson.lessonWriting.gapAnswers());
        return new Request(input,entry,lesson.activeStep,lesson.lessonWriting.draft);
    }

    static LessonWritingFlow.Transition persist(LocalStore store,LessonSessionState lesson,ReviewSession review,
                                                Request check,WritingAssessment.Result result,long now){
        LessonWritingFlow.Context context=new LessonWritingFlow.Context(
            check.entry().id,lesson.studyStage,lesson.activeStep.start,lesson.activeStep.end,review.active(),lesson.groupMode,
            review.active()&&"VERSE_RUN".equals(review.current().item().stage),
            lesson.lessonWriting.practiceWeakFragment,lesson.lessonWriting.hadFeedback);
        LessonWritingFlow.Transition transition=LessonWritingFlow.decide(context,check.input(),result);
        if(review.active()&&!review.matchesMaterial(store.entry(check.entry().id)))return null;
        transition.persist(store,now);
        return transition;
    }

    static String expected(LessonSessionState lesson,String original,LessonCurriculumLogic.Variant variant,Function<String[],int[]> positions){
        if(lesson.lessonWriting.practiceWeakFragment)return lesson.lessonWriting.weakFragmentText;
        if(variant==LessonCurriculumLogic.Variant.FULL)return original;
        String[] tokens=ExerciseTextLogic.tokens(original);
        int[] gaps=InlineGapLogic.wordPositions(tokens,positions.apply(tokens));
        StringBuilder response=new StringBuilder();
        for(int position:gaps){if(response.length()>0)response.append(' ');response.append(tokens[position]);}
        return response.toString();
    }
}
