#!/usr/bin/env python3
"""Regression tests for validate_docs.py.

Run:  python3 scripts/test_validate_docs.py
      python3 -m unittest discover -s scripts -p 'test_*.py'

Each test builds a throwaway vault, runs the real validator against it, and asserts on
what it reported. A skeleton vault satisfies every REQUIRED_SECTIONS entry so that a
failing test isolates the one rule it names instead of drowning in setup noise.
"""
import contextlib
import io
import pathlib
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import validate_docs as vd  # noqa: E402

REAL_VAULT = pathlib.Path(__file__).resolve().parent.parent / "docs" / "Knowledge_Base"


def skeleton() -> dict[str, str]:
    """A vault that satisfies REQUIRED_SECTIONS, so only the tested rule can fail."""
    notes = {}
    for name, frags in vd.REQUIRED_SECTIONS.items():
        body = "\n\n".join(f"## {frag} section\n\nText.\n" for frag in frags)
        notes[name] = f"# {name[:-3]}\n\n{body}\n"
    return notes


def run_vault(files: dict[str, str], *flags: str) -> tuple[int, str]:
    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp) / "vault"
        root.mkdir()
        for name, text in files.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        argv = ["validate_docs.py", "--vault", str(root), *flags]
        buf = io.StringIO()
        with mock.patch.object(sys, "argv", argv), contextlib.redirect_stdout(buf):
            code = vd.main()
        return code, buf.getvalue()


def dashboard(body: str) -> dict[str, str]:
    return skeleton() | {"Dashboard.md": f"# Dashboard\n\n## Queries\n\n{body}\n"}


class Dataview(unittest.TestCase):
    """The plugin is installed, so a dashboard that can never render is a defect."""

    def test_valid_block_is_accepted(self):
        code, out = run_vault(dashboard(
            '```dataview\nTABLE file.mtime AS "Updated"\nFROM ""\n'
            'SORT file.mtime DESC\nLIMIT 10\n```'))
        self.assertEqual(0, code)
        self.assertNotIn("ERROR", out)

    def test_folder_from_target_is_accepted(self):
        files = dashboard('```dataview\nLIST\nFROM "Templates"\n```')
        files["Templates/Blank.md"] = "# Blank\n\n## Summary\n\n"
        code, out = run_vault(files)
        self.assertNotIn("Dataview FROM", out)

    def test_from_path_matching_no_note_is_reported(self):
        # The real Home.md shipped `FROM " "` - a folder named " " does not exist.
        code, out = run_vault(dashboard('```dataview\nTASK\nFROM " "\nWHERE !completed\n```'))
        self.assertEqual(1, code)
        self.assertIn('Dataview FROM " " matches no note', out)

    def test_unknown_query_type_is_reported(self):
        code, out = run_vault(dashboard('```dataview\nSELECT * FROM "x"\n```'))
        self.assertEqual(1, code)
        self.assertIn("unknown Dataview query type 'select'", out)

    def test_empty_block_is_reported(self):
        code, out = run_vault(dashboard('```dataview\n```'))
        self.assertEqual(1, code)
        self.assertIn("can never render", out)

    def test_unclosed_fence_is_reported(self):
        code, out = run_vault(dashboard('```dataview\nTABLE file.mtime'))
        self.assertEqual(1, code)
        self.assertIn("unclosed ```dataview block", out)

    def test_mistyped_clause_warns(self):
        body = '```dataview\nTABLE file.mtime\nFRM ""\n```'
        code, out = run_vault(dashboard(body))
        self.assertIn("'frm' is not a Dataview clause keyword", out)
        self.assertEqual(0, code)              # a warning, not an error
        strict, _ = run_vault(dashboard(body), "--strict")
        self.assertEqual(1, strict)            # but --strict treats it as a failure

    def test_indented_continuation_is_not_flagged(self):
        code, out = run_vault(dashboard(
            '```dataview\nTABLE WITHOUT ID\n  file.name AS "Name",\n  file.mtime AS "At"\n'
            'FROM ""\n```'))
        self.assertNotIn("clause keyword", out)


