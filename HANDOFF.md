# Handoff: where things stand

_Last updated: 15 Nov 2026, 11:50 am. Keep this file current (see CLAUDE.md, "Session handoff notes")._

## Right now

- **Phase 1 (Courses & tasks): done and merged into `main`** (`543b387`). Its history is in `PHASE-1.md` and `ISSUES.md`.
- **Current phase: Phase 1b, Task views.** The plan is in **`PHASE-1B.md`**; the user approved both the plan and the calendar/timeline layouts.
- **Branch:** `phase-1b`, pushed. Done:
  - B.1 Days left
  - B.2 Course colours ("next free colour")
  - 🔺 B.3 Database version 2 ("available from"), done at max effort
  - B.4 Home tabs
  - B.5 Calendar
  - B.6 Timeline
  - B.7 Course folder views (added by the user after tablet testing; OK'd, pushed and installed on the tablet)
  - 🔺 B.8 Archive courses (database v3), done at max effort; OK'd, pushed and installed on the tablet
- **The tablet runs B.1–B.8 (database version 3)**, installed 15 Nov ~11:50 am with the user's OK. The upgrade was compared with a fresh safety copy: every value of all 124 tasks, 10 courses and 4 folders was identical, no course started archived, integrity OK.
- **Mode: "Working together"** (the user is back). They liked B.1–B.6 on the tablet ("looks good, feels good and works well") and asked for two more steps, now B.7 and B.8 in `PHASE-1B.md`.

## Next steps (in order)

1. **B.9 New folder: course drop-down** (current courses only, no archived; also in "Link a course").
2. End of phase: `/code-review`, fix, retest, then ask to merge into `main`.
3. **Later, in Phase 2: subfolders** (the user asked for them on 15 Nov). IDEA.md §2.1 and the §5 Phase 2 row now include them.
   - Planned for the start of Phase 2, so the folder database change goes in with the notebook storage change.
   - Layout reference: `design-reference/notein-inside-course-folder.jpg` (a filter row All · PDF · Note · Folder, then a thumbnail grid).
   - Open questions for then:
     - do subfolders inherit the parent's course?
     - do their tasks show in the parent?
     - how deep can folders nest?

## Tablet database safety copy

- **`backups/tablet-db-2026-11-15-before-v2/`** and **`…-before-v3/`** (git-ignored, local only) hold the tablet's database from just before the B.3 and B.8 upgrades. It contains personal Canvas names but no secrets. Keep it until Phase 1b is merged and the user is happy; never commit it.
- How it was made, for next time (ask the user first):
  - force-stop the app
  - `adb -s <tablet> exec-out run-as com.nzfrankwu.noteapp cat databases/<f>` for `noteapp.db`, `-wal` and `-shm`
  - check it by opening a copy with Python's `sqlite3`, printing counts only
- **Never install an older build than the tablet has** (it's now heading to database v3): Room can't downgrade, so the app would crash at start. Data stays safe, and a current build fixes it.

## Waiting on the user

- **Tablet testing** of B.1–B.6 (installed; see `REPORT.md` for the steps).
- **Tablet checks not yet reported:**
  - a test reminder arriving with the app swiped away (ColorOS)
  - "Last synced" moving on its own over several hours (background sync)

  Ask about these when convenient.
- **Their Canvas sign-in on the tablet works** (go/no-go passed 15 Nov). Their real courses and assignments are on the tablet.

## Decisions to remember

- **The app name** is spelled "Noteapp". The app ID `com.nzfrankwu.noteapp` never changes.
- **Tasks:**
  - Tasks belong to courses; folders link to at most one course.
  - Overdue comes only from the real due date.
  - A due date without a time means 11:59 pm; a target date without a time means 9:00 am.
- **Canvas is read-only (GET only).** Canvas tasks can't be deleted unless removed from Canvas. Auto-status only moves forward, once per new submission.
- **Phase 1b:**
  - **Course colours:** "next free colour" (the user's choice).
    - Courses are sorted by `created_at` (ties by code), and the nth course gets colour n mod 7 (`CourseColorKey.slotsInOrder`).
    - `MainActivity` feeds the course list into `NoteAppTheme(courses = …)`. Codes not yet saved fall back to a fixed hash.
    - The palette order alternates hues, so reordering it recolours courses. Red, orange and amber are kept out.
  - **Days left:**
    - amber for "Due today" up to "2 days left"
    - a task due earlier today says "Was due today" (red)
    - the Overdue badge stays alongside "N days overdue"
  - **"Available from" (B.3):**
    - `Task.availableAt` is Canvas's `unlock_at` (not `lock_at`), Canvas-owned and refreshed on every sync.
    - It's null for your own tasks and for Canvas assignments without an unlock date. The timeline then uses `createdAt` (for Canvas tasks, when Noteapp first imported them).
  - The calendar weeks run Monday to Sunday.
  - **Archiving (B.8):**
    - `courses.archived_at`
    - Home uses `observeActiveWithCourse()`; a course folder uses `observeAllWithCourse()`
    - Course filters list current courses only, and a filter left on a since-archived course resets to all
    - The task form's course picker shows current courses plus the task's own course
    - Reminders skip archived courses' tasks (`ReminderScheduler.apply`, plus a re-check in `ReminderReceiver`)
    - Course colours still count archived courses, so colours never shift

## Code map (app/src/main/java/com/nzfrankwu/noteapp/)

| Folder | What's in it |
|---|---|
| `data/db/` | Room database **v3**: `Course` (+`archivedAt`), `Folder`, `Task` (+`availableAt`), DAOs, `Migrations.kt` (`MIGRATION_1_2`, `MIGRATION_2_3`), `CourseListItem` |
| `data/` | Repositories (`FolderRepository`, `TaskRepository`), `CanvasSettings` (DataStore), `QuickAdd` |
| `domain/` | Plain-Kotlin rules: `DaysLeft`, `CourseColorKey`, `TaskRules` (overdue, Due soon sections, default times), `Reminders`, `CourseCodes`, `Links`, `SharedLink`, `DateText` |
| `canvas/` | `CanvasClient` (GET only), `CanvasParser`, `CanvasMerge`, `CanvasSync`, `CanvasSyncRunner`, `CanvasSyncWorker`, `CanvasAccount` (sign-in and out), `FakeCanvas` (debug; most assignments have "available from" dates) |
| `reminders/` | Alarm scheduling and receivers |
| `ui/` | `NoteAppRoot` (Navigation 3), with screens in `home/`, `folder/`, `task/`, `settings/`, `canvas/`; reusable parts in `components/` (incl. `CourseTag`, `DaysLeftText`); all styling in `theme/` (`Color`, `Shape`, `Dimens`, `StatusColors`, `CourseColors`, `Type`) |

`AppContainer.kt` creates everything (manual dependency injection), and `NoteAppApplication` re-plans reminders and keeps the background sync scheduled.

## Gotchas (save yourself time)

- **Shells:**
  - In PowerShell, run Gradle as `.\gradlew.bat …`.
  - In Git Bash, put `export MSYS_NO_PATHCONV=1` before `adb shell` commands that use device paths like `/sdcard/...`.
  - **adb isn't on the Git Bash PATH:** use `/c/Users/hiimf/AppData/Local/Android/Sdk/platform-tools/adb.exe`.
- **Emulator tests:** `$env:ANDROID_SERIAL = "emulator-5554"` before `connectedDebugAndroidTest`, so tests never run on the tablet.
  - That run **uninstalls and reinstalls the app on the emulator**, which wipes its data. Recreate sample data before taking screenshots: Settings → "Use fake Canvas (debug)", then Home → "Link courses" → "Create folder" ×3.
  - One test class only: add `"-Pandroid.testInstrumentationRunnerArguments.class=<full class name>"`.
  - Results are in `app/build/outputs/androidTest-results/connected/debug/*.xml`. Gradle `-q` prints nothing on success.
- **Tablet:** if `adb devices` doesn't list it, run `adb kill-server` then `adb start-server`. Install with `adb -s <tablet serial> install -r app/build/outputs/apk/debug/app-debug.apk`. Check the serial with `adb devices -l`; it's the OPD2405.
- **Emulator stylus handwriting is off** (`settings put secure stylus_handwriting_enabled 0`). Its "Try out your stylus" tutorial swallowed scripted typing.
- **To tap UI by text in scripts:**
  - `uiautomator dump`, pull the XML, find the node's bounds, then `input tap`.
  - Windows Python can't read `/tmp`, so pull the dump into a Windows path (use `cygpath -w`).
  - Matching is by substring, so "Edit" also hits "Tap Edit to add some". Use the exact content description (e.g. "Edit task").
  - Type text with `input text "Word%sword"` (`%s` = space).
- **Reading the emulator app's database:** `adb exec-out run-as com.nzfrankwu.noteapp cat databases/noteapp.db > file` (plus `-wal` and `-shm`), then open a copy with Python's `sqlite3`. This works because debug builds are debuggable. Emulator only, unless the user agrees.
- **Emulator sample data:** `bash <scratchpad>/setup.sh`-style script: install, Settings → fake Canvas, Link courses → Create folder ×3 (scratchpad scripts may be gone in a new session; rewrite with the tap-by-text pattern).
- **Home remembers the last tab** (DataStore survives emulator test reinstalls): tests that open the real app must tap `home-tab-overview` first (see `NavigationTest`).
- **Testing an app upgrade for real:**
  - keep the old build's APK
  - install it on the emulator and add data
  - snapshot the database
  - `install -r` the new build
  - compare the databases
- **Pinned versions:**
  - `kotlinx.serialization` is at 1.9.0 (the newest for Kotlin 2.2, which comes with AGP 9.5.1). It must stay at or above Room's needs, or `MigrationTest` crashes.
  - Libraries built for Kotlin 2.3+ won't fit until Kotlin is upgraded.
- **Database changes:** follow the steps at the top of `Migrations.kt`.
  - a version bump
  - a migration declared **above** `ALL_MIGRATIONS`
  - a new column as the **last** entity field
  - a new line in `SchemaHistoryTest`
  - new checks in `MigrationTest`

  Never destructive. The guard tests (`SchemaHistoryTest`, `MigrationListTest`, `DataSafetyRulesTest`, `MigrationTest`) fail otherwise.
- **Secrets:** never create, read or print files in `C:\Users\hiimf\keys\` (signing key and passwords). Never read the in-app browser's cookie store.
- **`REPORT.md`, `REPORT-*.md` and `screenshots/` are git-ignored**: local only. `REPORT-phase-1.md` is the old Phase 1 overnight report.
