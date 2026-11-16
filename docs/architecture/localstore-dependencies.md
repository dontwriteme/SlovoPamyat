# LocalStore: анализ до этапа 3

База: c160206. 1 401 строка; все методы ниже связаны Java Compiler Tree API.

Один SQLiteOpenHelper, slovo_pamyat.db, версия 8. Нативные миграционные fixtures этапов 1/2 покрывают версии 1–7; этап 2: 98 unit-тестов и 23 storage guards.

## Таблицы, ключи и индексы

- `groups_`: `CREATE TABLE groups_ (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL)`
- `entries`: `CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, group_id INTEGER NOT NULL REFERENCES groups_(id) ON DELETE CASCADE, title TEXT NOT NULL, reference TEXT NOT NULL, lines TEXT NOT NULL, learned INTEGER NOT NULL DEFAULT 0, repetitions INTEGER NOT NULL DEFAULT 0, days INTEGER NOT NULL DEFAULT 0, ease REAL NOT NULL DEFAULT 2.5, due INTEGER NOT NULL DEFAULT 0, last_score INTEGER NOT NULL DEFAULT -1)`
- `verse_progress`: `CREATE TABLE IF NOT EXISTS verse_progress (entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, position INTEGER NOT NULL, best_score INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(entry_id,position))`
- `stage_progress`: `CREATE TABLE IF NOT EXISTS stage_progress (entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, stage TEXT NOT NULL, first_line INTEGER NOT NULL, last_line INTEGER NOT NULL, best_score INTEGER NOT NULL, PRIMARY KEY(entry_id,stage,first_line,last_line))`
- `recall_activity`: `CREATE TABLE IF NOT EXISTS recall_activity (entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, stage TEXT NOT NULL, first_line INTEGER NOT NULL, last_line INTEGER NOT NULL, last_attempt INTEGER NOT NULL DEFAULT 0, first_success INTEGER NOT NULL DEFAULT 0, last_success INTEGER NOT NULL DEFAULT 0, success_count INTEGER NOT NULL DEFAULT 0, fail_streak INTEGER NOT NULL DEFAULT 0, last_score INTEGER NOT NULL DEFAULT -1, PRIMARY KEY(entry_id,stage,first_line,last_line))`
- `lesson_progress`: `CREATE TABLE IF NOT EXISTS lesson_progress (entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, position INTEGER NOT NULL, completed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(entry_id,position))`
- `review_schedule`: `CREATE TABLE IF NOT EXISTS review_schedule (entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, stage TEXT NOT NULL, first_line INTEGER NOT NULL, last_line INTEGER NOT NULL, level INTEGER NOT NULL DEFAULT 0, due INTEGER NOT NULL, last_score INTEGER NOT NULL DEFAULT -1, PRIMARY KEY(entry_id,stage,first_line,last_line))`
- `review_attempts`: `CREATE TABLE IF NOT EXISTS review_attempts (id INTEGER PRIMARY KEY AUTOINCREMENT, entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, stage TEXT NOT NULL, first_line INTEGER NOT NULL, last_line INTEGER NOT NULL, attempted_at INTEGER NOT NULL, accuracy INTEGER NOT NULL, assisted INTEGER NOT NULL)`
- `transition_issues`: `CREATE TABLE IF NOT EXISTS transition_issues (entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE, from_position INTEGER NOT NULL, to_position INTEGER NOT NULL, failures INTEGER NOT NULL DEFAULT 0, last_failed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(entry_id,from_position,to_position))`
- `card_decks`: `CREATE TABLE IF NOT EXISTS card_decks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL)`
- `flashcards`: `CREATE TABLE IF NOT EXISTS flashcards (id INTEGER PRIMARY KEY AUTOINCREMENT, book_id INTEGER NOT NULL, first_chapter INTEGER NOT NULL, first_verse INTEGER NOT NULL, last_chapter INTEGER NOT NULL, last_verse INTEGER NOT NULL, reference TEXT NOT NULL, content TEXT NOT NULL, UNIQUE(book_id,first_chapter,first_verse,last_chapter,last_verse))`
- `card_deck_members`: `CREATE TABLE IF NOT EXISTS card_deck_members (deck_id INTEGER NOT NULL REFERENCES card_decks(id) ON DELETE CASCADE, card_id INTEGER NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE, PRIMARY KEY(deck_id,card_id))`
- `study_order`: `CREATE TABLE IF NOT EXISTS study_order (entry_id INTEGER PRIMARY KEY REFERENCES entries(id) ON DELETE CASCADE, group_id INTEGER NOT NULL, position INTEGER NOT NULL)`
- `card_order`: `CREATE TABLE IF NOT EXISTS card_order (deck_id INTEGER NOT NULL REFERENCES card_decks(id) ON DELETE CASCADE, card_id INTEGER NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE, position INTEGER NOT NULL, PRIMARY KEY(deck_id,card_id))`
- `unified_collections`: `CREATE TABLE IF NOT EXISTS unified_collections (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, group_id INTEGER NOT NULL UNIQUE REFERENCES groups_(id), deck_id INTEGER NOT NULL UNIQUE REFERENCES card_decks(id), origin TEXT NOT NULL)`
- `unified_card_entries`: `CREATE TABLE IF NOT EXISTS unified_card_entries (collection_id INTEGER NOT NULL REFERENCES unified_collections(id) ON DELETE CASCADE, card_id INTEGER NOT NULL, entry_id INTEGER NOT NULL, PRIMARY KEY(collection_id,card_id))`
- `unified_entry_cards`: `CREATE TABLE IF NOT EXISTS unified_entry_cards (collection_id INTEGER NOT NULL REFERENCES unified_collections(id) ON DELETE CASCADE, entry_id INTEGER NOT NULL, verse_position INTEGER NOT NULL, card_id INTEGER NOT NULL, PRIMARY KEY(collection_id,entry_id,verse_position))`

