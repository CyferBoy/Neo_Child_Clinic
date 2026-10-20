#!/usr/bin/env python3
"""
validate_docs.py — lightweight integrity check for the Obsidian knowledge base.

Stdlib only, no dependencies, no network. Designed to run locally and in CI.

Checks
  1. Broken wikilinks      [[Note]] that resolve to no file in the vault
  2. Orphan notes          notes nothing else links to
  3. Mermaid blocks        fence balance, known diagram type, label quoting
  4. Required sections     heading skeletons for the notes that must have them
  5. External links        http(s) link shape and bracket balance
  6. Secret patterns       credentials/tokens/PHI that must never be committed
  7. Stub notes            files that exist but are effectively empty
  8. Anchors               [[#Heading]], [[Note#Heading]], [[Note#^block-id]] resolve
  9. Dataview blocks       fence balance, query type, clause keywords, FROM targets
 10. Bug register          index statuses are documented; every card has Status + Severity
 11. Citations            every `path:line` resolves to a file and to lines that exist

What this cannot check
  Dataview queries are read, never executed. Obsidian and the plugin are not
  available here, so a well-formed query that returns no rows still passes.
  Anchor matching normalises inline markdown (backticks, emphasis) instead of
  requiring Obsidian's exact heading text, so it errs towards silence.
  A citation that is still in range but now points at different code cannot be
  detected mechanically - only a reader can. Citations are resolved from the
  repository root, so run this from there (or pass --repo-root).

Usage
  python3 scripts/validate_docs.py                 # whole vault
  python3 scripts/validate_docs.py --strict        # warnings become failures
  python3 scripts/validate_docs.py --changed       # only validate notes git sees as changed
  python3 scripts/validate_docs.py --quiet         # summary only
  python3 scripts/test_validate_docs.py            # regression tests for this validator

Exit codes
  0  no errors (warnings allowed unless --strict)
  1  at least one error
  2  bad invocation
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

VAULT_DEFAULT = "docs/Knowledge_Base"

# Files in the vault that are Obsidian machinery, not documentation.
SKIP_FILES = {"Welcome.md", "Untitled.base"}

WIKILINK = re.compile(r"\[\[([^\]|#\n]+?)(?:[#|][^\]\n]*)?\]\]")
# Anchor links, including the [[#Heading]] form WIKILINK cannot match (it needs a
# character before the '#'). Group 1 is the note ("" = this note), 2 the anchor, 3 the alias.
ANCHOR_LINK = re.compile(r"\[\[([^\]\n#|]*)#([^\]\n|]+)(?:\|([^\]\n]*))?\]\]")
BLOCK_ID = re.compile(r"(?:^|\s)\^([A-Za-z][\w-]*)\s*$", re.M)
# `path:line` citations. The extension list keeps prose like "at 12:30" out.
CITATION = re.compile(
    r"`([A-Za-z0-9_][A-Za-z0-9_./-]*\.(?:kt|kts|ts|tsx|js|mjs|sql|py|md|json|toml|gradle"
    r"|yml|yaml|xml|csv)):(\d+)(?:-(\d+))?`")
# A citation marked as living outside version control is excused from resolution.
UNTRACKED_MARKER = re.compile(r"artifact|scratch|untracked|git-?ignored", re.I)
FENCE = re.compile(r"^\s*```")
MD_LINK = re.compile(r"(?<!!)\[[^\]\n]*\]\(([^)\s]+)")
HTTP = re.compile(r"^https?://\S+$")

# Diagram types Mermaid understands. Anything else is a typo.
MERMAID_TYPES = {
    "graph", "flowchart", "sequenceDiagram", "classDiagram", "stateDiagram",
    "stateDiagram-v2", "erDiagram", "journey", "gantt", "pie", "gitGraph",
    "mindmap", "timeline", "quadrantChart", "requirementDiagram",
    "C4Context", "block-beta", "packet-beta", "architecture-beta",
    "sankey-beta", "xychart-beta", "radar-beta", "treemap-beta", "xychart",
}

# Dataview. Query types are the four DQL query forms; clause keywords are the ones
# that may begin a line below the query type. Anything else at column 0 is a typo.
DATAVIEW_LANGS = ("dataview", "dataviewjs")
DATAVIEW_QUERY_TYPES = {"table", "list", "task", "calendar"}
DATAVIEW_CLAUSES = {"from", "where", "sort", "group", "flatten", "limit", "offset"}

# ── Bug register ────────────────────────────────────────────────────────────────
# The register's own Status vocabulary table declares the legal statuses; the index
# must not use a status that is not documented there.
BUG_REGISTER_NOTE = "Bug Register.md"
BUG_REGISTER_INDEX = 3  # 0-based column of Status in the index table
CARD_HEADING = re.compile(r"^## (NCC-\d+)\b.*$", re.M)

# ── Secret / PHI patterns ───────────────────────────────────────────────────────
# Deliberately conservative: must not fire on prose or on documented placeholders.
SECRET_PATTERNS = [
    ("supabase-anon-key", re.compile(r"\beyJ[A-Za-z0-9_-]{30,}\b")),
    ("supabase-publishable-key", re.compile(r"\bsbp_[A-Za-z0-9]{20,}\b")),
    ("firebase-api-key", re.compile(r"\bAIza[0-9A-Za-z_-]{30,}\b")),
    ("github-token", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{20,}\b")),
    ("aws-access-key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("private-key-block", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("slack-token", re.compile(r"\bxox[abprs]-[A-Za-z0-9-]{10,}\b")),
    ("postgres-url-with-password", re.compile(r"postgres(ql)?://[^:\s]+:[^@\s]+@")),
    ("jwt-assigned-literal", re.compile(r'"(token|password|secret|api[_-]?key)"\s*[:=]\s*"[^"]{12,}"')),
]

# ── Required section skeletons ──────────────────────────────────────────────────
# note name -> heading fragments that must each appear as a heading somewhere.
REQUIRED_SECTIONS = {
    "Home.md": ["Start here"],
    "Architecture Map.md": ["Package layout", "Dependency", "Navigation"],
    "Data Flow.md": ["Read", "Write", "Sync"],
    "Source File Index.md": ["Source roots", "Entry points"],
    "Database Relationships.md": ["Entity", "Foreign key", "Supabase"],
    "Offline Synchronization.md": ["Trigger", "Queue", "Conflict"],
    "Known Bugs.md": ["Confirmed", "Related"],
    "Bug Register.md": ["Status vocabulary", "Severity scale", "Index", "Closing a bug", "Related"],
    "Documentation Maintenance.md": ["When to update", "Validate", "Bug"],
    "Release Checklist.md": ["Build", "Test", "Database", "Security", "Documentation"],
}

# Minimum useful byte size before a note counts as a stub.
STUB_BYTES = 500

# Directories whose notes are legitimately empty or skeletal: a template with blank
# headings is doing its job, so flagging it as a stub is noise, not signal.
TEMPLATE_DIRS = {"Templates"}


class Report:
    def __init__(self) -> None:
        self.errors: list[tuple[str, str]] = []
        self.warnings: list[tuple[str, str]] = []
        self.info: list[str] = []

    def error(self, where: str, msg: str) -> None:
        self.errors.append((where, msg))

    def warn(self, where: str, msg: str) -> None:
        self.warnings.append((where, msg))

    def note(self, msg: str) -> None:
        self.info.append(msg)


def strip_inline_code(line: str) -> str:
    """Blank out `inline code` spans so [[…]] written as an example is not a link."""
    return re.sub(r"`[^`]*`", lambda m: " " * len(m.group(0)), line)


def split_code_fences(lines: list[str]) -> list[tuple[bool, str]]:
    """Return [(inside_fence, line)] so link/mermaid checks skip code blocks.

    Inline `code` spans are blanked in non-fence lines: Obsidian does not render a
    wikilink inside them, so neither should the validator.
    """
    out, inside = [], False
    for line in lines:
        if FENCE.match(line):
            inside = not inside
            out.append((True, line))  # the fence line itself
            continue
        out.append((inside, strip_inline_code(line) if not inside else line))
    return out


def read_notes(vault: Path) -> list[Path]:
    """Every note whose CONTENT is validated.

    Subfolders such as Quality/ are part of the vault. Obsidian's own boilerplate
    (Welcome.md) and Bases are excluded from content checks — but they are still
    valid link targets, which is why check_stems() builds its own set.
    """
    return sorted(
        p for p in vault.rglob("*.md")
        if p.name not in SKIP_FILES and ".obsidian" not in p.parts
    )


def check_stems(vault: Path) -> set[str]:
    """Valid link targets: bare stems AND vault-relative paths.

    Obsidian accepts both [[Note]] and [[folder/Note]], so the validator must too.
    Includes skipped files so that linking to Welcome.md is not an error.
    """
    targets = set()
    for p in vault.rglob("*.md"):
        if ".obsidian" in p.parts:
            continue
        targets.add(p.stem)
        try:
            targets.add(p.relative_to(vault).with_suffix("").as_posix())
        except ValueError:
            pass
    return targets


def changed_notes(vault: Path) -> set[str]:
    """Notes git reports as added/modified, plus notes linking to them."""
    try:
        out = subprocess.run(
            ["git", "diff", "--name-only", "HEAD"],
            capture_output=True, text=True, timeout=15, check=False,
        ).stdout
    except (OSError, subprocess.SubprocessError):
        return set()
    stems = {Path(l).stem for l in out.splitlines()
             if l.strip().endswith(".md") and vault.name in l}
    return stems


def check_links(vault: Path, notes: list[Path], stems: set[str],
                rep: Report) -> dict[str, set[str]]:
    """Resolve every [[wikilink]]; record inbound edges by vault-relative path."""
    inbound: dict[str, set[str]] = defaultdict(set)
    for note in notes:
        self_key = _key(vault, note)
        tagged = split_code_fences(note.read_text(encoding="utf-8").splitlines())
        for idx, (in_fence, line) in enumerate(tagged, 1):
            if in_fence:
                continue
            for m in WIKILINK.finditer(line):
                target = m.group(1).strip()
                if target not in stems:
                    rep.error(f"{note.relative_to(vault)}:{idx}",
                              f"broken wikilink [[{target}]]")
                elif _key(vault, note) != _resolve_key(target, note, vault):
                    inbound[_resolve_key(target, note, vault)].add(self_key)
    return inbound


def _key(vault: Path, note: Path) -> str:
    return note.relative_to(vault).with_suffix("").as_posix()


def _resolve_key(target: str, note: Path, vault: Path) -> str:
    """A bare [[Note]] resolves to a sibling; a [[folder/Note]] is vault-relative."""
    if "/" in target:
        return target
    return note.parent.relative_to(vault).as_posix() + "/" + target if note.parent != vault else target


def check_markdown_links(notes: list[Path], rep: Report) -> None:
    for note in notes:
        tagged = split_code_fences(note.read_text(encoding="utf-8").splitlines())
        for idx, (in_fence, line) in enumerate(tagged, 1):
            if in_fence:
                continue
            for target in MD_LINK.findall(line):
                if target.startswith(("http://", "https://", "#", "mailto:")):
                    if not HTTP.match(target):
                        rep.error(f"{note.name}:{idx}",
                                  f"malformed external link: {target}")
                elif target.endswith(".md") and not target.startswith("http"):
                    if not (note.parent / target).exists():
                        rep.error(f"{note.name}:{idx}",
                                  f"broken relative link: {target}")


def check_mermaid(notes: list[Path], rep: Report) -> int:
    total = 0
    for note in notes:
        lines = note.read_text(encoding="utf-8").splitlines()
        in_mermaid, buf, start = False, [], 0
        for idx, line in enumerate(lines, 1):
            if line.strip() == "```mermaid":
                in_mermaid, buf, start = True, [], idx
                continue
            if in_mermaid and line.strip() == "```":
                total += 1
                _validate_mermaid(note.name, start, buf, rep)
                in_mermaid = False
                continue
            if in_mermaid:
                buf.append((idx, line))
        if in_mermaid:
            rep.error(f"{note.name}:{start}", "unclosed ```mermaid block")
    return total


def _validate_mermaid(name: str, start: int, buf: list[tuple[int, str]],
                      rep: Report) -> None:
    body = [(i, l) for i, l in buf if l.strip() and not l.strip().startswith("%%")]
    if not body:
        rep.error(f"{name}:{start}", "empty mermaid block")
        return

    head = body[0][1].strip()
    dtype = head.split()[0] if head.split() else ""
    if dtype not in MERMAID_TYPES:
        rep.error(f"{name}:{body[0][0]}",
                  f"unknown mermaid diagram type '{dtype}' (expected one of "
                  f"graph/flowchart/sequenceDiagram/erDiagram/...)")
        return

    text = "\n".join(l for _, l in body)

    # erDiagram encodes cardinality with { } and takes free-form quoted labels, so the
    # bracket-balance heuristic below does not apply to it.
    if dtype == "erDiagram":
        return

    # Unbalanced edge-label brackets/braces are the common authoring slip.
    for open_ch, close_ch, label in (("[", "]", "square brackets"),
                                     ("(", ")", "parentheses"),
                                     ("{", "}", "braces")):
        if text.count(open_ch) != text.count(close_ch):
            rep.error(f"{name}:{start}",
                      f"unbalanced {label} in mermaid block "
                      f"({text.count(open_ch)} '{open_ch}' vs {text.count(close_ch)} '{close_ch}')")
    if text.count('"') % 2 != 0:
        rep.error(f"{name}:{start}", "unbalanced double quote in mermaid block")
    if re.search(r"-->[^|\n]*\[[^\]\n]*$", text, re.M):
        rep.error(f"{name}:{start}", "mermaid edge label missing closing bracket")
    if dtype in ("graph", "flowchart") and "-->" not in text and "---" not in text:
        rep.warn(f"{name}:{start}", "flowchart block declares no edges")


def _note_paths(vault: Path) -> list[str]:
    """Vault-relative posix paths of every note."""
    return sorted(p.relative_to(vault).as_posix() for p in vault.rglob("*.md")
                  if ".obsidian" not in p.parts)


def _norm_heading(h: str) -> str:
    """Heading text in the form a link target has to match.

    Obsidian compares the raw heading text. Normalising inline markdown on both sides
    means a link written with or without the backticks still resolves, so this check
    can never be stricter than the link itself.
    """
    h = re.sub(r"`([^`]*)`", r"\1", h)
    h = re.sub(r"\[\[([^\]|#]*)[^\]]*\]\]", r"\1", h)
    h = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", h)
    h = re.sub(r"[*_~]", "", h)
    return re.sub(r"\s+", " ", h).strip().casefold()


def check_anchors(vault: Path, notes: list[Path], rep: Report) -> int:
    """Resolve [[#Heading]], [[Note#Heading]] and [[Note#^block-id]].

    Obsidian resolves an anchor to a heading whose text matches, or to a block marked
    with ^id. A bare id ([[#ncc-001]]) against a heading that reads "NCC-001 - ..."
    resolves to neither: the link looks right in the source and does nothing in Obsidian.
    """
    by_stem: dict[str, Path] = {}
    for p in vault.rglob("*.md"):
        if ".obsidian" not in p.parts:
            by_stem.setdefault(p.stem, p)
    heads: dict[str, set[str]] = {}
    blocks: dict[str, set[str]] = {}
    for stem, path in by_stem.items():
        text = path.read_text(encoding="utf-8")
        heads[stem] = {_norm_heading(h)
                       for h in re.findall(r"^#{1,6}\s+(.*)$", text, re.M)}
        blocks[stem] = {b.casefold() for b in BLOCK_ID.findall(text)}

    total = 0
    for note in notes:
        raw_lines = note.read_text(encoding="utf-8").splitlines()
        tagged = split_code_fences(raw_lines)
        for idx, (line, (in_fence, masked)) in enumerate(zip(raw_lines, tagged), 1):
            if in_fence:
                continue
            for m in ANCHOR_LINK.finditer(line):
                if not masked[m.start():m.end()].strip():
                    continue        # the link sits inside an inline code span
                target, anchor = m.group(1).strip(), m.group(2).strip()
                if not anchor:
                    continue
                stem = note.stem if not target else Path(target).stem
                if target and stem not in by_stem:
                    continue        # check_links already reports the missing note
                total += 1
                where = f"{note.relative_to(vault)}:{idx}"
                if anchor.startswith("^"):
                    if anchor[1:].casefold() not in blocks.get(stem, set()):
                        rep.error(where, f"dead block anchor [[{target}#{anchor}]] - "
                                         f"no ^{anchor[1:]} block in {stem}")
                elif _norm_heading(anchor) not in heads.get(stem, set()):
                    rep.error(where, f"dead heading anchor [[{target}#{anchor}]] - "
                                     f"{stem} has no heading reading '{anchor}'")
    return total


def _fenced_blocks(lines: list[str], langs: tuple[str, ...],
                   ) -> list[tuple[str, int, list[str], bool]]:
    """[(lang, fence line number, body lines, closed)] for ```<lang> blocks."""
    out: list[tuple[str, int, list[str], bool]] = []
    lang, start, buf = None, 0, []
    for idx, line in enumerate(lines, 1):
        if lang is None:
            m = re.match(r"^`{3,}\s*([A-Za-z]+)\s*$", line.strip())
            if m and m.group(1).lower() in langs:
                lang, start, buf = m.group(1).lower(), idx, []
            continue
        if re.match(r"^`{3,}\s*$", line.strip()):
            out.append((lang, start, buf, True))
            lang = None
            continue
        buf.append(line)
    if lang is not None:
        out.append((lang, start, buf, False))
    return out


def check_dataview(vault: Path, notes: list[Path], rep: Report) -> int:
    """Check Dataview blocks structurally. The queries are never executed.

    Catches the ways a dashboard goes silently empty: an unclosed fence, an empty
    block, a query type the plugin does not know, a mistyped clause keyword, and a
    FROM target that matches no note. A well-formed query naming a field that does
    not exist still passes - only Dataview itself could tell.
    """
    paths = _note_paths(vault)
    stems = {Path(p).stem for p in paths}
    total = 0
    for note in notes:
        lines = note.read_text(encoding="utf-8").splitlines()
        for lang, start, body, closed in _fenced_blocks(lines, DATAVIEW_LANGS):
            total += 1
            where = f"{note.name}:{start}"
            if not closed:
                rep.error(where, f"unclosed ```{lang} block")
            elif lang == "dataviewjs":
                if not any(l.strip() for l in body):
                    rep.error(where, "empty ```dataviewjs block")
                else:
                    rep.note(f"{where}: dataviewjs body not validated (JavaScript)")
            else:
                _validate_dql(note.name, start, body, paths, stems, rep)
    return total


def _validate_dql(name: str, start: int, body: list[str], paths: list[str],
                  stems: set[str], rep: Report) -> None:
    where = f"{name}:{start}"
    rows = [(start + i, l) for i, l in enumerate(body) if l.strip()]
    if not rows:
        rep.error(where, "empty ```dataview block - this dashboard can never render")
        return
    first_no, first = rows[0]
    qtype = first.split()[0].lower()
    if qtype not in DATAVIEW_QUERY_TYPES:
        rep.error(f"{name}:{first_no}",
                  f"unknown Dataview query type '{qtype}' (expected "
                  f"{'/'.join(sorted(DATAVIEW_QUERY_TYPES))})")
        return
    for no, line in rows[1:]:
        if line[:1].isspace():
            continue            # a continuation of the clause above
        head = line.split()[0].lower()
        if head not in DATAVIEW_CLAUSES:
            rep.warn(f"{name}:{no}",
                     f"'{head}' is not a Dataview clause keyword "
                     f"({', '.join(sorted(DATAVIEW_CLAUSES))}) - typo?")
        elif head == "from":
            _check_dql_from(name, no, line, paths, stems, rep)


def _check_dql_from(name: str, no: int, line: str, paths: list[str],
                    stems: set[str], rep: Report) -> None:
    parts = line.split(None, 1)
    arg = parts[1].strip() if len(parts) > 1 else ""
    quoted = re.findall(r'"([^"]*)"', arg)
    for q in quoted:
        if q and not any(p.startswith(q.rstrip("/")) for p in paths):
            rep.error(f"{name}:{no}",
                      f'Dataview FROM "{q}" matches no note in the vault')
    for target in re.findall(r"\[\[([^\]|#]+)", arg):
        if target.strip() not in stems:
            rep.error(f"{name}:{no}", f"Dataview FROM [[{target}]] resolves to no note")
    if "#" in arg:
        rep.note(f"{name}:{no}: tag filter in FROM is not validated (no tag index)")
    elif not quoted and "[[" not in arg:
        rep.warn(f"{name}:{no}", f"Dataview FROM target not verifiable: {arg!r}")


def _section(text: str, frag: str) -> str:
    """The text under the first heading containing `frag`, up to the next heading."""
    m = re.search(rf"^#{{1,6}}\s.*{re.escape(frag)}.*$", text, re.M | re.I)
    if not m:
        return ""
    rest = text[m.end():]
    nxt = re.search(r"^#{1,6}\s", rest, re.M)
    return rest[:nxt.start()] if nxt else rest


def _first_table(section: str) -> list[list[str]]:
    """Cells of the first contiguous markdown table in `section`."""
    rows, started = [], False
    for line in section.splitlines():
        s = line.strip()
        if not s.startswith("|"):
            if started:
                break
            continue
        started = True
        cells = [c.strip() for c in s.strip("|").split("|")]
        if cells and not all(re.fullmatch(r":?-{2,}:?", c) for c in cells):
            rows.append(cells)
    return rows


def _plain(cell: str) -> str:
    """Cell text with link syntax, emphasis, emoji and punctuation removed."""
    cell = re.sub(r"\[\[(?:[^\]|\n]*\|)?([^\]\n]*)\]\]", r"\1", cell)
    cell = re.sub(r"[*`_]", "", cell)
    return "".join(ch for ch in cell if ch.isalnum() or ch in " -'").strip()


def check_bug_register(notes: list[Path], rep: Report) -> None:
    """The register must document every status it uses, and every card must carry one.

    Its Status vocabulary is the contract with the reader: each row says what the
    status means and what it requires. A status used in the index but absent from
    that table cannot be interpreted, and a card with no status cannot be triaged.
    """
    note = next((p for p in notes if p.name == BUG_REGISTER_NOTE), None)
    if note is None:
        return                 # check_required_sections reports a missing register
    text = note.read_text(encoding="utf-8")

    documented = [row[0] for row in _first_table(_section(text, "Status vocabulary"))[1:]
                  if row and _plain(row[0])]
    for row in _first_table(_section(text, "Index"))[1:]:
        if len(row) <= BUG_REGISTER_INDEX:
            continue
        cell = _plain(row[BUG_REGISTER_INDEX]).casefold()
        if not any(cell.startswith(_plain(s).casefold()) for s in documented):
            rep.error(f"{BUG_REGISTER_NOTE}:index",
                      f"status '{row[BUG_REGISTER_INDEX]}' is not in the Status vocabulary "
                      f"({', '.join(_plain(s) for s in documented)})")

    # Range sections (NCC-019 to NCC-024) hold one status per table row instead.
    for card in re.split(r"^## ", text, flags=re.M)[1:]:
        head = card.splitlines()[0] if card.splitlines() else ""
        m = CARD_HEADING.match("## " + head)
        if not m or " to " in head:
            continue
        body = card.split("\n## ")[0]
        for field in ("Status", "Severity"):
            if not re.search(rf"^\|.*\*\*{field}\*\*", body, re.M):
                rep.error(f"{BUG_REGISTER_NOTE}:{m.group(1)}",
                          f"card has no **{field}** row")


# Directories that hold no cited source and cost seconds to walk.
INDEX_SKIP = {".git", ".gradle", ".gradle-user", ".idea", ".kotlin", ".artifacts",
              "build", "graphify-out", "neochildclinic-release", "node_modules"}


def _repo_index(root: Path) -> list[str]:
    """Repo-relative posix paths of every file a citation could name."""
    out = []
    for path in root.rglob("*"):
        if INDEX_SKIP.intersection(path.parts):
            continue
        if path.is_file():
            out.append(path.relative_to(root).as_posix())
    return out


def check_citations(root: Path, notes: list[Path], rep: Report) -> int:
    """Every `path:line` citation must name a file that exists, within its length.

    Notes cite short forms ("DueReminderDao.kt:39", "core/cache/MemoryCache.kt:12"), so
    resolution tries the repo-relative path, then a path suffix, then a unique basename.
    A basename matching several files is reported as ambiguous, never guessed - the point
    is to catch citations that resolve to nothing, not to pick for the author.
    """
    if not root.is_dir():
        rep.note(f"citation check skipped: {root} is not a directory")
        return 0
    index = _repo_index(root)
    by_name: dict[str, list[str]] = defaultdict(list)
    for rel in index:
        by_name[rel.rsplit("/", 1)[-1]].append(rel)
    lengths: dict[str, int] = {}
    total = 0
    for note in notes:
        raw = note.read_text(encoding="utf-8").splitlines()
        for idx, (line, (in_fence, _)) in enumerate(zip(raw, split_code_fences(raw)), 1):
            if in_fence:
                continue
            for m in CITATION.finditer(line):
                path, span = m.group(1), m.group(2) + ("-" + m.group(3) if m.group(3) else "")
                total += 1
                where = f"{note.name}:{idx}"
                if path in index:
                    rel = path
                else:
                    cands = ([r for r in index if r.endswith("/" + path)]
                             or by_name.get(path.rsplit("/", 1)[-1], []))
                    if not cands:
                        # A citation to a file that is deliberately outside version control
                        # (audit scratch SQL, a local DB dump) can never resolve from a
                        # clone. The line must say so - "artifact", "scratch", "untracked",
                        # "gitignored" - or the citation is reported as a broken one.
                        span_label = f"`{path}:{span}`"
                        if UNTRACKED_MARKER.search(line) or UNTRACKED_MARKER.search(path):
                            rep.note(f"{where}: {span_label} cites an untracked artifact "
                                     f"(not checkable from a clone)")
                        else:
                            rep.error(where, f"citation {span_label} - no such file in the repo")
                        continue
                    if len(cands) > 1:
                        rep.warn(where, f"citation `{path}` is ambiguous: "
                                        f"{', '.join(sorted(cands)[:3])}")
                        continue
                    rel = cands[0]
                if rel not in lengths:
                    with open(root / rel, encoding="utf-8", errors="replace") as fh:
                        lengths[rel] = sum(1 for _ in fh)
                hi = int(m.group(3)) if m.group(3) else int(m.group(2))
                if hi > lengths[rel]:
                    rep.error(where, f"citation `{path}:{span}` - {rel} has "
                                     f"{lengths[rel]} lines")
    return total


def check_required_sections(vault: Path, notes: list[Path], rep: Report) -> None:
    """Enforce heading skeletons, but only for notes actually in scope.

    In --changed mode a required note may legitimately be out of scope; that is not an
    error. A required note missing from the vault entirely always is.
    """
    in_scope = {p.name: p.read_text(encoding="utf-8") for p in notes}
    for name, fragments in REQUIRED_SECTIONS.items():
        if name not in in_scope:
            if not any(p.name == name for p in vault.rglob("*.md")):
                rep.error(name, "required note is missing from the vault")
            continue
        text = in_scope[name]
        for frag in fragments:
            if not re.search(rf"^#{{1,6}}\s.*{re.escape(frag)}", text, re.M | re.I):
                rep.error(name, f"required section missing: a heading containing '{frag}'")


def check_secrets(notes: list[Path], rep: Report) -> None:
    for note in notes:
        text = note.read_text(encoding="utf-8")
        for label, pat in SECRET_PATTERNS:
            for m in pat.finditer(text):
                line_no = text.count("\n", 0, m.start()) + 1
                redacted = m.group(0)[:6] + "…"
                rep.error(f"{note.name}:{line_no}",
                          f"possible {label} committed to the vault ({redacted})")


def check_stubs(vault: Path, notes: list[Path], inbound: dict[str, set[str]],
                rep: Report) -> None:
    for note in notes:
        size = note.stat().st_size
        where = note.relative_to(vault).as_posix()
        is_template = bool(TEMPLATE_DIRS.intersection(note.relative_to(vault).parts))
        if size < STUB_BYTES and not is_template:
            rep.warn(where, f"stub note ({size} bytes) — fill or delete")
        if not inbound.get(_key(vault, note)):
            rep.warn(where, "orphan note — nothing links to it")
        text = note.read_text(encoding="utf-8")
        headings = re.findall(r"^#{1,6}\s+\S", text, re.M)
        if len(headings) < 2 and note.stem not in ("Home",):
            rep.warn(where, "fewer than 2 headings — may be incomplete")


def main() -> int:
    ap = argparse.ArgumentParser(description="Validate the Obsidian knowledge base.")
    ap.add_argument("--vault", default=VAULT_DEFAULT, help=f"vault directory (default: {VAULT_DEFAULT})")
    ap.add_argument("--strict", action="store_true", help="treat warnings as failures")
    ap.add_argument("--changed", action="store_true", help="only check notes git reports as changed")
    ap.add_argument("--quiet", action="store_true", help="summary only")
    ap.add_argument("--repo-root", default=".",
                    help="root the `path:line` citations resolve against (default: .)")
    args = ap.parse_args()

    vault = Path(args.vault)
    if not vault.is_dir():
        print(f"error: vault not found: {vault}", file=sys.stderr)
        return 2

    notes = read_notes(vault)
    if not notes:
        print(f"error: no notes found in {vault}", file=sys.stderr)
        return 2

    if args.changed:
        touched = changed_notes(vault)
        if touched:
            # Include anything linking to a touched note, so index pages get checked too.
            linked_in = set()
            for n in notes:
                if n.stem in touched:
                    for other in notes:
                        if other.stem != n.stem and re.search(
                            rf"\[\[{re.escape(n.stem)}(\]\]|[#|])", other.read_text(encoding="utf-8")
                        ):
                            linked_in.add(other.stem)
            keep = touched | linked_in
            notes = [n for n in notes if n.stem in keep]

    rep = Report()
    stems = check_stems(vault)
    inbound = check_links(vault, notes, stems, rep)
    check_markdown_links(notes, rep)
    mer = check_mermaid(notes, rep)
    anchors = check_anchors(vault, notes, rep)
    dv = check_dataview(vault, notes, rep)
    cites = check_citations(Path(args.repo_root), notes, rep)
    check_required_sections(vault, notes, rep)
    check_bug_register(notes, rep)
    check_secrets(notes, rep)
    check_stubs(vault, notes, inbound, rep)

    if not args.quiet:
        for msg in rep.info:
            print(f"  {msg}")
        for where, msg in sorted(rep.warnings):
            print(f"WARN  {where}: {msg}")
    for where, msg in sorted(rep.errors):
        print(f"ERROR {where}: {msg}")

    scope = f"{vault} ({len(notes)} notes checked" + (", changed-only" if args.changed else "") + ")"
    print(f"\n{scope}, {mer} mermaid blocks, {dv} dataview blocks, {anchors} anchor links, "
          f"{cites} citations")
    if rep.errors:
        print(f"FAILED: {len(rep.errors)} error(s), {len(rep.warnings)} warning(s)")
        return 1
    if rep.warnings and args.strict:
        print(f"FAILED (strict): 0 error(s), {len(rep.warnings)} warning(s)")
        return 1
    print(f"OK: 0 error(s), {len(rep.warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
