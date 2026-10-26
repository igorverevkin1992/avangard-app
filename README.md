# Iter Meum

Личное Android-приложение одного человека: труд, молитва, путь к купели.
Реализация по документу «ТЗ Iter Meum v2» (фазы 1–9). Принятые по ходу
решения — в `docs/DECISIONS.md`.

## Что внутри

- **Сегодня** — утренний блок (будильник → QR-пруф → Serviam → карантин),
  кольцо дня в литургическом цвете, три стрика, звонки/договорённости,
  баннеры заморозок, экран вехи.
- **Труд** — план дня до начала работы, обзвон героев с отсечкой 14:00
  («лягушка»), последний камень до 21:00, ритуал перехода 15 с, сценарий,
  проекты с этическим фильтром, QS-плитка и виджет 4×2.
- **Молитва** — ментальная молитва, Angelus/Regina Caeli, стрелы по
  ситуациям, Евангелие дня (Синодальный перевод + собственный лекционарий
  A/B/C, I/II), программа чтения, Розарий, Месса (духовное причастие до
  крещения), Examen с валидатором propósito, SM-2 заучивание молитв,
  литургический календарь.
- **Путь** — катехизация (суббота 16:00), обзор недели, итог месяца,
  квартальная проверка, «Я крещён», Летопись.
- **Система** — логический день 03:00–02:59, 23 нормы, расписание с
  тихими окнами, эскалация уведомлений с лимитом 8/день, точные будильники
  и HyperOS-чеклист, шифрование полей через Keystore, бэкап v1
  (AES-256-GCM, PBKDF2 210 000) в Google Drive AppData.

## Сборка

Ключ подписи в репозиторий не кладётся. Локально: файл `app/keystore/itermeum.jks`
и в корне репозитория `keystore.properties` со строкой `storePassword=…`. В CI —
секреты `ITERMEUM_KEYSTORE_B64` и `ITERMEUM_KEYSTORE_PASSWORD`.

При изменении схемы БД: поднять версию в `AppDatabase`, написать миграцию в
`Migrations.kt`, собрать и закоммитить новый `app/schemas/.../N.json`; CI
проверяет, что схема закоммичена, а `MigrationsTest` прогоняет миграции по
настоящим схемам.

```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Нужен Android SDK 35 (`platforms;android-35`, `build-tools;35.0.0`) и `local.properties`
с `sdk.dir`. CI (`.github/workflows/build.yml`) на каждый push: юнит-тесты, debug- и
release-сборка (R8, mapping в артефакте), lint (отчёт в артефакте).
Артефакты: `itermeum-debug-apk`, `itermeum-release`.

## Что нужно сделать вручную

- Google Sign-In требует OAuth-клиента Android в Google Cloud для пакета
  `com.itermeum.app` с SHA-1 ключа подписи
  `EE:B5:F0:60:1A:53:98:B1:B5:82:AC:6F:1D:C9:D8:51:30:1D:6D:18` (D-21).
  Ключ хранится вне репозитория: в GitHub Actions как секреты
  `ITERMEUM_KEYSTORE_B64` (base64 файла .jks) и `ITERMEUM_KEYSTORE_PASSWORD`,
  локально как `app/keystore/itermeum.jks` + `keystore.properties` со строкой
  `storePassword=…`. Без ключа бэкап в Drive выключен (локально всё работает).
- Тексты молитв (`assets/content/prayers.json`) и таблицы лекционария
  (`assets/lectionary/*.json`) помечены `verify: true` — сверить с
  официальными изданиями перед снятием пометки «редакция не подтверждена».
