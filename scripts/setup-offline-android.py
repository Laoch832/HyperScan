#!/usr/bin/env python3
"""Laoch832: install checksum-pinned Android build prerequisites without root access."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tarfile
from urllib.parse import urlparse, unquote
from zipfile import ZipFile

root = Path(os.environ.get('HYPERSCAN_TOOLS_ROOT', '/workspace/tools' if Path('/workspace').exists() else str(Path.home()/'.cache/hyperscan/tools')))
root.mkdir(parents=True, exist_ok=True)
gradle_home = root.parent / '.local/share/hyperscan-gradle'
gradle_home.mkdir(parents=True, exist_ok=True)

def fetch(name, url, algorithm, digest):
    path = root / name
    def valid(): return path.exists() and hashlib.new(algorithm, path.read_bytes()).hexdigest() == digest
    if not valid(): subprocess.run(['curl', '--fail', '--location', '--retry', '2', '--silent', '--show-error', url, '--output', str(path)], check=True)
    if not valid(): raise RuntimeError('Archive integrity check failed: ' + name)
    return path

def unzip(path, destination, executable):
    if not executable.exists():
        destination.mkdir(parents=True, exist_ok=True)
        with ZipFile(path) as archive: archive.extractall(destination)

jdk_archive = fetch('hyperscan-jdk21.tar.gz', 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz', 'sha256', 'ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94')
jdk = root / 'jdk-21.0.12.1+1'
if not (jdk/'bin/javac').exists():
    with tarfile.open(jdk_archive) as archive: archive.extractall(root, filter='data')
gradle_archive = fetch('gradle-8.14.3-bin.zip', 'https://downloads.gradle.org/distributions/gradle-8.14.3-bin.zip', 'sha256', 'bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531')
gradle = root/'gradle-8.14.3/bin/gradle'
unzip(gradle_archive, root, gradle)
gradle.chmod(0o755)
# Android archive checksums published by Google's SDK repository XML.
platform_archive = fetch('platform-36_r02.zip', 'https://dl.google.com/android/repository/platform-36_r02.zip', 'sha1', '2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576')
platform = root/'android-platform-36/android-36'
unzip(platform_archive, root/'android-platform-36', platform/'android.jar')
tools36_archive = fetch('android-build-tools.zip', 'https://dl.google.com/android/repository/build-tools_r36_linux.zip', 'sha1', 'b0b6376977657e8ad9b969bacf4093601da2c6fb')
tools36 = root/'android-build-tools/android-16'
unzip(tools36_archive, root/'android-build-tools', tools36/'apksigner')
tools35_archive = fetch('hyperscan-build-tools35.zip', 'https://dl.google.com/android/repository/build-tools_r35_linux.zip', 'sha1', '2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc')
unzip(tools35_archive, root/'hyperscan-build-tools35', root/'hyperscan-build-tools35/android-15/source.properties')
tools35 = next((root/'hyperscan-build-tools35').glob('*/source.properties')).parent
for directory in (tools35, tools36):
    for name in ('aapt', 'aapt2', 'aidl', 'zipalign', 'apksigner', 'd8', 'dexdump', 'split-select'):
        if (directory/name).exists(): (directory/name).chmod(0o755)
sdk = root/'android-sdk'
for path, source in ((sdk/'platforms/android-36', platform), (sdk/'build-tools/36.0.0', tools36), (sdk/'build-tools/35.0.0', tools35)):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists(): path.symlink_to(source)
properties = ['org.gradle.workers.max=2', 'org.gradle.daemon=false']
trust = Path('/etc/ssl/certs/java/cacerts')
properties.append('org.gradle.jvmargs=-Xmx2g' + (' -Djavax.net.ssl.trustStore=' + str(trust) if trust.exists() else ''))
def escape(value): return value.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r')
for protocol in ('http', 'https'):
    proxy = urlparse(os.environ.get(protocol.upper()+'_PROXY', ''))
    if proxy.hostname:
        properties += ['systemProp.'+protocol+'.proxyHost='+proxy.hostname, 'systemProp.'+protocol+'.proxyPort='+str(proxy.port or 80)]
        for attribute, suffix in (('username', 'User'), ('password', 'Password')):
            value = getattr(proxy, attribute)
            if value: properties.append('systemProp.'+protocol+'.proxy'+suffix+'='+escape(unquote(value)))
config = gradle_home/'gradle.properties'
config.write_text('\n'.join(properties)+'\n'); config.chmod(0o600)
environment = {'java_home': str(jdk), 'gradle': str(gradle), 'gradle_user_home': str(gradle_home), 'sdk': str(sdk), 'build_tools': str(tools36)}
(root/'hyperscan-offline-environment.json').write_text(json.dumps(environment, indent=2)+'\n')
subprocess.run([str(jdk/'bin/javac'), '-version'], check=True)
print('Verified offline Android build tools; configuration saved outside the checkout.')
