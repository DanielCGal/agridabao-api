param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function JsonBody($Object) {
    return ($Object | ConvertTo-Json -Depth 30 -Compress)
}

function Expect-HttpStatus {
    param([scriptblock]$Action, [int]$ExpectedStatus, [string]$Label)
    try {
        & $Action | Out-Null
        throw "$Label unexpectedly succeeded; expected HTTP $ExpectedStatus."
    }
    catch {
        $status = $null
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $status = [int]$_.Exception.Response.StatusCode
        }

        if ($status -ne $ExpectedStatus) {
            throw "$Label returned HTTP $status instead of $ExpectedStatus. $($_.Exception.Message)"
        }
        Write-Host "PASS: $Label returned HTTP $ExpectedStatus"
    }
}

Write-Host "1. Health check"
$health = Invoke-RestMethod -Method Get -Uri "$BaseUrl/actuator/health"
if ($health.status -ne "UP") { throw "Backend health is not UP." }
Write-Host "PASS: backend is UP"

$email = "agridabao.test.$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@example.com"
$password = "TestFarm123!"

Write-Host "2. Register $email (request code + verify)"
$codeRequest = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/register/request" `
    -ContentType "application/json" `
    -Body (JsonBody @{
        email = $email
        password = $password
        confirmPassword = $password
        displayName = "AgriDabao Test Player"
    })

if ([string]::IsNullOrWhiteSpace($codeRequest.devCode)) {
    throw "No devCode returned. Start the backend with APP_VERIFICATION_EXPOSE_CODE=true (the dev default)."
}

$register = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/register/verify" `
    -ContentType "application/json" `
    -Body (JsonBody @{
        email = $email
        code = $codeRequest.devCode
    })

if ([string]::IsNullOrWhiteSpace($register.accessToken)) { throw "No access token was returned." }
$headers = @{ Authorization = "Bearer $($register.accessToken)" }
Write-Host "PASS: two-step registration and JWT issuance"

Write-Host "3. Read current user"
$me = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/users/me" -Headers $headers
if ($me.email -ne $email) { throw "The /api/users/me email did not match." }
Write-Host "PASS: authenticated user endpoint"

Write-Host "4. Confirm farm is initially missing"
Expect-HttpStatus -ExpectedStatus 404 -Label "Initial farm GET" -Action {
    Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/farms/me" -Headers $headers
}

$snapshot = @{
    area = @{
        x = 0.30; y = 0.47; width = 0.02; height = 0.02
        districtName = "Test District"; terrainSeed = 12345
    }
    soil = @{
        sampleALatitude = 7.0; sampleALongitude = 125.5
        sampleA = @{ sand = 45; silt = 28; clay = 27; phh2o = 6.1; soc = 22; cfvo = 6; bdod = 125; nitrogen = 14 }
        sampleBLatitude = 7.01; sampleBLongitude = 125.51
        sampleB = @{ sand = 40; silt = 30; clay = 30; phh2o = 6.3; soc = 24; cfvo = 5; bdod = 120; nitrogen = 16 }
    }
    player = @{ position = @{x=10;y=5;z=10}; rotation = @{x=0;y=0;z=0;w=1} }
    inventory = @{ selectedSlotIndex = 0; money = 250; slots = @() }
    gameTime = @{ totalGameDays = 4.33; daysPerMonth = 16; monthsPerYear = 12 }
    weather = @{ currentEvent = "Clear"; currentTemperatureC = 27; currentHumidity = 78; currentRainIntensity = 0; remainingEventDays = 0; dailyLowTemperatureC = 24; dailyHighTemperatureC = 31 }
    climateEvent = @{ isTrackingEvent = $false; activeEventType = "Clear"; activeDurationDays = 0; eventStartGameDay = 0; beforeSnapshots = @(); actionRecords = @() }
    crops = @()
    worldObjects = @()
}

Write-Host "5. Create first farm save"
$save1 = Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/farms/me" -Headers $headers `
    -ContentType "application/json" `
    -Body (JsonBody @{
        expectedRevision = 0
        schemaVersion = 1
        generatorVersion = "davao-terrain-v1"
        snapshot = $snapshot
    })
if ($save1.revision -ne 1) { throw "First save revision should be 1." }
Write-Host "PASS: first farm save, revision 1"

Write-Host "6. Load farm"
$loaded = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/farms/me" -Headers $headers
if ($loaded.snapshot.area.districtName -ne "Test District") { throw "Loaded farm data did not match." }
Write-Host "PASS: farm snapshot load"

Write-Host "7. Replace farm with correct revision"
$snapshot.inventory.money = 999
$save2 = Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/farms/me" -Headers $headers `
    -ContentType "application/json" `
    -Body (JsonBody @{
        expectedRevision = 1
        schemaVersion = 1
        generatorVersion = "davao-terrain-v1"
        snapshot = $snapshot
    })
if ($save2.revision -ne 2) { throw "Second save revision should be 2." }
Write-Host "PASS: replacement save, revision 2"

Write-Host "8. Reject stale revision"
Expect-HttpStatus -ExpectedStatus 409 -Label "Stale farm PUT" -Action {
    Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/farms/me" -Headers $headers `
        -ContentType "application/json" `
        -Body (JsonBody @{
            expectedRevision = 1
            schemaVersion = 1
            generatorVersion = "davao-terrain-v1"
            snapshot = $snapshot
        })
}

Write-Host "9. Record logout"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/farms/me/logout" -Headers $headers | Out-Null
Write-Host "PASS: logout metadata endpoint"

Write-Host "`nALL BACKEND TESTS PASSED"
