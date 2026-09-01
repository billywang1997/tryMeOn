#!/usr/bin/env bash
# Pushes the relay's secrets from functions/.secret.local into Secret Manager.
#
# The function binds seventeen secrets, and `firebase deploy` fails if any one
# of them does not exist — so this is the step before the first deploy, not an
# afterthought. Thirteen already have values locally; the four AliExpress ones
# have to be added to that file first.
#
# Values are piped straight from the file into the CLI: nothing is echoed, and
# nothing lands in shell history. Run with --check to see what is set and what
# is missing without sending anything.
set -euo pipefail

PROJECT="${FIREBASE_PROJECT:-mycloset-ce07e}"
ENV_FILE="functions/.secret.local"
CHECK_ONLY=false
[ "${1:-}" = "--check" ] && CHECK_ONLY=true

[ -f "$ENV_FILE" ] || { echo "no $ENV_FILE"; exit 1; }

# The names the function actually binds, read from its own source so this
# cannot drift from what a deploy will demand.
NAMES=$(grep -oE 'defineSecret\("[A-Z_]+"\)' functions/src/targets.ts \
        | sed 's/defineSecret("//; s/")//')

missing=0
for name in $NAMES; do
  # Take the last assignment, strip surrounding quotes, keep the value in a
  # variable that is never printed.
  # `|| true`: a name with no line at all is the normal case for a secret that
  # has not been filled in yet, and `set -e` would otherwise end the run on the
  # first one instead of listing them.
  value="$(grep "^${name}=" "$ENV_FILE" 2>/dev/null | tail -1 | cut -d= -f2- | sed 's/^"//; s/"$//' || true)"
  if [ -z "$value" ]; then
    echo "  MISSING  $name"
    missing=$((missing + 1))
    continue
  fi
  # A value short enough to be a placeholder is worth saying out loud: this
  # file shipped full of them, and pushing those would deploy a relay that
  # authenticates with the word "your_key".
  hint=""
  case "$value" in
    *[!a-z0-9_-]*) ;;
    *) [ "${#value}" -lt 24 ] && hint="  ← looks like a placeholder" ;;
  esac
  if $CHECK_ONLY; then
    echo "  ready    $name (${#value} chars)$hint"
  else
    printf '%s' "$value" | firebase functions:secrets:set "$name" \
      --project "$PROJECT" --data-file - >/dev/null
    echo "  set      $name$hint"
  fi
done

if [ "$missing" -gt 0 ]; then
  echo
  echo "$missing missing. Add them to $ENV_FILE first — a deploy binds every one"
  echo "of these and fails on the first that does not exist."
  exit 1
fi
