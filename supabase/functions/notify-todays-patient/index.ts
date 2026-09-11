// Called only by the notify_todays_patient() trigger (see
// supabase/migrations/20260911_todays_patient_notifications.sql) right after a
// receptionist's new consultation_todos/vaccination_todos row is committed to Supabase.
//
// This function is an alert dispatcher only - it never writes application data. The
// doctor's Room database always gets the real row through the existing sync_queue pull
// (PatientTodoRepository.refresh()) / Supabase Realtime path, never through this
// notification payload.
//
// Shares its FCM v1 signing approach (getAccessToken/str2ab) with notify-update, and its
// shared-secret trigger auth pattern (Authorization: Bearer <SECRET>) with the same
// function, for consistency with how this project already gates trigger-invoked
// functions.
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { create, getNumericDate } from "https://deno.land/x/djwt@v2.8/mod.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

type TodoType = "consultation" | "vaccination"

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const authHeader = req.headers.get('Authorization')
    const secret = Deno.env.get('TODO_NOTIFIER_SECRET')

    if (!secret || !authHeader || authHeader !== `Bearer ${secret}`) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const payload = await req.json()
    const todoType = payload.todo_type as TodoType
    const todoId = payload.todo_id as string | undefined
    const doctorIdFromPayload = payload.doctor_id as string | null | undefined

    if (todoType !== 'consultation' && todoType !== 'vaccination') {
      return new Response(JSON.stringify({ error: 'todo_type must be consultation or vaccination' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }
    if (!todoId) {
      return new Response(JSON.stringify({ error: 'todo_id is required' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    // Defense in depth for "the doctor must never be notified for a patient that does
    // not exist in Supabase" - re-read the authoritative row rather than trusting the
    // trigger payload alone (it also covers the row being deleted in the brief window
    // before this async call runs).
    const table = todoType === 'consultation' ? 'consultation_todos' : 'vaccination_todos'
    const { data: todo, error: todoError } = await supabase
      .from(table)
      .select('id, name, todo_date, patient_id, doctor_id')
      .eq('id', todoId)
      .maybeSingle()

    if (todoError || !todo) {
      return new Response(JSON.stringify({ message: 'Todo not found (possibly deleted); no notification sent' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    // Doctor targeting (req. 16): Today's Patient rows now carry an assigned doctor_id,
    // set at creation time by the receptionist's doctor+slot picker. When present, notify
    // only that doctor's device(s) - never every doctor. Fall back to the old
    // broadcast-to-all-active-doctors behavior only for a row with no doctor assigned
    // (e.g. a pre-migration queue entry, or a todo added before the doctor picker was
    // filled in), so existing behavior for those rows is preserved rather than silently
    // dropping the notification.
    const assignedDoctorId = todo.doctor_id ?? doctorIdFromPayload ?? null

    let doctorIds: string[]
    if (assignedDoctorId) {
      const { data: assignedDoctor, error: assignedDoctorError } = await supabase
        .from('profiles')
        .select('id')
        .eq('id', assignedDoctorId)
        .eq('role', 'doctor')
        .eq('is_active', true)
        .or('is_deleted.is.null,is_deleted.eq.false')
        .maybeSingle()

      if (assignedDoctorError) throw assignedDoctorError

      // Assigned doctor exists and is active - target them only, and nobody else.
      doctorIds = assignedDoctor ? [assignedDoctor.id] : []
    } else {
      const { data: doctors, error: doctorsError } = await supabase
        .from('profiles')
        .select('id')
        .eq('role', 'doctor')
        .eq('is_active', true)
        .or('is_deleted.is.null,is_deleted.eq.false')

      if (doctorsError) throw doctorsError
      doctorIds = (doctors ?? []).map((d) => d.id)
    }

    if (doctorIds.length === 0) {
      return new Response(JSON.stringify({ message: 'No active doctor accounts found for this notification' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const { data: devices, error: devicesError } = await supabase
      .from('user_devices')
      .select('id, fcm_token')
      .in('user_id', doctorIds)
      .eq('is_active', true)

    if (devicesError) throw devicesError

    const deviceRows = (devices ?? []).filter((d) => !!d.fcm_token)
    if (deviceRows.length === 0) {
      return new Response(JSON.stringify({ message: 'No active doctor devices found' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const serviceAccount = JSON.parse(Deno.env.get('FIREBASE_SERVICE_ACCOUNT') || '{}')
    if (!serviceAccount.project_id) {
      throw new Error('FIREBASE_SERVICE_ACCOUNT not configured')
    }

    const accessToken = await getAccessToken(serviceAccount)
    const fcmUrl = `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`

    const isConsultation = todoType === 'consultation'
    const title = isConsultation ? 'New Consultation Patient' : 'New Vaccination Patient'
    const listLabel = isConsultation ? 'consultation' : 'vaccination'
    const body = `${todo.name} has been added to today's ${listLabel} list.`

    // Kept intentionally small (req. 5): notification type, todo id, patient id, name,
    // date - no vaccine names, address, phone, or other clinical detail.
    const dataPayload = {
      type: isConsultation ? 'today_patient_consultation' : 'today_patient_vaccination',
      todo_id: String(todo.id),
      patient_id: todo.patient_id ? String(todo.patient_id) : '',
      patient_name: todo.name,
      todo_date: todo.todo_date,
    }

    const results = { total: deviceRows.length, sent: 0, failed: 0 }

    for (const device of deviceRows) {
      const message = {
        message: {
          token: device.fcm_token,
          notification: { title, body },
          data: dataPayload,
          android: { priority: 'high' },
        },
      }

      const res = await fetch(fcmUrl, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(message),
      })

      if (res.ok) {
        results.sent++
      } else {
        results.failed++
        const errText = await res.text()
        console.error(`FCM error for device ${device.id}:`, errText)
        // Stale/uninstalled-app tokens: deactivate so future notifications (and the
        // existing DeviceRepository heartbeat) stop targeting a dead token. Best-effort -
        // a failure here must never fail the overall notification pass.
        if (errText.includes('UNREGISTERED') || errText.includes('NOT_FOUND') || errText.includes('INVALID_ARGUMENT')) {
          await supabase.from('user_devices').update({ is_active: false }).eq('id', device.id)
        }
      }
    }

    return new Response(JSON.stringify({ success: true, results }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })

  } catch (error) {
    console.error("Function error:", error)
    return new Response(JSON.stringify({ error: error.message }), {
      status: 400,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })
  }
})

async function getAccessToken(serviceAccount: any): Promise<string> {
  const pem = serviceAccount.private_key
  const key = await crypto.subtle.importKey(
    "pkcs8",
    str2ab(pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\n/g, "")),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  )

  const jwt = await create(
    { alg: "RS256", typ: "JWT" },
    {
      iss: serviceAccount.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      exp: getNumericDate(3600),
      iat: getNumericDate(0),
    },
    key
  )

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  })

  const { access_token } = await res.json()
  return access_token
}

function str2ab(str: string) {
  const binaryString = atob(str)
  const len = binaryString.length
  const bytes = new Uint8Array(len)
  for (let i = 0; i < len; i++) {
    bytes[i] = binaryString.charCodeAt(i)
  }
  return bytes.buffer
}
