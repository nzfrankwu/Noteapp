# NoteApp

NoteApp is an Android **tablet** app written in **Kotlin** with **Jetpack Compose**.
Package: `com.example.noteapp` (single `app` module).

## The plan

The product plan lives in `IDEA.md`. Read it before starting work and follow it.
Ask the user before changing the plan or doing work that isn't in it.

## Building

- Run `gradlew.bat assembleDebug` after every change.
- If the build fails, fix the errors and rebuild before saying a step is finished. Never hand back a broken build.

## How to work

- Work in small steps: one feature or change at a time, each ending in a working build.
- Ask the user before adding big new libraries or dependencies. Small AndroidX/Compose additions that are clearly part of the step are fine; mention them in the summary.
- Design layouts for tablet screens (large width, landscape and portrait).

## Reporting each step

Keep reports high-level; the user doesn't need line-by-line explanations. After each step, give a short summary with:

1. **What was built.** One or two plain-language sentences.
2. **What changes for the user.** New or changed screens, buttons, gestures, or behaviour. Say "nothing visible" if that's the case.
3. **How to test it on the tablet.** Exact steps: install and launch, where to tap, what to look for, and what result means it works.
4. **Issues.** Anything new added to, or resolved in, `ISSUES.md`.

Include a short code snippet when it's important or worth learning from, such as a key pattern, a tricky fix, or something the user may want to change later. Keep it brief and say why it matters.

## Tracking issues

`ISSUES.md` is the running list of the project's current state. Keep it up to date as part of every step:

- **Known bugs.** What's broken, how to reproduce it, and how serious it is.
- **Open questions.** Decisions waiting on the user.
- **Follow-ups / tech debt.** Shortcuts taken, things to revisit, warnings ignored for now.
- **Done.** Move fixed items here with a short note, so the history stays visible.

Read it at the start of a session. Commit it together with the step it relates to.

## Git

- After a step works and the user confirms it, commit with a clear, descriptive message and push to GitHub (`origin`, branch `main`).
- Don't commit before the user confirms.

## Secrets

Never commit, log or hard-code secrets, including:

- Canvas session cookies, tokens or API keys
- Canvas links/URLs (course, file or institution links)

Keep such values out of source code and out of `Log` calls. Load them at runtime from user input or secure on-device storage. Before every commit, check the diff for anything secret-looking. `local.properties` is git-ignored and may hold local-only config.
