# Prime Mover

Промышленный интерфейс управления когнитивным ресурсом. Не трекер привычек, а
бескомпромиссный шлюз между намерением и действием для оператора-перводвигателя,
работающего в одиночку над генеративным ИИ-производством.

Архитектура — на пересечении объективистской этики (`A = A`), прикладной
нейробиологии и операционного менеджмента в парадигме Lean Six Sigma. Геймификация
запрещена; единственная награда за выполненный квант труда — двухсекундная пауза
с монохромной мантрой.

## Operator Manual

### Пять направлений

| Код | Направление    | Категория      |
| --- | -------------- | -------------- |
| 01  | ГЕНЕРАЦИИ      | Ядро           |
| 02  | ИСПАНСКИЙ      | Инфраструктура |
| 03  | СПОРТ          | Инфраструктура |
| 04  | НАСМОТРЕННОСТЬ | Инфраструктура |
| 05  | ЧТЕНИЕ         | Инфраструктура |

Все пять направлений запускаются в любой момент дня — оператор владеет
последовательностью. Ядро структурно выделено: только его «АНАЛИЗ ГЕНЕРАЦИИ»
является источником классификации дня (Извлечён / Частичен / Упущен) для
хронометра, и только Ядро имеет персональный модальный пульт с авторизацией.

### Режим дня (СТАНДАРТ / МИНИМУМ)

Один тумблер на день, выбирается в шапке пульта или во вкладке **РЕЖИМ**.
После первого тапа фиксируется на сегодня и блокируется (`AlreadyApproved`);
сброс — естественный, на следующий день. Хронометр читает это поле для
классификации суток:

- `Standard` → Extracted (сутки идут «вверх по графику»).
- `Mvd` → Partial (сутки извлечены частично — минимально жизнеспособный день).

`МИНИМУМ` — рассчитанный инженерный предохранитель, не психологическая
индульгенция. Это явное обязательство по минимальной планке, а не отказ от
работы. MVD выполнено = операционная победа, а не провал.

Что считается «СТАНДАРТом» и «МИНИМУМом» для каждой привычки — оператор
прописывает один раз во вкладке **РЕЖИМ → КРИТЕРИИ**. Кнопка «ЗАПОЛНИТЬ
ЗНАЧЕНИЯМИ ПО УМОЛЧАНИЮ» подсаживает seed-тексты (Спорт = 45 мин, Генерации
≥ 60 мин с фиксацией результата, и т.д.). Существующие непустые значения не
перезатираются.

### Концентрация (Flash-сессии)

`[ ВСПЫШКА ]` на каждой карточке запускает фокус-сессию по направлению.
Таймер моноширинными цифрами `HH:MM:SS`. После 5-минутной отметки цвет
переключается на `Approve` (зелёный) без звука — визуальное подтверждение, что
амигдала-блок пройден. Дальше таймер бесшовно считает вверх.

Все Flash-сессии персистируются в `focus_session`; восстанавливаются после
убийства процесса по `started_at`. Одновременно может быть активна только одна
сессия — гарантируется на уровне SQLite через partial unique index
`uniq_focus_active ON focus_session(ended_at) WHERE ended_at IS NULL`.

Во время активной сессии висит sticky foreground-нотификация
(`FlashForegroundService`) с таймером. Свайп её не убирает — `setDeleteIntent`
переподнимает её через `FocusNotificationDismissReceiver`, пока сессия
открыта.

Опционально (Настройки → Помодоро) — авто-остановка через N минут.

### Quick Settings tile

`FlashTileService` выставляет на шторку быстрого доступа Android плитку
«ВСПЫШКА». Тап стартует/останавливает фокус-сессию по Ядру без открытия
приложения.

### Авторизация и АНАЛИЗ ГЕНЕРАЦИИ

После завершения сессии по Ядру открывается модалка `АНАЛИЗ ГЕНЕРАЦИИ`. Поля:

> Присутствует ли в этом результате мой инженерный расчёт, или это слепой автоматизм?

Кнопка `[ ПОДТВЕРДИТЬ И СОХРАНИТЬ ]` активируется только при непустом промпте
**и** включённом чекбоксе `ПОДТВЕРЖДАЮ ИНЖЕНЕРНЫЙ РАСЧЁТ`. Это сверка с
реальностью — разрыв петли бессознательного паттерна (CBT-интервенция).

### Earned Pride

После сохранения АНАЛИЗА интерфейс блокируется на ~2.5 секунды. На фоне
`Carbon` отображается монохромная мантра:

