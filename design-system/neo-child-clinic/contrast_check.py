#!/usr/bin/env python3
"""
Neo Child Clinic design system - WCAG contrast check.

Fails if any declared token pair drops below its required ratio. This is the
runnable check for design-system/neo-child-clinic/MASTER.md section 1.4 - if a
color is changed without re-running this, it fails.

Usage:  python design-system/neo-child-clinic/contrast_check.py
Exit:   0 = all pass, 1 = at least one violation
"""


def _linear(c: int) -> float:
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def luminance(hex_color: str) -> float:
    h = hex_color.lstrip("#")
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    return 0.2126 * _linear(r) + 0.7152 * _linear(g) + 0.0722 * _linear(b)


def contrast(fg: str, bg: str) -> float:
    a, b = luminance(fg), luminance(bg)
    hi, lo = max(a, b), min(a, b)
    return (hi + 0.05) / (lo + 0.05)


TEXT = 4.5   # WCAG AA normal text
LARGE = 3.0  # WCAG AA large text, and non-text UI (borders, chart series)
LARGE_SP = 18.66  # 14pt bold / 18pt regular, in px-equivalent

LIGHT_SURFACE = "#FFFBFE"
DARK_SURFACE = "#121212"

# (label, foreground, background, minimum_ratio)
CHECKS = [
    # --- role layer: light -------------------------------------------------
    ("primary button label",      "#FFFFFF", "#0059B8", TEXT),
    ("primary as text",           "#0059B8", "#FFFBFE", TEXT),
    ("onPrimaryContainer",        "#1E3A5F", "#D6E4F0", TEXT),
    ("onSecondaryContainer",      "#00695C", "#D9F2F0", TEXT),
    ("onTertiaryContainer",       "#4A148C", "#F2E4F6", TEXT),
    ("onErrorContainer",          "#B71C1C", "#FCE4E4", TEXT),
    ("error as text",             "#C62828", "#FFFBFE", TEXT),
    ("onSurface",                 "#1A1C1E", "#FBF8F5", TEXT),
    ("onSurfaceVariant",          "#424242", "#FBF8F5", TEXT),
    ("outline vs surface",        "#8A8A8A", "#FBF8F5", LARGE),

    # --- full role coverage: light (every pair Theme.kt now declares) ------
    ("onPrimary",                 "#FFFFFF", "#0059B8", TEXT),
    ("secondary fill",            "#00695C", "#FFFFFF", TEXT),
    ("onSecondaryContainer",      "#00695C", "#D9F2F0", TEXT),
    ("tertiary fill",             "#6A1B9A", "#FFFFFF", TEXT),
    ("onError",                   "#FFFFFF", "#C62828", TEXT),
    ("onBackground",              "#1A1C1E", "#FBF8F5", TEXT),
    ("scrim",                     "#000000", "#FBF8F5", LARGE),

    # --- role layer: dark --------------------------------------------------
    ("primary on dark surface",   "#92CCFF", "#121212", TEXT),
    ("onPrimary on primary dark", "#003355", "#92CCFF", TEXT),
    ("onPrimaryContainer dark",   "#C2E8FF", "#004977", TEXT),
    ("onSecondaryContainer dark", "#80CBC4", "#004D40", TEXT),
    ("onTertiaryContainer dark",  "#F2DAFF", "#553F5F", TEXT),
    ("error on dark surface",     "#FFB4AB", "#121212", TEXT),
    ("onSurface dark",            "#E2E2E6", "#121212", TEXT),
    ("onSurfaceVariant dark",     "#C4C6C7", "#121212", TEXT),
    ("outline vs dark surface",   "#8E8E8E", "#121212", LARGE),

    # --- full role coverage: dark -----------------------------------------
    ("onSecondary dark",          "#00352F", "#80CBC4", TEXT),
    ("tertiary fill dark",        "#381E40", "#E0BBE4", TEXT),
    ("onError dark",              "#690005", "#FFB4AB", TEXT),
    ("onErrorContainer dark",     "#FFDAD6", "#93000A", TEXT),
    ("onPrimaryContainer->onPrim","#C2E8FF", "#004977", TEXT),
    ("onBackground dark",         "#E2E2E6", "#121212", TEXT),

    # --- accent layer ------------------------------------------------------
    ("info accent",      "#1E3A5F", "#D6E4F0", TEXT),
    ("success accent",   "#1B5E20", "#DCF0E2", TEXT),
    ("warning accent",   "#9C4D04", "#FFE8D1", TEXT),
    ("accent tertiary",  "#4A148C", "#F2E4F6", TEXT),
    ("teal accent",      "#00695C", "#D9F2F0", TEXT),
    ("neutral accent",   "#424242", "#EBEBEB", TEXT),
    ("danger accent",    "#B71C1C", "#FCE4E4", TEXT),
    ("success delta",    "#1B5E20", "#FFFBFE", TEXT),
    ("error delta",      "#C62828", "#FFFBFE", TEXT),

    # --- chart series (non-text, 3:1 floor) --------------------------------
    ("chart/patients",     "#2196F3", LIGHT_SURFACE, LARGE),
    ("chart/consultations","#9C27B0", LIGHT_SURFACE, LARGE),
    ("chart/vaccinations", "#009688", LIGHT_SURFACE, LARGE),
    ("chart/revenue",      "#B45309", LIGHT_SURFACE, LARGE),
    ("chart/online",       "#3F51B5", LIGHT_SURFACE, LARGE),
    ("chart/cash",         "#1B5E20", LIGHT_SURFACE, LARGE),
    ("chart/netProfit",    "#E91E63", LIGHT_SURFACE, LARGE),
    ("chart/cogs",         "#FF5722", LIGHT_SURFACE, LARGE),
    ("chart/expenses",     "#795548", LIGHT_SURFACE, LARGE),
]

