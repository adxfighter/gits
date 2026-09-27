# Stops the GITS local stack. Pass -Purge to also delete the database volume.
param([switch]$Purge)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
if ($Purge) { docker compose down -v } else { docker compose down }
