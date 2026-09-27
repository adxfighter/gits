# Windows wrapper: runs scripts/sandbox-selftest.sh in Git Bash.
# Binary tar data must not pass through the PowerShell pipeline, so the logic lives in the bash script.
$ErrorActionPreference = 'Stop'
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
$bash = Join-Path ${env:ProgramFiles} 'Git\bin\bash.exe'
if (-not (Test-Path $bash)) { Write-Error 'Git Bash is required: install Git for Windows.' }
& $bash (Join-Path $PSScriptRoot 'sandbox-selftest.sh') @args
exit $LASTEXITCODE
