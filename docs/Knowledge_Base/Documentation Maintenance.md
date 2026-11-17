# Documentation Maintenance

> The contributor guide for this vault. Read this before adding or changing a note.
> Tooling: [[Documentation Maintenance]] → `scripts/validate_docs.py`

## Principles

1. **A claim without a `path:line` citation is a hypothesis.** If you cannot cite it, move
   it to [[Quality/Hypotheses]].
2. **"Fixed" requires evidence against the original reproduction.** Not "the mechanism
   changed". Four audit entries were closed this way and were still broken — see
   [[Quality/Audit Reconciliation]].
3. **Never rewrite the whole vault.** Targeted updates only. See
   [[Documentation Maintenance]] § Workflow below.
4. **Never overwrite an audit report.** Append a reconciliation, don't edit the original.

## When to update which notes

| If you change… | Must update | Also check |
|---|---|---|
| An `@Entity` field, table or index | [[Room Database]], [[Database Relationships]] | `AppDatabase.kt` (new migration), `SyncUploader` descriptor, a Supabase migration |
| A Room migration | [[Room Database]] § Migrations | `AppDatabaseMigrationTest` contiguity |
| A DAO query | the owning feature note | [[Testing]] if an untested query gained a live caller |
| A Supabase migration | [[Supabase]], [[Database Schema]] | [[Row Level Security]] if policies changed |
| An RLS policy | [[Row Level Security]], [[Quality/Security Concerns]] | re-run the verification query |
| Sync behaviour | [[Offline Synchronization]], [[Offline-First Architecture]] | [[Quality/Bug Register]] sync rows |
| A cache layer or TTL | [[Caching]] | [[Data Flow]] read path |
| A ViewModel's UiState or error path | [[MVVM]], [[Error Handling]] | [[Quality/Bug Register]] if a dead error field was added |
| A screen's empty/error/loading state | [[MVVM]] coverage matrix | — |
| A stock or expiry rule | [[Inventory Management]] | [[Quality/Bug Register]]; add a test to `InventoryStockMovementTest` or `InventoryUtilsExpiryTest` |
| A reminder or notification trigger | [[Due Vaccination]] | [[Quality/Bug Register]] scheduling rows |
| A statistics or finance rule | [[Statistics Dashboard]] | `FinanceCalculatorTest` / `ExpenseCalculatorTest` |
| A backup format or restore rule | [[Backup and Restore]] | `BackupValidatorTest` |
| A new file or package | [[Source File Index]] | [[Architecture Map]] if it changes a layer |
| A new navigation destination | [[Architecture Map]] § Navigation | [[Technical Debt]] if it adds a raw route interpolation |
| Any bug fixed | [[Quality/Bug Register]] — status + evidence | the note in the "Related" column |
| A release | [[Release Notes]], [[Release Checklist]] | — |

## Recording an architectural decision

Add a numbered entry to [[Architecture Decisions]]. It must contain:

| Field | Requirement |
|---|---|
| The decision | one sentence, present tense |
| The reasoning | why, in the author's words or a cited comment |
| The evidence | `path:line` or a migration comment |
| The cost | what it makes harder |
| Whether it still holds | ⚠️ if a later change undid it, say so **here** |

Precedent: entry 23 explains *why* pure engines exist — which is the reason the codebase
tests logic well and does not test wiring at all. That is a decision note earning its keep.

## Recording and verifying a bug

Start from a template — [[Templates/Bug Report Template]] for a general defect, or
[[Templates/Inventory Deduction Bug]] for a stock/deduction-ledger issue, where the
reproduction has to pin down which batch, which direction the quantity moved, and what the
ledger claimed afterwards.

1. **Reproduce it, or prove it by reading the cited code.** If neither, it is a hypothesis
   → [[Quality/Hypotheses]].
2. Take the next free ID in [[Quality/Bug Register]] — `NCC-nnn`, `NSC-nnn`, `HYP-nnn`.
   **Never renumber or reuse an ID.**
3. Fill the card: status, severity, impact, exact file/class/function, reproduction, evidence,
   root cause, proposed fix, regression test.
4. Separate the **reproduction** from the **evidence**. A repro is what a reader can repeat;
   evidence is why you believe it.

### Verification evidence format

When closing a bug, quote the check:

```
Fixed 2026-10-16 — `InventoryStockMovementTest.deducted_rejects_zero_quantity` (added in
the same commit) fails without the change and passes with it.
```

❌ Not acceptable: *"Fixed in commit abc123."*
❌ Not acceptable: *"The bug was in the wrong place."*

### Status transitions

```
Investigating ──reproduce──> Open ──schedule──> (fix) ──evidence──> Fixed
      │                       │
      └──refute──> Refuted    └──product decision──> Won't Fix (+ reason)
```

## Validating links and diagrams

```bash
python3 scripts/validate_docs.py              # whole vault
python3 scripts/validate_docs.py --strict     # warnings also fail
python3 scripts/validate_docs.py --changed    # only notes git sees as changed
python3 scripts/validate_docs.py --quiet      # summary only
python3 scripts/test_validate_docs.py         # 37 regression tests for the validator
```

What it checks:

| Check | Failure means |
|---|---|
| Broken wikilinks | the target note was renamed, moved, or deleted |
| Orphan notes | nothing links to the note — it will not be found |
| Mermaid blocks | unclosed fence, unknown diagram type, unbalanced label brackets |
| Required sections | a note that must have a given heading lost it |
| Markdown links | a relative `.md` target or external URL is malformed |
| Secret patterns | a credential or token was pasted into the vault |
| Stub notes | a note exists but is effectively empty |
| Heading and block anchors | `[[#Heading]]` / `[[Note#Heading]]` / `[[Note#^id]]` goes nowhere |
| Dataview blocks | unclosed or empty fence, unknown query type, typo'd clause, `FROM` target that matches no note |
| Bug register | a status in the index is not in the Status vocabulary, or a card has no Status/Severity |
| `path:line` citations | a cited file does not exist, or the cited line is past the end of the file. A citation into a deliberately untracked artifact is reported as an information note, not an error |

