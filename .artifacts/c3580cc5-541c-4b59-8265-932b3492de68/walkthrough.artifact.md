# Walkthrough: Add Consultation Screen & Medical History Redesign

I have refactored the **Add Consultation** screen and redesigned the **Patient Details** medical history view to provide a cleaner, more consistent, and professional experience.

### 1. New Add Consultation Screen
The Add Consultation screen now follows the modern, scrollable form design used for vaccinations.
- **Read-Only Patient Context**: Displays verified patient name and ID.
- **Automatic Doctor Selection**: Populates the doctor's name from the logged-in profile.
- **Chief Complaint**: Added a dedicated field for "Problem / Chief Complaint".
- **Atomic Transaction**: Saving a consultation now creates a `Visit` record, a `Consultation` record, and a `Finance` record atomically.
- **Split Payments**: Support for recording both Cash and Online payments in a single visit.

### 2. Redesigned Patient Details History
The medical history view has been overhauled for better readability and information density.
- **Segmented History**: Quick toggle between **Vaccination** and **Consultation** records.
- **Modern Cards**:
    - **Vaccination Card**: Shows dates, administered vaccines, next follow-up, and split payment details.
    - **Consultation Card**: Shows doctor name, chief complaint, total fee, and payment breakdown.
- **Dynamic FAB**: Floating Action Button now expands to allow adding either a Vaccination or a Consultation.

### 3. Data Integrity & Schema Updates
- **Unified Visit Model**: Added `visitType` to the clinical visit table to distinguish between various types of patient encounters.
- **Finance Integration**: Both vaccinations and consultations are now automatically linked to the financial tracking system with support for split payment methods.

> [!TIP]
> You can now long-press any card in the patient's history to access Edit or Delete options.
