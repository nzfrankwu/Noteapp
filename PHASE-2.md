# Phase 2: Handwriting notebooks

Subfolders, notebooks and the ink canvas (IDEA.md §2.1, §2.5 and §5).
Work happens on branch `phase-2`; it's merged into `main` at the end, with the user's OK.
- ★ marks a check-in with the user.
- 🔺 marks a difficult or high-stakes step. Before starting one, tell the user so they can switch to a stronger model or higher effort.

## Decided with the user (15 Nov 2026)

- **Ink engine: Jetpack Ink** (`androidx.ink`, Google's stylus library). The user OK'd adding it. Standard Android, not OPPO-only.
- **Pages run side by side, scrolling sideways, like Notein** (`design-reference/doc ui reference.jpg`).
- **Inside a folder, its subfolders and notebooks sit above the tasks** (one grid on top of the task list, in the same scroll), not in a separate tab.
- **Subfolders:**
  - They belong to the top folder's course and can't link a course of their own.
  - The course's tasks show only in the top course folder.
  - They can nest to any depth.
- **Pen button:** one click of the stylus side button switches to lasso; another click goes back to the tool used before. This uses the standard `MotionEvent.BUTTON_STYLUS_PRIMARY`; whether the OPPO Pencil 2 reports its button that way is checked on the tablet.
- **Press hard to erase:** with the pen or highlighter, pressing the tip harder than a set level erases instead of drawing. The pen pop-up has a sensitivity slider and an off switch, so normal writing never triggers it.
- **Double-tap with a finger fits the page to the screen.** The default is "fit height". Settings → Notebooks offers fit height, fit width or whole page.
- **The toolbar is a bit thinner than Notein's:** one narrow column, about 48 dp. Extras that fit without widening it:
  - 3 pen slots, each remembering its own colour and thickness, plus highlighter, eraser and lasso
  - 8 colour swatches plus "More colours"; 3 thickness presets plus a slider in the pop-up
  - stroke smoothing ("Stability") in the pen pop-up
  - hold the pen still at the end of a line to straighten it (pen and highlighter)
  - a collapse button that tucks the toolbar into a small round handle
- **Lasso moved from Phase 5 into Phase 2** (select, then move, resize or delete). IDEA.md §2.5 and §5 were updated to match.

## Design decisions (data safety, IDEA.md §5a)

- **Database version 4** (one migration, 3 → 4):
  - `folders.parent_id`: null means a top-level folder.
  - a new `notebooks` table: id, folder, title, template, page width and height (in page units), created, updated, `deleted_at`
  - a new `pages` table: id, notebook, position, created, updated
- **Strokes live in files, one per page:** `filesDir/notebooks/<notebookId>/<pageId>.ink`, not in the database.
  - Each file has a small header with a format version, then the strokes (Jetpack Ink's stroke encoding plus our brush settings).
  - Files are written whole to a temporary file and then renamed, so a crash never leaves half a page.
  - One file per page lets Phase 6 back up only the pages that changed.
- **Stroke positions are in page units.** Pages are A4 portrait for now, but the size is stored per notebook, so other sizes later need no migration.
- **Thumbnails are only a cache:** they can be rebuilt and are never backed up.
- **Deleting is safe:**
  - Deleting a notebook only marks it (`deleted_at`), hides it and offers Undo. The real "Recently deleted" screen comes in Phase 6.
  - A folder can be deleted only when it has no subfolders or notebooks.
- **Home shows top-level folders only.**
- **New notebook:** a title and a template (Lined by default, Blank, Grid, Dot grid), loosely after Notein's "New Note" screen, without covers, paper colours or paper sizes.
- **A debug-only "Draw with finger" setting** lets the emulator test drawing. The real stylus is tested on the tablet.

## Checklist

- [x] 🔺 **2.1 Database version 4.** _(done 15 Nov, hands-off: `Folder.parentId`, new `Notebook`, `Page`, `NotebookDao`, `PageTemplate` (keys frozen in `StoredKeysTest`), `FolderPlace` (path + inherited course), `NotebookRepository`; `MIGRATION_3_4` = `ALTER TABLE folders ADD COLUMN parent_id … REFERENCES folders` + two `CREATE TABLE`s, schema 4 recorded. Notebooks and subfolders use `ON DELETE SET NULL`, so even a buggy delete can't remove them; `FolderRepository.deleteFolder` refuses non-empty folders. Marked-as-deleted notebooks in a deleted folder stay in the database with no folder (Phase 6 "Recently deleted" will offer them). Tested: 2 new migration tests, a deliberately broken migration caught by 7 of 9, a real upgrade of the version-3 app with data on the emulator (every row identical), and 15 new repository tests.)_
  - Subfolders, notebooks and pages, with a tested 3 → 4 migration (version-3 data must survive).
  - Record schema 4 in `SchemaHistoryTest`, extend `MigrationTest`, catch a deliberately broken migration, and do a real upgrade of the version-3 app with data on the emulator (see the gotchas in HANDOFF.md).
  - Repositories for folders inside folders, notebooks and pages.
  - Settle here what happens to marked-as-deleted notebooks when their folder is deleted.
- [x] **2.2 Subfolders in the folder screen** (layout below). ★ _(done 15 Nov, hands-off, following the layout draft below and `notein-inside-course-folder.jpg`; the ★ check-in is in REPORT.md. `ui/folder/FolderLibrary.kt`, `components/LibraryTiles.kt`. Filter row is All · Notebooks · Folders. Subfolder icons take the course colour; a subfolder's header shows the course tag and "in Top › Parent". ＋ is a floating button with New notebook / New folder.)_
- [x] **2.3 New notebook and notebook tiles.** _(done 15 Nov: `NewNotebookDialog` (name pre-filled "Notes 15 Nov", paper picker), a new notebook opens straight away, tiles show "15 Nov · 1 page" and the paper pattern (`components/PaperBackground.kt`, `domain/PagePattern`), Delete shows "Deleted … Undo". `NotebookRoute` opens a first `ui/notebook/NotebookScreen` (pages side by side, no writing yet).)_
  - The New notebook dialog (title and template).
  - Tiles show the title, date and page count, with the template's pattern as a placeholder picture.
  - Rename, and delete with Undo.
- [x] 🔺 **2.4 Jetpack Ink go/no-go.** ★ _(build side done 15 Nov, hands-off: `androidx.ink` 1.0.0 builds with Kotlin 2.2 / AGP 9.5.1 / minSdk 26, and draws low-latency strokes on the emulator (stylus events via `adb shell input stylus`). Waiting on the user: try the OPPO Pencil 2 for pressure and lag.)_
  - Add the library and confirm it builds with our Kotlin 2.2, AGP 9.5.1 and minSdk 26.
  - Draw low-latency strokes on one page on the emulator.
  - The user then tries the OPPO Pencil 2 on the tablet: pressure and lag. If Jetpack Ink doesn't fit, stop and discuss a fallback.
- [x] 🔺 **2.5 Page files and autosave.** _(done 15 Nov: `domain/ink/PageInkCodec` (our own format "NAPG" v1, gzip with checksum; Jetpack Ink's own encoder uses native code, so it couldn't be unit-tested), `data/NotebookFiles` (temp file + fsync + atomic rename), saving 0.4 s after each change, on leaving and on ON_STOP, in an app-wide scope so closing the notebook can't cut a save off. A damaged page shows tinted and can't be written on. 10 + 7 unit tests.)_
  - The per-page file format, with its version number, written atomically.
  - Save shortly after each stroke, when leaving the notebook, and when the app goes to the background. Load on open.
  - A damaged file is never overwritten with an empty page.
  - Unit tests: save and load give the same strokes, a newer format version is refused safely, and an interrupted write keeps the old file.
- [x] 🔺 **2.6 Notebook editor** (layout below). ★ _(done 15 Nov, hands-off, following the layout draft below; the ★ check-in is in REPORT.md. `ui/notebook/NotebookCanvas`, `NotebookScreen`, `domain/ink/PageStrip` (fit, zoom, scroll limits; 11 unit tests). Settings → Notebooks has the double-tap fit choice and a debug "Draw with finger".)_
  - Templates drawn: lined, blank, grid, dot grid.
  - Pinch to zoom, pan with a finger, double-tap to fit (following the Settings choice).
  - The stylus draws and a finger never does (palm rejection).
  - Add and delete pages; a page-number badge.
  - Landscape and portrait, light and dark.
- [x] **2.7 Tools and the floating toolbar.** ★ _(done 15 Nov: `ui/notebook/InkToolbar` (48 dp, drag by the handle, tap it to tuck away), `ToolSettingsPanel` (tap the chosen tool again or ＋). Highlighter is see-through (alpha 0x66) and drawn under the pen. Stability = smoothing of the finished stroke (default 0). Logic in `domain/ink/EditorTools`, 14 unit tests.)_
  - The thin, draggable toolbar remembers where it was left and can collapse.
  - 3 pen slots: pressure-sensitive, colour swatches plus "More colours", 3 thickness presets, and a pop-up with a thickness slider and Stability.
  - Highlighter (see-through), whole-stroke eraser, and hold-to-straighten.
- [x] **2.8 Undo and redo** for drawing, erasing and adding or deleting pages, with unit tests. _(done 15 Nov: `domain/ink/NotebookInk`, 9 unit tests; 6 editor tests on the emulator.)_
- [x] 🔺 **2.9 Lasso select.** ★ _(done 15 Nov: loop → strokes with more than half their points inside; drag inside the box to move, the corner handle to resize, Delete / Done under it; undoable. `domain/ink/InkGeometry`, 12 unit tests. Checked on the emulator.)_
  - Draw a loop to select strokes, then move them, resize them (corner handles) or delete them. All undoable.
  - Unit tests for which strokes a loop selects.
- [x] 🔺 **2.10 Pen button and press hard to erase.** ★ _(built 15 Nov: the side button is read as `PointerButtons.isSecondaryPressed` (= `BUTTON_STYLUS_PRIMARY`) while touching or hovering; press-hard starts erasing once a pen-down shows real pressure at or above the level (default 90%). Unit-tested; waiting on the user to try both on the OPPO Pencil 2.)_
  - A side-button click toggles lasso ↔ the previous tool.
  - Pressing harder than the threshold erases whole strokes it touches, with a sensitivity slider and an off switch.
  - Unit tests for the toggle and the threshold. The user tests both on the OPPO Pencil 2: the emulator can't check pressure levels or the pen button.
- [x] **2.11 Thumbnails.** Notebook tiles show their first page (a cached picture, refreshed after edits). _(done 15 Nov: `data/Thumbnails` draws the first page to a PNG in the cache folder when that page is saved; `components/ThumbnailImage` loads it for the tile, reloading when the notebook changes. 2 emulator tests.)_
- [x] **2.12 Performance check on the tablet.** A long notebook (50+ pages of dense writing) stays smooth; fix what's slow. _(done on the emulator, 15 Nov; not yet run on the tablet. Measured with 51 pages of about 900 strokes (editor ⋮ → "Add 50 pages of test writing (debug)"), two full pages on screen, small back-and-forth swipes. Frame time: 150 ms (up to 1.3 s) at the start → about 50 ms with 32-stroke chunk layers (`f24e69a`) → **median 19 ms, p90 32 ms** with page pictures; the render thread went 13 ms → 1 ms, the app's drawing about 1 ms. Also: saving a full page 10× faster (buffered codec), strokes built in the background, scrolling doesn't re-lay-out, and rotating keeps the current page. Left as follow-ups in ISSUES.md: very deep zoom (no page picture there) and pre-drawing the next page.)_
- [x] **End of phase review** (15 Nov): `/code-review` found 8 things, 7 fixed (see ISSUES.md, Done). Installed on the tablet the same evening (database v4 upgrade checked).

### 2.13 Tablet feedback (15 Nov, from the user's first try)

Decided with the user: the top bar is **dark, like Notein, in both themes**; **Paste** appears from a **finger press-and-hold** on the page (lasso tool); the lasso menu's **Resize** shows handles on all corners and edges.

Batch 1, quick fixes:
- [x] **F1. Zoomed-in pages cover the top bar** (the buttons still work but can't be seen). Clip the pages to their area.
- [x] **F2. Pen side button does nothing.** The OPPO Pencil 2 sends its button as a Bluetooth key (`KEY_F21`, Android `KEYCODE_F21` = 334), not as a pen button. Catch that key (plus Android's stylus-button keys) and toggle the lasso. _(Done: `MainActivity.dispatchKeyEvent` → `PenButtonKeyEvents`; checked on the emulator with `input keyevent 334`; found with `adb shell getevent -lt` on the tablet.)_
- [x] **F3. Press hard to erase misses the hardest presses.** A pressure of exactly 100% was ignored; count it.

Batch 2, the page view:
- [x] **F4. Pages touch**, with no gap: just a thin dark line between them.
- [x] **F5. Top bar like Notein:** one thin dark bar of round icon buttons, no title. Left: Back. Middle pill: Undo, Redo. Right: Add page, ⋮.
- [x] 🔺 **F6. Fling scrolling:** a quick finger flick keeps moving (through many pages), easing to a stop, and sticks to the sideways direction while flicking, even zoomed in.
- [x] **F7. Double-tap to fit animates** (smooth, short: about 250 ms).
- [x] 🔺 **F8. Faster scrolling through a long, full notebook:** pages far from the current one show a quick, blurry preview first (like Notein) and sharpen when you stop.

Batch 3, pens:
- [x] 🔺 **F9. Press hard to erase, held:** erasing starts only when you press hard **and hold roughly still for about 0.5 s** (the time is a setting). Allow a harder "how hard" range.
- [x] **F10. Custom pen colours:** a colour wheel with a hex box.
- [x] 🔺 **F11. Hold to straighten, live:** after holding still, the line snaps straight right away, and you can keep moving its end; it gently snaps to common angles (horizontal, vertical, 45°). _(A pen held perfectly still may send no samples, so a 40 ms watcher also checks both holds. The emulator can't test F9: its pen pressure is always 1.0.)_
- [ ] **F12. Lasso menu:** Resize (handles on all corners and edges), Duplicate, Copy, Cut, Delete; Paste from a finger press-and-hold on the page.

- [ ] **End of phase (again, after 2.13):** run `/code-review`, fix what it finds, retest, then ask to merge into `main`.

## Folder screen layout (2.2), draft to confirm

```
┌ ←  ELECTENG 292  ▌ELECTENG 292                [Tasks|Calendar|Timeline]  ⋮ ┐
│  [ All | Note | Folder ]                                                   │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                                       │
│  │ ▭▭▭  │ │ ≡≡≡  │ │ ≡≡≡  │ │ ▭▭▭  │                                       │
│  └──────┘ └──────┘ └──────┘ └──────┘                                       │
│  tutorials Lecture 3  Tut 5     practice                                   │
│  2 items  12 Nov·8p  10 Nov·3p  5 items                                    │
│  ── Overdue ───────────────────────────                                     │
│  task cards … (as today)                                                    │
│                                                               ( ＋ )        │
└────────────────────────────────────────────────────────────────────────────┘
```
- The grid of subfolders (first) and notebooks sits above today's task sections, in one scroll.
- **＋** offers New folder and New notebook.
- A subfolder shows its own name, with the top folder's course tag, and no task sections or tabs.
- Back goes up one level. Each tile's ⋮ has Rename and Delete.
- Reference: `design-reference/notein-inside-course-folder.jpg`.

## Notebook editor layout (2.6), draft to confirm

```
┌ ←  Lecture 3            ↶  ↷                         ＋page   ⋮ ┐
│ ┌──┐ ┌─────────────────────┐ ┌─────────────────────┐ ┌────     │
│ │✒ │ │ ─────────────────── │ │ ─────────────────── │ │ ───     │
│ │✒ │ │ ─────────────────── │ │ ─────────────────── │ │ ───     │
│ │✒ │ │ ─────────────────── │ │ ─────────────────── │ │ ───     │
│ │▬ │ │                     │ │                     │ │         │
│ │⌫ │ │                     │ │                     │ │         │
│ │◌ │ │                     │ │                     │ │         │
│ │●●│ │                     │ │                     │ │         │
│ │●●│ │                     │ │                     │ │         │
│ │ ‑│ │                     │ │                     │ │         │
│ └──┘ └─────────────────────┘ └─────────────────────┘ └────     │
│ (1)                                                            │
└────────────────────────────────────────────────────────────────┘
```
- A slim top bar: back, title, undo and redo, add page, ⋮ (template, delete page).
- Pages sit side by side and scroll sideways. A double-tap fits the page to the screen.
- The floating toolbar on the left (draggable, collapsible): 3 pens, highlighter, eraser, lasso, colours, thicknesses.
- A page-number badge sits at the bottom left.
- Reference: `design-reference/doc ui reference.jpg` and `pen selection tab.jpg`.