Явных CREATE INDEX нет. SQLite создаёт индексы для PRIMARY KEY/UNIQUE; FK не всегда индексирован (например entries.group_id). На этом этапе DDL не меняется.

## Методы и фактические зависимости

| Метод | Строка | Владелец | Вызываемые методы LocalStore |
| --- | ---: | --- | --- |
| `<init>/1` | 32 | LocalStore: infrastructure/migration |  |
| `onConfigure/1` | 33 | LocalStore: infrastructure/migration |  |
| `onCreate/1` | 34 | LocalStore: infrastructure/migration | `createFlashcardTables`, `createLessonTables`, `createOrderTables`, `createProgressTables`, `createRecallTables`, `createUnifiedTables`, `createWeakTransitionsTable`, `migrateLegacyCollections` |
| `createProgressTables/1` | 46 | LocalStore: infrastructure/migration |  |
| `createRecallTables/1` | 50 | LocalStore: infrastructure/migration |  |
| `createLessonTables/1` | 53 | LocalStore: infrastructure/migration |  |
| `createWeakTransitionsTable/1` | 58 | LocalStore: infrastructure/migration |  |
| `createFlashcardTables/1` | 62 | LocalStore: infrastructure/migration |  |
| `createOrderTables/1` | 67 | LocalStore: infrastructure/migration |  |
| `createUnifiedTables/1` | 73 | LocalStore: infrastructure/migration |  |
| `migrateLegacyCollections/1` | 79 | LocalStore: infrastructure/migration |  |
| `unifiedCollections/0` | 106 | CollectionRepository |  |
| `unifiedCollection/1` | 121 | CollectionRepository | `unifiedCollections` |
| `unifiedForGroup/1` | 125 | CollectionRepository | `unifiedCollections` |
| `createUnifiedCollection/1` | 129 | CollectionRepository |  |
| `renameUnifiedCollection/2` | 144 | CollectionRepository | `unifiedCollection` |
| `deleteUnifiedCollection/1` | 157 | CollectionRepository |  |
| `clearUnifiedCollection/1` | 164 | CollectionRepository | `libraryId`, `unifiedCollection` |
| `containsEntry/2` | 184 | StudyRepository |  |
| `belongsToGroupMaterial/2` | 190 | CollectionRepository | `bibleBookFromReference`, `entries`, `entry` |
| `bibleBookFromReference/1` | 212 | CollectionRepository |  |
| `verseNumbers/1` | 217 | CollectionRepository |  |
| `syncUnifiedCollection/2` | 222 | CollectionRepository | `syncUnifiedCollectionInTransaction` |
| `syncUnifiedCollectionInTransaction/2` | 234 | CollectionRepository | `add`, `addCustomFlashcard`, `addFlashcard`, `bibleBookFromReference`, `entries`, `flashcards`, `mapCardEntry`, `mapEntryCard`, `mappedCardEntry`, `mappedEntryCard`, `unifiedCollection`, `verseNumbers` |
| `mappedCardEntry/2` | 287 | CollectionRepository |  |
| `mappedEntryCard/3` | 291 | CollectionRepository |  |
| `mapCardEntry/3` | 298 | CollectionRepository |  |
| `mapEntryCard/4` | 302 | CollectionRepository |  |
| `newCardDeck/1` | 315 | FlashcardRepository |  |
| `cardDecks/0` | 319 | FlashcardRepository |  |
| `cardDeck/1` | 328 | FlashcardRepository | `cardDecks` |
| `renameCardDeck/2` | 329 | FlashcardRepository |  |
| `deleteCardDeck/1` | 333 | FlashcardRepository |  |
| `addFlashcard/8` | 338 | FlashcardRepository |  |
| `addCustomFlashcard/3` | 359 | FlashcardRepository |  |
| `flashcards/1` | 378 | FlashcardRepository |  |
| `removeFlashcard/2` | 391 | FlashcardRepository |  |
| `recordWeakTransition/4` | 398 | ProgressRepository |  |
| `rescheduleWeakVerse/3` | 407 | ProgressRepository | `isStepMastered` |
| `matchingVerseLocations/2` | 428 | ProgressRepository | `allEntries`, `entry` |
| `sharedVerseCompletion/2` | 457 | ProgressRepository | `matchingVerseLocations` |
| `exerciseDone/2` | 474 | ProgressRepository | `isStepMastered`, `sharedVerseCompletion` |
| `unlockedPosition/2` | 484 | ProgressRepository |  |
| `finishExercise/3` | 498 | ProgressRepository | `exerciseDone` |
| `scheduleReview/7` | 507 | ProgressRepository | `isStepMastered` |
| `dueItems/1` | 532 | ProgressRepository |  |
| `dueCount/1` | 543 | ProgressRepository | `dueItems` |
| `onUpgrade/3` | 544 | LocalStore: infrastructure/migration | `createFlashcardTables`, `createLessonTables`, `createOrderTables`, `createProgressTables`, `createRecallTables`, `createUnifiedTables`, `createWeakTransitionsTable`, `migrateLegacyCollections` |
| `masteredLinkedSegments/1` | 581 | ProgressRepository |  |
| `masteryCount/1` | 588 | ProgressRepository | `entry`, `isStepMastered` |
| `firstUnmastered/2` | 597 | ProgressRepository | `isStepMastered` |
| `isStepMastered/4` | 601 | ProgressRepository | `matchingVerseLocations` |
| `markMastered/5` | 623 | ProgressRepository |  |
| `recordRecall/7` | 639 | ProgressRepository |  |
| `recentFailures/4` | 663 | ProgressRepository |  |
| `consolidatedLocal/4` | 670 | ProgressRepository |  |
| `consolidated/4` | 678 | ProgressRepository | `consolidatedLocal`, `matchingVerseLocations` |
| `recallDue/5` | 686 | ProgressRepository |  |
| `progressFor/1` | 695 | ProgressRepository | `isStepMastered`, `recallDue`, `recentFailures` |
| `newGroup/1` | 708 | CollectionRepository |  |
| `groups/0` | 712 | CollectionRepository |  |
| `group/1` | 719 | CollectionRepository | `groups` |
| `add/4` | 723 | StudyRepository |  |
| `canonicalStudyEntryId/1` | 738 | StudyRepository | `completedExerciseCount`, `entry`, `masteryCount` |
| `completedExerciseCount/1` | 758 | ProgressRepository |  |
| `findOrCreateCardStudyEntry/2` | 766 | StudyRepository | `add`, `allEntries`, `canonicalStudyEntryId`, `newGroup` |
| `reorderStudy/2` | 790 | StudyRepository | `entry` |
| `reorderCards/2` | 805 | FlashcardRepository |  |
| `libraryId/1` | 818 | StudyRepository |  |
| `removeUnifiedPassage/2` | 826 | CollectionRepository | `detachStudyReference`, `unifiedForGroup` |
| `detachStudyReference/2` | 851 | CollectionRepository | `libraryId` |
| `addPickedPassage/4` | 862 | CollectionRepository | `addPickedPassage` |
| `addPickedPassage/5` | 865 | CollectionRepository | `entries`, `reorderStudy`, `restoreOrAddStudyPassage`, `syncUnifiedCollection`, `unifiedForGroup` |
| `restoreOrAddStudyPassage/3` | 889 | StudyRepository | `add`, `allEntries`, `group` |
| `reviseStudyPassage/3` | 913 | StudyRepository | `entry` |
| `changeCardInDeck/9` | 1002 | FlashcardRepository | `addCustomFlashcard`, `addFlashcard`, `removeFlashcard` |
| `readEntry/1` | 1016 | StudyRepository |  |
| `entry/1` | 1024 | StudyRepository | `readEntry` |
| `entries/1` | 1029 | StudyRepository | `readEntry` |
| `allEntries/0` | 1036 | StudyRepository | `readEntry` |
| `due/0` | 1044 | ProgressRepository | `dueItems`, `entry` |
| `update/4` | 1051 | ProgressRepository |  |
| `consolidatedVerseCount/1` | 1058 | ProgressRepository | `consolidated`, `entry` |
| `statusesForChapter/2` | 1076 | ProgressRepository | `allEntries`, `consolidated`, `exerciseDone`, `isStepMastered` |
| `renameGroup/2` | 1122 | CollectionRepository |  |
| `renameEntry/2` | 1126 | StudyRepository |  |
| `deleteEntry/1` | 1130 | StudyRepository |  |
| `deleteGroup/1` | 1131 | CollectionRepository |  |
| `groupsForBackup/0` | 1133 | LibraryBackup |  |
| `exportJson/0` | 1143 | LibraryBackup | `entries`, `groupsForBackup` |
| `importJson/1` | 1218 | LibraryBackup |  |