class Anchors(unittest.TestCase):
    """Links that look right but resolve to nothing are the failure this catches."""

    def test_bare_id_anchor_is_reported(self):
        code, out = run_vault(skeleton() | {
            "Tracker.md": "# Tracker\n\nSee [[#ncc-001]].\n\n## NCC-001 — a title\n\nText.\n"})
        self.assertEqual(1, code)
        self.assertIn("dead heading anchor [[#ncc-001]]", out)

    def test_full_heading_text_resolves(self):
        code, out = run_vault(skeleton() | {
            "Tracker.md": "# Tracker\n\nSee [[#NCC-001 — a title]].\n\n## NCC-001 — a title\n"})
        self.assertNotIn("dead heading anchor", out)

    def test_markdown_in_heading_still_resolves(self):
        code, out = run_vault(skeleton() | {
            "Tracker.md": "# Tracker\n\nSee [[#NSC-009 — `manage-staff`: missing]].\n\n"
                          "## NSC-009 — `manage-staff`: missing\n"})
        self.assertNotIn("dead heading anchor", out)

    def test_block_id_anchor_resolves(self):
        code, out = run_vault(skeleton() | {
            "Tracker.md": "# Tracker\n\nSee [[#^ncc-001]].\n\nA paragraph. ^ncc-001\n"})
        self.assertNotIn("dead block anchor", out)

    def test_missing_block_id_is_reported(self):
        code, out = run_vault(skeleton() | {
            "Tracker.md": "# Tracker\n\nSee [[#^nope]].\n\nA paragraph. ^ncc-001\n"})
        self.assertEqual(1, code)
        self.assertIn("no ^nope block", out)

    def test_cross_note_anchor_resolves(self):
        files = skeleton() | {
            "Tracker.md": "# Tracker\n\nSee [[Dashboard#Queries]].\n",
            "Dashboard.md": "# Dashboard\n\n## Queries\n"}
        code, out = run_vault(files)
        self.assertNotIn("dead heading anchor", out)

    def test_cross_note_dead_anchor_is_reported(self):
        files = skeleton() | {
            "Tracker.md": "# Tracker\n\nSee [[Dashboard#Absent]].\n",
            "Dashboard.md": "# Dashboard\n\n## Queries\n"}
        code, out = run_vault(files)
        self.assertEqual(1, code)
        self.assertIn("[[Dashboard#Absent]]", out)

    def test_link_inside_a_code_span_is_ignored(self):
        code, out = run_vault(skeleton() | {
            "Tracker.md": '# Tracker\n\nWrite `[[#ncc-001]]` to link. See [[Dashboard]].\n',
            "Dashboard.md": "# Dashboard\n\n## Queries\n"})
        self.assertNotIn("dead heading anchor", out)


REGISTER_HEAD = """# Bug Register

## Status vocabulary

| Status | Meaning | Requirement |
|---|---|---|
| **Open** | not scheduled | evidence recorded |
| **Fixed** | landed | verification evidence |

## Severity scale

| Severity | Meaning |
|---|---|
| S1 | critical |

## Index

| ID | Title | Sev | Status | Area |
|---|---|---|---|---|
{rows}
## Closing a bug

1. Reproduce.

## Related

- [[Dashboard]]
"""


