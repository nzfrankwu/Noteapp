# Phase 5: Productivity extras (text boxes, split view, snip between notebooks)

Text boxes, two notebooks open side by side in the app, a recent notebooks panel, and moving things between the two notebooks (IDEA.md §2.5, §2.7, §2.8 and §5).
Work happens on branch `phase-5`; it's merged into `main` at the end, with the user's OK.
- ★ marks a check-in with the user (skipped in hands-off mode; listed in REPORT.md instead).
- 🔺 marks a difficult or high-stakes step. Before starting one, tell the user so they can switch to a stronger model or higher effort.

## Why
Phase 4 is merged into `main` (`aa342b5`) and installed on the tablet. IDEA.md §5 says Phase 5 is **"Text boxes, in-app split view of two notebooks, snip between split notebooks"**.

The user added more (16 Nov):
- a recent notebooks panel, opened from a button by Back
- dragging a notebook from that panel to split the screen
- two split modes
- a divider handle with a menu, and snap-to-close at the edges

These additions are written into IDEA.md §2.8 and the Phase 5 row of §5; the user OK'd it by approving the plan.

**Mode:** hands-off (the user said "continue on in hands off mode" on 16 Nov).

## Decided with the user (16 Nov 2026)
- **Text boxes are simple:** typed text with one size, one colour and Bold on/off for the whole box. The size is easy to change (A− / A+ with the number shown).
- **Adding one:** a **"T" button in the top bar's centre pill**, next to Image (like Notein's split-screen screenshot). Tap it, then tap the page where the box goes.
- **Split view:** one shared top bar and pen toolbar acting on **the pane last touched, which gets a blue outline**. Writing or tapping in the other pane makes it active.
- **Recent notebooks panel** (like `design-reference/recent doc tab.jpg`):
  - a **panel button next to Back** opens a panel on the left
  - it shows the recently opened notebooks as thumbnail tiles (title; date · page count)
  - **dragging a tile onto the page** opens it in split view beside the current notebook
- **Two split modes:** **side by side** and **top and bottom**, chosen from the divider menu. The first split uses side by side in landscape and top and bottom in portrait; after that the choice is remembered.
- **The divider** has a small **⋮ handle** in the middle:
  - **tap it** for a menu: **Swap sides**, **Side by side / Top and bottom**, **Close split screen**
  - **drag it** to resize; letting go **very near an edge** closes the squeezed pane

## Proposed by me (easy to change)
- **Editing a text box:**
  - Tap a box with a finger, or with anything while T is on, to edit it. With T off, the pen still writes over boxes.
  - While editing, a small dark text bar shows A− / size / A+, the pen palette's colours, Bold, Delete and Done.
  - Drag the box's side handle to change its width. The text wraps, and the box grows downwards as you type.
  - A box left empty disappears. Each editing session is one Undo step.
