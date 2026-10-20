-- 1. Fix vaccine_batches (rename batch_id column or align primary key)
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
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT now(),
    "user" TEXT NOT NULL,
    notes TEXT,
    status TEXT DEFAULT 'COMPLETED',
    failure_reason TEXT,
    processed_at TIMESTAMP WITH TIME ZONE,
    processed_by TEXT,
    is_synced BOOLEAN DEFAULT true
);

-- 3. Patient Notes Table
CREATE TABLE IF NOT EXISTS public.patient_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES public.patients(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    author TEXT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT now(),
    is_synced BOOLEAN DEFAULT true
);

-- 4. Audit Logs Table (UUID ID)
DROP TABLE IF EXISTS public.audit_logs CASCADE;
CREATE TABLE public.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT now(),
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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    is_synced BOOLEAN DEFAULT true
);

-- 6. Add transaction_group_id to sync_queue (if you use cloud sync queue)
-- Note: Usually sync_queue is local only, but if you sync it, add this:
-- ALTER TABLE public.sync_queue ADD COLUMN IF NOT EXISTS transaction_group_id TEXT;

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
