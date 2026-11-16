# Handoff: where things stand

_Last updated: 17 Nov 2026 (Phase 5 built, reviewed and installed on the tablet (v7); waiting on the user's tablet check and the OK to merge). Keep this file current (see CLAUDE.md, "Session handoff notes")._

## Right now

- **Phases 1, 1b, 2, 3 and 4 are done and merged into `main`** (Phase 4 merged 16 Nov, `aa342b5`). History in `PHASE-1.md` … `PHASE-4.md` and `ISSUES.md`.
- **Current phase: Phase 5, Productivity extras.** The plan is **`PHASE-5.md`**: text boxes, split view of two notebooks, a recent notebooks panel with drag-to-split, and moving things between the panes. IDEA.md §2.8 and §5 were updated (user's OK by approving the plan, 16 Nov).
- Phase 4 (Linking, `PHASE-4.md`) covered:
  - linking notebooks ↔ tasks
  - page bookmarks for tasks, with quick notes
  - selecting PDF text with a finger (Copy, Highlight, Underline, Bookmark)
  - the pop-up menus becoming icon pills
- **IDEA.md** §2.4, §2.6 and §5 were updated for bookmarks and text selection (user's OK, 16 Nov).
- **Branch:** `phase-5` (from `main`), pushed after each step.
- **Mode:** **Hands-off** for Phase 5 (the user said "continue on in hands off mode" on 16 Nov). List every 🔺 step done in REPORT.md.
- **The tablet runs Phase 5 with feedback rounds 2–4** (the build of `389dc33`), database **version 7** (latest install 17 Nov with the user's OK; compare PASS: every row, all 164 files, links and bookmarks unchanged). Backups in `backups/tablet-2026-11-17-before-v7/`, `…-before-fb2/`, `…-before-fb3/` and `…-before-fb4/` (with the page files). Never install a build older than v7 on it: Room can't downgrade. Pages with text boxes are page format 3.
- **The emulator runs Phase 5** (database v7).
- Unit tests and the full emulator suite pass at the end of Phase 5 (count in the latest commit message).

## Next steps (in order)

1. **Phase 5 is built and reviewed** (17 Nov, hands-off): text boxes (5.1–5.5), the user's feedback round FB1–FB5 (bookmark sheet ✓, long-press edit, task search/filters, two pens, the bookmark pen = database **v7**), the pane/host split (5.6), the recent panel and split view (5.7–5.9), between panes (5.10), narrow windows (5.11). The end-of-phase review's 10 findings are fixed (PHASE-5.md). REPORT.md is the Phase 5 report.
2. **Waiting on the user:**
   - **Trying Phase 5 on the tablet** (installed 17 Nov, v7; test steps in REPORT.md).
   - **The OK to merge `phase-5` into `main`.**
   - **Feedback rounds 2–4 (FB6–FB15) are done and on the tablet** (17 Nov; PHASE-5.md). The user is trying them.
3. Then plan **Phase 6** (Google Drive and backup; IDEA.md §5: must be done before real use).
4. Older follow-ups (ISSUES.md, not blocking):
   - very deep zoom has no page picture
   - pre-draw the next page
   - a big lasso move pauses on release
   - pen widths (F22)
   - deleted notebooks without a folder (Phase 6)
5. Still-unreported tablet checks: a reminder with the app swiped away (ColorOS); "Last synced" moving on its own.

## Phase 4 code map (new)

- `data/db/TaskLinks.kt`, `LinkDao.kt`: the tables.
- `data/LinkRepository.kt`, `data/LinkChoices.kt`: the pickers' lists.
- `data/PdfText.kt`: PDF words.
- `domain/Bookmarks.kt`, `domain/PdfCoords.kt`, `domain/ink/TextSelection.kt`.
- `ui/task/LinkNotebookSheet.kt`, `BookmarksSection.kt`.
- `ui/components/Pickers.kt`, `TaskCard.kt` (`LocalTaskNotebooks`).
- Phase 5: `ui/notebook/NotebookPane.kt` (`PaneState`), `NotebookWorkspace.kt` (split view), `RecentPanel.kt`, `TextBoxEditing.kt`; `data/TextBoxLayout.kt`; `domain/SplitLayout.kt`, `domain/ink/BookmarkPen.kt`, `domain/ink/InkText.kt`.
- `ui/notebook/`:
  - `NotebookLinks.kt`, `TaskLinkViews.kt` (the chip and the task picker)
  - `BookmarkSheet.kt`, `IconMenu.kt`
  - `TextSel` / `TextHandle` in `NotebookCanvas.kt`, `TextMenu` in `NotebookScreen.kt`
- `PageStrip.focus`: opening at a bookmark.

## Tablet database safety copy

- **`backups/tablet-db-2026-11-15-before-v2/`**, **`…-before-v3/`** and **`…-before-v4/`** (git-ignored, local only) hold the tablet's database from just before the B.3 and B.8 upgrades. It contains personal Canvas names but no secrets. Keep it until Phase 1b is merged and the user is happy; never commit it.
- How it was made, for next time (ask the user first):
  - force-stop the app
  - `adb -s <tablet> exec-out run-as com.nzfrankwu.noteapp cat databases/<f>` for `noteapp.db`, `-wal` and `-shm`
  - check it by opening a copy with Python's `sqlite3`, printing counts only
- **Never install an older build than the tablet has** (it's now heading to database v3): Room can't downgrade, so the app would crash at start. Data stays safe, and a current build fixes it.

## Waiting on the user

- **Phase 2 on the tablet** (installed 15 Nov; the user is checking it):
  - writing with the OPPO Pencil 2: lag, pressure, palm rejection
  - the pen's side button: does one click switch to the lasso and back?
  - press hard to erase: is 90% the right level?
  - a long notebook stays smooth (editor ⋮ → "Add 50 pages of test writing (debug)")

- **Tablet testing** of B.1–B.6 (installed; see `REPORT.md` for the steps).
- **Tablet checks not yet reported:**
  - a test reminder arriving with the app swiped away (ColorOS)
  - "Last synced" moving on its own over several hours (background sync)

  Ask about these when convenient.
- **Their Canvas sign-in on the tablet works** (go/no-go passed 15 Nov). Their real courses and assignments are on the tablet.

## Decisions to remember

- **The app name** is spelled "Noteapp". The app ID `com.nzfrankwu.noteapp` never changes.
- **Tasks:**
  - Tasks belong to courses; folders link to at most one course.
  - Overdue comes only from the real due date.
  - A due date without a time means 11:59 pm; a target date without a time means 9:00 am.
- **Canvas is read-only (GET only).** Canvas tasks can't be deleted unless removed from Canvas. Auto-status only moves forward, once per new submission.
- **Phase 1b:**
  - **Course colours:** "next free colour" (the user's choice).
    - Courses are sorted by `created_at` (ties by code), and the nth course gets colour n mod 7 (`CourseColorKey.slotsInOrder`).
    - `MainActivity` feeds the course list into `NoteAppTheme(courses = …)`. Codes not yet saved fall back to a fixed hash.
    - The palette order alternates hues, so reordering it recolours courses. Red, orange and amber are kept out.
  - **Days left:**
    - amber for "Due today" up to "2 days left"
    - a task due earlier today says "Was due today" (red)
    - the Overdue badge stays alongside "N days overdue"
  - **"Available from" (B.3):**
    - `Task.availableAt` is Canvas's `unlock_at` (not `lock_at`), Canvas-owned and refreshed on every sync.
    - It's null for your own tasks and for Canvas assignments without an unlock date. The timeline then uses `createdAt` (for Canvas tasks, when Noteapp first imported them).
  - The calendar weeks run Monday to Sunday.
  - **Archiving (B.8):**
    - `courses.archived_at`
    - Home uses `observeActiveWithCourse()`; a course folder uses `observeAllWithCourse()`
    - Course filters list current courses only, and a filter left on a since-archived course resets to all
    - The task form's course picker shows current courses plus the task's own course
    - Reminders skip archived courses' tasks (`ReminderScheduler.apply`, plus a re-check in `ReminderReceiver`)
    - Course colours still count archived courses, so colours never shift

- **Phase 2 (15 Nov):** the main choices are listed at the top of `PHASE-2.md`. In short:
  - Jetpack Ink for drawing; pages side by side (like Notein)
  - notebooks and subfolders above the tasks in a folder
  - subfolders inherit the course, any depth
  - strokes in one file per page; Room v4 for the structure
  - pen button toggles lasso, press hard to erase, double-tap to fit (default fit height)
  - lasso moved from Phase 5 into Phase 2 (IDEA.md updated with the user's OK)

## Code map (app/src/main/java/com/nzfrankwu/noteapp/)

| Folder | What's in it |
|---|---|
| `data/db/` | Room database **v6** (`TaskNotebookLink`, `TaskBookmark`, `LinkDao` in `TaskLinks.kt`/`LinkDao.kt`) (pages: `width`, `height`, `pdf_file`, `pdf_page`): `Course` (+`archivedAt`), `Folder` (+`parentId`), `Task` (+`availableAt`), `Notebook`, `Page`, DAOs, `FolderPlace`, `Migrations.kt` (`MIGRATION_1_2` … `MIGRATION_5_6`) |
| `data/` | Repositories (`FolderRepository`, `TaskRepository`, `NotebookRepository`), `NotebookFiles` (page files), `Thumbnails`, `InkSettings` / `CanvasSettings` / `ViewSettings` (DataStore), `QuickAdd` |
| `domain/ink/` | Plain-Kotlin handwriting logic: `InkStroke`, `PageInkCodec` (file format), `NotebookInk` (undo/redo), `InkGeometry` (eraser, lasso), `PageStrip` (fit/zoom), `EditorTools` (tools, palette, pen button, press-hard, straighten, smoothing) |
| `ui/notebook/` | The editor: `NotebookScreen`, `NotebookCanvas` (drawing, gestures, lasso), `InkToolbar`, `InkBrushes` (our strokes ↔ Jetpack Ink), `NotebookViewModel` (loading, saving) |
| `domain/` | Plain-Kotlin rules: `DaysLeft`, `CourseColorKey`, `TaskRules` (overdue, Due soon sections, default times), `Reminders`, `CourseCodes`, `Links`, `SharedLink`, `DateText` |
| `canvas/` | `CanvasClient` (GET only), `CanvasParser`, `CanvasMerge`, `CanvasSync`, `CanvasSyncRunner`, `CanvasSyncWorker`, `CanvasAccount` (sign-in and out), `FakeCanvas` (debug; most assignments have "available from" dates) |
| `reminders/` | Alarm scheduling and receivers |
| `ui/` | `NoteAppRoot` (Navigation 3), with screens in `home/`, `folder/`, `task/`, `settings/`, `canvas/`; reusable parts in `components/` (incl. `CourseTag`, `DaysLeftText`); all styling in `theme/` (`Color`, `Shape`, `Dimens`, `StatusColors`, `CourseColors`, `Type`) |

`AppContainer.kt` creates everything (manual dependency injection), and `NoteAppApplication` re-plans reminders and keeps the background sync scheduled.

## Gotchas (save yourself time)

- **Shells:**
  - In PowerShell, run Gradle as `.\gradlew.bat …`.
  - In Git Bash, put `export MSYS_NO_PATHCONV=1` before `adb shell` commands that use device paths like `/sdcard/...`.
  - **adb isn't on the Git Bash PATH:** use `/c/Users/hiimf/AppData/Local/Android/Sdk/platform-tools/adb.exe`.
- **Emulator tests:** `$env:ANDROID_SERIAL = "emulator-5554"` before `connectedDebugAndroidTest`, so tests never run on the tablet.
  - That run **uninstalls and reinstalls the app on the emulator**, which wipes its data. Recreate sample data before taking screenshots: Settings → "Use fake Canvas (debug)", then Home → "Link courses" → "Create folder" ×3.
  - One test class only: add `"-Pandroid.testInstrumentationRunnerArguments.class=<full class name>"`.
  - Results are in `app/build/outputs/androidTest-results/connected/debug/*.xml`. Gradle `-q` prints nothing on success.
- **Tablet snapshot script:** `tablet_snapshot.sh <dir> [with-files]` (force-stop, copy db/-wal/-shm via run-as, md5 of every notebook file, optional tar of `files/notebooks`); saved in `screenshots/tablet_snapshot.sh` (finds the tablet by model OPD2405). Compare with `python screenshots/compare_dbs.py <before> <after>` (counts only). Ask the user before running it.
- **Tablet:** if `adb devices` doesn't list it, run `adb kill-server` then `adb start-server`. Install with `adb -s <tablet serial> install -r app/build/outputs/apk/debug/app-debug.apk`. Check the serial with `adb devices -l`; it's the OPD2405.
- **Emulator stylus handwriting is off** (`settings put secure stylus_handwriting_enabled 0`). Its "Try out your stylus" tutorial swallowed scripted typing.
- **To tap UI by text in scripts:**
  - `uiautomator dump`, pull the XML, find the node's bounds, then `input tap`.
  - Windows Python can't read `/tmp`, so pull the dump into a Windows path (use `cygpath -w`).
  - Matching is by substring, so "Edit" also hits "Tap Edit to add some". Use the exact content description (e.g. "Edit task").
  - Type text with `input text "Word%sword"` (`%s` = space).
- **Reading the emulator app's database:** `adb exec-out run-as com.nzfrankwu.noteapp cat databases/noteapp.db > file` (plus `-wal` and `-shm`), then open a copy with Python's `sqlite3`. This works because debug builds are debuggable. Emulator only, unless the user agrees.
- **Emulator sample data:** `bash <scratchpad>/setup.sh`-style script: install, Settings → fake Canvas, Link courses → Create folder ×3 (scratchpad scripts may be gone in a new session; rewrite with the tap-by-text pattern).
- **Home remembers the last tab** (DataStore survives emulator test reinstalls): tests that open the real app must tap `home-tab-overview` first (see `NavigationTest`).
- **Testing an app upgrade for real:**
  - keep the old build's APK
  - install it on the emulator and add data
  - snapshot the database
  - `install -r` the new build
  - compare the databases
- **Pinned versions:**
  - `kotlinx.serialization` is at 1.9.0 (the newest for Kotlin 2.2, which comes with AGP 9.5.1). It must stay at or above Room's needs, or `MigrationTest` crashes.
  - Libraries built for Kotlin 2.3+ won't fit until Kotlin is upgraded.
- **`screenshots/p4demo.sh`** rebuilds the Phase 4 demo data: `lecture7.pdf` imported and linked to Assignment 3.
- **Real upgrade test v5 → v6 (4.1):** `screenshots/upgrade_v6.sh <Windows path of screenshots/app-v5.apk>` (the v5 APK built from `main` is kept there).
- **Real upgrade test, scripted (3.1):** `screenshots/upgrade_v5.sh <old APK as a Windows path>` installs the old build, makes sample data and a 51-page notebook, snapshots, installs the new build, snapshots again; then `python compare_dbs.py upgrade-v5/before upgrade-v5/after` compares every row and the page files' md5s. Pass the APK as `C:\...`: the script sets `MSYS_NO_PATHCONV=1`, so adb can't read `/c/...` paths.
- **The pen layer is ours now (F30):** `ui/notebook/WetInkLayer.kt` replaces Ink's Compose `InProgressStrokes`; finished strokes are removed by `InkHandoff` after the page layer draws them (`drawStrokes` calls `handoff.drawn`). Its counting lives in `domain/ink/HandoffBook` (unit-tested). Debugging timing: log in `onStrokesFinished` and in `drawStrokes`.
- **`folders.course_id` is unique:** subfolders store NULL there (they inherit the course through `parent_id`). Test data with a subfolder must use NULL.
- **Sharing into Noteapp on the emulator:** open a PDF from Files (Downloads) → "Open with" → Noteapp. For pictures: `adb shell pm grant com.nzfrankwu.noteapp android.permission.READ_MEDIA_IMAGES`, then `am start -a android.intent.action.SEND -t image/png --eu android.intent.extra.STREAM content://media/external/images/media/<id> -n com.nzfrankwu.noteapp/.MainActivity` (ids from `content query --uri content://media/external/images/media --projection _id:_display_name`). `am` can't send a URI list, so SEND_MULTIPLE is untested by script.
- **Multi-window on the emulator:** `adb shell am start --windowingMode 6 -n com.nzfrankwu.noteapp/.MainActivity` puts Noteapp's task in multi-window mode (not a neat split, but `isInMultiWindowMode` is true), which is enough to test the MediaProjection path. Android 16's prompt shows "Share entire screen" because we pass `createConfigForDefaultDisplay()`.
- **Compose `GraphicsLayer.toImageBitmap()` gives a HARDWARE bitmap:** `getPixel` crashes on it; copy it (`ScreenCapture.readable`).
- **Test pictures:** `screenshots/make_test_png.py out.png W H [--alpha]`; push to `/sdcard/Pictures/` and send `MEDIA_SCANNER_SCAN_FILE` so the Recent grid sees them. The emulator's photo permission dialog offers "Allow limited access / Allow all / Don't allow".
- **Test PDFs:** `screenshots/make_test_pdf.py out.pdf 100` writes a slide PDF with no libraries; push it to `/sdcard/Download/` and it shows in the system picker's Recent. Emulator tests make PDFs on the device with `android.graphics.pdf.PdfDocument` (`PdfImportTest`).
- **Heredocs:** a Python heredoc in the Bash tool failed with "unexpected EOF" twice for no clear reason; writing the script to a file with the Write tool always works.
- **Both the tablet and the emulator are often plugged in:** always set `ANDROID_SERIAL=emulator-5554` / `adb -s emulator-5554`.
- **Real upgrade test, old APK:** build `main` in a worktree (`git worktree add ../Noteapp-v3 main`, copy `local.properties`, `gradlew assembleDebug` there). Removing that worktree fails with "Filename too long"; delete it with `cmd /c rmdir /s /q` and `git worktree prune`.
- **After a deliberately broken migration test, rebuild** (`assembleDebug`) before installing: the broken-migration test leaves a broken APK in `app/build/outputs`.
- **`screenshots/p4setup.sh`** reinstalls, makes fake-Canvas sample data and opens a new notebook in Engineering Electromagnetics (every emulator test run wipes the app's data).
- **PdfBox's `PDFTextStripper.writeString`** gets a whole run of text (often a line), not one word: `PdfText` splits it into words itself.
- **Scripted finger hold-and-drag on the emulator:** `adb shell "input touchscreen motionevent DOWN x y; sleep 0.9; input touchscreen motionevent MOVE …; input touchscreen motionevent UP x y"` (one shell call). `screenshots/lecture7.pdf` (5 text slides) is in the emulator's Downloads.
- **`screenshots/results.py`** (run from the project folder) prints the last emulator test run's totals and failures.
- **Snackbar in a `LaunchedEffect(event)`:** clear the event only AFTER `showSnackbar` returns; clearing it first re-keys the effect and cancels the snackbar.
- **Helper scripts are saved in `screenshots/`** (git-ignored, this PC only): `ui.py` (tap by text or description), `setup.sh` (fake Canvas + 3 course folders), `perf_setup.sh` (install, set up, make a notebook, add 50 test pages), `framestats.py` (per-stage frame times). Run them from that folder with Git Bash and `PYTHONIOENCODING=utf-8`.
- **Measuring editor speed on the emulator:**
  - Per-stage frame times: `adb shell dumpsys gfxinfo com.nzfrankwu.noteapp framestats` (columns `DrawStart`, `SyncQueued`, `IssueDrawCommandsStart`, `SwapBuffers`, `FrameCompleted`); a small Python script summarised medians (rewrite if the scratchpad is gone).
  - Finger scroll: `adb shell input touchscreen swipe x1 y1 x2 y2 ms`. Double-tap: two `input touchscreen tap` about 0.12 s apart.
  - Give long emulator scripts a `timeout` per adb call and run them in the background: one was killed mid-way and its leftover Back presses left the app.
- **How the ink is drawn (2.12):** `NotebookCanvas` = desk and paper canvas → one `PageInkLayer` per page on screen → overlay canvas (lasso loop/box, eraser ring) → Jetpack Ink's wet layer. `PageInkLayer` splits a page's strokes into 32-stroke `InkChunk`s (`InkChunks.reconcile` reuses untouched chunks by object identity), each a `graphicsLayer` drawn once. Camera, lasso and `inkScale` live in `EditorViewState` and are read only in draw or layer blocks, so scrolling doesn't recompose.
- **Jetpack Ink gotchas:**
  - `CanvasStrokeRenderer.draw(canvas, stroke, matrix)` does NOT move the canvas: the matrix only tunes quality. `canvas.concat(matrix)` first.
  - The Compose `InProgressStrokes` takes a Compose `Matrix` (screen → desk), and draws every pointer nobody consumed. Our gesture layer consumes fingers in `PointerEventPass.Initial` (palm rejection) and stylus events for eraser/lasso.
  - Keep `InProgressStrokes`' inputs the same objects (`InkLayerInputs`), or it restarts and cancels the stroke being written.
  - Ink's own `encode`/`decode` use native code, which JVM unit tests can't load; we save with our own `PageInkCodec`.
  - Emulator stylus input: `adb shell input stylus swipe x1 y1 x2 y2 ms`, or `input stylus motionevent DOWN/MOVE/UP x y` (pressure is always 1.0 there).
  - Screen tests draw with "Draw with finger" on (`InkToolSettings(drawWithFinger = true)`), since test touches are fingers.
- **Pen button keys:** the OPPO Pencil 2's side button is a Bluetooth key (`KEY_F21`, Android 334), seen with `adb shell getevent -lt` (no device argument: it takes only one). Test it on the emulator with `adb shell input keyevent 334`.
- **Scripted pen holds on the emulator:** `input stylus motionevent MOVE` at the same spot isn't delivered again, so a held pen sends nothing; use `sleep 0.8` between MOVEs (the 40 ms hold watcher in `NotebookCanvas` handles it). Scripted `input touchscreen swipe` sends too few moves to fling: test flings with Compose tests (`NotebookEditorTest`).
- **Ink kinds (`InkTool`)**: PEN (stock pressure pen = pressure Full), PEN_STEADY / PEN_PRESSURE_LOW / PEN_PRESSURE_MID (F22, our round-tip brushes in `InkBrushes.roundPen`), HIGHLIGHTER (flat), HIGHLIGHTER_ROUND (F18). Highlighter opacity is the colour's alpha (opaque = default 40%, `HighlightOpacity`). Use `tool.isHighlighter`, never `== HIGHLIGHTER`. Keys are frozen by `PageInkCodecTest.toolKeys_neverChange`.
- **Emulator: touches near the bottom edge (y > ~1500 of 1600) hit the system home gesture** and leave the app; keep UI out of `WindowInsets.mandatorySystemGestures`. Also `ui.py dump` takes ~2 s, longer than the scroller's 1.5 s fade: grab it straight after a swipe, in the same `adb shell` call.
- **Page readiness is Compose state** (`StrokeDrawables.isReady` reads a `mutableStateMapOf`). Anything a composable checks before drawing must be state, or its layer never redraws when it changes (this was F16's blur bug).
- **Lasso rotation (F14)** exists only during the rotate drag (`LassoState.Selected.angle`); on pen-up the strokes are saved turned and a fresh, axis-aligned selection is made, so move and resize never deal with a turned box.
- **Python heredocs in Git Bash mangle non-ASCII (– ✓ etc.):** write the script with the Write tool and run it, or set `PYTHONUTF8=1`.
- **The editor's gesture handler** (`NotebookCanvas.handleGestures`) now also does: fling + axis lock (`domain/ink/PanPhysics.kt`), animated fit (`EditorViewState.animateTo`), press-hold erase (`PressHold`), live straight line (`Straighten`, `StraightLine`), lasso handles (`LassoBox`, `BoxHandle`), finger long-press → Paste bubble.
- **Rotating the emulator from a script:** `settings put system accelerometer_rotation 0` first (it can switch itself back on), then `settings put system user_rotation 1` (portrait) or `0` (landscape); wait about 5 s.
- **`InkPerformanceProbe`** (androidTest) only logs timings (`adb logcat -s InkPerf`); like every connected test run it uninstalls the app from the emulator afterwards.
- **Every `connectedDebugAndroidTest` run wipes the emulator app's data.** Take screenshots before running the suite, or recreate data with the scratchpad `setup2.sh` (fake Canvas, 3 course folders, and in "Electronics Design 2": 2 subfolders and 2 notebooks).
- **Emulator UI scripting:** a `ui.py` helper (tap by exact text / content description via `uiautomator dump`) lived in the scratchpad; rewrite it if gone. Set `PYTHONIOENCODING=utf-8` (screen text has "＋" etc.).
- **Database changes:** follow the steps at the top of `Migrations.kt`.
  - a version bump
  - a migration declared **above** `ALL_MIGRATIONS`
  - a new column as the **last** entity field
  - a new line in `SchemaHistoryTest`
  - new checks in `MigrationTest`

  Never destructive. The guard tests (`SchemaHistoryTest`, `MigrationListTest`, `DataSafetyRulesTest`, `MigrationTest`) fail otherwise.
- **Secrets:** never create, read or print files in `C:\Users\hiimf\keys\` (signing key and passwords). Never read the in-app browser's cookie store.
- **`REPORT.md`, `REPORT-*.md` and `screenshots/` are git-ignored**: local only. `REPORT.md` is the Phase 2 report so far; `REPORT-phase-1.md` and `REPORT-phase-1b.md` are the older ones.
