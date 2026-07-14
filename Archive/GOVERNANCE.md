# MiniMart — Documentation Governance

**Status:** Proposed
**Author role:** Documentation/process maintainer (this document)
**Date:** 2026-07-14
**Scope:** How `Archive/` is organized, what every document must carry, how a change to code or business rules produces a matching document, and what the automation in `scripts/doc-audit.sh` / `.claude/command/doc-review.md` does and doesn't catch. This document does not restate any business rule, architecture decision, or implementation detail — it governs the *process* those live under.
**Depends on:** Every doc referenced below already exists under `Archive/`; this document changes none of their content except by pointing at it.

---

## 1. Why this exists

`Archive/` already runs like a well-kept Confluence space: architecture decision records, a locked business-rules spec with rule IDs, a database design doc, per-phase API specs, and — for one feature (the Kong gateway) — a full `ChangeRequest → Implementation → ChangeLog → Issues` pipeline. That pattern was proven once, by hand, for the Gateway work. It was not applied consistently:

- `Archive/BusinessRules/ChangeRequest/Request-001` sat unresolved with no `ChangeLog` counterpart and no addendum in `BUSINESS_RULES.md`, even though that document's own revision policy requires one. (Fixed as the worked example for this governance pass — see `Archive/BusinessRules/ChangeLog/Request-001` and `BUSINESS_RULES.md` §10.)
- `Development/Database-Scripts` was renamed to `Development/Database-Dev` in git, but four docs kept citing the old name in prose. (Fixed in this pass.)
- `Issues/Gateway` was referenced with a stray trailing "0" (`Issues/Gateway0`) 8 times across the Gateway `ChangeRequest`/`Implementation`/`ChangeLog` triad — no file by that name exists. Caught by the first real run of `scripts/doc-audit.sh --check` against this repo (see §5) and fixed in this pass — a concrete demonstration of the tool catching something 3 commits of manual review hadn't.
- identity-service shipped in the same commit as the Gateway work but got none of the Implementation/ChangeLog treatment the Gateway got — still open, see §4.

None of this is a discipline problem — it's the predictable result of a convention that only ever lived in one person's head and one prior example. 
This document makes the convention explicit and gives it a mechanical backstop.

## 2. Taxonomy — what belongs where

