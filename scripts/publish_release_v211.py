import json
import urllib.request
import subprocess
import os

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
tag = 'v2.1.1'
apk_path = os.path.abspath(r'releases\DDMC.apk')

print(f'Checking release {tag} for {repo}...')

headers = {
    'Authorization': f'token {token}',
    'Accept': 'application/vnd.github.v3+json',
    'User-Agent': 'DDMC-Release-Publisher',
}

req_get = urllib.request.Request(
    f'https://api.github.com/repos/{repo}/releases/tags/{tag}',
    headers=headers
)
with urllib.request.urlopen(req_get) as resp:
    rel_data = json.loads(resp.read().decode('utf-8'))

upload_url = rel_data['upload_url'].split('{')[0] + '?name=DDMC.apk'

# Remove existing if any
for asset in rel_data.get('assets', []):
    if asset['name'] == 'DDMC.apk':
        print(f"Deleting existing asset ID {asset['id']}...")
        del_req = urllib.request.Request(
            f"https://api.github.com/repos/{repo}/releases/assets/{asset['id']}",
            headers=headers,
            method='DELETE'
        )
        urllib.request.urlopen(del_req).close()

print(f'Uploading {apk_path} via curl.exe...')
cmd = [
    'curl.exe',
    '-X', 'POST',
    '-H', f'Authorization: token {token}',
    '-H', 'Content-Type: application/vnd.android.package-archive',
    '--data-binary', f'@{apk_path}',
    upload_url
]
subprocess.run(cmd, check=True)
print('Upload complete!')