## Согласованные операции и границы

- create/rename unified: groups_, card_decks, unified_collections — одна существующая транзакция.
- clear/remove/revise passage: entries, order, mapping/membership, progress — общий database/transaction. При detach материал перемещается в скрытую библиотеку, история не удаляется.
- sync: обе mapping-таблицы, entries, flashcards и membership. Stage 2 уже добавил общий rollback/retry; сохранить вложенные транзакции того же helper.
- scheduleReview: INSERT review_attempts + UPDATE review_schedule атомарны. recordRecall — отдельная существующая граница; полный outcome упражнения относится к следующему этапу.
- addPickedPassage: восстановление записи + sync + проверка полноты + порядок — один transaction owner.
- change/remove card и removeUnifiedPassage сейчас имеют окна между записями: добавить fault-injection и внешний rollback после механического извлечения.
- reviseStudyPassage переносит позиции семи таблиц истории; сохраняем прежнее сопоставление текстов/границ, не меняем алгоритм.
- importJson проверяет полный документ до append и записывает материалы/историю одной транзакцией; формат v1–v5 не меняется. Карточки и unified metadata в старый формат не добавляются.

## Безопасная последовательность

Baseline build/tests → RepositoryScope (одно подключение и transaction helper) → Study → Progress → Flashcard → Collection → LibraryBackup (существующий codec) → общие транзакции/fault injection → typed callers → подтверждённые query bottlenecks → upgrade/regression. После каждого шага сборка/тесты/коммит. Модели LocalStore.* и совместимый старый API сохраняются без копий реализации.
