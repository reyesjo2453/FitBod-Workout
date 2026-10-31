# FitBod (Android)

A native Android rewrite of the FitBod workout logger, designed for the Galaxy Z Fold.
It reads and writes the same `FitBod/fitbod-sync.json` file in Google Drive as the
previous app, so your existing history, Set A / Set B routines, gyms and settings carry
over unchanged.

## Screens

- **Workout**: the next routine in your rotation (Set A → Set B → …), with a chip to
  pick any routine and a gym picker. Each exercise shows today's suggested sets, reps
  and weight. Tap the pencil to edit the routine, or **+ New** to create one.
- **Active workout**: a FitBod-style set table (Set · Previous · lb · Reps · ✓).
  Checking off a set starts the rest timer, which vibrates and chimes when it ends.
  You can add sets, add, swap, reorder or remove exercises, and minimise the workout
  to resume it later.
- **Log**: every finished workout grouped by month, with set details. Deleting a
  workout also removes it from Drive.
- **Progress**: workout totals, weekly streak, weekly volume, and per-exercise
  estimated 1RM charts, PRs and history.
- **Settings**: Drive connection, units, default rest, goal and split, routines, gyms,
  the exercise library, and JSON backup import/export.

### Folded, unfolded and Flex mode

| Phone state | Layout |
| --- | --- |
| Folded (cover screen) | One pane with a bottom tab bar. The active workout lists every exercise with its sets inline. |
| Unfolded | Navigation rail with list and detail panes: routine overview next to its exercises, exercises next to the set logger, history next to workout details, lifts next to charts. |
| Half-open, hinge horizontal (Flex/tabletop) | During a workout, the top half shows the current set target or a large rest countdown, and the bottom half holds the set controls. |
| Half-open, hinge vertical (book) | Panes split exactly at the hinge. |

The activity handles fold changes without restarting, so an in-progress workout and
the rest timer carry on when you open or close the phone.

## How progression works

For each routine exercise, the app looks at your last session of that exercise, using
the same gym if you have history there, since machines differ between gyms. If every
set at the top weight reached the target reps, it suggests +5 lb (or +2.5 kg).
Otherwise it repeats the weight. Exercises with no history start at the routine's
target weight.

## Sync and data compatibility

- The file format is version 2, with the same keys, key order, nesting and number
  style as the original app. Any fields this app doesn't know about (for example
  `templates`, or extra keys other tools add to workouts) are kept exactly as they
  are.
- Workouts merge by `syncId`; the newer `updatedAt` wins. Deletions are recorded in
  `tombstones`, so a workout deleted on one device stays deleted. Workouts that other
  writers (such as Merlin) add to the file are picked up on the next sync.
- Routines, gyms, settings and the exercise library have no timestamps. Changes you
  make on the phone win until they are synced; otherwise the Drive copy wins.
- Sync runs when the app comes to the foreground, after you finish or delete a
  workout, and from **Settings → Sync now**.
- In-progress workouts stay on the phone (`session.json`) and are written to Drive
  only when you finish them, as `status: "done"`.

`core/` contains this logic as plain Kotlin with unit tests. To check a real export:

```bash
cd android
CORE_ONLY=1 FITBOD_SYNC_FILE=/path/to/fitbod-sync.json ./gradlew :core:test
```

## Google Drive setup (one time)

The app keeps the original permission, `drive.file`, under which Google only shows an
app the files created by **the same Google Cloud project**. The package name is still
`com.johnreyes.fitbod`, so you can either:

1. **Use your original keystore (best).** Add it as repository secrets
   (`FITBOD_KEYSTORE_BASE64`, `FITBOD_KEYSTORE_PASSWORD`, `FITBOD_KEY_ALIAS`,
   `FITBOD_KEY_PASSWORD`). CI then also builds a signed release APK. That APK installs
   as an update over your current app, and Drive works with no Cloud changes.
2. **Use the debug build.** In the Google Cloud project that owns your existing
   FitBod OAuth client, open **APIs & Services → Credentials → Create credentials →
   OAuth client ID → Android**. Use package `com.johnreyes.fitbod` and this SHA-1:

   ```
   C0:32:F7:EB:5F:96:94:87:4A:01:56:44:0F:D5:39:76:07:11:69:53
   ```

   This is the SHA-1 of `app/debug-signing.jks`. It is committed so every debug build
   has the same fingerprint. Because the signature differs from your installed app,
   uninstall the old app first. Your data is safe in Drive.

If sign-in fails with code 10, the signing SHA-1 is not registered in that project.

## Building

GitHub Actions (`.github/workflows/android.yml`) runs the core tests and builds the
APK on every push that touches `android/`. Download it from the run's **fitbod-apk**
artifact. Locally, open `android/` in Android Studio, or run
`./gradlew :app:assembleDebug` with an Android SDK installed.

Minimum Android version: 8.0 (API 26). Target: API 35.
