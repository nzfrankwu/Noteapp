# Phase 4: Linking (notebooks ↔ tasks, page bookmarks, PDF text selection)

Links between tasks and notebooks, page bookmarks for tasks, and selecting PDF text with a finger (IDEA.md §2.4, §2.6 and §5).
Work happens on branch `phase-4`; it's merged into `main` at the end, with the user's OK.
- ★ marks a check-in with the user.
- 🔺 marks a difficult or high-stakes step. Before starting one, tell the user so they can switch to a stronger model or higher effort.

## Why
Phase 3 is merged into `main` (`7de0c06`). IDEA.md §5 says Phase 4 is **"Link notebooks/files ↔ tasks; course folder view showing notes + tasks together"** (§2.4: link a notebook to one or more tasks, see the notes from a task, see the tasks from a notebook). The course folder already shows notebooks above the course's tasks (Phase 2), so the main work is the links. Nothing links tasks and notebooks yet; the task screen has only a placeholder (`ui/task/TaskDetailScreen.kt:201`). PDFs are notebooks since Phase 3, so "files" are covered.

The user added more to this phase:
- **Page bookmarks for a task**, with an optional quick note.
- **Selecting real PDF text with a finger.** A menu offers Copy, Highlight, Underline and Bookmark; a bookmark made this way gets the selected text as its note.
- **Pop-up menus as simple icons** (lasso, text selection, selected picture).
- **IDEA.md change, OK'd by the user (16 Nov):** add bookmarks and PDF text selection to §2.4/§2.6 and to the Phase 4 row in §5.

## Decided with the user (16 Nov 2026)
- **From a task:** the "Linked notebooks" section shows the linked notebooks as tiles (tap to open) plus **＋ Link notebook**. That opens a sheet with:
  - the notebooks in this course's folder first
  - "Other folders ▸"
  - **＋ New notebook for this task**: makes a notebook in the course folder, links it, and opens it
- **Inside a notebook:** a small floating **chip next to Back** shows the linked task and its days left (or "2 tasks"). Tapping it opens a short list: open a task, unlink it, or ＋ Link to a task. With no links there's no chip; ⋮ gets "Link to a task…".
- **Task cards** (Due soon and folder task lists) show linked notebooks as small chips; tapping one opens that notebook.
- **A notebook tile's ⋮** gets "Link to task…", which offers this course's tasks.
- **Bookmarks**
  - **Three ways to make one:**
    - the **🔖 button** in the top bar bookmarks the page on screen
    - **🔖 in the lasso menu** bookmarks the selection's page and spot
    - **🔖 in the PDF text menu** bookmarks the selected text's page and spot, with **the selected text prefilled as the note**
  - **Saving one:** a small sheet: pick the task (linked tasks first, then "Other tasks ▸"), add an optional note (editable), Save. Bookmarking for a task that isn't linked yet links the notebook too.
  - **Under the task**, bookmarks are **grouped by notebook** and **ordered by page number, smallest first**. Each row shows "p. 12" and the note; the note can be added or changed later (✎), and the bookmark deleted.
  - **Tapping a bookmark** opens the notebook at that page, **zoomed to the spot** when it has one, otherwise fitted.
- **PDF text selection (finger)**
  - **Hold a finger on PDF text** to select a word; **drag the two handles** to select more, like in a browser.
  - **The menu:** **Copy · Highlight · Underline · Bookmark**.
    - Highlight: a neat highlighter mark over each selected line, in the highlighter's current colour.
    - Underline: a straight line under each line, in the pen's colour.
    - Both are normal ink: undoable, erasable and exported.
  - **Hold order:** a held finger on a picture selects it (F31); on PDF text it selects text; elsewhere it offers Paste (F12).
  - With "Draw with finger" on, a finger writes instead, so there's no text selection.
  - Scanned pages have no text, so holding there does what it does now.
