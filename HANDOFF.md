# Handoff: where things stand

_Last updated: 15 Nov 2026, 9:15 am. Keep this file current (see CLAUDE.md, "Session handoff notes")._

## Right now

- **Phase 1 (Courses & tasks): done and merged into `main`** (`543b387`). Its history is in `PHASE-1.md` and `ISSUES.md`.
- **Current phase: Phase 1b, Task views.** The plan is in **`PHASE-1B.md`**; the user approved both the plan and the calendar/timeline layouts.
- **Branch:** `phase-1b`. **B.1 Days left is built and committed; waiting for the user's check-in OK** (then push the branch).
- **Mode:** "Working together" unless the user says otherwise.

## Next steps (in order)

1. **Check-in for B.1** (screenshots `screenshots/b1-*.png`). Push `phase-1b` once the user OKs it.
2. **B.2 Automatic course colours.** Palette in `ui/theme/`, keyed by course code.
3. 🔺 **B.3 Database v2.** Add `available_at` (Canvas `unlock_at`), with a migration and tests (see `data/db/Migrations.kt` for the steps). Before starting, tell the user it's a 🔺 step so they can switch to a stronger model.
4. **B.4 Home tabs**, **B.5 Calendar** and **B.6 Timeline**, following the layouts in `PHASE-1B.md`.
5. End of phase: `/code-review`, then ask to merge into `main`.

## Waiting on the user

- **Tablet checks not yet reported:**
  - a test reminder arriving with the app swiped away (ColorOS)
  - "Last synced" moving on its own over several hours (background sync)

  Ask about these when convenient.
- **Their Canvas sign-in on the tablet works** (go/no-go passed 15 Nov). Their real courses and assignments are now on the tablet.

## Decisions to remember

- **The app name** is spelled "Noteapp". The app ID `com.nzfrankwu.noteapp` never changes.
- **Tasks:**
  - Tasks belong to courses; folders link to at most one course.
  - Overdue comes only from the real due date.
  - A due date without a time means 11:59 pm; a target date without a time means 9:00 am.
- **Canvas is read-only (GET only).** Canvas tasks can't be deleted unless removed from Canvas. Auto-status only moves forward, once per new submission.
- **Phase 1b:**
  - days left: amber for "Due today" up to "2 days left"; a task due earlier today says "Was due today" (red); the Overdue badge stays alongside "N days overdue"
  - the calendar weeks run Monday to Sunday
  - timeline bars start at Canvas "available from" (your own tasks: when they were added)
  - course colours are automatic for now

## Code map (app/src/main/java/com/nzfrankwu/noteapp/)

| Folder | What's in it |
|---|---|
| `data/db/` | Room database v1: `Course`, `Folder`, `Task`, DAOs, `Migrations.kt` |
| `data/` | Repositories (`FolderRepository`, `TaskRepository`), `CanvasSettings` (DataStore), `QuickAdd` |
| `domain/` | Plain-Kotlin rules: `DaysLeft`, `TaskRules` (overdue, Due soon sections, default times), `Reminders`, `CourseCodes`, `Links`, `SharedLink`, `DateText` |
| `canvas/` | `CanvasClient` (GET only), `CanvasParser`, `CanvasMerge`, `CanvasSync`, `CanvasSyncRunner`, `CanvasSyncWorker`, `CanvasAccount` (sign-in and out), `FakeCanvas` (debug) |
| `reminders/` | Alarm scheduling and receivers |
| `ui/` | `NoteAppRoot` (Navigation 3), with screens in `home/`, `folder/`, `task/`, `settings/`, `canvas/`; reusable parts in `components/`; all styling in `theme/` (`Color`, `Shape`, `Dimens`, `StatusColors`, `Type`) |

`AppContainer.kt` creates everything (manual dependency injection), and `NoteAppApplication` re-plans reminders and keeps the background sync scheduled.

## Gotchas (save yourself time)

- **Shells:**
  - In PowerShell, run Gradle as `.\gradlew.bat …`.
  - In Git Bash, put `export MSYS_NO_PATHCONV=1` before `adb shell` commands that use device paths like `/sdcard/...`.
- **Emulator tests:** `$env:ANDROID_SERIAL = "emulator-5554"` before `connectedDebugAndroidTest`, so tests never run on the tablet.
  - That run **uninstalls and reinstalls the app on the emulator**, which wipes its data. Recreate sample data (or use Settings → "Use fake Canvas (debug)") before taking screenshots.
- **Tablet:** if `adb devices` doesn't list it, run `adb kill-server` then `adb start-server`. Install with `adb -s <tablet serial> install -r app/build/outputs/apk/debug/app-debug.apk`. Check the serial with `adb devices -l`; it's the OPD2405.
- **Emulator stylus handwriting is off** (`settings put secure stylus_handwriting_enabled 0`). Its "Try out your stylus" tutorial swallowed scripted typing.
- **To tap UI by text in scripts:** `uiautomator dump`, pull the XML, find the node's bounds, then `input tap`. Windows Python can't read `/tmp`, so pull the dump into a Windows path (use `cygpath -w`).
- **adb isn't on the Git Bash PATH:** use `/c/Users/hiimf/AppData/Local/Android/Sdk/platform-tools/adb.exe`.
- **Pinned versions:**
  - `kotlinx.serialization` is at 1.9.0 (the newest for Kotlin 2.2, which comes with AGP 9.5.1). It must stay at or above Room's needs, or `MigrationTest` crashes.
  - Libraries built for Kotlin 2.3+ won't fit until Kotlin is upgraded.
- **Database changes:** a version bump, a migration and a new line in `SchemaHistoryTest`; never destructive. The guard tests fail otherwise.
- **Secrets:** never create, read or print files in `C:\Users\hiimf\keys\` (signing key and passwords). Never read the in-app browser's cookie store.
- **`REPORT.md` and `screenshots/` are git-ignored**: local only.
