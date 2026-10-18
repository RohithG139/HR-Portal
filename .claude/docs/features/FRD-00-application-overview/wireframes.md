# FRD-00 — Shared application chrome (text wireframes)

Source: `.claude/docs/source/CubeWorks_HR_interactive_wireframes_v6_1.html` (read-only, R-SRC-1 rank 4 — the FRD and `spec.md` win on conflict). FRD-00 has no screens of its own; what follows is the chrome every other module's screens sit inside — the top bar and the per-role sidebar — described once here instead of being repeated in every module's `wireframes.md`. The layout rules R-UI-1…R-UI-3 (CLAUDE.md §2.6, FRD-00 §4 "Screen layout rules") apply to every screen built on top of this chrome and are not repeated per screen.

**Still correct under the v1.2 per-module source split** (`.claude/docs/source/README.md`, confirmed canonical 2026-10-16): the Document Map lists a dedicated FRD docx + WF-xx wireframe pair for every module FRD-01…FRD-08, but explicitly *none* for FRD-00 itself. So this chrome has no newer per-module wireframe to move to, and the legacy v6.1 HTML remains its source until/unless the user supplies one. The nav item list below was re-checked against v1.2's module list (FRD-01…FRD-08) and needed no changes — every nav item's owning feature still matches that module list exactly.

F01-onboarding's `wireframes.md` §"S12" already drew this chrome **scoped to what F01 built**. This file is the full, generic version: every nav item the wireframe defines for every role, not only the ones shipped so far.

---

## Top bar (all roles, all pages)

```
┌ CW  CubeWorks HR                                    [ROLE]  🔔3  (AB) ┐
└────────────────────────────────────────────────────────────────────┘
```

- **Brand mark** (`CW` in a coloured square) + "CubeWorks HR" — `data-home`, clicking it returns to the Dashboard. Not a capability, just the home link.
- **Role pill** (`#role-pill`) — shows the current user's role exactly as it appears in the roles table (§2.1 of `spec.md`): "HR", "Manager", "Employee" or "IT admin". Read-only display, driven by `GET /api/me`'s `appRole` (F01, A01).
- **Notification bell** (`#bell`) — a bell icon with an unread-count badge (`#bell-dot`). Clicking it opens the notifications dropdown (below) instead of the user menu; the two dropdowns are mutually exclusive (clicking one closes the other).
- **Avatar / initials** (`#avatar`) — a circle with the user's two initials (e.g. "ED" for Emma de Vries), tooltip = full name. Clicking it opens the user menu dropdown (below) instead of the notifications dropdown. This is the FRD's literal wording: "The top bar shows the role, the notification bell and the user's initials." (FRD-00 §4, quoted exactly).

### Notifications dropdown (`#notif`, opened from the bell)

```
Notifications                              [Mark all read]
🔔 {title, may include bold names/employees}      {time ago}
🔔 {title}                                         {time ago}
...
```

One row per notification (G-04: every new task creates one of these plus an e-mail). Unread rows are visually distinct; "Mark all read" clears the bell's unread badge. Matches F01's wireframes.md S12 "Notifications (bell dropdown)" exactly — this is the same component, not a new one.

### User menu dropdown (`#usermenu`, opened from the avatar)

```
{Full name}
{e-mail}
My profile
Log out
```

"The user's menu (initials) holds My profile and Log out." (FRD-00 §4, quoted exactly.) **"My profile" here is the universal G-13 entry point** — it is how HR, Manager and IT admin reach their own profile screen (`GET`/`PUT /api/me/profile`, A29/A30, built in F01), since only the Employee role's sidebar additionally has a dedicated "My Profile" nav item (see the sidebar table below). Both paths land on the same screen (F01's S10) for whichever role opens it.

---

## Sidebar — full nav set per role (`CFG.nav` in the wireframe)

