# Implementation Plan - Inventory Improvements and Add Vaccine Enhancements

This plan addresses several improvements to the vaccine inventory system, focusing on visibility of vaccines with no stock and enhancing the "Add Vaccine" workflow with better data grouping and new fields.

## Proposed Changes

### 1. Model and Database Updates

#### [MODIFY] [VaccineEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/local/entity/VaccineEntity.kt)
- Add `mrp: Double` and `netRate: Double` fields to `VaccineEntity`.
- Update `toVaccine` mapper to include these fields if necessary.

#### [MODIFY] [InventoryItem.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/domain/model/InventoryItem.kt)
- Add `mrp: Double` and `netRate: Double` to the `InventoryItem` data class.

### 2. Repository and Logic Improvements

#### [MODIFY] [InventoryRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/repository/InventoryRepositoryImpl.kt)
- **Visibility Fix**: Remove the logic in `deleteBatch` that automatically sets `isDeleted = true` for a vaccine when its last batch is removed. This ensures vaccines stay visible in the inventory list with 0 quantity.
- **Pricing Fallback**: In `getInventoryItems`, if the base `VaccineEntity` has `mrp` or `netRate` as 0.0, automatically use the values from the **latest batch** (by purchase date) as a fallback.
- **Mapping**: Update `getInventoryItems` to populate the new `mrp` and `netRate` fields from `VaccineEntity` with the fallback applied.

### 3. Add Vaccine Workflow Enhancements

#### [MODIFY] [AddVaccineViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/inventory/AddVaccineViewModel.kt)
- **Suggestions**: Add a flow or state to provide existing vaccine types and brand names (grouped by type) to the UI for autocomplete/suggestions.
- **Saving**: Update `saveVaccine` to accept and persist `mrp` and `netRate`.

#### [MODIFY] [AddVaccineScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/inventory/AddVaccineScreen.kt)
- **Hybrid Type Field**: Implement an `ExposedDropdownMenuBox` (or similar) for the `type` field that allows both selecting from a list and entering new text.
- **Grouped Brands**: Implement suggestions for `brandName` that filter based on the selected `type`.
- **New Fields**: Add numeric input fields for `MRP` and `Net Rate`.

## User Review Required

> [!IMPORTANT]
> **Database Schema Change**: Adding fields to `VaccineEntity` will require a database migration or a destructive update. If you have existing data you wish to keep, I will implement a safe migration. Otherwise, I can simply increment the version and let Room clear the tables (if configured for destructive migration).

> [!NOTE]
> **MRP and Net Rate Fallback**: These fields in the Vaccine Definition will act as the "Standard" price. If they are not set (0.0), the system will automatically display the pricing from the most recently purchased batch for that vaccine.

## Open Questions
1. Should the `MRP` and `Net Rate` be mandatory for a vaccine definition?
2. Do you have a specific list of vaccine types you'd like to pre-populate (e.g. BCG, Polio, HepB)?

## Verification Plan

### Automated Tests
- Update unit tests for `InventoryRepository` to verify vaccines with 0 batches are returned.
- Add test case for saving/loading `mrp` and `netRate`.

### Manual Verification
1.  **Check Inventory**: Delete the last batch of a vaccine and verify the vaccine still shows in the list with "0" stock.
2.  **Add Vaccine**:
    - Select a type from the dropdown and see brand suggestions.
    - Type a new vaccine type and save.
    - Enter MRP and Net Rate and verify they are saved.
3.  **Add Batch**: Verify MRP/Net Rate are pre-filled (if I implement that part).
