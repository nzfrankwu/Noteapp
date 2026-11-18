# Phase 7: Polish (search, partial eraser, settings, speed)

Search, the partial eraser, per-page paper, new settings, Submitted merged into Done, the Phase 6 backup follow-ups and editor speed (IDEA.md §2, §5).
Work happens on branch `phase-7`; it's merged into `main` at the end, with the user's OK.
- ★ marks a check-in with the user (every step in this phase).
- 🔺 marks a difficult or high-stakes step. Before starting one, tell the user so they can switch to a stronger model or higher effort.

## Why
Phases 1–6 are merged into `main` (`c4b9e7a`) and the tablet runs the Phase 6 build (database v7) with Drive backup working. IDEA.md §5 lists Phase 7 as polish: search, partial eraser, custom tags, per-page templates, settings and performance tuning.

**Mode:** planned as working together (17 Nov); the user switched to **hands-off** on 18 Nov ("start, hands off mode"). Check-ins (★) are skipped and listed in REPORT.md; steps that need the user (a new screen layout without a reference, a big new library, the tablet) are left for them.

## Decided with the user (17 Nov 2026)
- **Search finds:** names and tasks, PDF text, text boxes **and handwriting**. Handwriting uses Google's ML Kit handwriting recognition (approved in principle; still ask before adding it at 7.10), **English only**.
- **Custom tags: dropped.** Instead, **Submitted is merged into Done** ("they mean the same thing"): the list becomes Not started → Started → Stuck / need help → Double-checking → Done. Canvas auto-status sets Done. Existing Submitted tasks become Done. IDEA.md §2.3 and §3.3 updated.
- **New settings:** reminder times, pick course colours, default paper for new notebooks, light/dark choice.
- **Also in this phase:** the Phase 6 review leftovers (backup lock, md5 cache, snapshot list cache, "Open Settings" → Backup) and the editor speed leftovers (deep zoom, pre-draw the next page, big lasso move pause, pen widths F22).
- **Not in this phase:** Version history (stays "later"), a Home nudge to connect Drive (still an open question in ISSUES.md).

## Proposed by me (easy to change)
- One database change for the phase (v8, step 7.1): the status rewrite and `courses.color`, so the tablet upgrades once.
- The search index lives in its own `search.db`, a cache that can be thrown away and rebuilt from the notes. It's never backed up and is rebuilt after a restore.
- Global search opens from a search icon on Home; inside a notebook, ⋮ → Find in this notebook.
- Reminder choices: 1 week, 3 days, 1 day, 2 hours, 1 hour, at due time (default stays 1 day + 2 hours).
- The partial eraser is a choice in the eraser's settings (Whole strokes / Part of strokes), default Whole strokes as today.

## Design
**Submitted → Done (7.1)** 🔺:
- `MIGRATION_7_8`: `UPDATE tasks SET status = 'done' WHERE status = 'submitted'`.
- `TaskStatus.SUBMITTED` goes; `TaskStatus.fromKey("submitted")` still returns DONE, so an old export or Drive backup restored later reads correctly even before Room migrates it.
- `CanvasMerge`: a new task already submitted starts as DONE; `autoStatus` moves to DONE on a new submission, still forward-only and never when the task is finished.
- `StatusColors`, `StatusChip`, `TaskStatusTest` / `StoredKeysTest` updated (the old key stays listed as a legacy alias).

**Course colour (7.1 column, 7.7 UI):** `courses.color` (nullable text key of a palette slot, the last entity field). Null = automatic. `CourseColorKey.slotsInOrder` keeps counting every course, so choosing one course's colour never shifts the others.

**Partial eraser (7.4)** 🔺:
- `InkGeometry` finds the parts of a stroke inside the eraser circle along the drag and returns the pieces outside it, cutting at interpolated points (x, y, time, pressure, tilt, orientation).
- `NotebookInk` gets a `Split` edit (remove one stroke, put its pieces at the same place in the drawing order).
- One eraser drag = one undo step, for both eraser kinds (today a whole-stroke drag can record several).
- Pieces are ordinary `InkStroke`s with new ids: no page-file format change.

