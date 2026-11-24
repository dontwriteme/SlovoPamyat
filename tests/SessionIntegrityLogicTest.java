package org.biblememory.app;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SessionIntegrityLogicTest {
    @Test public void changedTextOrBoundariesInvalidateSavedAssembly(){
        List<String> verse=Collections.singletonList("Один два три четыре пять шесть семь восемь девять десять одиннадцать двенадцать тринадцать четырнадцать пятнадцать шестнадцать семнадцать восемнадцать девятнадцать двадцать двадцать один двадцать два");
        List<String[]> original=WordOrderLogic.chunksForBudget(verse,8);
        List<String[]> resized=WordOrderLogic.chunksForBudget(verse,12);
        String a=SessionIntegrityLogic.assemblyFingerprint(LessonCurriculumLogic.Variant.GAPS,original);
        assertEquals(a,SessionIntegrityLogic.assemblyFingerprint(LessonCurriculumLogic.Variant.GAPS,
            WordOrderLogic.chunksForBudget(verse,8)));
        assertNotEquals(a,SessionIntegrityLogic.assemblyFingerprint(LessonCurriculumLogic.Variant.GAPS,resized));
        assertNotEquals(a,SessionIntegrityLogic.assemblyFingerprint(LessonCurriculumLogic.Variant.FULL,original));
        assertNotEquals(SessionIntegrityLogic.fingerprint(Arrays.asList("ab","c")),
            SessionIntegrityLogic.fingerprint(Arrays.asList("a","bc")));
    }

    @Test public void restoreOnlyCorrectUniqueTilesInOrder(){
        String[] tokens={"Слово","и","слово","истины"};
        int[] missing={0,2,3};
        assertTrue(SessionIntegrityLogic.validAssemblyPrefix(tokens,missing,2,Arrays.asList(1,0)));
        assertTrue(SessionIntegrityLogic.validAssemblyPrefix(tokens,missing,3,Arrays.asList(0,1,2)));
        assertFalse(SessionIntegrityLogic.validAssemblyPrefix(tokens,missing,2,Arrays.asList(0,0)));
        assertFalse(SessionIntegrityLogic.validAssemblyPrefix(tokens,missing,1,Arrays.asList(2)));
        assertFalse(SessionIntegrityLogic.validAssemblyPrefix(tokens,missing,1,Arrays.asList(3)));
        assertFalse(SessionIntegrityLogic.validAssemblyPrefix(tokens,missing,1,Arrays.asList(-1)));
        assertFalse(SessionIntegrityLogic.validAssemblyPrefix(tokens,missing,2,Arrays.asList(0)));
        assertFalse(SessionIntegrityLogic.validAssemblyPrefix(tokens,missing,4,Arrays.asList(0,1,2,0)));
    }

    @Test public void memoryRunCannotReuseDraftForChangedMembershipOrOrder(){
        String first=SessionIntegrityLogic.fingerprint(Arrays.asList("Бытие 1:1","Текст A","Бытие 1:2","Текст Б"));
        String reversed=SessionIntegrityLogic.fingerprint(Arrays.asList("Бытие 1:2","Текст Б","Бытие 1:1","Текст A"));
        assertTrue(SessionIntegrityLogic.canResumeRun(first,first,1,1,2));
        assertFalse(SessionIntegrityLogic.canResumeRun(first,reversed,1,1,2));
        assertFalse(SessionIntegrityLogic.canResumeRun("",first,0,0,2));
        assertFalse(SessionIntegrityLogic.canResumeRun(first,first,2,1,2));
        assertFalse(SessionIntegrityLogic.canResumeRun(first,first,1,2,2));
        assertFalse(SessionIntegrityLogic.canResumeRun(first,first,-1,0,2));
    }
}
