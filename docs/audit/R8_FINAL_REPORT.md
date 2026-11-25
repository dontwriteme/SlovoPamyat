# Итог архитектурного этапа R8

Исходная ревизия `b39b91a0156ea882c88733ab32b822fea0e45c1a` из карты R8.0. Итоговый код `f08c557` в `refactor/r8-9-navigation-lifecycle`. Одинаковая методика для обеих точек описана в [карте](MAIN_ACTIVITY_MAP.md). Строки физические, методы и поля посчитаны по AST верхнего уровня, зависимости по `jdeps` с объединением вложенных классов и исключением Android/JDK. Производственный объём учитывает все `.java` под `app/src/main/java`.

| Метрика | База R8.0 | Итог R8.9 |
| --- | ---: | ---: |
| Строки MainActivity | 7 746 | 3 217 |
| Методы MainActivity | 337 | 211 |
| Переназначаемые поля MainActivity | 155 | 44 |
| Постоянные рабочие ссылки MainActivity | 34 | 39 |
| Исходящие зависимости MainActivity | 43 | 82 |
| Рёбра графа проекта | 115 | 341 |
| Циклические компоненты | 3 | 2 |
| Производственные Java-файлы | 49 | 103 |
| Производственные Java-строки | 14 312 | 16 122 |
| JVM-тесты | 226/226 | 252/252 |
| Lint | 0 ошибок, 38 предупреждений | 0 ошибок, 43 предупреждения |

MainActivity стала короче на 4 529 строк. Производственный объём вырос на 1 810 строк вместе с явными типами состояния, границами UI и контрактами доставки результата. Рост исходящих зависимостей отражает композицию компонентов в Android-точке входа.

## Владельцы состояния

- `NavigationState` хранит текущий и последний отрисованный маршруты и прокрутку. `Screen` определяет статические Back-маршруты. `TabCache` владеет готовыми деревьями и поколением прогрева, `BottomNavigationView` и `TabTransition` отвечают за индикатор и смену страниц. `NavigationGesture` держит только текущий жест. Действия Back с сохранением черновика или сменой контекста остаются в Activity.
- Контекст вставки хранит `CollectionInsertion`, очередь уроков коллекции и её прежние ключи обслуживает `CollectionStudyQueue`. `CollectionPassages` и `CollectionEntryEditor` используют прежние операции SQLite. `CollectionDetailLoader` проверяет поколение и актуальность View при чтении и выводе порциями. `CollectionIo` владеет сериализованной очередью и вторым helper. Callback завершённой записи может очистить настройки после ухода Activity до уведомления о закрытии helper.
- Упражнение принадлежит `LessonSessionState` и прежним моделям сборки и письма. `LessonRestoration` сверяет идентичность материала, контрольную точку, маршрут и сохранённую геометрию до применения состояния. `LessonPlans` задаёт открытие и начальные параметры, `LessonWritingSubmission` формирует снимок ответа и вызывает прежнюю транзакцию после проверки актуальности. Activity применяет состояния только после возврата транзакции. `ReviewSession` отдельно владеет очередью повторений.
- `ActivityDataLifetime` открывает и закрывает основной helper, проверяет поколение Bundle и запускает сброс по сериализованной очереди. `BibleStartup` отделяет время жизни загрузки корпуса от доставки UI. `BackupCoordinator.Owner` передаёт только ожидание и завершение копирования. `MainActivity ↔ BackupCoordinator` исчез из графа.
- Два прежних цикла сверены отдельно. `LocalStore`, `CollectionProgressSnapshot` и `ReviewSchedule` разделяют типы и чтение внутри SQLite и планирования. `DeveloperModeController` вызывает предварительный просмотр `SequenceRecallController`, который проверяет режим разработчика. Их выделение требует отдельных задач слоя данных и DEV.

## Размеры компонентов, выделенных за R8

