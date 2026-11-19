#!/usr/bin/env bash
# Write the throwaway CI-only google-services.json so the Google Services Gradle
# plugin can configure the debug build. The real file is intentionally uncommitted.
cat > app/google-services.json <<'EOF'
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "neo-child-clinic-ci",
    "storage_bucket": "neo-child-clinic-ci.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000",
        "android_client_info": {
          "package_name": "com.neochildclinic"
        }
      },
      "api_key": [
        {
          "current_key": "CI_BUILD_ONLY"
        }
      ]
    }
  ]
}
EOF
