package org.biblememory.app;

import java.util.*;

/** Finds the likeliest weak verse and incoming transition in a continuous recall answer.
 * Alignment attributes omissions/insertions to their original verse rather than cascading errors.
 */
public final class WeakTransitionLogic {
    private WeakTransitionLogic(){}
    public static final class WeakSpot {
        public final int verseIndex, transitionFrom, errors;
        public WeakSpot(int verse,int from,int errors){verseIndex=verse;transitionFrom=from;this.errors=errors;}
    }
    public static WeakSpot locate(List<String> verses,String typed) {
        if(verses.isEmpty())return new WeakSpot(-1,-1,0);
        List<String> expected=new ArrayList<>();List<Integer> owner=new ArrayList<>();
        int[] begins=new int[verses.size()];
        for(int i=0;i<verses.size();i++){
            begins[i]=expected.size();
            for(String word:ReviewLogic.words(verses.get(i))){expected.add(word);owner.add(i);}
        }
        List<String> actual=ReviewLogic.words(typed);
        // For very large passages, use a bounded prefix/suffix approximation rather than quadratic memory.
        if((long)expected.size()*(actual.size()+1)>350000L) {
            return new WeakSpot(-1,-1,0);
        }
        int n=expected.size(),m=actual.size();int[][] dp=new int[n+1][m+1];
        for(int i=0;i<=n;i++)dp[i][0]=i;
        for(int j=0;j<=m;j++)dp[0][j]=j;
        for(int i=1;i<=n;i++)for(int j=1;j<=m;j++)dp[i][j]=Math.min(
            dp[i-1][j-1]+(expected.get(i-1).equals(actual.get(j-1))?0:1),
            Math.min(dp[i-1][j]+1,dp[i][j-1]+1));
        int[] errors=new int[verses.size()],boundaryErrors=new int[verses.size()];
        int[] lengths=new int[verses.size()];
        for(int o:owner)lengths[o]++;
        int i=n,j=m;
        while(i>0||j>0){
            if(i>0&&j>0&&dp[i][j]==dp[i-1][j-1]+(expected.get(i-1).equals(actual.get(j-1))?0:1)){
                if(!expected.get(i-1).equals(actual.get(j-1))){
                    int k=owner.get(i-1);errors[k]++;
                    if(i-1-begins[k]<2)boundaryErrors[k]++;
                }
                i--;j--;
            }else if(i>0&&dp[i][j]==dp[i-1][j]+1){
                int k=owner.get(i-1);errors[k]++;
                if(i-1-begins[k]<2)boundaryErrors[k]++;
                i--;
            }else{int k=i>0?owner.get(i-1):0;errors[k]++;
                if(i>0 && i-1-begins[k]<2)boundaryErrors[k]++;
                j--;
            }
        }
        int worst=0;
        for(int k=1;k<errors.length;k++)if((double)errors[k]/Math.max(1,lengths[k])>
            (double)errors[worst]/Math.max(1,lengths[worst]))worst=k;
        return new WeakSpot(worst,worst>0 && boundaryErrors[worst]>0?worst-1:-1,errors[worst]);
    }
}
