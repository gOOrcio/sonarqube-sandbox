#!/usr/bin/env bash
set -Eeuo pipefail

SONAR_URL="${SONAR_HOST_URL:-https://sonarqube.mati-lab.online}"
SONAR_TOKEN="${SONAR_TOKEN:?SONAR_TOKEN env var required}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

auth() { echo "-u ${SONAR_TOKEN}:"; }

sonar_get()  { curl -sf $(auth) "${SONAR_URL}${1}"; }
sonar_post() { curl -sf $(auth) -X POST "${SONAR_URL}${1}" "${@:2}"; }

# ── Quality Gates ──────────────────────────────────────────────────────────────

create_gate_if_missing() {
  local name="$1"
  local json_file="$2"

  existing=$(sonar_get "/api/qualitygates/list" | python3 -c "
import sys, json
gates = json.load(sys.stdin).get('qualitygates', [])
match = next((g for g in gates if g['name'] == '${name}'), None)
print(match['id'] if match else '')
" 2>/dev/null || true)

  if [[ -n "$existing" ]]; then
    echo "  Gate '${name}' already exists (id=${existing}), skipping creation"
    gate_id="$existing"
  else
    echo "  Creating gate '${name}'..."
    gate_id=$(sonar_post "/api/qualitygates/create" \
      --data-urlencode "name=${name}" \
      | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
    echo "  Created gate id=${gate_id}"
  fi

  # Apply conditions from JSON (idempotent: delete all then re-add)
  echo "  Applying conditions..."
  existing_conditions=$(sonar_get "/api/qualitygates/show?id=${gate_id}" \
    | python3 -c "import sys,json; [print(c['id']) for c in json.load(sys.stdin).get('conditions',[])]" \
    2>/dev/null || true)
  for cid in $existing_conditions; do
    sonar_post "/api/qualitygates/delete_condition" --data-urlencode "id=${cid}" > /dev/null
  done

  python3 - "${gate_id}" "${json_file}" <<'EOF'
import sys, json, urllib.request, urllib.parse, base64, os

gate_id = sys.argv[1]
with open(sys.argv[2]) as f:
    gate = json.load(f)

token = os.environ['SONAR_TOKEN']
base_url = os.environ.get('SONAR_HOST_URL', 'https://sonarqube.mati-lab.online')
auth = base64.b64encode(f"{token}:".encode()).decode()
headers = {"Authorization": f"Basic {auth}", "Content-Type": "application/x-www-form-urlencoded"}

for cond in gate['conditions']:
    params = urllib.parse.urlencode({
        "gateId": gate_id,
        "metric": cond["metric"],
        "op": cond["op"],
        "error": cond["error"]
    }).encode()
    req = urllib.request.Request(f"{base_url}/api/qualitygates/create_condition", data=params, headers=headers)
    urllib.request.urlopen(req)
    print(f"    Added condition: {cond['metric']} {cond['op']} {cond['error']}")
EOF
}

# ── Quality Profiles ───────────────────────────────────────────────────────────

import_profile_if_missing() {
  local name="$1"
  local language="$2"
  local xml_file="$3"

  existing=$(sonar_get "/api/qualityprofiles/search?language=${language}" \
    | python3 -c "
import sys, json
profiles = json.load(sys.stdin).get('profiles', [])
match = next((p for p in profiles if p['name'] == '${name}'), None)
print('found' if match else '')
" 2>/dev/null || true)

  if [[ -n "$existing" ]]; then
    echo "  Profile '${name}' (${language}) already exists, skipping"
  else
    echo "  Importing profile '${name}' (${language})..."
    sonar_post "/api/qualityprofiles/restore" \
      -F "backup=@${xml_file}" > /dev/null
    echo "  Imported"
  fi
}

# ── Main ───────────────────────────────────────────────────────────────────────

echo "SonarQube provisioning — ${SONAR_URL}"
echo ""

echo "Checking SonarQube is up..."
status=$(sonar_get "/api/system/status" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
if [[ "$status" != "UP" ]]; then
  echo "ERROR: SonarQube status is '${status}', expected UP" >&2
  exit 1
fi
echo "Status: ${status}"
echo ""

echo "=== Quality Gates ==="
create_gate_if_missing "Mati-Lab Default"       "${SCRIPT_DIR}/quality-gates/default-gate.json"
create_gate_if_missing "Mati-Lab Strict (Java)" "${SCRIPT_DIR}/quality-gates/strict-gate.json"
echo ""

echo "=== Quality Profiles ==="
import_profile_if_missing "Mati-Lab Java"       "java" "${SCRIPT_DIR}/quality-profiles/java-profile.xml"
import_profile_if_missing "Mati-Lab Python"     "py"   "${SCRIPT_DIR}/quality-profiles/python-profile.xml"
import_profile_if_missing "Mati-Lab Go"         "go"   "${SCRIPT_DIR}/quality-profiles/go-profile.xml"
import_profile_if_missing "Mati-Lab TypeScript" "ts"   "${SCRIPT_DIR}/quality-profiles/ts-profile.xml"
echo ""

echo "Provisioning complete."