| Область | Java-классы и строки |
| --- | --- |
| Общий UI и экраны | `ScreenUi` 209, `ScreenMenus` 87, `ModeInfoPopover` 202, `NavigationGlyph` 125, `MenuGlyph` 70, `ScreenArtwork` 64, `HomePage` 539, `SettingsView` 79 |
| Проверка знаний и карточки | `KnowledgeCheckMaterial` 30, `KnowledgeCheckSession` 177, `KnowledgeCheckView` 102, `FlashcardSession` 97, `FlashcardSessionView` 189 |
| Библия, выбор и поиск | `BibleSearchSession` 163, `BibleSearchView` 340, `BibleReader` 216, `PassagePicker` 336 |
| Коллекции | `ActiveCollection` 36, `CollectionDialogs` 109, `CollectionViews` 197, `CollectionInsertion` 43, `CollectionEntryEditor` 147, `CollectionPassages` 54, `CollectionStudyQueue` 114, `CollectionIo` 46, `CollectionDetailLoader` 82 |
| Повторения | `ReviewSession` 152, `ReviewSessionPreferences` 37 |
| Урок | `LessonReadingView` 179, `LessonAssemblyView` 243, `LessonAssemblyLayout` 137, `LessonWordWrap` 54, `LessonWritingView` 155, `LessonAnswerReviewView` 108, `LessonWritingWorker` 73, `LessonScreenView` 89, `LessonProgressView` 110, `LessonCompletionView` 113, `LessonSessionState` 86, `LessonSessionPreferences` 49, `LessonCheckpoint` 47, `LessonLaunch` 38, `LessonPlans` 67, `LessonRestoration` 89, `LessonWritingSubmission` 59, `VerseText` 16 |
| Навигация и ресурсы | `Screen` 48, `NavigationState` 18, `NavigationGesture` 39, `TabCache` 68, `BottomNavigationView` 149, `TabTransition` 149, `ActivityDataLifetime` 75, `BibleStartup` 34 |

## Проверки и ограничения

- Финальная офлайн-регрессия прошла в 42 JVM-наборах без пропусков. Проверены прежние 249 сценариев, включая ошибки SQLite, защиту от повторного зачёта и восстановление после пересоздания, плюс три новых теста навигации. Debug APK `org.biblememory.app` собран, подпись v2 проверена.
- Три нативные пробы API 28 завершились с `INSTRUMENTATION_CODE: -1`. `R51LessonCheck` открыл все пять режимов с Главной и проверил поворот, сборку, черновики, связки, экран завершения, обычное повторение, `VERSE_RUN`, удаление активного материала и импорт. `R0PickerCheck` проверил вкладки, Back, позиции прокрутки, поиск, переход к стиху, массовое добавление и карточки. `R44WritingCheck` проверил импорт при занятом worker и отсутствие записи отменённой попытки в восстановленную базу.

- Встроенный корпус, поисковый индекс, ресурсные файлы, схема и транзакционный код `LocalStore`, формат копий, алгоритмы оценки и повторений сверены с базой. Корпусные прогоны повторно не запускались. SHA-256 `synodal.json` — `553df7df63cff838fabac65b480aaf1a07ad59d536c14c140c93e1b29d801bb4`, `synodal.sidx` — `2e80ca4f77b36a14f509628d66df185454e6298e89eda5ceb3309c7de65e7259`. Оба совпадают с R8.0.
- Пять предупреждений Lint, появившихся после R8.0, рассмотрены отдельно. Четыре `ViewConstructor` относятся к Java-виджетам с обязательными параметрами: `MenuGlyph`, `NavigationGlyph`, `LessonWordWrap`, `LessonSegmentedBar`. Ещё одно `ClickableViewAccessibility` относится к снятию обработчика удержания в `LessonReadingView`. Подавлений нет. Действие удержания и его завершение покрыты тестом.
- Activity сохраняет Android-маршрутизацию, построение дерева активного экрана и связку структурных переходов между уроком, карточками и повторениями. Сложные команды SQLite и DEV остаются в пределах прежних контрактов. Физические устройства, попиксельное сравнение и профилирование не проводились.