> ФАКТ УСТАНОВЛЕН.
> РЕСУРС КОНВЕРТИРОВАН В КАПИТАЛ.
> A = A

Никаких конфетти, звуков монет, анимаций. Это окно серотониновой интеграции —
заслуженная гордость от преодоления сопротивления, не дофамин от внешнего
подкрепления.

### Soft Core primacy (без Hostage Logic)

Инфра-модули 02–05 запускаются и фиксируются в любой момент — старая жёсткая
блокировка снята. Что Генерации — главное дело, сигнализируется пассивно:

- `CoreReminderBanner` под шапкой пульта, пока Ядро ещё `Idle`.
- На вкладке **РЕЖИМ** карточка ГЕНЕРАЦИИ выделена 2dp зелёной рамкой и
  бейджем `ГЛАВНОЕ ДЕЛО`.
- Опциональный midday-check push в 12:00 (Настройки → Полуденная сверка).

### Sabotage Protocol

Кнопка `САБОТАЖ` в шапке пульта. Три статических CBT-скрипта без эмоциональной
окраски:

- **ИМПУЛЬС ПОДМЕНЫ** — попытка открыть теорию вместо генератора.
- **РЕАКЦИЯ НА БРАК** — острый гнев на артефакты нейросети.
- **СОЦИАЛЬНОЕ СРАВНЕНИЕ** — стыд при просмотре высококлассных чужих работ.

Каждое открытие пишется в `evasion_log` (UserPreferences), агрегаты виды в
Sunday Audit: «На неделе диагностировано N раз».

### Evening Close

Запускается из футера пульта или через notification в 21:00 (`EveningCloseScheduler`,
один AlarmManager-таргет). Состоит из:

1. **Три авто-индикатора** — ПРОДУКТИВНОСТЬ / ГОРДОСТЬ / ЦЕЛОСТНОСТЬ
   (`OK`/`FAIL`, вычисляются из daily-session).
2. **Четыре virtue-селектора** (Разумность / Независимость / Честность /
   Справедливость) — трёхпозиционные `[ − ][ 0 ][ + ]`. Подсказки auto-suggest
   подсвечиваются (но не выставляют) на основе фактов дня.
3. **Условный Defect/Waste dropdown** — появляется только если Ядро на момент
   закрытия всё ещё `Idle`. Без выбора `[ ЗАФИКСИРОВАТЬ ]` `Mute`.
4. **Журнал** — свободный текст ≤ JOURNAL_MAX_CHARS.
5. **Follow-up прошлого bottleneck** — `ДА / ЧАСТИЧНО / НЕТ` к ограничению,
   которое было поставлено в воскресенье.

Закрытие смены идемпотентно: повторная попытка отвергается с
`EveningClosedAlready`.

### Sunday PDCA Audit

Доступ к истории жёстко заблокирован в будние дни — DMN-изоляция (Default Mode
Network suppression). В воскресенье start-route MainActivity переключается с
OperatorPulpit на SundayAudit. Экран показывает:

- Метрики за последние 7 дней: часы Ядра, дни с Approved, Defect/Waste counts,
  MVD-частота, по-направлениям Done / NotDone, суммы virtues, число открытий
  Sabotage.
- Дельты к предыдущей неделе.
- Follow-up по прошлому ограничению.
- Dropdown `Bottleneck` с категориями (PromptDiscipline, ScheduleHygiene,
  SocialComparison, PerfectionismFreeze, FailureToAuthorise, InfraOverdrive,
  MvdOveruse, JournalSkipped, и т.д.).
- После фиксации bottleneck — кнопка `[ ОТКРЫТЬ ИСТОРИЮ ]` ведёт на monthly
  habit grid.

При попытке открыть `SundayAudit` или `HistoryGrid` deep-link'ом в будний день
показывается `WeekdayLockScreen`.

### Хронометр

Отдельный экран (`ChronometerScreen`) с LifeGrid — мозаикой недель жизни
оператора, окрашенных по статусу прожитой недели (Извлечена / Частично /
Упущена). Расчёт `ChronometerRepositoryImpl` опирается на `dayMode` (Standard
→ Extracted, Mvd → Partial, отсутствие или Failed → Wasted).

Утренний сигнал «ИГНИЦИЯ» (`IgnitionScheduler`) — опциональный push,
напоминающий вчерашний статус («Вчера: анализ генерации зафиксирован.» /
«Вчера: анализ не зафиксирован.»). Без давления «остаток N суток».

### Библиотека и цитаты

