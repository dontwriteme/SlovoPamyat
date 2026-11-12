package org.biblememory.app;

/** Ordered lessons are separate from open Bible reading and spaced reviews. */
public final class LessonFlowLogic {
    private LessonFlowLogic() {}
    public enum Exercise { READ, HIDE, ORDER, TYPE, FINAL }
    public static Exercise next(Exercise e) {
        int n=e.ordinal()+1;
        return n<Exercise.values().length?Exercise.values()[n]:null;
    }
    public static boolean canOpen(int target, int unlocked, int length) {
        return target>=0 && target<length && target<=unlocked;
    }
    public static int unlockAfterFinal(int finished, int unlocked, int length) {
        if(finished<0 || finished>=length) return unlocked;
        return Math.min(length-1,Math.max(unlocked,finished+1));
    }
    public static boolean passedTyped(int accuracy) { return accuracy==100; }
    public static boolean passedFinal(int accuracy, boolean assisted) {
        return accuracy==100 && !assisted;
    }
    public static String label(Exercise e) {
        switch(e) {
            case READ: return "1/5 · Знакомство";
            case HIDE: return "2/5 · Восстановление слов";
            case ORDER: return "3/5 · Собери стих";
            case TYPE: return "4/5 · Напиши по памяти";
            default: return "5/5 · Контрольная проверка";
        }
    }
}
