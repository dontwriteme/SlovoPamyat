#!/usr/bin/env python3
"""Audit every Synodal verse and adjacent within-chapter pair using actual Java grading code."""
import base64
import json
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSET = ROOT / "app/src/main/assets/synodal.json"
CLASSES = [
    "ExerciseTextLogic", "ReviewLogic", "LessonCurriculumLogic",
    "AcceptedTextLogic", "AnswerReviewLogic",
]
JAVA = ROOT / "app/src/main/java/org/biblememory/app"
DATA = json.loads(ASSET.read_text(encoding="utf-8"))
assert len(DATA["books"]) == 66, "Expected the 66-book bundled Synodal Bible"
corpus = []
verses = 0
for book in DATA["books"]:
    for chapter in book["chapters"]:
        texts = [v["text"] for v in chapter["verses"]]
        corpus.extend(texts)
        corpus.extend(texts[i] + "\n" + texts[i + 1] for i in range(len(texts) - 1))
        verses += len(texts)

with tempfile.TemporaryDirectory(prefix="orthography-audit-") as d:
    work = pathlib.Path(d)
    payload = work / "corpus.txt"
    payload.write_text(
        "\n".join(base64.b64encode(t.encode("utf-8")).decode("ascii") for t in corpus),
        encoding="utf-8",
    )
    source_files = [str(JAVA / (name + ".java")) for name in CLASSES]
    source_files.append(str(ROOT / "tests/OrthographyCorpusAudit.java"))
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(work), *source_files], check=True)
    subprocess.run(
        ["java", "-Xmx512m", "-cp", str(work),
         "org.biblememory.app.OrthographyCorpusAudit", str(payload)],
        check=True,
    )
print(f"Checked {len(DATA['books'])} books, {verses} individual verses, {len(corpus)} corpus texts")
