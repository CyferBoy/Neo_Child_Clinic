# Full Project SQL Schema & RLS Policies (Idempotent)

This script is safe to run multiple times. It will create missing tables, add missing columns to existing tables, and refresh security policies to their latest state.

```sql
-- ==========================================
-- 1. EXTENSIONS & HELPER FUNCTIONS
-- ==========================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Helper to get user role
CREATE OR REPLACE FUNCTION public.get_my_role()
RETURNS text AS $$
DECLARE
  user_role text;
BEGIN
  SELECT role INTO user_role FROM public.profiles WHERE id = auth.uid();
  RETURN COALESCE(user_role, 'nurse');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ==========================================
-- 2. TABLES & COLUMNS (Idempotent)
-- ==========================================

-- Profiles
CREATE TABLE IF NOT EXISTS public.profiles (id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE);
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS email TEXT UNIQUE;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS display_name TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS phone_number TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS employee_id TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'nurse';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS fcm_token TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT now();
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS last_login TIMESTAMPTZ;

-- Patients
CREATE TABLE IF NOT EXISTS public.patients (id UUID PRIMARY KEY DEFAULT gen_random_uuid());
ALTER TABLE public.patients ADD COLUMN IF NOT EXISTS patient_clinic_id TEXT UNIQUE;
ALTER TABLE public.patients ADD COLUMN IF NOT EXISTS name TEXT NOT NULL DEFAULT '';
ALTER TABLE public.patients ADD COLUMN IF NOT EXISTS phone TEXT NOT NULL DEFAULT '';
ALTER TABLE public.patients ADD COLUMN IF NOT EXISTS alternate_phone TEXT;
ALTER TABLE public.patients ADD COLUMN IF NOT EXISTS dob TEXT; -- Use TEXT to support app's custom date format
ALTER TABLE public.patients ADD COLUMN IF NOT EXISTS gender TEXT;
ALTER TABLE public.patients ADD COLUMN IF NOT EXISTS address TEXT;
ALTER TABLE public.patients ADD COLUMN IF NOT EXISTS registration_date TEXT DEFAULT now()::text;
ALTER TABLE public.patients ADD COLUMN IF NOT EXISTS attachments TEXT;
ALTER TABLE public.patients ADD COLUMN IF NOT EXISTS updated_at TEXT DEFAULT now()::text;
ALTER TABLE public.patients ADD COLUMN IF NOT EXISTS is_synced BOOLEAN DEFAULT true;

-- Patient Visits
CREATE TABLE IF NOT EXISTS public.patient_visits (id UUID PRIMARY KEY DEFAULT gen_random_uuid());
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS patient_id UUID REFERENCES public.patients(id) ON DELETE CASCADE;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS date_given TEXT NOT NULL DEFAULT now()::text;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS doctor TEXT;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS vaccine_names TEXT;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS vaccine_ids TEXT;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS batch_ids TEXT;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS batch_numbers TEXT;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS materials_used TEXT;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS receipt_number TEXT;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS total_paid DECIMAL(12,2) DEFAULT 0.0;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS payment_id UUID;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS nxt_vaccine_names TEXT;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS next_due_date TEXT;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS cash_amount DECIMAL(12,2) DEFAULT 0.0;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS online_amount DECIMAL(12,2) DEFAULT 0.0;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS with_fees BOOLEAN DEFAULT false;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS doctors_acc BOOLEAN DEFAULT false;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'ACTIVE';
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS source TEXT DEFAULT 'CLINIC';
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS visit_type TEXT DEFAULT 'VACCINATION';
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS inventory_status TEXT DEFAULT 'PENDING';
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS created_at TEXT DEFAULT now()::text;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS updated_at TEXT DEFAULT now()::text;
ALTER TABLE public.patient_visits ADD COLUMN IF NOT EXISTS is_synced BOOLEAN DEFAULT true;

-- Consultations
CREATE TABLE IF NOT EXISTS public.consultations (id UUID PRIMARY KEY DEFAULT gen_random_uuid());
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS visit_id UUID REFERENCES public.patient_visits(id) ON DELETE CASCADE;
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS patient_id UUID REFERENCES public.patients(id) ON DELETE CASCADE;
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS doctor_id UUID REFERENCES public.profiles(id);
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS doctor_name TEXT;
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS date TEXT NOT NULL DEFAULT now()::text;
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS amount DECIMAL(12,2) NOT NULL DEFAULT 0.0;
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS cash_amount DECIMAL(12,2) DEFAULT 0.0;
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS online_amount DECIMAL(12,2) DEFAULT 0.0;
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS problem TEXT;
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS next_follow_up_date TEXT;
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS created_at TEXT DEFAULT now()::text;
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS updated_at TEXT DEFAULT now()::text;
ALTER TABLE public.consultations ADD COLUMN IF NOT EXISTS is_synced BOOLEAN DEFAULT true;

-- Vaccines
CREATE TABLE IF NOT EXISTS public.vaccines (id UUID PRIMARY KEY DEFAULT gen_random_uuid());
ALTER TABLE public.vaccines ADD COLUMN IF NOT EXISTS type TEXT;
ALTER TABLE public.vaccines ADD COLUMN IF NOT EXISTS brand_name TEXT;
ALTER TABLE public.vaccines ADD COLUMN IF NOT EXISTS company_name TEXT;
ALTER TABLE public.vaccines ADD COLUMN IF NOT EXISTS manufacturer TEXT;
ALTER TABLE public.vaccines ADD COLUMN IF NOT EXISTS category TEXT;
ALTER TABLE public.vaccines ADD COLUMN IF NOT EXISTS dose_schedule TEXT;
ALTER TABLE public.vaccines ADD COLUMN IF NOT EXISTS storage_details TEXT;
ALTER TABLE public.vaccines ADD COLUMN IF NOT EXISTS mrp DECIMAL(12,2) DEFAULT 0.0;
ALTER TABLE public.vaccines ADD COLUMN IF NOT EXISTS net_rate DECIMAL(12,2) DEFAULT 0.0;
ALTER TABLE public.vaccines ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ DEFAULT now();

-- Batches
CREATE TABLE IF NOT EXISTS public.vaccine_batches (id UUID PRIMARY KEY DEFAULT gen_random_uuid());
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS vaccine_id UUID REFERENCES public.vaccines(id) ON DELETE CASCADE;
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS batch_number TEXT;
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS manufacturer TEXT;
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS purchase_date TEXT;
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS expiry_date TEXT;
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS purchase_quantity INTEGER DEFAULT 0;
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS remaining_quantity INTEGER DEFAULT 0;
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS reserved_quantity INTEGER DEFAULT 0;
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS used_quantity INTEGER DEFAULT 0;
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS wasted_quantity INTEGER DEFAULT 0;
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS borrowed_quantity INTEGER DEFAULT 0;
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS supplier TEXT;
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS purchase_cost DECIMAL(12,2);
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS selling_price DECIMAL(12,2);
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'ACTIVE';
ALTER TABLE public.vaccine_batches ADD COLUMN IF NOT EXISTS updated_at TEXT DEFAULT now()::text;

-- Finance
CREATE TABLE IF NOT EXISTS public.finance_transactions (id UUID PRIMARY KEY DEFAULT gen_random_uuid());
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS timestamp TIMESTAMPTZ DEFAULT now();
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS type TEXT;
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS category TEXT;
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS amount DECIMAL(12,2) DEFAULT 0.0;
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS cash_amount DECIMAL(12,2) DEFAULT 0.0;
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS online_amount DECIMAL(12,2) DEFAULT 0.0;
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS currency TEXT DEFAULT 'INR';
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS payment_method TEXT;
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS patient_id UUID REFERENCES public.patients(id);
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS visit_id UUID REFERENCES public.patient_visits(id);
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS reference_number TEXT;
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS remarks TEXT;
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS recorded_by TEXT;
ALTER TABLE public.finance_transactions ADD COLUMN IF NOT EXISTS is_synced BOOLEAN DEFAULT true;

-- Inventory Transactions
CREATE TABLE IF NOT EXISTS public.inventory_transactions (id UUID PRIMARY KEY DEFAULT gen_random_uuid());
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS vaccine_id UUID REFERENCES public.vaccines(id) ON DELETE CASCADE;
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS batch_id UUID REFERENCES public.vaccine_batches(id) ON DELETE CASCADE;
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS patient_id UUID REFERENCES public.patients(id);
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS vaccination_id UUID REFERENCES public.patient_visits(id);
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS transaction_type TEXT;
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS previous_quantity INTEGER DEFAULT 0;
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS current_quantity INTEGER DEFAULT 0;
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS timestamp TIMESTAMPTZ DEFAULT now();
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS "user" TEXT;
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'COMPLETED';
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS is_synced BOOLEAN DEFAULT true;

-- Patient Notes
CREATE TABLE IF NOT EXISTS public.patient_notes (id UUID PRIMARY KEY DEFAULT gen_random_uuid());
ALTER TABLE public.patient_notes ADD COLUMN IF NOT EXISTS patient_id UUID REFERENCES public.patients(id) ON DELETE CASCADE;
ALTER TABLE public.patient_notes ADD COLUMN IF NOT EXISTS content TEXT NOT NULL DEFAULT '';
ALTER TABLE public.patient_notes ADD COLUMN IF NOT EXISTS author TEXT;
ALTER TABLE public.patient_notes ADD COLUMN IF NOT EXISTS timestamp TIMESTAMPTZ DEFAULT now();
ALTER TABLE public.patient_notes ADD COLUMN IF NOT EXISTS is_synced BOOLEAN DEFAULT true;

-- Audit Logs
CREATE TABLE IF NOT EXISTS public.audit_logs (id UUID PRIMARY KEY DEFAULT gen_random_uuid());
ALTER TABLE public.audit_logs ADD COLUMN IF NOT EXISTS timestamp TIMESTAMPTZ DEFAULT now();
ALTER TABLE public.audit_logs ADD COLUMN IF NOT EXISTS "user" TEXT;
ALTER TABLE public.audit_logs ADD COLUMN IF NOT EXISTS module TEXT;
ALTER TABLE public.audit_logs ADD COLUMN IF NOT EXISTS entity_type TEXT;
ALTER TABLE public.audit_logs ADD COLUMN IF NOT EXISTS entity_id TEXT;
ALTER TABLE public.audit_logs ADD COLUMN IF NOT EXISTS action TEXT;
ALTER TABLE public.audit_logs ADD COLUMN IF NOT EXISTS old_value TEXT;
ALTER TABLE public.audit_logs ADD COLUMN IF NOT EXISTS new_value TEXT;
ALTER TABLE public.audit_logs ADD COLUMN IF NOT EXISTS remarks TEXT;
ALTER TABLE public.audit_logs ADD COLUMN IF NOT EXISTS device TEXT;
ALTER TABLE public.audit_logs ADD COLUMN IF NOT EXISTS patient_id UUID REFERENCES public.patients(id);
ALTER TABLE public.audit_logs ADD COLUMN IF NOT EXISTS is_synced BOOLEAN DEFAULT true;

-- Waste Records
CREATE TABLE IF NOT EXISTS public.waste_records (id UUID PRIMARY KEY DEFAULT gen_random_uuid());
ALTER TABLE public.waste_records ADD COLUMN IF NOT EXISTS vaccine_id UUID REFERENCES public.vaccines(id);
ALTER TABLE public.waste_records ADD COLUMN IF NOT EXISTS batch_id UUID REFERENCES public.vaccine_batches(id);
ALTER TABLE public.waste_records ADD COLUMN IF NOT EXISTS brand_name TEXT;
ALTER TABLE public.waste_records ADD COLUMN IF NOT EXISTS batch_number TEXT;
ALTER TABLE public.waste_records ADD COLUMN IF NOT EXISTS expiry_date DATE;
ALTER TABLE public.waste_records ADD COLUMN IF NOT EXISTS date_wasted DATE;
ALTER TABLE public.waste_records ADD COLUMN IF NOT EXISTS reason TEXT;
ALTER TABLE public.waste_records ADD COLUMN IF NOT EXISTS quantity INTEGER;
ALTER TABLE public.waste_records ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();
ALTER TABLE public.waste_records ADD COLUMN IF NOT EXISTS is_synced BOOLEAN DEFAULT true;

-- Borrow Records
CREATE TABLE IF NOT EXISTS public.borrow_records (id UUID PRIMARY KEY DEFAULT gen_random_uuid());
ALTER TABLE public.borrow_records ADD COLUMN IF NOT EXISTS doctor_name TEXT;
ALTER TABLE public.borrow_records ADD COLUMN IF NOT EXISTS vaccine_id UUID REFERENCES public.vaccines(id);
ALTER TABLE public.borrow_records ADD COLUMN IF NOT EXISTS batch_id UUID REFERENCES public.vaccine_batches(id);
ALTER TABLE public.borrow_records ADD COLUMN IF NOT EXISTS borrowed_date TEXT;
ALTER TABLE public.borrow_records ADD COLUMN IF NOT EXISTS quantity INTEGER DEFAULT 1;
ALTER TABLE public.borrow_records ADD COLUMN IF NOT EXISTS is_returned BOOLEAN DEFAULT false;
ALTER TABLE public.borrow_records ADD COLUMN IF NOT EXISTS returned_date TEXT;
ALTER TABLE public.borrow_records ADD COLUMN IF NOT EXISTS type TEXT DEFAULT 'BY';
ALTER TABLE public.borrow_records ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE public.borrow_records ADD COLUMN IF NOT EXISTS is_synced BOOLEAN DEFAULT true;

-- Reminders
CREATE TABLE IF NOT EXISTS public.reminders (id BIGSERIAL PRIMARY KEY);
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS patient_id UUID REFERENCES public.patients(id) ON DELETE CASCADE;
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS original_visit_id UUID REFERENCES public.patient_visits(id) ON DELETE SET NULL;
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS vaccine_name TEXT NOT NULL DEFAULT '';
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS due_date TEXT;
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'ACTIVE';
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS priority TEXT DEFAULT 'NORMAL';
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS reminder_enabled BOOLEAN DEFAULT true;
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS category TEXT DEFAULT 'VACCINATION';
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS completion_date TEXT;
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS performed_by TEXT;
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS dismissal_date TEXT;
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS dismissal_reason TEXT;
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS created_at TEXT DEFAULT now()::text;
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS updated_at TEXT DEFAULT now()::text;
ALTER TABLE public.reminders ADD COLUMN IF NOT EXISTS is_synced BOOLEAN DEFAULT true;

-- Vaccination Items
CREATE TABLE IF NOT EXISTS public.vaccination_items (id UUID PRIMARY KEY DEFAULT gen_random_uuid());
ALTER TABLE public.vaccination_items ADD COLUMN IF NOT EXISTS vaccination_id UUID REFERENCES public.patient_visits(id) ON DELETE CASCADE;
ALTER TABLE public.vaccination_items ADD COLUMN IF NOT EXISTS vaccine_id UUID REFERENCES public.vaccines(id);
ALTER TABLE public.vaccination_items ADD COLUMN IF NOT EXISTS batch_id UUID REFERENCES public.vaccine_batches(id);
ALTER TABLE public.vaccination_items ADD COLUMN IF NOT EXISTS quantity INTEGER DEFAULT 1;
ALTER TABLE public.vaccination_items ADD COLUMN IF NOT EXISTS mrp DECIMAL(12,2);
ALTER TABLE public.vaccination_items ADD COLUMN IF NOT EXISTS net_rate DECIMAL(12,2);
ALTER TABLE public.vaccination_items ADD COLUMN IF NOT EXISTS expiry_date DATE;

-- ==========================================
-- 3. INDEXES (Idempotent)
-- ==========================================

CREATE INDEX IF NOT EXISTS idx_patients_clinic_id ON public.patients(patient_clinic_id);
CREATE INDEX IF NOT EXISTS idx_visits_patient_id ON public.patient_visits(patient_id);
CREATE INDEX IF NOT EXISTS idx_consultations_patient_id ON public.consultations(patient_id);
CREATE INDEX IF NOT EXISTS idx_finance_patient_id ON public.finance_transactions(patient_id);
CREATE INDEX IF NOT EXISTS idx_reminders_patient_id ON public.reminders(patient_id);
CREATE INDEX IF NOT EXISTS idx_audit_patient_id ON public.audit_logs(patient_id);
CREATE INDEX IF NOT EXISTS idx_inventory_batch_id ON public.inventory_transactions(batch_id);

-- ==========================================
-- 4. TRIGGERS (Idempotent)
-- ==========================================

CREATE OR REPLACE FUNCTION public.update_batch_stock()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.vaccine_batches
        SET remaining_quantity = remaining_quantity + NEW.quantity,
            updated_at = now()
        WHERE id = NEW.batch_id;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.vaccine_batches
        SET remaining_quantity = remaining_quantity - OLD.quantity,
            updated_at = now()
        WHERE id = OLD.batch_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_update_batch_stock ON public.inventory_transactions;
CREATE TRIGGER tr_update_batch_stock
AFTER INSERT OR DELETE ON public.inventory_transactions
FOR EACH ROW EXECUTE FUNCTION public.update_batch_stock();

-- ==========================================
-- 5. RLS POLICIES (Refresh All)
-- ==========================================

-- Enable RLS
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.patients ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.patient_visits ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.consultations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vaccines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vaccine_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vaccination_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.finance_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reminders ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.patient_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.waste_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.borrow_records ENABLE ROW LEVEL SECURITY;

-- 1. Patients Policies
DROP POLICY IF EXISTS "Staff can view everything" ON public.patients;
CREATE POLICY "Staff can view everything" ON public.patients FOR SELECT USING (true);
DROP POLICY IF EXISTS "Staff can manage patients" ON public.patients;
CREATE POLICY "Staff can manage patients" ON public.patients FOR ALL USING (public.get_my_role() IN ('admin', 'doctor', 'nurse', 'receptionist'));

-- 2. Visit/Encounter Policies
DROP POLICY IF EXISTS "Staff can manage visits" ON public.patient_visits;
CREATE POLICY "Staff can manage visits" ON public.patient_visits FOR ALL USING (public.get_my_role() IN ('admin', 'doctor', 'nurse', 'receptionist'));
DROP POLICY IF EXISTS "Staff can manage consultations" ON public.consultations;
CREATE POLICY "Staff can manage consultations" ON public.consultations FOR ALL USING (public.get_my_role() IN ('admin', 'doctor', 'nurse'));

-- 3. Inventory Policies
DROP POLICY IF EXISTS "Staff can manage inventory" ON public.vaccines;
CREATE POLICY "Staff can manage inventory" ON public.vaccines FOR ALL USING (public.get_my_role() IN ('admin', 'inventory_manager', 'doctor', 'nurse'));
DROP POLICY IF EXISTS "Staff can manage batches" ON public.vaccine_batches;
CREATE POLICY "Staff can manage batches" ON public.vaccine_batches FOR ALL USING (public.get_my_role() IN ('admin', 'inventory_manager', 'doctor', 'nurse'));
DROP POLICY IF EXISTS "Staff can manage transactions" ON public.inventory_transactions;
CREATE POLICY "Staff can manage transactions" ON public.inventory_transactions FOR ALL USING (public.get_my_role() IN ('admin', 'inventory_manager', 'doctor', 'nurse'));

-- 4. Finance Policies
DROP POLICY IF EXISTS "Staff can manage finance" ON public.finance_transactions;
CREATE POLICY "Staff can manage finance" ON public.finance_transactions FOR ALL USING (public.get_my_role() IN ('admin', 'receptionist', 'doctor'));

-- 5. Audit & Notes
DROP POLICY IF EXISTS "Admins can view audit logs" ON public.audit_logs;
CREATE POLICY "Admins can view audit logs" ON public.audit_logs FOR SELECT USING (public.get_my_role() = 'admin');
DROP POLICY IF EXISTS "Staff can record audit logs" ON public.audit_logs;
CREATE POLICY "Staff can record audit logs" ON public.audit_logs FOR INSERT WITH CHECK (true);
DROP POLICY IF EXISTS "Staff can manage notes" ON public.patient_notes;
CREATE POLICY "Staff can manage notes" ON public.patient_notes FOR ALL USING (public.get_my_role() IN ('admin', 'doctor', 'nurse', 'receptionist'));

-- 6. Profiles
DROP POLICY IF EXISTS "Users can view their own profile" ON public.profiles;
CREATE POLICY "Users can view their own profile" ON public.profiles FOR SELECT USING (auth.uid() = id);
DROP POLICY IF EXISTS "Admins can manage all profiles" ON public.profiles;
CREATE POLICY "Admins can manage all profiles" ON public.profiles FOR ALL USING (public.get_my_role() = 'admin');

-- 7. Reminders/Waste/Borrow
DROP POLICY IF EXISTS "Staff can manage reminders" ON public.reminders;
CREATE POLICY "Staff can manage reminders" ON public.reminders FOR ALL USING (public.get_my_role() IN ('admin', 'doctor', 'nurse', 'receptionist'));
DROP POLICY IF EXISTS "Staff can manage waste" ON public.waste_records;
CREATE POLICY "Staff can manage waste" ON public.waste_records FOR ALL USING (public.get_my_role() IN ('admin', 'inventory_manager', 'doctor', 'nurse'));
DROP POLICY IF EXISTS "Staff can manage borrow" ON public.borrow_records;
CREATE POLICY "Staff can manage borrow" ON public.borrow_records FOR ALL USING (public.get_my_role() IN ('admin', 'inventory_manager', 'doctor', 'nurse'));
```