Вкладка **БИБЛИОТЕКА** — фонд цитат из whitepaper'а, индексированных по
четырём virtue-тегам. Цитата дня детерминирована по epoch-day. Принципы можно
запинить (`pinnedQuoteIds` в DataStore).

### Cloud sync (Google Drive AppData)

Авторизация — Google Sign-In (`play-services-auth 21.2`). Бэкап-файл живёт в
скрытой `appDataFolder` Drive — недоступен через UI Drive, виден только
приложению.

- `SyncCoordinator` дебаунсит локальные изменения и запускает
  `SyncUploadWorker` (WorkManager + Hilt) с экспоненциальным backoff.
- `DriveBackupClient` ходит в Drive REST v3 через OkHttp напрямую (без
  `google-api-client`).
- `RestoreBootstrapper` после первого Sign-In пуллит снапшот, мержит и
  фиксирует `initialRestoreDone` в preferences.
- Полный бэкап / восстановление + `wipe` руками доступны в **Настройках**.

## Навигация

Bottom navigation: 5 вкладок (`ПУЛЬТ / БИБЛИОТЕКА / ОЦЕНКА / ТРЕКЕР / РЕЖИМ`).
Модальные экраны (Settings, EveningClose, Sabotage, Authorisation, EarnedPride,
RestoringScreen, библиотека-детали) скрывают нав-бар через `shouldShowBottomNav`.

## Стек

- Android Native: `minSdk 26`, `targetSdk 34`, `compileSdk 35`, Kotlin 2.0.21
- Jetpack Compose (BOM 2024.12) + Material3 (тема `MachineTheme` на ISA-101
  палитре `IsaColors`)
- Hilt 2.52 (DI, + HiltWorker), Room 2.6.1 (KSP), Navigation Compose 2.8.5
- DataStore Preferences + kotlinx.serialization 1.7.3
- WorkManager + OkHttp 4 для Drive sync
- AlarmManager (evening close 21:00 / ignition утро / midday-check полдень)
- Foreground-сервис `FlashForegroundService` + QS-тайл `FlashTileService`

## Архитектура

```
app/src/main/java/com/avangard/app/
  AvangardApplication.kt    Hilt + schedulers ensureScheduled, sync init
  MainActivity.kt           Compose entry. Sunday-day branches start route,
                            onNewIntent handles notification deep-links.
  navigation/
    NavRoute.kt             Sealed routes (Pulpit, Library, SundayAudit,
                            HistoryGrid, Mode, Settings, Chronometer,
                            EveningClose, Sabotage, AuthorisationModal,
                            EarnedPride, SignIn, Restoring, VirtueQuotes,
                            QuoteDetail)
    AvangardNavHost.kt      NavHost + HistoryGate (Sunday-only)
    AvangardNavigationBar.kt 5-tab bottom-nav (Pulpit/Library/Audit/Tracker/Mode)
  ui/theme/                 IsaColors (ISA-101), Theme, Type
  core/
    common/                 Clock (Hilt), Time, DomainResult
    database/
      AppDatabase.kt        version=9, MIGRATION_1_2..8_9
      entity/               HabitLogEntity, DailySessionEntity (с core_mode,
                            journal_entry, bottleneck_followup),
                            FocusSessionEntity
      dao/                  HabitLogDao, DailySessionDao, FocusSessionDao
    data/
      RoomSessionRepository, RoomHabitRepository, RoomBackupRepository,
      ChronometerRepositoryImpl, QuoteRepository, UserPreferencesRepository
      auth/   AuthRepository (Google Sign-In)
      cloud/  DriveBackupClient, DriveModule, RemoteBackup, RestoreBootstrapper,
              SyncCoordinator, SyncUploadWorker
      di/     Hilt модули
    domain/
      model/                DailySession + CoreStatus + CoreMode + InfraStatus
                            (Done/NotDone) + DefectKind + Bottleneck + VirtueScores
                            + Habit + HabitStandard + HabitStandardDefaults
                            + SessionError + AccessPolicy + EvasionEvent +
                            QuoteEntry + BackupBundle
      repository/           SessionRepository, HabitRepository,
                            ChronometerRepository, etc.
      usecase/              StartFocus / EndFocus / ApproveCore / SetInfraStatus /
                            SetDayMode / SetJournal / SetBottleneck / CloseEvening /
                            ObserveDailySession / ObserveActiveFocus /
                            ObserveMonthHabits / SundayAudit /
                            IsHistoryUnlocked / ToggleHabit /
                            ExportBackup / ImportBackup
    ui/components/          PulpitPanel, HardButton, LabelStrip, StatusBadge,
                            FlashButton, CoreTimerDisplay, IndustrialToggle/Checkbox,
                            TickerFlow, PermissionBanner, CoreReminderBanner,
                            StatusBanner, Stepper, CollapsibleSection
  feature/
    pulpit/                 OperatorPulpit + VM + AuthorisationModal + EarnedPride
    library/                LibraryScreen + VirtueQuotesScreen + QuoteDetailScreen
    audit/                  SundayAuditScreen + VM
    habits/                 HabitTrackerScreen (monthly habit grid)
    mode/                   ModeScreen + ModeViewModel (day-mode picker + criteria)
    chronometer/            ChronometerScreen + LifeGrid
    closing/                EveningCloseScreen + VM
    sabotage/               SabotageProtocolScreen
    settings/               SettingsScreen (cold-start, evening close, ignition,
                            midday check, Pomodoro, cloud sync, backup, wipe)
    auth/                   SignInScreen + RestoringScreen
    locked/                 WeekdayLockScreen + HistoryGateViewModel
  sync/
    notifications/          SimpleNotificationPresenter, StatusNotifier,
                            FocusNotificationDismissReceiver
    scheduler/              EveningCloseScheduler/Receiver,
                            IgnitionScheduler/Receiver,
                            MiddayCheckScheduler/Receiver,
                            BootCompletedReceiver
    service/                FlashForegroundService + AndroidFocusServiceController
    tile/                   FlashTileService (QS tile)
```

