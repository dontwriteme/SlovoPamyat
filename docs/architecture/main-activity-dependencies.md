# MainActivity: карта зависимостей до извлечения

База: be7d97d; Java Compiler Tree API, связывание символов по типам.

Все 189 полей и 280 методов верхнего уровня перечислены ниже. Числа строк относятся к исходным 6482 строкам. Это карта предлагаемого владельца, не утверждение, что весь UI-код уже перенесён.

Порядок: исправления выявленной безопасности данных → Flashcard → Study → BibleReader → Collection → восстановление Activity и общая регрессия. Небольшой Flashcard-компонент первым проверяет механизм извлечения. Lifecycle, Screen, View/анимации/кэш остаются в Activity; общие типы не ссылаются обратно на Activity.

## Поля

| Поле | Тип | Предлагаемый владелец |
| --- | --- | --- |
| `BG` | `int` | MainActivity: UI/navigation/platform |
| `CARD` | `int` | MainActivity: UI/navigation/platform |
| `CARD_ALT` | `int` | MainActivity: UI/navigation/platform |
| `WHITE` | `int` | MainActivity: UI/navigation/platform |
| `MUTED` | `int` | MainActivity: UI/navigation/platform |
| `GREEN` | `int` | MainActivity: UI/navigation/platform |
| `VIOLET` | `int` | MainActivity: UI/navigation/platform |
| `BORDER` | `int` | MainActivity: UI/navigation/platform |
| `RED` | `int` | MainActivity: UI/navigation/platform |
| `HOME_SURFACE` | `int` | MainActivity: UI/navigation/platform |
| `HOME_BORDER` | `int` | MainActivity: UI/navigation/platform |
| `SUCCESS` | `int` | MainActivity: UI/navigation/platform |
| `GOLD` | `int` | MainActivity: UI/navigation/platform |
| `PRIMARY_ON` | `int` | MainActivity: UI/navigation/platform |
| `EXPORT` | `int` | MainActivity: UI/navigation/platform |
| `IMPORT` | `int` | MainActivity: UI/navigation/platform |
| `screen` | `Screen` | MainActivity: UI/navigation/platform |
| `UNIFIED_PREFS` | `String` | MainActivity: UI/navigation/platform |
| `activeUnifiedId` | `long` | CollectionController |
| `openUnifiedId` | `long` | CollectionController |
| `cardFromUnified` | `boolean` | FlashcardSessionController |
| `reviewCollectionGroup` | `long` | StudySessionController |
| `runLines` | `List<RunLine>` | StudySessionController |
| `runCollectionId` | `long` | StudySessionController |
| `runCursor` | `int` | StudySessionController |
| `runCorrect` | `int` | StudySessionController |
| `runMistakes` | `List<String>` | StudySessionController |
| `runDraft` | `String` | StudySessionController |
| `runChecked` | `boolean` | StudySessionController |
| `runPassed` | `boolean` | StudySessionController |
| `store` | `LocalStore` | MainActivity: UI/navigation/platform |
| `bible` | `Bible` | BibleReaderController |
| `bibleError` | `String` | BibleReaderController |
| `selectedBook` | `int` | BibleReaderController |
| `selectedChapter` | `int` | BibleReaderController |
| `mask` | `int` | StudySessionController |
| `stepCursor` | `int` | StudySessionController |
| `focusVerse` | `int` | BibleReaderController |
| `studyStage` | `StudyPlanLogic.Stage` | StudySessionController |
| `selectedGroup` | `long` | CollectionController |
| `selectedEntry` | `long` | StudySessionController |
| `customTargetGroupId` | `long` | CollectionController |
| `checked` | `boolean` | StudySessionController |
| `passed` | `boolean` | StudySessionController |
| `trainingMode` | `TrainingMode` | StudySessionController |
| `orderChunk` | `int` | StudySessionController |
| `orderPosition` | `int` | StudySessionController |
| `orderWrongTile` | `int` | StudySessionController |
| `orderMistakes` | `int` | StudySessionController |
| `orderBankOrder` | `int[]` | StudySessionController |
| `usedWordTiles` | `List<Integer>` | StudySessionController |
| `testAssisted` | `boolean` | StudySessionController |
| `currentScroller` | `ScrollView` | MainActivity: UI/navigation/platform |
| `HOME_UI_PREFS` | `String` | MainActivity: UI/navigation/platform |
| `homeCollectionsExpanded` | `boolean` | MainActivity: UI/navigation/platform |
| `homeScrollY` | `int` | MainActivity: UI/navigation/platform |
| `homeScrollRestored` | `boolean` | MainActivity: UI/navigation/platform |
| `lastRenderedScreen` | `Screen` | MainActivity: UI/navigation/platform |
| `collectionAnimator` | `ValueAnimator` | MainActivity: UI/navigation/platform |
| `draft` | `String` | StudySessionController |
| `lastExpected` | `String` | StudySessionController |
| `lastTestRef` | `String` | StudySessionController |
| `lastTestStep` | `String` | StudySessionController |
| `homeIndex` | `int` | MainActivity: UI/navigation/platform |
| `HOME_REFS` | `int[][]` | MainActivity: UI/navigation/platform |
| `lastAccuracy` | `int` | StudySessionController |
| `content` | `LinearLayout` | MainActivity: UI/navigation/platform |
| `root` | `LinearLayout` | MainActivity: UI/navigation/platform |
| `homeHandler` | `Handler` | MainActivity: UI/navigation/platform |
| `homeTicker` | `Runnable` | MainActivity: UI/navigation/platform |
| `activeTextArea` | `LinearLayout` | MainActivity: UI/navigation/platform |
| `activeWorkArea` | `LinearLayout` | MainActivity: UI/navigation/platform |
| `activePassage` | `TextView` | MainActivity: UI/navigation/platform |
| `activeStatus` | `TextView` | MainActivity: UI/navigation/platform |
| `bottomModeButtons` | `TextView[]` | MainActivity: UI/navigation/platform |
| `SESSION_PREFS` | `String` | MainActivity: UI/navigation/platform |
| `activeEntry` | `LocalStore.Entry` | StudySessionController |
| `activeStep` | `StudyPlanLogic.Step` | StudySessionController |
| `activeMastered` | `boolean` | StudySessionController |
| `reviewMode` | `boolean` | StudySessionController |
| `groupMode` | `boolean` | StudySessionController |
| `returnVerseCursor` | `int` | StudySessionController |
| `forcedStep` | `StudyPlanLogic.Step` | StudySessionController |
| `currentReview` | `LocalStore.DueItem` | StudySessionController |
| `blurSeed` | `int` | StudySessionController |
| `orderCompleted` | `boolean` | StudySessionController |
| `lessonVariant` | `int` | StudySessionController |
| `lessonChunk` | `int` | StudySessionController |
| `lessonPicked` | `int` | StudySessionController |
| `lessonWrongTile` | `int` | StudySessionController |
| `lessonSeed` | `long` | StudySessionController |
| `lessonUsedTiles` | `List<Integer>` | StudySessionController |
| `lessonPracticeAgain` | `boolean` | StudySessionController |
| `lessonWriteHadFeedback` | `boolean` | StudySessionController |
| `revealCleanup` | `Runnable` | MainActivity: UI/navigation/platform |
| `reviewQueue` | `List<LocalStore.DueItem>` | StudySessionController |
| `reviewPointer` | `int` | StudySessionController |
| `lessonBuildPrompt` | `TextView` | MainActivity: UI/navigation/platform |
| `lessonBuildCounter` | `TextView` | MainActivity: UI/navigation/platform |
| `lessonBuildError` | `TextView` | MainActivity: UI/navigation/platform |
| `lessonBuildAction` | `TextView` | MainActivity: UI/navigation/platform |
| `lessonBuildTiles` | `List<TextView>` | MainActivity: UI/navigation/platform |
| `lessonBuildRequired` | `int` | MainActivity: UI/navigation/platform |
| `lessonHotbarCells` | `List<List<TextView>>` | MainActivity: UI/navigation/platform |
| `lessonHotbarCounter` | `TextView` | MainActivity: UI/navigation/platform |
| `weakVerse` | `int` | StudySessionController |
| `weakTransitionFrom` | `int` | StudySessionController |
| `readerVerseNumber` | `int` | BibleReaderController |
| `chapterFromVerseGrid` | `boolean` | BibleReaderController |
| `selectingPassage` | `boolean` | BibleReaderController |
| `rangeStartChapter` | `int` | BibleReaderController |
| `rangeStartVerse` | `int` | BibleReaderController |
| `rangeEndChapter` | `int` | BibleReaderController |
| `rangeEndVerse` | `int` | BibleReaderController |
| `lessonWriteVariant` | `int` | StudySessionController |
| `groupBuildDone` | `boolean` | StudySessionController |
| `groupIntroDone` | `boolean` | StudySessionController |
| `inlineDrafts` | `String[]` | StudySessionController |
| `inlineFields` | `List<EditText>` | MainActivity: UI/navigation/platform |
| `inlineExpected` | `List<String>` | StudySessionController |
| `bibleSearchQuery` | `String` | BibleReaderController |
| `bibleSearchError` | `String` | BibleReaderController |
| `bibleSearchActive` | `boolean` | BibleReaderController |
| `bibleSearchLoading` | `boolean` | BibleReaderController |
| `bibleSearchHits` | `List<BibleSearchLogic.Hit>` | BibleReaderController |
| `searchVisibleLimit` | `int` | BibleReaderController |
| `searchGeneration` | `int` | BibleReaderController |
| `bibleSearchHandler` | `Handler` | BibleReaderController |
| `bibleSearchExecutor` | `java.util.concurrent.ExecutorService` | BibleReaderController |
| `bibleSearchTask` | `java.util.concurrent.Future<?>` | BibleReaderController |
| `bibleSearchDebounce` | `Runnable` | BibleReaderController |
| `bibleSearchNeedsRefresh` | `boolean` | BibleReaderController |
| `savedScrollY` | `EnumMap<Screen, Integer>` | MainActivity: UI/navigation/platform |
| `collectionQueue` | `List<Long>` | CollectionController |
| `collectionQueueCursor` | `int` | CollectionController |
| `collectionQueueGroup` | `long` | CollectionController |
| `executingCollectionQueue` | `boolean` | CollectionController |
| `collectionQueueNewStep` | `boolean` | CollectionController |
| `insertAfterEntryId` | `long` | CollectionController |
| `insertAfterCardId` | `long` | CollectionController |
| `studyInsertionTrail` | `List<Long>` | CollectionController |
| `cardInsertionTrail` | `List<Long>` | CollectionController |
| `readyTabs` | `EnumMap<Screen, ReadyTab>` | MainActivity: UI/navigation/platform |
| `tabWarmHandler` | `Handler` | MainActivity: UI/navigation/platform |
| `tabWarmGeneration` | `int` | MainActivity: UI/navigation/platform |
| `tabSwitchOldNav` | `View` | MainActivity: UI/navigation/platform |
| `tabSwitchAnimator` | `ValueAnimator` | MainActivity: UI/navigation/platform |
| `tabSwitchLeaving` | `LinearLayout` | MainActivity: UI/navigation/platform |
| `tabSwitchEntering` | `LinearLayout` | MainActivity: UI/navigation/platform |
| `tabSwitchInProgress` | `boolean` | MainActivity: UI/navigation/platform |
| `COLLECTION_PREFS` | `String` | MainActivity: UI/navigation/platform |
| `swipeStartX` | `float` | MainActivity: UI/navigation/platform |
| `swipeStartY` | `float` | MainActivity: UI/navigation/platform |
| `swipeIntercepted` | `boolean` | MainActivity: UI/navigation/platform |
| `lastNavIndex` | `int` | MainActivity: UI/navigation/platform |
| `selectedDeck` | `long` | FlashcardSessionController |
| `cardSequence` | `List<Long>` | FlashcardSessionController |
| `cardIndex` | `int` | FlashcardSessionController |
| `cardShowReference` | `boolean` | FlashcardSessionController |
| `cardRevealed` | `boolean` | FlashcardSessionController |
| `cardRandom` | `Random` | FlashcardSessionController |
| `pickerTarget` | `BibleTarget` | BibleReaderController |
| `pickerTargetId` | `long` | BibleReaderController |
| `pickerRange` | `boolean` | BibleReaderController |
| `pickerHasAnchor` | `boolean` | BibleReaderController |
| `pickerChooseEnd` | `boolean` | BibleReaderController |
| `pickerBackScreen` | `Screen` | BibleReaderController |
| `passageSelection` | `PassageSelection` | BibleReaderController |
| `pickerQuickButtons` | `List<View>` | MainActivity: UI/navigation/platform |
| `pickerSelectionLabel` | `TextView` | MainActivity: UI/navigation/platform |
| `pickerSaveButton` | `TextView` | MainActivity: UI/navigation/platform |
| `pickerSelectionPanel` | `LinearLayout` | MainActivity: UI/navigation/platform |
| `pickerRestoredScroll` | `int` | BibleReaderController |
| `pickerQuickBook` | `int` | BibleReaderController |
| `pickerQuickChapter` | `int` | BibleReaderController |
| `pickerQuickScroll` | `int` | BibleReaderController |
| `pickerVerseNumber` | `int` | BibleReaderController |
| `readerBookBeforePicker` | `int` | BibleReaderController |
| `readerChapterBeforePicker` | `int` | BibleReaderController |
| `cardStudyQueue` | `List<Long>` | FlashcardSessionController |
| `cardStudyCursor` | `int` | FlashcardSessionController |
| `cardStudyDeckId` | `long` | FlashcardSessionController |
| `practiceWeakFragment` | `boolean` | StudySessionController |
| `weakFragmentText` | `String` | StudySessionController |
| `rotationExerciseKey` | `String` | StudySessionController |
| `rotationMask` | `String` | StudySessionController |
| `rotationExerciseSeed` | `long` | StudySessionController |
| `collectionsIconCycle` | `int` | MainActivity: UI/navigation/platform |
| `lessonErrorArea` | `LinearLayout` | MainActivity: UI/navigation/platform |