**Search (7.8–7.10)** 🔺:
- Names and tasks (7.8): queried from the main database in memory, every word must match (like `LinkChoices.matches`), over task title, description and notes, notebook titles, folder and course names.
- `search.db` (7.9): Room FTS4 table of (notebook, page, kind = pdf / textbox / handwriting, text, boxes). Filled by a background worker: PDF words from `PdfText` per page (once per PDF file), text boxes when a page is saved, handwriting (7.10) when a page changes. Deleted notebooks' rows are removed. Excluded from backups (`BackupPaths` + a test).
- Results show a snippet; tapping opens the notebook at that page with the match highlighted.
- Handwriting (7.10): ML Kit `digital-ink-recognition`, English model downloaded once on Wi-Fi. Strokes are grouped into lines; each line is recognised on the device and stored with its box. Nothing leaves the tablet.

**Backup lock (7.2)** 🔺: `StorageLock.backup` is held only while `LocalSnapshot` copies. The upload runs from the staged copy; purge and clean-up skip files a running upload still lists.

## Steps (branch `phase-7` from `main`; ★ = check-in, 🔺 = difficult)
- [x] **7.0** This plan; IDEA.md (§2.2, §2.3, new §2.11 Search, §3.3, §5), HANDOFF.md, ISSUES.md.
- [x] 🔺 **7.1 Database v8** (18 Nov, hands-off). `MIGRATION_7_8`: `submitted` → `done` (nothing else in the row changes, not even `updated_at`), `courses.color` (null). `TaskStatus.SUBMITTED` is gone; `TaskStatus.LEGACY_KEYS` reads "submitted" as Done. Canvas auto-status sets Done. `CourseColorChoice` holds the 7 colour keys (frozen by `StoredKeysTest`). 2 new `MigrationTest`s (only the status changes; old data reads after updating). **Real v7 → v8 upgrade on the emulator: PASS** (`screenshots/upgrade_v8.sh`, `compare_dbs.py … --v8`: the 1 Submitted task became Done, every other row and all 50 page files unchanged). The v7 APK is kept as `screenshots/app-v7.apk`. 576 unit tests, 312 emulator tests.
- [x] 🔺 **7.2 Backup follow-ups** (18 Nov, hands-off).
  - **Lock:** the Drive run holds `StorageLock.backup` only while `LocalSnapshot` copies; the upload runs without it (`DriveBackup.oneRun` keeps runs one at a time). The PDFs and pictures it reads in place (`Snapshot.readInPlace`) have their notebooks pinned (`data/backup/BackupPins`) until the run ends; `NotebookPurge` and `FileCleanup` leave a pinned notebook's files for a later run. The PDF copies take the lock per notebook export (`AppPdfSource.export`), not for the whole run.
  - Changed from the plan: hard links first, but **Android refuses hard links in app storage** (SELinux, "Permission denied", checked with `run-as … ln` on the emulator), so pins instead.
  - **md5 cache** (`data/backup/Md5Cache`, `cache/backup-md5.txt`): write-once files keyed by path + size + modified time. **Snapshot lists cache** (`cache/drive-lists/<fileId>.json`): read instead of downloaded; a damaged one is downloaded again; lists of removed snapshots are forgotten.
  - Home's backup strip → `SettingsRoute(SettingsRoute.BACKUP)` → Settings scrolls to Backup once (`SettingsRoute` is now a data class).
  - Tests: 9 new unit tests (`DriveBackupTest` lock/pins/list cache, `Md5CacheTest`, `BackupPinsTest`), 3 emulator tests (`LocalSnapshotTest.aSnapshot_listsTheFilesItReadsInPlace`, `RecentlyDeletedTest.deleteNow_duringAnUpload_leavesThePinnedFilesForLater`, `DriveBackupScreenTest.settingsOpenedForBackup_scrollsToTheBackupSection`).
