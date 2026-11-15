---
name: validate-documentation
description: Instructions for running the strict documentation validator in Neo Child Clinic using scripts/validate_docs.py. Use whenever editing documentation, writing ADRs, or updating Knowledge Base notes.
---

# Validate Documentation Skill

This skill explains how to validate documentation files and citations across `docs/`.

## Running the Validator

The project includes a Python validator at `scripts/validate_docs.py`. It checks:
1. Every `path:line` citation resolves to a real file and line range.
2. Wikilinks (`[[Note Name]]`) resolve to existing markdown files.
3. Mermaid and Dataview code fences are syntactically well-formed.
4. No leaked secret patterns or unverified status claims.

### Execution Commands

```bash
# Validate only files changed in git
python3 scripts/validate_docs.py --changed

# Strict comprehensive validation across all notes
python3 scripts/validate_docs.py --strict
```

## Running Validator Regression Tests

```bash
python3 scripts/test_validate_docs.py
```

## Fixing Common Validation Errors
- **Broken Citation**: If code moved, grep for the cited function/class and update the line numbers in the doc.
- **Unresolved Wikilink**: Ensure the note exists in `docs/` and matches the linked title exactly.
