# Neo Child Clinic design set

- [System architecture](system-architecture.html) — offline-first component overview.
- [Offline sync sequence](offline-sync-sequence.html) — local write, durable queue, retry, and reconciliation.
- [Clinical data model](clinical-data-model.html) — core patient-centred entities and relationships.
- [Physical database schema](database-schema.html) — patient, vaccination and batch tables with key-level links.
- [Complete database table map](database-schema-complete.html) — all 21 Room/Supabase persistence tables, grouped by subsystem.
- [Complete Supabase schema map](supabase-database-complete.html) — authoritative 18-table backend view based on the supplied PostgreSQL schema, plus Room-only support tables.
- [Security deployment](security-deployment.html) — device and managed-cloud trust boundaries.
- [Notification flow](notification-flow.html) — scheduled local and server-initiated notification paths.

The diagrams intentionally compress feature-specific repositories, auxiliary tables, and individual Supabase policies so that each document stays readable at a glance.

## Complete Diagram Design catalog

The [catalog](catalog/README.md) contains 51 standalone, minimal-light reference examples covering every visual type and bundled variation supplied by Diagram Design. They are reference patterns; the five documents above are the diagrams tailored to this app.

## Detailed application set

[Open the 16 requested detailed diagrams](detailed-diagrams.html). The deck marks the six recommended GitHub/documentation diagrams: complete app architecture, feature map, database ER overview, offline-first sync, inventory architecture, and navigation flow.
