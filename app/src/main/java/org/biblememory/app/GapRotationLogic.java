package org.biblememory.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Rotating gap selection: random order, balanced coverage, independent of the Android UI. */
public final class GapRotationLogic {
    private GapRotationLogic(){}
    public static int amount(int wordCount){
        return wordCount<=0?0:Math.min(wordCount,Math.max(1,(int)Math.ceil(wordCount*.4)));
    }
    /** Prefer words with fewer previous omissions, randomize only tied positions. */
    public static int[] balancedPositions(int[] counts,int amount,long seed){
        if(counts==null)throw new IllegalArgumentException("Missing counts");
        int n=counts.length;
        int take=Math.max(0,Math.min(n,amount));
        List<Integer> positions=new ArrayList<>();
        for(int i=0;i<n;i++)positions.add(i);
        Collections.shuffle(positions,new Random(seed));
        Collections.sort(positions,(a,b)->Integer.compare(counts[a],counts[b]));
        int[] selected=new int[take];
        for(int i=0;i<take;i++){
            int idx=positions.get(i);
            selected[i]=idx;counts[idx]++;
        }
        Arrays.sort(selected);
        return selected;
    }
    public static String identity(String[] words){
        // Full text identity avoids combining different passages with the same word count.
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(String w:words){
                digest.update(w.getBytes(StandardCharsets.UTF_8));digest.update((byte)0);
            }
            byte[] bytes=digest.digest();StringBuilder hex=new StringBuilder("fragment_");
            for(int i=0;i<20;i++)hex.append(String.format(Locale.ROOT,"%02x",bytes[i]&255));
            return hex.toString();
        }catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    public static String encodeCounts(int[] values){
        StringBuilder b=new StringBuilder();
        for(int n:values){if(b.length()>0)b.append(',');b.append(Math.max(0,n));}
        return b.toString();
    }
    public static int[] decodeCounts(String s,int length){
        int[] result=new int[Math.max(0,length)];
        if(s==null||s.isEmpty())return result;
        String[] parts=s.split(",",-1);
        if(parts.length!=length)return result;
        try{for(int i=0;i<length;i++)result[i]=Math.max(0,Integer.parseInt(parts[i]));}
        catch(NumberFormatException ex){Arrays.fill(result,0);}
        return result;
    }
    public static String encodeMask(int[] positions){
        StringBuilder b=new StringBuilder();for(int i:positions){if(b.length()>0)b.append(',');b.append(i);}return b.toString();
    }
    public static int[] decodeMask(String s,int count){
        if(s==null||s.isEmpty())return null;
        String[] parts=s.split(",",-1);int[] result=new int[parts.length];
        try{
            int prev=-1;
            for(int i=0;i<parts.length;i++){
                int value=Integer.parseInt(parts[i]);
                if(value<0||value>=count||value<=prev)return null;
                result[i]=value;prev=value;
            }
        }catch(NumberFormatException ex){return null;}
        return result;
    }
}
