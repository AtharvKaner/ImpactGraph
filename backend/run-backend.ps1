# =============================================================================
# ImpactGraph Backend Startup Helper (run-backend.ps1)
# =============================================================================
# Automatically provisions an ephemeral 512-bit JWT secret in memory,
# checks PostgreSQL connectivity on port 5432, and launches Spring Boot.
# =============================================================================

$ErrorActionPreference = "Stop"

# 1. Generate 512-bit (64-byte) cryptographic JWT secret in memory if not set
if (-not $env:JWT_SECRET) {
    $bytes = New-Object byte[] 64
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
    $rng.Dispose()
    Write-Host "[INFO] Secure 512-bit JWT_SECRET configured in session memory." -ForegroundColor Cyan
}

# 2. Check PostgreSQL connectivity on port 5432
$pgListening = $false
try {
    $client = New-Object System.Net.Sockets.TcpClient
    $asyncResult = $client.BeginConnect("127.0.0.1", 5432, $null, $null)
    $connected = $asyncResult.AsyncWaitHandle.WaitOne(1000, $false)
    if ($connected -and $client.Connected) {
        $client.EndConnect($asyncResult)
        $pgListening = $true
    }
    $client.Close()
} catch {
    $pgListening = $false
}

if ($pgListening) {
    Write-Host "[INFO] PostgreSQL detected on localhost:5432. Connecting to PostgreSQL..." -ForegroundColor Green
} else {
    Write-Host "[INFO] PostgreSQL is not running on localhost:5432." -ForegroundColor Yellow
    Write-Host "[INFO] Activating 'dev' profile with embedded H2 database." -ForegroundColor Cyan
    $env:SPRING_PROFILES_ACTIVE = "dev"
}

# 3. Launch via Maven Wrapper
.\mvnw.cmd spring-boot:run
