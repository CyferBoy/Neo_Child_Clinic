# Inventory Management

> Batch-level stock, deduction rules, waste, borrows, and the client↔server stock contract.
> Related: [[Vaccination Management]] · [[Supabase]] · [[Row Level Security]] · [[Known Bugs]]

## Where quantity lives

**Per batch, never per vaccine.** The authoritative field is
`vaccine_batches.remaining_quantity` (`data/local/entity/VaccineEntity.kt:57`), with four
parallel counters:

```kotlin
@SerialName("remaining_quantity")   val remainingQuantity: Int
@SerialName("reserved_quantity")    val reservedQuantity: Int = 0    // ⚠️ never written
@SerialName("used_quantity")        val usedQuantity: Int = 0
@SerialName("wasted_quantity")      val wastedQuantity: Int = 0
@SerialName("borrowed_quantity")    val borrowedQuantity: Int = 0
```

`InventoryItem.stock` is a **derived sum** that excludes expired batches
(`InventoryRepositoryImpl.kt:69-78`):

```kotlin
val activeBatches = batches.filter { it.remainingQuantity > 0 && !InventoryUtils.isExpired(it.expiryDate) }
val totalStock = activeBatches.sumOf { it.remainingQuantity }
val isLowStock  = totalStock <= globalThreshold      // ⚠️ `<=`, see Known Bugs
val isOutOfStock = totalStock <= 0
```

## The single deduction funnel

`feature/inventory/data/InventoryStockMovement.kt:11-32` is the **only** place
`remainingQuantity` is decremented for a deduction:

```kotlin
internal fun VaccineBatchEntity.deducted(quantity: Int, transactionType: ..., userName: String) =
    copy(remainingQuantity = remainingQuantity - quantity, ...).let { base ->
        when (transactionType) {
            VACCINATION -> base.copy(usedQuantity     = usedQuantity     + quantity)
            BORROWED    -> base.copy(borrowedQuantity = borrowedQuantity + quantity)
            EXPIRED, DAMAGED, COLD_CHAIN_FAILURE, CONTAMINATED, OTHER ->
                          base.copy(wastedQuantity   = wastedQuantity   + quantity)
            else        -> base
        }
    }
```

with `require(quantity > 0)` guarding the delta. ✅ Good design — one funnel, one guard,
and it **is** tested (`InventoryStockMovementTest.kt`, 6 tests).

## Batch selection — FEFO, implemented twice

There is no shared selector. Two implementations of first-expiry-first-out remain:

| Where | Behaviour |
|---|---|
| `AddVaccinationLogic.bestAvailableBatch` (`:11-15`) | in stock, not expired **as of the given date**, soonest expiry |
| `BorrowBatchSelection.selectBorrowBatch` (`:21-41`) | must be a **single** batch covering the whole quantity; honours a preferred id first |

The old `InventoryRepository.deductStock` copy and its `VaccineDao.getActiveBatchesByExpiry`
query were deleted on 2026-10-25.

The "usable batch" predicate also has **five** slightly different variants across
`InventoryRepositoryImpl.kt:73`, `AddVaccinationLogic.kt:14`, `VaccineRowCard.kt:87`,
`BorrowBatchSelection.kt:29` and `WasteViewModel.kt:52` — two of which use different expiry
predicates (`isExpired` vs `isExpiredAsOf`).

## Expiry semantics

`feature/inventory/domain/InventoryUtils.kt`, all defaulting to **clinic-local IST today**
(`:17` `LocalDate.now(ZoneId.of("Asia/Kolkata"))`), not device today:

| Function | Rule | Line |
|---|---|---|
| `isExpired(expiry, today)` | `expiry.isBefore(today)` — **the expiry day itself is still usable** | `:23-25` |
| `isExpiredAsOf(expiry, ref)` | same, but relative to a reference date | `:37-41` |
| `isNearExpiry(expiry, 30)` | within 30 days, excluding already-expired/today | `:55-60` |

The boundary rule is deliberate and pinned by `InventoryUtilsExpiryTest.kt:25-28`.

⚠️ **All three fail open on an unparseable date**: `?: return false` at `:24`, `:38`. A
batch with a blank `expiryDate` is treated as not expired, so it counts toward `totalStock`,
appears in the dropdown, and passes the deduction guard. Only `addStockBatch` validates the
field; `addBatch` (used by the borrow-return path, `BorrowRepositoryImpl.kt:239`) does not.

