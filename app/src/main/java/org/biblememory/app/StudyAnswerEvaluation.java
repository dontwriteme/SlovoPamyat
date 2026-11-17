package org.biblememory.app;

/** Immutable answer evaluation. Contains no Android, persistence or session mutations. */
final class StudyAnswerEvaluation {
    final String expected;
    final boolean exact;
    final int accuracy;
    private StudyAnswerEvaluation(String expected,boolean exact,int accuracy){
        this.expected=expected;this.exact=exact;this.accuracy=accuracy;
    }
    static StudyAnswerEvaluation lesson(String expected,String actual,boolean special){
        boolean exact=AcceptedTextLogic.exact(expected,actual,special);
        String accepted=AcceptedTextLogic.bestExpected(expected,actual,special);
        return new StudyAnswerEvaluation(accepted,exact,exact?100:Math.min(99,ReviewLogic.accuracy(accepted,actual)));
    }
    static StudyAnswerEvaluation recall(String expected,String actual){
        return new StudyAnswerEvaluation(expected,LessonCurriculumLogic.exact(expected,actual),ReviewLogic.accuracy(expected,actual));
    }
}
