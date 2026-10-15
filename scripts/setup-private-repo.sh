#!/usr/bin/env bash
set -e

echo "=========================================================="
echo "   CONFIGURACIÓN DE REPOSITORIO PRIVADO - SONORA & PORT   "
echo "=========================================================="
echo ""

REPO_URL="$1"
if [ -z "$REPO_URL" ]; then
    echo "Introduce la URL de tu repositorio privado (GitHub, GitLab, etc.):"
    echo "Ejemplo: https://github.com/TU_USUARIO/sonora-private.git"
    read -r -p "URL: " REPO_URL
fi

if [ -z "$REPO_URL" ]; then
    echo "Error: No se proporcionó ninguna URL."
    exit 1
fi

echo ""
echo "[1/4] Agregando archivos al staging de Git..."
git add .

echo "[2/4] Creando commit..."
git commit -m "feat: port a Android, reproducción de vídeo en PC y móvil, y visualizador de audio" || true

echo "[3/4] Configurando remote 'private'..."
if git remote | grep -q "^private$"; then
    git remote set-url private "$REPO_URL"
else
    git remote add private "$REPO_URL"
fi

echo "[4/4] Subiendo rama main al repositorio privado..."
git push -u private main

echo ""
echo "¡Completado!"

