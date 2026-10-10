#!/usr/bin/env python3
"""Check package structure, preserved payloads, and the actual assembled DEX diff."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from zipfile import ZipFile
from loguru import logger
from androguard.core.apk import APK
from androguard.core.dex import DEX
from patch import check_source, TARGETS, POLICY_CLASS, target_tree

def methods(dex: DEX) -> dict:
    result = {}
    for klass in dex.get_classes():
        for method in klass.get_methods():
            key = (klass.get_name(), method.get_name(), method.get_descriptor().replace(' ', ''))
            code = method.get_code()
            if code:
                locals_count = code.get_registers_size() - code.get_ins_size()
                def normalize(output: str) -> str:
                    # DEX prints physical registers; smali's pN aliases move when a local is added.
                    return re.sub(r'\bv(\d+)\b', lambda match: f'p{int(match[1]) - locals_count}'
                                  if int(match[1]) >= locals_count else match[0], output)
                result[key] = tuple((instruction.get_name(), normalize(instruction.get_output())) for instruction in code.get_bc().get_instructions())
            else:
                result[key] = None
    return result

def verify(original: Path, preview: Path) -> dict:
    logger.disable('androguard')
    check_source(original)
    report = {'source': original.name, 'preview': preview.name, 'runtime_tested': False}
    with ZipFile(original) as old, ZipFile(preview) as new:
        old_files, new_files = set(old.namelist()), set(new.namelist())
        report['added_entries'] = sorted(new_files - old_files)
        report['removed_entries'] = sorted(old_files - new_files)
        changed = []
        for name in sorted(old_files & new_files):
            if old.read(name) != new.read(name):
                changed.append(name)
        report['changed_entries'] = changed
        changed_dex = {'classes10.dex', 'classes11.dex'}
        assert set(changed) <= changed_dex | {'resources.arsc', 'META-INF/MANIFEST.MF'}, changed
        assert changed_dex | {'resources.arsc'} <= set(changed)
        assert 'assets/community-build.json' in report['added_entries']
        assert all(name.startswith('META-INF/') for name in report['removed_entries'])
        for name in old_files - changed_dex - {'resources.arsc'}:
            if not name.startswith('META-INF/'):
                assert name in new_files and old.read(name) == new.read(name), name
        report['preserved_native_libraries'] = len([name for name in old_files if name.startswith('lib/') and name.endswith('.so')])
        report['preserved_dex_files'] = len([name for name in old_files if name.endswith('.dex') and name not in changed_dex])
        report['changed_methods'] = []
        report['retained_classes_in_modified_dex'] = 0
        report['unchanged_methods_in_modified_dex'] = 0
        for dex_name in sorted(changed_dex):
            old_dex, new_dex = DEX(old.read(dex_name)), DEX(new.read(dex_name))
            old_classes = {klass.get_name() for klass in old_dex.get_classes()}
            new_classes = {klass.get_name() for klass in new_dex.get_classes()}
            assert old_classes <= new_classes
            assert new_classes - old_classes == ({POLICY_CLASS} if dex_name == 'classes11.dex' else set())
            old_methods, new_methods = methods(old_dex), methods(new_dex)
            assert old_methods.keys() <= new_methods.keys()
            changed_methods = [key for key, code in old_methods.items() if new_methods[key] != code]
            expected = {}
            for filename, signature, _, kind in TARGETS:
                if target_tree(filename) != 'smali_' + dex_name.removesuffix('.dex'):
                    continue
                descriptor = 'L' + filename.removesuffix('.smali') + ';'
                name, parameters = signature.split('(', 1)
                expected[(descriptor, name, '(' + parameters)] = kind
            assert set(changed_methods) == expected.keys(), changed_methods
            for key, kind in expected.items():
                before, after = old_methods[key], new_methods[key]
                prefix_length = 9 if kind == 'filter-quota' else 4
                assert after is not None and before is not None and after[prefix_length:] == before, key
            report['changed_methods'].extend(list(key) for key in sorted(changed_methods))
            report['retained_classes_in_modified_dex'] += len(old_classes)
            report['unchanged_methods_in_modified_dex'] += len(old_methods) - len(changed_methods)
    base, modified = APK(str(original)), APK(str(preview))
    assert modified.is_valid_APK()
    assert base.get_package() == modified.get_package() == 'com.quark.scanking'
    assert base.get_androidversion_name() == modified.get_androidversion_name() == '10.13.0.455'
    assert set(base.get_permissions()) == set(modified.get_permissions())
    assert set(base.get_activities()) == set(modified.get_activities())
    assert set(base.get_services()) == set(modified.get_services())
    assert modified.get_app_name() == '扫描社区测试版'
    report.update(package=modified.get_package(), version=modified.get_androidversion_name(), label=modified.get_app_name(),
                  permissions=len(modified.get_permissions()), activities=len(modified.get_activities()), services=len(modified.get_services()),
                  sha256=hashlib.sha256(preview.read_bytes()).hexdigest(), structural_checks_passed=True)
    return report

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('original', type=Path)
    parser.add_argument('preview', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = verify(args.original, args.preview)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False, indent=2))
