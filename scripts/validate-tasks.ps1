# Windows wrapper for scripts/validate-tasks.sh (runs in Git Bash). Same arguments.
# The validator jar is built here with Maven from PowerShell; Git Bash then only runs docker and java.
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
if (-not $env:JAVA_HOME -and (Test-Path "$env:USERPROFILE\.jdks")) {
    $jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory | Where-Object { $_.Name -match '21' } | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$env:Path" }

$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'backend\gits-taskbank\target\gits-taskbank.jar'
if ($env:REBUILD -eq '1' -or -not (Test-Path $jar)) {
    Push-Location (Join-Path $root 'backend')
    try {
        mvn -B -q -pl gits-taskbank -am package -DskipTests
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally {
        Pop-Location
    }
}

$bash = Join-Path ${env:ProgramFiles} 'Git\bin\bash.exe'
if (-not (Test-Path $bash)) { Write-Error 'Git Bash is required: install Git for Windows.' }
$env:REBUILD = '0'
& $bash (Join-Path $PSScriptRoot 'validate-tasks.sh') @args
exit $LASTEXITCODE
