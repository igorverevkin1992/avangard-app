# Prime Mover

Промышленный интерфейс управления когнитивным ресурсом. Не трекер привычек, а
бескомпромиссный шлюз между намерением и действием для оператора-перводвигателя,
работающего в одиночку над генеративным ИИ-производством.

Архитектура — на пересечении объективистской этики (`A = A`), прикладной
нейробиологии и операционного менеджмента в парадигме Lean Six Sigma. Полная
концепция — в whitepaper'е `«Prime Mover»`. Геймификация запрещена; единственная
награда за выполненный квант труда — двухсекундная пауза с монохромной мантрой.

## Operator Manual

### Пять направлений (матрица)

| Код | Направление     | Категория      | Стандарт                                   | MVD                          |
| --- | --------------- | -------------- | ------------------------------------------ | ---------------------------- |
| 01  | ГЕНЕРАЦИИ       | Ядро           | 1–2 ч сфокусированной работы + сохранённый шот | 1 сохранённый промпт/шот    |
| 02  | ИСПАНСКИЙ       | Инфраструктура | 30–45 мин урок                              | 5 мин или 5 слов            |
| 03  | СПОРТ           | Инфраструктура | 45+ мин силовая                             | 5–10 мин гимнастики         |
| 04  | НАСМОТРЕННОСТЬ  | Инфраструктура | Разбор пайплайнов + конспект                | 1 пост/туториал             |
| 05  | ЧТЕНИЕ          | Инфраструктура | 15–20 страниц + тезисы                      | 1 абзац                     |

### Hostage Logic

Модули 02–05 (Инфраструктура) физически заблокированы на пульте, пока не зафиксирован
первый успешный квант по Ядру (01). Карточки 02–05 в это время отрисовываются с серой
заливкой `HostageGray`, кнопки `enabled=false`, и под лейб-полосой выводится баннер
`ЗАБЛОКИРОВАНО ДО ПЕРВОГО ЯДРА`. Дисциплина обновляется ежедневно: вчерашний Approved
не разблокирует сегодняшние Infra-модули.

### MVD-тумблер

Ручной переключатель `РЕЖИМ MVD: ВКЛ/ВЫКЛ` в шапке пульта. Объявляется на день; на
новый день сбрасывается. В режиме MVD пороги выполнения по матрице снижены до
минимально жизнеспособных — это рассчитанный инженерный предохранитель, не
психологическая индульгенция. Выполнение MVD классифицируется как 100% операционная
победа.

### Flash-таймер «Холодного запуска»

Большая прямоугольная кнопка `[ ВСПЫШКА ]` на каждой карточке. Запускает фокус-сессию
по направлению; таймер отображается моноширинными цифрами в формате HH:MM:SS. После
5-минутной отметки цвет переключается без звукового сигнала на `Approve` (зелёный) —
визуальное подтверждение что амигдала-блок пройден, дальше работаем по инерции. После
5 минут таймер бесшовно продолжает считать вверх. Все Flash-сессии персистируются в
`focus_session`; восстанавливаются после убийства процесса по `started_at`.

Одновременно может быть активна только одна Flash-сессия. Гарантируется на уровне
SQLite через partial unique index `uniq_focus_active`.

### Чекбокс осознанной авторизации

Перед сохранением шота по Ядру всплывает модальное окно с цитатой:

> Присутствует ли в этом результате мой инженерный расчёт, или это слепой автоматизм?

Кнопка `[ ПОДТВЕРДИТЬ И СОХРАНИТЬ ]` активируется только когда введён непустой
промпт **и** активирован чекбокс `ПОДТВЕРЖДАЮ ИНЖЕНЕРНЫЙ РАСЧЁТ`. Без обоих —
кнопка `Mute`. Это сверка с реальностью: разрыв петли бессознательного паттерна,
интервенция CBT.

### Earned Pride

После успешного сохранения шота интерфейс блокируется на 2.5 секунды. На фоне `Carbon`
отображается монохромная мантра:

> ФАКТ УСТАНОВЛЕН.
> РЕСУРС КОНВЕРТИРОВАН В КАПИТАЛ.
> A = A

Никаких конфетти, звуков монет, анимаций. Это окно серотониновой интеграции — заслуженная
гордость от преодоления сопротивления, не дофамин от внешнего подкрепления.

### Sabotage Protocol

