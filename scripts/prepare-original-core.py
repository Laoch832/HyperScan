#!/usr/bin/env python3
"""Bundle the original model-free QuarKit runtime, never download it at app runtime."""
import argparse
import hashlib
import json
import os
import tempfile
import urllib.request
from pathlib import Path
from zipfile import ZipFile
import py7zr

ROOT = Path(__file__).resolve().parents[1]
APK_SHA = '3e6a468befdd9c0808c319f25a2e5d1fc701b38fc5bd4870f566a5d4f826596e'
ARCHIVE_SHA = '2bc274afe459e118bac113a9f8377397d68c042ff81af97e930dff3f1ad25fa0'
URL = 'https://pdds.quark.cn/download/stfile/kkprnnkoklokmmrp/quarkit-arm64-v8a-20220424104922-7z.zip'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk', type=Path, default=Path('/workspace/artifacts/quark-original.apk'))
parser.add_argument('--cache', type=Path, default=Path('/workspace/artifacts/hyperscan-core-analysis'))
args = parser.parse_args()
args.cache.mkdir(parents=True, exist_ok=True)
if not args.apk.exists():
    # The user supplied this public release; GitHub's published digest matches APK_SHA.
    source = 'https://github.com/Laoch832/cloudflare_photoREPO_zrl/releases/download/hhh/QuarkScanking_V10.13.0.455_android_pf30001_.zh-cn._release_.Build260928170955-arm64.apk'
    args.apk.parent.mkdir(parents=True, exist_ok=True)
    handle, temporary = tempfile.mkstemp(prefix='hyperscan-original-', dir=args.apk.parent)
    try:
        digest = hashlib.sha256()
        with os.fdopen(handle, 'wb') as output, urllib.request.urlopen(source, timeout=60) as response:
            count = 0
            while chunk := response.read(1024 * 1024):
                count += len(chunk)
                if count > 512 * 1024 * 1024: raise ValueError('Original APK exceeds size limit')
                digest.update(chunk); output.write(chunk)
        if digest.hexdigest() != APK_SHA: raise ValueError('Downloaded original APK checksum mismatch')
        Path(temporary).replace(args.apk)
    finally:
        Path(temporary).unlink(missing_ok=True)
if hashlib.sha256(args.apk.read_bytes()).hexdigest() != APK_SHA:
    raise SystemExit('Original APK checksum mismatch; refuse to mix unknown native runtimes.')
archive = args.cache / 'quarkit-arm64.zip'
if not archive.exists():
    with urllib.request.urlopen(URL, timeout=60) as response:
        data = response.read(5058475)
    if len(data) != 5058474 or hashlib.sha256(data).hexdigest() != ARCHIVE_SHA:
        raise SystemExit('QuarKit download checksum mismatch.')
    archive.write_bytes(data)
if hashlib.sha256(archive.read_bytes()).hexdigest() != ARCHIVE_SHA:
    raise SystemExit('Cached QuarKit checksum mismatch.')
dest = ROOT / 'android/app/src/main/jniLibs/arm64-v8a'
dest.mkdir(parents=True, exist_ok=True)
expected = {'lib_info.json', 'libtbb.so', 'libopencv_java3.so', 'libupipe_jni.so', 'libquarkit.so'}
with py7zr.SevenZipFile(archive) as packed:
    if set(packed.getnames()) != expected:
        raise SystemExit('Unexpected QuarKit archive entries.')
    packed.extractall(dest)
metadata = json.loads((dest / 'lib_info.json').read_text())
for entry in metadata['libDataList']:
    data = (dest / entry['libName']).read_bytes()
    if len(data) != int(entry['size']) or hashlib.md5(data).hexdigest() != entry['md5']:
        raise SystemExit('Inner library mismatch: ' + entry['libName'])
(dest / 'lib_info.json').unlink()
with ZipFile(args.apk) as apk:
    for name in ['libMNN.so', 'libc++_shared.so']:
        (dest / name).write_bytes(apk.read('lib/arm64-v8a/' + name))
report = {'original_apk_sha256': APK_SHA, 'quarkit_archive_sha256': ARCHIVE_SHA,
          'archive_url': URL, 'original_loader_class': 'j71.f',
          'libraries': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(dest.glob('*.so'))}}
(ROOT / 'community/core/native-manifest.json').write_text(json.dumps(report, indent=2) + '\n')
print('Prepared 6 verified arm64 original computation libraries; no runtime downloader.')
