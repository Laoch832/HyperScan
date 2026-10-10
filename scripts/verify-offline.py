#!/usr/bin/env python3
"""Laoch832: verify the independent offline APK's identity and packaged OCR assets."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile
from androguard.core.apk import APK
from loguru import logger

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
logger.disable('androguard')
apk = APK(str(args.apk))
assert apk.get_package() == 'app.hyperscan.android'
assert apk.get_app_name() == 'HyperScan'
assert apk.get_androidversion_code() == '2'
permissions = set(apk.get_permissions())
assert not permissions & {'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE',
                          'android.permission.READ_EXTERNAL_STORAGE', 'android.permission.WRITE_EXTERNAL_STORAGE'}
manifest = apk.get_android_manifest_xml()
android = '{http://schemas.android.com/apk/res/android}'
application = manifest.find('application')
assert application.get(android + 'allowBackup') == 'false'
assert application.get(android + 'debuggable', 'false') == 'false'
with ZipFile(args.apk) as archive:
    names = set(archive.namelist())
    root = 'assets/public/ocr/'
    required = {root + 'worker.min.js', root + 'lang/eng.traineddata', root + 'lang/chi_sim.traineddata'}
    assert required <= names
    wasm = sorted(n for n in names if n.startswith(root + 'core/') and n.endswith('.wasm'))
    assert wasm
    assert not any('libsgmain' in n or 'yw_1222' in n for n in names)
    csp = archive.read('assets/public/index.html').decode()
    assert 'Content-Security-Policy' in csp and "connect-src 'self' blob:" in csp
    for n in names:
        if n.startswith('assets/public/assets/') and n.endswith('.js'):
            data = archive.read(n)
            assert not any(host in data for host in (b'scan.quark.cn', b'openacs4uc.m.taobao.com'))
    models = {n: hashlib.sha256(archive.read(n)).hexdigest() for n in sorted(required)}
report = {
    'author': 'Laoch832', 'edition': 'independent-offline', 'package': apk.get_package(),
    'version': apk.get_androidversion_name(), 'version_code': apk.get_androidversion_code(),
    'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(), 'size_bytes': args.apk.stat().st_size,
    'network_permission': False, 'automatic_backup': False, 'debuggable': False,
    'permissions': sorted(permissions), 'packaged_ocr_files_sha256': models,
    'packaged_wasm_variants': wasm, 'original_security_sdk_present': False,
    'structural_checks_passed': True, 'android_device_tested': False,
    'browser_tests_cover': ['import', 'crop', 'rotate', 'enhance', 'local persistence', 'reorder',
                            'two-page image PDF', 'real OCR with external requests blocked', 'TXT', 'mobile dark mode'],
}
args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(report, ensure_ascii=False, indent=2))