## Методы

В последней колонке — зависимости состояния другого компонента. Вызовы методов сохранены в исходной карте javac; перенос границ требует явных ports/callbacks, без передачи Activity целиком.

| Метод / число аргументов | Строка | Предлагаемый владелец | Чужое состояние |
| --- | --- | --- | --- |
| `isMainTab/1` | 194 | MainActivity |  |
| `prepareOtherTabs/0` | 200 | MainActivity | `bible`, `bibleSearchActive` |
| `prepareTab/1` | 236 | MainActivity | `bibleSearchActive`, `bibleSearchError`, `bibleSearchLoading` |
| `animatePreparedTabIndicator/3` | 322 | MainActivity |  |
| `onCreate/1` | 409 | MainActivity | `activeUnifiedId`, `bible`, `bibleError` |
| `isPickerScreen/1` | 436 | BibleReaderController |  |
| `onSaveInstanceState/1` | 440 | MainActivity | `insertAfterCardId`, `insertAfterEntryId`, `openUnifiedId`, `passageSelection`, `pickerBackScreen`, `pickerChooseEnd`, `pickerQuickBook`, `pickerQuickChapter`, `pickerQuickScroll`, `pickerTarget`, `pickerTargetId`, `pickerVerseNumber`, `readerBookBeforePicker`, `readerChapterBeforePicker`, `selectedBook`, `selectedChapter`, `selectedDeck`, `selectedGroup` |
| `restorePickerState/1` | 457 | BibleReaderController | `insertAfterCardId`, `insertAfterEntryId`, `openUnifiedId`, `screen`, `selectedDeck`, `selectedGroup` |
| `onResume/0` | 480 | MainActivity |  |
| `onPause/0` | 487 | MainActivity |  |
| `scheduleHomeRefresh/0` | 499 | MainActivity |  |
| `refreshHomeIndex/1` | 506 | MainActivity |  |
| `onBackPressed/0` | 524 | MainActivity | `cardFromUnified`, `cardStudyCursor`, `cardStudyDeckId`, `cardStudyQueue`, `chapterFromVerseGrid`, `currentReview`, `customTargetGroupId`, `executingCollectionQueue`, `focusVerse`, `forcedStep`, `groupMode`, `openUnifiedId`, `passageSelection`, `pickerBackScreen`, `pickerChooseEnd`, `pickerQuickBook`, `pickerQuickChapter`, `pickerQuickScroll`, `pickerRestoredScroll`, `readerBookBeforePicker`, `readerChapterBeforePicker`, `reviewMode`, `reviewPointer`, `reviewQueue`, `selectedBook`, `selectedChapter`, `selectedGroup` |
| `dp/1` | 571 | MainActivity |  |
| `shape/2` | 572 | MainActivity |  |
| `outlined/3` | 575 | MainActivity |  |
| `gradient/0` | 578 | MainActivity |  |
| `pageBackground/0` | 584 | MainActivity |  |
| `softAccent/0` | 612 | MainActivity |  |
| `displayText/2` | 616 | MainActivity |  |
| `prose/2` | 621 | MainActivity |  |
| `bookInk/1` | 626 | MainActivity |  |
| `illustratedBanner/4` | 633 | MainActivity |  |
| `txt/4` | 654 | MainActivity |  |
| `column/0` | 661 | MainActivity |  |
| `row/0` | 662 | MainActivity |  |
| `add/3` | 663 | MainActivity |  |
| `gap/2` | 666 | MainActivity |  |
| `card/1` | 667 | MainActivity |  |
| `headerCard/1` | 672 | MainActivity |  |
| `landscapeFor/1` | 677 | MainActivity |  |
| `homeSurface/0` | 688 | MainActivity |  |
| `landscapeThumbnail/1` | 694 | MainActivity |  |
| `homeVerseHero/3` | 707 | MainActivity |  |
| `button/3` | 904 | MainActivity |  |
| `action/4` | 912 | MainActivity |  |
| `tapCard/5` | 913 | MainActivity |  |
| `label/2` | 923 | MainActivity |  |
| `helper/2` | 924 | MainActivity |  |
| `progress/3` | 925 | MainActivity |  |
| `input/2` | 934 | MainActivity |  |
| `forbidKeyboardHints/2` | 946 | MainActivity |  |
| `numeric/2` | 953 | MainActivity |  |
| `number/1` | 957 | MainActivity |  |
| `value/1` | 958 | MainActivity |  |
| `toast/1` | 959 | MainActivity |  |
| `tabWordmark/0` | 961 | MainActivity |  |
| `title/2` | 964 | MainActivity |  |
| `testamentHeading/2` | 976 | MainActivity |  |
| `rememberHomeScroll/0` | 988 | MainActivity |  |
| `render/0` | 994 | MainActivity | `bible`, `bibleSearchActive`, `bibleSearchError`, `bibleSearchLoading`, `bibleSearchNeedsRefresh`, `chapterFromVerseGrid`, `focusVerse`, `pickerRestoredScroll` |
| `renderScrollingBibleSearch/1` | 1114 | MainActivity | `bibleSearchQuery` |
| `isBibleSearchScreen/0` | 1162 | MainActivity |  |
| `cancelBibleSearchWork/0` | 1163 | BibleReaderController |  |
| `resumeBibleSearchIfNeeded/0` | 1170 | BibleReaderController | `screen` |
| `findBibleSearchInput/1` | 1180 | MainActivity |  |
| `renderBibleSearchKeepingInput/0` | 1190 | MainActivity |  |
| `visibleSearchResults/0` | 1201 | MainActivity |  |
| `onBibleSearchChanged/2` | 1208 | BibleReaderController | `screen` |
| `submitBibleSearch/1` | 1230 | BibleReaderController | `screen` |
| `highlightSearchWords/2` | 1267 | MainActivity |  |
| `renderSearchResults/0` | 1288 | MainActivity | `bibleSearchError`, `bibleSearchHits`, `bibleSearchLoading`, `bibleSearchQuery` |
| `createSearchResultsList/0` | 1325 | MainActivity | `bibleSearchActive`, `bibleSearchHits`, `bibleSearchQuery`, `chapterFromVerseGrid`, `focusVerse`, `selectedBook`, `selectedChapter` |
| `addNav/1` | 1653 | MainActivity |  |
| `animateCollectionsIcon/3` | 1716 | MainActivity |  |
| `settleCollectionsIcons/0` | 1721 | MainActivity |  |
| `settleCollectionsIcon/1` | 1725 | MainActivity |  |
| `animateHomeIcon/3` | 1729 | MainActivity |  |
| `settleHomeIcons/0` | 1734 | MainActivity |  |
| `settleHomeIcon/1` | 1738 | MainActivity |  |
| `animateBibleIcon/4` | 1742 | MainActivity |  |
| `settleBibleIcons/0` | 1747 | MainActivity |  |
| `settleBibleIcon/1` | 1751 | MainActivity |  |
| `mainTabIndex/0` | 1755 | MainActivity | `pickerTarget`, `pickerTargetId` |
| `finishTabTransition/0` | 1769 | MainActivity |  |
| `goToMainTab/1` | 1812 | MainActivity | `bibleSearchLoading`, `bibleSearchNeedsRefresh`, `forcedStep`, `groupMode`, `passageSelection`, `readerBookBeforePicker`, `readerChapterBeforePicker`, `reviewMode`, `selectedBook`, `selectedChapter` |
| `dispatchTouchEvent/1` | 2070 | MainActivity |  |
| `restoreCollectionQueue/0` | 2101 | CollectionController | `COLLECTION_PREFS`, `store` |
| `persistCollectionQueue/0` | 2112 | CollectionController | `COLLECTION_PREFS` |
| `clearCollectionQueue/0` | 2120 | CollectionController | `COLLECTION_PREFS` |
| `startStudyCollection/1` | 2125 | MainActivity | `cardStudyCursor`, `cardStudyDeckId`, `cardStudyQueue`, `collectionQueue`, `collectionQueueCursor`, `collectionQueueGroup`, `collectionQueueNewStep`, `executingCollectionQueue` |
| `resumeCollectionTraining/0` | 2146 | MainActivity | `collectionQueue`, `collectionQueueCursor`, `collectionQueueNewStep`, `executingCollectionQueue` |
| `advanceStudyCollection/0` | 2154 | MainActivity | `collectionQueue`, `collectionQueueCursor`, `collectionQueueNewStep`, `executingCollectionQueue` |
| `studyProgressFor/1` | 2166 | MainActivity |  |
| `chooseCardDeck/1` | 2175 | MainActivity |  |
| `newCardDeck/1` | 2186 | MainActivity | `openUnifiedId` |
| `deckPlayButton/1` | 2193 | MainActivity | `selectedDeck` |
| `renderCardDecks/0` | 2216 | MainActivity | `selectedDeck` |
| `showCardDeckActions/2` | 2240 | MainActivity | `selectedDeck` |
| `showActionsMenu/4` | 2257 | MainActivity |  |
| `renameCardDeck/1` | 2388 | MainActivity |  |
| `confirmDeleteCardDeck/1` | 2398 | MainActivity |  |
| `showNewCustomFlashcard/1` | 2406 | MainActivity |  |
| `renderCardDeck/0` | 2421 | MainActivity | `selectedDeck` |
| `addCardDeckFooter/2` | 2444 | MainActivity | `selectedDeck` |
| `addBibleFooter/3` | 2561 | MainActivity |  |
| `addStudyCollectionFooter/1` | 2571 | MainActivity |  |
| `openBiblePicker/3` | 2678 | BibleReaderController | `savedScrollY`, `screen` |
| `openPickerRange/0` | 2692 | BibleReaderController | `currentScroller`, `screen` |
| `beginPickerBoundary/1` | 2698 | BibleReaderController | `savedScrollY`, `screen` |
| `pickerBoundaryReference/1` | 2708 | BibleReaderController |  |
| `renderUnifiedBiblePicker/0` | 2713 | MainActivity | `passageSelection` |
| `updatePickerRangeSummary/0` | 2729 | MainActivity | `bible`, `passageSelection` |
| `afterPickerLayout/2` | 2745 | MainActivity |  |
| `scrollPickerGridTo/2` | 2753 | MainActivity |  |
| `renderPickerVerseGrid/0` | 2759 | MainActivity |  |
| `renderVerseGrid/1` | 2760 | MainActivity | `bible`, `chapterFromVerseGrid`, `focusVerse`, `passageSelection`, `pickerChooseEnd`, `pickerVerseNumber`, `readerVerseNumber`, `selectedBook`, `selectedChapter` |
| `renderPickerVerseText/0` | 2802 | MainActivity | `bible`, `focusVerse`, `passageSelection`, `pickerRestoredScroll`, `pickerTarget`, `pickerTargetId`, `selectedBook`, `selectedChapter` |
| `savePickerRange/0` | 2866 | MainActivity | `passageSelection`, `pickerBackScreen`, `readerBookBeforePicker`, `readerChapterBeforePicker`, `selectedBook`, `selectedChapter` |
| `pickerCollectionContainsVerse/3` | 2876 | CollectionController | `pickerTarget`, `pickerTargetId`, `store` |
| `togglePickerCollectionVerse/3` | 2885 | CollectionController | `bible`, `pickerTarget`, `pickerTargetId`, `store` |
| `quickBibleVerseExists/3` | 2913 | CollectionController | `store` |
| `toggleQuickBibleVerse/3` | 2921 | CollectionController | `bible`, `store` |
| `pickerHasReference/4` | 2942 | CollectionController | `pickerTarget`, `pickerTargetId`, `store` |
| `savePickedPassage/4` | 2952 | CollectionController | `selectedBook` |
| `savePickedPassageFor/5` | 2955 | CollectionController | `bible`, `pickerTarget`, `pickerTargetId`, `store` |
| `removePickedVerse/4` | 2990 | CollectionController | `pickerTarget`, `pickerTargetId`, `store` |
| `saveBibleVerseToGroup/4` | 3011 | CollectionController | `bible`, `store` |
| `studyCardDeck/0` | 3022 | FlashcardSessionController | `bible`, `executingCollectionQueue`, `store` |
| `advanceCardStudy/0` | 3043 | FlashcardSessionController | `screen` |
| `renderCardPicker/0` | 3053 | MainActivity | `selectedBook`, `selectedChapter` |
| `addCardFromSelection/1` | 3065 | MainActivity | `rangeEndChapter`, `rangeEndVerse`, `rangeStartChapter`, `rangeStartVerse`, `selectedBook` |
| `addBibleCard/6` | 3069 | MainActivity | `bible` |
| `clearSavedCardSession/1` | 3079 | FlashcardSessionController |  |
| `cardIds/1` | 3082 | FlashcardSessionController |  |
| `eligibleSessionCards/0` | 3085 | FlashcardSessionController | `store` |
| `startCardSession/0` | 3094 | FlashcardSessionController | `screen` |
| `saveCardSession/0` | 3113 | FlashcardSessionController |  |
| `nextFlashcard/0` | 3122 | FlashcardSessionController | `screen` |
| `renderCardSession/0` | 3217 | MainActivity | `cardFromUnified`, `cardIndex`, `cardRevealed`, `cardSequence`, `cardShowReference`, `selectedDeck` |
| `renderHome/0` | 3289 | MainActivity | `bible` |
| `createHomeCollection/0` | 3325 | MainActivity |  |
| `collectionTile/1` | 3333 | MainActivity |  |
| `renderHomeCollections/0` | 3356 | MainActivity | `selectedGroup` |
| `collectionCountNoun/1` | 3418 | MainActivity |  |
| `animateHomeCollections/3` | 3424 | MainActivity |  |
| `stat/3` | 3455 | MainActivity |  |
| `bibleNotReady/0` | 3459 | MainActivity | `bible`, `bibleError` |
| `bookGroupName/1` | 3465 | MainActivity |  |
| `renderBooks/0` | 3479 | MainActivity | `bibleSearchActive`, `passageSelection`, `pickerHasAnchor`, `readerVerseNumber`, `selectedBook`, `selectedChapter`, `selectingPassage` |
| `lastReadChapter/1` | 3531 | BibleReaderController |  |
| `rememberChapter/2` | 3535 | BibleReaderController |  |
| `openReaderVerseGrid/1` | 3539 | BibleReaderController | `savedScrollY`, `screen` |
| `renderChapters/0` | 3545 | MainActivity | `bible`, `passageSelection`, `pickerVerseNumber`, `selectedBook`, `selectedChapter` |
| `clearPassageSelection/0` | 3576 | BibleReaderController |  |
| `selectPassageVerse/2` | 3579 | BibleReaderController | `currentScroller` |
| `inPassageRange/2` | 3590 | BibleReaderController |  |
| `passageSummary/0` | 3596 | BibleReaderController |  |
| `renderChapter/0` | 3602 | MainActivity | `bible`, `chapterFromVerseGrid`, `focusVerse`, `selectedBook`, `selectedChapter` |
| `spinnerAdapter/1` | 3653 | MainActivity |  |
| `chapterPicker/1` | 3669 | MainActivity | `bible`, `selectedBook` |
| `updateVersePicker/3` | 3676 | MainActivity | `bible`, `selectedBook` |
| `bindChapterVerse/2` | 3682 | MainActivity |  |
| `showChapterJump/0` | 3691 | MainActivity | `bible`, `selectedBook`, `selectedChapter` |
| `renderInlineRangeBuilder/0` | 3756 | MainActivity |  |
| `renderInlineRangeBuilder/1` | 3757 | MainActivity | `pickerBackScreen`, `rangeEndChapter`, `rangeEndVerse`, `rangeStartChapter`, `rangeStartVerse`, `selectedBook`, `selectedChapter`, `selectedDeck` |
| `bindChapterVerseAndUpdate/3` | 3862 | MainActivity |  |
| `previewPassage/0` | 3872 | MainActivity | `bible`, `rangeEndChapter`, `rangeEndVerse`, `rangeStartChapter`, `rangeStartVerse`, `selectedBook`, `selectingPassage` |
| `styledDialog/1` | 3908 | MainActivity |  |
| `chooseGroup/1` | 3921 | MainActivity |  |
| `newGroup/1` | 3932 | MainActivity | `openUnifiedId` |
| `showStudyGroupActions/2` | 3939 | MainActivity | `customTargetGroupId`, `selectedGroup` |
| `confirmDeleteStudyGroup/1` | 3951 | MainActivity |  |
| `renderEntries/0` | 3961 | MainActivity | `selectedGroup` |
| `renameGroup/1` | 3992 | MainActivity |  |
| `clearInsertionContext/0` | 4002 | CollectionController |  |
| `removedInsertionCandidate/2` | 4006 | CollectionController |  |
| `repositionStudyAfter/3` | 4017 | CollectionController | `store` |
| `repositionCardAfter/3` | 4023 | CollectionController | `store` |
| `renderStudyReorder/0` | 4030 | MainActivity | `openUnifiedId`, `selectedGroup` |
| `renderCardReorder/0` | 4041 | MainActivity | `selectedDeck` |
| `renderReorderRow/4` | 4048 | MainActivity | `selectedDeck`, `selectedGroup` |
| `parseBibleLocation/1` | 4077 | MainActivity |  |
| `editBibleRange/7` | 4084 | MainActivity | `bible` |
| `editStudyEntry/1` | 4126 | MainActivity | `bible` |
| `editCardEntry/1` | 4163 | MainActivity | `bible`, `selectedDeck` |
| `showEntryActions/2` | 4187 | MainActivity | `insertAfterEntryId`, `studyInsertionTrail` |
| `showCardActions/2` | 4206 | MainActivity | `cardInsertionTrail`, `insertAfterCardId`, `selectedDeck` |
| `manageEntry/1` | 4226 | MainActivity |  |
| `entryCard/1` | 4244 | MainActivity |  |
| `date/1` | 4263 | MainActivity |  |
| `renderCustom/0` | 4264 | MainActivity | `customTargetGroupId` |
| `startReviewSession/0` | 4293 | MainActivity | `draft`, `lessonWriteHadFeedback`, `reviewCollectionGroup`, `reviewPointer`, `reviewQueue` |
| `renderDue/0` | 4356 | MainActivity |  |
| `openReview/1` | 4363 | MainActivity | `checked`, `currentReview`, `draft`, `forcedStep`, `groupMode`, `lastAccuracy`, `lessonPracticeAgain`, `lessonWriteHadFeedback`, `mask`, `passed`, `reviewMode`, `selectedEntry`, `stepCursor`, `studyStage`, `testAssisted`, `trainingMode`, `weakTransitionFrom`, `weakVerse` |
| `advanceReviewSession/0` | 4376 | MainActivity | `currentReview`, `forcedStep`, `reviewMode`, `reviewPointer`, `reviewQueue` |
| `rememberRecent/1` | 4383 | MainActivity |  |
| `recentIds/0` | 4393 | MainActivity |  |
| `saveTrainingSession/0` | 4402 | StudySessionController | `SESSION_PREFS` |
| `restoreTrainingSession/1` | 4430 | StudySessionController |  |
| `restoreTrainingSession/2` | 4431 | StudySessionController | `SESSION_PREFS` |
| `resetWordTiles/0` | 4464 | StudySessionController |  |
| `openTraining/1` | 4468 | MainActivity | `checked`, `collectionQueueNewStep`, `currentReview`, `draft`, `executingCollectionQueue`, `forcedStep`, `groupMode`, `lessonPracticeAgain`, `lessonWriteHadFeedback`, `passed`, `returnVerseCursor`, `reviewMode`, `selectedEntry`, `stepCursor`, `studyStage`, `testAssisted`, `trainingMode` |
| `selectRecommendation/1` | 4513 | StudySessionController | `store` |
| `followRecommendation/1` | 4521 | MainActivity |  |
| `showAnyVerse/1` | 4524 | MainActivity | `stepCursor`, `studyStage` |
| `isSermon/1` | 4539 | StudySessionController |  |
| `studySteps/1` | 4543 | StudySessionController |  |
| `resetTrainingStep/1` | 4549 | StudySessionController | `store` |
| `moveStep/2` | 4575 | StudySessionController | `store` |
| `texts/1` | 4586 | StudySessionController |  |
| `chooseTrainingMode/2` | 4591 | MainActivity | `blurSeed`, `groupMode`, `mask`, `reviewMode`, `selectedEntry`, `stepCursor`, `studyStage`, `testAssisted`, `trainingMode` |
| `addTrainingModeButtons/1` | 4618 | MainActivity | `activeMastered`, `trainingMode` |
| `updateModeBar/0` | 4633 | MainActivity | `mask`, `trainingMode` |
| `updateBlurText/0` | 4645 | MainActivity | `activeEntry`, `activeStep`, `blurSeed`, `mask`, `trainingMode` |
| `installTemporaryReveal/1` | 4682 | MainActivity |  |
| `updateActiveTrainingUI/0` | 4720 | MainActivity | `activeEntry`, `activeMastered`, `activeStep`, `draft`, `groupMode`, `mask`, `reviewMode`, `selectedEntry`, `stepCursor`, `studyStage`, `trainingMode` |
| `completeIntro/1` | 4774 | MainActivity | `groupMode`, `mask`, `reviewMode`, `selectedEntry`, `stepCursor`, `studyStage`, `trainingMode` |
| `renderStepNavigator/3` | 4783 | MainActivity | `groupMode`, `reviewMode`, `stepCursor`, `studyStage` |
| `wordTile/5` | 4825 | MainActivity |  |
| `showWordOrder/2` | 4832 | MainActivity | `groupMode`, `orderBankOrder`, `orderChunk`, `orderCompleted`, `orderMistakes`, `orderPosition`, `orderWrongTile`, `reviewMode`, `selectedEntry`, `stepCursor`, `studyStage`, `usedWordTiles` |
| `renderTraining/0` | 4884 | MainActivity | `activeEntry`, `activeMastered`, `activeStep`, `checked`, `draft`, `forcedStep`, `groupMode`, `lastAccuracy`, `lastExpected`, `passed`, `returnVerseCursor`, `reviewMode`, `selectedEntry`, `stepCursor`, `studyStage`, `testAssisted`, `trainingMode` |
| `checkAnswer/3` | 4969 | StudySessionController | `store` |
| `typedFeedback/2` | 4996 | MainActivity |  |
| `bookForEntry/1` | 5007 | StudySessionController |  |
| `nextGroup/1` | 5012 | StudySessionController | `store` |
| `pendingAfterCurrent/2` | 5019 | StudySessionController | `store` |
| `openGroup/1` | 5027 | MainActivity | `checked`, `currentReview`, `draft`, `forcedStep`, `groupBuildDone`, `groupIntroDone`, `groupMode`, `lessonChunk`, `lessonPracticeAgain`, `lessonVariant`, `lessonWriteHadFeedback`, `lessonWriteVariant`, `mask`, `passed`, `practiceWeakFragment`, `returnVerseCursor`, `reviewMode`, `selectedEntry`, `stepCursor`, `studyStage`, `testAssisted`, `trainingMode`, `weakFragmentText` |
| `renderFullscreenLesson/0` | 5041 | MainActivity | `activeEntry`, `activeMastered`, `activeStep`, `bible`, `checked`, `groupIntroDone`, `groupMode`, `lessonChunk`, `lessonPicked`, `lessonVariant`, `lessonWriteVariant`, `mask`, `openUnifiedId`, `passed`, `selectedEntry`, `stepCursor`, `studyStage` |
| `lessonFullReference/1` | 5131 | MainActivity | `activeStep` |
| `renderCompletion/1` | 5144 | MainActivity | `groupMode` |
| `advanceAfterSuccess/1` | 5230 | MainActivity | `activeStep`, `cardStudyCursor`, `collectionQueueCursor`, `executingCollectionQueue`, `forcedStep`, `groupMode`, `returnVerseCursor`, `reviewMode`, `selectedEntry`, `stepCursor`, `studyStage` |
| `advanceStudyCollectionAfterGroups/1` | 5256 | MainActivity |  |
| `lessonHotbarModel/2` | 5262 | MainActivity | `activeStep`, `executingCollectionQueue`, `groupMode`, `lessonChunk`, `lessonPicked`, `lessonPracticeAgain`, `lessonVariant`, `lessonWriteVariant`, `mask`, `reviewMode`, `studyStage` |
| `renderLessonHotbar/3` | 5286 | MainActivity |  |
| `paintLessonHotbarCell/4` | 5426 | MainActivity |  |
| `refreshLessonPhaseProgress/2` | 5452 | MainActivity |  |
| `lessonPhase/1` | 5463 | StudySessionController | `executingCollectionQueue`, `store` |
| `lessonPhaseHeader/2` | 5472 | MainActivity |  |
| `addLessonReference/1` | 5482 | MainActivity |  |
| `renderLessonIntro/0` | 5491 | MainActivity | `activeEntry`, `mask`, `trainingMode` |
| `resetLessonTiles/0` | 5514 | StudySessionController |  |
| `buildPromptText/3` | 5532 | MainActivity |  |
| `lessonMaxTiles/0` | 5543 | StudySessionController |  |
| `lessonChunks/1` | 5549 | StudySessionController |  |
| `updateLessonBuildView/2` | 5552 | MainActivity | `lessonPicked`, `lessonUsedTiles`, `lessonWrongTile` |
| `lessonMissingPositions/2` | 5572 | StudySessionController |  |
| `renderLessonBuild/1` | 5594 | MainActivity | `activeStep`, `lessonChunk`, `lessonPicked`, `lessonSeed`, `lessonUsedTiles`, `lessonVariant`, `lessonWrongTile` |
| `advanceLessonBuild/1` | 5641 | StudySessionController | `store` |
| `writingVariants/1` | 5660 | StudySessionController |  |
| `writingExpected/2` | 5666 | StudySessionController |  |
| `renderLessonWriting/0` | 5677 | MainActivity | `activeEntry`, `draft`, `lastAccuracy`, `lessonWriteVariant`, `practiceWeakFragment` |
| `renderInlineGapInputs/1` | 5709 | MainActivity | `activeEntry`, `activeStep`, `draft`, `inlineDrafts`, `inlineExpected`, `lastAccuracy` |
| `weakPhrase/2` | 5805 | StudySessionController |  |
| `showModernErrors/3` | 5819 | MainActivity |  |
| `validateLessonWriting/1` | 5840 | MainActivity | `activeStep`, `checked`, `currentReview`, `draft`, `groupMode`, `inlineDrafts`, `inlineExpected`, `lastAccuracy`, `lastExpected`, `lessonPracticeAgain`, `lessonSeed`, `lessonWriteHadFeedback`, `lessonWriteVariant`, `passed`, `practiceWeakFragment`, `reviewMode`, `studyStage`, `weakFragmentText`, `weakTransitionFrom`, `weakVerse` |
| `ensureActiveUnified/0` | 5955 | CollectionController | `UNIFIED_PREFS`, `store` |
| `activeUnified/0` | 5961 | CollectionController | `store` |
| `activateUnified/1` | 5964 | CollectionController | `UNIFIED_PREFS`, `store` |
| `newUnifiedCollection/1` | 5970 | MainActivity | `openUnifiedId` |
| `showUnifiedSelector/0` | 5986 | MainActivity | `activeUnifiedId` |
| `renderUnifiedHomePanel/0` | 6000 | MainActivity |  |
| `unifiedModeRow/4` | 6047 | MainActivity |  |
| `startActiveStudy/0` | 6063 | MainActivity | `bible`, `openUnifiedId`, `selectedGroup` |
| `startActiveCards/0` | 6070 | MainActivity | `bible`, `cardFromUnified`, `selectedDeck` |
| `startActiveReview/1` | 6079 | MainActivity | `reviewCollectionGroup` |
| `renderUnifiedCollections/0` | 6085 | MainActivity |  |
| `renderUnifiedCollectionRows/2` | 6121 | MainActivity | `activeUnifiedId`, `openUnifiedId` |
| `unifiedSettingRow/5` | 6145 | MainActivity |  |
| `showUnifiedCollectionSettings/1` | 6162 | MainActivity | `openUnifiedId`, `selectedGroup` |
| `clearUnified/1` | 6178 | MainActivity | `runCollectionId`, `runLines` |
| `renameUnified/1` | 6197 | MainActivity |  |
| `deleteUnified/1` | 6207 | MainActivity |  |
| `renderUnifiedCollectionDetail/0` | 6216 | MainActivity | `openUnifiedId`, `selectedDeck`, `selectedGroup` |
| `addUnifiedCollectionFooter/0` | 6238 | MainActivity | `customTargetGroupId`, `openUnifiedId` |
| `runKey/1` | 6263 | StudySessionController |  |
| `runVerseKey/2` | 6264 | StudySessionController |  |
| `buildRunLines/1` | 6274 | StudySessionController | `store` |
| `startActiveRun/0` | 6295 | MainActivity | `runCollectionId`, `runLines` |
| `resetRun/0` | 6311 | StudySessionController |  |
| `restoreRunSession/0` | 6315 | StudySessionController | `UNIFIED_PREFS` |
| `saveRunSession/0` | 6328 | StudySessionController | `UNIFIED_PREFS` |
| `finishRunLine/0` | 6337 | MainActivity | `runChecked`, `runCollectionId`, `runCorrect`, `runCursor`, `runDraft`, `runLines`, `runMistakes`, `runPassed` |
| `saveCompletedRun/0` | 6350 | StudySessionController | `UNIFIED_PREFS` |
| `renderWritingRun/0` | 6364 | MainActivity | `runChecked`, `runCursor`, `runDraft`, `runLines`, `runPassed` |
| `renderRunSummary/0` | 6423 | MainActivity | `runCorrect`, `runLines`, `runMistakes` |
| `exportFile/0` | 6436 | MainActivity |  |
| `importFile/0` | 6442 | MainActivity |  |
| `onActivityResult/3` | 6447 | MainActivity |  |
| `onDestroy/0` | 6475 | MainActivity | `bibleSearchExecutor`, `bibleSearchHandler` |

