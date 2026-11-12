package org.biblememory.app;
import java.util.*;import java.nio.*;import java.nio.file.*;import java.lang.reflect.*;import java.security.*;
public class SearchHarness {
 @SuppressWarnings("unchecked") public static Bible fixture(String fixture,String indexFile)throws Exception {
  Bible b=new Bible();Field f=Bible.class.getDeclaredField("books");f.setAccessible(true);
  Map<Integer,SortedMap<Integer,List<Bible.Verse>>> books=(Map<Integer,SortedMap<Integer,List<Bible.Verse>>>)f.get(b);
  for(String line:Files.readAllLines(Path.of(fixture))){String[] a=line.split("\t");int book=Integer.parseInt(a[0]),c=Integer.parseInt(a[1]);books.computeIfAbsent(book,k->new TreeMap<>()).computeIfAbsent(c,k->new ArrayList<>()).add(new Bible.Verse(c+":"+a[2],new String(Base64.getDecoder().decode(a[3]),java.nio.charset.StandardCharsets.UTF_8)));}
  f=Bible.class.getDeclaredField("ready");f.setAccessible(true);f.set(b,true);
  Class<?> idx=Class.forName("org.biblememory.app.BibleSearchLogic$Index");Constructor<?> constructor=idx.getDeclaredConstructor(ByteBuffer.class);constructor.setAccessible(true);
  Object index=constructor.newInstance(ByteBuffer.wrap(Files.readAllBytes(Path.of(indexFile))).order(ByteOrder.BIG_ENDIAN));
  f=BibleSearchLogic.class.getDeclaredField("INDEX");f.setAccessible(true);f.set(null,index);return b;
 }
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static int highlights(String text,String query){
  // REPLACE_HIGHLIGHTS
  int count=0;
  java.util.regex.Matcher words=java.util.regex.Pattern.compile("[\\p{L}]+").matcher(text);
  String[] terms=BibleSearchLogic.normalized(query).split(" +");
  for(;words.find();)for(String term:terms)
   if(!term.isEmpty()&&BibleSearchLogic.allTermsMatch(term,words.group())){count+=words.start()+words.end()+1;break;}
  return count;
 }
 public static void main(String[] args)throws Exception {
  Bible b=fixture(args[0],args[1]);
  // REPLACE_CHECKS
  for(String q:new String[]{"и","бог","любовь","господь","начале","блаженны","бог любовь"}){
   long cold=System.nanoTime();List<BibleSearchLogic.Hit> hits=BibleSearchLogic.search(b,q);cold=System.nanoTime()-cold;
   for(int i=0;i<2;i++)BibleSearchLogic.search(b,q);
   long[] times=new long[7];for(int i=0;i<times.length;i++){long start=System.nanoTime();BibleSearchLogic.search(b,q);times[i]=System.nanoTime()-start;}Arrays.sort(times);
   MessageDigest md=MessageDigest.getInstance("SHA-256");for(BibleSearchLogic.Hit h:hits)md.update((h.book+":"+h.chapter+":"+h.verse+":"+h.score+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
   long[] marking=new long[3];int marks=0;
   for(int run=0;run<marking.length;run++){
    long start=System.nanoTime();int sum=0;
    for(int i=0;i<Math.min(hits.size(),200);i++)sum+=highlights(hits.get(i).text,q);
    marking[run]=System.nanoTime()-start;marks=sum;
   }
   Arrays.sort(marking);
   System.out.printf(Locale.ROOT,"%s\t%d\t%.3f\t%.3f\t%s\t%.3f\t%d%n",q,hits.size(),cold/1e6,times[3]/1e6,HexFormat.of().formatHex(md.digest()),marking[1]/1e6,marks);
  }
  for(String query:new String[]{"БОГ!","бога","любовью","во Христе","жизни","он","а","в","они","был","веры","небо земля","благодать","Господь Иисус","дом","мир","мой","не","Но","123abc","qzxvnotfound","🕊️"}){
   List<BibleSearchLogic.Hit> hits=BibleSearchLogic.search(b,query);
   MessageDigest digest=MessageDigest.getInstance("SHA-256");
   for(BibleSearchLogic.Hit h:hits)digest.update((h.book+":"+h.chapter+":"+h.verse+":"+h.score+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
   System.out.println("CHECK\t"+query+"\t"+hits.size()+"\t"+HexFormat.of().formatHex(digest.digest()));
  }

 }
}
