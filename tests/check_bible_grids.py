"""Validate numeric grid bounds and passage counts against the actual bundled Bible."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
books = json.loads((root / 'app/src/main/assets/synodal.json').read_text())['books']
assert len(books) == 66
for book in books:
    assert [c['chapter'] for c in book['chapters']] == list(range(1, len(book['chapters']) + 1))
    for chapter in book['chapters']:
        assert [v['number'] for v in chapter['verses']] == list(range(1, len(chapter['verses']) + 1))
psalms = next(b for b in books if b['bookId'] == 19)['chapters']
assert len(psalms) == 150
assert len(psalms[117]['verses']) == 176
matthew = next(b for b in books if b['bookId'] == 40)['chapters']
assert len(matthew[4]['verses'][2:12]) == 10
assert sum(len(c['verses']) for c in matthew[4:7]) - 2 == 109
print('PASS: 66 books have contiguous chapter/verse numbers; Psalms 150/176; Matthew ranges 10/109')
