# Avangard

Утилитарная система персональной отчётности (Android).

## Стек
- Android Native (minSdk 26, targetSdk 34), Kotlin 2.0
- Jetpack Compose + Material3 (тема `MachineTheme`)
- Hilt, Room, WorkManager, DataStore
- KSP для аннотационной обработки

## Сборка
```
./gradlew :app:assembleDebug
```
Требуется Android SDK 34 и `local.properties` с `sdk.dir=/path/to/Android/sdk`.

## Структура
```
app/src/main/java/com/avangard/app/
  AvangardApplication.kt    # Hilt + WorkManager
  MainActivity.kt           # точка входа Compose
  ui/theme/                 # MachineTheme: цвета, типографика
  feature/dashboard/        # экран панели управления
```
