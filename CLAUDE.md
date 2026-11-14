# NoteApp

NoteApp is an Android **tablet** app written in **Kotlin** with **Jetpack Compose**.
It's a handwriting-first note-taking app with built-in Canvas assignment tracking.
App ID / package: `com.nzfrankwu.noteapp` (single `app` module). minSdk 26. **Never change the app ID**: Android would treat it as a different app, and the user's notes wouldn't carry over.

The current phase plan and its checklist are in `PHASE-1.md`. Read it at the start of a session and tick steps as they pass.

## The plan: IDEA.md

`IDEA.md` is the plan to follow: features, Canvas import, backup, UI, data-safety rules and build phases (§5).

- Read it at the start of every session, and build phases in order.
- Never change `IDEA.md` without asking the user first.
- Treat these sections as hard rules:
  - **§3.5 Canvas security:** read-only, `GET` requests only, and session cookies are treated like passwords.
  - **§5a data safety:** proper database migrations (never delete and recreate), storage designed for backup, and a release signing key that stays out of git. Never tell the user to uninstall the app or clear app data without warning that it deletes their notes.
  - **§2.10 UI:** use `design-reference/` (Notein screenshots, git-ignored) for layout and feel only. Use Material icons and our own styling. Before coding a new screen, describe its layout to the user or ask which reference screenshot to follow.
- Use standard Android stylus/input APIs, not OPPO-only features. The user's device is an OPPO Pad 3 with OPPO Pencil 2, but the app should work on most tablets.

## Building and testing

- Run `gradlew.bat assembleDebug` after every change. If it fails, fix the errors and rebuild before finishing a step. Never leave a broken build.
- Write automated tests for logic (data, sorting, status rules, sync merging, migrations, and so on) and run them with `gradlew.bat testDebugUnitTest`. A step only passes when the build and tests pass.
- Check UI changes on the emulator. The `Pixel_Tablet` AVD is the default; test landscape and portrait, and light and dark mode where relevant. Take screenshots to confirm the result.
- Save screenshots in `screenshots/` (git-ignored, because they may show personal Canvas data).

## Working modes

### "Working together" mode (default)

This is the default, and it also applies when the user says "I'm working with you".

1. Work on a git branch for the current phase (for example `phase-1`), one feature at a time.
2. For each feature: build, write and run the tests, check the UI on the emulator with screenshots, and commit after each passing step.
3. Then stop for a check-in that covers:
   - what the feature does
   - the screenshots
   - anything you couldn't test (for example stylus pressure or real Canvas login)
   - 2–4 things to try on the tablet
4. Wait for the user's OK before starting the next feature. Push the phase branch once the user OKs the feature.

### "Hands-off" mode

This applies when the user says "I'm hands off, work on your own".

- Keep going through the phase's features without stopping for check-ins, until the phase is finished or there's no more work you can do safely.
- Still build, test, check the emulator, and **commit and push** the phase branch after every passing step.
- If something needs the user, don't guess. Examples: logins, a big new library, a change to the plan, or an unclear decision (including a new screen layout that no reference screenshot clearly covers). Note it in `REPORT.md` and `ISSUES.md`, skip to other work you can do, and leave it for the user.
- When you stop, write `REPORT.md` covering:
  - what got done
  - screenshots (linked from `screenshots/`)
  - what's waiting on the user
  - what to test on the tablet
- When the user is back, give them a short summary.

### Rules for both modes

- **Always ask first** before anything involving the user's logins (Canvas, Google), adding a big new library, changing `IDEA.md`, or merging into `main`.
- **Never merge into `main` without the user's approval.**
- **At the end of each phase:** run `/code-review`, fix the issues it finds, rebuild and retest, then ask the user to approve the merge into `main`.

## Reporting

Keep reports high-level; the user doesn't need line-by-line explanations. Each report covers:

1. **What was built.** One or two plain-language sentences.
2. **What changes for the user.** New or changed screens, buttons, gestures, or behaviour. Say "nothing visible" if that's the case.
3. **How to test it on the tablet.** Exact steps: where to tap, what to look for, and what result means it works.
4. **Issues.** Anything new added to, or resolved in, `ISSUES.md`.

Include a short code snippet when it's important or worth learning from, such as a key pattern, a tricky fix, or something the user may want to change later. Keep it brief and say why it matters.

## Tracking issues

`ISSUES.md` is the running list of the project's current state. Keep it up to date as part of every step:

- **Known bugs.** What's broken, how to reproduce it, and how serious it is.
- **Open questions.** Decisions waiting on the user.
- **Follow-ups / tech debt.** Shortcuts taken, things to revisit, warnings ignored for now.
- **Done.** Move fixed items here with a short note, so the history stays visible.

Read it at the start of a session. Commit it together with the step it relates to.

## Secrets

Never commit, log, print or hard-code secrets, and never include them in Drive backups. Secrets include:

- Canvas session cookies, tokens, and the calendar feed link
- Canvas links/URLs tied to the user's account
- Signing keys (`*.jks` / `*.keystore`) and their passwords

Keep them only in the app's private storage at runtime. Before every commit, check the diff for anything secret-looking.

The signing key and its passwords live outside the project in `<user home>/keys/` (`noteapp-release.jks`, `noteapp-keystore.properties`), and `app/build.gradle.kts` reads them from there. **Never create, read, edit or print files that hold passwords or secrets.** Give the user a template in chat and let them create the file themselves. To check signing, use only `gradlew.bat signingReport` or `apksigner verify`, which show fingerprints, never passwords.
