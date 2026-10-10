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
from patch import sha256, rename_label, prepare_smali
from verify import verify as verify_base, methods

BASE_SHA = 'c0dd4201e7232abadcf0bff5a37d5b7ae72be2fab35606150b3d2fd235cbc852'
ACTIVITY_SHA = '223853a204aaaa80dfe6b0d05d1d1586dc47c5a8e497fd1dc9ea63e0319117b9'
HOOK = 'Lorg/hyperscan/diagnostics/DebugOverlay;->install(Landroid/app/Activity;)V'
PROBE = 'Lorg/hyperscan/diagnostics/CryptoProbe;->'
CRYPTO_SHA = '0e21c2d5eca9618df6cd572faf2d663405fb79443bba5ae4572e2f1893da5a4a'
STS_SHA = '194886b827cde6bedc33c76ec367dd986795b231aef3ade41b8ac1b73121a285'

def insert_method(text, signature, marker, addition):
    start = text.index(signature)
    end = text.index('.end method', start)
    body = text[start:end]
    assert body.count(marker) == 1, (signature, marker)
    return text[:start] + body.replace(marker, marker + '\n    ' + addition, 1) + text[end:]

def probe_flow(dex, key):
    """Compare original instructions and branch destinations after removing observation calls."""
    method = next(m for c in dex.get_classes() for m in c.get_methods()
                  if (c.get_name(), m.get_name(), m.get_descriptor().replace(' ', '')) == key)
    code = method.get_code()
    local_count = code.get_registers_size() - code.get_ins_size()
    rows = []; offset = 0; ordinal = 0; destinations = {}
    for instruction in code.get_bc().get_instructions():
        is_hook = PROBE in instruction.get_output()
        destinations[offset] = ordinal
        rows.append((offset, instruction, is_hook))
        if not is_hook: ordinal += 1
        offset += instruction.get_length()
    result = []
    for offset, instruction, is_hook in rows:
        if is_hook: continue
        value = re.sub(r'\bv(\d+)\b', lambda m: f'p{int(m[1])-local_count}' if int(m[1]) >= local_count else m[0], instruction.get_output())
        name = instruction.get_name()
        if name.startswith(('if-', 'goto')):
            target = offset + instruction.get_ref_off() * 2
            assert target in destinations
            value = value.rsplit(',', 1)[0] + ',target#' + str(destinations[target]) if name.startswith('if-') else 'target#' + str(destinations[target])
        result.append((name, value))
    return tuple(result)

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
    for number in (8, 11):
        shutil.copytree(decoded / f'smali_classes{number}', output.parent / f'smali_classes{number}')
    baseline_smali = output.parent / "baseline-smali"
    prepare_smali(decoded, baseline_smali)
    shutil.rmtree(output.parent / "smali_classes11")
    shutil.copytree(baseline_smali / "smali_classes11", output.parent / "smali_classes11")
    crypto = output.parent / 'smali_classes8/com/uc/encrypt/EncryptHelper.smali'
    sts = output.parent / 'smali_classes11/com/ucpro/feature/study/edit/task/net/direct/mtop/HttpStsTokenHelper$StsTokenRequester.smali'
    assert sha256(crypto) == CRYPTO_SHA
    assert sha256(sts) == STS_SHA
    text = crypto.read_text()
    for name, method in [('handleDecryptException', 'decryptError'), ('handleInitException', 'initError')]:
        signature = f'.method private static {name}(Ljava/lang/Throwable;I)V'
        start = text.index(signature); end = text.index('.end method', start)
        body = text[start:end]
        marker = re.search(r'    \.locals \d+', body).group()
        text = insert_method(text, signature, marker, f'invoke-static {{p0, p1}}, {PROBE}{method}(Ljava/lang/Throwable;I)V')
    text = insert_method(text, '.method private static decryptByKey(Ljava/lang/String;)Ljava/lang/String;', '    move-exception p0', f'invoke-static {{p0}}, {PROBE}caught(Ljava/lang/Throwable;)V')
    crypto.write_text(text)
    text = sts.read_text()
    marker = '    move-result-object v10'
    signature = '.method public static b(Lkotlin/jvm/internal/Ref$ObjectRef;'
    sts.write_text(insert_method(text, signature, marker, f'invoke-static {{v7, v8, v10}}, {PROBE}sts(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V'))

