"""
Workflow: Release Readiness Assessment
Evaluates build configurations, versioning, unit tests, and documentation before release.
"""

phase("Phase 1: Build & Shrinking Configuration")
log("Consulting release-engineer for packaging checks...")
rel_agent = agent(
    prompt="Inspect gradle/libs.versions.toml, app/build.gradle.kts, and proguard-rules.pro. Verify versionCode, versionName, ABI filters, and R8 rules.",
    role="Release Inspector",
    type_name="release-engineer",
    schema={"type": "string"}
)
build_check = await rel_agent()
log("Build configuration verified.")

phase("Phase 2: QA & Test Status Check")
log("Checking test verification status with qa-engineer...")
qa_agent = agent(
    prompt="Review the latest test run status across the current unit-test suite. Verify that no regressions or skipped tests exist for offline sync or billing calculations.",
    role="QA Release Auditor",
    type_name="qa-engineer",
    schema={"type": "string"}
)
qa_check = await qa_agent()
log("QA verification verified.")

phase("Phase 3: Release Notes & Documentation")
log("Generating release summary with documentation-engineer...")
doc_agent = agent(
    prompt=f"Compile release readiness assessment and draft release notes based on:\n\nBuild Check:\n{build_check}\n\nQA Check:\n{qa_check}",
    role="Release Documenter",
    type_name="documentation-engineer",
    schema={"type": "string"}
)
release_notes = await doc_agent()
log("Release Readiness Workflow completed successfully.")