⚠️ The Mermaid check is **heuristic, not a parser**. It catches the common slips (unclosed
fence, wrong diagram keyword, unbalanced label brackets) and will not catch a semantically
invalid graph. Render the note in Obsidian to be sure.

⚠️ Brackets inside code fences and inline `` `code` `` spans are excluded from link
scanning, because Obsidian does not render them as links. Do not rely on that to hide a
real broken link.

### Heading and block anchors

The validator resolves the fragment in `[[Note#Heading]]` and `[[#Heading]]` against two things:
a heading whose *text* matches, or a `^block-id` marker. Inline markdown is normalised on both
sides, so a heading containing backticks resolves whether or not the link carries them.

This check was added because the vault had **100 dead anchors**. The tracker notes linked ids as
`[[#ncc-026]]` while every card is headed `## NCC-026 — …`; Obsidian's rule is that the fragment
is *the heading text* ([Internal links](https://obsidian.md/help/links)) and it does not
prefix-match, so none of them resolved. They read as navigation and were inert.

They were repaired on 2026-10-18, by context:

- **In a table row — the id is plain text.** A wikilink alias needs a `|`, and a `|` inside a
  markdown table cell ends the cell. Obsidian's escape (`\|`) is documented for *displaying* a
  pipe, not for aliases inside links, so this vault does not rely on it. Jump within a note with
  the outline pane (`Ctrl/Cmd+Shift+O`): the ids are section headings.
- **Anywhere else — the full heading text with the id as the alias**, e.g.
  `[[#NCC-026 — reclassified, not fixed|NCC-026]]`, or
  `[[Quality/Bug Register#NCC-001 — …|NCC-001]]` across notes.
- **A cross-note reference inside a table row** links the note itself and keeps the id as text
  (`[[Quality/Hypotheses]] HYP-002`) — no alias, so no pipe.

Follow the same rule for new links. A retitled heading that a link points at is now a build
failure rather than a silent dead link.

### What the validator cannot check

It reads the vault; it does not run Obsidian. A Dataview query is checked for shape and for a
`FROM` target that exists — never for the rows it returns, so a query naming a field that does
not exist still passes. The `path:line` check resolves the file and the line range only: a
citation **inside** the file that no longer points at the code it describes still passes, so
re-read the cited line when a claim matters — that is the accuracy rule in § 1, not something
the tool can enforce. The Mermaid check is heuristic, not a parser. Anchor matching is looser
than Obsidian's exact-text rule, so it errs towards silence. Render the vault to be certain.

## Mermaid conventions used here

- One diagram per idea. If it needs a legend, split it.
- Node labels quote text containing spaces: `A["Some label"]`.
- Use `flowchart TD` / `graph LR` for flows; `erDiagram` for entities.
- ⚠️ In `erDiagram`, cardinality uses `{ }` — the validator exempts that diagram type from
  bracket balancing, so do not rely on the check there.
- Mark unverified edges with `⚠️` in the label or in the surrounding prose. A diagram that
  looks certain is worse than one that admits uncertainty.

## Keeping documentation synchronised with the source

### Manual workflow (recommended)

This is the safe loop. It works without a daemon and never rewrites the vault.

```bash
# 1. See what changed.
git diff --name-only HEAD

# 2. Map changed files to affected notes.
#    Look at the table at the top of this page.

# 3. Update ONLY those notes, targeted edits.

# 4. Validate.
python3 scripts/validate_docs.py --changed

# 5. If you fixed a bug, update its register row with evidence.
```

### Assisted workflow for OpenCode

Ask for a documentation pass scoped to the diff:

> *Review the files changed in this branch. Using the "which notes must be updated" table
> in [[Documentation Maintenance]], identify the affected notes. Make targeted edits only —
> do not rewrite whole notes. Do not change application code. Then run
> `python3 scripts/validate_docs.py --changed` and report what it says.*

⚠️ Constraints worth repeating in the prompt:

- **No application code changes.** Documentation pass only.
- **No database migration changes.**
- **Never mark a bug Fixed without the evidence format above.**
- **Do not delete a note to silence an orphan warning.** Link it from [[Home]] instead.

### What NOT to do

| Anti-pattern | Why |
|---|---|
| Auto-rewrite every note on save | destroys deliberate wording and buries the signal |
| Delete notes that fail validation | hides the problem; a renamed note usually just needs its links updated |
| Bulk-search-replace across the vault | will corrupt code blocks and audit evidence |
| Regenerate `Known Bugs` from a scan | loses the reproduction steps and the reasoning |

## CI integration

`docs` job in `.github/workflows/android-ci.yml` runs the validator on push and pull request
to `main`, as a **separate job** so a documentation failure never breaks the build.

It is **advisory by default** — check the log, do not block merges yet. To make it blocking,
add the job name to the repository's required status checks in
**Settings → Branches → Branch protection rules**.

## Related

- [[Quality/Bug Register]] · [[Quality/Hypotheses]] · [[Quality/Security Concerns]]
- [[Quality/Audit Reconciliation]] — why this page is strict about evidence
- [[Quality/Documentation Repair 2026-10-20]] — the last full-vault pass, and its validator changes
- [[Home]] — the index
- [[Release Checklist]] — the pre-ship gate
