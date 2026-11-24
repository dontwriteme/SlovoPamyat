package org.biblememory.app;

import java.util.*;

/** Pure session guards; indices are meaningful only for the exact saved material. */
final class SessionIntegrityLogic {
    private SessionIntegrityLogic(){}

    static String fingerprint(List<String> parts){
        // Length framing distinguishes empty tokens and fragment boundaries.
        String[] framed=new String[parts.size()];
        for(int i=0;i<parts.size();i++){
            String value=parts.get(i);
            framed[i]=value.length()+":"+value;
        }
        return GapRotationLogic.identity(framed);
    }

    static String assemblyFingerprint(LessonCurriculumLogic.Variant variant,List<String[]> chunks){
        List<String> parts=new ArrayList<>();
        parts.add(variant.name());
        for(String[] chunk:chunks){
            parts.add(Integer.toString(chunk.length));
            Collections.addAll(parts,chunk);
        }
        return fingerprint(parts);
    }

    static boolean validAssemblyPrefix(String[] tokens,int[] missing,int picked,List<Integer> used){
        if(picked<0||picked>missing.length||used.size()!=picked)return false;
        boolean[] seen=new boolean[missing.length];
        for(int i=0;i<picked;i++){
            Integer index=used.get(i);
            if(index==null||index<0||index>=missing.length||seen[index])return false;
            seen[index]=true;
            if(!WordOrderLogic.accepts(tokens,missing[i],missing[index]))return false;
        }
        return true;
    }

    static boolean canResumeRun(String saved,String current,int cursor,int correct,int total){
        return saved!=null&&!saved.isEmpty()&&saved.equals(current)&&
            cursor>=0&&cursor<total&&correct>=0&&correct<=cursor;
    }
}
