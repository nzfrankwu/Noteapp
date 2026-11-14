# Phase 1: Courses & tasks

The approved plan for IDEA.md Phase 1 (approved 2026-11-14), with the checklist ticked as steps pass.
Work happens on branch `phase-1`. ★ marks a check-in with the user (Working-together mode); the branch is pushed after each OK.
🔺 marks a difficult or high-stakes step. Before starting one, tell the user so they can switch to a stronger model (see CLAUDE.md).

Decisions made:
- **App ID:** `com.nzfrankwu.noteapp`. It must never change again.
- **Signing:** the release signing key was set up first. It lives in `<user home>/keys/`, outside the project.
- **Order:** IDEA's feature order is kept, so quick-add from a link comes last.

## Checklist

### F0: Foundation
- [x] **0.1 Signing key.** All builds are signed with your own key, so the app on the tablet always updates in place.
- [x] **0.2 App identity and clean-up.**
  - App ID and package become `com.nzfrankwu.noteapp`, and the name becomes "NoteApp".
  - The app gets its own fixed light/dark theme.
  - Libraries are updated.
  - Android's built-in backup and device-to-device transfer are off.
  - This file is added.
- [x] 🔺 **0.3 Database.** Course, Folder and Task tables, schema export, and migration test setup. *Nothing visible.*
  - Database version 1 (`data/db/`). The schema is in `app/schemas/`.
  - Guard tests:
    - `SchemaHistoryTest` fails if a released version's tables change.
    - `DataSafetyRulesTest` fails on any destructive database fallback.
    - `StoredKeysTest` fails if a saved status key changes.
    - `MigrationTest` checks that version-1 data survives updates.
- [x] **0.4 App shell.** Home with empty Due soon and Folders areas, Settings, navigation, and wide/narrow layouts (the layout is agreed first). ★
  - Agreed layout:
    - At 720 dp or wider (the tablet in both orientations), Due soon (left, 40%, shaded panel) and Folders sit side by side.
    - Narrower than that, Due soon is stacked above Folders.
    - Small round icon buttons sit in the top bar.
    - Settings opens with Canvas (placeholder) and About.
  - Navigation 3 (`ui/navigation/Routes.kt`, `ui/NoteAppRoot.kt`).

### F1: Folders
- [x] **1.1** Create a folder (name plus optional course code), rename it, or delete it with a confirmation.
  - The logic is in `FolderRepository` and the course-code rules in `CourseCodes`.
  - The UI is the home folder grid, `FolderTile`, and the New/Rename/Delete dialogs.
- [ ] **1.2** The folder screen: course header, a Notebooks placeholder, a Tasks section, and link/unlink a course. ★

### F2: Tasks
_Logic done ahead of the screens:_
- `domain/TaskRules.kt`: overdue, past target, Due soon sections, default times.
- `data/TaskRepository.kt`: create, edit, status, delete and undo.
- **Still to do:** the task screens below.
- [ ] **2.1** Add a task: name, course, optional due date and time, optional "My target date", link and notes. Undated tasks go at the bottom.
- [ ] **2.2** Task detail, edit, delete with Undo, and opening the link.
- [ ] **2.3** Status tags and the automatic Overdue badge. ★

### F3: Home screen
- [ ] **3.1** Due soon across all courses, sorted by target-or-due date, with overdue tasks flagged.
- [ ] **3.2** The collapsible "No due date" section, finished tasks, and narrow/portrait polish. ★

### F4: Reminders
_Logic done ahead:_ `domain/Reminders.kt`, with 9 tests. It covers:
- when reminders fire: 1 day before (same clock time) and 2 hours before the target date (or the due date)
- none for finished, undated or past tasks
- stable IDs per task and offset
- the notification text
- daylight-saving days

_Android part done:_
- `reminders/ReminderScheduler.kt` sets and cancels alarms (inexact-while-idle) and remembers them by ID.
- `ReminderReceiver` shows the notification after re-checking the task.
- `RescheduleReceiver` handles restart, app update, time and time-zone changes.
- `NoteAppApplication` re-plans alarms whenever tasks change.
- 4 emulator tests with real alarms.

