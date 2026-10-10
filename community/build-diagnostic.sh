#!/usr/bin/env bash
set -euo pipefail
project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
original=${1:?Specify original APK}
base=${2:?Specify the audited R2 preview}
output=${3:?Specify new diagnostic output path}
decoded=${4:-/workspace/artifacts/quark-decoded}
output=$(realpath -m "$output")
test ! -e "$output" || { echo 'Choose a new output path.' >&2; exit 1; }
work=$(mktemp -d /workspace/artifacts/hyperscan-diagnostic.XXXXXX)
echo "Build directory: $work"
python3 "$project_dir/community/diagnostic.py" prepare "$decoded" "$work/smali_classes9"
java -Xmx4g --class-path /workspace/tools/apktool_3.0.3.jar "$project_dir/community/AssembleDex.java" "$work/smali_classes9" "$work/classes9.dex"
platform=/workspace/tools/android-platform-36/android-36/android.jar
test -f "$platform"
java "$project_dir/community/CompileJava.java" "$platform" "$work/classes" "$project_dir"/community/diagnostics/*.java
python3 - "$work" <<'PY'
import sys,zipfile
from pathlib import Path
r=Path(sys.argv[1]);c=r/'classes'
with zipfile.ZipFile(r/'diagnostics.jar','x') as z:
    for p in c.rglob('*.class'):z.write(p,p.relative_to(c))
(r/'dex').mkdir()
PY
tools=/workspace/tools/android-build-tools/android-16
"$tools/d8" --min-api 23 --lib "$platform" --output "$work/dex" "$work/diagnostics.jar"
cp "$work/dex/classes.dex" "$work/classes13.dex"
python3 "$project_dir/community/diagnostic.py" pack "$base" "$work" "$work/unsigned.apk"
"$tools/zipalign" -P 16 -f 4 "$work/unsigned.apk" "$work/aligned.apk"
signing=/workspace/.local/share/paperscan-community/signing
"$tools/apksigner" sign --ks "$signing/development.p12" --ks-key-alias community-development --ks-pass "file:$signing/password" --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out "$output" "$work/aligned.apk"
"$tools/apksigner" verify --verbose --print-certs "$output" > "$work/signature-verification.txt" 2>&1
"$tools/zipalign" -c -P 16 4 "$output"
python3 "$project_dir/community/diagnostic.py" audit "$original" "$base" "$output" "$output.validation.json"
sha256sum "$output"
