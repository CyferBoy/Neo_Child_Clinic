#!/usr/bin/env bash
# Fail closed: a signed release needs a real keystore. Reads only env vars populated from
# repository secrets by the workflow; no credential value lives in this script or the repo.
set -euo pipefail

for name in KEYSTORE_BASE64 KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
  if [ -z "${!name:-}" ]; then
    echo "::error::$name is not configured; refusing to build an unsigned release."
    exit 1
  fi
done

keystore="$RUNNER_TEMP/neochildclinic-release.jks"
printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$keystore"
chmod 600 "$keystore"

# JKS starts with FEEDFEED, PKCS12 with 3082. Catch a garbled secret here, not at install.
magic="$(od -An -tx1 -N4 "$keystore" | tr -d ' \n')"
case "$magic" in
  feedfeed|3082*) ;;
  *) echo "::error::Decoded keystore is neither JKS nor PKCS12 (magic: $magic)."; exit 1 ;;
esac

{
  echo "KEYSTORE_FILE=$keystore"
  echo "KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD"
  echo "KEY_ALIAS=$KEY_ALIAS"
  echo "KEY_PASSWORD=$KEY_PASSWORD"
} >> "$GITHUB_ENV"
