# Documentation Drift Review

Governance policy: [Archive/GOVERNANCE.md](../../Archive/GOVERNANCE.md). This command is the semantic layer described there in §5 — the structural layer (`scripts/doc-audit.sh --check`) already catches broken paths, orphaned ChangeRequests, and missing headers; don't re-derive those by hand.

You are responsible to execute the following:

1. Run `scripts/doc-audit.sh` (no flag — regenerates `Archive/INDEX.md`/`Archive/RULE-INDEX.md` and runs the structural checks) from the repo root. Read its output.
2. For every service directory (`identity-service`, `catalog-service`, `order-service`, `notification-service`), compare what's actually implemented (controllers, entities, migrations under `src/main`) against its corresponding `Archive/Development/Backend/Phase/Phase-N-*` spec. Flag any phase whose spec claims more than the code delivers, or vice versa — cite the specific endpoint/rule.
3. Check every `ChangeRequest/Request-NNN` under `Archive/` that touches `BUSINESS_RULES.md` (mentions an ACC-/CAT-/ORD-/NTF-/GEN- rule, or is filed under `Archive/BusinessRules/`) has a corresponding numbered addendum in `BUSINESS_RULES.md` §10 — not just a `ChangeLog` entry. The structural script can't tell whether the *locked document itself* was actually updated; you can.
4. Skim `Archive/Plan/plan.md` and `Archive/Misc/project-ideas.md` for internal self-contradiction (e.g. recommending one library, then noting it's deprecated, without updating the example code) — these are the two docs in `Archive/` that read as generic reference material rather than project-specific ADRs, so they're the likeliest to drift silently.
5. Cross-check `Archive/Issues/*` — for each "Unresolved" entry, check whether the code it describes has since changed in a way that would resolve it (e.g. a dependency now wired up that wasn't before).

Report back as a short drift report, one line per finding in the voice already used in this repo's ChangeLogs (state what's wrong, how you'd confirm it, don't invent a fix unless asked): group findings as "Confirmed drift" vs. "Checked, no drift found" so a clean pass is visible, not just silence. Do not edit any files unless the user asks you to act on a specific finding — this command reviews, it doesn't fix.
