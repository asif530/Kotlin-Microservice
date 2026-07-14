# <Area> — Change Log Request-<NNN>

**Status:** Draft
**Owner role:** <who implemented/wrote this>
**Date:** <YYYY-MM-DD>
**Scope:** <one line: what actually changed as a result of the request>
**Depends on:** `Archive/<Section>/ChangeRequest/Request-<NNN>` (or the section-specific path, e.g. `Archive/Development/Backend/ChangeRequest/Request-<NNN>`), plus any design-constraint docs this change had to respect (cite section numbers, e.g. `ARCHITECTURE.md §N`, `BUSINESS_RULES.md <RULE-ID>`).

---

Source: <path to the ChangeRequest this resolves>. Design constraints: <docs/sections this change must not contradict>.

New files:

- `<path>` — <what it is and why it exists>.

  - **[Decision] <the judgment call>.** <why this option over the alternative(s); cite the rule/section that forced or allowed it>.

Changes to existing files:

- `<path>` — <what changed>.

  - **[Decision] <judgment call, if any>.**

---

Every `[Decision]` above should be something a future reader could disagree with and re-derive why it was made — not just a restatement of what changed. If nothing broke while implementing this, say so explicitly; if something did, it belongs in `Archive/Issues/<Area>`, not buried here.
