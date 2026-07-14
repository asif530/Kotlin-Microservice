# Adding a new top-level Archive/ category

The current taxonomy (`Architecture/`, `BusinessRules/`, `Development/`, `Issues/`, `Prompts/`, `Plan/`, `Misc/`, `_Templates/`) is enforced by `scripts/doc-audit.sh`'s `check_taxonomy()` — any top-level directory under `Archive/` not on that list fails the audit. This is deliberate: a stray folder created without thinking (a typo, an experiment left behind, a one-off dumping ground) should be caught immediately, not discovered a year later.

If you actually mean to add a new, permanent category — not a subfolder inside an existing one — do all three of these together, in one change:

1. **Confirm it doesn't fit an existing category first.** Re-read `Archive/GOVERNANCE.md` §2's table. Most new needs fit inside `Development/` (living, growing) or `Misc/` (background/context) rather than needing a new top-level name.
2. **Add a row to the taxonomy table in `Archive/GOVERNANCE.md` §2** — name, contents, mutability (living vs. locked vs. immutable-transcript, per the existing rows).
3. **Add the directory name to the `known` array in `check_taxonomy()`** in `scripts/doc-audit.sh`.

Then run `scripts/doc-audit.sh --index` to fold the new directory's contents into `Archive/INDEX.md`.

Skipping step 2 or 3 is exactly the failure mode this template exists to prevent: 
the folder exists but nobody wrote down why, or the folder exists but the audit doesn't know about it yet and will flag it as unrecognized on the next check.