# Implementation Plan - Move Completed & Dismissed Cards Above Search in Due Tab

Reorder the Due Vaccination tab layout to place the Completed and Dismissed navigation cards at the top (above the search bar) and remove the counter numbers from the cards.

## User Review Required

> [!NOTE]
> The Completed and Dismissed summary cards will be displayed as clean navigation cards without counter numbers at the top of the Due Vaccination tab, immediately above the search bar.

## Proposed Changes

### Reminder Features

#### [MODIFY] [ReminderCards.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/reminder/ReminderCards.kt)
- Update `CompletedDismissedSummaryCards` to remove the counter numbers (`completedCount` and `dismissedCount`).
- Adjust padding and alignment so the cards serve as clean, balanced navigation buttons for "Completed" and "Dismissed".

#### [MODIFY] [DueTab.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/reminder/DueTab.kt)
- Move `CompletedDismissedSummaryCards` above the search bar (`OutlinedTextField`) in the `LazyColumn`.
- Update the parameters passed to `CompletedDismissedSummaryCards`.

## Verification Plan

### Automated Tests
- Run `gradle_build("app:assembleDebug")` to verify compilation.

### Manual Verification
- Open the **Due Vaccination** tab.
- Verify that the **Completed** and **Dismissed** cards appear at the top of the tab above the search bar.
- Verify that no counter numbers are displayed on the cards.
- Tap either card to confirm navigation to the Completed & Dismissed screen.
