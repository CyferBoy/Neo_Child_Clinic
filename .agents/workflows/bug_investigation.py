"""
Workflow: Bug Investigation & Root Cause Remediation
Orchestrates root-cause investigation: Reproduce -> Root Cause -> Fix -> Regression Test.
"""

bug_description = args.get("bug_description", "Investigate reported application issue")

phase("Phase 1: Reproduction & Root Cause Analysis")
log(f"Investigating bug: {bug_description}...")
investigator = agent(
    prompt=f"Investigate this bug report: '{bug_description}'. Do not just patch the symptom caller—trace the real flow end-to-end to find the root cause. Grep all sibling callers and propose a single shared fix.",
    role="Bug Investigator",
    type_name="android-developer",
    schema={"type": "string"}
)
root_cause_analysis = await investigator()
log("Root cause identified.")

phase("Phase 2: Independent Peer Review of Proposed Fix")
log("Consulting code-reviewer on blast radius...")
reviewer = agent(
    prompt=f"Review the proposed fix for blast radius and regression risk:\n\n{root_cause_analysis}",
    role="Blast Radius Reviewer",
    type_name="code-reviewer",
    schema={"type": "string"}
)
review_feedback = await reviewer()
log("Review complete.")

phase("Phase 3: Regression Test Authoring")
log("Assigning regression test to qa-engineer...")
qa = agent(
    prompt=f"Write a targeted JVM unit test that reproduces the defect and asserts that the root-cause fix prevents recurrence:\n\n{root_cause_analysis}",
    role="Regression Tester",
    type_name="qa-engineer",
    schema={"type": "string"}
)
regression_test = await qa()
log("Bug investigation and verification completed.")
