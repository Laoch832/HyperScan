#!/usr/bin/env bash
set -euo pipefail
cd /workspace/Default_Eniveronment
python3 -m pip install -r requirements-analysis.txt
analysis_tools_dir=/workspace/tools/jadx-1.5.6
analysis_zip=/workspace/tools/jadx-1.5.6.zip
mkdir -p /workspace/tools
# SHA-256 published on skylot/jadx's GitHub v1.5.6 release assets page.
analysis_sha=545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974
if ! test -f "$analysis_zip" || ! printf '%s  %s\n' "$analysis_sha" "$analysis_zip" | sha256sum --check --status; then
  curl --fail --location --retry 2 --max-time 60 https://github.com/skylot/jadx/releases/download/v1.5.6/jadx-1.5.6.zip -o "$analysis_zip"
fi
printf '%s  %s\n' "$analysis_sha" "$analysis_zip" | sha256sum --check
python3 - "$analysis_zip" "$analysis_tools_dir" <<'PY'
import sys
from zipfile import ZipFile
from pathlib import Path
target = Path(sys.argv[2])
target.mkdir(parents=True, exist_ok=True)
with ZipFile(sys.argv[1]) as archive:
    archive.extractall(target)
PY
chmod +x "$analysis_tools_dir/bin/jadx"
"$analysis_tools_dir/bin/jadx" --version
