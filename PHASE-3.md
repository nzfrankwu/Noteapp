# Phase 3: Images & PDFs

PDFs, images, PDF export, sharing into Noteapp, and the screenshot tool (IDEA.md §2.6, §2.7 and §5).
Work happens on branch `phase-3`; it's merged into `main` at the end, with the user's OK.
- ★ marks a check-in with the user.
- 🔺 marks a difficult or high-stakes step. Before starting one, tell the user so they can switch to a stronger model or higher effort.

## Why
Phase 2 (handwriting notebooks) is merged into `main` (`5971e43`). IDEA.md §5 says Phase 3 is **"Insert images, import PDFs and annotate, export annotated PDF, 'Share to NoteApp'"** (details in §2.6). Today notebooks hold handwriting only:
- page size is per notebook (A4)
- page files (`PageInkCodec` v1) can hold strokes only
- sharing into the app accepts text links only
- there is no PDF or image code and no FileProvider

The goal is to let the user import lecture PDFs, write on them, drop images onto pages, export the annotated PDF and share PDFs or images into Noteapp from other apps, without risking existing notes (IDEA.md §5a).

## Decided with the user (16 Nov 2026)
- **PDF export uses PdfBox-Android** (`com.tom-roush:pdfbox-android`, Apache 2.0, about 3–5 MB). The user OK'd the library. Exported PDFs keep the original pages (text stays sharp and selectable) with the ink on top as vector shapes.
- **A shared image asks each time** (a small sheet):
  - "Add to a notebook": pick a notebook; the image goes on a new page at the end.
  - "New notebook in a folder": pick a folder.
- **A shared PDF becomes a new PDF notebook** in a chosen folder.
- **Writing on the last page of a PDF notebook adds a blank page after it**, the same as plain notebooks (F13).
- **Images are objects** under the ink:
  - inserted in the middle of the current page
  - the lasso selects them (a loop, or a lasso tap on one) to move, resize, rotate, duplicate, copy, cut, paste or delete, all undoable
