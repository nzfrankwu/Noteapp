# Phase 1b: Task views (Calendar, Timeline, Days left)

A mini-phase after Phase 1, inspired by the user's old Notion Canvas tracker (IDEA.md §2.2).
Work happens on branch `phase-1b`; it's merged into `main` after Phase 1.
- ★ marks a check-in with the user.
- 🔺 marks a difficult or high-stakes step. Before starting one, tell the user so they can switch to a stronger model.

Decided with the user (15 Nov 2026):
- **Where:** a new mini-phase after merging Phase 1.
- **Home tabs:** Overview · Calendar · Timeline in the top bar; each view is full width.
- **Timeline bars** start when Canvas makes the assignment available ("available from"); your own tasks start when you added them. This needs database version 2.
- **Course colours** are picked automatically for now; choosing your own comes later.

## Checklist

- [x] **B.1 Days left.** _(done 15 Nov: `domain/DaysLeft.kt`, `ui/components/DaysLeftText.kt`, 16 unit tests + 1 screen test. "Due today" and "Due tomorrow" are amber too, and a task due earlier today says "Was due today". The calendar list will reuse `DaysLeftText` in B.5.)_ Every dated, unfinished task shows how long is left until its *real* due date, counted in local calendar days:
  - "Due today", "Due tomorrow", "3 days left", "1 day overdue", "5 days overdue"
  - Overdue text is red; 1–2 days left is amber.
  - It's shown on task cards (Due soon, folders, calendar list) and on the task page.
  - Undated or finished tasks show nothing.
  - Logic plus unit tests, including daylight-saving days.
- [x] **B.2 Course colours.** _(done 15 Nov: 7 colours, not 8, because red, orange and amber are left out so a course never looks "overdue" or "due soon". `domain/CourseColorKey.kt`, `ui/theme/CourseColors.kt`, `ui/components/CourseTag.kt`. Used on task cards, the task page, folder tiles and icons, the folder header and Link courses. The calendar and timeline will use `LocalCourseColors` and `CourseColor.container` for bars.)_ Each course gets a steady colour from a palette of about 8, chosen from its course code, so the same course always gets the same colour.
  - Light and dark versions live in the theme.
  - Course tags on cards, the calendar and the timeline use it.
- [ ] 🔺 **B.3 Database version 2: "available from".**
  - A new `available_at` time on tasks, with a tested 1→2 migration (version-1 data must survive), recorded in `SchemaHistoryTest`.
  - The Canvas import reads `unlock_at`, which is Canvas-owned and updated on every sync.
  - Your own tasks leave it empty; the timeline then uses the date you added the task.
- [ ] **B.4 Home tabs.** Overview · Calendar · Timeline as a segmented button in the top bar. The app remembers the last tab.
- [ ] **B.5 Calendar** (layout below). ★
- [ ] **B.6 Timeline** (layout below). ★
- [ ] **End of phase:** run `/code-review`, fix what it finds, retest, then ask to merge into `main`.

## Calendar layout (B.5)

```
┌ Noteapp      [Overview] [Calendar] [Timeline]                   ⚙ ┐
│  ◀  October 2026  ▶   [Today]        [All courses ▾]  ☐ Show finished│
│  Mon      Tue      Wed      Thu      Fri      Sat      Sun          │
│ ┌──────┬──────────┬──────┬──────┬──────────┬──────────┬──────┐      │
│ │ 28   │ 29       │ 30   │ (1)  │ 2        │ 3        │ 4    │      │
│ │      │▌Lab 6    │      │      │▌Lab 2 —… │▌Peer Mar…│      │      │
│ │      │ 4:00 pm ●│      │      │ 11:59 pm●│ 10:00 pm●│      │      │
│ │      │▌Quiz 8   │      │      │▌Start St…│          │      │      │
│ │      │+1 more   │      │      │          │          │      │      │
│ └──────┴──────────┴──────┴──────┴──────────┴──────────┴──────┘      │
└──────────────────────────────────────────────────────────────────────┘
```
- **The grid:**
  - a full-width month, Monday to Sunday (the same weeks as "This week" in Due soon)
  - today circled in blue; days from other months greyed
- **Each task chip:**
  - a coloured course bar on the left, the title (1 line), the due time (following the tablet's 12/24-hour setting) and a status dot
  - overdue tasks in red
- **Space:** up to 3 tasks fit in a day, then "+N more".
- **Tapping:** a task opens its page. A day (or "+N more") opens a side sheet listing that day's tasks, with "＋ Add task on this day" (the due date is pre-filled).
- **Which date:** tasks sit on their **due date**. A task with only a "My target date" sits on that date, marked "Target".
- **Filters:** "All courses ▾" narrows to one course, and "Show finished" adds Submitted and Done tasks, faded.
- **Navigation:** ◀ ▶ change month, **Today** jumps back, and swiping left or right also changes month.
- **Portrait:** same grid, but chips show the title only.

## Timeline layout (B.6)

```
┌ Noteapp      [Overview] [Calendar] [Timeline]                        ⚙ ┐
│  ◀ ▶ [Today]   [2 weeks | Month]      [All courses ▾]  ☐ Show finished  │
│        M 5  T 6  W 7  T 8  F 9  S 10 S 11 M 12 T 13 W 14 …  (today ┃)   │
│  ▌ELECTENG 204 ● Lab 2 ───────────────◆────────┤ 11:59 pm Fri          │
│        ▌ENGGEN 204 ● Quiz 8 ──────────┤                                  │
│  ◀ Report Project (starts earlier)                                      │
│                    ▌ELECTENG/COMPSYS 209 ● Lab 6 ─────────┤             │
└─────────────────────────────────────────────────────────────────────────┘
```
- **Rows:** one per task, sorted by due date. Days run across the top, weekends lightly shaded, and a blue line marks today.
- **Bars:**
  - each runs from **available from** (Canvas) or the date you added the task, to the **due date**
  - filled in the course colour, with the course tag, a status dot and the title on the bar
  - ◆ marks your target date
  - overdue bars get a red outline
- **Off-screen bars:** a bar outside the visible days shows an arrow at the edge (like Notion's), and tapping the arrow scrolls to it.
- **Tapping a bar** opens the task.
- **Zoom:** "2 weeks" or "Month". Swipe sideways to move in time and up or down through the tasks.
- **Filters:** the same "All courses ▾" and "Show finished" as the calendar. Tasks with no due date aren't shown, and the bottom line says "3 tasks without a due date".
