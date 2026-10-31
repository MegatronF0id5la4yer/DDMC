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
tag = 'v2.1.2'
apk_path = os.path.abspath(r'releases\DDMC.apk')

print(f'Checking release {tag} for {repo}...')

headers = {
    'Authorization': f'token {token}',
    'Accept': 'application/vnd.github.v3+json',
    'User-Agent': 'DDMC-Release-Publisher',
}

# 1. Create or get release
req_get = urllib.request.Request(
    f'https://api.github.com/repos/{repo}/releases/tags/{tag}',
    headers=headers
)
try:
    with urllib.request.urlopen(req_get) as resp:
        rel_data = json.loads(resp.read().decode('utf-8'))
        print(f"Existing release ID: {rel_data['id']}")
except Exception:
    payload = {
        'tag_name': tag,
        'target_commitish': 'main',
        'name': f'DDMC {tag} - Zero Black Bars Adaptive Video & True Immersive Fullscreen',
        'body': (
            f'### DDMC {tag}\n\n'
            '**Official Release with Cavifax / Chris Chan / Aimep3 / Bagresuko Suite**\n\n'
            '#### Novedades en v2.1.2:\n'
            '- **Cero Franjas Negras Sin Recortar (Zero Black Bars Adaptive Video)**: El contenedor del escenario adapta dinámicamente sus dimensiones proporcionales a la relación de aspecto nativa del video (`effW : effH`), eliminando por completo las barras negras superiores/inferiores y laterales sin recortar un solo píxel de la imagen original.\n'
            '- **Modo Inmersivo Total (Edge-to-Edge Sin Barras de Sistema)**: Ocultación permanente de la barra de notificaciones superior (reloj, batería, notificaciones) y de la barra inferior de botones/gestos de Android mediante `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` y tema transparente en ventana, aprovechando el 100% de la pantalla OLED.\n'
            '- **Transparencia en Shutter y Contenedor**: Fondo transparente en `stageVideoCard` y obturador de `PlayerView` para evitar cuadros negros artificiales durante transiciones.\n'
            '- **Firma RSA Permanente (`ddmc.keystore`)**: Mantiene la misma firma de versión anterior para actualizaciones directas sin conflicto de firmas.\n\n'
            '#### Assets:\n'
            '- `DDMC.apk`: Compilación estándar limpia oficial (`com.cavifax.bagresuko`).'
        ),
        'draft': False,
        'prerelease': False,
    }
    req_post = urllib.request.Request(
        f'https://api.github.com/repos/{repo}/releases',
        data=json.dumps(payload).encode('utf-8'),
        headers=headers,
        method='POST'
    )
    with urllib.request.urlopen(req_post) as resp:
        rel_data = json.loads(resp.read().decode('utf-8'))
        print(f"Created release ID: {rel_data['id']}")

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