- [x] **7.3 Per-page paper + default paper** (18 Nov, hands-off). Editor ⋮ now has **"This page's paper"** (hidden on PDF pages) and **"Paper for all pages"** (ticked only when every page uses it). Both are undo steps (`InkEdit.PagePaper`, `InkEdit.NotebookPaper`, carried out by the view model like Add page). Changed from before: "all pages" now also clears pages' own paper (Undo brings it back). `NotebookRepository.setPageTemplate` / `setNotebookPaper`. The notebook tile picture follows the first page's paper. **Settings → Notebooks → "Paper for new notebooks"** (`InkToolSettings.newNotebookPaper`, key `new_notebook_paper`; `AppContainer.newNotebookPaper`), used by New notebook in a folder and from a task. Not done: the Recent panel's placeholder (shown only before a notebook has a tile picture) still uses the notebook's paper. 1 unit test, 1 editor test. Screenshots: `screenshots/p7/73-*.png`. ★
- [x] 🔺 **7.4 Partial eraser** (18 Nov, hands-off). Eraser panel: **Whole strokes / Part of strokes** (`InkToolSettings.eraserPartial`, key `eraser_partial`, default whole). `domain/ink/PartialErase.cut` cuts a stroke's centre line where it's within `radius + size/2` of the eraser's move (ternary + binary search per segment), interpolating position, time, pressure, tilt and orientation at the cut; pieces under 2 points are dropped, a touched dot goes. `InkEdit.Split` puts the pieces in the stroke's place in the drawing order. **One eraser drag = one undo step** for both kinds (`NotebookInk.record(edit, drag)` merges edits of the same drag, keyed by the pen-down time); this also fixes the old whole-stroke drag making several steps. Works for the eraser tool, the pen's eraser end and press-hard-to-erase. No file-format change. 13 unit tests (`PartialEraseTest`), 1 editor test. Screenshots: `screenshots/p7/74-*.png`. Not checked: how it feels with the OPPO Pencil (speed of a long rub over dense writing). ★
- [x] **7.5 Reminder times** (18 Nov, hands-off). Settings → Reminders has chips (any mix): 1 week, 3 days, 1 day, 2 hours, 1 hour before, At the time; default 1 day + 2 hours as before; none = reminders off. Saved in `view_settings` (`reminder_offsets`, so it's backed up) through the new `AppSettingsStore` (`ViewSettings`). `ReminderOffset` gained keys `1w`, `3d`, `1h`, `0` (frozen by `RemindersTest.offsetKeys_neverChange`) and `before()`. `NoteAppApplication` re-plans alarms when tasks or the choice change. 5 unit tests, 2 emulator tests. Screenshot: `screenshots/p7/75-reminders.png`. ★
- [x] **7.6 Light/dark choice** (18 Nov, hands-off). New **Settings → Appearance**: "Like the tablet / Light / Dark" (`domain/ThemeMode`, key `theme_mode` in `view_settings`, so it's backed up; keys frozen in `StoredKeysTest`). `MainActivity` reads it once before the first frame (no flash), passes it to `NoteAppTheme`, and sets the status/navigation bar icon style to match. `NoteAppTheme` provides `LocalDarkTheme`; the editor's two `isSystemInDarkTheme()` calls now read it. **Rule: never call `isSystemInDarkTheme()` in screens; read `LocalDarkTheme.current`.** 1 unit test, 1 screen test. Checked on the emulator: Dark chosen while the emulator is light (`screenshots/p7/76-*.png`). ★
- [x] **7.7 Course colours** (18 Nov, hands-off). A **"Colour"** button on each row of Settings → Courses and **"Course colour"** in a course folder's ⋮ menu open `ui/components/CourseColorDialog`: the 7 course colours (light/dark aware) plus "Automatic". Saved in `courses.color` (from 7.1) by `FolderRepository.setCourseColor`; Canvas sync keeps it. `CourseColors.chosen` wins over the automatic slot; `CourseColorKey.slotsInOrder` still counts every course, so other courses never shift. 1 unit test, 1 emulator test. Screenshots: `screenshots/p7/77-*.png`. ★
- [x] **7.8 Search: names and tasks** (18 Nov; layout OK'd by the user). A **search icon on Home** (next to Settings) opens `ui/search/SearchScreen` (`SearchRoute`): a centred search box on top (focused on open, clear ✕), results below in one centred column grouped **Tasks · Notebooks · Folders & courses** with counts. Tasks are normal task cards (status menu works); notebooks show "in <folder › subfolder> · N pages"; folders show their course tag. Rules in `data/SearchIndex` (every word, any case, any order; title matches first; tasks unfinished and soonest first, archived courses last; descriptions read as plain text). 6 unit tests, 2 screen tests. Screenshots: `screenshots/p7/78-*.png`. ★
- [x] 🔺 **7.9 Search index: PDF text + text boxes** (18 Nov).
  - **Store:** `data/search/NoteTextStore`, its own SQLite file `databases/search.db` (plain `SQLiteOpenHelper`, one `page_text` table: page, notebook, PDF text, text box text and the "keys" they were read from). It's a cache: a new version starts empty (never the real database's rule), and backups never include it (they take only `noteapp.db` and the notebook files; test). Changed from the plan: no FTS table; every search word is a `LIKE '%word%'` (escaped), so part-words match like the rest of search.
  - **Indexer:** `data/search/NoteTextIndexer`, run 5 s after start-up and after every database change (`NoteAppApplication.keepSearchInStep`). It re-reads only pages whose source changed (PDF: file + page + size; text boxes: page file size + time), opens each PDF once (`PdfText.read`), drops deleted pages and notebooks, and skips anything it can't read (next run tries again). New DAO queries `getAllActivePages`, `getAllActiveNotebooks` (no schema change).
  - **Search screen:** a new group **"In your notes"** ("Lecture 7 · page 12" + the text around the match). Pages with the words together ("slide 3") come first. Tapping one opens `NotebookRoute(…, pageId, find = query)`: on a PDF page the words are **selected** (the usual Copy / Highlight / Underline / Bookmark menu) and the page is zoomed to a band across its width around them; for a text box, the page is zoomed around it (`NotebookPane.showFound`, `LocalOpenAtFind`).
  - **Find in this notebook:** editor ⋮ → opens `SearchRoute(notebookId)`, only that notebook's pages ("Page 5"); tapping a page swaps back to the notebook at that page.
  - Tests: 4 unit (`TextSelection.find`, snippet, words together), 3 indexer + 2 screen emulator tests; full suite 614 unit / 328 emulator pass (`NoteTextIndexerTest`, `SearchNotesScreenTest`). Checked on the emulator with `lecture7.pdf` (`screenshots/p7/79-*.png`).
  - Not done: highlighting the match inside a text box (the page zooms to the box); scanned PDFs have no text to find (handwriting in 7.10 is separate).
- [x] 🔺 **7.10 Handwriting search** (18 Nov, hands-off; ML Kit OK'd by the user).
  - Library: `com.google.mlkit:digital-ink-recognition:19.0.0` (builds with Kotlin 2.2 / AGP 9.5.1; classes are in `…digitalink.recognition`). The debug APK grew from ~32 MB to ~63 MB (native code for every processor type).
  - `domain/ink/HandwritingLines` groups a page's strokes into lines (no highlighters, dots or tall drawing strokes; flat strokes like a "T" bar count as a letter's height). `data/search/Handwriting.kt`: `HandwritingReader` + `MlKitHandwriting` (en-US model, downloaded on Wi-Fi only, waits up to 2 min, else tries next run; recognition on the device).
  - The indexer reads handwriting last, page by page, saving as it goes; keyed like text boxes (page file size + time). `NoteTextStore` v2 adds `hand_text`, `hand_lines` (each line's band), `hand_key`; PDF/box writes keep the handwriting columns.
  - Search: a handwriting-only match shows a pen icon and opens zoomed to that line's band (`NoteHit.spot` → `NotebookRoute.spot`).
  - Tests: 5 unit (`HandwritingLinesTest`), 2 indexer tests with a fake reader, 1 real ML Kit test (`MlKitHandwritingTest`: the model downloaded on the emulator and read outlined "hello" as "heldo"; skipped when it can't download). **Real check in the app on the emulator:** "TO" written with the stylus → recognised "TO" → search "to" → "In your notes" → opens at the line (`screenshots/p7/710-*.png`). Found on the way: a "T" bar was put on its own line (fixed, test added).
  - `site/privacy.html` has a "Handwriting search" line. **Not published:** the live page (`gh-pages`) is the user's call.
  - **Messy handwriting (18 Nov, the user asked; ML Kit can't be trained on one person's writing):**
    1. Up to 5 readings per line are kept (`hand_alt`, "a | b | c"), and search looks at all of them. Example from the real model: outlined "hello" read as heldo, heddo, **hello**, hedoo, herdo.
    2. Forgiving matching for handwriting only (`domain/FuzzyWords`): a word of 4–6 letters may be 1 letter off, 7+ letters 2 off; 1–3 letters exact. Such "close" pages are listed after exact ones, showing what was read.
    3. Each line is read with the line above as context (`RecognitionContext.preContext`, last 20 characters).
    - Not possible: giving ML Kit a word list (no API). A "correct what it read" screen is a possible later step.
    - **Tablet: "handwriting detection doesn't work at all" (18 Nov).** Found without the tablet: on a real, dense page, ascenders and descenders made every line's band overlap the next, and the old grouping merged the whole page into one "line", which reads as gibberish. `HandwritingLines` now clusters strokes by their middle height (a stroke joins the line while its middle is within 0.8 × a typical stroke height of the line's average middle; an i-dot/accent "line" joins the line below). New test: a dense 3-line page with ascenders, descenders, a t-bar and an i-dot stays 3 lines; real ML Kit test of a 3-line page at note size gets 3 lines each read. Tried and dropped: scaling lines up before recognition (ML Kit normalises size; no change). Added a **status line on the search screen** while handwriting isn't all read ("read 40 of 252 pages so far", "downloading the handwriting model (Wi-Fi only)…", or "couldn't be downloaded yet"). Still to check on the tablet: whether the model downloaded there.
    - `NoteTextStore` v3 (the cache starts again and every page is read again once). Tests: 3 unit (`FuzzyWordsTest`), indexer and screen tests updated/added; full suite 622 unit / 334 emulator pass.
- [ ] **7.11 Editor speed.** A picture of just the visible part at very deep zoom; pre-draw the next page; no pause after a big lasso move. Measure with `screenshots/framestats.py`. ★
  - Full suite after 7.7: 605 unit tests, 321 emulator tests pass (18 Nov).
  - Skipped in the hands-off run (18 Nov): the fixes change how pages draw their ink (`StrokeDrawables`, `PageInkLayers`, the lasso commit in `NotebookCanvas.commitSelection`) and can't be measured on the emulator. Idea for the lasso pause: moved strokes are rebuilt one by one on the main thread in `StrokeDrawables.get`; build them in the background before the selection is let go.
- [ ] **7.12 Pen widths (F22)** after the user tries the pens on the tablet; new ink keys if notes already use the old widths. ★
- [x] **7.13 Tablet check** (18 Nov, the user's OK). Snapshot with files (`backups/tablet-2026-11-18-before-v8/`) → install `d6ea79a` → opened once → snapshot → `compare_dbs.py … --v8`: **PASS** (v7 → v8; the 38 Submitted tasks became Done, none left; every other value of 10 courses, 5 folders, 124 tasks, 8 notebooks, 252 pages unchanged; 167 page files identical; integrity ok).
- [ ] **End of phase:** `/code-review`, fix, rebuild and retest, ask to merge into `main`.
  - **Review done (18 Nov, after 7.10, hands-off):** 10 findings, 9 fixed, 1 kept on purpose:
    - Find in this notebook rebuilt the editor (undo history and split view lost) → it now asks the open editor to go to the page (`FindRequest`, `LocalFindRequests`).
    - The Drive backup's tidy-up ran outside the storage lock, so it could remove a snapshot a restore was downloading → tidy is under the lock again (the upload still isn't).
    - Opening a search hit could lose the selection if the view resized while the PDF text loaded → it runs in its own coroutine.
    - Changes made during the first (long) index run weren't noticed → the first run and later changes share one `collectLatest`.
    - With no Wi-Fi the handwriting model download was waited for (2 min) on every run → asked for once, not waited for; the indexer runs again when it arrives.
    - Search could briefly show the last query's note hits → hits carry their query.
    - The store's result limit could cut pages with the words together → `ORDER BY` them first.
    - `Md5Cache` scanned every entry per new file → keyed by path.
    - Unused teal status colours and an unused import removed.
    - Kept: the theme is read with `runBlocking` once before the first frame (one tiny file), so a forced light/dark never flashes.
  - Tests after the fixes: 619 unit, 333 emulator (2 new: `aFindRequest_takesTheOpenNotebookToThePage_andKeepsUndo`, `wordsTogether_comeFirst_beforeTheLimit`).
  - Still open in Phase 7: 7.11 (editor speed, needs tablet measurements), 7.12 (pen widths, needs the user's try), 7.13 (tablet install, needs the user's OK). Merge into `main` after those or when the user says.

## Verification
- Every step: `.\gradlew.bat assembleDebug testDebugUnitTest`, then `$env:ANDROID_SERIAL="emulator-5554"; .\gradlew.bat connectedDebugAndroidTest`.
- Screenshots of every changed screen (landscape/portrait, light/dark).
- 7.1 and 7.13: real upgrade checks with `compare_dbs.py`.
- The tablet only with the user's OK and a snapshot first.
