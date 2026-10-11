#!/usr/bin/env python3
"""Laoch832: verify the independent offline APK's identity and packaged OCR assets."""
import argparse
import hashlib
import json
import io
import struct
from pathlib import Path
from zipfile import ZipFile
from androguard.core.apk import APK
from androguard.core.dex import DEX
from elftools.elf.elffile import ELFFile
from loguru import logger

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
logger.disable('androguard')
apk = APK(str(args.apk))
assert apk.get_package() == 'app.hyperscan.android'
assert apk.get_app_name() == 'HyperScan'
assert apk.get_androidversion_code() == '5'
permissions = set(apk.get_permissions())
assert 'android.permission.CAMERA' in permissions
assert not permissions & {'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE',
                          'android.permission.READ_EXTERNAL_STORAGE', 'android.permission.WRITE_EXTERNAL_STORAGE'}
manifest = apk.get_android_manifest_xml()
android = '{http://schemas.android.com/apk/res/android}'
application = manifest.find('application')
assert application.get(android + 'allowBackup') == 'false'
assert application.get(android + 'debuggable', 'false') == 'false'
camera_activity = next(a for a in application.findall('activity') if a.get(android + 'name', '').endswith('.ScanCameraActivity'))
assert camera_activity.get(android + 'exported') == 'false'
with ZipFile(args.apk) as archive:
    names = set(archive.namelist())
    root = 'assets/public/ocr/'
    required = {root + 'worker.min.js', root + 'lang/eng.traineddata', root + 'lang/chi_sim.traineddata'}
    assert required <= names
    wasm = sorted(n for n in names if n.startswith(root + 'core/') and n.endswith('.wasm'))
    assert wasm
    assert not any('libsgmain' in n or 'yw_1222' in n for n in names)
    native_manifest = json.loads((Path(__file__).resolve().parents[1] / 'community/core/native-manifest.json').read_text())
    for library, sha256 in native_manifest['libraries'].items():
        assert hashlib.sha256(archive.read('lib/arm64-v8a/' + library)).hexdigest() == sha256
    registration = json.loads((Path(__file__).resolve().parents[1] / 'community/core/jni-registration.json').read_text())
    # All FOUR registrations called by this pinned libquarkit JNI_OnLoad, including
    # MediaScanner's early registration even though HyperScan never runs its graphs.
    pinned = ELFFile(io.BytesIO(archive.read('lib/arm64-v8a/libquarkit.so')))
    relocations = {r['r_offset']: r['r_addend'] for section in pinned.iter_sections()
                   if section['sh_type'] == 'SHT_RELA' for r in section.iter_relocations() if r['r_info_type'] == 1027}
    def read_virtual(address, size):
        for segment in pinned.iter_segments():
            if segment['p_type'] == 'PT_LOAD' and segment['p_vaddr'] <= address < segment['p_vaddr'] + segment['p_filesz']:
                offset = address - segment['p_vaddr']
                return segment.data()[offset:offset + size]
        raise AssertionError('Invalid native table address')
    def native_string(address):
        return read_virtual(address, 256).split(b'\0')[0].decode()
    binary_registration = {}
    for class_address, table_address, count in [(0x1adb03, 0x217cc8, 7), (0x1a3b8f, 0x212d28, 32), (0x1a4726, 0x213178, 32), (0x1a47fc, 0x2134e8, 2)]:
        methods = []
        for i in range(count):
            address = table_address + i * 24
            pointers = [relocations.get(address + j, struct.unpack('<Q', read_virtual(address + j, 8))[0]) for j in (0, 8)]
            methods.append({'name': native_string(pointers[0]), 'descriptor': native_string(pointers[1])})
        binary_registration[native_string(class_address)] = methods
    assert binary_registration == registration, 'Original startup registration inventory is incomplete'
    declared = {}
    for dex_name in names:
        if dex_name.startswith('classes') and dex_name.endswith('.dex'):
            for klass in DEX(archive.read(dex_name)).get_classes():
                declared[klass.get_name()] = {(m.get_name(), m.get_descriptor().replace(' ', '')): m.get_access_flags() for m in klass.get_methods()}
    for klass, methods in registration.items():
        actual = declared['L' + klass + ';']
        for method in methods:
            assert actual[(method['name'], method['descriptor'])] & 0x100, (klass, method)
    alinn_methods = json.loads((Path(__file__).resolve().parents[1] / 'community/core/alinn-jni.json').read_text())
    alinn_class = 'com/taobao/android/alinnkit/alinn/AliNNNetNative'
    alinn_symbols = ELFFile(io.BytesIO(archive.read('lib/arm64-v8a/libmnnkitcore.so'))).get_section_by_name('.dynsym')
    symbol_names = {symbol.name for symbol in alinn_symbols.iter_symbols()}
    actual = declared['L' + alinn_class + ';']
    for method in alinn_methods:
        assert actual[(method['name'], method['descriptor'])] & 0x100
        assert 'Java_' + alinn_class.replace('/', '_') + '_' + method['name'] in symbol_names
    document_models = {}
    for model, digest in native_manifest['document_models'].items():
        asset = 'assets/scanner-models/' + model
        assert hashlib.sha256(archive.read(asset)).hexdigest() == digest
        document_models[model] = digest
    assert 'Lapp/hyperscan/android/OriginalCorePlugin;' in declared
    supplied = set(native_manifest['libraries'])
    platform = {'libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libandroid.so', 'libjnigraphics.so', 'libEGL.so', 'libGLESv2.so'}
    native_dependencies = {}
    for library in supplied:
        elf = ELFFile(io.BytesIO(archive.read('lib/arm64-v8a/' + library)))
        assert elf.header['e_machine'] == 'EM_AARCH64'
        dependencies = [tag.needed for tag in elf.get_section_by_name('.dynamic').iter_tags() if tag.entry.d_tag == 'DT_NEEDED']
        assert set(dependencies) <= supplied | platform, (library, dependencies)
        native_dependencies[library] = dependencies
    csp = archive.read('assets/public/index.html').decode()
    assert 'Content-Security-Policy' in csp and "connect-src 'self' blob:" in csp
    for n in names:
        if n.startswith('assets/public/assets/') and n.endswith('.js'):
            data = archive.read(n)
            assert not any(host in data for host in (b'scan.quark.cn', b'openacs4uc.m.taobao.com'))
    models = {n: hashlib.sha256(archive.read(n)).hexdigest() for n in sorted(required)}