Кнопка `САБОТАЖ` в шапке пульта. Открывает три статических CBT-скрипта без эмоциональной
окраски:

* **ИМПУЛЬС ПОДМЕНЫ** — попытка обойти Hostage Logic и открыть теорию вместо генератора.
* **РЕАКЦИЯ НА БРАК** — острый гнев на артефакты нейросети.
* **СОЦИАЛЬНОЕ СРАВНЕНИЕ** — стыд при просмотре высококлассных чужих работ.

Каждый скрипт — депрерсонализирующее заявление с принципом `A = A`.

### Evening Close (вечернее закрытие)

Запускается из футера пульта или через notification в 21:00. Состоит из:

1. **Три авто-индикатора**: ПРОДУКТИВНОСТЬ / ГОРДОСТЬ / ЦЕЛОСТНОСТЬ — `OK`/`FAIL`,
   вычисляются из daily-session.
2. **Четыре virtue-селектора** (Разумность / Независимость / Честность /
   Справедливость) — каждый трёхпозиционный `[ − ][ 0 ][ + ]`.
3. **Условный Defect/Waste dropdown** — появляется только если Core всё ещё `Idle` на
   момент закрытия. Без выбора — `[ ЗАФИКСИРОВАТЬ ]` `Mute`.

Закрытие смены идемпотентно: повторная попытка отвергается с `EveningClosedAlready`.

### Sunday PDCA Audit

Доступ к истории жёстко заблокирован в будние дни — это DMN-изоляция (Default Mode
Network suppression). В воскресенье start-route MainActivity переключается с
OperatorPulpit на SundayAudit. Экран показывает:

* Сухую табличку метрик за последние 7 дней: часы Ядра, дни с Approved,
  Defect/Waste counts, MVD-частота, по-направлениям Std/MVD/NotDone, суммы virtues.
* Dropdown `Bottleneck` с 7 категориями (PromptDiscipline, ScheduleHygiene,
  SocialComparison, PerfectionismFreeze, FailureToAuthorise, InfraOverdrive,
  MvdOveruse).
* После фиксации bottleneck — кнопка `[ ОТКРЫТЬ ИСТОРИЮ ]` ведёт на monthly habit
  grid (наследие из v2, теперь Sunday-only).

При попытке открыть `SundayAudit` или `HistoryGrid` deep-link'ом в будний день
показывается `WeekdayLockScreen` с текстом `ДОСТУП К ИСТОРИИ — ВОСКРЕСЕНЬЕ.`

## Стек

* Android Native (minSdk 26, targetSdk 34, compileSdk 35), Kotlin 2.0.21
* Jetpack Compose + Material3 (тема `MachineTheme` на ISA-101 палитре `IsaColors`)
* Hilt 2.52 (DI), Room 2.6.1 (БД, KSP), Navigation Compose 2.8
* AlarmManager (одна точка — 21:00 evening close reminder)
* `androidx.activity:activity-compose` для runtime permission requests

## Архитектура

