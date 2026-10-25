# Iter Meum

Личное Android-приложение одного человека: труд, молитва, путь к купели.
Реализация по документу «ТЗ Iter Meum v2». Принятые по ходу решения — в
`docs/DECISIONS.md`.

## Сборка

```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Нужен Android SDK 35 (`platforms;android-35`, `build-tools;35.0.0`) и `local.properties`
с `sdk.dir`. Google Sign-In требует OAuth-клиента Android для пакета
`com.itermeum.app` с SHA-1 вашего ключа подписи; без него бэкап выключен.
