package org.biblememory.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CustomStudyTextTest {
    private static Bible.Verse verse(String ref,String text){
        return new Bible.Verse(ref,text);
    }
    private static void reject(String raw,String expected){
        try{
            CustomStudyText.parse(raw,null);
            fail("Accepted an unlearnable text: "+raw);
        }catch(IllegalArgumentException invalid){
            assertEquals(expected,invalid.getMessage());
        }
    }

    @Test public void rejectsPunctuationInvisibleAndMixedLines(){
        reject("— …","Строка 1 должна содержать хотя бы одно слово или число");
        reject("\u200B\u00A0","Строка 1 должна содержать хотя бы одно слово или число");
        reject("Слово\n\n…\nЖизнь","Строка 2 должна содержать хотя бы одно слово или число");
        reject(" \n \t","Введите текст для изучения");
        reject(String.join("\n",Collections.nCopies(501,"Слово")),"Максимум 500 строк");
    }

    @Test public void acceptsExistingLexicalContractAndLineSeparators(){
        List<Bible.Verse> lines=CustomStudyText.parse(
            "  «Сло\u0301во» — свет! \r\n\r\nкто-нибудь\n１２３  ",null);
        assertEquals(3,lines.size());
        assertEquals("1",lines.get(0).ref);
        assertEquals("«Сло\u0301во» — свет!",lines.get(0).text);
        assertEquals("2",lines.get(1).ref);
        assertEquals("кто-нибудь",lines.get(1).text);
        assertEquals("3",lines.get(2).ref);
        assertEquals("１２３",lines.get(2).text);
        assertEquals(-1,CustomStudyText.firstUnlearnable(lines,0,lines.size()));
    }

    @Test public void replacingAnInvalidLineKeepsUnaffectedIdentities(){
        List<Bible.Verse> old=Arrays.asList(verse("1","Начало"),verse("2","…"),
            verse("3","Конец"));
        assertEquals(1,CustomStudyText.firstUnlearnable(old,0,3));
        assertEquals(-1,CustomStudyText.firstUnlearnable(old,2,3));
        List<Bible.Verse> fixed=CustomStudyText.parse("Начало\nСередина\nКонец",old);
        assertEquals("1",fixed.get(0).ref);
        assertEquals("2",fixed.get(1).ref);
        assertEquals("Середина",fixed.get(1).text);
        assertEquals("3",fixed.get(2).ref);
        assertEquals(-1,CustomStudyText.firstUnlearnable(fixed,0,3));
        List<Bible.Verse> shortened=CustomStudyText.parse("Начало\nКонец",old);
        assertEquals("3",shortened.get(1).ref);
        List<Bible.Verse> appended=CustomStudyText.parse("Начало\nСередина\nКонец\nДалее",fixed);
        assertEquals("4",appended.get(3).ref);
    }

    @Test public void doesNotAssignAmbiguousMovedDuplicateHistory(){
        List<Bible.Verse> old=Arrays.asList(verse("1","Слово"),verse("2","…"),
            verse("3","Слово"),verse("свой:1","Конец"));
        List<Bible.Verse> fixed=CustomStudyText.parse("Слово\nКонец",old);
        assertNotEquals("1",fixed.get(0).ref);
        assertNotEquals("3",fixed.get(0).ref);
        assertEquals("свой:1",fixed.get(1).ref);
        assertNotEquals(fixed.get(0).ref,fixed.get(1).ref);
        List<Bible.Verse> same=CustomStudyText.parse("Слово\nНовое\nСлово\nКонец",old);
        assertEquals("1",same.get(0).ref);
        assertEquals("3",same.get(2).ref);
    }

    @Test public void sameLengthShiftPreservesUnchangedLinesBeforeAllocatingNewIds(){
        List<Bible.Verse> old=Arrays.asList(verse("1","Начало"),verse("2","Середина"),
            verse("3","Конец"));
        List<Bible.Verse> shifted=CustomStudyText.parse("Середина\nКонец\nДобавление",old);
        assertEquals("2",shifted.get(0).ref);
        assertEquals("3",shifted.get(1).ref);
        assertEquals("4",shifted.get(2).ref);
        List<Bible.Verse> reordered=CustomStudyText.parse("Конец\nНачало\nСередина",old);
        assertEquals("3",reordered.get(0).ref);
        assertEquals("1",reordered.get(1).ref);
        assertEquals("2",reordered.get(2).ref);
    }

    @Test public void ambiguousCopiesDoNotInheritAnUnrelatedIdentity(){
        List<Bible.Verse> old=Arrays.asList(verse("1","Слово"),verse("2","Другое"),
            verse("3","Слово"));
        List<Bible.Verse> shifted=CustomStudyText.parse("Другое\nСлово\nСлово",old);
        assertEquals("2",shifted.get(0).ref);
        assertNotEquals("1",shifted.get(1).ref);
        assertNotEquals("3",shifted.get(1).ref);
        assertNotEquals("1",shifted.get(2).ref);
        assertNotEquals("3",shifted.get(2).ref);
        assertNotEquals(shifted.get(1).ref,shifted.get(2).ref);
    }
}
