#!/usr/bin/env python3
"""
Servidor de Actualizaciones OTA (PC Local + Git Sync) para Estudio Musical Android.
Escucha en 0.0.0.0:8765 (LAN 192.168.1.150:8765 y túnel USB ADB 127.0.0.1:8765).
Endpoints:
  - GET  /update.json               -> Metadatos del último APK, commit Git y tamaño
  - GET  /Estudio-Musical-HiRes.apk -> Descarga directa del APK compilado en esta PC
  - POST /rebuild                   -> Ejecuta `git pull` + `gradlew assembleDebug` y devuelve el nuevo update.json
"""
import datetime
import json
import os
import shutil
import subprocess
from http.server import BaseHTTPRequestHandler, HTTPServer

ROOT_DIR = os.path.dirname(os.path.abspath(__file__))
ANDROID_DIR = os.path.join(ROOT_DIR, "android_app")
DEBUG_APK = os.path.join(ANDROID_DIR, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
PUBLISHED_APK = os.path.join(ROOT_DIR, "Estudio-Musical-HiRes.apk")
PORT = 8765


def get_apk_path() -> str:
    if os.path.exists(DEBUG_APK):
        if not os.path.exists(PUBLISHED_APK) or os.path.getmtime(DEBUG_APK) >= os.path.getmtime(PUBLISHED_APK):
            try:
                shutil.copy2(DEBUG_APK, PUBLISHED_APK)
            except Exception:
                return DEBUG_APK
    return PUBLISHED_APK if os.path.exists(PUBLISHED_APK) else DEBUG_APK


def get_git_meta():
    commit = "HEAD"
    branch = "main"
    msg = "Build en vivo desde esta PC (UI Estudio + Dokis DDSC + Landscape 16:9 + GIF Covers)"
    try:
        commit = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"], cwd=ROOT_DIR, stderr=subprocess.DEVNULL
        ).decode("utf-8", errors="ignore").strip()
        branch = subprocess.check_output(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"], cwd=ROOT_DIR, stderr=subprocess.DEVNULL
        ).decode("utf-8", errors="ignore").strip()
        last_msg = subprocess.check_output(
            ["git", "log", "-1", "--pretty=%s"], cwd=ROOT_DIR, stderr=subprocess.DEVNULL
        ).decode("utf-8", errors="ignore").strip()
        if last_msg:
            msg = last_msg
    except Exception:
        pass
    return commit, branch, msg


def build_update_payload():
    apk = get_apk_path()
    size_mb = "0 MB"
    updated_at = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    if os.path.exists(apk):
        size_bytes = os.path.getsize(apk)
        size_mb = f"{size_bytes / (1024 * 1024):.1f} MB"
        mtime = os.path.getmtime(apk)
        updated_at = datetime.datetime.fromtimestamp(mtime).strftime("%Y-%m-%d %H:%M:%S")
    commit, branch, msg = get_git_meta()
    return {
        "version": f"1.5.0-{commit}",
        "commit": commit,
        "branch": branch,
        "updatedAt": updated_at,
        "sizeMb": size_mb,
        "changelog": msg,
        "apkPath": "/Estudio-Musical-HiRes.apk",
    }


class UpdateHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(f"[OTA-PC] {self.address_string()} - {fmt % args}")

    def do_GET(self):
        if self.path.startswith("/update.json") or self.path == "/":
            payload = json.dumps(build_update_payload(), ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        elif self.path.startswith("/Estudio-Musical-HiRes.apk") or self.path.endswith(".apk"):
            apk = get_apk_path()
            if not os.path.exists(apk):
                self.send_error(404, "APK not found on PC")
                return
            size = os.path.getsize(apk)
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            self.send_header("Content-Length", str(size))
            self.send_header("Content-Disposition", 'attachment; filename="Estudio-Musical-HiRes.apk"')
            self.end_headers()
            with open(apk, "rb") as f:
                shutil.copyfileobj(f, self.wfile)
        else:
            self.send_error(404, "Not Found")

    def do_POST(self):
        if self.path.startswith("/rebuild"):
            try:
                subprocess.run(["git", "pull", "--rebase", "--autostash"], cwd=ROOT_DIR, timeout=30)
            except Exception:
                pass
            gradlew = os.path.join(ANDROID_DIR, "gradlew.bat" if os.name == "nt" else "gradlew")
            subprocess.run([gradlew, "assembleDebug"], cwd=ANDROID_DIR, timeout=110)
            payload = json.dumps(build_update_payload(), ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        else:
            self.send_error(404, "Not Found")


if __name__ == "__main__":
    print(f"Iniciando servidor OTA en http://0.0.0.0:{PORT} ...")
    HTTPServer(("0.0.0.0", PORT), UpdateHandler).serve_forever()