## Where the guards are

| Layer | Guard | Notes |
|---|---|---|
| UI | filters the dropdown to `remainingQuantity > 0 && !isExpiredAsOf(…)` (`VaccineRowCard.kt:86-88`); clamps quantity to `>= 1` (`AddVaccinationViewModel.kt:337`) | display only — **no quantity-vs-stock check at all** |
| Domain | `require(quantity > 0)` (`InventoryStockMovement.kt:16`) | blocks zero/negative deltas, not over-draw |
| Repository | `if (batch.remainingQuantity < quantity) throw InsufficientStockException` (`InventoryRepositoryImpl.kt:476-478`) | **read-then-write, not a constraint** |
| Room | ⚠️ **none** — no `CHECK` on `remainingQuantity`; verified in the exported `33.json` | |
| Postgres | `RAISE EXCEPTION … USING ERRCODE = 'check_violation'` (`20261013140000:44-49`) | AFTER trigger, raises rather than clamps |

So the only real client-side guard is a repository pre-read. Two offline devices can both
pass it; only the server trigger catches the second. See [[Supabase]].

## The client ↔ server contract

The client does **not** push absolute stock. `SyncUploader.kt:612-623` (comment `:599-611`):

```kotlin
// vaccine_batches.remaining_quantity is maintained server-side by the
// tr_update_batch_stock trigger, which adjusts it by NEW.quantity for every
// inventory_transactions row this app inserts …
if (isCreate) fields["remaining_quantity"] = JsonPrimitive(0)
else fields.remove("remaining_quantity")
postgrest.from(table).upsert(JsonObject(fields))
```

⚠️ **The fallback the migration comment claims does not exist.** `20261013140000:9-10` says
*"the client's absolute `vaccine_batches` sync … clamps at 0"* — but the uploader *omits* the
column on update and *zeroes* it on create. On a database where the trigger was never
installed (the function body is wrapped in an existence check at `:13-21`, and the trigger
install at `20261016:127-144` is gated the same way), **every batch is created with zero
stock and stays there.** `20261018_ensure_stock_trigger_installed.sql` now installs the
trigger when it is missing but installable (`:18-39`) and aborts the migration, naming the
missing piece, when it is not (`:41-68`) — so the silent-zero case fails the deployment
instead. 🔍 The migration has not been executed against a live database; the independent
check (`select … from pg_trigger …`) still has to be run there.

## Reads, writes, and the non-atomic paths

Now opened internally by `transactionRunner.run`: `addStockBatch` (`:250`, NCC-018) and
`deductStockFromBatch` (`:456`) / `reverseDeduction` (`:564`) (NCC-021); the wrapping nests
safely when a caller already holds one (`:453-455`). Wrapped by the caller: `addBatch`,
`updateBatch`, `addStockToBatch`, `returnBorrowedStock` — called correctly from
`ClinicalVaccinationService`, `WasteRepositoryImpl`, `BorrowRepositoryImpl`.

⚠️ **Still not transactional:**

| Path | Problem |
|---|---|
| `addVaccine` / `updateVaccine` / `deleteVaccine` / `deleteBatch` | no `transactionRunner` |
| `PersonalReminderRepositoryImpl`, `DoctorAvailabilityRepositoryImpl` | repositories with **no `TransactionRunner` dependency at all** |

**Was: `addStockBatch` partial commits (NCC-018).** `:239-275` — ~10 validation branches
interleaved with the writes of one loop, with no `transactionRunner.run`: a 5-batch
submission failing on batch 3 left 2 batches, 2 `PURCHASE` transactions and 2 sync enqueues
committed while the UI reported total failure, and a retry minted fresh
`UUID.randomUUID()` batch ids (`AddStockViewModel.kt:210`) → duplicate stock. The loop now
runs inside the transaction opened at `:250`.

**Was a lost-update race (NCC-021).** `deductStockFromBatch` read then wrote with no
transaction and **no `@Version` column** on `VaccineBatchEntity`:

```kotlin
val batch = vaccineDao.getBatchById(batchId) ?: throw StockDeductionBlockedException("Batch not found")   // :461
// ... 20 lines later, inside the transaction now opened at :456 ...
vaccineDao.updateBatch(batch.deducted(quantity, transactionType, userName))                      // :481
```

