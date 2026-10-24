begin;

-- Drop legacy permissive policies that relied on the insecure get_my_role() function.
-- These were created via unversioned scratch files and bypassed the App Metadata RBAC.
DO $$
BEGIN
    -- Attempt to drop the legacy policy on profiles if it exists
    IF EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public'
          AND tablename = 'profiles'
          AND policyname = 'Admins can manage all profiles'
    ) THEN
        DROP POLICY "Admins can manage all profiles" ON public.profiles;
    END IF;

    -- Attempt to drop the legacy get_my_role function if it exists
    -- CASCADE is required because 14 other legacy policies (e.g. "Admins can view all devices")
    -- reference this function. We want to drop the function and all policies that depend on it
    -- so that they don't break subsequent migrations or allow bypasses.
    IF EXISTS (
        SELECT 1 FROM pg_proc p
        JOIN pg_namespace n ON p.pronamespace = n.oid
        WHERE n.nspname = 'public' AND p.proname = 'get_my_role'
    ) THEN
        DROP FUNCTION public.get_my_role() CASCADE;
    END IF;
END $$;

commit;