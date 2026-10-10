#!/usr/bin/env python3
"""Read-only APK inventory. Keep the input package and reports outside source control."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile
from loguru import logger
from androguard.core.apk import APK

def inspect(path: Path) -> dict:
    logger.disable('androguard')
    with ZipFile(path) as archive:
        names = archive.namelist()
        dex_files = [name for name in names if name.endswith('.dex')]
        native_libraries = [name for name in names if name.startswith('lib/') and name.endswith('.so')]
    apk = APK(str(path))
    if not apk.is_valid_APK():
        raise ValueError('Not a valid APK: AndroidManifest.xml could not be parsed.')
    return {
        'file': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
        'package': apk.get_package(), 'version_name': apk.get_androidversion_name(),
        'version_code': apk.get_androidversion_code(), 'minimum_sdk': apk.get_min_sdk_version(),
        'target_sdk': apk.get_target_sdk_version(), 'main_activity': apk.get_main_activity(),
        'permissions': apk.get_permissions(), 'activities': apk.get_activities(),
        'services': apk.get_services(), 'dex_files': dex_files, 'native_libraries': native_libraries,
        'signed_v1': apk.is_signed_v1(), 'signed_v2': apk.is_signed_v2(), 'signed_v3': apk.is_signed_v3(),
    }

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = json.dumps(inspect(args.apk), ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result + '\n')
    else:
        print(result)
