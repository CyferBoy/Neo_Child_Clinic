import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  // Handle CORS
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? ''
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

    if (!supabaseUrl || !supabaseServiceKey) {
      throw new Error('Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY')
    }

    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey)

    // Log the request for debugging
    const body = await req.json()
    console.log("Received request:", JSON.stringify(body))

    const { name, email, password, role, employeeId, phoneNumber, action } = body

    // Default to 'CREATE' if action is missing but all data is present
    const effectiveAction = action || (name && email && password ? 'CREATE' : null)

    if (effectiveAction === 'CREATE') {
      console.log(`Creating staff user: ${email} with role: ${role}`)

      // 1. Create the user in Supabase Auth
      const { data: userData, error: authError } = await supabaseAdmin.auth.admin.createUser({
        email,
        password,
        email_confirm: true,
        user_metadata: {
          name,
          role,
          employee_id: employeeId,
          phone_number: phoneNumber,
          display_name: name
        }
      })

      if (authError) {
        console.error("Auth creation error:", authError.message)
        throw authError
      }

      // 2. Insert into the profiles table
      const { error: profileError } = await supabaseAdmin
        .from('profiles')
        .insert([
          {
            id: userData.user.id,
            email: email,
            display_name: name,
            role: role,
            employee_id: employeeId,
            phone_number: phoneNumber,
            is_active: true
          }
        ])

      if (profileError) {
        console.error("Profile insertion error:", profileError.message)
        // Cleanup the auth user if profile fails?
        // For simplicity, we just throw, but in prod you might want to delete the auth user.
        throw profileError
      }

      return new Response(JSON.stringify({ success: true, user: userData.user }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200,
      })
    }

    console.error("Unsupported or missing action:", action)
    throw new Error(`Unsupported action: ${action}`)

  } catch (error) {
    console.error("Function error:", error.message)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
