param(
    [string]$DatabasePassword = $env:SPRING_DATASOURCE_PASSWORD
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$SecretFile = Join-Path $ProjectRoot ".dev-jwt-secret.txt"
$MailSecretFile = Join-Path $ProjectRoot ".dev-mail-secret.txt"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java was not found in PATH. Java 21 is required for this setup."
}

$javaVersion = (& java --version | Select-Object -First 1)
Write-Host "Using $javaVersion"

if ([string]::IsNullOrWhiteSpace($DatabasePassword)) {
    $secure = Read-Host "PostgreSQL password for agridabao_admin" -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $DatabasePassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

if (-not (Test-Path $SecretFile)) {
    $bytes = New-Object byte[] 32

    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()

    try {
        $rng.GetBytes($bytes)
    }
    finally {
        $rng.Dispose()
    }

    [Convert]::ToBase64String($bytes) |
        Set-Content -NoNewline -Encoding ascii $SecretFile
}

$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/agridabao_players"
$env:SPRING_DATASOURCE_USERNAME = "agridabao_admin"
$env:SPRING_DATASOURCE_PASSWORD = $DatabasePassword
$env:APP_JWT_SECRET_BASE64 = (Get-Content $SecretFile -Raw).Trim()
$env:APP_ACCESS_TOKEN_MINUTES = "1440"

# Local dev only: return the verification code in the request-step response so
# testing works without a real inbox. The application default is FALSE so a
# deployed server never hands codes out over the wire - this line is what turns
# the convenience back on for local runs.
if ([string]::IsNullOrWhiteSpace($env:APP_VERIFICATION_EXPOSE_CODE)) {
    $env:APP_VERIFICATION_EXPOSE_CODE = "true"
}

# Email verification codes.
# In dev, leave everything below unset: codes are logged to this console and
# returned in the request-step response (see the line above), so testing works
# without a real inbox.
#
# To send real Gmail without retyping env vars each time, create
# .dev-mail-secret.txt (next to this script's parent folder, git-ignored) with:
#   MAIL_USERNAME=youraddress@gmail.com
#   MAIL_PASSWORD=your-16-char-app-password
# It's loaded automatically below. Env vars you set manually before running
# always take priority over the file. Set APP_VERIFICATION_EXPOSE_CODE=false
# in production so the code is never returned over the wire.
if (-not (Test-Path $MailSecretFile)) {
    @"
# Fill these in for real Gmail sending, then re-run this script.
# Leave as-is (or delete this file) to keep dev mode: codes print to the console instead.
MAIL_USERNAME=
MAIL_PASSWORD=
"@ | Set-Content -Encoding utf8 $MailSecretFile
}
else {
    Get-Content $MailSecretFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }

        # Accept "NAME=value" or "NAME:value" so a stray colon doesn't silently drop a line.
        $separatorIndex = $line.IndexOfAny(@('=', ':'))
        if ($separatorIndex -lt 1) { return }

        $name = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim().Trim('"').Trim("'")
        if ($value -eq "") { return }

        if ([string]::IsNullOrWhiteSpace([System.Environment]::GetEnvironmentVariable($name))) {
            Set-Item -Path "env:$name" -Value $value
            Write-Host "Loaded $name from .dev-mail-secret.txt"
        }
    }
}

if (-not [string]::IsNullOrWhiteSpace($env:MAIL_USERNAME) -and [string]::IsNullOrWhiteSpace($env:MAIL_PASSWORD)) {
    Write-Warning "MAIL_USERNAME is set but MAIL_PASSWORD is not - real email sending will be skipped."
}

Set-Location $ProjectRoot

if (-not (Test-Path ".\gradlew.bat")) {
    throw "gradlew.bat is missing. Generate the base project at start.spring.io first, then copy this overlay into it."
}

& .\gradlew.bat bootRun
