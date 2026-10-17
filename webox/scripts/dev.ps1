# Starts everything needed for a local demo.
#
#   pwsh -File scripts/dev.ps1              # infrastructure must already be running
#   pwsh -File scripts/dev.ps1 -WithDocker  # also runs `docker compose up -d` first
#
# Opens the backend on :8080 and the SPA on :5173 in two windows, waits until both answer,
# then prints the URLs.

param(
    [switch]$WithDocker,
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot

if ($WithDocker) {
    Write-Host 'Starting MySQL and Redis via docker compose...'
    Push-Location $root
    docker compose up -d
    Pop-Location
}

if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) {
    Write-Warning 'JAVA_HOME does not point at a JDK 17 installation. Set it first, e.g.:'
    Write-Warning '  $env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.2"'
    exit 1
}

# --- backend -------------------------------------------------------------
$backendDir = Join-Path $root 'backend'
$jar = Join-Path $backendDir 'target\webox-backend-1.0.0.jar'

if (-not (Test-Path $jar)) {
    Write-Host 'Building the backend (first run downloads dependencies)...'
    Push-Location $backendDir
    if (Test-Path '.\mvnw.cmd') { & .\mvnw.cmd -B -q package -DskipTests }
    else { & mvn -B -q package -DskipTests }
    Pop-Location
}

Write-Host 'Starting the backend on http://localhost:8080 ...'
Start-Process -FilePath (Join-Path $JavaHome 'bin\java.exe') `
    -ArgumentList '-jar', $jar `
    -WorkingDirectory $backendDir `
    -RedirectStandardOutput (Join-Path $backendDir 'backend.log') `
    -RedirectStandardError (Join-Path $backendDir 'backend.err.log')

# --- frontend ------------------------------------------------------------
$frontendDir = Join-Path $root 'frontend'
if (-not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
    Write-Host 'Installing frontend dependencies...'
    Push-Location $frontendDir
    npm install --no-audit --no-fund
    Pop-Location
}

Write-Host 'Starting the SPA on http://localhost:5173 ...'
Start-Process -FilePath 'npm.cmd' -ArgumentList 'run', 'dev' -WorkingDirectory $frontendDir

# --- wait for readiness --------------------------------------------------
$backendReady = $false
for ($i = 1; $i -le 45; $i++) {
    Start-Sleep -Seconds 2
    try {
        Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 4 -UseBasicParsing | Out-Null
        $backendReady = $true
        break
    } catch { }
}

$frontendReady = $false
for ($i = 1; $i -le 30; $i++) {
    Start-Sleep -Seconds 1
    try {
        Invoke-WebRequest -Uri 'http://localhost:5173/' -TimeoutSec 3 -UseBasicParsing | Out-Null
        $frontendReady = $true
        break
    } catch { }
}

Write-Host ''
Write-Host ('backend  : ' + $(if ($backendReady) { 'UP  http://localhost:8080' } else { 'NOT RESPONDING - see backend/backend.log' }))
Write-Host ('frontend : ' + $(if ($frontendReady) { 'UP  http://localhost:5173' } else { 'NOT RESPONDING - see the npm window' }))
Write-Host ''
Write-Host 'Employee app : http://localhost:5173'
Write-Host 'Console      : http://localhost:5173/console'
Write-Host 'API docs     : http://localhost:8080/swagger-ui.html'
Write-Host 'Admin login  : admin@webox.com / Admin1234'
