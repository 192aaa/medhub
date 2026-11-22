# ============================================================================
# Offline backend test runner (no Docker required)
#
# How it works:
#   Sets IDENTITY_TEST_USE_LOCAL_INFRA=true so IdentityTestSupport reuses the
#   local MySQL(3306) / Redis(6379) directly instead of starting Testcontainers
#   (mysql:8.0 / redis:3.2-alpine). Flyway still loads only db/migration
#   (constitution VI); the test database is cloud_consultation_test.
#
# Prerequisites:
#   Local MySQL(3306) and Redis(6379) must be running.
#   MySQL root password must be provided via env var MYSQL_PASSWORD.
#
# Usage:
#   .\run-test-local.ps1                     # full suite (offline -o)
#   .\run-test-local.ps1 -Reset              # recreate test DB first (clears stale Flyway history)
#   .\run-test-local.ps1 -Filter "AppointmentStatusConverterTest,RevisitEligibilityServiceTest"
#   .\run-test-local.ps1 -Online             # online mode (default is offline)
#
# Tip: if you see "Detected failed migration to version ...", run with -Reset.
# ============================================================================
param(
    [switch]$Reset,       # recreate test DB to clear failed Flyway history
    [string]$Filter,      # run only the given test class(es), comma separated (-Dtest=)
    [switch]$Online       # run mvn online (default offline -o)
)

Set-Location -Path $PSScriptRoot

# 1) Local-infrastructure mode flag
$env:IDENTITY_TEST_USE_LOCAL_INFRA = "true"

# 2) Put Maven on PATH
$mavenHome = "C:\tools\apache-maven-3.9.6"
if (Test-Path "$mavenHome\bin\mvn.cmd") {
    $env:PATH = "$mavenHome\bin;$env:PATH"
} elseif (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error "Maven not found. Install it or edit `$mavenHome in this script."
    exit 1
}

# 3) Locate the MySQL client
$mysql = $env:MYSQL_PATH
if (-not $mysql) {
    $candidates = @(
        "D:\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
    )
    $mysql = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}
$mysqlPassword = $env:MYSQL_PASSWORD
if (-not $mysqlPassword) {
    Write-Error "MYSQL_PASSWORD is required. Set it before running this script."
    exit 1
}

# 4) Prepare the test database (password via MYSQL_PWD to avoid the CLI warning)
if ($mysql) {
    if ($Reset) {
        $sql = "DROP DATABASE IF EXISTS cloud_consultation_test; CREATE DATABASE cloud_consultation_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
        Write-Host "Recreating test database cloud_consultation_test ..."
    } else {
        $sql = "CREATE DATABASE IF NOT EXISTS cloud_consultation_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
    }
    $env:MYSQL_PWD = $mysqlPassword
    & $mysql -u root -e $sql 2>$null
    $dbOk = ($LASTEXITCODE -eq 0)
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    if (-not $dbOk) {
        Write-Warning "Failed to prepare test DB. If Flyway reports a failed migration, run with -Reset."
    }
} else {
    Write-Warning "mysql client not found; skipped DB prep. Ensure cloud_consultation_test already exists."
}

# 5) Run tests
$mvnArgs = @()
if (-not $Online) { $mvnArgs += "-o" }
$mvnArgs += "test"
if ($Filter) { $mvnArgs += "-Dtest=$Filter" }

Write-Host "Running: mvn $($mvnArgs -join ' ')"
& mvn @mvnArgs
exit $LASTEXITCODE
