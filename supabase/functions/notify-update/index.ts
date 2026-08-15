import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { create, getNumericDate } from "https://deno.land/x/djwt@v2.8/mod.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const authHeader = req.headers.get('Authorization')
    const secret = Deno.env.get('UPDATE_NOTIFIER_SECRET')

    if (!authHeader || authHeader !== `Bearer ${secret}`) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const payload = await req.json()
    const { version_name, mandatory } = payload

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    // Fetch all active FCM tokens
    const { data: devices, error: dbError } = await supabase
      .from('user_devices')
      .select('fcm_token')
      .eq('is_active', true)

    if (dbError) throw dbError

    const tokens = devices?.map((d) => d.fcm_token).filter(Boolean) || []
    if (tokens.length === 0) {
      return new Response(JSON.stringify({ message: 'No active devices found' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const serviceAccount = JSON.parse(Deno.env.get('FIREBASE_SERVICE_ACCOUNT') || '{}')
    if (!serviceAccount.project_id) {
      throw new Error('FIREBASE_SERVICE_ACCOUNT not configured')
    }

    const accessToken = await getAccessToken(serviceAccount)
    const fcmUrl = `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`

    const title = mandatory ? "Vaccine Manager update required" : "Vaccine Manager update available"
    const bodyText = mandatory
      ? `Version ${version_name} is required. Tap to update.`
      : `Version ${version_name} is now available. Tap to update.`

    const results = { total: tokens.length, sent: 0, failed: 0 }

    for (const token of tokens) {
      const message = {
        message: {
          token,
          notification: { title, body: bodyText },
          data: {
            type: "app_update",
            version_name: version_name,
            mandatory: String(mandatory)
          }
        }
      }

      const res = await fetch(fcmUrl, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(message),
      })

      if (res.ok) results.sent++
      else {
        results.failed++
        console.error(`FCM error for token ${token.substring(0, 10)}...:`, await res.text())
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
