#!/usr/bin/env python3
"""Sign a direct-distribution APK without putting private credentials in the repository."""
import argparse
import hashlib
import os
from pathlib import Path
import secrets
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--init', action='store_true', help='Create a dedicated key if none exists')
parser.add_argument('--input', type=Path, default=root / 'app/build/outputs/apk/release/app-release-unsigned.apk')
parser.add_argument('--output', type=Path, default=root / 'artifacts/FMB-Vasaigaam-0.4.0-release.apk')
args = parser.parse_args()
private = Path(os.environ.get('FMB_SIGNING_DIR', str(Path.home() / '.local/share/fmb-vasaigaam/signing'))).resolve()
if private == root or root in private.parents:
    parser.error('Signing files must live outside the Git repository')
keystore = private / 'fmb-release.p12'
password = private / 'store-password.txt'
if not args.input.is_file():
    parser.error('Build :app:assembleRelease first')
if not keystore.exists():
    if not args.init:
        parser.error('No signing key. Use --init once, then back up the private directory securely.')
    if password.exists():
        parser.error('Partial signing setup exists; inspect it instead of replacing credentials')
    private.mkdir(mode=0o700, parents=True, exist_ok=True)
    private.chmod(0o700)
    with password.open('x') as handle:
        os.chmod(password, 0o600)
        handle.write(secrets.token_urlsafe(48) + '\n')
    keytool = shutil.which('keytool')
    if not keytool:
        parser.error('JDK keytool is required')
    subprocess.run([keytool, '-genkeypair', '-keystore', str(keystore), '-storetype', 'PKCS12',
                    '-storepass:file', str(password), '-keypass:file', str(password),
                    '-alias', 'fmb', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                    '-dname', 'CN=FMB Vasaigaam, O=FMB Vasaigaam, C=IN'], check=True)
    keystore.chmod(0o600)
if not password.is_file():
    parser.error('Signing password file is missing; restore it from your private backup')
sdk = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
if not sdk:
    for line in (root / 'local.properties').read_text().splitlines():
        if line.startswith('sdk.dir='):
            sdk = line.split('=', 1)[1].replace('\\ ', ' ')
if not sdk:
    parser.error('Set ANDROID_SDK_ROOT or sdk.dir in local.properties')
build_tools = Path(sdk) / 'build-tools'
versions = sorted((p for p in build_tools.iterdir() if (p / 'apksigner').is_file()),
                  key=lambda p: tuple(int(x) if x.isdigit() else 0 for x in p.name.replace('-', '.').split('.')))
if not versions:
    parser.error('Install Android SDK build tools')
bin_dir = versions[-1]
args.output.parent.mkdir(parents=True, exist_ok=True)
subprocess.run([str(bin_dir / 'apksigner'), 'sign', '--ks', str(keystore), '--ks-key-alias', 'fmb',
                '--ks-pass', f'file:{password}',
                '--out', str(args.output), str(args.input)], check=True)
subprocess.run([str(bin_dir / 'apksigner'), 'verify', '--verbose', '--print-certs', str(args.output)], check=True)
subprocess.run([str(bin_dir / 'zipalign'), '-c', '-P', '16', '4', str(args.output)], check=True)
print(f'APK: {args.output}')
print(f'SHA-256: {hashlib.sha256(args.output.read_bytes()).hexdigest()}')
print(f'Private signing files (never upload): {private}')
