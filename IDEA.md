# NoteApp — Idea & Build Plan

> Working name: **NoteApp** (may change if I share it later).
> A handwriting-first note-taking app for Android tablets — similar in feel to **Notein** — with built-in **Canvas assignment tracking**, so each course folder shows my notes *and* what's due.

---

## 1. Who it's for & where it runs

- **Just me for now**, but it should work well on **most Android tablets**, not only mine.
- My device: **OPPO Pad 3** with **OPPO Pencil 2** (test everything here first).
- Tablet-first layouts; support both **landscape and portrait**, and different screen sizes.
- Stack: **Kotlin + Jetpack Compose**, minSdk 26 (adjust if a key library needs higher).

---

## 2. Core features

### 2.1 Course folders
- Folders for organising notebooks; a folder can be **linked to a course** (e.g. `ELECTENG 204`).
- Opening a course folder shows **two sections**: my **notebooks/files**, and **upcoming tasks** for that course.
- When tasks are imported, the app detects the course code and offers to link it to an existing folder or create one.
- **Tabs in a course folder:** Tasks · Calendar · Timeline (added Nov 2026). Calendar and Timeline are the Home views showing only that course.
- **Subfolders** (added Nov 2026):
  - Folders can hold other folders, e.g. ELECTENG 292 → tutorials, practice, slides, as I do in Notein.
  - Inside a folder, like Notein: a filter row (All · PDF · Note · Folder) and a grid of thumbnails for subfolders, notebooks and PDFs.
  - See `design-reference/notein-inside-course-folder.jpg` and `inside folder docs and other sub folders.jpg`.
- **New folder picks the course from a list:** a drop-down of my current (not archived) courses, instead of typing the course code (added Nov 2026).
- **Archiving courses** (added Nov 2026; about 4 new courses each semester):
  - Archive a course from its folder's ⋮ menu, from "Link your Canvas courses", or from Settings → Courses (which also covers courses without a folder).
  - An archived course's tasks disappear from Due soon, Calendar and Timeline, and its reminders stop. Nothing is deleted, and its folder still shows everything.
  - Archived folders sit in a collapsed "Archived (N)" row under Folders, and archived courses are never suggested again.

### 2.2 Home screen
- Split layout: **"Due soon"** list (all courses, sorted by due date) on one side, **course folders** on the other.
- Overdue items clearly flagged.
- **Tabs at the top: Overview · Calendar · Timeline** (added Nov 2026, inspired by my old Notion Canvas tracker):
  - **Overview:** the split layout above.
  - **Calendar:** a month grid with each task on its due date (time, course tag, status), plus Today and previous/next month.
  - **Timeline:** one row per task, with a bar from when Canvas makes the assignment available (or when I added the task) to its due date. My target date is marked on the bar, and there's a line for today.
- **Days left:** every dated task shows how long is left until the real due date ("Due today", "Due tomorrow", "3 days left", "2 days overdue").
- **Course colours:** each course gets a steady colour, used for its tags everywhere. I can choose a course's colour myself (Phase 7).

### 2.3 Tasks (Canvas items + my own)
- **Canvas items** are imported automatically by **signing in to Canvas inside the app** (see §3) — this includes undated assignments and descriptions.
- **My own tasks** (e.g. "Study for Test 2") can be added manually — they behave the same as Canvas items.
- Each task shows:
  - Name
  - Course
  - Due date/time
  - Description (if Canvas provides one)
  - **Link to open it in Canvas** (Canvas items)
  - **Status tag**
  - **Linked notebooks/files**
- **Status tags (fixed list):**
  `Not started` → `Started` → `Stuck / need help` → `Double-checking` → `Done`
  - Submitted and Done were merged into one, `Done` (Nov 2026): they mean the same thing. Custom tags were dropped.
  - **Overdue** is shown automatically (a badge, not a tag) when the due date has passed and the status isn't Done.
