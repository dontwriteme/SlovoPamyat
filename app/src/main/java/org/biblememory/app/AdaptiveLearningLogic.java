package org.biblememory.app;

import java.util.*;

/** Deterministic offline lesson selector. Never locks navigation or training modes. */
public final class AdaptiveLearningLogic {
    private AdaptiveLearningLogic() {}
    public interface Progress {
        boolean mastered(StudyPlanLogic.Stage stage,int start,int end);
        /** Repeated correctly after a delay; new work should not masquerade as retention. */
        boolean isDue(StudyPlanLogic.Stage stage,int start,int end,long now);
        int recentFailures(StudyPlanLogic.Stage stage,int start,int end);
    }
    public static final class Recommendation {
        public final StudyPlanLogic.Stage stage;
        public final int index;
        public final String reason;
        public Recommendation(StudyPlanLogic.Stage stage,int index,String reason) {
            this.stage=stage;this.index=index;this.reason=reason;
        }
    }
    private static final StudyPlanLogic.Stage[] LEVELS={StudyPlanLogic.Stage.VERSE,
        StudyPlanLogic.Stage.CHAIN,StudyPlanLogic.Stage.SECTION,StudyPlanLogic.Stage.CHAPTER};

    public static Recommendation recommend(List<String> refs,boolean sermon,Progress progress,long now) {
        if(refs.isEmpty()) return new Recommendation(StudyPlanLogic.Stage.VERSE,0,"Нет текста для тренировки");
        List<StudyPlanLogic.Step> verses=StudyPlanLogic.build(refs,StudyPlanLogic.Stage.VERSE,sermon);
        // Recently failed cards should be addressed first, even if previously marked mastered.
        for(int i=0;i<verses.size();i++){
            StudyPlanLogic.Step s=verses.get(i);
            if(progress.recentFailures(StudyPlanLogic.Stage.VERSE,s.start,s.end)>0)
                return new Recommendation(StudyPlanLogic.Stage.VERSE,i,"Повторим стих, в котором возникли ошибки");
        }
        for(int i=0;i<verses.size();i++){
            StudyPlanLogic.Step s=verses.get(i);
            if(!progress.mastered(StudyPlanLogic.Stage.VERSE,s.start,s.end))
                return new Recommendation(StudyPlanLogic.Stage.VERSE,i,"Начни с воспроизведения отдельного стиха");
        }
        // A delayed independent recall takes precedence over introducing longer passages.
        for(int i=0;i<verses.size();i++){
            StudyPlanLogic.Step s=verses.get(i);
            if(progress.isDue(StudyPlanLogic.Stage.VERSE,s.start,s.end,now))
                return new Recommendation(StudyPlanLogic.Stage.VERSE,i,"Пора проверить, сохранился ли стих в памяти");
        }
        if(refs.size()==1)
            return new Recommendation(StudyPlanLogic.Stage.VERSE,0,"Стих изучен: повторяй его без подсказок");
        for(int level=1;level<LEVELS.length;level++) {
            StudyPlanLogic.Stage stage=LEVELS[level];
            List<StudyPlanLogic.Step> steps=StudyPlanLogic.build(refs,stage,sermon);
            for(int i=0;i<steps.size();i++){
                StudyPlanLogic.Step s=steps.get(i);
                if(progress.recentFailures(stage,s.start,s.end)>0)
                    return new Recommendation(stage,i,"Закрепим трудный переход между стихами");
            }
            for(int i=0;i<steps.size();i++){
                StudyPlanLogic.Step s=steps.get(i);
                if(!progress.mastered(stage,s.start,s.end)) {
                    String hint=stage==StudyPlanLogic.Stage.CHAIN?
                        "Попробуй воспроизвести соседние стихи подряд":
                        stage==StudyPlanLogic.Stage.SECTION?
                        "Потренируй цельный отрывок без подсказок":
                        "Попробуй воспроизвести всю главу или отрывок";
                    return new Recommendation(stage,i,hint);
                }
            }
        }
        return new Recommendation(StudyPlanLogic.Stage.CHAPTER,0,
            "Все уровни пройдены: повторяй отрывок для долговременной памяти");
    }
}
