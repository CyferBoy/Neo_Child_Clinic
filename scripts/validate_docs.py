#!/usr/bin/env python3
"""
validate_docs.py — lightweight integrity check for the Obsidian knowledge base.

Stdlib only, no dependencies, no network. Designed to run locally and in CI.

Checks
  1. Broken wikilinks      [[Note]] that resolve to no file in the vault
  2. Orphan notes          notes nothing else links to
  3. Mermaid blocks        fence balance, known diagram type, label quoting
  4. Required sections    heading skeletons for the notes that must have them
  5. External links        http(s) link shape and bracket balance
  6. Secret patterns       credentials/tokens/PHI that must never be committed
  7. Stub notes            files that exist but are effectively empty

Usage
  python3 scripts/validate_docs.py                 # whole vault
  python3 scripts/validate_docs.py --strict        # warnings become failures
  python3 scripts/validate_docs.py --changed       # only validate notes git sees as changed
  python3 scripts/validate_docs.py --quiet         # summary only

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
    "Documentation Maintenance.md": ["When to update", "Validate", "Bug"],
    "Release Checklist.md": ["Build", "Test", "Database", "Security", "Documentation"],
}

# Minimum useful byte size before a note counts as a stub.
STUB_BYTES = 500


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
        if size < STUB_BYTES:
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
    check_required_sections(vault, notes, rep)
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
    print(f"\n{scope}, {mer} mermaid blocks")
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
