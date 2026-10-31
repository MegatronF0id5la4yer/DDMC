import json
import urllib.request
import urllib.error
import subprocess
import os

# Retrieve credentials
proc = subprocess.run(
    ['git', 'credential', 'fill'],
    input=b'protocol=https\nhost=github.com\n\n',
    capture_output=True,
    check=True
)
lines = proc.stdout.decode('utf-8').splitlines()
creds = dict(l.split('=', 1) for l in lines if '=' in l)
token = creds.get('password')
repo = 'MegatronF0id5la4yer/DDMC'
tag = 'v2.1.0'
apk_path = os.path.abspath(r'releases\DDMC.apk')

print(f'Creating release {tag} for {repo}...')

headers = {
    'Authorization': f'token {token}',
    'Accept': 'application/vnd.github.v3+json',
    'User-Agent': 'DDMC-Release-Publisher',
}

payload = {
    'tag_name': tag,
    'target_commitish': 'main',
    'name': f'DDMC {tag} - Storage Video Player, Default Player & Expressive 3D Dance',
    'body': (
        f'### DDMC {tag}\n\n'
        '**Official Release with Cavifax / Chris Chan / Aimep3 / Bagresuko Suite**\n\n'
        '#### What\'s New in v2.1.0:\n'
        '- **Storage Video Player Section**: Dedicated video tab in library (`LibMode.VIDEOS`) automatically scanning local storage (MP4, MKV, WebM, AVI, MOV) with full ExoPlayer video surface rendering.\n'
        '- **Default Media Player**: System intent filter support for `android.intent.action.VIEW` for both audio and video streams/files (`content://` and `file://`).\n'
        '- **Expressive 3D Idol Choreography**: High-range arm articulation (-16° to +44° shoulder, -20° to +32° elbow), marked dynamic leg pops (35° knee-pop, 22° thigh rotation), deep hip bounces, and zero torso mesh tearing.\n'
        '- **Custom Yuri Posture**: Canonical Satchely DDLC pose with hands gracefully clasped behind the back, continuous flawless upper body mesh (zero cutouts or tearing) and full dance responsiveness.\n'
        '- **Interactive Speed & Positioning Controls**: Live Doki dance speed slider (25% to 200%), Visualizer animation speed slider (20% to 200%), 2D Sprite vs 3D HD frame switch, and horizontal/vertical stage drag positioning (±240dp / ±200dp).\n'
        '- **Zero interruptions or telemetry**: Completely offline DSP audio processing.\n\n'
        '#### Assets:\n'
        '- `DDMC.apk`: Official production debug build (`com.cavifax.bagresuko`).'
    ),
    'draft': False,
    'prerelease': False,
}

# 1. Create or get release
req = urllib.request.Request(
    f'https://api.github.com/repos/{repo}/releases',
    data=json.dumps(payload).encode('utf-8'),
    headers=headers,
    method='POST'
)

try:
    with urllib.request.urlopen(req) as resp:
        rel_data = json.loads(resp.read().decode('utf-8'))
        print(f"Created release ID: {rel_data['id']}")
except urllib.error.HTTPError as e:
    err_body = e.read().decode('utf-8')
    print(f'HTTPError creating release: {e.code} - {err_body}')
    if e.code == 422:  # Already exists, fetch it
        req_get = urllib.request.Request(
            f'https://api.github.com/repos/{repo}/releases/tags/{tag}',
            headers=headers
        )
        with urllib.request.urlopen(req_get) as resp:
            rel_data = json.loads(resp.read().decode('utf-8'))
            print(f"Existing release ID: {rel_data['id']}")
    else:
        raise

# 2. Upload asset DDMC.apk
upload_url_template = rel_data['upload_url']
upload_url = upload_url_template.split('{')[0] + '?name=DDMC.apk'

print(f'Uploading {apk_path} ({os.path.getsize(apk_path)} bytes) to {upload_url}...')

# Check if asset already exists in release
for asset in rel_data.get('assets', []):
    if asset['name'] == 'DDMC.apk':
        print(f"Deleting existing asset ID {asset['id']}...")
        del_req = urllib.request.Request(
            f"https://api.github.com/repos/{repo}/releases/assets/{asset['id']}",
            headers=headers,
            method='DELETE'
        )
        urllib.request.urlopen(del_req).close()

with open(apk_path, 'rb') as f:
    apk_bytes = f.read()

up_headers = {
    'Authorization': f'token {token}',
    'Content-Type': 'application/vnd.android.package-archive',
    'Content-Length': str(len(apk_bytes)),
    'User-Agent': 'DDMC-Release-Publisher',
}

up_req = urllib.request.Request(upload_url, data=apk_bytes, headers=up_headers, method='POST')
with urllib.request.urlopen(up_req) as resp:
    asset_data = json.loads(resp.read().decode('utf-8'))
    print(f"Asset uploaded successfully! Download URL: {asset_data.get('browser_download_url')}")

print("GitHub Release published successfully!")