- **Reminders:** notification before a task is due (e.g. 1 day and 2 hours before — configurable later).
- **Tasks without a due date:**
  - Due date is optional for every task.
  - Undated tasks go in a **"No due date"** section — at the bottom of each course folder's task list, and as a collapsible section on the home screen (below "Due soon").
  - Any task can have an optional **"My target date"** (my own do-by date), separate from the Canvas due date. If set, it's used for sorting and reminders, and shown alongside the real due date.
  - No reminders for undated tasks unless a target date is set.
  - **Quick-add from a link:** share any link (e.g. a Canvas page, a course website) to NoteApp → creates a task with the link pre-filled; I fill in the name/course.

### 2.4 Linking notes ↔ tasks
- Link any notebook or file to one or more tasks.
- From a task: see and open its linked notes.
- From a notebook: see which task(s) it belongs to.
- **Page bookmarks** (added Nov 2026): bookmark a page of a notebook for a task, with an optional quick note. A bookmark made from selected PDF text gets that text as its note, and the note can be changed later. Under the task, bookmarks are grouped by notebook and ordered by page number (smallest first); tapping one opens that page, zoomed to the spot.

### 2.5 Notebooks (handwriting)
- **Handwriting first**; **text boxes** added in a later phase.
- **Page templates:** **Lined (default)**, Blank, Grid, Dot grid. Choose per notebook (and per page later).
- **Tools (keep it simple, like Notein's essentials):**
  - Pen (colour + thickness, pressure-sensitive)
  - Highlighter
  - Eraser (whole-stroke first; partial eraser later)
  - Lasso select → move / resize / delete
  - Undo / redo
  - Insert image
- **Pen toolbar:** **small, floating and movable** (drag it anywhere; remembers its position). A bit thinner than Notein's, and it can collapse (added Nov 2026).
- **Pen button** (added Nov 2026): one click of the stylus side button switches to lasso; another click goes back to the tool I was using.
- **Shapes** (added Nov 2026): draw a rectangle/square, circle/ellipse or triangle and hold the pen still at the end: it snaps to a clean shape (a square or circle when nearly so). Still holding, move the pen to resize it from the corner you stopped at (the opposite corner stays). Lasso one straight line or shape to drag its points (a line's ends, a shape's corners, an ellipse's sides).
- **Press hard to erase** (added Nov 2026): pressing the pen tip harder than a set level erases instead of drawing, with a sensitivity setting and an off switch.
- **Stylus vs finger:** stylus draws, finger scrolls/zooms (palm rejection).
- Pinch to zoom, scroll between pages (side by side, like Notein), add/delete pages.
- **Double-tap to fit** (added Nov 2026): a double-tap fits the page to the screen. Default: fit height; fit width or whole page can be chosen in Settings.
- **Autosave** — never lose strokes.

### 2.6 Files: PDFs, images, Word/PowerPoint
- **PDF:** import and write/highlight on top; export the annotated PDF.
- **Selecting PDF text** (added Nov 2026): hold a finger on a PDF's text to select it and drag the handles to select more; a small icon menu offers Copy, Highlight, Underline and Bookmark (to a task). Scanned pages have no text to select.
- **Images:** JPG, PNG, WebP, HEIC — insert into a page or import as their own page.
- **Word (.docx) & PowerPoint (.pptx):** converted to PDF first, then annotated like a PDF (see §4 — conversion is done through Google Drive).
- **"Share to NoteApp":** from any other app (Canvas, browser, Files), share a PDF/image straight into a chosen folder.

### 2.7 Screen capture ("snip") into a note
- **Built in Phase 3** (moved from Phase 5 in Nov 2026): capture → crop → insert, alone or in split screen. Snip between NoteApp's own split notebooks stays in Phase 5.
- **Capture the whole screen, including other apps** — e.g. NoteApp on one side of Android split screen and a PDF/browser/Canvas on the other: capture → **drag a box to crop the part I want** → it's inserted into my open notebook as an image (move/resize it like any image).
- **Two notebooks open in NoteApp's own split view:** snip from one notebook and drop it into the other. Ask which notebook to insert into (default: the one I was last writing in).
- Android's screen-capture permission prompt is fine with me — accept that it may appear each time.
- Note: some apps block screenshots (e.g. banking apps, some protected PDFs/streaming) — those areas come out black. Nothing can be done about that; just show a helpful message if the capture is all black.
- Extra: "Insert latest screenshot" (one tap to insert the most recent Android screenshot) as a quick alternative.

### 2.8 Split view
- Open **two notebooks side by side** inside the app (e.g. lecture notes + tutorial sheet).
- Also behave well in Android's own split-screen with other apps.
- **Recent notebooks panel** (added Nov 2026): a button next to Back opens a panel on the left with the recently opened notebooks as thumbnails. Tap one to open it; **drag one onto the page** to open it in split view beside the current notebook.
- **Two split modes:** side by side and top and bottom. The divider has a small ⋮ handle: tap it for Swap sides, Side by side / Top and bottom, and Close split screen; drag it to resize, and letting go very near an edge closes the squeezed notebook.

### 2.9 Backup to Google Drive
Modelled on GoodNotes' auto-backup (one-way backup to Drive whenever something changes, saved in both the app's own format and PDF). Main goal: **if the tablet dies or I get a new one, I can restore everything.** Priority is **reliability** over fancy features.

**What's backed up — two copies, both automatic:**
1. **Full backup (for restoring):** everything needed to rebuild the app exactly — notebooks with editable strokes, imported PDFs/images, folders, course links, tasks (status tags, target dates, links to notes), and settings.
2. **Readable PDF copies (for viewing anywhere):** each notebook exported as a PDF, in Drive folders that mirror my course folders. View-only — the full backup is what restores.

**Never backed up:** Canvas session cookies / feed link (I just sign in to Canvas again after restoring).

**Drive layout (a normal, visible folder):**
```
My Drive/Noteapp/
  Backup/      ← full backup (app format, complete snapshots) — don't touch
  Notes (PDF)/
    ELECTENG 204/  Lecture notes.pdf, Tutorial 5.pdf …
    ELECTENG 292/  …
```

**When it backs up:**
- Automatically, **a few minutes after I stop editing** a notebook (not on every stroke), plus a daily check that everything is up to date.
- Only uploads what **changed** (an unchanged 20 MB lecture PDF isn't re-uploaded every time).
- If offline, waits and retries automatically when back online. Wi-Fi only by default.

**Reliability features:**
- **Backup status** in Settings and a small indicator: "Last backed up 3 min ago" / "Backing up…" / "Backup failed — tap to fix".
- **Warning** if nothing has backed up successfully for 3+ days.
- **"Back up now"** button.
- Backups never contain half-written files — upload complete files only, so a backup interrupted halfway can't corrupt the previous good one.

**Restore (new or reset tablet):**
- Install → sign in to Google → **"Restore from Google Drive"** → shows backup date and size → restores everything → I sign in to Canvas again.

**Deleted items & older versions:**
- **Recently deleted** (in-app, 30 days): deleted notebooks/files can be recovered; their backup isn't removed from Drive until the 30 days are up.
- **Older versions (decided 17 Nov 2026):** each backup is a complete snapshot (a list of files written last, so an interrupted backup can't spoil the previous one). Drive keeps the newest snapshot plus one per day for the last 7 days, and Restore lists them. Later phase: a **"Version history"** option on a notebook can reuse these snapshots.

**Storage (rough estimate):** handwriting is small — a typical 50-page notebook is likely a few MB. Imported PDFs (lecture slides) are usually the biggest items. A full semester should fit comfortably in Google's free 15 GB, even with old versions included. Show total backup size in Settings.

### 2.10 Look & feel (UI)
- **Simple and clean.** Layout and general feel **inspired by Notein** (the app I currently use), then improved where it makes sense.
- Reference screenshots of Notein are in `design-reference/` (home/library, notebook editor, toolbar, pen settings). Use them for **layout and feel only** — don't copy Notein's logo, icons, name or artwork; use standard Material icons and our own styling.
- Lots of space for writing — the page is the focus; toolbars small and out of the way.
- Light and dark mode.
- Consistent look across all screens (same colours, spacing, corner rounding, icon style).
- When building a new screen, describe the layout to me first (or ask which reference screenshot to follow) before coding it.

### 2.11 Search (added Nov 2026)
- **One search from Home** that finds notebook, folder and course names, tasks (title, description, my notes), words in imported PDFs, text in text boxes, and **my handwriting** (English, recognised on the tablet itself; nothing is sent anywhere).
- Tapping a result opens the notebook at that page with the match highlighted.
- Inside a notebook: **Find in this notebook**.

---

## 3. Canvas import — how it works

I can't get a Canvas API token, but I've tested that the Canvas API **works with a normal logged-in browser session** (`<canvas address>/api/v1/courses` returns JSON when I'm logged in). So the app signs in to Canvas like a browser and reads my data **as me**.

### 3.1 Sign-in
- **Settings → "Sign in to Canvas"**: I enter my Canvas address (e.g. `https://canvas.auckland.ac.nz`) — keep this configurable so it works with any Canvas school.
- The app opens an **in-app browser (WebView)** showing the normal Canvas/university login. I log in as usual.
- Once logged in, the app uses that browser session's cookies to call the Canvas API.
- **First thing to test in this phase:** that the university login page works inside the in-app browser (some login pages block in-app browsers). If it doesn't, fall back to the calendar feed (§3.4).

### 3.2 What gets imported
- My **active courses** → offer to link each to a course folder (or create one).
- For each course, **all assignments** (dated and undated, quizzes included): name, due date, description, Canvas link.
- **Submission status** from Canvas, where available.
- Handle Canvas paging (results come in pages) so nothing is missed.
- Descriptions come as HTML → display them nicely (formatted or cleaned up), not as raw tags.

### 3.3 Syncing rules
- Sync when the app opens, on pull-to-refresh, and in the background a few times a day.
- Changes on Canvas (e.g. new due date, edited description) update the task **without** resetting my status tag, target date, or linked notes.
- **Auto-status:** if Canvas shows an assignment as submitted, move it to `Done` — but only move it **forward**, never backwards (never override `Done`, never undo my own choice).
- Assignments removed from Canvas are **not** deleted — mark them "removed from Canvas".
- **When the session expires:** keep all existing data, show a small "Canvas sign-in expired — sign in again" banner, and keep working offline.

### 3.4 Fallback: calendar feed (optional)
- If in-app sign-in stops working, I can paste my Canvas **calendar feed** link (Canvas → Calendar → "Calendar Feed") in Settings instead.
- Limitation: the feed only includes items **with a date**, and no submission status.

### 3.5 Security & safety rules (important)
- The app is **read-only** with Canvas: only `GET` requests. **Never** submit, edit, delete or post anything on Canvas.
- The Canvas session cookies (and feed link, if used) work like **passwords**: never hard-code, log, print, back up to Drive, or commit them to git. Keep them only in the app's private storage.
- "Sign out of Canvas" in Settings clears the session completely.
- Don't hammer the server: sensible sync frequency, no rapid repeated requests.

---

## 4. Google Drive integration (notes for later phases)

- Uses Google sign-in + the Google Drive API (free). Needs a (free) Google Cloud project — guide me through setting it up step by step when we get there.
- Use the narrowest permission (only files the app itself creates) so no special Google verification is needed for personal use.
- Check whether the Google Cloud project needs to be set to **"In production"** rather than "Testing" — in testing mode, sign-ins can expire after about 7 days, which would silently break automatic backup.
- Background backup uses Android's background job system so it survives the app being closed and the tablet restarting.
- Drive can also **convert Word/PowerPoint → PDF** (upload with conversion, then export as PDF). This is how .docx/.pptx support works, so it needs internet.
- Offline fallback for Word/PowerPoint: open the file in another app and "Print → Save as PDF", then share to NoteApp.

---

## 5. Build phases

Build one phase at a time; each phase should leave a working app. Commit after every working step.

| Phase | What gets built |
|---|---|
| **1. Courses & tasks** | Folders, manual tasks, status tags, home screen (Due soon + No due date + folders), task detail, reminders → then **Canvas sign-in + sync** (test the in-app login first), course linking, auto-status, quick-add from a link |
| **1b. Task views** | Days left on tasks, automatic course colours, Canvas "available from" date (database v2), home tabs, Calendar (month), Timeline |
| **2. Handwriting notebooks** | **Subfolders** (folder-in-folder, done together with the notebook storage changes), notebook/page model, ink canvas with pen/highlighter/eraser, undo/redo, page templates (lined default), floating movable toolbar, zoom/scroll, palm rejection, autosave, **lasso select** (moved here from Phase 5, Nov 2026), pen button → lasso, press hard to erase, double-tap to fit |
| **3. Images & PDFs** | Insert images, import PDFs and annotate, export annotated PDF, "Share to NoteApp", **screen capture/snip** (whole screen incl. other apps → crop → insert; moved here from Phase 5, Nov 2026) |
| **4. Linking** | Link notebooks/files ↔ tasks; course folder view showing notes + tasks together; **page bookmarks for tasks** (with quick notes) and **selecting PDF text** with a finger (Copy, Highlight, Underline, Bookmark), both added Nov 2026 |
| **5. Productivity extras** | Text boxes, in-app split view of two notebooks (side by side or top and bottom, divider menu), **recent notebooks panel** with drag-to-split (added Nov 2026), snip between split notebooks |
| **6. Google Drive & backup** | ⚠️ Must be done and tested before I use the app for real. Google sign-in, automatic full backup + restore, manual "Export everything" / "Import from export", PDF copies mirroring course folders, backup status/warnings, Recently deleted, Word/PowerPoint conversion → later: Version history |
| **7. Polish** | **Search** (names, tasks, PDF text, text boxes, handwriting), partial eraser, **shapes** (hold to snap a rectangle, circle or triangle; lasso a line or shape to edit its points), Submitted merged into Done (replaces custom tags, Nov 2026), per-page paper, settings (reminder times, course colours, default paper, light/dark), performance tuning, backup follow-ups from Phase 6 |

---

## 5a. Important rules for protecting my data

I won't use the app for real notes until uni starts next year, but **backup and data safety are key features**. Google Drive backup (Phase 6) **must be finished and tested before I start using the app for real**. These rules apply from Phase 1:

- **Never lose data on updates.** Whenever the database/storage structure changes, write a proper **migration** that keeps existing data. Never use "delete and recreate the database" shortcuts. Store a format/version number with saved data.
- **Design storage with backup in mind from the start**: notebook strokes, imported files and database should be easy to copy, back up, restore and export to PDF later (Phase 6), and support "only upload what changed".
- **Manual export too:** alongside automatic backup, have **"Export everything"** (one .zip I can save anywhere) and **"Import from export"** — a second safety net that doesn't depend on Google.
- **App signing key:** set up a proper **release signing key** early (before I rely on the app). If the signing key changes (e.g. new computer, reinstalled Android Studio), Android refuses to update the app and I'd have to uninstall it, which **deletes all my notes**. The key must be **backed up somewhere safe outside the project and never committed to git**. (The same key is also needed later for Google sign-in.) Walk me through this.
- Never tell me to uninstall the app or "clear app data" to fix a problem without warning me that it deletes my notes.

---

## 6. How I want to work with Claude Code

- I don't need to understand the code in detail. After each step, give a **short, high-level summary**: what was built, and anything that changes **what I see or how I use the app** (screens, buttons, gestures, behaviour). Skip code-level explanations unless I ask.
- Tell me exactly **how to test** each step on the tablet.
- Small steps; build with Gradle after every change and fix errors before finishing.
- Test on my OPPO Pad 3 regularly, but don't use OPPO-only features — standard Android stylus APIs so it works on other tablets.
- Ask me before adding big new libraries or changing the plan.

---

## 7. Still to decide

- App name (if I share it).
- Whether to add other backup providers (e.g. OneDrive) later — Google Drive only for now.
- More feature ideas to come.
