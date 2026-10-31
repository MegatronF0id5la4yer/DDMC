import json
import urllib.request
import urllib.error
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

print(f'Publishing release {tag} for {repo}...')

headers = {
    'Authorization': f'token {token}',
    'Accept': 'application/vnd.github.v3+json',
    'User-Agent': 'DDMC-Release-Publisher',
}

payload = {
    'tag_name': tag,
    'target_commitish': 'main',
    'name': f'DDMC {tag} - Stage Video Player, Kalcifer Synced Lyrics Engine & Cookie Persistence',
    'body': (
        f'### DDMC {tag}\n\n'
        '**Official Release with Cavifax / Chris Chan / Aimep3 / Bagresuko Suite**\n\n'
        '#### Novedades en v2.1.1:\n'
        '- **Reproducción de Video en Escenario (Stage Video Surface)**: Los videos locales de almacenamiento y de YouTube ahora se renderizan directamente en la tarjeta principal del reproductor (`PlayerView`), con ajuste dinámico de aspecto (Zero Franjas), controles por toque y soporte para gestos.\n'
        '- **Motor Kalcifer & Corrección de Archivos .LRC**: Búsqueda multi-ruta de archivos compañeros `.lrc` y `.txt` en la misma carpeta del audio y en rutas comunes (`/sdcard/Music/lyrics`), sin distinción entre mayúsculas y minúsculas (`.lrc` / `.LRC`), y persistencia por clave dual resistente a reescaneos de almacenamiento.\n'
        '- **Integración LRCLIB Online**: Sincronización automática de letras en vivo desde la base de datos abierta de LRCLIB en segundo plano cuando no exista archivo `.lrc` local.\n'
        '- **Keystore RSA Permanente (No más pérdida de cookies)**: Todas las compilaciones estándar ahora se firman con una clave RSA 2048 permanente (`ddmc.keystore`). Esto permite instalar futuras versiones directamente sobre la versión instalada sin que Android exija desinstalar ni borre la caché, datos o cookies.\n'
        '- **CookieBackupEngine**: Almacenamiento persistente y restauración de cookies de sesión para WebView y YouTube, asegurando que las cuentas y preferencias permanezcan intactas.\n'
        '- **Permiso de Almacenamiento Ampliado**: Soporte para `MANAGE_EXTERNAL_STORAGE` y `requestLegacyExternalStorage` en Android 11+ para garantizar acceso directo a archivos de letras multimedia.\n\n'
        '#### Assets:\n'
        '- `DDMC.apk`: Compilación estándar limpia oficial (`com.cavifax.bagresuko`).'
    ),
    'draft': False,
    'prerelease': False,
}

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
    if e.code == 422:
        req_get = urllib.request.Request(
            f'https://api.github.com/repos/{repo}/releases/tags/{tag}',
            headers=headers
        )
        with urllib.request.urlopen(req_get) as resp:
            rel_data = json.loads(resp.read().decode('utf-8'))
            print(f"Existing release ID: {rel_data['id']}")
    else:
        raise

upload_url_template = rel_data['upload_url']
upload_url = upload_url_template.split('{')[0] + '?name=DDMC.apk'

print(f'Uploading {apk_path} ({os.path.getsize(apk_path)} bytes) to {upload_url}...')

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

up_req = urllib.request.Request(
    upload_url,
    data=apk_bytes,
    headers=up_headers,
    method='POST'
)

with urllib.request.urlopen(up_req) as resp:
    res = json.loads(resp.read().decode('utf-8'))
    print(f"Upload complete! Asset ID: {res['id']}, download URL: {res['browser_download_url']}")