The sidebar renders only the items the current role's `menu[]` from `GET /api/me` includes (G-02/WS-01: "the dashboard and menu shown depend only on the user's role"). The wireframe defines more nav items per role than F01 built; the table below is the **complete** set, with what's live today and what isn't.

```
┌──────────────┐
│ [icon] Dashboard    │
│ [icon] My Tasks  (n)│  ← badge shows open-task count
│ [icon] {role-specific items below}
│ ...
└──────────────┘
```

### HR (7 items)

| Nav item | Status | Owning feature |
|---|---|---|
| Dashboard | Stub only (empty page, title "Good morning, {name}") | Content not yet built, see F03 |
| My Tasks | Built | F01 |
| Employees | Built (Overview/Profile/History tabs only; Projects and Development & reviews tabs not yet built) | F01 |
| Projects | Not yet built | F04 |
| Appraisals | Not yet built | F06 |
| Reports | Not yet built | F09 |
| Settings | Not yet built | F09 |

### Manager (5 items)

| Nav item | Status | Owning feature |
|---|---|---|
| Dashboard | Stub only | F03 |
| My Tasks | Built | F01 |
| My Team | Built (same Employees-list/record component as HR's, scoped to WS-02 "own team only"; Overview has no stage line for Manager, per F01 wireframes.md S11) | F01 |
| My Projects | Not yet built | F04 |
| Appraisals | Not yet built | F06 |

### Employee (6 items)

| Nav item | Status | Owning feature |
|---|---|---|
| Dashboard | Built (new-joiner dashboard, F01 S7) — note this is onboarding-specific content; the FRD-00-generic "Dashboard" for an Employee past onboarding is not yet built | F01 (onboarding dashboard only), remainder F03 |
| My Tasks | Built | F01 |
| My Profile | Built (F01 S10) — the role-specific sidebar shortcut to the same screen the avatar menu's "My profile" reaches for every role | F01 |
| My Projects | Not yet built | F04 |
| My Development | Not yet built | F05 |
| My Reviews | Not yet built | F06 |

### IT admin (4 items)

| Nav item | Status | Owning feature |
|---|---|---|
| Dashboard | Stub only | F03 |
| My Tasks | Built | F01 |
| Assets | **Not yet built** — the wireframe's `p-it-assets` page is a browsable, searchable, exportable asset register (R-UI-2), distinct from F01's S5/S6 which only *create* assets as a side effect of the equipment-request flow. Ownership not explicit in `docs/features/README.md`; most likely F08 given its title ("Contract end, offboarding & assets") — **[OPEN]**, see `spec.md` §12 OPEN-ASSETS. | Unconfirmed, see spec.md §12 |
| Employees | Built (read-only: name, status and assets only — per the permissions matrix, `spec.md` §2.2) | F01 |

---

## Login (`#login-view`)

Not part of the sidebar/top-bar chrome (it renders before either exists), but it is FRD-00-adjacent since G-07 ("Clients never log in") and the whole roles model (§2.1) only make sense once a user has logged in as one of the four roles. The screen itself, its fields and its exact messages belong to FRD-01 (Shared workspace) and are already fully specified in F01's `spec.md` §5/S13 and `wireframes.md` S13 — not repeated here.

The v1.2 FRD-00 document's Appendix A (gap #1, new — see `spec.md` §12 OPEN-LOGIN) flags that the *password-reset* variants of this screen (Set password, Forgot password, Reset password) have no wireframe anywhere and the data model has no token table — still undrawn and undecided as of this revision. Picking that up is FRD-01/F03's spec's job, not this file's.

---

## What this file deliberately does not draw

Per `spec.md` §1 "Out of scope": the actual content of Dashboard (all roles), Projects/My Projects, Appraisals/My Reviews, Reports, Settings and My Development is each module's own concern. This file stops at "the nav item exists, here is which feature owns filling it in" — drawing those screens here would duplicate work that belongs in F03–F09's own `wireframes.md` files when those features are specified.
