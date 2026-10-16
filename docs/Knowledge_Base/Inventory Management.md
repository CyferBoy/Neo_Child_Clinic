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
and it **is** tested (`InventoryStockMovementTest.kt`, 7 tests).

## Batch selection — FEFO, implemented four times

There is no shared selector. Four implementations of first-expiry-first-out exist:

| Where | Behaviour |
|---|---|
| `AddVaccinationLogic.bestAvailableBatch` (`:11-15`) | in stock, not expired **as of the given date**, soonest expiry |
| `BorrowBatchSelection.selectBorrowBatch` (`:21-41`) | must be a **single** batch covering the whole quantity; honours a preferred id first |
| `InventoryRepositoryImpl.deductStock` (`:384-409`) | multi-batch spread, FEFO |
| `VaccineDao.getActiveBatchesByExpiry` (`:32`) | `ORDER BY expiryDate ASC` **on a non-padded date string** ⚠️ |

The "usable batch" predicate also has **five** slightly different variants across
`InventoryRepositoryImpl.kt:72`, `AddVaccinationLogic.kt:14`, `VaccineRowCard.kt:87`,
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
| Repository | `if (batch.remainingQuantity < quantity) throw InsufficientStockException` (`InventoryRepositoryImpl.kt:447-449`) | **read-then-write, not a constraint** |
| Room | ⚠️ **none** — no `CHECK` on `remainingQuantity`; verified in the exported `33.json` | |
| Postgres | `RAISE EXCEPTION … USING ERRCODE = 'check_violation'` (`20261013140000:44-49`) | AFTER trigger, raises rather than clamps |

So the only real client-side guard is a repository pre-read. Two offline devices can both
pass it; only the server trigger catches the second. See [[Supabase]].

## The client ↔ server contract

The client does **not** push absolute stock. `SyncUploader.kt:595-619`:

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
stock and stays there.** 🔍 Verify `update_batch_stock` exists in production.

## Reads, writes, and the four non-atomic paths

Well-transacted (wrapped by the caller in `transactionRunner.run`): `addBatch`, `updateBatch`,
`deductStockFromBatch`, `addStockToBatch`, `reverseDeduction`, `returnBorrowedStock` —
called correctly from `ClinicalVaccinationService`, `WasteRepositoryImpl`,
`BorrowRepositoryImpl`.

⚠️ **Not transactional:**

| Path | Problem |
|---|---|
| `addStockBatch` (`:233-263`) | ~10 validation branches interleaved with writes in one loop, **no `transactionRunner.run`**. A 5-batch submission failing on batch 3 leaves 2 batches, 2 `PURCHASE` transactions and 2 sync enqueues committed while the UI reports total failure. Retrying mints fresh `UUID.randomUUID()` batch ids (`AddStockViewModel.kt:210`) → duplicate stock. |
| `deductStock` (`:372-421`) | a `for` loop over batches with no transaction — but ⚠️ **it is dead code**, zero callers |
| `addVaccine` / `updateVaccine` / `deleteVaccine` / `deleteBatch` | no `transactionRunner` |
| `PersonalReminderRepositoryImpl`, `DoctorAvailabilityRepositoryImpl` | repositories with **no `TransactionRunner` dependency at all** |

⚠️ **Lost-update race.** `deductStockFromBatch` reads then writes with no transaction and
**no `@Version` column** on `VaccineBatchEntity`:

```kotlin
val batch = vaccineDao.getBatchById(batchId) ?: throw IllegalStateException("Batch not found")   // :435
// ... 17 lines later ...
vaccineDao.updateBatch(batch.deducted(quantity, transactionType, userName))                      // :452
```

`updateBatch` is a full-row `@Update` (`VaccineDao.kt:57-58`). Two concurrent deductions of
the same batch both read the same `remainingQuantity` and the second write overwrites the
first. Same shape in `reverseDeduction` (`:531-538`), `addStockToBatch` (`:478`), and
`returnBorrowedStock` (`:565-583`, `:613-619`). Given stock is money-adjacent, this is the
most consequential concurrency finding in the app.

## Borrow and return

`feature/borrowed/` — a borrowed dose leaves the batch (`borrowedQuantity` up,
`remainingQuantity` down) and `returnBorrowedStock` (`:557-633`) reverses it, including a
cross-batch branch that re-reads `originalBatch` at `:613` and throws if absent, deliberately
inside the caller's transaction so the whole return rolls back (`:611-612`).

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
| `InventoryRepository.deductStock` — 50 lines of FEFO-spread | zero callers; its `getActiveBatchesByExpiry` query is also only reachable from it |
| `reservedQuantity` column | declared, never read or written |
| `VaccineDao.getBatchesByVaccineSync`, `getAuditCountForVaccine`, `getAuditCountExcludingCreation` | zero callers — and the latter two query the **wrong column** (`audit_logs.remarks` instead of `old_value`/`new_value`) |
| `Vaccine.stock`, `Vaccine.isLowStock` | never populated |

## Confirmed findings

See [[Known Bugs]] for severity. Inventory-specific:

| # | Finding | Evidence |
|---|---|---|
| 1 | `InventorySort.EXPIRY` / `NEWEST` / `OLDEST` compare `"d MMM yyyy"` **lexicographically** — `"16 Oct 2026"` sorts before `"9 Oct 2026"` | `InventoryRepositoryImpl.kt:121`, `:123-124` |
| 2 | `maxByOrNull { it.purchaseDate }` picks the *oldest* batch for the MRP/net-rate fallback | `InventoryRepositoryImpl.kt:81` |
| 3 | Same string-sort defect at | `AddBatchViewModel.kt:56` |
| 4 | `addStockBatch` is not atomic | `InventoryRepositoryImpl.kt:233-263` |
| 5 | Lost-update race on every batch mutation | `InventoryRepositoryImpl.kt:435` + `:452` |
| 6 | `isExpired` fails open on an unparseable date | `InventoryUtils.kt:24` |
| 7 | `isLowStock` uses `<=`, so exactly-at-threshold stock is flagged LOW | `InventoryRepositoryImpl.kt:77` |
| 8 | The Postgres stock trigger's `UPDATE` branch applies `-OLD.quantity` with no `+NEW.quantity`, corrupting stock on any retried upsert | `20261013140000:56-61` |
| 9 | The legacy trigger `tr_update_batch_stock` is **never dropped**, so both may be installed — **every movement applied twice** | `20261016:134-138` |

The app already documents hazard #1 in `domain/model/Vaccination.kt:60-61` and fixes it
there. The four sites above were missed.

## Related

- [[Vaccination Management]] — the main consumer of stock
- [[Supabase]] — the trigger and its guard
- [[Statistics Dashboard]] — `lowStockCount` feeds the dashboard tile
- [[Known Bugs]] — full ranked defect list
