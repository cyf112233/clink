#!/bin/sh
set -e
HER_RUNTIME="${HER_RUNTIME:-/her}"
export GRADLE_HOME="$HER_RUNTIME/toolchains/gradle"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HER_RUNTIME/.gradle}"
chmod +x "$GRADLE_HOME/bin/gradle" 2>/dev/null || true
exec "$GRADLE_HOME/bin/gradle" "$@"