## Стратегия данных

Один файл БД, **версия 9**. Миграции v1→v9 в `AppDatabase` companion;
покрыты `MigrationTest` (часть кейсов с partial unique indexes выключена под
Robolectric sqlite4java — он не парсит expression-индексы). Текущая схема:

- `habit_log` — `(date_epoch, habit_code)` PK; денормализованный view
  Infra-направлений, питает Sunday HistoryGrid.
- `daily_session` — `date_epoch` PK; одна строка на день. Колонки:
  `core_mode` (NULLable, `Standard` / `Mvd` — это и есть «Режим дня»),
  Core status (Idle / Approved + prompt + authorised_at / Failed + defect_kind),
  Infra статусы 02–05 (`Done / NotDone`), evening close + virtues, bottleneck
  + bottleneck_followup, journal_entry.
- `focus_session` — autoincrement id; событийный лог Flash-сессий + partial
  unique index `uniq_focus_active`.

Все мульти-DAO операции в `RoomSessionRepository` обёрнуты в
`androidx.room.withTransaction { ... }` — `setInfraStatus` пишет в
`daily_session` и `habit_log` атомарно; `approveCore` / `closeEvening` /
`setDayMode` читают свежее состояние внутри транзакции для устранения race
conditions.

Пользовательские настройки и побочные структуры (HabitStandards,
evasion log, pinned quote ids, флаги хронометра, sync timestamps) — в
DataStore Preferences (`UserPreferencesRepository`).

## Локальная сборка

1. Установите Android SDK 35 (Android Studio Iguana+ или
   `sdkmanager "platforms;android-35" "build-tools;35.0.0"`).
2. Создайте `local.properties` в корне:
   ```
   sdk.dir=/path/to/Android/sdk
   ```
3. Для Google Sign-In нужно зарегистрировать SHA-1 вашего `debug.keystore`
   в Google Cloud Console (OAuth client → Android). Без этого Sign-In
   падает с `code=10 (DEVELOPER_ERROR)`. Получить SHA-1:
   ```
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
       -storepass android -keypass android
   ```
4. Собрать и прогнать юнит-тесты:
   ```
   ./gradlew :app:assembleDebug
   ./gradlew :app:testDebugUnitTest
   ```
5. Установить на телефон:
   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   или через ▶ Run в Android Studio.

## Тесты

31 файл в `app/src/test/` (Robolectric + JUnit4 + mockk + Turbine). Покрыты:

- Все доменные use-case'ы (StartFocus / ApproveCore / CloseEvening /
  SundayAudit / SetJournal / IsHistoryUnlocked / ExportBackup / ImportBackup).
- ChronometerRepositoryImpl и MonthlyCells (классификация дней).
- Pulpit / EveningClose / SundayAudit / HabitTracker / Settings ViewModel'и.
- Cloud-стек: DriveBackupClient (через `okhttp.mockwebserver`),
  RestoreBootstrapper, SyncCoordinator, SyncUploadWorker.
- Room MigrationTest (часть кейсов `@Ignore` из-за ограничения
  Robolectric sqlite на partial unique indexes — гонять на устройстве через
  `connectedAndroidTest`).