report = {
    'author': 'Laoch832', 'edition': 'original-core-migration-preview', 'package': apk.get_package(),
    'version': apk.get_androidversion_name(), 'version_code': apk.get_androidversion_code(),
    'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(), 'size_bytes': args.apk.stat().st_size,
    'network_permission': False, 'automatic_backup': False, 'debuggable': False,
    'permissions': sorted(permissions), 'original_document_models_sha256': document_models, 'alinn_jni_count': len(alinn_methods), 'packaged_ocr_files_sha256': models,
    'packaged_wasm_variants': wasm, 'original_security_sdk_present': False,
    'structural_checks_passed': True, 'android_device_tested': False,
    'native_core_sha256': native_manifest['libraries'],
    'native_dependencies': native_dependencies, 'jni_registration_methods_verified': sum(map(len, registration.values())),
    'migrated_paths': ['QuarKit imagePerspective', 'PicturesPDFExportAndroidImp local drawing path'],
    'not_migrated': ['automatic edge models', 'original OCR models', 'original intelligent enhancement models'],
    'browser_tests_cover': ['import', 'crop', 'rotate', 'enhance', 'local persistence', 'reorder',
                            'two-page image PDF', 'real OCR with external requests blocked', 'TXT', 'mobile dark mode'],
}
args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(report, ensure_ascii=False, indent=2))