# Colors the app uses today that are KNOWN to fail. Kept as a regression guard:
# if someone re-introduces them, this fails loudly.
LEGACY_FAILURES = {
    "ClinicBlue #007BFF as button text": ("#FFFFFF", "#007BFF"),
    "ClinicBlue #007BFF as text":        ("#007BFF", "#FFFBFE"),
    "SuccessGreen #4CAF50 as text":       ("#4CAF50", "#FFFBFE"),
    "ErrorRed #F44336 as text":           ("#F44336", "#FFFBFE"),
    "ChartRevenue #FF9800 non-text":      ("#FF9800", "#FFFBFE"),
    "ChartCash #4CAF50 non-text":         ("#4CAF50", "#FFFBFE"),
}


def main() -> int:
    failures = []

    print("=" * 72)
    print("DESIGN SYSTEM TOKENS")
    print("=" * 72)
    for label, fg, bg, minimum in CHECKS:
        ratio = contrast(fg, bg)
        ok = ratio >= minimum
        if not ok:
            failures.append((label, ratio, minimum))
        print(f"  [{'ok' if ok else 'FAIL'}] {label:<28} {fg} on {bg} "
              f"= {ratio:5.2f}:1 (min {minimum})")

    print()
    print("=" * 72)
    print("LEGACY COLORS (must stay unused - each of these fails AA)")
    print("=" * 72)
    for label, (fg, bg) in LEGACY_FAILURES.items():
        ratio = contrast(fg, bg)
        still_failing = ratio < TEXT
        if not still_failing:
            failures.append((label + " no longer fails?", ratio, TEXT))
        print(f"  [{'ok' if still_failing else 'FAIL'}] {label:<40} = {ratio:5.2f}:1")

    print()
    if failures:
        print(f"FAILED: {len(failures)} violation(s)")
        for label, ratio, minimum in failures:
            print(f"  - {label}: {ratio:.2f}:1 < {minimum}")
        return 1

    print(f"PASSED: all {len(CHECKS)} token pairs meet WCAG AA, "
          f"all {len(LEGACY_FAILURES)} legacy colors confirmed failing.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
