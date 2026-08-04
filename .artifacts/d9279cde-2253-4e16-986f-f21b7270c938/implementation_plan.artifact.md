# Redesign Implementation Plan: Staff, Profile & Navigation

This plan outlines the redesign and enhancement of the "Manage Staff", "Profile", and "Navigation Drawer" modules for the NeoChildClinic app, following Material Design 3 principles and role-based access control (RBAC).

---

## 1. Manage Staff Module

A single-clinic module for administrators to manage staff accounts and permissions.

### Proposed Changes

#### [MODIFY] [Profile.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/domain/model/Profile.kt)
- Add `phoneNumber`, `employeeId`, `lastLogin`, `accountStatus` (Active/Inactive), `updatedAt`.

#### [MODIFY] [AdminViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/dashboard/AdminViewModel.kt)
- Add methods for `toggleStaffStatus`, `softDeleteStaff`, and calculating activity summary from audit logs.

#### [MODIFY] [ManageStaffScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/dashboard/ManageStaffScreen.kt)
- Redesign staff cards: Avatar, Name, Email, Role, Status Badge.
- Implement search by Name/Email/Role and filters by Role/Status.

#### [NEW] [StaffDetailsScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/dashboard/StaffDetailsScreen.kt)
- Detailed view with grouped sections: Personal Info, Account Info, Permissions (read-only), Activity Summary.
- Admin actions: Edit, Change Role, Reset Password, Deactivate/Activate, Delete.

#### [NEW] [AddStaffScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/dashboard/AddStaffScreen.kt)
- Form to create staff: Name, Email, Phone, Role, Temporary Password.

#### [NEW] [EditStaffScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/dashboard/EditStaffScreen.kt)
- Edit existing staff: Name, Phone, Role, Status.

---

## 2. Profile Module (My Profile)

A personal management module for the currently logged-in user.

### Proposed Changes

#### [MODIFY] [ProfileViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/profile/ProfileViewModel.kt)
- Add `updatePhoneNumber` and `changePassword` methods.
- Support fetching app version and database connectivity status.

#### [MODIFY] [ProfileScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/profile/ProfileScreen.kt)
- Redesign using Material 3 and grouped sections:
    - **Header**: Avatar, Name, Role, Status.
    - **Personal Info**: Name, Email, Phone, Role, Employee ID.
    - **Account Info**: UUID, Created/Updated At, Last Login.
    - **Security**: Change Password, Logout.
    - **About**: App Version, DB Status, Links.
- Implement "Edit Profile" flow for Name and Phone.
- Implement "Change Password" dialog.
- Implement Logout confirmation dialog.

---

## 3. Navigation Drawer (Sidebar) Redesign

A role-aware navigation menu for quick access to app modules.

### Proposed Changes

#### [NEW] [AppDrawer.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/app/AppDrawer.kt)
- Extract drawer logic into a reusable `AppDrawer` composable.
- **Header**: Tappable header with Profile Photo, Name, Role, and Online Status (linked to My Profile).
- **Menu Items**: Role-based visibility for:
    - Dashboard, Patients, Vaccinations, Due Vaccinations, Consultations, Inventory, Billing, Reports, Manage Staff, Settings, Help & Support.
- **Footer**: Display App Version and Supabase Connection Status.
- **Logout**: Styled logout button at the bottom.

#### [MODIFY] [DashboardScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/dashboard/DashboardScreen.kt)
- Replace internal drawer logic with the new `AppDrawer` composable.
- Implement Permission Guard to prevent unauthorized access even if navigated via deep link/other ways.

---

## 4. Infrastructure & Navigation

#### [MODIFY] [Routes.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/app/Routes.kt)
- Add missing routes for `STAFF_DETAILS`, `ADD_STAFF`, `EDIT_STAFF`.
- Add placeholders for `REPORTS`, `BILLING`, `CONSULTATIONS` (if needed for the drawer links).

#### [MODIFY] [AppNavigation.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/app/Navigation.kt)
- Define new composable routes and handle role-based navigation guards.

---

## Verification Plan

### Manual Verification
1.  **Manage Staff**:
    - Verify list cards, search, and role filters.
    - Test "Add Staff" flow and check record creation in Supabase.
2.  **My Profile**:
    - Verify information groups display correct logged-in user data.
    - Test "Edit Profile" and "Change Password".
3.  **Navigation Drawer**:
    - Login as different roles (Admin, Doctor, Nurse, etc.) and verify that only authorized menu items are visible.
    - Verify that clicking the header opens the Profile screen.
    - Check the footer for correct version and connection status.
    - Verify "Access Denied" if a restricted route is manually triggered (if implemented).
