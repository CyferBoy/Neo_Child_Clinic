"""Print the instrumented tests that failed, straight into the emulator step's own log.

Gradle's `connectedDebugAndroidTest` only says "There were failing tests" and points at an
HTML report, so without this the job log names no failing test at all. Run it from the same
shell invocation as Gradle (see .github/workflows/android-instrumented.yml) so the answer
lands in the log everyone is already reading.

Always exits 0: the caller re-raises Gradle's own exit code, so reporting never masks a
failure and never turns one green.
"""
import glob
import sys
import xml.etree.ElementTree as ET

# Do not pin a single AGP output path - the connected-test XML has moved between AGP
# versions. Sweep app/build and always say what was found, so "no XML at all" is
# distinguishable from "XML present with zero failures".
paths = sorted(glob.glob("app/build/**/TEST-*.xml", recursive=True))
print(f"::group::androidTest results - {len(paths)} JUnit XML file(s)")
for path in paths:
    print(path)

if not paths:
    print("No instrumentation XML found - Gradle failed before or during the test run.")
    print("::endgroup::")
    sys.exit(0)

failed = 0
for path in paths:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        print(f"could not parse {path}: {exc}")
        continue
    # The suite's own counters are the runner's verdict; the per-case scan below is a
    # best-effort restatement of it. Printing both means a disagreement is visible here rather
    # than having to be reconstructed from the raw log later.
    print(
        f"::group::{path} - tests={root.get('tests')} failures={root.get('failures')} "
        f"errors={root.get('errors')} skipped={root.get('skipped')}"
    )
    print(f"name={root.get('name')}")
    print("::endgroup::")
    for case in root.iter("testcase"):
        # A skipped or @Ignore'd test is not a failure, but JUnit XML has more than one way of
        # saying so, and AGP reports the @Ignore'd ExpenseMigrationTest with an empty <failure>
        # node while Gradle exits 0. Counting that cried wolf on a green run - the job was
        # reported "FAILED: 1 instrumented test(s)" when BUILD SUCCESSFUL was the truth, which
        # is exactly the kind of line people learn to scroll past. Only count a case nothing
        # has marked as skipped.
        if case.find("skipped") is not None or case.get("skipped") == "true":
            continue
        for kind in ("failure", "error"):
            for node in case.iter(kind):
                failed += 1
                print(f"::group::{case.get('classname')}.{case.get('name')} [{kind}]")
                print((node.get("message") or node.text or "").strip()[:4000])
                print("::endgroup::")

if failed:
    print(f"FAILED: {failed} instrumented test(s).")
else:
    print("JUnit XML parsed, zero failures recorded in it.")
sys.exit(0)