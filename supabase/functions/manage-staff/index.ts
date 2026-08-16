import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders })
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? ""
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    if (!supabaseUrl || !serviceRoleKey) throw new Error("Server configuration error")

    const admin = createClient(supabaseUrl, serviceRoleKey)

    const authHeader = req.headers.get("Authorization")
    if (!authHeader?.startsWith("Bearer ")) throw new Error("Missing Authorization header")
    const token = authHeader.substring("Bearer ".length)

    const { data: { user }, error: userError } = await admin.auth.getUser(token)
    if (userError || !user) throw new Error("Invalid user token")

    const { data: actor, error: actorError } = await admin
      .from("profiles")
      .select("role,is_active,is_deleted")
      .eq("id", user.id)
      .single()

    if (actorError || actor?.role !== "admin" || actor.is_active !== true || actor.is_deleted === true) {
      return json({ error: "Unauthorized: only active administrators can manage staff" }, 403)
    }

    const body = await req.json()
    const action = body.action ?? (body.name && body.email && body.password ? "CREATE" : null)

    if (action === "CREATE") {
      const { name, email, password, role, employeeId, phoneNumber } = body
      if (!name || !email || !password || !role) throw new Error("name, email, password and role are required")
      validateRole(role)

      const { data: userData, error: authError } = await admin.auth.admin.createUser({
        email,
        password,
        email_confirm: true,
        user_metadata: {
          display_name: name,
          name,
          role,
          employee_id: employeeId ?? null,
          phone_number: phoneNumber ?? null,
        },
      })
      if (authError) throw authError

      const { error: profileError } = await admin.from("profiles").insert({
        id: userData.user.id,
        email,
        display_name: name,
        role,
        employee_id: employeeId ?? null,
        phone_number: phoneNumber ?? null,
        is_active: true,
        is_deleted: false,
      })

      if (profileError) {
        // Avoid leaving an orphaned Auth user when profile creation fails.
        await admin.auth.admin.deleteUser(userData.user.id)
        throw profileError
      }

      return json({ success: true, user_id: userData.user.id })
    }

    const staffId = body.staffId
    if (!staffId) throw new Error("staffId is required")
    if (staffId === user.id && ["CHANGE_ROLE", "SET_STATUS", "SOFT_DELETE"].includes(action)) {
      throw new Error("An administrator cannot disable or delete their own account")
    }

    const { data: target, error: targetError } = await admin
      .from("profiles")
      .select("id,email,is_active,is_deleted")
      .eq("id", staffId)
      .single()
    if (targetError || !target) throw new Error("Staff member not found")

    switch (action) {
      case "UPDATE_PROFILE": {
        const update: Record<string, unknown> = {}
        if (body.name !== undefined) update.display_name = body.name
        if (body.phoneNumber !== undefined) update.phone_number = body.phoneNumber
        if (body.role !== undefined) {
          validateRole(body.role)
          update.role = body.role
        }
        update.updated_by = user.id

        const { error } = await admin.from("profiles").update(update).eq("id", staffId)
        if (error) throw error

        const { error: authUpdateError } = await admin.auth.admin.updateUserById(staffId, {
          user_metadata: {
            display_name: body.name,
            name: body.name,
            role: body.role,
            phone_number: body.phoneNumber,
          },
        })
        if (authUpdateError) throw authUpdateError
        return json({ success: true })
      }

      case "CHANGE_ROLE": {
        validateRole(body.role)
        const { error } = await admin.from("profiles").update({
          role: body.role,
          updated_by: user.id,
        }).eq("id", staffId)
        if (error) throw error

        const { error: authUpdateError } = await admin.auth.admin.updateUserById(staffId, {
          user_metadata: { role: body.role },
        })
        if (authUpdateError) throw authUpdateError
        return json({ success: true })
      }

      case "SET_STATUS": {
        const isActive = Boolean(body.isActive)
        const { error } = await admin.from("profiles").update({
          is_active: isActive,
          updated_by: user.id,
        }).eq("id", staffId)
        if (error) throw error

        return json({ success: true })
      }

      case "SOFT_DELETE": {
        const { error } = await admin.from("profiles").update({
          is_active: false,
          is_deleted: true,
          deleted_at: new Date().toISOString(),
          deleted_by: user.id,
          updated_by: user.id,
        }).eq("id", staffId)
        if (error) throw error

        await admin.from("user_devices")
          .update({ is_active: false })
          .eq("user_id", staffId)

        return json({ success: true })
      }

      case "RESET_PASSWORD_EMAIL": {
        const { error } = await admin.auth.resetPasswordForEmail(target.email)
        if (error) throw error
        return json({ success: true })
      }

      default:
        throw new Error(`Unsupported action: ${action}`)
    }
  } catch (error) {
    console.error("manage-staff error:", error)
    const message = error instanceof Error ? error.message : "Unexpected error"
    return json({ error: message }, 400)
  }
})

function validateRole(role: string) {
  const allowed = ["admin", "doctor", "receptionist", "nurse", "inventory_manager"]
  if (!allowed.includes(role)) throw new Error("Invalid staff role")
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  })
}