## Фактический результат этапа 2

После извлечения и восстановления Activity: 5 347 строк, 200 методов и 77 полей
верхнего уровня. Методы вложенных View-классов не включены ни в исходный, ни в итоговый счёт.
Полный результат и команды проверок: [отчёт этапа 2](../ARCHITECTURE_REFACTOR_STAGE_2.md).

| Владелец | Фактическая ответственность | Внешние зависимости |
| --- | --- | --- |
| MainActivity | Java View, системный lifecycle/Back, активный Screen, кэш вкладок и анимации, диалоги, применение прокрутки | Четыре контроллера; LocalStore для экранных запросов и оставшихся старых обработчиков; Android API |
| BibleReaderController | Чтение/сетки, выбор диапазона, состояние reader/picker, debounce/generation/cancellation поиска, жизненный цикл загрузки | Bible/BibleSearchLogic; application Context; callback-порты UI и синхронизации; SharedPreferences последней главы |
| FlashcardSessionController | Порядок/курсор/направление/раскрытие карточек, checkpoint; отдельная очередь изучения карточек | LocalStore и SharedPreferences через Source |
| StudySessionController | Этап/упражнение/ввод/feedback/маски/плитки; открытие и продолжение уроков/связок/повторений; проверка ответа; writing run; checkpoints | Существующие pure-Java алгоритмы; один LocalStore; порты UI/навигации, checkpoint-предпочтений и очередей |
| CollectionController | Активная коллекция, unified CRUD/очистка, membership/toggle/отрывки, порядок вставки, очередь независимых записей, sync | Тот же LocalStore; preference-порты; read-only входы выбора Библии; callbacks владельцам учебных сессий |

Контроллеры не импортируют MainActivity или View и не обращаются друг к другу
через конкретные типы. Activity связывает их портами: например, очистка коллекции
передаёт очистку writing-run владельцу Study, а запуск очереди — завершение
предыдущей card-study владельцу Flashcard. Дублирующих копий извлечённого состояния
и production-reflection нет. Общее соединение/схема SQLite остаётся у LocalStore.

Снимки Bundle принадлежат контроллерам; SessionSnapshot содержит только типизированные
кодеки списков, шагов и DueItem. Screen — общий словарь навигации, PassageText —
общая операция соединения текстов без зависимости одного контроллера от другого.