- **Image menu like Notein** (`design-reference/insert image menu.jpg`): a panel drops down from the Image button:
  - header: ✓ (select several), "Image", ✕
  - **Recent Images**: a scrolling 3-column grid of the newest photos and screenshots on the tablet, tap to insert
  - **Photo Library** (Android's photo picker)
  - **Take Photo** (the camera app)

  The user OK'd the photo permission. With Android's "only selected photos" option, the grid shows just those. If permission is refused, the grid shows an "Allow access" button, and Photo Library still works.
- **Screenshot tool like Notein, capturing other apps too** (`design-reference/screenshot selection.jpg`). This pulls IDEA.md §2.7 "snip" forward from Phase 5; the user chose this, and IDEA.md §2.7 and §5 are updated to match. The flow:
  - A crop button dims the screen and shows a box with 8 handles over a still picture of the screen, with **Cancel / Confirm**.
  - **Confirm inserts the cropped part right away** as a selected image on the current page, ready to drag.
  - Noteapp alone on screen: the picture is the notebook view itself (no prompt).
  - In Android split screen or a floating window: Android's screen-capture prompt (it may appear each time, which IDEA.md accepts), then one frame of the whole screen.
  - An all-black capture (a protected app) shows a helpful message.
  - Snipping between two notebooks in Noteapp's own split view stays in Phase 5 with that split view.

## Design decisions (data safety, IDEA.md §5a)
- 🔺 **Database version 5** (`MIGRATION_4_5`, new columns last, never destructive; follow the steps at the top of `data/db/Migrations.kt`). Four nullable columns on `pages`:
  - `width`, `height`: null means the notebook's size
  - `pdf_file`: the file name inside the notebook's folder
  - `pdf_page`: the page index in that PDF

  Storing these per page means mixed page sizes and a future "insert PDF pages into a notebook" need no further migration.
- **Files stay inside the notebook's folder**, so Phase 6 can back up per notebook and upload only what changed:
  - `filesDir/notebooks/<id>/<pageId>.ink` (as now)
  - `…/pdf-<uuid>.pdf`: the imported PDF, copied in whole (written to a temp file, then renamed; never kept as a link to the outside file)
  - `…/images/<uuid>.jpg|png`: the inserted images
- **Imported files are never deleted in Phase 3**, even when their pages or images are deleted (undo and future Recently deleted need them). Clean-up is a Phase 6/7 follow-up for ISSUES.md.
- 🔺 **Page file format v2** (`PageInkCodec`): strokes, then an image list (id, asset name, left, top, width, height, rotation). Version 1 files keep loading. Version 2 is written only when a page has images, so pages with handwriting only stay byte-identical.
- **The eraser (and press-hard erase) never removes images**; delete them with the lasso.
- **Images are re-encoded on import:** the longest side is at most 2560 px, EXIF rotation is applied, and they're saved as JPEG 90, or PNG when the image has see-through parts. HEIC is decoded with `ImageDecoder` (Android 9+).
- **PDF pages are drawn with Android's built-in `PdfRenderer`** (no library):
  - one renderer per open file, on a single background thread
  - a page picture rendered at the zoom the view settles at, kept in a small memory cache
  - a tile of just the visible area when zoomed in very far
  - the F8 quick previews include the PDF page
- **Page units stay 1/72 inch**, which is the PDF's own unit (points), so a PDF page's size is its page size.
- **Page template:** PDF pages draw no paper pattern. Blank pages added to a PDF notebook use the notebook's template, which is Lined by default.
- **Password-protected or broken PDFs** get a clear message ("This PDF is password-protected …"), and nothing is created.

## Layout drafts (to confirm with the user at each ★, CLAUDE.md §2.10)
- **Folder ＋ menu:** New notebook · **Import PDF** · New folder. PDF notebooks show their first page on the tile with a small "PDF" tag. The filter row becomes **All · PDFs · Notebooks · Folders** (IDEA.md §2.1 asks for a PDF filter).
- **Editor:** two round buttons join the floating middle pill, after Undo and Redo, like Notein: **Image** and **Screenshot** (a crop icon). The right side stays Add page · ⋮.
  - Image opens the image menu (above). An image goes on the current page. "As a new page" is a long-press option on a grid image, or a switch in the menu header.
  - ⋮ gains **Export as PDF**.
- **Screenshot overlay:** the still picture fills the editor, dimmed outside the box. The box has corner and edge handles and starts at the middle half of the screen. Cancel and Confirm sit under the box, as in the reference.
- **Export:** a progress dialog, then a sheet with **Share…** (the Android share sheet, via FileProvider) and **Save to device** (the system "save as").
- **Share to Noteapp:** a small sheet with:
  - for an image: "Add to a notebook" / "New notebook in a folder"
  - a list to pick from: folders as a tree, or notebooks grouped by folder
  - for a PDF: the folder picker

  Reference: Notein's import sheet if a screenshot exists; otherwise describe this to the user before building it.

## Checklist
- [ ] 🔺 **3.1 Database version 5.** Add the four `pages` columns; record the schema in `SchemaHistoryTest`; extend `MigrationTest`; check that a deliberately broken migration is caught; upgrade a real v4 install with data on the emulator. Repository: `createPdfNotebook(folderId, title, pdfFile, pageSizes)` in one transaction; `addPage` copies the neighbouring page's size. Unit and emulator tests.
- [ ] 🔺 **3.2 Pages of different sizes.**
   - `domain/ink/PageStrip` uses per-page widths and heights: running left edges, a binary search in `pageAt`/`visiblePages`, pages centred top to bottom, `fit` and `clamp` per page, and the F20 scroller fraction.
   - Rewrite `PageStripTest`.
   - Plain notebooks must look and scroll exactly as before.
- [ ] 🔺 **3.3 Import a PDF.** ★
   - Folder ＋ → Import PDF (system file picker, `OpenDocument("application/pdf")`): copy the file in, read page sizes with `PdfRenderer`, create the notebook and open it.
   - New `data/PdfPages` (renderer, cache, tiles) drawn in `NotebookCanvas` layer 1, between the white paper and the ink. PDF pages are included in previews (F8) and thumbnails (`data/Thumbnails`).
   - Folder tile "PDF" tag and filter.
   - Measure with a 100-page lecture PDF on the emulator.
- [ ] 🔺 **3.4 Page items: images in the model.**
   - `domain/ink`: `InkImage`, `PageInkCodec` v2 (still reads v1), and `NotebookInk` holding strokes and images per page with undoable edits (add, remove, replace images).
   - `NotebookFiles` stores image assets.
   - Unit tests: v1 ↔ v2 round trips, a newer version refused, undo/redo of image edits.
- [ ] **3.5 Insert image and the image menu** (`insert image menu.jpg`). ★
   - The panel under the Image button.
   - **Recent Images** grid: `MediaStore` images newest first, small thumbnails loaded on a background thread, paged as you scroll.
   - Permissions: `READ_MEDIA_IMAGES` and `READ_MEDIA_VISUAL_USER_SELECTED` (Android 13/14+), `READ_EXTERNAL_STORAGE` up to Android 12. Handle "allow all", "selected photos" and "don't allow".
   - **Photo Library** (`PickVisualMedia`); **Take Photo** (`ACTION_IMAGE_CAPTURE` into a FileProvider cache file; no camera permission needed).
   - ✓ multi-select inserts the chosen images cascaded.
   - Decode, downscale, rotate per EXIF and save on a background thread.
   - Place it at the middle of the current page at about 60% of the page width, selected and ready to move.
   - "As a new page": a page sized to the image's shape, the image filling it.
   - Draw images under the ink in `PageInkLayer`, with a bitmap cache sized to the zoom.
- [ ] 🔺 **3.6 Lasso with images.**
   - Selection includes images: a loop touching or crossing the image's box, or a lasso tap on the image.
   - Move, resize and rotate (reuse `LassoBox.map` maths for image boxes).
   - Duplicate, copy, cut, paste, and delete, all undoable.
- [ ] 🔺 **3.7 Export as PDF** (add PdfBox-Android; `PDFBoxResourceLoader.init` in `NoteAppApplication`). ★
   - Pages from a PDF: import the original page and draw the ink over it.
   - Other pages: a new page with the paper pattern (`domain/PagePattern`), the images, then the ink.
   - Ink as vector: fill each stroke's outline from Jetpack Ink's `Stroke.shape` outlines; the highlighter at its opacity, via the PDF's transparency setting.
   - Add a `FileProvider` (cache-only paths) for Share; Save to device via `CreateDocument`.
   - Check that the exported PDF opens correctly in another PDF app.
- [ ] **3.8 Share to Noteapp.** ★
   - Manifest: `SEND`/`SEND_MULTIPLE` for `application/pdf` and `image/*`, plus `VIEW` for PDFs ("Open with").
   - `MainActivity.takeRequests` copies the shared files into a private import folder at once (the read permission on a shared file can lapse), then `NoteAppRoot` shows the destination sheet (layout above).
   - Several images: one page each.
- [ ] 🔺 **3.9 Screenshot tool** (`screenshot selection.jpg`; update IDEA.md §2.7 and §5 to say snip moved to Phase 3). ★
   - **Noteapp on its own:** capture the editor area with Compose's `GraphicsLayer.toImageBitmap()`.
   - **In split screen / multi-window** (`Activity.isInMultiWindowMode`): `MediaProjection`. This needs:
     - the consent prompt each time
     - a short-lived foreground service of type `mediaProjection` (Android 14+ rule; manifest `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION`)
     - one frame via `VirtualDisplay` + `ImageReader`, then stop at once
   - The overlay: dim the screen, show the crop box with 8 handles (reuse the `BoxHandle`/`LassoBox.dragged` maths), Cancel / Confirm.
   - Confirm: crop, save as an image asset, and insert it on the current page, selected.
   - An all-black capture shows "That app doesn't allow screenshots".
   - Test on the emulator in split screen with Chrome.
- [ ] **3.10 Tablet and data-safety check.**
   - Before installing on the tablet, copy its database (HANDOFF.md steps, ask first), then confirm the v4 → v5 upgrade keeps all 10 courses, 4 folders and 124 tasks, and every notebook.
   - The user tries a real lecture PDF, images, export and sharing on the tablet.
- [ ] **End of phase:** `/code-review`, fix, rebuild and retest (unit tests + full emulator suite), then ask the user to approve merging into `main`.

## Files to change (main ones)
- Database: `data/db/Page.kt`, `NoteAppDatabase.kt` (VERSION 5), `Migrations.kt`, `app/schemas/5.json`; tests `SchemaHistoryTest`, `MigrationTest`.
- Storage: `data/NotebookRepository.kt`, `data/NotebookFiles.kt`, `domain/ink/PageInkCodec.kt`, `domain/ink/NotebookInk.kt`, plus new `data/PdfPages.kt`, `data/ImageImport.kt`, `data/PdfExport.kt` and `domain/ink/InkImage.kt`.
- Editor: `domain/ink/PageStrip.kt`, `ui/notebook/NotebookCanvas.kt`, `PageInkLayers.kt`, `NotebookViewModel.kt`, `NotebookScreen.kt`, `EditorTopBar.kt`, `domain/ink/LassoBox.kt`, `InkGeometry.kt`.
- Image menu and screenshot: new `ui/notebook/ImageMenu.kt`, `ui/notebook/SnipOverlay.kt`, `data/RecentImages.kt` (MediaStore), `capture/ScreenCaptureService.kt`; `IDEA.md` §2.7 and §5 (snip moved to Phase 3, with the user's OK).
- Library and sharing: `ui/folder/FolderScreen.kt`, `FolderViewModel.kt`, `FolderLibrary.kt`, `ui/components/LibraryTiles.kt`, `MainActivity.kt`, `ui/NoteAppRoot.kt`, `AndroidManifest.xml`, `res/xml/file_paths.xml`, `gradle/libs.versions.toml`, `app/build.gradle.kts`.

## Reuse
- **Atomic file writes:** `NotebookFiles.write` (temp file, fsync, rename).
- **Preview pipeline:** `NotebookViewModel.requestPreviews`, `InkSketch.draw`, `StrokeDrawables` readiness as Compose state (the F16 lesson: anything a layer waits for must be state).
- **Lasso maths:** `LassoBox.map`, `rotation`, `dragged`; `InkGeometry.pointInPolygon` and `segmentsCross` for image hit-tests.
- **Paper pattern:** `drawPaperPattern` (`ui/components/PaperBackground.kt`) and `domain/PagePattern` (shared by the editor, thumbnails and export).
- **Shared-content handling:** the `takeRequests` / `shareRequest` pattern in `MainActivity`, which already does this for text links.

## Verification
- Every step: `gradlew.bat assembleDebug`, `testDebugUnitTest`, and the emulator suite for database and editor steps (`$env:ANDROID_SERIAL="emulator-5554"`). The suite wipes the emulator app's data, so take screenshots first.
- Migration: schema 5 recorded; v4 → v5 tests; a real upgrade on the emulator compared row by row.
- Emulator: import a multi-page PDF (push one to `/sdcard/Download`), scroll, zoom deep, write and erase; insert a JPG and a PNG with see-through parts (and HEIC if the emulator has one); lasso images; export and open the result in another PDF app; share a PDF and an image from Files into Noteapp; the image menu with permission allowed, set to selected photos only, and refused; Take Photo (the emulator camera); the screenshot tool alone and in split screen with Chrome (accept the capture prompt). Screenshots in `screenshots/`, landscape and portrait, light and dark.
- Tablet: back up its database first, then check the upgrade counts and try a real lecture PDF with the OPPO Pencil 2.
