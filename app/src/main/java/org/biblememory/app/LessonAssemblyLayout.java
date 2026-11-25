package org.biblememory.app;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

final class LessonAssemblyLayout {
    private final Context context;
    LessonAssemblyLayout(Context context){this.context=context;}

    private final EnumMap<LessonCurriculumLogic.Variant,List<String[]>> lessonChunkCache=
        new EnumMap<>(LessonCurriculumLogic.Variant.class);
    private String lessonChunkCacheKey="";

    void clear(){lessonChunkCache.clear();lessonChunkCacheKey="";}

    /** Allow longer fragments where only a few words must be chosen. The answer
     * and the entire word bank are estimated together, not by word count alone. */
    List<String[]> chunks(long selectedEntry,StudyPlanLogic.Stage studyStage,StudyPlanLogic.Step activeStep,
                          List<String> lines,LessonCurriculumLogic.Variant kind){
        android.util.DisplayMetrics d=context.getResources().getDisplayMetrics();
        float widthDp=d.widthPixels/d.density;
        // Use stable screen dimensions; the previous root can still be attached
        // while a new lesson is rendering, and its height may not be final.
        float heightDp=d.heightPixels/d.density;
        float fontScale=Math.max(1f,context.getResources().getConfiguration().fontScale);
        String key=selectedEntry+":"+studyStage+":"+
            (activeStep==null?-1:activeStep.start)+":"+
            (activeStep==null?-1:activeStep.end)+":"+lines.hashCode()+":"+
            d.widthPixels+"x"+d.heightPixels+":"+d.scaledDensity+":"+fontScale;
        if(!key.equals(lessonChunkCacheKey)){
            lessonChunkCache.clear();
            lessonChunkCacheKey=key;
        }
        List<String[]> cached=lessonChunkCache.get(kind);
        if(cached!=null)return cached;
        int maximum=kind==LessonCurriculumLogic.Variant.GAPS?26:
            kind==LessonCurriculumLogic.Variant.ENDING?22:18;
        // A short or narrow display needs fewer words; on larger phones keep the
        // approved caps (26 gaps / 22 partial / 18 full) if the text itself fits.
        float scale=Math.min(1f,Math.min(widthDp/360f,heightDp/780f))/fontScale;
        int limit=Math.max(6,Math.min(maximum,Math.round(maximum*scale)));
        Paint font=new Paint(Paint.ANTI_ALIAS_FLAG);
        font.setTypeface(Typeface.create("serif",Typeface.BOLD));
        font.setTextSize(18f*d.scaledDensity);
        float availableAnswer=Math.max(190f,widthDp-68f);
        float availableBank=Math.max(190f,widthDp-52f);
        // Reserve the header, progress, reference, bottom action and safe insets.
        float availableHeight=Math.max(205f,heightDp-285f);
        List<String[]> best=null;
        // Search the usable budget in O(log N) fragment evaluations instead of
        // measuring every candidate. This matters for long verses like Esther 8:9.
        int low=6,high=limit;
        while(low<=high){
            int budget=(low+high)/2;
            List<String[]> chunks=WordOrderLogic.chunksForBudget(lines,budget);
            boolean fits=true;
            for(String[] chunk:chunks){
                if(!assemblyChunkFits(chunk,kind,font,d.density,availableAnswer,
                    availableBank,availableHeight,fontScale)){
                    fits=false;break;
                }
            }
            if(fits){best=chunks;low=budget+1;}
            else high=budget-1;
        }
        // On very small devices, the existing ScrollView remains the safety net.
        if(best==null)best=WordOrderLogic.chunksForBudget(lines,6);
        lessonChunkCache.put(kind,best);
        return best;
    }
    private boolean assemblyChunkFits(String[] words,LessonCurriculumLogic.Variant kind,
            Paint font,float density,float answerWidth,float bankWidth,
            float heightBudget,float fontScale){
        int n=words.length;
        if(n==0)return true;
        float[] textWidths=new float[n];
        for(int i=0;i<n;i++)textWidths[i]=font.measureText(words[i])/density;
        if(kind==LessonCurriculumLogic.Variant.ENDING){
            // The seed switches which half is hidden between exercises. Account
            // for both layouts so the chunk boundaries never depend on that seed.
            int midpoint=Math.max(1,n/2);
            boolean[] first=new boolean[n],last=new boolean[n];
            for(int i=0;i<n;i++){
                first[i]=i<midpoint;
                last[i]=i>=midpoint;
            }
            return Math.max(assemblyEstimatedHeight(textWidths,first,false,
                answerWidth,bankWidth,fontScale),
                assemblyEstimatedHeight(textWidths,last,false,
                answerWidth,bankWidth,fontScale))<=heightBudget;
        }
        boolean[] missing=new boolean[n];
        if(kind==LessonCurriculumLogic.Variant.FULL){
            Arrays.fill(missing,true);
        }else{
            int selections=Math.max(1,(int)Math.ceil(n*.4));
            for(int i=0;i<selections;i++)missing[i*n/selections]=true;
        }
        return assemblyEstimatedHeight(textWidths,missing,
            kind==LessonCurriculumLogic.Variant.GAPS,
            answerWidth,bankWidth,fontScale)<=heightBudget;
    }
    private float assemblyEstimatedHeight(float[] textWidths,boolean[] missing,
            boolean conservativeBank,float answerWidth,float bankWidth,float fontScale){
        int n=textWidths.length,selections=0;
        for(boolean gap:missing)if(gap)selections++;
        float[] answer=new float[n],bank=new float[selections];
        for(int i=0;i<n;i++)
            answer[i]=missing[i]?Math.max(56f,textWidths[i]+20f):textWidths[i]+8f;
        if(conservativeBank){
            // Gaps rotate; size the bank for the longest plausible selected words.
            float[] longest=textWidths.clone();
            Arrays.sort(longest);
            for(int i=0;i<selections;i++)bank[i]=longest[n-1-i]+24f;
        }else{
            int index=0;
            for(int i=0;i<n;i++)if(missing[i])bank[index++]=textWidths[i]+24f;
        }
        float lineHeight=Math.max(43f,24f*fontScale+18f);
        float answerHeight=assemblyRows(answer,answerWidth,7f)*(lineHeight+7f)+27f;
        float bankHeight=assemblyRows(bank,bankWidth,8f)*(lineHeight+8f)+6f;
        // Reserve space for inter-card spacing and slight font metric differences.
        return answerHeight+bankHeight+32f;
    }
    private static int assemblyRows(float[] widths,float available,float gap){
        int rows=1;float used=0f;
        for(float width:widths){
            if(used>0f&&used+gap+width>available){rows++;used=0f;}
            used+=(used==0f?0f:gap)+width;
        }
        return rows;
    }
}
