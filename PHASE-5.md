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
- **A bundled font** (Noto Sans regular and bold, OFL licence, under 1 MB), so the screen, thumbnails and exported PDF break lines in the same places on any tablet.

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
- [ ] **5.0** Write `PHASE-5.md`, update IDEA.md (§2.8, §5), HANDOFF.md and ISSUES.md. Commit.

**Text boxes**
- [ ] 🔺 **5.1 Text box data:**
  - `InkText` (like `InkImage`), `PageContent.texts`
  - codec format 3, `NotebookFiles.read/write`
  - `InkEdit.AddTexts` / `RemoveTexts` / `ReplaceTexts` with undo/redo
  - unit tests: round trip, old formats, byte-identical pages without text, damaged files, undo/redo
- [ ] **5.2 Drawing text:** `TextBoxLayout`, page layers, thumbnails, hit-testing. Unit-test the line breaks and heights.
- [ ] **5.3 Placing and editing:** the T button, text mode, tap-to-place, tap-to-edit, the text bar, the width handle, keeping the box above the keyboard.
- [ ] **5.4 Lasso and clipboard for text:** select, move, scale, Duplicate, Copy, Cut, Delete, Bookmark.
- [ ] 🔺 **5.5 PDF export of text:** embedded font subset, each line where it is on screen (selectable text); boxes with characters the font lacks are drawn as a picture. Emulator test reads the text back with `PdfText`. ★

**Split view**
- [ ] 🔺 **5.6 Editor split into pane + shared controls:** nothing visible changes; `NotebookEditorTest` must all pass.
- [ ] **5.7 Recent notebooks panel:** the panel button, the recent list (unit-tested), tiles, tap to open.
- [ ] 🔺 **5.8 Two notebooks split:** the route, two ViewModels, the active outline, both modes, the divider with its rules and ⋮ menu, Back.
- [ ] **5.9 Drag from the panel:** drag-and-drop with lit drop zones, replace on drop, tile ⋮ "Open beside this one", screen tests. ★
- [ ] **5.10 Between the panes:** app-wide clipboard (copies picture files), snip "Insert into".
- [ ] **5.11 Android split screen with other apps:** half and one-third screen, landscape and portrait. Under about 840 dp in the split direction, dragging to split is off and the panel says why. Fix any crowding.
- [ ] **5.12 Tablet check:** ask first; back up with `screenshots/tablet_snapshot.sh`, install, compare. No database upgrade.
- [ ] **End of phase:** `/code-review`, fix, rebuild and retest, then ask to merge into `main`.

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
