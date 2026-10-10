#!/usr/bin/env python3
"""Laoch832: add a bounded in-app diagnostics provider to the verified R2 preview."""
import argparse
import json
import re
import shutil
from pathlib import Path
from zipfile import ZipFile
from loguru import logger
from androguard.core.apk import APK
from androguard.core.dex import DEX
from patch import sha256, rename_label
from verify import verify as verify_base, methods

BASE_SHA = 'c0dd4201e7232abadcf0bff5a37d5b7ae72be2fab35606150b3d2fd235cbc852'
ACTIVITY_SHA = '223853a204aaaa80dfe6b0d05d1d1586dc47c5a8e497fd1dc9ea63e0319117b9'
HOOK = 'Lorg/hyperscan/diagnostics/DebugOverlay;->install(Landroid/app/Activity;)V'

def check_base(base):
    if sha256(base) != BASE_SHA: raise ValueError('Requires the exact statically audited R2 preview.')

def prepare(decoded, output):
    if output.exists(): raise FileExistsError(output)
    activity = decoded / 'smali_classes9/com/ucpro/MainActivity.smali'
    assert sha256(activity) == ACTIVITY_SHA
    shutil.copytree(decoded / 'smali_classes9', output)
    target = output / 'com/ucpro/MainActivity.smali'
    text = target.read_text()
    marker = '    invoke-super {p0, p1}, Landroidx/fragment/app/FragmentActivity;->onCreate(Landroid/os/Bundle;)V'
    assert text.count(marker) == 1
    target.write_text(text.replace(marker, marker + '\n\n    # Non-blocking diagnostics by Laoch832.\n    invoke-static {p0}, ' + HOOK, 1))

def pack(base, dex_dir, output):
    check_base(base)
    if output.exists(): raise FileExistsError(output)
    with ZipFile(base) as old, ZipFile(output, 'x') as new:
        for entry in old.infolist():
            if re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))', entry.filename, re.I): continue
            data = old.read(entry)
            if entry.filename == 'classes9.dex': data = (dex_dir/'classes9.dex').read_bytes()
            if entry.filename == 'resources.arsc': data = rename_label(data, '扫描社区测试版', 'HyperScan 诊断版')
            new.writestr(entry, data)
        new.writestr('classes13.dex', (dex_dir/'classes13.dex').read_bytes())
        new.writestr('assets/hyperscan-diagnostics.json', json.dumps({
            'author': 'Laoch832', 'source_preview_sha256': BASE_SHA, 'runtime_tested': False,
            'provider': 'in-process logcat classifications, connectivity and uncaught exception class/frames',
            'raw_logs_or_payloads_exported': False, 'tls_or_server_verification_modified': False,
        }, ensure_ascii=False, indent=2))

def audit(original, base, preview, output):
    check_base(base)
    baseline = verify_base(original, base)
    logger.disable('androguard')
    with ZipFile(base) as old, ZipFile(preview) as new:
        changed = [n for n in set(old.namelist()) & set(new.namelist()) if old.read(n) != new.read(n)]
        signatures = {n for n in old.namelist() if re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))', n, re.I)}
        assert set(changed) - signatures == {'classes9.dex','resources.arsc'}, changed
        assert 'META-INF/MANIFEST.MF' in changed
        added = set(new.namelist()) - set(old.namelist())
        assert added == {'classes13.dex','assets/hyperscan-diagnostics.json'}, added
        assert set(old.namelist()) <= set(new.namelist())
        for n in set(old.namelist()) - set(changed): assert old.read(n) == new.read(n), n
        before, after = DEX(old.read('classes9.dex')), DEX(new.read('classes9.dex'))
        assert {c.get_name() for c in before.get_classes()} == {c.get_name() for c in after.get_classes()}
        a,b=methods(before), methods(after)
        assert a.keys() == b.keys()
        differences = [k for k in a if a[k]!=b[k]]
        key=('Lcom/ucpro/MainActivity;','onCreate','(Landroid/os/Bundle;)V')
        assert differences == [key], differences
        calls = [i for i,ins in enumerate(b[key]) if HOOK in ins[1]]
        assert len(calls)==1
        i=calls[0]
        assert b[key][:i]+b[key][i+1:] == a[key], 'Original lifecycle instructions must remain intact.'
        extra=DEX(new.read('classes13.dex'))
        assert all(c.get_name().startswith('Lorg/hyperscan/diagnostics/') for c in extra.get_classes())
        assert any(c.get_name()=='Lorg/hyperscan/diagnostics/DebugOverlay;' for c in extra.get_classes())
    first, second=APK(str(base)), APK(str(preview))
    assert first.get_package()==second.get_package()
    assert first.get_androidversion_code()==second.get_androidversion_code()
    for f in ['get_permissions','get_activities','get_services']: assert set(getattr(first,f)())==set(getattr(second,f)())
    assert second.get_app_name()=='HyperScan 诊断版'
    report={'author':'Laoch832','runtime_tested':False,'structural_checks_passed':True,
            'baseline_audit':baseline,'sha256':sha256(preview),'label':second.get_app_name(),
            'diagnostics_hook':list(key),'original_lifecycle_instructions_retained':True,
            'diagnostic_classes':len(extra.get_classes()),'tls_or_server_verification_modified':False,
            'existing_apk_payload_changes':sorted(changed),'added_entries':sorted(added)}
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__); sub=p.add_subparsers(dest='action',required=True)
    q=sub.add_parser('prepare');q.add_argument('decoded',type=Path);q.add_argument('output',type=Path)
    q=sub.add_parser('pack');q.add_argument('base',type=Path);q.add_argument('dex_dir',type=Path);q.add_argument('output',type=Path)
    q=sub.add_parser('audit');q.add_argument('original',type=Path);q.add_argument('base',type=Path);q.add_argument('preview',type=Path);q.add_argument('output',type=Path)
    args=vars(p.parse_args());action=args.pop('action');{'prepare':prepare,'pack':pack,'audit':audit}[action](**args)