- **The lasso treats text boxes like pictures:** move, resize (scales the text size), Duplicate, Copy, Cut, Delete and Bookmark. The eraser leaves them alone.
- **The recent panel:**
  - It slides over the page from the left (the page doesn't move); the panel button again or ✕ closes it.
  - It lists up to 30 notebooks, newest first. The open notebook(s) are outlined and can't be dragged.
  - **Tapping a tile** opens that notebook in place of the active pane's.
  - Each tile's ⋮ has **"Open beside this one"** (splitting without dragging).
  - **Dragging:**
    - Hold a tile and drag it over the pages. The half it will land in lights up: left/right in side-by-side mode, top/bottom in top-and-bottom mode.
    - Dropped onto an already split screen, it replaces that pane's notebook.
  - "Recently opened" is a list of notebook IDs in `data/ViewSettings.kt` (DataStore): no database change. Deleted notebooks are left out.
- **The same notebook can't be in both panes,** so two editors never save the same pages.
- **The divider:**
  - Free between 25% and 75%.
  - Released within about 8% of an edge, the squeezed pane closes. In that zone the pane dims and says "Release to close".
  - Released between 75% and 92%, it springs back to 75%.
  - The position is remembered.
  - **Close split screen** keeps the active pane.
  - **Back** leaves split view altogether.
- **Snip between the panes:** the screenshot button captures both panes, you crop, then **"Insert into: [left] / [right]"**, defaulting to the pane last written in (IDEA.md §2.7).
- **Copy and paste between notebooks:** the lasso's Copy/Cut becomes app-wide; pasting into another notebook copies the pictures into that notebook's own files.
- **The font** is Liberation Sans (OFL), already inside the app with PdfBox-Android (changed from Noto Sans in 5.2: nothing to download or add), so the screen, thumbnails and exported PDF break lines in the same places on any tablet. Bold is drawn thicker.

## Design (data safety, IDEA.md §5a)
- **No database change in this phase.** Text boxes live in the page files with strokes and pictures.
- 🔺 **Page file format 3** (`domain/ink/PageInkCodec.kt`):
  - text boxes after the pictures: ID, text, left, top, width, size, colour, bold
  - written **only when a page has text boxes**, so every other page is saved exactly as now, byte for byte
  - formats 1 and 2 still read
  - an older app build can't read a format 3 page: that's a `PageFileException`, and the file is never overwritten
- The split position, split mode and recent list go in `data/ViewSettings.kt` (DataStore).
- **Text layout is shared:** one `TextBoxLayout` (StaticLayout + the bundled font) for the page layers, thumbnails, hit-testing and PDF export, so all four agree.
- **Split view:** `SplitNotebookRoute(first, second, mode)`; one `NotebookViewModel` per notebook (`viewModel(key = …)`). `NotebookScreen` is split into a `NotebookPane` (canvas, overlays, scroller, page badge) and a host (top bar, toolbar, sheets, snackbars) that works on the active pane.
- **Divider rules** are pure Kotlin in `domain/SplitLayout.kt`, unit-tested.
- **App-wide clipboard** in `AppContainer`; pasting pictures into another notebook copies their files.

## Steps (branch `phase-5` from `main`; ★ = check-in, 🔺 = difficult)
- [x] **5.0** Write `PHASE-5.md`, update IDEA.md (§2.8, §5), HANDOFF.md and ISSUES.md. Commit.

**Text boxes**
- [x] 🔺 **5.1 Text box data** (16 Nov, hands-off). `domain/ink/InkText.kt` keeps the box's last laid-out `height`, so hit-testing and the lasso need no Android text layout. The text is stored as length + UTF-8 bytes (no 64 KB `writeUTF` limit), at most 20,000 characters. `NotebookInk.commitText(before, after)` turns one editing session into one undo step (new → AddTexts, emptied → RemoveTexts, changed → ReplaceTexts). The editor saves `ink.content(page)`, so a save always includes every kind of item. 18 unit tests in `PageTextsTest`.
  - `InkText` (like `InkImage`), `PageContent.texts`
  - codec format 3, `NotebookFiles.read/write`
  - `InkEdit.AddTexts` / `RemoveTexts` / `ReplaceTexts` with undo/redo
  - unit tests: round trip, old formats, byte-identical pages without text, damaged files, undo/redo
- [x] **5.2 Drawing text** (16 Nov, hands-off). Changed from the plan: the font is **Liberation Sans, which PdfBox-Android already puts in the app** (no download, no new file); bold is "fake bold" (thicker strokes), so bold and normal break lines identically. `data/TextBoxLayout.kt` (StaticLayout at 8× page units, line spacing 1.15, cached) draws the page, previews and tiles and gives the PDF its lines. 7 emulator tests in `TextBoxLayoutTest`. Was: `TextBoxLayout`, page layers, thumbnails, hit-testing. Unit-test the line breaks and heights.
- [x] **5.3 Placing and editing** (16 Nov, hands-off). `ui/notebook/TextBoxEditing.kt`: the box being typed in is a BasicTextField over the page (same font, size and line height as the page drawing), with a dashed outline, a width knob just outside its right edge, and the text bar (− size +, 6 colours, Bold, Delete, Done) above it. T on: a pen or finger tap places a box; T off: a finger tap on a box edits it. Touching the page elsewhere, Back, Undo/Redo, picking a tool, leaving or the app going to the background all finish typing first. A new box copies the last box's size, colour and bold. 7 screen tests in `TextBoxesTest`. Was: the T button, text mode, tap-to-place, tap-to-edit, the text bar, the width handle, keeping the box above the keyboard.
- [x] **5.4 Lasso and clipboard for text** (16 Nov, hands-off). A selection with text boxes shows only the four corner handles (text scales evenly, and a short box's middle stays free to drag). The lasso loop picks boxes it touches or surrounds; a lasso tap, or a finger or pen held on a box, picks that box (boxes are above pictures). Boxes scale evenly (size and width together) and stay upright when the selection turns. Duplicate, Copy, Cut, Paste, Delete and Bookmark include them; Crop stays for a single picture. Unit tests in `PageTextsTest`, a screen test in `TextBoxesTest`. Was: select, move, scale, Duplicate, Copy, Cut, Delete, Bookmark.
- [x] 🔺 **5.5 PDF export of text** (16 Nov, hands-off). Each line from `TextBoxLayout.lines` is drawn with the embedded Liberation Sans subset (`PDType0Font`), its text matrix flipped upright in our top-down units; bold = fill + outline (0.035 × size). A box whose text the font can't encode (emoji, Chinese, tabs) is drawn by Android at 4 px per unit and placed as a picture. Two emulator tests in `PdfExportTest`: the text reads back from the PDF at the screen's line position, the font is embedded, bold red shows red; an emoji box becomes a picture. Was: embedded font subset, each line where it is on screen (selectable text); boxes with characters the font lacks are drawn as a picture. Emulator test reads the text back with `PdfText`. ★

**Split view**
- **Stopped here for the user's feedback** (16 Nov): the user asked to review Phase 4 and the text boxes (5.1–5.5) before split view.
- [ ] 🔺 **5.6 Editor split into pane + shared controls:** nothing visible changes; `NotebookEditorTest` must all pass.
- [ ] **5.7 Recent notebooks panel:** the panel button, the recent list (unit-tested), tiles, tap to open.
- [ ] 🔺 **5.8 Two notebooks split:** the route, two ViewModels, the active outline, both modes, the divider with its rules and ⋮ menu, Back.
- [ ] **5.9 Drag from the panel:** drag-and-drop with lit drop zones, replace on drop, tile ⋮ "Open beside this one", screen tests. ★
- [ ] **5.10 Between the panes:** app-wide clipboard (copies picture files), snip "Insert into".
- [ ] **5.11 Android split screen with other apps:** half and one-third screen, landscape and portrait. Under about 840 dp in the split direction, dragging to split is off and the panel says why. Fix any crowding.
- [ ] **5.12 Tablet check:** ask first; back up with `screenshots/tablet_snapshot.sh`, install, compare. No database upgrade.
- [ ] **End of phase:** `/code-review`, fix, rebuild and retest, then ask to merge into `main`.

## Feedback round 1 (17 Nov 2026, on Phase 4 and text boxes)
Done before split view, hands-off. The user's answers to my questions are in brackets.
- [x] **FB1 The bookmark sheet shows which task is picked** (17 Nov): a check-circle icon, a primary-container background, bold title, and "For: <task>" beside Save. The picked row gets a check mark and a stronger highlight.
- [x] **FB2 Long press a text box with a finger to edit it** (17 Nov) (as well as tapping it). The lasso still selects boxes: a lasso tap or loop, or the pen held on a box.
- [x] **FB3 Search and filters in both task pickers** (17 Nov; `LinkChoices.filter`, `TaskSearch` in `TaskLinkViews.kt`; the course chip opens a menu; tests in `LinkChoicesTest` and `NotebookEditorTest`) ("Link to a task" and the bookmark sheet):
  - a search field (task title or course code)
  - **course:** this course (default), all other courses, or one chosen course; with no course, "All courses"
  - **due:** "Due soon" (default: due date still ahead, plus undated tasks) or "Already due" (due date passed, latest first); done tasks show in both, marked done [user]
  - the sheet's linked tasks stay at the top; the search narrows them too
  - pure filtering in `data/LinkChoices.kt`, unit-tested
- [x] **FB4 Two pens instead of three** (17 Nov; `PenDefaults` in `EditorTools.kt` does the one-time colour move, unit-tested; the saved key `pens_version` = 2) [user]: pen 3 goes. The defaults become pen 1 dark blue and pen 2 red. Existing settings still at the old defaults (pen 1 black, pen 2 blue) are moved to the new ones; anyone on pen 3 is switched to pen 1.
- [x] 🔺 **FB5 The bookmark pen** (17 Nov, hands-off). Database v7 (`MIGRATION_6_7`, schema hash `8d62aed9…`); MigrationTest 6→7 and "v6 data survives"; a real v6 → v7 upgrade on the emulator (`screenshots/upgrade_v7.sh`, old APK built from `3ea489b`): every row, all 50 page files, the old link and bookmark unchanged. The picture is the screen as drawn (the pages layer, like the snip tool), cut to the rectangle, at most 1400 px; it's only written when the bookmark is saved. The loop is orange. `domain/ink/BookmarkPen.kt` (unit-tested), two screen tests in `NotebookEditorTest`. Also: the filter chips got clear colours (filled + ✓ when on, outlined when off) after an emulator check showed them invisible on the dialog. a new toolbar tool after the lasso, with two modes like a lasso: **free loop** and **rectangle**.
  - Selecting an area takes a picture of it (the rectangle around a loop [user]) and opens the bookmark sheet with that picture as the note, plus optional text. Cancel, or touching outside the sheet, closes it as now.
  - **Database version 7:** `task_bookmarks` gets an `image` column (nullable): the picture's file name. One `MIGRATION_6_7` (`ALTER TABLE … ADD COLUMN`), never destructive; old bookmarks keep no picture.
  - Pictures are files in the notebook's folder (`bookmarks/<uuid>.png`), so Phase 6 backs them up with the notebook. A bookmark's picture file is deleted only when the bookmark is deleted for good (not while Undo can bring it back).
  - Under the task, a bookmark with a picture shows it as a thumbnail beside "p. N" and its text.

## Verification
- **Every step:** `gradlew.bat assembleDebug testDebugUnitTest`, plus `connectedDebugAndroidTest` with `ANDROID_SERIAL=emulator-5554` (a run wipes the emulator's app data; rebuild it with `screenshots/p4setup.sh`).
- **Emulator screen tests:**
  - placing, editing and undoing a text box
  - the recent panel: tap-to-open, "Open beside this one"
  - switching panes, both modes, Swap sides, the divider closing a pane
  - snip "Insert into"
- **Scripted on the emulator:** dragging a tile from the panel.
- **Screenshots** in `screenshots/`, landscape and portrait, light and dark: text bar, text box, split both ways, the panel, Android split screen.
- **Exported PDF** with text boxes: read back and looked at.
- **Tablet (5.12):** stylus and keyboard feel, handwriting next to text, split view with the OPPO Pencil.
