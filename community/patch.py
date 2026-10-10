#!/usr/bin/env python3
"""Version-pinned, local community build adjustments. Never changes the input APK."""
import argparse
import hashlib
import json
import re
import shutil
import struct
from pathlib import Path
from zipfile import ZipFile

SOURCE_SHA256 = '3e6a468befdd9c0808c319f25a2e5d1fc701b38fc5bd4870f566a5d4f826596e'
POLICY_CLASS = 'Lorg/paperscan/community/CommunityAccess;'
TARGETS = [
    ('com/ucpro/feature/study/main/member/a.smali', 'g()Z', '5fba2543d106bb6f4766715c23eac17bb76a8ff39f55c697685f05d700f30f7b', 'access'),
    ('com/ucpro/feature/study/main/member/a.smali', 'h()Z', 'ffdc0c95e6e51bfdd98da7611984afa5c229df02dfd33f9b916b38961d7bbbaa', 'access'),
    ('com/ucpro/feature/study/main/member/d.smali', 'a()Z', 'b0475000aad0bc83f78e594d43b3e3ec66618d98f52f83b9291561f1df7b5e7b', 'access'),
    ('com/ucpro/feature/study/main/member/CameraVipPurchasePanelManager.smali', 'b(Ljava/lang/String;Ljava/lang/String;Z)V', '157de68b1ce285b5fdfa8932e449cc8c8207b383ba89f5b72bc080f83ee007c7', 'purchase'),
    ('com/ucpro/feature/study/home/tab/HomeCameraVipBanner.smali', 'setVisibility(I)V', 'd18fc0314b27ae22844a9ebad93e2d2451857e929e415250ba2790fc5bea0b3e', 'banner'),
    ('com/ucpro/feature/study/edit/pay/n.smali', 'a(Landroidx/concurrent/futures/CallbackToFutureAdapter$a;)Ljava/lang/Object;', '8f76515f068152898e125cac1f5b64c33511124f01e0bb17e31091b44ee318db', 'filter-quota'),
]

def target_tree(filename: str) -> str:
    return 'smali_classes10' if filename == 'com/ucpro/feature/study/edit/pay/n.smali' else 'smali_classes11'

