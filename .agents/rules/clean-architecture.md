---
trigger: glob
globs: "**/domain/**, **/feature/**, **/data/**, **/app/**"
---

# Clean Architecture & MVVM Guidelines

When modifying application source code in domain, feature, data, or app packages:

1. **Layer Dependency Direction**:
   - `Presentation` -> `Domain` <- `Data`
   - Presentation observes data solely through domain interfaces.
   - Domain never imports or references Android framework classes (`android.*`), Compose UI, or Room.
2. **ViewModel Invariants**:
   - ViewModels must NEVER inject Room DAOs, SQLite entities, or Supabase clients directly.
   - All state mutations and queries route through domain repositories.
   - Expose state via a single, immutable `StateFlow<UiState>`.
3. **Cross-Feature Decoupling**:
   - Features in `feature/<name>` must never directly import classes from other `feature/<other>` directories.
   - Coordinate cross-feature interactions using Compose Navigation routes with primitive arguments.
4. **Data Isolation**:
   - Database entities (`*Entity.kt`) and remote DTOs are private to the `data` layer.
   - Map entities to pure Kotlin domain models (`domain/model/`) before passing them across layer boundaries.
