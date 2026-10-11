#!/usr/bin/env bash
set -euo pipefail
project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
output=${1:?Specify a new APK output path}
output=$(realpath -m "$output")
test ! -e "$output" || { echo 'Choose a new output path.' >&2; exit 1; }
python3 "$project_dir/scripts/setup-offline-android.py"
python3 "$project_dir/scripts/prepare-original-core.py"
tools_root=$(python3 - <<'PY'
import os
from pathlib import Path
print(os.environ.get('HYPERSCAN_TOOLS_ROOT', '/workspace/tools' if Path('/workspace').exists() else str(Path.home()/'.cache/hyperscan/tools')))
PY
)
mapfile -t build_paths < <(python3 - "$tools_root/hyperscan-offline-environment.json" <<'PY'
import json,sys
c=json.load(open(sys.argv[1]))
for name in ['java_home','gradle','gradle_user_home','sdk','build_tools']:print(c[name])
PY
)
export JAVA_HOME=${build_paths[0]}
printf 'sdk.dir=%s\n' "${build_paths[3]}" > "$project_dir/android/local.properties"
cd "$project_dir"
npm run android:sync
cd android
"${build_paths[1]}" --gradle-user-home "${build_paths[2]}" assembleRelease
signing=${HYPERSCAN_SIGNING_DIR:-$tools_root/../.local/share/paperscan-community/signing}
umask 077
mkdir -p "$signing" "$(dirname -- "$output")"
if ! test -f "$signing/development.p12"; then
  test ! -f "$signing/password" || { echo 'Existing signing password without key; repair the signing directory.' >&2; exit 1; }
  python3 - "$signing/password" <<'PY'
from pathlib import Path
import secrets,sys
Path(sys.argv[1]).write_text(secrets.token_urlsafe(36)+'\n')
PY
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$signing/development.p12" -storetype PKCS12 -alias community-development -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Laoch832, O=HyperScan' -storepass:file "$signing/password" -keypass:file "$signing/password"
fi
work=$(mktemp -d "${TMPDIR:-/tmp}/hyperscan-offline.XXXXXX")
"${build_paths[4]}/zipalign" -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk "$work/aligned.apk"
"${build_paths[4]}/apksigner" sign --ks "$signing/development.p12" --ks-key-alias community-development --ks-pass "file:$signing/password" --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out "$output" "$work/aligned.apk"
"${build_paths[4]}/apksigner" verify --verbose "$output" > "$work/signature-check.txt" 2>&1
"${build_paths[4]}/zipalign" -c -P 16 4 "$output"
python3 "$project_dir/scripts/verify-offline.py" "$output" --output "$output.validation.json"
sha256sum "$output"
