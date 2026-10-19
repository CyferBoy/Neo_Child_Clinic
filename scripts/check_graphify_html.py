#!/usr/bin/env python3
"""Verify graphify-out/graph.html still has the local viewer tweaks and parses.

The zoom buttons and the dim-on-select handler live in the installed graphify
package (exporters/html.py), not in this repo, so a `pip install -U graphifyy`
silently removes them. This check fails loudly when that happens:

    patch -p1 --batch -d "$(python3 -c 'import graphify,os;print(os.path.dirname(os.path.dirname(graphify.__file__)))')" \
        < scripts/graphify-viewer.patch
    graphify export html

The patch assumes graphify 0.9.84 — check `graphify --version` matches before
applying it to a newer release.

Usage: python3 scripts/check_graphify_html.py
Exit 0 = fine, 1 = tweak missing or generated JS is broken.
"""
import re
import subprocess
import sys
import tempfile
from pathlib import Path

HTML = Path(__file__).resolve().parent.parent / "graphify-out" / "graph.html"
REQUIRED = {
    "zoom buttons": "navigationButtons: true",
    "dim on select": "applyDim",
    # Neighbors must stay lit, not dim like the rest — asserts the current
    # behavior, so re-applying an older patch file fails this check.
    "neighbors lit": "lit.has(n.id)",
}


def main() -> int:
    if not HTML.exists():
        print(f"FAIL: {HTML} not found — run `graphify export html` first")
        return 1
    html = HTML.read_text(encoding="utf-8")
    bad = [name for name, needle in REQUIRED.items() if needle not in html]
    if bad:
        print(f"FAIL: missing {', '.join(bad)} in {HTML}")
        print("       the graphify package was upgraded or reinstalled — re-apply the viewer patch")
        return 1
    scripts = [body for body in re.findall(r"<script(?![^>]*\bsrc=)[^>]*>(.*?)</script>", html, re.S) if body.strip()]
    with tempfile.TemporaryDirectory() as tmp:
        for i, body in enumerate(scripts):
            f = Path(tmp) / f"inline_{i}.js"
            f.write_text(body, encoding="utf-8")
            proc = subprocess.run(["node", "--check", str(f)], capture_output=True, text=True)
            if proc.returncode != 0:
                print(f"FAIL: inline script #{i} does not parse:\n{proc.stderr.strip()}")
                return 1
    print(f"OK: {len(scripts)} inline script(s) parse, zoom buttons + dim-on-select present")
    return 0


if __name__ == "__main__":
    sys.exit(main())
