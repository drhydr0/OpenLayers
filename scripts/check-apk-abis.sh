#!/usr/bin/env bash
# Fails if an APK can't run on 64-bit-only phones (e.g. Pixel 7 and later, Galaxy S26).
# Pure Kotlin/Java needs nothing. Any native library must also ship for arm64-v8a,
# or the app crashes when that code loads on a phone without 32-bit support.
set -euo pipefail

apk="${1:?usage: check-apk-abis.sh <apk>}"
libs=$(unzip -Z1 "$apk" | grep -E '^lib/[^/]+/[^/]+\.so$' || true)

if [ -z "$libs" ]; then
  echo "OK: no native libraries; runs on every CPU type, including 64-bit-only."
  exit 0
fi

missing=0
for name in $(echo "$libs" | sed -E 's|^lib/[^/]+/||' | sort -u); do
  if ! echo "$libs" | grep -qx "lib/arm64-v8a/$name"; then
    echo "MISSING: lib/arm64-v8a/$name (found only for: $(echo "$libs" | grep "/$name\$" | cut -d/ -f2 | tr '\n' ' '))"
    missing=1
  fi
done

if [ "$missing" -ne 0 ]; then
  echo "FAIL: native code without a 64-bit ARM build won't run on 64-bit-only phones."
  exit 1
fi
echo "OK: every native library has an arm64-v8a build."
