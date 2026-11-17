# Noteapp – Issues & Status

Running list of the project's current state. Updated with every step (see `CLAUDE.md`).

## Known bugs

_None known._

## Open questions

- **Tablet checks for Phase 2 (waiting on you):** writing with the OPPO Pencil 2 (lag, pressure, palm rejection), the pen's side button (does one click switch to the lasso?), and press-hard-to-erase (is 90% the right level?). The emulator can't test pressure or the pen button. Installing a Phase 2 build upgrades the tablet's database to version 4, so it needs your OK.

## Follow-ups / tech debt

- **Open in Canvas (Phase 5 FB11):** not tried with the real Canvas Student app: whether it opens the university's assignment links (else the Chrome tab takes over) needs the tablet.
- **Split view (Phase 5.8):** each notebook opened in a pane keeps its view model until you leave the notebook screen (cheap, but many swaps in one visit keep many notebooks in memory). A real side-by-side split with another app needs the tablet.
- **Bookmark pen (Phase 5 FB5):** deleting a bookmark keeps its picture file (so Undo works), and nothing removes it later yet; a clean-up of unused bookmark pictures belongs with backup (Phase 6). The picture is what's on screen, so a rectangle drawn partly off screen gets only the part on screen.
- **Text boxes (Phase 5.1–5.5):**
  - A box moved or enlarged past the page's edge is cut off there (like pictures); it isn't pulled back onto the page.
  - In an exported PDF, a box with a character the font lacks (emoji, Chinese, a tab) is a picture, so its text can't be selected there.
  - Bold is drawn thicker (the font has no bold of its own).
  - While typing, the lines can sit about one pixel off where the finished box draws them.
  - Android's own pen handwriting-to-text in text fields wasn't tried (the emulator has no stylus handwriting); try writing in a box with the OPPO Pencil.
  - With "Draw with finger" on, a finger tap only opens a box while T is on.

