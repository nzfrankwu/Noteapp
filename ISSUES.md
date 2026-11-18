# Noteapp – Issues & Status

Running list of the project's current state. Updated with every step (see `CLAUDE.md`).

## Known bugs

None known right now.

## Open questions

- **Launch (18 Nov, the user: "i think we are ready to launch") — your decisions:**
  - **Privacy page:** publish the handwriting-search line (`site/privacy.html`) to the website (`gh-pages`).
  - **Google Play:** a Play Console developer account is yours to create (one-time fee, identity check). New personal accounts currently have to run a closed test with at least 12 testers for 14 days before a public release. Then the store listing (name, description, screenshots, icon), content rating and the Data safety form.
  - **Photo permission:** the image menu's Recent Images grid uses READ_MEDIA_IMAGES. Google Play only allows that for apps whose main purpose needs all photos; a note app is usually told to use Android's photo picker instead. Options: drop the grid (use the picker only), or ask Google and risk a rejection.
  - **Name:** "Noteapp" (IDEA.md §7 still lists the public name as undecided).
  - **Screen capture service:** Play asks for a short declaration (with a video) for the "media projection" foreground service.
  - **Android developer verification (researched 18 Nov):** Google now requires apps on certified Android devices to come from a verified developer, even APKs downloaded from GitHub. It started in Brazil, Indonesia, Singapore and Thailand (30 Sep 2026); worldwide is planned for 2027. Until then the GitHub APK installs as usual; after that, unverified APKs need an "advanced" install flow. Registering (identity check in the Android Developer Console, or the Play Console account) avoids it.

- **Handwriting corrections (later, your call):** a screen to see what Noteapp read on a page and fix words, remembered for search. Only worth it if search still misses your writing after the messy-handwriting changes.

- **Phase 7, waiting on you (18 Nov, hands-off stop):**
  - **7.11 editor speed** skipped in hands-off: it changes how pages draw their ink and can't be measured on the emulator (deep zoom needs a pinch; the lasso pause needs the real pen). To do together with the tablet.

- **Tablet checks for Phase 2 (waiting on you):** writing with the OPPO Pencil 2 (lag, pressure, palm rejection), the pen's side button (does one click switch to the lasso?), and press-hard-to-erase (is 90% the right level?). The emulator can't test pressure or the pen button. Installing a Phase 2 build upgrades the tablet's database to version 4, so it needs your OK.

- **A Home nudge when Drive isn't connected? (your call):** today Home only warns once Drive is connected and backups fail or stop for 3 days. Nothing on Home reminds you to connect Drive in the first place.

## Follow-ups / tech debt

