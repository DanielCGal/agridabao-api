param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Json($value) {
    return ($value | ConvertTo-Json -Depth 50 -Compress)
}

function New-EmptySlots {
    $slots = @()
    for ($i = 0; $i -lt 36; $i++) {
        $slots += @{ itemType = "None"; amount = 0; liquidMl = 0; sprayerLiquid = "None" }
    }
    return $slots
}

function New-FarmSnapshot([string]$district, [int]$money, [string]$itemType, [int]$amount) {
    $slots = New-EmptySlots
    $slots[0] = @{ itemType = $itemType; amount = $amount; liquidMl = 0; sprayerLiquid = "None" }
    return @{
        area = @{ x = 0.30; y = 0.47; width = 0.02; height = 0.02; districtName = $district; terrainSeed = 12345 }
        soil = @{
            sampleALatitude = 7.0; sampleALongitude = 125.5
            sampleA = @{ sand = 45; silt = 28; clay = 27; phh2o = 6.1; soc = 22; cfvo = 6; bdod = 125; nitrogen = 14 }
            sampleBLatitude = 7.01; sampleBLongitude = 125.51
            sampleB = @{ sand = 40; silt = 30; clay = 30; phh2o = 6.3; soc = 24; cfvo = 5; bdod = 120; nitrogen = 16 }
        }
        player = @{ position = @{x=10;y=5;z=10}; rotation = @{x=0;y=0;z=0;w=1} }
        inventory = @{ selectedSlotIndex = 0; money = $money; slots = $slots }
        gameTime = @{ totalGameDays = 4.33; daysPerMonth = 16; monthsPerYear = 12 }
        weather = @{ currentEvent = "Clear"; currentTemperatureC = 27; currentHumidity = 78; currentRainIntensity = 0; remainingEventDays = 0; dailyLowTemperatureC = 24; dailyHighTemperatureC = 31 }
        climateEvent = @{ isTrackingEvent = $false; activeEventType = "Clear"; activeDurationDays = 0; eventStartGameDay = 0; beforeSnapshots = @(); actionRecords = @() }
        crops = @()
        worldObjects = @()
    }
}

function Register-Player([string]$displayName) {
    $stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $email = "$($displayName.ToLower()).$stamp@example.com"
    $codeRequest = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/register/request" -ContentType "application/json" -Body (Json @{
        email = $email
        password = "TestFarm123!"
        confirmPassword = "TestFarm123!"
        displayName = $displayName
    })
    if ([string]::IsNullOrWhiteSpace($codeRequest.devCode)) {
        throw "No devCode returned. Start the backend with APP_VERIFICATION_EXPOSE_CODE=true (the dev default)."
    }
    return Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/register/verify" -ContentType "application/json" -Body (Json @{
        email = $email
        code = $codeRequest.devCode
    })
}

function Headers($auth) { return @{ Authorization = "Bearer $($auth.accessToken)" } }

Write-Host "1. Checking backend"
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health"
if ($health.status -ne "UP") { throw "Backend is not UP." }

Write-Host "2. Creating two test players"
$alice = Register-Player "AliceFarm"
Start-Sleep -Milliseconds 5
$bob = Register-Player "BobFarm"
$aliceHeaders = Headers $alice
$bobHeaders = Headers $bob

Write-Host "3. Creating farm snapshots"
Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/farms/me" -Headers $aliceHeaders -ContentType "application/json" -Body (Json @{
    expectedRevision = 0; schemaVersion = 1; generatorVersion = "davao-terrain-v1"
    snapshot = New-FarmSnapshot "Calinan" 5000 "Tomato" 20
}) | Out-Null
Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/farms/me" -Headers $bobHeaders -ContentType "application/json" -Body (Json @{
    expectedRevision = 0; schemaVersion = 1; generatorVersion = "davao-terrain-v1"
    snapshot = New-FarmSnapshot "Toril" 5000 "Corn" 20
}) | Out-Null

Write-Host "4. Marking both players online"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/presence/heartbeat" -Headers $aliceHeaders | Out-Null
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/presence/heartbeat" -Headers $bobHeaders | Out-Null

Write-Host "5. Search and friendship"
$search = Invoke-RestMethod -Uri "$BaseUrl/api/players/search?displayName=BobFarm" -Headers $aliceHeaders
if ($search.Count -lt 1) { throw "Player search returned no result." }
$bobId = $bob.user.id
$aliceId = $alice.user.id
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/friends/requests/$bobId" -Headers $aliceHeaders | Out-Null
$requests = Invoke-RestMethod -Uri "$BaseUrl/api/friends/requests" -Headers $bobHeaders
if ($requests.Count -lt 1) { throw "Friend request was not received." }
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/friends/requests/$($requests[0].requestId)/accept" -Headers $bobHeaders | Out-Null

Write-Host "6. Offline-capable chat"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/chat/$bobId/messages" -Headers $aliceHeaders -ContentType "application/json" -Body (Json @{ body = "Hello from the automated social test." }) | Out-Null
$messages = Invoke-RestMethod -Uri "$BaseUrl/api/chat/$aliceId/messages" -Headers $bobHeaders
if ($messages.Count -lt 1) { throw "Chat message was not stored." }

Write-Host "7. Marketplace create and buy"
$listing = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/marketplace/listings" -Headers $aliceHeaders -ContentType "application/json" -Body (Json @{
    itemType = "Tomato"; quantity = 2; askingPrice = 50
})
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/marketplace/listings/$($listing.id)/buy" -Headers $bobHeaders -ContentType "application/json" -Body "{}" | Out-Null

Write-Host "8. Online trade with two-step confirmation"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/presence/heartbeat" -Headers $aliceHeaders | Out-Null
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/presence/heartbeat" -Headers $bobHeaders | Out-Null
$trade = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/trades/invite" -Headers $aliceHeaders -ContentType "application/json" -Body (Json @{ targetPlayerId = $bobId })
$trade = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/trades/$($trade.id)/accept" -Headers $bobHeaders -ContentType "application/json" -Body "{}"
Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/trades/$($trade.id)/offer" -Headers $aliceHeaders -ContentType "application/json" -Body (Json @{
    money = 10; items = @(@{ itemType = "Tomato"; quantity = 1 })
}) | Out-Null
Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/trades/$($trade.id)/offer" -Headers $bobHeaders -ContentType "application/json" -Body (Json @{
    money = 5; items = @(@{ itemType = "Corn"; quantity = 1 })
}) | Out-Null
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/trades/$($trade.id)/agree" -Headers $aliceHeaders -ContentType "application/json" -Body "{}" | Out-Null
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/trades/$($trade.id)/agree" -Headers $bobHeaders -ContentType "application/json" -Body "{}" | Out-Null
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/trades/$($trade.id)/confirm" -Headers $aliceHeaders -ContentType "application/json" -Body "{}" | Out-Null
$completed = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/trades/$($trade.id)/confirm" -Headers $bobHeaders -ContentType "application/json" -Body "{}"
if ($completed.status -ne "COMPLETED") { throw "Trade did not complete." }

Write-Host "`nALL SOCIAL, CHAT, MARKETPLACE, AND TRADE TESTS PASSED"
