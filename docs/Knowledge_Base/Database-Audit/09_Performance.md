# Performance Analysis

## 1. Static inventory

- **21 DAOs**, **230 `@Query` methods**.
- 25 Room entities; schema v33.
- 13-step full pull, several unbounded `SELECT *` calls.
- 24 sync descriptors; 2 upload-only ledger descriptors.
- No database profiling, `EXPLAIN`, or benchmark was executed. All costs below are **static estimates**.

## 2. Query and index coverage

| Query / path | Index available | Cost risk | Evidence |
| --- | --- | --- | --- |
| `SELECT * FROM patient_visits ORDER BY dateGiven DESC` | `index_patient_visits_dateGiven` (v33) | full result set materialized; sort index now helps, but unbounded rows | `VaccinationDao.kt:11,34` |
| `getAllVaccinationsWithItems()` | dateGiven + item visit index | loads **every visit and every item** for statistics/dashboard | `VaccinationDao.kt:33-35` |
| finance `getAllTransactions()` `ORDER BY COALESCE(transaction_date, substr(timestamp,1,10))` | expression prevents use of `transaction_date` index | full scan + temp B-tree on every finance/statistics load | `FinanceDao.kt:15-19` |
| patient search `name LIKE :q OR phone LIKE :q OR ...` | name/phone indexes unusable with leading `%` | full scan + correlated `patient_visits` LIKE subquery | `PatientDao.kt:44-49` |
| `sync_queue` dedupe `WHERE entityName/entityId/operation/status` | **no entityId index**; only status/priority | scan per enqueue; queue is usually small | `SyncQueueDao.kt:30`; schema v33 indices |
| `sync_queue` `unsyncedIds(name)` | no `(entityName,status)` index | scan per refresh step | `SyncQueueDao.kt:83` |
| `inventory_transactions` local history `WHERE vaccineId ORDER BY timestamp DESC` | vaccineId index; no local timestamp index | filter + sort | `VaccineDao.kt:77` |
| stock history remote paging | server migration adds timestamp/vaccine/batch/type indexes | network-bound; no local cache | `20261016:64-75` |
| audit logs paging | patient/timestamp server index | network-bound; no local fallback | `20261016` |
| reminder orphan check per remote row | `getVaccinationById` PK lookup | N queries for N reminders | `ReminderRepositoryImpl.kt:456-470` |
| finance self-heal per remote row | `getVaccinationById` PK lookup | N queries + possible enqueue per row | `FinanceRepositoryImpl.kt:389-431` |
| vaccination items apply per visit | per-visit queries/updates | N+1 over visits | `VaccinationRepositoryImpl.kt:169-260` |

## 3. Network and refresh amplification

1. **Whole-table pulls.** `fetchAllPatients` and the other refresh methods select all rows with no `range`/updated-since cursor. Every app start/reconnect and every patient-list realtime burst re-downloads the full tables.
2. **Realtime triggers the full pipeline.** A burst of changes is debounced 500 ms, but the resulting action is all 13 steps, not only changed tables.
3. **Multiple screens refresh the same tables.** Repository refreshes bypass the global single-flight (DBA-11).
4. **Stock history has no cache.** Every page/refresh is a network round-trip and the ledger is never pulled into Room.
5. **Full CSV/PDF report generation** reads the same unbounded Room lists and parses dates per row; no pagination at the calculator layer.
6. **Widget** recomputes from Room on every write notification, but uses a unique WorkManager name, so it does not queue unbounded jobs.

## 4. Write amplification

- Every business save is one local transaction plus one or more queue rows.
- A vaccination create enqueues visit, items, stock ledger/batch updates, finance and reminders; a group upload can be 10+ HTTP requests.
- `INVENTORY_TRANSACTION` retries after a lost response are UPDATE statements; with the broken trigger they also corrupt stock (DBA-01).
- Finance self-heal enqueues an UPDATE for every row whose date differs from the visit date; on a non-IST device this can be **every finance row on every pull** (DBA-04).
- Server write rate limit is 600 rows/minute/user; a large backlog group could hit it and classify as retryable/permanent depending on error mapping.

## 5. Startup cost (estimated)

Order of magnitude for a clinic with:

- 5,000 patients
- 20,000 visits
- 40,000 vaccination items
- 20,000 finance rows
- 30,000 ledger rows

| Work | Estimate |
| --- | --- |
| Room open + migrations | one-time; v33 adds an index only |
| `getAllVaccinationsWithItems()` | 20k + 40k rows read and joined on Room query thread |
| Statistics aggregation | O(visits × items) per emission, repeated across charts |
| Full pull | 20k + 20k + 40k + ... JSON rows over mobile network |
| Finance sort | 20k rows + temp sort per emission (expression index absent) |

These are not measured; they are expected scaling behavior from the query shapes. They justify a benchmark before optimizing.

## 6. Performance findings

| ID | Severity | Finding |
| --- | --- | --- |
| DBA-11 | S3 | Uncoalesced duplicate pulls across screens/pipeline. |
| DBA-17 | S3 | Missing `sync_queue(entityName/entityId)` index; dedupe scans. |
| DBA-20 | S3 | Unbounded full-table pulls and unbounded `getAll*` Flows; no cursor/limit. |
| DBA-21 | S3 | Finance `COALESCE` ordering defeats the transaction-date index. |
| DBA-22 | S3 | Leading-wildcard patient search scans patients plus a correlated visits subquery. |
| DBA-07 | S2 | Stock history is network-only, with per-page latency and offline failure. |

## 7. Recommended measurement plan

1. On a disposable Room DB populated with realistic rows, run `EXPLAIN QUERY PLAN` for:
   - `getAllVaccinationsWithItems`
   - finance `getAllTransactions`
   - patient search
   - sync-queue dedupe/unsynced query
2. Macrobenchmark cold start + screen open + statistics tab on a mid-range device.
3. Count HTTP requests per launch and per save via an interceptor/logcat.
4. Measure full-pull payload size and duration; compare with a cursor-based pull.

Do not add indexes until these measurements identify the actual bottleneck; every index adds write cost on a sync-heavy app.