- **Shapes:** no automated screen test for the gestures (test-injected pen moves don't reach the pen layer); checked by script on the emulator. A finger can't drag a shape's points yet (the pen can). A turned (rotated) ellipse isn't recognised as editable after turning it with the lasso (still moves, resizes and turns as writing).

- **Forgiving handwriting search scales with the notes:** the pages' words are now kept in memory between key presses (10 s), but each search still compares every handwritten word (fine for a few thousand pages; revisit if search gets slow).

- **Search (7.9–7.10):** a text box or handwriting match zooms to it but doesn't mark the words; scanned PDF pages have no text. The first pass over many big PDFs and many handwritten pages runs in the background after start-up (not measured on the tablet yet). Recognition quality on real handwriting (and maths) needs the tablet. The debug app is ~63 MB with ML Kit.

- **Backup (Phase 6):**
  - After "Import from export" the app restarts on Home; the "Restored the backup from …" message only shows in Settings → Backup.
  - Restoring an export from an older database version relies on Room's normal migrations (covered by `MigrationTest`), not tested end to end with a real old export.
  - The PDF copies have no status of their own: if one notebook can't be drawn, or Drive fails while updating the copies, nothing is shown (the full backup already succeeded; the next run tries again).
  - Two tablets backing up to the same Google account would share one backup folder (each run keeps the other's newer snapshots, and unused files wait a day before they go), but restore lists them together. One tablet is the plan; revisit if that changes.
  - Each Drive run lists `Backup/blobs/` (1 request per 1000 files) instead of caching Drive ids locally. Fine for thousands of files; revisit if backups get slow.
  - **From the end-of-phase review (17 Nov), not fixed yet** (the other four were fixed in Phase 7 step 7.2):
    - When one notebook's PDF copy fails, the copies still count as up to date until the next change or the daily run (on purpose, so a broken notebook isn't retried every 30 minutes).
    - **New in 7.2:** "Delete now" on a notebook while a Drive upload is still reading it removes its rows straight away, but its files stay until the clean-up removes them (a folder of a gone notebook goes once all its files are 30+ days old). Harmless (unused files, not in later backups), just space for a while.
  - "Back up now" isn't an expedited job (that would need its own notification on Android 11 and older), so Android may start it a little later.

- **Open in Canvas (Phase 5 FB11):** not tried with the real Canvas Student app: whether it opens the university's assignment links (else the Chrome tab takes over) needs the tablet.
- **Split view (Phase 5.8):** each notebook opened in a pane keeps its view model until you leave the notebook screen (cheap, but many swaps in one visit keep many notebooks in memory). A real side-by-side split with another app needs the tablet.
- **Bookmark pen (Phase 5 FB5):** deleting a bookmark keeps its picture file (so Undo works); the daily clean-up (Phase 6.5) removes it 30 days later. The picture is what's on screen, so a rectangle drawn partly off screen gets only the part on screen.
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
- **Imported PDFs and images (Phase 3 decision, Phase 6.5):** a notebook's `pdf-*.pdf` and `images/*` files stay when their pages or images are deleted, so Undo works; the daily clean-up removes them once nothing has used them for 30 days (by file age, so a picture inserted long ago and removed today can go at the next clean-up unless the notebook is open).
- **Pen widths (F22, 7.12):** Medium and Full got stronger v2 pens (18 Nov). Off and Low are unchanged; whether Off/Low look right next to the new Full at your usual pressure is still for you to say. Any further change to a pen's look needs a new key.
- **Notebook editor, small things to revisit (Phase 2):** (very deep zoom, pre-drawing the next page and the big lasso move pause are planned as Phase 7 step 7.11; pen widths F22 as 7.12)
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
- **For Phase 6 backup:** Room keeps recent changes in a side file (`noteapp.db-wal`) next to `noteapp.db`. The backup must save them into the main file first (a "checkpoint") or copy all three files together. Otherwise a backup could miss the latest edits.

## Done

- **Hands-off fixes (19 Nov):** press hard to erase only starts near the start of a stroke (after about 5 mm of drawing, pressing hard keeps the line, and holding still snaps a line or shape); `saveNow` waits for a save already running (two flaky tests came from this); search keeps handwritten pages' words between key presses; Home shows Recently deleted even with no folders.
- **Handwriting search found nothing on the tablet (18 Nov, the user).** The model had downloaded, but the first 49 pages read were the debug test-writing pages (unreadable by design), so no real writing had been read. Fixed: newest pages first, plus a line-grouping fix for full pages and a progress line on the search screen. Installed on the tablet 18 Nov; recognition quality on the user's real writing still to be confirmed by them.

- **GitHub (19 Nov):** README on the front page; first public release "Noteapp 1.0" with the signed APK (Releases → latest). Phase 7 merged into `main` with the user's OK.

- **Shapes (7.14, 18 Nov):** hold the pen still at the end of a rough rectangle, circle or triangle and it snaps (a square or circle when nearly so); keep holding and move to resize from the corner you stopped at. Lasso one line or shape to drag its points (line ends, corners, an ellipse's sides). No change to how pages are saved.
- **Press hard to erase misses (7.15, 18 Nov):** a hard press now rides out small pressure wobbles, and holding it no longer snaps a line or shape instead.

- **Launch review (18 Nov):** reviewed everything since the Phase 7 review (`c37fba6`); 8 findings, 7 fixed: forgiving matches only for handwriting (typed PDF and text box words must match as typed), a damaged page no longer keeps the progress line up forever, a short real line (one cursive word) is no longer merged into the line below, Find in this notebook gives up after 5 s if the editor never lays out, the progress check stops once everything is read, two small tidy-ups. Kept: search re-reads every handwritten page's text on each typed letter (fine at today's size; see Follow-ups).

- **Pen widths, 7.12 (18 Nov, the user's feedback):** Medium and Full now respond more to pressure, like a real pen: thinner when you press lightly, thicker when you press hard (Full the most). Only new writing changes; what you already wrote keeps its look (new ink keys `pen-pressure-mid-v2`, `pen-pressure-full-v2`). Off and Low are unchanged. If it's too much or too little, the numbers are in `InkBrushes` (a further change needs another new key).

- **Handwriting reading progress while searching (18 Nov, the user asked):** the "read 40 of 252 pages so far" line now stays at the top of the results too (before, it only showed before typing or when nothing matched). In "Find in this notebook" it counts that notebook's pages, and that notebook's handwriting is read first while Find is open. An edited page counts as not read until it's read again.

- **"Find in this notebook" didn't go to the page (18 Nov, the user, on the tablet).** Coming back from the search screen rebuilds the editor; if the request arrived before the editor knew its size it was dropped, and the editor's first "fit the page" could put it back on the old page. Now it waits for that first fit, then goes to the page; a handwritten match also zooms to its line (as from Home's search). New screen test reproduces it (fails on the old code). Couldn't make it fail by hand on the emulator, so check on the tablet.

- **The release build crashed at start (fixed 18 Nov).** R8 moved one of Kotlin's internal string classes out of its package; `app/keep-rules.pro` now says `-dontobfuscate` (optimisations kept, no renaming). Checked on the emulator: every main screen (`screenshots/release_smoke.sh`), PDF search, Find in this notebook and handwriting search (model download + "TO" found). The release build is much smoother: typing in search ~18 ms frames instead of ~150 ms (debug). On the tablet since 18 Nov (the user said yes); for safety snapshots, install the debug build of the same commit first (release can't be copied off with `run-as`).

- **Search tweaks (18 Nov):** notes first in results; a 🔍 in course folders (searches that folder and its subfolders, plus the course's tasks); the notebook editor's own 🔍 button (Find in this notebook) at the top right.

- **Phase 7 installed on the tablet (18 Nov, 7.13):** database v7 → v8, compare PASS (38 Submitted tasks became Done; everything else and all 167 page files unchanged).

- **Messy handwriting (18 Nov):** search now keeps up to 5 readings of each handwritten line, forgives a letter or two in longer words (handwriting only), and reads each line with the line above as context. Real quality needs your notes on the tablet.

- **Phase 7 code review (18 Nov, hands-off):** 10 findings, 9 fixed (list in PHASE-7.md), 1 kept on purpose (the theme is read once before the first frame). 619 unit and 333 emulator tests pass.

- **Phase 7 step 7.10, handwriting search (18 Nov, hands-off):** search also finds your handwriting (English), read on the tablet by Google's ML Kit; a match opens zoomed to its line. The English model (~20 MB) downloads once on Wi-Fi.

- **Phase 7 step 7.9, searching inside notes (18 Nov):** search now also finds words in imported PDFs and text boxes ("In your notes"); tapping one opens the page with the words selected. Notebook ⋮ → "Find in this notebook". The words are kept in a separate `search.db` that's rebuilt in the background and never backed up.

- **Phase 7 step 7.8, search (18 Nov):** a search icon on Home finds tasks (titles, descriptions, your notes, course), notebooks and folders. Words in PDFs, text boxes and handwriting come in 7.9–7.10.

- **Phase 7 step 7.7, course colours (18 Nov, hands-off):** choose a course's colour from Settings → Courses ("Colour") or the folder's ⋮ menu ("Course colour"), or go back to Automatic.

- **Phase 7 step 7.6, light/dark (18 Nov, hands-off):** Settings → Appearance: Like the tablet, Light or Dark, for the whole app including the notebook editor.

- **Phase 7 step 7.5, reminder times (18 Nov, hands-off):** Settings → Reminders lets you pick any of 1 week, 3 days, 1 day, 2 hours, 1 hour before, or at the time (default still 1 day and 2 hours).

- **Phase 7 step 7.4, partial eraser (18 Nov, hands-off):** the eraser's panel has Whole strokes / Part of strokes. Part of strokes rubs out only where it passes. One eraser drag is now one Undo (before, a long whole-stroke rub could take several Undos).

- **Phase 7 step 7.3, page paper (18 Nov, hands-off):** the notebook ⋮ menu has "This page's paper" and "Paper for all pages", both undoable; Settings → Notebooks → "Paper for new notebooks".

- **Phase 7 step 7.2, backup follow-ups (18 Nov, hands-off):** a Drive backup no longer makes Delete now, Export everything, the 30-day purge or a restore wait for its upload (only its quick local copy holds the lock; the notebooks it's still uploading are protected another way). Unchanged PDFs aren't re-read every backup, the kept backup lists aren't downloaded again, and Home's "Open Settings" goes straight to Backup.

- **Phase 7 step 7.1, database version 8 (18 Nov, hands-off):** Submitted is merged into Done. Updating turns every Submitted task into Done; nothing else changes. Courses got an empty `color` column for step 7.7. Real v7 → v8 upgrade on the emulator: PASS.

- **Phase 7 planned (17 Nov):** `PHASE-7.md`, working-together mode. Decided with the user: search over names, tasks, PDF text, text boxes and handwriting (ML Kit, English); custom tags dropped and **Submitted merged into Done**; new settings (reminder times, course colours, default paper, light/dark); the Phase 6 review leftovers and editor speed leftovers are in. IDEA.md §2.2, §2.3, §2.11 (new), §3.3 and §5 updated with the user's OK. Version history stays "later"; the Home Drive nudge stays an open question.

- **Spare test account's Drive cleaned up (17 Nov):** the user deleted the Noteapp folder that got part of a backup of their real notes during the account mix-up.

- **Ended courses are suggested for archiving (17 Nov):** after each successful Canvas sync, Noteapp remembers since when a linked course stopped appearing in Canvas's active courses (`EndedCourses`, kept in `canvas_settings`, so no database change). After 14 days Home shows "ELECTENG 204 isn't in your Canvas courses any more. Archive it?" with Archive / Not now. IDEA.md's archiving section doesn't mention this yet (ask before editing it).
- **The PDF-copy job now reports "sign in again" straight away** (it used to wait for the next full backup). Found because Disconnect on one device takes Noteapp's permission back for that Google account everywhere.

- **A long first backup kept being cancelled (tablet, 17 Nov):** every change while uploading replaced the running "soon" job, so on a slow connection big files could restart forever. Now a running backup is never cancelled; the next one waits after it (`BackupWorker.soonPolicy`). Settings also shows "Backing up 12 MB of 70 MB…" and "Updating the PDF copies (3 of 8)…".

- **Google sign-in published (17 Nov):** app icon, homepage and privacy policy (`site/`, served by GitHub Pages from `gh-pages`: https://nzfrankwu.github.io/Noteapp/), Branding filled in and Publish app done by the user. Any Google account can now connect.

- **Phase 6.4 Recently deleted (17 Nov):** deleted notebooks and PDFs are listed for 30 days (Home row and Settings) with Restore and Delete now; a notebook whose folder was deleted asks for a folder. Resolves the old "deleted notebooks without a folder" follow-up. A daily job deletes anything older than 30 days for good.
- **Phase 6.1–6.3 Export everything / Import from export (17 Nov):** Settings → Backup. Real round trip on the emulator: compare PASS.

- **Phase 6 planned (17 Nov):** `PHASE-6.md`. Decided with the user: only `play-services-auth` plus our own Drive client; the .zip Export/Import first; PDF copies daily; Word/PowerPoint conversion last; Recently deleted on Home and in Settings; Drive keeps 7 days of complete snapshots (IDEA.md §2.9 updated); a separate test Google account on the emulator.

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
