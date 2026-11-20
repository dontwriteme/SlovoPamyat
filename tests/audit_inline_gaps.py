#!/usr/bin/env python3
"""Compile production Java logic and exhaustively audit the bundled corpus. No asset writes."""
import base64, json, pathlib, re, subprocess, tempfile
ROOT = pathlib.Path(__file__).resolve().parents[1]
data = json.loads((ROOT / 'app/src/main/assets/synodal.json').read_text())
texts, issues, chapter_texts = [], [], []
verses = chapters = bad_tokens = 0
for book in data['books']:
    for chapter in book['chapters']:
        chapters += 1
        lines = []
        for verse in chapter['verses']:
            verses += 1
            text = verse['text']
            texts.append(text)
            lines.append(text)
            bad = [t for t in text.split() if not any(c.isalnum() for c in t)]
            if bad:
                bad_tokens += len(bad)
                issues.append(f"{book['book']} {chapter['chapter']}:{verse['number']}\t{json.dumps(bad, ensure_ascii=False)}")
        chapter_texts.append('\n'.join(lines))
        texts.extend('\n'.join(lines[i:i+2]) for i in range(len(lines)-1))
texts.extend(chapter_texts)
texts.extend(chapter_texts[i]+'\n'+chapter_texts[i+1] for i in range(len(chapter_texts)-1))
with tempfile.TemporaryDirectory(prefix='inline-gap-audit-') as temp:
    temp = pathlib.Path(temp)
    corpus = temp / 'corpus.txt'
    corpus.write_text('\n'.join(base64.b64encode(t.encode()).decode() for t in texts))
    names = ['ExerciseTextLogic','ReviewLogic','InlineGapLogic','AcceptedTextLogic','LessonCurriculumLogic','WordOrderLogic','GapRotationLogic']
    sources = [ROOT/f'app/src/main/java/org/biblememory/app/{n}.java' for n in names]
    subprocess.run(['javac','-encoding','UTF-8','-d',str(temp),*map(str,sources),str(ROOT/'tests/inlinegap/InlineGapAudit.java')],check=True)
    subprocess.run(['java','-Xmx512m','-cp',str(temp),'org.biblememory.app.InlineGapAudit',str(corpus)],check=True)
print(f"Books={len(data['books'])}; chapters={chapters}; verses={verses}; affected verses before={len(issues)}; punctuation tokens before={bad_tokens}")
(ROOT/'tests/INLINE_GAP_AFFECTED_VERSES.tsv').write_text('reference\tstandalone punctuation (potential empty targets before fix)\n'+'\n'.join(issues)+'\n')
