# Contributing to Neo Child Clinic

Thank you for contributing to Neo Child Clinic! This project manages pediatric vaccination schedules, medical inventory, patient visits, and clinical records for healthcare providers. Maintaining reliability, data safety, and architectural clarity is our top priority.

---

## 1. Development Workflow

1. **Fork & Branch**: Create a feature or bugfix branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. **Setup Environment**: Follow [`docs/getting-started/onboarding.md`](docs/getting-started/onboarding.md) to configure Android Studio, JDK 17, and local `.env.local` keys.
3. **Architecture Guidance**: Check [`docs/architecture/where-things-belong.md`](docs/architecture/where-things-belong.md) before creating new files or packages.

---

## 2. Architectural & Coding Rules

- **Clean Architecture & Separation of Concerns**:
  - Keep domain logic in `domain/` pure (no Android or Room imports).
  - Repositories declare interfaces in `domain/repository/` and implement them in `data/`.
  - Screens belong in `feature/<feature_name>/presentation/`.
- **Offline-First Persistence**:
  - Never write directly to Supabase from ViewModels or UI.
  - Mutations must write to Room and enqueue in `sync_queue` within the same `TransactionRunner` block.
- **Compose Best Practices**:
  - Unidirectional Data Flow (UDF): ViewModels expose immutable `StateFlow<UiState>`.
  - Handle all UI states explicitly: Loading, Content, Empty, Error, and Offline.
  - Avoid heavy computations during recomposition.

---

## 3. Testing Requirements

- **Non-Trivial Code Requires Tests**: Every bug fix and new business rule must include at least one unit test reproducing the issue or asserting the contract.
- **Run the Suite Before Submitting**:
  ```bash
  # Run JVM unit tests
  ./gradlew testDebugUnitTest

  # Run documentation validation (strict mode)
  python3 scripts/validate_docs.py --strict
  python3 scripts/test_validate_docs.py
  ```

---

## 4. Documentation & Citations

- When modifying clinical workflows, update the relevant note in [`docs/Knowledge_Base/`](docs/Knowledge_Base/).
- Every line citation in `docs/Knowledge_Base/` must accurately resolve to real files and line numbers. Running `python3 scripts/validate_docs.py --strict` is required prior to committing changes.

---

## 5. Submitting a Pull Request

1. Provide a concise, descriptive title and description referencing any related issues.
2. Confirm that all tests pass and no secrets (`google-services.json`, keystores, `.env.local`) are included in the commit.
3. Keep changes scoped and avoid unrelated reformatting.