- **PDF text selection (Phase 4.7):** selects whole words (not single letters). Holding and Highlight have screen tests; dragging the knobs was checked by script on the emulator only. Text in a turned direction (e.g. a sideways label) selects as boxes around each word, but Highlight/Underline draw horizontal lines. Feel with a real finger and the OPPO Pencil needs the tablet.
- **Add Page menu (F34):** pages added from a PDF (or the whole PDF) aren't an undo step; delete them with ⋮ → Delete this page. A copied-in PDF nobody used is removed if you cancel the page picker.
- **Pen layer (F30):** our own copy of Jetpack Ink's Compose pen layer (`WetInkLayer`) now feeds the pen; if a future Ink version changes its Compose layer, compare. A finished stroke is drawn by both layers for one frame (invisible for pen; a highlighter may look a touch darker for that frame).
- **Image menu, not checked yet (Phase 3.5):** Take Photo with the emulator's camera; "Allow limited access" (only some photos) and "Don't allow" paths; HEIC photos (the emulator has none). 
- **Share to Noteapp (Phase 3.8):** sharing several pictures at once (SEND_MULTIPLE) is built but not tested by script on the emulator (adb can't send a list); try it from the Gallery on the tablet. Shares over 50 pictures keep the first 50.
- **Screenshot tool (Phase 3.9):** a true side-by-side split with Chrome wasn't set up on the emulator (only multi-window mode); try it on the tablet. The capture includes Android's own red "sharing" chip in the status bar; crop it out. On ColorOS the prompt may look different.
- **Export (Phase 3.7):** PDF pages with a `/Rotate` or a crop box offset aren't tested; ink is placed using the crop box. The app is about 9 MB bigger (debug) with PdfBox; check the release size.
- **Pictures while selected (Phase 3.6):** a selected picture is drawn above the ink until it's let go (it's in the selection layer); the rotate handle turns pictures too but a non-square picture turned and then stretched sideways is only approximately right (the stretch follows the box, not the picture's own axes).
- **PDF pages, things not checked yet (Phase 3.3):** the sharp deep-zoom part (`pdfDetail`) needs a two-finger pinch, which the emulator scripts can't do; check on the tablet. Jumping far with the page scroller shows the page white for a moment before its picture arrives (plain notebooks also have no preview that far away). PDFs with rotated pages (`/Rotate`) aren't tested. Android 15+ can open password-protected PDFs with a password; we just refuse them for now.
- **Imported PDFs and images are never deleted (Phase 3 decision):** a notebook's `pdf-*.pdf` and `images/*` files stay even when their pages or images are deleted, so Undo and Phase 6's "Recently deleted" can bring them back. Clean-up of files nothing uses is a Phase 6/7 job.
- **Steady pen width vs the pressure pen (F22):** at the same Thickness, the Off/Low/Medium pens may look thinner or thicker than the Full pen at your usual pressure (the emulator only has full pressure). Tune the multipliers in `InkBrushes.roundPen` after trying it on the tablet; changing them changes how existing strokes of those kinds look, so give a new key if notes already use them.
- **Notebook editor, small things to revisit (Phase 2):**
  - Ink still being written shows on top of the toolbar if you write under it; it drops behind once you lift the pen.
  - Stability smooths a stroke when you lift the pen, so a wobbly line shifts slightly at that moment. It's off (0%) by default.
  - Hold-to-straighten snaps the line after you lift the pen, not while you're still holding.
  - Panning stops when your finger lifts (no "fling").
  - Moving a large lasso selection pauses briefly when you let go (the moved strokes are rebuilt).
  - Every page of a notebook is loaded into memory when it opens (fine for 50 pages; revisit for very long notebooks).
  - A full page appears in a few quick steps the first time it scrolls into view.
  - Zoomed in very far (a page bigger than about 4,000 pixels on screen), pages are no longer kept as a ready-made picture, so scrolling there is slower (about the 50 ms a frame of before). Not measured, because the emulator can't pinch from a script. Ideas: skip ink that's off screen, or keep only the visible part as a picture.
  - The next page isn't drawn ahead of time; it appears as it scrolls in.
  - Quick page previews are kept for pages within 12 of the screen. A very long flick past that shows plain paper for a moment.
  - Press-and-hold to erase can't be checked on the emulator (its pen pressure is always 100%); it needs the tablet.
  - The copy/paste clipboard lasts while the notebook is open; it doesn't carry over to other notebooks or apps.
  - When a straight line snaps while the pen is held perfectly still, the wobbly line can stay visible until the pen moves or lifts (some pens send nothing while still).
  - If a page can't be saved at all (e.g. storage full), Noteapp tries 4 times over about 8 seconds, then keeps the page marked unsaved and says so. If that happens just as you leave the notebook, the last changes can still be lost once the notebook is closed. A fuller fix would keep unsaved pages app-wide and retry later; revisit with Phase 6 backup.
  - Jetpack Ink adds about 7 MB to the app (its drawing code for every kind of processor). A release build for one device type would be smaller; not worth doing now.

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
- **For Phase 6 "Recently deleted":** deleting a folder only checks for notebooks that aren't deleted, so notebooks deleted earlier stay in the database with no folder (their pages and files are kept). "Recently deleted" must list these and offer somewhere to restore them.
- **For Phase 6 backup:** Room keeps recent changes in a side file (`noteapp.db-wal`) next to `noteapp.db`. The backup must save them into the main file first (a "checkpoint") or copy all three files together. Otherwise a backup could miss the latest edits.

## Done

- **Second Phase 5 review (17 Nov), merged into `main` with the user's OK (`3ecda09`):** 8 findings fixed (list in PHASE-5.md): a drop near an edge no longer shows the old layout first, the Chrome tab opens over Noteapp, the divider handle takes less of the pages, no drop zone over the panel, plus tidying and 7 new screen tests. Those tests found a ninth: a narrow course folder could crash as its notes loaded (fixed).

- **Phase 5 merged into `main`** (17 Nov, `09c1449`): text boxes, the bookmark pen (database v7), split view, the recent notebooks panel, feedback rounds FB1–FB15. On the tablet; 273 emulator tests pass.

- **Phase 4 built (16 Nov, hands-off), steps 4.1–4.10:** links between tasks and notebooks, page bookmarks, PDF text selection, icon menus, card chips. End-of-phase review: 10 findings, all fixed (list in PHASE-4.md). Installed on the tablet 16 Nov (v5 → v6, compare PASS) and merged into `main` (`aa342b5`).
- **Phase 4 step 4.1, database version 6 (16 Nov, hands-off):** two new, empty tables for task ↔ notebook links and page bookmarks. Deleting a task removes its links and bookmarks, and Undo brings them back. Real v5 → v6 upgrade on the emulator: every row and page file unchanged. Nothing visible yet.
- **Phase 3 merged into `main` (16 Nov, `7de0c06`).** Phase 4 planned the same day (`PHASE-4.md`): links, page bookmarks, PDF text selection, icon menus.
- **IDEA.md §2.7 and §5 (16 Nov):** snip noted as built in Phase 3 (user's OK).
- **F36 (16 Nov):** a stroke written across the edge between two pages is no longer cut off: it's split at the edge, one piece per page (one undo step). Strokes written before the fix stay cut.
- **End-of-phase review, Phase 3 (16 Nov):** 10 findings fixed (details in PHASE-3.md): crop crash on very long screenshots, a finger move lost on pinch, export of PDF pages that have their own rotation, a stale "PDF" tag, a share lost on rotation, the pen layer waiting on an unready page, the fresh last page after a split stroke, recent photos before Android 11, F35's corners (pictures only), and tests for the pen handoff.
- **F35 (16 Nov):** in resize mode the corner handles keep a picture's shape; the side handles still stretch. Writing alone keeps free corners in resize mode, as in Phase 2.
- **Tablet feedback batch 1 (Phase 3, F30–F34), 16 Nov.** No flash when the pen lifts; hold a picture to select it; pictures select in resize mode and drag with a finger; Crop for pictures; a second ＋ "Add Page" menu (page style, photos, camera, pages from a PDF, a whole PDF).
- **Phase 3 step 3.8 (16 Nov):** Share to Noteapp (layout OK'd by the user): share or "Open with" a PDF → pick a folder; pictures → add to a notebook (new pages at the end) or a new notebook in a folder.
- **Phase 3 step 3.9 (16 Nov):** the screenshot tool (crop button): snip the notebook, or the whole screen in split screen, and insert it. Fixed on the way: a crash when confirming (Compose gives a hardware bitmap).
- **Phase 3 step 3.7 (16 Nov):** Export as PDF (⋮ → Export as PDF → Share… or Save to device).
- **Phase 3 step 3.6 (16 Nov):** the lasso selects pictures (loop or tap) to move, resize, turn, copy, cut, paste, duplicate and delete, all undoable.
- **Phase 3 step 3.5 (16 Nov):** Insert image: the image menu (Recent Images, Photo Library, Take Photo); pictures sit under the ink and are saved with the page.
- **Phase 3 steps 3.2–3.4 (16 Nov):** page files can hold pictures (format 2, only when a page has one).
- **Phase 3 steps 3.2–3.3 (16 Nov):** pages of different sizes; Import PDF (folder ＋ → Import PDF), drawn with Android's PdfRenderer, with a "PDF" tag and a PDFs filter.
- **Phase 3 step 3.1, database version 5 (16 Nov):** pages got their own size and PDF page columns (empty for every existing page). Real v4 → v5 upgrade on the emulator: every row unchanged, all page files byte-identical; a deliberately broken migration is caught by `MigrationTest`.
- **Phase 2 second end-of-phase review (16 Nov):** 6 of 7 findings fixed, incl. Back now closes the tool panel (then the lasso selection) before leaving the notebook. All 183 emulator tests pass.
- **Tablet feedback, batch 7 (Phase 2, F26–F28), 16 Nov.** A hard press held perfectly still now erases; the pen's settings panel closes when the pen touches the page; colour-wheel colours can be deleted or added to the toolbar.
- **Tablet feedback, batch 6 (Phase 2, F21–F25), 16 Nov.** Highlighter opacity; pen pressure (Off/Low/Medium/Full, Off = same width); toolbar colours can be changed, reordered, deleted and added; tapping the colour in use opens the colour wheel; darker desk.
- **Page scroller (Phase 2, F20), 16 Nov.** The page number became a draggable handle on a bottom track that scrolls through all pages; it fades when the pages stop moving.
- **Tablet feedback, batch 5 (Phase 2, F17–F19), 16 Nov.** No top bar: the buttons float over the pages, which reach up to the status bar and fill the height when fitted. Highlighter has Flat tip and Round tip (new ink kind `highlighter-round-v1`). The lasso picks strokes that are only partly inside the loop.
- **Tablet feedback, batch 4 (Phase 2, F13–F16), 16 Nov.** Writing on the last page adds an empty page after it (not an undo step). The lasso's Resize mode has a rotate handle above the box (snaps to quarter turns). No Done button: a finger tap outside the selection, or the pen outside it, lets it go. Fixed: small pages could stay blurry after a fast scroll until zoomed or touched (page readiness wasn't Compose state, so a waiting page never redrew). New unit tests (rotation, page readiness).
- **Tablet feedback, batches 2 and 3 (Phase 2, F4–F12), 16 Nov.** Pages touch with a thin line between them; a dark Notein-style top bar of buttons only; finger flicks coast through pages and stick to sideways; double-tap glides to fit; far pages show a soft preview while scrolling. Pens: press hard *and hold* to erase (hold time in settings, "how hard" up to 100%); a colour wheel with a hex box (picked colours remembered); hold still mid-line to snap a straight line whose end you can still move, with gentle angle snapping. Lasso menu: Resize (handles on every corner and edge), Duplicate, Copy, Cut, Delete, Done; a finger held on a page pastes. New unit tests (fling, axis lock, press-hold, angle snap, colour hex, lasso box) and an emulator flick test.

- **Tablet feedback, batch 1 (Phase 2, F1–F3), 15 Nov.** Zoomed-in pages no longer draw over the top bar. The OPPO Pencil 2's side button now toggles the lasso (the pen sends it as a Bluetooth key, F21, which the app didn't listen for). Press hard to erase now also works on the very hardest presses (a full 100% reading was ignored).

- **End-of-phase review fixes (Phase 2), 15 Nov.** A `/code-review` of the whole phase found 8 things; 7 fixed:
  - a page that fails to save is now tried again a few times, even after you leave the notebook (new emulator test)
  - a database error while noting a save can no longer crash the app
  - the pen's eraser end, adding a page and deleting a page now end a lasso selection (before, erased strokes could stay visible in the selection, or its box showed over the wrong page)
  - highlighting no longer redraws the whole page picture on every stroke
  - a newly added page can't lose a stroke written on it in the first instant
  - garbled characters in the database-upgrade notes (`Migrations.kt`) repaired

  The 8th (deleted notebooks left without a folder) is kept for Phase 6; see Follow-ups.

- **A long notebook scrolls smoothly (Phase 2, step 2.12), 15 Nov.** On the emulator, with two full pages on screen (about 1,800 strokes), a frame went from about 150 ms (up to 1.3 s) to a median of 19 ms, with the graphics work down from 13 ms to 1 ms. How: saving a full page is 10× faster, strokes are prepared in the background, the ink is drawn in reusable groups of 32 strokes, and each page is kept on the graphics card as a ready-made picture that scrolling just moves. Writing, erasing, the lasso, undo and rotating still look right. Also fixed: **rotating the tablet sent the editor back to page 1**; it now stays on the same page. Not yet tried on the tablet.

- **Handwriting in notebooks (Phase 2, steps 2.4–2.10), 15 Nov.** Notebooks open on a desk with pages side by side; a double-tap fits the page (fit height, or fit width / whole page in Settings → Notebooks). The pen writes with low lag (Google's Jetpack Ink library); a finger never writes but scrolls, pinches to zoom and double-taps. The thin floating toolbar has 3 pens, a see-through highlighter (drawn under the writing), an eraser (whole strokes), the lasso (circle, move, resize, delete), 8 colours plus more, 3 sizes plus a slider, Stability, hold-to-straighten and press-hard-to-erase; drag it by its handle or tuck it away. Undo and redo cover drawing, erasing, the lasso and adding or deleting pages. Pages save themselves within half a second, when you leave and when the app goes to the background; each page is its own file, written so a crash can't leave half a page. 63 new unit tests and 6 editor tests on the emulator.

- **Subfolders and notebooks in folders (Phase 2, steps 2.2–2.3), 15 Nov.** Inside a folder, a grid of its folders and notebooks sits above the tasks, with an All · Notebooks · Folders filter. The ＋ button makes a new notebook (name and paper: Lined, Blank, Grid or Dot grid) or a folder. Folders can go inside folders as deep as you like; they show the course colour and tag, and "in ELECTENG 292 › tutorials" at the top. Each tile's ⋮ has Rename and Delete; deleting a notebook offers Undo, and a folder can only be deleted when it's empty. A new notebook opens straight away, showing its blank pages (writing comes next). 7 new screen tests.

- **Database version 4: subfolders and notebooks (Phase 2, step 2.1 🔺), 15 Nov.** Folders can now sit inside other folders, and the database has notebooks and pages (the handwriting itself will be saved in one file per page, step 2.5). Updating the app only adds an empty column and two new, empty tables: every course, folder and task is kept exactly. Deleting is safe: a folder that holds anything can't be deleted, and deleting a notebook or page only hides it. Checked with migration tests, a deliberately broken migration (caught, and the failed update left the old data untouched), and a real upgrade of the previous app with data on the emulator. Nothing visible changes yet, except that deleting a non-empty folder now explains why it can't. 11 unit tests, 15 emulator tests.

- **Code review of phase-1b (15 Nov): 7 of 8 findings fixed.**
  - The Calendar's day panel slides away again.
  - Typing an archived course's code in New folder brings that course back.
  - Switching the picked course updates a name that was filled in automatically.
  - Swipes always use the latest callbacks.
  - Short timeline bars stay inside the grid.
  - A course filter on an archived course is cleared for good.
  - The course label is shared.
  - Skipped: Home keeps a tiny course query running (loading it only when the dialog opens would make the dialog flicker).

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