def pack(base, dex_dir, output):
    check_base(base)
    if output.exists(): raise FileExistsError(output)
    with ZipFile(base) as old, ZipFile(output, 'x') as new:
        for entry in old.infolist():
            if re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))', entry.filename, re.I): continue
            data = old.read(entry)
            if entry.filename in {'classes8.dex','classes9.dex','classes11.dex'}: data = (dex_dir/entry.filename).read_bytes()
            if entry.filename == 'resources.arsc': data = rename_label(data, '扫描社区测试版', 'HyperScan 诊断版')
            new.writestr(entry, data)
        new.writestr('classes13.dex', (dex_dir/'classes13.dex').read_bytes())
        new.writestr('assets/hyperscan-diagnostics.json', json.dumps({
            'author': 'Laoch832', 'source_preview_sha256': BASE_SHA, 'runtime_tested': False,
            'provider': 'redacted own-process threadtime messages and stack traces, connectivity and exception causes',
            'raw_logs_or_payloads_exported': False, 'tls_or_server_verification_modified': False,
        }, ensure_ascii=False, indent=2))

def audit(original, base, preview, output):
    check_base(base)
    baseline = verify_base(original, base)
    logger.disable('androguard')
    with ZipFile(base) as old, ZipFile(preview) as new:
        changed = [n for n in set(old.namelist()) & set(new.namelist()) if old.read(n) != new.read(n)]
        signatures = {n for n in old.namelist() if re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))', n, re.I)}
        assert set(changed) - signatures == {'classes8.dex','classes9.dex','classes11.dex','resources.arsc'}, changed
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
        expected = {
            8: {
                ('Lcom/uc/encrypt/EncryptHelper;', 'handleDecryptException', '(Ljava/lang/Throwable;I)V'): 'decryptError',
                ('Lcom/uc/encrypt/EncryptHelper;', 'handleInitException', '(Ljava/lang/Throwable;I)V'): 'initError',
                ('Lcom/uc/encrypt/EncryptHelper;', 'decryptByKey', '(Ljava/lang/String;)Ljava/lang/String;'): 'caught',
            },
            11: {('Lcom/ucpro/feature/study/edit/task/net/direct/mtop/HttpStsTokenHelper$StsTokenRequester;', 'b', '(Lkotlin/jvm/internal/Ref$ObjectRef;Lcom/ucpro/feature/study/edit/task/net/direct/mtop/HttpStsTokenHelper$StsTokenRequester;[B)Lcom/ucpro/feature/study/edit/task/net/direct/mtop/OssStsKey;'): 'sts'},
        }
        probe_checks = []
        for number, changed_methods in expected.items():
            left, right = DEX(old.read(f'classes{number}.dex')), DEX(new.read(f'classes{number}.dex'))
            assert {c.get_name() for c in left.get_classes()} == {c.get_name() for c in right.get_classes()}
            before_methods, after_methods = methods(left), methods(right)
            assert before_methods.keys() == after_methods.keys()
            actual = {k for k in before_methods if before_methods[k] != after_methods[k]}
            assert actual == set(changed_methods), actual
            for method, name in changed_methods.items():
                original_instructions, patched = before_methods[method], after_methods[method]
                hooks = [n for n, instruction in enumerate(patched) if PROBE in instruction[1]]
                assert len(hooks) == 1 and PROBE + name + '(' in patched[hooks[0]][1]
                n = hooks[0]
                assert probe_flow(left, method) == probe_flow(right, method), 'Original instruction/control flow changed'
                probe_checks.append(list(method))
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
            'outcome_probe_hooks':probe_checks,'original_probe_method_instructions_retained':True,
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
