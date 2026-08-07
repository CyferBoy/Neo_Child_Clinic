# Walkthrough: Fix Foreign Key Violations on Deletion

I have addressed the `finance_transactions_visit_id_fkey` error that occurred when deleting records. The fix ensures that associated data is removed in the correct order, both locally and in the cloud.

## Key Changes

### 1. Cascade Deletions in Repositories
- **Finance Module**: Added functionality to find and delete financial transactions linked to a specific visit ID.
- **Vaccination Repository**: Updated the deletion logic to remove any linked income records from the `finance_transactions` table before deleting the vaccination itself.
- **Consultation Repository**: Enhanced the deletion process to remove:
    1. Linked financial records.
    2. The "Visit Header" from the `patient_visits` table.
    3. The consultation record itself.

### 2. Smart Sync Ordering
- **Sync Engine**: Refactored the synchronization logic to handle `DELETE` operations with high intelligence.
- **Reverse Priority**: The system now automatically negates entity priority during deletion. This ensures that "Child" entities (like Finance records) are always deleted from Supabase before their "Parent" entities (like Visits), preventing foreign key constraint violations on the server.

## Verification Results

- **Local Cleanup**: Verified that deleting a vaccination or consultation now removes all associated financial data from the local database.
- **Visit Header Integrity**: Confirmed that consultation deletions now correctly clean up the shared `patient_visits` table.
- **Server Sync**: The new sorting logic ensures that the cloud database remains in a consistent state during background synchronization.

> [!TIP]
> This fix not only prevents crashes but also ensures that your financial reports remain accurate by removing income associated with cancelled or deleted clinic visits.
