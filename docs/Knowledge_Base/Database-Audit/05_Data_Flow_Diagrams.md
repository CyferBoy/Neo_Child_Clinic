# Data-Flow Diagrams

All diagrams describe verified HEAD behavior. Red notes mark confirmed defects; they are not target-state designs.

## 1. Context

```mermaid
flowchart LR
    UI["Compose screen"] --> VM["Hilt ViewModel"]
    VM --> UC["Use case / engine"]
    UC --> REPO["Repository implementation"]
    REPO --> DAO["Room DAO"]
    DAO --> DB[("SQLCipher Room v33")]
    REPO --> OB["sync_queue"]
    OB --> UP["SyncWorker / SyncUploader"]
    UP --> PG["Supabase PostgREST"]
    PG --> PDB[("Postgres + RLS + triggers")]
    REPO -. "pull (online)" .-> PG
    PG -. "realtime hint" .-> RT["RealtimeChangeSubscriptions"]
    RT --> VM
```

## 2. Local-first read

```mermaid
sequenceDiagram
    participant S as Screen
    participant V as ViewModel
    participant R as Repository
    participant D as Room DAO
    participant W as SyncWorker
    S->>V: collect StateFlow
    V->>R: Flow / suspend query
    R->>D: SELECT
    D-->>R: local rows
    R-->>V: domain models
    V-->>S: render immediately (offline OK)
    W->>W: push queue then pull in background
```

## 3. Vaccination create

```mermaid
sequenceDiagram
    participant VM as AddVaccinationViewModel
    participant S as ClinicalVaccinationService
    participant R as VaccinationRepository
    participant I as InventoryRepository
    participant F as FinanceRepository
    participant Q as sync_queue
    participant W as SyncUploader
    participant DB as Postgres
    VM->>S: recordVaccination(input)
    S->>S: Room transaction begins
    S->>R: insert visit + items
    S->>I: deductStockFromBatch per item
    I->>I: update batch + insert ledger row
    S->>F: updateIncomeForVisit
    S->>R: saveNextVaccination
    S->>Q: VACCINATION / ITEM / FINANCE / REMINDERS
    S->>S: commit
    VM-->>VM: show saved (local commit, no server ack)
    W->>DB: upsert group
    DB->>DB: receipt trigger, stock trigger
```

## 4. Vaccination edit (diff-based)

```mermaid
sequenceDiagram
    participant VM as AddVaccinationViewModel
    participant E as VaccinationEditEngine
    participant R as VaccinationRepository
    participant I as InventoryRepository
    participant F as FinanceRepository
    participant Q as sync_queue
    VM->>E: execute(original, updated)
    E->>E: diff per batch (new - old)
    E->>E: Room transaction
    loop positive delta
        E->>I: deductStockFromBatch(delta)
    end
    loop negative delta
        E->>I: reverseDeduction(min(-delta, completed ledger))
    end
    E->>R: update visit + replace items
    E->>F: update income if amount/items changed
    E->>R: reconcile reminders (delete/create)
    E->>Q: enqueue group
    E->>E: commit
```

Single-device double deduction is not present at HEAD: the diff is computed once, reversals are capped by COMPLETED deduction rows, and the whole edit is one transaction. The server retry path can still corrupt stock (DBA-01).

## 5. Visit / patient deletion

```mermaid
sequenceDiagram
    participant VM as PatientDetailsViewModel
    participant P as PatientRepository
    participant V as VaccinationRepository
    participant I as InventoryRepository
    participant Q as sync_queue
    participant DB as Postgres
    VM->>P: deletePatient(id)
    P->>P: Room transaction
    P->>Q: REMINDERS DELETE with LOCAL id
    Note over P,Q: DBA-05: pulled reminders use a different serverId
    P->>P: delete local reminders
    P->>P: clear inventory_deductions for visits
    P->>V: deleteVaccination per visit
    V->>I: reverse COMPLETED deductions
    V->>Q: VACCINATION_ITEM / FINANCE / REMINDERS / VACCINATION DELETE
    Note over V,DB: DBA-02: visit_id is no longer cleared on inventory_transactions
    P->>Q: PATIENT DELETE
    P->>P: commit
    DB->>DB: stock trigger reverses ledger rows
```

## 6. Startup / reconnect sync

```mermaid
flowchart TD
    A["App start or offline→online"] --> B["SyncReconciler"]
    B --> C["processNextItems (single-flight)"]
    C --> D{"queue empty?"}
    D -- no --> E["upload group: priority DESC, createdAt ASC"]
    E --> F["mark success / retry / fail"]
    F --> C
    D -- yes --> G["RefreshDataUseCase (single-flight)"]
    G --> H["patients → visits → finance → expenses → doctors → COGS → consultations → todos → reminders → waste ∥ inventory → personal reminders → items"]
    H --> I["per-step cloudRefresh + conflict guard"]
    I --> J["Room upsert (never delete-missing)"]
    J -. "DBA-06 remote deletes never converge" .-> J
```

## 7. Pull conflict decision

```mermaid
flowchart TD
    R["Remote row"] --> P{"in pending queue?"}
    P -- yes --> S["skip local update"]
    P -- no --> L{"local exists?"}
    L -- no --> I["insert as synced"]
    L -- yes --> T{"local isSynced?"}
    T -- no --> S
    T -- yes --> U["upsert remote"]
    U --> F{"finance self-heal?"}
    F -- yes --> O["force overwrite + isSynced=false + enqueue UPDATE"]
    O -. "DBA-03 bypasses pending guard" .-> S
```

## 8. Stock trigger behavior (actual)

```mermaid
flowchart TD
    T["inventory_transactions row"] --> OP{"TG_OP"}
    OP -- INSERT --> D["delta = new.quantity"]
    OP -- DELETE --> X["delta = -old.quantity; new_batch = null"]
    OP -- UPDATE --> B["delta = new.quantity - old.quantity"]
    D --> A["new batch: remaining += delta"]
    X --> U["undo old batch: remaining -= old.quantity"]
    B --> U2["if delta != old.quantity: remaining -= old.quantity"]
    U2 --> A2["new batch: remaining += delta"]
    A2 -. "DBA-01: net change is new - 2*old" .-> A2
```

Correct behavior for an UPDATE is one of:

- same batch: `remaining += (new.quantity - old.quantity)` with no separate undo; or
- move: `old.remaining -= old.quantity` and `new.remaining += new.quantity`.
