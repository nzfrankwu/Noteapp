# NoteApp – Issues & Status

Running list of the project's current state. Updated with every step (see `CLAUDE.md`).

## Known bugs

_None yet._

## Open questions

_None right now._

## Follow-ups / tech debt

- Build warning: `libandroidx.graphics.path.so` can't be stripped of debug symbols, so it's packaged as is. This is harmless and comes from AndroidX. No action needed unless APK size matters.
- The signing key exists only on this PC (in `C:\Users\hiimf\keys\`) plus your own backups. On a new computer, copy both `noteapp-release.jks` and `noteapp-keystore.properties` into `<user home>\keys\` before building. Otherwise builds fall back to the debug key (a warning is printed) and can't update the app on the tablet.

- No backup exists until Phase 6. Android's built-in backup is now off (step 0.2), so nothing on the tablet is backed up until NoteApp's own Drive backup and "Export everything" are built. That's fine while the app only holds test data. IDEA §5a already requires Phase 6 to be finished before real use.
- The launcher icon is still the template's green Android robot. A proper NoteApp icon is a polish item.

## Done

- App identity (Phase 1, step 0.2). The app ID and package are now `com.nzfrankwu.noteapp` (this resolves the old "template package name" follow-up). The app is named "NoteApp" and has its own fixed light/dark theme (neutral greys with a blue accent, no wallpaper colours). Template libraries are updated. Android's built-in backup and device-to-device transfer are off. `PHASE-1.md` checklist added.
- Release signing key set up (Phase 1, step 0.1). All builds are signed with your own key, stored outside the project. The first key's password was accidentally exposed in the chat, so the key was recreated with a new password before anything was signed with it.

- Git repo set up with an Android `.gitignore` and pushed to GitHub (`main`).
- `CLAUDE.md` added with project guidelines.
- `IDEA.md` (the full plan) added. `CLAUDE.md` now points to it and defines the "Working together" and "Hands-off" modes.
