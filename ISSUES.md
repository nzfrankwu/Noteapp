# Noteapp – Issues & Status

Running list of the project's current state. Updated with every step (see `CLAUDE.md`).

## Known bugs

_None yet._

## Open questions

_None right now._

## Follow-ups / tech debt

- Build warning: `libandroidx.graphics.path.so` can't be stripped of debug symbols, so it's packaged as is. This is harmless and comes from AndroidX. No action needed unless APK size matters.
- The signing key exists only on this PC (in `C:\Users\hiimf\keys\`) plus your own backups. On a new computer, copy both `noteapp-release.jks` and `noteapp-keystore.properties` into `<user home>\keys\` before building. Otherwise builds fall back to the debug key (a warning is printed) and can't update the app on the tablet.

- No backup exists until Phase 6. Android's built-in backup is now off (step 0.2), so nothing on the tablet is backed up until NoteApp's own Drive backup and "Export everything" are built. That's fine while the app only holds test data. IDEA §5a already requires Phase 6 to be finished before real use.
- The launcher icon is still the template's green Android robot. A proper NoteApp icon is a polish item.
- **kotlinx.serialization is pinned at 1.9.0**, the newest built for this project's Kotlin 2.2 (it comes with AGP 9.5.1). It must stay at or above Room's needs (1.8.1), or `MigrationTest` crashes with `AbstractMethodError`. A later Kotlin upgrade (2.3+) would allow newer versions.
- **Reminders can't show yet: no notification-permission prompt.** Android 13+ needs the user to allow notifications; the prompt comes with the task screens (4.1). Until then reminders are planned but silently not shown.
- **Tapping a reminder opens the home screen, not the task**, until the task detail screen exists (2.2).
- **Reminder alarms are inexact.** They may arrive a few minutes late in battery saving, and ColorOS may delay them more; this needs a tablet check (4.2). No "exact alarm" permission is used.
- **Canvas course codes with extra text** (e.g. "ELECTENG 204 S2 2026") won't auto-link to your own "ELECTENG 204". They appear as separate courses, to be linked by hand on the "Link your courses" screen (6.2, not built yet).
- **The emulator's stylus handwriting is turned off** (emulator only; `settings put secure stylus_handwriting_enabled 0`), because its "Try out your stylus" tutorial swallowed scripted typing. The tablet is unaffected.
- **For Phase 6 backup:** Room keeps recent changes in a side file (`noteapp.db-wal`) next to `noteapp.db`. The backup must save them into the main file first (a "checkpoint") or copy all three files together. Otherwise a backup could miss the latest edits.

## Done

- **Overnight 15 Nov, logic and data with no new screens** (full details in REPORT.md, which stays on this PC):
  - Task rules (overdue, past target, Due soon sections, default times).
  - Task repository (create, edit, status, delete and undo).
  - Canvas reading:
    - JSON parsing
    - a read-only client
    - merge and auto-status rules
    - the sync engine
  - Reminders:
    - when they fire
    - alarms that follow task changes, restarts and time changes
  - Reading links shared into the app.
  - Canvas sign-in groundwork: the address check, cookies and sign-out, and settings.
  - Totals: 120 unit tests and 63 emulator tests, all passing.
- Folders (Phase 1, step 1.1).
  - Create a folder with an optional course code, rename it, or delete it (with a confirmation).
  - Course codes are tidied and matched ignoring spaces and case, and a course can have only one folder.
  - Deleting a folder keeps its course and tasks.
  - Tests:
    - 5 course-code unit tests
    - 10 repository tests on the emulator
    - 5 screen tests on the emulator
  - Spacing and shapes now live in central theme files (`Dimens.kt`, `Shape.kt`).
- App renamed to "Noteapp" (lowercase a) everywhere the user sees it.

- App shell (Phase 1, step 0.4).
  - The home screen has Due soon and Folders: side by side at 720 dp or wider, stacked when narrower.
  - There's a Settings screen with Canvas (placeholder) and About (version).
  - Navigation 3 handles moving between screens, including the system back gesture.
  - 5 new screen tests run on the emulator. Material 3 Adaptive turned out not to be needed: a simple width check does the job.

- Database foundation (Phase 1, step 0.3). Room database version 1 with courses, folders and tasks.
  - Tasks belong to courses. Deleting a folder or course never deletes tasks.
  - Every row has a permanent random ID and created/updated times.
  - Statuses are saved by fixed keys.
  - The schema is exported to `app/schemas/`.
  - Guard tests protect against data-losing changes, and 29 automated tests pass (14 on the computer, 15 on the emulator).

- App identity (Phase 1, step 0.2). The app ID and package are now `com.nzfrankwu.noteapp` (this resolves the old "template package name" follow-up). The app is named "NoteApp" and has its own fixed light/dark theme (neutral greys with a blue accent, no wallpaper colours). Template libraries are updated. Android's built-in backup and device-to-device transfer are off. `PHASE-1.md` checklist added.
- Release signing key set up (Phase 1, step 0.1). All builds are signed with your own key, stored outside the project. The first key's password was accidentally exposed in the chat, so the key was recreated with a new password before anything was signed with it.

- Git repo set up with an Android `.gitignore` and pushed to GitHub (`main`).
- `CLAUDE.md` added with project guidelines.
- `IDEA.md` (the full plan) added. `CLAUDE.md` now points to it and defines the "Working together" and "Hands-off" modes.
