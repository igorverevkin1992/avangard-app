# Avangard

Утилитарная система персональной отчётности (Android). Жизненные принципы
Extreme Ownership / Утилитарность / Фокус на артефактах конвертированы в
жёсткие алгоритмические правила.

## Стек
- Android Native (minSdk 26, targetSdk 34), Kotlin 2.0
- Jetpack Compose + Material3 (тема `MachineTheme`)
- Hilt, Room, WorkManager, DataStore, Navigation Compose
- KSP для аннотационной обработки

## Локальная сборка
1. Установите Android SDK 34 (Android Studio Iguana+ или `sdkmanager
   "platforms;android-34" "build-tools;34.0.0"`).
2. Создайте `local.properties` в корне:
   ```
   sdk.dir=/path/to/Android/sdk
   ```
3. Соберите и запустите тесты:
   ```
   ./gradlew :app:assembleDebug
   ./gradlew :app:testDebugUnitTest
   ```

## Структура
```
app/src/main/java/com/avangard/app/
  AvangardApplication.kt    Hilt + WorkManager + ScheduleBootstrapper
  MainActivity.kt           Compose entry point
  navigation/               Compose Navigation graph
  ui/theme/                 MachineTheme: цвета, типографика
  core/
    common/                 Clock, DomainResult
    database/               Room entities, DAO, AppDatabase
    data/                   RoomReportRepository
    domain/                 ReportRules, use cases, models
    ui/components/          IndustrialToggle, IndustrialGauge,
                            IndustrialCheckbox, OscilloscopeChart
  feature/
    dashboard/              Главный экран
    report/morning/         Утренняя инициализация
    report/evening/         Вечерний рапорт + Анализ отказа
    analytics/              Осциллограф + журнал
    settings/               Разрешения + стирание базы
  sync/
    notifications/          Канал и тексты уведомлений
    permissions/            PermissionGate composable
    scheduler/              AlarmDispatcher, BootCompletedReceiver,
                            ReportAlarmReceiver, AlarmSlot
```

## Замечания

### Шрифт
В качестве временного решения подключён `Roboto Condensed` через
`androidx.compose.ui:ui-text-google-fonts`. Прод-цель — `Univers Next
Cyrillic` (коммерческий) или `ST-Kosmolet`. Замена локализована в
`ui/theme/Type.kt::DisplayFamily`.

### Окно утренней инициализации
В `MorningReportViewModel` вызов `InitializeDayUseCase` пропускает
проверку слота 06:00–08:00 в debug-сборках (`BuildConfig.DEBUG`). В
release-сборке окно соблюдается строго.

### Точные будильники
Если пользователь отзывает `SCHEDULE_EXACT_ALARM`, `AlarmDispatcher`
автоматически переходит на `setAndAllowWhileIdle()` и на дашборде
показывается красный баннер `ТОЧНОСТЬ СНИЖЕНА` с deep-link в системные
настройки.

### Бэкап
Auto-backup отключён в манифесте (`android:allowBackup="false"`).
Резервное копирование в `appDataFolder` Google Drive — Фаза 6 (v2.0).
