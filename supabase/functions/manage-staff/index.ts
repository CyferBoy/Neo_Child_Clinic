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

    // 1. Verify Authorization Header
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) {
      throw new Error('Missing Authorization header')
    }

    const token = authHeader.replace('Bearer ', '')
    const { data: { user }, error: userError } = await supabaseAdmin.auth.getUser(token)

    if (userError || !user) {
      throw new Error('Invalid user token')
    }

    // 2. Check if user is ADMIN in profiles table
    const { data: profile, error: profileGetError } = await supabaseAdmin
      .from('profiles')
      .select('role')
      .eq('id', user.id)
      .single()

    if (profileGetError || profile?.role !== 'ADMIN') {
      console.error(`Unauthorized access attempt by user ${user.email} with role ${profile?.role}`)
      throw new Error('Unauthorized: Only admins can manage staff')
    }

    // Process the request
    const body = await req.json()
    console.log("Authorized request received:", JSON.stringify(body))

    const { name, email, password, role, employeeId, phoneNumber, action } = body
    const effectiveAction = action || (name && email && password ? 'CREATE' : null)

    if (effectiveAction === 'CREATE') {
      console.log(`Creating staff user: ${email} with role: ${role}`)

      // Create the user in Supabase Auth
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

      // Insert into the profiles table
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
        throw profileError
      }

      return new Response(JSON.stringify({ success: true, user: userData.user }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200,
      })
    }

    throw new Error(`Unsupported action: ${action}`)

  } catch (error) {
    console.error("Function error:", error.message)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
