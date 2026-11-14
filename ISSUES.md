# NoteApp – Issues & Status

Running list of the project's current state. Updated with every step (see `CLAUDE.md`).

## Known bugs

_None yet._

## Open questions

- When to set up the release signing key (IDEA.md §5a). It must happen before the app is used for real. Doing it early in Phase 1 is safest.

## Follow-ups / tech debt

- Build warning: `libandroidx.graphics.path.so` can't be stripped of debug symbols, so it's packaged as is. This is harmless and comes from AndroidX. No action needed unless APK size matters.
- The project still uses the template package name `com.example.noteapp`. Change it to a real one before any Play Store release.

## Done

- Git repo set up with an Android `.gitignore` and pushed to GitHub (`main`).
- `CLAUDE.md` added with project guidelines.
- `IDEA.md` (the full plan) added. `CLAUDE.md` now points to it and defines the "Working together" and "Hands-off" modes.