```
app/src/main/java/com/avangard/app/
  AvangardApplication.kt    Hilt + EveningCloseScheduler.ensureScheduled
  MainActivity.kt           Compose entry. Sunday-day branches start route.
                            onNewIntent handles foreground notification deep-link.
  navigation/
    NavRoute.kt             Sealed routes (Pulpit, AuthorisationModal, EarnedPride,
                            Sabotage, EveningClose, SundayAudit, HistoryGrid, Settings)
    AvangardNavHost.kt      NavHost + HistoryGate composable for Sunday-gating
  ui/theme/
    Color.kt                IsaColors (ISA-101 industrial palette)
    Theme.kt                IsaColorScheme
    Type.kt                 Roboto Condensed + monospace stack
  core/
    common/                 Clock (Hilt-injected), Time.DAY_MILLIS, DomainResult
    database/
      AppDatabase.kt        version=5, MIGRATION_1_2..4_5
      entity/               HabitLogEntity, DailySessionEntity, FocusSessionEntity
      dao/                  HabitLogDao, DailySessionDao (abstract + @Transaction
                            ensureRow), FocusSessionDao
    data/
      RoomSessionRepository Session writes through database.withTransaction
      RoomHabitRepository   habit_log read/write
    domain/
      model/                DailySession + CoreStatus sealed, FocusSession,
                            VirtueScores, DefectKind, InfraStatus, Bottleneck,
                            SessionError sealed (InfraLocked, NotAuthorised,
                            AnotherFocusActive, EveningClosedAlready,
                            MissingDefectKind, PromptEmpty, HistoryLocked,
                            AlreadyApproved), AccessPolicy (Sunday gate),
                            Habit enum (01-05 codes)
      repository/           SessionRepository, HabitRepository
      usecase/              ObserveDailySession, ObserveActiveFocus,
                            StartFocus (guards Hostage + AlreadyApproved),
                            EndFocus, ApproveCore (guards AlreadyApproved),
                            SetInfraStatus, ToggleMvd, CloseEvening
                            (guards MissingDefectKind on Idle), SetBottleneck,
                            IsHistoryUnlocked, SundayAudit (reactive on
                            sessions + focus event log)
    ui/components/          PulpitPanel, HardButton, LabelStrip, StatusBadge,
                            FlashButton, CoreTimerDisplay, IndustrialToggle,
                            IndustrialCheckbox, TickerFlow (Clock-driven),
                            PermissionBanner (notifications + exact alarm)
  feature/
    pulpit/                 OperatorPulpit screen + ViewModel + AuthorisationModal
                            + EarnedPride + SessionErrorMessage
    sabotage/               SabotageProtocolScreen
    closing/                EveningClose screen + ViewModel
    audit/                  SundayAudit screen + ViewModel
    locked/                 WeekdayLockScreen + HistoryGateViewModel
    settings/               Settings stub (wipe-only, long-press on date strip)
    habits/                 HabitTracker screen (legacy from v2 — Sunday HistoryGrid)
  sync/
    notifications/          SimpleNotificationPresenter (one channel)
    scheduler/              EveningCloseScheduler (single 21:00 alarm),
                            EveningCloseReceiver (re-arms before presenting),
                            BootCompletedReceiver (re-arms after device reboot)
```

## Стратегия данных

Один файл БД, версия 5. Миграции v1→v5 в `AppDatabase` companion. Текущая схема:

* `habit_log` — `(date_epoch, habit_code)` PK; денормализованный view маркировки
  Infra-направлений, на нём держится Sunday HistoryGrid из v2.
* `daily_session` — `date_epoch` PK; одна строка на день, держит MVD-тумблер,
  Core status (Idle/Approved/Failed + prompt + authorised_at + defect_kind),
  Infra статусы 02-05, evening close + virtues, bottleneck для следующей недели.
* `focus_session` — autoincrement id; событийный лог Flash-сессий. Partial unique
  index `uniq_focus_active ON focus_session(ended_at) WHERE ended_at IS NULL`
  гарантирует «не более одной активной сессии» на уровне SQLite.

Все мульти-DAO операции в `RoomSessionRepository` обёрнуты в
`androidx.room.withTransaction { ... }` — `setInfraStatus` пишет в
`daily_session` и `habit_log` атомарно, `approveCore`/`closeEvening` читают
свежее состояние внутри транзакции для устранения race conditions.

## Локальная сборка

1. Установите Android SDK 35 (Android Studio Iguana+ или `sdkmanager
   "platforms;android-35" "build-tools;35.0.0"`).
2. Создайте `local.properties` в корне:
   ```
   sdk.dir=/path/to/Android/sdk
   ```
3. Соберите и запустите тесты:
   ```
   ./gradlew :app:assembleDebug
   ./gradlew :app:testDebugUnitTest
   ```

## Известные TODO

* **`app/schemas/`** — `exportSchema=true` в `@Database`, KSP генерит JSON-схемы
  при сборке, но они пока не закоммичены. После первой локальной сборки добавьте
  файлы `app/schemas/com.avangard.app.core.database.AppDatabase/{1..5}.json` в
  репозиторий, чтобы будущие миграции можно было покрыть `MigrationTestHelper`.
* **Migration tests** — после коммита schemas написать
  `app/src/test/java/.../MigrationTest.kt` с фикстурами для v1→v5 (Robolectric
  + `androidx.room.testing.MigrationTestHelper`).
* **Cross-midnight focus split** — Flash-сессия, перешагнувшая полночь, остаётся
  привязанной к стартовому дню. UI таймер продолжает считать (`26:14:32`), но
  `sumFocusDurationFor` нового дня её не учитывает. Авто-split — будущая фича.
