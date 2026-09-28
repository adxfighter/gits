# Resets the local demo (Windows): deletes the database, starts the stack (the task bank and the demo accounts
# are loaded by api at start) and seeds the recorded demo sessions from seed/demo-sessions.
# Usage: powershell -ExecutionPolicy Bypass -File scripts/demo-reset.ps1 [-Yes]
param([switch]$Yes)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $Yes) {
    $answer = Read-Host 'Все данные локальной базы GITS будут удалены. Продолжить? (y/N)'
    if ($answer -notin @('y', 'Y', 'д', 'Д')) {
        Write-Host 'Отменено.'
        exit 1
    }
}

docker compose --profile tools down -v --remove-orphans
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& "$PSScriptRoot/up.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
# up returns once the containers start; the seed needs the schema, the bank and the demo employer made by api
docker compose up -d --wait
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

docker compose --profile tools run --rm --build taskbank demo-seed /seed/demo-sessions
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$port = (Select-String -Path '.env' -Pattern '^WEB_PORT=(.+)$').Matches.Groups[1].Value
Write-Host "Готово. Кабинет работодателя: http://localhost:$port/employer — балл и индикаторы демо-сессий появятся в течение минуты."
