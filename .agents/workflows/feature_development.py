"""
Workflow: Feature Development Lifecycle
Orchestrates end-to-end development: Architecture -> Implementation -> Code Review -> QA Test Plan.
"""

feature_request = args.get("feature_request", "Add a new pediatric clinical feature")

phase("Phase 1: Architectural Analysis")
log(f"Consulting software-architect for feature: {feature_request}...")
arch_agent = agent(
    prompt=f"Analyze this feature request: '{feature_request}'. Define the Domain Model, Repository Contract, and Presentation UI state boundaries. Produce an implementation design plan.",
    role="Architectural Planner",
    type_name="software-architect",
    schema={"type": "string"}
)
plan = await arch_agent()
log("Architecture plan established.")

phase("Phase 2: Sequential Implementation")
log("Assigning implementation to android-developer...")
dev_agent = agent(
    prompt=f"Implement the planned feature following Clean Architecture:\n\n{plan}\n\nDeliver the Kotlin code, ViewModel, and Composable UI state handling.",
    role="Feature Developer",
    type_name="android-developer",
    schema={"type": "string"}
)
dev_output = await dev_agent()
log("Implementation completed.")

phase("Phase 3: Independent Peer Review")
log("Assigning diff review to code-reviewer...")
review_agent = agent(
    prompt=f"Review the implementation against Clean Architecture, concurrency safety, and the lazy senior dev principle:\n\n{dev_output}",
    role="Code Reviewer",
    type_name="code-reviewer",
    schema={"type": "string"}
)
review = await review_agent()
log("Code review completed.")

phase("Phase 4: QA Test Plan & Verification")
log("Assigning test suite design to qa-engineer...")
qa_agent = agent(
    prompt=f"Design unit tests and offline-first test cases for the newly implemented feature:\n\n{dev_output}",
    role="Test Engineer",
    type_name="qa-engineer",
    schema={"type": "string"}
)
tests = await qa_agent()
log("Feature Development Workflow completed.")
