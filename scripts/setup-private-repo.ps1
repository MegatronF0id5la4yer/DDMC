<#
.SYNOPSIS
    Configura y sube el proyecto Sonora (con el port de Android y soporte de vídeo) a tu repositorio privado de Git.
.DESCRIPTION
    Este script te permite vincular un repositorio privado nuevo (GitHub, GitLab, etc.) y subir todos los cambios.
#>

param(
    [string]$RepoUrl
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   CONFIGURACIÓN DE REPOSITORIO PRIVADO - SONORA & PORT   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

if (-not $RepoUrl) {
    Write-Host "Introduce la URL de tu repositorio privado:" -ForegroundColor Yellow
    Write-Host "Ejemplo HTTPS: https://github.com/TU_USUARIO/sonora-private.git" -ForegroundColor DarkGray
    Write-Host "Ejemplo SSH:   git@github.com:TU_USUARIO/sonora-private.git" -ForegroundColor DarkGray
    $RepoUrl = Read-Host "URL del repositorio privado"
}

if (-not $RepoUrl) {
    Write-Host "Error: No se proporcionó ninguna URL." -ForegroundColor Red
    exit 1
}

# 1. Preparar commits locales
Write-Host "`n[1/4] Agregando archivos al staging de Git..." -ForegroundColor Green
git add .

Write-Host "[2/4] Creando commit de las novedades..." -ForegroundColor Green
git commit -m "feat: port a Android, reproducción de vídeo en PC y móvil, y visualizador de audio"

# 2. Configurar el remote privado
Write-Host "[3/4] Configurando remote 'private'..." -ForegroundColor Green
$existingRemote = git remote | Select-String -Pattern "^private$"
if ($existingRemote) {
    git remote set-url private $RepoUrl
    Write-Host "Remote 'private' actualizado a: $RepoUrl" -ForegroundColor Gray
} else {
    git remote add private $RepoUrl
    Write-Host "Remote 'private' creado con URL: $RepoUrl" -ForegroundColor Gray
}

# 3. Subir a la rama main
Write-Host "[4/4] Subiendo al repositorio privado (rama main)..." -ForegroundColor Green
Write-Host "Ejecutando: git push -u private main" -ForegroundColor Yellow
git push -u private main

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n¡Éxito! Tu repositorio privado ha sido actualizado con todas las funciones." -ForegroundColor Green
} else {
    Write-Host "`nSi la autenticación falló, asegúrate de haber creado el repositorio privado vacío previamente en tu proveedor (GitHub/GitLab) y tener tus credenciales o token configurados." -ForegroundColor Yellow
    Write-Host "Para forzar o reintentar manualmente, ejecuta:" -ForegroundColor DarkGray
    Write-Host "  git push -u private main" -ForegroundColor Cyan
}

