# Starts the GITS local stack on Windows (Docker Desktop).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error 'Docker is not installed or not in PATH. Install Docker Desktop (WSL2 backend) first.'
}

if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    Write-Warning '.env created from .env.example - review the demo credentials before showing the demo.'
}

if (Test-Path 'sandbox/java/Dockerfile') {
    $image = (Select-String -Path '.env' -Pattern '^SANDBOX_IMAGE=(.+)$').Matches.Groups[1].Value
    if (-not $image) { $image = 'gits-sandbox-java:local' }
    Write-Host "Building sandbox image $image"
    docker build -t $image sandbox/java
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

docker compose up --build -d
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
docker compose ps
$port = (Select-String -Path '.env' -Pattern '^WEB_PORT=(.+)$').Matches.Groups[1].Value
Write-Host "GITS UI: http://localhost:$port"
