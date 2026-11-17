# Phase 6: Google Drive and backup (export, restore, Recently deleted)

Backup to Google Drive, restore, a manual .zip export, readable PDF copies, Recently deleted, and Word/PowerPoint conversion (IDEA.md §2.9, §4, §5 and §5a).
Work happens on branch `phase-6`; it's merged into `main` at the end, with the user's OK.
- ★ marks a check-in with the user (skipped in hands-off mode; listed in REPORT.md instead).
- 🔺 marks a difficult or high-stakes step. Before starting one, tell the user so they can switch to a stronger model or higher effort.

## Why
Phase 5 is merged into `main` (`09c1449`, review fixes `3ecda09`) and installed on the tablet. IDEA.md §5 says Phase 6 is **"Google sign-in, automatic full backup + restore, manual 'Export everything' / 'Import from export', PDF copies mirroring course folders, backup status/warnings, Recently deleted, Word/PowerPoint conversion"**, and §5a says it **must be finished and tested before the user relies on the app**.

Nothing on the tablet is backed up yet: Android's own backup is off on purpose (it would copy the Canvas cookies).

**Mode:** hands-off (the user said "hands off mode, use all of limit left" on 17 Nov).

## Decided with the user (17 Nov 2026)
- **Library:** only `com.google.android.gms:play-services-auth` (Google's standard permission screen, `Identity.getAuthorizationClient`, scope `drive.file`). Drive is reached through our own small client over OkHttp, which the app already has. Google's Java Drive client is not used.
- **Order:** the .zip Export/Import comes first (no Google needed). It builds and tests the whole restore path; Drive is then added on top of the same code.
- **PDF copies:** refreshed in the daily run and on "Back up now" (not a few minutes after each edit).
- **Word/PowerPoint conversion:** stays in Phase 6, as the last step.
- **Recently deleted:** a "Recently deleted (N)" row under the folders on Home (like Archived, hidden when empty), plus a row in Settings. It opens a grid of thumbnails ("deleted 3 days ago"), each with Restore and Delete now.
- **Snapshots:** Drive keeps the newest snapshot plus one per day for the last 7 days. Restore offers the newest, with older ones listed. IDEA.md §2.9 updated (user's OK): Version history can later reuse these snapshots.
- **Testing:** a **separate test Google account**, signed in on the emulator by the user (the AVD is a Google Play image).

## Proposed by me (easy to change)
- The Drive folder is named **"Noteapp"** (the app's spelling).
- "Back up now" uses any connection; automatic backups are **Wi-Fi only** by default (a switch in Settings).
- **Restore replaces everything on this tablet.** It never merges. The data it replaces is kept on the device as a "before restore" copy until the user removes it in Settings.
- A deleted notebook's PDF copy stays in Drive until it's purged (30 days), like the full backup.
- `canvas_settings` is **not** backed up. After a restore the user signs in to Canvas again.

## Design (data safety, IDEA.md §5a)
**What's in a backup set** (the same for the zip and Drive):
- `manifest.json`: format version, created time, app version, database version, counts, and for every file its path, size and md5
- `db/noteapp.db`: one consistent copy (below)
- `files/notebooks/**`: page files (formats 1–3), `pdf-*.pdf`, `images/`, `bookmarks/`; never `*.saving` or `*.writing`
- `settings.json`: the values of `ink_settings` and `view_settings`

**Never in a backup:** `canvas_settings`, the WebView's cookies (`app_webview/`), `shared-inbox/`, the cache, the `reminders` prefs and `backup_settings`. A unit test fails if an excluded path gets in.

**Consistent snapshot** (`data/backup/LocalSnapshot`):
1. Hold the app-wide `StorageLock` (the editor's save lock, moved into `AppContainer`), so page saves pause.
2. In a write transaction: `PRAGMA wal_checkpoint(TRUNCATE)`, copy `noteapp.db`, end the transaction.
3. `PRAGMA integrity_check` on the copy.
4. Copy the notebook files and hash them.

Zipping and uploads read only from the staged copy. (`VACUUM INTO` needs API 30; minSdk is 26.)

**Restore** (one path for zip and Drive, `data/backup/RestoreSwap`) 🔺:
1. Unzip or download into `restore-staging/`.
2. Check every md5 against the manifest; integrity check on the database.
3. Refuse a database version newer than this app ("Update Noteapp first"). An older version is migrated by Room's normal migrations.
4. Write a `restore-ready` marker and restart the app.
5. At the very start of `NoteAppApplication.onCreate`, before anything opens the database or DataStore: move the current database and `files/notebooks` to `before-restore/<time>/`, move the staged files in, apply `settings.json`, delete the marker. Every step can run again, so a crash halfway is finished on the next start.
6. Reminders are planned again; the user signs in to Canvas again.

**Drive layout:**
```
My Drive/Noteapp/
  Backup/blobs/<md5>            ← each file once, named by content
  Backup/snapshots/<time>.json  ← the manifest, written LAST
  Notes (PDF)/<folder>/<subfolder>/<title>.pdf
```
Uploads are checked against Drive's `md5Checksum`. After a snapshot is committed, retention removes old snapshots, then blobs no kept snapshot uses.

**Auth:** `AuthorizationClient.authorize(drive.file)` gets a token silently in the worker; if the user has to act, the status says "Sign in to Google again". Play Services keeps the tokens; we never store, log or print them.

**Scheduling** (WorkManager): a "backup-soon" one-time job 5 minutes after the database changes (`REPLACE` debounce), a daily job (backup, PDF copies, purge, clean-up), Wi-Fi only by default, battery not low, and an expedited "Back up now".

**Recently deleted:** only notebooks soft-delete (`deleted_at`). Restore puts a notebook back in its folder, or asks for one when its folder is gone. After 30 days (or "Delete now", confirmed) the notebook's rows and `notebooks/<id>/` are removed for good. Pages deleted over 30 days ago inside live notebooks are purged the same way.

**Clean-up** 🔺: a file in `images/`, `bookmarks/` or a `pdf-*.pdf` is removed only when nothing refers to it (page files, bookmark rows, `pages.pdf_file`), it's older than 30 days, and its notebook isn't open. Leftover `*.writing` files go too.

## Steps (branch `phase-6` from `main`; ★ = check-in, 🔺 = difficult)
- [x] **6.0** This plan; IDEA.md §2.9 (snapshots, the "Noteapp" Drive folder), HANDOFF.md, ISSUES.md.
- [x] 🔺 **6.1 Local snapshot and manifest** (17 Nov, hands-off; `f62a311`). Changed from the plan: the two settings stores are backed up as their own files (`files/datastore/*.preferences_pb`) instead of `settings.json`; they're swapped in at start-up before DataStore opens, so no conversion is needed. Page files are copied under the save lock; PDFs and pictures (written once, never changed) are read where they are. 26 unit tests, 5 emulator tests (`LocalSnapshotTest`: the latest changes still in `-wal` are in the copy; a snapshot taken while 40 pages are added is consistent). Was: `StorageLock`, `LocalSnapshot`, `BackupManifest`, `SettingsExport`.
  - unit tests: the manifest, the exclusions (Canvas never included), settings to and from JSON
  - emulator tests: a snapshot taken while pages save is consistent; the integrity check passes
- [x] **6.2 Export everything (.zip)** (17 Nov). Was: a **Settings → Backup** section (between Notebooks and Reminders) with Export everything / Import from export; the zip is written through the system "Save as". ★
- [x] 🔺 **6.3 Import from export** (17 Nov). Real check on the emulator: sample data (3 folders, 7 tasks, a notebook with strokes) → Export → `pm clear` → Import → confirm → the app restarts → `compare_dbs.py` PASS (every row) and every file's md5 the same. The old data went to `before-restore/<time>/`. Tested by unit tests instead of by hand: tampered, missing, extra and escaping (`../`) files, Canvas settings in a zip, no manifest, not a zip, a newer manifest format, both crash points of the swap, an unreadable marker. Not tested: restoring a v6 database (Room's own migrations do it; `MigrationTest` covers them). Was: confirmation (backup date, size, counts, "replaces everything on this tablet"), staging, md5 check, restart and swap, the before-restore copy.
  - tests: round trip; a corrupt or tampered zip is refused; a newer database version is refused; a swap killed halfway recovers
  - real check on the emulator: data → export → wipe → import → compare PASS
  - ★
- [ ] **6.4 Recently deleted:** the Home row, the Settings row and the grid screen; Restore (folder picker if the folder is gone); Delete now; the 30-day purge in a daily `MaintenanceWorker`. ★
- [ ] 🔺 **6.5 Clean-up of unused files** (the rules above).
- [ ] **6.6 Google Cloud setup, guided with the user** (their login): project, Drive API, OAuth consent screen (External, `drive.file`, **"In production"**), Android OAuth client with the SHA-1 from `gradlew.bat signingReport`; add `play-services-auth`, `INTERNET`, `ACCESS_NETWORK_STATE`.
- [ ] **6.7 Google sign-in in Settings → Backup.** ★
- [ ] 🔺 **6.8 `DriveClient` over OkHttp** (MockWebServer tests).
- [ ] 🔺 **6.9 Drive backup engine:** blobs, snapshot last, md5 checks, retention, garbage collection.
- [ ] **6.10 Automatic backup and status:** jobs, Wi-Fi only, Back up now, status in Settings, Home strip when failing or 3+ days without a backup. ★
- [ ] 🔺 **6.11 Restore from Google Drive.** ★
- [ ] **6.12 PDF copies in `Notes (PDF)/`.** ★
- [ ] **6.13 Word/PowerPoint → PDF.** ★
- [ ] **6.14 Tablet check** (ask first; snapshot; install; the user connects their real Google account).
- [ ] **End of phase:** `/code-review`, fix, rebuild and retest, ask to merge into `main`.

## Verification
- Every step: `.\gradlew.bat assembleDebug testDebugUnitTest`, then `$env:ANDROID_SERIAL="emulator-5554"; .\gradlew.bat connectedDebugAndroidTest`.
- Restore round trips on the emulator, checked with `compare_dbs.py` (every row) and the md5 of every page file.
- A check that a produced zip has no `canvas_settings`, `app_webview` or cookie data.
- Screenshots of every new screen (landscape/portrait, light/dark).
- The tablet only with the user's OK and a snapshot first.