`updateBatch` is a full-row `@Update` (`VaccineDao.kt:57-58`). Two concurrent deductions of
the same batch could both read the same `remainingQuantity` and the second write overwrite
the first. Same shape in `reverseDeduction` (now wrapped at `:564`), `addStockToBatch`
(`:527`) and `returnBorrowedStock` (`:618`) — those two inherit a transaction from all of
their callers, so only the two genuinely unprotected methods were wrapped. Stock is
money-adjacent, so this was the most consequential concurrency finding in the app.

## Borrow and return

`feature/borrowed/` — a borrowed dose leaves the batch (`borrowedQuantity` up,
`remainingQuantity` down) and `returnBorrowedStock` (`:592-668`) reverses it, including a
cross-batch branch that re-reads `originalBatch` at `:648-649` and throws if absent,
deliberately inside the caller's transaction so the whole return rolls back (`:646-647`).

`BorrowBatchSelectionTest.kt` (8 tests) covers the batch picker well. The **quantity-edit
diff, the outstanding cap and the cross-batch release in `BorrowRepositoryImpl` have zero
tests.**

## Waste

`feature/waste/` — `recordWaste` restores nothing (it deducts) and unwinds `wastedQuantity`
on delete. All three mutators are transaction-wrapped (`:38`, `:75`, `:121`) — ✅ correct,
and unlike `addStockBatch`.

## Dead code

| Item | Evidence |
|---|---|
| `reservedQuantity` column | declared, never read or written |
| `VaccineDao.getBatchesByVaccineSync` | zero callers |
| `Vaccine.stock`, `Vaccine.isLowStock` | never populated |

The dead `InventoryRepository.deductStock` and `VaccineDao.getActiveBatchesByExpiry` were
deleted on 2026-10-25, along with the two wrong `VaccineDao` audit-count methods (NCC-030).

## Confirmed findings

See [[Known Bugs]] for severity. Inventory-specific:

| # | Finding | Evidence |
|---|---|---|
| 1 | `InventorySort.EXPIRY` / `NEWEST` / `OLDEST` compare `"d MMM yyyy"` **lexicographically** — `"16 Oct 2026"` sorts before `"9 Oct 2026"` | Was `:121`, `:123-124`; now parsed keys at `:127-130` (NCC-017) |
| 2 | `maxByOrNull { it.purchaseDate }` picks the *oldest* batch for the MRP/net-rate fallback | Was `:81`; now `parseDate` at `:84` (NCC-017) |
| 3 | Same string-sort defect at | Was `AddBatchViewModel.kt:56`; now `:59-60` (NCC-040) |
| 4 | `addStockBatch` is not atomic | Was `:233-263`; now `transactionRunner.run` at `:250` (NCC-018) |
| 5 | Lost-update race on every batch mutation | Was `:435` + `:452`; now wrapped at `:456` / `:564` (NCC-021) |
| 6 | `isExpired` fails open on an unparseable date | `InventoryUtils.kt:24` — deliberate, see NCC-026 |
| 7 | `isLowStock` uses `<=`, so exactly-at-threshold stock is flagged LOW | `InventoryRepositoryImpl.kt:78` |
| 8 | ✅ Was: the Postgres stock trigger's `UPDATE` branch corrupted stock on a retried upsert. Fixed by `20261022_fix_stock_trigger_update_arithmetic.sql`; the regression test passes in CI. Production deployment verification remains NCC-AUDIT-003. | `20261022_fix_stock_trigger_update_arithmetic.sql`; `supabase/tests/stock_trigger_arithmetic.sql` |
| 9 | The legacy trigger `tr_update_batch_stock` was **never dropped**, so both could be installed — **every movement applied twice** | Dropped by `20261017_stock_trigger_duplicate_and_update_arithmetic.sql:26`, with a duplicate-trigger guard at `:31-46`; 🔍 live DB check pending (NCC-003) |

The app already documents hazard #1 in `domain/model/Vaccination.kt:60-61` and fixes it
there. The sites above were missed; they now parse (`:127-130`, `:84`,
`AddBatchViewModel.kt:59-60`).

## Related

- [[Vaccination Management]] — the main consumer of stock
- [[Supabase]] — the trigger and its guard
- [[Statistics Dashboard]] — `lowStockCount` feeds the dashboard tile
- [[Known Bugs]] — full ranked defect list
