# Phase 1: Courses & tasks

The approved plan for IDEA.md Phase 1 (approved 2026-11-14), with the checklist ticked as steps pass.
Work happens on branch `phase-1`. ★ marks a check-in with the user (Working-together mode); the branch is pushed after each OK.

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
- [ ] **0.3 Database.** Course, Folder and Task tables, schema export, and migration test setup. *Nothing visible.*
- [ ] **0.4 App shell.** Home with empty Due soon and Folders areas, Settings, navigation, and wide/narrow layouts (the layout is agreed first). ★

### F1: Folders
- [ ] **1.1** Create a folder (name plus optional course code), rename it, or delete it with a confirmation.
- [ ] **1.2** The folder screen: course header, a Notebooks placeholder, a Tasks section, and link/unlink a course. ★

### F2: Tasks
- [ ] **2.1** Add a task: name, course, optional due date and time, optional "My target date", link and notes. Undated tasks go at the bottom.
- [ ] **2.2** Task detail, edit, delete with Undo, and opening the link.
- [ ] **2.3** Status tags and the automatic Overdue badge. ★

### F3: Home screen
- [ ] **3.1** Due soon across all courses, sorted by target-or-due date, with overdue tasks flagged.
- [ ] **3.2** The collapsible "No due date" section, finished tasks, and narrow/portrait polish. ★

### F4: Reminders
- [ ] **4.1** Notifications 1 day and 2 hours before the target date (or the due date), asking permission first. Tapping one opens the task. Debug builds get a test button.
- [ ] **4.2** Reminders stay correct after edits, restarts, and time-zone or daylight-saving changes. ★

### F5: Canvas sign-in (go/no-go) ⚠ needs you
- [ ] **5.1** Canvas address, then in-app browser login, then "Signed in as …", plus Sign out. You do the real login. If the uni login fails in the in-app browser, stop and discuss the calendar-feed fallback (§3.4). ★

### F6: Canvas import
- [ ] **6.1** Read-only Canvas client: GET only, reads every page, polite pacing, notices an expired sign-in. Tested against a fake server.
- [ ] **6.2** Import active courses, then the "Link your courses" screen.
- [ ] **6.3** Import assignments (dated, undated and quizzes) with formatted descriptions, "Open in Canvas" and submission status. Debug builds get a fake-Canvas mode. ★

### F7: Sync rules
- [ ] **7.1** Canvas updates never touch your status, target date or links. Removed assignments are marked, never deleted, and only after a complete fetch.
- [ ] **7.2** Auto-status moves a task to Submitted: forward only, never over Done, and once per new submission.
- [ ] **7.3** Sync on app open (if more than 15 min since the last), on pull-to-refresh, and in the background about every 6 h. Settings shows Last synced and has Sync now. An expired-sign-in banner appears when needed, and new courses prompt you to link them. ★

### F8: Quick-add from a link
- [ ] **8.1** Share a link to NoteApp to get a New task with the link (and title or course, if known) filled in. ★

### End of phase
- [ ] Run `/code-review`, fix what it finds, retest, update `ISSUES.md`, then ask for approval to merge into `main`.

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