| Directory        | Contents                                                                                                                                                                                        | Mutability                                                                                                                                                                                                                                                                                                              |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Architecture/`  | The architecture decision record and its amendment log                                                                                                                                          | `ARCHITECTURE.md` amended via `Change-log-N` entries, never edited in place for a past decision                                                                                                                                                                                                                         |
| `BusinessRules/` | The locked business-rules spec                                                                                                                                                                  | `BUSINESS_RULES.md` v1.0 is frozen; changes are numbered addenda in its own §10, per its revision policy                                                                                                                                                                                                                |
| `Development/`   | Everything that turns rules+architecture into a buildable system: database design, per-phase API specs, the scenario inventory, and per-feature `ChangeRequest/Implementation/ChangeLog` triads | Living — grows as features are built                                                                                                                                                                                                                                                                                    |
| `Issues/`        | Bugs found and fixed (or deliberately deferred), filed by technical topic, not by task                                                                                                          | Append-only; an unresolved item moves to resolved in place when fixed                                                                                                                                                                                                                                                   |
| `Prompts/`       | Verbatim transcripts of the instructions that drove each stage                                                                                                                                  | **Immutable.** Never edited, even when they reference something later renamed — they are a historical record, not a living reference. (This is why `Archive/Prompts/Backend-development`'s `Database-Scripts` reference was deliberately left alone in this pass, unlike the same stale name in `Scenarios`/`Phase-*`.) |
| `Plan/`, `Misc/` | Pre-decision background (stack-capability notes, brainstorming)                                                                                                                                 | Historical context, not authoritative — if it conflicts with a locked doc, the locked doc wins                                                                                                                                                                                                                          |
| `_Templates/`    | Reusable skeletons (change-unit template, this taxonomy)                                                                                                                                        | Not itself documentation — excluded from the path/header checks                                                                                                                                                                                                                                                         |

This table is the single source of truth for what's a recognized top-level category; `scripts/doc-audit.sh`'s `check_taxonomy()` enforces it mechanically — any `Archive/*` directory not listed above fails the audit. To add a new one on purpose, follow `Archive/_Templates/NewTaxonomyCategory.md`, which updates this table and the script's `known` list together so the two never drift apart.

## 3. Required header block

Every document under `Architecture/`, `BusinessRules/`, and `Development/Database*` — and every new document going forward, anywhere in `Archive/` — carries:

```
**Status:** Draft | Proposed | Locked | Superseded
**Owner role:** <role, not necessarily a name>
**Date:** YYYY-MM-DD
**Scope:** <one line>
**Depends on:** <other docs this one assumes are already true>
```

`Prompts/` and `Issues/` entries are exempt by design (§2) — a transcript doesn't have a "status," and an issues log's own Resolved/Unresolved split already carries that information.

Legacy docs without this header are not retrofitted in this pass — `scripts/doc-audit.sh` warns on them (doesn't block) so the gap stays visible instead of silently persisting. Add the header the next time a doc listed as missing one is meaningfully edited.

## 4. The change-unit template

Templates: `Archive/_Templates/{ChangeRequest,ChangeLog,Issues}.md`.

Rule: every `ChangeRequest/Request-NNN`, wherever in `Archive/` it lives, produces a matching `ChangeLog/Request-NNN` (or `Implementation/<Area>/Request-NNN` + `ChangeLog/<Area>/Request-NNN` for a code change). If the request touches a locked document — currently only `BUSINESS_RULES.md` — it also produces a new numbered addendum there, never a silent edit to an existing rule.

`scripts/doc-audit.sh` checks the ChangeRequest/ChangeLog pairing mechanically. It cannot check whether a locked-document addendum was actually written for a request that needed one (BusinessRules-Request-001 is exactly the case that slipped through); `.claude/command/doc-review.md` does that check semantically, on demand.

### Known gaps register

Tracked here instead of silently sitting in the tree:

1. **identity-service has no `Implementation/Identity` or `ChangeLog/Identity`**, unlike the Gateway, despite shipping in the same commit. Only its `Phase-1-Identity-Foundations` spec exists. Backfilling this accurately (reconstructing decisions after the fact) is a separate writing task, not tooling — not done in this pass.
2. ~~ER diagrams live only as external `claude.ai/code/artifact/...` links, not in-repo.~~ **Addressed:** Mermaid ER diagrams for all four services plus a combined view are now committed at `Development/Database-Dev/ErDiagram/{identity-service,catalog-service,order-service,notification-service,combined}.md`, derived directly from `Development/Database` and the `Database-Dev/postgres|mongo` schema scripts — text, diffable, no external dependency. The original externally-hosted links are kept in `Development/Database-Dev/ErDiagram/Diagram` for provenance only; the Mermaid files are now the durable copy.
3. **`Plan/plan.md` recommends Mongock, then notes Flamingock deprecated it, but still shows Mongock code samples.** Internally inconsistent; needs a content fix, not a process fix — flagged here so it doesn't get mistaken for current guidance.
4. **~35 of ~40 Archive files have no file extension**, so they don't render or lint as Markdown even though they're written in it. Not renamed in this pass (would touch every cross-reference to them); new files should use `.md` going forward per this document, and `scripts/doc-audit.sh` warns (doesn't block) on extension-less files under directories that require the header block.

## 5. Automation layers

Three layers, each catching a different class of drift:

1. **`scripts/doc-audit.sh --check`** — deterministic, no model involved. Checks: every `Archive/...`-shaped path referenced in prose (outside `Prompts/`) actually exists on disk; every `ChangeRequest/Request-NNN` has a matching `ChangeLog`/`Implementation` entry; every top-level `Archive/*` directory is one of the categories in §2's table (see `Archive/_Templates/NewTaxonomyCategory.md` to add one on purpose); required-header presence (warn-only). `--index` (re)generates `Archive/INDEX.md` and `Archive/RULE-INDEX.md`; run it before `--check` after adding new docs, or use no flag to run both. Fast enough to run in a pre-commit hook (`.githooks/pre-commit`) — **this is opt-in per clone, not automatic**: run `./scripts/setup-hooks.sh` once (it just sets local `core.hooksPath`; see the script for what it does and how to undo it). Nothing in this repo — not the Gradle build, not this document — runs that for you; it deliberately stays a deliberate, visible action rather than a silent build-time side effect.
   - Known false-positive class: a shorthand range like "Phase-1 through Phase-7" gets partially matched as a path (truncated at the space, one directory short of the real file) and flagged, even though the prose is correct. Treat a `FAIL` as a lead to check by hand, not an automatic truth — the script optimizes for catching real breaks (like the trailing-"0" typo above) over zero false positives.
2. **`.claude/command/doc-review.md`** — on demand, semantic. Runs the script, then reasons about drift a script can't detect: whether a Phase spec still matches what's actually implemented in the corresponding service, whether a doc's content contradicts itself (the Mongock/Flamingock case), whether a locked-document addendum is missing for a resolved ChangeRequest.
3. **Scheduled review** — `.github/workflows/doc-review.yml`, a GitHub Actions workflow running `scripts/doc-audit.sh --check` (the structural layer only; CI has no LLM to run the semantic `/doc-review` pass). Written with a manual `workflow_dispatch` trigger active and its recurring `schedule:` cron **commented out** — cadence (which day, what time) is a deliberate decision left open, not guessed. Uncomment the `schedule:` block once a cadence is chosen. This lives in the repo itself (durable across sessions), unlike this environment's own in-session cron tool, which is ephemeral and expires after 7 days — not a fit for a standing weekly check.

## 6. What this does not do

This governance layer does not judge whether a business rule or architecture decision is *correct* — only whether the paper trail around it is internally consistent (references resolve, requests get logged, locked docs get addenda instead of silent edits). Getting the business rule right is still a human/agent judgment call, same as it always was.
