"""Run actual search over bundled data, compare baseline results/timings on the host JVM.
Usage: JAVA_HOME=... ANDROID_HOME=... python3 tests/benchmark_search.py
No Android device, UI timing, production dependencies, or tracked assets are modified.
"""
import base64
import json
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/org/biblememory/app'
sdk = Path(os.environ.get('ANDROID_HOME', '/workspace/toolchains/android-sdk'))
android = sdk / 'platforms/android-35/android.jar'
java_home = Path(os.environ['JAVA_HOME']) / 'bin'
checks = '''
  require(BibleSearchLogic.search(b,null).isEmpty(),"null query");
  require(BibleSearchLogic.search(b,"   ").isEmpty(),"blank query");
  require(BibleSearchLogic.search(b,"qzxvnotfound").isEmpty(),"no match");
  require(BibleSearchLogic.search(b,"и").size()>20000,"complete large result set");
  require(BibleSearchLogic.search(b,"Матфея 5:3–7:29").size()==109,"cross chapter reference");
  require(BibleSearchLogic.search(b,"Матфея 5:3-12").size()==10,"reference range");
  require(BibleSearchLogic.search(b,"Пс 118:1–176").size()==176,"large chapter reference");
  int psalms=0;for(int c=1;c<=b.chapterCount(19);c++)psalms+=b.chapter(19,c).size();
  require(BibleSearchLogic.search(b,"Псалтирь").size()==psalms,"whole book");
  require(BibleSearchLogic.search(b,"1 Ин 3:16").size()==1,"numbered book alias");
  require(BibleSearchLogic.search(b,"Матфея 7:29–5:3").isEmpty(),"invalid reference");
  require(BibleSearchLogic.search(b,"Матфея 5:999").isEmpty(),"invalid verse");
  Thread.currentThread().interrupt();
  try{require(BibleSearchLogic.search(b,"и").isEmpty(),"canceled search");}finally{Thread.interrupted();}
  BibleSearchLogic.WordMatcher matcher=new BibleSearchLogic.WordMatcher("Господь любовь");
  for(String word:new String[]{"Господа","любовью","Вера","господ","любовию","Бог"}){
   boolean expected=BibleSearchLogic.allTermsMatch("Господь",word)||BibleSearchLogic.allTermsMatch("любовь",word);
   require(matcher.matches(word)==expected,"highlight semantics: "+word);
  }
'''
optimized_highlights = '''
 static int highlights(String text,String query){
  int count=0;BibleSearchLogic.WordMatcher matcher=new BibleSearchLogic.WordMatcher(query);
  java.util.regex.Matcher words=BibleSearchLogic.WORDS.matcher(text);
  while(words.find())if(matcher.matches(words.group()))count+=words.start()+words.end()+1;
  return count;
 }
'''
with tempfile.TemporaryDirectory(prefix='bible-search-') as tmp:
    tmp = Path(tmp)
    fixture = tmp / 'verses.tsv'
    with fixture.open('w') as out:
        for book in json.loads((root/'app/src/main/assets/synodal.json').read_text())['books']:
            for chapter in book['chapters']:
                for verse in chapter['verses']:
                    out.write(f"{book['bookId']}\t{chapter['chapter']}\t{verse['number']}\t" +
                              base64.b64encode(verse['text'].encode()).decode()+'\n')
    results = {}
    for mode in ['before', 'after']:
        directory = tmp / mode
        directory.mkdir()
        engine = directory/'BibleSearchLogic.java'
        engine.write_text(subprocess.check_output(
            ['git','show','4b8ae8d:app/src/main/java/org/biblememory/app/BibleSearchLogic.java'],cwd=root,text=True)
            if mode=='before' else (source/'BibleSearchLogic.java').read_text())
        harness = (root/'tests/search/SearchHarness.java').read_text()
        if mode=='after':
            start = harness.index(' static int highlights(')
            end = harness.index(' public static void main(',start)
            harness=harness[:start]+optimized_highlights+harness[end:]
            # Run semantic tests after measurements so first-query timings remain cold.
            at=harness.rfind('\n }')
            harness=harness[:at]+checks+harness[at:]
        (directory/'SearchHarness.java').write_text(harness)
        subprocess.run([str(java_home/'javac'),'-cp',str(android),'-d',str(directory),
                        str(source/'Bible.java'),str(source/'BookColorLogic.java'),str(engine),
                        str(directory/'SearchHarness.java')],check=True)
        output = subprocess.check_output([str(java_home/'java'),'-cp',os.pathsep.join([str(directory),str(android)]),
                                          'org.biblememory.app.SearchHarness',str(fixture),
                                          str(root/'app/src/main/assets/synodal.sidx')],text=True)
        lines=output.strip().splitlines()
        results[mode] = [line.split('\t') for line in lines if not line.startswith('CHECK\t')]
        results[mode+'_checks'] = [line for line in lines if line.startswith('CHECK\t')]
    print('Query | Hits | Before cold ms | After cold ms | Before median ms | After median ms | Highlight before/after ms (<=200 rows)')
    for before, after in zip(results['before'],results['after']):
        assert before[0:2]==after[0:2] and before[4]==after[4], 'Result identity/order/score changed: '+before[0]
        assert before[6]==after[6], 'Highlight offsets changed: '+before[0]
        print(f'{before[0]} | {before[1]} | {before[2]} | {after[2]} | {before[3]} | {after[3]} | {before[5]}/{after[5]}')
    assert results['before_checks']==results['after_checks'], 'Additional text query results differ'
    print('PASS: 29 complete ordered result fingerprints match baseline; highlight offsets match; empty/reference/cancellation checks pass.')
