# Room schema snapshots

Room is configured with `exportSchema = true` and
`room.schemaLocation = "$projectDir/schemas"`. After any successful
`./gradlew :app:assembleDebug` KSP writes JSON snapshots into
`com.avangard.app.core.database.AppDatabase/{1,2,3,4,5}.json`.

**Action required after first successful build with v3.3 on a machine
with plugin-repo access:**

1. `./gradlew :app:assembleDebug`
2. `git add app/schemas/com.avangard.app.core.database.AppDatabase/*.json`
3. Commit the snapshots.
4. Rewrite `app/src/test/.../MigrationTest.kt` to use Room's
   `MigrationTestHelper` (it requires the JSON snapshots to live in
   `app/schemas/`).

Until those snapshots land, the hand-rolled migration tests in
`MigrationTest.kt` keep behavioural coverage of every column add /
table drop / index change between AppDatabase v1 and v5.
