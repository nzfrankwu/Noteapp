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
- **Reminder alarms are inexact.** They may arrive a few minutes late in battery saving, and ColorOS may delay them more; this needs a tablet check (4.2). No "exact alarm" permission is used.
- **Canvas course codes with extra text** (e.g. "ELECTENG 204 S2 2026") won't auto-link to your own "ELECTENG 204". They appear as separate courses; home then suggests linking them ("New from Canvas: …"), and **Add to a folder** puts one in your existing folder.
- **The emulator's stylus handwriting is turned off** (emulator only; `settings put secure stylus_handwriting_enabled 0`), because its "Try out your stylus" tutorial swallowed scripted typing. The tablet is unaffected.
- **Screen tests leave their in-memory databases open** (`TaskScreensTest`, `DueSoonTest`). Closing them while a screen was still reading crashed a run once. Harmless for tests; revisit if test memory ever becomes a problem.
- **Never install an older build than the one on the tablet.** The database is now version 3 (B.8), and Room can't go back to an older version: the older app would refuse to open (crash at start). Your data stays safe, and installing a current build fixes it, but don't downgrade.
- **For Phase 6 backup:** Room keeps recent changes in a side file (`noteapp.db-wal`) next to `noteapp.db`. The backup must save them into the main file first (a "checkpoint") or copy all three files together. Otherwise a backup could miss the latest edits.

## Done

- **New folder: choose the course from a list (Phase 1b, step B.9), 15 Nov.** New folder and "Link a course" offer your current courses that don't have a folder yet (archived ones left out), plus "Other course code…" for a new one. Picking a course fills in an empty folder name. 3 new emulator tests.

- **Archive courses (Phase 1b, step B.8 🔺), 15 Nov.** Archive a course from its folder's ⋮ menu (on the tile or inside the folder), from "Link your Canvas courses", or from the new Settings → Courses list (which covers courses without a folder too). An archived course's tasks leave Due soon, Calendar, Timeline and the course filters, its reminders stop, and its folder moves to a collapsed "Archived (N)" row under Folders (faded). Its folder still shows everything, with an "Archived" badge. Nothing is deleted, and archived courses are never suggested as "New from Canvas". Updating the app adds an empty column (database version 3); every existing value is kept. Checked with migration tests, a deliberately broken migration (caught), and a real upgrade of the previous app with data on the emulator. 4 unit tests and 15 emulator tests.

- **Course folder views (Phase 1b, step B.7), 15 Nov.** A course folder has Tasks · Calendar · Timeline tabs, the Calendar and Timeline showing only that course. 3 new screen tests.

- **Timeline (Phase 1b, step B.6), 15 Nov.** One row per dated task with a bar from "available from" (or when it was added) to the due date, course colours, status dot, ◆ target date, a red outline when overdue, ◀/▶ when a bar carries on off-screen, edge arrows for tasks outside the visible days (tap to jump there), weekends shaded, a blue line for now, "2 weeks | Month", ◀ ▶ / Today / swipe, the same filters as the calendar, and "N tasks without a due date". 21 unit tests and 4 screen tests.

- **Calendar (Phase 1b, step B.5), 15 Nov.** Month grid Monday–Sunday with each task on its due date (or its target date, marked "Target"), course-colour bars, time and status dot, overdue in red, "+N more", a side sheet per day with "Add task on this day", ◀ ▶ / Today / swipe, "All courses ▾" and "Show finished". 18 unit tests and 4 screen tests.

- **Home tabs (Phase 1b, step B.4), 15 Nov.** Overview · Calendar · Timeline in the top bar; the app reopens on the last tab used. Pull-to-refresh stays on Overview. 2 screen tests and 2 unit tests.

- **Database version 2: "available from" (Phase 1b, step B.3 🔺), 15 Nov.** Tasks now store when Canvas makes an assignment available (Canvas's `unlock_at`), for the timeline. Updating the app adds an empty column; every existing value is kept, and the next Canvas sync fills in the dates. Your own tasks have none (the timeline will use the date you added them). Checked with unit tests, 5 migration tests on the emulator, a deliberately broken migration (caught), and a real upgrade of the previous app with data on the emulator. Nothing visible changes yet.

- **Course colours (Phase 1b, step B.2), 15 Nov.** Each course gets the next free colour in the order Noteapp first saw it (your choice at the check-in, so up to 7 courses never clash; 7 colours, light and dark). Neighbouring colours are very different, so a semester's new courses look distinct. It's used for the course tag (a small colour bar plus the code) on task cards, the task page, folder tiles (the folder icon too), the folder header and Link courses. 18 new unit tests.

- **Days left (Phase 1b, step B.1), 15 Nov.** Task cards and the task page show "Due today", "Due tomorrow", "3 days left" or "2 days overdue", counted in local calendar days to the real due date (daylight saving tested). Amber for today to 2 days, red when overdue. 16 unit tests and 1 screen test.

- **Canvas sign-in go/no-go passed (15 Nov, step 5.1).** Your university login works in Noteapp's in-app browser on the tablet, so the calendar-feed fallback isn't needed. This also answers the https-only question: the login didn't stall.

- **Code review of phase-1 (15 Nov, 2:35 am): 7 findings fixed.** App-open sync no longer re-asks Canvas while the sign-in is expired; folder task sections refresh every minute; one sign-in message instead of two; a test-only workaround removed; faster task lists; one shared wording for folder errors.

- **Canvas screens (Phase 1, steps 5.1 built, 6.2, 6.3, 7.3), 15 Nov.** Settings → Canvas (address, Sign in, Signed in as, Last synced, Sync now, Sign out), the in-app sign-in browser, sync when the app opens and every 6 h, pull-to-refresh, the expired-sign-in strip, and "Link your Canvas courses". Debug builds have a fake Canvas. Waiting on your real login (5.1).

- **Quick-add from a link (Phase 1, step 8.1), 15 Nov.** Share a link to Noteapp from the browser or any app: New task opens with the title and link filled in (and the course, for Canvas links once Canvas is connected).

- **Reminders finished on the app side (Phase 1, step 4.1), 15 Nov.** The app asks to allow notifications after you save a dated task; tapping a reminder opens its task; Settings has a Reminders section with a debug "Test reminder in 1 minute". (Resolves "no permission prompt" and "tapping opens home".) Still needs the ColorOS check on the tablet.

- **Due soon on the home screen (Phase 1, steps 3.1–3.2), 15 Nov.** Every course's tasks by date with Overdue flagged, a collapsible "No due date" section, "Show finished", and a narrow-card layout for portrait. 5 unit and 4 screen tests.

- **Task screens (Phase 1, steps 2.1–2.3), 15 Nov.** Add, view, edit and delete tasks (with Undo), status tags you tap to change, and the Overdue badge. 13 new unit tests and 9 new screen tests.

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
  - Sync timing: the 15-minute rule, the sync runner, and the 6-hourly background job.
  - Linking a course to an existing folder, and unlinking it.
  - The folder screen (step 1.2): opens from a tile, shows the course's tasks in sections, and links or unlinks a course.
  - Totals: 123 unit tests and 77 emulator tests, all passing.
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
