#!/bin/bash
# Cloud Run entrypoint: materialize the Oracle ADB wallet from a mounted zip,
# then exec the service. Wallet zip itself is NOT baked into the image.
set -euo pipefail

TNS_ADMIN="${TNS_ADMIN:-/app/wallet}"
WALLET_ZIP="${WALLET_ZIP:-/mnt/wallet/Wallet_mytestdb.zip}"

mkdir -p "$TNS_ADMIN"

# Candidates checked when $WALLET_ZIP does not exist (secret .b64 mount first,
# then plain zip; GCS FUSE mount paths as fallback).
CANDIDATES=(
  "$WALLET_ZIP"
  "${WALLET_ZIP}.b64"
  "/mnt/wallet/Wallet_mytestdb.zip"
  "/mnt/wallet/Wallet_mytestdb.zip.b64"
  "/mnt/wallets/Wallet_mytestdb.zip"
  "/mnt/wallets/Wallet_mytestdb.zip.b64"
  "/secrets/Wallet_mytestdb.zip"
  "/secrets/Wallet_mytestdb.zip.b64"
  "/app/Wallet_mytestdb.zip"
  "./Wallet_mytestdb.zip"
)

ZIP_FOUND=""
for c in "${CANDIDATES[@]}"; do
  if [ -f "$c" ]; then ZIP_FOUND="$c"; break; fi
done
# De-duplicate: if WALLET_ZIP was already checked, loop above covers it.

if [ -f "$TNS_ADMIN/tnsnames.ora" ] && [ -f "$TNS_ADMIN/cwallet.sso" ]; then
  echo "[entrypoint] wallet already unpacked in $TNS_ADMIN, skipping unzip."
elif [ -n "$ZIP_FOUND" ]; then
  case "$ZIP_FOUND" in
    *.b64)
      # Secret Manager volume mount: file content is base64-encoded zip.
      # /mnt/wallet is usually read-only, so decode into writable TNS_ADMIN.
      DECODED_ZIP="$TNS_ADMIN/.wallet-decoded.zip"
      echo "[entrypoint] base64-decoding $ZIP_FOUND -> $DECODED_ZIP"
      # stdin redirect works on both GNU (container) and BSD (macOS) base64.
      if ! base64 -d < "$ZIP_FOUND" > "$DECODED_ZIP" 2>/dev/null; then
        echo "[entrypoint] ERROR: failed to base64-decode $ZIP_FOUND" >&2
        exit 1
      fi
      if ! unzip -t -q "$DECODED_ZIP" >/dev/null 2>&1; then
        echo "[entrypoint] ERROR: decoded file from $ZIP_FOUND is not a valid zip" >&2
        exit 1
      fi
      echo "[entrypoint] unzipping $DECODED_ZIP -> $TNS_ADMIN"
      unzip -o -q "$DECODED_ZIP" -d "$TNS_ADMIN"
      rm -f "$DECODED_ZIP"
      ;;
    *)
      echo "[entrypoint] unzipping $ZIP_FOUND -> $TNS_ADMIN"
      unzip -o -q "$ZIP_FOUND" -d "$TNS_ADMIN"
      ;;
  esac
else
  echo "[entrypoint] WARNING: no wallet zip found. Checked:" >&2
  printf '  - %s\n' "${CANDIDATES[@]}" >&2
  echo "[entrypoint] WARNING: continuing without wallet; DB connections will fail until a zip is mounted at \$WALLET_ZIP." >&2
fi

# Patch sqlnet.ora placeholder "?/network/admin" (ships in ADB downloads) to the real dir.
# -i.bak form works on both GNU sed (container) and BSD sed (macOS).
if [ -f "$TNS_ADMIN/sqlnet.ora" ]; then
  sed -i.bak "s|?/network/admin|$TNS_ADMIN|g" "$TNS_ADMIN/sqlnet.ora" || true
  rm -f "$TNS_ADMIN/sqlnet.ora.bak" || true
fi

# Materialize ${TNS_ADMIN} placeholder in ojdbc.properties if the driver does not expand it.
if [ -f "$TNS_ADMIN/ojdbc.properties" ]; then
  sed -i.bak "s|\${TNS_ADMIN}|$TNS_ADMIN|g" "$TNS_ADMIN/ojdbc.properties" || true
  rm -f "$TNS_ADMIN/ojdbc.properties.bak" || true
fi

# Tighten perms on secrets; ignore failures on read-only mounts.
chmod 600 "$TNS_ADMIN"/cwallet.sso "$TNS_ADMIN"/ewallet.p12 2>/dev/null || true

echo "[entrypoint] TNS_ADMIN=$TNS_ADMIN ORACLE_TNS_ALIAS=${ORACLE_TNS_ALIAS:-<unset>} PORT=${PORT:-8080}"
echo "[entrypoint] wallet files:"
ls -1 "$TNS_ADMIN" 2>/dev/null || echo "  (empty/missing)"

exec "$@"