**Still to do:**
- asking for notification permission (needs a screen)
- tapping a notification opening the task (the task screen doesn't exist yet; for now it opens the app)
- the debug "test reminder in 1 minute" button
- tablet checks: restart, ColorOS battery limits
- [ ] **4.1** Notifications 1 day and 2 hours before the target date (or the due date), asking permission first. Tapping one opens the task. Debug builds get a test button.
- [x] 🔺 **4.2** Reminders stay correct after edits, restarts, and time-zone or daylight-saving changes. ★
  - Code is done and tested on the emulator.
  - Not yet checked on the tablet: a real restart, and whether ColorOS delays or blocks alarms.

### F5: Canvas sign-in (go/no-go) ⚠ needs you
- _Groundwork done:_
  - `canvas/CanvasSignIn.kt` checks the Canvas address (https only) and spots the end of the login (9 tests).
  - `WebViewCanvasCookies` reads the cookie and signs out completely.
  - `data/CanvasSettings.kt` stores the address, "signed in as" name and last-synced time (DataStore).
  - `AppContainer.canvasClient/canvasSync` connects the pieces.
  - **Still to do:** the Settings Canvas section and the in-app browser screen, then your real login test.
- [ ] 🔺 **5.1** Canvas address, then in-app browser login, then "Signed in as …", plus Sign out. You do the real login. If the uni login fails in the in-app browser, stop and discuss the calendar-feed fallback (§3.4). ★

### F6: Canvas import
- [x] 🔺 **6.1** Read-only Canvas client: GET only, reads every page, polite pacing, notices an expired sign-in. Tested against a fake server.
  - `canvas/CanvasParser.kt` handles reading responses (15 tests with made-up sample JSON in `app/src/test/resources/canvas/`).
  - `canvas/CanvasClient.kt` makes the requests (14 MockWebServer tests). It has:
    - the GET-only `ReadOnlyGuard`
    - the cookie sent only to the Canvas host and never logged
    - links to other sites refused
    - a 250 ms pause between pages
    - 3 tries with growing waits when rate-limited
    - 401 or a redirect to `/login` treated as an expired sign-in
    - redirects not followed
    - all-or-nothing lists
  - The real cookie source (WebView `CookieManager`) gets connected in 5.1.
- [ ] **6.2** Import active courses, then the "Link your courses" screen.
  - _Logic done in `CanvasSync`:_ courses are imported and auto-linked to your own course with the same code.
  - **Still to do:** the "Link your courses" screen for Canvas courses that have no folder yet.
- [ ] **6.3** Import assignments (dated, undated and quizzes) with formatted descriptions, "Open in Canvas" and submission status. Debug builds get a fake-Canvas mode. ★

### F7: Sync rules
- [x] 🔺 **7.1** Canvas updates never touch your status, target date or links. Removed assignments are marked, never deleted, and only after a complete fetch.
- [x] 🔺 **7.2** Auto-status moves a task to Submitted: forward only, never over Done, and once per new submission.
  - The rules are in `canvas/CanvasMerge.kt` (17 unit tests).
  - The sync engine is `canvas/CanvasSync.kt` (9 emulator tests against a fake Canvas). It:
    - matches courses by Canvas ID, then by code to link your own course, and otherwise creates a new one
    - reads each course's assignments in full and applies the rules in one transaction per course
    - skips a course that fails to load
    - stops on an expired sign-in
    - leaves inactive courses untouched
- [ ] 🔺 **7.3** Sync on app open (if more than 15 min since the last), on pull-to-refresh, and in the background about every 6 h. Settings shows Last synced and has Sync now. An expired-sign-in banner appears when needed, and new courses prompt you to link them. ★

### F8: Quick-add from a link
_Logic done ahead:_ `domain/SharedLink.kt`, with 9 tests. It reads the link and a suggested title from shared text or subject, tidies punctuation, and finds the Canvas course ID in Canvas links.

**Still to do:** the Android share target (intent filter) and the prefilled New task form.
- [ ] **8.1** Share a link to NoteApp to get a New task with the link (and title or course, if known) filled in. ★

### End of phase
- [ ] 🔺 Run `/code-review`, fix what it finds, retest, update `ISSUES.md`, then ask for approval to merge into `main`.

---

## How the app is structured

```
 Screens (Compose)  ──►  ViewModel per screen  ──►  Repositories  ──►  Room database
      ▲   shows live data from the database ◄──────────────────────────┘   (single source of truth)
 Canvas: in-app sign-in → read-only API client → sync job ──► writes into the database
 Reminders: read tasks from the database → schedule notifications
```

- **Screens** only draw state and pass taps to their ViewModel.
- **Rules** are plain Kotlin with no Android code, and fully unit-tested:
  - overdue
  - sorting
  - reminder times
  - Canvas merge
  - forward-only status
- **Data:** the Room database is the single source of truth. Screens watch it live, and the app works offline. Small settings live in DataStore.
- **Wiring:** a small `AppContainer` connects everything, with no dependency-injection framework.
- **Packages:** `data/`, `domain/`, `canvas/`, `reminders/`, `ui/<screen>/`, `ui/theme/`.

**Database version 1:**
- Course: code, name, and the Canvas ID (optional).
- Folder: name and an optional linked course.
- Task: title, course, due date (optional), target date (optional), description, link, status, and source (mine or Canvas). Canvas tasks also store their Canvas IDs, submission info, and a "removed from Canvas" flag.

Tasks belong to a course, not a folder. Every row has a permanent random ID and a last-changed time. Statuses are stored by name. The schema is exported to `app/schemas/`, and every change gets a tested migration, never delete-and-recreate (IDEA §5a).

## Libraries (approved with this plan)

| Library | Used for |
|---|---|
| Room + KSP | Database, migrations and migration tests |
| Navigation 3 + Material 3 Adaptive | Screen navigation and two-pane tablet layouts |
| Lifecycle / ViewModel | Screen state |
| DataStore | Small settings |
| WorkManager | Background Canvas sync (and Phase 6 backup) |
| OkHttp | Canvas API, GET only |
| kotlinx.serialization | Canvas JSON and navigation keys |
| Test only: MockWebServer, coroutines-test, Room testing | Fake Canvas server, plus database tests |

Built into Android, no extra library: the in-app browser (Canvas login), AlarmManager and notifications (reminders), `java.time`, and Compose's HTML-to-text (Canvas descriptions).

## Needs the user
- **0.1:** the key and its passwords. Done.
- **New screen layouts:** agreeing each one before coding (IDEA §2.10).
- **F5:** the real Canvas login and the go/no-go decision.
- **F6/F7:** checking imports against real Canvas data.
- **If needed:** ColorOS battery and background settings.

## Risks
1. The uni login inside the in-app browser (F5). The fallback is the calendar feed.
2. How long the Canvas sign-in lasts.
3. ColorOS background limits for reminders and sync.
4. NZ daylight saving and reminder times. Covered by tests.

## Testing
- **Every step:**
  - `.\gradlew.bat assembleDebug`
  - `.\gradlew.bat testDebugUnitTest`
- **On the `Pixel_Tablet` emulator:** `.\gradlew.bat connectedDebugAndroidTest` for database, migration and screen tests, plus screenshots in landscape/portrait and light/dark, saved to `screenshots/` (git-ignored).
- **Tablet only:** the real Canvas login, and notifications and background behaviour on ColorOS.

## Technical notes
- **Canvas API** (GET only):
  - Endpoints:
    - `/api/v1/users/self`
    - `/api/v1/courses?enrollment_state=active&per_page=100`
    - `/api/v1/courses/:id/assignments?include[]=submission&per_page=100`
  - Strip the `while(1);` prefix from responses.
  - Follow `Link: rel="next"` for paging.
  - A 401 or a redirect to `/login` means the session has expired.
- **Cookies:**
  - Read from WebView's `CookieManager` at request time; never copied or logged.
  - No HTTP logging.
  - Sign out clears cookies and WebView storage.
  - Tests use `canvas.example.edu`.
- **Reminders:** AlarmManager inexact-while-idle, one unique PendingIntent per task and offset. Reschedule on boot, time or time-zone change, and app update.
- **Times:** stored as UTC instants and shown in local time.
