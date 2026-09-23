#!/bin/sh
set -eu

# Newly mounted persistent disks can be owned by root. Prepare only the
# configured storage directory, then run Java as the unprivileged app user.
if [ "$(id -u)" = "0" ]; then
  if [ -z "${S3_BUCKET:-}" ]; then
    storage_path="${STORAGE_PATH:-/app/uploads}"
    mkdir -p "$storage_path"
    chown applypilot:applypilot "$storage_path"
  fi
  exec su-exec applypilot "$@"
fi

exec "$@"
