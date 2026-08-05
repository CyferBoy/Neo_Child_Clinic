-- 1. Fix vaccine_batches (rename batch_id column or align primary key)
-- The app uses @SerialName("id") for batchId, so the primary key in SQL should be 'id'
ALTER TABLE public.vaccine_batches RENAME COLUMN id TO old_id; -- backup if exists
ALTER TABLE public.vaccine_batches RENAME COLUMN batch_id TO id;

-- Ensure all required columns exist in vaccine_batches
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='vaccine_batches' AND column_name='reserved_quantity') THEN
        ALTER TABLE public.vaccine_batches ADD COLUMN reserved_quantity INTEGER DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='vaccine_batches' AND column_name='used_quantity') THEN
        ALTER TABLE public.vaccine_batches ADD COLUMN used_quantity INTEGER DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='vaccine_batches' AND column_name='wasted_quantity') THEN
        ALTER TABLE public.vaccine_batches ADD COLUMN wasted_quantity INTEGER DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='vaccine_batches' AND column_name='borrowed_quantity') THEN
        ALTER TABLE public.vaccine_batches ADD COLUMN borrowed_quantity INTEGER DEFAULT 0;
    END IF;
END $$;

-- 2. Inventory Transactions Table
CREATE TABLE IF NOT EXISTS public.inventory_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vaccine_id UUID NOT NULL REFERENCES public.vaccines(id) ON DELETE CASCADE,
    batch_id UUID NOT NULL REFERENCES public.vaccine_batches(id) ON DELETE CASCADE,
    patient_id UUID REFERENCES public.patients(id),
    vaccination_id UUID REFERENCES public.patient_visits(id),
    transaction_type TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    previous_quantity INTEGER NOT NULL,
    current_quantity INTEGER NOT NULL,
    timestamp BIGINT NOT NULL,
    "user" TEXT NOT NULL,
    notes TEXT,
    status TEXT DEFAULT 'COMPLETED',
    failure_reason TEXT,
    processed_at BIGINT,
    processed_by TEXT,
    is_synced BOOLEAN DEFAULT true
);

-- 3. Patient Notes Table
CREATE TABLE IF NOT EXISTS public.patient_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES public.patients(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    author TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    is_synced BOOLEAN DEFAULT true
);

-- 4. Audit Logs Table (Updated with UUID id)
-- If audit_logs exists with BIGINT id, we might need to recreate it
DROP TABLE IF EXISTS public.audit_logs;
CREATE TABLE public.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp BIGINT NOT NULL,
    "user" TEXT NOT NULL,
    module TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    action TEXT NOT NULL,
    old_value TEXT,
    new_value TEXT,
    remarks TEXT,
    device TEXT,
    is_synced BOOLEAN DEFAULT true,
    patient_id UUID REFERENCES public.patients(id)
);

-- 5. Waste Records Table
CREATE TABLE IF NOT EXISTS public.waste_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vaccine_id UUID NOT NULL REFERENCES public.vaccines(id),
    batch_id UUID NOT NULL REFERENCES public.vaccine_batches(id),
    brand_name TEXT,
    batch_number TEXT,
    expiry_date DATE,
    date_wasted DATE,
    reason TEXT,
    quantity INTEGER,
    updated_at BIGINT,
    is_synced BOOLEAN DEFAULT true
);

-- RLS Policies for new tables
ALTER TABLE public.inventory_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.patient_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.waste_records ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Staff can manage transactions" ON public.inventory_transactions FOR ALL USING (public.get_my_role() IN ('admin', 'inventory_manager', 'doctor', 'nurse'));
CREATE POLICY "Staff can manage notes" ON public.patient_notes FOR ALL USING (public.get_my_role() IN ('admin', 'doctor', 'nurse', 'receptionist'));
CREATE POLICY "Admins can view audit logs" ON public.audit_logs FOR SELECT USING (public.get_my_role() = 'admin');
CREATE POLICY "Staff can record audit logs" ON public.audit_logs FOR INSERT WITH CHECK (true);
CREATE POLICY "Staff can manage waste" ON public.waste_records FOR ALL USING (public.get_my_role() IN ('admin', 'inventory_manager', 'doctor', 'nurse'));
