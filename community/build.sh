#!/usr/bin/env bash
set -euo pipefail
project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
source_apk=${1:?Usage: build.sh SOURCE_APK OUTPUT_APK [DECODED_SOURCE_DIR]}
output_apk=${2:?Specify a new output APK filename}
source_apk=$(realpath "$source_apk")
output_apk=$(realpath -m "$output_apk")
test ! -e "$output_apk" || { echo 'Output exists; choose a new filename to preserve it.' >&2; exit 1; }
python3 - "$source_apk" "$project_dir" <<'PY'
import sys
from pathlib import Path
sys.path.insert(0, str(Path(sys.argv[2]) / 'community'))
from patch import check_source
check_source(Path(sys.argv[1]))
PY
mkdir -p /workspace/artifacts
build_dir=$(mktemp -d /workspace/artifacts/community-build.XXXXXX)
printf 'Build directory: %s\n' "$build_dir"
decoded_dir=${3:-"$build_dir/decoded"}
if test ! -d "$decoded_dir"; then
  java -Xmx4g -jar /workspace/tools/apktool_3.0.3.jar decode --no-res --jobs 3 --output "$decoded_dir" "$source_apk"
fi
python3 "$project_dir/community/patch.py" prepare --source "$source_apk" --decoded "$decoded_dir" --output "$build_dir/smali"
for dex_number in 10 11; do
  java -Xmx4g --class-path /workspace/tools/apktool_3.0.3.jar "$project_dir/community/AssembleDex.java" "$build_dir/smali/smali_classes${dex_number}" "$build_dir/classes${dex_number}.dex"
done
python3 "$project_dir/community/patch.py" repack --source "$source_apk" --dex-directory "$build_dir" --output "$build_dir/unsigned.apk"
build_tools_dir=/workspace/tools/android-build-tools/android-16
"$build_tools_dir/zipalign" -P 16 -f 4 "$build_dir/unsigned.apk" "$build_dir/aligned.apk"

# A retained local development identity; never publish the private key or password.
signing_dir=/workspace/.local/share/paperscan-community/signing
mkdir -p "$signing_dir"
chmod 700 "$signing_dir"
if test ! -f "$signing_dir/password"; then
  test ! -f "$signing_dir/development.p12" || { echo 'Keystore exists but its password file is missing.' >&2; exit 1; }
  python3 - "$signing_dir/password" <<'PY'
import os, secrets, sys
with os.fdopen(os.open(sys.argv[1], os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w') as output:
    output.write(secrets.token_urlsafe(36) + '\n')
PY
fi
if test ! -f "$signing_dir/development.p12"; then
  keytool -genkeypair -noprompt -storetype PKCS12 -alias community-development -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Laoch832 Community Development, O=HyperScan' -keystore "$signing_dir/development.p12" \
    -storepass:file "$signing_dir/password" -keypass:file "$signing_dir/password"
  chmod 600 "$signing_dir/development.p12"
fi
mkdir -p "$(dirname "$output_apk")"
"$build_tools_dir/apksigner" sign --ks "$signing_dir/development.p12" --ks-key-alias community-development \
  --ks-pass "file:$signing_dir/password" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
  --out "$output_apk" "$build_dir/aligned.apk"
"$build_tools_dir/apksigner" verify --verbose --print-certs "$output_apk" > "$build_dir/signature-verification.txt" 2>&1
"$build_tools_dir/zipalign" -c -P 16 4 "$output_apk"
python3 "$project_dir/community/verify.py" "$source_apk" "$output_apk" --output "$output_apk.validation.json"
sha256sum "$output_apk"
printf 'Signed client preview: %s\n' "$output_apk"