class BugRegister(unittest.TestCase):
    def register(self, rows: str, tail: str = "") -> dict[str, str]:
        return skeleton() | {
            "Bug Register.md": REGISTER_HEAD.format(rows=rows) + tail,
            "Dashboard.md": "# Dashboard\n\n## Queries\n",
        }

    def test_skeleton_is_accepted(self):
        code, out = run_vault(self.register("| NCC-001 | a thing | S1 | **Fixed** | Sync |\n"))
        self.assertNotIn("ERROR", out)

    def test_missing_required_section_is_reported(self):
        files = skeleton() | {"Dashboard.md": "# Dashboard\n\n## Queries\n"}
        files["Bug Register.md"] = files["Bug Register.md"].replace("## Severity scale section",
                                                                   "## Something else")
        code, out = run_vault(files)
        self.assertEqual(1, code)
        self.assertIn("required section missing: a heading containing 'Severity scale'", out)

    def test_undocumented_status_is_reported(self):
        code, out = run_vault(self.register(
            "| NCC-001 | a thing | S1 | ⚠️ Needs decision | Sync |\n"))
        self.assertEqual(1, code)
        self.assertIn("'⚠️ Needs decision' is not in the Status vocabulary", out)

    def test_card_without_severity_is_reported(self):
        code, out = run_vault(self.register(
            "| NCC-001 | a thing | S1 | **Fixed** | Sync |\n",
            "\n## NCC-001 — a thing\n\n| | |\n|---|---|\n| **Status** | Fixed |\n"))
        self.assertEqual(1, code)
        self.assertIn("card has no **Severity** row", out)

    def test_group_heading_needs_no_card_fields(self):
        code, out = run_vault(self.register(
            "| NCC-019 | a thing | S3 | **Fixed** | Sync |\n",
            "\n## NCC-019 to NCC-024 — group\n\n| ID | Sev | Status |\n|---|---|---|\n"
            "| NCC-019 | S3 | Fixed |\n"))
        self.assertNotIn("has no **", out)


class ExistingRules(unittest.TestCase):
    """The checks that existed before this change must still fire."""

    def test_broken_wikilink_is_reported(self):
        code, out = run_vault(skeleton() | {"Tracker.md": "# Tracker\n\nSee [[Nope]].\n"})
        self.assertEqual(1, code)
        self.assertIn("broken wikilink [[Nope]]", out)

    def test_resolving_wikilink_is_accepted(self):
        code, out = run_vault(skeleton() | {
            "Tracker.md": "# Tracker\n\nSee [[Dashboard]].\n",
            "Dashboard.md": "# Dashboard\n\n## Queries\n"})
        self.assertNotIn("broken wikilink", out)

    def test_orphan_note_is_reported(self):
        code, out = run_vault(skeleton() | {"Lonely.md": "# Lonely\n\n## Section\n\n" + "x " * 300})
        self.assertIn("orphan note", out)

    def test_stub_warns_but_templates_are_exempt(self):
        files = skeleton() | {
            "Tiny.md": "# Tiny\n\nShort.\n",
            "Templates/Blank.md": "# Blank\n\n## Summary\n\n",
            "Dashboard.md": "# Dashboard\n\n## Queries\n"}
        code, out = run_vault(files)
        self.assertIn("Tiny.md: stub note", out)
        self.assertNotIn("Blank.md: stub note", out)

    def test_mermaid_diagram_type_typo_is_reported(self):
        code, out = run_vault(skeleton() | {
            "Tracker.md": "# Tracker\n\n## Diagram\n\n```mermaid\ngraphx TD\n  A --> B\n```\n"})
        self.assertEqual(1, code)
        self.assertIn("unknown mermaid diagram type 'graphx'", out)

    def test_secret_pattern_is_reported(self):
        code, out = run_vault(skeleton() | {
            "Tracker.md": "# Tracker\n\n## Keys\n\nkey = AKIAIOSFODNN7EXAMPLE\n"})
        self.assertEqual(1, code)
        self.assertIn("possible aws-access-key", out)


class ShippedVault(unittest.TestCase):
    def test_real_vault_passes(self):
        argv = ["validate_docs.py", "--vault", str(REAL_VAULT)]
        buf = io.StringIO()
        with mock.patch.object(sys, "argv", argv), contextlib.redirect_stdout(buf):
            code = vd.main()
        self.assertEqual(0, code, buf.getvalue())


if __name__ == "__main__":
    unittest.main(verbosity=2)