- **Icon menus:** the lasso menu (including a selected picture's Crop), the text menu and the Paste bubble become a dark pill of Material icons with no text. Holding an icon shows its name as a tooltip, and each icon has a content description (accessibility and tests).

## Design (data safety, IDEA.md §5a)
- 🔺 **Database version 6:** two new tables in one `MIGRATION_5_6`, never destructive. Follow the steps at the top of `data/db/Migrations.kt`; MIGRATION_3_4 is the template. Add both to `NoteAppDatabase.entities`.
  - **`task_notebook_links`**
    - columns: `task_id`, `notebook_id`, `created_at`
    - primary key (`task_id`, `notebook_id`)
    - foreign keys with `ON DELETE CASCADE`; index on `notebook_id`
  - **`task_bookmarks`**
    - columns: `id` (UUID), `task_id`, `notebook_id`, `page_id`, `note` (nullable), the spot as `left`/`top`/`width`/`height` (nullable, page units), `created_at`, `updated_at`
    - foreign keys to tasks, notebooks and pages with `ON DELETE CASCADE`; indexes on each
  - Existing rows are untouched.
- **Page number = order, not stored.** A bookmark points at a `page_id`; its number is the page's place among the notebook's non-deleted pages (`pages.position`), so it stays right as pages change.
- **Deleting a task is a hard delete with Undo** (`TaskRepository.deleteTask` / `restoreTask`). The cascade removes its links and bookmarks, so:
  - `DeleteTaskResult.Deleted` carries them too
  - `restoreTask(task, links, bookmarks)` puts them back, skipping notebooks or pages that are gone
- **Deleted notebooks and pages are soft-deleted**, so their rows stay and come back with Undo. Every query filters `deleted_at IS NULL` on notebooks and pages.
- **Canvas sync keeps task IDs** (`CanvasMerge.update` copies the row; removed tasks are only flagged), so links and bookmarks survive sync.
- **Backup (Phase 6):** it's all in the database.
- **Repository:** `data/LinkRepository.kt`, created in `AppContainer`.
  - Links: `link`, `unlink`, `observeNotebooksForTask` (as `NotebookListItem`), `observeTasksForNotebook` (`TaskWithCourse`), `observeAllLinks()` (for the card chips), `createNotebookForTask`
    - `createNotebookForTask` uses the course folder; with none, it uses the folder picker from `data/ShareTargets.folderTree`
  - Bookmarks: `addBookmark` (links too), `setNote`, `deleteBookmark`, `observeBookmarksForTask`
  - Grouping and sorting in pure `domain/Bookmarks.kt` (unit-tested)
- 🔺 **PDF text layer** (`data/PdfText.kt`, PdfBox-Android, already a dependency):
  - **Reading the words:** per PDF page, the words with their boxes, using `PDFTextStripper` with text positions. Lines are built from the words.
  - **Units:** boxes are turned into our page units with the inverse of `PdfExport.pageToPdf` (crop box plus `/Rotate`). One shared function, tested both ways.
  - **When it runs:** on the PdfPages thread (or its own), the first time a page is held; then kept in a small cache per notebook. Pages with no text give an empty layer.
  - **Selection logic:** pure Kotlin in `domain/ink/TextSelection.kt`, unit-tested:
    - word at a point
    - extend to a handle
    - selected lines → rectangles
    - copy text with spaces and line breaks
    - highlight/underline strokes for the lines
  - **Drawing the selection:** a tint over the rectangles plus two handles, drawn in the canvas overlay (like the lasso box).
  - **Gestures:** `NotebookCanvas.handleGestures`, beside the finger long-press (`selectPictureAt` / Paste). The handles are dragged with a finger or the pen.
  - **Highlight / Underline:** one straight stroke per line (highlighter: line height thick, flat; underline: pen, at the baseline), added in one undo step (`NotebookInk.addAcross` / `InkEdit.Group`).
  - **Copy:** Android's clipboard (a text clip).
- **Opening at a spot:** `NotebookRoute(notebookId, pageId? = null, spot? = null)` (the defaults keep saved back stacks loading). `NotebookScreen.showPage` gets a "zoom to region" version through `PageStrip`.
- **Icon menu component:** `ui/notebook/IconMenu.kt` holds an `IconMenuItem(icon, label, onClick, destructive)` row with tooltips. It's used by LassoMenu, the text menu and the Paste bubble, and the existing test tags (e.g. `lasso-crop`) are kept.
- New ViewModel parameters get defaults (`links: LinkRepository? = null`), so `TaskScreensTest`, `FolderScreenTest`, `FolderLibraryTest` and `NotebookEditorTest` keep compiling.

## Steps (branch `phase-4` from `main`; ★ = check-in, 🔺 = difficult)
- [x] **4.0** Write `PHASE-4.md` from this plan, update IDEA.md (§2.4, §2.6, §5; OK'd), and update HANDOFF.md and ISSUES.md. Commit.
- [ ] 🔺 **4.1 Database v6 + LinkRepository**
  - entities, DAOs, the migration, `6.json`, the SchemaHistoryTest hash
  - MigrationTest: both tables in the column-order list, `migration5To6`, and "v5 data survives" with `insertVersion5Data`
  - repository tests: link/unlink, duplicates ignored, deleted notebooks/pages hidden, bookmark order, the cascades, and task delete → Undo restores everything
  - unit tests for `domain/Bookmarks`
  - a real upgrade check on the emulator (old APK from `main`)
- [ ] **4.2 Task screen: linked notebooks**
  - tiles (reuse `LibraryTiles.NotebookTile`), unlink in the tile's ⋮
  - `ui/task/LinkNotebookSheet.kt`, "New notebook for this task"
  - `onOpenNotebook` in `entry<TaskRoute>`
  - ★ check-in
- [ ] **4.3 Notebook chip + "Link to a task…"**
  - `EditorTopBar` chip slot by Back
  - the task picker `ui/notebook/LinkTaskSheet.kt`
  - `onOpenTask` from `entry<NotebookRoute>`
  - ★ check-in
- [ ] **4.4 Icon menus**: `IconMenu.kt`; LassoMenu and the Paste bubble become icons with tooltips. Screenshots light and dark. ★ quick look
- [ ] **4.5 Bookmarks in the editor**: the 🔖 top-bar button, 🔖 in the lasso menu, and the bookmark sheet (task picker + note; reuses `LinkTaskSheet`).
- [ ] 🔺 **4.6 PDF text layer**: `data/PdfText.kt` (words, lines, coordinate mapping incl. turned pages), the cache, emulator tests with PDFs made by `PdfDocument`. Then `domain/ink/TextSelection.kt` with unit tests.
- [ ] 🔺 **4.7 Finger text selection**
  - hold → word, handles, the overlay
  - the icon menu: Copy, Highlight, Underline, Bookmark (prefilled note)
  - gesture order with the picture hold and Paste
  - ★ check-in
- [ ] **4.8 Bookmarks under the task**
  - grouped by notebook, page order
  - add or change the note, delete
  - tap → open at the page/spot
  - ★ check-in
- [ ] **4.9 Chips on task cards** (`TaskCard`: optional `notebooks` + `onOpenNotebook`) in Home Due soon and folder task lists; one `observeAllLinks()` flow per screen.
- [ ] **4.10 Tile ⋮ "Link to task…"** (`FolderLibrary.LibraryRow` → the task picker, this course's tasks).
- [ ] **4.11 Tablet check**: ask first; back up with `tablet_snapshot.sh` and compare before/after. This install upgrades the database to v6.
- [ ] **End of phase:** `/code-review`, fix, rebuild and retest, then ask to merge into `main`.

## Verification
- **Every step:** `gradlew.bat assembleDebug testDebugUnitTest`, plus `connectedDebugAndroidTest` with `ANDROID_SERIAL=emulator-5554` (the tablet is often plugged in).
- **UI on the `Pixel_Tablet` emulator**, landscape and portrait, light and dark, screenshots in `screenshots/`. With fake Canvas data and an imported text PDF (`screenshots/make_test_pdf.py`):
  - link a notebook from a task, and make a new one from a task
  - open it from a task card chip
  - see the chip in the editor and go to the task and back
  - hold a finger on PDF text, drag the handles, then Copy, Highlight (undo works) and Underline
  - 🔖 from the text menu: the note shows the selected text
  - 🔖 from the top bar on another page
  - the task lists the bookmarks grouped and in page order; tap one → the notebook opens zoomed to the spot
  - delete the task → Undo → links and bookmarks are back
- **Upgrade test v5 → v6 with real data:** rows and page files unchanged (`compare_dbs.py`).
- **Can't be tested on the emulator:** how holding and dragging handles feels with a real finger, and pen-dragged handles on the OPPO Pencil. These are for the tablet check.