def sha256(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def check_source(path: Path) -> None:
    if sha256(path) != SOURCE_SHA256:
        raise ValueError('APK SHA-256 mismatch. This patch supports only the inspected 10.13.0.455 package.')

def patch_method(text: str, signature: str, digest: str, kind: str) -> str:
    matches = [match for match in re.finditer(r'^\.method[^\n]*\n.*?^\.end method', text, re.M | re.S)
               if match.group(0).splitlines()[0].endswith(' ' + signature)]
    if len(matches) != 1:
        raise ValueError(f'Expected exactly one method: {signature}')
    match = matches[0]
    original = match.group(0)
    if hashlib.sha256(original.encode()).hexdigest() != digest:
        raise ValueError(f'Original method mismatch: {signature}')
    method = re.sub(r'(?m)^    \.locals (\d+)$', lambda value: f'    .locals {max(1, int(value[1]))}', original, count=1)
    start = method.find('\n    .line ')
    if start < 0:
        raise ValueError(f'No supported insertion point: {signature}')
    action = {
        'access': '    return v0\n',
        'purchase': '    return-void\n',
        'banner': '    const/16 p1, 0x8\n',
        'filter-quota': (
            '    new-instance v0, Lcom/ucpro/feature/study/main/paint/widget/SvipFreeCostData;\n'
            '    sget-object v1, Lcom/ucpro/feature/study/main/paint/widget/SvipFreeCostCommonLayout$State;->SVIP:Lcom/ucpro/feature/study/main/paint/widget/SvipFreeCostCommonLayout$State;\n'
            '    invoke-direct {v0, v1}, Lcom/ucpro/feature/study/main/paint/widget/SvipFreeCostData;-><init>(Lcom/ucpro/feature/study/main/paint/widget/SvipFreeCostCommonLayout$State;)V\n'
            '    invoke-virtual {p1, v0}, Landroidx/concurrent/futures/CallbackToFutureAdapter$a;->c(Ljava/lang/Object;)Z\n'
            '    const/4 v0, 0x0\n'
            '    return-object v0\n'),
    }[kind]
    prefix = (f'\n    # Community policy; preserve original logic when disabled.\n'
              f'    invoke-static {{}}, {POLICY_CLASS}->isEnabled()Z\n'
              f'    move-result v0\n'
              f'    if-eqz v0, :community_original\n' + action +
              f'    :community_original\n')
    patched = method[:start] + prefix + method[start:]
    return text[:match.start()] + patched + text[match.end():]

def prepare_smali(decoded: Path, output: Path) -> None:
    if output.exists():
        raise FileExistsError(f'Preserving existing output directory: {output}')
    # Work on copied DEX trees; keep the original decoded package intact.
    output.mkdir(parents=True)
    for tree in ('smali_classes10', 'smali_classes11'):
        shutil.copytree(decoded / tree, output / tree)
    for name, signature, digest, kind in TARGETS:
        path = output / target_tree(name) / name
        path.write_text(patch_method(path.read_text(), signature, digest, kind))
    policy = output / 'smali_classes11/org/paperscan/community/CommunityAccess.smali'
    policy.parent.mkdir(parents=True, exist_ok=True)
    policy.write_text(f'.class public final {POLICY_CLASS}\n.super Ljava/lang/Object;\n\n'
                      '.method public static isEnabled()Z\n    .locals 1\n'
                      '    const/4 v0, 0x1\n    return v0\n.end method\n')
    print('Patched six local methods; original branches retained.')

def length8(data: bytes, offset: int) -> tuple[int, int]:
    value = data[offset]
    if value & 0x80:
        return ((value & 0x7f) << 8) | data[offset + 1], offset + 2
    return value, offset + 1

def length16(data: bytes, offset: int) -> tuple[int, int]:
    value = struct.unpack_from('<H', data, offset)[0]
    if value & 0x8000:
        return ((value & 0x7fff) << 16) | struct.unpack_from('<H', data, offset + 2)[0], offset + 4
    return value, offset + 2

def encode8(value: int) -> bytes:
    if value > 0x7fff:
        raise ValueError('String exceeds the UTF-8 string-pool length limit.')
    return bytes([value]) if value < 0x80 else bytes([0x80 | (value >> 8), value & 255])

def encode16(value: int) -> bytes:
    return struct.pack('<H', value) if value < 0x8000 else struct.pack('<HH', 0x8000 | (value >> 16), value & 0xffff)

def rename_label(table: bytes) -> bytes:
    root_type, root_header, root_size = struct.unpack_from('<HHI', table)
    if root_type != 2 or root_size != len(table):
        raise ValueError('Unexpected resource table.')
    position = root_header
    while position < len(table):
        kind, header, size = struct.unpack_from('<HHI', table, position)
        if kind == 1:
            chunk = table[position:position + size]
            count, styles, flags, string_start, style_start = struct.unpack_from('<IIIII', chunk, 8)
            strings = []
            changed = 0
            for index in range(count):
                offset = string_start + struct.unpack_from('<I', chunk, header + index * 4)[0]
                original_start = offset
                if flags & 0x100:
                    _, offset = length8(chunk, offset)
                    length, offset = length8(chunk, offset)
                    value = chunk[offset:offset + length]
                    encoded = chunk[original_start:offset + length + 1]
                    match = value == '夸克扫描王'.encode('utf-8')
                else:
                    length, offset = length16(chunk, offset)
                    value = chunk[offset:offset + length * 2]
                    encoded = chunk[original_start:offset + length * 2 + 2]
                    match = value == '夸克扫描王'.encode('utf-16le')
                if match:
                    title = '扫描社区测试版'
                    utf16 = title.encode('utf-16le')
                    if flags & 0x100:
                        utf8 = title.encode('utf-8')
                        encoded = encode8(len(utf16) // 2) + encode8(len(utf8)) + utf8 + b'\0'
                    else:
                        encoded = encode16(len(utf16) // 2) + utf16 + b'\0\0'
                    changed += 1
                strings.append(encoded)
            if changed == 0:
                raise ValueError('Original app title was not present in the global string pool.')
            buffer = bytearray()
            offsets = []
            for encoded in strings:
                offsets.append(len(buffer))
                buffer += encoded
            buffer += b'\0' * (-len(buffer) % 4)
            style_data = chunk[style_start:] if styles else b''
            prefix = bytearray(chunk[:string_start])
            for index, offset in enumerate(offsets):
                struct.pack_into('<I', prefix, header + index * 4, offset)
            new_style_start = string_start + len(buffer) if styles else 0
            new_size = len(prefix) + len(buffer) + len(style_data)
            struct.pack_into('<I', prefix, 4, new_size)
            struct.pack_into('<I', prefix, 16, flags & ~1)  # New title may change lexical sort order.
            struct.pack_into('<I', prefix, 24, new_style_start)
            result = bytearray(table[:position] + prefix + buffer + style_data + table[position + size:])
            struct.pack_into('<I', result, 4, len(result))
            print(f'Renamed {changed} app title string(s), keeping resource IDs and indices.')
            return bytes(result)
        if size <= 0:
            raise ValueError('Invalid resource chunk size.')
        position += size
    raise ValueError('Global string pool not found.')

def repack(source: Path, dex_directory: Path, output: Path) -> None:
    if output.exists():
        raise FileExistsError(f'Preserving existing file: {output}')
    check_source(source)
    provenance = {'edition': 'community-client-preview', 'source_sha256': SOURCE_SHA256,
                  'base_version': '10.13.0.455', 'local_access_policy': True,
                  'backend_membership_modified': False,
                  'changed_methods': [{'path': row[0], 'method': row[1]} for row in TARGETS]}
    with ZipFile(source) as original, ZipFile(output, 'w') as modified:
        for entry in original.infolist():
            # Remove only the original JAR signature, retaining service-provider metadata.
            if re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))', entry.filename, re.I):
                continue
            data = original.read(entry.filename)
            if entry.filename in ('classes10.dex', 'classes11.dex'):
                data = (dex_directory / entry.filename).read_bytes()
            elif entry.filename == 'resources.arsc':
                data = rename_label(data)
            modified.writestr(entry, data)
        modified.writestr('assets/community-build.json', json.dumps(provenance, ensure_ascii=False, indent=2).encode())
    print(f'Repacked preview without changing the source APK: {output}')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    prepare = commands.add_parser('prepare')
    prepare.add_argument('--source', type=Path, required=True)
    prepare.add_argument('--decoded', type=Path, required=True)
    prepare.add_argument('--output', type=Path, required=True)
    pack = commands.add_parser('repack')
    pack.add_argument('--source', type=Path, required=True)
    pack.add_argument('--dex-directory', type=Path, required=True)
    pack.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    check_source(args.source)
    if args.command == 'prepare':
        prepare_smali(args.decoded, args.output)
    else:
        repack(args.source, args.dex_directory, args.output)
